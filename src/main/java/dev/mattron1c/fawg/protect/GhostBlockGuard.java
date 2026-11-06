package dev.mattron1c.fawg.protect;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.session.PlayerSession;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Kills "ghost blocks" (blocks that appear for a split second on high ping / Geyser and then vanish).
 *
 * 1. Prediction lock: when a survival player stands somewhere they cannot build, they are switched to
 *    adventure mode. Java and Bedrock clients then do not predict block placing / breaking at all, so a
 *    denied block never appears client side. Doors, chests, buttons, combat and items keep working.
 *    The original game mode is stored in the player's persistent data, so it survives crashes.
 * 2. Ping-aware resync: every denied block change is followed by an immediate correction of the block,
 *    its neighbours and the player's inventory, repeated after the player's round trip time so late
 *    packets from laggy / Bedrock clients cannot leave a phantom block behind.
 */
public final class GhostBlockGuard {
    private final FAWG plugin;
    private final NamespacedKey key;
    private volatile boolean lockEnabled = true, resyncEnabled = true;
    private volatile int maxDelay = 20;

    public GhostBlockGuard(FAWG plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "ghost_lock_gamemode");
    }

    public void reload() {
        var c = plugin.getConfig();
        lockEnabled = c.getBoolean("anti-ghost.prediction-lock", true);
        resyncEnabled = c.getBoolean("anti-ghost.resync", true);
        maxDelay = c.getInt("anti-ghost.max-resync-delay-ticks", 20);
    }

    // ------------------------------------------------------------------------------------- resync
    public void resync(Player p, Block b) {
        if (!resyncEnabled || p == null || b == null) return;
        send(p, b);
        p.updateInventory();
        int delay = Math.max(1, Math.min(maxDelay, p.getPing() / 50 + 2));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) { send(p, b); p.updateInventory(); } }, 1L);
        if (delay > 1) plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) send(p, b); }, delay);
    }

    private void send(Player p, Block b) {
        if (!b.getWorld().equals(p.getWorld())) return;
        List<BlockState> states = new ArrayList<>(8);
        states.add(b.getState());
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            states.add(b.getRelative(f).getState());
        }
        try {
            p.sendBlockChanges(states);
        } catch (Throwable t) {
            for (BlockState s : states) p.sendBlockChange(s.getLocation(), s.getBlockData());
        }
    }

    // ------------------------------------------------------------------------------- prediction lock
    /** Called by the movement listener whenever the player's region set changes. */
    public void update(Player p, PlayerSession s, ApplicableSet set) {
        if (!lockEnabled) { release(p, s); return; }
        boolean shouldLock = false;
        if (!set.isEmpty() && (p.getGameMode() == GameMode.SURVIVAL || s.ghostLocked)) {
            Boolean flag = plugin.protection().value(p, set, Flags.ANTI_GHOST_BLOCKS);
            boolean enabled = flag != null ? flag : plugin.getConfig().getBoolean("anti-ghost.lock-by-default", true);
            if (enabled) {
                Protection pr = plugin.protection();
                shouldLock = !pr.can(p, set, Flags.BLOCK_PLACE) && !pr.can(p, set, Flags.BLOCK_BREAK);
            }
        }
        if (shouldLock && !s.ghostLocked) {
            if (p.getGameMode() != GameMode.SURVIVAL) return;
            p.getPersistentDataContainer().set(key, PersistentDataType.STRING, GameMode.SURVIVAL.name());
            s.ghostLocked = true;
            p.setGameMode(GameMode.ADVENTURE);
        } else if (!shouldLock && s.ghostLocked) {
            release(p, s);
        }
    }

    public void release(Player p, PlayerSession s) {
        if (!s.ghostLocked) return;
        s.ghostLocked = false;
        p.getPersistentDataContainer().remove(key);
        if (p.getGameMode() == GameMode.ADVENTURE) p.setGameMode(GameMode.SURVIVAL);
    }

    /** On join: restore a game mode left over from a crash / restart. */
    public void restoreOnJoin(Player p) {
        String stored = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (stored == null) return;
        p.getPersistentDataContainer().remove(key);
        if (p.getGameMode() == GameMode.ADVENTURE) {
            try { p.setGameMode(GameMode.valueOf(stored)); } catch (IllegalArgumentException ignored) { p.setGameMode(GameMode.SURVIVAL); }
        }
    }

    /** True when the player was put in adventure mode by FAWG (so other code can ignore that change). */
    public boolean isLocked(PlayerSession s) { return s.ghostLocked; }
}
