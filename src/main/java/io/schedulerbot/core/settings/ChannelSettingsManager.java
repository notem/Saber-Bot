package io.schedulerbot.core.settings;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;

import java.time.ZoneId;
import java.util.HashMap;

/**
 */
public class ChannelSettingsManager
{
    private HashMap<String, ChannelSettings> settingsByChannel;

    public ChannelSettingsManager()
    {
        settingsByChannel = new HashMap<>();
    }

    public void checkChannel(MessageChannel channel)
    {
        if( settingsByChannel.get(channel.getId()) == null )
        {
            ChannelSettings settings = new ChannelSettings( channel );
            settingsByChannel.put(channel.getId(), settings );
            settings.reloadSettingsMsg();
        }
    }

    public void sendSettingsMsg( MessageChannel channel )
    {
        settingsByChannel.get( channel.getId() ).reloadSettingsMsg();
    }

    public void loadSettings( Message message )
    {
        settingsByChannel.put( message.getGuild().getId(), new ChannelSettings( message ) );
    }

    public String getAnnounceChan(String gId )
    {
        return settingsByChannel.get(gId).announceChannel;
    }

    public String getAnnounceFormat(String gId )
    {
        return settingsByChannel.get(gId).announceFormat;
    }

    public String getClockFormat(String gId )
    {
        return settingsByChannel.get(gId).clockFormat;
    }

    public ZoneId getTimeZone(String gId )
    {
        return settingsByChannel.get(gId).timeZone;
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
}
