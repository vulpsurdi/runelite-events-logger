package com.eventslogger.event;

import java.util.HashMap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Slf4j
public class NPCTracker {
    @Data
    private static class TrackedNPC {
        private final int npcIndex;
        private final int npcId;

        private EventData.@Nullable DetailedPosition position;

        public TrackedNPC(int npcIndex, int npcId) {
            this.npcIndex = npcIndex;
            this.npcId = npcId;
        }

        public static TrackedNPC zero() {
            return new TrackedNPC(0, 0);
        }
    }

    private final HashMap<Integer, TrackedNPC> trackedNpcs = new HashMap<>();

    private final Client client;

    private final EventLogger eventLogger;

    public NPCTracker(Client client, EventLogger eventLogger) {
        this.client = client;
        this.eventLogger = eventLogger;
    }

    public void onNpcSpawned(NpcSpawned event) {
        final var npc = event.getNpc();
        if (npc.getComposition().isFollower()) {
            return;
        }
        eventLogger.submitEvent(new EventData.NPCSpawnedLogEntry(
                eventLogger.tickCount(),
                eventLogger.eventTime(),
                npc.getIndex(),
                npc.getId(),
                npc.getName(),
                npc.getCombatLevel()));
    }

    public void onNpcDespawned(NpcDespawned event) {
        final var npc = event.getNpc();
        if (npc.getComposition().isFollower()) {
            return;
        }
        eventLogger.submitEvent(new EventData.NPCDespawnedLogEntry(
                eventLogger.tickCount(), eventLogger.eventTime(), npc.getIndex(), npc.getId()));
    }

    public void onNpcChanged(NpcChanged event) {
        final var npc = event.getNpc();
        if (event.getOld().isFollower()) {
            return;
        }
        eventLogger.submitEvent(new EventData.NPCChangedLogEntry(
                eventLogger.tickCount(),
                eventLogger.eventTime(),
                npc.getIndex(),
                event.getOld().getId(),
                npc.getId()));
    }

    private void trackNpcDiff(TrackedNPC oldTracked, TrackedNPC newTracked) {}

    private TrackedNPC buildNpcTracking(NPC npc) {
        final var detailedPosition =
                EventData.DetailedPosition.from(client, npc.getWorldLocation(), npc.getLocalLocation());
        final var trackedNPC = new TrackedNPC(npc.getIndex(), npc.getId());
        trackedNPC.setPosition(detailedPosition);
        return trackedNPC;
    }

    private void trackNpc(NPC npc) {
        if (npc.getComposition().isFollower()) {
            return;
        }
        final var trackedNpc = buildNpcTracking(npc);
        final var existingTracking = trackedNpcs.get(npc.getIndex());
        trackNpcDiff(existingTracking == null ? TrackedNPC.zero() : existingTracking, trackedNpc);
        trackedNpcs.put(npc.getIndex(), trackedNpc);
    }

    public void onGameTick() {
        final var npcs = client.getTopLevelWorldView().npcs();
        final var trackedIndices = trackedNpcs.keySet();
        for (final var npc : npcs) {
            trackNpc(npc);
            trackedIndices.remove(npc.getIndex());
        }

        trackedIndices.forEach(trackedNpcs::remove);
    }
}
