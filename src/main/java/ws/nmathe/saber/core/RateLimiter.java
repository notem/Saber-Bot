package ws.nmathe.saber.core;

import ws.nmathe.saber.Main;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple class which maintains a list of timestamps of the last command
 * message that a user sent (within the bot's current runtime instance)
 */
public class RateLimiter
{
    private long threshold;
    private Map<String, Long> timestampMap;

    public RateLimiter()
    {
        this.threshold = 0;
        this.timestampMap = new ConcurrentHashMap<>();
    }

    public RateLimiter(long threshold)
    {
        this.threshold = threshold;
        this.timestampMap = new ConcurrentHashMap<>();
    }

    /**
     * determine if a command should be ignored due to exceeded rate limit
     * @param userId (String) ID of the user
     * @return (boolean) true if last command was sent within the threshold
     */
    public boolean isOnCooldown(String userId)
    {
        // if rate limiter is constructed without a threshold, use bot settings.
        long threshold = this.threshold==0 ? Main.getBotSettingsManager().getCooldownThreshold() : this.threshold;
        if(timestampMap.containsKey(userId))
        {
            long time = timestampMap.get(userId);
            if(System.currentTimeMillis() - time <= threshold)
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
