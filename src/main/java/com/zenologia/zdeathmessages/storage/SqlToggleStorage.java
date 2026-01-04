package com.zenologia.zdeathmessages.storage;

import com.zenologia.zdeathmessages.ZDeathMessagesPlugin;
import com.zenologia.zdeathmessages.config.StorageConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

final class SqlToggleStorage implements PlayerToggleStorage {

    enum Mode { SQLITE, MYSQL }

    private final Mode mode;
    private final HikariDataSource ds;

    SqlToggleStorage(ZDeathMessagesPlugin plugin, Mode mode, StorageConfig cfg) throws Exception {
        this.mode = mode;

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder: " + plugin.getDataFolder());
        }

        HikariConfig hc = new HikariConfig();

        if (mode == Mode.SQLITE) {
            File dbFile = new File(plugin.getDataFolder(), cfg.sqliteFile());
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            hc.setJdbcUrl(url);
        } else {
            StorageConfig.MysqlConfig m = cfg.mysql();
            String url = "jdbc:mysql://" + m.host() + ":" + m.port() + "/" + m.database()
                    + "?useSSL=" + m.useSsl()
                    + "&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=UTC";
            hc.setJdbcUrl(url);
            hc.setUsername(m.username());
            hc.setPassword(m.password());
        }

        hc.setPoolName("ZDeathMessages");
        // Pool size: SQLite should remain single-connection; MySQL should respect config.
        if (mode == Mode.SQLITE) {
            hc.setMaximumPoolSize(1);
        } else {
            hc.setMaximumPoolSize(Math.max(1, cfg.mysql().poolSize()));
        }

        this.ds = new HikariDataSource(hc);

        initSchema();
    }

    private void initSchema() throws Exception {
        // Design-doc schema
        String ddl = "CREATE TABLE IF NOT EXISTS player_settings (" +
                "uuid CHAR(36) PRIMARY KEY," +
                "toggle BOOLEAN NOT NULL" +
                ");";
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(ddl);
        }
    }

    @Override
    public Optional<Boolean> loadToggle(UUID playerId) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT toggle FROM player_settings WHERE uuid = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getBoolean(1));
            }
        }
    }

    @Override
    public void saveToggle(UUID playerId, boolean showCustom) throws Exception {
        if (mode == Mode.SQLITE) {
            // SQLite upsert
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO player_settings(uuid, toggle) VALUES(?, ?) " +
                         "ON CONFLICT(uuid) DO UPDATE SET toggle = excluded.toggle"
                 )) {
                ps.setString(1, playerId.toString());
                ps.setBoolean(2, showCustom);
                ps.executeUpdate();
            }
        } else {
            // MySQL upsert
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO player_settings(uuid, toggle) VALUES(?, ?) " +
                         "ON DUPLICATE KEY UPDATE toggle = VALUES(toggle)"
                 )) {
                ps.setString(1, playerId.toString());
                ps.setBoolean(2, showCustom);
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void close() {
        try { ds.close(); } catch (Throwable ignored) {}
    }
}
