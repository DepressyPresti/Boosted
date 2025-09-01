package com.depressy.boosted.model;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BoostDefinition {

    public record CommandPair(String start, String end) {}

    private final String name;
    private final String group;
    private final String globalStartMessage;
    private final String globalEndMessage;
    private final List<CommandPair> commands;

    public BoostDefinition(String name, String group, String globalStartMessage, String globalEndMessage, List<CommandPair> commands) {
        this.name = name;
        this.group = group;
        this.globalStartMessage = globalStartMessage;
        this.globalEndMessage = globalEndMessage;
        this.commands = commands;
    }

    public String getName() { return name; }
    public String getGroup() { return group; }
    public String getGlobalStartMessage() { return globalStartMessage; }
    public String getGlobalEndMessage() { return globalEndMessage; }
    public List<CommandPair> getCommands() { return commands; }

    @SuppressWarnings("unchecked")
    public static BoostDefinition fromConfig(String name, String group, String startMsg, String endMsg, List<Map<?, ?>> list) {
        if (group == null || group.isEmpty()) return null;
        List<CommandPair> pairs = new ArrayList<>();
        if (list != null) {
            for (Map<?, ?> m : list) {
                Object s = m.get("start");
                Object e = m.get("end");
                String ss = s != null ? String.valueOf(s) : "";
                String ee = e != null ? String.valueOf(e) : "";
                pairs.add(new CommandPair(ss, ee));
            }
        }
        return new BoostDefinition(name, group, startMsg != null ? startMsg : "", endMsg != null ? endMsg : "", pairs);
    }
}
