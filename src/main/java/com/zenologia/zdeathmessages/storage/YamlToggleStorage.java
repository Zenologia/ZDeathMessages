package com.zenologia.zdeathmessages.storage;

import com.zenologia.zdeathmessages.ZDeathMessagesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class YamlToggleStorage implements PlayerToggleStorage {

    private final File file;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private YamlConfiguration yaml;

    YamlToggleStorage(ZDeathMessagesPlugin plugin, String fileName) throws IOException {
        this.file = new File(plugin.getDataFolder(), fileName);

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Unable to create plugin data folder: " + plugin.getDataFolder());
        }

        if (!file.exists()) {
            if (!file.createNewFile()) {
                throw new IOException("Unable to create YAML storage file: " + file);
            }
        }

        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public Optional<Boolean> loadToggle(UUID playerId) {
        lock.readLock().lock();
        try {
            // Design-doc schema: players.<uuid>.toggle
            String path = "players." + playerId + ".toggle";
            if (yaml.contains(path)) {
                return Optional.of(yaml.getBoolean(path));
            }

            // Back-compat from early builds
            String legacy = "players." + playerId + ".show-custom";
            if (yaml.contains(legacy)) {
                return Optional.of(yaml.getBoolean(legacy));
            }

            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void saveToggle(UUID playerId, boolean showCustom) {
        lock.writeLock().lock();
        try {
            yaml.set("players." + playerId + ".toggle", showCustom);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void flushToDisk() throws IOException {
        lock.writeLock().lock();
        try {
            if (yaml != null) {
                yaml.save(file);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        try {
            flushToDisk();
        } catch (Throwable ignored) {
        }
    }
}
