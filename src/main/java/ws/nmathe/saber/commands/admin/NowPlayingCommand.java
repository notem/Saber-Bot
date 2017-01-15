package ws.nmathe.saber.commands.admin;

import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;

/**
 */
public class NowPlayingCommand implements Command
{
    @Override
    public String help(boolean brief)
    {
        return null;
    }

    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        return "";
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        String str = "";
        for( int i = 0; i<args.length-1 ;i++ )
        {
            str += args[i] + " ";
        }
        str += args[args.length-1];

        final String finalStr = str;
        Main.getBotJda().getPresence().setGame(new Game()
        {
            @Override
            public String getName()
            {
                return finalStr;
            }

            @Override
            public String getUrl()
            {
                return "";
            }

            @Override
            public GameType getType()
            {
                return GameType.DEFAULT;
            }
        });
    }
}
