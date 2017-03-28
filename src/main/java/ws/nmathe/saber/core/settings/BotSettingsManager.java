package ws.nmathe.saber.core.settings;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * contains configurable variables for the bot
 * file should be auto-generated with required fields if the file is found
 * not to exist.
 */
public class BotSettingsManager
{
    private static final String FILENAME = "saber.toml";
    private BotSettings settings;

    public BotSettingsManager()
    {
        InputStream input = null;
        try
        {
            input = new FileInputStream("./" + FILENAME);
            settings = (new Toml()).read(input).to(BotSettings.class);
        }
        catch (IOException ex)
        {
            this.generateFile();
            settings = null;
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
    }

    private void generateFile()
    {
        OutputStream output = null;
        try
        {
            output = new FileOutputStream(FILENAME);
            (new TomlWriter()).write(new BotSettings(), output);
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

    public void reloadSettings()
    {
        InputStream input = null;
        try
        {
            input = new FileInputStream("./" + FILENAME);
            settings = (new Toml()).read(input).to(BotSettings.class);
        }
        catch (IOException ignored)
        { }
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
    }

    private class BotSettings
    {
        String discord_token;
        String web_token;
        String mongodb;
        String prefix;
        String admin_prefix;
        String admin_id;
        String bot_control_chan;
        int max_entries;
        String default_announce_chan;
        String default_announce_msg;
        String default_clock_format;
        String default_time_zone;
        List<String> nowplaying_list;
        Set<String> blacklist;

        BotSettings()
        {
            discord_token = "BOT_TOKEN";
            web_token = "ABAL_TOKEN";
            mongodb = "mongodb://localhost:27017";

            prefix = "!";
            admin_prefix = "s.";
            admin_id = "ADMIN_USER_ID";
            max_entries = 25;
            bot_control_chan = "saber_control";

            default_announce_chan = "general";
            default_time_zone = "America/New_York";
            default_clock_format = "12";
            default_announce_msg = "Event %a: ``%t``";

            nowplaying_list = new ArrayList<>();
            blacklist = new HashSet<>();
        }
    }

    public boolean hasSettings()
    {
        return settings == null;
    }

    public String getToken()
    {
        return settings.discord_token;
    }

    public String getWebToken()
    {
        return settings.web_token;
    }

    public String getAdminId()
    {
        return settings.admin_id;
    }

    public int getMaxEntries()
    {
        return settings.max_entries;
    }

    public String getCommandPrefix()
    {
        return settings.prefix;
    }

    public String getAdminPrefix()
    {
        return settings.admin_prefix;
    }

    public String getAnnounceChan()
    {
        return settings.default_announce_chan;
    }

    public String getControlChan()
    {
        return settings.bot_control_chan;
    }

    public String getAnnounceFormat()
    {
        return settings.default_announce_msg;
    }

    public String getClockFormat()
    {
        return settings.default_clock_format;
    }

    public String getTimeZone()
    {
        return settings.default_time_zone;
    }

    public String getMongoURI()
    {
        return settings.mongodb;
    }

    public List<String> getNowPlayingList()
    {
        return settings.nowplaying_list;
    }

    public Set<String> getBlackList()
    {
        return settings.blacklist;
    }
}
