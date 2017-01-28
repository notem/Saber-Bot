package ws.nmathe.saber.core.settings;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.__out;
import net.dv8tion.jda.core.entities.*;

import java.time.ZoneId;

/**
 * Object which represents the settings for a schedule channel with it's associated
 * settings Message.
 */
class ChannelSettings
{
    String announceChannel = Main.getBotSettings().getAnnounceChan();   //
    String announceFormat = Main.getBotSettings().getAnnounceFormat();  //
    ZoneId timeZone = ZoneId.of(Main.getBotSettings().getTimeZone());   // defaults
    String clockFormat = Main.getBotSettings().getClockFormat();        //
    String messageStyle = "embed";                                      //
    private Message msg;

    ChannelSettings(MessageChannel channel)
    {
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
        String raw;
        if(msg.getEmbeds().isEmpty())
            raw = msg.getRawContent();
        else
            raw = msg.getEmbeds().get(0).getDescription();

        String trimmed = raw.replace("```java\n", "").replace("\n```", "");
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
                case "style":
                    this.messageStyle = splt[1].replaceAll("\"","");
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
                " | clock:\"" + this.clockFormat + "\"" +
                " | style:\"" + this.messageStyle + "\"";
        msg += "\n```";
        return msg;
    }

    void reloadSettingsMsg()
    {
        TextChannel scheduleChan = this.msg.getTextChannel();
        if( this.messageStyle.equals("plain") )
        {
            MessageUtilities.deleteMsg(this.msg, (x) ->
                    MessageUtilities.sendMsg(this.generateSettingsMsg(), scheduleChan, (message) -> this.msg = message)
            );
        }
        else if( this.messageStyle.equals("embed") )
        {
            MessageUtilities.deleteMsg(this.msg, (x) ->
            {
                MessageEmbed embed = new EmbedBuilder().setDescription(this.generateSettingsMsg()).build();
                MessageUtilities.sendMsg( new MessageBuilder().setEmbed(embed).build(),scheduleChan, (message)-> this.msg = message);
            });
        }
    }
}
