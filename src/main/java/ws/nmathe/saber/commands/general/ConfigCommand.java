package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * Command which is used to adjust the schedule settings for a channel
 */
public class ConfigCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "config";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "```diff\n- Usage\n" + invoke + " <channel> [<option> <new config>]```\n" +
                "The config command can be used to both view and " +
                "change schedule settings. To view a schedule's current settings, supply only the ``<channel>`` argument.\n" +
                "Options are 'msg' (announcement message format), chan (announcement channel), zone (timezone to use), and clock " +
                "('12' to use am/pm or '24' for full form)." +
                "\n\n" +
                "To turn off calendar sync or event reminders, pass **off** as a command parameter when setting the config option." +
                "\n\n```diff\n+ Event Reminders```\n" +
                "Events can be configured to send reminder announcements at configured thresholds before an event begins.\n" +
                "To configure the times at which events on the schedule should send reminders, use the 'remind' with an " +
                "argument containing the relative times to remind delimited by spaces (see examples).\n" +
                "Reminder messages are defined by a configured format, see below." +
                "\n\n```diff\n+ Custom announcements and reminders```\n" +
                "When an event begins or ends an announcement message is sent to the configured channel.\n" +
                "The message that is sent is determined from the message format the schedule is configured to use." +
                "\n\n" +
                "When creating a custom announcement message format the " +
                "'%' acts as a delimiter for entry parameters such as the title or a comment.\n" +
                "**%t** will cause the entry title to be inserted\n**%c[1-9]** will cause the nth comment to be inserted\n**%a** will insert" +
                " 'begins' or 'ends'\n**%%** will insert %." +
                "\n\n" +
                "If you wish to create a multi-line message like the default message format, new lines can be entered using" +
                " SHIFT+Enter. However, be sure to encapsulate the entire string (new lines included) in quotations.";

        String USAGE_BRIEF = "``" + invoke + "`` - configure a schedule's settings";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + invoke + " #schedule``\n" +
                "``" + invoke + " #guild_events msg \"@here The event %t %a. %c1\"``\n" +
                "``" + invoke + " #guild_events remind \"10, 20, 30 min\"``\n" +
                "``" + invoke + " #events_channel chan \"general\"``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        if (args.length < 1)
            return "That's not enough arguments! Use ``" + invoke + " <channel> [<option> <new config>]``";

        String cId = args[index].replace("<#","").replace(">","");
        if( !Main.getScheduleManager().isASchedule(cId) )
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild. " +
                    "Use the ``" + invoke + "`` command to create a new schedule!";

        if(Main.getScheduleManager().isLocked(cId))
            return "Schedule is locked while sorting/syncing. Please try again after sort/sync finishes.";

        index++;

        if (args.length > 1)
        {
            if (args.length < 3)
                return "That's not enough arguments! Use ``" + invoke + " <channel> [<option> <new config>]``";

            switch( args[index++] )
            {
                case "m":
                case "msg":
                case "message":
                    break;

                case "ch":
                case "chan":
                case "channel":
                    break;

                case "z":
                case "zone":
                    try
                    {
                        ZoneId.of(args[index]);
                    } catch(Exception e)
                    {
                        return "**" + args[index] +  "** is not a valid timezone! Use the ``zones`` command to learn " +
                                "what options are available.";
                    }
                    break;

                case "cl":
                case "clock":
                    if( !args[index].equals("24") && !args[index].equals("12"))
                        return "Argument **" + args[index] +  "** is not a valid option. Argument must be **24** " +
                                "or **12**";
                    break;

                case "sy":
                case "sync":
                    if( args[index].equals("off") )
                        return "";
                    if( !Main.getCalendarConverter().checkValidAddress(args[index]) )
                        return "I cannot sync to **" + args[index] + "**! Provide a valid google calendar url or **off**.";
                    break;

                case "t":
                case "time":
                    if(!VerifyUtilities.verifyTime(args[index]))
                        return "I cannot parse ``" + args[index] + "`` into a time!";
                    break;

                case "r":
                case "remind":
                case "reminder":
                case "reminders":
                    if(args[index].toLowerCase().equals("off"))
                        return "";

                    List<Integer> list = ParsingUtilities.parseReminderStr(args[index]);
                    if (list.size() <= 0)
                        return "I could not parse out any times!";
                    if (list.size() > 10)
                        return "More than 10 reminders are not allowed!";
                    for(Integer i : list)
                    {
                        if (i<5)
                            return "Reminders under 5 minutes are not allowed!";
                    }
                    break;

                case "rm":
                case "remind-msg":
                    break;

                case "rch":
                case "remind-chan":
                    break;

                case "rsvp":
                    break;

                case "st":
                case "style":
                    break;

                case "l":
                case "len":
                case "length":
                    if(!VerifyUtilities.verifyInteger(args[index]))
                        return "*" + args[index] + "*" + " is not an integer!";
                    Integer len = Integer.parseInt(args[index]);
                    if(len>7 || len<1)
                        return "The sync length must be an integer between 1 and 7!";
                    break;

                default:
                    return "Argument **" + args[index-1] + "** is not a configurable setting! Options are **msg**, " +
                            "**chan**, **zone**, **clock**, **sync**, **time**, and **remind**.";
            }
        }

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
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
                    Main.getScheduleManager().setAnnounceFormat(scheduleChan.getId(), args[index]);
                    MessageUtilities.sendMsg(this.genMsgStr(cId, 1), event.getChannel(), null);
                    break;

                case "ch":
                case "chan":
                    String chanName;
                    String chanID = args[index].replace("<#","").replace(">","");
                    try
                    {
                        TextChannel tmp = event.getGuild().getTextChannelById(chanID);
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

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 1), event.getChannel(), null);
                    break;

                case "z":
                case "zone":
                    ZoneId zone = ZoneId.of(args[index]);
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

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 3), event.getChannel(), null);
                    break;

                case "cl":
                case "clock":
                    Main.getScheduleManager().setClockFormat(scheduleChan.getId(), args[index]);

                    // reload the schedule display
                    Main.getDBDriver().getEventCollection().find(eq("channelId", scheduleChan.getId()))
                            .forEach((Consumer<? super Document>) document ->
                                    Main.getEntryManager().reloadEntry((Integer) document.get("_id")));

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 2), event.getChannel(), null);
                    break;

                case "s":
                case "sync":
                    if( Main.getCalendarConverter().checkValidAddress(args[index]) )
                        Main.getScheduleManager().setAddress(scheduleChan.getId(), args[index]);
                    else
                        Main.getScheduleManager().setAddress(scheduleChan.getId(), "off");

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 3), event.getChannel(), null);
                    break;

                case "t":
                case "time":
                    ZonedDateTime syncTime = ParsingUtilities.parseTime(
                            ZonedDateTime.now().withZoneSameLocal(Main.getScheduleManager().getTimeZone(cId)),
                            args[index]
                    );

                    // don't allow times set in the past
                    if(syncTime.isBefore(ZonedDateTime.now()))
                        syncTime.plusDays(1);

                    Main.getScheduleManager().setSyncTime(cId, Date.from(syncTime.toInstant()));

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 4), event.getChannel(), null);
                    break;

                case "r":
                case "remind":
                    List<Integer> rem;
                    if(args[index].toLowerCase().equals("off"))
                        rem = new ArrayList<>();
                    else
                        rem = ParsingUtilities.parseReminderStr(args[index]);

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

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 2), event.getChannel(), null);
                    break;

                case "rm":
                case "remind-msg":
                    Main.getScheduleManager().setReminderFormat(scheduleChan.getId(), args[index]);

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 2), event.getChannel(), null);
                    break;

                case "rc":
                case "remind-chan":
                    String chanName2;
                    String chanID2 = args[index].replace("<#","").replace(">","");
                    try
                    {
                        TextChannel tmp2 = event.getGuild().getTextChannelById(chanID2);
                        if(tmp2!=null)
                            chanName2 = tmp2.getName();
                        else
                            chanName2 = args[index];
                    }
                    catch(Exception e)
                    {
                        chanName2 = args[index];
                    }

                    Main.getScheduleManager().setReminderChan(scheduleChan.getId(), chanName2);

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 2), event.getChannel(), null);
                    break;

                case "rsvp":
                    boolean enabled = Main.getScheduleManager().isRSVPEnabled(cId);

                    boolean new_enabled;
                    switch(args[index].toLowerCase())
                    {
                        case "on":
                        case "true":
                        case "yes":
                        case "y":
                            new_enabled = true;
                            break;

                        default:
                            new_enabled = false;
                            break;
                    }

                    if(enabled != new_enabled)
                    {
                        if(new_enabled)
                        {
                            // update every entry on the schedule
                            Main.getDBDriver().getEventCollection().updateMany(
                                    eq("channelId", scheduleChan.getId()),
                                    and(set("rsvp_yes", new ArrayList<>()), set("rsvp_no", new ArrayList<>())));

                            // for each entry on the schedule
                            Main.getDBDriver().getEventCollection()
                                    .find(eq("channelId", scheduleChan.getId()))
                                    .forEach((Consumer<? super Document>) document ->
                                    {
                                        // add reaction options
                                        Message msg = event.getGuild()
                                                .getTextChannelById(document.getString("channelId"))
                                                .getMessageById(document.getString("messageId"))
                                                .complete();

                                        msg.addReaction(Main.getBotSettingsManager().getYesEmoji()).queue();
                                        msg.addReaction(Main.getBotSettingsManager().getNoEmoji()).queue();
                                        msg.addReaction(Main.getBotSettingsManager().getClearEmoji()).queue();
                                    });
                        }
                        else
                        {
                            // update every entry on the schedule
                            Main.getDBDriver().getEventCollection().updateMany(
                                    eq("channelId", scheduleChan.getId()),
                                    and(set("rsvp_yes", null), set("rsvp_no", null)));

                            // for each entry on the schedule
                            Main.getDBDriver().getEventCollection()
                                    .find(eq("channelId", scheduleChan.getId()))
                                    .forEach((Consumer<? super Document>) document ->
                                    {
                                        // clear reactions
                                        event.getGuild().getTextChannelById(document.getString("channelId"))
                                                .getMessageById(document.getString("messageId")).complete()
                                                .clearReactions().queue();
                                    });
                        }

                        // set schedule settings
                        Main.getScheduleManager().setRSVPEnable(cId, new_enabled);

                        // for each entry on the schedule
                        Main.getDBDriver().getEventCollection()
                                .find(eq("channelId", scheduleChan.getId()))
                                .forEach((Consumer<? super Document>) document ->
                                        Main.getEntryManager().reloadEntry(document.getInteger("_id"))
                                );
                    }

                    MessageUtilities.sendMsg(this.genMsgStr(cId, 3), event.getChannel(), null);
                    break;

                case "st":
                case "style":
                    String style = args[index].toLowerCase();
                    if(style.equals("full"))
                        Main.getScheduleManager().setStyle(cId, style);
                    else if(style.equals("narrow"))
                        Main.getScheduleManager().setStyle(cId, style);

                    // for each entry on the schedule
                    Main.getDBDriver().getEventCollection()
                            .find(eq("channelId", scheduleChan.getId()))
                            .forEach((Consumer<? super Document>) document ->
                                    Main.getEntryManager().reloadEntry(document.getInteger("_id"))
                            );
                    MessageUtilities.sendMsg(this.genMsgStr(cId, 3), event.getChannel(), null);
                    break;

                case "l":
                case "len":
                case "length":
                    Main.getScheduleManager().setSyncLength(cId, Integer.parseInt(args[index]));
                    MessageUtilities.sendMsg(this.genMsgStr(cId, 4), event.getChannel(), null);
                    break;
            }
        }
        else    // print out all settings
        {
            MessageUtilities.sendMsg(this.genMsgStr(cId, 0), event.getChannel(), null);
        }
    }

    private String genMsgStr(String cId, int type)
    {
        ZoneId zone = Main.getScheduleManager().getTimeZone(cId);
        String content = "**Configuration for** <#" + cId + ">\n";

        switch(type)
        {
            default:
            case 1:
                content += "```js\n" +
                        "// Event Announcement Settings\n" +
                        "[msg] Format for start/end messages\n " + "\"" +
                        Main.getScheduleManager().getAnnounceFormat(cId).replace("```","`\uFEFF`\uFEFF`") + "\"\n" +
                        "\n[chan] Announce start/end to channel\n " +
                        "\"" + Main.getScheduleManager().getAnnounceChan(cId) + "\"\n" +
                        "```";

                if(type == 1)
                    break;
            case 2:
                List<Integer> reminders = Main.getScheduleManager().getDefaultReminders(cId);
                String reminderStr = "";
                if(reminders.isEmpty())
                {
                    reminderStr = "off";
                } else
                {
                    reminderStr += reminders.get(0);
                    for (int i=1; i<reminders.size()-1; i++)
                    {
                        reminderStr += ", " + reminders.get(i) ;
                    }
                    if(reminders.size() > 1)
                        reminderStr += " and " + reminders.get(reminders.size()-1);
                    reminderStr += " minutes";
                }

                content += "```js\n" +
                        "// Event Reminder Settings\n" +
                        "[remind] Send reminders before event begins\n " +
                        "\"" + reminderStr + "\"\n" +
                        "\n[remind-msg] Format for reminder messages\n " + "\"" +
                        Main.getScheduleManager().getReminderFormat(cId).replace("```","`\uFEFF`\uFEFF`") + "\"\n" +
                        "\n[remind-chan] Announce reminder to channel\n " +
                        "\"" + Main.getScheduleManager().getReminderChan(cId) + "\"\n" +
                        "```";

                if(type == 2)
                    break;
            case 3:
                content += "```js\n" +
                        "// Misc. Settings\n" +
                        "[zone] Display events in this timezone\n " +
                        "\"" + zone + "\"\n" +
                        "\n[clock] Display events using this clock format\n " +
                        "\"" + Main.getScheduleManager().getClockFormat(cId) + "\"\n" +
                        "\n[rsvp] Allow users to RSVP to events (on|off)\n " +
                        "\"" + (Main.getScheduleManager().isRSVPEnabled(cId) ? "on" : "off") + "\"\n" +
                        "\n[style] Display style of the events (full|narrow)\n " +
                        "\"" + Main.getScheduleManager().getStyle(cId) + "\"\n" +
                        "```";

                if(type == 3)
                    break;
            case 4:
                Date syncTime = Main.getScheduleManager().getSyncTime(cId);
                OffsetTime sync_time_display = ZonedDateTime.ofInstant(syncTime.toInstant(), zone)
                        .toOffsetDateTime().toOffsetTime().truncatedTo(ChronoUnit.MINUTES);

                content += "```js\n" +
                        "// Schedule Sync Settings\n" +
                        "[sync] Sync to Google calendar address\n " +
                        "\"" + Main.getScheduleManager().getAddress(cId) + "\"\n" +
                        "\n[time] Time of day to sync calendar\n " +
                        "\"" + sync_time_display + "\"\n" +
                        "\n[length] How many days to sync\n " +
                        "\"" + Main.getScheduleManager().getSyncLength(cId) + "\"\n" +
                        "```";

                if(type == 4)
                    break;
        }

        return content;
    }
}
