package io.schedulerbot.commands;

import io.schedulerbot.Main;
import io.schedulerbot.utils.BotConfig;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;

/**
 * file: CreateCommand.java
 *
 * CreateCommand places a new event on the EVENT_CHAN text channel
 * Note that the actual ScheduledEvent thread has not yet been created.
 */
public class CreateCommand implements Command
{
    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        int counter = 0;
        if(!args[counter].startsWith("\""))
            return false;
        if(args[counter].endsWith("\""))
            counter++;
        else while( args[counter].endsWith("\"") )
        {
            counter++;
            if( counter >= args.length )
                return false;
        }
        // TODO more checking
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        String eventName = "";
        String eventStart;
        String eventEnd;
        String eventDesc = "";

        int count = 0;
        eventName += args[count++].replace("\""," ");
        while( count < args.length - 2 )
        {
            eventName += " " + args[count].replace("\"","");
            if( args[count++].endsWith("\"") )
                break;
        }

        eventStart = args[count++];
        eventEnd = args[count++];

        if( count < args.length )
            eventDesc += args[count++].replace("\"","");
        while( count < args.length )
        {
            eventDesc += " " + args[count].replace("\"","");
            if( args[count++].endsWith("\"") )
                break;
        }

        // TODO, needs better formatting
        String msg = eventName + "\nbegins " + eventStart + ", ends " + eventEnd + "\n" + eventDesc + "\n";

        try
        {
            event.getGuild().getTextChannelsByName(BotConfig.EVENT_CHAN, false).get(0).sendMessage(msg).queue();
        }
        catch( PermissionException e )
        {
            Main.handleException( e );
        }
    }
}
