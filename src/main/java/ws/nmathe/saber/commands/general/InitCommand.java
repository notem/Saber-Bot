package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.utils.MessageUtilities;

import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;

/**
 * Creates a new schedule for events
 */
public class InitCommand implements Command
{
    @Override
    public String name()
    {
        return "init";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - initialize a new schedule";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.CORE);

        String cat1 = "- Usage\n" + head + " [<channel>|<name>]";
        String cont1 = "With this bot, all events must be placed on a schedule." +
                "\nSchedules are discord channels which are used to store and display the details of an event." +
                "\n\n" +
                "This command is used to either create a new schedule or to convert an existing channel to a schedule.\n" +
                "Converting a channel to schedule can only be undone by deleting the schedule.\n" +
                "Existing messages in that schedule will not be removed." +
                "\n\n" +
                "The single argument the command takes is optional." +
                "\nThe argument should either be an existing #channel, or the name of the schedule you wish to create." +
                "\nIf omitted, a new schedule named 'new_schedule' will be created.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head);
        info.addUsageExample(head+" \"Guild Events\"");
        info.addUsageExample(head+" #events");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        if(Main.getScheduleManager().isLimitReached(event.getGuild().getId()))
        {
            return "You have reached the limit for schedules! Please delete one of your guild's schedules before trying again.";
        }
        if(args.length > 1)
        {
            return "That's too many arguments! Use ``" + prefix + this.name() + " [<name>|<channel>]``";
        }
        if(args.length == 1)
        {
            if (args[0].matches("<#[\\d]+>")) // first arg is a discord channel link
            {
                String chanId = args[0].replaceFirst("<#","").replaceFirst(">","");
                String commandChannelId = Main.getGuildSettingsManager()
                        .getGuildSettings(event.getGuild().getId()).getCommandChannelId();
                if(chanId.equals(commandChannelId) || chanId.equals(event.getChannel().getId()))
                {
                    return "Your guild's command channel cannot be converted to a schedule!";
                }

                TextChannel channel = event.getGuild().getTextChannelById(chanId);
                if (channel == null)
                {
                    return "I could not find the channel, " + args[0] + "!";
                }

                if (channel.getHistory().retrievePast(6).complete().size() > 5) // refuse to convert channels with messages
                {
                    return "That channel is not empty! I will not convert an active channel to a schedule channel!";
                }
            }
            else // otherwise arg must be channel name
            {
                if(args[0].length()>100 || args[0].length()<2)
                {
                    return "Schedule name must be between 2 and 100 characters long!";
                }

                boolean canCreateChannels = event.getGuild().getMember(event.getJDA().getSelfUser())
                        .getPermissions().contains(Permission.MANAGE_CHANNEL);
                if(!canCreateChannels)
                {
                    return "I need the Manage Channels permission to create a new schedule!";
                }
            }
        }
        return "";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        String body;
        if(args.length > 0)
        {
            boolean isAChannel = false;
            TextChannel chan = null;
            String chanId = null;
            try
            {
                chanId = args[0].replaceAll("[^\\d]","");
                chan = event.getGuild().getTextChannelById(chanId);
                if(chan != null) isAChannel = true;
            }
            catch(Exception ignored)
            {}

            if(!isAChannel) // use the argument as the new channel's name
            {
                String chanTitle = args[0].replaceAll("[^A-Za-z0-9_ \\-]","").replaceAll("[ ]","_");
                Main.getScheduleManager().createSchedule(event.getGuild().getId(), chanTitle);
                body = "A new schedule channel named **" + chanTitle.toLowerCase() + "** has been created!\n" +
                        "You can now use the create command to create events on that schedule, or the sync command to sync " +
                        "that schedule to a Google Calendar.";
            }
            else // convert the channel to a schedule
            {
                if(Main.getScheduleManager().isSchedule(chan.getId()))
                {   // clear the channel of events
                    TextChannel finalChan = chan;
                    Main.getDBDriver().getEventCollection().find(eq("channelId", chan.getId()))
                            .forEach((Consumer<? super Document>) document ->
                            {
                                String msgId = document.getString("messageId");
                                finalChan.deleteMessageById(msgId).complete();
                                Main.getEntryManager().removeEntry(document.getInteger("_id"));
                            });
                    body = "The schedule <#" + chanId + "> has been cleared!";
                }
                else
                {   // create a new schedule
                    Main.getScheduleManager().createSchedule(chan);
                    body = "The channel <#" + chanId + "> has been converted to a schedule channel!\n" +
                            "You can now use the create command to create events on that schedule, or the sync " +
                            "command to sync that schedule to a Google Calendar.";
                }
            }
        }
        else // create a new schedule using the default name
        {
            Main.getScheduleManager().createSchedule(event.getGuild().getId(), null);
            body = "A new schedule channel named **new_schedule** has been created!\n" +
                    "You can now use the create command to create events on that schedule, or the sync command" +
                    " to sync that schedule to a Google Calendar.";
        }
        MessageUtilities.sendMsg(body, event.getChannel(), null);
    }
}
