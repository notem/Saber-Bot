### ALERT: Development on Saber-bot has been halted. Instead, I'll be working the [G4M3R discord bot](https://github.com/pedall/G4M3R) for the time being. However, I will continue to provide hosting for the bot.

### Current Issues

+ NULL pointer exception encountered in some instances of calendar sync'ing
+ Various, intermitten concurrency issues on guild leave, schedule checking, and bot schedule reloading.
+ Getting rate limited by Discord causes all maner of issues.

In general Saber-bot struggles to handle schedule management simultaneously for many discord servers. Some of my core design choices (No backend db, reloading of schedules and channel settings on startup) are deeply flawed and require a major rework. Unfortunately, I cannot currently devote the time necessary to do these revisions. I will likely return to finish up this project sometime in the future (assuming other discord scheduling utilities haven't replaced it's need).

<hr>
# Saber-Bot
## A calendar/schedule manager discord bot written in Java 8 and using JDA

### Features
+ Schedule events with a date, start time, end time, title, comments and repeat.
+ View scheduled events on schedule channels.
+ Organize events across multiple schedule channels.
+ Custom announcement messages on start/end of events (can be used to schedule other bot commands).
+ Set announcement message, channel to announce to, timezone and clock format per schedule channel.
+ Sync a schedule channel to a public Google Calendars calendar

### Dependencies

+ [JDA](https://github.com/DV8FromTheWorld/JDA) - 3.0.BETA2 Build:108
+ [Unirest](https://github.com/Mashape/unirest-java) - 1.4.9

### Suggestions/Complaints

[A common discord server is setup for all my bots.](https://discord.gg/ZQZnXsC) If you need help, have suggestions, or wish to rant about something concerning my bot hit me up there.

If you're looking for user documentation, the bots invite link, or a list of commands follow the link to my website or use the 'help' and 'setup' commands in discord server containing the bot.

## User Docs

Some user documentation is hosted on [my website](https://nmathe.ws/bots/saber).  Alternatively, the docs are [also viewable through my website's github repository](https://github.com/notem/nmathe.ws-content/blob/master/bots/saber/index.md), whose webpage formatting is likely nicer and far more reliable than my website.
