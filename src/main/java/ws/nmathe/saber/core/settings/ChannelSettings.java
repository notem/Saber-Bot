package ws.nmathe.saber.core.settings;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.__out;
import net.dv8tion.jda.core.entities.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
    String calendarAddress = "off";                                     //

    ZonedDateTime nextSync = null;

    private String msgId;
    private String chaId;

    ChannelSettings(MessageChannel channel)
    {
        // use defaults
        MessageEmbed embed = new EmbedBuilder().setDescription(this.generateSettingsMsg()).build();
        this.msgId = MessageUtilities.sendMsg(new MessageBuilder().setEmbed(embed).build(), channel).getId();
        this.chaId = channel.getId();
    }

    ChannelSettings(Message msg)
    {
        String raw;
        if(msg.getEmbeds().isEmpty())
            raw = msg.getRawContent();
        else
            raw = msg.getEmbeds().get(0).getDescription();

        if( !raw.contains("```java\n") )
            return;

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
                case "sync":
                    this.calendarAddress = splt[1].replaceAll("\"","");
            }
        }

        this.msgId = msg.getId();
        this.chaId = msg.getChannel().getId();
    }

    private String generateSettingsMsg()
    {
        String msg = "```java\n";
        msg += "Settings " +
                " | zone:\"" + this.timeZone + "\"" +
                " | clock:\"" + this.clockFormat + "\"" +
                " | style:\"" + this.messageStyle + "\"" +
                " | msg:\"" + this.announceFormat + "\"" +
                " | chan:\"" + this.announceChannel + "\"" +
                " | sync:\"" + this.calendarAddress + "\"";
        msg += "\n```";
        return msg;
    }

    void reloadSettingsMsg()
    {
        TextChannel chan = Main.getBotJda().getTextChannelById(chaId);
        try
        {
            Message msg = chan.getMessageById(this.msgId).block();
            MessageUtilities.deleteMsg( msg );
        }
        catch( Exception ignored )
        {}

        if( this.messageStyle.equals("plain") )
        {
            MessageUtilities.sendMsg(this.generateSettingsMsg(), chan, (message) -> this.msgId = message.getId());
        }
        else if( this.messageStyle.equals("embed") )
        {
            MessageEmbed embed = new EmbedBuilder().setDescription(this.generateSettingsMsg()).build();
            MessageUtilities.sendMsg( new MessageBuilder().setEmbed(embed).build(),chan, (message)-> this.msgId = message.getId());
        }
    }
}
