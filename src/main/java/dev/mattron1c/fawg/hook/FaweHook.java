package dev.mattron1c.fawg.hook;

import com.fastasyncworldedit.bukkit.regions.BukkitMaskManager;
import com.fastasyncworldedit.core.regions.FaweMask;
import com.fastasyncworldedit.core.regions.FaweMaskManager;
import com.fastasyncworldedit.core.util.WEManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.region.PlayerSubject;
import org.bukkit.entity.Player;

/**
 * Native FAWE mask manager: when FAWE region restrictions are on, players may only edit inside the FAWG
 * region they are standing in and own (or are a member of, with fawe.fawg.member).
 */
public final class FaweHook extends BukkitMaskManager {
    private final FAWG plugin;

    private FaweHook(FAWG plugin) {
        super(plugin.getName());
        this.plugin = plugin;
    }

    static void register(FAWG plugin) {
        WEManager.weManager().addManager(new FaweHook(plugin));
    }

    @Override
    public FaweMask getMask(com.sk89q.worldedit.entity.Player wePlayer, FaweMaskManager.MaskType type, boolean isWhitelist) {
        Player p = BukkitAdapter.adapt(wePlayer);
        if (p == null) return null;
        ApplicableSet set = plugin.regions().query(p.getLocation());
        PlayerSubject subject = PlayerSubject.of(p);
        boolean members = type == FaweMaskManager.MaskType.MEMBER || p.hasPermission("fawe.fawg.member");
        for (Region r : set.regions()) {
            boolean ok = r.isOwner(subject) || (members && r.isMember(subject));
            if (!ok) continue;
            if (!plugin.protection().allowsFor(p, set, Flags.WORLDEDIT) || plugin.protection().lockDenies(p, set, Flags.WORLDEDIT)) return null;
            final Region region = r;
            return new FaweMask(WorldEditHook.toWorldEdit(p.getWorld(), r.shape())) {
                @Override
                public boolean isValid(com.sk89q.worldedit.entity.Player player, FaweMaskManager.MaskType t) {
                    Player bp = BukkitAdapter.adapt(player);
                    if (bp == null) return false;
                    if (plugin.regions().find(bp.getWorld(), region.id()) != region) return false;
                    PlayerSubject s = PlayerSubject.of(bp);
                    return region.isOwner(s) || (members && region.isMember(s));
                }
            };
        }
        return null;
    }
}
