package dev.mattron1c.fawg.select;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.text.SmallCaps;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** FAWG's own selection system (works with or without WorldEdit). */
public final class SelectionManager implements Listener {
    private final FAWG plugin;
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final NamespacedKey wandKey;

    public SelectionManager(FAWG plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "wand");
    }

    public Selection get(Player p) { return selections.computeIfAbsent(p.getUniqueId(), k -> new Selection()); }

    public Selection peek(Player p) { return selections.get(p.getUniqueId()); }

    public void forget(UUID id) { selections.remove(id); }

    public ItemStack wand() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("selection.wand-item", "blaze_rod"));
        ItemStack it = new ItemStack(m == null ? Material.BLAZE_ROD : m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plugin.text().component("wand.name", null));
        meta.lore(List.of(
                plugin.text().component("wand.lore-1", null),
                plugin.text().component("wand.lore-2", null),
                plugin.text().component("wand.lore-3", null)));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isWand(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isWand(e.getItem())) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("fawg.wand")) return;
        Block b = e.getClickedBlock();
        if (b == null) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR && p.isSneaking()) {
                Selection s = get(p);
                Selection.Type next = Selection.Type.values()[(s.type.ordinal() + 1) % Selection.Type.values().length];
                s.reset(next);
                plugin.text().send(p, "selection.type", "type", next.name().toLowerCase());
                e.setCancelled(true);
            }
            return;
        }
        Selection s = get(p);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            s.primary(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            plugin.text().send(p, s.type == Selection.Type.POLY ? "selection.poly-start" : "selection.pos1", "x", b.getX(), "y", b.getY(), "z", b.getZ(), "info", s.describe());
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            s.secondary(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            plugin.text().send(p, s.type == Selection.Type.POLY ? "selection.poly-point" : "selection.pos2", "x", b.getX(), "y", b.getY(), "z", b.getZ(), "info", s.describe(), "count", s.points.size());
        } else return;
        e.setCancelled(true);
        plugin.visualizer().showSelection(p);
    }
}
