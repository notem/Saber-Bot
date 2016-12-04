package io.schedulerbot.commands.general;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.utils.BotConfig;
import io.schedulerbot.utils.EventEntry;
import io.schedulerbot.utils.MessageUtilities;
import io.schedulerbot.utils.VerifyUtilities;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

/**
 */
public class DestroyCommand implements Command
{
    private static final String USAGE_EXTENDED = "\nCalling **!destroy <ID>** will end the event with <ID>" +
            " prematurely. If **all** is used instead of the event ID, all scheduled events will be destroyed." +
            "\nEx1: **!destroy 084c**\nEx2: **!destroy all**";

    private static final String USAGE_BRIEF = "**" + BotConfig.PREFIX + "destroy** - Removes an entry from " +
            BotConfig.EVENT_CHAN + ", sending an event ended early or canceled announcement.";

    @Override
    public String help(boolean brief)
    {
        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n" + USAGE_EXTENDED;
    }

    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        if(args.length>1 || args.length==0)
            return false;
        if( args[0].equals("all") )
        {
            return true;
        }
        if( !VerifyUtilities.verifyHex( args[0] ) )
        {
            return false;
        }
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        Guild guild = event.getGuild();

        if( args[0].equals("all") )
        {
            ArrayList<Integer> ent = Main.getEntriesByGuild( guild.getId() );
            if( ent == null )
            {
                MessageUtilities.sendMsg(
                        "Your guild has no entries on the schedule.",
                        event.getChannel(), null);
                return;
            }
            ArrayList<Integer> entries = new ArrayList<>();
            entries.addAll( ent );
            for (Integer eId : entries)
            {
                EventEntry entry = Main.getEventEntry(eId);
                if (entry != null)
                {
                    // create the announcement message strings
                    String cancelMsg = "@everyone The event **" + entry.eTitle
                            + "** has been cancelled.";
                    String earlyMsg = "@everyone The event **" + entry.eTitle
                            + "** has ended early.";

                    // compare the current time to the start time
                    int dif = entry.eStart.toSecondOfDay() - LocalTime.now().toSecondOfDay();

                    // if the difference is less than 0 the event was ended early
                    if (dif < 0 && entry.eDate.equals(LocalDate.now()))
                        MessageUtilities.sendAnnounce(earlyMsg, guild, null);

                        // otherwise event was canceled before it began
                    else
                        MessageUtilities.sendAnnounce(cancelMsg, guild, null);

                    // interrupt the entriesGlobal thread, causing the message to be deleted and the thread killed.
                    entry.thread.interrupt();

                    // remove the entry from global
                    Main.removeId( eId, guild.getId() );

                    // delete the old message
                    MessageUtilities.deleteMsg(entry.eMsg, null);
                }
            }
        }

        else
        {
            Integer entryId = Integer.decode("0x" + args[0]);
            EventEntry entry = Main.getEventEntry(entryId);

            if (entry == null)
            {
                String msg = "There is no event entry with ID " + args[0] + ".\"";
                MessageUtilities.sendMsg(msg, event.getChannel(), null);
                return;
            }

            // create the announcement message strings
            String cancelMsg = "@everyone The event **" + entry.eTitle
                    + "** has been cancelled.";
            String earlyMsg = "@everyone The event **" + entry.eTitle
                    + "** has ended early.";

            // compare the current time to the start time
            int dif = entry.eStart.toSecondOfDay() - LocalTime.now().toSecondOfDay();

            // if the difference is less than 0 the event was ended early
            if (dif < 0 && entry.eDate.equals(LocalDate.now()))
                MessageUtilities.sendAnnounce(earlyMsg, guild, null);

                // otherwise event was canceled before it began
            else
                MessageUtilities.sendAnnounce(cancelMsg, guild, null);

            // interrupt the entriesGlobal thread, causing the message to be deleted and the thread killed.
            entry.thread.interrupt();

            // remove the entry from global
            Main.removeId( entryId, guild.getId() );

            // delete the old entry
            MessageUtilities.deleteMsg(entry.eMsg, null);
        }
    }
}
