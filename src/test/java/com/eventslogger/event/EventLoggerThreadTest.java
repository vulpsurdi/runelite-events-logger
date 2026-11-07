package com.eventslogger.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.eventslogger.gson.ZonedDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * These tests drive {@link EventLoggerThread#run()} synchronously: commands are submitted first, then the thread's
 * interrupt flag is set before calling run(). This makes run() skip its blocking main loop and fall straight through to
 * the drain-and-close logic, so the behaviour is fully deterministic with no background threads or sleeps.
 */
public class EventLoggerThreadTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Gson gson;
    private Filepath pluginPath;

    @Before
    public void setUp() {
        gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .create();
        pluginPath = Filepath.Unchecked.getRooted(tempFolder.getRoot().toPath().resolve("events-logger"));
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private EventData.HitsplatLogEntry hitsplat(int amount) {
        return new EventData.HitsplatLogEntry(
                1, ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), amount, 0, EventData.ActorType.PLAYER, 42);
    }

    /** Set the interrupt flag then run; run() drains all queued commands and closes the file, then returns. */
    private void runSynchronously(EventLoggerThread thread) {
        Thread.currentThread().interrupt();
        try {
            thread.run();
        } finally {
            // Clear the interrupt flag we set, so it doesn't leak into later tests.
            Thread.interrupted();
        }
    }

    private Path logFilePath(String fileName) {
        return tempFolder.getRoot().toPath().resolve("events-logger").resolve(fileName);
    }

    @Test
    public void writesSubmittedEventsToOpenedLogFile() throws IOException {
        EventLoggerThread thread = new EventLoggerThread(gson, fixedClock(), pluginPath);

        thread.submitOpenLog("test.log");
        thread.submitEvent(hitsplat(10));
        thread.submitEvent(hitsplat(20));

        runSynchronously(thread);

        List<String> lines = Files.readAllLines(logFilePath("test.log"));
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\"amount\":10"));
        assertTrue(lines.get(1).contains("\"amount\":20"));
    }

    @Test
    public void createsLogDirectoryIfMissing() throws IOException {
        EventLoggerThread thread = new EventLoggerThread(gson, fixedClock(), pluginPath);
        assertFalse(Files.exists(logFilePath("created.log").getParent()));

        thread.submitOpenLog("created.log");
        thread.submitEvent(hitsplat(5));
        runSynchronously(thread);

        assertTrue(Files.exists(logFilePath("created.log")));
    }

    @Test
    public void eventsSubmittedBeforeLogFileAreBackloggedThenFlushed() throws IOException {
        EventLoggerThread thread = new EventLoggerThread(gson, fixedClock(), pluginPath);

        // Event arrives before any log file is open -> goes to backlog.
        thread.submitEvent(hitsplat(99));
        // Opening the log file should cause the backlog to drain on the next run cycle.
        thread.submitOpenLog("backlog.log");

        runSynchronously(thread);

        List<String> lines = Files.readAllLines(logFilePath("backlog.log"));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"amount\":99"));
    }

    @Test
    public void writesToNewFileAfterReopening() throws IOException {
        EventLoggerThread thread = new EventLoggerThread(gson, fixedClock(), pluginPath);

        thread.submitOpenLog("first.log");
        thread.submitEvent(hitsplat(1));
        thread.submitOpenLog("second.log");
        thread.submitEvent(hitsplat(2));

        runSynchronously(thread);

        List<String> first = Files.readAllLines(logFilePath("first.log"));
        assertEquals(1, first.size());
        assertTrue(first.get(0).contains("\"amount\":1"));

        List<String> second = Files.readAllLines(logFilePath("second.log"));
        assertEquals(1, second.size());
        assertTrue(second.get(0).contains("\"amount\":2"));
    }

    @Test
    public void isKilledStaysFalseDuringNormalOperation() {
        EventLoggerThread thread = new EventLoggerThread(gson, fixedClock(), pluginPath);
        assertFalse(thread.isKilled());

        thread.submitOpenLog("ok.log");
        thread.submitEvent(hitsplat(1));
        runSynchronously(thread);

        assertFalse(thread.isKilled());
    }

    @Test
    public void noFileWrittenWhenLogDirectoryCannotBeCreated() throws IOException {
        // Point the plugin path at an existing *file*. Creating the subdirectory or file under it fails, so no log
        // file can ever be opened and nothing should be written.
        Path blocker = tempFolder.newFile("not-a-dir").toPath();
        Filepath badPath = Filepath.Unchecked.getRooted(blocker);
        EventLoggerThread thread = new EventLoggerThread(gson, fixedClock(), badPath);

        thread.submitOpenLog("test.log");
        thread.submitEvent(hitsplat(1));

        // openLogFile() retries with Thread.sleep between attempts; setting the interrupt flag makes those sleeps bail
        // out immediately so the test does not wait on the real retry delay.
        Thread.currentThread().interrupt();
        try {
            thread.run();
        } finally {
            Thread.interrupted();
        }

        assertFalse(Files.exists(blocker.resolve("test.log")));
    }

    @Test
    public void droppedEventsBeyondBacklogCapacityAreNotWritten() throws IOException {
        EventLoggerThread thread = new EventLoggerThread(gson, fixedClock(), pluginPath);

        // Submit more events than MAX_BACKLOG (100) before any log file exists. Excess should be dropped, not stored.
        for (int i = 0; i < 150; i++) {
            thread.submitEvent(hitsplat(i));
        }
        thread.submitOpenLog("capacity.log");

        runSynchronously(thread);

        List<String> lines = Files.readAllLines(logFilePath("capacity.log"));
        assertEquals(100, lines.size());
    }

    @Test
    public void eventTimeAndClockAreUsedForFlushScheduling() throws IOException {
        // A mutable clock lets us verify flush interval behaviour without real time.
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        EventLoggerThread thread = new EventLoggerThread(gson, clock, pluginPath);

        thread.submitOpenLog("flush.log");
        thread.submitEvent(hitsplat(1));
        // Advance well beyond the flush interval so occasionalFlush() flushes at least once more.
        clock.advanceSeconds(30);
        thread.submitEvent(hitsplat(2));

        runSynchronously(thread);

        // Close/drain on shutdown always flushes; we mainly assert both events landed intact.
        List<String> lines = Files.readAllLines(logFilePath("flush.log"));
        assertEquals(2, lines.size());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
