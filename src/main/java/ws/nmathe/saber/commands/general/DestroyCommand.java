package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 */
public class DestroyCommand implements Command
{
    private String prefix = Main.getBotSettings().getCommandPrefix();
    private String scheduleChan = Main.getBotSettings().getScheduleChan();
    private ScheduleManager schedManager = Main.getScheduleManager();

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "\nCalling **!destroy <ID>** will end the event with <ID>" +
                " prematurely. If **all** is used instead of the event ID, all scheduled events will be destroyed." +
                "\nEx1: **!destroy 084c**\nEx2: **!destroy all**";

        String USAGE_BRIEF = "**" + prefix + "destroy** - Removes an entry from " +
                scheduleChan + ".";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n" + USAGE_EXTENDED;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        if(args.length>1 || args.length==0)
            return "Not enough arguments";
        if( args[0].equals("all") )
        {
            return "";
        }
        if( !VerifyUtilities.verifyHex( args[0] ) )
        {
            return "ID \"" + args[0] + "\" is not a value ID value";
        }
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        Guild guild = event.getGuild();

        if( args[0].equals("all") )
        {
            ArrayList<Integer> ent = schedManager.getEntriesByGuild( guild.getId() );
            if( ent == null )
            {
                MessageUtilities.sendMsg(
                        "Your guild has no entries on the schedule.",
                        event.getChannel(), null);
                return;
            }
            ArrayList<ScheduleEntry> entries = ent.stream()
                    .map(id -> schedManager.getEntry(id)).collect(Collectors.toCollection(ArrayList::new));
            for (ScheduleEntry entry : entries)
            {
                if (entry != null)
                {
                    synchronized( schedManager.getScheduleLock() )
                    {
                        schedManager.removeEntry(entry.eID);
                    }

                    // delete the old message
                    MessageUtilities.deleteMsg(entry.eMsg, null);
                }
            }
        }

        else
        {
            Integer entryId = Integer.decode("0x" + args[0]);
            ScheduleEntry entry = schedManager.getEntry(entryId);

            if (entry == null)
            {
                String msg = "There is no event entry with ID " + args[0] + ".\"";
                MessageUtilities.sendMsg(msg, event.getChannel(), null);
                return;
            }

            synchronized( schedManager.getScheduleLock() )
            {
                schedManager.removeEntry(entryId);
            }

            // delete the old entry
            MessageUtilities.deleteMsg(entry.eMsg, null);

        }
    }
}
