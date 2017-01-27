package ws.nmathe.saber.commands.general;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.EmbedParser;
import ws.nmathe.saber.core.schedule.ScheduleEntry;

public class AsEmbedCommand implements Command
{
    private String prefix = Main.getBotSettings().getCommandPrefix();

    @Override
    public String help(boolean brief)
    {
        return "``" + prefix + "embed_test <id>`` - Preview the new event format (release time undetermined)";
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        try {
            int id = Integer.decode("0x" + args[0]);

            ScheduleEntry se = Main.getScheduleManager().getEntry(id);
            if( se==null)
                return;

            EmbedBuilder builder = EmbedParser.generate(se.getTitle(), se.getStart(), se.getEnd(), se.getComments(),
                    se.getRepeat(), se.getId(), se.getMessage().getChannel().getId());

            event.getChannel().sendMessage(builder.build()).queue();
        }
        catch(Exception ignored)
        {}
    }
}
