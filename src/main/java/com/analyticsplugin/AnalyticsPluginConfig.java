package com.analyticsplugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(AnalyticsPluginConfig.GROUP)
public interface AnalyticsPluginConfig extends Config {
    String GROUP = "analyticsplugin";

    @ConfigSection(
			position = 0,
			name = "Logging",
			description = "Logging settings"
	)
	String loggingSection = "loggingSection";

    public static final String LOG_OTHER_PLAYERS = "logOtherPlayers";
    @ConfigItem(
			keyName = LOG_OTHER_PLAYERS,
			name = "Log other non-party players.",
			description = "Logs all players visible to the client.",
			warning = "This significantly increase log sizes especially in busy areas such as the Grand Exchange.",
			section = loggingSection,
			position = 0
	)
	default boolean logOtherPlayers()
	{
		return false;
	}
}
