package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.ZoneId;
import java.util.Set;

/**
 */
public class TimeZonesCommand implements Command
{
    @Override
    public String help(boolean brief)
    {
        String invoke = Main.getBotSettings().getCommandPrefix() + "zones";

        String USAGE_EXTENDED = "``" + invoke + " <filter>`` to list timezone options. " +
                "A search filter argument is required (eg. ``us``).";

        String USAGE_BRIEF = "``" + invoke + "`` - show available timezones";

        String EXAMPLES = "Ex: ``" + invoke + " america``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        return args.length==1 ? "" : "Incorrect amount of arguments!";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        Set<String> zones = ZoneId.getAvailableZoneIds();

        String msg = "**Available options for time zones**\n";
        for (String zone : zones )
        {
            if (msg.length() > 1900)
            {
                MessageUtilities.sendMsg(msg, event.getChannel(), null);
                try
                {Thread.sleep(1000);}
                catch( Exception ignored )
                { }
                msg = "**continued. . .**\n";
            }
            if( args.length == 0 )
                msg += "  " + zone + "\n";
            else if( zone.toUpperCase().contains(args[0].toUpperCase()) )
                msg += "  " + zone + "\n";
        }

        MessageUtilities.sendMsg(msg, event.getChannel(), null);
    }
}
