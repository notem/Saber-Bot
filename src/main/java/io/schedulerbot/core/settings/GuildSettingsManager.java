package io.schedulerbot.core.settings;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;

import java.time.ZoneId;
import java.util.HashMap;

/**
 */
public class GuildSettingsManager
{
    private HashMap<String, GuildSettings > settingsByGuild;

    public GuildSettingsManager()
    {
        settingsByGuild = new HashMap<>();
    }

    public void checkGuild( Guild guild )
    {
        if( settingsByGuild.get(guild.getId()) == null )
        {
            GuildSettings settings = new GuildSettings( guild );
            settingsByGuild.put(guild.getId(), settings );
            settings.reloadSettingsMsg();
        }
    }

    public void sendSettingsMsg( Guild guild )
    {
        settingsByGuild.get( guild.getId() ).reloadSettingsMsg();
    }

    public void loadSettings( Message message )
    {
        settingsByGuild.put( message.getGuild().getId(), new GuildSettings( message ) );
    }

    public String getGuildAnnounceChan( String gId )
    {
        return settingsByGuild.get(gId).announceChannel;
    }

    public String getGuildAnnounceFormat( String gId )
    {
        return settingsByGuild.get(gId).announceFormat;
    }

    public String getGuildClockFormat( String gId )
    {
        return settingsByGuild.get(gId).clockFormat;
    }

    public ZoneId getGuildTimeZone(String gId )
    {
        return settingsByGuild.get(gId).timeZone;
    }

    public void setGuildAnnounceChan( String gId, String chan )
    {
        settingsByGuild.get(gId).announceChannel = chan;
        settingsByGuild.get(gId).reloadSettingsMsg();
    }

    public void setGuildAnnounceFormat( String gId, String format )
    {
        settingsByGuild.get(gId).announceFormat = format;
        settingsByGuild.get(gId).reloadSettingsMsg();
    }

    public void setGuildClockFormat( String gId, String clock )
    {
        settingsByGuild.get(gId).clockFormat = clock;
        settingsByGuild.get(gId).reloadSettingsMsg();
    }

    public void setGuildTimeZone( String gId, ZoneId zone )
    {
        settingsByGuild.get(gId).timeZone = zone;
        settingsByGuild.get(gId).reloadSettingsMsg();
    }
}
