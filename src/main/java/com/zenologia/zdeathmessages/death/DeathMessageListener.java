package com.zenologia.zdeathmessages.death;

import com.zenologia.zdeathmessages.ZDeathMessagesPlugin;
import com.zenologia.zdeathmessages.config.ConfigManager;
import com.zenologia.zdeathmessages.config.GeneralConfig;
import com.zenologia.zdeathmessages.config.TemplateConfig;
import com.zenologia.zdeathmessages.hooks.HookManager;
import com.zenologia.zdeathmessages.message.MessageRenderer;
import com.zenologia.zdeathmessages.message.MessageTemplateResolver;
import com.zenologia.zdeathmessages.message.ResolvedTemplate;
import com.zenologia.zdeathmessages.storage.PlayerToggleService;
import com.zenologia.zdeathmessages.util.DebugService;
import com.zenologia.zdeathmessages.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathMessageListener implements Listener {

    private final ZDeathMessagesPlugin plugin;
    private final ConfigManager config;
    private final HookManager hooks;
    private final PlayerToggleService toggles;
    private final DebugService debug;

    private final DeathClassifier classifier = new DeathClassifier();
    private final MessageTemplateResolver templateResolver;
    private final MessageRenderer renderer;

    private final Map<UUID, Component> vanillaSnapshot = new ConcurrentHashMap<>();

    private static final EnumSet<EntityDamageEvent.DamageCause> VANILLA_CAUSES = EnumSet.allOf(EntityDamageEvent.DamageCause.class);
    static {
        // Treat CUSTOM as non-vanilla / plugin-specific.
        VANILLA_CAUSES.remove(EntityDamageEvent.DamageCause.CUSTOM);
    }

    public DeathMessageListener(ZDeathMessagesPlugin plugin,
                               ConfigManager config,
                               HookManager hooks,
                               PlayerToggleService toggles,
                               DebugService debug) {
        this.plugin = plugin;
        this.config = config;
        this.hooks = hooks;
        this.toggles = toggles;
        this.debug = debug;
        this.templateResolver = new MessageTemplateResolver(config);
        this.renderer = new MessageRenderer(config, hooks);
    }

    public void onConfigReload() {
        // no-op (uses live config)
    }

    public void shutdown() {
        vanillaSnapshot.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeathLowest(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        vanillaSnapshot.put(victim.getUniqueId(), event.deathMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathHighest(PlayerDeathEvent event) {
        GeneralConfig general = config.getGeneral();
        if (general == null || !general.enabled()) return;

        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();

        Component before = vanillaSnapshot.remove(victimId);
        Component current = event.deathMessage();

        // If the message was already suppressed or modified by another plugin, do nothing.
        if (current == null || before == null) return;
        if (!Objects.equals(before, current)) return;

        // Only override vanilla causes (skip CUSTOM / non-vanilla)
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last == null) return;
        if (!VANILLA_CAUSES.contains(last.getCause())) return;
// World settings
        String worldName = victim.getWorld().getName();
        List<String> disabledRegions = templateResolver.getDisabledRegionsForWorld(worldName);
        if (general.respectWorldGuard() && hooks.isWorldGuardAvailable()) {
            if (hooks.isInDisabledRegion(victim.getLocation(), disabledRegions)) {
                debug.debug("Skipped: victim in disabled region (" + worldName + ") " + disabledRegions);
                return;
            }
        }

        // Classify
        DeathContext baseCtx = classifier.classify(victim);
        String group = TextUtil.weaponGroup(baseCtx.weaponMaterial());
DeathContext ctx = new DeathContext(
                baseCtx.victim(),
                baseCtx.killerPlayer(),
                baseCtx.killerEntityType(),
                baseCtx.cause(),
                baseCtx.mode(),
                baseCtx.weaponMaterial(),
                group,
                baseCtx.distanceMeters(),
                baseCtx.victimLocation()
        );

        ResolvedTemplate resolved = templateResolver.resolve(ctx);
        if (resolved.disabledWorld()) {
            debug.debug("Skipped: world disabled (" + worldName + ")");
            return;
        }

        boolean victimVanished = hooks.isVanished(victim);
        boolean killerVanished = ctx.killerPlayer() != null && hooks.isVanished(ctx.killerPlayer());

        // Prevent vanilla broadcast; we'll send per-recipient ourselves.
        event.deathMessage(null);
        try {
            event.deathScreenMessageOverride(before);
        } catch (Throwable ignored) {}

        // Send to players
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean isStaff = viewer.hasPermission("zdeathmessages.staffview");
            boolean showCustom = toggles.getShowCustom(viewer.getUniqueId());

            if (victimVanished && general.vanishConfig().victimSuppressPublic() && !isStaff) {
                continue;
            }

            if (!showCustom) {
                // Respect toggle: show vanilla unless it would leak a vanished killer to public
                if (killerVanished && !isStaff) {
                    sendCustom(viewer, resolved.template(), ctx, false, true);
                } else if (victimVanished && isStaff && !general.vanishConfig().victimStaffMessage().isEmpty()) {
                    sendCustom(viewer, general.vanishConfig().victimStaffMessage(), ctx, true, false);
                } else {
                    viewer.sendMessage(before);
                }
                continue;
            }

            if (victimVanished && isStaff && !general.vanishConfig().victimStaffMessage().isEmpty()) {
                sendCustom(viewer, general.vanishConfig().victimStaffMessage(), ctx, true, false);
            } else {
                sendCustom(viewer, resolved.template(), ctx, isStaff, killerVanished && !isStaff);
            }
        }

        // Console (treated as staff)
        CommandSender console = Bukkit.getConsoleSender();
        if (victimVanished && !general.vanishConfig().victimStaffMessage().isEmpty()) {
            sendCustom(console, general.vanishConfig().victimStaffMessage(), ctx, true, false);
        } else {
            sendCustom(console, resolved.template(), ctx, true, false);
        }

        // Required console logging (concise classification + template source)
        plugin.getLogger().info("Handled death: mode=" + ctx.mode()
                + " cause=" + ctx.cause()
                + " world=" + worldName
                + " killerPlayer=" + (ctx.killerPlayer() != null ? ctx.killerPlayer().getName() : "none")
                + " killerEntity=" + (ctx.killerEntityType() != null ? ctx.killerEntityType().name() : "none")
                + " weaponGroup=" + (ctx.weaponGroup() != null ? ctx.weaponGroup() : "none")
                + " weaponMaterial=" + (ctx.weaponMaterial() != null ? ctx.weaponMaterial().name() : "none")
                + " templateSource=" + resolved.source());

        // Debug mode: log resolved message strings (post-substitution, pre-Adventure)
        if (debug.isAnyEnabled()) {
            List<String> pub = renderer.renderStrings(resolved.template(), ctx, new MessageRenderer.ViewerContext(false, killerVanished));
            List<String> staff = renderer.renderStrings(resolved.template(), ctx, new MessageRenderer.ViewerContext(true, false));
            plugin.getLogger().info("Resolved message (public): " + String.join(" | ", pub));
            plugin.getLogger().info("Resolved message (staff): " + String.join(" | ", staff));
        }

        debug.debug("Handled: mode=" + ctx.mode() + " cause=" + ctx.cause() + " world=" + worldName + " source=" + resolved.source());

    }

    private void sendCustom(CommandSender viewer, TemplateConfig template, DeathContext ctx, boolean isStaff, boolean redactKiller) {
        MessageRenderer.ViewerContext vctx = new MessageRenderer.ViewerContext(isStaff, redactKiller);
        for (Component c : renderer.renderLines(template, ctx, vctx)) {
            viewer.sendMessage(c);
        }
    }
}
