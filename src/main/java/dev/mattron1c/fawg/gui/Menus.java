package dev.mattron1c.fawg.gui;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.region.Region;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Entry point for the HUD: Java players get native dialogs, Bedrock players a chest GUI. */
public final class Menus {
    private final FAWG plugin;
    private final DialogMenus dialogs;
    private final ChestMenus chests;

    public Menus(FAWG plugin) {
        this.plugin = plugin;
        this.dialogs = new DialogMenus(plugin);
        this.chests = new ChestMenus(plugin);
    }

    public ChestMenus chests() { return chests; }
    public DialogMenus dialogs() { return dialogs; }

    public boolean useChest(Player p) {
        String mode = plugin.getConfig().getString("gui.mode", "auto").toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "chest" -> true;
            case "dialog" -> false;
            default -> plugin.bedrock().isBedrock(p);
        };
    }

    public void open(Player p, String regionId) {
        if (p == null) return;
        if (!p.hasPermission("fawg.region.menu") && !p.hasPermission("fawg.admin")) {
            plugin.text().send(p, "error.no-permission");
            return;
        }
        Region r = regionId == null ? null : plugin.regions().find(regionId, p.getWorld());
        if (useChest(p)) {
            if (r != null) chests.region(p, r); else chests.main(p);
        } else {
            try {
                if (r != null) dialogs.region(p, r); else dialogs.main(p);
            } catch (Throwable t) {
                plugin.getLogger().warning("Dialog HUD failed (" + t + "), falling back to chest GUI");
                if (r != null) chests.region(p, r); else chests.main(p);
            }
        }
    }
}
