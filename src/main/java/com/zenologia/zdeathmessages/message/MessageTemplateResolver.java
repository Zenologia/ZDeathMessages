package com.zenologia.zdeathmessages.message;

import com.zenologia.zdeathmessages.config.ConfigManager;
import com.zenologia.zdeathmessages.config.TemplateConfig;
import com.zenologia.zdeathmessages.death.DeathContext;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;
import java.util.stream.Collectors;

public final class MessageTemplateResolver {

    private final ConfigManager config;

    public MessageTemplateResolver(ConfigManager config) {
        this.config = config;
    }

    public ResolvedTemplate resolve(DeathContext ctx) {
        // Special case: void + unknown killer
        if (ctx.cause() == EntityDamageEvent.DamageCause.VOID && ctx.killerPlayer() == null && ctx.killerEntityType() == null) {
            TemplateConfig t = templateFromStringOrSection("general.void-unknown-killer-message",
                    config.getGeneral().voidUnknownKillerMessage());
            if (!t.isEmpty()) {
                return new ResolvedTemplate(t, TemplateSource.VOID_UNKNOWN_KILLER);
            }
        }

        // World-specific lookup, with inheritance from default
        String worldName = ctx.victimLocation().getWorld() != null ? ctx.victimLocation().getWorld().getName() : "default";
        ConfigurationSection worlds = config.getWorldsSection();
        if (worlds == null) {
            return new ResolvedTemplate(templateFromStringOrSection("general.generic-fallback-message",
                    config.getGeneral().genericFallbackMessage()), TemplateSource.GENERIC_FALLBACK);
        }

        ConfigurationSection specific = worlds.getConfigurationSection(worldName);
        ConfigurationSection def = worlds.getConfigurationSection("default");

        // If a specific world section exists and is disabled, treat as "no handling"
        if (specific != null && !specific.getBoolean("enabled", true)) {
            return ResolvedTemplate.forWorldDisabled();
        }
        // If default is disabled and there's no specific override, also treat as no handling
        if (specific == null && def != null && !def.getBoolean("enabled", true)) {
            return ResolvedTemplate.forWorldDisabled();
        }

        TemplateConfig inSpecific = resolveInSectionOnly(specific, ctx);
        if (inSpecific != null && !inSpecific.isEmpty()) {
            return new ResolvedTemplate(inSpecific, TemplateSource.WORLD_OVERRIDE);
        }

        TemplateConfig inDefault = resolveInSectionOnly(def, ctx);
        if (inDefault != null && !inDefault.isEmpty()) {
            return new ResolvedTemplate(inDefault, TemplateSource.DEFAULT_WORLD);
        }

        // Generic fallback
        TemplateConfig fb = templateFromStringOrSection("general.generic-fallback-message",
                config.getGeneral().genericFallbackMessage());
        return new ResolvedTemplate(fb, TemplateSource.GENERIC_FALLBACK);
    }

    /**
     * Returns null if section is null or no relevant templates exist.
     */
    private TemplateConfig resolveInSectionOnly(ConfigurationSection worldSection, DeathContext ctx) {
        if (worldSection == null) return null;

        if (ctx.mode() == DeathContext.Mode.PVP) {
            return resolvePvp(worldSection.getConfigurationSection("pvp"), ctx);
        } else {
            return resolvePve(worldSection.getConfigurationSection("pve"), ctx);
        }
    }

    private TemplateConfig resolvePvp(ConfigurationSection pvp, DeathContext ctx) {
        if (pvp == null) return null;

        // Priority: weapon group -> material -> fallback
        String group = ctx.weaponGroup();
        if (group != null && !group.isBlank()) {
            TemplateConfig t = TemplateConfig.from(pvp.getConfigurationSection("WEAPON_GROUP." + group));
            if (!t.isEmpty()) return t;
        }

        Material mat = ctx.weaponMaterial();
        if (mat != null) {
            TemplateConfig t = TemplateConfig.from(pvp.getConfigurationSection("MATERIAL." + mat.name()));
            if (!t.isEmpty()) return t;
        }

        TemplateConfig fb = TemplateConfig.from(pvp.getConfigurationSection("FALLBACK"));
        return fb.isEmpty() ? null : fb;
    }

    private TemplateConfig resolvePve(ConfigurationSection pve, DeathContext ctx) {
        if (pve == null) return null;

        // ENTITY templates
        if (ctx.killerEntityType() != null) {
            EntityType type = ctx.killerEntityType();
            ConfigurationSection entSec = pve.getConfigurationSection("ENTITY." + type.name());
            if (entSec != null) {
                // Optional per-weapon specialization inside ENTITY node (if present)
                String group = ctx.weaponGroup();
                if (group != null && !group.isBlank()) {
                    TemplateConfig t = TemplateConfig.from(entSec.getConfigurationSection("WEAPON_GROUP." + group));
                    if (!t.isEmpty()) return t;
                }
                Material mat = ctx.weaponMaterial();
                if (mat != null) {
                    TemplateConfig t = TemplateConfig.from(entSec.getConfigurationSection("MATERIAL." + mat.name()));
                    if (!t.isEmpty()) return t;
                }
                TemplateConfig t = TemplateConfig.from(entSec);
                if (!t.isEmpty()) return t;
            }
        }

        // ENVIRONMENT templates
        EntityDamageEvent.DamageCause cause = ctx.cause();
        if (cause != null) {
            TemplateConfig t = TemplateConfig.from(pve.getConfigurationSection("ENVIRONMENT." + cause.name()));
            if (!t.isEmpty()) return t;
        }

        TemplateConfig fb = TemplateConfig.from(pve.getConfigurationSection("FALLBACK"));
        return fb.isEmpty() ? null : fb;
    }

    public List<String> getDisabledRegionsForWorld(String worldName) {
        ConfigurationSection worlds = config.getWorldsSection();
        if (worlds == null) return Collections.emptyList();

        ConfigurationSection specific = worlds.getConfigurationSection(worldName);
        ConfigurationSection def = worlds.getConfigurationSection("default");

        // World-specific list if present; else inherit default
        ConfigurationSection src = specific != null ? specific : def;
        if (src == null) return Collections.emptyList();

        List<String> regions = src.getStringList("regions.disabled");
        if (regions == null) regions = Collections.emptyList();

        return regions.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private TemplateConfig templateFromStringOrSection(String path, String fallbackString) {
        ConfigurationSection sec = config.raw().getConfigurationSection(path);
        if (sec != null) return TemplateConfig.from(sec);

        String s = config.raw().getString(path, fallbackString);
        if (s == null) s = fallbackString;
        // Wrap into TemplateConfig
        return new TemplateConfig(List.of(s), Collections.emptyList(), null);
    }
}
