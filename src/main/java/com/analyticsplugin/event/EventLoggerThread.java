package com.analyticsplugin.event;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Where events are written to a log file. We do this on a separate thread, so we're not eating up CPU cycles of the
 * client thread.
 */
@NullMarked
@Slf4j
public class EventLoggerThread implements Runnable {

    private interface Command {}

    @Value
    private static class EventCommand implements Command {
        AnalyticsData.LogEntry event;
    }

    @Value
    private static class OpenLogCommand implements Command {
        String logFileName;
    }

    private static final String LOG_DIRECTORY = "analytics";
    private static final int FLUSH_INTERVAL_SECONDS = 10;
    private static final int OPEN_LOG_FILE_RETRY_ATTEMPTS = 10;
    private static final int OPEN_LOG_FILE_RETRY_DELAY_SECONDS = 10;
    /// How long to wait before activating the kill switch if we can't submit commands.
    private static final int KILL_AFTER_SECONDS_OF_SUBMISSION_FAILURES =
            OPEN_LOG_FILE_RETRY_DELAY_SECONDS * OPEN_LOG_FILE_RETRY_ATTEMPTS * 2;
    /// Maximum messages we can have sitting on the command queue. After that, we will drop messages.
    private static final int MAX_COMMAND_QUEUE = 1000;
    private static final int MAX_BACKLOG = 100;

    private final Gson gson;
    private final Clock clock;
    private final Filepath logDirectory;

    private final LinkedBlockingQueue<Command> commandQueue = new LinkedBlockingQueue<>(MAX_COMMAND_QUEUE);
    private final Queue<Command> backloggedCommands = new ArrayDeque<>(MAX_BACKLOG);

    private final AtomicBoolean killSwitch = new AtomicBoolean(false);

    private Instant lastSuccessfulSubmission;

    @Nullable
    private LogFile logFile;

    @Nullable
    private Instant nextFlush;

    @Value
    private static class LogFile implements Flushable, Closeable {
        Filepath file;
        BufferedWriter bufferedWriter;

        @Override
        public void close() throws IOException {
            this.bufferedWriter.close();
        }

        @Override
        public void flush() throws IOException {
            this.bufferedWriter.flush();
        }

        public void write(String json) throws IOException {
            this.bufferedWriter.write(json);
        }

        public void newLine() throws IOException {
            this.bufferedWriter.newLine();
        }
    }

    public EventLoggerThread(Gson gson, Clock clock, Filepath pluginPath) {
        this.gson = gson;
        this.clock = clock;
        this.logDirectory = pluginPath.join(LOG_DIRECTORY);
        this.lastSuccessfulSubmission = clock.instant();
    }

    private void closeLogFile() {
        if (logFile != null) {
            try {
                logFile.flush();
            } catch (IOException e) {
                log.warn("Failed to flush log file", e);
            }
            try {
                logFile.close();
            } catch (IOException e) {
                log.warn("Failed to close log file", e);
            }
            logFile = null;
        }
    }

    private void openLogFile(OpenLogCommand openLogCommand) {
        if (logFile != null) {
            closeLogFile();
        }

        for (int i = 0; i < OPEN_LOG_FILE_RETRY_ATTEMPTS; i++) {
            try {
                final var filepath = logDirectory.join(openLogCommand.getLogFileName());
                filepath.getParent().createDirectories();
                // Writes in UTF-8 format
                final var bufferedWriter = filepath.openBufferedWriter(
                        StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                logFile = new LogFile(filepath, bufferedWriter);
                return;
            } catch (IOException e) {
                log.error("Failed to create new log file", e);
            }
            // Opening a log file is critical, so we need to retry.
            try {
                // We're on the writer thread, so we won't be consuming any commands while sleeping.
                Thread.sleep(OPEN_LOG_FILE_RETRY_DELAY_SECONDS * 1000);
            } catch (InterruptedException ex) {
                return;
            }
        }
        // Critical failure: We can't open the log file, so kill the thread.
        killSwitch.set(true);
        commandQueue.clear();
        log.error(
                "Failed to open log file after {} attempts, closing writer thread. Plugin will need to be restarted to try again.",
                OPEN_LOG_FILE_RETRY_ATTEMPTS);
        Thread.currentThread().interrupt();
    }

    private void writeEvent(AnalyticsData.LogEntry logEntry) {
        if (logFile != null) {
            final var json = gson.toJson(logEntry);
            log.debug("{}", json);
            try {
                logFile.write(json);
                logFile.newLine();
            } catch (IOException e) {
                log.warn("Failed to write to log file", e);
            }
        } else {
            if (backloggedCommands.size() < MAX_BACKLOG) {
                backloggedCommands.add(new EventCommand(logEntry));
            }
        }
    }

    private void occasionalFlush() {
        if (nextFlush != null && nextFlush.isAfter(clock.instant())) {
            return;
        }
        nextFlush = clock.instant().plusSeconds(FLUSH_INTERVAL_SECONDS);

        if (logFile != null) {
            try {
                logFile.flush();
            } catch (IOException e) {
                log.warn("Failed to flush log file", e);
            }
        }
    }

    private void handleCommand(Command command) {
        if (command instanceof EventCommand) {
            final var eventCommand = (EventCommand) command;
            writeEvent(eventCommand.event);
            occasionalFlush();
        } else if (command instanceof OpenLogCommand) {
            final var openLogCommand = (OpenLogCommand) command;
            openLogFile(openLogCommand);
        }
    }

    // Client-thread methods.

    private void submitCommand(Command command) {
        if (!commandQueue.offer(command)) {
            if (Duration.between(lastSuccessfulSubmission, clock.instant()).toSeconds()
                    > KILL_AFTER_SECONDS_OF_SUBMISSION_FAILURES) {
                // It's unlikely the issue is a slow event consumer because the volume of events is small (few per
                // second at most).
                // So if we're hitting this, it's most likely a total failure of the event writer thread.
                log.error(
                        "Event logger command queue has remained full for over {} seconds. Event consumption has either stopped or is too slow and unlikely to recover. Killing event logger thread. Plugin will need to be restarted",
                        KILL_AFTER_SECONDS_OF_SUBMISSION_FAILURES);
                killSwitch.set(true);
            } else {
                // We want visibility if we start dropping messages. This will spam until we recover or the kill switch
                // condition above activates. It could theoretically spam forever if we occasionally have a successful
                // submission, but that only happens if we are producing events roughly at the same rate as we are
                // consuming them. We should not be producing events anywhere near that rate, so we're choosing not to
                // solve for that edge case.
                log.warn(
                        "Event logger command queue is full. Event consumption has either stopped or is too slow. Dropping event.");
            }
        } else {
            lastSuccessfulSubmission = clock.instant();
        }
    }

    private void drainBacklog() {
        Command command;
        while (logFile != null && (command = backloggedCommands.poll()) != null) {
            handleCommand(command);
        }
    }

    @Override
    public void run() {
        if (killSwitch.get()) {
            return;
        }

        while (!Thread.currentThread().isInterrupted() && !killSwitch.get()) {
            try {
                final var command = commandQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (command != null) {
                    handleCommand(command);
                }
                drainBacklog();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Command command;
        while ((command = commandQueue.poll()) != null) {
            handleCommand(command);
        }

        drainBacklog();

        closeLogFile();
    }

    public void submitOpenLog(String fileName) {
        if (killSwitch.get()) {
            return;
        }

        submitCommand(new OpenLogCommand(fileName));
    }

    /**
     * Submits an event to be written by the event logger thread.
     */
    public void submitEvent(AnalyticsData.LogEntry logEntry) {
        if (killSwitch.get()) {
            return;
        }

        submitCommand(new EventCommand(logEntry));
    }

    public boolean isKilled() {
        return killSwitch.get();
    }
}
