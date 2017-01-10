package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.ZoneId;
import java.util.Collection;

/**
 */
public class SetCommand implements Command
{
    private static ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();
    private static ScheduleManager schedManager = Main.getScheduleManager();

    private static String invoke = Main.getBotSettings().getCommandPrefix() + "set";

    private static final String USAGE_EXTENDED = "**!set [option] \"new configuration\"**. Options are 'msg' " +
            "(announcement message format), chan (announcement channel), zone (timezone to use), and clock " +
            "('12' to use am/pm or '24' for full form). When creating a custom announcement message format the " +
            "'%' acts as a delimiter for entry parameters such as the title or a comment. %t will cause the entry" +
            " title to be inserted, %c[1-9] will cause the nth comment for the entry to be inserted, %a will insert" +
            " 'begins' or 'ends', and %% will insert %.";

    private static final String USAGE_BRIEF = "**" + invoke + "** - Used to set guild-wide schedule botSettings.";

    private static final String EXAMPLES = "Ex1: **" + invoke + " msg \"@here The event %t %a.\"**\n" +
            "Ex2: **" + invoke + " msg \"@everyone %c1\"" +
            "Ex3: **" + invoke + " chan \"event announcements\"**";

    @Override
    public String help(boolean brief)
    {
        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        if( args.length < 3 )
        {
            return "Not enough arguments";
        }

        int index = 0;
        Collection<TextChannel> chans = event.getGuild().getTextChannelsByName(args[index], true);
        if( chans.isEmpty() )
            return "Schedule channel \"" + args[index] + "\" does not exist";
        if( chans.size() > 1 )
            return "Duplicate schedule channels with name \"" + args[index] + "\"";
        index++;

        switch( args[index] )
        {
            case "msg" :
                break;

            case "chan" :
                break;

            case "zone" :
                if( ZoneId.of(args[index+1].replace("\"","")) == null )
                    return "Invalid zone argument \"" + args[index+1] +  "\"";
                break;

            case "clock" :
                if( !args[index+1].replace("\"","").equals("24") && !args[index+1].replace("\"","").equals("12"))
                    return "Invalid time argument \"" + args[index+1] +  "\"";
                break;
        }
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        int index = 0;
        TextChannel scheduleChan = event.getGuild().getTextChannelsByName( args[index], true ).get(0);
        index++;

        switch (args[index++])
        {
            case "msg":
                String msg = "";
                for (int i = index; i < args.length; i++)
                {
                    msg += args[i].replace("\"", "");
                    if (i + 1 != args.length)
                    {
                        msg += " ";
                    }
                }
                chanSetManager.setAnnounceFormat(scheduleChan.getId(), msg);
                break;

            case "chan":
                String chan = "";
                for (int i = index; i < args.length; i++)
                {
                    chan += args[i].replace("\"", "");
                    if (i + 1 != args.length)
                    {
                        chan += " ";
                    }
                }
                chanSetManager.setAnnounceChan(scheduleChan.getId(), chan);
                break;

            case "zone":
                ZoneId zone = ZoneId.of(args[index].replace("\"", ""));
                chanSetManager.setTimeZone(scheduleChan.getId(), zone);

                // edit and reload the schedule
                for (Integer id : schedManager.getEntriesByChannel(scheduleChan.getId()))
                {
                    ScheduleEntry se = schedManager.getEntry(id);
                    se.eStart.withZoneSameLocal(zone);
                    se.eEnd.withZoneSameLocal(zone);
                    schedManager.reloadEntry(se.eID);
                }
                break;

            case "clock":
                chanSetManager.setClockFormat(scheduleChan.getId(), args[index].replace("\"", ""));

                // reload the schedule
                for (Integer id : schedManager.getEntriesByChannel(scheduleChan.getId()))
                {
                    ScheduleEntry se = schedManager.getEntry(id);
                    schedManager.reloadEntry(se.eID);
                }
                break;
        }
    }
}
