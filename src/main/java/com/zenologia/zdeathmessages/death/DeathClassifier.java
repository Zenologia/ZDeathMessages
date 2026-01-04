package com.zenologia.zdeathmessages.death;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import org.bukkit.inventory.EntityEquipment;

import java.util.Locale;

public final class DeathClassifier {

    public DeathContext classify(Player victim) {
        EntityDamageEvent last = victim.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = last != null ? last.getCause() : null;
        if (cause == null) cause = EntityDamageEvent.DamageCause.CUSTOM;

        // Player killer (Minecraft sets this for most PVP sources, including projectiles)
        Player killerPlayer = victim.getKiller();

        // Resolve ultimate living damager (player or mob) to support chain reactions and PVE weapon detection.
        LivingEntity livingDamager = null;

        // Non-player killer type (best-effort)
        EntityType killerEntityType = null;
        if (last instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            livingDamager = resolveLivingDamager(damager);
            if (killerPlayer == null && livingDamager instanceof Player p) {
                killerPlayer = p;
            }
            if (livingDamager != null && !(livingDamager instanceof Player)) {
                killerEntityType = livingDamager.getType();
            }
        }

        DeathContext.Mode mode = killerPlayer != null ? DeathContext.Mode.PVP : DeathContext.Mode.PVE;

        Material weaponMaterial = null;
        double distance = -1.0;

        // Weapon/material and distance:
        // - PVP: player main hand
        // - PVE (mob damager): mob main hand if present
        Location vl = victim.getLocation();
        if (killerPlayer != null) {
            weaponMaterial = killerPlayer.getInventory().getItemInMainHand().getType();
            Location kl = killerPlayer.getLocation();
            if (vl.getWorld() != null && vl.getWorld().equals(kl.getWorld())) {
                distance = vl.distance(kl);
            }
        } else if (livingDamager != null) {
            EntityEquipment eq = livingDamager.getEquipment();
            if (eq != null && eq.getItemInMainHand() != null) {
                Material m = eq.getItemInMainHand().getType();
                if (m != null && m != Material.AIR) {
                    weaponMaterial = m;
                }
            }

            Location kl = livingDamager.getLocation();
            if (vl.getWorld() != null && vl.getWorld().equals(kl.getWorld())) {
                distance = vl.distance(kl);
            }
        }

        return new DeathContext(
                victim,
                killerPlayer,
                killerEntityType,
                cause,
                mode,
                weaponMaterial,
                null,
                distance,
                victim.getLocation()
        );
    }

    public static LivingEntity resolveLivingDamager(Entity damager) {
        if (damager == null) return null;

        if (damager instanceof LivingEntity le) return le;

        if (damager instanceof Projectile proj) {
            Object shooter = proj.getShooter();
            if (shooter instanceof LivingEntity le) return le;
        }

        if (damager instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof LivingEntity le) return le;
        }

        return null;
    }

    public static String causeKey(EntityDamageEvent.DamageCause cause) {
        if (cause == null) return "CUSTOM";
        return cause.name().toUpperCase(Locale.ROOT);
    }
}
