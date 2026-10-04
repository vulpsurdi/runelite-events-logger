package com.analyticsplugin.event;

import com.analyticsplugin.AnalyticsPluginConfig;
import java.util.HashMap;
import java.util.HashSet;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
@Slf4j
public class PlayerTracker {

    @Data
    private static class TrackedPlayer {
        private final int playerId;
        private AnalyticsData.DetailedPosition position;

        public TrackedPlayer(int playerId) {
            this.playerId = playerId;
        }
    }

    private final HashMap<Integer, TrackedPlayer> trackedPlayers = new HashMap<>();

    private final Client client;

    private final EventLogger eventLogger;

    @Setter
    private boolean trackOtherPlayers = false;

    public PlayerTracker(Client client, EventLogger eventLogger) {
        this.client = client;
        this.eventLogger = eventLogger;
    }

    public void onConfigChanged(AnalyticsPluginConfig config) {
        trackOtherPlayers = config.logOtherPlayers();
    }

    /**
     * Log initial information about a player, things that likely don't change
     * in a game session.
     */
    private void logPlayerStartTracking(Player player) {
        final var localPlayer = client.getLocalPlayer();
        final boolean isLocal = localPlayer != null && localPlayer.getId() == player.getId();
        eventLogger.logEvent(new AnalyticsData.PlayerStartTrackingLogEntry(
                eventLogger.tickCount(), eventLogger.eventTime(), player.getId(), player.getName(), isLocal));
    }

    /**
     * Player is no longer tracked, likely because they are no longer visible to us.
     */
    private void logPlayerStopTracking(int playerId) {
        eventLogger.logEvent(new AnalyticsData.PlayerStopTrackingLogEntry(
                eventLogger.tickCount(), eventLogger.eventTime(), playerId));
    }

    private void logPlayerPosition(int playerId, AnalyticsData.DetailedPosition detailedPosition) {
        eventLogger.logEvent(new AnalyticsData.PlayerMoveLogEntry(
                eventLogger.tickCount(), eventLogger.eventTime(), playerId, detailedPosition));
    }

    /**
     * Begin or update tracking for a player. Return true if the player is being tracked, false if not.
     */
    public boolean trackPlayer(Player player) {
        final var detailedPosition =
                AnalyticsData.DetailedPosition.from(client, player.getWorldLocation(), player.getLocalLocation());
        var trackedPlayer = trackedPlayers.get(player.getId());
        final var localPlayer = client.getLocalPlayer();
        final boolean isLocal = localPlayer != null && localPlayer.getId() == player.getId();
        if (!isLocal && !trackOtherPlayers) {
            return false;
        }
        if (trackedPlayer == null) {
            logPlayerStartTracking(player);
            logPlayerPosition(player.getId(), detailedPosition);
            trackedPlayer = new TrackedPlayer(player.getId());
            trackedPlayer.setPosition(detailedPosition);
            trackedPlayers.put(player.getId(), trackedPlayer);
        } else if (!trackedPlayer.position.equals(detailedPosition)) {
            trackedPlayer.setPosition(detailedPosition);
            logPlayerPosition(player.getId(), detailedPosition);
        }
        return true;
    }

    public void trackPlayers() {
        final var trackedPlayerSet = new HashSet<>(trackedPlayers.keySet());
        // For now just do top-level world view. If we need it in the future,
        // we can add tracking for players beyond the top-level.
        for (var player : client.getTopLevelWorldView().players()) {
            if (trackPlayer(player)) {
                trackedPlayerSet.remove(player.getId());
            }
        }
        trackedPlayerSet.forEach(this::logPlayerStopTracking);
    }
}
