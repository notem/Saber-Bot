package ws.nmathe.saber.core.command;

import ws.nmathe.saber.Main;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple class which maintains a list of timestamps of the last command
 * message that a user sent (within the bot's current runtime instance)
 */
class RateLimiter
{
    private Map<String, Long> timestampMap;

    RateLimiter()
    {
        this.timestampMap = new ConcurrentHashMap<>();
    }

    /**
     * determine if a command should be ignored due to exceeded rate limit
     * @param userId (String) ID of the user
     * @return (boolean) true if last command was sent within the threshold
     */
    boolean isOnCooldown(String userId)
    {
        if(timestampMap.containsKey(userId)) {
            long time = timestampMap.get(userId);
            if(System.currentTimeMillis() - time <= Main.getBotSettingsManager().getCooldownThreshold())
            {
                return true;
            }
            else
            {
                timestampMap.put(userId, System.currentTimeMillis());
                return false;
            }
        }
        else
        {
            timestampMap.put(userId, System.currentTimeMillis());
            return false;
        }
    }
}
