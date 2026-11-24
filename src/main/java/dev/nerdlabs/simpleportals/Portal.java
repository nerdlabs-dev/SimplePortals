package dev.nerdlabs.simpleportals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** One portal: a box of blocks plus what happens when a player walks into it. */
public final class Portal {

    public enum MessageType { NORMAL, BAR, TITLE, SUBTITLE }

    private final String name;
    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    private boolean enabled = true;
    private SwitchLocation destination;    // local teleport target (may be null); plain values so unloaded worlds don't break loading
    private String server;                 // proxy server to send to (null = none)
    private SwitchLocation switchLocation; // where to land on that server (may be null)
    private final List<String> commands = new ArrayList<>();
    private boolean commandOnly;
    private String message;
    private MessageType messageType = MessageType.NORMAL;
    private boolean messagesDisabled;
    private int cooldownSeconds = -1;      // -1 = use config default
    private int delaySeconds;
    private String fillMaterial;

    public Portal(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2); this.maxX = Math.max(x1, x2);
        this.minY = Math.min(y1, y2); this.maxY = Math.max(y1, y2);
        this.minZ = Math.min(z1, z2); this.maxZ = Math.max(z1, z2);
    }

    /** A destination on another server, stored as plain values because that world isn't loaded here. */
    public record SwitchLocation(String world, double x, double y, double z, float yaw, float pitch) {
        @Override public String toString() {
            return String.format("%s %.1f %.1f %.1f %.0f %.0f", world, x, y, z, yaw, pitch);
        }
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean containsBlock(String worldName, int x, int y, int z) {
        return worldName.equals(world) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public Location center() {
        World w = Bukkit.getWorld(world);
        return new Location(w, (minX + maxX + 1) / 2.0, minY, (minZ + maxZ + 1) / 2.0);
    }

    // ---- saving / loading ----

    public void save(ConfigurationSection s) {
        s.set("world", world);
        s.set("min", List.of(minX, minY, minZ));
        s.set("max", List.of(maxX, maxY, maxZ));
        s.set("enabled", enabled);
        saveLoc(s, "destination", destination);
        s.set("server", server);
        saveLoc(s, "switch-location", switchLocation);
        s.set("commands", commands);
        s.set("command-only", commandOnly);
        s.set("message", message);
        s.set("message-type", messageType.name());
        s.set("messages-disabled", messagesDisabled);
        s.set("cooldown", cooldownSeconds);
        s.set("delay", delaySeconds);
        s.set("fill-material", fillMaterial);
    }

    public static Portal load(String name, ConfigurationSection s) {
        List<Integer> min = s.getIntegerList("min"), max = s.getIntegerList("max");
        if (min.size() != 3 || max.size() != 3) throw new IllegalArgumentException("bad min/max");
        Portal p = new Portal(name, s.getString("world"), min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
        p.enabled = s.getBoolean("enabled", true);
        p.destination = loadLoc(s.getConfigurationSection("destination"));
        p.server = s.getString("server");
        p.switchLocation = loadLoc(s.getConfigurationSection("switch-location"));
        p.commands.addAll(s.getStringList("commands"));
        p.commandOnly = s.getBoolean("command-only");
        p.message = s.getString("message");
        try { p.messageType = MessageType.valueOf(s.getString("message-type", "NORMAL")); } catch (IllegalArgumentException ignored) { }
        p.messagesDisabled = s.getBoolean("messages-disabled");
        p.cooldownSeconds = s.getInt("cooldown", -1);
        p.delaySeconds = s.getInt("delay");
        p.fillMaterial = s.getString("fill-material");
        return p;
    }

    private static void saveLoc(ConfigurationSection s, String key, SwitchLocation l) {
        if (l == null) { s.set(key, null); return; }
        ConfigurationSection c = s.createSection(key);
        c.set("world", l.world());
        c.set("x", l.x());
        c.set("y", l.y());
        c.set("z", l.z());
        c.set("yaw", l.yaw());
        c.set("pitch", l.pitch());
    }

    private static SwitchLocation loadLoc(ConfigurationSection c) {
        if (c == null || c.getString("world") == null) return null;
        return new SwitchLocation(c.getString("world"), c.getDouble("x"), c.getDouble("y"), c.getDouble("z"),
                (float) c.getDouble("yaw"), (float) c.getDouble("pitch"));
    }

    // ---- getters / setters ----

    public String name() { return name; }
    public String world() { return world; }
    public int minX() { return minX; } public int minY() { return minY; } public int minZ() { return minZ; }
    public int maxX() { return maxX; } public int maxY() { return maxY; } public int maxZ() { return maxZ; }
    public boolean enabled() { return enabled; }
    public void enabled(boolean v) { enabled = v; }
    /** The local teleport target, or null if none is set or its world isn't loaded. */
    public Location destination() {
        if (destination == null) return null;
        World w = Bukkit.getWorld(destination.world());
        return w == null ? null : new Location(w, destination.x(), destination.y(), destination.z(), destination.yaw(), destination.pitch());
    }
    public boolean hasDestination() { return destination != null; }
    public void destination(Location v) {
        destination = v == null ? null : new SwitchLocation(v.getWorld().getName(), v.getX(), v.getY(), v.getZ(), v.getYaw(), v.getPitch());
    }
    public String server() { return server; }
    public void server(String v) { server = (v == null || v.equalsIgnoreCase("none")) ? null : v; }
    public SwitchLocation switchLocation() { return switchLocation; }
    public void switchLocation(SwitchLocation v) { switchLocation = v; }
    public List<String> commands() { return commands; }
    public boolean commandOnly() { return commandOnly; }
    public void commandOnly(boolean v) { commandOnly = v; }
    public String message() { return message; }
    public void message(String v) { message = v; }
    public MessageType messageType() { return messageType; }
    public void messageType(MessageType v) { messageType = v; }
    public boolean messagesDisabled() { return messagesDisabled; }
    public void messagesDisabled(boolean v) { messagesDisabled = v; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public void cooldownSeconds(int v) { cooldownSeconds = v; }
    public int delaySeconds() { return delaySeconds; }
    public void delaySeconds(int v) { delaySeconds = v; }
    public String fillMaterial() { return fillMaterial; }
    public void fillMaterial(String v) { fillMaterial = v; }
}
