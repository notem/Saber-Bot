package ws.nmathe.saber.core;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageDeleteEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.core.settings.GuildSettingsManager;
import ws.nmathe.saber.utils.*;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;

/**
 * Executes actions for all events received by the JDA shards
 */
public class EventListener extends ListenerAdapter
{
    private final RateLimiter reactionLimiter = new RateLimiter(50);

    @Override
    public void onReady(ReadyEvent event)
    {
        if(event.getJDA().getShardInfo() != null)
        {
            Logging.info(this.getClass(), "Shard " + event.getJDA().getShardInfo().getShardId() + " ready!");
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // store some properties of the message for use later
        String content = event.getMessage().getContentRaw();   // the raw string the user sent
        String userId = event.getAuthor().getId();             // the ID of the user

        // ignore messages sent by itself
        //if(userId.equals(event.getJDA().getSelfUser().getId())) return;

        // leave the guild if the message author is blacklisted
        if(Main.getBotSettingsManager().getBlackList().contains(userId))
        {
            if (event.isFromType(ChannelType.TEXT)) event.getGuild().leave().queue();
            return;
        }

        // process admin commands
        String adminPrefix = Main.getBotSettingsManager().getAdminPrefix();
        String adminId = Main.getBotSettingsManager().getAdminId();
        if (content.startsWith(adminPrefix) && userId.equals(adminId))
        {
            Main.getCommandHandler().handleCommand(event, 1, Main.getBotSettingsManager().getAdminPrefix());
            return;
        }

        // process private commands
        String prefix = Main.getBotSettingsManager().getCommandPrefix();
        if (event.isFromType(ChannelType.PRIVATE) && !userId.equals(event.getJDA().getSelfUser().getId()))
        {
            String a = "("+prefix+")?help(.+)?$";
            String b = "("+prefix+")?oauth(.+)?$";
            // info and setup general commands
            if (content.matches(a) || content.matches(b))
            {
                Main.getCommandHandler().handleCommand(event, 0, prefix);
                return;
            }
            return;
        }

        // stop processing if the event is not from a guild text channel
        if (!event.isFromType(ChannelType.TEXT)) return;

        // leave the guild if the message author is black listed
        if(Main.getBotSettingsManager().getBlackList().contains(userId))
        {
            event.getGuild().leave().queue();
            return;
        }

        // leave guild if the guild is blacklisted
        if(Main.getBotSettingsManager().getBlackList().contains(event.getGuild().getId()))
        {
            event.getGuild().leave().queue();
            return;
        }

        // if channel is a schedule for the guild
        // delete all other user's messages
        //if (Main.getScheduleManager().getSchedulesForGuild(event.getGuild().getId()).contains(event.getChannel().getId()))
        //{
        //    if (!userId.equals(event.getJDA().getSelfUser().getId()))
        //    {
        //        MessageUtilities.deleteMsg(event.getMessage(), null);
        //        return;
        //    }
        //}

        /* command processing */
        // set prefix to local guild prefix or bot @mention
        GuildSettingsManager.GuildSettings guildSettings = Main.getGuildSettingsManager().getGuildSettings(event.getGuild().getId());
        if (content.matches("<@"+event.getJDA().getSelfUser().getId()+">([ ]*)(.)*"))
        {   // use @mention as prefix
            prefix = "<@"+event.getJDA().getSelfUser().getId()+">";
        }
        else
        {   // use local guild prefix
            prefix = guildSettings.getPrefix();
        }

        // operate on the command if the string starts with the prefix
        if(content.startsWith(prefix))
        {
            // remove the prefix from the command string
            String trimmedContent = StringUtils.replaceOnce(content, prefix, "");

            // check if command is restricted on the guild
            Boolean isRestricted = true;
            for(String command : guildSettings.getUnrestrictedCommands())
            {
                if(trimmedContent.startsWith(command))
                {
                    isRestricted = false;
                    break;
                }
            }

            // if the command is restricted on the guild
            // check if the guild has a custom command channel and if the channel IDs match,
            // otherwise check if the channel is equal to the default command channel name
            if(isRestricted)
            {
                String controlChannelName = Main.getBotSettingsManager().getControlChan();
                if(guildSettings.getCommandChannelId() != null &&
                        guildSettings.getCommandChannelId().equals(event.getChannel().getId()))
                {
                    Main.getCommandHandler().handleCommand(event, 0, prefix);
                    return;
                }
                else if (event.getChannel().getName().toLowerCase().equals(controlChannelName))
                {
                    Main.getCommandHandler().handleCommand(event, 0, prefix);
                    guildSettings.setCommandChannelId(event.getChannel().getId());
                    String body = "<#" + event.getChannel().getId() + "> is now set as my control channel.\n" +
                            "You can now safely rename <#" + event.getChannel().getId() + "> without affecting my behavior.";
                    MessageUtilities.sendMsg(body, event.getChannel(), null);
                    return;
                }
            }
            // if not restricted, process the command
            else
            {
                Main.getCommandHandler().handleCommand(event, 0, prefix);
                return;
            }
        }
    }

    @Override
    public void onGuildJoin( GuildJoinEvent event )
    {
        // leave guild if guild is blacklisted
        if(Main.getBotSettingsManager().getBlackList().contains(event.getGuild().getId()))
        {
            event.getGuild().leave().queue();
            return;
        }

        // identify which shard is responsible for the guild
        String guildId = event.getGuild().getId();
        JDA jda = Main.getShardManager().isSharding() ? Main.getShardManager().getShard(guildId) : Main.getShardManager().getJDA();

        // send welcome message to the server owner
        String welcomeMessage = "```diff\n- Joined```\n" +
                "**" + jda.getSelfUser().getName() + "**, a calendar bot, has been added to the guild you own, '"
                + event.getGuild().getName() + "'." +
                "\n\n" +
                "If this is your first time using the bot, you will need to create a new channel in your guild named" +
                " **" + Main.getBotSettingsManager().getControlChan() + "** to control the bot.\n" +
                "The bot will not listen to commands in any other channel!" +
                "\n\n" +
                "If you have not yet reviewed the **Quickstart** guide (as seen on the bots.discord.pw listing), " +
                "it may be found here: https://bots.discord.pw/bots/250801603630596100";
        MessageUtilities.sendPrivateMsg(
                welcomeMessage,
                event.getGuild().getOwner().getUser(),
                null
        );

        // update web stats
        JDA.ShardInfo info = event.getJDA().getShardInfo();
        HttpUtilities.updateStats(info==null ? null : info.getShardId());
    }

    @Override
    public void onGuildLeave( GuildLeaveEvent event )
    {
        /* Disabled reactive database pruning as the Discord API seems to like to send GuildLeave notifications
           during discord outages

        // purge the leaving guild's entry list
        Main.getDBDriver().getEventCollection().deleteMany(eq("guildId", event.getGuild().getId()));
        // remove the guild's schedules
        Main.getDBDriver().getScheduleCollection().deleteMany(eq("guildId", event.getGuild().getId()));
        // remove the guild's settings
        Main.getDBDriver().getGuildCollection().deleteOne(eq("_id", event.getGuild().getId()));
        */

        JDA.ShardInfo info = event.getJDA().getShardInfo();
        HttpUtilities.updateStats(info==null ? null : info.getShardId());
    }

    @Override
    public void onMessageDelete( MessageDeleteEvent event )
    {
        // delete the event if the delete message was an event message
        Main.getDBDriver().getEventCollection().findOneAndDelete(eq("messageId", event.getMessageId()));
    }

    @Override
    public void onTextChannelDelete(TextChannelDeleteEvent event)
    {
        String cId = event.getChannel().getId();

        // if the deleted channel was a schedule, clear the db entries
        if(Main.getScheduleManager().isSchedule(cId))
        {
            Main.getDBDriver().getEventCollection().deleteMany(eq("channelId", cId));
            Main.getDBDriver().getScheduleCollection().deleteOne(eq("_id", cId));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onGuildMemberLeave(GuildMemberLeaveEvent event)
    {
        String memberId = event.getUser().getId();

        // remove user from any events they have rsvp'ed to
        Collection<ScheduleEntry> entries = Main.getEntryManager().getEntriesFromGuild(event.getGuild().getId());
        for(ScheduleEntry se : entries)
        {
            boolean updateFlag = false;
            // check each rsvp group on the entry
            for(String key : se.getRsvpMembers().keySet())
            {
                List<String> members = se.getRsvpMembersOfType(key);
                if (members.contains(event.getUser().getId()))
                {
                    members.remove(event.getUser().getId());
                    se.setRsvpMembers(key, members);
                    updateFlag = true;
                }
            }
            if(updateFlag)
            {
                Main.getEntryManager().updateEntry(se, false);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onMessageReactionAdd(MessageReactionAddEvent event)
    {
        // stop processing if the event is not from a guild text channel
        if (!event.isFromType(ChannelType.TEXT)) return;

        // don't process reactions added on non RSVP channels
        if(!Main.getScheduleManager().isRSVPEnabled(event.getChannel().getId())) return;

        // don't process reactions added by the bot
        if(event.getUser().getId().equals(event.getJDA().getSelfUser().getId())) return;

        if(reactionLimiter.check(event.getUser().getId())) return;

        // if the schedule is rsvp enabled and the user added an rsvp emoji to the event
        // add the user to the appropriate rsvp list and remove the emoji
        try
        {
            Document doc = Main.getDBDriver().getEventCollection()
                    .find(eq("messageId", event.getMessageId())).first();

            if(doc != null)
            {
                ScheduleEntry se = new ScheduleEntry(doc);
                boolean removeReaction = se.handleRSVPReaction(event);
                if (removeReaction)
                {
                    // attempt to remove the reaction
                    Consumer<Throwable> errorProcessor = e ->
                    {
                        if(!(e instanceof PermissionException))
                        {
                            Logging.exception(this.getClass(), e);
                        }
                    };
                    event.getReaction().removeReaction(event.getUser()).queue(null, errorProcessor);
                }
            }
        }
        catch(PermissionException ignored) { }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
