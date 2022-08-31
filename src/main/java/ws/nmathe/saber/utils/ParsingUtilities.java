package ws.nmathe.saber.utils;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import org.apache.commons.lang3.StringUtils;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.core.schedule.ScheduleEntry;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * utilities which do some sort of string parsing
 */
public class ParsingUtilities
{
    /**
     * parses a local time string inputted by a user into a ZonedDateTime object
     * @param userInput the local time
     * @return new ZonedDateTime with the new time
     */
    public static LocalTime parseTime(String userInput, ZoneId zone)
    {
        // use the system zone if zoneId is null
        zone = (zone == null) ? ZoneId.systemDefault() : zone;

        LocalTime time = LocalTime.now(zone);

        // relative time
        if (userInput.toLowerCase().matches(".+min(ute)?(s)?$"))
        {
            Long minutes = Long.parseLong(userInput.replaceAll("[^\\d]", ""));
            time = time.plusMinutes(minutes);
        }

        // absolute time
        // special case (24:00 == 00:00)
        else if(userInput.equals("24:00") || userInput.equals("24"))
        {
            time = LocalTime.MAX;
        }
        // 1:00pm or 13:00
        else if(userInput.contains(":"))
        {
            DateTimeFormatter format = userInput.toLowerCase().matches(".+([ap][m])") ?
                    DateTimeFormatter.ofPattern("h:mma") : DateTimeFormatter.ofPattern("H:mm");
            time = LocalTime.parse(userInput.toUpperCase(), format);
        }
        else // 1pm or 13
        {
            DateTimeFormatter format = userInput.toLowerCase().matches(".+([ap][m])") ?
                    DateTimeFormatter.ofPattern("ha") : DateTimeFormatter.ofPattern("H");
            time = LocalTime.parse(userInput.toUpperCase(), format);
        }

        return time;
    }

    /**
     * TODO: further revise
     * @param raw the base string to parse into a message
     * @param entry the entry associated with the message
     * @param firstPass boolean used to prevent message parsing loops
     * @return a new message which has entry specific information inserted into the format string
     */
    public static String processText(String raw, ScheduleEntry entry, boolean firstPass)
    {
        // determine time formatter from schedule settings
        String clock = Main.getScheduleManager().getClockFormat(entry.getChannelId());
        DateTimeFormatter timeFormatter;
        if(clock.equalsIgnoreCase("12"))
             timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
        else
             timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        /*
         * function handles the insertion of the '[..]' text for advanced substitution
         */
        BiFunction<String, Matcher, String> helper = (String insert, Matcher matcher) -> {
            String str = "";
            if(matcher.find())
                str += matcher.group().replaceAll("[\\[\\]]", "");
            str += insert;
            if(matcher.find())
                str += matcher.group().replaceAll("[\\[\\]]", "");
            return str;
        };

        // advanced parsing
        /*
         * parses the format string using regex grouping
         * allows for an 'if element exists, print string + element + string' type of insertion
         */
        int count = 0;
        Matcher matcher = Pattern.compile("%\\{(.*?)}").matcher(raw);
        while (matcher.find())
        {
            if (count++ > 30)
            {
                Logging.warn(ParsingUtilities.class, "Reached loop limit in processText()!");
                break; // protection against endless loop?
            }

            String group = matcher.group();
            String trimmed = group.substring(2, group.length()-1);
            StringBuilder sub = new StringBuilder();
            Matcher matcher2 = Pattern.compile("\\[.*?]").matcher(trimmed);
            if(!trimmed.isEmpty())
            {
                // the nth comment
                // unlike the legacy insertion tokens, comment numbers greater than 9 are supported
                if(trimmed.matches("(\\[.*?])?comment \\d+(\\[.*?])?") && firstPass)
                {
                    int i = Integer.parseInt(trimmed.replaceAll("(\\[.*?])?comment |\\[.*?]", ""));
                    if (entry.getComments().size() >= i && i > 0)
                    {
                        String preprocessed = helper.apply(entry.getComments().get(i - 1), matcher2);
                        sub.append(processText(preprocessed, entry, false));
                    }
                }

                // inserts the (date)time for the end of the event
                // using the provided datetime formatter string
                else if(trimmed.matches("(\\[.*?])?start .+(\\[.*?])?")) // advanced end
                {
                    String formatter = trimmed
                            .replaceAll("start ","")
                            .replaceAll("\\[.*?]","")
                            .trim();
                    String startString = "";

                    ZoneId zone = entry.getStart().getZone();
                    for (String token : trimmed.replaceAll("start ","").split(" "))
                    {
                        if (ZoneId.getAvailableZoneIds().contains(token))
                        {
                            zone = ZoneId.of(token);
                            formatter = formatter
                                    .replaceAll(token+"( )?", "");
                        }
                    }
                    try {
                        startString = entry.getStart()
                                .withZoneSameInstant(zone)
                                .format(DateTimeFormatter.ofPattern(formatter));
                    } catch(Exception ignored) {}
                    sub.append(helper.apply(startString, matcher2));
                }

                // inserts the (date)time for the end of the event
                // using the provided datetime formatter string
                else if(trimmed.matches("(\\[.*?])?end .+(\\[.*?])?")) // advanced end
                {
                    String formatter = trimmed.replaceAll("end ","")
                            .replaceAll("\\[.*?]","");
                    String endString = "";

                    ZoneId zone = entry.getEnd().getZone();
                    for (String token : trimmed.replaceAll("end ","").split(" "))
                    {
                        if (ZoneId.getAvailableZoneIds().contains(token))
                        {
                            zone = ZoneId.of(token);
                            formatter = formatter
                                    .replaceAll(token+"( )?", "");
                        }
                    }
                    try {
                        endString = entry.getEnd()
                                .withZoneSameInstant(zone)
                                .format(DateTimeFormatter.ofPattern(formatter));
                    } catch(Exception ignored) {}
                    sub.append(helper.apply(endString, matcher2));
                }

                // inserts the current (date)time using the provided datetime formatter string
                else if(trimmed.matches("(\\[.*?])?now .+(\\[.*?])?"))
                {
                    String formatter = trimmed.replaceAll("now ","")
                            .replaceAll("\\[.*?]","");
                    String nowString = "";

                    ZoneId zone = entry.getStart().getZone();
                    for (String token : trimmed.replaceAll("now ","").split(" "))
                    {
                        if (ZoneId.getAvailableZoneIds().contains(token))
                        {
                            zone = ZoneId.of(token);
                            formatter = formatter
                                    .replaceAll(token+"( )?", "");
                        }
                    }
                    try {
                        nowString = ZonedDateTime.now()
                                .withZoneSameInstant(zone)
                                .format(DateTimeFormatter.ofPattern(formatter));
                    } catch(Exception ignored) {}
                    sub.append(helper.apply(nowString, matcher2));
                }

                // time until the event's start or end
                // users cannot control from which time the until text is calculated from
                else if(trimmed.matches("(\\[.*?])?until( .+)?(\\[.*?])?"))
                {
                    String args = trimmed.replaceAll("until( )?","").replaceAll("\\[.*?]","");
                    int depth = 3;
                    boolean isShort = false;
                    boolean useRaw = false;
                    for (String token : args.split(" "))
                    {
                        if (token.matches("[0123]"))
                            depth = Integer.parseInt(token);
                        else if (token.toLowerCase().matches("s(hort)?"))
                            isShort = true;
                        else if (token.toLowerCase().matches("r(aw)?"))
                            useRaw = true;
                    }

                    long minutes = ZonedDateTime.now()
                            .until(entry.hasStarted() ? entry.getEnd() : entry.getStart(), ChronoUnit.MINUTES);
                    if (useRaw == true)
                    {
                        sub.append(helper.apply(Long.toString(minutes), matcher2));
                    }
                    else
                    {
                        if (minutes > 1)
                        {
                            StringBuilder builder = new StringBuilder();
                            addTimeGap(builder, minutes, isShort, depth);
                            sub.append(helper.apply(builder.toString(), matcher2));
                        }
                    }
                }

                // inserts the number of users who have rsvp'ed for a particular rsvp category
                else if (trimmed.matches("(\\[.*?])?rsvp .+(\\[.*?])?")) // rsvp count
                {
                    String name = trimmed.replaceAll("rsvp ","").replaceAll("\\[.*?]","");
                    List<String> members = entry.getRsvpMembers().get(name);
                    if (members != null)
                    {
                        sub.append(helper.apply(""+members.size(), matcher2));
                    }
                }

                // inserts @mentions for all users who have rsvped to a particular rsvp category
                else if (trimmed.matches("(\\[.*?])?mention .+(\\[.*?])?")) // rsvp mentions
                {
                    String name = trimmed.replaceAll("mention ","").replaceAll("\\[.*?]","");
                    List<String> users = compileUserList(entry, name);
                    if (users != null)  // a valid mention option was used
                    {
                        StringBuilder userMentions = new StringBuilder();
                        for(int i=0; i<users.size(); i++)
                        {
                            String user = users.get(i);
                            boolean isId = user.matches("\\d+"); // is probably an ID
                            try
                            {
                                if (Main.getShardManager().getJDA(entry.getGuildId())
                                        .getGuildById(entry.getGuildId()).getMemberById(user) != null)
                                {   // if member does not exist, ommit the user
                                    userMentions.append("<@").append(user).append(">");
                                }
                            }
                            catch (Exception e)
                            {   // if the ID was invalid, flag to be appended as plaintext
                                isId = false;
                            }
                            if (!isId)
                            {   // user is plaintext (added by !manage)
                                userMentions.append(user);
                            }
                            if (i+1<users.size())
                                userMentions.append(", ");
                        }
                        sub.append(helper.apply(userMentions.toString(), matcher2));
                    }
                }

                // the raw names of users from a particular rsvp category
                else if(trimmed.matches("(\\[.*?])?mentionm .+(\\[.*?])?")
                        || trimmed.matches("(\\[.*?])?list .+(\\[.*?])?")) // rsvp mentions
                {
                    String name = trimmed
                            .replace("mentionm ","")
                            .replace("list ","")
                            .replaceAll("\\[.*?]","");
                    List<String> users = compileUserList(entry, name);
                    if (users != null)
                    {
                        StringBuilder userMentions = new StringBuilder();
                        for (int i=0; i<users.size(); i++)
                        {
                            String user = users.get(i);
                            boolean isId = user.matches("\\d+"); // looks like an ID
                            if (isId)
                            {   // is a user's ID, find user's effective name
                                try
                                {
                                    Member member = Main.getShardManager().getJDA(entry.getGuildId())
                                            .getGuildById(entry.getGuildId()).getMemberById(user);
                                    if (member != null)
                                    {   // if member does not exist, ommit the user
                                        userMentions.append(member.getEffectiveName());
                                    }
                                }
                                catch (Exception e)
                                {   // if the ID was invalid, flag to be appended as plaintext
                                   isId = false;
                                }
                            }
                            if (!isId)
                            {   // user is plaintext (added by !manage)
                                userMentions.append(user);
                            }
                            if (i+1<users.size())
                                userMentions.append(", "); // don't add comma if last element
                        }
                        sub.append(helper.apply(userMentions.toString(), matcher2));
                    }
                }

                // inserts the custom title url used by the event (if used)
                else if (trimmed.matches("(\\[.*?])?url(\\[.*?])?")) // advanced title url
                {
                    if (entry.getTitleUrl() != null)
                    {
                        sub.append(helper.apply(entry.getTitleUrl(), matcher2));
                    }
                }

                // inserts the custom image url used by the event (if used)
                else if (trimmed.matches("(\\[.*?])?image(\\[.*?])?")) // advanced image url
                {
                    if (entry.getImageUrl() != null)
                    {
                        sub.append(helper.apply(entry.getImageUrl(), matcher2));
                    }
                }

                // inserts the custom thumbnail url used by the event (if used)
                else if(trimmed.matches("(\\[.*?])?thumbnail(\\[.*?])?")) // advanced thumbnail url
                {
                    if (entry.getThumbnailUrl() != null)
                    {
                        sub.append(helper.apply(entry.getThumbnailUrl(), matcher2));
                    }
                }

                // inserts the custom location string (if used)
                else if(trimmed.matches("(\\[.*?])?location(\\[.*?])?")) // advanced thumbnail url
                {
                    if (entry.getThumbnailUrl() != null)
                    {
                        sub.append(helper.apply(entry.getLocation(), matcher2));
                    }
                }

                // substitution for event start text (to be used for localization)
                // TODO remove when full localization features are finished
                else if (trimmed.matches("(\\[.*?])?s(\\[.*?])?"))
                {
                    if (!entry.hasStarted())
                    {
                        sub.append(helper.apply("", matcher2));
                    }
                }

                // substitution for event end text (to be used for localization)
                // TODO remove when full localization features are finished
                else if (trimmed.matches("(\\[.*?])?e(\\[.*?])?"))
                {
                    if (entry.hasStarted())
                    {
                        sub.append(helper.apply("", matcher2));
                    }
                }
            }
            raw = raw.replace(group, sub.toString());
        }

        // legacy parsing
        /*
         * parses the format string character by character looking for % characters
         * a token is one % character followed by a key character
         */
        StringBuilder processed = new StringBuilder();
        for (int i = 0; i < raw.length(); i++)
        {
            char ch = raw.charAt(i);
            if(ch == '%' && i+1 < raw.length())
            {
                i++;
                ch = raw.charAt(i);
                switch(ch)
                {
                    // comments 1-9
                    case 'c' :
                        if (i+1 < raw.length() && firstPass)
                        {
                            ch = raw.charAt(i+1);
                            if (Character.isDigit(ch))
                            {
                                i++;
                                int x = Integer.parseInt("" + ch);
                                if (entry.getComments().size()>=x && x!=0)
                                {
                                    String parsedComment =
                                            ParsingUtilities.processText(entry.getComments().get(x - 1), entry, false);
                                    processed.append(parsedComment);
                                }
                            }
                        }
                        break;

                    // full list of comments, no line padding
                    case 'f' :
                        if (firstPass)
                        {   // if this call of the parser is nested, don't insert comments
                            processed.append(String.join("\n", entry.getComments().stream()
                                    .map(comment -> ParsingUtilities.processText(comment, entry, false))
                                    .collect(Collectors.toList())));
                        }
                        break;

                    // full list of comments, each comment padded by newline
                    // used as the default description string
                    case 'g':
                        if (firstPass)
                        {
                            StringBuilder stringBuilder = new StringBuilder();
                            for (int j=0; j<entry.getComments().size(); j++)
                            {
                                if (j>0) stringBuilder.append("\n"); // newline pad between comment lines
                                stringBuilder
                                        .append(processText(entry.getComments().get(j), entry, false))
                                        .append("\n"); // trailing newline
                            }
                            processed.append(stringBuilder.toString());
                        }
                        break;

                    // dynamic 'begins|ends in [x] minutes|hours|days' text
                    case 'a' :
                        if (!entry.hasStarted())
                        {
                            processed.append("begins");
                            long minutes = ZonedDateTime.now().until(entry.getStart(), ChronoUnit.MINUTES);
                            if (minutes > 0)
                            {
                                processed.append(" in ");
                                addTimeGap(processed, minutes+1, false, 1);
                            }
                        } else
                        {
                            processed.append("ends");
                            long minutes = ZonedDateTime.now().until(entry.getEnd(), ChronoUnit.MINUTES);
                            if (minutes > 0)
                            {
                                processed.append(" in ");
                                addTimeGap(processed, minutes+1, false, 1);
                            }
                        }
                        break;

                    // contextual 'begins' or 'ends'
                    case 'b' :
                        if (!entry.hasStarted())
                            processed.append("begins");
                        else
                            processed.append("ends");
                        break;

                    // dynamic 'in [x] minutes|hours|days' text
                    case 'x' :
                        if (!entry.hasStarted())
                        {
                            long minutes = ZonedDateTime.now().until(entry.getStart(), ChronoUnit.MINUTES);
                            addTimeGap(processed, minutes+1, false, 1);
                        }
                        else
                        {
                            long minutes = ZonedDateTime.now().until(entry.getEnd(), ChronoUnit.MINUTES);
                            addTimeGap(processed, minutes+1, false, 1);
                        }
                        break;

                    // simple start date time
                    case 's':
                        processed.append(entry.getStart().format(timeFormatter));
                        break;

                    // simple end date time
                    case 'e':
                        processed.append(entry.getEnd().format(timeFormatter));
                        break;

                    // event title
                    case 't' :
                        processed.append(entry.getTitle());
                        break;

                    // start day of month, padded numeric
                    case 'd' :
                        processed.append(String.format("%02d",entry.getStart().getDayOfMonth()));
                        break;

                    // start day of week
                    case 'D' :
                        processed.append(StringUtils.capitalize(entry.getStart().getDayOfWeek().toString()));
                        break;

                    // start month, numeric
                    case 'm' :
                        processed.append(String.format("%02d",entry.getStart().getMonthValue()));
                        break;

                    // start month, name
                    case 'M' :
                        processed.append(StringUtils.capitalize(entry.getStart().getMonth().toString()));
                        break;

                    // start year
                    case 'y' :
                        processed.append(entry.getStart().getYear());
                        break;

                    // encoded event ID
                    case 'i':
                        processed.append(ParsingUtilities.intToEncodedID(entry.getId()));
                        break;

                    // '%' character
                    case '%' :
                        processed.append('%');
                        break;

                    // entry title url, if one exists
                    case 'u' :
                        processed.append(entry.getTitleUrl() == null ? "" : entry.getTitleUrl());
                        break;

                    // entry image url, if one exists
                    case 'v' :
                        processed.append(entry.getImageUrl() == null ? "" : entry.getImageUrl());
                        break;

                    // entry thumbnail url, if one exists
                    case 'w':
                        processed.append(entry.getThumbnailUrl() == null ? "" : entry.getThumbnailUrl());
                        break;

                    // newline
                    case 'n':
                        processed.append("\n");
                        break;

                    // start hour, padded numeric
                    case 'h':
                        processed.append(String.format("%02d",entry.getStart().getHour()));
                        break;

                    // start minute, padded numeric
                    case 'k':
                        processed.append(String.format("%02d",entry.getStart().getMinute()));
                        break;

                    // event location information
                    case 'l':
                        processed.append(entry.getLocation());
                        break;
                }
            }
            else
            {   // append the current character
                processed.append(ch);
            }
        }

        // build the string and return
        return processed.toString();
    }


    /**
     * generates a list of user IDs for a given RSVP category of an event
     * @param entry ScheduleEntry object
     * @param category name of RSVP category
     * @return List of Stings or null if category is invalid
     */
    private static List<String> compileUserList(ScheduleEntry entry, String category)
    {
        Set<String> users = null;
        if (category.toLowerCase().equals("no-input"))
        {
            List<String> rsvped = new ArrayList<>();
            Set<String> keys = entry.getRsvpMembers().keySet();
            for(String key : keys)
            {
                rsvped.addAll(entry.getRsvpMembersOfType(key));
            }
            JDA shard = Main.getShardManager().getJDA(entry.getGuildId());
            Guild guild = shard.getGuildById(entry.getGuildId());
            GuildChannel channel = shard.getTextChannelById(entry.getChannelId());
            users = guild.getMembers().stream()
                    .filter(member -> member.getPermissions(channel).contains(Permission.MESSAGE_HISTORY))
                    .map(member -> member.getUser().getId())
                    .filter(memberId -> !rsvped.contains(memberId)).collect(Collectors.toSet());
        } else
        {
            List<String> members = entry.getRsvpMembers().get(category);
            if(members != null)
                users = entry.getRsvpMembersOfType(category);
        }
        return (users == null) ? null : new ArrayList<>(users);
    }


    /**
     * Parses user supplied input for information indicating the reminder intervals to use for a schedule's
     * reminder settings
     * @param arg (String) user input
     * @return (Set) a linked set of integers representing the time (in minutes) before an event starts
     */
    public static Set<Integer> parseReminder(String arg)
    {
        Set<Integer> list = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("[-]?\\d+[^\\d]?").matcher(arg);
        while(matcher.find())
        {
            String group = matcher.group();
            Character ch = group.charAt(group.length()-1);
            if(Character.isDigit(ch))
            {
                if(VerifyUtilities.verifyInteger(group))
                {
                    list.add(Integer.parseInt(group));
                }
            }
            else if(VerifyUtilities.verifyInteger(group.substring(0, group.length()-1)))
            {
                Integer units = Integer.parseInt(group.substring(0, group.length()-1));
                switch(ch)
                {
                    case 'h':
                        list.add(units*60);
                        break;
                    case 'd':
                        list.add(units*60*24);
                        break;
                    case 'm':
                    default:
                        list.add(units);
                        break;
                }
            }
        }
        return list;
    }


    /**
     * Parses user input for a date information
     * @param arg (String)
     */
    public static LocalDate parseDate(String arg, ZoneId zone)
    {
        switch (arg)
        {
            case "today":
                return LocalDate.now(zone);

            case "tomorrow":
                return LocalDate.now(zone).plusDays(1);

            case "overmorrow":
                return LocalDate.now(zone).plusDays(2);

            default:
                String[] splt = arg.split("[^0-9]+");
                LocalDate date = LocalDate.now().plusDays(1);
                if(splt.length == 1)
                {
                    date = LocalDate.of(LocalDate.now().getYear(), LocalDate.now().getMonth(), Integer.parseInt(splt[0]));
                }
                else if(splt.length == 2)
                {
                    date = LocalDate.of(LocalDate.now().getYear(), Integer.parseInt(splt[0]), Integer.parseInt(splt[1]));
                }
                else if(splt.length == 3)
                {
                    date = LocalDate.of(Integer.parseInt(splt[0]), Integer.parseInt(splt[1]), Integer.parseInt(splt[2]));
                }
                return date;
        }
    }


    /**
     * Parses user supplied input for zone information
     * Allows for zone names to be inputted without proper capitalization
     * @param userInput (String) user input
     * @return (ZoneId) zone information as parsed
     */
    public static ZoneId parseZone(String userInput)
    {
        for(String validZone : ZoneId.getAvailableZoneIds())
        {
            if(validZone.equalsIgnoreCase(userInput))
            {
                return ZoneId.of(validZone);
            }
        }
        return ZoneId.of(Main.getBotSettingsManager().getTimeZone());
    }


    /**
     * Parses out expire and deadline keyword input
     */
    public static LocalDate parseNullableDate(String input, ZoneId zone)
    {
        switch(input)
        {
            case "off":
            case "none":
            case "never":
            case "null":
                return null;
            default:
                return ParsingUtilities.parseDate(input, zone);
        }
    }

    /**
     * Parses out url, image, and thumbnail keyword arguments
     */
    public static String parseUrl(String input)
    {
        switch(input)
        {
            case "null":
            case "off":
            case "none":
                return null;
            default:
                return input;
        }
    }

    /**
     * evaluates a announcement override 'time string' into a definite ZonedDateTime object
     * @param time 'time string'
     * @param se ScheduleEntry the override resides on
     * @return ZonedDateTime for announcement override, or null if un-parsable
     */
    public static ZonedDateTime parseTimeString(String time, ScheduleEntry se)
    {
        time = time.toUpperCase();  // all caps

        // determine basis for the announcement time
        ZonedDateTime announcementTime;
        if(time.startsWith("START"))
        {
            time = time.replace("START", "");
            announcementTime = se.getStart();
        }
        else if(time.startsWith("END"))
        {
            time = time.replace("END", "");
            announcementTime = se.getEnd();
        }
        else
        {
            return null;
        }

        // determine if offset is positive or negative
        boolean positive;
        if(time.startsWith("+"))
        {
            time = time.replace("+", "");
            positive = true;
        }
        else if(time.startsWith("-"))
        {
            time = time.replace("-", "");
            positive = false;
        }
        else
        {
            return announcementTime;
        }

        // parse out the time offset
        Integer minutes = time.replaceAll("[^\\d]", "").isEmpty() ?
                0 : Integer.parseInt(time.replaceAll("[^\\d]", ""));
        if (minutes != 0)
        {
            switch(time.charAt(time.length()-1))
            {
                case 'H':
                    minutes = minutes*60;
                    break;
                case 'D':
                    minutes = 60*24;
                    break;
            }
        }

        // add offset to the time and return
        if(positive) return announcementTime.plusMinutes(minutes);
        else return announcementTime.minusMinutes(minutes);
    }

    /**
     * @param input user-supplied encoded string
     * @return int representation of the base64 string
     */
    public static int encodeIDToInt(String input)
    {
        int base = 36; // use base36 encoding (0-z)
        if (Character.MAX_RADIX < base) // just in case there are platforms with unusual MAX_RADIX
            base = 16; // revert to hex (0-F)
        return Integer.parseInt(input, base);
    }

    /**
     * @param input integer representing an event's ID
     * @return Base64 encoded ID string
     */
    public static String intToEncodedID(int input)
    {
        int base = 36; // use base36 encoding (0-z)
        if (Character.MAX_RADIX < base) // just in case there are platforms with unusual MAX_RADIX
            base = 16; // revert to hex (0-F)
        return Integer.toString(input, base);
    }

    /**
     * Credit goes to @Somename#0436
     * @param content StringBuilder to which the time text should be appended
     * @param timeTil the time before an event begins/ends (in minutes)
     * @param isShort
     * @param depth 1 (display only minutes), 2 (minutes and hours), or 3 (minutes, hours, and days)
     */
    public static void addTimeGap(StringBuilder content, long timeTil, boolean isShort, int depth)
    {
        long div, mod, quot;
        Boolean needSep;
        String sep;

        if (timeTil <= 0)
        {
            content.append(isShort ? "<1m" : "less than 1 minute");
            return;
        }

        depth = Math.max(depth, 1);
        needSep = false;
        sep = isShort ? " " : " and ";

        // do days
        quot = (60 * 24);
        div = timeTil / quot;
        mod = timeTil % quot;
        if (div > 0)
        {
            if (depth == 1 && mod >= (quot / 2)) div++;
            addDays(content, div, isShort);
            needSep = true;
            depth--;
        }
        if (mod <= 0 || depth <= 0) return;

        // do hours
        quot = 60;
        div = mod / quot;
        mod = mod % quot;
        if (div > 0)
        {
            if (depth == 1 && mod >= (quot / 2)) div++;
            if (needSep) content.append(depth == 1 ? sep : " ");
            addHours(content, div, isShort);
            needSep = true;
            depth--;
        }
        if (mod <= 0 || depth <= 0) return;

        // only minutes remaining
        if (needSep) content.append(sep);
        addMinutes(content, mod, isShort);
    }

    /**
     *
     */
    static void addValueCaption(StringBuilder str, long value, String singular, String plural)
    {
        str.append(value).append(" ").append(value > 1 ? plural : singular);
    }

    /**
     *
     */
    static void addValueCaptionType(StringBuilder str, long value, String singularShort, String pluralShort,
                             String singularLong, String pluralLong, boolean isShort)
    {
        addValueCaption(str, value, isShort ? singularShort : singularLong, isShort ? pluralShort : pluralLong);
    }

    /**
     *
     */
    static void addMinutes(StringBuilder str, long value, boolean isShort)
    {
        addValueCaptionType(str, value, "m", "m", "minute", "minutes", isShort);
    }

    /**
     *
     */
    static void addHours(StringBuilder str, long value, boolean isShort)
    {
        addValueCaptionType(str, value, "h", "h", "hour", "hours", isShort);
    }

    /**
     *
     */
    static void addDays(StringBuilder str, long value, boolean isShort)
    {
        addValueCaptionType(str, value, "d", "d", "day", "days", isShort);
    }
}
