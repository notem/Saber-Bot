package ws.nmathe.saber.core.settings;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.__out;
import net.dv8tion.jda.core.entities.*;

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

        try
        {
            this.msg = channel.sendMessage(this.generateSettingsMsg()).block();
        }
        catch( Exception e)
        {
            __out.printOut( MessageUtilities.class, e.getMessage() );
        }
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
        TextChannel scheduleChan = this.msg.getTextChannel();
        MessageUtilities.deleteMsg(this.msg, (x) ->
                MessageUtilities.sendMsg(this.generateSettingsMsg(), scheduleChan, (message) -> this.msg = message)
        );
    }
}
