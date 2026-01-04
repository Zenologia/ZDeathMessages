package com.zenologia.zdeathmessages.hooks;

import com.zenologia.zdeathmessages.ZDeathMessagesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Level;

public final class HookManager {

    private final ZDeathMessagesPlugin plugin;

    private boolean placeholderApi;
    private WorldGuardHook worldGuardHook;
    private VanishHook vanishHook;

    public HookManager(ZDeathMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg != null) {
            try {
                this.worldGuardHook = new WorldGuardHook(plugin);
                plugin.getLogger().info("Hooked WorldGuard.");
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "WorldGuard detected but hook failed; WorldGuard support disabled.", t);
                this.worldGuardHook = null;
            }
        } else {
            this.worldGuardHook = null;
        }

        this.vanishHook = new CompositeVanishHook(plugin);
    }

    public boolean isPlaceholderApiAvailable() {
        return placeholderApi;
    }

    public boolean isWorldGuardAvailable() {
        return worldGuardHook != null;
    }

    public boolean isInDisabledRegion(Location loc, List<String> disabledRegionIdsLower) {
        if (worldGuardHook == null) return false;
        return worldGuardHook.isInDisabledRegion(loc, disabledRegionIdsLower);
    }

    public boolean isVanished(Player player) {
        return vanishHook != null && vanishHook.isVanished(player);
    }
}
