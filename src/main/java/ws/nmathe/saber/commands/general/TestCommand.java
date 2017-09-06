package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

/**
 */
public class TestCommand implements Command
{
    @Override
    public String name()
    {
        return "test";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " <ID>```\n" +
                "The test command will send an test announcement for the event to **#"
                + Main.getBotSettingsManager().getControlChan() + "**.\n The announcement message for an event is " +
                "controlled by the schedule to which the event belongs to, and can be changed using the ``config``" +
                " command.";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + head + " 7fffffff``";

        String USAGE_BRIEF = "``" + head + "`` - test an event's announcement message";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();

        int index = 0;

        if( args.length < 1 )
        {
            return "That's not enough arguments! Use ``" + head + " <ID>``";
        }
        if( args.length > 1 )
        {
            return "That's too many arguments! Use ``" + head + " <ID>``";
        }

        // check for a valid entry ID
        if( !VerifyUtilities.verifyHex(args[index]) )
        {
            return "``" + args[index] + "`` is not a valid entry ID!";
        }

        Integer Id = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );

        if(entry == null)
        {
            return "I could not find an entry with that ID!";
        }

        return ""; // return valid
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        try
        {
            int index = 0;

            Integer entryId = Integer.decode( "0x" + args[index] );
            ScheduleEntry entry = Main.getEntryManager().getEntry( entryId );

            Message msg = entry.getMessageObject();
            if( msg==null ) return;

            String format = Main.getScheduleManager().getStartAnnounceFormat(entry.getMessageObject().getChannel().getId());
            String remindMsg = ParsingUtilities.parseMsgFormat(format, entry);
            MessageUtilities.sendMsg(remindMsg, event.getChannel(), null);
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
