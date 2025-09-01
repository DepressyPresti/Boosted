package com.depressy.boosted;

import com.depressy.boosted.commands.BoostCommand;
import com.depressy.boosted.commands.BoostListCommand;
import com.depressy.boosted.commands.UnboostCommand;
import com.depressy.boosted.model.BoostDefinition;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.RegisteredServiceProvider;

public class BoostedPlugin extends JavaPlugin {

    private final Map<String, BoostDefinition> boostDefinitions = new HashMap<>();
    private ActiveBoostManager activeBoostManager;
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadBoostDefinitions();

        try {
            RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) luckPerms = provider.getProvider();
        } catch (Throwable ignored) {}

        activeBoostManager = new ActiveBoostManager(this);

        // Commands
        BoostCommand boostCmd = new BoostCommand(this);
        getCommand("boost").setExecutor(boostCmd);
        getCommand("boost").setTabCompleter(boostCmd);

        UnboostCommand unboostCmd = new UnboostCommand(this);
        getCommand("unboost").setExecutor(unboostCmd);
        getCommand("unboost").setTabCompleter(unboostCmd);

        BoostListCommand boostListCmd = new BoostListCommand(this);
        getCommand("boostlist").setExecutor(boostListCmd);
        getCommand("boostlist").setTabCompleter(boostListCmd);

        // Resume active boosts from file
        try {
            activeBoostManager.loadAndResumeFromDisk();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to resume active boosts", ex);
        }

        getLogger().info("Boosted enabled.");
    }

    @Override
    public void onDisable() {
        // Persist active boosts to disk (they resume automatically next start)
        try {
            activeBoostManager.saveActiveToDisk();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to save active boosts", ex);
        }
        getLogger().info("Boosted disabled.");
    }

    public void reloadBoostedConfig() {
        reloadConfig();
        loadBoostDefinitions();
    }

    private void loadBoostDefinitions() {
        boostDefinitions.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("boosts");
        if (sec == null) {
            getLogger().warning("No 'boosts' section found in config!");
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection b = sec.getConfigurationSection(key);
            if (b == null) continue;
            String group = b.getString("group", "").trim();
            String startMsg = b.getString("global_start_message", "");
            String endMsg = b.getString("global_end_message", "");
            BoostDefinition def = BoostDefinition.fromConfig(key, group, startMsg, endMsg, b.getMapList("commands"));
            if (def != null) {
                boostDefinitions.put(key.toLowerCase(), def);
            } else {
                getLogger().warning("Invalid boost config for key: " + key);
            }
        }
        getLogger().info("Loaded " + boostDefinitions.size() + " boost definition(s).");
    }

    public Map<String, BoostDefinition> getBoostDefinitions() {
        return boostDefinitions;
    }

    public LuckPerms getLuckPerms() { return luckPerms; }

    public ActiveBoostManager getActiveBoostManager() {
        return activeBoostManager;
    }

    public File getDataFile(String name) {
        File folder = getDataFolder();
        if (!folder.exists()) folder.mkdirs();
        return new File(folder, name);
    }
}
