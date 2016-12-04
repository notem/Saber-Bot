package io.schedulerbot.utils;

import io.schedulerbot.Main;

import net.dv8tion.jda.core.MessageHistory;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 *  file: MessageListener.java
 *  the Listener object that gets attached to the JDAbot
 *  when an event is received, this object calls it's related on... function.
 */
public class MessageListener extends ListenerAdapter
{

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        String content = event.getMessage().getContent();   // the raw string the user sent
        String userId = event.getAuthor().getId();          // the ID of the user
        String origin = event.getChannel().getName();       // the name of the originating text channel

        // if the message was received private, it's either an admin command or a help/setup command
        if( event.isFromType(ChannelType.PRIVATE) )
        {
            if( content.startsWith(BotConfig.PREFIX + "help") || content.startsWith(BotConfig.PREFIX + "setup") )
            {
                Main.handleGeneralCommand(Main.commandParser.parse(content, event));
            }
            else if( content.startsWith(BotConfig.ADMIN_PREFIX) && userId.equals(BotConfig.ADMIN_ID) )
            {
                Main.handleAdminCommand(Main.commandParser.parse(content, event));
            }
        }

        // bot listens for all messages with PREFIX and originating from CONTROL_CHAN channel
        else if( content.startsWith(BotConfig.PREFIX) &&
                (origin.equals(BotConfig.CONTROL_CHAN) || BotConfig.CONTROL_CHAN.isEmpty()))
        {
            Main.handleGeneralCommand(Main.commandParser.parse(content, event));
        }

        // bot also listens on EVENT_CHAN messages
        else if(origin.equals(BotConfig.EVENT_CHAN))
        {
            // if it is it's own message, parse it into a thread
            if( userId.equals( Main.getBotSelfUser().getId() ) )
            {
                String guildId = event.getGuild().getId();
                Main.handleEventEntry( Main.scheduler.parse(event.getMessage()), guildId);
            }
            // otherwise, attempt to delete the message
            else
            {
                MessageUtilities.deleteMsg( event.getMessage(), null );
            }
        }
    }

    @Override
    public void onReady( ReadyEvent event )
    {
        String msg = event.getJDA().getSelfUser().getName() + " reporting for duty! Reloading the event entries. . .";

        // announces to all attached discord servers with EVENT_CHAN configured that the bot is alive
        for( Guild guild : event.getJDA().getGuilds())
        {
            List<TextChannel> chan = guild.getTextChannelsByName( BotConfig.EVENT_CHAN, false );
            if( !chan.isEmpty() ) {
                MessageUtilities.sendAnnounce( msg, guild, null );

                // create a message history object
                MessageHistory history = chan.get(0).getHistory();

                // create a consumer
                Consumer<List<Message>> cons = (l) ->
                {
                    String reloadMsg;
                    for (Message eMsg : l)
                        if (eMsg.getAuthor().getId().equals(Main.getBotSelfUser().getId()))
                            Main.handleEventEntry(Main.scheduler.parse(eMsg), guild.getId());

                    ArrayList<Integer> entries = Main.getEntriesByGuild(guild.getId());
                    if (entries != null) {
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
                history.retrievePast(50).queue(cons);
            }
        }
    }
}
