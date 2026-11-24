package dev.nerdlabs.simpleportals;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/** /simpleportals (aliases /sp, /portal, /portals ...) with the same sub-commands as the original plugin. */
public final class Commands implements TabExecutor {

    private static final List<String> SUBS = List.of("selectionmode", "sm", "reload", "info", "create", "delete", "fill",
            "setlocation", "sl", "showregion", "sr", "enable", "disable", "switchserver", "ss", "setswitchlocation", "ssl",
            "addcommand", "addcmd", "clearcommands", "clearcmds", "togglecommandonly", "tco", "commands", "cmds", "message",
            "disablemessages", "dm", "cooldown", "delay", "find", "help");

    /** Sub-commands whose 2nd argument is a portal name. */
    private static final Set<String> TAKES_PORTAL = Set.of("delete", "fill", "setlocation", "sl", "showregion", "sr", "enable",
            "disable", "switchserver", "ss", "setswitchlocation", "ssl", "addcommand", "addcmd", "clearcommands", "clearcmds",
            "togglecommandonly", "tco", "commands", "cmds", "message", "disablemessages", "dm", "cooldown", "coolodown", "delay");

    private static final List<String> FILLS = List.of("NETHER_PORTAL", "END_GATEWAY", "WATER", "LAVA", "AIR",
            "LIGHT_BLUE_STAINED_GLASS_PANE", "PURPLE_STAINED_GLASS_PANE", "COBWEB");

    private static final String[] HELP = {
            "&d/sp <selectionmode|sm> &7- toggle selection mode (left-click = point 1, right-click = point 2)",
            "&d/sp reload &7- reload config and portals",
            "&d/sp info &7- plugin info",
            "&d/sp create <name> &7- create a portal from your selection",
            "&d/sp delete <name> &7- delete a portal",
            "&d/sp fill <name> <material> &7- fill the portal's empty space with a block",
            "&d/sp <setlocation|sl> <name> [other portal] &7- set the teleport spot (your position, or another portal's center)",
            "&d/sp <showregion|sr> <name> &7- show the portal's outline",
            "&d/sp <enable|disable> <name> &7- turn a portal on/off",
            "&d/sp <switchserver|ss> <name> <server|none> &7- send players to another server",
            "&d/sp <setswitchlocation|ssl> <name> [world x y z yaw pitch] &7- where players land on that server",
            "&d/sp <addcommand|addcmd> <name> <command>[:PLAYER|:CONSOLE] &7- run a command on entry",
            "&d/sp <clearcommands|clearcmds> <name> &7- clear the portal's commands",
            "&d/sp <togglecommandonly|tco> <name> &7- only run commands, no teleport",
            "&d/sp <commands|cmds> <name> &7- list the portal's commands",
            "&d/sp message <name> <text>[:NORMAL|:BAR|:TITLE|:SUBTITLE] &7- set the entry message",
            "&d/sp <disablemessages|dm> <name> &7- toggle the portal's messages",
            "&d/sp cooldown <name> <seconds> &7- per-player cooldown",
            "&d/sp delay <name> <seconds> &7- wait before teleporting",
            "&d/sp find <range> &7- the 3 nearest portals"
    };

    private final SimplePortals plugin;

    public Commands(SimplePortals plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (a.length == 0) return help(s);
        String sub = a[0].toLowerCase(Locale.ROOT);
        String perm = "simpleportals." + canonical(sub);
        if (!s.hasPermission(perm) && !s.hasPermission("simpleportals.admin")) {
            plugin.msg(s, "&cYou don't have permission (" + perm + ").");
            return true;
        }

        switch (sub) {
            case "selectionmode", "sm" -> { Player p = player(s); if (p == null) return true;
                boolean on = plugin.selections().toggle(p.getUniqueId());
                plugin.msg(p, on ? "Selection mode &aon&7. Left-click a block for point 1, right-click for point 2."
                        : "Selection mode &coff&7.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.portals().load();
                plugin.msg(s, "Reloaded config and &d" + plugin.portals().all().size() + " &7portal(s).");
            }
            case "info" -> plugin.msg(s, "SimplePortals &d" + plugin.getPluginMeta().getVersion()
                    + " &7(lean rewrite, based on XZot1K's SimplePortals) - &d" + plugin.portals().all().size() + " &7portal(s).");
            case "create" -> create(s, a);
            case "delete" -> { Portal p = portal(s, a); if (p == null) return true;
                plugin.portals().remove(p);
                plugin.msg(s, "Deleted portal &d" + p.name() + "&7.");
            }
            case "fill" -> fill(s, a);
            case "setlocation", "sl" -> setLocation(s, a);
            case "showregion", "sr" -> { Portal p = portal(s, a); Player pl = player(s); if (p == null || pl == null) return true;
                showRegion(pl, p);
                plugin.msg(s, "Showing &d" + p.name() + "&7 for 10 seconds.");
            }
            case "enable", "disable" -> { Portal p = portal(s, a); if (p == null) return true;
                p.enabled(sub.equals("enable"));
                plugin.portals().save();
                plugin.msg(s, "Portal &d" + p.name() + " &7is now " + (p.enabled() ? "&aenabled" : "&cdisabled") + "&7.");
            }
            case "switchserver", "ss" -> { Portal p = portal(s, a); if (p == null) return true;
                if (a.length < 3) return usage(s, "/sp ss <name> <server|none>");
                p.server(a[2]);
                plugin.portals().save();
                plugin.msg(s, p.server() == null ? "Portal &d" + p.name() + " &7no longer switches servers."
                        : "Portal &d" + p.name() + " &7now sends players to &d" + p.server() + "&7.");
            }
            case "setswitchlocation", "ssl" -> setSwitchLocation(s, a);
            case "addcommand", "addcmd" -> { Portal p = portal(s, a); if (p == null) return true;
                if (a.length < 3) return usage(s, "/sp addcmd <name> <command>[:PLAYER|:CONSOLE]");
                String line = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                p.commands().add(line);
                plugin.portals().save();
                plugin.msg(s, "Added command #" + p.commands().size() + " to &d" + p.name() + "&7: &f" + line);
            }
            case "clearcommands", "clearcmds" -> { Portal p = portal(s, a); if (p == null) return true;
                p.commands().clear();
                plugin.portals().save();
                plugin.msg(s, "Cleared the commands on &d" + p.name() + "&7.");
            }
            case "togglecommandonly", "tco" -> { Portal p = portal(s, a); if (p == null) return true;
                p.commandOnly(!p.commandOnly());
                plugin.portals().save();
                plugin.msg(s, "Command-only mode for &d" + p.name() + "&7: " + (p.commandOnly() ? "&aon" : "&coff"));
            }
            case "commands", "cmds" -> { Portal p = portal(s, a); if (p == null) return true;
                if (p.commands().isEmpty()) { plugin.msg(s, "&d" + p.name() + " &7has no commands."); return true; }
                plugin.msg(s, "Commands on &d" + p.name() + "&7:");
                for (int i = 0; i < p.commands().size(); i++) s.sendMessage(SimplePortals.color("&7" + (i + 1) + ". &f" + p.commands().get(i)));
            }
            case "message" -> setMessage(s, a);
            case "disablemessages", "dm" -> { Portal p = portal(s, a); if (p == null) return true;
                p.messagesDisabled(!p.messagesDisabled());
                plugin.portals().save();
                plugin.msg(s, "Messages for &d" + p.name() + "&7: " + (p.messagesDisabled() ? "&cdisabled" : "&aenabled"));
            }
            case "cooldown", "coolodown" -> { Portal p = portal(s, a); Integer n = number(s, a, "/sp cooldown <name> <seconds>"); if (p == null || n == null) return true;
                p.cooldownSeconds(n);
                plugin.portals().save();
                plugin.msg(s, "Cooldown for &d" + p.name() + " &7set to &d" + n + "s&7.");
            }
            case "delay" -> { Portal p = portal(s, a); Integer n = number(s, a, "/sp delay <name> <seconds>"); if (p == null || n == null) return true;
                p.delaySeconds(n);
                plugin.portals().save();
                plugin.msg(s, "Delay for &d" + p.name() + " &7set to &d" + n + "s&7.");
            }
            case "find" -> find(s, a);
            default -> help(s);
        }
        return true;
    }

    // ---------------- the longer sub-commands ----------------

    private void create(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) { usage(s, "/sp create <name>"); return; }
        String name = a[1];
        if (plugin.portals().exists(name)) { plugin.msg(s, "&cA portal named " + name + " already exists."); return; }
        Location l1 = plugin.selections().point1(p.getUniqueId()), l2 = plugin.selections().point2(p.getUniqueId());
        if (l1 == null || l2 == null) { plugin.msg(s, "&cSelect both points first (/sp sm, then left- and right-click)."); return; }
        if (l1.getWorld() != l2.getWorld()) { plugin.msg(s, "&cBoth points must be in the same world."); return; }
        Portal portal = new Portal(name, l1.getWorld().getName(), l1.getBlockX(), l1.getBlockY(), l1.getBlockZ(),
                l2.getBlockX(), l2.getBlockY(), l2.getBlockZ());
        plugin.portals().add(portal);
        plugin.selections().clear(p.getUniqueId());
        plugin.msg(s, "Created portal &d" + name + "&7. Next: &f/sp sl " + name + " &7or &f/sp ss " + name + " <server>&7.");
    }

    private void fill(CommandSender s, String[] a) {
        Portal p = portal(s, a);
        if (p == null) return;
        if (a.length < 3) { usage(s, "/sp fill <name> <material>"); return; }
        Material mat = Material.matchMaterial(a[2]);
        if (mat == null || !mat.isBlock()) { plugin.msg(s, "&c" + a[2] + " isn't a block."); return; }
        World w = Bukkit.getWorld(p.world());
        if (w == null) { plugin.msg(s, "&cWorld " + p.world() + " isn't loaded."); return; }

        Material previous = p.fillMaterial() == null ? null : Material.matchMaterial(p.fillMaterial());
        // Thin along X -> portal faces east/west, so nether portal blocks need the Z axis.
        boolean alongZ = p.minX() == p.maxX() && p.minZ() != p.maxZ();
        int changed = 0;
        for (int x = p.minX(); x <= p.maxX(); x++)
            for (int y = p.minY(); y <= p.maxY(); y++)
                for (int z = p.minZ(); z <= p.maxZ(); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.getType().isAir() && b.getType() != previous) continue; // never overwrite the frame/builds
                    BlockData data = mat.createBlockData();
                    if (data instanceof Orientable o && o.getAxes().contains(org.bukkit.Axis.X))
                        o.setAxis(alongZ ? org.bukkit.Axis.Z : org.bukkit.Axis.X);
                    b.setBlockData(data, false); // false = no physics, so unframed portal blocks/water stay put
                    changed++;
                }
        p.fillMaterial(mat.isAir() ? null : mat.name());
        plugin.portals().save();
        plugin.msg(s, "Filled &d" + changed + " &7block(s) of &d" + p.name() + " &7with &d" + mat.name().toLowerCase() + "&7.");
    }

    private void setLocation(CommandSender s, String[] a) {
        Portal p = portal(s, a);
        Player pl = player(s);
        if (p == null || pl == null) return;
        if (a.length >= 3) {
            Portal other = plugin.portals().get(a[2]);
            if (other == null) { plugin.msg(s, "&cNo portal named " + a[2] + "."); return; }
            Location c = other.center();
            if (c.getWorld() == null) { plugin.msg(s, "&cThat portal's world isn't loaded."); return; }
            c.setYaw(pl.getLocation().getYaw());
            p.destination(c);
            plugin.msg(s, "&d" + p.name() + " &7now teleports to the center of &d" + other.name()
                    + "&7. Tip: give the other portal a cooldown or players can bounce.");
        } else {
            p.destination(pl.getLocation().clone());
            plugin.msg(s, "&d" + p.name() + " &7now teleports to your current position.");
        }
        plugin.portals().save();
    }

    private void setSwitchLocation(CommandSender s, String[] a) {
        Portal p = portal(s, a);
        if (p == null) return;
        Portal.SwitchLocation loc;
        if (a.length >= 8) {
            try {
                loc = new Portal.SwitchLocation(a[2], Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5]),
                        Float.parseFloat(a[6]), Float.parseFloat(a[7]));
            } catch (NumberFormatException e) {
                usage(s, "/sp ssl <name> <world> <x> <y> <z> <yaw> <pitch>");
                return;
            }
        } else if (a.length == 2 && s instanceof Player pl) {
            Location l = pl.getLocation();
            loc = new Portal.SwitchLocation(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        } else if (a.length == 3 && a[2].equalsIgnoreCase("none")) {
            p.switchLocation(null);
            plugin.portals().save();
            plugin.msg(s, "&d" + p.name() + " &7now drops players at the other server's normal spawn.");
            return;
        } else {
            usage(s, "/sp ssl <name> <world> <x> <y> <z> <yaw> <pitch>  (or no coords = your position, 'none' = clear)");
            return;
        }
        p.switchLocation(loc);
        plugin.portals().save();
        plugin.msg(s, "Players from &d" + p.name() + " &7will land at &f" + loc + "&7"
                + (p.server() == null ? " &e(set a server with /sp ss first)" : " on &d" + p.server()) + "&7.");
    }

    private void setMessage(CommandSender s, String[] a) {
        Portal p = portal(s, a);
        if (p == null) return;
        if (a.length < 3) { usage(s, "/sp message <name> <text>[:NORMAL|:BAR|:TITLE|:SUBTITLE]"); return; }
        String text = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
        Portal.MessageType type = Portal.MessageType.NORMAL;
        int colon = text.lastIndexOf(':');
        if (colon >= 0) {
            try {
                type = Portal.MessageType.valueOf(text.substring(colon + 1).trim().toUpperCase(Locale.ROOT));
                text = text.substring(0, colon);
            } catch (IllegalArgumentException ignored) { } // colon was part of the message
        }
        p.message(text);
        p.messageType(type);
        p.messagesDisabled(false);
        plugin.portals().save();
        plugin.msg(s, "Message for &d" + p.name() + " &7(" + type.name().toLowerCase() + "): &r" + text);
    }

    private void find(CommandSender s, String[] a) {
        Player pl = player(s);
        if (pl == null) return;
        int range;
        try { range = a.length >= 2 ? Integer.parseInt(a[1]) : 50; }
        catch (NumberFormatException e) { usage(s, "/sp find <range>"); return; }
        Location here = pl.getLocation();
        List<Portal> near = plugin.portals().all().stream()
                .filter(p -> p.world().equals(here.getWorld().getName()))
                .filter(p -> p.center().distance(here) <= range)
                .sorted(Comparator.comparingDouble(p -> p.center().distance(here)))
                .limit(3).toList();
        if (near.isEmpty()) { plugin.msg(s, "No portals within &d" + range + " &7blocks."); return; }
        plugin.msg(s, "Nearest portals:");
        for (Portal p : near) {
            Location c = p.center();
            s.sendMessage(SimplePortals.color("&d" + p.name() + " &7at &f" + c.getBlockX() + ", " + c.getBlockY() + ", " + c.getBlockZ()
                    + " &7(" + Math.round(c.distance(here)) + " blocks)"));
        }
    }

    /** Draws the box edges with red dust particles for 10 seconds, only for this player. */
    private void showRegion(Player player, Portal p) {
        World w = Bukkit.getWorld(p.world());
        if (w == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 60, 200), 1.0f);
        double x1 = p.minX(), y1 = p.minY(), z1 = p.minZ(), x2 = p.maxX() + 1, y2 = p.maxY() + 1, z2 = p.maxZ() + 1;
        new BukkitRunnable() {
            int runs = 0;
            @Override public void run() {
                if (!player.isOnline() || runs++ >= 20) { cancel(); return; }
                for (double x = x1; x <= x2; x += 0.5) for (double y : new double[]{y1, y2}) for (double z : new double[]{z1, z2})
                    player.spawnParticle(Particle.DUST, new Location(w, x, y, z), 1, dust);
                for (double y = y1; y <= y2; y += 0.5) for (double x : new double[]{x1, x2}) for (double z : new double[]{z1, z2})
                    player.spawnParticle(Particle.DUST, new Location(w, x, y, z), 1, dust);
                for (double z = z1; z <= z2; z += 0.5) for (double x : new double[]{x1, x2}) for (double y : new double[]{y1, y2})
                    player.spawnParticle(Particle.DUST, new Location(w, x, y, z), 1, dust);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ---------------- helpers ----------------

    private static String canonical(String sub) {
        return switch (sub) {
            case "sm" -> "selectionmode";
            case "sl" -> "setlocation";
            case "sr" -> "showregion";
            case "enable", "disable" -> "toggle";
            case "ss" -> "switchserver";
            case "ssl" -> "setswitchlocation";
            case "addcmd" -> "addcommand";
            case "clearcmds" -> "clearcommands";
            case "tco" -> "togglecommandonly";
            case "cmds" -> "commands";
            case "dm" -> "disablemessages";
            case "coolodown" -> "cooldown";
            case "help" -> "use";
            default -> SUBS.contains(sub) ? sub : "use";
        };
    }

    private boolean help(CommandSender s) {
        plugin.msg(s, "Commands:");
        for (String line : HELP) s.sendMessage(SimplePortals.color(line));
        return true;
    }

    private boolean usage(CommandSender s, String usage) {
        plugin.msg(s, "&cUsage: &f" + usage);
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        plugin.msg(s, "&cOnly players can use that.");
        return null;
    }

    private Portal portal(CommandSender s, String[] a) {
        if (a.length < 2) { usage(s, "/sp " + a[0] + " <name> ..."); return null; }
        Portal p = plugin.portals().get(a[1]);
        if (p == null) plugin.msg(s, "&cNo portal named " + a[1] + ".");
        return p;
    }

    private Integer number(CommandSender s, String[] a, String usage) {
        if (a.length < 3) { usage(s, usage); return null; }
        try {
            int n = Integer.parseInt(a[2]);
            if (n < 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            plugin.msg(s, "&c" + a[2] + " isn't a whole number of seconds.");
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        if (a.length == 1) return match(SUBS, a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && TAKES_PORTAL.contains(sub)) return match(plugin.portals().names(), a[1]);
        if (a.length == 3) {
            switch (sub) {
                case "fill" -> { return match(FILLS, a[2]); }
                case "setlocation", "sl" -> { return match(plugin.portals().names(), a[2]); }
                case "switchserver", "ss" -> { return match(List.of("none"), a[2]); }
                case "setswitchlocation", "ssl" -> {
                    List<String> opts = new ArrayList<>(List.of("none"));
                    Bukkit.getWorlds().forEach(w -> opts.add(w.getName()));
                    return match(opts, a[2]);
                }
                default -> { }
            }
        }
        return List.of();
    }

    private static List<String> match(Collection<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }
}
