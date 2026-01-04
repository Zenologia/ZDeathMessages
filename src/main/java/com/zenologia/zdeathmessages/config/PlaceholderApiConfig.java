package com.zenologia.zdeathmessages.config;

import org.bukkit.configuration.ConfigurationSection;

public record PlaceholderApiConfig(
        boolean enabled,
        boolean applyKillerContext
) {
    public static PlaceholderApiConfig defaults() {
        return new PlaceholderApiConfig(true, false);
    }

    public static PlaceholderApiConfig from(ConfigurationSection sec) {
        if (sec == null) return defaults();
        boolean enabled = sec.getBoolean("enabled", true);
        boolean killerCtx = sec.getBoolean("apply-killer-context", false);
        return new PlaceholderApiConfig(enabled, killerCtx);
    }
}
