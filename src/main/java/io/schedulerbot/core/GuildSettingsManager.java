package io.schedulerbot.core;

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

}
