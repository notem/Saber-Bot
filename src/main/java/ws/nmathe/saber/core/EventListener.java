package ws.nmathe.saber.core;

import ws.nmathe.saber.Main;

import ws.nmathe.saber.core.command.CommandHandler;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;
import ws.nmathe.saber.utils.*;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 * Listens for new messages and performs actions during it's own
 * startup and join/leave guild events.
 */
public class EventListener extends ListenerAdapter
{
    // store bot settings for easy reference
    private String prefix = Main.getBotSettings().getCommandPrefix();
    private String adminPrefix = Main.getBotSettings().getAdminPrefix();
    private String adminId = Main.getBotSettings().getAdminId();
    private String controlChan = Main.getBotSettings().getControlChan();
    private String scheduleChan = Main.getBotSettings().getScheduleChan();

    private ScheduleManager scheduleManager = Main.getScheduleManager();
    private ChannelSettingsManager channelSettingsManager = Main.getChannelSettingsManager();
    private CommandHandler cmdHandler = Main.getCommandHandler();

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // store some properties of the message for use later
        String content = event.getMessage().getContent();   // the raw string the user sent
        String userId = event.getAuthor().getId();          // the ID of the user
        String origin = event.getChannel().getName().toLowerCase();       // the name of the originating text channel

        // process private commands
        if (event.isFromType(ChannelType.PRIVATE))
        {
            // help and setup general commands
            if (content.startsWith(prefix + "help") || content.startsWith(prefix + "setup"))
            {
                cmdHandler.handleCommand(event, 0);
                return;
            }
            // admin commands
            else if (content.startsWith(adminPrefix) && userId.equals(adminId))
            {
                cmdHandler.handleCommand(event, 1);
                return;
            }
            return;
        }

        // if main schedule channel is not setup go no further
        if( !VerifyUtilities.verifyScheduleChannel( event.getGuild() ) )
        {
           return;
        }

        // process a command if it originates in the control channel and with appropriate prefix
        if (origin.equals(controlChan) && content.startsWith(prefix))
        {
            // handle command received
            cmdHandler.handleCommand(event, 0);
            return;
        }

        // keep schedule channels clean and resend channel settings message when a new entry is added
        if (origin.startsWith(scheduleChan))
        {
            // delete other user's messages
            if (!userId.equals(Main.getBotSelfUser().getId()))
                MessageUtilities.deleteMsg(event.getMessage(), null);

            // if it is from myself, resend the guild botSettings message (so that it is at the bottom)
            else if(!content.startsWith("```java"))
                channelSettingsManager.sendSettingsMsg(event.getChannel());
        }
    }

    @Override
    public void onGuildJoin( GuildJoinEvent event )
    {
        // load channels of joining guild
        GuildUtilities.loadScheduleChannels( event.getGuild() );

        String auth =Main.getBotSettings().getWebToken();
        if( auth != null )
        {
            HttpUtilities.updateCount(Main.getBotJda().getGuilds().size(), auth);
        }
    }

    @Override
    public void onGuildLeave( GuildLeaveEvent event )
    {
        // purge the leaving guild's entry list
        for( Integer id : scheduleManager.getEntriesByGuild( event.getGuild().getId() ) )
        {
            scheduleManager.removeEntry( id );
        }

        String auth =Main.getBotSettings().getWebToken();
        if( auth != null )
        {
            HttpUtilities.updateCount(Main.getBotJda().getGuilds().size(), auth);
        }
    }
}
