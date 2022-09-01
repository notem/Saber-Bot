package ws.nmathe.saber.utils;

import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.core.schedule.ScheduleEntry;

import java.awt.*;
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
            String[] split = emoji.split(":"); // split on colons to isolate the reaction name from it's ID
            String emoteId = split[split.length-1].replaceAll("[^\\d]", ""); // trim to include only the ID
            //Emote emote = null;
            Emoji emote = null;
            try
            {
                for(JDA jda : Main.getShardManager().getShards())
                {
                    emote = jda.getEmojiById(emoteId);
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
        String regex = "START([+-](\\d+([MHD])?)?)?|END([-](\\d+([MHD])?)?)?";
        return time.matches(regex);
    }


    /*
     *  The verify functions below return string (error messages) rather
     *  than success/fail booleans.
     *  These functions are primarily used by the edit and create command
     *  during their verification stages.
     */


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
                if (!VerifyUtilities.verifyDate(args[index]))
                {
                    return "I could not understand **" + args[index] + "** as a date! Please use the format M/d.";
                }
                if (ParsingUtilities.parseDate(args[index], zone).isBefore(LocalDate.now(zone)))
                {
                    return "That date is in the past!";
                }
        }
        return "";
    }


    /**
     *  Returns error message (or empty string) for several date-based keywords
     */
    public static String verifyDate(String[] args, int index, String head, ScheduleEntry entry, ZoneId zone, boolean checkStarted)
    {
        if(args.length-index < 1)
        {
            return "That's not the right number of arguments for **"+args[index-1]+"**! " +
                    "Use ``"+head+" "+args[0]+" "+args[index-1]+" [date]``";
        }
        if(!VerifyUtilities.verifyDate(args[index]))
        {
            return "I could not understand **" + args[index] + "** as a date! Please use the format 'M/d'.";
        }
        LocalDate date = ParsingUtilities.parseDate(args[index], zone);
        if(date.isBefore(LocalDate.now(zone)))
        {
            return "That date is in the past!";
        }
        if(checkStarted && entry!=null && entry.hasStarted())
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
        index++;    // move to [limit] argument
        if (VerifyUtilities.verifyInteger(args[index]))
        {
            if (Integer.parseInt(args[index]) < 0)
            {
                return "Your limit cannot be a negative value!";
            }
        }
        else if(!args[index].equalsIgnoreCase("off"))
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
        if(args[index].contains("@"))
        {
            String a[] = args[index].split("@");
            if(!VerifyUtilities.verifyDate(a[0]))
            {
                return "*" + a[0] + "* does not look like a date! Be sure to use the format yyyy/mm/dd!";
            }
            if(!VerifyUtilities.verifyTime(a[1]))
            {
                return "*" + a[1] + "* does not look like a time! Be sure to use the format HH:mm!";
            }
        }
        else if(!VerifyUtilities.verifyDate(args[index]))
        {
            if (args[index].equalsIgnoreCase("off") ||
                args[index].equalsIgnoreCase("never") ||
                args[index].equalsIgnoreCase("none"))
            {
                return "";
            }
            return "*" + args[index] + "* does not look like a date! Be sure to use the format yyyy/mm/dd!";
        }
        return "";
    }

    /**
     *  Returns error message (or empty string) for shouldRepeat keyword verification
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
     *  Returns error message (or empty string) for announcement add keyword verification
     */
    public static String verifyAnnouncementAdd(String[] args, int index, String head, EventCompat event)
    {
        if (args.length - index < 3)
        {
            return "That's not the right number of arguments for **" + args[index - 2] +" "+ args[index - 1] + "**!\n" +
                    "Use ``" + head + " " + args[0] + " " + args[index - 2] + " " + args[index - 1] + " [#target] [time] [message]``";
        }
        JDA jda = Main.getShardManager().getJDA(event.getGuild().getId());
        String channelId = args[index].replaceAll("[^\\d]", "");
        if (!channelId.matches("\\d+") || jda.getTextChannelById(channelId)==null)
        {
            return "**" + args[index] + "** is not a channel on your server!";
        }
        if(!VerifyUtilities.verifyTimeString(args[index+1]))
        {
            return "**" + args[index+1] + "** is not a properly formed announcement time!\n" +
                    "Times use the format \"TYPE+/-OFFSET\". Ex: ``START+10m``, ``END-1h``";
        }
        return "";
    }

    /**
     *  Returns error message (or empty string) for announcement remove keyword
     */
    public static String verifyAnnouncementRemove(String[] args, int index, String head, ScheduleEntry se)
    {
        if (args.length - index < 1)
        {
            return "That's not the right number of arguments for **" + args[index - 2] +" "+ args[index - 1] + "**!\n" +
                    "Use ``" + head + " " + args[0] + " " + args[index - 2] + " " + args[index - 1] + "  [number]``";
        }
        if (!verifyInteger(args[index]))
        {
            return "*" + args[index] + "* is not a number!\n" +
                    "Use ``" + head + " " + args[0] + " " +  args[index - 2] + " " + args[index - 1] + " [number]``";
        }
        Integer i = Integer.parseInt(args[index]);
        if (!se.getAnnouncementTimes().keySet().contains((i-1)+"") || i < 1)
        {
            return "There does not exist an announcement with number *" + args[index] + "*!\n" +
                    "Use ``" + head + " " + args[0] + " " + args[index - 2] + " " + args[index - 1] + " [number]``";
        }
        return "";
    }

    /**
     *  Returns error message (or empty string) for comment add verification
     */
    public static String verifyCommentAdd(String[] args, int index, String head)
    {
        if(args.length-index < 1)
        {
            return "That's not enough arguments for *comment add*!\n" +
                    "Use ``"+head+" "+args[0]+" "+args[index-2]+" "+args[index-1]+" \"your comment\"``";
        }
        if(args[index].length() > 1024) return "Comments should not be larger than 1024 characters!";
        return "";
    }

    /**
     *  Returns error message (or empty string) for comment swap verification
     */
    public static String verifyCommentSwap(String[] args, int index, String head, ScheduleEntry entry)
    {
        if(args.length-index < 2)
        {
            return "That's not enough arguments for *comment swap*!" +
                    "\nUse ``"+ head +" "+args[0]+" "+args[index-2]+" "+args[index-1]+" [number] [number]``";
        }
        if(!VerifyUtilities.verifyInteger(args[index]))
        {
            return "Argument **" + args[index] + "** is not a number!";
        }
        if(Integer.parseInt(args[index]) > entry.getComments().size() || Integer.parseInt(args[index]) < 1)
        {
            return "Comment **#" + args[index] + "** does not exist!";
        }
        index++;
        if(!VerifyUtilities.verifyInteger(args[index]))
        {
            return "Argument **" + args[index] + "** is not a number!";
        }
        if(Integer.parseInt(args[index]) > entry.getComments().size() || Integer.parseInt(args[index]) < 1)
        {
            return "Comment **#" + args[index] + "** does not exist!";
        }
        return "";
    }

    /**
     *  Returns error message (or empty string) for comment remove verification
     */
    public static String verifyCommentRemove(String[] args, int index, String head, ScheduleEntry entry)
    {
        if(args.length-index < 1)
        {
            return "That's not enough arguments for *comment remove*!\n" +
                    "Use ``"+head+" "+args[0]+" "+args[index-2]+" "+args[index-1]+" [number]``";
        }
        if((!args[index].isEmpty() && Character.isDigit(args[index].charAt(0)))
                && !VerifyUtilities.verifyInteger(args[index]))
        {
            return "I cannot use **" + args[index] + "** to remove a comment!";
        }
        if(VerifyUtilities.verifyInteger(args[index]))
        {
            Integer it = Integer.parseInt(args[index]);
            if(it > entry.getComments().size())
            {
                return "The event doesn't have a comment number " + it + "!";
            }
            if(it < 1)
            {
                return "The comment number must be above 0!";
            }
        }
        return "";
    }

    /**
     * Returns error message (or empty string) for event count verification
     */
    public static String verifyCount(String[] args, int index, String head)
    {
        if (args.length-index < 1)
        {
            return "That's not enough arguments for *count*!\n" +
                    "Use ``"+head+" "+args[0]+" "+args[index-1]+" "+" [number]``";
        }
        else if(args[index].equalsIgnoreCase("off"))
        {
            return "";
        }
        else if (!args[index].matches("[\\d]+"))
        {
            return "**" + args[index-1] + "** does not look like a valid number!";
        }
        else if (Integer.parseInt(args[index])<=0)
        {
            return "The *count* must be non-zero!";
        }
        return "";
    }

    /**
     * Evaluates correctness of a location string
     */
    public static String verifyLocation(String[] args, int index, String head)
    {
        if (args.length-index < 1)
        {
            return "That is not enough arguments for *location*!\n" +
                    "Use ``"+head+" .. "+args[index-1]+" [location]``";
        }
        else if (args[index].length() > 100)
        {
            return "Your location name is to many characters long!\n" +
                    "Please keep the name length to under 100 characters.";
        }
        return "";
    }

    /**
     * determines if the arguments provided for the ``color`` keyword are valid
     */
    public static String verifyColor(String[] args, int index, String head)
    {
        if (args.length-index < 1)
        {
            return "That's not the right number of arguments!\n" +
                    "Use ``" + head + " " + args[0] + " " + args[index-1] + " [color_code]";
        }
        if (!args[index].matches("0x.+||off||null||none"))
        {
            return "The color you wish to use should be in a hexadecimal form!\n" +
                    "It should look something like \"0xF4442E\".";
        }
        try
        {
            Color.decode(args[index]);
        }
        catch(Exception e)
        {
            return "I was unable to decode your color code value!";
        }
        return "";
    }

    /**
     *
     * @param text text of unknown format
     * @return true if is of proper <@\\d+> format
     */
    public static boolean verifyUserMention(String text)
    {
        return text.matches("<@(!)?\\d+>");
    }
}
