package ws.nmathe.saber.core;

import com.google.common.collect.Iterables;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.utils.MiscUtil;
import net.dv8tion.jda.core.utils.SessionControllerAdapter;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;
import javax.security.auth.login.LoginException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * The ShardManager manages the JDA objects used to interface with the Discord api
 */
public class ShardManager
{
    private Integer shardTotal = null;
    private ConcurrentMap<Integer, JDA> jdaShards = null;        // used only when sharded
    private JDA jda = null;                                      // used only when unsharded

    private Iterator<String> games;

    private Integer primaryPoolSize = 15;    // used by the jda responsible for handling DMs
    private Integer secondaryPoolSize = 6;   // used by all other shards
    private Integer queryTimeout = 5*60*1000;// time to wait for API queries (milliseconds)

    private JDABuilder builder;

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
        this.shardTotal = shardTotal;

        try // build the bot
        {
            // basic skeleton of a jda shard
            this.builder = new JDABuilder(AccountType.BOT)
                    .setToken(Main.getBotSettingsManager().getToken())
                    .setStatus(OnlineStatus.ONLINE)
                    .setAutoReconnect(true);

            // custom OkHttpClient builder
            //OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
            //httpBuilder.connectionPool(new ConnectionPool())
            //        .connectTimeout(queryTimeout, TimeUnit.MILLISECONDS)
            //        .readTimeout(queryTimeout, TimeUnit.MILLISECONDS)
            //        .writeTimeout(queryTimeout, TimeUnit.MILLISECONDS)
            //        .retryOnConnectionFailure(true);

            // custom OkHttpClient uses longer timeout values
            // custom websocket factory also uses the longer timeout value
            //   this improved bot responsiveness in a previous era;
            //   it is unknown if these overrides are still necessary today (2018-07-21)
            //this.builder.setHttpClientBuilder(httpBuilder);
            //this.builder.setWebsocketFactory(new WebSocketFactory()
            //        .setConnectionTimeout(queryTimeout));

            // EventListener handles all types of bot events
            this.builder.addEventListener(new EventListener());

            // previous session queue mechanism was deprecated and has seemingly been replaced with
            //   this SessionController object
            this.builder.setSessionController(new SessionControllerAdapter() {
                @Override
                public void appendSession(SessionConnectNode node) {
                    System.out.println("[SessionController] Adding SessionConnectNode to Queue!");
                    super.appendSession(node);
                }
            });

            // handle sharding
            if(shardTotal > 0)
            {
                this.jdaShards = new ConcurrentHashMap<>();
                Logging.info(this.getClass(), "Starting shard " + shards.get(0) + ". . .");

                // add the reconnection queue
                //builder.setReconnectQueue(new SessionReconnectQueue());

                // build the first shard synchronously with Main
                // to block the initialization process until one shard is active
                if(shards.contains(0))
                {
                    // build primary shard (id 0)
                    JDA jda = this.builder
                            //.setCorePoolSize(primaryPoolSize)
                            .useSharding(0, shardTotal)
                            .build().awaitReady();

                    this.jdaShards.put(0, jda);
                    shards.remove((Object) 0);  // remove '0' (not necessarily the first element of the list)
                }
                else
                {
                    // build whatever the first shard id in the list is
                    // -this ought to occur only if the bot is running on multiple systems
                    // -and the current system is not responsible for the primary (0) shard
                    JDA jda = this.builder
                            //.setCorePoolSize(primaryPoolSize)
                            .useSharding(shards.get(0), shardTotal)
                            .build().awaitReady();

                    this.jdaShards.put(shards.get(0), jda);
                    shards.remove(shards.get(0));
                }

                // core functionality can now be initialized
                Main.getEntryManager().init();
                Main.getCommandHandler().init();

                // bring each additional shard up one after another
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() ->
                {
                    try
                    {
                        for(Integer shardId : shards)
                        {
                            // sleep for 5 seconds before continuing
                            //try { Thread.sleep(5*1000); }
                            //catch (InterruptedException ignored) {}
                            Logging.info(this.getClass(), "Starting shard " + shardId + ". . .");
                            JDA shard = this.builder
                                    //.setCorePoolSize(secondaryPoolSize)
                                    .useSharding(shardId, shardTotal)
                                    .build();
                            this.jdaShards.put(shardId, shard);
                        }
                        this.startGamesTimer();
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
                this.jda = this.builder
                        //.setCorePoolSize(primaryPoolSize)
                        .build().awaitReady();
                this.jda.setAutoReconnect(true);
                this.startGamesTimer();

                Main.getEntryManager().init();
                Main.getCommandHandler().init();
            }
        }
        catch (Exception e)
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
        if(this.isSharding())
        {
            return this.jdaShards.values();
        }
        else
        {
            return Collections.singletonList(this.jda);
        }
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
     * Shuts down and recreates a JDA shard
     * @param shardId (Integer) shardID of the JDA shard
     */
    public void restartShard(Integer shardId)
    {
        try
        {
            if(this.jdaShards.containsKey(shardId))
            {
                Logging.info(this.getClass(), "Shutting down shard-" + shardId + ". . .");
                this.getShard(shardId).shutdown();
                this.jdaShards.remove(shardId);
            }

            Logging.info(this.getClass(), "Starting shard-" + shardId + ". . .");
            JDABuilder shardBuilder;
            if(shardId == 0)
            {
                 shardBuilder = this.builder
                         //.setCorePoolSize(primaryPoolSize)
                         .useSharding(shardId, shardTotal);
            }
            else
            {
                shardBuilder = this.builder
                        //.setCorePoolSize(secondaryPoolSize)
                        .useSharding(shardId, shardTotal);
            }
            this.jdaShards.put(shardId, shardBuilder.build());
        }
        catch (LoginException e)
        {
            Logging.warn(this.getClass(), e.getMessage());
            this.restartShard(shardId);
        }
        catch (Exception e)
        {
            Logging.exception(this.getClass(), e);
        }
    }

    /**
     * Initializes a schedule timer which iterates the "NowPlaying" game list for a JDA object
     * Runs every 30 seconds
     */
    private void startGamesTimer()
    {
        // cycle "now playing" message every 30 seconds
        (new Timer()).scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                Consumer<JDA> task = (shard)->
                {
                    if(!games.hasNext()) return;

                    String name = games.next();
                    if(name.contains("%"))
                    {
                        if(name.contains("%shardId"))
                            name = name.replaceAll("%shardId", shard.getShardInfo().getShardId() + "");
                        if(name.contains("%shardTotal"))
                            name = name.replaceAll("%shardTotal", shard.getShardInfo().getShardTotal() + "");
                    }
                    shard.getPresence().setGame(Game.of(Game.GameType.DEFAULT, name, "https://nmathe.ws/bots/saber"));
                };

                if(isSharding())
                {
                    for(JDA shard : getShards())
                    {
                        if(JDA.Status.valueOf("CONNECTED") == shard.getStatus())
                            task.accept(shard);
                    }
                }
                else
                {
                    if(JDA.Status.valueOf("CONNECTED") == getJDA().getStatus())
                        task.accept(getJDA());
                }
            }
        }, 0, 30*1000);
    }
}
