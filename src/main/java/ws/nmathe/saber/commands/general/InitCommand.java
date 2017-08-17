package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.utils.MessageUtilities;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;

public class InitCommand implements Command
{
    @Override
    public String name()
    {
        return "init";
    }

    @Override
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " [<channel>|<name>]```\n" +
                "With this bot, all events must be placed on a schedule." +
                "\nSchedules are discord channels which are used to store and display the details of an event." +
                "\n\n" +
                "This command is used to either create a new schedule or to convert an existing channel to a schedule.\n" +
                "Converting a channel to schedule can only be undone by deleting the schedule.\n" +
                "Existing messages in that schedule will not be removed." +
                "\n\n" +
                "The single argument the command takes is optional." +
                "\nThe argument should either be an existing #channel, or the name of the schedule you wish to create." +
                "\nIf omitted, a new schedule named 'new_schedule' will be created.";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + head + "``\n" +
                "``" + head + " \"Guild Events\"``" +
                "``" + head + " #events";

        String USAGE_BRIEF = "``" + head + "`` - initialize a new schedule";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();

        if(!event.getGuild().getMember(Main.getBotJda().getSelfUser())
                .getPermissions().contains(Permission.MANAGE_CHANNEL))
            return "I need the Manage Channels permission to create a new schedule!";

        if(Main.getScheduleManager().isLimitReached(event.getGuild().getId()))
            return "You have reached the limit for schedules! Please remove one schedule channel before trying again.";

        if(args.length > 1)
            return "That's too many arguments! Use ``" + head + " [<name>]``";

        if(args.length == 1)
        {
            if(args[0].length()>100 || args[0].length()<2)
                return "Schedule name must be between 2 and 100 characters long!";

            String chanId = args[0].replaceFirst("<#","").replaceFirst(">","");
            MessageChannel publicChannel = event.getGuild().getPublicChannel();
            if(publicChannel != null && publicChannel.getId().equals(chanId))
            {
                return "Your guild's public channel cannot be converted to a schedule!";
            }

            String commandChannelId = Main.getGuildSettingsManager().getGuildSettings(event.getGuild().getId()).getCommandChannelId();
            if(chanId.equals(commandChannelId) || chanId.equals(event.getChannel().getId()))
            {
                return "Your guild's command channel cannot be converted to a schedule!";
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
                chanId = args[0].replaceFirst("<#","").replaceFirst(">","");
                chan = event.getGuild().getTextChannelById(chanId);
                if(chan != null) isAChannel = true;
            }
            catch(Exception ignored)
            {}

            if(!isAChannel) // use the argument as the new channel's name
            {
                String chanTitle = args[0].replaceAll("[^A-Za-z0-9_ -]","").replace(" ","_");
                Main.getScheduleManager().createSchedule(event.getGuild().getId(), chanTitle);
                body = "A new schedule channel named **" + chanTitle.toLowerCase() + "** has been created!";

                body += "\nYou can now use the create command to create events on that schedule, or the sync command to sync " +
                        "that schedule to a Google Calendar.";
            }
            else // convert the channel to a schedule
            {
                if(Main.getScheduleManager().isASchedule(chan.getId()))
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
                    body = "The channel <#" + chanId + "> has been converted to a schedule channel!";

                    body += "\nYou can now use the create command to create events on that schedule, or the sync command to sync " +
                            "that schedule to a Google Calendar.";
                }
            }
        }
        else // create a new schedule using the default name
        {
            Main.getScheduleManager().createSchedule(event.getGuild().getId(), null);
            body = "A new schedule channel named **new_schedule** has been created!";

            body += "\nYou can now use the create command to create events on that schedule, or the sync command to sync " +
                    "that schedule to a Google Calendar.";
        }

        MessageUtilities.sendMsg(body, event.getChannel(), null);
    }
}
