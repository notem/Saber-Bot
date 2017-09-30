package ws.nmathe.saber.utils;

import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Emote;
import ws.nmathe.saber.Main;

import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
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
}
