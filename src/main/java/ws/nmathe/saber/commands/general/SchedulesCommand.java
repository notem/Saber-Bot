package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * used for generating the list of valid timezone strings
 */
public class SchedulesCommand implements Command
{
    @Override
    public String name()
    {
        return "schedules";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - lists all schedules";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.MISC);

        String cat1 = "- Usage\n" + head + "";
        String cont1 = "This command will generate a list of all schedules active for the guild.\n" +
                "Each schedule is listed with a short summary.\n" +
                "This command is non-destructive, and can be safely used by non-administrator users.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head);

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        Guild guild = event.getGuild();
        List<String> scheduleIds = Main.getScheduleManager().getSchedulesForGuild(guild.getId());

        // build output main body
        String content = "";
        for(String sId : scheduleIds)
        {
            content += "<#" + sId + "> - has " + Main.getEntryManager().getEntriesFromChannel(sId).size() + " events\n";
        }

        String title = "Schedules on " + guild.getName();           // title for embed
        String footer = scheduleIds.size() + " schedule(s)";   // footer for embed

        // build embed
        MessageEmbed embed = new EmbedBuilder()
                                .setDescription(content)
                                .setTitle(title)
                                .setFooter(footer, null).build();

        Message message = new MessageBuilder().setEmbed(embed).build();           // build message
        MessageUtilities.sendMsg(message, event.getTextChannel(), null);    // send message
    }
}
