package ws.nmathe.saber.core.settings;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;

import java.time.ZoneId;
import java.util.HashMap;

/**
 * Manage's the settings for all valid schedule channels
 */
public class ChannelSettingsManager
{
    private HashMap<String, ChannelSettings> settingsByChannel;

    public ChannelSettingsManager()
    {
        settingsByChannel = new HashMap<>();
    }

    public void sendSettingsMsg( MessageChannel channel )
    {
        ChannelSettings settings = settingsByChannel.get( channel.getId() );
        if( settings == null )
            settingsByChannel.put(channel.getId(), new ChannelSettings( channel ));
        else
            settings.reloadSettingsMsg();
    }

    public void loadSettings( Message message )
    {
        settingsByChannel.put( message.getChannel().getId(), new ChannelSettings( message ) );
    }

    public String getAnnounceChan(String cId )
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            return Main.getBotSettings().getAnnounceChan();
        }
        return settings.announceChannel;
    }

    public String getAnnounceFormat(String cId )
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            return Main.getBotSettings().getAnnounceFormat();
        }
        return settings.announceFormat;
    }

    public String getClockFormat(String cId )
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            return Main.getBotSettings().getClockFormat();
        }
        return settings.clockFormat;
    }

    public ZoneId getTimeZone(String cId )
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            return ZoneId.of(Main.getBotSettings().getTimeZone());
        }
        return settings.timeZone;
    }

    public String getStyle(String cId)
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            return "embed";
        }
        return settings.messageStyle;
    }

    public void setAnnounceChan(String cId, String chan )
    {
        settingsByChannel.get(cId).announceChannel = chan;
        settingsByChannel.get(cId).reloadSettingsMsg();
    }

    public void setAnnounceFormat(String cId, String format )
    {
        settingsByChannel.get(cId).announceFormat = format;
        settingsByChannel.get(cId).reloadSettingsMsg();
    }

    public void setClockFormat(String cId, String clock )
    {
        settingsByChannel.get(cId).clockFormat = clock;
        settingsByChannel.get(cId).reloadSettingsMsg();
    }

    public void setTimeZone(String cId, ZoneId zone )
    {
        settingsByChannel.get(cId).timeZone = zone;
        settingsByChannel.get(cId).reloadSettingsMsg();
    }

    public void setStyle(String cId, String style)
    {
        settingsByChannel.get(cId).messageStyle = style;
        settingsByChannel.get(cId).reloadSettingsMsg();
    }
}
