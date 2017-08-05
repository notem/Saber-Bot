package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * Sets a channel to sync to a google calendar address
 */
public class SyncCommand implements Command
{
    @Override
    public String name()
    {
        return "sync";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " <channel> [<calendar address>]```\n" +
                "The sync command will replace all events in the specified channel" +
                "with events imported from a public google calendar.\n" +
                "The command imports the next 7 days of events into the channel;" +
                " the channel will then automatically re-sync once every day.\n\n" +
                "If ``<calendar address>`` is not included, " +
                "the address saved in the channel settings will be used.";

        String USAGE_BRIEF = "``" + head + "`` - sync a schedule to a google calendar";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + head + " #new_schedule g.rit.edu_g4elai703tm3p4iimp10g8heig@group.calendar.google.com``" +
                "\n``" + head + " #new_schedule``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();

        if( args.length < 1 )
            return "That's not enough arguments! Use ``" + head + " <channel> [<calendar address>]``";
        if( args.length > 2)
            return "That's too many arguments! Use ``" + head + " <channel> [<calendar address>]``";

        String cId = args[0].replace("<#","").replace(">","");
        if( !Main.getScheduleManager().isASchedule(cId))
            return "Channel " + args[0] + " is not on my list of schedule channels for your guild.";

        if(Main.getScheduleManager().isLocked(cId))
            return "Schedule is locked while sorting or syncing. Please try again after I finish.";
        return "";
    }

    @Override
    public void action(String head, String[] args, MessageReceivedEvent event)
    {
        String cId = args[0].replace("<#","").replace(">","");
        TextChannel channel = event.getGuild().getTextChannelById(cId);

        String address;
        if( args.length == 1 )
            address = Main.getScheduleManager().getAddress(cId);
        else
        {
            address = args[1];

            // enable auto-sync'ing timezone
            Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("timezone_sync", true));
        }

        Main.getCalendarConverter().syncCalendar(address, channel);
        Main.getScheduleManager().setAddress(cId,address);

        String content = "I have finished syncing <#" + cId + ">!";
        MessageUtilities.sendMsg(content, event.getChannel(), null);
    }
}
