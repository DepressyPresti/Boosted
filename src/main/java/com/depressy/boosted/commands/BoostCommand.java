package com.depressy.boosted.commands;

import com.depressy.boosted.ActiveBoostManager;
import com.depressy.boosted.BoostedPlugin;
import com.depressy.boosted.model.BoostDefinition;
import com.depressy.boosted.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class BoostCommand implements TabExecutor {

    private final BoostedPlugin plugin;

    public BoostCommand(BoostedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("boosted.admin")) {
                sender.sendMessage(color("&cYou don't have permission."));
                return true;
            }
            plugin.reloadBoostedConfig();
            sender.sendMessage(color("&aBoosted config reloaded."));
            return true;
        }

        if (!sender.hasPermission("boosted.admin")) {
            sender.sendMessage(color("&cYou don't have permission."));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(color("&eUsage: /boost <player|@s|@a> <boostName> <duration>"));
            return true;
        }

        String target = args[0];
        String boostName = args[1].toLowerCase();
        String durationRaw = String.join("", Arrays.copyOfRange(args, 2, args.length)); // allow spaces if user wrote "1h 30m"

        if (DurationParser.parseToMillis(durationRaw) <= 0) {
            sender.sendMessage(color("&cInvalid duration: &e" + durationRaw));
            return true;
        }

        BoostDefinition def = plugin.getBoostDefinitions().get(boostName);
        if (def == null) {
            sender.sendMessage(color("&cUnknown boost: &e" + boostName));
            return true;
        }

        List<Player> targets = new ArrayList<>();
        if (target.equalsIgnoreCase("@all")) {
            // All known players: online (respect bypass) + offline (no bypass check)
            java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("boosted.bypass")) {
                    targets.add(p);
                    seen.add(p.getUniqueId());
                }
            }
            for (org.bukkit.OfflinePlayer off : Bukkit.getOfflinePlayers()) {
                if (off.getUniqueId() == null) continue;
                if (seen.contains(off.getUniqueId())) continue; // skip online already added
                // schedule for offline directly via manager
                plugin.getActiveBoostManager().startBoostOffline(off, def, durationRaw);
            }
        } else if (target.equalsIgnoreCase("@a")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("boosted.bypass")) targets.add(p);
            }
        } else if (target.equalsIgnoreCase("@s")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(color("&c@s can only be used by a player."));
                return true;
            }
            Player p = (Player) sender;
            if (p.hasPermission("boosted.bypass")) {
                sender.sendMessage(color("&eYou have &6boosted.bypass&e; not applying boost."));
                return true;
            }
            targets.add(p);
        } else {
            Player p = Bukkit.getPlayerExact(target);
            if (p == null) {
                sender.sendMessage(color("&cPlayer not found or not online: &e" + target));
                return true;
            }
            if (p.hasPermission("boosted.bypass")) {
                sender.sendMessage(color("&e" + p.getName() + " has &6boosted.bypass&e; not applying boost."));
                return true;
            }
            targets.add(p);
        }

        ActiveBoostManager mgr = plugin.getActiveBoostManager();
        int before = mgr.countActiveFor(def.getName());
        for (Player p : targets) {
            mgr.startBoost(p, def, durationRaw);
        }
        int after = mgr.countActiveFor(def.getName());
        int delta = Math.max(0, after - before);
        sender.sendMessage(color("&aStarted &e" + def.getName() + " &afor &e" + delta + " &aplayer(s) for &e" + durationRaw + "&a."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("@a");
            base.add("@all");
            if (sender instanceof Player) base.add("@s");
            base.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            return partial(base, args[0]);
        } else if (args.length == 2) {
            return partial(new ArrayList<>(plugin.getBoostDefinitions().keySet()), args[1]);
        } else if (args.length == 3) {
            return partial(Arrays.asList("30m","1h","1h30m","2h","1d"), args[2]);
        } else if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }

    private List<String> partial(List<String> options, String token) {
        String t = token.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase().startsWith(t)) out.add(o);
        return out;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
