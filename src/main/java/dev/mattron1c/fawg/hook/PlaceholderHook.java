package dev.mattron1c.fawg.hook;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.region.PlayerSubject;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * PlaceholderAPI: %fawg_region%, %fawg_region_name%, %fawg_regions%, %fawg_priority%, %fawg_owners%,
 * %fawg_is_owner%, %fawg_is_member%, %fawg_owned_count%, %fawg_total%, %fawg_flag_&lt;flag&gt;%, %fawg_can_build%.
 */
public final class PlaceholderHook extends PlaceholderExpansion {
    private final FAWG plugin;

    public PlaceholderHook(FAWG plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "fawg"; }
    @Override public String getAuthor() { return "mattron1c"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer off, String params) {
        String k = params.toLowerCase(Locale.ROOT);
        if (k.equals("total")) return String.valueOf(plugin.regions().totalRegions());
        if (!(off instanceof Player p) || !p.isOnline()) return "";
        ApplicableSet set = plugin.regions().query(p.getLocation());
        Region top = set.highest();
        PlayerSubject sub = PlayerSubject.of(p);
        switch (k) {
            case "region": return top == null ? "" : top.id();
            case "region_name": return top == null ? plugin.getConfig().getString("placeholders.wilderness", "Wilderness") : top.displayName();
            case "regions": return String.join(", ", set.ids());
            case "priority": return top == null ? "" : String.valueOf(top.priority());
            case "owners": return top == null ? "" : String.valueOf(top.owners().size());
            case "members": return top == null ? "" : String.valueOf(top.members().size());
            case "is_owner": return String.valueOf(top != null && top.isOwner(sub));
            case "is_member": return String.valueOf(top != null && top.isMember(sub));
            case "can_build": return String.valueOf(plugin.protection().can(p, set, Flags.BUILD));
            case "owned_count": {
                int n = 0;
                for (Region r : plugin.regions().world(p.getWorld()).regions()) if (r.owners().contains(sub)) n++;
                return String.valueOf(n);
            }
            default: break;
        }
        if (k.startsWith("flag_")) {
            Flag<?> f = Flags.REGISTRY.get(k.substring(5));
            if (f == null) return "";
            Object v = plugin.protection().value(p, set, f);
            @SuppressWarnings("unchecked") String s = v == null ? "" : ((Flag<Object>) f).format(v);
            return s;
        }
        return null;
    }
}
