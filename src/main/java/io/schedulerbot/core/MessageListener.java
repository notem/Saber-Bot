package io.schedulerbot.core;

import io.schedulerbot.Main;

import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.MessageHistory;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Servers three purposes:
 * 1) listens for command messages on private and bot channels
 * 2) listens for new schedule entries
 * 3) recovers after shutdown by parsing the schedule when bot is ready
 */
public class MessageListener extends ListenerAdapter
{
    // store the bot settings to easy reference
    private String prefix = Main.getSettings().getCommandPrefix();
    private String adminPrefix = Main.getSettings().getAdminPrefix();
    private String adminId = Main.getSettings().getAdminId();
    private int maxEntries = Main.getSettings().getMaxEntries();
    private String controlChan = Main.getSettings().getControlChan();
    private String scheduleChan = Main.getSettings().getScheduleChan();

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // store some properties of the message for use later
        String content = event.getMessage().getContent();   // the raw string the user sent
        String userId = event.getAuthor().getId();          // the ID of the user
        String origin = event.getChannel().getName();       // the name of the originating text channel

        if (event.isFromType(ChannelType.PRIVATE))
        {
            if (content.startsWith(prefix + "help") || content.startsWith(prefix + "setup"))
            {
                Main.handleGeneralCommand(Main.commandParser.parse(content, event));
            }
            else if (content.startsWith(adminPrefix) && userId.equals(adminId))
            {
                Main.handleAdminCommand(Main.commandParser.parse(content, event));
            }
        }

        else if (origin.equals(controlChan) && content.startsWith(prefix))
        {
            Main.handleGeneralCommand(Main.commandParser.parse(content, event));
        }

        else if (origin.equals(scheduleChan))
        {
            // if it is it's own message, parse it into a thread
            if (userId.equals(Main.getBotSelfUser().getId()))
            {
                String guildId = event.getGuild().getId();
                Main.handleScheduleEntry(Main.scheduleParser.parse(event.getMessage()), guildId);
            }
            // otherwise, attempt to delete the message
            else
            {
                MessageUtilities.deleteMsg(event.getMessage(), null);
            }
        }
    }

    @Override
    public void onReady(ReadyEvent event)
    {
        String msg = event.getJDA().getSelfUser().getName() + " reporting for duty! Reloading the event entries. . .";

        // announces to all attached discord servers with EVENT_CHAN configured that the bot is alive
        for (Guild guild : event.getJDA().getGuilds())
        {
            List<TextChannel> chan = guild.getTextChannelsByName(scheduleChan, false);
            if (!chan.isEmpty())
            {
                MessageUtilities.sendAnnounce(msg, guild, null);

                // create a message history object
                MessageHistory history = chan.get(0).getHistory();

                // create a consumer
                Consumer<List<Message>> cons = (l) -> {
                    String reloadMsg;
                    for (Message eMsg : l)
                    {
                        if (eMsg.getAuthor().getId().equals(Main.getBotSelfUser().getId()))
                            Main.handleScheduleEntry(Main.scheduleParser.parse(eMsg), guild.getId());
                    }

                    ArrayList<Integer> entries = Main.getEntriesByGuild(guild.getId());
                    if (entries != null)
                    {
                        reloadMsg = "There ";
                        if (entries.size() > 1)
                            reloadMsg += "are " + entries.size() + " events on the schedule.";
                        else
                            reloadMsg += "is a single event on the schedule.";
                    } else
                        reloadMsg = "There are no events on the schedule.";
                    MessageUtilities.sendAnnounce(reloadMsg, guild, null);
                };

                // retrieve history and have the consumer act on it
                history.retrievePast((maxEntries>=0) ? maxEntries*2:50).queue(cons);
            }
        }
    }
}
