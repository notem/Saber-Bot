package ws.nmathe.saber.core.schedule;

import ws.nmathe.saber.utils.MessageUtilities;
import net.dv8tion.jda.core.entities.Message;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manage's the schedule of ScheduleEntries for all attached guilds
 */
public class ScheduleManager
{
    private final ExecutorService executor = Executors.newCachedThreadPool();     // thread pool for schedule tasks
    private final Object scheduleLock = new Object();                                       // lock when modifying entry maps

    private HashMap<Integer, ScheduleEntry> entriesGlobal;         // maps id to entry
    private HashMap<String, ArrayList<Integer>> entriesByGuild;    // maps guild to ids
    private HashMap<String, ArrayList<Integer>> entriesByChannel;  // maps channel ids to entries
    private Collection<ScheduleEntry> coarseTimerBuff; // holds entries where 1h < start < 24h
    private Collection<ScheduleEntry> fineTimerBuff;   // holds entries where now < start < 1h

    public ScheduleManager()
    {
        this.entriesGlobal = new HashMap<>();
        this.entriesByGuild = new HashMap<>();
        this.entriesByChannel = new HashMap<>();

        this.coarseTimerBuff = new ArrayList<>();
        this.fineTimerBuff = new ArrayList<>();
    }

    /**
     * creates the scheduledExecutor thread pool and starts schedule timers which
     * check for expired entries
     */
    public void init()
    {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate( new ScheduleChecker( entriesGlobal.values() , 2 ),
                0, 12*60*60, TimeUnit.SECONDS);
        // 15 min timer
        scheduler.scheduleAtFixedRate( new ScheduleChecker(coarseTimerBuff, 1 ),
                1, 60*15, TimeUnit.SECONDS);
        // 1 min timer
        scheduler.scheduleAtFixedRate( new ScheduleChecker(fineTimerBuff, 0 ),
                60 - (LocalTime.now().toSecondOfDay()%60), 60, TimeUnit.SECONDS );
    }

    /**
     * Takes a valid message containing a schedule entry, parses it into an entry
     * and then adds it to the hashmaps. Should be used when a guild joins or during
     * the init of the bot.
     *
     * @param message Discord Message
     */
    public void addEntry( Message message )
    {
        ScheduleEntry se = ScheduleEntryParser.parse( message );
        if( se == null ) return;    // end early if parsing failed

        // regenerate the entry on the off chance the Id was changed
        String msg = ScheduleEntryParser.generate(se.getTitle(),se.getStart(),se.getEnd(),se.getComments(),se.getRepeat(),se.getId(),se.getMessage().getChannel().getId());
        MessageUtilities.editMsg( msg, se.getMessage(), null);

        String guildId = message.getGuild().getId();
        String channelId = message.getChannel().getId();

        // put the ScheduleEntry thread into a HashMap by ID
        entriesGlobal.put(se.getId(), se);

        // put the id in guild mapping
        if( !entriesByGuild.containsKey( guildId ) )
        {
            ArrayList<Integer> entries = new ArrayList<>();
            entries.add(se.getId());
            entriesByGuild.put( guildId, entries );
        }
        else
            entriesByGuild.get( guildId ).add( se.getId() );

        // put the id in channel mapping
        if( !entriesByChannel.containsKey( channelId ))
        {
            ArrayList<Integer> entries = new ArrayList<>();
            entries.add(se.getId());
            entriesByChannel.put( channelId, entries );
        }
        else
            entriesByChannel.get( channelId ).add( se.getId() );

        // adjusts the displayed time til timer (since it is not set at creation)
        se.adjustTimer();

        // add the entry to buffer
        if( !se.hasStarted() )
        {
            long timeTil = ZonedDateTime.now().until(se.getStart(), ChronoUnit.SECONDS);
            if (timeTil <= 45 * 60)
                fineTimerBuff.add(se);
            else if (timeTil <= 32 * 60 * 60)
                coarseTimerBuff.add(se);
        }
        else
        {
            fineTimerBuff.add(se);
        }
    }

    /**
     * Constructs a schedule entry from it's parts, which are provided as method arguments.
     * Needs to do no parsing of the message.  Should be used with the create and edit commands.
     *
     * @param title the entry's title
     * @param start the entry's ZoneDateTime start
     * @param end the entry's ZoneDateTime end
     * @param comments list of comment strings
     * @param Id the integer Id of the entry
     * @param message the discord Message that contains the entry's information
     * @param repeat integer repeat settings
     */
    public void addEntry(String title, ZonedDateTime start, ZonedDateTime end, ArrayList<String> comments, Integer Id, Message message, int repeat)
    {
        ScheduleEntry se = new ScheduleEntry( title, start, end, comments, Id, message, repeat);

        String guildId = message.getGuild().getId();
        String channelId = message.getChannel().getId();

        // put the ScheduleEntry thread into a HashMap by ID
        entriesGlobal.put(se.getId(), se);

        // put the id in guild mapping
        if( !entriesByGuild.containsKey( guildId ) )
        {
            ArrayList<Integer> entries = new ArrayList<>();
            entries.add(se.getId());
            entriesByGuild.put( guildId, entries );
        }
        else
            entriesByGuild.get( guildId ).add( se.getId() );

        // put the id in channel mapping
        if( !entriesByChannel.containsKey( channelId ))
        {
            ArrayList<Integer> entries = new ArrayList<>();
            entries.add(se.getId());
            entriesByChannel.put( channelId, entries );
        }
        else
            entriesByChannel.get( channelId ).add( se.getId() );

        // adjusts the displayed time til timer (since it is not set at creation)
        se.adjustTimer();

        // add the entry to buffer
        if( !se.hasStarted() )
        {
            long timeTil = ZonedDateTime.now().until(se.getStart(), ChronoUnit.SECONDS);
            if (timeTil <= 45 * 60)
                fineTimerBuff.add(se);
            else if (timeTil <= 32 * 60 * 60)
                coarseTimerBuff.add(se);
        }
        else
        {
            fineTimerBuff.add(se);
        }
    }

    /**
     * removes the entry from the mappings, does not delete the Message!
     * Only used internally by the scheduleManager
     *
     * @param eId integer Id
     */
    private void removeId( Integer eId )
    {
        ScheduleEntry se = this.getEntry( eId );
        if( se == null ) return;

        String gId = se.getMessage().getGuild().getId();
        String cId = se.getMessage().getChannel().getId();

        // remove entry from guild map
        entriesByGuild.get(gId).remove(eId);
        if (entriesByGuild.get(gId).isEmpty())
            entriesByGuild.remove(gId);

        // remove entry from guild map
        entriesByChannel.get(cId).remove(eId);
        if (entriesByChannel.get(cId).isEmpty())
            entriesByGuild.remove(cId);

        // remove entry from global map
        entriesGlobal.remove(eId);
    }

    /**
     * removes the entry from the mappings and the timer buffers
     *
     * @param eId integer Id
     */
    public void removeEntry( Integer eId )
    {
        ScheduleEntry se = this.getEntry( eId );
        if( se == null ) return;

        this.getFineTimerBuff().remove( se );
        this.getCoarseTimerBuff().remove( se );
        this.removeId( eId );
    }

    /**
     * regenerates the displayed Message text for a schedule entry
     *
     * @param eId integer Id
     */
    public void reloadEntry( Integer eId )
    {
        ScheduleEntry se = getEntry( eId );
        if( se == null ) return;

        String msg = ScheduleEntryParser.generate(se.getTitle(),se.getStart(),se.getEnd(),se.getComments(),se.getRepeat(),se.getId(),se.getMessage().getChannel().getId());
        //MessageUtilities.editMsg( msg, se.getMessage(), (ignored)-> se.adjustTimer());
        MessageUtilities.editMsg( msg, se.getMessage(), null);
    }

    /**
     * generates a valid and free schedule ID number. A ID my be requested, which will
     * be returned if the ID is free
     *
     * @param oldId integer Id
     * @return a free integer Id
     */
    public Integer newId( Integer oldId )
    {
        // try first to use the requested Id
        Integer ID;
        if (oldId == null)
            ID = (int) Math.ceil(Math.random() * (Math.pow(2, 16) - 1));
        else
            ID = oldId;

        // if the Id is in use, generate a new one until a free one is found
        while (entriesGlobal.containsKey(ID))
        {
            ID = (int) Math.ceil(Math.random() * (Math.pow(2, 16) - 1));
        }

        return ID;
    }

    public ScheduleEntry getEntry(Integer eId)
    {
        // check if entry exists, if so return it
        if( entriesGlobal.containsKey(eId) )
            return entriesGlobal.get(eId);

        else    // otherwise return null
            return null;
    }

    public ArrayList<Integer> getEntriesByGuild( String gId )
    {
        // check if guild exists in map, if so return their entries
        if( entriesByGuild.containsKey(gId) )
            return entriesByGuild.get(gId);

        else        // otherwise return null
            return new ArrayList<>();
    }

    public ArrayList<Integer> getEntriesByChannel( String cId )
    {
        // check if guild exists in map, if so return their entries
        if( entriesByChannel.containsKey(cId) )
            return entriesByChannel.get(cId);

        else        // otherwise return null
            return new ArrayList<>();
    }

    public Collection<Integer> getAllIds()
    {
        return entriesGlobal.keySet();
    }

    public Collection<ScheduleEntry> getAllEntries()
    {
        return entriesGlobal.values();
    }

    public Object getScheduleLock()
    {
        return scheduleLock;
    }

    public Collection<ScheduleEntry> getCoarseTimerBuff()
    {
        return coarseTimerBuff;
    }

    public Collection<ScheduleEntry> getFineTimerBuff()
    {
        return fineTimerBuff;
    }

    ExecutorService getExecutor()
    {
        return executor;
    }
}
