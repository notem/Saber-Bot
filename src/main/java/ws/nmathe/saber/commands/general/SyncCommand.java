package ws.nmathe.saber.commands.general;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.calendar.Calendar;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.google.GoogleAuth;
import ws.nmathe.saber.utils.MessageUtilities;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * Sets a channel to sync to a google calendar address
 */
public class SyncCommand implements Command
{
    @Override
    public String name()
    {
        return "sync";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - sync a schedule to a google calendar";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.GOOGLE);

        String cat1 = "- Usage\n" + head + " <channel> [import|export] [<calendar address>]";
        String cont1 = "The sync command will replace all events in the specified channel" +
                "with events imported from a public google calendar.\n" +
                "The command imports the next 7 days of events into the channel;" +
                " the channel will then automatically re-sync once every day." +
                "\n\n" +
                "If ``<calendar address>`` is not included, " +
                "the address saved in the schedule's settings will be used." +
                "\n\n" +
                "For more information concerning Google Calendar setup, reference the " +
                "online docs at https://nmathe.ws/bots/saber";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head+" #new_schedule g.rit.edu_g4elai703tm3p4iimp10g8heig@group.calendar.google.com");
        info.addUsageExample(head+" #calendar import g.rit.edu_g4elai703tm3p4iimp10g8heig@group.calendar.google.com");
        info.addUsageExample(head+" #calendar export 0a0jbiclczoiaai@group.calendar.google.com");
        info.addUsageExample(head+" #new_schedule");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, MessageReceivedEvent event)
    {
        String head = prefix + this.name();
        int index = 0;

        // check arg length
        if(args.length < 1)
        {
            return "That's not enough arguments!\n" +
                    "Use ``" + head + " <channel> [<calendar address>]``";
        }
        if(args.length > 3)
        {
            return "That's too many arguments!\n" +
                    "Use ``" + head + " <channel> [<import|export> <calendar_address>]``";
        }

        // validate the supplied channel
        String cId = args[0].replaceAll("[^\\d]","");
        if(!Main.getScheduleManager().isSchedule(cId))
        {
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild.";
        }
        if(Main.getScheduleManager().isLocked(cId))
        {
            return "Schedule is locked while sorting or syncing. Please try again after I finish.";
        }

        // get user Google credentials (if they exist)
        Credential credential = GoogleAuth.getCredential(event.getAuthor().getId());
        if(credential == null) return "I failed to connect to Google API Services!";
        Calendar service = GoogleAuth.getCalendarService(credential);

        // validate the calendar address
        String address;

        if(args.length == 2)
        {
            index++;
            address = args[index];
        }
        else if(args.length == 3)
        {
            index++;
            switch(args[index].toLowerCase())
            {
                case "import":
                case "export":
                    break;

                default:
                    return "*"+args[index]+"* is invalid! Please use either *import* or *export*!";
            }
            index++;
            address = args[index];
        }
        else
        {
            address = Main.getScheduleManager().getAddress(cId);
            if(address.isEmpty())
            {
                return "Your channel, " + args[index] + ", is not setup with a Google Calendar address to sync with!";
            }
        }
        if(!Main.getCalendarConverter().checkValidAddress(address, service))
        {
            return "Calendar address **" + address + "** is not valid!";
        }
        return "";
    }

    @Override
    public void action(String head, String[] args, MessageReceivedEvent event)
    {
        // get user Google credentials (if they exist)
        Credential credential = GoogleAuth.getCredential(event.getAuthor().getId());
        Calendar service = GoogleAuth.getCalendarService(credential);

        int index = 0;
        String cId = args[index].replaceAll("[^\\d]","");
        TextChannel channel = event.getGuild().getTextChannelById(cId);

        index++;

        boolean importFlag = true;
        String address;
        if( args.length == 1 )
        {
            address = Main.getScheduleManager().getAddress(cId);
        }
        else
        {
            if(args[index].equalsIgnoreCase("export"))
            {
                importFlag = false;
                index++;
            }
            else if(args[index].equalsIgnoreCase("import"))
            {
                index++;
            }

            address = args[index];
            if(importFlag)
            {
                // enable auto-sync'ing timezone
                Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("timezone_sync", true));

                // set user who has authorized the sync
                if(GoogleAuth.authorize(event.getAuthor().getId()) != null)
                    Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("sync_user", event.getAuthor().getId()));
                else
                    Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("sync_user", null));
            }
        }

        if(importFlag)
        {
            Main.getCalendarConverter().importCalendar(address, channel, service);
            Main.getScheduleManager().setAddress(cId,address);

            String content = "I have finished syncing <#" + cId + ">!";
            MessageUtilities.sendMsg(content, event.getChannel(), null);
        }
        else
        {
            boolean success = Main.getCalendarConverter().exportCalendar(address, channel, service);
            String content;
            if(success)
            {
                content = "I have finished exporting <#" + cId + ">!";
            } else
            {
                content = "I was unable to export <#" + cId + "> to " + address + "!\n" +
                        "Please make sure I am authorized to edit that calendar!\n" +
                        "You can provide me access through the ``oauth`` command.";
            }
            MessageUtilities.sendMsg(content, event.getChannel(), null);
        }
    }
}
