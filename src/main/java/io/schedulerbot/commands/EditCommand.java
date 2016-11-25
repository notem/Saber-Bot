package io.schedulerbot.commands;

import io.schedulerbot.Main;
import io.schedulerbot.utils.BotConfig;
import io.schedulerbot.utils.EventEntryParser;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;

import java.time.LocalDate;
import java.util.ArrayList;

/**
 */
public class EditCommand implements Command
{
    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        // TODO actual verification of input
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        // parse argument into the event entry's ID
        Integer entryID = Integer.decode( "0x" + args[0] );

        // check if the entry exists
        if( !Main.schedule.containsKey(entryID) )
        {
            String msg = "There is no event entry with ID " + entryID + ".";
            event.getChannel().sendMessage( msg ).queue();
            return;
        }

        String title = Main.schedule.get(entryID).eTitle;
        ArrayList<String> comments = Main.schedule.get(entryID).eComments;
        String start = Main.schedule.get(entryID).eStart;
        String end = Main.schedule.get(entryID).eEnd;
        int repeat = Main.schedule.get(entryID).eRepeat;
        LocalDate date = Main.schedule.get(entryID).eDate;

        switch( args[1] )
        {
            case "comment":
                if( args[2].equals("add") )
                {
                    String comment = "";
                    for( int i = 3; i < args.length; i++ )
                    {
                        comment += args[i].replace("\"", "");
                        if (!(i == args.length - 1))
                            comment += " ";
                    }
                    comments.add( comment );
                }
                else if( args[2].equals("remove"))
                {
                    String comment = "";
                    for( int i = 3; i < args.length; i++ )
                    {
                        comment += args[i].replace("\"", "");
                        if (!(i == args.length - 1))
                            comment += " ";
                    }
                    comments.remove( comment );
                }
                break;

            case "start":
                start = args[2];
                break;

            case "end":
                end = args[2];
                break;

            case "title":
                title = "";
                for( int i = 2; i < args.length; i++ )
                {
                    title += args[i].replace("\"", "");
                    if(!(i == args.length-1))
                        title += " ";
                }
                break;

            case "date":
                //option = 5;
                break;

            case "repeat":
                if( Character.isDigit( args[2].charAt(0) ))
                    repeat = Integer.parseInt(args[2]);
                else if( args[2].equals("daily") )
                    repeat = 1;
                else if( args[2].equals("weekly") )
                    repeat = 2;
                break;
        }

        // interrupt the schedule thread, causing the message to be deleted and the thread killed.
        Main.schedule.get(entryID).thread.interrupt();

        // generate the new event entry message
        String msg = EventEntryParser.generate( title, start, end, comments, repeat, date );

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
