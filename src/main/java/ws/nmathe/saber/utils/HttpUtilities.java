package ws.nmathe.saber.utils;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;
import ws.nmathe.saber.Main;

/**
 */
public class HttpUtilities
{
    public static void updateCount(int i, String auth)
    {
        try
        {
            JSONObject json = new JSONObject().put("server_count",i);

            HttpResponse<JsonNode> response = Unirest.post("https://bots.discord.pw/api/bots/" + Main.getBotSelfUser().getId() + "/stats")
                    .header("Authorization", auth)
                    .header("Content-Type", "application/json")
                    .body(json).asJson();

            __out.printOut(HttpUtilities.class, "Updating stats: abal response " + response.getStatus() + ", body: " + response.getBody());

        } catch (UnirestException e)
        {
            e.printStackTrace();
        }
    }
}
