package com.depressy.boosted.model;

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
    private final String soundName;
    private final float soundVolume;
    private final float soundPitch;

    public BoostDefinition(String name,
                           String group,
                           String globalStartMessage,
                           String globalEndMessage,
                           List<CommandPair> commands,
                           String soundName,
                           float soundVolume,
                           float soundPitch) {
        this.name = name;
        this.group = group;
        this.globalStartMessage = globalStartMessage;
        this.globalEndMessage = globalEndMessage;
        this.commands = commands;
        this.soundName = soundName;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
    }

    public String getName() { return name; }
    public String getGroup() { return group; }
    public String getGlobalStartMessage() { return globalStartMessage; }
    public String getGlobalEndMessage() { return globalEndMessage; }
    public List<CommandPair> getCommands() { return commands; }
    public String getSoundName() { return soundName; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }

    @SuppressWarnings("unchecked")
    public static BoostDefinition fromConfig(String name,
                                             String group,
                                             String startMsg,
                                             String endMsg,
                                             List<Map<?, ?>> list,
                                             Map<String, Object> soundSection) {
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

        String sName = null;
        float sVol = 1.0f;
        float sPit = 1.0f;
        if (soundSection != null) {
            Object n = soundSection.get("name");
            Object v = soundSection.get("volume");
            Object p = soundSection.get("pitch");
            if (n != null) sName = String.valueOf(n);
            if (v != null) {
                try { sVol = Float.parseFloat(String.valueOf(v)); } catch (Exception ignored) {}
            }
            if (p != null) {
                try { sPit = Float.parseFloat(String.valueOf(p)); } catch (Exception ignored) {}
            }
        }

        return new BoostDefinition(
                name,
                group,
                startMsg != null ? startMsg : "",
                endMsg != null ? endMsg : "",
                pairs,
                sName,
                sVol,
                sPit
        );
    }
}
