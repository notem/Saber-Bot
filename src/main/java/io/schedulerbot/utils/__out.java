package io.schedulerbot.utils;

import java.time.LocalTime;

/**
 */
public class __out
{
    public static void printOut(Class caller, String msg)
    {
        LocalTime now = LocalTime.now();
        System.out.println("[" + now.getHour() + ":" + now.getMinute() + ":" + now.getSecond() + "] [" + caller.getSimpleName() + "] " + msg  );
    }
}
