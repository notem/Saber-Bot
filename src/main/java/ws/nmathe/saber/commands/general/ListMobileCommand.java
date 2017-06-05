package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.ISnowflake;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 */
public class ListMobileCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "listm";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "```diff\n- Usage\n" + invoke + " <ID> [filters]```\n" +
                "The list command will show all users who have rsvp'ed yes.\n" +
                "The command takes a single argument which should be the ID of the event you wish query.\n" +
                "\nThe schedule holding the event must have 'rsvp' turned on in the configuration settings.\n" +
                "RSVP can be enabled on a channel using the config command as followed, ``" +
                Main.getBotSettingsManager().getCommandPrefix() + "config #channel rsvp on``\n" +
                "\nThe list may be filtered by either users or roles by appending \"r: @role\", \"u: @user\", or \"t: [type]\" to the command\n" +
                "Any number of filters may be appended.";

        String USAGE_BRIEF = "``" + invoke + "`` - list with better mobile support";

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
        if (args.length==0)
            return "That's not enough arguments! Use ``" + invoke + " <ID> [filters]``";

        int index = 0;

        if (VerifyUtilities.verifyHex(args[index]))
        {
            Integer entryId = Integer.decode("0x" + args[index]);
            ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());
            if (entry == null)
            {
                return "The requested entry does not exist!";
            }
            if (!Main.getScheduleManager().isRSVPEnabled(entry.getScheduleID()))
            {
                return "The schedule that the entry is on is not rsvp enabled!";
            }
        }

        index++;

        for(; index<args.length; index++)
        {
            String[] filter = args[index].toLowerCase().split(":");
            if(filter.length != 2)
            {
                return "Invalid filter ``" + args[index] + "``!\nFilters must be of the form \"r: @role\" or \"u: @user\"";
            }

            String filterType = filter[0].trim();
            String filterValue = filter[1].trim();
            switch(filterType)
            {
                case "r":
                case "role":
                    break;

                case "u":
                case "user":
                    break;

                case "t":
                case "type":
                    switch(filterValue)
                    {
                        case "no":
                        case "yes":
                        case "undecided":
                        case "no-input":
                            break;

                        default:
                            return "Invalid [type] for type filter!" +
                                    "\nPossible filter types are \"no\", \"yes\", \"undecided\", or \"no-input\".";
                    }
                    break;

                default:
                    return "Invalid filter ``" + args[index] + "``!" +
                            "\nFilters must be of the form \"r: @role\", \"u: @user\", or \"t: [type]\"";
            }
        }

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        int index = 0;
        Integer entryId = Integer.decode("0x" + args[index++]);
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());

        List<String> rsvpYes = entry.getRsvpYes();
        List<String> rsvpNo = entry.getRsvpNo();
        List<String> rsvpUndecided = entry.getRsvpUndecided();
        String content = "";

        List<String> userFilters = new ArrayList<>();
        List<String> roleFilters = new ArrayList<>();
        boolean filterByType = false;
        boolean typeYes = false;
        boolean typeNo = false;
        boolean typeUndecided = false;
        boolean typeNoInput = false;
        for(; index<args.length; index++)
        {
            String filterType = args[index].toLowerCase().split(":")[0].trim();
            String filterValue = args[index].toLowerCase().split(":")[1].trim();
            switch(filterType)
            {
                case "r":
                case "role":
                    roleFilters.add(filterValue.replace("<@&","").replace(">",""));
                    break;

                case "u":
                case "user":
                    userFilters.add(filterValue.replace("<@","").replace(">",""));
                    break;

                case "t":
                case "type":
                    filterByType = true;
                    switch(filterValue)
                    {
                        case "no":
                            typeNo = true;
                            break;
                        case "yes":
                            typeYes = true;
                            break;
                        case "undecided":
                            typeUndecided = true;
                            break;
                        case "no-input":
                            typeNoInput = true;
                            break;
                    }
            }
        }

        if(!filterByType || typeYes)
        {
            content += "**RSVP'ed \"Yes\"\n======================**\n";
            for(String id : rsvpYes)
            {
                if(content.length() > 1900)
                {
                    MessageUtilities.sendMsg(content, event.getChannel(), null);
                    content = "*continued. . .* \n";
                }
                Member member = event.getGuild().getMemberById(id);
                if(this.checkMember(member, userFilters, roleFilters))
                {
                    content += event.getGuild().getMemberById(id).getEffectiveName() + "\n";
                }
            }
        }

        if(!filterByType || typeNo)
        {
            content += "\n**RSVP'ed \"No\"\n======================**\n";
            for(String id : rsvpNo)
            {
                if(content.length() > 1900)
                {
                    MessageUtilities.sendMsg((new MessageBuilder()).setEmbed(
                            (new EmbedBuilder()).setDescription(content).build()
                    ).build(), event.getChannel(), null);
                    content = "*continued. . .* \n";
                }
                Member member = event.getGuild().getMemberById(id);
                if(this.checkMember(member, userFilters, roleFilters))
                {
                    content += event.getGuild().getMemberById(id).getEffectiveName() + "\n";
                }
            }
        }

        if(!filterByType || typeUndecided)
        {
            content += "**\nRSVP'ed \"Undecided\"\n======================**\n";
            for(String id : rsvpUndecided)
            {
                if(content.length() > 1900)
                {
                    MessageUtilities.sendMsg((new MessageBuilder()).setEmbed(
                            (new EmbedBuilder()).setDescription(content).build()
                    ).build(), event.getChannel(), null);
                    content = "*continued. . .* \n";
                }
                Member member = event.getGuild().getMemberById(id);
                if(this.checkMember(member, userFilters, roleFilters))
                {
                    content += event.getGuild().getMemberById(id).getEffectiveName() + "\n";
                }
            }
        }

        if(!filterByType || typeNoInput)
        {
            List<String> undecided = event.getGuild().getMembers().stream()
                    .filter(member -> checkMember(member, userFilters, roleFilters))
                    .map(member -> member.getUser().getId()).collect(Collectors.toList());
            undecided.removeAll(rsvpYes);
            undecided.removeAll(rsvpNo);

            content += "**\nNo input\n======================\n**";
            if(!filterByType & undecided.size() > 10)
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
                content += event.getGuild().getMemberById(id).getEffectiveName() + "\n";
            }
        }

        String titleUrl = entry.getTitleUrl()==null?"https://nnmathe.ws/saber":entry.getTitleUrl();
        String title = entry.getTitle()+" ["+Integer.toHexString(entryId)+"]";
        MessageUtilities.sendMsg((new MessageBuilder()).setEmbed(
                (new EmbedBuilder())
                        .setDescription(content)
                        .setTitle(title, titleUrl)
                        .build()
        ).build(), event.getChannel(), null);
    }

    private boolean checkMember(Member member, List<String> userFilters, List<String> roleFilters)
    {
        if(member!=null)
        {
            boolean skip = false;
            if(!userFilters.isEmpty() && !userFilters.contains(member.getUser().getId()))
            {
                skip = true;
            }
            else if(!roleFilters.isEmpty())
            {
                skip = true;
                for(String role : roleFilters)
                {
                    List<String> memberRoles = member.getRoles().stream()
                            .map(ISnowflake::getId).collect(Collectors.toList());
                    if(memberRoles.contains(role))
                    {
                        skip = false;
                        break;
                    }
                }
            }
            return !skip;
        }
        return false;
    }

}
