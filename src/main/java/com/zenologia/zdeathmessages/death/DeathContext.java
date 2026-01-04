package com.zenologia.zdeathmessages.death;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

public record DeathContext(
        Player victim,
        Player killerPlayer,
        EntityType killerEntityType,
        EntityDamageEvent.DamageCause cause,
        Mode mode,
        Material weaponMaterial,
        String weaponGroup,
        double distanceMeters,
        Location victimLocation
) {
    public enum Mode { PVP, PVE }
}
