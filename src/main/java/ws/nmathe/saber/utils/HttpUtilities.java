package ws.nmathe.saber.utils;

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

            int response = Unirest.post("https://bots.discord.pw/api/bots/" + Main.getBotSelfUser().getId() + "stats")
                    .header("Authorization", auth)
                    .body(json).asString().getStatus();

            __out.printOut(HttpUtilities.class, "Updated abal bot list, recieved response code: " + response);

        } catch (UnirestException e)
        {
            e.printStackTrace();
        }
    }
}
