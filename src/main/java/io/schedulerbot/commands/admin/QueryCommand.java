package io.schedulerbot.commands.admin;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.utils.EventEntry;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.ArrayList;

/**
 */
public class QueryCommand implements Command
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
        String msg = "You didn't supply me with a valid option to query.";
        int op;
        switch( args[0] )
        {
            case "guild":
                op = 1;
                break;
            case "entry":
                op = 2;
                break;
            default:
                op = 0;
                break;
        }
        if( op == 1 )
        {
            ArrayList<Integer> entries = Main.getEntriesByGuild( args[1] );
            if( entries == null )
                msg = "Guild " + args[1] + " has no entries.";
            else
            {
                msg = "Guild " + args[1] + " has " + entries.size() + " entries: ";
                for( Integer entry : entries )
                {
                    msg += Integer.toHexString( entry ) + ", ";
                }
            }
        }
        if( op == 2 ) {
            EventEntry entry = Main.getEventEntry(Integer.decode("0x" + args[1]));

            if (entry == null) {
                msg = "Entry " + args[1] + " does not exist.";
            } else {
                msg = "Entry " + Integer.decode( "0x" + entry.eID ) + " belongs to " +
                        entry.eMsg.getGuild().getName() + "(" + entry.eMsg.getGuild().getId() + ")" + ".\n" +
                        "\t\tTitle = '" + entry.eTitle + "'\n\t\tDate = '" + entry.eDate + "'\n\t\tStart = '" +
                        entry.eStart + "'\n\t\tEnd = '" + entry.eEnd + "'\n\t\tRepeat = '" + entry.eRepeat + "'\n" +
                        "\t\tZone = '" + "EST" + "'\n\t\tComments = ";
                for (String comment : entry.eComments) {
                    msg += "\"" + comment + "\"\n\t\t\t";
                }
            }
        }

        MessageUtilities.sendPrivateMsg( msg, event.getAuthor() );
    }
}
