package io.schedulerbot.core;

import io.schedulerbot.Main;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;

import java.time.ZoneId;

/**
 */
public class GuildSettings
{
    public String announceChannel;
    public String announceFormat;
    public ZoneId timeZone;
    public String clockFormat;
    private Message msg;
    private Guild guild;

    public GuildSettings( Guild guild )
    {
        this.announceChannel = Main.getSettings().getAnnounceChan();
        this.announceFormat = Main.getSettings().getAnnounceFormat();
        this.timeZone = ZoneId.of(Main.getSettings().getTimeZone());
        this.clockFormat = Main.getSettings().getClockFormat();

        this.guild = guild;
    }

    public GuildSettings( Message msg )
    {
        String trimmed = msg.getRawContent().replace("```java\n","").replace("\n```","");
        String[] options = trimmed.split(" \\| ");

        for( String option : options )
        {
            String[] splt = option.split(":");
            switch( splt[0] )
            {
                case "msg":
                    this.announceFormat = splt[1].replaceAll("\"","");
                    break;
                case "chan":
                    this.announceChannel = splt[1].replaceAll("\"","");
                    break;
                case "zone":
                    this.timeZone = ZoneId.of(splt[1].replaceAll("\"",""));
                    break;
                case "clock":
                    this.clockFormat = splt[1].replaceAll("\"","");
                    break;
            }
        }

        this.msg = msg;
        this.guild = this.msg.getGuild();
    }

    private String generateSettingsMsg()
    {
        String msg = "```java\n";
        msg += "Settings " +
                " | msg:\"" + this.announceFormat + "\"" +
                " | chan:\"" + this.announceChannel + "\"" +
                " | zone:\"" + this.timeZone + "\"" +
                " | clock:\"" + this.clockFormat + "\"";
        msg += "\n```";
        return msg;
    }

    public void reloadSettingsMsg()
    {
        String msg = this.generateSettingsMsg();
        MessageChannel scheduleChan = this.guild
                .getTextChannelsByName(Main.getSettings().getScheduleChan(), false).get(0);
        if( scheduleChan == null )
        {
            return;
        }
        if( this.msg == null )
        {
            MessageUtilities.sendMsg( msg, scheduleChan, (message) -> this.msg = message);
            return;
        }
        else
        {
            MessageUtilities.deleteMsg(this.msg, (x) ->
                    MessageUtilities.sendMsg(msg, scheduleChan, (message) -> this.msg = message)
            );
            return;
        }
    }

}
