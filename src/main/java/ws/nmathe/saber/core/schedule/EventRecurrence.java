package ws.nmathe.saber.core.schedule;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.nextOrSame;

public class EventRecurrence
{
    /** DateTimeFormatter that is RFC3339 compliant  */
    public static DateTimeFormatter RFC3339_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * The event recurrence is described as an int which represents on the following modes:
     *  daily interval      - mode = 0
     *  weekly interval     - mode = 1
     *  minute interval     - mode = 2
     *  year interval       - mode = 3
     *  weekly by day       - mode = 4
     *  monthly by weekday  - mode = 5
     *  monthly by date     - mode = 6
     *
     * The recurrence int should be interpreted in it's binary representation:
     *
     * ..0...1...2...3...4...5...6...7...8...9..10..11..12..13..14..15.
     * +-----------+---------------------------------------------------+
     * | mode=0-3  |                    interval                       |
     * +---------------------------------------------------------------+
     *
     * ..0...1...2...3...4...5...6...7...8...9..10..11..12..13..14..15.
     * +-----------+---------------------------------------------------+
     * |  mode=4   | Su  Mo  Tu  We  Th  Fr  Sa |        unused        |
     * +---------------------------------------------------------------+
     *
     * ..0...1...2...3...4...5...6...7...8...9..10..11..12..13..14..15.
     * +-----------+---------------------------------------------------+
     * |  mode=5   |  weekday  |    nth    |     monthly interval      |
     * +---------------------------------------------------------------+
     *
     * ..0...1...2...3...4...5...6...7...8...9..10..11..12..13..14..15.
     * +-----------+---------------------------------------------------+
     * |  mode=6   |    day of month   |       monthly interval        |
     * +---------------------------------------------------------------+
     */
    private Integer recurrence;

    /** the remaining number of times the event should repeat */
    private Integer occurrences;

    /** the date to expire */
    private ZonedDateTime expire;

    // empty constructor
    public EventRecurrence()
    {
        recurrence  = 0;
        occurrences = null;
        expire      = null;
    }

    public EventRecurrence(int recurrence)
    {
        this.recurrence  = recurrence;
        this.occurrences = null;
        this.expire      = null;
    }

    /**
     * parses a recurrence string that is formatted in accordance
     * with the RFC5545 specifications for calendar event recurrence
     * @param rfc5545 string containing required information
     */
    public EventRecurrence(List<String> rfc5545)
    {
        recurrence  = 0;
        occurrences = null;
        expire      = null;
        for(String rule : rfc5545)
        {
            if(rule.startsWith("RRULE") && rule.contains("FREQ" ))
            {
                // parse out the frequency of recurrence
                String tmp = rule.split("FREQ=")[1].split(";")[0];
                if(tmp.equals("DAILY" ))
                {
                    if(rule.contains("INTERVAL"))
                    {
                        int interval = Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]);
                        this.recurrence = interval<<3;
                    }
                    else
                    {
                        this.recurrence = (0b1111111)<<3;
                    }
                }
                else if(tmp.equals("WEEKLY") && rule.contains("BYDAY"))
                {
                    tmp = rule.split("BYDAY=")[1].split(";")[0];
                    this.recurrence = EventRecurrence.parseRepeat(tmp);
                }

                // parse out the end date of recurrence
                if(rule.contains("UNTIL="))
                {
                    tmp = rule.split("UNTIL=")[1].split(";")[0];
                    int year  = Integer.parseInt(tmp.substring(0, 4));
                    int month = Integer.parseInt(tmp.substring(4,6));
                    int day   = Integer.parseInt(tmp.substring(6, 8));
                    expire = ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.MIN, ZoneId.systemDefault());
                }
            }
        }
    }

    /**
     *
     * @param legacyRepeat
     * @return
     */
    public EventRecurrence fromLegacy(Integer legacyRepeat)
    {
        // todo
        return this;
    }

    /**
     * Parses out the intended event repeat information from user input
     * @param input (String) the user input
     * @return (int) an integer representing the repeat information (stored in binary)
     */
    public static int parseRepeat(String input)
    {
        input = input.toLowerCase().trim(); // sanity check
        int mode = 0, data = 0;
        if(input.toLowerCase().equals("daily"))
        {
            mode = 4;
            data = 0b1111111;
        }
        else if(input.toLowerCase().equals("yearly"))
        {
            mode = 3;
            data = 1;
        }
        else if(input.equals("off") || input.startsWith("no"))
        {
            return 0;
        }
        else
        {
            String regex = "[,;:. ]([ ]+)?";
            String[] s = input.split(regex);
            for(String string : s)
            {
                if(string.matches("su(n(day)?)?"))    data |= 1;
                if(string.matches("mo(n(day)?)?"))    data |= 1<<1;
                if(string.matches("tu(e(sday)?)?"))   data |= 1<<2;
                if(string.matches("we(d(nesday)?)?")) data |= 1<<3;
                if(string.matches("th(u(rsday)?)?"))  data |= 1<<4;
                if(string.matches("fr(i(day)?)?"))    data |= 1<<5;
                if(string.matches("sa(t(urday)?)?"))  data |= 1<<6;
            }
        }
        return mode | (data<<3);
    }

    /**
     * Generated a string describing the repeat settings of an event
     * @param isNarrow (boolean) for use with narrow style events
     * @return string
     */
    public String toString(boolean isNarrow)
    {
        String str = "";
        int mode = recurrence & 0b111;
        int data = recurrence >> 3;
        if(isNarrow)
        {
            if (recurrence == 0)
                return "once";
            if (mode == 4 & data == 0b1111111)
                return "every day";
            // repeat on interval
            if (mode == 1)
            {
                String[] spellout = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
                return "every " + (data>spellout.length ? data : spellout[data-1]) + " days";
            }
            // repeat x minutes
            if (mode == 2)
            {
                if (data%60 == 0)
                {
                    if (data == 60)
                        return "every hour";
                    else
                        return "every "+ data/60 +" hours";
                }
                else
                {
                    return "every " + data + " minutes";
                }
            }
            // yearly repeat
            if (mode == 3 && data == 1)
            {
                return "every year";
            }
            // monthly on weekday
            if (mode==5)
            {
                // todo
            }
            // monthly on day of month
            if (mode==6)
            {
                // todo
            }
        }
        else
        {
            if (recurrence == 0)
                return "does not repeat";
            if (mode == 4 & data == 0b1111111)
                return "repeats daily";
            // repeat on interval
            if (mode == 1)
            {
                String[] spellout = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
                return "repeats every " + (data>spellout.length ? data : spellout[data-1]) + " days";
            }
            // repeat x minutes
            if (mode == 2)
            {
                if (data%60 == 0)
                {
                    if (data == 60)
                        return "repeats hourly";
                    else
                        return "repeats every " + (data/60) + " hours";
                }
                else
                {
                    return "repeats every " + data + " minutes";
                }
            }
            // yearly repeat
            if (mode == 3 && data == 1)
            {
                return "repeats yearly";
            }
            // monthly on weekday
            if (mode==5)
            {
                // todo
            }
            // monthly on day of month
            if (mode==6)
            {
                // todo
            }
        }

        // weekday repeat
        if (mode == 4)
        {
            if (isNarrow) str = "every ";
            else str = "repeats weekly on ";

            /// only reaches here for weekly repeat
            for(int j=0; data!=0; j++, data>>=1)
            {
                String full;
                String narrow;
                switch (j)
                {
                    case 0:
                        full = "Sunday";
                        narrow = "Sun";
                        break;
                    case 1:
                        full = "Monday";
                        narrow = "Mon";
                        break;
                    case 2:
                        full = "Tuesday";
                        narrow = "Tue";
                        break;
                    case 3:
                        full = "Wednesday";
                        narrow = "Wed";
                        break;
                    case 4:
                        full = "Thursday";
                        narrow = "Thu";
                        break;
                    case 5:
                        full = "Friday";
                        narrow = "Fri";
                        break;
                    case 6:
                        full = "Saturday";
                        narrow = "Sat";
                        break;
                    default:
                        full = "error";
                        narrow = "err";
                        break;
                }
                if (data==1)
                    return str + full;
                str += narrow;
                if ((data>>1) != 0 )
                    str += ", ";
            }
        }
        return str;
    }

    /**
     * determine if the event should recur
     * @return true if the event should repeat
     */
    public boolean repeat()
    {
        return this.recurrence != 0 &&
                !(this.occurrences != null && this.occurrences <= 0) &&
                !this.expire.isBefore(ZonedDateTime.now());
    }

    /**
     * determine the next start time for the event
     * @param date
     * @return
     */
    public ZonedDateTime next(ZonedDateTime date)
    {
        if (this.recurrence == 0) return date;

        /// determine mode
        int mode = this.recurrence & 0b111;   // separate out mode
        int tmp  = this.recurrence >>3;       // shift off mode bits
        switch(mode)
        {
            // interval repeat
            case 0:
                return date.plusDays(tmp);
            case 1:
                return date.plusWeeks(tmp);
            case 2:
                return date.plusMinutes(tmp);
            case 3:
                return date.plusYears(tmp);

            // repeat by day of week
            case 4:
                // represent current day of week as int
                int day = 1<<date.getDayOfWeek().getValue();
                if (day==1<<7) day = 1; // represent sunday as 1

                // capture first 7 bits
                tmp = tmp & 0b1111111;
                if (tmp != 0) return date;

                if (day == tmp)
                    return date.plusDays(7);

                int count = 0;
                while (day != (tmp&day))
                {
                    if (day<7) day<<=1;
                    else day = 1;
                    count++;
                }
                return date.plusDays(count);

            // repeat on nth week day every mth month
            case 5:
                DayOfWeek dayOfWeek = DayOfWeek.of(tmp&0b111);
                tmp >>= 3;
                int nth = (tmp & 0b111);
                date = date.plusMonths(tmp>>3).with(firstDayOfMonth()).with(nextOrSame(dayOfWeek));
                while(nth>0)
                {
                   date = date.plusDays(1).with(nextOrSame(dayOfWeek));
                   nth--;
                }
                return date;

            // repeat on n day of every mth month
            case 6:
                int dayOfMonth    = tmp & 0b11111;
                int monthInterval = tmp>>5;
                return date.plusMonths(monthInterval).withDayOfMonth(dayOfMonth);

            // something went wrong
            default:
                return date;
        }
    }

    /**
     * generates a valid list of event recurrence rules
     * as specified by RFC5545
     * @return singleton list containing the RRULE
     */
    public List<String> toRFC5545()
    {
        List<String> rules = new ArrayList<>();
        if (this.recurrence == 0) return rules;

        String rule = "RRULE:";
        int mode    = this.recurrence & 0b111;
        int trimmed = this.recurrence >> 3;
        switch (mode)
        {
            // daily interval repeat
            case 0:
                rule += "FREQ=DAILY;INTERVAL=" + trimmed + ";";
                break;
            // weekly repeat
            case 1:
                // todo
                break;
            // yearly repeat
            case 3:
                // todo
                break;
            // repeat by weekday
            case 4:
                if(trimmed == 0b1111111) // every day
                {
                    rule += "FREQ=DAILY;";
                }
                else
                {
                    rule += "FREQ=WEEKLY;BYDAY=";
                    List<String> days = new ArrayList<>();
                    if((trimmed&0b0000001) == 0b0000001) days.add("SU");
                    if((trimmed&0b0000010) == 0b0000010) days.add("MO");
                    if((trimmed&0b0000100) == 0b0000100) days.add("TU");
                    if((trimmed&0b0001000) == 0b0001000) days.add("WE");
                    if((trimmed&0b0010000) == 0b0010000) days.add("TH");
                    if((trimmed&0b0100000) == 0b0100000) days.add("FR");
                    if((trimmed&0b1000000) == 0b1000000) days.add("SA");
                    rule += String.join(",",days) + ";";
                }
                break;
            // repeat on nth week day every mth month
            case 5:
                // todo
                break;
            // repeat on n day of every mth month
            case 6:
                // todo
                break;
        }
        if (expire != null)
        {
            rule += "UNTIL=" +
                    String.format("%04d%02d%02d",expire.getYear(),expire.getMonthValue(),expire.getDayOfMonth()) +
                    "T000000Z;";
        }
        else if (occurrences != null)
        {
            // todo
        }
        rules.add(rule);
        return rules;
    }

    public ZonedDateTime getExpire()
    {
        return this.expire;
    }

    public Integer getRepeat()
    {
        return this.recurrence;
    }

    public Integer getOccurrences()
    {
        return this.occurrences;
    }

    public EventRecurrence setExpire(ZonedDateTime expire)
    {
        this.expire = expire;
        return this;
    }

    public EventRecurrence setRepeat(Integer repeat)
    {
        this.recurrence = repeat;
        return this;
    }

    public EventRecurrence setOccurrences(Integer occurrences)
    {
        this.occurrences = occurrences;
        return this;
    }
}
