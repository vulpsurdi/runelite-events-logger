package com.analyticsplugin.event;

import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.api.Client;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class AnalyticsData {

    public static String LOG_VERSION = "1.0.0";

    public enum LogEntryType {
        NEW_SESSION,
        HITSPLAT,
        PLAYER_START_TRACKING,
        PLAYER_STOP_TRACKING,
        PLAYER_MOVE,
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
    public static class PlayerMoveLogEntry implements LogEntry {
        LogEntryType type = LogEntryType.PLAYER_MOVE;
        int tickCount;
        ZonedDateTime timestamp;
        int playerId;
        DetailedPosition detailedPosition;
    }

    @Data
    @AllArgsConstructor
    public static class WorldPoint {
        int x;
        int y;
        int plane;
        int regionId;

        public static WorldPoint from(net.runelite.api.coords.WorldPoint worldPoint) {
            return new WorldPoint(worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane(), worldPoint.getRegionID());
        }
    }

    @Data
    @AllArgsConstructor
    public static class LocalPoint {
        /// 1/128th of a tile
        int x;
        /// 1/128th of a tile
        int y;
        int worldView;

        public static LocalPoint from(net.runelite.api.coords.LocalPoint localPoint) {
            return new LocalPoint(localPoint.getX(), localPoint.getY(), localPoint.getWorldView());
        }
    }

    @Data
    @EqualsAndHashCode
    @AllArgsConstructor
    public static class DetailedPosition {
        /// This isn't a very useful position inside an instance.
        WorldPoint worldPoint;
        /// Sub-tile position, potentially within an instance.
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
            final var detailedPosition =
                    new AnalyticsData.DetailedPosition(WorldPoint.from(worldPoint), LocalPoint.from(localPoint), false, null);

            final var worldView = client.getWorldView(localPoint.getWorldView());
            detailedPosition.isInstance = worldView.isInstance();

            final var convertedWorldLocation = net.runelite.api.coords.WorldPoint.fromLocalInstance(client, localPoint);
            if (convertedWorldLocation != null) {
                detailedPosition.realWorldPoint = WorldPoint.from(convertedWorldLocation);
            }
            return detailedPosition;
        }
    }
}
