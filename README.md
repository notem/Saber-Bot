# Saber-Bot
## A calendar/schedule manager discord bot written in Java 8 and using JDA

### Features
+ Schedule events with a date, start time, end time, title, comments and repeat.
+ View scheduled events on schedule channels.
+ Organize events across multiple schedule channels.
+ Custom announcement messages on start/end of events (can be used to schedule other bot commands).
+ Set announcement message, channel to announce to, timezone and clock format per schedule channel.

### Roadmap
+ Support sync'ing to public Google Calendars
+ Support deletion of entries on message deletion
+ Add optional reminders which send announcement messages before an event begins
+ Use SQL as backend for storing the list of schedule entries
+ (When channel categories release) organize schedule channels under the 'Schedule' category
+ Dynamically sort entries in a channel w/ the top-most entry having the earliest start time
