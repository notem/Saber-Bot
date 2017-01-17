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

    public static void sendMsg( String content, MessageChannel chan )
    {
        try
        {
            chan.sendMessage(content).block();
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
            user.openPrivateChannel().block();
            sendMsg( content, user.getPrivateChannel(), action );
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
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

    public static void deleteMsg( Message msg )
    {
        try
        {
            msg.deleteMessage().block();
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
    }
}
