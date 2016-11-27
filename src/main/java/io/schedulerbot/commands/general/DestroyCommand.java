package io.schedulerbot.commands.general;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.utils.BotConfig;
import io.schedulerbot.utils.EventEntry;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 */
public class DestroyCommand implements Command
{
    private static final String USAGE_EXTENDED = "\nCalling **!destroy <ID>** will end the event with <ID>" +
            " prematurely.\nEx: **!destroy 084c**";

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
        // TODO
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        // parse argument into the event entry's ID
        Integer entryId = Integer.decode( "0x" + args[0] );

        EventEntry entry = Main.getEventEntry( entryId );
        // check if the entry exists
        if( entry == null )
        {
            String msg = "There is no event entry with ID " + args[0] + ".\"";
            MessageUtilities.sendMsg( msg, event.getChannel() );
            return;
        }

        // create the announcement message strings
        Guild guild = entry.eMsg.getGuild();
        String cancelMsg = "@everyone The event **" + entry.eTitle
                + "** has been cancelled.";
        String earlyMsg = "@everyone The event **" + entry.eTitle
                + "** has ended early.";

        // convert the start time into an integer as seconds since 00:00
        Integer startH = Integer.parseInt(entry.eStart.split(":")[0]);
        Integer startM = Integer.parseInt(entry.eStart.split(":")[1]);
        Integer start = startH*60*60 + startM*60;

        // compare the current time to the start time
        LocalTime now = LocalTime.now();
        Integer dif = start - (now.getHour()*60*60 + now.getMinute()*60 + now.getSecond());

        // if the difference is less than 0 the event was ended early
        if(dif < 0 && entry.eDate.equals(LocalDate.now()))
            MessageUtilities.sendAnnounce( earlyMsg, guild );

        // otherwise event was canceled before it began
        else
            MessageUtilities.sendAnnounce( cancelMsg, guild );

        // interrupt the entriesGlobal thread, causing the message to be deleted and the thread killed.
        entry.thread.interrupt();
    }
}
