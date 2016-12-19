package io.schedulerbot.core;

import io.schedulerbot.Main;

import io.schedulerbot.utils.MessageUtilities;
import io.schedulerbot.utils.VerifyUtilities;
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

    private ScheduleManager scheduleManager = Main.scheduleManager;
    private GuildSettingsManager guildSettingsManager = Main.guildSettingsManager;

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
                return;
            }
            else if (content.startsWith(adminPrefix) && userId.equals(adminId))
            {
                Main.handleAdminCommand(Main.commandParser.parse(content, event));
                return;
            }
        }

        // if main schedule channel is not setup give up
        if( !VerifyUtilities.verifyScheduleChannel( event.getGuild() ) )
        {
           return;
        }

        if (origin.equals(controlChan) && content.startsWith(prefix))
        {
            guildSettingsManager.checkGuild( event.getGuild() );
            Main.handleGeneralCommand(Main.commandParser.parse(content, event));
            return;
        }

        if (origin.equals(scheduleChan))
        {
            // if it is it's own message, parse it into a thread
            if (userId.equals(Main.getBotSelfUser().getId()))
            {
                if( !event.getMessage().getRawContent().startsWith("```java") )
                {
                    scheduleManager.addEntry(event.getMessage());
                    guildSettingsManager.sendSettingsMsg( event.getGuild() );
                }
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

                // create a message history object
                MessageHistory history = chan.get(0).getHistory();

                // create a consumer
                Consumer<List<Message>> cons = (l) -> {
                    String reloadMsg;
                    for (Message message : l)
                    {
                        if (message.getAuthor().getId().equals(Main.getBotSelfUser().getId()))
                        {
                            if (message.getRawContent().startsWith("```java"))
                                guildSettingsManager.loadSettings( message );
                            else
                                scheduleManager.addEntry(message);
                        }
                        else
                            MessageUtilities.deleteMsg( message, null );
                    }

                    guildSettingsManager.checkGuild(guild);

                    MessageUtilities.sendAnnounce(msg, guild, (message)->{
                        try
                        {
                            Thread.sleep(1000*4);
                        }
                        catch( Exception ignored )
                        { }
                        MessageUtilities.deleteMsg( message, null );
                    });

                    ArrayList<Integer> entries = scheduleManager.getEntriesByGuild(guild.getId());
                    if (entries != null)
                    {
                        reloadMsg = "There ";
                        if (entries.size() > 1)
                            reloadMsg += "are " + entries.size() + " events on the schedule.";
                        else
                            reloadMsg += "is a single event on the schedule.";
                    }
                    else
                        reloadMsg = "There are no events on the schedule.";
                    MessageUtilities.sendAnnounce(reloadMsg, guild, (message)->{
                        try
                        {
                            Thread.sleep(1000*4);
                        }
                        catch( Exception ignored )
                        { }
                        MessageUtilities.deleteMsg( message, null );
                    });
                };

                // retrieve history and have the consumer act on it
                history.retrievePast((maxEntries>=0) ? maxEntries*2:50).queue(cons);
            }
        }
    }
}
