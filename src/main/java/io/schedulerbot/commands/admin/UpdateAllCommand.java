package io.schedulerbot.commands.admin;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.core.ScheduleEntry;
import io.schedulerbot.core.ScheduleEntryParser;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 */
public class UpdateAllCommand implements Command
{
    @Override
    public String help(boolean brief)
    {
        return null;
    }

    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        for( ScheduleEntry se : Main.scheduleManager.getAllEntries() )
        {
            String msg = ScheduleEntryParser.generate(se.eTitle, se.eStart, se.eEnd, se.eComments, se.eRepeat, se.eID, se.eMsg.getGuild().getId() );
            MessageUtilities.editMsg( msg, se.eMsg, null );
        }
    }
}
