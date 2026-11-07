package com.eventslogger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(EventsLoggerConfig.GROUP)
public interface EventsLoggerConfig extends Config {
    String GROUP = "eventslogger";

    @ConfigSection(position = 0, name = "Logging", description = "Logging settings")
    String loggingSection = "loggingSection";

    String LOG_OTHER_PLAYERS = "logOtherPlayers";

    @ConfigItem(
            keyName = LOG_OTHER_PLAYERS,
            name = "Log other players",
            description = "Logs all players visible to the client",
            warning = "This significantly increase log sizes especially in busy areas such as the Grand Exchange.",
            section = loggingSection,
            position = 0)
    default boolean logOtherPlayers() {
        return false;
    }
}
