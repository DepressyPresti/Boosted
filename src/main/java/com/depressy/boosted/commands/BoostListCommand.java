package com.depressy.boosted.commands;

import com.depressy.boosted.ActiveBoostManager;
import com.depressy.boosted.BoostedPlugin;
import com.depressy.boosted.util.DurationFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.*;
import java.util.stream.Collectors;

public class BoostListCommand implements TabExecutor {

    private final BoostedPlugin plugin;

    public BoostListCommand(BoostedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boosted.admin")) {
            sender.sendMessage(color("&cYou don't have permission."));
            return true;
        }

        Collection<ActiveBoostManager.ActiveEntry> entries = plugin.getActiveBoostManager().getActiveEntries();
        if (args.length == 0) {
            if (entries.isEmpty()) {
                sender.sendMessage(color("&7No active boosts."));
                return true;
            }
            long now = System.currentTimeMillis();

            // Group by boost name
            Map<String, List<ActiveBoostManager.ActiveEntry>> grouped = new HashMap<>();
            for (ActiveBoostManager.ActiveEntry e : entries) {
                grouped.computeIfAbsent(e.boostName.toLowerCase(), k -> new ArrayList<>()).add(e);
            }

            // Sort groups by soonest ending entry
            List<Map.Entry<String, List<ActiveBoostManager.ActiveEntry>>> list = new ArrayList<>(grouped.entrySet());
            list.sort(Comparator.comparingLong(o -> o.getValue().stream().mapToLong(x -> x.endAt).min().orElse(Long.MAX_VALUE)));

            sender.sendMessage(color("&6Active Boosts (Grouped):"));
            for (Map.Entry<String, List<ActiveBoostManager.ActiveEntry>> grp : list) {
                String boost = grp.getKey();
                List<ActiveBoostManager.ActiveEntry> g = grp.getValue();
                int count = g.size();
                long minRemaining = g.stream().mapToLong(x -> Math.max(0L, x.endAt - now)).min().orElse(0L);
                String line = "&b" + boost + " &7— &e" + count + " &7player" + (count==1?"":"s") + ", &f~&a" + DurationFormat.format(minRemaining) + " &7left"
                        + " &8(/boostlist " + boost + "&8)";
                sender.sendMessage(color(line));
            }
            return true;
        } else {
            String targetBoost = args[0].toLowerCase();
            List<ActiveBoostManager.ActiveEntry> filtered = entries.stream()
                    .filter(e -> e.boostName.equalsIgnoreCase(targetBoost))
                    .sorted(Comparator.comparingLong(e -> e.endAt))
                    .collect(Collectors.toList());

            if (filtered.isEmpty()) {
                sender.sendMessage(color("&7No active entries for &e" + targetBoost + "&7."));
                return true;
            }

            long now = System.currentTimeMillis();
            sender.sendMessage(color("&6Active: &b" + targetBoost));
            for (ActiveBoostManager.ActiveEntry e : filtered) {
                long remaining = Math.max(0L, e.endAt - now);
                OfflinePlayer off = Bukkit.getOfflinePlayer(e.player);
                String name = off.getName() != null ? off.getName() : e.player.toString();
                String line = "&e" + name + " &7(" + DurationFormat.format(remaining) + " left)";
                sender.sendMessage(color(line));
            }
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>(plugin.getBoostDefinitions().keySet());
            // Also include any currently-active boost keys that might not be in config anymore
            for (ActiveBoostManager.ActiveEntry e : plugin.getActiveBoostManager().getActiveEntries()) {
                String k = e.boostName.toLowerCase();
                if (!names.contains(k)) names.add(k);
            }
            Collections.sort(names);
            String t = args[0].toLowerCase();
            return names.stream().filter(n -> n.startsWith(t)).collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
