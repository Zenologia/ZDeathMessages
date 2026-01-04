package com.zenologia.zdeathmessages.hooks;

import com.zenologia.zdeathmessages.ZDeathMessagesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class CompositeVanishHook implements VanishHook {

    private final ZDeathMessagesPlugin plugin;
    private final List<VanishHook> delegates = new ArrayList<>();

    public CompositeVanishHook(ZDeathMessagesPlugin plugin) {
        this.plugin = plugin;
        loadHooks();
    }

    private void loadHooks() {
        // Essentials/EssentialsX
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null) ess = Bukkit.getPluginManager().getPlugin("EssentialsX");
        if (ess != null) {
            try {
                delegates.add(new EssentialsVanishHook(ess));
                plugin.getLogger().info("Hooked vanish via Essentials.");
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to hook Essentials vanish.", t);
            }
        }

        // SuperVanish / PremiumVanish API (reflection)
        try {
            Class<?> api = Class.forName("de.myzelyam.api.vanish.VanishAPI");
            Method m = findIsInvisible(api);
            if (m != null) {
                delegates.add(new MyzelyamVanishHook(api, m));
                plugin.getLogger().info("Hooked vanish via de.myzelyam.api.vanish.VanishAPI.");
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to hook VanishAPI.", t);
        }
    }

    private static Method findIsInvisible(Class<?> api) {
        try {
            return api.getMethod("isInvisible", Player.class);
        } catch (NoSuchMethodException ignored) {}
        try {
            return api.getMethod("isInvisible", java.util.UUID.class);
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    @Override
    public boolean isVanished(Player player) {
        for (VanishHook h : delegates) {
            try {
                if (h.isVanished(player)) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static final class MyzelyamVanishHook implements VanishHook {
        private final Class<?> api;
        private final Method method;

        private MyzelyamVanishHook(Class<?> api, Method method) {
            this.api = api;
            this.method = method;
            this.method.setAccessible(true);
        }

        @Override
        public boolean isVanished(Player player) {
            try {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(Player.class)) {
                    Object res = method.invoke(null, player);
                    return res instanceof Boolean b && b;
                }
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(java.util.UUID.class)) {
                    Object res = method.invoke(null, player.getUniqueId());
                    return res instanceof Boolean b && b;
                }
            } catch (Throwable ignored) {}
            return false;
        }
    }
}
