package io.schedulerbot.commands;

import io.schedulerbot.Main;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 */
public class DestroyCommand implements Command
{
    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        // parse argument into the event entry's ID
        Integer entryID = Integer.decode( "0x" + args[0] );

        // check if the entry exists
        if( !Main.entriesGlobal.containsKey(entryID) )
        {
            String msg = "There is no event entry with ID " + entryID + ".\"";
            Main.sendMsg( msg, event.getChannel() );
            return;
        }

        // create the announcement message strings
        Guild guild = Main.entriesGlobal.get(entryID).msgEvent.getGuild();
        String cancelMsg = "@everyone The event **" + Main.entriesGlobal.get(entryID).eTitle
                + "** has been cancelled.";
        String earlyMsg = "@everyone The event **" + Main.entriesGlobal.get(entryID).eTitle
                + "** has ended early.";

        // convert the start time into an integer as seconds since 00:00
        Integer startH = Integer.parseInt(Main.entriesGlobal.get(entryID).eStart.split(":")[0]);
        Integer startM = Integer.parseInt(Main.entriesGlobal.get(entryID).eStart.split(":")[1]);
        Integer start = startH*60*60 + startM*60;

        // interrupt the entriesGlobal thread, causing the message to be deleted and the thread killed.
        Main.entriesGlobal.get(entryID).thread.interrupt();

        // compare the current time to the start time
        LocalTime now = LocalTime.now();
        Integer dif = start - (now.getHour()*60*60 + now.getMinute()*60 + now.getSecond());

        // if the difference is less than 0 the event was ended early
        if(dif < 0 && Main.entriesGlobal.get(entryID).eDate.equals(LocalDate.now()))
            Main.sendAnnounce( earlyMsg, guild );

        // otherwise event was canceled before it began
        else
            Main.sendAnnounce( cancelMsg, guild );
    }
}
