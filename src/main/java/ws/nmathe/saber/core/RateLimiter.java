package ws.nmathe.saber.core;

import ws.nmathe.saber.Main;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple class which maintains a list of timestamps of the last command
 * message that a user sent (within the bot's current runtime instance)
 */
public class RateLimiter
{
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private long startThreshold = Main.getBotSettingsManager().getCooldownThreshold();
    private long maxThreshold   = 10*60*1000; // 10 minute default
    private int scaleFactor     = 2; // doubles
    private Map<String, Long> timestampMap = new ConcurrentHashMap<>();
    private Map<String, Long> thresholdMap = new ConcurrentHashMap<>();

    public RateLimiter()
    {
        this.init();
    }

    public RateLimiter(long startThreshold)
    {
        this.startThreshold = startThreshold;
        this.init();
    }

    /**
     * schedule a thread to remove unnecessary map entries so as to avoid accumulation
     * of many unnecessary entries (exhausting memory)
     */
    private void init()
    {
        this.executor.scheduleWithFixedDelay(()-> {

            // remove entity's whose most recent timestamp needs to no
            // longer be tracked for rate-limiting purposes
            long now = System.currentTimeMillis();
            for (String key : this.timestampMap.keySet())
            {
                long time = this.timestampMap.get(key);
                long threshold = this.thresholdMap.get(key);
                if (time + threshold <= now)
                {
                    this.timestampMap.remove(key);
                    this.thresholdMap.remove(key);
                }
            }
        }, 0, this.maxThreshold, TimeUnit.MILLISECONDS);
    }

    /**
     * determine if an action should be ignored due to exceeded rate limit
     * @param entityId unique identifier for entity to monitor
     * @return true if last command was sent within the cool-down threshold
     */
    public boolean check(String entityId)
    {
        long now = System.currentTimeMillis(); // current time
        if(timestampMap.containsKey(entityId))
        {
            long time = timestampMap.get(entityId); // time entity was last seen
            timestampMap.replace(entityId, now);    // update last seen value
            if (now - time <= startThreshold)
            {   // increase the entity's cool-down threshold & return true (is on cool-down)
                long newThreshold = this.scaleFactor * this.thresholdMap.get(entityId);
                if (newThreshold <= maxThreshold)
                    thresholdMap.replace(entityId, newThreshold);
                return true;
            }
            else
            {   // reset the entity's cool-down threshold & return false (not on cool-down)
                thresholdMap.replace(entityId, this.startThreshold);
                return false;
            }
        }
        else
        {   // add new entity to the timestamp map, set the starting threshold,
            // & return false (not on cool-down)
            timestampMap.put(entityId, now);
            thresholdMap.put(entityId, this.startThreshold);
            return false;
        }
    }
}
