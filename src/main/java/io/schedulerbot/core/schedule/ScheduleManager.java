package io.schedulerbot.core.schedule;

import io.schedulerbot.utils.MessageUtilities;
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
 */
public class ScheduleManager
{
    public static final ExecutorService scheduleExec = Executors.newCachedThreadPool();     // thread pool for schedule tasks
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

    public void addEntry( Message message )
    {
        ScheduleEntry se = ScheduleEntryParser.parse( message );
        if( se == null ) return;

        String guildId = message.getGuild().getId();

        // put the ScheduleEntry thread into a HashMap by ID
        entriesGlobal.put(se.eID, se);

        // put the id in guild mapping
        if( !entriesByGuild.containsKey( guildId ) )
        {
            ArrayList<Integer> entries = new ArrayList<>();
            entries.add(se.eID);
            entriesByGuild.put( guildId, entries );
        }
        else
            entriesByGuild.get( guildId ).add( se.eID );

        // put the id in channel mapping
        if( !entriesByChannel.containsKey( message.getChannel().getId() ))
        {
            ArrayList<Integer> entries = new ArrayList<>();
            entries.add(se.eID);
            entriesByChannel.put( message.getChannel().getId(), entries );
        }
        else
            entriesByChannel.get( message.getChannel().getId() ).add( se.eID );

        // adjusts the displayed time til timer (since it is not set at creation)
        se.adjustTimer();

        // add the entry to buffer
        if( !se.startFlag )
        {
            long timeTil = ZonedDateTime.now().until(se.eStart, ChronoUnit.SECONDS);
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

    public void removeId( Integer eId )
    {
        ScheduleEntry se = this.getEntry( eId );
        if( se == null ) return;
        String gId = se.eMsg.getGuild().getId();
        String cId = se.eMsg.getChannel().getId();

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

    public void removeEntry( Integer eId )
    {
        ScheduleEntry se = this.getEntry( eId );
        if( se == null ) return;

        this.getFineTimerBuff().remove( se );
        this.getCoarseTimerBuff().remove( se );
        this.removeId( eId );
    }

    public void reloadEntry( Integer eId )
    {
        ScheduleEntry se = getEntry( eId );
        String msg = ScheduleEntryParser.generate(se.eTitle,se.eStart,se.eEnd,se.eComments,se.eRepeat,se.eID,se.eMsg.getGuild().getId());
        MessageUtilities.editMsg( msg, se.eMsg, this::addEntry);
    }

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
            return null;
    }

    public ArrayList<Integer> getEntriesByChannel( String cId )
    {
        // check if guild exists in map, if so return their entries
        if( entriesByChannel.containsKey(cId) )
            return entriesByChannel.get(cId);

        else        // otherwise return null
            return null;
    }

    public Collection<Integer> getAllEntries()
    {
        return entriesGlobal.values().stream()
                .map(entry -> entry.eID).collect(Collectors.toCollection(ArrayList::new));
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
}
