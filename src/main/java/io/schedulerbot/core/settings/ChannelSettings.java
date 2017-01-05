package io.schedulerbot.core.settings;

import io.schedulerbot.Main;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;

import java.time.ZoneId;

/**
 */
class ChannelSettings
{
    String announceChannel;
    String announceFormat;
    ZoneId timeZone;
    String clockFormat;
    private Message msg;

    ChannelSettings(MessageChannel channel)
    {
        this.announceChannel = Main.getBotSettings().getAnnounceChan();
        this.announceFormat = Main.getBotSettings().getAnnounceFormat();
        this.timeZone = ZoneId.of(Main.getBotSettings().getTimeZone());
        this.clockFormat = Main.getBotSettings().getClockFormat();

        MessageUtilities.sendMsg( this.generateSettingsMsg(), channel, (message) -> this.msg = message);
    }

    ChannelSettings(Message msg)
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

    void reloadSettingsMsg()
    {
        Guild guild = this.msg.getGuild();
        String msg = this.generateSettingsMsg();
        MessageChannel scheduleChan = guild.getTextChannelsByName(
                Main.getBotSettings().getScheduleChan(), false).get(0);
        if( scheduleChan != null )
        {
            MessageUtilities.deleteMsg(this.msg, (x) ->
                    MessageUtilities.sendMsg(msg, scheduleChan, (message) -> this.msg = message)
            );
        }
    }
}
