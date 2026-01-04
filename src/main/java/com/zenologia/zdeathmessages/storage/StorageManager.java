package com.zenologia.zdeathmessages.storage;

import com.zenologia.zdeathmessages.ZDeathMessagesPlugin;
import com.zenologia.zdeathmessages.config.StorageConfig;

import java.util.logging.Level;

public final class StorageManager {

    private final ZDeathMessagesPlugin plugin;
    private PlayerToggleStorage storage;

    public StorageManager(ZDeathMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void reloadFrom(StorageConfig cfg) throws Exception {
        shutdown();

        PlayerToggleStorage chosen = null;
        Exception firstFailure = null;

        try {
            chosen = switch (cfg.backend()) {
                case YAML -> new YamlToggleStorage(plugin, cfg.yamlFile());
                case SQLITE -> new SqlToggleStorage(plugin, SqlToggleStorage.Mode.SQLITE, cfg);
                case MYSQL -> new SqlToggleStorage(plugin, SqlToggleStorage.Mode.MYSQL, cfg);
            };
        } catch (Exception ex) {
            firstFailure = ex;
            plugin.getLogger().log(Level.WARNING, "Storage backend " + cfg.backend() + " failed; falling back to YAML.", ex);
        }

        if (chosen == null) {
            try {
                chosen = new YamlToggleStorage(plugin, cfg.yamlFile());
            } catch (Exception yamlEx) {
                plugin.getLogger().log(Level.SEVERE, "YAML fallback storage also failed. Disabling plugin.", yamlEx);
                if (firstFailure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Original storage failure was:", firstFailure);
                }
                throw yamlEx;
            }
        }

        this.storage = chosen;
        plugin.getLogger().info("Using storage backend: " + storage.getClass().getSimpleName());
    }

    public PlayerToggleStorage getStorage() {
        return storage;
    }

    public void shutdown() {
        if (storage != null) {
            try {
                storage.close();
            } catch (Throwable ignored) {}
            storage = null;
        }
    }
}
