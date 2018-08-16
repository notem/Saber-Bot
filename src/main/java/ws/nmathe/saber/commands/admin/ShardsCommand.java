package ws.nmathe.saber.commands.admin;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.utils.MessageUtilities;

import java.util.function.Consumer;

/**
 * retrieves the status of bot shards for the admin
 */
public class ShardsCommand implements Command
{
    @Override
    public String name()
    {
        return "shards";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        return null;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        Consumer<String> sendMsg = (msg) ->
        {
            if(event.isFromType(ChannelType.PRIVATE))
            {
                MessageUtilities.sendPrivateMsg( msg, event.getAuthor(), null );
            }
            else
            {
                MessageUtilities.sendMsg( msg, event.getTextChannel(), null );
            }
        };

        String msg = "I am not sharded!";
        if(Main.getShardManager().isSharding())
        {
            msg = "```javascript\n" +
                    "\"Total Shards\" (" + Main.getBotSettingsManager().getShardTotal() + ")\n";
            for(JDA shard : Main.getShardManager().getShards())
            {
                JDA.ShardInfo info = shard.getShardInfo();
                msg += "\n[Shard-" + info.getShardId() + "]\n" +
                        "       Status: \"" + shard.getStatus().toString() + "\"\n" +
                        "         Ping: \"" + shard.getPing() + "\"\n" +
                        "       Guilds: \"" + shard.getGuilds().size() + "\"\n" +
                        "        Users: \"" + shard.getUsers().size() + "\"\n" +
                        "ResponseTotal: \"" + shard.getResponseTotal() + "\"\n";

                if(msg.length() > 1900)
                {
                    msg += "```";
                    sendMsg.accept(msg);
                    msg = "```javascript\n";
                }
            }

            msg += "```";
        }

        sendMsg.accept(msg);
    }
}
