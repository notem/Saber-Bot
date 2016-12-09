package io.schedulerbot.utils;

import java.io.*;
import java.util.Properties;

/**
 * contains configurable variables for the bot
 */
public class BotSettings
{
    private static final String FILENAME = "saber.properties";
    private static final String DEFAULT_TOKEN = "BOT_TOKEN";
    private static final String DEFAULT_ADMIN_ID = "ADMIN_USER_ID";
    private static final String DEFAULT_MAX_ENTRIES = "15";
    private static final String DEFAULT_COMMAND_PREFIX = "saber: ";
    private static final String DEFAULT_ADMIN_COMMAND_PREFIX = ".";

    private Properties properties;

    public static BotSettings init()
    {
        BotSettings bc = new BotSettings();
        bc.properties = new Properties();
        InputStream input = null;

        try
        {
            input = new FileInputStream("./" + FILENAME);

            //load a properties file from class path, inside static method
            bc.properties.load(input);
        }
        catch (IOException ex)
        {
            generateFile();
            return null;
        }
        finally
        {
            if (input != null)
            {
                try
                {
                    input.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        return bc;
    }

    private static void generateFile()
    {
        Properties p = new Properties();
        OutputStream output = null;
        try
        {
            output = new FileOutputStream(FILENAME);

            // set the default values
            p.setProperty("token", DEFAULT_TOKEN);
            p.setProperty("admin_id", DEFAULT_ADMIN_ID);
            p.setProperty("max_entries", DEFAULT_MAX_ENTRIES);
            p.setProperty("command_prefix", DEFAULT_COMMAND_PREFIX);
            p.setProperty("admin_command_prefix", DEFAULT_ADMIN_COMMAND_PREFIX);
            p.setProperty("chan_schedule", "event_schedule");
            p.setProperty("chan_control", "saber_control");
            p.setProperty("chan_announce","announce");

            // save properties to project root folder
            p.store(output, null);
        }
        catch (IOException io)
        {
            io.printStackTrace();
        }
        finally
        {
            if (output != null)
            {
                try
                {
                    output.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public String getToken()
    {
        return this.properties.getProperty("token");
    }

    public String getAdminId()
    {
        return this.properties.getProperty("admin_id");
    }

    public int getMaxEntries()
    {
        return Integer.parseInt(this.properties.getProperty("max_entries"));
    }

    public String getCommandPrefix()
    {
        return this.properties.getProperty("command_prefix");
    }

    public String getAdminPrefix()
    {
        return this.properties.getProperty("admin_command_prefix");
    }

    public String getAnnounceChan()
    {
        return this.properties.getProperty("chan_announce");
    }

    public String getScheduleChan()
    {
        return this.properties.getProperty("chan_schedule");
    }

    public String getControlChan()
    {
        return this.properties.getProperty("chan_control");
    }
}
