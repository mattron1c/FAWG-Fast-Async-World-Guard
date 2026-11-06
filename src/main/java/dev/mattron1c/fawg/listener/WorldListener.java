package dev.mattron1c.fawg.listener;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.Flags;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class WorldListener implements Listener {
    private final FAWG plugin;

    public WorldListener(FAWG plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(WorldLoadEvent e) {
        plugin.regions().world(e.getWorld());
        refreshChunkTickets(e.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(WorldUnloadEvent e) {
        if (!e.isCancelled()) plugin.regions().unload(e.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent e) {
        if (e.getCause() == LightningStrikeEvent.Cause.COMMAND || e.getCause() == LightningStrikeEvent.Cause.CUSTOM) return;
        if (!plugin.protection().natural(e.getLightning().getLocation(), Flags.LIGHTNING)) e.setCancelled(true);
    }

    /** Keeps chunks of regions with chunk-unload=deny loaded via plugin chunk tickets. */
    public void refreshChunkTickets(World w) {
        try { w.removePluginChunkTickets(plugin); } catch (Throwable ignored) { }
        RegionWorld rw = plugin.regions().world(w);
        int budget = plugin.getConfig().getInt("performance.max-forced-chunks-per-world", 2048);
        for (Region r : rw.regions()) {
            if (r.flags().get(Flags.CHUNK_UNLOAD) != StateFlag.State.DENY) continue;
            var s = r.shape();
            for (int cx = s.minX() >> 4; cx <= s.maxX() >> 4 && budget > 0; cx++) {
                for (int cz = s.minZ() >> 4; cz <= s.maxZ() >> 4 && budget > 0; cz++) {
                    if (!s.intersectsChunk(cx, cz)) continue;
                    w.addPluginChunkTicket(cx, cz, plugin);
                    budget--;
                }
            }
        }
    }

    public void refreshAllChunkTickets() {
        for (World w : Bukkit.getWorlds()) refreshChunkTickets(w);
    }
}
