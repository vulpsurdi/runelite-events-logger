package com.eventslogger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class EventsLoggerPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(EventsLoggerPlugin.class);
        RuneLite.main(args);
    }
}
