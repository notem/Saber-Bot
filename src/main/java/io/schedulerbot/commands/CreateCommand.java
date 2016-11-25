package io.schedulerbot.commands;

import io.schedulerbot.Main;
import io.schedulerbot.utils.BotConfig;
import io.schedulerbot.utils.EventEntryParser;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;

import java.util.ArrayList;

/**
 * file: CreateCommand.java
 *
 * CreateCommand places a new event on the EVENT_CHAN text channel
 * Note that the actual EventEntry thread has not yet been created.
 */
public class CreateCommand implements Command
{
    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        // TODO do some actually verification
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        String eTitle = "";
        String eStart = "00:00";    // initialized just in case verify failed it's duty
        String eEnd = "00:00";      //
        ArrayList<String> eComments = new ArrayList<String>();

        String buffComment = "";    // String to generate comments strings to place in eComments

        boolean flag1 = false;  // true if 'eTitle' has been grabbed from args
        boolean flag2 = false;  // true if 'eStart' has been grabbed from args
        boolean flag3 = false;  // true if 'eEnd' has been grabbed from args
        boolean flag4 = false;  // true if a comment argument was found and is being processed,
                                // false when the last arg forming the comment is found

        for( String arg : args )
        {
            if(!flag1)
            {
                if( arg.endsWith("\"") )
                {
                    flag1 = true;
                    eTitle += arg.replace("\"", "");
                }
                else
                    eTitle += arg.replace("\"", "") + " ";
            }
            else if(!flag2)
            {
                flag2 = true;
                eStart = arg;
            }
            else if(!flag3)
            {
                flag3 = true;
                eEnd = arg;
            }
            else
            {
                if( arg.startsWith("\"") )
                    flag4 = true;
                if( flag4 )
                    buffComment += arg.replace("\"","");
                if( arg.endsWith("\"") )
                {
                    flag4 = false;
                    eComments.add(buffComment);
                    buffComment = "";
                }
                else
                    buffComment += " ";

            }

        }

        // generate the event entry message
        String msg = EventEntryParser.generate( eTitle, eStart, eEnd, eComments );

        try
        {
            event.getGuild().getTextChannelsByName(BotConfig.EVENT_CHAN, false).get(0).sendMessage(msg).queue();
        }
        catch( PermissionException e )
        {
            Main.handleException( e, event );
        }
    }
}
