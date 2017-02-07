# Saber-Bot
## A calendar/schedule manager discord bot written in Java 8 and using JDA

### Features
+ Schedule events with a date, start time, end time, title, comments and repeat.
+ View scheduled events on schedule channels.
+ Organize events across multiple schedule channels.
+ Custom announcement messages on start/end of events (can be used to schedule other bot commands).
+ Set announcement message, channel to announce to, timezone and clock format per schedule channel.
+ Sync a schedule channel to a public Google Calendars calendar

### Roadmap
+ Improve sync to google calendar
+ Add optional reminders which send announcement messages before an event begins
+ Use a DB system as backend for storing the list of schedule entries
+ (When channel categories release) organize schedule channels under the 'Schedule' category
+ Dynamically sort entries in a channel w/ the top-most entry having the earliest start time
+ More options for custom messages
+ User experience improvements, and automated setup
+ Character limits on user input


### Dependencies

+ [JDA](https://github.com/DV8FromTheWorld/JDA) - 3.0.BETA2 Build:108
+ [Unirest](https://github.com/Mashape/unirest-java) - 1.4.9

### Suggestions/Complaints

[A common discord server is setup for all my bots.](https://discord.gg/ZQZnXsC) If you need help, have suggestions, or wish to rant about something concerning my bot hit me up there.

If you're looking for user documentation, the bots invite link, or a list of commands follow the link to my website or use the 'help' and 'setup' commands in discord server containing the bot.

## User Docs

Some user documentation is hosted on [my website](https://nmathe.ws/saber/index.md).  Alternatively, the docs are [also viewable through my website's github repository](https://github.com/notem/nmathe.ws-content/blob/master/bots/saber/index.md), whose webpage formatting is likely nicer and far more reliable than my website.
