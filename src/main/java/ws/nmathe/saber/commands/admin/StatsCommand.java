package ws.nmathe.saber.commands.admin;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 */
public class StatsCommand implements Command
{
    @Override
    public String help(boolean brief)
    {
        return null;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        String msg = "```python\n";
        msg += "Entries: " + Main.getScheduleManager().getAllEntries().size() + "\n";
        msg += "Guilds: " + Main.getBotJda().getGuilds().size() + "\n";
        Runtime rt = Runtime.getRuntime();
        msg += "Memory-total: " +rt.totalMemory()/1024/1024 + " MB\n" +
                "      -free : " + rt.freeMemory()/1024/1024 + " MB\n" +
                "      -max  : " + rt.maxMemory()/1024/1024 + " MB\n";
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        msg += "  Uptime: " + rb.getUptime()/1000/60 + " minute(s)";
        msg += "```";

        MessageUtilities.sendPrivateMsg( msg, event.getAuthor(), null );
    }
}
