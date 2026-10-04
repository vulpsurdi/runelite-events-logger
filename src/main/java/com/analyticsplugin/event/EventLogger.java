package com.analyticsplugin.event;

import com.google.gson.Gson;

import java.io.*;
import java.nio.file.StandardOpenOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.util.Filepath;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Slf4j
public class EventLogger {

    private static final String LOG_DIRECTORY = "analytics";
    private static final int RETRY_DELAY_SECONDS = 10;
    private static final int FLUSH_INTERVAL_SECONDS = 10;

    private final Client client;

    private final Gson gson;

    private final Clock clock;

    private final String runeliteVersion;

    private final Filepath logDirectory;

    @Nullable
    private LogFile logFile;

    @Nullable
    private Instant nextRetry;

    @Nullable
    private Instant nextFlush;

    @Value
    private static class LogFile implements Flushable, Closeable {
        LocalDate dateUtc;
        String playerName;
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
    }

    public EventLogger(Client client, Gson gson, Clock clock, Filepath pluginPath, String runeliteVersion) {
        this.client = client;
        this.gson = gson;
        this.clock = clock;
        this.runeliteVersion = runeliteVersion;
        this.logDirectory = pluginPath.join(LOG_DIRECTORY);
    }

    public int tickCount() {
        return client.getTickCount();
    }

    public ZonedDateTime eventTime() {
        return clock.instant().atZone(clock.getZone());
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

    public void logEvent(AnalyticsData.LogEntry logEntry) {
        final var json = gson.toJson(logEntry);
        log.debug("{}", json);
        if (logFile != null) {
            try {
                logFile.write(json);
            } catch (IOException e) {
                log.warn("Failed to write to log file", e);
            }
        }
    }

    public void logNewSession() {
        logEvent(new AnalyticsData.NewSessionLogEntry(tickCount(), eventTime(), runeliteVersion));
    }

    private void occasionalFlush() {
        if (logFile == null) {
            return;
        }

        if (nextFlush != null && nextFlush.isAfter(clock.instant())) {
            return;
        }
        nextFlush = clock.instant().plusSeconds(FLUSH_INTERVAL_SECONDS);

        try {
            logFile.flush();
        } catch (IOException e) {
            log.warn("Failed to flush log file", e);
        }
    }

    private void checkLogFile() {
        if (nextRetry != null && nextRetry.isAfter(clock.instant())) {
            return;
        }

        final var localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return;
        }

        final var playerName = localPlayer.getName();
        if (playerName == null) {
            return;
        }

        final var dateUtc = clock.instant().atZone(ZoneId.of("UTC")).toLocalDate();

        if (logFile == null || !logFile.playerName.equals(playerName) || !Objects.equals(dateUtc, logFile.dateUtc)) {
            if (logFile != null) {
                closeLogFile();
            }

            try {
                final var formattedDate = dateUtc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                final var filepath = logDirectory.join(playerName, formattedDate + ".log");
                filepath.getParent().createDirectories();
                // Writes in UTF-8 format
                final var bufferedWriter = filepath.openBufferedWriter(StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                logFile = new LogFile(dateUtc, playerName, filepath, bufferedWriter);
            } catch (IOException e) {
                log.error("Failed to create new log file", e);
                nextRetry = clock.instant().plusSeconds(RETRY_DELAY_SECONDS);
            }

            logNewSession();
        }
    }

    public void onGameTicket() {
        occasionalFlush();
        checkLogFile();
    }

    public void shutDown() {
        closeLogFile();
    }

}
