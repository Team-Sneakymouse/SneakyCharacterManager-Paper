package net.sneakycharactermanager.paper.storage;

import java.util.*;

/** Main-thread snapshot ordering. Retention is bounded by requests awaiting main-thread delivery. */
public final class RevisionFence {
    private static final class Pending { int count; long revision = -1; long changes; }
    private final Map<String, Pending> owners = new HashMap<>();
    public void begin(String owner) { owners.computeIfAbsent(owner, unused -> new Pending()).count++; }
    public long changes(String owner) { return owners.get(owner).changes; }
    public void changed(String owner) { owners.get(owner).changes++; }
    public boolean unchanged(String owner, long changes) {
        Pending pending = owners.get(owner);
        return pending != null && pending.changes == changes;
    }
    public boolean accept(String owner, long revision) {
        Pending pending = owners.get(owner);
        if (pending == null || revision < pending.revision) return false;
        pending.revision = revision; return true;
    }
    public void finish(String owner) {
        Pending pending = owners.get(owner);
        if (pending != null && --pending.count == 0) owners.remove(owner);
    }
    public int size() { return owners.size(); }
    public void clear() { owners.clear(); }
}
