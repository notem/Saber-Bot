package ws.nmathe.saber.core;

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

import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Updates.set;

/**
 * Listens for new messages and performs actions during it's own
 * startup and join/leave guild events.
 */
public class EventListener extends ListenerAdapter
{
    // store bot settings for easy reference
    private String prefix = Main.getBotSettingsManager().getCommandPrefix();
    private String adminPrefix = Main.getBotSettingsManager().getAdminPrefix();
    private String adminId = Main.getBotSettingsManager().getAdminId();
    private String controlChan = Main.getBotSettingsManager().getControlChan();

    private final RateLimiter reactionLimiter = new RateLimiter(Main.getBotSettingsManager().getCooldownThreshold());

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // store some properties of the message for use later
        String content = event.getMessage().getRawContent();   // the raw string the user sent
        String userId = event.getAuthor().getId();          // the ID of the user

        // leave the guild if the message author is black listed
        if(Main.getBotSettingsManager().getBlackList().contains(userId))
        {
            event.getGuild().leave().queue();
            return;
        }

        // process private commands
        if (event.isFromType(ChannelType.PRIVATE))
        {
            // help and setup general commands
            if (content.startsWith(prefix + "help") || content.startsWith("help"))
            {
                Main.getCommandHandler().handleCommand(event, 0, Main.getBotSettingsManager().getCommandPrefix());
                return;
            }
            // admin commands
            else if (content.startsWith(adminPrefix) && userId.equals(adminId))
            {
                Main.getCommandHandler().handleCommand(event, 1, Main.getBotSettingsManager().getAdminPrefix());
                return;
            }
            return;
        }

        // leave guild if the guild is blacklisted
        if(Main.getBotSettingsManager().getBlackList().contains(event.getGuild().getId()))
        {
            event.getGuild().leave().queue();
            return;
        }

        // if channel is a schedule for the guild
        if (Main.getScheduleManager().getSchedulesForGuild(event.getGuild().getId()).contains(event.getChannel().getId()))
        {
            // delete all other user's messages
            if (!userId.equals(Main.getBotJda().getSelfUser().getId()))
            {
                MessageUtilities.deleteMsg(event.getMessage(), null);
                return;
            }
        }

        // command processing
        GuildSettingsManager.GuildSettings guildSettings = Main.getGuildSettingsManager().getGuildSettings(event.getGuild().getId());
        String prefix = content.startsWith("<@"+Main.getBotJda().getSelfUser().getId()+"> ") ?
                "<@"+Main.getBotJda().getSelfUser().getId()+"> " : guildSettings.getPrefix();
        if(content.startsWith(prefix))
        {
            // check if command is restricted on the guild
            String trimmedContent = StringUtils.replaceOnce(content,prefix, "").trim();
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
                if(guildSettings.getCommandChannelId() != null &&
                        guildSettings.getCommandChannelId().equals(event.getChannel().getId()))
                {
                    Main.getCommandHandler().handleCommand(event, 0, prefix);
                    return;
                }
                else if (event.getChannel().getName().toLowerCase().equals(controlChan))
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
    public void onMessageDelete( MessageDeleteEvent event )
    {
        // delete the event if the delete message was an event message
        Main.getDBDriver().getEventCollection()
                .findOneAndDelete(eq("messageId", event.getMessageId()));
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

        // send welcome message to the server owner
        String welcomeMessage = "```diff\n- Joined```\n" +
                "**" + Main.getBotJda().getSelfUser().getName() + "**, a calendar bot, has been added to the guild own, '"
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
        HttpUtilities.updateStats();
    }

    @Override
    public void onGuildLeave( GuildLeaveEvent event )
    {
        // purge the leaving guild's entry list
        Main.getDBDriver().getEventCollection().deleteMany(eq("guildId", event.getGuild().getId()));
        // remove the guild's schedules
        Main.getDBDriver().getScheduleCollection().deleteOne(eq("guildId", event.getGuild().getId()));
        // remove the guild's settings
        Main.getDBDriver().getGuildCollection().deleteOne(eq("_id", event.getGuild().getId()));

        HttpUtilities.updateStats();
    }

    @Override
    public void onTextChannelDelete(TextChannelDeleteEvent event)
    {
        String cId = event.getChannel().getId();
        Document scheduleDocument = Main.getDBDriver().getScheduleCollection()
                .find(eq("_id", cId)).first();

        // if the deleted channel was a schedule, clear the db entries
        if(scheduleDocument != null)
        {
            Main.getDBDriver().getEventCollection().deleteMany(eq("channelId", cId));
            Main.getDBDriver().getScheduleCollection().deleteOne(eq("_id", cId));
        }
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event)
    {
        String memberId = event.getMember().getUser().getId();

        // remove user from any events they have rsvp'ed to
        Main.getDBDriver().getEventCollection()
                .find(or(eq("rsvp_yes", memberId ),
                        eq("rsvp_no", memberId),
                        eq("rsvp_undecided", memberId))
                ).forEach((Consumer<? super Document>) document ->
                {
                    List<String> rsvpYes = (List<String>) document.get("rsvp_yes");
                    List<String> rsvpNo = (List<String>) document.get("rsvp_no");
                    List<String> rsvpUndecided = (List<String>) document.get("rsvp_undecided");
                    Integer entryId = document.getInteger("_id");

                    // remove user from yes list
                    if(rsvpYes.contains(memberId))
                    {
                        rsvpYes.remove(memberId);
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_yes", rsvpYes));
                    }   // remove user from no list
                    if(rsvpNo.contains(memberId))
                    {
                        rsvpNo.remove(memberId);
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_no", rsvpNo));
                    }   // remove user from undecided list
                    if(rsvpUndecided.contains(memberId))
                    {
                        rsvpUndecided.remove(memberId);
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_undecided", rsvpUndecided));
                    }
                });
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event)
    {
        if(event.getUser().getId().equals(Main.getBotJda().getSelfUser().getId()))
            return;
        if(reactionLimiter.isOnCooldown(event.getUser().getId()))
            return;

        // if the schedule is rsvp enabled and the user added an rsvp emoji to the event
        // add the user to the appropriate rsvp list and remove the emoji
        try
        {
            if(Main.getScheduleManager().isRSVPEnabled(event.getChannel().getId()))
            {
                Document doc = Main.getDBDriver().getEventCollection()
                        .find(eq("messageId", event.getMessageId())).first();

                ScheduleEntry se = new ScheduleEntry(doc);

                if(doc == null) // shouldn't happen, but if it does
                    return;

                Integer entryId = doc.getInteger("_id");

                // current list of players
                List<String> rsvpYes = (List<String>) doc.get("rsvp_yes");
                List<String> rsvpNo = (List<String>) doc.get("rsvp_no");
                List<String> rsvpUndecided = (List<String>) doc.get("rsvp_undecided");

                MessageReaction.ReactionEmote emote = event.getReaction().getEmote();

                // if the user added the 'yes'/join emote
                if(emote.getName().equals(Main.getBotSettingsManager().getYesEmoji()))
                {
                    // add user to yes list (only if the event has not filled)
                    if(!rsvpYes.contains(event.getUser().getId()) && !se.isFull())
                    {
                        rsvpYes.add(event.getUser().getId());
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_yes", rsvpYes));
                    }   // remove user from no list
                    if(rsvpNo.contains(event.getUser().getId()))
                    {
                        rsvpNo.remove(event.getUser().getId());
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_no", rsvpNo));
                    }   // remove user from undecided list
                    if(rsvpUndecided.contains(event.getUser().getId()))
                    {
                        rsvpUndecided.remove(event.getUser().getId());
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_undecided", rsvpUndecided));
                    }

                    Main.getEntryManager().reloadEntry(entryId);
                    event.getReaction().removeReaction(event.getUser()).queue();
                }
                // if the user added the 'no'/leave emote
                else if(emote.getName().equals(Main.getBotSettingsManager().getNoEmoji()))
                {
                    // add user to no list
                    if(!rsvpNo.contains(event.getUser().getId()))
                    {
                        rsvpNo.add(event.getUser().getId());
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_no", rsvpNo));
                    }   // remove user from yes list
                    if(rsvpYes.contains(event.getUser().getId()))
                    {
                        rsvpYes.remove(event.getUser().getId());
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_yes", rsvpYes));
                    }   // remove user from undecided list
                    if(rsvpUndecided.contains(event.getUser().getId()))
                    {
                        rsvpUndecided.remove(event.getUser().getId());
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_undecided", rsvpUndecided));
                    }

                    Main.getEntryManager().reloadEntry(entryId);
                    event.getReaction().removeReaction(event.getUser()).queue();
                }
                // if the user added the undecided emote
                else if(emote.getName().equals(Main.getBotSettingsManager().getClearEmoji()))
                {
                    // add user to undecided list
                    if(!rsvpUndecided.contains(event.getUser().getId()))
                    {
                        rsvpUndecided.add(event.getUser().getId());
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_undecided", rsvpUndecided));
                    }   // remove user from yes list
                    if(rsvpYes.contains(event.getUser().getId()))
                    {
                        rsvpYes.remove(event.getUser().getId());
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_yes", rsvpYes));
                    }   // remove user from no list
                    if(rsvpNo.contains(event.getUser().getId()))
                    {
                        rsvpNo.remove(event.getUser().getId());
                        Main.getDBDriver().getEventCollection()
                                .updateOne(eq("_id", entryId), set("rsvp_no", rsvpNo));
                    }

                    Main.getEntryManager().reloadEntry(entryId);
                    event.getReaction().removeReaction(event.getUser()).queue();
                }
            }
        }
        catch(PermissionException ignored)
        { return; }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
