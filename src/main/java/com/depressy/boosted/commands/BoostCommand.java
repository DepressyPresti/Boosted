package com.depressy.boosted.commands;

import com.depressy.boosted.ActiveBoostManager;
import com.depressy.boosted.BoostedPlugin;
import com.depressy.boosted.model.BoostDefinition;
import com.depressy.boosted.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

public class BoostCommand implements CommandExecutor, TabCompleter {

    private final BoostedPlugin plugin;

    public BoostCommand(BoostedPlugin plugin) {
        this.plugin = plugin;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String getPerBoostStartMessage(BoostDefinition def) {
        // Prefer model getter if present
        try {
            Method m = def.getClass().getMethod("getGlobalStartMessage");
            Object o = m.invoke(def);
            if (o != null) return String.valueOf(o);
        } catch (Throwable ignored) {}
        // Fallback to config
        String path = "boosts." + def.getName() + ".global_start_message";
        return plugin.getConfig().getString(path, "");
    }

    private String replaceBothPlaceholders(String msg, String activator, String boostName, String duration, String targetName) {
        if (msg == null) return "";
        // legacy %...%:
        msg = msg.replace("%player%", activator)
                .replace("%boost_name%", boostName)
                .replace("%duration%", duration);
        if (targetName != null) msg = msg.replace("%target%", targetName);
        // new {...}:
        msg = msg.replace("{player}", activator)
                .replace("{boost}", boostName)
                .replace("{time}", duration);
        if (targetName != null) msg = msg.replace("{target}", targetName);
        return msg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boosted.boost")) {
            sender.sendMessage(color("&cYou don't have permission to use this command."));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(color("&eUsage: &7/boost <@all|player> <boostName> <duration>"));
            return true;
        }

        String target = args[0];
        String boostName = args[1].toLowerCase(Locale.ROOT);
        String durationRaw = args[2];

        Map<String, BoostDefinition> defs = plugin.getBoostDefinitions();
        if (!defs.containsKey(boostName)) {
            sender.sendMessage(color("&cUnknown boost: &e" + boostName));
            return true;
        }
        BoostDefinition def = defs.get(boostName);

        long ms = DurationParser.parseToMillis(durationRaw);
        if (ms <= 0) {
            sender.sendMessage(color("&cInvalid duration: &e" + durationRaw));
            return true;
        }

        List<Player> targets = new ArrayList<>();
        boolean isAll = target.equalsIgnoreCase("@all") || target.equalsIgnoreCase("@a");
        if (isAll) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("boosted.bypass")) targets.add(p);
            }
        } else if (target.equalsIgnoreCase("@s") && sender instanceof Player) {
            targets.add((Player) sender);
        } else {
            Player p = Bukkit.getPlayerExact(target);
            if (p != null) {
                targets.add(p);
            } else {
                // try offline
                OfflinePlayer off = Bukkit.getOfflinePlayer(target);
                if (off != null && off.hasPlayedBefore()) {
                    plugin.getActiveBoostManager().startBoostOffline(off, def, durationRaw);
                    sender.sendMessage(color("&aQueued offline boost for &e" + off.getName() + "&a."));
                } else {
                    sender.sendMessage(color("&cPlayer not found: &e" + target));
                }
            }
        }

        ActiveBoostManager mgr = plugin.getActiveBoostManager();

        // if @all, also queue offline players (unique, not currently online)
        if (isAll) {
            HashSet<UUID> seen = new HashSet<>();
            for (Player pSeen : targets) seen.add(pSeen.getUniqueId());
            for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
                try {
                    if (off == null || off.getUniqueId() == null) continue;
                    if (seen.contains(off.getUniqueId())) continue; // already boosted online
                    if (!off.hasPlayedBefore()) continue;
                    mgr.startBoostOffline(off, def, durationRaw); // bypass handled inside
                } catch (Throwable ignored) {}
            }
        }

        // boost online targets now
        for (Player p : targets) {
            mgr.startBoost(p, def, durationRaw);
        }

        // single broadcast (not per player)
        String activator = (sender instanceof Player) ? ((Player) sender).getName() : "Console";
        String perBoostStart = getPerBoostStartMessage(def);

        if (isAll) {
            String msg;
            if (perBoostStart != null && !perBoostStart.isEmpty()) {
                msg = replaceBothPlaceholders(perBoostStart, activator, def.getName(), durationRaw, null);
                for (String line : msg.split("\\n")) Bukkit.broadcastMessage(color(line));
            } else {
                msg = plugin.getConfig().getString("messages.broadcast_all_start",
                        "&a{player} &7has boosted &6everyone &7with &e{boost} &7for &b{time}&7!\n&7Make sure to thank them!");
                msg = replaceBothPlaceholders(msg, activator, def.getName(), durationRaw, null);
                for (String line : msg.split("\\n")) Bukkit.broadcastMessage(color(line));
            }
        } else if (!targets.isEmpty()) {
            String tName = (targets.size() == 1 ? targets.get(0).getName() : target);
            String msg;
            if (perBoostStart != null && !perBoostStart.isEmpty()) {
                msg = replaceBothPlaceholders(perBoostStart, activator, def.getName(), durationRaw, tName);
                Bukkit.broadcastMessage(color(msg));
            } else {
                msg = plugin.getConfig().getString("messages.broadcast_single_start",
                        "&a{player} &7boosted &e{target} &7with &6{boost} &7for &b{time}&7!");
                msg = replaceBothPlaceholders(msg, activator, def.getName(), durationRaw, tName);
                Bukkit.broadcastMessage(color(msg));
            }
        }

        sender.sendMessage(color("&aApplied &6" + def.getName() + " &afor &b" + durationRaw + "&a to &e" + (isAll ? "everyone" : target) + "&a."));
        return true;
    }

    // -------- Tab Complete ----------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            out.add("@all"); out.add("@a"); out.add("@s");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(partial));
            Collections.sort(out);
            return out;
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            out.addAll(plugin.getBoostDefinitions().keySet());
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(partial));
            Collections.sort(out);
            return out;
        }
        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            for (String p : Arrays.asList("5m","10m","15m","30m","1h","2h","1d")) {
                if (p.startsWith(partial)) out.add(p);
            }
            return out;
        }
        return Collections.emptyList();
    }
}
