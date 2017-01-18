package ws.nmathe.saber.utils;

import net.dv8tion.jda.core.MessageHistory;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 */
public class GuildUtilities
{
    public static void loadScheduleChannels(Guild guild)
    {
        ScheduleManager scheduleManager = Main.getScheduleManager();
        ChannelSettingsManager channelSettingsManager = Main.getChannelSettingsManager();
        int maxEntries = Main.getBotSettings().getMaxEntries();

        Collection<TextChannel> chans = getValidScheduleChannels(guild);

        // parseMsgFormat the history of each schedule channel
        for (TextChannel chan : chans)
        {
            MessageHistory history = chan.getHistory();

            // ready a consumer to parseMsgFormat the history
            Consumer<List<Message>> cons = (l) ->
            {
                for (Message message : l)
                {
                    if (message.getAuthor().getId().equals(Main.getBotSelfUser().getId()))
                    {
                        if (message.getRawContent().startsWith("```java"))
                            channelSettingsManager.loadSettings(message);
                        else
                            scheduleManager.addEntry(message);
                    } else
                        MessageUtilities.deleteMsg(message, null);
                }

                channelSettingsManager.checkChannel(chan);
            };

            // retrieve history and have the consumer act on it
            history.retrievePast((maxEntries >= 0) ? maxEntries * 2 : 50).queue(cons);
        }
    }

    public static List<TextChannel> getValidScheduleChannels(Guild guild)
    {
        String name = Main.getBotSettings().getScheduleChan();
        List<TextChannel> chans = new ArrayList<>();

        Member botAsMember = guild.getMember(Main.getBotSelfUser());
        List<Permission> perms = Arrays.asList( // required permissions
                Permission.MESSAGE_HISTORY, Permission.MESSAGE_READ,
                Permission.MESSAGE_WRITE, Permission.MESSAGE_MANAGE
        );

        for( TextChannel chan : guild.getTextChannels() )
        {
            if( chan.getName().startsWith( name ) && botAsMember.hasPermission(chan, perms) )
            {
                chans.add(chan);
            }
        }

        return chans;
    }
}
