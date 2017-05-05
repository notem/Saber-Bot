package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.util.List;
import java.util.stream.Collectors;

/**
 */
public class ListCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "list";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "```diff\n- Usage\n" + invoke + " <ID>```\n" +
                "The list command will show all users who have rsvp'ed yes.\n" +
                "The command takes a single argument which should be the ID of the event you wish query.\n" +
                "\nThe schedule holding the event must have 'rsvp' turned on in the configuration settings.\n" +
                "RSVP can be enabled on a channel using the config command as followed, ``" +
                Main.getBotSettingsManager().getCommandPrefix() + "config #channel rsvp on``";

        String USAGE_BRIEF = "``" + invoke + "`` - show an event's rsvp list";

        String USAGE_EXAMPLES = "```diff\n- Examples```\n" +
                "``" + invoke + " 080194c``";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + USAGE_EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        if (args.length>1)
            return "That's too many arguments! Use ``" + invoke + " <ID>``";
        if (args.length==0)
            return "That's not enough arguments! Use ``" + invoke + " <ID>``";

        if (VerifyUtilities.verifyHex(args[0]))
        {
            Integer entryId = Integer.decode("0x" + args[0]);
            ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());
            if (entry == null)
            {
                return "The requested entry does not exist!";
            }
            if (!Main.getScheduleManager().isRSVPEnabled(entry.getScheduleID()))
            {
                return "The schedule that the entry is on is not rsvp enabled!";
            }
            return "";
        }

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        Integer entryId = Integer.decode("0x" + args[0]);
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());

        List<String> rsvpYes = entry.getRsvpYes();
        List<String> rsvpNo = entry.getRsvpNo();

        String content = "RSVP'ed \"Yes\"\n======================\n";
        for(String id : rsvpYes)
        {
            if(content.length() > 1900)
            {
                MessageUtilities.sendMsg(content, event.getChannel(), null);
                content = "*continued. . .* \n";
            }
            content += " <@" + id + ">\n";
        }

        content += "\nRSVP'ed \"No\"\n======================\n";
        for(String id : rsvpNo)
        {
            if(content.length() > 1900)
            {
                MessageUtilities.sendMsg((new MessageBuilder()).setEmbed(
                                (new EmbedBuilder()).setDescription(content).build()
                ).build(), event.getChannel(), null);
                content = "*continued. . .* \n";
            }
            content += " <@" + id + ">\n";
        }

        List<String> undecided = event.getGuild().getMembers().stream()
                .map(member -> member.getUser().getId()).collect(Collectors.toList());
        undecided.removeAll(rsvpYes);
        undecided.removeAll(rsvpNo);

        content += "\nNo input\n======================\n";
        if(undecided.size() > 10)
        {
            content += " Too many users to show: " + undecided.size() + " users with no rsvp\n";
        }
        else for(String id : undecided)
        {
            if(content.length() > 1900)
            {
                MessageUtilities.sendMsg((new MessageBuilder()).setEmbed(
                        (new EmbedBuilder()).setDescription(content).build()
                ).build(), event.getChannel(), null);
                content = "*continued. . .* \n";
            }
            content += " <@" + id + ">\n";
        }

        MessageUtilities.sendMsg((new MessageBuilder()).setEmbed(
                (new EmbedBuilder()).setDescription(content).build()
        ).build(), event.getChannel(), null);
    }
}
