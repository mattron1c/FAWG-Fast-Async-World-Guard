package dev.mattron1c.fawg.core.region;

import dev.mattron1c.fawg.core.flag.FlagMap;
import dev.mattron1c.fawg.core.shape.Shape;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** A protected region. Geometry is immutable and swapped atomically; everything else is thread safe. */
public final class Region implements Comparable<Region> {
    public static final String GLOBAL_ID = "__global__";
    private static final Pattern ID = Pattern.compile("^[a-z0-9_,'\\-+/]{1,64}$");

    private final String id;
    private final String world;
    private volatile Shape shape;
    private volatile List<Shape> excludes = List.of();
    private volatile int priority;
    private volatile Region parent;
    private volatile String parentId;
    private final Domain owners = new Domain();
    private final Domain members = new Domain();
    private final FlagMap flags = new FlagMap();
    private volatile long createdAt = System.currentTimeMillis();
    private volatile long modifiedAt = createdAt;
    private volatile String createdBy = "";
    private volatile String displayName;
    /** Bumped on every change, used by caches / visualizers. */
    private volatile int version;

    public Region(String id, String world, Shape shape) {
        String n = id.toLowerCase(Locale.ROOT);
        if (!isValidId(n)) throw new IllegalArgumentException("Invalid region id: " + id);
        this.id = n;
        this.world = world;
        this.shape = shape;
    }

    public static boolean isValidId(String id) { return id != null && (GLOBAL_ID.equals(id) || ID.matcher(id.toLowerCase(Locale.ROOT)).matches()); }

    public String id() { return id; }
    public String world() { return world; }
    public Shape shape() { return shape; }
    public List<Shape> excludes() { return excludes; }
    public int priority() { return priority; }
    public Region parent() { return parent; }
    public String parentId() { return parentId; }
    public Domain owners() { return owners; }
    public Domain members() { return members; }
    public FlagMap flags() { return flags; }
    public long createdAt() { return createdAt; }
    public long modifiedAt() { return modifiedAt; }
    public String createdBy() { return createdBy; }
    public String displayName() { return displayName == null ? id : displayName; }
    public int version() { return version; }
    public boolean isGlobal() { return shape instanceof Shape.Global; }

    public void shape(Shape s) { this.shape = s; touch(); }
    public void excludes(List<Shape> ex) { this.excludes = List.copyOf(ex); touch(); }
    public void priority(int p) { this.priority = p; touch(); }
    public void createdAt(long t) { this.createdAt = t; }
    public void createdBy(String s) { this.createdBy = s == null ? "" : s; }
    public void displayName(String s) { this.displayName = s; touch(); }
    public void modifiedAt(long t) { this.modifiedAt = t; }

    /** Sets the parent, rejecting circular inheritance. */
    public void parent(Region p) {
        for (Region c = p; c != null; c = c.parent) {
            if (c == this) throw new IllegalArgumentException("Circular parent: " + p.id + " already inherits from " + id);
        }
        this.parent = p;
        this.parentId = p == null ? null : p.id;
        touch();
    }

    public void parentId(String pid) { this.parentId = pid; }

    public void touch() { modifiedAt = System.currentTimeMillis(); version++; }

    public boolean contains(int x, int y, int z) {
        if (!shape.contains(x, y, z)) return false;
        List<Shape> ex = excludes;
        if (!ex.isEmpty()) for (int i = 0, n = ex.size(); i < n; i++) if (ex.get(i).contains(x, y, z)) return false;
        return true;
    }

    /** Owner check walks the parent chain (owners of a parent own the children). */
    public boolean isOwner(Subject s) {
        for (Region r = this; r != null; r = r.parent) if (r.owners.contains(s)) return true;
        return false;
    }

    public boolean isMember(Subject s) {
        for (Region r = this; r != null; r = r.parent) if (r.owners.contains(s) || r.members.contains(s)) return true;
        return false;
    }

    public Association association(Subject s) {
        if (s == null) return Association.NON_MEMBER;
        if (isOwner(s)) return Association.OWNER;
        if (isMember(s)) return Association.MEMBER;
        return Association.NON_MEMBER;
    }

    public boolean isAncestorOf(Region child) {
        for (Region c = child.parent; c != null; c = c.parent) if (c == this) return true;
        return false;
    }

    public int depth() {
        int d = 0;
        for (Region c = parent; c != null; c = c.parent) d++;
        return d;
    }

    /** Higher priority first, then deeper (child) regions first, then id for stable ordering. */
    @Override public int compareTo(Region o) {
        if (priority != o.priority) return Integer.compare(o.priority, priority);
        int d = Integer.compare(o.depth(), depth());
        return d != 0 ? d : id.compareTo(o.id);
    }

    @Override public String toString() { return id; }
}
