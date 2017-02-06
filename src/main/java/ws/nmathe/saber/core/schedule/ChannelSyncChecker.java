package ws.nmathe.saber.core.schedule;

import net.dv8tion.jda.core.entities.TextChannel;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.settings.ChannelSettingsManager;

import java.util.Collection;

public class ChannelSyncChecker implements Runnable {

    private Collection<String> cIds;

    ChannelSyncChecker(Collection<String> cIds)
    {
        this.cIds = cIds;
    }

    @Override
    public void run()
    {
        ChannelSettingsManager chanSetManager = Main.getChannelSettingsManager();
        for( String cId : cIds )
        {
            if( chanSetManager.checkSync(cId) )
            {
                try
                {
                    TextChannel channel = Main.getBotJda().getTextChannelById(cId);
                    Main.getCalendarConverter().syncCalendar(
                            chanSetManager.getAddress(cId),
                            channel );

                    chanSetManager.sendSettingsMsg(channel);
                    chanSetManager.adjustSync(cId);
                }
                catch( Exception e )
                {
                    e.printStackTrace();
                }
            }
        }

    }
}
