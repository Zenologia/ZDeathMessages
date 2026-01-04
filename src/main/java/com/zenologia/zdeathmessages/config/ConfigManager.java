package com.zenologia.zdeathmessages.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.stream.Collectors;

public final class ConfigManager {

    private final Object lock = new Object();

    private GeneralConfig generalConfig;
    private StorageConfig storageConfig;

    private Map<String, Set<String>> weaponGroupsUpperToMaterialsUpper; // group -> material names

    private FileConfiguration raw;

    public ConfigManager(Object ignoredPluginRef) {
        // plugin ref not needed; provided for future expansion
    }

    public void reloadFrom(FileConfiguration config) {
        synchronized (lock) {
            this.raw = config;

            this.generalConfig = GeneralConfig.from(config.getConfigurationSection("general"));
            this.storageConfig = StorageConfig.from(config.getConfigurationSection("storage"));

            this.weaponGroupsUpperToMaterialsUpper = parseWeaponGroups(config.getConfigurationSection("general.weapon-groups"));
        }
    }

    public FileConfiguration raw() {
        synchronized (lock) {
            return raw;
        }
    }

    public GeneralConfig getGeneral() {
        synchronized (lock) {
            return generalConfig;
        }
    }

    public StorageConfig getStorageConfig() {
        synchronized (lock) {
            return storageConfig;
        }
    }

    public Map<String, Set<String>> getWeaponGroups() {
        synchronized (lock) {
            return weaponGroupsUpperToMaterialsUpper;
        }
    }

    public Optional<String> findWeaponGroupForMaterial(String materialNameUpper) {
        synchronized (lock) {
            for (Map.Entry<String, Set<String>> e : weaponGroupsUpperToMaterialsUpper.entrySet()) {
                if (e.getValue().contains(materialNameUpper)) return Optional.of(e.getKey());
            }
            return Optional.empty();
        }
    }

    public ConfigurationSection getWorldSectionOrDefault(String worldName) {
        synchronized (lock) {
            ConfigurationSection worlds = raw.getConfigurationSection("worlds");
            if (worlds == null) return null;
            ConfigurationSection specific = worlds.getConfigurationSection(worldName);
            if (specific != null) return specific;
            return worlds.getConfigurationSection("default");
        }
    }

    public ConfigurationSection getDefaultWorldSection() {
        synchronized (lock) {
            ConfigurationSection worlds = raw.getConfigurationSection("worlds");
            return worlds == null ? null : worlds.getConfigurationSection("default");
        }
    }

    public ConfigurationSection getWorldsSection() {
        synchronized (lock) {
            return raw.getConfigurationSection("worlds");
        }
    }

    private static Map<String, Set<String>> parseWeaponGroups(ConfigurationSection sec) {
        if (sec == null) return Collections.emptyMap();
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            List<String> list = sec.getStringList(key);
            Set<String> mats = list.stream()
                    .filter(Objects::nonNull)
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            out.put(key.trim().toUpperCase(Locale.ROOT), mats);
        }
        return out;
    }
}
