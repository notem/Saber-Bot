package io.schedulerbot.utils;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import java.util.function.Consumer;

/**
 * A collection of method wrappers for sending different types of messages to specific channels
 */
public class MessageUtilities
{
    /**
     * send a message to a message channel, use sendPrivateMsg if the receiving channel is
     * a private channel.
     *
     * @param content The message string to send
     * @param chan The channel to send to
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void sendMsg( String content, MessageChannel chan, Consumer<Message> action )
    {
        try
        {
            chan.sendMessage(content).queue( action );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }

    /**
     * sends a message to a private message channel, opening the channel before use
     *
     * @param content the message to send as a string
     * @param user the user to send the private message to
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void sendPrivateMsg(String content, User user, Consumer<Message> action )
    {
        try
        {
            user.openPrivateChannel();
            sendMsg( content, user.getPrivateChannel(), action );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }

    /**
     * sends a message to the announcement channel of a guild
     *
     * @param content the message to send
     * @param guild the guild to send the message to
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void sendAnnounce(String content, Guild guild, Consumer<Message> action )
    {
        if(guild.getTextChannelsByName(BotConfig.EVENT_CHAN, false).isEmpty())
        {
            // send no message if the channel does not have the event channel configured
            return;
        }
        // otherwise, if the announcement channel is empty in botconfig, or the guild did not
        // setup an announcement channel. Send the message to their public channel
        if(BotConfig.ANNOUNCE_CHAN.isEmpty() ||
                guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).isEmpty())
        {
            sendMsg(content, guild.getPublicChannel(), action);
        }
        // otherwise send to the first announcement channel in the list
        else
        {
            sendMsg(content, guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0), action);
        }
    }

    /**
     * replaces the content of a message with a new content (string)
     *
     * @param content the new message content
     * @param msg the message object to edit
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void editMsg(String content, Message msg, Consumer<Message> action )
    {
        try
        {
            msg.editMessage(content).queue( action );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }

    /**
     * attempts to remove a message
     *
     * @param msg the message to delete
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void deleteMsg( Message msg, Consumer<Void> action )
    {
        try
        {
            msg.deleteMessage().queue( action );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }
}
