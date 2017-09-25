package ws.nmathe.saber.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class CommandInfo
{
    public enum CommandType {ADMIN, CORE, GOOGLE, MISC}

    private String usage;
    private Map<String, String> usageExtended;
    private Collection<String> usageExamples;
    private CommandType commandCommandType;

    public CommandInfo(String usage, CommandType commandType)
    {
        this.usage = usage;
        this.usageExtended = new LinkedHashMap<>();
        this.usageExamples = new ArrayList<>();
        this.commandCommandType = commandType;
    }

    public String getUsage()
    {
        return usage;
    }

    public Map<String, String> getUsageExtended()
    {
        return this.usageExtended;
    }

    public Collection<String> getUsageExamples()
    {
        return this.usageExamples;
    }

    public CommandType getType()
    {
        return this.commandCommandType;
    }

    public CommandInfo addUsageExample(String example)
    {
        this.usageExamples.add(example);
        return this;
    }

    public CommandInfo addUsageCategory(String category, String content)
    {
        this.usageExtended.put(category, content);
        return this;
    }
}
