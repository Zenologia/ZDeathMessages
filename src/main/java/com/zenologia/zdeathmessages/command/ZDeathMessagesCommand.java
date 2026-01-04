package com.zenologia.zdeathmessages.command;

import com.zenologia.zdeathmessages.ZDeathMessagesPlugin;
import com.zenologia.zdeathmessages.config.ConfigManager;
import com.zenologia.zdeathmessages.death.DeathContext;
import com.zenologia.zdeathmessages.hooks.HookManager;
import com.zenologia.zdeathmessages.message.MessageRenderer;
import com.zenologia.zdeathmessages.message.MessageTemplateResolver;
import com.zenologia.zdeathmessages.message.ResolvedTemplate;
import com.zenologia.zdeathmessages.storage.PlayerToggleService;
import com.zenologia.zdeathmessages.storage.StorageManager;
import com.zenologia.zdeathmessages.util.DebugService;
import com.zenologia.zdeathmessages.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;
import java.util.stream.Collectors;

public final class ZDeathMessagesCommand implements CommandExecutor, TabCompleter {

    private final ZDeathMessagesPlugin plugin;
    private final ConfigManager config;
    private final StorageManager storageManager;
    private final PlayerToggleService toggles;
    private final HookManager hooks;
    private final DebugService debug;

    private final MessageTemplateResolver resolver;
    private final MessageRenderer renderer;

    private static final List<String> SUBCOMMANDS = List.of("toggle", "reload", "test", "debug");

    public ZDeathMessagesCommand(ZDeathMessagesPlugin plugin,
                                ConfigManager config,
                                StorageManager storageManager,
                                PlayerToggleService toggles,
                                HookManager hooks,
                                DebugService debug) {
        this.plugin = plugin;
        this.config = config;
        this.storageManager = storageManager;
        this.toggles = toggles;
        this.hooks = hooks;
        this.debug = debug;
        this.resolver = new MessageTemplateResolver(config);
        this.renderer = new MessageRenderer(config, hooks);
    }

    public void register() {
        plugin.registerBukkitCommand(
                "zdeathmessages",
                "ZDeathMessages command",
                List.of("zdm"),
                this,
                this
        );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                toggleSelf(sender, p);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "toggle" -> {
                if (args.length == 1) {
                    if (sender instanceof Player p) {
                        toggleSelf(sender, p);
                    } else {
                        sender.sendMessage(Component.text("Console must specify a player: /zdeathmessages toggle <player>"));
                    }
                    return true;
                }
                if (args.length == 2) {
                    if (!sender.hasPermission("zdeathmessages.admin")) {
                        sender.sendMessage(Component.text("You do not have permission."));
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player not found (must be online)."));
                        return true;
                    }
                    boolean next = toggles.toggle(target.getUniqueId());
                    sender.sendMessage(Component.text("ZDeathMessages: " + target.getName() + " custom messages " + (next ? "enabled" : "disabled") + "."));
                    return true;
                }
                sendHelp(sender);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("zdeathmessages.admin")) {
                    sender.sendMessage(Component.text("You do not have permission."));
                    return true;
                }
                try {
                    plugin.reloadAll();
                    sender.sendMessage(Component.text("ZDeathMessages reloaded."));
                } catch (Exception ex) {
                    sender.sendMessage(Component.text("Reload failed: " + ex.getMessage()));
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Reload failed", ex);
                }
                return true;
            }
            case "debug" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Debug mode can only be toggled by a player."));
                    return true;
                }
                if (!sender.hasPermission("zdeathmessages.debug")) {
                    sender.sendMessage(Component.text("You do not have permission."));
                    return true;
                }
                boolean enabled = debug.toggle(p);
                sender.sendMessage(Component.text("ZDeathMessages debug " + (enabled ? "enabled" : "disabled") + "."));
                return true;
            }
            case "test" -> {
                if (!sender.hasPermission("zdeathmessages.admin")) {
                    sender.sendMessage(Component.text("You do not have permission."));
                    return true;
                }
                runTest(sender, Arrays.copyOfRange(args, 1, args.length));
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void toggleSelf(CommandSender sender, Player p) {
        if (!sender.hasPermission("zdeathmessages.toggle")) {
            sender.sendMessage(Component.text("You do not have permission."));
            return;
        }
        boolean next = toggles.toggle(p.getUniqueId());
        sender.sendMessage(Component.text("ZDeathMessages: custom messages " + (next ? "enabled" : "disabled") + "."));
    }

    private void runTest(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /zdeathmessages test <player> <PVP|PVE> <cause|entity> [weaponMaterial]"));
            return;
        }

        Player victim = Bukkit.getPlayerExact(args[0]);
        if (victim == null) {
            sender.sendMessage(Component.text("Player not found (must be online)."));
            return;
        }

        DeathContext.Mode mode = "PVP".equalsIgnoreCase(args[1]) ? DeathContext.Mode.PVP : DeathContext.Mode.PVE;
        String causeOrEntity = args[2].toUpperCase(Locale.ROOT);

        EntityType entityType = null;
        EntityDamageEvent.DamageCause cause = EntityDamageEvent.DamageCause.CUSTOM;

        if (mode == DeathContext.Mode.PVE) {
            try {
                EntityType t = EntityType.valueOf(causeOrEntity);
                if (t.isAlive()) entityType = t;
            } catch (IllegalArgumentException ignored) {}
        }
        if (entityType == null) {
            try {
                cause = EntityDamageEvent.DamageCause.valueOf(causeOrEntity);
            } catch (IllegalArgumentException ignored) {
                cause = EntityDamageEvent.DamageCause.CUSTOM;
            }
        }

        Material weapon = null;
        if (args.length >= 4) {
            try {
                weapon = Material.valueOf(args[3].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        Player killer = null;
        if (mode == DeathContext.Mode.PVP) {
            killer = sender instanceof Player p ? p : victim; // best-effort
        }

        String weaponGroup = TextUtil.weaponGroup(weapon);

        DeathContext ctx = new DeathContext(
                victim,
                killer,
                entityType,
                cause,
                mode,
                weapon,
                weaponGroup,
                killer != null ? victim.getLocation().distance(killer.getLocation()) : 0.0,
                victim.getLocation()
        );

        ResolvedTemplate resolved = resolver.resolve(ctx);

        if (resolved.disabledWorld()) {
            sender.sendMessage(Component.text("World is disabled for ZDeathMessages in config."));
            return;
        }

        boolean isStaff = sender.hasPermission("zdeathmessages.staffview");
        boolean killerVanished = ctx.killerPlayer() != null && hooks.isVanished(ctx.killerPlayer());

        for (Component line : renderer.renderLines(resolved.template(), ctx, new MessageRenderer.ViewerContext(isStaff, killerVanished && !isStaff))) {
            sender.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ("toggle".equals(sub)) {
            if (args.length == 2) {
                if (!sender.hasPermission("zdeathmessages.admin")) return List.of();
                return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
            }
            return List.of();
        }

        if ("test".equals(sub)) {
            if (args.length == 2) {
                return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(List.of("PVP", "PVE"), args[2]);
            }
            if (args.length == 4) {
                List<String> options = new ArrayList<>();
                for (EntityType t : EntityType.values()) {
                    if (t.isAlive()) options.add(t.name());
                }
                for (EntityDamageEvent.DamageCause c : EntityDamageEvent.DamageCause.values()) {
                    options.add(c.name());
                }
                return filterPrefix(options, args[3], 80);
            }
            if (args.length == 5) {
                List<String> mats = Arrays.stream(Material.values())
                        .map(Material::name)
                        .collect(Collectors.toList());
                return filterPrefix(mats, args[4], 80);
            }
        }

        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        return filterPrefix(options, prefix, 40);
    }

    private static List<String> filterPrefix(List<String> options, String prefix, int limit) {
        String p = prefix == null ? "" : prefix.toUpperCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toUpperCase(Locale.ROOT).startsWith(p))
                .sorted()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("ZDeathMessages commands:"));
        sender.sendMessage(Component.text("/zdeathmessages (toggle self)"));
        sender.sendMessage(Component.text("/zdeathmessages toggle [player]"));
        sender.sendMessage(Component.text("/zdeathmessages reload"));
        sender.sendMessage(Component.text("/zdeathmessages test <player> <PVP|PVE> <cause|entity> [weaponMaterial]"));
        sender.sendMessage(Component.text("/zdeathmessages debug"));
    }
}
