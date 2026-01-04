package com.zenologia.zdeathmessages.hooks;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Reflection-based EssentialsX vanish detection to avoid hard dependency.
 * Attempts to call: Essentials#getUser(Player) -> User#isVanished()
 */
final class EssentialsVanishHook implements VanishHook {

    private final Plugin essentials;
    private final Method getUser;
    private final Method isVanished;

    EssentialsVanishHook(Plugin essentials) throws Exception {
        this.essentials = essentials;

        Class<?> essClass = essentials.getClass();
        this.getUser = essClass.getMethod("getUser", Player.class);

        // User class can vary by package; resolve from return type.
        Class<?> userClass = getUser.getReturnType();
        this.isVanished = userClass.getMethod("isVanished");
    }

    @Override
    public boolean isVanished(Player player) {
        try {
            Object user = getUser.invoke(essentials, player);
            if (user == null) return false;
            Object res = isVanished.invoke(user);
            return res instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
