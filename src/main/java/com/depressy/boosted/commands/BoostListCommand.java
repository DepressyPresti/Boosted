package com.depressy.boosted.commands;

import com.depressy.boosted.ActiveBoostManager;
import com.depressy.boosted.BoostedPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BoostListCommand implements CommandExecutor {

    private final BoostedPlugin plugin;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public BoostListCommand(BoostedPlugin plugin) {
        this.plugin = plugin;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<ActiveBoostManager.ActiveEntry> list = plugin.getActiveBoostManager().getActiveEntries();
        if (list.isEmpty()) {
            sender.sendMessage(color("&7No active boosts."));
            return true;
        }
        sender.sendMessage(color("&eActive boosts (&6" + list.size() + "&e):"));
        long now = System.currentTimeMillis();
        for (ActiveBoostManager.ActiveEntry e : list) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(e.player);
            String name = (off != null && off.getName() != null) ? off.getName() : e.player.toString();
            long secs = Math.max(0L, (e.endAt - now) / 1000L);
            sender.sendMessage(color("&7- &e" + name + " &7-> &6" + e.boostName + " &7(" + secs + "s left)"));
        }
        return true;
    }
}
