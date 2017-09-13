package ws.nmathe.saber.commands.general;

import com.google.api.client.auth.oauth2.Credential;
import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.User;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.core.google.GoogleAuth;
import ws.nmathe.saber.core.schedule.EntryManager;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * Command which is used to adjust the schedule settings for a channel
 */
public class ConfigCommand implements Command
{
    @Override
    public String name()
    {
        return "config";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String cmd = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + cmd + " <channel> [<option> <new config>]```\n" +
                "The config command can be used to both view and " +
                "change schedule settings. To view a schedule's current settings, supply only the ``<channel>`` argument.\n" +
                "The full list of setting options can be found when using the command with no <option> or <new config> parameters." +
                "\n\n" +
                "To modify any settings, use the term inside the brackets as the parameter of <option> and supply the " +
                "new setting configuration as the final <new config> parameter.\n" +
                "To turn off calendar sync or event reminders, pass **off** as a command parameter when setting the config ``sync`` and ``remind`` options." +
                "\n\n" +
                "```diff\n+ Event Reminders```\n" +
                "Events can be configured to send reminder announcements at configured thresholds before an event begins.\n" +
                "To configure the times at which events on the schedule should send reminders, use the 'remind' with an " +
                "argument containing the relative times to remind delimited by spaces (see examples).\n" +
                "Reminder messages are defined by a configured format, see below." +
                "splithere" +
                "```diff\n+ Custom announcements and reminders```\n" +
                "When an event begins or ends an announcement message is sent to the configured channel.\n" +
                "The message that is sent is determined from the message format the schedule is configured to use." +
                "\n\n" +
                "When creating a custom announcement message format the " +
                "'%' acts as a delimiter for entry parameters such as the title or a comment.\n" +
                "**%t** will cause the entry title to be inserted\n**%c[1-9]** will cause the nth comment to be inserted\n**%a** will insert" +
                " 'begins' or 'ends'\n**%%** will insert %." +
                "\n\n" +
                "If you wish to create a multi-line message like the default message format, new lines can be entered using" +
                " SHIFT+Enter.\n" +
                "However, be sure to encapsulate the entire string (new lines included) in quotations." +
                "\n\n" +
                "To reset a custom message or channel to the default pass **reset** as the command parameter." +
                "splithere" +
                "```diff\n+ Event RSVP```\n" +
                "Schedules can be configured to allow users to RSVP to events on the schedule.\n" +
                "To enable RSVP for the schedule, use the ``rsvp`` option and provide the argument **on** (see Examples for details)\n" +
                "\nCustom rsvp options can be configured by using ``rsvp add`` and ``rsvp remove``.\n" +
                "When adding a new rsvp group two arguments are necessary: the first argument denotes the name for the rsvp group," +
                "the second argument is the emoticon to use as the message reaction button.\n" +
                "Custom discord emoticons are allowed.\n" +
                "\nWhen removing an rsvp group, simply provide the group's name as an argument.";

        String USAGE_BRIEF = "``" + cmd + "`` - configure a schedule's settings";

        String EXAMPLES = "```diff\n- Examples```" +
                "\n``" + cmd + " #guild_events``" +
                "\n``" + cmd + " #guild_events msg \"@here The event %t %a. %c1\"``" +
                "\n``" + cmd + " #guild_events remind \"10, 20, 30 min\"``" +
                "\n``" + cmd + " #events_channel chan \"general\"``" +
                "\n``" + cmd + " #events_channel remind-msg \"reset\"``" +
                "\n``" + cmd + " #schedule rsvp on``" +
                "\n``" + cmd + " #schedule rsvp add DPS :crossed_swords:``" +
                "\n``" + cmd + " #schedule rsvp remove Undecided``";

        if( brief ) return USAGE_BRIEF;
        else return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String cmd = prefix + this.name();
        int index = 0;

        if (args.length < 1)
        {
            return "That's not enough arguments! Use ``" + cmd + " <channel> [<option> <new config>]``";
        }

        String cId = args[index].replace("<#","").replace(">","");
        if( !Main.getScheduleManager().isASchedule(cId) )
        {
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild. " +
                    "Use the ``" + prefix + "init`` command to create a new schedule!";
        }

        if(Main.getScheduleManager().isLocked(cId))
        {
            return "Schedule is locked while sorting/syncing. Please try again after sort/sync finishes.";
        }

        // get user Google credentials (if they exist)
        Credential credential;
        com.google.api.services.calendar.Calendar service;
        try
        {
            credential = GoogleAuth.authorize(event.getAuthor().getId());
            if(credential == null)
            {
                credential = GoogleAuth.authorize();
            }
            service = GoogleAuth.getCalendarService(credential);
        }
        catch (IOException e)
        {
            return "I failed to connect to Google API Services!";
        }

        index++;

        if (args.length > 1)
        {
            if (args.length < 2)
            {
                return "That's not enough arguments! Use ``" + cmd + " <channel> [<option> <new config>]``";
            }
            switch( args[index++] )
            {
                case "m":
                case "msg":
                case "message":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [#channel] msg <new config>]``, " +
                                "where ``<new config>`` is the message format string to use when create announcement and remind messages.\n" +
                                "Reference the ``help`` command information for ``config`` to learn more about custom announcement messages.";
                    }
                    break;

                case "ch":
                case "chan":
                case "channel":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [#channel] chan <new config>``, " +
                                "where ``<new config>`` is a discord channel to which announcement messages should be sent.\n";
                    }
                    break;


                case "em":
                case "end-msg":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [#channel] end-msg <new config>``, " +
                                "where ``<new config>`` is the message format string to use when create event end messages.\n" +
                                "This overrides the ``[msg]`` setting for events which are ending.\n" +
                                "Reference the ``help`` command information for ``config`` to learn more about custom announcement messages.";
                    }
                    break;

                case "ech":
                case "end-chan":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [#channel] end-chan <new config>``, " +
                                "where <new config> is a discord channel to which event end messages should be sent.\n" +
                                "This overrides the ``[chan]`` setting for events which are ending.";
                    }
                    break;

                case "z":
                case "zone":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [#channel] zone <new config>``, where ``<new config>`` is a valid timezone string." +
                                "\nA list of valid timezones can be seen using the ``zones`` command).";
                    }
                    if(!VerifyUtilities.verifyZone(args[index]))
                    {
                        return "**" + args[index] +  "** is not a valid timezone! Use the ``zones`` command to learn " +
                                "what options are available.";
                    }
                    break;

                case "cl":
                case "clock":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [#channel] clock <new config>``, " +
                                "where ``<new config>`` is **\"12\"** for 12 hour format (am/pm), or **\"24\"** for full 24 hour time.";
                    }
                    if( !args[index].equals("24") && !args[index].equals("12"))
                    {
                        return "Argument **" + args[index] +  "** is not a valid option. Argument must be **24** " +
                                "or **12**";
                    }
                    break;

                case "sy":
                case "sync":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [#channel] sync <new config>``, " +
                                "where ``<new config>`` is a google calendar address or **\"off\"**";
                    }
                    if(args[index].equalsIgnoreCase("off"))
                    {
                        return "";
                    }
                    if(!Main.getCalendarConverter().checkValidAddress(args[index], service))
                    {
                        return "I cannot sync to **" + args[index] + "**!";
                    }
                    break;

                case "t":
                case "time":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [#channel] time <new config>``, " +
                                "where ``<new config>`` is the time of day to which the schedule should be automatically " +
                                "resync to the linked google calendar address.";
                    }
                    if(!VerifyUtilities.verifyTime(args[index]))
                    {
                        return "I cannot parse ``" + args[index] + "`` into a time!";
                    }
                    break;

                case "r":
                case "remind":
                case "reminder":
                case "reminders":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " " + args[0] + " reminder [add|remove] [reminder]``, " +
                                "where ``[reminder]`` is the number of minutes before the event starts " +
                                "that the reminder should be sent.";
                    }

                    Set<Integer> list = new LinkedHashSet<>();
                    list.addAll(Main.getScheduleManager().getReminders(cId));
                    switch(args[index])
                    {
                        case "off":
                            return "";

                        case "add":
                            if (args.length < 4)
                            {
                                return "That's not enough arguments!\n" +
                                        "Use ``" + cmd + " " + args[0] + " reminder "+args[index]+" [reminder]``, " +
                                        "where ``[reminder]`` is the number of minutes before the event starts " +
                                        "that the reminder should be sent.";
                            }
                            index++;
                            list.addAll(ParsingUtilities.parseReminderStr(args[index]));
                            if (list.size() <= 0) return "I could not parse out any times!";
                            break;

                        case "remove":
                            if (args.length < 4)
                            {
                                return "That's not enough arguments!\n" +
                                        "Use ``" + cmd + " " + args[0] + " reminder "+args[index]+" [reminder]``, " +
                                        "where ``[reminder]`` is the number of minutes before the event starts " +
                                        "that the reminder should be sent.";
                            }
                            index++;
                            list.removeAll(ParsingUtilities.parseReminderStr(args[index]));
                            break;

                        default:
                            list = ParsingUtilities.parseReminderStr(args[index]);
                            if (list.size() <= 0) return "I could not parse out any times!";
                            break;
                    }

                    if (list.size() > 20)
                    {
                        return "More than 20 reminders are not allowed!";
                    }
                    for(Integer i : list)
                    {
                        if (i<5) return "Reminders under 5 minutes are not allowed!";
                    }
                    break;

                case "rm":
                case "remind-msg":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [chan] remind-msg <new config>``, " +
                                "where ``<new config>`` is the message format string to use when sending remind messages.\n" +
                                "This setting will override the ``[msg]`` option for reminders.\n" +
                                "Reference the ``help`` command information for ``config`` to learn more about custom announcement messages.";
                    }
                    break;

                case "rch":
                case "remind-chan":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [chan] remind-chan <new config>``, " +
                                "where ``<new config>`` is a discord channel to which announcement messages should be sent.\n";
                    }
                    break;

                case "rsvp":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [chan] rsvp <new config>``, " +
                                "where <new config> should be \"on\" to enable the rsvp feature, or \"off\" to " +
                                "disable the feature.\n";
                    }
                    switch(args[index])
                    {
                        case "add":
                        case "a":
                            index++;
                            if(args.length != 5)
                            {
                                return "Argument *" + args[index] + "* is not properly formed for the ``add`` option!\n" +
                                        "Use ``" + cmd + " [#channel] rsvp add [name] [emoji]`` to add a new rsvp option " +
                                        "where [emoji] is the discord emoji for the rsvp option to use and " +
                                        "[name] is the display name of the rsvp option.";
                            }

                            // verify input is either a valid unicode emoji or discord emoji
                            if(!EmojiManager.isEmoji(args[index+1]))
                            {
                                String emoteId = args[index+1].replaceAll("[^\\d]", "");
                                Emote emote = null;
                                try
                                {
                                    for(JDA jda : Main.getShardManager().getShards())
                                    {
                                        emote = jda.getEmoteById(emoteId);
                                        if(emote != null) break;
                                    }
                                }
                                catch(Exception e)
                                {
                                    return "*" + args[index+1] + "* is not an emoji!\n" +
                                            "Your emoji must be a valid unicode emoji or custom discord emoji!";
                                }
                                if(emote == null)
                                {
                                    return "*" + args[index+1] + "* is not an emoji!\n" +
                                            "Your emoji must be a valid unicode emoji or custom discord emoji!";
                                }
                            }
                            if(Main.getScheduleManager().getRSVPOptions(cId).values().contains(args[index].trim()))
                            {
                                return "RSVP group name *" + args[index] + "* already exists!\n" +
                                        "Please choose a different name for your rsvp group!";
                            }
                            break;

                        case "remove":
                        case "r":
                            break;

                        case "off":
                        case "on":
                        case "true":
                        case "false":
                            break;

                        default:
                        {
                            return "Argument *" + args[index] + "* is not an appropriate argument!\n" +
                                    "Use ``" + cmd + " [#channel] rsvp [on|off]`` to enable/disable rsvp on the schedule.\n" +
                                    "Use ``" + cmd + " [#channel] rsvp add [emoji]-[name]`` to add a new rsvp option.\n" +
                                    "Use ``" + cmd + " [#channel] rsvp remove [emoji|name]`` to remove an rsvp option.";
                        }
                    }
                    break;

                case "st":
                case "style":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [chan] style <new config>``, " +
                                "where ``<new config>`` can either be **\"full\"** to display events in the lengthy full information style, or **\"narrow\"** to " +
                                "to display events in a compressed, smaller style.\n";
                    }
                    break;

                case "l":
                case "len":
                case "length":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [chan] length <new config>``, " +
                                "where ``<new config>`` is the length of time (in days) to which the linked google " +
                                "calendar should be synced.\nFor example, \"7\" will sync a weeks worth events to " +
                                "the schedule and \"30\" will have the schedule display the full month of events.";
                    }

                    if(!VerifyUtilities.verifyInteger(args[index]))
                    {
                        return "*" + args[index] + "*" + " is not an integer!\n This option takes a ";
                    }
                    Integer len = Integer.parseInt(args[index]);
                    if(len>30 || len<1)
                    {
                        return "The sync length must be an integer between 1 and 30!";
                    }
                    break;

                case "so":
                case "sort":
                    if (args.length < 3)
                    {
                        return "That's not enough arguments!\n" +
                                "Use ``" + cmd + " [chan] sort <new config>``, " +
                                "where ``<new config>`` may be either **\"asc\"** to automatically sort the schedule in ascending order," +
                                " **\"desc\"** for descending order, or **\"off\"** to disable auto-sorting.";
                    }

                    switch(args[index])
                    {
                        case "disabled":
                        case "off":
                        case "desc":
                        case "asc":
                        case "ascending":
                        case "descending":
                            return "";
                        default:
                            return "*" + args[index] + "* is not a valid sort option! Use *off*, *desc*, or *asc*.";
                    }

                default:
                {
                    return "Argument **" + args[index-1] + "** is not a configurable setting! Options are **msg**, " +
                            "**chan**, **zone**, **clock**, **sync**, **time**, and **remind**.";
                }
            }
        }

        return "";
    }

    @Override
    public void action(String head, String[] args, MessageReceivedEvent event)
    {
        try
        {
            // get user Google credentials (if they exist)
            Credential credential;
            com.google.api.services.calendar.Calendar service;
            try
            {
                credential = GoogleAuth.authorize(event.getAuthor().getId());
                if(credential == null)
                {
                    credential = GoogleAuth.authorize();
                }
                service = GoogleAuth.getCalendarService(credential);
            }
            catch (IOException e)
            {
                return;
            }

            int index = 0;
            String cId = args[index].replace("<#","").replace(">","");
            TextChannel scheduleChan = event.getGuild()
                    .getTextChannelById(args[index].replace("<#","").replace(">",""));

            index++;

            if (args.length > 1)
            {
                switch (args[index++])
                {
                    case "m":
                    case "msg":
                        String msgFormat;
                        switch(args[index].toLowerCase())
                        {
                            case "reset":
                            case "default":
                                msgFormat = Main.getBotSettingsManager().getAnnounceFormat();
                                break;

                            case "off":
                                msgFormat = "";
                                break;

                            default:
                                msgFormat = args[index];
                                break;
                        }
                        Main.getScheduleManager().setAnnounceFormat(scheduleChan.getId(), msgFormat);
                        MessageUtilities.sendMsg(this.genMsgStr(cId, 1, event.getJDA()), event.getChannel(), null);
                        break;

                    case "ch":
                    case "chan":
                        String chanName;
                        String chanId = args[index].replace("<#","").replace(">","");
                        try
                        {
                            TextChannel tmp = event.getGuild().getTextChannelById(chanId);
                            if(tmp!=null)
                                chanName = tmp.getName();
                            else
                                chanName = args[index];
                        }
                        catch(Exception e)
                        {
                            chanName = args[index];
                        }

                        Main.getScheduleManager().setAnnounceChan(scheduleChan.getId(), chanName);
                        MessageUtilities.sendMsg(this.genMsgStr(cId, 1, event.getJDA()), event.getChannel(), null);
                        break;


                    case "em":
                    case "end-msg":
                        String endFormat;
                        switch(args[index].toLowerCase())
                        {
                            case "reset":
                            case "default":
                            case "null":
                                endFormat = null;
                                break;

                            case "off":
                                endFormat = "";
                                break;

                            default:
                                endFormat = args[index];
                                break;
                        }
                        Main.getScheduleManager().setEndAnnounceFormat(scheduleChan.getId(), endFormat);
                        MessageUtilities.sendMsg(this.genMsgStr(cId, 1, event.getJDA()), event.getChannel(), null);
                        break;

                    case "ech":
                    case "end-chan":
                        String endChanName;
                        switch(args[index].toLowerCase())
                        {
                            case "reset":
                            case "default":
                            case "null":
                                endChanName = null;
                                break;

                            default:
                                String endChanId = args[index].replace("<#","").replace(">","");
                                try
                                {
                                    TextChannel tmp = event.getGuild().getTextChannelById(endChanId);
                                    if(tmp!=null)
                                        endChanName = tmp.getName();
                                    else
                                        endChanName = args[index];
                                }
                                catch(Exception e)
                                {
                                    endChanName = args[index];
                                }
                        }
                        Main.getScheduleManager().setEndAnnounceChan(scheduleChan.getId(), endChanName);
                        MessageUtilities.sendMsg(this.genMsgStr(cId, 1, event.getJDA()), event.getChannel(), null);
                        break;

                    case "z":
                    case "zone":
                        ZoneId zone = ParsingUtilities.parseZone(args[index]);
                        Main.getScheduleManager().setTimeZone(scheduleChan.getId(), zone);

                        // correct/reload the event displays
                        Main.getDBDriver().getEventCollection().find(eq("channelId", scheduleChan.getId()))
                                .forEach((Consumer<? super Document>) document ->
                                {
                                    Integer id = document.getInteger("_id");

                                    // if the timezone conversion causes the end go past 24:00
                                    // the date needs to be corrected
                                    ScheduleEntry se = Main.getEntryManager().getEntry(id);
                                    if(se.getStart().isAfter(se.getEnd()))
                                    {
                                        Main.getDBDriver().getEventCollection().updateOne(
                                                eq("_id", id),
                                                set("end", Date.from(se.getEnd().plusDays(1).toInstant()))
                                        );
                                    }

                                    // reload the entry's display to match new timezone
                                    Main.getEntryManager().reloadEntry(id);
                                });

                        // disable auto-sync'ing timezone
                        Main.getDBDriver().getScheduleCollection()
                                .updateOne(eq("_id", scheduleChan.getId()), set("timezone_sync", false));

                        MessageUtilities.sendMsg(this.genMsgStr(cId, 3, event.getJDA()), event.getChannel(), null);
                        break;

                    case "cl":
                    case "clock":
                        Main.getScheduleManager().setClockFormat(scheduleChan.getId(), args[index]);

                        // reload the schedule display
                        Main.getDBDriver().getEventCollection().find(eq("channelId", scheduleChan.getId()))
                                .forEach((Consumer<? super Document>) document ->
                                        Main.getEntryManager().reloadEntry((Integer) document.get("_id")));

                        MessageUtilities.sendMsg(this.genMsgStr(cId, 2, event.getJDA()), event.getChannel(), null);
                        break;

                    case "s":
                    case "sync":
                        if( Main.getCalendarConverter().checkValidAddress(args[index], service) )
                            Main.getScheduleManager().setAddress(scheduleChan.getId(), args[index]);
                        else
                            Main.getScheduleManager().setAddress(scheduleChan.getId(), "off");

                        MessageUtilities.sendMsg(this.genMsgStr(cId, 3, event.getJDA()), event.getChannel(), null);
                        break;

                    case "t":
                    case "time":
                        ZonedDateTime syncTime = ZonedDateTime.of(
                                LocalDate.now(),
                                ParsingUtilities.parseTime(args[index]),
                                Main.getScheduleManager().getTimeZone(cId));

                        // don't allow times set in the past
                        if(syncTime.isBefore(ZonedDateTime.now()))
                            syncTime.plusDays(1);

                        Main.getScheduleManager().setSyncTime(cId, Date.from(syncTime.toInstant()));

                        MessageUtilities.sendMsg(this.genMsgStr(cId, 4, event.getJDA()), event.getChannel(), null);
                        break;

                    case "r":
                    case "remind":
                    case "reminder":
                    case "reminders":
                        Set<Integer> list = new LinkedHashSet<>();
                        list.addAll(Main.getScheduleManager().getReminders(cId));
                        switch(args[index])
                        {
                            case "off":
                                list = new LinkedHashSet<>();
                                break;

                            case "add":
                                index++;
                                list.addAll(ParsingUtilities.parseReminderStr(args[index]));
                                break;

                            case "remove":
                                index++;
                                list.removeAll(ParsingUtilities.parseReminderStr(args[index]));
                                break;

                            default:
                                list = ParsingUtilities.parseReminderStr(args[index]);
                                break;
                        }

                        // convert set to a list
                        List<Integer> rem = new ArrayList<>(list);
                        Main.getScheduleManager().setDefaultReminders(cId, rem);

                        // for every entry on channel, update
                        Main.getDBDriver().getEventCollection().find(eq("channelId", scheduleChan.getId()))
                                .forEach((Consumer<? super Document>) document ->
                                {
                                    // generate new entry reminders
                                    List<Date> reminders = new ArrayList<>();
                                    Instant start = ((Date) document.get("start")).toInstant();
                                    for(Integer til : rem)
                                    {
                                        if(Instant.now().until(start, ChronoUnit.MINUTES) > til)
                                        {
                                            reminders.add(Date.from(start.minusSeconds(til*60)));
                                        }
                                    }

                                    // update db
                                    Main.getDBDriver().getEventCollection()
                                            .updateOne(eq("_id", document.get("_id")), set("reminders", reminders));

                                    // reload displayed message
                                    Main.getEntryManager().reloadEntry((Integer) document.get("_id"));
                                });

                        MessageUtilities.sendMsg(this.genMsgStr(cId, 2, event.getJDA()), event.getChannel(), null);
                        break;

                    case "rm":
                    case "remind-msg":
                        String remindFormat;
                        switch(args[index].toLowerCase())
                        {
                            case "reset":
                            case "default":
                            case "null":
                                remindFormat = null;
                                break;

                            case "off":
                                remindFormat = "";
                                break;

                            default:
                                remindFormat = args[index];
                                break;
                        }
                        Main.getScheduleManager().setReminderFormat(scheduleChan.getId(), remindFormat);
                        MessageUtilities.sendMsg(this.genMsgStr(cId, 2, event.getJDA()), event.getChannel(), null);
                        break;

                    case "rc":
                    case "remind-chan":
                        switch(args[index].toLowerCase())
                        {
                            case "reset":
                            case "default":
                            case "null":
                                chanName = null;
                                break;

                            default:
                                chanId = args[index].replace("<#","").replace(">","");
                                try
                                {
                                    TextChannel tmp2 = event.getGuild().getTextChannelById(chanId);
                                    if(tmp2!=null)
                                        chanName = tmp2.getName();
                                    else
                                        chanName = args[index];
                                }
                                catch(Exception e)
                                {
                                    chanName = args[index];
                                }
                                break;
                        }
                        Main.getScheduleManager().setReminderChan(scheduleChan.getId(), chanName);
                        MessageUtilities.sendMsg(this.genMsgStr(cId, 2, event.getJDA()), event.getChannel(), null);
                        break;

                    case "rsvp":
                        boolean enabled = Main.getScheduleManager().isRSVPEnabled(cId);
                        Map<String, String> options = Main.getScheduleManager().getRSVPOptions(cId);
                        Boolean new_enabled = null;
                        switch(args[index++].toLowerCase())
                        {
                            case "add":
                            case "a":
                                String emoji = args[index+1].trim();
                                if(!EmojiManager.isEmoji(emoji))
                                {
                                    emoji = emoji.replaceAll("[^\\d]","");
                                }
                                options.put(emoji, args[index].trim());
                                Main.getScheduleManager().setRSVPOptions(cId, options);
                                break;

                            case "remove":
                            case "r":
                                if(options.containsKey(args[index]))
                                {
                                    options.remove(args[index]);
                                }
                                else if(options.containsValue(args[index]))
                                {
                                    options.values().remove(args[index]);
                                }
                                Main.getScheduleManager().setRSVPOptions(cId, options);
                                break;

                            case "on":
                            case "true":
                                new_enabled = true;
                                break;

                            case "off":
                            case "false":
                                new_enabled = false;
                                break;
                        }

                        // if add or remove option was used, clear the reactions and re-add the new reactions
                        if(new_enabled == null)
                        {
                            // for each entry on the schedule
                            Main.getDBDriver().getEventCollection()
                                    .find(eq("channelId", scheduleChan.getId()))
                                    .forEach((Consumer<? super Document>) document ->
                                    {
                                        // clear reactions
                                        event.getGuild().getTextChannelById(document.getString("channelId"))
                                                .getMessageById(document.getString("messageId")).complete()
                                                .clearReactions().queue((message) ->
                                        {
                                            Map<String, String> map = Main.getScheduleManager()
                                                    .getRSVPOptions(document.getString("channelId"));

                                            // add reaction options
                                            event.getGuild()
                                                    .getTextChannelById(document.getString("channelId"))
                                                    .getMessageById(document.getString("messageId"))
                                                    .queue(msg -> EntryManager.addRSVPReactions(map, msg));
                                        });

                                        Main.getEntryManager().reloadEntry(document.getInteger("_id"));
                                    });
                        }
                        // otherwise, if the rsvp setting was changes
                        else if(enabled != new_enabled)
                        {
                            // set schedule settings
                            Main.getScheduleManager().setRSVPEnable(cId, new_enabled);

                            if(new_enabled)
                            {
                                // for each entry on the schedule
                                Main.getDBDriver().getEventCollection()
                                        .find(eq("channelId", scheduleChan.getId()))
                                        .forEach((Consumer<? super Document>) document ->
                                        {
                                            Map<String, String> map = Main.getScheduleManager()
                                                    .getRSVPOptions(document.getString("channelId"));

                                            // add reaction options
                                             event.getGuild()
                                                    .getTextChannelById(document.getString("channelId"))
                                                    .getMessageById(document.getString("messageId"))
                                                    .queue(msg -> EntryManager.addRSVPReactions(map, msg));

                                            Main.getEntryManager().reloadEntry(document.getInteger("_id"));
                                        });
                            }
                            else
                            {
                                // for each entry on the schedule
                                Main.getDBDriver().getEventCollection()
                                        .find(eq("channelId", scheduleChan.getId()))
                                        .forEach((Consumer<? super Document>) document ->
                                        {
                                            // clear reactions
                                            event.getGuild().getTextChannelById(document.getString("channelId"))
                                                    .getMessageById(document.getString("messageId")).complete()
                                                    .clearReactions().queue();

                                            Main.getEntryManager().reloadEntry(document.getInteger("_id"));
                                        });
                            }
                        }

                        MessageUtilities.sendMsg(this.genMsgStr(cId, 5, event.getJDA()), event.getChannel(), null);
                        break;

                    case "st":
                    case "style":
                        String style = args[index].toLowerCase();
                        if(style.equals("full")) Main.getScheduleManager().setStyle(cId, style);
                        else if(style.equals("narrow")) Main.getScheduleManager().setStyle(cId, style);

                        // for each entry on the schedule
                        Main.getDBDriver().getEventCollection()
                                .find(eq("channelId", scheduleChan.getId()))
                                .forEach((Consumer<? super Document>) document ->
                                        Main.getEntryManager().reloadEntry(document.getInteger("_id"))
                                );
                        MessageUtilities.sendMsg(this.genMsgStr(cId, 3, event.getJDA()), event.getChannel(), null);
                        break;

                    case "l":
                    case "len":
                    case "length":
                        Main.getScheduleManager().setSyncLength(cId, Integer.parseInt(args[index]));
                        MessageUtilities.sendMsg(this.genMsgStr(cId, 4, event.getJDA()), event.getChannel(), null);
                        break;

                    case "so":
                    case "sort":
                        int sortType;
                        switch(args[index])
                        {
                            case "on":
                            case "asc":
                            case "ascending":
                                sortType = 1;
                                break;
                            case "desc":
                            case "descending":
                                sortType = 2;
                                break;
                            default:
                                sortType = 0;
                                break;
                        }
                        Main.getScheduleManager().setAutoSort(cId, sortType);
                        MessageUtilities.sendMsg(this.genMsgStr(cId, 3, event.getJDA()), event.getChannel(), null);

                        // now sort the schedule
                        if(sortType == 1)
                        {
                            Main.getScheduleManager().sortSchedule(cId, false);
                        }
                        if(sortType == 2)
                        {
                            Main.getScheduleManager().sortSchedule(cId, true);
                        }
                        break;
                }
            }
            else    // print out all settings
            {
                MessageUtilities.sendMsg(this.genMsgStr(cId, 0, event.getJDA()), event.getChannel(), null);
            }
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }


    /**
     * Generates the schedule config message to display to the user
     * type codes:  0 - full message
     *              1 - announcement settings
     *              2 - reminder settings
     *              3 - miscellaneous settings
     *              4 - sync settings
     *              5 - rsvp settings
     * @param cId (String) the ID of the schedule/channel
     * @param type (int) the type code for the message to generate
     * @return (String) the message to display
     */
    private String genMsgStr(String cId, int type, JDA jda)
    {
        ZoneId zone = Main.getScheduleManager().getTimeZone(cId);
        String content = "**Configuration for** <#" + cId + ">\n";

        switch(type)
        {
            default:
            case 1:
                String form1 = Main.getScheduleManager().getStartAnnounceFormat(cId);
                String form2 = Main.getScheduleManager().getEndAnnounceFormat(cId);
                content += "```js\n" +
                        "// Event Announcement Settings" +
                        "\n[msg]      " + (form1.isEmpty()?"(off)":
                        "\"" + form1.replace("```","`\uFEFF`\uFEFF`") + "\"") +
                        "\n[chan]     " +
                        "\"" + Main.getScheduleManager().getStartAnnounceChan(cId) + "\"" +
                        "\n[end-msg]  " +
                        (Main.getScheduleManager().isEndFormatOverridden(cId) ?
                                (form2.isEmpty()?"(off)":
                                "\"" + form2.replace("```","`\uFEFF`\uFEFF`")  + "\""):
                                "(using [msg])") +
                        "\n[end-chan] " +
                        (Main.getScheduleManager().isEndChannelOverridden(cId) ?
                                "\"" + Main.getScheduleManager().getEndAnnounceChan(cId) + "\"" :
                                "(using [chan])") +
                        "```";

                if(type == 1) break;
            case 2:
                String form3 = Main.getScheduleManager().getReminderFormat(cId);
                List<Integer> reminders = Main.getScheduleManager().getReminders(cId);
                String reminderStr = "";
                if(reminders.isEmpty())
                {
                    reminderStr = "off";
                } else
                {
                    reminderStr += reminders.get(0);
                    for (int i=1; i<reminders.size()-1; i++)
                    {
                        reminderStr += ", " + reminders.get(i);
                    }
                    if(reminders.size() > 1)
                        reminderStr += " and " + reminders.get(reminders.size()-1);
                    reminderStr += " minutes";
                }

                content += "```js\n" +
                        "// Event Reminder Settings" +
                        "\n[remind]      " +
                        "\"" + reminderStr + "\"" +
                        "\n[remind-msg]  " +
                        (Main.getScheduleManager().isRemindFormatOverridden(cId) ? (form3.isEmpty()?"(off)":
                                "\"" + form3.replace("```","`\uFEFF`\uFEFF`")  + "\""):
                                "(using [msg])") +

                        "\n[remind-chan] " +
                        (Main.getScheduleManager().isRemindChanOverridden(cId) ?
                                "\"" + Main.getScheduleManager().getReminderChan(cId) + "\"":
                                "(using [chan])") +
                        "```";

                if(type == 2) break;
            case 3:
                int sortType = Main.getScheduleManager().getAutoSort(cId);
                String sort = "";
                switch(sortType)
                {
                    case 0:
                        sort = "disabled";
                        break;
                    case 1:
                        sort = "ascending";
                        break;
                    case 2:
                        sort = "descending";
                        break;
                }

                content += "```js\n" +
                        "// Misc. Settings" +
                        "\n[zone]   " +
                        "\"" + zone + "\"" +
                        "\n[clock]  " +
                        "\"" + Main.getScheduleManager().getClockFormat(cId) + "\"" +
                        "\n[style]  " +
                        "\"" + Main.getScheduleManager().getStyle(cId).toLowerCase() + "\"" +
                        "\n[sort]   " +
                        "\"" + sort + "\"" +
                        "```";

                if(type == 3) break;
            case 4:
                Date syncTime = Main.getScheduleManager().getSyncTime(cId);
                OffsetTime sync_time_display = ZonedDateTime.ofInstant(syncTime.toInstant(), zone)
                        .toOffsetDateTime().toOffsetTime().truncatedTo(ChronoUnit.MINUTES);

                User user = null;
                if(Main.getScheduleManager().getSyncUser(cId)!=null)
                {
                    user = jda.getUserById(Main.getScheduleManager().getSyncUser(cId));
                }

                content += "```js\n" +
                        "// Schedule Sync Settings" +
                        "\n[sync]   " +
                        "\"" + Main.getScheduleManager().getAddress(cId) + "\"" +
                            (user!=null?" (authorized by "+user.getName()+")":"") +
                        "\n[time]   " +
                        "\"" + sync_time_display + "\"" +
                        "\n[length] " +
                        "\"" + Main.getScheduleManager().getSyncLength(cId) + "\"" +
                        "```";

                if(type == 4) break;
            case 5:
                content += "```js\n" +
                        "// RSVP Settings" +
                        "\n[rsvp]   " +
                        "\"" + (Main.getScheduleManager().isRSVPEnabled(cId) ? "on" : "off") + "\"" +
                        "\n<Groups>\n";

                Map<String, String> options = Main.getScheduleManager().getRSVPOptions(cId);
                for(String key : options.keySet())
                {
                    if(EmojiManager.isEmoji(key))
                    {
                        content += " " + options.get(key) + " - " + key + "\n";
                    }
                    else
                    {
                        Emote emote = null;
                        for(JDA shard : Main.getShardManager().getShards())
                        {
                            emote = shard.getEmoteById(key);
                            if(emote != null) break;
                        }
                        String displayName = emote.getName();
                        content += " " + options.get(key) + " - :" + displayName + ":\n";
                    }
                }

                content += "```";

                if(type == 5) break;
        }

        return content;
    }
}
