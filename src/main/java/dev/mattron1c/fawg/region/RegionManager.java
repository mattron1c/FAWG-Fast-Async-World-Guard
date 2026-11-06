package dev.mattron1c.fawg.region;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.Flags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Owns every {@link RegionWorld}: loading, querying, async saving and backups. */
public final class RegionManager {
    private final FAWG plugin;
    private final Map<String, RegionWorld> worlds = new ConcurrentHashMap<>();
    private final RegionStorage storage;
    private final File dir;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FAWG-IO");
        t.setDaemon(true);
        return t;
    });

    public RegionManager(FAWG plugin) {
        this.plugin = plugin;
        this.storage = new RegionStorage(Flags.REGISTRY, plugin.getLogger());
        this.dir = new File(plugin.getDataFolder(), "regions");
    }

    public RegionStorage storage() { return storage; }
    public File directory() { return dir; }

    // ------------------------------------------------------------------------------------------- worlds
    public RegionWorld world(World w) { return world(w.getName(), w); }

    public RegionWorld world(String name) {
        World w = Bukkit.getWorld(name);
        return world(name, w);
    }

    private RegionWorld world(String name, World w) {
        RegionWorld rw = worlds.get(name);
        if (rw != null) return rw;
        return worlds.computeIfAbsent(name, n -> {
            RegionWorld nw = new RegionWorld(n);
            if (w != null) nw.heights(w.getMinHeight(), w.getMaxHeight() - 1);
            nw.largeThreshold(plugin.getConfig().getInt("performance.large-region-chunks", 4096));
            storage.load(file(n), nw);
            return nw;
        });
    }

    public RegionWorld existing(String name) { return worlds.get(name); }

    public Collection<RegionWorld> worlds() { return worlds.values(); }

    public void loadAll() {
        for (World w : Bukkit.getWorlds()) world(w);
        // also load files of worlds that are not loaded (so /rg list -w works and nothing is lost)
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) for (File f : files) world(f.getName().substring(0, f.getName().length() - 4));
    }

    public void unload(String world) {
        RegionWorld w = worlds.get(world);
        if (w != null && w.dirty()) saveNow(w);
        worlds.remove(world);
    }

    public void reload() {
        saveAllNow();
        worlds.clear();
        loadAll();
    }

    public File file(String world) { return new File(dir, world + ".yml"); }

    // ------------------------------------------------------------------------------------------- queries
    public ApplicableSet query(Location l) {
        return world(l.getWorld()).query(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public ApplicableSet query(Block b) {
        return world(b.getWorld()).query(b.getX(), b.getY(), b.getZ());
    }

    public ApplicableSet query(World w, int x, int y, int z) { return world(w).query(x, y, z); }

    public Region find(World w, String id) { return world(w).get(id); }

    /** Finds a region by id in any world (current world preferred). */
    public Region find(String id, World prefer) {
        if (prefer != null) {
            Region r = world(prefer).get(id);
            if (r != null) return r;
        }
        for (RegionWorld w : worlds.values()) {
            Region r = w.get(id);
            if (r != null && !r.isGlobal()) return r;
        }
        return null;
    }

    public int totalRegions() {
        int n = 0;
        for (RegionWorld w : worlds.values()) n += w.size();
        return n;
    }

    // ------------------------------------------------------------------------------------------- saving
    public void markDirty(Region r) {
        RegionWorld w = worlds.get(r.world());
        if (w != null) w.markDirty();
    }

    /** Index rebuild + save scheduling after geometry / priority / parent changes. */
    public void changed(Region r) {
        RegionWorld w = worlds.get(r.world());
        if (w != null) w.changed();
    }

    public void saveDirtyAsync() {
        for (RegionWorld w : worlds.values()) {
            if (!w.clearDirty()) continue;
            Map<String, Object> snap = storage.snapshot(w);
            io.execute(() -> write(w.name(), snap));
        }
    }

    public void saveAsync(RegionWorld w) {
        w.clearDirty();
        Map<String, Object> snap = storage.snapshot(w);
        io.execute(() -> write(w.name(), snap));
    }

    private void saveNow(RegionWorld w) {
        w.clearDirty();
        write(w.name(), storage.snapshot(w));
    }

    public void saveAllNow() {
        for (RegionWorld w : worlds.values()) if (w.dirty()) saveNow(w);
    }

    private void write(String world, Map<String, Object> snap) {
        try {
            storage.write(file(world), snap);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save regions of " + world, e);
            RegionWorld w = worlds.get(world);
            if (w != null) w.markDirty();
        }
    }

    public void shutdown() {
        saveAllNow();
        io.shutdown();
        try { io.awaitTermination(15, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    // ------------------------------------------------------------------------------------------- backups
    public File backupDir() { return new File(plugin.getDataFolder(), "backups"); }

    /** Zips all region files. Runs on the IO thread, returns immediately. */
    public void backup(String reason, java.util.function.Consumer<File> done) {
        saveDirtyAsync();
        io.execute(() -> {
            File out = new File(backupDir(), new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + "_" + reason.replaceAll("[^a-zA-Z0-9_-]", "") + ".zip");
            try {
                Files.createDirectories(out.getParentFile().toPath());
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out.toPath()))) {
                    File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
                    if (files != null) for (File f : files) {
                        zip.putNextEntry(new ZipEntry(f.getName()));
                        Files.copy(f.toPath(), zip);
                        zip.closeEntry();
                    }
                }
                prune();
                if (done != null) Bukkit.getScheduler().runTask(plugin, () -> done.accept(out));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Backup failed", e);
                if (done != null) Bukkit.getScheduler().runTask(plugin, () -> done.accept(null));
            }
        });
    }

    private void prune() {
        int keep = plugin.getConfig().getInt("backups.keep", 20);
        File[] f = backupDir().listFiles((d, n) -> n.endsWith(".zip"));
        if (f == null || f.length <= keep) return;
        Arrays.sort(f, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < f.length - keep; i++) //noinspection ResultOfMethodCallIgnored
            f[i].delete();
    }

    public List<String> backups() {
        File[] f = backupDir().listFiles((d, n) -> n.endsWith(".zip"));
        List<String> l = new ArrayList<>();
        if (f != null) { Arrays.sort(f, Comparator.comparingLong(File::lastModified).reversed()); for (File x : f) l.add(x.getName()); }
        return l;
    }

    /** Restores a backup zip (current files are backed up first). Must be called from the main thread. */
    public boolean restore(String name) {
        File zip = new File(backupDir(), name);
        if (!zip.exists() || !zip.getName().endsWith(".zip")) return false;
        saveAllNow();
        try (var zf = new java.util.zip.ZipFile(zip)) {
            Path tmpDir = Files.createTempDirectory("fawg-restore");
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String n = new File(e.getName()).getName();
                if (!n.endsWith(".yml")) continue;
                Files.copy(zf.getInputStream(e), tmpDir.resolve(n));
            }
            File[] existing = dir.listFiles((d, n) -> n.endsWith(".yml"));
            if (existing != null) for (File f : existing) Files.move(f.toPath(), f.toPath().resolveSibling(f.getName() + ".pre-restore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (var s = Files.list(tmpDir)) {
                for (Path p : s.toList()) Files.move(p, dir.toPath().resolve(p.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            worlds.clear();
            loadAll();
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Restore failed", e);
            return false;
        }
    }

    public void runIo(Runnable r) { io.execute(r); }
}
