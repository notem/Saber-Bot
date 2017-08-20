package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.ZoneId;
import java.util.Set;

/**
 */
public class TimeZonesCommand implements Command
{
    @Override
    public String name()
    {
        return "zones";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " <filter>```\n" +
                "The zones command will provide a list of valid timezone strings for schedule configuration." +
                "\nA search filter argument is required (eg. ``us``).";

        String USAGE_BRIEF = "``" + head + "`` - show available timezones";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + head + " america``" +
                "\n``" + head + " tokyo``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();

        return args.length==1 ? "" : "Incorrect amount of arguments! Use ``" + head + " <filter>``";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        try
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
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
