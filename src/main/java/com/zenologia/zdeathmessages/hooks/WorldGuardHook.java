package com.zenologia.zdeathmessages.hooks;

import com.zenologia.zdeathmessages.ZDeathMessagesPlugin;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class WorldGuardHook {

    @SuppressWarnings("unused")
    private final ZDeathMessagesPlugin plugin;
    private final RegionQuery query;

    WorldGuardHook(ZDeathMessagesPlugin plugin) {
        this.plugin = plugin;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        this.query = container.createQuery();
    }

    boolean isInDisabledRegion(Location loc, List<String> disabledRegionIdsLower) {
        if (loc == null || loc.getWorld() == null) return false;
        if (disabledRegionIdsLower == null || disabledRegionIdsLower.isEmpty()) return false;

        Set<String> disabled = new HashSet<>();
        for (String id : disabledRegionIdsLower) {
            if (id != null) disabled.add(id.toLowerCase(Locale.ROOT));
        }

        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        for (ProtectedRegion r : set.getRegions()) {
            String id = r.getId();
            if (id != null && disabled.contains(id.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
