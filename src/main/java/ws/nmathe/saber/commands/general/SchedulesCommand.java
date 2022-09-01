package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
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
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.USER);

        String cat1 = "- Usage\n" + head + "";
        String cont1 = "This command will generate a list of all schedules active for the guild.\n" +
                "Each schedule is listed with a short summary.\n" +
                "This command is non-destructive, and can be safely used by non-administrator users.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head);

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
    {
        return "";
    }

    @Override
    public void action(String prefix, String[] args, EventCompat event)
    {
        Guild guild = event.getGuild();
        List<String> scheduleIds = Main.getScheduleManager().getSchedulesForGuild(guild.getId());

        // build output main body
        StringBuilder content = new StringBuilder();
        for(String sId : scheduleIds)
        {
            content.append("<#")
                    .append(sId)
                    .append("> - has ")
                    .append(Main.getEntryManager().getEntriesFromChannel(sId).size())
                    .append(" events\n");
        }

        String title = "Schedules on " + guild.getName();           // title for embed
        String footer = scheduleIds.size() + " schedule(s)";   // footer for embed

        // build embed
        MessageEmbed embed = new EmbedBuilder()
                                .setDescription(content.toString())
                                .setTitle(title)
                                .setFooter(footer, null).build();

        MessageCreateData message = new MessageCreateBuilder().setEmbeds(embed).build();           // build message
        MessageUtilities.sendMsg(message, event.getChannel(), null);    // send message
    }
}
