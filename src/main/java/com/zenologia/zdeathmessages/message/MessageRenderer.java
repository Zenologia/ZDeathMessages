package com.zenologia.zdeathmessages.message;

import com.zenologia.zdeathmessages.config.ConfigManager;
import com.zenologia.zdeathmessages.config.GeneralConfig;
import com.zenologia.zdeathmessages.config.TemplateConfig;
import com.zenologia.zdeathmessages.death.DeathContext;
import com.zenologia.zdeathmessages.hooks.HookManager;
import com.zenologia.zdeathmessages.util.TextUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

public final class MessageRenderer {

    private final ConfigManager config;
    private final HookManager hooks;

    private final MiniMessage mini = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public MessageRenderer(ConfigManager config, HookManager hooks) {
        this.config = config;
        this.hooks = hooks;
    }

    /**
     * Returns fully resolved message lines as raw strings:
     * - internal variable substitution
     * - PlaceholderAPI expansion (victim context, and killer context only when allowed)
     * This is useful for debug logging (pre-Adventure deserialization).
     */
    public List<String> renderStrings(TemplateConfig template, DeathContext ctx, ViewerContext viewer) {
        GeneralConfig general = config.getGeneral();
        List<String> lines = viewer.isStaff() ? template.staffLinesOrPublic() : template.publicLines();
        if (lines.isEmpty()) return List.of();

        Map<String, String> vars = buildVars(ctx, viewer);

        List<String> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) continue;
            // Support multi-line templates either via YAML lists, actual newlines, or embedded "\\n" sequences.
            String normalized = raw.replace("\\\\n", "\n");
            for (String split : normalized.split("\n", -1)) {
                String s = applyVars(split, vars);

                // PlaceholderAPI pass-through (victim context then optional killer context)
                if (hooks.isPlaceholderApiAvailable() && general.placeholderApiConfig().enabled()) {
                    s = PlaceholderAPI.setPlaceholders(ctx.victim(), s);
                    if (general.placeholderApiConfig().applyKillerContext()
                            && ctx.killerPlayer() != null
                            && !viewer.redactKiller()) {
                        s = PlaceholderAPI.setPlaceholders(ctx.killerPlayer(), s);
                    }
                }

                out.add(s);
            }
        }
        return out;
    }

    public List<Component> renderLines(TemplateConfig template, DeathContext ctx, ViewerContext viewer) {
        GeneralConfig general = config.getGeneral();
        GeneralConfig.MessageFormat fmt = template.formatOverride() != null ? template.formatOverride() : general.defaultFormat();

        List<String> rendered = renderStrings(template, ctx, viewer);
        if (rendered.isEmpty()) return List.of();

        List<Component> out = new ArrayList<>(rendered.size());
        for (String s : rendered) {
            Component c = switch (fmt) {
                case MINIMESSAGE -> mini.deserialize(s);
                case LEGACY -> legacy.deserialize(s);
            };
            out.add(c);
        }
        return out;
    }


    private Map<String, String> buildVars(DeathContext ctx, ViewerContext viewer) {
        Player victim = ctx.victim();

        String victimName = victim.getName();
        String victimDisplay = TextUtil.plainDisplayName(victim);

        String killerName = "";
        String killerDisplay = "";

        if (ctx.killerPlayer() != null) {
            killerName = viewer.redactKiller() ? config.getGeneral().vanishedKillerPublicName() : ctx.killerPlayer().getName();
            killerDisplay = viewer.redactKiller() ? config.getGeneral().vanishedKillerPublicName() : TextUtil.plainDisplayName(ctx.killerPlayer());
        } else if (ctx.killerEntityType() != null) {
            killerName = ctx.killerEntityType().name().toLowerCase(Locale.ROOT);
            killerDisplay = killerName;
        } else {
            killerName = "Unknown";
            killerDisplay = "Unknown";
        }

        String cause = ctx.cause() != null ? TextUtil.prettyCause(ctx.cause()) : "unknown";
        String world = ctx.victimLocation().getWorld() != null ? ctx.victimLocation().getWorld().getName() : "unknown";

        int x = ctx.victimLocation().getBlockX();
        int y = ctx.victimLocation().getBlockY();
        int z = ctx.victimLocation().getBlockZ();

        String weaponName = "";
        String weaponType = ""; // per spec: SWORD/AXE/BOW/CROSSBOW/TRIDENT/OTHER
        Material mat = ctx.weaponMaterial();
        if (mat != null) {
            // Prefer the precomputed weapon group when available, otherwise derive.
            weaponType = ctx.weaponGroup() != null && !ctx.weaponGroup().isBlank()
                    ? ctx.weaponGroup()
                    : TextUtil.weaponGroup(mat);
            if (ctx.killerPlayer() != null) {
                weaponName = TextUtil.weaponName(ctx.killerPlayer());
            } else {
                weaponName = TextUtil.prettyMaterial(mat);
            }
        }

        String distance = ctx.distanceMeters() >= 0 ? String.format(Locale.ROOT, "%.1f", ctx.distanceMeters()) : "";

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("${victim}", victimName);
        vars.put("${victim_display}", victimDisplay);

        vars.put("${killer}", killerName);
        vars.put("${killer_display}", killerDisplay);

        vars.put("${cause}", cause);
        vars.put("${world}", world);

        vars.put("${x}", Integer.toString(x));
        vars.put("${y}", Integer.toString(y));
        vars.put("${z}", Integer.toString(z));

        vars.put("${weapon_name}", weaponName);
        vars.put("${weapon_type}", weaponType);
        vars.put("${distance}", distance);

        String group = ctx.weaponGroup() == null ? "" : ctx.weaponGroup();
        vars.put("${weapon_group}", group);

        return vars;
    }

    private static String applyVars(String input, Map<String, String> vars) {
        String out = input;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    public record ViewerContext(
            boolean isStaff,
            boolean redactKiller
    ) {}
}
