package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.Permission;
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
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "initScheduleSync";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "```diff\n- Usage\n" + invoke + " [<channel>|<name>]```\n" +
                "With this bot, all events must be placed on a schedule." +
                "\nSchedules are discord channels which are used to store and display the details of an event." +
                "\n\n" +
                "This command is used to either create a new schedule or to convert an existing channel to a schedule." +
                "Converting a channel to schedule can only be undone by deleting the schedule.\n" +
                "Existing messages in that message will not be removed." +
                "\n\n" +
                "The single argument the command takes is optional." +
                "\nThe argument should either be an existing #channel, or the name of the schedule you wish to create." +
                "\nIf omitted, a new schedule named 'new_schedule' will be created.";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + invoke + "``\n" +
                "``" + invoke + " \"Guild Events\"``";

        String USAGE_BRIEF = "``" + invoke + "`` - initialize a new schedule";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        if(!event.getGuild().getMember(Main.getBotJda().getSelfUser())
                .getPermissions().contains(Permission.MANAGE_CHANNEL))
            return "I need the Manage Channels permission to create a new schedule!";

        if(Main.getScheduleManager().isLimitReached(event.getGuild().getId()))
            return "You have reached the limit for schedules! Please remove one schedule channel before trying again.";

        if(args.length > 1)
            return "That's too many arguments! Use ``" + invoke + " [<name>]``";

        if(args.length == 1)
        {
            if(args[0].length()>100 || args[0].length()<2)
                return "Schedule name must be between 2 and 100 characters long!";

            String chanId = args[0].replaceFirst("<#","").replaceFirst(">","");
            if(event.getGuild().getPublicChannel().getId().equals(chanId))
            {
                return "Your public guild's public channel cannot be converted to a schedule!";
            }
        }

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        String body;
        if(args.length > 0)
        {
            String chanId = args[0].replaceFirst("<#","").replaceFirst(">","");

            List<TextChannel> chans = event.getGuild().getTextChannels().stream()
                    .filter(chan -> chan.getId().equals(chanId))
                    .collect(Collectors.toList());

            if(chans.isEmpty()) // use the argument as the new channel's name
            {
                String chanTitle = args[0].replaceAll("[^A-Za-z0-9_ ]","").replace(" ","_");
                Main.getScheduleManager().createSchedule(event.getGuild().getId(), chanTitle);
                body = "A new schedule channel named **" + chanTitle.toLowerCase() + "** has been created!";

                body += "\nYou can now use the create command to create events on that schedule, or the sync command to sync " +
                        "that schedule to a Google Calendar.";
            }
            else // convert the channel to a schedule
            {
                TextChannel chan = chans.get(0);

                if(Main.getScheduleManager().isASchedule(chan.getId()))
                {   // clear the channel of events
                    Main.getDBDriver().getEventCollection().find(eq("channelId", chan.getId()))
                            .forEach((Consumer<? super Document>) document ->
                            {
                                String msgId = document.getString("messageId");
                                chan.deleteMessageById(msgId).complete();
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
