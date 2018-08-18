package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.utils.MessageUtilities;

import java.util.List;

import static com.mongodb.client.model.Filters.eq;

/**
 * used for generating the list of valid timezone strings
 */
public class DiagnoseCommand implements Command
{
    @Override
    public String name()
    {
        return "diagnose";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - troubleshoot problems";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.MISC);

        String cat1 = "- Usage\n" + head + "[#channel]";
        String cont1 = "This command performs several checks which may indicate sources of issues.\n" +
                "If no arguments are used, the command will evaluate guild-wide permissions and control channel access.\n" +
                "If the first argument is a channel link, information regarding channel specific permissions and configuration will listed.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head);

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        if (args.length > 0)
        {
            if (!args[0].matches("<#\\d+>"))
            {
                return "*" + args[0] + "* does not look like a channel link!";
            }
        }
        return "";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        Member saber = event.getGuild().getMember(event.getJDA().getSelfUser());
        StringBuilder builder = new StringBuilder("```diff\n");
        if (args.length < 1)
        {   // general diagnosis
            // 1) check #saber_control
            Document doc = Main.getDBDriver().getGuildCollection()
                    .find(eq("_id", event.getGuild().getId())).first();
            TextChannel controlChannel = null;
            // attempt to find the control channel using the saved ID
            if (doc != null)
            {
                String controlId = doc.getString("command_channel");
                if (controlId != null && !controlId.isEmpty())
                {
                    controlChannel = event.getGuild().getTextChannelById(controlId);
                    if (controlChannel != null)
                    {
                        builder.append("+ Your guild's control channel is #")
                                .append(controlChannel.getName())
                                .append("\n");
                    }
                }
            }
            // search for default control channels
            if (controlChannel == null)
            {
                List<TextChannel> defaultControls = event.getGuild()
                        .getTextChannelsByName(Main.getBotSettingsManager().getControlChan(), true);
                if (defaultControls.isEmpty())
                    builder.append("- Your guild's control channel could not be found!\n");
                else
                    builder.append("+ I found your guild's default control channel!\n");
            }
            // check permissions on the control channel
            if (controlChannel != null)
            {
                boolean read = saber.hasPermission(controlChannel, Permission.MESSAGE_READ);
                boolean write = saber.hasPermission(controlChannel, Permission.MESSAGE_WRITE);
                if (!read || !write)
                {
                    builder.append("- I do not have the permissions to ");
                    if (!read)
                        builder.append("read");
                    if (!read && !write)
                        builder.append(" or send");
                    else if (!write)
                        builder.append("send");
                    builder.append(" messages on the control channel!\n");
                }
            }

            // 2) check guild-wide saber permissions
            boolean admin = saber.hasPermission(Permission.ADMINISTRATOR);
            boolean manage = saber.hasPermission(Permission.MANAGE_CHANNEL);
            if (admin)
                builder.append("- I have administrator permissions!\n");
            if (manage)
                builder.append("+ I am able to create new channels.\n");
            else
                builder.append("- I do not have the permissions to create new channels!\n");

            // 3) check local channel permissions
            boolean write = saber.hasPermission(event.getTextChannel(), Permission.MESSAGE_WRITE);
            if (!write)
            {
                builder.append("- I do not have the permissions to send messages to #")
                        .append(event.getTextChannel().getName())
                        .append("!\n");
            }
            else
            {
                boolean embed = saber.hasPermission(event.getTextChannel(), Permission.MESSAGE_EMBED_LINKS);
                if (!embed)
                    builder.append("- I cannot send messages with embeds to this channel!\n");
            }
        }
        else
        {   // diagnose a schedule or channel
            // 1) can Saber view the requested channel
            String channelId = args[0].replaceAll("[^\\d]","");
            TextChannel channel = event.getGuild().getTextChannelById(channelId);
            if (channel != null)
            {
                builder.append("+ I found the channel called #")
                        .append(channel.getName())
                        .append(".\n");

                // 2) is the channel a schedule?
                boolean schedule = Main.getScheduleManager().isSchedule(channelId);
                if (schedule)
                {
                    builder.append("+ That channel is a schedule.\n");

                    boolean read = saber.hasPermission(channel, Permission.MESSAGE_READ);
                    boolean history = saber.hasPermission(channel, Permission.MESSAGE_HISTORY);
                    if (!read || !history)
                    {
                        if (read)
                        {
                            builder.append("- I cannot read message history on #")
                                    .append(channel.getName())
                                    .append("!\n");
                        }
                        else if (!history)
                        {
                            builder.append("- I cannot read messages or the message history on #")
                                    .append(channel.getName())
                                    .append("!\n");
                        }
                        else
                        {
                            builder.append("- I cannot read messages on #")
                                    .append(channel.getName())
                                    .append("!\n");
                        }
                    }

                    // 3) can Saber locate the announcement channels?
                    String start = Main.getScheduleManager().getStartAnnounceChan(channelId);
                    String end = Main.getScheduleManager().getEndAnnounceChan(channelId);
                    String reminder = Main.getScheduleManager().getReminderChan(channelId);
                    if (start != null && start.equals(end))
                    {
                        helper(builder, event, start, "start and end announcements", saber);
                    }
                    else
                    {
                        if (start != null && !start.isEmpty())
                        {
                            helper(builder, event, start, "start announcements", saber);
                        }
                        if (end != null)
                        {
                            helper(builder, event, end, "end announcements", saber);
                        }
                    }
                    if (reminder != null)
                    {
                        helper(builder, event, reminder, "reminders", saber);
                    }

                    // check for reaction permissions (rsvp)
                    boolean reaction = saber.hasPermission(channel, Permission.MESSAGE_ADD_REACTION);
                    if (!reaction)
                    {
                        builder.append("- I do not have the permissions to add reactions to messages on #")
                                .append(channel.getName())
                                .append("!\n");
                    }

                    // manage messages
                    boolean manage = saber.hasPermission(channel, Permission.MESSAGE_MANAGE);
                    if (!manage)
                    {
                        builder.append("- I do not have the permission to delete user messages on ")
                                .append(channel.getName())
                                .append("!\n");
                    }
                }
                else
                {
                    builder.append("+ That channel is not a schedule.\n");
                    boolean read = saber.hasPermission(channel, Permission.MESSAGE_READ);
                    if (!read)
                    {
                        builder.append("- I cannot read messages on #")
                                .append(channel.getName())
                                .append("!\n");
                    }
                }

                // can Saber write messages with embeds to that channel?
                boolean write = saber.hasPermission(channel, Permission.MESSAGE_WRITE);
                boolean embed = saber.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS);
                if (!write)
                {
                    builder.append("- I do not have the permissions to send messages to #")
                            .append(channel.getName())
                            .append("!\n");
                }
                if (write && !embed)
                {
                    builder.append("- I do not have the permissions to send messages with embeds to #")
                            .append(channel.getName())
                            .append("!\n");
                }

                // mention @everyone
                boolean mention = saber.hasPermission(channel, Permission.MESSAGE_MENTION_EVERYONE);
                if (!mention)
                {
                    builder.append("- I do not have the permission to mention everyone on #")
                            .append(channel.getName())
                            .append(".\n");
                }
            }
            else
            {
                builder.append("- I could not find the requested channel!\n");
            }
        }
        builder.append("```");

        // send the diagnosis message
        if (saber.hasPermission(event.getTextChannel(), Permission.MESSAGE_WRITE))
        {   // only if Saber can write to the channel
            MessageUtilities.sendMsg(builder.toString(), event.getTextChannel(), null);
        }
        else
        {   // attempt to DM the user the diagnosis
            MessageUtilities.sendPrivateMsg(builder.toString(), event.getAuthor(), null);
        }
    }

    /**
     * evaluates if an announcement channel can be accessed
     */
    private TextChannel helper(StringBuilder builder, MessageReceivedEvent event, String identifier, String type, Member saber)
    {
        TextChannel channel = null;
        if (identifier.matches("[\\d]+"))
        {
            channel = event.getGuild().getTextChannelById(identifier);
        }
        else
        {
            List<TextChannel> channels = event.getGuild().getTextChannelsByName(identifier, true);
            if (!channels.isEmpty())
            {
                channel = channels.get(0);
            }
        }
        if (channel != null)
        {
            boolean write = saber.hasPermission(channel, Permission.MESSAGE_WRITE);
            if (write)
            {
                builder.append("+ I will send ")
                        .append(type)
                        .append(" to #")
                        .append(channel.getName())
                        .append(" for events on this schedule.\n");
            }
            else
            {
                builder.append("- I cannot send messages to the channel to which ")
                        .append(type)
                        .append(" are configured to be sent!\n");
            }
        }
        else
        {
            builder.append("- I cannot access the channel to which ")
                    .append(type)
                    .append(" are configured to be sent!\n");
        }
        return channel;
    }
}
