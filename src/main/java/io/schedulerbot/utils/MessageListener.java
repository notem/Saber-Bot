package io.schedulerbot.utils;

import io.schedulerbot.Main;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageDeleteEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

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
            Main.handleEventEntry(Main.eventEntryParser.parse(content, event), guildId);
        }
    }

    @Override
    public void onReady( ReadyEvent event )
    {
        // announces to all attached discord servers that the bot is alive
        String msg = event.getJDA().getSelfUser().getName() + " reporting for duty!";

        for( Guild guild : event.getJDA().getGuilds())
        {
            Main.sendAnnounce( msg, guild );
        }
    }
}
