package dev.nerdlabs.simpleportals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Walk-in detection, selection clicks, cross-server arrivals, and blocking vanilla portal behaviour inside portals. */
public final class PortalListener implements Listener {

    private final SimplePortals plugin;
    private final Map<UUID, Portal> inside = new HashMap<>();          // portal each player is standing in
    private final Map<String, Long> cooldowns = new HashMap<>();       // uuid|portal -> time it ends (ms)
    private final Map<UUID, BukkitTask> pending = new HashMap<>();     // delayed teleports

    public PortalListener(SimplePortals plugin) { this.plugin = plugin; }

    // ---------------- selection mode ----------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSelect(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!plugin.selections().inMode(p.getUniqueId()) || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.selections().setPoint1(p.getUniqueId(), b.getLocation());
            plugin.msg(p, "Point &d1 &7set at &f" + b.getX() + ", " + b.getY() + ", " + b.getZ());
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.selections().setPoint2(p.getUniqueId(), b.getLocation());
            plugin.msg(p, "Point &d2 &7set at &f" + b.getX() + ", " + b.getY() + ", " + b.getZ());
        } else return;
        e.setCancelled(true);
    }

    /** Don't break blocks while picking corners (matters in creative). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSelectBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (plugin.selections().inMode(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    // ---------------- walking into portals ----------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ() && from.getWorld() == to.getWorld()) return;

        Player player = e.getPlayer();
        Portal now = plugin.portals().at(to);
        Portal before = inside.get(player.getUniqueId());
        if (now == before) return;

        if (now == null) {
            inside.remove(player.getUniqueId());
            cancelPending(player.getUniqueId());
            return;
        }
        inside.put(player.getUniqueId(), now);
        cancelPending(player.getUniqueId());
        enter(player, now);
    }

    /** Any teleport (ours or not) just records where the player landed, so arriving inside a portal doesn't fire it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        markInside(e.getPlayer(), e.getTo());
        cancelPending(e.getPlayer().getUniqueId());
    }

    private void markInside(Player player, Location at) {
        Portal p = plugin.portals().at(at);
        if (p == null) inside.remove(player.getUniqueId());
        else inside.put(player.getUniqueId(), p);
    }

    private void enter(Player player, Portal portal) {
        if (!portal.enabled()) return;
        if (plugin.getConfig().getBoolean("require-portal-permission", false)
                && !player.hasPermission("simpleportals.portal." + portal.name().toLowerCase())
                && !player.hasPermission("simpleportals.portal.*")) {
            plugin.msg(player, plugin.getConfig().getString("messages.no-portal-permission", "&cYou can't use this portal."));
            pushBack(player);
            return;
        }

        String key = player.getUniqueId() + "|" + portal.name().toLowerCase();
        long left = cooldowns.getOrDefault(key, 0L) - System.currentTimeMillis();
        if (left > 0) {
            plugin.msg(player, plugin.getConfig().getString("messages.cooldown", "&cWait {time}s before using this portal again.")
                    .replace("{time}", String.valueOf((left + 999) / 1000)));
            pushBack(player);
            return;
        }

        if (portal.delaySeconds() > 0) {
            plugin.msg(player, plugin.getConfig().getString("messages.delay", "&7Teleporting in &d{time}s&7, stay in the portal...")
                    .replace("{time}", String.valueOf(portal.delaySeconds())));
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pending.remove(player.getUniqueId());
                if (player.isOnline() && inside.get(player.getUniqueId()) == portal) activate(player, portal, key);
            }, portal.delaySeconds() * 20L);
            pending.put(player.getUniqueId(), task);
        } else {
            activate(player, portal, key);
        }
    }

    private void activate(Player player, Portal portal, String cooldownKey) {
        int cd = portal.cooldownSeconds() >= 0 ? portal.cooldownSeconds() : plugin.getConfig().getInt("default-cooldown", 3);
        if (cd > 0) cooldowns.put(cooldownKey, System.currentTimeMillis() + cd * 1000L);

        if (!portal.messagesDisabled() && portal.message() != null && !portal.message().isEmpty()) {
            Component text = SimplePortals.color(portal.message().replace("{player}", player.getName()));
            switch (portal.messageType()) {
                case BAR -> player.sendActionBar(text);
                case TITLE -> player.showTitle(Title.title(text, Component.empty()));
                case SUBTITLE -> player.showTitle(Title.title(Component.empty(), text));
                default -> player.sendMessage(text);
            }
        }

        for (String line : portal.commands()) runCommand(player, line);
        if (portal.commandOnly()) return;

        if (portal.server() != null) {
            if (portal.switchLocation() != null)
                plugin.transfers().write(player.getUniqueId(), portal.server(), portal.switchLocation());
            playSound(player);
            plugin.sendToServer(player, portal.server());
        } else if (portal.destination() != null) {
            player.teleport(portal.destination());
            playSound(player);
        } else if (portal.hasDestination()) {
            plugin.getLogger().warning("Portal " + portal.name() + " points to a world that isn't loaded.");
        } else if (player.hasPermission("simpleportals.admin")) {
            plugin.msg(player, "&ePortal &f" + portal.name() + " &ehas no destination yet. Use &f/sp sl " + portal.name()
                    + " &eor &f/sp ss " + portal.name() + " <server>&e.");
        }
    }

    /** "say hi:CONSOLE" runs as console, "spawn:PLAYER" runs as the player. Default is console. {player} is replaced. */
    private void runCommand(Player player, String line) {
        String cmd = line;
        boolean asPlayer = false;
        int colon = line.lastIndexOf(':');
        if (colon > 0) {
            String who = line.substring(colon + 1).trim();
            if (who.equalsIgnoreCase("PLAYER") || who.equalsIgnoreCase("CONSOLE")) {
                asPlayer = who.equalsIgnoreCase("PLAYER");
                cmd = line.substring(0, colon);
            }
        }
        cmd = cmd.replace("{player}", player.getName()).trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (asPlayer) player.performCommand(cmd);
        else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void playSound(Player player) {
        String sound = plugin.getConfig().getString("teleport-sound", "minecraft:entity.enderman.teleport");
        if (sound != null && !sound.isEmpty() && !sound.equalsIgnoreCase("none"))
            player.playSound(player.getLocation(), sound.toLowerCase(), 1f, 1f);
    }

    private void pushBack(Player player) {
        double strength = plugin.getConfig().getDouble("push-back", 0.6);
        if (strength <= 0) return;
        Vector v = player.getLocation().getDirection().setY(0);
        if (v.lengthSquared() < 0.001) return;
        player.setVelocity(v.normalize().multiply(-strength).setY(0.25));
    }

    private void cancelPending(UUID id) {
        BukkitTask t = pending.remove(id);
        if (t != null) t.cancel();
    }

    // ---------------- joining / leaving ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        markInside(player, player.getLocation());
        Portal.SwitchLocation arrival = plugin.transfers().take(player.getUniqueId());
        if (arrival == null) return;
        // One tick later so the player is fully in the world before we move them.
        Bukkit.getScheduler().runTask(plugin, () -> {
            World w = Bukkit.getWorld(arrival.world());
            if (w == null) {
                plugin.getLogger().warning("Arrival world '" + arrival.world() + "' doesn't exist on this server.");
                return;
            }
            player.teleport(new Location(w, arrival.x(), arrival.y(), arrival.z(), arrival.yaw(), arrival.pitch()));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        inside.remove(id);
        cancelPending(id);
        plugin.selections().clear(id);
    }

    // ---------------- keep vanilla stuff from interfering ----------------

    /** Stop nether/end portal blocks used as decoration inside a portal from sending players to the nether. */
    @EventHandler(ignoreCancelled = true)
    public void onVanillaPortal(PlayerPortalEvent e) {
        if (plugin.portals().at(e.getFrom()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (plugin.portals().at(e.getFrom()) != null) e.setCancelled(true);
    }

    /** Water/lava used as portal fill shouldn't flow out. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block b = e.getBlock();
        if (plugin.portals().atBlock(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()) != null) e.setCancelled(true);
    }

    /** Nether portal blocks without a frame would pop; keep them inside portals. */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        Material type = b.getType();
        if (type != Material.NETHER_PORTAL && type != Material.END_GATEWAY && type != Material.END_PORTAL) return;
        if (plugin.portals().atBlock(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()) != null) e.setCancelled(true);
    }
}
