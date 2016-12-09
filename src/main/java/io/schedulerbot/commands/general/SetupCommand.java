package io.schedulerbot.commands.general;

import io.schedulerbot.Main;
import io.schedulerbot.commands.Command;
import io.schedulerbot.utils.MessageUtilities;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 */
public class SetupCommand implements Command
{
    private static String prefix = Main.getSettings().getCommandPrefix();
    private static String controlChan = Main.getSettings().getControlChan();
    private static String announceChan = Main.getSettings().getAnnounceChan();

    private static final String BOTOATH_LINK = "https://discordapp.com/api/oauth2/authorize?client_id=" +
            Main.getBotSelfUser().getId() + "&scope=bot&permissions=0\n";

    private static final String USAGE_EXTENDED = "\nYou can invite Saber to your discord server with " +
            "this link: " + BOTOATH_LINK;

    private static final String USAGE_BRIEF = "**" + prefix + "setup** - the steps to getting " +
            "Saber working on your server.";


    @Override
    public String help(boolean brief)
    {
        if (brief)
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n" + USAGE_EXTENDED;
    }

    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        // I don't care if you put invalid args
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        String msg = "If you've not already, Saber will need to be added to your server. To do so, follow the " +
                "link at the bottom. Before Saber can be effectively used on your server a couple channels need" +
                " to be configured.\n\n(1) Create a channel named **" + controlChan + "**." +
                " You need to then give Saber the follow permissions: **Read Messages**, **Send " +
                "Messages**, **Manage Messages**, and **Read Message History**. This is the channel from " +
                "which Saber will listen to commands with the '" + prefix + "' prefix. The full " +
                "list of commands Saber will accept can be found with the " + "!" + "help command.\n\n(2) Create a " +
                "channel named **" + controlChan + "**. Again, Saber will need the **Read " +
                "Messages**, **Send Messages**, **Manage Messages**, and **Read Message History**. " +
                "It is also highly recommended that you disable the **Send Messages** permission from " +
                "@everyone. Saber will use this channel to store the event schedule entries.\n\n(Optional) Create a channel named **" +
                announceChan + "** and give Saber at minimum the **Send Messages** permission." +
                " Saber will by default post event announcements to this channel. If you would rather" +
                " Saber instead send announcements to your discord server's default channel, simply give " +
                "Saber her permissions on your default channel and do not create an " + announceChan +
                ".\n\nAnd that's it. ~~If you'd rather Saber setup up the channels her self, give Saber admin " +
                "permissions on your server and use the command **" + prefix + "setup auto** in your " +
                "default public channel.~~ <- not yet implemented\n\n";

        MessageUtilities.sendPrivateMsg(msg + BOTOATH_LINK, event.getAuthor(), null);
    }
}

