package ws.nmathe.saber.utils;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;
import ws.nmathe.saber.Main;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 */
public class HttpUtilities
{
    private static LocalDateTime lastUpdate = LocalDateTime.MIN;

    /**
     * Updates bot metrics for any connected metric tracking services
     */
    public static void updateStats()
    {
        String auth = Main.getBotSettingsManager().getWebToken();
        if( auth != null )
        {
            HttpUtilities.updateStats_abal(Main.getShardManager().getGuilds().size(), auth);
        }
    }

    /**
     * updates bot metrics for bots.discord.pw tracking
     * @param i the guild count
     * @param auth the abal authentication token for the bot
     */
    private static void updateStats_abal(int i, String auth)
    {
        if (lastUpdate.until(LocalDateTime.now(), ChronoUnit.SECONDS) > 60)
        {
            JSONObject json = new JSONObject().put("server_count", i);

            try
            {
                Unirest.post("https://bots.discord.pw/api/bots/" + Main.getShardManager().getJDA().getSelfUser().getId() + "/stats")
                        .header("Authorization", auth)
                        .header("Content-Type", "application/json")
                        .body(json).asJson();
            }
            catch (UnirestException e)
            {
                Logging.warn(HttpUtilities.class, e.getMessage());
            }
            catch (Exception e)
            {
                Logging.exception(HttpUtilities.class, e);
            }
        }
    }
}
