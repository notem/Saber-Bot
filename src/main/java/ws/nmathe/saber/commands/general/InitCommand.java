package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.GuildUtilities;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class InitCommand implements Command
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
        try
        {
                // clear the guild mapping
                List<Integer> removeQueue = new ArrayList<>();
                for (Integer id : Main.getScheduleManager().getEntriesByGuild(event.getGuild().getId()))
                {
                    removeQueue.add(id);
                }
                for( Integer id : removeQueue )
                {
                    synchronized (Main.getScheduleManager().getScheduleLock())
                    {
                        Main.getScheduleManager().removeEntry(id);
                    }
                }

                // reload all channels
                GuildUtilities.loadScheduleChannels(event.getGuild());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
