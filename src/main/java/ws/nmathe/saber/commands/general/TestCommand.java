package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

/**
 */
public class TestCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "test";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "```diff\n- Usage\n" + invoke + " <ID>```\n" +
                "The test command will send an test announcement for the event to **#"
                + Main.getBotSettingsManager().getControlChan() + "**.\n The announcement message for an event is " +
                "controlled by the schedule to which the event belongs to, and can be changed using the ``config``" +
                " command.";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + invoke + " 7fffffff``";

        String USAGE_BRIEF = "``" + invoke + "`` - test an event's announcement message";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        if( args.length < 1 )
            return "That's not enough arguments! Use ``" + invoke + " <ID>``";
        if( args.length > 1 )
            return "That's too many arguments! Use ``" + invoke + " <ID>``";

        // check first arg
        if( !VerifyUtilities.verifyHex(args[index]) )
            return "``" + args[index] + "`` is not a valid entry ID!";

        Integer Id = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );

        if(entry == null)
            return "I could not find an entry with that ID!";

        return ""; // return valid
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        Integer entryId = Integer.decode( "0x" + args[index] );
        ScheduleEntry entry = Main.getEntryManager().getEntry( entryId );

        Message msg = entry.getMessageObject();
        if( msg==null )
            return;

        String format = Main.getScheduleManager().getStartAnnounceFormat(entry.getMessageObject().getChannel().getId());
        String remindMsg = ParsingUtilities.parseMsgFormat(format, entry);
        MessageUtilities.sendMsg(remindMsg, event.getChannel(), null);
    }
}
