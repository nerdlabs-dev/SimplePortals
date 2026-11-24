package dev.nerdlabs.simpleportals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * SimplePortals (lean rewrite). Region portals that teleport locally or send players to
 * another server behind Velocity/BungeeCord. Uses only the Paper API: no NMS, no reflection,
 * no Minecraft-version checks, so new drops normally only need the paper-api version bumped.
 *
 * Based on the original SimplePortals by XZot1K (MIT).
 */
public final class SimplePortals extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private PortalManager portals;
    private Selections selections;
    private TransferStore transfers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        portals = new PortalManager(this);
        portals.load();
        selections = new Selections();
        transfers = new TransferStore(this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);

        PluginCommand cmd = getCommand("simpleportals");
        if (cmd != null) {
            Commands handler = new Commands(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public PortalManager portals() { return portals; }
    public Selections selections() { return selections; }
    public TransferStore transfers() { return transfers; }

    // ---- small helpers used everywhere ----

    public static Component color(String text) { return LEGACY.deserialize(text == null ? "" : text); }

    /** Sends a prefixed chat message. Supports & color codes. */
    public void msg(CommandSender to, String text) {
        to.sendMessage(color(getConfig().getString("prefix", "&8[&dPortals&8] &7") + text));
    }

    /** Asks the proxy (Velocity or BungeeCord) to move the player to another server. */
    public void sendToServer(Player player, String server) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(this, "BungeeCord", bytes.toByteArray());
        } catch (IOException e) {
            getLogger().warning("Could not send " + player.getName() + " to " + server + ": " + e.getMessage());
        }
    }
}
