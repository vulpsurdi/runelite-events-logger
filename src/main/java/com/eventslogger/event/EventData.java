package com.eventslogger.event;

import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.api.Client;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class EventData {

    private static final String LOG_VERSION = "1.0.0";

    public enum LogEntryType {
        NEW_SESSION,
        HITSPLAT,
        PLAYER_START_TRACKING,
        PLAYER_STOP_TRACKING,
        PLAYER_MOVE,
        GAME_STATE_CHANGED,
        NPC_SPAWNED,
        NPC_DESPAWNED,
        NPC_CHANGED,
        NPC_MOVE,
        TICK_SNAPSHOT,
    }

    public interface LogEntry {
        LogEntryType getType();

        int getTickCount();

        ZonedDateTime getTimestamp();
    }

    @Value
    public static class NewSessionLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.NEW_SESSION;
        int tickCount;
        ZonedDateTime timestamp;
        String logVersion = LOG_VERSION;
        String runeliteClientVersion;
    }

    public enum ActorType {
        PLAYER,
        NPC,
        UNKNOWN,
    }

    @Value
    public static class HitsplatLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.HITSPLAT;
        int tickCount;
        ZonedDateTime timestamp;
        int amount;
        /// @see net.runelite.api.HitsplatID
        int hitsplatType;
        ActorType actorType;
        int actorId;
    }

    @Value
    public static class PlayerStartTrackingLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.PLAYER_START_TRACKING;
        int tickCount;
        ZonedDateTime timestamp;
        int playerId;

        @Nullable
        String playerName;

        boolean isLocal;
    }

    @Value
    public static class PlayerStopTrackingLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.PLAYER_STOP_TRACKING;
        int tickCount;
        ZonedDateTime timestamp;
        int playerId;
    }

    @Value
    public static class GameStateChangedLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.GAME_STATE_CHANGED;
        int tickCount;
        ZonedDateTime timestamp;
        int gameState;
    }

    @Value
    public static class NPCSpawnedLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.NPC_SPAWNED;
        int tickCount;
        ZonedDateTime timestamp;
        int index;
        int npcId;

        @Nullable
        String name;

        int combatLevel;
    }

    @Value
    public static class NPCDespawnedLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.NPC_DESPAWNED;
        int tickCount;
        ZonedDateTime timestamp;
        int index;
        int npcId;
    }

    @Value
    public static class NPCChangedLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.NPC_CHANGED;
        int tickCount;
        ZonedDateTime timestamp;
        int npcIndex;
        int oldId;
        int newId;
    }

    @Value
    public static class TickSnapshotLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.TICK_SNAPSHOT;
        int tickCount;
        ZonedDateTime timestamp;
        TickSnapshot tickSnapshot;
    }

    @Data
    @AllArgsConstructor
    public static class WorldPoint {
        int x;
        int y;
        int plane;
        int regionId;

        public static WorldPoint from(net.runelite.api.coords.WorldPoint worldPoint) {
            return new WorldPoint(
                    worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane(), worldPoint.getRegionID());
        }

        /**
         * Produces a WorldPoint capturing the difference between this and other such that
         * this + delta = other.
         */
        public WorldPoint diff(WorldPoint other) {
            return new WorldPoint(
                    other.x - this.x,
                    other.y - this.y,
                    other.plane - this.plane,
                    other.regionId - this.regionId
            );
        }
    }

    @Data
    @AllArgsConstructor
    public static class LocalPoint {
        /// In scene space
        int x;
        /// In scene space
        int y;
        int worldView;

        public static LocalPoint from(net.runelite.api.coords.LocalPoint localPoint) {
            return new LocalPoint(localPoint.getSceneX(), localPoint.getSceneY(), localPoint.getWorldView());
        }

        /**
         * Produces a LocalPoint capturing the difference between this and other such that
         * this + delta = other.
         */
        public LocalPoint diff(LocalPoint other) {
            return new LocalPoint(
                    other.x - this.x,
                    other.y - this.y,
                    other.worldView - this.worldView
            );
        }
    }

    @Data
    @EqualsAndHashCode
    @AllArgsConstructor
    public static class DetailedPosition {
        /// This isn't a very useful position inside an instance.
        WorldPoint worldPoint;
        /// Position relative to the scene. This is the visual location,
        /// not true-tile, so it will lag behind worldPoint and realWorldPoint
        /// by about a tick.
        LocalPoint localPoint;
        boolean isInstance;
        /// Converted from localPoint using the worldView. This tells
        /// us the real-world location that the instance position corresponds to.
        @Nullable
        WorldPoint realWorldPoint;

        public static DetailedPosition from(
                Client client,
                net.runelite.api.coords.WorldPoint worldPoint,
                net.runelite.api.coords.LocalPoint localPoint) {
            final var detailedPosition = new EventData.DetailedPosition(
                    WorldPoint.from(worldPoint), LocalPoint.from(localPoint), false, null);

            final var worldView = client.getWorldView(localPoint.getWorldView());
            detailedPosition.isInstance = worldView.isInstance();

            if (worldView.isInstance()) {
                final var tileLocal = net.runelite.api.coords.LocalPoint.fromWorld(worldView, worldPoint);
                if (tileLocal != null) {
                    final var converted = net.runelite.api.coords.WorldPoint.fromLocalInstance(client, tileLocal);
                    if (converted != null) {
                        detailedPosition.realWorldPoint = WorldPoint.from(converted);
                    }
                }
            } else {
                detailedPosition.realWorldPoint = detailedPosition.worldPoint;
            }
            return detailedPosition;
        }

        /**
         * Produces a DetailedPosition capturing the difference between this and other such that
         * this + delta = other.
         * <p>
         * `isInstance` will be true if the boolean flipped, false if it stayed the same.
         * <p>
         * realWorldPoint's nullability will reflect `other`'s nullability. If other's real world point
         * is null, the diff's is null. If other's real world point is non-null but this real world point
         * is null, the delta will simply be the other's real world point.
         */
        public DetailedPosition diff(DetailedPosition other) {
            WorldPoint realWorldPointDiff = null;
            if (other.realWorldPoint != null) {
                if (this.realWorldPoint != null) {
                    realWorldPointDiff = this.realWorldPoint.diff(other.realWorldPoint);
                } else {
                    realWorldPointDiff = other.realWorldPoint;
                }
            }
            return new DetailedPosition(
                    this.worldPoint.diff(other.worldPoint),
                    this.localPoint.diff(other.localPoint),
                    this.isInstance != other.isInstance,
                    realWorldPointDiff
            );
        }
    }
}
