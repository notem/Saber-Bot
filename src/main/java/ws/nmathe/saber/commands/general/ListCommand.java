package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.apache.commons.lang3.StringUtils;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.util.*;
import java.util.stream.Collectors;

/**
 * retrieves the list of RSVP'ed members to an event
 */
public class ListCommand implements Command
{
    @Override
    public String name()
    {
        return "list";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - show an event's rsvp list";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.USER);

        String cat1 = "- Usage\n" + head + " <ID> [mode] [filters]";
        String cont1 = "The list command will show all users who have rsvp'ed yes.\n" +
                "The command takes a single argument which should be the ID of the event you wish query." +
                "\n\n" +
                "The schedule holding the event must have 'rsvp' turned on in the configuration settings.\n" +
                "RSVP can be enabled on a channel using the config command as followed, ``" +
                prefix + "config #channel rsvp on``" +
                "\n\n" +
                "The list may be filtered by either users or roles by appending \"r: @role\", \"u: @user\", or " +
                "\"t: [type]\" to the command.\n" +
                "Any number of filters may be appended to the command.\n" +
                "To display only users who have not rsvp'ed, use *no-input* as the ``[type]``." +
                "\n\n" +
                "The list command has two optional 'modes' of display. \n" +
                "If the term 'mobile' is added as an argument to the command, non-mentionable usernames will be displayed.\n" +
                "If the term 'id' is added as an argument, usernames will be displayed as they escaped mentionable ID tags.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head + " 01JAO3");
        info.addUsageExample(head + " 01JAO3 \"u: @notem\"");
        info.addUsageExample(head + " 01JAO3 mobile \"t: yes\" \"t: no\"");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
    {
        String head = prefix + this.name();
        if (args.length==0)
        {
            return "That's not enough arguments! Use ``" + head + " <ID> [filters]``";
        }
        int index = 0;

        ScheduleEntry entry;
        if (VerifyUtilities.verifyEntryID(args[index]))
        {
            Integer entryId = ParsingUtilities.encodeIDToInt(args[index]);
            entry = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());
            if (entry == null)
            {
                return "The requested entry does not exist!";
            }
            if (!Main.getScheduleManager().isRSVPEnabled(entry.getChannelId()))
            {
                return "The schedule that the entry is on is not rsvp enabled!";
            }
        }
        else
        {
            return "Argument *" + args[index] + "* is not a valid entry ID!";
        }

        index++;

        boolean mobileFlag = false;
        boolean IdFlag = false;
        for(; index<args.length; index++)
        {
            if(args[index].equalsIgnoreCase("mobile") || args[index].equalsIgnoreCase("m"))
            {
                mobileFlag = true;
                if(IdFlag)
                {
                    return "Both 'mobile' and 'id' modes cannot be active at the same time!\n" +
                            "Include either the 'id' or 'mobile' argument in the command, not both.";
                }
                continue;
            }
            if(args[index].equalsIgnoreCase("id") || args[index].equalsIgnoreCase("i"))
            {
                IdFlag = true;
                if(mobileFlag)
                {
                    return "Both 'mobile' and 'id' modes cannot be active at the same time!\n" +
                            "Include either the 'id' or 'mobile' argument in the command, not both.";
                }
                continue;
            }

            String[] filter = args[index].split(":");
            if(filter.length != 2)
            {
                return "Invalid filter ``" + args[index] + "``!\nFilters must be of the form \"r: @role\" or \"u: @user\"";
            }

            String filterType = filter[0].toLowerCase().trim();
            String filterValue = filter[1].trim();
            switch(filterType.toLowerCase())
            {
                case "r":
                case "role":
                    break;

                case "u":
                case "user":
                    break;

                case "t":
                case "type":
                    if(filterValue.equalsIgnoreCase("no-input")) break;
                    Map<String, String> options = Main.getScheduleManager().getRSVPOptions(entry.getChannelId());
                    if(!options.values().contains(filterValue))
                    {
                        return "Invalid ``[type]`` for type filter!" +
                                "``[type]`` must be either an rsvp group used on that event or \"no-input\".";
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
    public void action(String prefix, String[] args, EventCompat event)
    {
        int index = 0;
        Integer entryId = ParsingUtilities.encodeIDToInt(args[index++]);
        ScheduleEntry se = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());

        String titleUrl = se.getTitleUrl()==null ? "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/1200px-Cat03.jpg": se.getTitleUrl();
        String title = se.getTitle()+" ["+ParsingUtilities.intToEncodedID(entryId)+"]";

        String content = "";

        List<String> userFilters = new ArrayList<>();
        List<String> roleFilters = new ArrayList<>();
        boolean filterByType = false;
        Set<String> typeFilters = new HashSet<>();

        boolean mobileFlag = false;
        boolean IdFlag = false;
        for(; index<args.length; index++)
        {
            if(args[index].equalsIgnoreCase("mobile") || args[index].equalsIgnoreCase("m"))
            {
                mobileFlag = true;
                continue;
            }
            if(args[index].equalsIgnoreCase("id") || args[index].equalsIgnoreCase("i"))
            {
                IdFlag = true;
                continue;
            }

            String filterType = args[index].split(":")[0].toLowerCase().trim();
            String filterValue = args[index].split(":")[1].trim();
            switch(filterType.toLowerCase())
            {
                case "r":
                case "role":
                    roleFilters.add(filterValue
                            .replace("<@&","")
                            .replace(">",""));
                    break;

                case "u":
                case "user":
                    userFilters.add(filterValue
                            .replace("<@","")
                            .replace(">",""));
                    break;

                case "t":
                case "type":
                    filterByType = true;
                    typeFilters.add(filterValue);
                    break;
            }
        }

        int lengthCap = 1900;   // maximum number of characters before creating a new message
        int mobileLineCap = 25; // maximum number of lines until new message, in mobile mode
        Set<String> uniqueMembers = new HashSet<>();
        Map<String, String> options = Main.getScheduleManager().getRSVPOptions(se.getChannelId());
        for(String type : options.values())
        {
            if(!filterByType || typeFilters.contains(type))
            {
                content += "**\"" + type + "\"\n======================**\n";
                Set<String> members = se.getRsvpMembersOfType(type);
                for(String id : members)
                {
                    // if the message is nearing maximum length, or if in mobile mode and the max lines have been reached
                    if(content.length() > lengthCap ||
                            (mobileFlag && StringUtils.countMatches(content, "\n") > mobileLineCap))
                    {
                        // build and send the embedded message object
                        MessageCreateData message = (new MessageCreateBuilder()).setEmbeds(
                                (new EmbedBuilder()).setDescription(content).setTitle(title, titleUrl).build()
                        ).build();
                        MessageUtilities.sendMsg(message, event.getChannel(), null);

                        // clear the content sting
                        content = "*continued. . .* \n";
                    }

                    if (id.matches("\\d+"))
                    {   // cases in which the id is most likely a valid discord user's ID
                        Member member = event.getGuild().getMemberById(id);
                        if(checkMember(member, userFilters, roleFilters))
                        {   // if the user is still a member of the guild, add to the list
                            uniqueMembers.add(member.getUser().getId());
                            content += this.getNameDisplay(mobileFlag, IdFlag, member);
                        }
                        else // otherwise, remove the member from the event and update
                        {
                            Set<String> tmp = se.getRsvpMembersOfType(type);
                            tmp.remove(id);
                            se.getRsvpMembersOfType(type).remove(id);
                            Main.getEntryManager().updateEntry(se, false);
                        }
                    }
                    else
                    {   // handles cases in which a non-discord user was added by an admin
                        uniqueMembers.add(id);
                        content += "*"+id+"*\n";
                    }
                }
            }
            content += "\n";
        }

        if(!filterByType || typeFilters.contains("no-input"))
        {
            // generate a list of all members of the guild who pass the filter and map to their ID
            List<String> noInput = event.getGuild().getMembers().stream()
                    .filter(member -> checkMember(member, userFilters, roleFilters))
                    .map(member -> member.getUser().getId()).collect(Collectors.toList());

            for(String type : options.values())
            {
                noInput.removeAll(se.getRsvpMembersOfType(type));
            }

            content += "**No input\n======================\n**";
            if(!filterByType & noInput.size() > 10)
            {
                content += " Too many users to show: " + noInput.size() + " users with no rsvp\n";
            }
            else for(String id : noInput)
            {
                if(content.length() > lengthCap ||
                        (mobileFlag && StringUtils.countMatches(content, "\n") > mobileLineCap))
                {
                    // build and send the embedded message object
                    MessageCreateData message = (new MessageCreateBuilder()).setEmbeds(
                            (new EmbedBuilder()).setDescription(content).setTitle(title, titleUrl).build()
                    ).build();
                    MessageUtilities.sendMsg(message, event.getChannel(), null);

                    // clear the content sting
                    content = "*continued. . .* \n";
                }
                Member member = event.getGuild().getMemberById(id);
                content += this.getNameDisplay(mobileFlag, IdFlag, member);
            }
        }

        String footer = uniqueMembers.size() + " unique member(s) appear in this search";

        // build and send the embedded message object
        MessageCreateData message = (new MessageCreateBuilder()).setEmbeds(
                (new EmbedBuilder()).setDescription(content)
                        .setTitle(title, titleUrl)
                        .setFooter(footer, null).build()
        ).build();
        MessageUtilities.sendMsg(message, event.getChannel(), null);
    }

    /**
     * Determines if a user should be display based on the set filters
     * @param member (Member) User to evaluate
     * @param userFilters (List) list of user IDs to filter by
     * @param roleFilters (List) list of role IDs to filter by
     * @return (boolean) should the user be included in the list?
     */
    private boolean checkMember(Member member, List<String> userFilters, List<String> roleFilters)
    {
        if(member!=null)
        {
            if(userFilters.isEmpty() && roleFilters.isEmpty())
            {   // filtering is disabled if both lists are empty
                return true;
            }
            // check for filtered users
            if(userFilters.contains(member.getUser().getId()) ||
                    userFilters.contains(member.getEffectiveName()))
            {
                return true;
            }
            // check for filtered roles
            List<String> memberRoleIDs = member.getRoles().stream()
                    .map(ISnowflake::getId).collect(Collectors.toList());
            List<String> memberRoleNames = member.getRoles().stream()
                    .map(Role::getName).collect(Collectors.toList());
            // include the user if a role filter matches either the
            // name or ID of a role held by the user
            for(String role : roleFilters)
            {
                if(memberRoleIDs.contains(role) || memberRoleNames.contains(role))
                {
                    return true;
                }
            }
        }
        // user matched no filters
        return false;
    }


    /**
     * produces the display style of the users who have rsvped for an event
     * @param mobileFlag (boolean) use mobile style?
     * @param IdFlag (boolean) use ID style?
     * @param member (Member) user to display
     * @return (String) display name of the user
     */
    private String getNameDisplay(boolean mobileFlag, boolean IdFlag, Member member)
    {
        String display;
        if(mobileFlag)
        {
            display = member.getEffectiveName() + "\n";
        }
        else if(IdFlag)
        {
            display = " \\<@" + member.getUser().getId() + ">\n";
        }
        else
        {
            display = " <@" + member.getUser().getId() + ">\n";
        }
        return display;
    }
}
