package io.schedulerbot.utils;

/**
 * file: BotConfig.java
 * contains configurable variables for the bot
 */
public class BotConfig
{
    //public static String TOKEN = "MjUwODAxNjAzNjMwNTk2MTAw.CxaJqw.IPLbPDoRhVBZhZyQ9JjmoKvsskE" ;     // your bot's token
    public static String TOKEN = "MjUxODQ0MDgzMTM0MzY1Njk3.CxpsKA.QQl9q3sZD2ipQlt10huDU5HjFfI" ;     // your bot's token

    public static String PREFIX = "!";                  // prefix bot should respond to

    public static String ADMIN_PREFIX = "&&!";           // prefix for admin commands

    public static String ANNOUNCE_CHAN = "announce";    // the channel name where bot announces events too
                                                        // if ANNOUNCE_CHAN is empty, defaults to default channel

    public static String EVENT_CHAN = "event_schedule"; // the channel in which bot manages scheduled events

    public static String CONTROL_CHAN = "saber_control";// the channel where bot listens for commands, if set
                                                        // to an empty string bot listens everywhere

    public static int MAX_ENTRIES = 15;                 // determines the maximum amount of event event entries
                                                        // any one guild may maintain. Set to a negative values
                                                        // for no limit.
    public static String ADMIN_ID = "198595412876066817";
}
