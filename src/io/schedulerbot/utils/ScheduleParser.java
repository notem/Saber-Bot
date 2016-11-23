package io.schedulerbot.utils;

import io.schedulerbot.Main;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;

import java.time.LocalTime;

/**
 * file: ScheduleParser.java
 *
 * ScheduleParser parses a MessageReceivedEvent sent by the bot in response to 'create' commands into
 * a ScheduledEvent worker thread.  A ScheduledEvent thread lives for the length of two sleep calls.
 * The first timer measures the time until the event starts (causes an announce when reached).
 * The second timer measures the time from the event start until the event ends (causes another announce).
 * The bot clears the event from the schedule at the end of the thread's life.
 */
public class ScheduleParser
{
    /**
     * Parses a the content of a ScheduledEvent MessageReceivedEvent into a
     * ScheduledEvent worker thread.
     * @param raw raw content of the message
     * @param event the event object
     * @return ScheduledEvent worker thread
     */
    public ScheduledEvent parse(String raw, MessageReceivedEvent event)
    {
        String eventName;
        Integer eventStart;
        Integer eventEnd;
        String eventDesc;

        String[] lines = raw.split("\n");
        eventName = lines[0];
        eventDesc = lines[2];

        String[] tokens = lines[1].split(" ");

        String start = tokens[1];
        String end = tokens[3];
        start = start.replace(",","");

        Integer startH = Integer.parseInt(start.split(":")[0]);
        Integer startM = Integer.parseInt(start.split(":")[1]);
        eventStart = startH*60*60 + startM*60;

        Integer endH = Integer.parseInt(end.split(":")[0]);
        Integer endM = Integer.parseInt(end.split(":")[1]);
        eventEnd = endH*60*60 + endM*60;

        return new ScheduledEvent( eventName, eventStart, eventEnd, eventDesc, 0, event );
    }

    /**
     * the ScheduledEvent worker thread subclass
     */
    public class ScheduledEvent implements Runnable {

        public String eventName;
        public Integer eventStart;
        public Integer eventEnd;
        public String eventDescription;
        public Integer eventID;
        public MessageReceivedEvent event;

        Thread runner;

        /**
         * Thread constructor
         * @param eName name of the event (String)
         * @param eStart time of day the event starts (Integer)
         * @param eEnd time of day the event ends (Integer)
         * @param eDesc the descriptive text of the event (String)
         * @param eID the ID of the event (Integer)
         * @param event the discord message object (MessageReceivedEvent)
         */
        public ScheduledEvent(String eName, Integer eStart, Integer eEnd, String eDesc, Integer eID, MessageReceivedEvent event)
        {
            this.eventName = eName;
            this.eventStart = eStart;
            this.eventEnd = eEnd;
            this.eventDescription = eDesc;
            this.eventID = eID;
            this.event = event;

            runner = new Thread( this,  eName );
            runner.start();
        }

        @Override
        public void run()
        {
            Guild guild = this.event.getGuild();
            String startMsg = "@everyone The event \"" + this.eventName + "\" has begun!";
            String endMsg = "@everyone The event \"" + this.eventName + "\" has ended.";

            LocalTime now = LocalTime.now();
            Integer nowInSeconds = (now.getHour()*60*60 + now.getMinute()*60 + now.getSecond());
            Integer wait1 = this.eventStart - nowInSeconds;
            Integer wait2 = this.eventEnd - this.eventStart;

            if( wait1 < 0 )
                wait1 += 24*60*60;
            if( wait2 < 0 )
                wait2 += 24*60*60;

            try
            {
                Thread.sleep(wait1*1000);        // sleep until the event starts

                // announce that the event is beginning
                if(BotConfig.ANNOUNCE_CHAN.isEmpty() ||
                        guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).isEmpty())
                    guild.getPublicChannel().sendMessage( startMsg ).queue();
                else
                    guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0)
                            .sendMessage( startMsg ).queue();

                Thread.sleep(wait2*1000);    // sleep until the event ends

                // announce that the event is ending
                if(BotConfig.ANNOUNCE_CHAN.isEmpty() ||
                        guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).isEmpty())
                    guild.getPublicChannel().sendMessage( endMsg ).queue();
                else
                    guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0)
                            .sendMessage( endMsg ).queue();

                // attempt to delete the old ScheduledEvent message.
                Main.schedule.remove(this.eventID);
                try
                {
                    this.event.getMessage().deleteMessage().queue();
                }
                catch( PermissionException e )
                {
                    Main.handleException( e );
                }
            }
            catch(InterruptedException e)
            {
                // find current time in seconds
                now = LocalTime.now();
                nowInSeconds = this.eventStart - (now.getHour()*60*60 + now.getMinute()*60 + now.getSecond());
                // if the current time is after the event started
                if(nowInSeconds > this.eventStart)
                {
                    // announce that the event is ending
                    if(BotConfig.ANNOUNCE_CHAN.isEmpty() ||
                            guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).isEmpty())
                        guild.getPublicChannel().sendMessage( endMsg ).queue();
                    else
                        guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0)
                                .sendMessage( endMsg ).queue();
                }

                // destroy the ScheduledEvent
                Main.schedule.remove(this.eventID);
                try
                {
                    this.event.getMessage().deleteMessage();
                }
                catch( PermissionException ee )
                {
                    Main.handleException( e );
                }
            }
        }
    }
}
