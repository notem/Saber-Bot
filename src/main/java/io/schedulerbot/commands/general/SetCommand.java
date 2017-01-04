package io.schedulerbot.commands.general;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.core.settings.GuildSettingsManager;
import io.schedulerbot.core.schedule.ScheduleEntry;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.ZoneId;

/**
 */
public class SetCommand implements Command
{
    private GuildSettingsManager guildSettingsManager = Main.guildSettingsManager;

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
        if( args.length < 2 )
        {
            return "Not enough arguments";
        }
        switch( args[0] )
        {
            case "msg" :
                break;

            case "chan" :
                break;

            case "zone" :
                if( ZoneId.of(args[1].replace("\"","")) == null )
                    return "Invalid argument \"" + args[1] +  "\"";
                break;

            case "clock" :
                if( !args[1].replace("\"","").equals("24") && !args[1].replace("\"","").equals("12"))
                    return "Invalid argument \"" + args[1] +  "\"";
                break;
        }
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        switch( args[0] )
        {
            case "msg" :
                String msg = "";
                for( int i = 1 ; i<args.length ; i++ )
                {
                    msg += args[i].replace("\"","");
                    if( i+1 != args.length )
                    {
                        msg += " ";
                    }
                }
                guildSettingsManager.setGuildAnnounceFormat( event.getGuild().getId(), msg );
                break;

            case "chan" :
                String chan = "";
                for( int i = 1 ; i<args.length ; i++ )
                {
                    chan += args[i].replace("\"","");
                    if( i+1 != args.length )
                    {
                        chan += " ";
                    }
                }
                guildSettingsManager.setGuildAnnounceChan( event.getGuild().getId(), chan );
                break;

            case "zone" :
                ZoneId zone = ZoneId.of(args[1].replace("\"",""));
                guildSettingsManager.setGuildTimeZone( event.getGuild().getId(), zone );
                for( Integer id : Main.scheduleManager.getEntriesByGuild(event.getGuild().getId()) )
                {
                    ScheduleEntry se = Main.scheduleManager.getEntry( id );
                    se.eStart.withZoneSameLocal( zone );
                    se.eEnd.withZoneSameLocal( zone );
                    Main.scheduleManager.reloadEntry(se.eID);
                }
                break;

            case "clock" :
                guildSettingsManager.setGuildClockFormat( event.getGuild().getId(), args[1].replace("\"","") );
                for( Integer id : Main.scheduleManager.getEntriesByGuild(event.getGuild().getId()) )
                {
                    ScheduleEntry se = Main.scheduleManager.getEntry(id);
                    Main.scheduleManager.reloadEntry(se.eID);
                }
                break;
        }

    }
}
