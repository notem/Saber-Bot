package io.schedulerbot.utils;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.exceptions.PermissionException;

/**
 */
public class MessageUtilities
{
    /**
     * send a message to a message channel, use sendPrivateMsg if the receiving channel is
     * a private channel.
     *
     * @param content The message string to send
     * @param chan The channel to send to
     */
    public static void sendMsg( String content, MessageChannel chan )
    {
        try
        {
            chan.sendMessage(content).queue();
        }
        catch (Exception ignored)
        { }
    }

    /**
     * sends a message to a private message channel, opening the channel before use
     *
     * @param content the message to send as a string
     * @param user the user to send the private message to
     */
    public static void sendPrivateMsg(String content, User user )
    {
        try
        {
            user.openPrivateChannel();
            sendMsg( content, user.getPrivateChannel() );
        }
        catch( Exception ignored)
        { }
    }

    /**
     * sends a message to the announcement channel of a guild
     *
     * @param content the message to send
     * @param guild the guild to send the message to
     */
    public static void sendAnnounce(String content, Guild guild )
    {
        if(BotConfig.ANNOUNCE_CHAN.isEmpty() ||
                guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).isEmpty())
            sendMsg(content, guild.getPublicChannel());

        else
            sendMsg(content, guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0));
    }

    /**
     * replaces the content of a message with a new content (string)
     *
     * @param content the new message content
     * @param msg the message object to edit
     */
    public static void editMsg(String content, Message msg )
    {
        try
        {
            msg.editMessage(content).queue();
        }
        catch( Exception ignored)
        { }
    }

    /**
     * attempts to remove a message
     *
     * @param msg the message to delete
     */
    public static void deleteMsg( Message msg )
    {
        try
        {
            msg.deleteMessage().queue();
        }
        catch( PermissionException ignored)
        { }
    }
}
