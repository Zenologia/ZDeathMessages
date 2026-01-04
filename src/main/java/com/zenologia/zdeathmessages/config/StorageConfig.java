package com.zenologia.zdeathmessages.config;

import org.bukkit.configuration.ConfigurationSection;

public record StorageConfig(
        Backend backend,
        String yamlFile,
        String sqliteFile,
        MysqlConfig mysql
) {
    public enum Backend { YAML, SQLITE, MYSQL }

    public static StorageConfig from(ConfigurationSection sec) {
        if (sec == null) {
            return new StorageConfig(Backend.YAML, "playerdata.yml", "zdeathmessages.db",
                    new MysqlConfig("127.0.0.1", 3306, "zdeathmessages", "user", "pass", false, 5));
        }

        Backend backend = Backend.YAML;
        String backendStr = sec.getString("backend", "YAML");
        if (backendStr != null) {
            try { backend = Backend.valueOf(backendStr.trim().toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }

        ConfigurationSection yaml = sec.getConfigurationSection("yaml");
        ConfigurationSection sqlite = sec.getConfigurationSection("sqlite");
        ConfigurationSection mysqlSec = sec.getConfigurationSection("mysql");

        String yamlFile = yaml != null ? yaml.getString("file", "playerdata.yml") : "playerdata.yml";
        String sqliteFile = sqlite != null ? sqlite.getString("file", "zdeathmessages.db") : "zdeathmessages.db";
        MysqlConfig mysql = MysqlConfig.from(mysqlSec);

        return new StorageConfig(backend, yamlFile, sqliteFile, mysql);
    }

    public record MysqlConfig(
            String host,
            int port,
            String database,
            String username,
            String password,
            boolean useSsl,
            int poolSize
    ) {
        public static MysqlConfig from(ConfigurationSection sec) {
            if (sec == null) {
                return new MysqlConfig("127.0.0.1", 3306, "zdeathmessages", "user", "pass", false, 5);
            }
            return new MysqlConfig(
                    sec.getString("host", "127.0.0.1"),
                    sec.getInt("port", 3306),
                    sec.getString("database", "zdeathmessages"),
                    sec.getString("username", "user"),
                    sec.getString("password", "pass"),
                    sec.getBoolean("use-ssl", false),
                    sec.getInt("connection-pool-size", 5)
            );
        }
    }
}
