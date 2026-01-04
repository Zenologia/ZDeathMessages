package com.zenologia.zdeathmessages.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

public record GeneralConfig(
        boolean enabled,
        MessageFormat defaultFormat,
        boolean respectWorldGuard,
        boolean perPlayerToggleDefault,
        String voidUnknownKillerMessage,
        String genericFallbackMessage,
        String vanishedKillerPublicName,
        VanishConfig vanishConfig,
        PlaceholderApiConfig placeholderApiConfig
) {
    public static GeneralConfig from(ConfigurationSection sec) {
        if (sec == null) {
            return new GeneralConfig(
                    true,
                    MessageFormat.LEGACY,
                    false,
                    true,
                    "&c${victim} fell into the void.",
                    "&7${victim} died.",
                    "Someone",
                    VanishConfig.defaults(),
                    PlaceholderApiConfig.defaults()
            );
        }

        boolean enabled = sec.getBoolean("enabled", true);
        MessageFormat fmt = MessageFormat.fromString(sec.getString("default-format", "LEGACY"));
        boolean respectWg = sec.getBoolean("respect-worldguard", true);
        boolean perDefault = sec.getBoolean("per-player-toggle-default", true);

        String voidMsg = sec.getString("void-unknown-killer-message", "&c${victim} fell into the void.");
        String genericMsg = sec.getString("generic-fallback-message", "&7${victim} died.");
        String vanishedName = sec.getString("vanished-killer-public-name", "Someone");

        VanishConfig vanish = VanishConfig.from(sec.getConfigurationSection("vanish"));
        PlaceholderApiConfig papi = PlaceholderApiConfig.from(sec.getConfigurationSection("placeholderapi"));

        return new GeneralConfig(enabled, fmt, respectWg, perDefault, voidMsg, genericMsg, vanishedName, vanish, papi);
    }

    public enum MessageFormat {
        LEGACY,
        MINIMESSAGE;

        public static MessageFormat fromString(String s) {
            if (s == null) return LEGACY;
            String up = s.trim().toUpperCase(Locale.ROOT);
            return "MINIMESSAGE".equals(up) ? MINIMESSAGE : LEGACY;
        }
    }
}
