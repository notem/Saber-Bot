package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.GuildUtilities;

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
        synchronized( Main.getScheduleManager().getScheduleLock() )
        {
            // clear the guild mapping
            for (Integer id : Main.getScheduleManager().getEntriesByGuild(event.getGuild().getId()))
            {
                Main.getScheduleManager().removeEntry(id);
            }

            // reload all channels
            GuildUtilities.loadScheduleChannels(event.getGuild());
        }
    }
}
