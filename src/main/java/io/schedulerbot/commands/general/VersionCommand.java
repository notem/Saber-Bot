package io.schedulerbot.commands.general;

import io.schedulerbot.commands.Command;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 */
public class VersionCommand implements Command
{
    @Override
    public String help(boolean brief)
    {
        return null;
    }

    @Override
    public boolean verify(String[] args, MessageReceivedEvent event)
    {
        return false;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {

    }
}
