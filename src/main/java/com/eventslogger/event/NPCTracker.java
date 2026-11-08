package com.eventslogger.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

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

        public static TrackedNPC blank() {
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

    private void trackNpcDiff(TrackedNPC oldTracked, TrackedNPC newTracked, TickSnapshot.NPCSnapshot snapshot) {
        if (!Objects.equals(oldTracked.getPosition(), newTracked.getPosition()) && newTracked.getPosition() != null) {
            if (oldTracked.getPosition() == null) {
                snapshot.positionAbsolutes.put("" + newTracked.npcIndex, newTracked.getPosition());
            } else {
                snapshot.positionDeltas.put("" + newTracked.npcIndex, oldTracked.getPosition().diff(newTracked.getPosition()));
            }
        }
    }

    private TrackedNPC buildNpcTracking(NPC npc) {
        final var detailedPosition =
                EventData.DetailedPosition.from(client, npc.getWorldLocation(), npc.getLocalLocation());
        final var trackedNPC = new TrackedNPC(npc.getIndex(), npc.getId());
        trackedNPC.setPosition(detailedPosition);
        return trackedNPC;
    }

    private void trackNpc(NPC npc, TickSnapshot.NPCSnapshot snapshot) {
        if (npc.getComposition().isFollower()) {
            return;
        }
        final var trackedNpc = buildNpcTracking(npc);
        final var existingTracking = trackedNpcs.get(npc.getIndex());
        trackNpcDiff(existingTracking == null ? TrackedNPC.blank() : existingTracking, trackedNpc, snapshot);
        trackedNpcs.put(npc.getIndex(), trackedNpc);
    }

    public TickSnapshot.@Nullable NPCSnapshot onGameTick() {
        final var snapshot = new TickSnapshot.NPCSnapshot();
        final var trackedIndices = new HashSet<>(trackedNpcs.keySet());
        // For now just do top-level world view. If we need it in the future,
        // we can add tracking for players beyond the top-level.
        for (var npc : client.getTopLevelWorldView().npcs()) {
            trackNpc(npc, snapshot);
            trackedIndices.remove(npc.getIndex());
        }
        trackedIndices.forEach(trackedNpcs::remove);

        if (snapshot.isEmpty()) {
            return null;
        } else {
            return snapshot;
        }
    }
}
