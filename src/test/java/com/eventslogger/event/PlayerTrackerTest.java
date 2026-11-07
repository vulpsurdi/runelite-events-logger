package com.eventslogger.event;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventslogger.EventsLoggerConfig;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class PlayerTrackerTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private Client client;
    private EventLogger eventLogger;
    private PlayerTracker tracker;
    private MockedStatic<net.runelite.api.coords.WorldPoint> worldPointStatic;

    @Before
    public void setUp() {
        client = mock(Client.class);
        eventLogger = mock(EventLogger.class);
        when(eventLogger.tickCount()).thenReturn(1);
        when(eventLogger.eventTime()).thenReturn(NOW);

        // DetailedPosition.from queries the world view and a static converter.
        WorldView worldView = mock(WorldView.class);
        when(worldView.isInstance()).thenReturn(false);
        when(client.getWorldView(org.mockito.ArgumentMatchers.anyInt())).thenReturn(worldView);

        worldPointStatic = org.mockito.Mockito.mockStatic(net.runelite.api.coords.WorldPoint.class);
        worldPointStatic
                .when(() -> net.runelite.api.coords.WorldPoint.fromLocalInstance(
                        any(Client.class), any(net.runelite.api.coords.LocalPoint.class)))
                .thenReturn(null);

        tracker = new PlayerTracker(client, eventLogger);
    }

    @org.junit.After
    public void tearDown() {
        worldPointStatic.close();
    }

    private Player player(int id, String name, int x, int y) {
        Player player = mock(Player.class);
        when(player.getId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(x, y, 0));
        when(player.getLocalLocation()).thenReturn(new net.runelite.api.coords.LocalPoint(x, y, 0));
        return player;
    }

    @Test
    public void doesNotTrackOtherPlayersByDefault() {
        Player local = player(1, "Me", 0, 0);
        Player other = player(2, "Other", 10, 10);
        when(client.getLocalPlayer()).thenReturn(local);

        boolean tracked = tracker.trackPlayer(other);

        assertFalse(tracked);
        verify(eventLogger, never()).submitEvent(any());
    }

    @Test
    public void tracksLocalPlayerByDefault() {
        Player local = player(1, "Me", 5, 5);
        when(client.getLocalPlayer()).thenReturn(local);

        boolean tracked = tracker.trackPlayer(local);

        assertTrue(tracked);
        // Expect a start-tracking entry and an initial position entry.
        verify(eventLogger, times(2)).submitEvent(any());
    }

    @Test
    public void startTrackingEntryMarksLocalPlayer() {
        Player local = player(7, "Me", 5, 5);
        when(client.getLocalPlayer()).thenReturn(local);

        tracker.trackPlayer(local);

        ArgumentCaptor<EventData.LogEntry> captor = ArgumentCaptor.forClass(EventData.LogEntry.class);
        verify(eventLogger, times(2)).submitEvent(captor.capture());

        EventData.PlayerStartTrackingLogEntry start =
                (EventData.PlayerStartTrackingLogEntry) captor.getAllValues().get(0);
        assertTrue(start.isLocal());
        org.junit.Assert.assertEquals(7, start.getPlayerId());
        org.junit.Assert.assertEquals("Me", start.getPlayerName());
    }

    @Test
    public void tracksOtherPlayersWhenEnabled() {
        tracker.setTrackOtherPlayers(true);
        Player local = player(1, "Me", 0, 0);
        Player other = player(2, "Other", 10, 10);
        when(client.getLocalPlayer()).thenReturn(local);

        boolean tracked = tracker.trackPlayer(other);

        assertTrue(tracked);
        verify(eventLogger, times(2)).submitEvent(any());
    }

    @Test
    public void onConfigChangedUpdatesFlag() {
        EventsLoggerConfig config = mock(EventsLoggerConfig.class);
        when(config.logOtherPlayers()).thenReturn(true);
        Player local = player(1, "Me", 0, 0);
        Player other = player(2, "Other", 10, 10);
        when(client.getLocalPlayer()).thenReturn(local);

        tracker.onConfigChanged(config);

        assertTrue(tracker.trackPlayer(other));
    }

    @Test
    public void doesNotReLogSamePositionOnSecondTrack() {
        Player local = player(1, "Me", 5, 5);
        when(client.getLocalPlayer()).thenReturn(local);

        tracker.trackPlayer(local); // start + position = 2
        tracker.trackPlayer(local); // same position = no new events

        verify(eventLogger, times(2)).submitEvent(any());
    }

    @Test
    public void logsNewPositionWhenPlayerMoves() {
        Player local = player(1, "Me", 5, 5);
        when(client.getLocalPlayer()).thenReturn(local);
        tracker.trackPlayer(local); // start + position = 2

        Player moved = player(1, "Me", 9, 9);
        tracker.trackPlayer(moved); // moved = 1 more position event

        ArgumentCaptor<EventData.LogEntry> captor = ArgumentCaptor.forClass(EventData.LogEntry.class);
        verify(eventLogger, times(3)).submitEvent(captor.capture());
        assertTrue(captor.getAllValues().get(2) instanceof EventData.PlayerMoveLogEntry);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void trackPlayersLogsStopWhenPlayerDisappears() {
        Player local = player(1, "Me", 5, 5);
        when(client.getLocalPlayer()).thenReturn(local);

        WorldView topLevel = mock(WorldView.class);
        when(client.getTopLevelWorldView()).thenReturn(topLevel);

        // First tick: local player is present.
        org.mockito.Mockito.doReturn(indexedObjectSet(local)).when(topLevel).players();
        tracker.trackPlayers();

        // Second tick: no players present -> stop-tracking should be logged.
        org.mockito.Mockito.doReturn(indexedObjectSet()).when(topLevel).players();
        tracker.trackPlayers();

        ArgumentCaptor<EventData.LogEntry> captor = ArgumentCaptor.forClass(EventData.LogEntry.class);
        verify(eventLogger, times(3)).submitEvent(captor.capture());
        EventData.LogEntry last = captor.getAllValues().get(2);
        assertTrue(last instanceof EventData.PlayerStopTrackingLogEntry);
        org.junit.Assert.assertEquals(1, ((EventData.PlayerStopTrackingLogEntry) last).getPlayerId());
    }

    @SuppressWarnings("unchecked")
    private IndexedObjectSet<Player> indexedObjectSet(Player... players) {
        List<Player> list = Arrays.asList(players);
        IndexedObjectSet<Player> set = mock(IndexedObjectSet.class);
        when(set.iterator()).thenAnswer(invocation -> list.iterator());
        return set;
    }
}
