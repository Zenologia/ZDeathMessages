package com.zenologia.zdeathmessages.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugService {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (enabled.contains(id)) {
            enabled.remove(id);
            return false;
        } else {
            enabled.add(id);
            return true;
        }
    }

    public boolean isEnabled(UUID playerId) {
        return enabled.contains(playerId);
    }

    public boolean isAnyEnabled() {
        return !enabled.isEmpty();
    }

    public void clear() {
        enabled.clear();
    }

    public void debug(String message) {
        Component c = Component.text("[ZDeathMessages Debug] " + message);
        for (UUID id : enabled) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage(c);
            }
        }
    }
}
