package ws.nmathe.saber.core.settings;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import ws.nmathe.saber.Main;

import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    public boolean loadSettings(Message msg )
    {
        String raw;
        if(msg.getEmbeds().isEmpty())
            raw = msg.getRawContent();
        else
            raw = msg.getEmbeds().get(0).getDescription();

        if( !raw.contains("```java\n") )
        {
            this.sendSettingsMsg(msg.getChannel());
            return false;
        }
        else
        {
            settingsByChannel.put(msg.getChannel().getId(), new ChannelSettings(msg));
            return true;
        }
    }

    public String getAnnounceChan(String cId )
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            this.sendSettingsMsg(Main.getBotJda().getTextChannelById(cId));
            return Main.getBotSettings().getAnnounceChan();
        }
        return settings.announceChannel;
    }

    public String getAnnounceFormat(String cId )
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            this.sendSettingsMsg(Main.getBotJda().getTextChannelById(cId));
            return Main.getBotSettings().getAnnounceFormat();
        }
        return settings.announceFormat;
    }

    public String getClockFormat(String cId )
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            this.sendSettingsMsg(Main.getBotJda().getTextChannelById(cId));
            return Main.getBotSettings().getClockFormat();
        }
        return settings.clockFormat;
    }

    public ZoneId getTimeZone(String cId )
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            this.sendSettingsMsg(Main.getBotJda().getTextChannelById(cId));
            return ZoneId.of(Main.getBotSettings().getTimeZone());
        }
        return settings.timeZone;
    }

    public String getStyle(String cId)
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            this.sendSettingsMsg(Main.getBotJda().getTextChannelById(cId));
            return "embed";
        }
        return settings.messageStyle;
    }

    public String getAddress(String cId)
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
        {
            this.sendSettingsMsg(Main.getBotJda().getTextChannelById(cId));
            return "off";
        }
        return settings.calendarAddress;
    }

    public boolean checkSync(String cId)
    {
        ChannelSettings settings = settingsByChannel.get(cId);
        if( settings == null )
            return false;
        if( settings.calendarAddress.equals("off") )
            return false;
        if( settings.nextSync == null )
            return true;

        return settings.nextSync.isBefore(ZonedDateTime.now());
    }

    public void adjustSync(String cId)
    {
        int rand = (int)((Math.random()*8)+20);  // safeguard to protect against overloaded syncing
        settingsByChannel.get(cId).nextSync = ZonedDateTime.now().plusHours(rand);
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

    public void setAddress(String cId, String address)
    {
        settingsByChannel.get(cId).calendarAddress = address;
        settingsByChannel.get(cId).reloadSettingsMsg();
    }

    public boolean idIsInMap(String cId)
    {
        return settingsByChannel.containsKey(cId);
    }
}
