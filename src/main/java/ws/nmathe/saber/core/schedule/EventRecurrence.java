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
     * The event recurrence is described as an int which represents on the following modes:
     *  daily interval      - mode = 0
     *  unused              - mode = 1
     *  minute interval     - mode = 2
     *  year interval       - mode = 3
     *  weekly by day       - mode = 4
     *  monthly by weekday  - mode = 5
     *  monthly by date     - mode = 6
     *  unused              - mode = 7
     *
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
                Logging.info(this.getClass(), rule);
                // parse out the frequency of recurrence
                String tmp = rule.split("FREQ=")[1].split(";")[0];
                switch (tmp)
                {
                    case "DAILY":
                        mode = 0;
                        if (rule.contains("INTERVAL"))
                            data = Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]);
                        else
                            data = 0b1;
                        break;
                    case "WEEKLY":
                        mode = 4;
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
                            mode = 5;
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
                            mode = 6;
                            if (rule.contains("INTERVAL"))
                                data |= Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]) << 5;
                            else
                                data |= 1 << 5;
                        }
                        break;
                    case "YEARLY":
                        mode = 3;
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
     * convert an old repeat Integer representation to the new format
     * @param legacyRepeat old shouldRepeat representation
     * @return the updated event recurrence object
     */
    public EventRecurrence fromLegacy(Integer legacyRepeat)
    {
        int mode, data;
        if (legacyRepeat == 0)
        {
            mode = 0;
            data = 0;
        }
        // minute repeat (12th bit)
        else if ((legacyRepeat & (1<<11)) == 0b100000000000)
        {
            int minutes = legacyRepeat & 0b011111111111; // first 11 bits represent minute interval
            mode = 2;
            data = minutes;
        }
        // yearly repeat (9th bit)
        else if ((legacyRepeat & (1<<8)) == 0b100000000)
        {
            mode = 3;
            data = 1;                                    // old recurrence format did not support intervals
        }                                                // other than 1
        // shouldRepeat on daily interval (8th bit)
        else if ((legacyRepeat & (1<<7)) == 0b10000000)
        {
            mode = 0;
            data = legacyRepeat & 0b1111111;             //  first 7 bits represent day interval
        }
        // day-of-week shouldRepeat
        else
        {
            int weekdays = legacyRepeat & 0b1111111;     // first 7 bits represent days of the week
            int sunday   = weekdays & 0b1;               // first bit represents Sunday
            mode = 4;
            data = (weekdays>>1) | (sunday<<6);          // new recurrence format uses Monday as the start of the week
        }                                                // so adjustments must be made
        this.recurrence = mode | (data<<3);
        return this;
    }

    /**
     * parses out shouldRepeat information for the 'interval' edit/create option
     * @param arg interval user-input
     * @return shouldRepeat bitset
     */
    public static int parseInterval(String arg)
    {
        int mode = 0, data = 0;
        if(arg.matches("\\d+([ ]?m(in(utes)?)?)"))
        {
            mode = 2;
            data = Integer.parseInt(arg.replaceAll("[^\\d]",""));
        }
        else if(arg.matches("\\d+([ ]?h(our(s)?)?)"))
        {
            mode = 2;
            data = Integer.parseInt(arg.replaceAll("[^\\d]",""))*60;
        }
        else if(arg.matches("\\d+([ ]?d(ay(s)?)?)?"))
        {
            mode = 0;
            data = Integer.parseInt(arg.replaceAll("[^\\d]",""));
        }
        else if(arg.matches("\\d+([ ]?m(onth(s)?)?)"))
        {
            mode = 6;
            data = Integer.parseInt(arg.replaceAll("[^\\d]",""))<<5;
        }
        else if(arg.matches("\\d+([ ]?y(ear(s)?)?)"))
        {
            mode = 3;
            data = Integer.parseInt(arg.replaceAll("[^\\d]",""));
        }
        return mode | (data<<3);
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
            mode = 4;
            data = 0b1111111;
        }
        else if(input.matches("weekly"))
        {
            mode = 4;
            data = 1<<7;
        }
        else if(input.matches("month(ly)?"))
        {
            mode = 3;
            data = 1<<5;
        }
        else if(input.matches("year(ly)?"))
        {
            mode = 3;
            data = 1;
        }
        else
        {
            mode = 4;
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
        }
        return mode | (data<<3);
    }

    /**
     * Generated a string describing the shouldRepeat settings of an event
     * @param isNarrow (boolean) for use with narrow style events
     * @return string
     */
    public String toString(boolean isNarrow)
    {
        // useful string constants
        String[] spellout = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
        String[] prefixes = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};

        StringBuilder str = new StringBuilder();
        int mode = recurrence & 0b111;
        int data = recurrence >> 3;
        if(isNarrow)
        {
            // not shouldRepeat
            if (recurrence == 0)
                return "once";
            // shouldRepeat daily
            if ((mode == 4 && data == 0b1111111) || (mode==0 && data==1))
                return "every day";
            // shouldRepeat on interval days
            if (mode == 0)
                return "every "+(data>spellout.length ? data : spellout[data-1])+" days";

            // shouldRepeat x minutes
            if (mode == 2)
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
            // yearly shouldRepeat
            if (mode == 3 && data == 1)
                return "every year";

            // monthly on weekday
            if (mode==5)
            {
                int monthInterval = data>>6;
                if (monthInterval>1)
                {
                    return "every"+(monthInterval>spellout.length ? data : spellout[monthInterval]) + " months";
                }
                else
                {
                    return "every month";
                }
            }
            // monthly on day of month
            if (mode==6)
            {
                int monthInterval = data>>5;
                if (monthInterval>1)
                    return "every"+(monthInterval>spellout.length ? data : spellout[monthInterval]) + " months";
                else
                    return "every month";
            }
        }
        else
        {
            // no shouldRepeat
            if (recurrence == 0)
                return "does not repeat";
            // shouldRepeat daily
            if ((mode == 4 && data == 0b1111111) || (mode==0 && data==1))
                return "repeats daily";
            // shouldRepeat on interval
            if (mode==0)
                return "repeats every "+(data>spellout.length ? data : spellout[data-1])+" days";

            // shouldRepeat x minutes
            if (mode==2)
            {
                if (data%60 == 0)
                {
                    int hours = data/60;
                    if (data == 60) return "repeats hourly";
                    else return "repeats every "+(hours<=spellout.length ? spellout[hours-1]:hours)+" hours";
                }
                else
                {
                    return "repeats every "+data+" minutes";
                }
            }
            // yearly shouldRepeat
            if (mode==3 && data==1)
                return "repeats yearly";

            // monthly on weekday
            if (mode==5)
            {
                DayOfWeek dayOfWeek = DayOfWeek.of(data&0b111);
                int nth = (data>>3)&0b111;
                int monthInterval = data>>6;
                if (monthInterval>1)
                {
                    return nth+prefixes[nth]+" "+dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())+
                            " of every "+monthInterval+prefixes[monthInterval]+" months";
                }
                else
                {
                    return nth+prefixes[nth]+" "+dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())+
                            " of every month";
                }
            }
            // monthly on day of month
            if (mode==6)
            {
                int dayOfMonth    = data&0b11111;
                int monthInterval = data>>5;
                if (monthInterval>1)
                {
                    return "repeats every"+(monthInterval>spellout.length ? data : spellout[monthInterval-1]) +
                            " months"+(dayOfMonth>0 ? " on the "+dayOfMonth+prefixes[dayOfMonth%10]:"");
                } else
                {
                    return "repeats every month"+(dayOfMonth>0 ? " on the "+dayOfMonth+prefixes[dayOfMonth%10]:"");
                }
            }
        }

        // weekday shouldRepeat
        if (mode==4)
        {
            int weeks = data>>7;
            data &= 0b1111111;
            if (weeks>1)
            {
                if(isNarrow) str = new StringBuilder("every "+(weeks>spellout.length ? weeks+"":spellout[weeks-1])+" weeks on ");
                else str = new StringBuilder("repeats every "+(weeks>spellout.length ? weeks+"":spellout[weeks-1])+" weeks on ");
            }
            else
            {
                if(isNarrow) str = new StringBuilder("weekly on ");
                else str = new StringBuilder("repeats weekly on ");
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
                return this.expire.isBefore(now);
            return true;
        }
    }

    /**
     * determine the next start time for the event
     * @param date the date of the (last) start of the event
     * @return the new start of the event (based on the recurrence rule)
     */
    public ZonedDateTime next(ZonedDateTime date)
    {
        if (this.recurrence == 0) return date;

        /// determine mode
        int mode = this.recurrence & 0b111;   // separate out mode
        int data  = this.recurrence >>3;       // shift off mode bits
        switch(mode)
        {
            // interval shouldRepeat
            case 0:
                return date.plusDays(data);
            case 2:
                return date.plusMinutes(data);
            case 3:
                return date.plusYears(data);

            // shouldRepeat weekly by day of week
            case 4:
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

            // shouldRepeat on nth week day every mth month
            case 5:
                DayOfWeek dayOfWeek = DayOfWeek.of(data&0b111);
                int nth = (data>>3)&0b111;
                date = date.with(firstDayOfMonth()).plusMonths((data>>6)>1 ? (data>>6):1).with(nextOrSame(dayOfWeek));
                while(nth>1)
                {
                   date = date.plusDays(1).with(nextOrSame(dayOfWeek));
                   nth--;
                }
                return date;

            // shouldRepeat on n day of every mth month
            case 6:
                int dayOfMonth    = data&0b11111;
                int monthInterval = data>>5;
                if (dayOfMonth > 0) return date.plusMonths(monthInterval).withDayOfMonth(dayOfMonth);
                else return date.plusMonths(monthInterval);

            // something went wrong
            default:
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
            // daily interval shouldRepeat
            case 0:
                rule += "FREQ=DAILY;INTERVAL="+data+";";
                break;
            // yearly shouldRepeat
            case 3:
                rule += "FREQ=YEARLY;INTERVAL="+data+";";
                break;
            // shouldRepeat by weekday
            case 4:
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
            // shouldRepeat on nth week day every mth month
            case 5:
                DayOfWeek dayOfWeek = DayOfWeek.of(data&0b111);
                int nth = (data>>3)&0b111;
                rule += "FREQ=MONTHLY;BYDAY="+nth+dayOfWeek+";INTERVAL="+(data>>6==0 ? 1:data>>6)+";";
                break;
            // shouldRepeat on n day of every mth month
            case 6:
                int dayOfMonth = data&0b11111;
                rule += "FREQ=MONTHLY;BYMONTHDAY="+dayOfMonth+";INTERVAL="+(data>>5==0 ? 1:data>>5)+";";
                break;
        }
        if (expire != null)
        {
            rule += "UNTIL="+
                    String.format("%04d%02d%02d",expire.getYear(),expire.getMonthValue(),expire.getDayOfMonth()) +
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
        if (count==null || startDate==null) return -1; // error
        if (now.isBefore(startDate) || now.equals(startDate)) return count;  // no events have occurred yet

        // determine remaining event occurrences
        int mode = this.recurrence & 0b111;
        int data = this.recurrence >> 3;
        switch (mode)
        {
            case 0:     // daily
                long days = startDate.until(now, ChronoUnit.DAYS);
                return count - ((int) days/(data==0?1:data));
            case 2:     // minutely
                long minutes = startDate.until(now, ChronoUnit.MINUTES);
                return count - ((int) minutes/(data==0?1:data));
            case 3:     // yearly
                long years = startDate.until(now, ChronoUnit.YEARS);
                return count - ((int) years/(data==0?1:data));
            case 4:     // weekly
                int weeks = (int) (startDate.until(now, ChronoUnit.WEEKS)/((data>>7)==0?1:(data>>7)));

                // calculate the number of days of the week the event repeats on
                int c = 0;                      // count of weekdays event repeats on
                for (int tmp=(data&0b1111111); tmp>0; tmp>>=1) { if ((tmp&0b1)==1) c++; }
                Logging.info(this.getClass(), "c="+c);
                int rem = count - c*weeks;      // how many events have past
                Logging.info(this.getClass(), "rem="+c);

                // process the remaining portion of the week
                ZonedDateTime cur = startDate.plusWeeks(weeks);
                for (int j=cur.getDayOfWeek().getValue(); j<=7 && now.isAfter(cur); j++)
                {   // for each day of last remaining week
                    // if day of week is set, decrement rem count
                    if (((data&0b1111111) & (1<<(j-1))) == 1) rem--;
                    cur = cur.plusDays(1);
                }
                return rem;
            case 5:     // mo by day
                long months = startDate.until(now, ChronoUnit.MONTHS);
                return count - ((int) months/((data>>6)==0?1:(data>>5)));
            case 6:     // mo by date
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
