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
    private Integer shardTotal = null;                      // >0 sharding; =0 no sharding
    private ConcurrentMap<Integer, JDA> jdaShards = null;   // used only when sharded
    private JDA jda = null;                                 // used only when unsharded

    // list of game names which are used to create a rotating bulletin board-like message system
    private Iterator<String> games;

    private Integer primaryPoolSize = 14;    // used by the jda responsible for handling DMs
    private Integer secondaryPoolSize = 7;   // used by all other shards

    private JDABuilder builder;  // builder to be used as the template for starting/restarting shards

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

        try // connect the bot to the discord API and initialize schedule components
        {
            // basic skeleton of a jda shard
            this.builder = new JDABuilder(AccountType.BOT)
                    .setToken(Main.getBotSettingsManager().getToken())
                    .setStatus(OnlineStatus.ONLINE)
                    .setAutoReconnect(true);

            // EventListener handles all types of bot events
            this.builder.addEventListener(new EventListener());

            // previous session queue mechanism was deprecated and has seemingly been replaced with
            //   this SessionController object
            this.builder.setSessionController(new SessionControllerAdapter()
            {
                @Override
                public void appendSession(SessionConnectNode node)
                {
                    System.out.println("[SessionController] Adding SessionConnectNode to Queue!");
                    super.appendSession(node);
                }
            });

            // handle sharding
            if(shardTotal > 0)
            {
                this.jdaShards = new ConcurrentHashMap<>();
                Logging.info(this.getClass(), "Starting shard " + shards.get(0) + ". . .");

                // build the first shard synchronously with Main
                // to block the initialization process until one shard is active
                if(shards.contains(0))
                {
                    // build primary shard (id 0)
                    JDA jda = this.builder
                            .setCorePoolSize(primaryPoolSize)
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
                            .setCorePoolSize(secondaryPoolSize)
                            .useSharding(shards.get(0), shardTotal)
                            .build().awaitReady();

                    this.jdaShards.put(shards.get(0), jda);
                    shards.remove(shards.get(0));
                }

                // core functionality can now be initialized
                Main.getEntryManager().init();
                Main.getCommandHandler().init();

                // start additional shards
                for (Integer shardId : shards)
                {
                    Logging.info(this.getClass(), "Starting shard " + shardId + ". . .");
                    JDA shard = this.builder
                            .setCorePoolSize(secondaryPoolSize)
                            .useSharding(shardId, shardTotal)
                            .build();
                    this.jdaShards.put(shardId, shard);
                }
            }
            else // no sharding
            {
                Logging.info(this.getClass(), "Starting bot without sharding. . .");
                this.jda = this.builder
                        .setCorePoolSize(primaryPoolSize)
                        .build().awaitReady();
                this.jda.setAutoReconnect(true);

                Main.getEntryManager().init();
                Main.getCommandHandler().init();
            }

            // start the "Now playing.." message cycler
            this.startGamesTimer();

            // executor service schedules shard-checking threads
            // restart any shards which are not in a CONNECTED state
            ScheduledExecutorService shardExecutor = Executors.newSingleThreadScheduledExecutor();
            shardExecutor.scheduleWithFixedDelay(() ->
            {
                Logging.info(this.getClass(), "Examining status of shards. . .");
                this.getShards().forEach((shard) ->
                {
                    JDA.Status status = shard.getStatus();
                    if (!status.equals(JDA.Status.CONNECTED))
                    {
                        Integer id = shard.getShardInfo().getShardId();
                        Logging.warn(this.getClass(), "Shard-"+id+" is not connected! ["+status+"]");

                        try
                        {
                            this.restartShard(id);
                        }
                        catch (LoginException | InterruptedException e)
                        {
                            Logging.warn(this.getClass(), "Failed to restart shard! ["+e.getMessage()+"]");
                        }
                        catch (Exception e)
                        {
                            Logging.exception(this.getClass(), e);
                            System.exit(-1);
                        }
                    }
                });
            }, 1, 1, TimeUnit.HOURS);
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
     * Retrieves the bot JDA if unsharded, otherwise take a shard from the shards collection
     * @return bot JDA
     */
    public JDA getJDA()
    {
        if(this.jda == null)
        {   // take a shard, any shard
            return this.jdaShards.values()
                    .iterator().next();
        }
        return this.jda;
    }

    /**
     * Retrieves the JDA responsible for a guild
     * @param guildId unique (snowflake) guild ID
     * @return JDA responsible for the guild
     */
    public JDA getJDA(String guildId)
    {
        return Main.getShardManager().isSharding() ?
                Main.getShardManager().getShard(guildId) : Main.getShardManager().getJDA();
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
    public void restartShard(Integer shardId) throws LoginException, InterruptedException {
        if (this.isSharding())
        {
            // do not handle shards not assigned to the current instance of the bot
            if (this.jdaShards.containsKey(shardId))
            {
                // shutdown the shard
                Logging.info(this.getClass(), "Shutting down shard-" + shardId + ". . .");
                this.getShard(shardId).shutdownNow();
                this.jdaShards.remove(shardId);

                // configure the builder from the template
                Logging.info(this.getClass(), "Starting shard-" + shardId + ". . .");
                JDABuilder shardBuilder;
                if (shardId == 0)
                {
                    shardBuilder = this.builder
                            .setCorePoolSize(primaryPoolSize)
                            .useSharding(shardId, shardTotal);
                }
                else
                {
                    shardBuilder = this.builder
                            .setCorePoolSize(secondaryPoolSize)
                            .useSharding(shardId, shardTotal);
                }

                // restart the shard (asynchronously)
                JDA shard = shardBuilder.build();
                this.jdaShards.put(shardId, shard);
            }
        }
        else
        {
            Logging.info(this.getClass(), "Restarting bot JDA. . .");
            this.jda.shutdownNow();
            this.jda = this.builder
                    .setCorePoolSize(primaryPoolSize)
                    .build().awaitReady();
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

                    // add shard-specific information
                    String name = games.next();
                    if(name.contains("%"))
                    {
                        if (name.contains("%shardId"))
                            name = name.replaceAll("%shardId", shard.getShardInfo().getShardId() + "");
                        if (name.contains("%shardTotal"))
                            name = name.replaceAll("%shardTotal", shard.getShardInfo().getShardTotal() + "");
                        if (name.contains("%guilds"))
                            name = name.replaceAll("%guilds", ""+shard.getGuilds().size());
                    }

                    // update shard presence
                    shard.getPresence().setGame(Game.playing(name));
                };

                if(isSharding())
                {
                    for(JDA shard : getShards())
                    {
                        if (shard.getStatus().equals(JDA.Status.CONNECTED))
                            task.accept(shard);
                    }
                }
                else
                {
                    if (getJDA().getStatus().equals(JDA.Status.CONNECTED))
                        task.accept(getJDA());
                }
            }
        }, 0, 30*1000);
    }
}
