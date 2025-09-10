package com.depressy.boosted.commands;

import com.depressy.boosted.BoostedPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UnboostCommand implements CommandExecutor, TabCompleter {

    private final BoostedPlugin plugin;

    public UnboostCommand(BoostedPlugin plugin) {
        this.plugin = plugin;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boosted.unboost")) {
            sender.sendMessage(color("&cYou don't have permission to use this command."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(color("&eUsage: &7/unboost <boostName|all>"));
            return true;
        }

        String first = args[0];

        if (first.equalsIgnoreCase("all")) {
            int ended = plugin.getActiveBoostManager().endAll();
            sender.sendMessage(color("&aEnded &e" + ended + " &aactive boost instance(s)."));
            return true;
        }

        String name = first.toLowerCase(Locale.ROOT);
        if (!plugin.getBoostDefinitions().containsKey(name)) {
            sender.sendMessage(color("&cUnknown boost: &e" + name));
            return true;
        }

        int ended = plugin.getActiveBoostManager().endAllForBoost(name);
        if (ended <= 0) {
            sender.sendMessage(color("&eNo active instances found for &6" + name + "&e."));
        } else {
            sender.sendMessage(color("&aEnded &e" + ended + " &aactive instance(s) of &6" + name + "&a."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>(plugin.getBoostDefinitions().keySet());
            options.add("all");
            List<String> out = new ArrayList<>();
            for (String opt : options) {
                if (opt.toLowerCase(Locale.ROOT).startsWith(partial)) out.add(opt);
            }
            Collections.sort(out);
            return out;
        }
        return Collections.emptyList();
    }
}
