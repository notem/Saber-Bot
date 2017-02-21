package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.utils.GuildUtilities;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Command which is used to adjust the schedule settings for a channel
 */
public class SetCommand implements Command
{
    private ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();
    private ScheduleManager schedManager = Main.getScheduleManager();
    private String invoke = Main.getBotSettings().getCommandPrefix() + "set";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "``" + invoke + " <channel> <option> <new configuration>``. Options are 'msg' " +
                "(announcement message format), chan (announcement channel), zone (timezone to use), and clock " +
                "('12' to use am/pm or '24' for full form). When creating a custom announcement message format the " +
                "'%' acts as a delimiter for entry parameters such as the title or a comment. %t will cause the entry" +
                " title to be inserted, %c[1-9] will cause the nth comment for the entry to be inserted, %a will insert" +
                " 'begins' or 'ends', and %% will insert %.";

        String USAGE_BRIEF = "``" + invoke + "`` - Used to set guild-wide schedule botSettings.";

        String EXAMPLES = "Ex1: ``" + invoke + " #events_channel msg \"@here The event %t %a.\"``\n" +
                "Ex2: ``" + invoke + " #events_channel msg \"@everyone %c1\"``\n" +
                "Ex3: ``" + invoke + " #events_channel chan \"event announcements\"``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        if (args.length < 3)
            return "Not enough arguments";

        if( !chanSetManager.idIsInMap(args[index].replace("<#","").replace(">","")) )
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild. " +
                    "Try using the ``init`` command!";

        index++;

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

            case "style" :
                if( !args[index].equals("embed") && !args[index].equals("plain"))
                    return "Argument **" + args[index] +  "** is not a valid option. Argument may be **embed** " +
                            "or **plain**";
                break;
            case "sync" :
                break;
            default:
                return "Argument **" + args[index-1] + "** is not a configurable setting!";
        }
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        int index = 0;
        TextChannel scheduleChan = event.getGuild()
                .getTextChannelById(args[index++].replace("<#","").replace(">",""));

        switch (args[index++])
        {
            case "msg":
                chanSetManager.setAnnounceFormat(scheduleChan.getId(), args[index]);
                break;

            case "chan":
                TextChannel tmp = event.getGuild()
                        .getTextChannelById(args[index].replace("<#","").replace(">",""));
                String chanName = (tmp==null) ? args[index] : tmp.getName();

                chanSetManager.setAnnounceChan(scheduleChan.getId(), chanName);
                break;

            case "zone":
                ZoneId zone = ZoneId.of(args[index]);
                chanSetManager.setTimeZone(scheduleChan.getId(), zone);

                // edit and reload the schedule
                for (Integer id : schedManager.getEntriesByChannel(scheduleChan.getId()))
                {
                    ScheduleEntry se = schedManager.getEntry(id);
                    se.setZone(zone);
                    schedManager.reloadEntry(se.getId());
                }
                break;

            case "clock":
                chanSetManager.setClockFormat(scheduleChan.getId(), args[index]);

                // reload the schedule
                for (Integer id : schedManager.getEntriesByChannel(scheduleChan.getId()))
                {
                    schedManager.reloadEntry(id);
                }
                break;


            case "style":
                chanSetManager.setStyle(scheduleChan.getId(), args[index]);

                // reload the schedule
                for (Integer id : schedManager.getEntriesByChannel(scheduleChan.getId()))
                {
                    schedManager.reloadEntry(id);
                }
                break;
            case "sync":
                if( Main.getCalendarConverter().checkValidAddress(args[index]) )
                    chanSetManager.setAddress(scheduleChan.getId(), args[index]);
                else
                    chanSetManager.setAddress(scheduleChan.getId(), "off");
        }
    }
}
