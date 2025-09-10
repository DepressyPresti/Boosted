package com.depressy.boosted;

import com.depressy.boosted.model.BoostDefinition;
import com.depressy.boosted.util.DurationParser;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ActiveBoostManager {

    public static class ActiveEntry {
        public final UUID player;
        public final String boostName;
        public final String group;
        public final long endAt;
        public final String durationRaw;
        public ActiveEntry(UUID player, String boostName, String group, long endAt, String durationRaw) {
            this.player = player;
            this.boostName = boostName;
            this.group = group;
            this.endAt = endAt;
            this.durationRaw = durationRaw;
        }
    }

    private final BoostedPlugin plugin;
    private final LuckPerms luckPerms;
    private final Map<String, BukkitTask> scheduled = new ConcurrentHashMap<>();
    private final Map<String, ActiveEntry> active = new ConcurrentHashMap<>();

    public ActiveBoostManager(BoostedPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = plugin.getLuckPerms();
    }

    private String key(String boostName, UUID uuid) {
        return boostName.toLowerCase() + ":" + uuid.toString();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void dispatchConsole(String cmd) {
        if (cmd == null || cmd.isEmpty()) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private String applyPlaceholders(String s, Player player, String boostName, String durationRaw) {
        if (s == null) return "";
        String out = s.replace("&", "§");
        if (player != null) {
            out = out.replace("%player%", player.getName())
                    .replace("%player_uuid%", player.getUniqueId().toString());
        }
        out = out.replace("%boost_name%", boostName)
                .replace("%duration%", durationRaw);
        return out;
    }

    private String applyPlaceholdersOffline(String s, String playerName, UUID uuid, String boostName, String durationRaw) {
        if (s == null) return "";
        String out = s.replace("&", "§");
        if (playerName != null) out = out.replace("%player%", playerName);
        if (uuid != null) out = out.replace("%player_uuid%", uuid.toString());
        out = out.replace("%boost_name%", boostName)
                .replace("%duration%", durationRaw);
        return out;
    }

    public List<ActiveEntry> getActiveEntries() {
        return new ArrayList<>(active.values());
    }

    public void startBoost(Player player, BoostDefinition def, String durationRaw) {
        if (player == null || def == null) return;
        if (player.hasPermission("boosted.bypass")) return;

        UUID uuid = player.getUniqueId();
        String k = key(def.getName(), uuid);
        cancelInternal(k, false);

        // LuckPerms: add temp parent (accumulate)
        dispatchConsole(String.format("lp user %s parent addtemp %s %s accumulate", uuid, def.getGroup(), durationRaw));

        // Run start commands
        for (BoostDefinition.CommandPair pair : def.getCommands()) {
            if (pair.start() != null && !pair.start().isEmpty()) {
                dispatchConsole(applyPlaceholders(pair.start(), player, def.getName(), durationRaw));
            }
        }

        // Per-boost sound (robust: model getters OR config fallback)
        tryPlayConfiguredSound(player, def);

        long durationMs = Math.max(1L, DurationParser.parseToMillis(durationRaw));
        long endAt = System.currentTimeMillis() + durationMs;
        long ticks = Math.max(1L, durationMs / 50L);
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() { endBoost(uuid, def, durationRaw, true); }
        }.runTaskLater(plugin, ticks);

        scheduled.put(k, task);
        active.put(k, new ActiveEntry(uuid, def.getName(), def.getGroup(), endAt, durationRaw));
        saveActiveSafe();
    }

    public void startBoostOffline(OfflinePlayer off, BoostDefinition def, String durationRaw) {
        if (off == null || off.getUniqueId() == null || def == null) return;
        if (hasBypassOffline(off.getUniqueId())) return;

        UUID uuid = off.getUniqueId();
        String k = key(def.getName(), uuid);
        cancelInternal(k, false);

        dispatchConsole(String.format("lp user %s parent addtemp %s %s accumulate", uuid, def.getGroup(), durationRaw));

        long durationMs = Math.max(1L, DurationParser.parseToMillis(durationRaw));
        long endAt = System.currentTimeMillis() + durationMs;
        long ticks = Math.max(1L, durationMs / 50L);
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() { endBoost(uuid, def, durationRaw, true); }
        }.runTaskLater(plugin, ticks);

        scheduled.put(k, task);
        active.put(k, new ActiveEntry(uuid, def.getName(), def.getGroup(), endAt, durationRaw));
        saveActiveSafe();
    }

    public int countActiveFor(String boostName) {
        int n = 0;
        for (ActiveEntry e : active.values()) if (e.boostName.equalsIgnoreCase(boostName)) n++;
        return n;
    }

    public int endAllForBoost(String boostName) {
        int ended = 0;
        List<String> keys = new ArrayList<>();
        for (var e : active.entrySet()) if (e.getValue().boostName.equalsIgnoreCase(boostName)) keys.add(e.getKey());
        for (String k : keys) {
            ActiveEntry entry = active.get(k);
            BoostDefinition def = plugin.getBoostDefinitions().get(entry.boostName.toLowerCase());
            if (def != null) endBoost(entry.player, def, entry.durationRaw, true);
            else {
                removeGroupCompletely(entry.player, resolveName(entry.player), entry.group);
                cancelInternal(k, true);
            }
            ended++;
        }
        saveActiveSafe();
        return ended;
    }

    public int endAll() {
        int ended = 0;
        List<String> keys = new ArrayList<>(active.keySet());
        for (String k : keys) {
            ActiveEntry entry = active.get(k);
            if (entry == null) continue;
            BoostDefinition def = plugin.getBoostDefinitions().get(entry.boostName.toLowerCase());
            if (def != null) endBoost(entry.player, def, entry.durationRaw, true);
            else {
                removeGroupCompletely(entry.player, resolveName(entry.player), entry.group);
                cancelInternal(k, true);
            }
            ended++;
        }
        saveActiveSafe();
        return ended;
    }

    public void endBoost(UUID uuid, BoostDefinition def, String durationRaw, boolean runEndActions) {
        String k = key(def.getName(), uuid);
        cancelInternal(k, true);

        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        Player online = (off != null) ? off.getPlayer() : null;

        if (runEndActions) {
            // LP removal via API (context-agnostic), UUID + name
            removeGroupCompletely(uuid, resolveName(uuid), def.getGroup());

            // Run end commands
            for (BoostDefinition.CommandPair pair : def.getCommands()) {
                if (pair.end() != null && !pair.end().isEmpty()) {
                    String cmd = (online != null)
                            ? applyPlaceholders(pair.end(), online, def.getName(), durationRaw)
                            : applyPlaceholdersOffline(pair.end(), resolveName(uuid) != null ? resolveName(uuid) : uuid.toString(), uuid, def.getName(), durationRaw);
                    dispatchConsole(cmd);
                }
            }

            // Per-boost end broadcast (uses per-boost or cfg fallback)
            String endMsg = getPerBoostEndMessage(def);
            if (endMsg != null && !endMsg.isEmpty()) {
                String msg = (online != null)
                        ? applyPlaceholders(endMsg, online, def.getName(), durationRaw)
                        : applyPlaceholdersOffline(endMsg, resolveName(uuid) != null ? resolveName(uuid) : uuid.toString(), uuid, def.getName(), durationRaw);
                Bukkit.broadcastMessage(color(msg));
            }
        }
    }

    private void cancelInternal(String key, boolean removeActive) {
        BukkitTask task = scheduled.remove(key);
        if (task != null) task.cancel();
        if (removeActive) active.remove(key);
    }

    public void saveActiveToDisk() throws IOException {
        File f = plugin.getDataFile("active.yml");
        YamlConfiguration y = new YamlConfiguration();
        for (var e : active.entrySet()) {
            ActiveEntry a = e.getValue();
            y.set(e.getKey() + ".player", a.player.toString());
            y.set(e.getKey() + ".boost", a.boostName);
            y.set(e.getKey() + ".group", a.group);
            y.set(e.getKey() + ".endAt", a.endAt);
            y.set(e.getKey() + ".duration", a.durationRaw);
        }
        y.save(f);
    }

    private void saveActiveSafe() {
        try { saveActiveToDisk(); }
        catch (Exception ex) { plugin.getLogger().log(Level.WARNING, "Failed to persist active boosts", ex); }
    }

    public void loadAndResumeFromDisk() throws IOException {
        File f = plugin.getDataFile("active.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String k : y.getKeys(false)) {
            String playerStr = y.getString(k + ".player");
            String boost = y.getString(k + ".boost");
            String group = y.getString(k + ".group");
            long endAt = y.getLong(k + ".endAt", 0L);
            String durationRaw = y.getString(k + ".duration", "1m");
            if (playerStr == null || boost == null || endAt <= System.currentTimeMillis()) continue;

            UUID uuid; try { uuid = UUID.fromString(playerStr); } catch (Exception ex) { continue; }
            BoostDefinition def = plugin.getBoostDefinitions().get(boost.toLowerCase());
            if (def != null && (group == null || group.isEmpty())) group = def.getGroup();

            long remainingMs = Math.max(1L, endAt - System.currentTimeMillis());
            long ticks = Math.max(1L, remainingMs / 50L);
            final BoostDefinition fDef = def;
            final String fGroup = (group != null ? group : (def != null ? def.getGroup() : ""));
            BukkitTask task = new BukkitRunnable() {
                @Override public void run() {
                    if (fDef != null) endBoost(uuid, fDef, durationRaw, true);
                    else {
                        removeGroupCompletely(uuid, resolveName(uuid), fGroup);
                        cancelInternal(key(boost, uuid), true);
                    }
                }
            }.runTaskLater(plugin, ticks);

            scheduled.put(k, task);
            active.put(k, new ActiveEntry(uuid, (def != null ? def.getName() : boost), fGroup, endAt, durationRaw));
        }
    }

    private boolean hasBypassOffline(UUID uuid) {
        if (luckPerms == null) return false;
        try {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) return false;
            QueryOptions qo = luckPerms.getContextManager().getQueryOptions(user)
                    .orElse(luckPerms.getContextManager().getStaticQueryOptions());
            return user.getCachedData().getPermissionData(qo).checkPermission("boosted.bypass").asBoolean();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "LuckPerms offline bypass check failed for " + uuid, t);
            return false;
        }
    }

    private String resolveName(UUID uuid) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return (off != null && off.getName() != null) ? off.getName() : null;
    }

    private String getPerBoostEndMessage(BoostDefinition def) {
        // Try def.getGlobalEndMessage() if exists
        try {
            Method m = def.getClass().getMethod("getGlobalEndMessage");
            Object o = m.invoke(def);
            if (o != null) return String.valueOf(o);
        } catch (Throwable ignored) {}
        // Fallback to config: boosts.<name>.global_end_message
        String path = "boosts." + def.getName() + ".global_end_message";
        return plugin.getConfig().getString(path, "");
    }

    /** Plays per-boost sound; tries model getters then config fallback. */
    private void tryPlayConfiguredSound(Player player, BoostDefinition def) {
        if (player == null || def == null) return;
        String name = null;
        float vol = 1.0f, pit = 1.0f;
        try {
            // model getters via reflection (works whether or not they exist)
            try {
                Method m = def.getClass().getMethod("getSoundName");
                Object o = m.invoke(def);
                if (o != null) name = String.valueOf(o);
            } catch (Throwable ignored) {}
            try {
                Method m = def.getClass().getMethod("getSoundVolume");
                Object o = m.invoke(def);
                if (o != null) vol = Float.parseFloat(String.valueOf(o));
            } catch (Throwable ignored) {}
            try {
                Method m = def.getClass().getMethod("getSoundPitch");
                Object o = m.invoke(def);
                if (o != null) pit = Float.parseFloat(String.valueOf(o));
            } catch (Throwable ignored) {}

            // config fallback: boosts.<name>.sound
            if (name == null || name.isEmpty()) {
                String base = "boosts." + def.getName() + ".sound.";
                name = plugin.getConfig().getString(base + "name", null);
                if (plugin.getConfig().isSet(base + "volume")) {
                    try { vol = Float.parseFloat(String.valueOf(plugin.getConfig().get(base + "volume"))); } catch (Throwable ignored) {}
                }
                if (plugin.getConfig().isSet(base + "pitch")) {
                    try { pit = Float.parseFloat(String.valueOf(plugin.getConfig().get(base + "pitch"))); } catch (Throwable ignored) {}
                }
            }

            if (name != null && !name.isEmpty()) {
                org.bukkit.Sound s = org.bukkit.Sound.valueOf(name.toUpperCase());
                player.playSound(player.getLocation(), s, vol, pit);
            }
        } catch (IllegalArgumentException bad) {
            plugin.getLogger().warning("Invalid sound for " + def.getName() + ": " + name);
        } catch (Throwable ignored) {
            // stay silent if fields absent
        }
    }

    /** Remove ALL LP group nodes for this user (any contexts), by UUID and name. */
    private void removeGroupCompletely(UUID uuid, String nameMaybe, String group) {
        if (luckPerms == null || group == null || group.isEmpty()) return;

        java.util.function.Function<UUID, Integer> remover = (theUuid) -> {
            int removed = 0;
            try {
                User u = luckPerms.getUserManager().loadUser(theUuid).join();
                if (u == null) return 0;
                List<Node> toRemove = new ArrayList<>();
                for (Node node : new ArrayList<>(u.getNodes())) {
                    if (node instanceof InheritanceNode in && in.getGroupName().equalsIgnoreCase(group)) {
                        toRemove.add(node);
                    }
                }
                for (Node n : toRemove) {
                    if (u.data().remove(n).wasSuccessful()) removed++;
                    u.transientData().remove(n);
                }
                if (removed > 0) luckPerms.getUserManager().saveUser(u);
            } catch (Throwable ignored) {}
            return removed;
        };

        int total = remover.apply(uuid);
        if (nameMaybe != null && !nameMaybe.isEmpty()) {
            try {
                UUID byName = luckPerms.getUserManager().lookupUniqueId(nameMaybe).join();
                if (byName != null) total += remover.apply(byName);
            } catch (Throwable ignored) {}
        }
        if (total > 0) plugin.getLogger().info("[Boosted] Removed " + total + " '" + group + "' node(s) for " + uuid + (nameMaybe != null ? " (" + nameMaybe + ")" : ""));
    }
}
