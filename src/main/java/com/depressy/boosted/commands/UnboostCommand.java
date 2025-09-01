package com.depressy.boosted.commands;

import com.depressy.boosted.BoostedPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UnboostCommand implements TabExecutor {

    private final BoostedPlugin plugin;

    public UnboostCommand(BoostedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boosted.admin")) {
            sender.sendMessage(color("&cYou don't have permission."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(color("&eUsage: /unboost <boostName>"));
            return true;
        }
        String name = args[0].toLowerCase();
        if (!plugin.getBoostDefinitions().containsKey(name)) {
            sender.sendMessage(color("&cUnknown boost: &e" + name));
            return true;
        }
        int ended = plugin.getActiveBoostManager().endAllForBoost(name);
        sender.sendMessage(color("&aEnded &e" + ended + " &aactive instance(s) of &e" + name + "&a."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(plugin.getBoostDefinitions().keySet());
            return partial(opts, args[0]);
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
