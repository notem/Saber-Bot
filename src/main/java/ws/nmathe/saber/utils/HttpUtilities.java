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
    public static void updateStats(Integer shardId)
    {
        String auth = Main.getBotSettingsManager().getWebToken();
        if (auth != null)
        {
            HttpUtilities.updateStats_abal(auth, shardId);
        }
    }


    /**
     * updates bot metrics for bots.discord.pw tracking
     * @param auth the abal authentication token for the bot
     */
    private static void updateStats_abal(String auth, Integer shardId)
    {
        if (lastUpdate.until(LocalDateTime.now(), ChronoUnit.SECONDS) > 60)
        {
            JSONObject json;
            if (Main.getShardManager().isSharding() && shardId != null)
            {
                // if the bot is sharding send shard information
                int count = Main.getShardManager().getShard(shardId).getGuilds().size();
                int total = Main.getBotSettingsManager().getShardTotal();
                json = new JSONObject().put("shard_count", total).put("shard_id", shardId).put("server_count", count);
            }
            else
            {
                // otherwise send only the server count
                int count = Main.getShardManager().getGuilds().size();
                json = new JSONObject().put("server_count", count);
            }

            try
            {
                // send the API request
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
