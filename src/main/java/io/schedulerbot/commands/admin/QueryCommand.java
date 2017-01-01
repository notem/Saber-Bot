package io.schedulerbot.commands.admin;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.core.settings.GuildSettingsManager;
import io.schedulerbot.core.schedule.ScheduleEntry;
import io.schedulerbot.core.schedule.ScheduleManager;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.ArrayList;

/**
 */
public class QueryCommand implements Command
{
    private static ScheduleManager scheduleManager = Main.scheduleManager;
    //private static GuildSettingsManager guildSettingsManager = Main.guildSettingsManager;

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
            case "finebuf" :
                op = 3;
                break;
            case "coursebuf" :
                op = 4;
                break;
            case "entries" :
                op = 5;
                break;
            default:
                op = 0;
                break;
        }

        if( op == 1 )
        {
            ArrayList<Integer> entries = scheduleManager.getEntriesByGuild( args[1] );
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
        if( op == 2 )
        {
            ScheduleEntry entry = scheduleManager.getEntry(Integer.decode("0x" + args[1]));

            if (entry == null)
            {
                msg = "Entry " + args[1] + " does not exist.";
            }
            else
            {
                msg = "Entry " + Integer.toHexString( entry.eID ) + " belongs to " +
                        entry.eMsg.getGuild().getName() + "(" + entry.eMsg.getGuild().getId() + ")" + ".\n" +
                        "\t\tTitle = '" + entry.eTitle + "'\n" +
                        "\t\tStart = " + entry.eStart + "'\n" +
                        "\t\tEnd = '" + entry.eEnd + "'\n" +
                        "\t\tRepeat = '" + entry.eRepeat + "'\n" +
                        "\t\tComments = ";
                for (String comment : entry.eComments)
                {
                    msg += "\"" + comment + "\"\n\t\t\t";
                }
            }
        }
        if( op == 3 )
        {
            msg = "**Current contents of the fine timer buffer**\n";
            for( ScheduleEntry se : Main.scheduleManager.getFineTimerBuff() )
            {
                if( msg.length() >= 1900 )
                {
                    MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
                    msg = "**continued**\n";
                }
                msg += Integer.toHexString(se.eID) + "\n";
            }
        }
        if( op == 4 )
        {
            msg = "**Current contents of the coarse timer buffer**\n";
            for( ScheduleEntry se : Main.scheduleManager.getCoarseTimerBuff() )
            {
                if( msg.length() >= 1900 )
                {
                    MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
                    msg = "**continued**\n";
                }
                msg += Integer.toHexString(se.eID) + "\n";
            }
        }
        if( op == 5 )
        {
            msg = "**Current contents of the entries map**\n";
            for( Integer Id : Main.scheduleManager.getAllEntries() )
            {
                if( msg.length() >= 1900 )
                {
                    MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
                    msg = "**continued**\n";
                }
                msg += Integer.toHexString(Id) + "\n";
            }
        }

        MessageUtilities.sendPrivateMsg( msg, event.getAuthor(), null );
    }
}