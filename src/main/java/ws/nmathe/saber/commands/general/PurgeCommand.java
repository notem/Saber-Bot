package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.bson.conversions.Bson;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.RateLimiter;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.utils.MessageUtilities;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.client.model.Filters.eq;

public class PurgeCommand implements Command
{

    // rate limiter with a threshold of 1 minute
    private static RateLimiter limiter = new RateLimiter(60*1000);

    // set of guilds which have an ongoing purge
    private static Map<String, String> processing = new ConcurrentHashMap<>();

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
    public String verify(String prefix, String[] args, EventCompat event)
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
            return "Your channel name, *"+args[index]+"*, looks malformed!";
        }

        // verify that argument is a proper link to a channel
        MessageChannel channel = event.getJDA().getTextChannelById(args[index].replaceAll("[^\\d]", ""));
        if (channel == null)
        {
            return "I could not find " + args[index] + " on your guild!";
        }

        // basic protection against misuse
        if (limiter.check(event.getGuild().getId()))
        {
            return "The purge command has been used on your guild recently.\n" +
                    "Please wait at least one minute before reusing the command!";
        }

        if (processing.keySet().contains(event.getGuild().getId()))
        {
            return "I am still purging messages from <#" + processing.getOrDefault(event.getGuild().getId(), "0") + ">.\n" +
                    "You may not issue another purge command until this finishes.";
        }

        return "";
    }

    @Override
    public void action(String prefix, String[] args, EventCompat event)
    {
        TextChannel channel = event.getGuild().getJDA().getTextChannelById(args[0].replaceAll("[^\\d]", ""));
        Integer[] count = {100};                                // number of messages to remove
        String botId = event.getJDA().getSelfUser().getId();    // ID of bot to check messages against

        processing.put(event.getGuild().getId(), channel.getId());
        channel.getIterableHistory().stream()
                .filter(message -> message.getAuthor().getId().equals(botId) && (count[0]-- > 0))
                .forEach((message ->
                {
                    message.getChannel().sendTyping().queue();

                    // sleep for half a second before continuing
                    try { Thread.sleep(500); }
                    catch (InterruptedException ignored) {}

                    Bson query = eq("messageId", message.getId());
                    if (Main.getDBDriver().getEventCollection().count(query) == 0)
                    {
                        MessageUtilities.deleteMsg(message);
                    }
                }));
        processing.remove(event.getGuild().getId(), channel.getId());

        // send success message
        String content = "Finished purging old message.";
        MessageUtilities.sendMsg(content, event.getChannel(), null);
    }
}
