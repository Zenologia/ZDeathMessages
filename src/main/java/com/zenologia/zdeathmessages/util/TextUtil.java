package com.zenologia.zdeathmessages.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Locale;

public final class TextUtil {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private TextUtil() {}

    public static String plainDisplayName(Player p) {
        try {
            Component display = p.displayName();
            return display == null ? p.getName() : PLAIN.serialize(display);
        } catch (Throwable ignored) {
            return p.getName();
        }
    }

    public static String weaponName(Player killer) {
        ItemStack stack = killer.getInventory().getItemInMainHand();
        if (stack == null) return "";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            try {
                Component dn = meta.displayName();
                if (dn != null) {
                    String s = PLAIN.serialize(dn);
                    if (!s.isBlank()) return s;
                }
            } catch (Throwable ignored) {
                // legacy servers or older meta
            }
            try {
                String legacy = meta.getDisplayName();
                if (legacy != null && !legacy.isBlank()) return legacy;
            } catch (Throwable ignored) {}
        }
        return prettyMaterial(stack.getType());
    }

    public static String prettyMaterial(Material m) {
        if (m == null) return "";
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public static String weaponType(Material m) {
        if (m == null) return "";
        String n = m.name();
        if (n.endsWith("_SWORD")) return "sword";
        if (n.endsWith("_AXE")) return "axe";
        if (n.equals("BOW")) return "bow";
        if (n.equals("CROSSBOW")) return "crossbow";
        if (n.equals("TRIDENT")) return "trident";
        if (n.endsWith("_PICKAXE")) return "pickaxe";
        if (n.endsWith("_SHOVEL")) return "shovel";
        if (n.endsWith("_HOE")) return "hoe";
        if (n.endsWith("_MACE")) return "mace";
        return n.toLowerCase(Locale.ROOT);
    }

    public static String weaponGroup(Material m) {
        if (m == null || m == Material.AIR) return null;
        String n = m.name();
        if (n.endsWith("_SWORD")) return "SWORD";
        if (n.endsWith("_AXE")) return "AXE";
        if (n.equals("BOW")) return "BOW";
        if (n.equals("CROSSBOW")) return "CROSSBOW";
        if (n.equals("TRIDENT")) return "TRIDENT";
        return "OTHER";
    }

    public static String prettyCause(EntityDamageEvent.DamageCause cause) {
        if (cause == null) return "unknown";
        return switch (cause) {
            case VOID -> "void";
            case FALL -> "fall";
            case FIRE, FIRE_TICK -> "fire";
            case LAVA -> "lava";
            case DROWNING -> "drowning";
            case SUFFOCATION -> "suffocation";
            case STARVATION -> "starvation";
            case POISON -> "poison";
            case WITHER -> "wither";
            case LIGHTNING -> "lightning";
            case MAGIC -> "magic";
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> "melee";
            case PROJECTILE -> "projectile";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "explosion";
            default -> cause.name().toLowerCase(Locale.ROOT).replace('_', '-');
        };
    }
}
