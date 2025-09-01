package com.depressy.boosted;

import com.depressy.boosted.model.BoostDefinition;
import com.depressy.boosted.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// LuckPerms (soft) for offline bypass checks
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

public class ActiveBoostManager {

    public static class ActiveEntry {
        public UUID player;
        public String boostName;
        public long endAt; // epoch millis
        public String durationRaw;

        public ActiveEntry(UUID player, String boostName, long endAt, String durationRaw) {
            this.player = player;
            this.boostName = boostName;
            this.endAt = endAt;
            this.durationRaw = durationRaw;
        }
    }

    private final BoostedPlugin plugin;
    private final LuckPerms luckPerms;
    // key = boostName(lower) + ":" + uuid
    private final Map<String, BukkitTask> scheduled = new ConcurrentHashMap<>();
    private final Map<String, ActiveEntry> active = new ConcurrentHashMap<>();

    public ActiveBoostManager(BoostedPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = plugin.getLuckPerms();
    }

    private String key(String boostName, UUID uuid) {
        return boostName.toLowerCase() + ":" + uuid;
    }

    public boolean isActive(String boostName, UUID uuid) {
        return active.containsKey(key(boostName, uuid));
    }

    public int countActiveFor(String boostName) {
        int c = 0;
        for (ActiveEntry e : active.values()) {
            if (e.boostName.equalsIgnoreCase(boostName)) c++;
        }
        return c;
    }

    public void startBoost(Player player, BoostDefinition def, String durationRaw) {
        long durationMs = DurationParser.parseToMillis(durationRaw);
        if (durationMs <= 0) {
            player.sendMessage(color("&cInvalid duration: &e" + durationRaw));
            return;
        }
        long endAt = System.currentTimeMillis() + durationMs;
        UUID uuid = player.getUniqueId();
        String k = key(def.getName(), uuid);

        // If already active for this player+boost, replace existing schedule with new end time
        cancelInternal(k, false);

        // Broadcast start
        String startMsg = def.getGlobalStartMessage();
        if (startMsg != null && !startMsg.isEmpty()) {
            Bukkit.broadcastMessage(color(applyPlaceholders(startMsg, player, def.getName(), durationRaw)));
        }

        // LuckPerms temp parent add
        String lpAddTemp = String.format("lp user %s parent addtemp %s %s accumulate", uuid, def.getGroup(), durationRaw);
        dispatchConsole(lpAddTemp);

        // Run start commands
        for (BoostDefinition.CommandPair pair : def.getCommands()) {
            if (pair.start() != null && !pair.start().isEmpty()) {
                String cmd = applyPlaceholders(pair.start(), player, def.getName(), durationRaw);
                dispatchConsole(cmd);
            }
        }

        // Schedule end
        long ticks = Math.max(1L, durationMs / 50L);
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                endBoost(uuid, def, durationRaw, true);
            }
        }.runTaskLater(plugin, ticks);

        scheduled.put(k, task);
        active.put(k, new ActiveEntry(uuid, def.getName(), endAt, durationRaw));
        saveActiveSafe();
    }

    public void startBoostOffline(OfflinePlayer off, BoostDefinition def, String durationRaw) {
        if (off == null || off.getUniqueId() == null) return;
        if (hasBypassOffline(off.getUniqueId())) return;

        long durationMs = DurationParser.parseToMillis(durationRaw);
        if (durationMs <= 0) {
            if (off.getPlayer() != null) {
                off.getPlayer().sendMessage(color("&cInvalid duration: &e" + durationRaw));
            }
            return;
        }
        long endAt = System.currentTimeMillis() + durationMs;
        UUID uuid = off.getUniqueId();
        String k = key(def.getName(), uuid);

        cancelInternal(k, false);

        String startMsg = def.getGlobalStartMessage();
        if (startMsg != null && !startMsg.isEmpty()) {
            String applied = applyPlaceholdersOffline(startMsg,
                    off.getName() != null ? off.getName() : uuid.toString(),
                    uuid, def.getName(), durationRaw);
            Bukkit.broadcastMessage(color(applied));
        }

        String lpAddTemp = String.format("lp user %s parent addtemp %s %s accumulate", uuid, def.getGroup(), durationRaw);
        dispatchConsole(lpAddTemp);

        for (BoostDefinition.CommandPair pair : def.getCommands()) {
            if (pair.start() != null && !pair.start().isEmpty()) {
                String cmd = applyPlaceholdersOffline(pair.start(),
                        off.getName() != null ? off.getName() : uuid.toString(),
                        uuid, def.getName(), durationRaw);
                dispatchConsole(cmd);
            }
        }

        long ticks = Math.max(1L, durationMs / 50L);
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                endBoost(uuid, def, durationRaw, true);
            }
        }.runTaskLater(plugin, ticks);

        scheduled.put(k, task);
        active.put(k, new ActiveEntry(uuid, def.getName(), endAt, durationRaw));
        saveActiveSafe();
    }

    public int endAllForBoost(String boostName) {
        int ended = 0;
        List<String> toEnd = new ArrayList<>();
        for (Map.Entry<String, ActiveEntry> e : active.entrySet()) {
            if (e.getValue().boostName.equalsIgnoreCase(boostName)) {
                toEnd.add(e.getKey());
            }
        }
        for (String k : toEnd) {
            ActiveEntry entry = active.get(k);
            BoostDefinition def = plugin.getBoostDefinitions().get(entry.boostName.toLowerCase());
            if (def != null) {
                endBoost(entry.player, def, entry.durationRaw, true);
                ended++;
            } else {
                cancelInternal(k, true);
            }
        }
        saveActiveSafe();
        return ended;
    }

    public void endBoost(UUID uuid, BoostDefinition def, String durationRaw, boolean runEndActions) {
        String k = key(def.getName(), uuid);
        cancelInternal(k, true);

        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        Player online = off != null ? off.getPlayer() : null;

        if (runEndActions) {
            // LuckPerms parent remove (harmless if already removed by temp expiry)
            String lpRemove = String.format("lp user %s parent remove %s", uuid, def.getGroup());
            dispatchConsole(lpRemove);

            // Run end commands
            for (BoostDefinition.CommandPair pair : def.getCommands()) {
                if (pair.end() != null && !pair.end().isEmpty()) {
                    String cmd;
                    if (online != null) {
                        cmd = applyPlaceholders(pair.end(), online, def.getName(), durationRaw);
                    } else {
                        String playerName = off != null && off.getName() != null ? off.getName() : uuid.toString();
                        cmd = applyPlaceholdersOffline(pair.end(), playerName, uuid, def.getName(), durationRaw);
                    }
                    dispatchConsole(cmd);
                }
            }

            // Broadcast end
            String endMsg = def.getGlobalEndMessage();
            if (endMsg != null && !endMsg.isEmpty()) {
                String finalMsg;
                if (online != null) {
                    finalMsg = applyPlaceholders(endMsg, online, def.getName(), durationRaw);
                } else {
                    String playerName = off != null && off.getName() != null ? off.getName() : uuid.toString();
                    finalMsg = applyPlaceholdersOffline(endMsg, playerName, uuid, def.getName(), durationRaw);
                }
                Bukkit.broadcastMessage(color(finalMsg));
            }
        }

        saveActiveSafe();
    }

    private void cancelInternal(String key, boolean removeActive) {
        BukkitTask task = scheduled.remove(key);
        if (task != null) task.cancel();
        if (removeActive) active.remove(key);
    }

    private void dispatchConsole(String cmd) {
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
        return s.replace("&", "§")
                .replace("%player%", playerName != null ? playerName : uuid.toString())
                .replace("%player_uuid%", uuid.toString())
                .replace("%boost_name%", boostName)
                .replace("%duration%", durationRaw);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /* -------------------- Persistence -------------------- */

    public void saveActiveToDisk() throws IOException {
        File f = plugin.getDataFile("active_boosts.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ActiveEntry e : active.values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("player", e.player.toString());
            m.put("boost", e.boostName);
            m.put("endAt", e.endAt);
            m.put("duration", e.durationRaw);
            list.add(m);
        }
        yaml.set("active", list);
        yaml.save(f);
    }

    private void saveActiveSafe() {
        try {
            saveActiveToDisk();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save active boosts", ex);
        }
    }

    public void loadAndResumeFromDisk() throws IOException {
        File f = plugin.getDataFile("active_boosts.yml");
        if (!f.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
        List<Map<?, ?>> list = yaml.getMapList("active");
        long now = System.currentTimeMillis();
        for (Map<?, ?> m : list) {
            try {
                UUID uuid = UUID.fromString(String.valueOf(m.get("player")));
                String boost = String.valueOf(m.get("boost"));
                long endAt = Long.parseLong(String.valueOf(m.get("endAt")));
                String durationRaw = String.valueOf(m.get("duration"));
                BoostDefinition def = plugin.getBoostDefinitions().get(boost.toLowerCase());
                if (def == null) continue;

                long remaining = endAt - now;
                if (remaining <= 0) {
                    // Fire end immediately
                    endBoost(uuid, def, durationRaw, true);
                } else {
                    String k = key(def.getName(), uuid);
                    long ticks = Math.max(1L, remaining / 50L);
                    BukkitTask task = new BukkitRunnable() {
                        @Override public void run() {
                            endBoost(uuid, def, durationRaw, true);
                        }
                    }.runTaskLater(plugin, ticks);
                    scheduled.put(k, task);
                    active.put(k, new ActiveEntry(uuid, def.getName(), endAt, durationRaw));
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Bad active boost entry: " + m, ex);
            }
        }
    }

    public java.util.Collection<ActiveEntry> getActiveEntries() {
        return new java.util.ArrayList<>(active.values());
    }

    private boolean hasBypassOffline(UUID uuid) {
        if (luckPerms == null) return false; // LP not present: can't check offline bypass
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
}
