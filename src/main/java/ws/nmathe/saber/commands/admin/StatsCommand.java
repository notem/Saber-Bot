package ws.nmathe.saber.commands.admin;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.ChannelType;
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
    public String name()
    {
        return "stats";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        return null;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        JDA.ShardInfo info = Main.getBotJda().getShardInfo();
        Runtime rt = Runtime.getRuntime();
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();

        String msg = "```python\n" +
                "\"Database\"\n" +
                "     Entries: " + Main.getDBDriver().getEventCollection().count() + "\n" +
                "   Schedules: " + Main.getDBDriver().getScheduleCollection().count() + "\n" +
                "\n\"Sharding\"\n" +
                "     ShardId: " + info.getShardId() + "/" + info.getShardTotal() + "\n" +
                "      Guilds: " + Main.getBotJda().getGuilds().size() + "\n" +
                "       Users: " + Main.getBotJda().getUsers().size() + "\n" +
                "\n\"Application\"\n" +
                "Memory-total: " +rt.totalMemory()/1024/1024 + " MB\n" +
                "      -free : " + rt.freeMemory()/1024/1024 + " MB\n" +
                "      -max  : " + rt.maxMemory()/1024/1024 + " MB\n" +
                "     Threads: " + Thread.activeCount() + "\n" +
                "      Uptime: " + rb.getUptime()/1000/60 + " minute(s)" +
                "```";

        if(event.isFromType(ChannelType.PRIVATE))
        {
            MessageUtilities.sendPrivateMsg( msg, event.getAuthor(), null );
        }
        else
        {
            MessageUtilities.sendMsg( msg, event.getTextChannel(), null );
        }
    }
}
