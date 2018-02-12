package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.utils.MessageUtilities;

import static com.mongodb.client.model.Filters.eq;

public class PurgeCommand implements Command
{
    @Override
    public String name()
    {
        return "purge";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - removes messages created by the bot";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.USER);

        String cat1 = "- Usage\n" + head + " <channel>";
        String cont1 = "This command can be used to remove all messages sent by the bot on a particular channel " +
                "(with limitations).\n The messages for any active events will not be removed.\n\n" +
                "Messages that were sent far back in the past may not be removed, these will need to be deleted manually.\n" +
                "Only 100 messages will be removed per usage of this command.\n";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head);
        info.addUsageExample(head+" #alerts");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();
        int index = 0;

        // verify current argument count
        if (args.length != 1)
        {
            return "This command requires a channel as an argument!";
        }

        // verify argument 1 is properly formed
        if (!args[index].matches("<#[\\d]+>"))
        {
            return "The argument supplied with the command does not look like a valid channel!";
        }

        // verify that argument is a proper link to a channel
        Channel channel = event.getJDA().getTextChannelById(args[index].replaceAll("[^\\d]", ""));
        if (channel == null)
        {
            return "I could not find " + args[index] + " on your guild!";
        }

        return "";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        TextChannel channel = event.getGuild().getJDA().getTextChannelById(args[0].replaceAll("[^\\d]", ""));
        Integer[] count = {100};                                // number of messages to remove
        String botId = event.getJDA().getSelfUser().getId();    // ID of bot to check messages against
        channel.getIterableHistory().stream()
                .filter(message -> message.getAuthor().getId().equals(botId) && (count[0]-- > 0))
                .forEach((message ->
                {
                    // sleep for half a second before continuing
                    try { Thread.sleep(500); }
                    catch (InterruptedException ignored) {}

                    Bson query = eq("messageId", message.getId());
                    if (Main.getDBDriver().getEventCollection().count(query) == 0)
                    {
                        MessageUtilities.deleteMsg(message, null);
                    }
                }));

        // send success message
        String content = "Finished purging old message.";
        MessageUtilities.sendMsg(content, event.getTextChannel(), null);
    }
}
