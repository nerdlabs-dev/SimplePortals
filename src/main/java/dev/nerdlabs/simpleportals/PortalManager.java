package dev.nerdlabs.simpleportals;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/** Holds all portals in memory and keeps portals.yml in sync. */
public final class PortalManager {

    private final SimplePortals plugin;
    private final File file;
    private final Map<String, Portal> portals = new LinkedHashMap<>(); // key = lower-case name

    public PortalManager(SimplePortals plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    public void load() {
        portals.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("portals");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            try {
                portals.put(name.toLowerCase(Locale.ROOT), Portal.load(name, root.getConfigurationSection(name)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipping broken portal '" + name + "' in portals.yml: " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + portals.size() + " portal(s).");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection root = yml.createSection("portals");
        for (Portal p : portals.values()) p.save(root.createSection(p.name()));
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save portals.yml", e);
        }
    }

    public Portal get(String name) { return name == null ? null : portals.get(name.toLowerCase(Locale.ROOT)); }
    public boolean exists(String name) { return get(name) != null; }
    public Collection<Portal> all() { return Collections.unmodifiableCollection(portals.values()); }
    public List<String> names() { return portals.values().stream().map(Portal::name).toList(); }

    public void add(Portal p) { portals.put(p.name().toLowerCase(Locale.ROOT), p); save(); }
    public void remove(Portal p) { portals.remove(p.name().toLowerCase(Locale.ROOT)); save(); }

    /** The first portal containing this location, or null. */
    public Portal at(Location loc) {
        for (Portal p : portals.values()) if (p.contains(loc)) return p;
        return null;
    }

    public Portal atBlock(String world, int x, int y, int z) {
        for (Portal p : portals.values()) if (p.containsBlock(world, x, y, z)) return p;
        return null;
    }
}
