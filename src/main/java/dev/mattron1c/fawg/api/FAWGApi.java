package dev.mattron1c.fawg.api;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.Flags;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Public API for other plugins. Everything here is thread safe and may be called from async threads.
 *
 * <pre>
 *   StateFlag MY_FLAG = FAWGApi.registerFlag(new StateFlag("my-flag", FlagCategory.MISC, true));
 *   if (!FAWGApi.test(player, location, MY_FLAG)) { ... }
 * </pre>
 */
public final class FAWGApi {
    private FAWGApi() {}

    private static FAWG p() {
        FAWG f = FAWG.get();
        if (f == null) throw new IllegalStateException("FastAsyncWorldGuard is not loaded");
        return f;
    }

    /** Registers a custom flag. Values already stored for this flag name are picked up automatically. */
    public static <F extends Flag<?>> F registerFlag(F flag) {
        flag.external();
        Flags.REGISTRY.register(flag);
        FAWG f = FAWG.get();
        if (f != null && f.regions() != null) {
            for (RegionWorld w : f.regions().worlds()) for (Region r : w.regionsIncludingGlobal()) {
                Object raw = r.flags().unknown().remove(flag.name());
                if (raw != null) dev.mattron1c.fawg.region.RegionStorage.readFlags(Map.of(flag.name(), raw), r.flags(), Flags.REGISTRY);
            }
        }
        return flag;
    }

    public static Flag<?> flag(String name) { return Flags.REGISTRY.get(name); }

    public static ApplicableSet query(Location l) { return p().regions().query(l); }

    public static RegionWorld world(World w) { return p().regions().world(w); }

    public static Region region(World w, String id) { return p().regions().world(w).get(id); }

    /** Full protection check (bypass, locks, membership, flag chain). */
    public static boolean test(Player player, Location l, StateFlag flag) { return p().protection().can(player, l, flag); }

    public static boolean canBuild(Player player, Location l) { return test(player, l, Flags.BLOCK_PLACE); }

    public static boolean canBreak(Player player, Location l) { return test(player, l, Flags.BLOCK_BREAK); }

    /** Flag test without a player (natural events). */
    public static boolean natural(Location l, StateFlag flag) { return p().protection().natural(l, flag); }

    public static <T> T value(Player player, Location l, Flag<T> flag) { return p().protection().value(player, query(l), flag); }
}
