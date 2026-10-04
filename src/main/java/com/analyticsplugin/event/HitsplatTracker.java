package com.analyticsplugin.event;

import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.HitsplatApplied;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class HitsplatTracker {

    public EventLogger eventLogger;

    public HitsplatTracker(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    public void logHitsplat(HitsplatApplied hitsplatApplied) {
        // The actor is who got hit; we don't know the damage source.
        final var actor = hitsplatApplied.getActor();
        AnalyticsData.ActorType actorType = null;
        int actorId = 0;
        if (actor instanceof Player) {
            final var player = (Player) actor;
            actorType = AnalyticsData.ActorType.PLAYER;
            actorId = player.getId();
        } else if (actor instanceof NPC) {
            final var npc = (NPC) actor;
            actorType = AnalyticsData.ActorType.NPC;
            actorId = npc.getId();
        } else {
            actorType = AnalyticsData.ActorType.UNKNOWN;
            actorId = -1;
        }

        eventLogger.logEvent(new AnalyticsData.HitsplatLogEntry(
                eventLogger.tickCount(),
                eventLogger.eventTime(),
                hitsplatApplied.getHitsplat().getAmount(),
                hitsplatApplied.getHitsplat().getHitsplatType(),
                actorType,
                actorId));
    }
}
