package ws.nmathe.saber.utils;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import java.util.function.Consumer;

/**
 * A collection of method wrappers for sending different types of messages to specific channels
 * Consumer may be passed into functions to operate on the result of the RestAction, exceptions
 * are caught and printed to stdout
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
    public static void sendMsg(String content, MessageChannel chan, Consumer<Message> action )
    {
        try
        {
            chan.sendMessage(content).queue( action, null );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }

    /// version which takes a message rather than a string
    public static void sendMsg(Message message, MessageChannel chan, Consumer<Message> action )
    {
        try
        {
            chan.sendMessage(message).queue( action, null );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }

    /// blocking version
    public static Message sendMsg(Message message, MessageChannel chan)
    {
        try
        {
            return chan.sendMessage(message).complete();
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
            return null;
        }
    }

    /**
     * sends a message to a private message channel, opening the channel before use
     *, asynchronous (non-blocking)
     * @param content the message to send as a string
     * @param user the user to send the private message to
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void sendPrivateMsg(String content, User user, Consumer<Message> action )
    {
        try
        {
            user.openPrivateChannel().complete();
            sendMsg( content, user.getPrivateChannel(), action );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }

    /**
     * replaces the content of a message with a new content (string)
     * , asynchronous (non-blocking)
     * @param content the new message content
     * @param msg the message object to edit
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void editMsg(String content, Message msg, Consumer<Message> action )
    {
        try
        {
            msg.editMessage(content).queue( action, null );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }

    public static void editMsg(Message newMsg, Message msg, Consumer<Message> action )
    {
        try
        {
            msg.editMessage(newMsg).queue( action, null );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }

    public static Message editMsg(Message newMsg, Message msg)
    {
        try
        {
            return msg.editMessage(newMsg).complete();
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
            return null;
        }
    }

    /**
     * attempts to remove a message, asynchronous (non-blocking)
     *
     * @param msg the message to delete
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void deleteMsg( Message msg, Consumer<Void> action )
    {
        try
        {
            msg.delete().queue( action, null );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }
}
