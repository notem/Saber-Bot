package ws.nmathe.saber.core.schedule;

import ws.nmathe.saber.utils.Logging;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.nextOrSame;

public class EventRecurrence
{
    /** DateTimeFormatter that is RFC3339 compliant  */
    public static DateTimeFormatter RFC3339_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     *  daily interval      - mode = 0
     *  unused              - mode = 1
     *  minute interval     - mode = 2
     *  year interval       - mode = 3
     *  weekly by day       - mode = 4
     *  monthly by weekday  - mode = 5
     *  monthly by date     - mode = 6
     *  unused              - mode = 7
     */
    private static final int DAILY_MODE  = 0;
    private static final int MINUTE_MODE = 2;
    private static final int YEAR_MODE   = 3;
    private static final int WEEK_MODE   = 4;
    private static final int MONTH1_MODE = 5;
    private static final int MONTH2_MODE = 6;

    /**
     * The recurrence int should be interpreted in it's binary representation:
     *
     * ..0...1...2...3...4...5...6...7...8...9..10..11..12..13..14..15.
     * +-----------+---------------------------------------------------+
     * | mode=0-3  |                    interval                       |  max = 2^13
     * +---------------------------------------------------------------+
     *
     * ..0...1...2...3...4...5...6...7...8...9..10..11..12..13..14..15.
     * +-----------+---------------------------------------------------+
     * |  mode=4   | Su  Mo  Tu  We  Th  Fr  Sa|     weekly interval   |  max = 64
     * +---------------------------------------------------------------+
     *
     * ..0...1...2...3...4...5...6...7...8...9..10..11..12..13..14..15.
     * +-----------+---------------------------------------------------+
     * |  mode=5   |  weekday  |    nth    |     monthly interval      |  max = 128
     * +---------------------------------------------------------------+
     *
     * ..0...1...2...3...4...5...6...7...8...9..10..11..12..13..14..15.
     * +-----------+---------------------------------------------------+
     * |  mode=6   |    day of month   |       monthly interval        |  max = 256
     * +---------------------------------------------------------------+
     */
    private Integer recurrence;

    /** the remaining number of times the event should repeat */
    private Integer count;

    /** the date of the first occurrence of the event*/
    private ZonedDateTime startDate;

    /** the date to expire */
    private ZonedDateTime expire;

    // empty constructor
    public EventRecurrence(ZonedDateTime dtStart)
    {
        this.recurrence  = 0;
        this.expire      = null;
        this.count       = null;
        this.startDate   = dtStart;
    }

    public EventRecurrence(int recurrence, ZonedDateTime dtStart)
    {
        this.recurrence  = recurrence;
        this.expire      = null;
        this.count       = null;
        this.startDate   = dtStart;
    }

    /**
     * parses a recurrence string that is formatted in accordance
     * with the RFC5545 specifications for calendar event recurrence
     * NOTE: this follows Google Calendar's implementation of the ruleset
     * @param rfc5545 string containing required information
     */
    public EventRecurrence(List<String> rfc5545, ZonedDateTime dtstart)
    {
        this.recurrence  = 0;
        this.expire      = null;
        this.count       = null;
        this.startDate   = dtstart;

        // attempt to parse the ruleset
        int mode = 0, data = 0;
        for(String rule : rfc5545)
        {
            if(rule.startsWith("RRULE") && rule.contains("FREQ"))
            {
                // parse out the frequency of recurrence
                String tmp = rule.split("FREQ=")[1].split(";")[0];
                switch (tmp)
                {
                    case "DAILY":
                        mode = DAILY_MODE;
                        if (rule.contains("INTERVAL"))
                            data = Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]);
                        else
                            data = 0b1;
                        break;
                    case "WEEKLY":
                        mode = WEEK_MODE;
                        if (rule.contains("BYDAY"))
                            data |= EventRecurrence.parseRepeat(rule.split("BYDAY=")[1].split(";")[0])>>3;
                        if (rule.contains("INTERVAL"))
                            data |= Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]) << 7;
                        else
                            data |= 1 << 7;
                        break;
                    case "MONTHLY":
                        if (rule.contains("BYDAY"))
                        {
                            mode = MONTH1_MODE;
                            tmp  = rule.split("BYDAY=")[1].split(";")[0];
                            switch (tmp.replaceAll("[\\d]",""))
                            {
                                case "MO": data |= 1; break;
                                case "TU": data |= 2; break;
                                case "WE": data |= 3; break;
                                case "TH": data |= 4; break;
                                case "FR": data |= 5; break;
                                case "SA": data |= 6; break;
                                case "SU": data |= 7; break;
                            }
                            data |= Integer.valueOf(tmp.replaceAll("[^\\d]","")) << 3;
                            if (rule.contains("INTERVAL"))
                                data |= Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]) << 6;
                            else
                                data |= 1 << 6;
                        }
                        else
                        {
                            mode = MONTH2_MODE;
                            if (rule.contains("INTERVAL"))
                                data |= Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]) << 5;
                            else
                                data |= 1 << 5;
                        }
                        break;
                    case "YEARLY":
                        mode = YEAR_MODE;
                        if (rule.contains("INTERVAL"))
                            data |= Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]);
                        else
                            data |= 1;
                        break;
                }
                this.recurrence = mode | data<<3;

                // parse out the end date of recurrence
                if(rule.contains("UNTIL="))
                {
                    tmp = rule.split("UNTIL=")[1].split(";")[0];
                    int year    = Integer.parseInt(tmp.substring(0, 4));
                    int month   = Integer.parseInt(tmp.substring(4,6));
                    int day     = Integer.parseInt(tmp.substring(6, 8));
                    this.expire = ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.MIN, ZoneId.systemDefault());
                }
                // interpret occurrence count
                else if(rule.contains("COUNT="))
                {
                    this.count = Integer.valueOf(rule.split("COUNT=")[1].split(";")[0]);
                }
            }
        }
    }


    /**
     * Parses out the intended event shouldRepeat information from user input
     * @param input (String) the user input
     * @return (int) an integer representing the shouldRepeat information (stored in binary)
     */
    public static int parseRepeat(String input)
    {
        input = input.toLowerCase().trim(); // safety
        int mode = 0, data = 0;
        if(input.matches("off"))
        {
            return 0;
        }
        else if(input.matches("daily"))
        {
            mode = WEEK_MODE;
            data = 0b1111111;
        }
        else if(input.matches("weekly"))
        {
            mode = DAILY_MODE;
            data = 7;
        }
        else if(input.matches("month(ly)?"))
        {
            mode = MONTH2_MODE;
            data = 0;
        }
        else if(input.matches("year(ly)?"))
        {
            mode = YEAR_MODE;
            data = 1;
        }
        else if(input.matches("\\d+([ ]?m(in(utes)?)?)"))
        {
            mode = MINUTE_MODE;
            data = Integer.parseInt(input.replaceAll("[^\\d]",""));
        }
        else if(input.matches("\\d+([ ]?h(our(s)?)?)"))
        {
            mode = MINUTE_MODE;
            data = Integer.parseInt(input.replaceAll("[^\\d]",""))*60;
        }
        else if(input.matches("\\d+([ ]?d(ay(s)?)?)?"))
        {
            mode = DAILY_MODE;
            data = Integer.parseInt(input.replaceAll("[^\\d]",""));
        }
        else if(input.matches("\\d+([ ]?w(eek(s)?)?)?"))
        {
            mode = DAILY_MODE;
            data = Integer.parseInt(input.replaceAll("[^\\d]",""))*7;
        }
        else if(input.matches("\\d+([ ]?m(onth(s)?)?)"))
        {
            mode = MONTH2_MODE;
            data = Integer.parseInt(input.replaceAll("[^\\d]",""))<<5;
        }
        else if(input.matches("\\d+([ ]?y(ear(s)?)?)"))
        {
            mode = YEAR_MODE;
            data = Integer.parseInt(input.replaceAll("[^\\d]",""));
        }
        else
        {
            String regex = "[,;:. ]([ ]+)?";
            String[] s = input.split(regex);
            for(String string : s)
            {
                if(string.matches("mo(n(day)?)?"))    data |= 1;
                if(string.matches("tu(e(sday)?)?"))   data |= 1<<1;
                if(string.matches("we(d(nesday)?)?")) data |= 1<<2;
                if(string.matches("th(u(rsday)?)?"))  data |= 1<<3;
                if(string.matches("fr(i(day)?)?"))    data |= 1<<4;
                if(string.matches("sa(t(urday)?)?"))  data |= 1<<5;
                if(string.matches("su(n(day)?)?"))    data |= 1<<6;
            }
            // change the mode to by weekday only if at least weekday string was found
            if (data > 0)
            {
                mode = WEEK_MODE;
            }
        }
        return mode | (data<<3);
    }

    /**
     * Generated a string describing the recurrence settings of an event
     * @return string
     */
    public String toString()
    {
        // useful string constants
        String[] spellout = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
        String[] prefixes = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};

        StringBuilder str = new StringBuilder();
        int mode = recurrence & 0b111;
        int data = recurrence >> 3;

        // no repeat
        if (recurrence == 0)
            return "once";

        // every day
        if ((mode == WEEK_MODE && data == 0b1111111)
                || (mode==DAILY_MODE && data==1))
            return "every day";

        // on interval days
        if (mode == DAILY_MODE)
        {
            if (data == 7)
                return "every week";
            return "every "+(data>spellout.length ? data : spellout[data-1])+" days";
        }

        // minute repeat
        if (mode == MINUTE_MODE)
        {
            if (data%60 == 0)
            {
                int hours = data/60;
                if (data == 60) return "every hour";
                else return "every "+(hours<=spellout.length ? spellout[hours-1]:hours)+" hours";
            }
            else
            {
                return "every "+data+" minutes";
            }
        }

        // yearly
        if (mode == YEAR_MODE && data == 1)
            return "every year";


        // monthly on weekday
        if (mode == MONTH1_MODE)
        {
            DayOfWeek dayOfWeek = DayOfWeek.of(data&0b111);
            int nth = (data>>3)&0b111;
            int monthInterval = data>>6;
            if (monthInterval>1)
            {
                return nth+prefixes[nth]+" " +
                        dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())+
                        " of every " + monthInterval+prefixes[monthInterval%10]+" months";
            }
            else
            {
                return nth+prefixes[nth] + " " +
                        dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                        " of every month";
            }
        }
        // monthly on day of month
        if (mode == MONTH2_MODE)
        {
            int dayOfMonth    = data&0b11111;
            int monthInterval = data>>5;
            if (monthInterval>1)
            {
                return "every " + (monthInterval > spellout.length ? monthInterval : spellout[monthInterval-1]) +
                        " months" + (dayOfMonth>0 ? " on the "+dayOfMonth+prefixes[dayOfMonth%10]:"");
            }
            else
            {
                return "every month"+(dayOfMonth>0 ? " on the "+dayOfMonth+prefixes[dayOfMonth%10]:"");
            }
        }

        // on certain weekday
        if (mode == WEEK_MODE)
        {
            int weeks = data>>7;
            data &= 0b1111111;
            if (weeks>1)
            {
                str = new StringBuilder("every "+(weeks>spellout.length ? weeks+"":spellout[weeks-1])+" weeks on ");
            }
            else
            {
                str = new StringBuilder("weekly on ");
            }
            for(int j=0; data!=0; j++, data>>=1)
            {
                String full;
                String narrow;
                if ((data&0b1)==1)
                {
                    full   = DayOfWeek.of(j+1).getDisplayName(TextStyle.FULL, Locale.getDefault());
                    narrow = DayOfWeek.of(j+1).getDisplayName(TextStyle.SHORT, Locale.getDefault());
                    if (data==1)
                        return str + full;
                    str.append(narrow);
                    if ((data>>1) != 0 )
                        str.append(", ");
                }
            }
        }
        return str.toString();
    }

    /**
     * determine if the event should recur
     * @return true if the event should shouldRepeat
     */
    public boolean shouldRepeat(ZonedDateTime now)
    {
        if (this.recurrence == 0)
        {
            return false;
        }
        else
        {
            if (this.count!=null && this.startDate!=null)
                return this.countRemaining(now)>1;
            else if (this.expire!=null)
                return this.expire.isAfter(now);
            return true;
        }
    }

    /**
     * determine the next time for the event
     * @param date the date of the previous start/end of the event
     * @return the new start of the event (based on the recurrence rule)
     */
    public ZonedDateTime next(ZonedDateTime date)
    {
        if (this.recurrence == 0) return date;

        /// determine mode
        int mode = this.recurrence & 0b111; // separate out mode
        int data = this.recurrence >>3;     // shift off mode bits
        switch(mode)
        {
            // interval repeat
            case DAILY_MODE:
                return date.plusDays(data);
            case MINUTE_MODE:
                return date.plusMinutes(data);
            case YEAR_MODE:
                return date.plusYears(data);

            // repeat weekly by day of week
            case WEEK_MODE:
                int day   = 1<<(date.getDayOfWeek().getValue()-1);  // represent current day of week as int
                int weeks = (data>>7)==0 ? 1:data>>7;               // number of weeks to shouldRepeat
                data = data & 0b1111111;                            // integer representing weekdays

                if (data == 0)
                    return date.plusWeeks(weeks);
                if (day == data)
                    return date.plusWeeks(weeks);

                int count = 1;
                if (day<(1<<6)) day<<=1;
                else { day = 1; date = date.plusWeeks(weeks-1); }
                while (day != (data&day))
                {
                    if (day<(1<<6)) day<<=1;
                    else { day = 1; date = date.plusWeeks(weeks-1); }
                    count++;
                }
                return date.plusDays(count);

            // repeat on nth week day every mth month
            case MONTH1_MODE:
                DayOfWeek dayOfWeek = DayOfWeek.of(data&0b111);
                int nth = (data>>3)&0b111;
                date = date.with(firstDayOfMonth())
                        .plusMonths((data>>6)>1 ? (data>>6) : 1)
                        .with(nextOrSame(dayOfWeek));
                while(nth>1)
                {
                   date = date.plusDays(1).with(nextOrSame(dayOfWeek));
                   nth--;
                }
                return date;

            // repeat on n day of every mth month
            case MONTH2_MODE:
                int dayOfMonth    = data&0b11111;
                int monthInterval = data>>5;
                if (dayOfMonth > 0)
                {   // dayOfMonth portion indicates the day of the month the event should repeat on
                    return date.plusMonths(monthInterval).withDayOfMonth(dayOfMonth);
                }
                else
                {   // if the monthInterval portion is zero, increment to onet
                    return date.plusMonths(monthInterval == 0 ? 1 : monthInterval);
                }

            // something went wrong
            default:
                Logging.warn(this.getClass(), "Encountered invalid repeat mode!");
                return date;
        }
    }

    /**
     * generates a valid list of event recurrence rules as specified by RFC5545
     * NOTE: this follows Google Calendar's implementation of the ruleset
     * @return singleton list containing the RRULE
     */
    public List<String> toRFC5545()
    {
        List<String> rules = new ArrayList<>();
        if (this.recurrence == 0) return rules;

        String rule = "RRULE:";
        int mode = this.recurrence & 0b111;
        int data = this.recurrence >> 3;
        switch (mode)
        {
            // daily interval
            case DAILY_MODE:
                rule += "FREQ=DAILY;INTERVAL="+data+";";
                break;

            // yearly
            case YEAR_MODE:
                rule += "FREQ=YEARLY;INTERVAL="+data+";";
                break;

            // by weekday
            case WEEK_MODE:
                rule += "FREQ=WEEKLY;BYDAY=";
                List<String> days = new ArrayList<>();
                if((data&0b0000001) == 0b0000001) days.add("SU");
                if((data&0b0000010) == 0b0000010) days.add("MO");
                if((data&0b0000100) == 0b0000100) days.add("TU");
                if((data&0b0001000) == 0b0001000) days.add("WE");
                if((data&0b0010000) == 0b0010000) days.add("TH");
                if((data&0b0100000) == 0b0100000) days.add("FR");
                if((data&0b1000000) == 0b1000000) days.add("SA");
                rule += String.join(",",days) + ";";
                rule += "INTERVAL="+(data>>7)+";";
                break;

            // on nth week day every mth month
            case MONTH1_MODE:
                DayOfWeek dayOfWeek = DayOfWeek.of(data&0b111);
                int nth = (data>>3)&0b111;
                rule += "FREQ=MONTHLY;BYDAY="+nth+dayOfWeek+";INTERVAL="+(data>>6==0 ? 1:data>>6)+";";
                break;

            // on n day of every mth month
            case MONTH2_MODE:
                int dayOfMonth = data&0b11111;
                rule += "FREQ=MONTHLY;BYMONTHDAY="+dayOfMonth+";INTERVAL="+(data>>5==0 ? 1:data>>5)+";";
                break;
        }
        if (expire != null)
        {
            rule += "UNTIL="+
                    String.format("%04d%02d%02d",expire.getYear(),
                            expire.getMonthValue(),expire.getDayOfMonth()) +
                    "T000000Z;";
        }
        else if (count != null)
        {
            rule += "COUNT="+this.count+";";
        }
        rules.add(rule);
        return rules;
    }

    /**
     * uses the count, startDate, and current time to determine how many more occurrences the event
     * has until it expires
     * @return null if count not set, else remaining count
     */
    public Integer countRemaining(ZonedDateTime now)
    {
        if (count==null || startDate==null)
            return -1; // error
        if (now.isBefore(startDate) || now.equals(startDate))
            return count;  // no events have occurred yet

        // determine remaining event occurrences
        int mode = this.recurrence & 0b111;
        int data = this.recurrence >> 3;
        switch (mode)
        {
            case DAILY_MODE:     // daily
                long days = startDate.until(now, ChronoUnit.DAYS);
                return count - ((int) days/(data==0?1:data));

            case MINUTE_MODE:     // minutely
                long minutes = startDate.until(now, ChronoUnit.MINUTES);
                return count - ((int) minutes/(data==0?1:data));

            case YEAR_MODE:     // yearly
                long years = startDate.until(now, ChronoUnit.YEARS);
                return count - ((int) years/(data==0?1:data));

            case WEEK_MODE:     // weekly
                int weeks = (int) (startDate.until(now, ChronoUnit.WEEKS)/((data>>7)==0?1:(data>>7)));

                // calculate the number of days of the week the event repeats on
                int c = 0;                      // count of weekdays event repeats on
                for (int tmp=(data&0b1111111); tmp>0; tmp>>=1) { if ((tmp&0b1)==1) c++; }
                int rem = count - c*weeks;      // how many events have past

                // process the remaining portion of the week
                ZonedDateTime cur = startDate.plusWeeks(weeks);
                for (int j=cur.getDayOfWeek().getValue(); j<=7 && now.isAfter(cur); j++)
                {   // for each day of last remaining week
                    // if day of week is set, decrement rem count
                    if (((data&0b1111111) & (1<<(j-1))) == 1) rem--;
                    cur = cur.plusDays(1);
                }
                return rem;

            case MONTH1_MODE:     // mo by day
                long months = startDate.until(now, ChronoUnit.MONTHS);
                return count - ((int) months/((data>>6)==0?1:(data>>5)));

            case MONTH2_MODE:     // mo by date
                months = startDate.until(now, ChronoUnit.MONTHS);
                return count - ((int) months/((data>>5)==0?1:(data>>5)));

            default:    // something went wrong!
                return null;
        }
    }

    public ZonedDateTime getExpire()
    {
        return this.expire;
    }

    public Integer getRepeat()
    {
        return this.recurrence;
    }

    public Integer getCount()
    {
        return this.count;
    }

    public ZonedDateTime getOriginalStart()
    {
        return this.startDate;
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

    public EventRecurrence setCount(Integer count)
    {
        this.count = count;
        return this;
    }

    public EventRecurrence setOriginalStart(ZonedDateTime original)
    {
        this.startDate = original;
        return this;
    }
}
