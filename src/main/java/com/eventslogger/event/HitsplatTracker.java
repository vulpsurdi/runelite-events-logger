package com.eventslogger.event;

import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.HitsplatApplied;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class HitsplatTracker {

    private final EventLogger eventLogger;

    public HitsplatTracker(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    public void logHitsplat(HitsplatApplied hitsplatApplied) {
        // The actor is who got hit; we don't know the damage source.
        final var actor = hitsplatApplied.getActor();
        EventData.ActorType actorType = null;
        int actorId = 0;
        if (actor instanceof Player) {
            final var player = (Player) actor;
            actorType = EventData.ActorType.PLAYER;
            actorId = player.getId();
        } else if (actor instanceof NPC) {
            final var npc = (NPC) actor;
            actorType = EventData.ActorType.NPC;
            actorId = npc.getId();
        } else {
            actorType = EventData.ActorType.UNKNOWN;
            actorId = -1;
        }

        eventLogger.submitEvent(new EventData.HitsplatLogEntry(
                eventLogger.tickCount(),
                eventLogger.eventTime(),
                hitsplatApplied.getHitsplat().getAmount(),
                hitsplatApplied.getHitsplat().getHitsplatType(),
                actorType,
                actorId));
    }
}
