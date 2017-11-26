package ws.nmathe.saber.core.schedule;

import ws.nmathe.saber.utils.Logging;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
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
    public EventRecurrence()
    {
        this.recurrence  = 0;
        this.count       = null;
        this.startDate   = null;
        this.expire      = null;
    }

    public EventRecurrence(int recurrence)
    {
        this.recurrence  = recurrence;
        this.count       = null;
        this.startDate   = null;
        this.expire      = null;
    }

    /**
     * parses a recurrence string that is formatted in accordance
     * with the RFC5545 specifications for calendar event recurrence
     * NOTE: this follows Google Calendar's implementation of the ruleset
     * @param rfc5545 string containing required information
     */
    public EventRecurrence(List<String> rfc5545)
    {
        this.recurrence  = 0;
        this.count       = null;
        this.startDate   = null;
        this.expire      = null;

        // attempt to parse the ruleset
        int mode = 0, data = 0;
        for(String rule : rfc5545)
        {
            Logging.warn(this.getClass(), rule);
            if(rule.startsWith("RRULE") && rule.contains("FREQ"))
            {
                // parse out the frequency of recurrence
                String tmp = rule.split("FREQ=")[1].split(";")[0];
                switch (tmp)
                {
                    case "DAILY":
                        mode = 0;
                        if (rule.contains("INTERVAL"))
                            data = Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]);
                        else
                            data = 0b1111111;
                        break;
                    case "WEEKLY":
                        mode = 4;
                        if (rule.contains("BYDAY"))
                            data |= EventRecurrence.parseRepeat(rule.split("BYDAY=")[1].split(";")[0]);
                        if (rule.contains("INTERVAL"))
                            data |= Integer.valueOf(rule.split("INTERVAL=")[1].split(";")[0]) << 7;
                        else
                            data |= 1;
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
     * @param legacyRepeat old repeat representation
     * @return the updated event recurrence object
     */
    public EventRecurrence fromLegacy(Integer legacyRepeat)
    {
        int mode, data;
        // minute repeat (12th bit)
        if ((legacyRepeat & (1<<11)) == 0b100000000000)
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
        // repeat on daily interval (8th bit)
        else if ((legacyRepeat & (1<<7)) == 0b10000000)
        {
            mode = 0;
            data = legacyRepeat & 0b1111111;             //  first 7 bits represent day interval
        }
        // day-of-week repeat
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
     * parses out repeat information for the 'interval' edit/create option
     * @param arg interval user-input
     * @return repeat bitset
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
     * Parses out the intended event repeat information from user input
     * @param input (String) the user input
     * @return (int) an integer representing the repeat information (stored in binary)
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
     * Generated a string describing the repeat settings of an event
     * @param isNarrow (boolean) for use with narrow style events
     * @return string
     */
    public String toString(boolean isNarrow)
    {
        // useful string constants
        String[] spellout = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
        String[] prefixes = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};

        StringBuilder str = new StringBuilder();
        int mode = recurrence&0b111;
        int data = recurrence >> 3;
        Logging.info(this.getClass(), "mode: "+mode+" | data: "+data);
        if(isNarrow)
        {
            // not repeat
            if (recurrence == 0)
                return "once";
            // repeat daily
            if (mode == 4 && data == 0b1111111)
                return "every day";
            // repeat on interval days
            if (mode == 0)
                return "every "+(data>spellout.length ? data : spellout[data-1])+" days";

            // repeat x minutes
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
            // yearly repeat
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
            // no repeat
            if (recurrence == 0)
                return "does not repeat";
            // repeat daily
            if (mode==4 && data == 0b1111111)
                return "repeats daily";
            // repeat on interval
            if (mode==0)
                return "repeats every "+(data>spellout.length ? data : spellout[data-1])+" days";

            // repeat x minutes
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
            // yearly repeat
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

        // weekday repeat
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
                else str = new StringBuilder("weekly on ");
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
     * @return true if the event should repeat
     */
    public boolean repeat()
    {
        return this.recurrence != 0 &&
                !(this.count != null && this.count <= 0) &&
                (this.expire==null || this.expire.isBefore(ZonedDateTime.now()));
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
            // interval repeat
            case 0:
                return date.plusDays(data);
            case 2:
                return date.plusMinutes(data);
            case 3:
                return date.plusYears(data);

            // repeat weekly by day of week
            case 4:
                int day   = 1<<(date.getDayOfWeek().getValue()-1);  // represent current day of week as int
                int weeks = (data>>7)==0 ? 1:data>>7;               // number of weeks to repeat
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

            // repeat on n day of every mth month
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
        int mode    = this.recurrence & 0b111;
        int data = this.recurrence >> 3;
        switch (mode)
        {
            // daily interval repeat
            case 0:
                rule += "FREQ=DAILY;INTERVAL="+data+";";
                break;
            // yearly repeat
            case 3:
                rule += "FREQ=YEARLY;INTERVAL="+data+";";
                break;
            // repeat by weekday
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
            // repeat on nth week day every mth month
            case 5:
                DayOfWeek dayOfWeek = DayOfWeek.of(data&0b111);
                int nth = (data>>3)&0b111;
                rule += "FREQ=MONTHLY;BYDAY="+nth+dayOfWeek+";INTERVAL="+(data>>6==0 ? 1:data>>6)+";";
                break;
            // repeat on n day of every mth month
            case 6:
                int dayOfMonth    = data&0b11111;
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
}
