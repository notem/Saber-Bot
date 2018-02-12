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

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * command which summarizes all events currently scheduled
 * for the guild in which the command is called
 */
public class EventsCommand implements Command
{
    @Override
    public String name()
    {
        return "events";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - lists all events for the guild";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.USER);

        String cat1 = "- Usage\n" + head + "";
        String cont1 = "This command will generate a list of all events for the guild.\n" +
                "Each event is listed with a short summary detailing the event's title, ID, and start-time.\n" +
                "The output can be filtered by channel by appending desired schedules to the command.\n" +
                "This command is non-destructive, and can be safely used by non-administrator users.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head);
        info.addUsageExample(head+" #schedule");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        /*
        * this command is non-destructive, so it is allowable that verify never fails
        * this command is intended to be used by anyone on a server, so it is desirable
        * that the command should fail silently so as to not avoid message spam
        */
        return "";
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        // process any optional channel arguments
        List<String> channelIds = new ArrayList<>();
        for (String arg : args)
        {
            channelIds.add(arg.replaceAll("[^\\d]", ""));
        }

        Guild guild = event.getGuild();
        List<String> scheduleIds = Main.getScheduleManager().getSchedulesForGuild(guild.getId());
        if(!channelIds.isEmpty())
        {
            // filter the list of schedules
            scheduleIds = scheduleIds.stream().filter(channelIds::contains).collect(Collectors.toList());
        }

        // build the embed body content
        int count = 0;
        StringBuilder content = new StringBuilder();
        for(String sId : scheduleIds)
        {
            // for each schedule, generate a list of events scheduled
            Collection<ScheduleEntry> entries = Main.getEntryManager().getEntriesFromChannel(sId);
            if(!entries.isEmpty())
            {
                content.append("<#").append(sId).append("> ...\n");  // start a new schedule list
                while(!entries.isEmpty())
                {
                    // find and remove the next earliest occurring event
                    ScheduleEntry top = entries.toArray(new ScheduleEntry[entries.size()])[0];
                    for(ScheduleEntry se : entries)
                    {
                        if(se.getStart().isBefore(top.getStart())) top = se;
                    }
                    entries.remove(top);

                    // determine time until the event begins/ends
                    long timeTil = ZonedDateTime.now().until(top.getStart(), ChronoUnit.MINUTES);
                    String status = "begins";
                    if (timeTil < 0)    // adjust if event is ending
                    {
                        timeTil = ZonedDateTime.now().until(top.getEnd(), ChronoUnit.MINUTES);
                        status = "ends";
                    }

                    // add the event as a single line in the content
                    content.append(":id:``").append(ParsingUtilities.intToEncodedID(top.getId()))
                            .append("`` ~ **").append(top.getTitle()).append("** ").append(status).append(" in *");
                    if(timeTil < 120)
                        content.append(timeTil).append(" minutes*\n");
                    else if(timeTil < 24*60)
                        content.append(timeTil / 60).append(" hours and ").append(timeTil % 60).append(" minutes*\n");
                    else
                        content.append(timeTil / (60 * 24)).append(" days*\n");
                    count++;     // iterate event counter
                }
                content.append("\n"); // end a schedule list
            }
        }

        String title = "Events on " + guild.getName();          // title for embed
        String footer = count + " event(s)";                    // footer for embed

        // build embed and message
        MessageEmbed embed = new EmbedBuilder()
                                .setFooter(footer, null)
                                .setTitle(title)
                                .setDescription(content.toString()).build();

        Message message = new MessageBuilder().setEmbed(embed).build();            // build message
        MessageUtilities.sendMsg(message, event.getTextChannel(), null);     // send message
    }
}
