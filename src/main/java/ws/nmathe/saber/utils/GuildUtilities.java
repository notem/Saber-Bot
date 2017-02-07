package ws.nmathe.saber.utils;

import net.dv8tion.jda.core.MessageHistory;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
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
        Collection<TextChannel> chans = getValidScheduleChannels(guild);

        // parseMsgFormat the history of each schedule channel
        for (TextChannel chan : chans)
        {
            loadScheduleChannel(chan);
        }
    }

    public static void loadScheduleChannel(TextChannel chan)
    {
        ScheduleManager schedManager = Main.getScheduleManager();
        ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();
        int maxEntries = Main.getBotSettings().getMaxEntries();

        MessageHistory history = chan.getHistory();

        // ready a consumer to parseMsgFormat the history
        Consumer<List<Message>> cons = (l) ->
        {
            if(l.size()<1)
                return;

            int i = 0;
            if( chanSetManager.loadSettings(l.get(0)) )
                i++;

            for (; i < l.size(); i++)
            {
                Message message = l.get(i);
                if (message.getAuthor().getId().equals(Main.getBotSelfUser().getId()))
                {
                    schedManager.addEntry(message);
                } else
                    MessageUtilities.deleteMsg(message, null);
            }

            //chanSetManager.sendSettingsMsg(chan);
        };

        // retrieve history and have the consumer act on it
        history.retrievePast((maxEntries >= 0) ? maxEntries * 2 : 50).queue(cons);

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
