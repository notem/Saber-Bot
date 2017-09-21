package ws.nmathe.saber.commands.general;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.calendar.Calendar;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.google.GoogleAuth;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;

import java.io.IOException;

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
    public String help(String prefix, boolean brief)
    {
        String head = prefix + this.name();

        String USAGE_EXTENDED = "```diff\n- Usage\n" + head + " <channel> [import|export] [<calendar address>]```\n" +
                "The sync command will replace all events in the specified channel" +
                "with events imported from a public google calendar.\n" +
                "The command imports the next 7 days of events into the channel;" +
                " the channel will then automatically re-sync once every day.\n\n" +
                "If ``<calendar address>`` is not included, " +
                "the address saved in the channel settings will be used.\n\n" +
                "For more information concerning Google Calendar setup, reference the " +
                "online docs at https://nmathe.ws/bots/saber";

        String USAGE_BRIEF = "``" + head + "`` - sync a schedule to a google calendar";

        String EXAMPLES = "```diff\n- Examples```\n" +
                "``" + head + " #new_schedule g.rit.edu_g4elai703tm3p4iimp10g8heig@group.calendar.google.com``" +
                "\n``" + head + " #calendar import g.rit.edu_g4elai703tm3p4iimp10g8heig@group.calendar.google.com``" +
                "\n``" + head + " #calendar export 0a0jbiclczoiaai@group.calendar.google.com``" +
                "\n``" + head + " #new_schedule``";

        if( brief ) return USAGE_BRIEF;
        else return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
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
        String cId = args[0].replace("<#","").replace(">","");
        if(!Main.getScheduleManager().isASchedule(cId))
        {
            return "Channel " + args[index] + " is not on my list of schedule channels for your guild.";
        }
        if(Main.getScheduleManager().isLocked(cId))
        {
            return "Schedule is locked while sorting or syncing. Please try again after I finish.";
        }

        // get user Google credentials (if they exist)
        Credential credential;
        Calendar service;
        try
        {
            credential = GoogleAuth.authorize(event.getAuthor().getId());
            if(credential == null)
            {
                credential = GoogleAuth.authorize();
            }
            service = GoogleAuth.getCalendarService(credential);
        }
        catch (IOException e)
        {
            return "I failed to connect to Google API Services!";
        }

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
        try
        {
            // get user Google credentials (if they exist)
            Credential credential;
            Calendar service;
            try
            {
                credential = GoogleAuth.authorize(event.getAuthor().getId());
                if(credential == null)
                {
                    credential = GoogleAuth.authorize();
                }
                service = GoogleAuth.getCalendarService(credential);
            }
            catch (IOException e)
            {
                return;
            }

            int index = 0;
            String cId = args[index].replace("<#","").replace(">","");
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
                    Main.getDBDriver().getScheduleCollection().updateOne(eq("_id", cId), set("sync_user", event.getAuthor().getId()));
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
                Main.getCalendarConverter().exportCalendar(address, channel, service);
                String content = "I have finished exporting <#" + cId + ">!";
                MessageUtilities.sendMsg(content, event.getChannel(), null);
            }
        }
        catch(Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }
}
