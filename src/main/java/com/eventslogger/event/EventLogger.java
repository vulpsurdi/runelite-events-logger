package com.eventslogger.event;

import com.google.gson.Gson;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.util.Filepath;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Key class for submitting events to be logged.
 * <p>
 * This class is also responsible for managing the event writer thread.
 */
@NullMarked
@Slf4j
public class EventLogger {

    private final Client client;

    private final Clock clock;

    private final String runeliteVersion;

    @Nullable
    private Thread writerThread;

    private final EventLoggerThread eventLoggerThread;

    public EventLogger(Client client, Gson gson, Clock clock, Filepath pluginPath, String runeliteVersion) {
        this.client = client;
        this.clock = clock;
        this.runeliteVersion = runeliteVersion;
        this.eventLoggerThread = new EventLoggerThread(gson, clock, pluginPath);
        openNewLogFile();
    }

    public int tickCount() {
        return client.getTickCount();
    }

    public ZonedDateTime eventTime() {
        return clock.instant().atZone(clock.getZone());
    }

    public void submitEvent(EventData.LogEntry logEntry) {
        eventLoggerThread.submitEvent(logEntry);
    }

    public void logNewSession() {
        submitEvent(new EventData.NewSessionLogEntry(tickCount(), eventTime(), runeliteVersion));
    }

    private void openNewLogFile() {
        ZonedDateTime now = clock.instant().atZone(clock.getZone());
        String formattedTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        this.eventLoggerThread.submitOpenLog(formattedTime + ".log");
        logNewSession();
    }

    public void onGameTick() {
        if (eventLoggerThread.isKilled() && writerThread != null && writerThread.isAlive()) {
            writerThread.interrupt();
            // No join since we don't want to block the client thread.
            writerThread = null;
        }
    }

    public void startUp() {
        writerThread = new Thread(eventLoggerThread, "events-logger-writer-thread");
        // Don't block the client from closing.
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public void shutDown() {
        if (writerThread != null && writerThread.isAlive()) {
            writerThread.interrupt();
            try {
                writerThread.join(10_000);
            } catch (InterruptedException e) {
                log.error("Failed to interrupt events writer thread", e);
            }
            writerThread = null;
        }
    }
}
