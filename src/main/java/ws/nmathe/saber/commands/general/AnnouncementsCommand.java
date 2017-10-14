package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.schedule.ScheduleEntry;
import ws.nmathe.saber.utils.MessageUtilities;
import ws.nmathe.saber.utils.ParsingUtilities;
import ws.nmathe.saber.utils.VerifyUtilities;

/**
 * used to test an event's announcement format
 */
public class AnnouncementsCommand implements Command
{
    @Override
    public String name()
    {
        return "announcements";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - list and configure event announcements";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.MISC);

        String cat1 = "- Usage\n" + head + " <ID> [<add|remove> <args>]";
        String cont1 = "The ``announcements`` command will list all currently scheduled announcements for the event.\n" +
                        "Announcements are divided into *Schedule* and *Event* announcements." +
                        "\n\n" +
                        "Schedule announcements can be modified using the ``config`` command.\n" +
                        "Event announcements can be modified using either this command or the ``edit`` command." +
                        "\n\n" +
                        "Reference the command examples to see how to add and remove event-specific announcements.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head + " J09DlA");
        info.addUsageExample(head + " J09DlA add #general start-1h \"Get ready! **%t** begins in one hour!\"");
        info.addUsageExample(head + " J09DlA remove 1");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();
        int index = 0;

        // length checks
        if(args.length < 1)
        {
            return "That's not enough arguments!\n" +
                    "Use ``" + head + " <ID> [<add|remove> <args>]``";
        }

        // check for a valid entry ID
        if(!VerifyUtilities.verifyEntryID(args[index]))
        {
            return "``" + args[index] + "`` is not a valid entry ID!";
        }

        // check to see if event with the provided ID exists for the guild
        Integer Id = ParsingUtilities.encodeIDToInt(args[index]);
        ScheduleEntry entry = Main.getEntryManager().getEntryFromGuild( Id, event.getGuild().getId() );
        if(entry == null)
        {
            return "I could not find an entry with that ID!";
        }

        index++; // next argument is optional

        if(args.length > 2)
        {
            String verify = VerifyUtilities.verifyAnnouncementTime(args, index, head, event);
            if(!verify.isEmpty()) return verify;
        }

        return ""; // return valid
    }

    @Override
    public void action(String prefix, String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        // get entry object
        Integer entryId = ParsingUtilities.encodeIDToInt(args[index]);
        ScheduleEntry entry = Main.getEntryManager().getEntry( entryId );

        // verify the entry's message exists
        Message msg = entry.getMessageObject();
        if(msg == null) return;

        index++;

        if(args.length > 2)
        {
            // if additional args have been provided. . .
            switch(args[index++])
            {
                case "a":
                case "add":
                    String target = args[index].replaceAll("[^\\d]","");
                    String time = args[index+1];
                    String message = args[index+2];
                    entry.addAnnouncementOverride(target, time, message);
                    break;

                case "r":
                case "remove":
                    Integer id = Integer.parseInt(args[index].replaceAll("[^\\d]",""))-1;
                    entry.removeAnnouncementOverride(id);
                    break;
            }
            Main.getEntryManager().updateEntry(entry, false);
        }

        /*
         * generate output message
         */

        String content = "```js\n// Schedule Announcements\n";
        if(!entry.isQuietStart())
        {
            String format = Main.getScheduleManager().getStartAnnounceFormat(entry.getChannelId());
            String target = Main.getScheduleManager().getStartAnnounceChan(entry.getChannelId());
            if(target.matches("\\d+"))
            {
                JDA jda = Main.getShardManager().getJDA(entry.getGuildId());
                try
                { target = jda.getTextChannelById(target).getName(); }
                catch(Exception ignored)
                {}
            }
            content += "\"" + format + "\" at \"START\" on \"#" + target + "\"\n";
        }
        if(!entry.isQuietEnd())
        {
            String format = Main.getScheduleManager().getEndAnnounceFormat(entry.getChannelId());
            String target = Main.getScheduleManager().getEndAnnounceChan(entry.getChannelId());
            if(target.matches("\\d+"))
            {
                JDA jda = Main.getShardManager().getJDA(entry.getGuildId());
                try
                { target = jda.getTextChannelById(target).getName(); }
                catch(Exception ignored)
                {}
            }
            content += "\"" + format + "\" at \"END\" on \"#" + target + "\"\n";
        }
        if(!entry.isQuietRemind())
        {
            String format = Main.getScheduleManager().getReminderFormat(entry.getChannelId());
            String target = Main.getScheduleManager().getReminderChan(entry.getChannelId());
            if(target.matches("\\d+"))
            {
                JDA jda = Main.getShardManager().getJDA(entry.getGuildId());
                try
                { target = jda.getTextChannelById(target).getName(); }
                catch(Exception ignored)
                {}
            }
            for(Integer reminder : Main.getScheduleManager().getReminders(entry.getChannelId()))
            {
                content += "\"" + format + "\" at \"START" +
                        (reminder>0?"-"+reminder:"+"+Math.abs(reminder)) + "\" on \"#" + target + "\"\n";
            }
            format = Main.getScheduleManager().getReminderFormat(entry.getChannelId());
            for(Integer reminder : Main.getScheduleManager().getEndReminders(entry.getChannelId()))
            {
                content += "\"" + format + "\" at \"END" +
                        (reminder>0?"-"+reminder:"+"+Math.abs(reminder)) + "\" on \"#" + target + "\"\n";
            }
        }
        if(!entry.getAnnouncementTimes().values().isEmpty())
        {
            content += entry.announcementsToString();
        }
        content += "```";
        MessageUtilities.sendMsg(content, event.getChannel(), null);
    }
}
