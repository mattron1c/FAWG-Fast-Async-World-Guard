package dev.mattron1c.fawg.listener;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.flags.FlagFamilies;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.protect.Protection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;
import java.util.Set;

public final class BlockListener implements Listener {
    private final FAWG plugin;
    private final Protection pr;

    public BlockListener(FAWG plugin) {
        this.plugin = plugin;
        this.pr = plugin.protection();
    }

    private static StateFlag[] orDefault(StateFlag[] fam, StateFlag def) {
        return fam.length == 0 ? new StateFlag[]{def} : fam;
    }

    /** Material allow / deny list check. */
    private boolean listDenies(Player p, ApplicableSet set, Material m, dev.mattron1c.fawg.core.flag.StringSetFlag allow, dev.mattron1c.fawg.core.flag.StringSetFlag deny) {
        if (pr.bypasses(p)) return false;
        Set<String> d = pr.value(p, set, deny);
        if (d != null && d.contains(m.name())) return true;
        Set<String> a = pr.value(p, set, allow);
        return a != null && !a.isEmpty() && !a.contains(m.name());
    }

    // =========================================================================================== players
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        Material m = b.getType();
        ApplicableSet set = pr.at(b);
        StateFlag denied = pr.firstDenied(p, set, orDefault(FlagFamilies.place(m), Flags.BLOCK_PLACE));
        if (denied == null && listDenies(p, set, m, Flags.ALLOW_BLOCK_PLACE, Flags.DENY_BLOCK_PLACE)) denied = Flags.BLOCK_PLACE;
        if (denied == null && e instanceof BlockMultiPlaceEvent mp) {
            for (BlockState s : mp.getReplacedBlockStates()) {
                if (s.getBlock().equals(b)) continue;
                ApplicableSet other = pr.at(s.getLocation());
                if (!pr.can(p, other, Flags.BLOCK_PLACE)) { denied = Flags.BLOCK_PLACE; set = other; break; }
            }
        }
        if (denied != null) {
            e.setCancelled(true);
            e.setBuild(false);
            pr.deny(p, set, denied, b.getLocation());
            plugin.ghost().resync(p, b);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Material m = b.getType();
        ApplicableSet set = pr.at(b);
        StateFlag denied = pr.firstDenied(p, set, orDefault(FlagFamilies.breaking(m), Flags.BLOCK_BREAK));
        if (denied == null && listDenies(p, set, m, Flags.ALLOW_BLOCK_BREAK, Flags.DENY_BLOCK_BREAK)) denied = Flags.BLOCK_BREAK;
        if (denied != null) {
            e.setCancelled(true);
            pr.deny(p, set, denied, b.getLocation());
            plugin.ghost().resync(p, b);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        Block b = e.getBlock().getRelative(e.getBlockFace());
        ApplicableSet set = pr.at(b);
        if (!pr.check(p, set, Flags.HANGING_PLACE, b.getLocation())) {
            e.setCancelled(true);
            p.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        ApplicableSet set = pr.at(b);
        StateFlag[] fl = orDefault(FlagFamilies.useItem(e.getBucket()), Flags.BLOCK_PLACE);
        StateFlag denied = pr.firstDenied(p, set, fl);
        if (denied == null && listDenies(p, set, e.getBucket(), Flags.ALLOW_ITEM_USE, Flags.DENY_ITEM_USE)) denied = Flags.BLOCK_PLACE;
        if (denied != null) {
            e.setCancelled(true);
            pr.deny(p, set, denied, b.getLocation());
            plugin.ghost().resync(p, b);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockClicked();
        ApplicableSet set = pr.at(b);
        StateFlag denied = pr.firstDenied(p, set, orDefault(FlagFamilies.useItem(Material.BUCKET), Flags.BLOCK_BREAK));
        if (denied != null) {
            e.setCancelled(true);
            pr.deny(p, set, denied, b.getLocation());
            plugin.ghost().resync(p, b);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        ApplicableSet set = pr.at(e.getBlock());
        if (!pr.check(e.getPlayer(), set, Flags.SIGN_EDIT, e.getBlock().getLocation())) e.setCancelled(true);
    }

    // =========================================================================================== fire
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        Block b = e.getBlock();
        ApplicableSet set = pr.at(b);
        StateFlag cause = FlagFamilies.IGNITE.get(e.getCause());
        Player p = e.getPlayer();
        if (p == null && e.getIgnitingEntity() instanceof Projectile pj && pj.getShooter() instanceof Player sp) p = sp;
        if (p != null) {
            if (!pr.check(p, set, Flags.LIGHTER, b.getLocation()) || (cause != null && !pr.check(p, set, cause, b.getLocation()))) {
                e.setCancelled(true);
                plugin.ghost().resync(p, b);
            }
            return;
        }
        if (plugin.settings().fireDisabled) { e.setCancelled(true); return; }
        if (cause != null && !pr.natural(set, cause)) { e.setCancelled(true); return; }
        switch (e.getCause()) {
            case SPREAD -> { if (!pr.natural(set, Flags.FIRE_SPREAD)) e.setCancelled(true); }
            case LAVA -> { if (!pr.natural(set, Flags.LAVA_FIRE)) e.setCancelled(true); }
            case LIGHTNING -> { if (!pr.natural(set, Flags.LIGHTNING)) e.setCancelled(true); }
            default -> { }
        }
        if (!e.isCancelled() && e.getIgnitingBlock() != null && e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) {
            if (!pr.sameDomain(pr.at(e.getIgnitingBlock()), set) && !pr.explicit(set, Flags.FIRE_SPREAD, StateFlag.State.ALLOW)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (plugin.settings().fireDisabled) { e.setCancelled(true); return; }
        ApplicableSet set = pr.at(e.getBlock());
        if (!pr.natural(set, Flags.FIRE_BURN) || !pr.natural(set, Flags.FIRE_SPREAD)) e.setCancelled(true);
    }

    // =========================================================================================== nature
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        StateFlag f = FlagFamilies.SPREAD.get(e.getSource().getType());
        if (f == null) f = FlagFamilies.SPREAD.get(e.getNewState().getType());
        if (f == null) return;
        ApplicableSet set = pr.at(e.getBlock());
        if (!pr.natural(set, f)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        StateFlag f = FlagFamilies.FADE.get(e.getBlock().getType());
        if (f == null) return;
        if (!pr.natural(e.getBlock(), f)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        ApplicableSet set = pr.at(e.getBlock());
        if (e instanceof EntityBlockFormEvent ef) {
            Entity en = ef.getEntity();
            if (en instanceof Player p) {
                if (!pr.can(p, set, Flags.FROSTED_ICE_FORM) || !pr.natural(set, Flags.FROSTWALKER)) e.setCancelled(true);
                return;
            }
            if (en instanceof Snowman) {
                if (!pr.natural(set, Flags.SNOWMAN_TRAILS)) e.setCancelled(true);
                return;
            }
        }
        StateFlag f = FlagFamilies.FORM.get(e.getNewState().getType());
        if (f != null && !pr.natural(set, f)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        StateFlag f = FlagFamilies.GROW.get(e.getNewState().getType());
        if (f == null) f = FlagFamilies.GROW.get(e.getBlock().getType());
        if (f == null) f = Flags.CROP_GROWTH;
        if (!pr.natural(e.getBlock(), f)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent e) {
        if (!pr.natural(e.getBlock(), Flags.LEAF_DECAY)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMoisture(MoistureChangeEvent e) {
        if (!pr.natural(e.getBlock(), Flags.MOISTURE_CHANGE)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent e) {
        if (!pr.natural(e.getBlock(), Flags.SPONGE_ABSORB)) { e.setCancelled(true); return; }
        ApplicableSet origin = pr.at(e.getBlock());
        e.getBlocks().removeIf(s -> !pr.sameDomain(origin, pr.at(s.getLocation())));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onStructure(StructureGrowEvent e) {
        ApplicableSet origin = pr.at(e.getLocation());
        Player p = e.getPlayer();
        if (p != null && e.isFromBonemeal() && !pr.check(p, origin, Flags.BONE_MEAL, e.getLocation())) { e.setCancelled(true); return; }
        if (!pr.natural(origin, Flags.STRUCTURE_GROWTH)) { e.setCancelled(true); return; }
        e.getBlocks().removeIf(s -> {
            ApplicableSet t = pr.at(s.getLocation());
            return p != null ? !pr.can(p, t, Flags.BLOCK_PLACE) : !pr.sameDomain(origin, t);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        Player p = e.getPlayer();
        ApplicableSet origin = pr.at(e.getBlock());
        if (p != null && !pr.check(p, origin, Flags.BONE_MEAL, e.getBlock().getLocation())) { e.setCancelled(true); return; }
        e.getBlocks().removeIf(s -> {
            ApplicableSet t = pr.at(s.getLocation());
            return p != null ? !pr.can(p, t, Flags.BLOCK_PLACE) : !pr.sameDomain(origin, t);
        });
    }

    // =========================================================================================== fluids
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block from = e.getBlock(), to = e.getToBlock();
        Material m = from.getType();
        ApplicableSet target = pr.at(to);
        if (m == Material.DRAGON_EGG) {
            if (!pr.natural(target, Flags.DRAGON_EGG_TELEPORT) || !pr.natural(pr.at(from), Flags.DRAGON_EGG_TELEPORT)) e.setCancelled(true);
            return;
        }
        StateFlag f = m == Material.LAVA ? Flags.LAVA_FLOW : m == Material.WATER ? Flags.WATER_FLOW : null;
        if (f == null) {
            if (from.getBlockData() instanceof org.bukkit.block.data.Waterlogged) f = Flags.WATER_FLOW;
            else return;
        }
        if (!pr.natural(target, f)) { e.setCancelled(true); return; }
        if (!target.isEmpty()) {
            ApplicableSet source = pr.at(from);
            if (!target.sameRegions(source) && !pr.sameDomain(source, target) && !pr.natural(target, Flags.FLUID_FLOW_IN)) e.setCancelled(true);
        }
    }

    // =========================================================================================== redstone
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!piston(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!piston(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    private boolean piston(Block piston, List<Block> blocks, BlockFace dir) {
        ApplicableSet origin = pr.at(piston);
        if (!pr.natural(origin, Flags.PISTONS)) return false;
        for (Block b : blocks) {
            for (Block t : new Block[]{b, b.getRelative(dir)}) {
                ApplicableSet target = pr.at(t);
                if (!pr.natural(target, Flags.PISTONS)) return false;
                if (!pr.sameDomain(origin, target) && !pr.explicit(target, Flags.CROSS_BORDER_PISTONS, StateFlag.State.ALLOW)) return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        Block b = e.getBlock();
        ApplicableSet origin = pr.at(b);
        if (!pr.natural(origin, Flags.DISPENSERS)) { e.setCancelled(true); return; }
        if (b.getBlockData() instanceof Directional d) {
            ApplicableSet target = pr.at(b.getRelative(d.getFacing()));
            if (!pr.sameDomain(origin, target)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRedstone(BlockRedstoneEvent e) {
        if (!plugin.settings().redstoneFlag) return;
        if (e.getOldCurrent() == e.getNewCurrent()) return;
        if (!pr.natural(e.getBlock(), Flags.REDSTONE_SIGNALS)) e.setNewCurrent(e.getOldCurrent());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent e) {
        ApplicableSet set = pr.at(e.getBlock());
        Entity primer = e.getPrimingEntity();
        Player p = primer instanceof Player pl ? pl : primer instanceof Projectile pj && pj.getShooter() instanceof Player sp ? sp : null;
        if (p != null) {
            if (!pr.check(p, set, Flags.LIGHTER, e.getBlock().getLocation()) || !pr.check(p, set, Flags.TNT_PRIME, e.getBlock().getLocation())) e.setCancelled(true);
            return;
        }
        if (!pr.natural(set, Flags.TNT_PRIME) || !pr.natural(set, Flags.TNT)) e.setCancelled(true);
    }

    // =========================================================================================== explosions
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Entity en = e.getEntity();
        StateFlag f = FlagFamilies.EXPLOSION.get(en.getType());
        if (f == null) f = Flags.OTHER_EXPLOSION;
        Location src = e.getLocation();
        ApplicableSet origin = pr.at(src);
        if (!pr.natural(origin, f)) { e.setCancelled(true); return; }
        filterExplosion(e.blockList(), origin, f);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        Material m;
        try { m = e.getExplodedBlockState().getType(); } catch (Throwable t) { m = e.getBlock().getType(); }
        StateFlag f = m.name().endsWith("_BED") ? FlagFamilies.EXPLOSION_BED : m == Material.RESPAWN_ANCHOR ? FlagFamilies.EXPLOSION_ANCHOR : Flags.OTHER_EXPLOSION;
        ApplicableSet origin = pr.at(e.getBlock());
        if (!pr.natural(origin, f)) { e.setCancelled(true); return; }
        filterExplosion(e.blockList(), origin, f);
    }

    private void filterExplosion(List<Block> blocks, ApplicableSet origin, StateFlag f) {
        if (!pr.natural(origin, Flags.EXPLOSION_BLOCK_DAMAGE)) { blocks.clear(); return; }
        blocks.removeIf(b -> {
            ApplicableSet t = pr.at(b);
            if (t.sameRegions(origin)) return false;
            if (!pr.natural(t, f) || !pr.natural(t, Flags.EXPLOSION_BLOCK_DAMAGE)) return true;
            return !pr.sameDomain(origin, t) && !pr.explicit(t, Flags.CROSS_BORDER_EXPLOSIONS, StateFlag.State.ALLOW);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        Entity en = e.getEntity();
        Block b = e.getBlock();
        ApplicableSet set = pr.at(b);
        if (en instanceof Player p) {
            if (!pr.check(p, set, Flags.BLOCK_TRAMPLING, b.getLocation())) e.setCancelled(true);
            return;
        }
        if (en instanceof FallingBlock) {
            if (!pr.natural(set, Flags.FALLING_BLOCKS)) e.setCancelled(true);
            return;
        }
        if (b.getType() == Material.TURTLE_EGG && !pr.natural(set, Flags.TURTLE_EGG_TRAMPLE)) { e.setCancelled(true); return; }
        if (b.getType() == Material.FARMLAND && !pr.natural(set, Flags.BLOCK_TRAMPLING)) { e.setCancelled(true); return; }
        StateFlag f = FlagFamilies.GRIEF.get(en.getType());
        if (f != null && !pr.natural(set, f)) e.setCancelled(true);
        else if (f == null && en instanceof Projectile && !pr.natural(set, Flags.OTHER_EXPLOSION)) e.setCancelled(true);
    }
}
