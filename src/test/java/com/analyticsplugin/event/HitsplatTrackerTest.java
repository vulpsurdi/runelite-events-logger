package com.analyticsplugin.event;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.HitsplatApplied;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class HitsplatTrackerTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private EventLogger eventLogger;
    private HitsplatTracker tracker;

    @Before
    public void setUp() {
        eventLogger = mock(EventLogger.class);
        when(eventLogger.tickCount()).thenReturn(42);
        when(eventLogger.eventTime()).thenReturn(NOW);
        tracker = new HitsplatTracker(eventLogger);
    }

    private HitsplatApplied hitsplatApplied(Actor actor, int amount, int hitsplatType) {
        Hitsplat hitsplat = mock(Hitsplat.class);
        when(hitsplat.getAmount()).thenReturn(amount);
        when(hitsplat.getHitsplatType()).thenReturn(hitsplatType);

        HitsplatApplied event = mock(HitsplatApplied.class);
        when(event.getActor()).thenReturn(actor);
        when(event.getHitsplat()).thenReturn(hitsplat);
        return event;
    }

    private AnalyticsData.HitsplatLogEntry capture() {
        ArgumentCaptor<AnalyticsData.LogEntry> captor = ArgumentCaptor.forClass(AnalyticsData.LogEntry.class);
        verify(eventLogger).submitEvent(captor.capture());
        return (AnalyticsData.HitsplatLogEntry) captor.getValue();
    }

    @Test
    public void classifiesPlayerActor() {
        Player player = mock(Player.class);
        when(player.getId()).thenReturn(123);

        tracker.logHitsplat(hitsplatApplied(player, 7, 1));

        AnalyticsData.HitsplatLogEntry entry = capture();
        assertEquals(AnalyticsData.ActorType.PLAYER, entry.getActorType());
        assertEquals(123, entry.getActorId());
        assertEquals(7, entry.getAmount());
        assertEquals(1, entry.getHitsplatType());
        assertEquals(42, entry.getTickCount());
        assertEquals(NOW, entry.getTimestamp());
        assertEquals(AnalyticsData.LogEntryType.HITSPLAT, entry.getType());
    }

    @Test
    public void classifiesNpcActor() {
        NPC npc = mock(NPC.class);
        when(npc.getId()).thenReturn(456);

        tracker.logHitsplat(hitsplatApplied(npc, 10, 2));

        AnalyticsData.HitsplatLogEntry entry = capture();
        assertEquals(AnalyticsData.ActorType.NPC, entry.getActorType());
        assertEquals(456, entry.getActorId());
        assertEquals(10, entry.getAmount());
    }

    @Test
    public void classifiesUnknownActor() {
        Actor actor = mock(Actor.class);

        tracker.logHitsplat(hitsplatApplied(actor, 0, 0));

        AnalyticsData.HitsplatLogEntry entry = capture();
        assertEquals(AnalyticsData.ActorType.UNKNOWN, entry.getActorType());
        assertEquals(-1, entry.getActorId());
    }
}
