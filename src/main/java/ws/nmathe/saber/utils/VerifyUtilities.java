package ws.nmathe.saber.utils;

import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.schedule.ScheduleEntry;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * static methods used to verify user input for the verify() method of commands
 * more often than not methods here have partner methods in ParsingUtilities
 * (ie. verifyTime() should be used to OK input for the parseTime())
 */
public class VerifyUtilities
{

    /**
     * verify that user input is valid for use by ParsingUtilities.parseTime()
     * @param userInput the user provided time string
     * @return true if valid
     */
    public static boolean verifyTime(String userInput)
    {
        Matcher matcher = Pattern.compile("\\d+").matcher(userInput);

        // relative time
        if(userInput.matches("\\d+[mM][iI][nN]$"))
        {
            return true;
        }
        else if(userInput.matches("\\d(\\d)?(:\\d\\d)?([aApP][mM])"))// absolute time with period indicator
        {
            if(userInput.contains(":"))
            {
                if(!matcher.find()) return false;
                if(Integer.parseInt(matcher.group()) > 12) return false;
                if(!matcher.find()) return false;
                if(Integer.parseInt(matcher.group()) > 59) return false;
            }
            else
            {
                if(!matcher.find()) return false;
                if(Integer.parseInt(matcher.group()) > 12) return false;
            }
        }
        else if(userInput.matches("\\d(\\d)?(:\\d\\d)?")) // absolute time with no period indicator
        {
            if(userInput.contains(":"))
            {
                if(userInput.equalsIgnoreCase("24:00")) return true;

                if(!matcher.find()) return false;
                if(Integer.parseInt(matcher.group()) > 23) return false;
                if(!matcher.find()) return false;
                if(Integer.parseInt(matcher.group()) > 59) return false;
            }
            else
            {
                if(!matcher.find()) return false;
                if(Integer.parseInt(matcher.group()) > 23) return false;
            }
        }
        else // fails to match either absolute or relative times
        {
            return false;
        }
        return true;
    }


    /**
     * verify that a date string is properly formed
     * @param arg user-supplied date string
     * @return true if valid
     */
    public static boolean verifyDate( String arg )
    {
        if(arg.toLowerCase().equals("tomorrow") || arg.toLowerCase().equals("today"))
            return true;

        try
        {
            String[] splt = arg.split("[^0-9]+");
            if(splt.length == 1)
            {
                LocalDate.of(LocalDate.now().getYear(), LocalDate.now().getMonth(), Integer.parseInt(splt[0]));
            }
            else if(splt.length == 2)
            {
                LocalDate.of(LocalDate.now().getYear(), Integer.parseInt(splt[0]), Integer.parseInt(splt[1]));
            }
            else if(splt.length == 3)
            {
                LocalDate.of(Integer.parseInt(splt[0]), Integer.parseInt(splt[1]), Integer.parseInt(splt[2]));
            }
            else
            {
                return false;
            }
            return true;
        }
        catch(Exception e)
        {
            return false;
        }
    }


    /**
     * verify that an encoded entry ID string is valid
     * @param arg user-supplied encoded string (Character.RADIX_MAX)
     * @return true if valid
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static boolean verifyEntryID(String arg)
    {
        try
        {
            Integer.parseInt(arg, Character.MAX_RADIX);
        }
        catch(Exception e)
        {
            return false;
        }

        return true;
    }


    /**
     * verify that a string may be parsed into an integer
     * @param arg user-supplied integer
     * @return true if valid
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static boolean verifyInteger(String arg)
    {
        try
        {
            Integer.parseInt(arg);
        }
        catch (Exception e)
        {
            return false;
        }
        return true;
    }


    /**
     * verify that an url is valid and is reachable
     * @param arg user-supplied url
     * @return true if valid
     */
    public static boolean verifyUrl(String arg)
    {
        try
        {
            (new URL(arg)).openConnection().connect();
        }
        catch(Exception e)
        {
            return false;
        }
        return true;
    }


    /**
     * Verify that a provided Zone string is valid
     * @param arg Zone string
     * @return true if valid
     */
    public static boolean verifyZone(String arg)
    {
        for(String validZone : ZoneId.getAvailableZoneIds())
        {
            if(validZone.equalsIgnoreCase(arg))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * verify that a supplied emoji string is a valid discord emoji
     * @param emoji emoji string (either raw emoji char or discord emoji ID)
     * @return true if valid
     */
    public static boolean verifyEmoji(String emoji)
    {
        if(!EmojiManager.isEmoji(emoji))
        {
            String emoteId = emoji.replaceAll("[^\\d]", "");
            Emote emote = null;
            try
            {
                for(JDA jda : Main.getShardManager().getShards())
                {
                    emote = jda.getEmoteById(emoteId);
                    if(emote != null) break;
                }
            }
            catch(Exception e)
            {
                return false;
            }
            if(emote == null)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Verify that an announcement override time string is properly formed
     * @param time 'time string'
     * @return boolean, true if valid
     */
    public static boolean verifyTimeString(String time)
    {
        time = time.toUpperCase();  // all caps
        String regex = "(START|END)([+-](\\d+([MHD])?)?)?";
        return time.matches(regex);
    }


    /*
     *  The verify functions below return string (error messages) rather
     *  than success/fail booleans.
     *  These functions are primarily used by the edit and create command
     *  during their verification stages.
     */


    /**
     *  Returns error message (or empty string) for interval keyword verification
     */
    public static String verifyInterval(String[] args, int index, String head)
    {
        if(args.length-index < 1)
        {
            return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                    "Use ``"+head+" "+args[0]+" "+args[index-1]+" [number]``";
        }
        if(!(args[index].matches("\\d+(([ ]?d(ay(s)?)?)?||([ ]?m(in(utes)?)?)||([ ]?h(our(s)?)?))")))
        {
            return "**" + args[index] + "** is not a correct interval format!\n" +
                    "For days, use ``\"n days\"``. For hours, use ``\"n hours\"``. For minutes, use ``\"n minutes\"``.";
        }
        if(args[index].matches("\\d+([ ]?d(ay(s)?)?)?"))
        {
            Integer d = Integer.parseInt(args[index].replaceAll("[^\\d]", ""));
            if(d < 0 || d > 9000) return "Invalid number of days!";
        }
        else if(args[index].matches("\\d+([ ]?m(in(utes)?)?)"))
        {
            Integer d = Integer.parseInt(args[index].replaceAll("[^\\d]", ""));
            if(d > 100000) return "That's too many minutes!";
            if(d < 10) return "Your minute interval must be greater than or equal to 10 minutes!";
        }
        else if(args[index].matches("\\d+([ ]?h(our(s)?)?)"))
        {
            Integer d = Integer.parseInt(args[index].replaceAll("[^\\d]", ""));
            if(d < 0 || d > 1000) return "That's too many hours!";
        }
        return "";
    }


    /**
     *  Returns error message (or empty string) for interval url, image, and thumbnail verification
     */
    public static String verifyUrl(String[] args, int index, String head)
    {
        if(args.length-index < 1)
        {
            return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                    "Use ``"+head+" "+args[0]+" "+args[index-1]+" [url]``";
        }
        switch(args[index])
        {
            case "off":
            case "null":
                break;

            default:
                if (!VerifyUtilities.verifyUrl(args[index]))
                {
                    return "**" + args[index] + "** doesn't look like a url to me! Please include the ``http://`` portion of the url!";
                }
        }
        return "";
    }


    /**
     *  Returns error message (or empty string) for expire keyword verification
     */
    public static String verifyExpire(String[] args, int index, String head, ZoneId zone)
    {
        if(args.length-index < 1)
        {
            return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                    "Use ``"+head+" "+args[0]+" "+args[index-1]+" [date]``";
        }
        switch(args[index])
        {
            case "none":
            case "never":
            case "null":
                return "";

            default:
                if(!VerifyUtilities.verifyDate( args[index] ))
                {
                    return "I could not understand **" + args[index] + "** as a date! Please use the format M/d.";
                }
                if(ParsingUtilities.parseDate(args[index], zone).isBefore(ZonedDateTime.now()))
                {
                    return "That date is in the past!";
                }
        }
        return "";
    }


    /**
     *  Returns error message (or empty string) for several date-based keywords
     */
    public static String verifyDate(String[] args, int index, String head, ScheduleEntry entry, ZoneId zone)
    {
        if(args.length-index < 1)
        {
            return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                    "Use ``"+head+" "+args[0]+" "+args[index-1]+" [date]``";
        }
        if(!VerifyUtilities.verifyDate(args[index]))
        {
            return "I could not understand **" + args[index] + "** as a date! Please use the format M/d.";
        }
        ZonedDateTime time = ParsingUtilities.parseDate(args[index], zone);
        if(time.isBefore(ZonedDateTime.now()))
        {
            return "That date is in the past!";
        }
        if(entry!=null && entry.hasStarted())
        {
            return "You cannot modify the date of events which have already started!";
        }
        return "";
    }

    /**
     *  Returns error message (or empty string) for limit keyword verification
     */
    public static String verifyLimit(String[] args, int index, String head, ScheduleEntry entry)
    {
        if(args.length-index < 2)
        {
            return "That's not the right number of arguments for **"+args[index-1]+"**!\n" +
                    "Use ``"+ head + " "+args[0]+" "+args[index-1]+" [group] [limit]`` where ``[group]`` " +
                    "is the name of the rsvp group you wish to limit and ``[limit]`` maximum number of participants to allow.";
        }
        if(!Main.getScheduleManager().getRSVPOptions(entry.getChannelId()).containsValue(args[index]))
        {
            return "*" + args[index] + "* is not an rsvp group for that event's schedule!";
        }
        index++;
        if(!args[index].equalsIgnoreCase("off") && !VerifyUtilities.verifyInteger(args[index]))
        {
            return "*" + args[index] + "* is not a number!\nTo disable the rsvp group's limit use \"off\".";
        }
        return "";
    }

    /**
     *  Returns error message (or empty string) for deadline keyword verification
     */
    public static String verifyDeadline(String[] args, int index, String head)
    {
        if(args.length-index < 1)
        {
            return "That's not the right number of arguments for **"+args[index-1]+"**!\n" +
                    "Use ``"+ head +" "+ args[0] +" "+ args[index-1]+" [deadline]`` where ``[deadline]`` is " +
                    "the last day to RSVP for the event in a yyyy/mm/dd format.";
        }
        if(!VerifyUtilities.verifyDate(args[index]))
        {
            return "*" + args[index] + "* does not look like a date! Be sure to use the format yyyy/mm/dd!";
        }
        return "";
    }

    /**
     *  Returns error message (or empty string) for repeat keyword verification
     */
    public static String verifyRepeat(String[] args, int index, String head)
    {
        if (args.length - index < 1)
        {
            return "That's not the right number of arguments for **" + args[index - 1] + "**! " +
                    "Use ``" + head + " " + args[0] + " " + args[index - 1] + " [repeat]``";
        }
        return "";
    }

    /**
     *  Returns error message (or empty string) for announcement keyword verification
     */
    public static String verifyAnnouncementTime(String[] args, int index, String head, MessageReceivedEvent event)
    {
        if (args.length - index < 4)
        {
            return "That's not the right number of arguments for **" + args[index - 1] + "**! " +
                    "Use ``" + head + " " + args[0] + " " + args[index - 1] + " [add|remove] [#target] [time] [message]``";
        }
        switch(args[index++].toLowerCase())
        {
            case "a":
            case "add":
                JDA jda = Main.getShardManager().getJDA(event.getGuild().getId());
                String channelId = args[index].replaceAll("[^\\d]", "");
                if (!channelId.matches("\\d+") || jda.getTextChannelById(channelId)==null)
                {
                    return "**" + args[index] + "** is not a channel on your server!";
                }
                if(!VerifyUtilities.verifyTimeString(args[index+1]))
                {
                    return "**" + args[index+1] + "** is not a properly formed announcement time!\n" +
                            "Times use the format \"TYPE+/-OFFSET\". Ex: ``START-1h``, ``END-5m``";
                }
                break;

            case "r":
            case "remove":
                break;

            default:
                return "**" + args[index] + "** is not a valid option!\n" +
                        "You should use either *add* or *remove*!";
        }
        return "";
    }
}
