package dev.mattron1c.fawg;

import dev.mattron1c.fawg.command.FawgCommand;
import dev.mattron1c.fawg.command.RegionCommand;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.FlagFamilies;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.gui.Menus;
import dev.mattron1c.fawg.hook.Bedrock;
import dev.mattron1c.fawg.hook.WorldEditHook;
import dev.mattron1c.fawg.listener.BlockListener;
import dev.mattron1c.fawg.listener.EntityListener;
import dev.mattron1c.fawg.listener.InteractListener;
import dev.mattron1c.fawg.listener.PlayerListener;
import dev.mattron1c.fawg.listener.WorldListener;
import dev.mattron1c.fawg.migrate.WorldGuardImporter;
import dev.mattron1c.fawg.protect.GhostBlockGuard;
import dev.mattron1c.fawg.protect.Protection;
import dev.mattron1c.fawg.region.RegionManager;
import dev.mattron1c.fawg.region.RegionService;
import dev.mattron1c.fawg.select.SelectionManager;
import dev.mattron1c.fawg.session.PlayerSession;
import dev.mattron1c.fawg.session.RegionEffects;
import dev.mattron1c.fawg.session.SessionManager;
import dev.mattron1c.fawg.text.Text;
import dev.mattron1c.fawg.util.ActionLog;
import dev.mattron1c.fawg.util.Settings;
import dev.mattron1c.fawg.util.UpdateChecker;
import dev.mattron1c.fawg.visual.Visualizer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * FastAsyncWorldGuard — a from-scratch, lock-free, chunk-indexed region protection plugin for Paper.
 */
public final class FAWG extends JavaPlugin {
    private static FAWG instance;

    private Text text;
    private RegionManager regions;
    private SessionManager sessions;
    private Protection protection;
    private GhostBlockGuard ghost;
    private RegionEffects effects;
    private SelectionManager selections;
    private Visualizer visualizer;
    private RegionService service;
    private Menus menus;
    private Bedrock bedrock;
    private ActionLog actionLog;
    private UpdateChecker updates;
    private final Settings settings = new Settings();
    private WorldEditHook worldEdit;
    private WorldListener worldListener;
    private PlayerListener playerListener;

    public static FAWG get() { return instance; }

    @Override
    public void onLoad() {
        instance = this;
        Flags.init();
    }

    @Override
    public void onEnable() {
        long start = System.nanoTime();
        saveDefaultConfig();
        mergeConfigDefaults();
        settings.load(getConfig());
        bedrock = new Bedrock();
        bedrock.init();
        text = new Text(this);
        text.load();
        actionLog = new ActionLog(this);
        actionLog.reload();
        regions = new RegionManager(this);
        sessions = new SessionManager();
        protection = new Protection(this);
        protection.reload();
        ghost = new GhostBlockGuard(this);
        ghost.reload();
        effects = new RegionEffects(this);
        selections = new SelectionManager(this);
        visualizer = new Visualizer(this);
        visualizer.start();
        service = new RegionService(this);
        menus = new Menus(this);
        updates = new UpdateChecker(this);

        regions.loadAll();

        var pm = getServer().getPluginManager();
        worldListener = new WorldListener(this);
        playerListener = new PlayerListener(this);
        pm.registerEvents(new BlockListener(this), this);
        pm.registerEvents(new InteractListener(this), this);
        pm.registerEvents(new EntityListener(this), this);
        pm.registerEvents(playerListener, this);
        pm.registerEvents(worldListener, this);
        pm.registerEvents(selections, this);
        pm.registerEvents(menus.chests(), this);

        if (pm.getPlugin("WorldEdit") != null || pm.getPlugin("FastAsyncWorldEdit") != null) {
            try {
                worldEdit = new WorldEditHook(this);
                worldEdit.enable();
            } catch (Throwable t) {
                getLogger().warning("WorldEdit / FAWE integration disabled: " + t);
                worldEdit = null;
            }
        }
        if (pm.getPlugin("PlaceholderAPI") != null) {
            try {
                new dev.mattron1c.fawg.hook.PlaceholderHook(this).register();
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI hook failed: " + t);
            }
        }

        RegionCommand rc = new RegionCommand(this);
        bind("region", rc);
        FawgCommand fc = new FawgCommand(this);
        bind("fawg", fc);

        long autosave = Math.max(20, getConfig().getLong("storage.autosave-seconds", 60) * 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            regions.saveDirtyAsync();
            regions.runIo(actionLog::flush);
        }, autosave, autosave);
        Bukkit.getScheduler().runTaskTimer(this, effects::tick, 20L, 20L);
        if (getConfig().getBoolean("backups.daily", true)) {
            Bukkit.getScheduler().runTaskTimer(this, () -> regions.backup("auto", null), 20L * 60 * 5, 20L * 60 * 60 * 24);
        }

        for (Player p : Bukkit.getOnlinePlayers()) playerListener.handleMove(p, p.getLocation(), PlayerListener.MoveType.JOIN);
        worldListener.refreshAllChunkTickets();
        updates.check();
        banner(start);
    }

    private void bind(String name, Object exec) {
        PluginCommand c = getCommand(name);
        if (c == null) { getLogger().severe("Command " + name + " missing from plugin.yml"); return; }
        c.setExecutor((org.bukkit.command.CommandExecutor) exec);
        c.setTabCompleter((org.bukkit.command.TabCompleter) exec);
    }

    private void banner(long start) {
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        var log = getComponentLogger();
        log.info(Text.parse("<gradient:#5a0000:#ff5c5c>━━━━━━━━━━━━</gradient> <#ffb3b3><b>✦</b></#ffb3b3> <gradient:#ff5c5c:#5a0000>━━━━━━━━━━━━</gradient>"));
        log.info(Text.parse("<gradient:#8b0000:#ff5555><b>ꜰᴀꜱᴛᴀꜱʏɴᴄᴡᴏʀʟᴅɢᴜᴀʀᴅ</b></gradient> <gray>v" + getPluginMeta().getVersion()));
        log.info(Text.parse("<gray>" + regions.totalRegions() + " regions · " + Flags.REGISTRY.size() + " flags (" + FlagFamilies.generatedCount() + " generated) · "
                + (worldEdit == null ? "standalone selections" : worldEdit.isFawe() ? "FAWE hooked" : "WorldEdit hooked") + " · " + (bedrock.available() ? "Geyser ready" : "Java") + " · " + String.format("%.0f", ms) + " ms"));
        log.info(Text.parse("<gradient:#5a0000:#ff5c5c>━━━━━━━━━━━━</gradient> <#ffb3b3><b>✦</b></#ffb3b3> <gradient:#ff5c5c:#5a0000>━━━━━━━━━━━━</gradient>"));
        WorldGuardImporter imp = new WorldGuardImporter(this);
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            getLogger().warning("WorldGuard is also installed. Import its regions with '/fawg import worldguard', then remove it with '/fawg cleanup confirm' and restart.");
        } else if (imp.available() && regions.totalRegions() == 0) {
            getLogger().info("Found WorldGuard region files. Import them with '/fawg import worldguard'.");
        }
    }

    private void mergeConfigDefaults() {
        try (var in = getResource("config.yml")) {
            if (in == null) return;
            YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String k : def.getKeys(true)) {
                if (def.isConfigurationSection(k)) continue;
                if (k.startsWith("presets.") && getConfig().isConfigurationSection("presets")) continue;
                if (!getConfig().contains(k, true)) { getConfig().set(k, def.get(k)); changed = true; }
            }
            if (changed) saveConfig();
        } catch (Exception e) {
            getLogger().warning("Could not merge config defaults: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (sessions != null) for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerSession s = sessions.peek(p.getUniqueId());
            if (s != null) effects.cleanup(p, s);
        }
        if (visualizer != null) visualizer.shutdown();
        if (worldEdit != null) worldEdit.disable();
        if (regions != null) regions.shutdown();
        if (actionLog != null) actionLog.flush();
        Bukkit.getScheduler().cancelTasks(this);
    }

    // ------------------------------------------------------------------------------------- helpers
    public void reloadAll() {
        reloadConfig();
        mergeConfigDefaults();
        settings.load(getConfig());
        text.load();
        protection.reload();
        ghost.reload();
        actionLog.reload();
        visualizer.start();
        regions.reload();
        refreshAll();
        worldListener.refreshAllChunkTickets();
    }

    /** Re-applies region effects for players currently inside a region (after flag / member changes). */
    public void refreshPlayersIn(Region r) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerSession s = sessions.peek(p.getUniqueId());
            if (s == null || s.current == null) continue;
            if (r.isGlobal() ? p.getWorld().getName().equals(r.world()) : s.current.contains(r)) refreshPlayer(p);
        }
    }

    public void refreshPlayer(Player p) {
        PlayerSession s = sessions.get(p);
        ApplicableSet set = regions.query(p.getLocation());
        s.current = set;
        s.world = p.getWorld().getName();
        effects.refresh(p, s, set);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refreshPlayer(p);
    }

    public void adminJoinNotices(Player p) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (updates.outdated()) text.send(p, "admin.update-available", "version", updates.latest());
            if (getServer().getPluginManager().getPlugin("WorldGuard") != null) text.send(p, "admin.worldguard-detected");
        }, 60L);
    }

    // ------------------------------------------------------------------------------------- getters
    public Text text() { return text; }
    public RegionManager regions() { return regions; }
    public SessionManager sessions() { return sessions; }
    public Protection protection() { return protection; }
    public GhostBlockGuard ghost() { return ghost; }
    public RegionEffects effects() { return effects; }
    public SelectionManager selections() { return selections; }
    public Visualizer visualizer() { return visualizer; }
    public RegionService service() { return service; }
    public Menus menus() { return menus; }
    public Bedrock bedrock() { return bedrock; }
    public ActionLog actionLog() { return actionLog; }
    public Settings settings() { return settings; }
    public WorldEditHook worldEdit() { return worldEdit; }
    public WorldListener worldListener() { return worldListener; }
    public PlayerListener playerListener() { return playerListener; }
}
