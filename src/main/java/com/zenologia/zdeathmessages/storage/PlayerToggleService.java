package com.zenologia.zdeathmessages.storage;

import com.zenologia.zdeathmessages.ZDeathMessagesPlugin;
import com.zenologia.zdeathmessages.config.ConfigManager;
import com.zenologia.zdeathmessages.config.GeneralConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlayerToggleService implements Listener {

    private final ZDeathMessagesPlugin plugin;
    private final ConfigManager config;
    private volatile PlayerToggleStorage storage;

    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    private int flushTaskId = -1;

    public PlayerToggleService(ZDeathMessagesPlugin plugin, ConfigManager config, PlayerToggleStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        startFlushTask();
    }

    
    public void rebindStorage(PlayerToggleStorage storage) {
        this.storage = storage;
        // Refresh online players from new storage
        for (Player p : Bukkit.getOnlinePlayers()) {
            cache.remove(p.getUniqueId());
            getShowCustom(p.getUniqueId());
        }
    }

    private void startFlushTask() {
        // Periodically flush dirty toggle changes to the selected backend.
        // Runs async to avoid blocking the main thread.
        if (flushTaskId != -1) return;
        flushTaskId = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::flushDirty, 200L, 200L)
                .getTaskId();
    }

    private void flushDirty() {
        PlayerToggleStorage currentStorage = this.storage;
        if (currentStorage == null) return;

        // Snapshot dirty IDs to avoid long-held iteration while concurrent toggles happen.
        List<UUID> ids = new ArrayList<>(dirty);
        if (ids.isEmpty()) return;

        boolean isYaml = currentStorage instanceof YamlToggleStorage;

        for (UUID id : ids) {
            Boolean val = cache.get(id);
            if (val == null) {
                dirty.remove(id);
                continue;
            }
            try {
                currentStorage.saveToggle(id, val);
                dirty.remove(id);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to persist toggle for " + id + " (will retry).", ex);
            }
        }

        if (isYaml) {
            try {
                ((YamlToggleStorage) currentStorage).flushToDisk();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to flush YAML toggle storage (will retry).", ex);
            }
        }
    }

public void warmOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            getShowCustom(p.getUniqueId());
        }
    }

    private boolean defaultValue() {
        try {
            return config.getGeneral() != null && config.getGeneral().perPlayerToggleDefault();
        } catch (Throwable t) {
            return true;
        }
    }


    public boolean getShowCustom(UUID playerId) {
        Boolean cached = cache.get(playerId);
        if (cached != null) return cached;

        boolean def = defaultValue();
        boolean loaded = def;

        try {
            loaded = storage.loadToggle(playerId).orElse(def);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load toggle for " + playerId + " (using default=" + def + ")", ex);
        }

        cache.put(playerId, loaded);
        return loaded;
    }

    public boolean toggle(UUID playerId) {
        boolean next = !getShowCustom(playerId);
        setShowCustom(playerId, next);
        return next;
    }

    public void setShowCustom(UUID playerId, boolean showCustom) {
        cache.put(playerId, showCustom);
        dirty.add(playerId);
    }

    public void forget(UUID playerId) {
        cache.remove(playerId);
    }

    public void shutdown() {
        try {
            if (flushTaskId != -1) {
                Bukkit.getScheduler().cancelTask(flushTaskId);
                flushTaskId = -1;
            }
        } catch (Throwable ignored) {}

        // Best-effort final flush
        try {
            flushDirty();
        } catch (Throwable ignored) {}
    }
}
