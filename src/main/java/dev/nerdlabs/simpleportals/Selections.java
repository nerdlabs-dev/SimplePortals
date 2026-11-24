package dev.nerdlabs.simpleportals;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks who is in selection mode and the two corner points they picked. */
public final class Selections {

    private final Set<UUID> inMode = new HashSet<>();
    private final Map<UUID, Location> point1 = new HashMap<>();
    private final Map<UUID, Location> point2 = new HashMap<>();

    /** Toggles selection mode, returns the new state. */
    public boolean toggle(UUID id) {
        if (inMode.remove(id)) return false;
        inMode.add(id);
        return true;
    }

    public boolean inMode(UUID id) { return inMode.contains(id); }
    public void setPoint1(UUID id, Location l) { point1.put(id, l); }
    public void setPoint2(UUID id, Location l) { point2.put(id, l); }
    public Location point1(UUID id) { return point1.get(id); }
    public Location point2(UUID id) { return point2.get(id); }

    public void clear(UUID id) {
        inMode.remove(id);
        point1.remove(id);
        point2.remove(id);
    }
}
