package ws.nmathe.saber.core;

import ws.nmathe.saber.Main;

import ws.nmathe.saber.core.command.CommandHandler;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.GuildUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 * Listens for new messages and performs actions during it's own
 * startup and join/leave guild events.
 */
public class EventListener extends ListenerAdapter
{
    // store the bot botSettings for easy reference
    private String prefix = Main.getBotSettings().getCommandPrefix();
    private String adminPrefix = Main.getBotSettings().getAdminPrefix();
    private String adminId = Main.getBotSettings().getAdminId();
    private int maxEntries = Main.getBotSettings().getMaxEntries();
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

        if (event.isFromType(ChannelType.PRIVATE))
        {
            if (content.startsWith(prefix + "help") || content.startsWith(prefix + "setup"))
            {
                cmdHandler.handleCommand(event, 0);
                return;
            }
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

        if (origin.equals(controlChan) && content.startsWith(prefix))
        {
            // handle command received
            cmdHandler.handleCommand(event, 0);
            return;
        }

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
        Guild guild = event.getGuild();
        GuildUtilities.loadScheduleChannels( guild );
    }

    @Override
    public void onGuildLeave( GuildLeaveEvent event )
    {
        for( Integer id : scheduleManager.getEntriesByGuild( event.getGuild().getId() ) )
        {
            scheduleManager.removeId( id );
        }
    }
}
