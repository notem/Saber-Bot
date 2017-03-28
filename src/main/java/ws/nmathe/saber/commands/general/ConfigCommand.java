package ws.nmathe.saber.commands.general;

import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

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
        String USAGE_EXTENDED = "``" + invoke + " <channel> [<option> <new config>]`` can be used to both view and " +
                "change schedule settings. To view a schedule's current settings, supply only the ``<channel>`` argument" +
                " Options are 'msg' (announcement message format), chan (announcement channel), zone (timezone to use), and clock " +
                "('12' to use am/pm or '24' for full form). \n\n" +
                "When creating a custom announcement message format the " +
                "'%' acts as a delimiter for entry parameters such as the title or a comment. %t will cause the entry" +
                " title to be inserted, %c[1-9] will cause the nth comment to be inserted, %a will insert" +
                " 'begins' or 'ends', and %% will insert %.";

        String USAGE_BRIEF = "``" + invoke + "`` - configure a schedule's settings";

        String EXAMPLES = "Ex1: ``" + invoke + " #schedule``\n" +
                "Ex2: ``" + invoke + " #guild_events msg \"@here The event %t %a.\"``\n" +
                "Ex3: ``" + invoke + " #events_channel chan \"general\"``";

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
            return "Not enough arguments";

        if( !Main.getScheduleManager().isASchedule(args[index].replace("<#","").replace(">","")) )
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild. " +
                    "Try using the ``init`` command!";

        index++;

        if (args.length > 1)
        {
            if (args.length < 3)
                return "Not enough arguments";

            switch( args[index++] )
            {
                case "msg" :
                    break;

                case "chan" :
                    break;

                case "zone" :
                    try
                    {
                        ZoneId.of(args[index]);
                    } catch(Exception e)
                    {
                        return "Argument **" + args[index] +  "** is not a valid timezone";
                    }
                    break;

                case "clock" :
                    if( !args[index].equals("24") && !args[index].equals("12"))
                        return "Argument **" + args[index] +  "** is not a valid option. Argument must be **24** " +
                                "or **12**";
                    break;

                case "sync" :
                    break;

                case "time" :
                    if(!VerifyUtilities.verifyTime(args[index]))
                        return "I can't parse ``" + args[index] + "`` into a time!";
                    break;

                case "remind" :
                    if(args[index].toLowerCase().equals("off"))
                        return "";

                    List<Integer> list = ParsingUtilities.parseReminderStr(args[index]);
                    if (list.size() <= 0)
                        return "I could not parse out any times!";
                    if (list.size() > 5)
                        return "More than 5 reminders are not allowed!";
                    for(Integer i : list)
                    {
                        if (i<5)
                            return "Reminders under 5 minutes are not allowed!";
                    }
                    break;

                default:
                    return "Argument **" + args[index-1] + "** is not a configurable setting!";
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

        if (args.length == 1)
        {
            ZoneId zone = Main.getScheduleManager().getTimeZone(cId);
            Date syncTime = Main.getScheduleManager().getSyncTime(cId);

            OffsetTime sync_time_display = ZonedDateTime.ofInstant(syncTime.toInstant(), zone)
                    .toOffsetDateTime().toOffsetTime().truncatedTo(ChronoUnit.MINUTES);

            List<Integer> reminders = Main.getScheduleManager().getDefaultReminders(cId);
            String reminderStr = "";
            if(reminders.isEmpty())
            {
                reminderStr = "off";
            }
            else
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

            String content = "<#" + cId + ">\n```js\n" +
                    "Message Format    (msg): " + "\"" + Main.getScheduleManager().getAnnounceFormat(cId) + "\"\n" +
                    "Announce Channel (chan): " + "\"" + Main.getScheduleManager().getAnnounceChan(cId) + "\"\n" +
                    "Timezone         (zone): " + "\"" + zone + "\"\n" +
                    "Clock Format    (clock): " + "\"" + Main.getScheduleManager().getClockFormat(cId) + "\"\n" +
                    "Calendar Sync    (sync): " + "\"" + Main.getScheduleManager().getAddress(cId) + "\"\n" +
                    "Time to Sync     (time): " + "\"" + sync_time_display + "\"\n" +
                    "Reminders      (remind): " + "\"" + reminderStr + "\"\n" +
                    "```";

            MessageUtilities.sendMsg(content, event.getChannel(), null);
        }
        else
        {
            switch (args[index++])
            {
                case "msg":
                    Main.getScheduleManager().setAnnounceFormat(scheduleChan.getId(), args[index]);
                    break;

                case "chan":
                    TextChannel tmp = event.getGuild()
                            .getTextChannelById(args[index].replace("<#","").replace(">",""));
                    String chanName = (tmp==null) ? args[index] : tmp.getName();

                    Main.getScheduleManager().setAnnounceChan(scheduleChan.getId(), chanName);
                    break;

                case "zone":
                    ZoneId zone = ZoneId.of(args[index]);
                    Main.getScheduleManager().setTimeZone(scheduleChan.getId(), zone);

                    // update schedule entries with new timezone
                    Main.getDBDriver().getEventCollection()
                            .updateMany(eq("channelId", scheduleChan.getId()), set("zone",zone.toString()));

                    // reload the schedule display
                    Main.getDBDriver().getEventCollection().find(eq("channelId", scheduleChan.getId()))
                            .forEach((Consumer<? super Document>) document ->
                            {
                                Main.getEntryManager().reloadEntry((Integer) document.get("_id"));
                            });
                    break;

                case "clock":
                    Main.getScheduleManager().setClockFormat(scheduleChan.getId(), args[index]);

                    // reload the schedule display
                    Main.getDBDriver().getEventCollection().find(eq("channelId", scheduleChan.getId()))
                            .forEach((Consumer<? super Document>) document ->
                            {
                                Main.getEntryManager().reloadEntry((Integer) document.get("_id"));
                            });
                    break;

                case "sync":
                    if( Main.getCalendarConverter().checkValidAddress(args[index]) )
                        Main.getScheduleManager().setAddress(scheduleChan.getId(), args[index]);
                    else
                        Main.getScheduleManager().setAddress(scheduleChan.getId(), "off");
                    break;

                case "time":
                    ZonedDateTime syncTime = ParsingUtilities.parseTime(
                            ZonedDateTime.now().withZoneSameLocal(Main.getScheduleManager().getTimeZone(cId)),
                            args[index]
                    );

                    // don't allow times set in the past
                    if(syncTime.isBefore(ZonedDateTime.now()))
                        syncTime.plusDays(1);

                    Main.getScheduleManager().setSyncTime(cId, Date.from(syncTime.toInstant()));
                    break;
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

            }
        }
    }
}
