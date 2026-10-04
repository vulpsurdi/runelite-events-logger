package com.analyticsplugin.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.analyticsplugin.gson.ZonedDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EventLoggerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-01T12:34:56Z");

    private Client client;
    private Gson gson;
    private Clock clock;
    private Filepath pluginPath;

    @Before
    public void setUp() {
        client = mock(Client.class);
        gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .create();
        clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        pluginPath = Filepath.Unchecked.getRooted(tempFolder.getRoot().toPath());
    }

    private EventLogger newLogger() {
        return new EventLogger(client, gson, clock, pluginPath, "1.2.3");
    }

    @Test
    public void tickCountDelegatesToClient() {
        when(client.getTickCount()).thenReturn(777);

        assertEquals(777, newLogger().tickCount());
    }

    @Test
    public void eventTimeUsesClockInstantAndZone() {
        EventLogger logger = newLogger();

        ZonedDateTime time = logger.eventTime();

        assertEquals(FIXED_INSTANT, time.toInstant());
        assertEquals(ZoneOffset.UTC, time.getZone());
    }

    @Test
    public void eventTimeRespectsClockZone() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        clock = Clock.fixed(FIXED_INSTANT, tokyo);

        ZonedDateTime time = newLogger().eventTime();

        assertEquals(FIXED_INSTANT, time.toInstant());
        assertEquals(tokyo, time.getZone());
    }

    @Test
    public void constructorOpensLogFileAndQueuesNewSessionEntry() throws Exception {
        when(client.getTickCount()).thenReturn(5);

        // The constructor opens a log file and logs a NEW_SESSION entry; starting/stopping the writer thread flushes
        // everything to disk deterministically because shutDown() joins the thread.
        EventLogger logger = newLogger();
        logger.startUp();
        logger.shutDown();

        Path analyticsDir = tempFolder.getRoot().toPath().resolve("analytics");
        assertTrue("log directory should exist", Files.isDirectory(analyticsDir));

        Path logFile = findSingleLogFile(analyticsDir);
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"type\":\"NEW_SESSION\""));
        assertTrue(lines.get(0).contains("\"runeliteClientVersion\":\"1.2.3\""));
        assertTrue(lines.get(0).contains("\"tickCount\":5"));
    }

    @Test
    public void logNewSessionWritesAnEntry() throws Exception {
        when(client.getTickCount()).thenReturn(1);
        EventLogger logger = newLogger();
        logger.startUp();

        logger.logNewSession();
        logger.shutDown();

        Path logFile = findSingleLogFile(tempFolder.getRoot().toPath().resolve("analytics"));
        List<String> lines = Files.readAllLines(logFile);
        // One NEW_SESSION from the constructor, one from the explicit call.
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\"type\":\"NEW_SESSION\""));
        assertTrue(lines.get(1).contains("\"type\":\"NEW_SESSION\""));
    }

    @Test
    public void submitEventIsWrittenAfterStartUp() throws Exception {
        when(client.getTickCount()).thenReturn(3);
        EventLogger logger = newLogger();
        logger.startUp();

        logger.submitEvent(
                new AnalyticsData.HitsplatLogEntry(3, logger.eventTime(), 15, 0, AnalyticsData.ActorType.NPC, 2000));
        logger.shutDown();

        Path logFile = findSingleLogFile(tempFolder.getRoot().toPath().resolve("analytics"));
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size()); // NEW_SESSION + the hitsplat
        assertTrue(lines.get(1).contains("\"type\":\"HITSPLAT\""));
        assertTrue(lines.get(1).contains("\"amount\":15"));
    }

    @Test
    public void shutDownIsSafeWhenNotStarted() {
        EventLogger logger = newLogger();

        // Should not throw even though startUp() was never called.
        logger.shutDown();
    }

    @Test
    public void onGameTickDoesNothingWhenThreadIsHealthy() {
        EventLogger logger = newLogger();
        logger.startUp();

        // The writer thread is alive and not killed, so onGameTick should be a no-op (no exception, thread keeps
        // running).
        logger.onGameTick();

        logger.shutDown();
    }

    private Path findSingleLogFile(Path analyticsDir) throws IOException {
        try (var stream = Files.list(analyticsDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).collect(java.util.stream.Collectors.toList());
            assertEquals("expected exactly one log file", 1, files.size());
            return files.get(0);
        }
    }
}
