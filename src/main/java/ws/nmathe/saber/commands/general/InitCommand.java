package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;

public class InitCommand implements Command
{
    private String prefix = Main.getBotSettings().getCommandPrefix();

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "``" + prefix + "init [<schedule name>]`` will create a new schedule that events may be" +
                "added to via the ``create`` command or synchronized to a google calendar via ``sync``. Every schedule" +
                " is initialized with a schedule channel. Either delete the channel or use the ``delete`` command to " +
                "remove a schedule. The ``<schedule name>`` argument is optional. If omitted, new schedules will be named" +
                " new_schedule.";

        String EXAMPLES = "" +
                "Ex1. ``" + prefix + "init``\n" +
                "Ex2. ``" + prefix + "init \"Guild Events\"``";

        String USAGE_BRIEF = "``" + prefix + "init`` - initialize a new schedule";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        // TODO check if bot has sufficient permissions on guild

        if(args.length > 1)
            return "That' too many arguments!";

        if(args.length == 1 && args[0].length()>100 && args[0].length()<2)
            return "Schedule name must be between 2 and 100 characters long!";

        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        if(args.length > 0)
        {
            Main.getScheduleManager().createSchedule(event.getGuild().getId(),
                    args[0].replaceAll("[^A-Za-z0-9 ]","").replace(" ","_"));
        }
        else
        {
            Main.getScheduleManager().createSchedule(event.getGuild().getId(), null);
        }
    }
}
