package ws.nmathe.saber.core;

import com.google.common.collect.Iterables;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.utils.MiscUtil;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;

import javax.security.auth.login.LoginException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ShardManager manages the JDA objects used to interface with the Discord api
 */
public class ShardManager
{
    private Map<Integer, JDA> jdaShards = null;
    private JDA jda = null;
    private Iterator<String> games;

    /**
     * Populates the shard manager with initialized JDA shards (if sharding)
     * @param shards a list of integers, where each integer represents a shard ID
     *               The size of the list should never be greater than shardTotal
     * @param shardTotal the total number of shards to create
     */
    public ShardManager(List<Integer> shards, Integer shardTotal)
    {
        // initialize the list of 'Now Playing' games
        this.loadGamesList();

        try // build the bot
        {
            // handle sharding
            if(shardTotal > 0)
            {
                this.jdaShards = new TreeMap<Integer, JDA>();


                Logging.info(this.getClass(), "Starting shard " + shards.get(0) + ". . .");

                // build the first shard synchronously with Main
                JDA jda = new JDABuilder(AccountType.BOT)
                        .setToken(Main.getBotSettingsManager().getToken())
                        .setStatus(OnlineStatus.ONLINE)
                        .setCorePoolSize(2)
                        .addEventListener(new EventListener())
                        .setAutoReconnect(true)
                        .useSharding(shards.get(0), shardTotal)
                        .buildBlocking();

                this.setGamesList(jda);
                this.jdaShards.put(shards.get(0), jda);

                shards.remove(0);

                // build remaining shards synchronously (for clean startup) but concurrently with Main
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() ->
                {
                    try
                    {
                        for(Integer shardId : shards)
                        {
                            Logging.info(this.getClass(), "Starting shard " + shardId + ". . .");

                            JDA shard = new JDABuilder(AccountType.BOT)
                                    .setToken(Main.getBotSettingsManager().getToken())
                                    .setStatus(OnlineStatus.ONLINE)
                                    .setCorePoolSize(2)
                                    .addEventListener(new EventListener())
                                    .setAutoReconnect(true)
                                    .useSharding(shardId, shardTotal)
                                    .buildBlocking();

                            this.setGamesList(shard);
                            this.jdaShards.put(shardId, shard);
                        }

                        executor.shutdown();
                    }
                    catch(Exception e)
                    {
                        Logging.exception(this.getClass(), e);
                    }
                });
            }
            else // no sharding
            {
                Logging.info(this.getClass(), "Starting bot without sharding. . .");

                this.jda = new JDABuilder(AccountType.BOT)
                        .setToken(Main.getBotSettingsManager().getToken())
                        .setStatus(OnlineStatus.ONLINE)
                        .setCorePoolSize(2)
                        .buildBlocking();

                this.setGamesList(jda);

                this.jda.addEventListener(new EventListener());
                this.jda.setAutoReconnect(true);

            }
        }
        catch( Exception e )
        {
            Logging.exception(Main.class, e);
            System.exit(1);
        }
    }


    /**
     * Identifies if the bot is sharding enabled
     * @return bool
     */
    public boolean isSharding()
    {
        return jda == null;
    }


    /**
     * Retrieves the JDA if unsharded, or the JDA shardID 0 if sharded
     * @return primary JDA
     */
    public JDA getJDA()
    {
        if(jda == null)
        {
            return jdaShards.get(0);
        }

        return jda;
    }


    /**
     * Retrieves the JDA responsible for a guild
     * @param guildId unique (snowflake) guild ID
     * @return JDA responsible for the guild
     */
    public JDA getJDA(String guildId)
    {
        return Main.getShardManager().isSharding() ? Main.getShardManager().getShard(guildId) : Main.getShardManager().getJDA();
    }

    /**
     * retrieves a specific JDA shard
     * Should only be used when sharding is enabled
     * @param shardId ID of JDA shard to retrieve
     * @return JDA shard
     */
    public JDA getShard(int shardId)
    {
        return jdaShards.get(shardId);
    }


    /**
     * Retrieves the shard responsible for a guild
     * Should only be used when sharding is enabled
     * @param guildId ID of guild
     * @return JDA shard
     */
    public JDA getShard(String guildId)
    {
        long id = MiscUtil.parseSnowflake(guildId);
        long shardId = (id >> 22) % Main.getBotSettingsManager().getShardTotal();
        return jdaShards.get((int) shardId);
    }


    /**
     * Retrieves all the JDA shards managed by this ShardManager
     * Should not be used when sharding is disabled
     * @return Collection of JDA Objects
     */
    public Collection<JDA> getShards()
    {
        return this.jdaShards.values();
    }


    /**
     * Retrieves the full list of guilds attached to the application
     * Will not be accurate if the bot is sharded across multiple physical servers
     * @return List of Guild objects
     */
    public List<Guild> getGuilds()
    {
        if(jda == null)
        {
            List<Guild> guilds = new ArrayList<>();
            for(JDA jda : jdaShards.values())
            {
                guilds.addAll(jda.getGuilds());
            }
            return guilds;
        }

        return jda.getGuilds();
    }

    /**
     * Loads the list of "NowPlaying" game titles from the settings config
     */
    public void loadGamesList()
    {
        this.games = Iterables.cycle(Main.getBotSettingsManager().getNowPlayingList()).iterator();
    }


    /**
     * Initializes a schedule timer which iterates the "NowPlaying" game list for a JDA object
     * @param shard JDA object
     */
    private void setGamesList(JDA shard)
    {
        // cycle "now playing" message every 30 seconds
        (new Timer()).scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                shard.getPresence().setGame(new Game()
                {
                    @Override
                    public String getName()
                    {
                        return games.next();
                    }

                    @Override
                    public String getUrl()
                    { return "https://nmathe.ws/bots/saber"; }

                    @Override
                    public GameType getType()
                    { return GameType.DEFAULT; }
                });
            }
        }, 0, 30*1000);
    }
}
