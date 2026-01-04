package com.zenologia.zdeathmessages.config;

import org.bukkit.configuration.ConfigurationSection;

public record VanishConfig(
        boolean victimSuppressPublic,
        TemplateConfig victimStaffMessage
) {
    public static VanishConfig defaults() {
        return new VanishConfig(true, TemplateConfig.empty());
    }

    public static VanishConfig from(ConfigurationSection sec) {
        if (sec == null) return defaults();
        boolean suppress = sec.getBoolean("victim-suppress-public", true);
        TemplateConfig staffMsg = TemplateConfig.from(sec.getConfigurationSection("victim-staff-message"));
        return new VanishConfig(suppress, staffMsg);
    }
}
