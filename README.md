# Runelite Events Logger

A RuneLite plugin that logs game events to a file locally. Nothing leaves your computer.

> [!NOTE]
> This plugin is a work-in-progress and this README describes features not yet implemented. This is not available on Plugin Hub yet.

## What does it do?

Save game events formatted in JSON to log files to `.runelite/plugin-data/events-logger/2026-10-31-08-38-17.log`

Examples of events:

* Chat
* Hitsplats
* Player and NPC positions
* Projectiles
* Animations
* Floor mechanics
* Prayers
* Gear
* Stat changes

Basically, if your game client is aware of it, we want to support logging it.

## Won't file sizes be a problem?

That depends on what you decide to log. Logging all player and NPC positions and chat at all times will definitely eat up your disk space. But you can toggle certain event logging on and off as needed.

The hope is to allow you to fine-tune the verbosity of the logging. For example, you might want to track more detailed information in a boss encounter compared to when you're doing agility laps.

Logs should also compress pretty well since it includes lots of repeated text. And you could configure the plugin to automatically delete logs after a certain amount of days.

## Motivation

Track now, analyze later. This plugin only does the tracking part. It captures data as you play that can be fed into tools to analyze it and display it. Here are some ideas of what you could do with the data:

- **General Analytics:** delta supplies used vs gp gained day-by-day, how many tiles have I traveled, which tiles do I step on the most.
- **Bossing analytics**: average damage you do or take at a boss, how often the boss used certain abilities, tick efficiency, speed, A/B test gear, setups, or strategies, how many supplies you use on average per kill, etc
- **Loot tracking**: keep track of everything you get. Runelite does this by default, but this gives you richer data since every individual loot event is logged alongside all the other logging context.
- **G.E. trades**: There are no shortage of G.E. tracker or flipping plugins so there's not as much reason to use this plugin for that, but you could if you want. You can also use this plugin to track it now and decide how you want to analyze it later.
- **Replays:** watch back your gameplay without having to record video. Great for learning hard content.
- **Chat logs:** never lose conversations you had or witnessed.

There's a lot of things you can do with the data. But first, you need to actually save the data.

## TODO

- Track more player data (names, hp, stats)
- Track npcs
- Track animations
- Track projectiles
- Track chat messages
  - by channel if possible
- Track party members
- Track equipment
- Track instance template chunks (for chambers layout)
- Add fine-tune options for each type of tracking.
- Compress log files when they are closed.
- Multilogging supports (save log files by username folders)
- Display log folder size
- Log lifecycle: delete after X days option.

## Ideate

- Consider a binary format. Gives us smaller file sizes but makes it harder for people to write tools to read it.
- Consider storing positions as deltas for better compression.
- Think through supporting tracking profiles so you can switch between tracking configurations based on the need (boss tracking vs skilling tracking vs questing tracking, etc.)
- Think through auto-triggers for tracking profiles so your boss tracking auto-enables when you're bossing.
