package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneId;
import java.util.Set;

/**
 * used for generating the list of valid timezone strings
 */
public class TimeZonesCommand implements Command
{
    @Override
    public String name()
    {
        return "zones";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - show available timezones";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.MISC);

        String cat1 = "- Usage\n" + head + " <filter>";
        String cont1 =  "The zones command will provide a list of valid timezone strings for schedule configuration." +
                        "\nA search filter argument is required (ex. ``us``).";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head+" america");
        info.addUsageExample(head+" tokyo");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
    {
        String head = prefix + this.name();
        return args.length==1 ? "" : "Incorrect amount of arguments!" +
                "\nUse ``" + head + " <filter>``";
    }

    @Override
    public void action(String prefix, String[] args, EventCompat event)
    {
        Set<String> zones = ZoneId.getAvailableZoneIds();
        StringBuilder msg = new StringBuilder("**Available options for time zones**\n");
        for (String zone : zones)
        {
            if (msg.length() > 1900)
            {
                MessageUtilities.sendMsg(msg.toString(), event.getChannel(), null);
                msg = new StringBuilder("**continued. . .**\n");

                // sleep for a second so as to not get rate limited
                try {Thread.sleep(1000);}
                catch (Exception ignored) { }
            }
            if (args.length == 0)
            {
                msg.append("  ").append(zone).append("\n");
            }
            else if (zone.toUpperCase().contains(args[0].toUpperCase()))
            {
                msg.append("  ").append(zone).append("\n");
            }
        }
        MessageUtilities.sendMsg(msg.toString(), event.getChannel(), null);
    }
}
