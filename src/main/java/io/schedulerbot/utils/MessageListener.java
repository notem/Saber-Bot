package io.schedulerbot.utils;

import io.schedulerbot.Main;

import net.dv8tion.jda.core.MessageHistory;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

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
    public void onMessageReceived( MessageReceivedEvent event )
    {
        String content = event.getMessage().getContent();   // the raw string the user sent
        String userId = event.getAuthor().getId();            // the ID of the user
        String origin = event.getChannel().getName();       // the name of the originating text channel
        String guildId = event.getGuild().getId();

        // bot listens for all messages with PREFIX and originating from CONTROL_CHAN channel
        if( content.startsWith(BotConfig.PREFIX) &&
                (origin.equals(BotConfig.CONTROL_CHAN) || BotConfig.CONTROL_CHAN.isEmpty()))
            Main.handleCommand( Main.commandParser.parse(content, event) );

        // bot also listens on EVENT_CHAN for it's own messages
        if( userId.equals( Main.jda.getSelfUser().getId() ) &&
                origin.equals(BotConfig.EVENT_CHAN)) {
            Main.handleEventEntry(Main.eventEntryParser.parse(event.getMessage()), guildId);
        }
    }

    @Override
    public void onReady( ReadyEvent event )
    {

        // announces to all attached discord servers that the bot is alive
        String msg = event.getJDA().getSelfUser().getName() + " reporting for duty! Reloading the event entries. . .";

        for( Guild guild : event.getJDA().getGuilds())
        {
            Main.sendAnnounce( msg, guild );

            // create a message history object
            MessageHistory history = guild.getTextChannelsByName( BotConfig.EVENT_CHAN, false ).get(0).getHistory();

            // create a consumer
            Consumer<List<Message>> cons = (l) ->
            {
                String reloadMsg;
                for( Message eMsg : l )
                    if( eMsg.getAuthor().getId().equals(Main.jda.getSelfUser().getId()) )
                        Main.handleEventEntry( Main.eventEntryParser.parse( eMsg ), guild.getId() );
                if( Main.entriesByGuild.containsKey( guild.getId() ) )
                {
                    reloadMsg = "There ";
                    if( Main.entriesByGuild.get(guild.getId()).size()>1 )
                        reloadMsg += "are " + Main.entriesByGuild.get(guild.getId()).size() + " events on the schedule.";
                    else
                        reloadMsg += "is a single event on the schedule.";
                }
                else
                    reloadMsg = "There are no events on the schedule.";
                Main.sendAnnounce( reloadMsg, guild );
            };

            // retrieve history and have the consumer act on it
            history.retrievePast( 50 ).queue( cons );

        }
    }
}
