package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;

/**
 */
public class SyncCommand implements Command
{

    private String invoke = Main.getBotSettings().getCommandPrefix() + "sync";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "Using ``" + invoke + " <channel> <calendar address>`` will replace all events in the specified channel" +
                "with events imported from a public google calendar. The command imports the next 7 days of events into the channel;" +
                " auto sync is planned but not yet implemented. You can find your google calendar's public URL address in the calendar's" +
                " settings; provide the command with the full address.";

        String USAGE_BRIEF = "``" + invoke + "`` - Sync a schedule channel to a public google calendar.";

        String EXAMPLES = "Ex: ``" + invoke + " schedule_entries g.rit.edu_g4elai703tm3p4iimp10g8heig@group.calendar.google.com``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        if( args.length < 2 )
            return "Not enough arguments";
        if( args.length > 2)
            return "Too many arguments";
        if( event.getGuild().getTextChannelsByName(args[0], false).isEmpty() )
            return "Channel **" + args[0] + "** does not exist";
        if( !Main.getCalendarConverter().checkValidAddress( args[1] ) )
            return "Cannot connect to google calendar address **" + args[2] + "**";

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        TextChannel channel = event.getGuild().getTextChannelsByName( args[0], false ).get(0);
        try
        {
            Main.getCalendarConverter().syncCalendar(args[1], channel);
            Main.getChannelSettingsManager().sendSettingsMsg(channel);
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }
}
