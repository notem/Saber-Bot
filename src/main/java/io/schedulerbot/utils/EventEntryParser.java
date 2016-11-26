package io.schedulerbot.utils;

import io.schedulerbot.Main;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * file: EventEntryParser.java
 *
 * EventEntryParser parses a MessageReceivedEvent sent by the bot in response to 'create' commands into
 * a EventEntry worker thread.  A EventEntry thread lives for the length of two sleep calls.
 * The first timer measures the time until the event starts (causes an announce when reached).
 * The second timer measures the time from the event start until the event ends (causes another announce).
 * The bot clears the event from the entriesGlobal at the end of the thread's life.
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
        int eRepeat = 0;
        LocalDate eDate = LocalDate.now();

        // split into lines
        String[] lines = raw.split("\n");


        // the first line is the title \\
        eTitle = lines[1].replaceFirst("# ", "");


        // the second line is the date and time \\
        String[] secondLine = lines[2].replace("< ","").split(" >");
        String[] date_time_repeat = secondLine[0].split(", ");

        String date = date_time_repeat[0];
        if( date.toLowerCase().equals("today") )
            eDate = LocalDate.now();
        else
        {
            eDate = eDate.withMonth(Month.valueOf(date.split(" ")[0]).getValue());
            eDate = eDate.withDayOfMonth(Integer.parseInt(date.split(" ")[1]));
        }

        String[] start_end_timezone = date_time_repeat[1].split(" - ");

        if( date_time_repeat.length == 3 )
        {
            String repeat = date_time_repeat[2].replaceFirst("repeats ", "");
            if( repeat.equals("daily") )
                eRepeat = 1;
            else if( repeat.equals("weekly") )
                eRepeat = 2;
        }

        eStart = start_end_timezone[0];
        eEnd = start_end_timezone[1].split(" ")[0];
        String timezone = start_end_timezone[1].split(" ")[1];


        // the third line is empty space,     \\

        // lines four through n-2 are comments \\
        for(int c = 4; c < lines.length-3; c++ )
            eComments.add(lines[c]);

        // line n-1 is an empty space \\

        // the last line contains the ID and minutes til timer \\
        eID = Integer.decode(
                "0x" + lines[lines.length-2].replace("[ID: ","").split("]")[0]
        ); // can throw away the minutes til timer


        return new EventEntry( eTitle, eStart, eEnd, eComments, eID, event, eRepeat, eDate );
    }


    /**
     *
     * @param eTitle
     * @param eStart
     * @param eEnd
     * @param eComments
     * @return
     */
    public static String generate(
            String eTitle,
            String eStart,
            String eEnd,
            ArrayList<String> eComments,
            int eRepeat,
            LocalDate eDate,
            Integer eID
    )
    {
        // the 'actual' first line (and last line) define format
        String msg = "```Markdown\n";

        eID = Main.newID( eID ); // generate an ID for the entry
        String firstLine = "# " + eTitle + "\n";

        String secondLine = "< " + eDate.getMonth() + " " + eDate.getDayOfMonth() + ", "
                + eStart + " - " + eEnd + " EST";
        if( eRepeat == 1 )
            secondLine += ", repeats daily >\n";
        else if( eRepeat == 2 )
            secondLine += ", repeats weekly >\n";
        else
            secondLine += " >\n";

        msg += firstLine + secondLine;

        // create an empty 'gap' line if there exists comments
        if( !eComments.isEmpty() )
            msg += "\n";

        // insert each comment line into the message
        for( String comment : eComments )
            msg += comment + "\n";

        // add the final ID and time til line
        msg += "\n[ID: " + Integer.toHexString(eID) + "](begins in " + "xx" + " hours.)\n";
        // cap the code block
        msg += "```";

        return msg;
    }


    /**
     * the EventEntry worker thread subclass
     */
    public class EventEntry implements Runnable {

        public String eTitle;                   // the title/name of the event
        public String eStart;                   // the time in (24h) when the event starts
        public String eEnd;                     // the ending time in 24h form
        public ArrayList<String> eComments;     // ArrayList of strings that make up the desc
        public Integer eID;                     // 16 bit identifier
        public int eRepeat;                      // 1 is daily, 2 is weekly, 0 is not at all
        public LocalDate eDate;
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
        public EventEntry(String eName, String eStart, String eEnd, ArrayList<String> eComments, Integer eID, MessageReceivedEvent msgEvent, int eRepeat, LocalDate eDate)
        {
            this.eTitle = eName;
            this.eStart = eStart;
            this.eEnd = eEnd;
            this.eComments = eComments;
            this.eID = eID;
            this.msgEvent = msgEvent;
            this.eRepeat = eRepeat;
            this.eDate = eDate;

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

            // convert the times into integers representing the time in seconds
            Integer startH = Integer.parseInt(this.eStart.split(":")[0]);
            Integer startM = Integer.parseInt(this.eStart.split(":")[1]);
            Integer start = startH*60*60 + startM*60;

            Integer endH = Integer.parseInt(this.eEnd.split(":")[0]);
            Integer endM = Integer.parseInt(this.eEnd.split(":")[1]);
            Integer end = endH*60*60 + endM*60;

            // using the local time, determine the wait interval (in seconds)
            LocalDateTime now = LocalDateTime.now();
            Integer nowInSeconds = (now.getHour()*60*60 + now.getMinute()*60 + now.getSecond());
            Integer wait1 = start - nowInSeconds;
            Integer wait2 = end - start;

            if( wait1 < 0 )
            {
                wait1 += 24 * 60 * 60;
            }
            if( wait2 < 0 )
            {
                wait2 += 24 * 60 * 60;
            }

            // run the main operation of the thread
            try
            {
                // sleep until the day of the event starts
                Integer wait;
                while( !this.eDate.equals(LocalDate.now()) )
                {
                    int days = (int) DAYS.between(LocalDate.now(), eDate);
                    String[] lines = this.msgEvent.getMessage().getRawContent().split("\n");

                    String newline = lines[lines.length-2].split("\\(")[0] + "(begins ";
                    if( days < 1)
                        newline += "tomorrow.)";
                    else
                        newline += "in " + days + " days.)";

                    String msg = "";
                    for(String line : lines)
                    {
                        if(line.equals(lines[lines.length-2]))
                            msg += newline;
                        else
                            msg += line;
                        if(!line.equals(lines[lines.length-1]))
                            msg += "\n";
                    }

                    Main.editMsg( msg, this.msgEvent.getMessage() );

                    wait = 24*60*60 - LocalTime.now().toSecondOfDay();
                    System.out.printf("[" + LocalTime.now().getHour() + ":" + LocalTime.now().getMinute() + ":"
                            + LocalTime.now().getSecond() + "]" + " [ID: " + Integer.toHexString(this.eID) +
                            "] Sleeping for " + wait + " seconds.\n");

                    Thread.sleep(wait * 1000);
                }

                // sleep until the start time
                wait = wait1 - (int)(Math.floor( ((double)wait1)/(60*60) )*60*60);
                wait1 = (int)Math.ceil(((double)wait1)/(60*60))*60*60;
                while( wait1 != 0 )
                {
                    String[] lines = this.msgEvent.getMessage().getRawContent().split("\n");
                    int hoursTil = (int)Math.ceil((double)wait2/(60*60));
                    String newline = lines[lines.length-2].split("\\(")[0] + "(begins ";
                    if( hoursTil < 1)
                        newline += "within the hour.)";
                    else
                        newline += "in " + hoursTil + " hours.)";

                    String msg = "";
                    for(String line : lines)
                    {
                        if(line.equals(lines[lines.length-2]))
                            msg += newline;
                        else
                            msg += line;
                        if(!line.equals(lines[lines.length-1]))
                            msg += "\n";
                    }

                    Main.editMsg( msg, this.msgEvent.getMessage() );

                    System.out.printf("[" + LocalTime.now().getHour() + ":" + LocalTime.now().getMinute() + ":"
                            + LocalTime.now().getSecond() + "]" + " [ID: " + Integer.toHexString(this.eID) +
                            "] Sleeping for " + wait + " seconds.\n");
                    Thread.sleep(wait * 1000);        // sleep until the event starts
                    wait = 60*60;                     // set wait to one hour
                    wait1 -= 60*60;                   // decrement wait1 by one hour
                }

                // announce that the event is beginning
                if(BotConfig.ANNOUNCE_CHAN.isEmpty() ||
                        guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).isEmpty())
                    guild.getPublicChannel().sendMessage( startMsg ).queue();
                else
                    guild.getTextChannelsByName(BotConfig.ANNOUNCE_CHAN, false).get(0)
                            .sendMessage( startMsg ).queue();

                // sleep until event end time
                wait = wait2 - (int)(Math.floor( ((double)wait2)/(60*60) )*60*60);
                wait2 = (int) Math.ceil( ((double)wait2)/(60*60) )*60*60;
                while( wait2 != 0 )
                {
                    String[] lines = this.msgEvent.getMessage().getRawContent().split("\n");
                    int hoursTil = (int)Math.ceil((double)wait2/(60*60));
                    String newline = lines[lines.length-2].split("\\(")[0] +
                            "(ends ";
                    if( hoursTil < 1)
                        newline += "within one hour.)";
                    else
                        newline += "in " + hoursTil + " hours.)";

                    String msg = "";
                    for(String line : lines)
                    {
                        if(line.equals(lines[lines.length-2]))
                            msg += newline;
                        else
                            msg += line;
                        if(!line.equals(lines[lines.length-1]))
                            msg += "\n";
                    }

                    Main.editMsg( msg, this.msgEvent.getMessage() );

                    System.out.printf("[" + LocalTime.now().getHour() + ":" + LocalTime.now().getMinute() + ":"
                            + LocalTime.now().getSecond() + "]" + " [ID: " + Integer.toHexString(this.eID) +
                            "] Sleeping for " + wait + " seconds.\n");

                    Thread.sleep(wait * 1000);        // sleep until the event starts
                    wait = 60*60;                     // set wait to one hour
                    wait2 -= 60*60;                   // decrement wait1 by one hour
                }

                // announce that the event is ending
                Main.sendAnnounce( endMsg, guild );

                // if the event entry is scheduled to repeat, must be handled with now
                if( this.eRepeat == 1 )
                {
                    // generate the event entry message
                    String msg = EventEntryParser.generate(
                            this.eTitle,
                            this.eStart,
                            this.eEnd,
                            this.eComments,
                            this.eRepeat,
                            this.eDate.plusDays(1),
                            this.eID
                    );

                    Main.sendMsg( msg, this.msgEvent.getChannel() );
                }
                else if( this.eRepeat == 2 )
                {
                    // generate the event entry message
                    String msg = EventEntryParser.generate(
                            this.eTitle,
                            this.eStart,
                            this.eEnd,
                            this.eComments,
                            this.eRepeat,
                            this.eDate.plusDays(7),
                            this.eID
                    );

                    Main.sendMsg( msg, this.msgEvent.getChannel() );
                }
            }

            // if an interrupt is received, quit operation
            catch(InterruptedException e)
            {
                return;
            }

            // always remove the entry from entriesGlobal and delete the message
            finally
            {
                // remove the thread from the entriesGlobal
                Main.entriesGlobal.remove(this.eID);

                // remove entry from guild
                Main.removeEntry( this.eID, this.msgEvent.getGuild().getId() );

                // delete the old entry
                Main.deleteMsg( this.msgEvent.getMessage() );
            }
        }
    }
}
