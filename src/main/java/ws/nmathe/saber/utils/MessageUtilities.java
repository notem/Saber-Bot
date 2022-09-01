package ws.nmathe.saber.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

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
        if(content.isEmpty()) return;

        try
        {
            chan.sendMessage(content).queue(action, e ->
            {
                if (!(e instanceof PermissionException))
                {
                    Logging.exception(MessageUtilities.class, e);
                }
            });
        }
        catch( PermissionException ignored) { }
        catch( Exception e)
        {
            Logging.exception(MessageUtilities.class, e);
        }
    }

    /// version which takes a message rather than a string
    public static void sendMsg(MessageCreateData message, MessageChannel chan, Consumer<Message> action )
    {
        if (message == null) return;
        if (message.getContent().isEmpty() && message.getEmbeds().isEmpty()) return;

        try
        {
            chan.sendMessage(message).queue(action, e ->
            {
                if(!(e instanceof PermissionException))
                {
                    Logging.exception(MessageUtilities.class, e);
                }
            });
        }
        catch( PermissionException ignored) { }
        catch(Exception e)
        {
            Logging.warn(MessageUtilities.class, e.getMessage());
        }
    }

    /// customizable error handling (used by list command temporarily)
    public static void sendMsg(MessageCreateData message, MessageChannel chan, Consumer<Message> action, Consumer<Throwable> error )
    {
        if (message == null) return;
        if (message.getContent().isEmpty() && message.getEmbeds().isEmpty()) return;

        try
        {
            chan.sendMessage(message).queue(action, error);
        }
        catch(Exception e)
        {
            Logging.warn(MessageUtilities.class, e.getMessage());
        }
    }

    /// blocking version
    public static Message sendMsg(MessageCreateData message, MessageChannel chan)
    {
        if (message == null) return null;
        if (message.getContent().isEmpty() && message.getEmbeds().isEmpty()) return null;

        try
        {
            return chan.sendMessage(message).complete();
        }
        catch (PermissionException e) { return null; }
        catch (Exception e)
        {
            Logging.exception( MessageUtilities.class, e );
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
        if (content.isEmpty()) return;
        if (user.isBot()) return;

        try
        {
            user.openPrivateChannel().queue(privateChannel -> sendMsg(content, privateChannel, action), null);
        }
        catch (PermissionException ignored) { }
        catch (Exception e)
        {
            Logging.exception( MessageUtilities.class, e );
        }
    }

    /**
     * replaces the content of a message with a new content (string)
     * , asynchronous (non-blocking)
     * @param newMsg the new message content
     * @param msg the message object to edit
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void editMsg(MessageEditData newMsg, Message msg, Consumer<Message> action )
    {
        if (newMsg == null) return;
        if (newMsg.getContent().isEmpty() && newMsg.getEmbeds().isEmpty()) return;

        try
        {
            msg.editMessage(newMsg).queue(action, e ->
            {
                if (!(e instanceof PermissionException))
                {
                    Logging.exception(MessageUtilities.class, e);
                }
            });
        }
        catch (PermissionException ignored) {}
        catch (Exception e)
        {
            Logging.exception(MessageUtilities.class, e);
        }
    }

    // blocking
    public static Message editMsg(MessageEditData newMsg, Message msg)
    {
        if (newMsg == null) return null;
        if (newMsg.getContent().isEmpty() && newMsg.getEmbeds().isEmpty()) return null;

        try
        {
            return msg.editMessage(newMsg).complete();
        }
        catch (PermissionException e) { return null; }
        catch (Exception e)
        {
            Logging.exception( MessageUtilities.class, e );
            return null;
        }
    }

    /**
     * attempts to remove a message, asynchronous (non-blocking)
     * @param msg the message to delete
     * @param action a non null Consumer will do operations on the results returned
     */
    public static void deleteMsg(Message msg, Consumer<Void> action)
    {
        try
        {
            msg.delete().queue(action, e ->
            {
                if (!(e instanceof PermissionException))
                {
                    Logging.exception(MessageUtilities.class, e);
                }
            });
        }
        catch (PermissionException ignored) { }
        catch (Exception e)
        {
            Logging.exception(MessageUtilities.class, e);
        }
    }

    public static Void deleteMsg(Message msg)
    {
        try
        {
            return msg.delete().complete();
        }
        catch (PermissionException ignored) { }
        catch (Exception e)
        {
            Logging.exception(MessageUtilities.class, e);
        }
        return null;
    }
}
