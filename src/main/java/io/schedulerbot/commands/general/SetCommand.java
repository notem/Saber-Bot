package io.schedulerbot.commands.general;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.core.GuildSettingsManager;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.ZoneId;

/**
 */
public class SetCommand implements Command
{
    private GuildSettingsManager guildSettingsManager = Main.guildSettingsManager;

    private static final String USAGE_EXTENDED = "**!set [option] [new configuration]**";

    private static final String USAGE_BRIEF = "Used to set guild-wide schedule settings.";

    @Override
    public String help(boolean brief)
    {
        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n" + USAGE_EXTENDED;
    }

    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        return true;
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
                guildSettingsManager.setGuildTimeZone( event.getGuild().getId(), ZoneId.of(args[1].replace("\"","")) );
                break;

            case "clock" :
                guildSettingsManager.setGuildClockFormat( event.getGuild().getId(), args[1].replace("\"","") );
                break;
        }

    }
}
