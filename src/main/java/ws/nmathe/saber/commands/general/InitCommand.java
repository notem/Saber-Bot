package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;

public class InitCommand implements Command
{
    private String invoke = Main.getBotSettingsManager().getCommandPrefix() + "init";

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "``" + invoke + " [<name>]`` will create a new schedule that events may be" +
                "added to via the ``create`` command or synchronized to a google calendar via ``sync``. Every schedule" +
                " is initialized with a schedule channel. Either delete the channel or use the ``delete`` command to " +
                "remove a schedule. The ``<schedule name>`` argument is optional. If omitted, new schedules will be named" +
                " new_schedule.";

        String EXAMPLES = "" +
                "Ex1. ``" + invoke + "``\n" +
                "Ex2. ``" + invoke + " \"Guild Events\"``";

        String USAGE_BRIEF = "``" + invoke + "`` - initialize a new schedule";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        if(!event.getGuild().getMember(Main.getBotJda().getSelfUser())
                .getPermissions().contains(Permission.MANAGE_CHANNEL))
            return "I need the Manage Channels permission to create a new schedule!";

        if(Main.getScheduleManager().isLimitReached(event.getGuild().getId()))
            return "You have reached the limit for schedules! Please remove one schedule channel before trying again.";

        if(args.length > 1)
            return "That's too many arguments! Use ``" + invoke + " [<name>]``";

        if(args.length == 1 && (args[0].length()>100 || args[0].length()<2))
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
