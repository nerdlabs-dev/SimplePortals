package dev.nerdlabs.simpleportals;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Hands a "land here" location from one server to another through a folder that every
 * server on the machine can read (set transfer-folder to the same path on each server).
 * The sending server writes <uuid>.yml, the receiving server reads and deletes it on join.
 */
public final class TransferStore {

    private final SimplePortals plugin;

    public TransferStore(SimplePortals plugin) { this.plugin = plugin; }

    private File folder() {
        File f = new File(plugin.getConfig().getString("transfer-folder", "../simpleportals-transfers"));
        if (!f.isAbsolute()) f = new File(plugin.getServer().getWorldContainer(), f.getPath());
        return f;
    }

    public void write(UUID player, String server, Portal.SwitchLocation loc) {
        File dir = folder();
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create transfer folder " + dir.getAbsolutePath());
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("server", server);
        yml.set("time", System.currentTimeMillis());
        yml.set("world", loc.world());
        yml.set("x", loc.x());
        yml.set("y", loc.y());
        yml.set("z", loc.z());
        yml.set("yaw", loc.yaw());
        yml.set("pitch", loc.pitch());
        try {
            yml.save(new File(dir, player + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write transfer file", e);
        }
    }

    /** Returns and removes a pending transfer meant for this server, or null. Old files (over 60s) are ignored. */
    public Portal.SwitchLocation take(UUID player) {
        File f = new File(folder(), player + ".yml");
        if (!f.exists()) return null;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        String thisServer = plugin.getConfig().getString("server-name", "");
        String target = yml.getString("server", "");
        // Not for us (player went somewhere else) -> leave it for the right server unless it's stale.
        boolean stale = System.currentTimeMillis() - yml.getLong("time") > 60_000;
        if (!target.equalsIgnoreCase(thisServer)) {
            if (stale) f.delete();
            return null;
        }
        f.delete();
        if (stale) return null;
        return new Portal.SwitchLocation(yml.getString("world"), yml.getDouble("x"), yml.getDouble("y"),
                yml.getDouble("z"), (float) yml.getDouble("yaw"), (float) yml.getDouble("pitch"));
    }
}
