package ws.nmathe.saber.commands.admin;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.core.schedule.ScheduleEntryParser;
import ws.nmathe.saber.utils.MessageUtilities;
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
    public String verify(String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        for( Integer id : Main.getScheduleManager().getAllEntries() )
        {
            ScheduleEntry se = Main.getScheduleManager().getEntry(id);
            String msg = ScheduleEntryParser.generate(se.getTitle(), se.getStart(), se.getEnd(), se.getComments(),
                    se.getRepeat(), se.getId(), se.getMessage().getChannel().getId() );

            MessageUtilities.editMsg( msg, se.getMessage(), null );
            se.adjustTimer();
        }
    }
}
