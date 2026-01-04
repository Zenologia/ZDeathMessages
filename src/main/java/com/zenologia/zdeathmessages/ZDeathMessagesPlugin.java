package com.zenologia.zdeathmessages;

import com.zenologia.zdeathmessages.command.ZDeathMessagesCommand;
import com.zenologia.zdeathmessages.config.ConfigManager;
import com.zenologia.zdeathmessages.death.DeathMessageListener;
import com.zenologia.zdeathmessages.hooks.HookManager;
import com.zenologia.zdeathmessages.storage.PlayerToggleService;
import com.zenologia.zdeathmessages.storage.StorageManager;
import com.zenologia.zdeathmessages.util.DebugService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.List;

public final class ZDeathMessagesPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private HookManager hookManager;
    private StorageManager storageManager;
    private PlayerToggleService toggleService;

    private DebugService debugService;

    private DeathMessageListener deathMessageListener;

    @Override
    public void onEnable() {
        // Runtime guard (compile-time is enforced via Maven Enforcer)
        int feature = Runtime.version().feature();
        if (feature != 21) {
            getLogger().severe("ZDeathMessages requires Java 21. Detected Java " + feature + ". Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.hookManager = new HookManager(this);
        this.storageManager = new StorageManager(this);
        this.debugService = new DebugService();

        try {
            reloadAll();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to initialize ZDeathMessages. Disabling.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Events
        this.deathMessageListener = new DeathMessageListener(this, configManager, hookManager, toggleService, debugService);
        getServer().getPluginManager().registerEvents(deathMessageListener, this);
        getServer().getPluginManager().registerEvents(toggleService, this);

        // Commands
        new ZDeathMessagesCommand(this, configManager, storageManager, toggleService, hookManager, debugService).register();

        getLogger().info("ZDeathMessages enabled.");
    }

    @Override
    public void onDisable() {
        if (deathMessageListener != null) {
            deathMessageListener.shutdown();
        }
        if (debugService != null) {
            debugService.clear();
        }
        if (toggleService != null) {
            toggleService.shutdown();
        }
        if (storageManager != null) {
            storageManager.shutdown();
        }
        getLogger().info("ZDeathMessages disabled.");
    }

    
    public void registerBukkitCommand(String name,
                                      String description,
                                      List<String> aliases,
                                      org.bukkit.command.CommandExecutor executor,
                                      org.bukkit.command.TabCompleter completer) {
        try {
            java.lang.reflect.Constructor<org.bukkit.command.PluginCommand> c =
                    org.bukkit.command.PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
            c.setAccessible(true);
            org.bukkit.command.PluginCommand cmd = c.newInstance(name, this);
            cmd.setDescription(description);
            cmd.setAliases(aliases);
            cmd.setExecutor(executor);
            if (completer != null) cmd.setTabCompleter(completer);

            java.lang.reflect.Method m = org.bukkit.Bukkit.getServer().getClass().getMethod("getCommandMap");
            org.bukkit.command.CommandMap map = (org.bukkit.command.CommandMap) m.invoke(org.bukkit.Bukkit.getServer());

            map.register(getName(), cmd);
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to register command '" + name + "'.", t);
        }
    }

public void reloadAll() throws Exception {
        // reload config
        reloadConfig();
        configManager.reloadFrom(getConfig());

        // hooks depend on config flags
        hookManager.reload();

        // storage
        storageManager.reloadFrom(configManager.getStorageConfig());
        if (this.toggleService == null) {
            this.toggleService = new PlayerToggleService(this, configManager, storageManager.getStorage());
        } else {
            this.toggleService.rebindStorage(storageManager.getStorage());
        }
        this.toggleService.warmOnlinePlayers();

        if (deathMessageListener != null) {
            deathMessageListener.onConfigReload();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public PlayerToggleService getToggleService() {
        return toggleService;
    }

    public DebugService getDebugService() {
        return debugService;
    }
}