package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;

/**
 */
public class SyncCommand implements Command
{

    @Override
    public String help(boolean brief)
    {
        return null;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        if( args.length < 2 )
            return "Not enough arguments";
        if( args.length > 2)
            return "Too many arguments";
        if( event.getGuild().getTextChannelsByName(args[0], false).isEmpty() )
            return "Channel **" + args[0] + "** does not exist";
        if( !Main.getCalendarConverter().checkValidAddress( args[1] ) )
            return "Cannot connect to google calendar address **" + args[2] + "**";

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        TextChannel channel = event.getGuild().getTextChannelsByName( args[0], false ).get(0);
        try
        {
            Main.getCalendarConverter().syncCalendar(args[1], channel);
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }
}
