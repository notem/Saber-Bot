package io.schedulerbot.utils;

import io.schedulerbot.Main;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;

import java.time.LocalTime;
import java.util.ArrayList;

/**
 * file: EventEntryParser.java
 *
 * EventEntryParser parses a MessageReceivedEvent sent by the bot in response to 'create' commands into
 * a EventEntry worker thread.  A EventEntry thread lives for the length of two sleep calls.
 * The first timer measures the time until the event starts (causes an announce when reached).
 * The second timer measures the time from the event start until the event ends (causes another announce).
 * The bot clears the event from the schedule at the end of the thread's life.
 */
public class EventEntryParser
{
    /**
     * Parses a the content of a EventEntry MessageReceivedEvent into a EventEntry worker thread.
     *
     * @param raw raw content of the message
     * @param event the event object
     * @return EventEntry worker thread
     */
    public EventEntry parse(String raw, MessageReceivedEvent event)
    {
        String eTitle;
        String eStart;
        String eEnd;
        ArrayList<String> eComments = new ArrayList<String>();
        Integer eID;

        // split into lines
        String[] lines = raw.split("\n");


        // the first line is the title \\
        String firstLine = lines[1].replaceFirst("# ", "");
        eTitle = firstLine;


        // the second line is the date and time \\
        String[] secondLine = lines[2].replace("< ","").split(" > ");
        String[] date_time_repeat = secondLine[0].split(", ");

        String date = date_time_repeat[0];
        String[] start_end_timezone = date_time_repeat[1].split(" - ");
        String repeat = "";
        if( date_time_repeat.length == 3 )
            repeat = date_time_repeat[2].replaceFirst("repeats ","");

        eStart = start_end_timezone[0];
        eEnd = start_end_timezone[1].split(" ")[0];
        String timezone = start_end_timezone[1].split(" ")[1];


        // the third line is empty space,     \\

        // lines four through n-2 are comments \\
        for(int c = 4; c < lines.length-2; c++ )
            eComments.add(lines[c]);

        // line n-1 is an empty space \\

        // the last line contains the ID and minutes til timer \\
        eID = Integer.decode(
                "0x" + lines[lines.length-2].replace("[ID: ","").split("]")[0]
        ); // can through away the minutes til timer


        return new EventEntry( eTitle, eStart, eEnd, eComments, eID, event );
    }


    /**
     *
     * @param eTitle
     * @param eStart
     * @param eEnd
     * @param eComments
     * @return
     */
    public static String generate(String eTitle, String eStart, String eEnd, ArrayList<String> eComments)
    {
        // the 'actual' first line (and last line) define format
        String msg = "```Markdown\n";

        Integer eID = Main.newID(); // generate an ID for the entry
        String firstLine = "# " + eTitle + "\n";

        String secondLine = "< " + "Today" + ", " + eStart + " - " + eEnd + " EST >\n";

        msg += firstLine + secondLine;

        // create an empty 'gap' line if there exists comments
        if( !eComments.isEmpty() )
            msg += "\n";

        // insert each comment line into the message
        for( String comment : eComments )
            msg += comment + "\n";

        // add the final ID and time til line
        msg += "\n[ID: " + Integer.toHexString(eID) + "](begins in " + "xxxx" + " minutes)\n";
        // cap the code block
        msg += "```";

        return msg;
    }


    /**
     * the EventEntry worker thread subclass
     */
    public class EventEntry implements Runnable {

        public String eTitle;
        public String eStart;
        public String eEnd;
        public ArrayList<String> eComments;
        public Integer eID;
        public MessageReceivedEvent msgEvent;

        public Thread thread;

        /**
         * Thread constructor
         * @param eName name of the event (String)
         * @param eStart time of day the event starts (Integer)
         * @param eEnd time of day the event ends (Integer)
         * @param eComments the descriptive text of the event (String)
         * @param eID the ID of the event (Integer)
         * @param msgEvent the discord message object (MessageReceivedEvent)
         */
        public EventEntry(String eName, String eStart, String eEnd, ArrayList<String> eComments, Integer eID, MessageReceivedEvent msgEvent)
        {
            this.eTitle = eName;
            this.eStart = eStart;
            this.eEnd = eEnd;
            this.eComments = eComments;
            this.eID = eID;
            this.msgEvent = msgEvent;

            this.thread = new Thread( this,  eName );
            this.thread.start();
        }

        @Override
        public void run()
        {
            // create the announcement message strings
            Guild guild = this.msgEvent.getGuild();
            String startMsg = "@everyone The event **" + this.eTitle + "** has begun!";
            String endMsg = "@everyone The event **" + this.eTitle + "** has ended.";
            //String cancelMsg = "@everyone The event **" + this.eTitle + "** has been cancelled.";

            // convert the times into integers
            Integer startH = Integer.parseInt(this.eStart.split(":")[0]);
            Integer startM = Integer.parseInt(this.eStart.split(":")[1]);
            Integer start = startH*60*60 + startM*60;

            Integer endH = Integer.parseInt(this.eEnd.split(":")[0]);
            Integer endM = Integer.parseInt(this.eEnd.split(":")[1]);
            Integer end = endH*60*60 + endM*60;

            // using the local time, determine the wait interval (in seconds)
            LocalTime now = LocalTime.now();
            Integer nowInSeconds = (now.getHour()*60*60 + now.getMinute()*60 + now.getSecond());
            Integer wait1 = start - nowInSeconds;
            Integer wait2 = end - start;

            if( wait1 < 0 )
                wait1 += 24*60*60;
            if( wait2 < 0 )
                wait2 += 24*60*60;

            // run the main operation of the thread
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
            }

            // if an interrupt is received, quit operation
            catch(InterruptedException e)
            {
                return;
            }

            // always remove the entry from schedule and delete the message
            finally
            {
                Main.schedule.remove(this.eID);
                try
                {
                    this.msgEvent.getMessage().deleteMessage().queue();
                }
                catch( PermissionException e )
                {
                    Main.handleException( e, this.msgEvent );
                }
            }
        }
    }
}
