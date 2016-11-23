package io.schedulerbot.commands;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 */
public class DestroyCommand implements Command
{
    @Override
    public boolean verify(String[] args, MessageReceivedEvent event) {
        return false;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event) {

    }
}
