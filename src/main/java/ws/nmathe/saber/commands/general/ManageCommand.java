package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * used for generating the list of valid timezone strings
 */
public class ManageCommand implements Command
{
    @Override
    public String name()
    {
        return "manage";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - add or kick users from an event";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.MISC);

        String cat1 = "- Usage\n" + head + " <id> <add|kick> <group> <@user>";
        String cont1 = "" +
                "This command may be used to manually add or remove particular users from an event's rsvp groups." +
                "\n\n" +
                "The ``<group>`` argument should be one of the RSVP groups configured for the schedule.\n" +
                "``<@user>`` should be an @mention for the user to be added/removed.\n" +
                "If ``<@user>`` is not an @mention for a user, a 'dummy' user will be used.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head+" 10agj2 add Yes @Saber#9015");
        info.addUsageExample(head+" 10agj2 kick Yes @notem#1654");
        info.addUsageExample(head+" 10agj2 clear all");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
    {
        String head = prefix + this.name();
        int index = 0;
        if (args.length < 2)
        {
            return "Incorrect amount of arguments!" +
                    "\nUse ``" + head + " <id> <add|kick|clear> <args>``";
        }

        // verify valid entry ID
        ScheduleEntry entry;
        if (VerifyUtilities.verifyEntryID(args[index]))
        {
            Integer entryId = ParsingUtilities.encodeIDToInt(args[index]);
            entry = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());
            if (entry == null)
            {
                return "The requested entry does not exist!";
            }
        }
        else
        {
            return "Argument *" + args[0] + "* is not a valid entry ID!";
        }

        // verify the schedule has RSVP enabled
        if (!Main.getScheduleManager().isRSVPEnabled(entry.getChannelId()))
        {
            return "That event is not on an RSVP enabled schedule!";
        }

        // verify valid action argument
        index++;
        switch (args[index].toLowerCase())
        {
            case "a":
            case "add":
            case "k":
            case "kick":
                if (args.length < 4)
                {
                    return "Incorrect number of arguments!\n" +
                            "Use ``"+head+" <id> "+args[index]+" <group> <user>";
                }
                break;

            case "c":
            case "clear":
                if (args.length < 3)
                {
                    return "Incorrect number of arguments!\n" +
                            "Use ``"+head+" <id> "+args[index]+" <'group'|all>``";
                }
                return "";

            default:
                return "*" + args[index] + "* is not a valid action argument!\n" +
                        "Please use either *add* or *kick*!";
        }

        // verify the group is a valid group
        index++;
        Map<String, String> options = Main.getScheduleManager().getRSVPOptions(entry.getChannelId());
        if (!options.values().contains(args[index]))
        {
            return "There is no RSVP group called *" + args[index] + "* on the event!";
        }

        return "";
    }

    @Override
    public void action(String prefix, String[] args, EventCompat event)
    {
        int index = 0;
        Integer entryId = ParsingUtilities.encodeIDToInt(args[index++]);
        ScheduleEntry se = Main.getEntryManager().getEntryFromGuild(entryId, event.getGuild().getId());
        String logging = Main.getScheduleManager().getRSVPLogging(se.getChannelId());

        String content="", group, user;
        Set<String> members;
        switch(args[index++].toLowerCase())
        {
            case "a":
            case "add":
                group = args[index++];
                user = args[index].matches("<@!?\\d+>") ? args[index].replaceAll("[^\\d]", ""):args[index];
                members = se.getRsvpMembersOfType(group);
                members.add(user);
                se.setRsvpMembers(group, members);
                content = "I have added *" + args[index] + "* to *" + group + "* on event :id:" + ParsingUtilities.intToEncodedID(se.getId());

                // log the rsvp action
                if (!logging.isEmpty())
                {
                    String msg = "";
                    if(user.matches("[\\d]+")) msg += "<@" + user + ">";
                    else msg += user;
                    msg += " has been added to the RSVP group *"+group+"* for **" +
                            se.getTitle() + "** - :id: **" + ParsingUtilities.intToEncodedID(se.getId()) + "**";
                    MessageUtilities.sendMsg(msg, event.getJDA().getTextChannelById(logging), null);
                }
                break;

            case "k":
            case "kick":
                group = args[index++];
                user = args[index].matches("<@!?\\d+>") ? args[index].replaceAll("[^\\d]", ""):args[index];
                members = se.getRsvpMembersOfType(group);
                members.remove(user);
                se.setRsvpMembers(group, members);
                content = "I have removed *" + args[index] + "* from *" + group + "* on event :id:" + ParsingUtilities.intToEncodedID(se.getId());

                // log the rsvp action
                if (!logging.isEmpty())
                {
                    String msg = "";
                    if(user.matches("[\\d]+")) msg += "<@" + user + ">";
                    else msg += user;
                    msg += " has been kicked from the RSVP group *"+group+"* for **" +
                            se.getTitle() + "** - :id: **" + ParsingUtilities.intToEncodedID(se.getId()) + "**";
                    MessageUtilities.sendMsg(msg, event.getJDA().getTextChannelById(logging), null);
                }
                break;

            case "c":
            case "clear":
                Set<String> categories = se.getRsvpMembers().keySet();
                if (categories.contains(args[index]))
                {   // clear the rsvp category's list
                    se.setRsvpMembers(args[index], new ArrayList<>());
                }
                else if (args[index].equalsIgnoreCase("all"))
                {   // clear all rsvp category lists
                    for (String type : categories)
                    {
                        se.setRsvpMembers(type, new ArrayList<>());
                    }
                }
                break;
        }

        Main.getEntryManager().updateEntry(se, false);
        MessageUtilities.sendMsg(content, event.getChannel(), null);
    }
}
