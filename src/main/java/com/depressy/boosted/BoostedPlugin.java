package com.depressy.boosted;

import com.depressy.boosted.commands.BoostCommand;
import com.depressy.boosted.commands.UnboostCommand;
import com.depressy.boosted.commands.BoostListCommand;
import com.depressy.boosted.model.BoostDefinition;
import net.luckperms.api.LuckPerms;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BoostedPlugin extends JavaPlugin {

    private LuckPerms luckPerms;
    private ActiveBoostManager activeBoostManager;
    private final Map<String, BoostDefinition> boostDefinitions = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hookLuckPerms();
        loadBoostDefinitions();

        this.activeBoostManager = new ActiveBoostManager(this);
        try {
            this.activeBoostManager.loadAndResumeFromDisk();
        } catch (Exception e) {
            getLogger().warning("Could not resume active boosts: " + e.getMessage());
        }

        // ---- register commands (executor + tab-complete) ----
        BoostCommand bc = new BoostCommand(this);
        getCommand("boost").setExecutor(bc);
        getCommand("boost").setTabCompleter(bc);

        UnboostCommand un = new UnboostCommand(this);
        getCommand("unboost").setExecutor(un);
        getCommand("unboost").setTabCompleter(un);

        getCommand("boostlist").setExecutor(new BoostListCommand(this));

        getLogger().info("Boosted enabled with " + boostDefinitions.size() + " boost(s).");
    }

    @Override
    public void onDisable() {
        try {
            this.activeBoostManager.saveActiveToDisk();
        } catch (Exception e) {
            getLogger().warning("Failed to save active boosts: " + e.getMessage());
        }
    }

    private void hookLuckPerms() {
        try {
            RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                luckPerms = provider.getProvider();
                getLogger().info("Hooked into LuckPerms API.");
            } else {
                getLogger().warning("LuckPerms not found; boosts will still run commands but LP features will be limited.");
            }
        } catch (Throwable t) {
            getLogger().warning("LuckPerms hook failed: " + t.getMessage());
        }
    }

    public LuckPerms getLuckPerms() { return luckPerms; }
    public ActiveBoostManager getActiveBoostManager() { return activeBoostManager; }
    public Map<String, BoostDefinition> getBoostDefinitions() { return Collections.unmodifiableMap(boostDefinitions); }
    public File getDataFile(String name) { return new File(getDataFolder(), name); }

    private void loadBoostDefinitions() {
        boostDefinitions.clear();
        FileConfiguration cfg = getConfig();
        ConfigurationSection section = cfg.getConfigurationSection("boosts");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection b = section.getConfigurationSection(key);
            if (b == null) continue;
            String group = b.getString("group", "");
            String startMsg = b.getString("global_start_message", "");
            String endMsg = b.getString("global_end_message", "");
            List<Map<?, ?>> commands = b.getMapList("commands");
            Map<String, Object> soundSection = b.getConfigurationSection("sound") != null ?
                    b.getConfigurationSection("sound").getValues(false) : null;

            BoostDefinition def = BoostDefinition.fromConfig(key, group, startMsg, endMsg, commands, soundSection);
            if (def != null) {
                boostDefinitions.put(key.toLowerCase(), def);
            }
        }
    }
}
