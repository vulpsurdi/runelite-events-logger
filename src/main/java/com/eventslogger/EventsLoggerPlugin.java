package com.eventslogger;

import com.eventslogger.event.EventData;
import com.eventslogger.event.EventLogger;
import com.eventslogger.event.HitsplatTracker;
import com.eventslogger.event.NPCTracker;
import com.eventslogger.event.PlayerTracker;
import com.eventslogger.gson.DetailedPositionAdapter;
import com.eventslogger.gson.ZonedDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Clock;
import java.time.ZonedDateTime;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.specialcounter.SpecialCounterUpdate;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Slf4j
@NullMarked
@PluginDescriptor(
        name = "Events Logger",
        internalName = "events-logger",
        description = "Logs game events to a local file",
        tags = {"events", "logger", "logging", "data", "combat"})
public class EventsLoggerPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ConfigManager configManager;

    @Nullable
    private Gson gson;

    @Nullable
    private NavigationButton navButton;

    @Nullable
    private Clock clock;

    @Nullable
    private String runeliteVersion;

    @Nullable
    private Filepath pluginDirectory;

    @Nullable
    private EventLogger eventLogger;

    @Nullable
    private PlayerTracker playerTracker;

    @Nullable
    private NPCTracker npcTracker;

    @Nullable
    private HitsplatTracker hitsplatTracker;

    @Provides
    EventsLoggerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EventsLoggerConfig.class);
    }

    @Override
    protected void startUp() {
        Panel panel = new Panel();
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/eventslogger/panel_icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Events Logger")
                .icon(icon)
                .priority(7)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);

        gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .registerTypeAdapter(EventData.DetailedPosition.class, new DetailedPositionAdapter())
                .create();
        clock = Clock.systemDefaultZone();

        final var config = configManager.getConfig(EventsLoggerConfig.class);

        runeliteVersion = "RuneLite/" + RuneLiteProperties.getVersion() + "-" + RuneLiteProperties.getCommit();
        try {
            pluginDirectory = this.getPluginDirectory();
        } catch (IOException e) {
            log.error("Failed to get path for plugin directory, aborting plugin initialization", e);
            shutDown();
            return;
        }

        eventLogger = new EventLogger(client, gson, clock, pluginDirectory, runeliteVersion);
        eventLogger.startUp();
        playerTracker = new PlayerTracker(client, eventLogger);
        playerTracker.onConfigChanged(config);
        npcTracker = new NPCTracker(client, eventLogger);
        hitsplatTracker = new HitsplatTracker(eventLogger);
    }

    @Override
    protected void shutDown() {
        hitsplatTracker = null;
        playerTracker = null;
        if (eventLogger != null) {
            eventLogger.shutDown();
        }
        eventLogger = null;
        pluginDirectory = null;
        runeliteVersion = null;

        clock = null;
        gson = null;

        clientToolbar.removeNavigation(navButton);
        navButton = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        final var config = configManager.getConfig(EventsLoggerConfig.class);
        if (playerTracker != null) {
            playerTracker.onConfigChanged(config);
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (hitsplatTracker != null) {
            hitsplatTracker.logHitsplat(event);
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {}

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {}

    @Subscribe
    public void onFakeXpDrop(FakeXpDrop event) {}

    @Subscribe
    public void onStatChanged(StatChanged event) {}

    @Subscribe
    public void onGraphicChanged(GraphicChanged event) {}

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        npcTracker.onNpcSpawned(event);
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event) {
        npcTracker.onNpcChanged(event);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        npcTracker.onNpcDespawned(event);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {}

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event) {}

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {}

    @Subscribe
    public void onChatMessage(ChatMessage event) {}

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {}

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (playerTracker != null) {
            playerTracker.trackPlayers();
        }
        if (eventLogger != null) {
            eventLogger.onGameTick();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (eventLogger != null) {
            eventLogger.submitEvent(new EventData.GameStateChangedLogEntry(
                    eventLogger.tickCount(),
                    eventLogger.eventTime(),
                    event.getGameState().getState()));
        }
    }

    @Subscribe
    public void onSpecialCounterUpdate(SpecialCounterUpdate event) {}

    @Subscribe
    public void onUserPart(UserPart event) {}
}
