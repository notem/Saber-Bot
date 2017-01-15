package ws.nmathe.saber.commands.admin;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.ArrayList;

/**
 */
public class QueryCommand implements Command
{
    private static ScheduleManager scheduleManager = Main.getScheduleManager();

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
        String msg = "You didn't supply me with a valid option to query.";
        switch( args[0] )
        {
            case "guild":
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
                break;

            case "entry":
                ScheduleEntry entry = scheduleManager.getEntry(Integer.decode("0x" + args[1]));

                if (entry == null)
                {
                    msg = "Entry " + args[1] + " does not exist.";
                }
                else
                {
                    msg = "Entry " + Integer.toHexString( entry.getId() ) + " belongs to " +
                            entry.getMessage().getGuild().getName() + "(" + entry.getMessage().getGuild().getId() + ")" + ".\n" +
                            "\t\tTitle = '" + entry.getTitle() + "'\n" +
                            "\t\tStart = " + entry.getStart() + "'\n" +
                            "\t\tEnd = '" + entry.getEnd() + "'\n" +
                            "\t\tRepeat = '" + entry.getRepeat() + "'\n" +
                            "\t\tComments = ";
                    for (String comment : entry.getComments())
                    {
                        msg += "\"" + comment + "\"\n\t\t\t";
                    }
                }
                break;

            case "finebuffer" :
                msg = "**Current contents of the fine timer buffer**\n";
                for( ScheduleEntry se : scheduleManager.getFineTimerBuff() )
                {
                    if( msg.length() >= 1900 )
                    {
                        MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
                        msg = "**continued**\n";
                    }
                    msg += Integer.toHexString(se.getId()) + "\n";
                }
                break;

            case "coarsebuffer" :
                msg = "**Current contents of the coarse timer buffer**\n";
                for( ScheduleEntry se : scheduleManager.getCoarseTimerBuff() )
                {
                    if( msg.length() >= 1900 )
                    {
                        MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
                        msg = "**continued**\n";
                    }
                    msg += Integer.toHexString(se.getId()) + "\n";
                }
                break;

            case "entries" :
                msg = "**Current contents of the entries map**\n";
                for( Integer Id : scheduleManager.getAllEntries() )
                {
                    if( msg.length() >= 1900 )
                    {
                        MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
                        msg = "**continued**\n";
                    }
                    msg += Integer.toHexString(Id) + "\n";
                }
                break;

            case "guilds" :
                msg = "**Currently connected guilds**\n";
                for( Guild guild : Main.getBotJda().getGuilds() )
                {
                    if( msg.length() >= 1900 )
                    {
                        MessageUtilities.sendPrivateMsg(msg, event.getAuthor(), null);
                        msg = "**continued**\n";
                    }
                    msg += guild.getId() + " (" + guild.getName() + ")" + "\n";
                }
                break;
        }

        MessageUtilities.sendPrivateMsg( msg, event.getAuthor(), null );
    }
}
