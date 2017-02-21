package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.GuildUtilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Reloads the schedule channel.
 */
public class InitCommand implements Command
{
    private String invoke = Main.getBotSettings().getCommandPrefix() + "init";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "Using ``" + invoke + "`` will reload all valid schedule channels. If the bot doesn't " +
                "seem to be recognizing your schedule channels, try using this command. Or, if you schedule " +
                "entries are missing the section which tells the time remaining until the event, this command should " +
                "fix the issue.";

        String USAGE_BRIEF = "``" + invoke + "`` - reloads schedule channels. Use this when something is odd.";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED;
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
