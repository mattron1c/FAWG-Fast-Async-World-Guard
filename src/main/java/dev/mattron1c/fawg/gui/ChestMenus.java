package dev.mattron1c.fawg.gui;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.BooleanFlag;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.FlagCategory;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.region.PlayerSubject;
import dev.mattron1c.fawg.region.RegionService.Fail;
import dev.mattron1c.fawg.session.PlayerSession;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Chest based HUD. Used for Bedrock (Geyser) players, who cannot open Java dialogs, or for everybody
 * when gui.mode = chest. Text input is done through a one-shot chat prompt.
 */
public final class ChestMenus implements Listener {
    public static final class Gui implements InventoryHolder {
        final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final FAWG plugin;

    public ChestMenus(FAWG plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------------------------- helpers
    private Component m(Player p, String key, Object... ph) { return plugin.text().component("gui." + key, p, ph); }

    private ItemStack item(Material mat, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(mat == null ? Material.PAPER : mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        if (lore != null) meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private Gui gui(Player p, Component title, int rows) {
        Gui g = new Gui();
        g.inv = Bukkit.createInventory(g, rows * 9, title);
        ItemStack filler = item(Material.RED_STAINED_GLASS_PANE, Component.text(" "), null);
        for (int i = 0; i < rows * 9; i++) if (i < 9 || i >= rows * 9 - 9) g.inv.setItem(i, filler);
        return g;
    }

    private void set(Gui g, int slot, ItemStack it, Consumer<InventoryClickEvent> a) {
        g.inv.setItem(slot, it);
        if (a != null) g.actions.put(slot, a);
    }

    private void safe(Player p, Runnable r) {
        try { r.run(); } catch (Fail f) { plugin.text().send(p, f.key, f.ph); p.closeInventory(); }
    }

    /** Asks the player to type something in chat ("cancel" aborts). */
    public void prompt(Player p, String key, Consumer<String> answer, Object... ph) {
        p.closeInventory();
        plugin.text().send(p, "gui.prompt." + key, ph);
        plugin.sessions().get(p).chatPrompt = answer;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        PlayerSession s = plugin.sessions().get(e.getPlayer());
        Consumer<String> c = s.chatPrompt;
        if (c == null) return;
        s.chatPrompt = null;
        e.setCancelled(true);
        String msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancel")) { plugin.text().send(e.getPlayer(), "gui.prompt.cancelled"); return; }
            safe(e.getPlayer(), () -> c.accept(msg));
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Gui g)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        Consumer<InventoryClickEvent> a = g.actions.get(e.getRawSlot());
        if (a != null && e.getWhoClicked() instanceof Player p) safe(p, () -> a.accept(e));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Gui) e.setCancelled(true);
    }

    // ------------------------------------------------------------------------------------- screens
    public void main(Player p) {
        Gui g = gui(p, m(p, "main.title"), 5);
        ApplicableSet here = plugin.regions().query(p.getLocation());
        set(g, 20, item(Material.COMPASS, m(p, "main.here"), List.of(m(p, "main.location", "region", here.isEmpty() ? "wilderness" : here.highest().displayName(), "count", here.size()))),
                e -> { if (!here.isEmpty()) region(p, here.highest()); else plugin.text().send(p, "gui.main.no-region-here"); });
        set(g, 21, item(Material.BOOK, m(p, "main.mine"), null), e -> list(p, 0, true));
        if (p.hasPermission("fawg.region.list")) set(g, 22, item(Material.MAP, m(p, "main.all"), null), e -> list(p, 0, false));
        if (p.hasPermission("fawg.region.define") || p.hasPermission("fawg.region.claim"))
            set(g, 23, item(Material.EMERALD, m(p, "main.create"), null), e -> prompt(p, "create", id -> p.performCommand((p.hasPermission("fawg.region.define") ? "rg define " : "rg claim ") + id)));
        if (p.hasPermission("fawg.region.wand")) set(g, 24, item(Material.BLAZE_ROD, m(p, "selection.wand"), null), e -> { p.getInventory().addItem(plugin.selections().wand()); p.closeInventory(); });
        set(g, 31, item(Material.SPYGLASS, m(p, "main.visualizer"), null), e -> { plugin.visualizer().toggleNearby(p); p.closeInventory(); });
        if (p.hasPermission("fawg.admin")) set(g, 32, item(Material.COMMAND_BLOCK, m(p, "main.admin"), null), e -> admin(p));
        set(g, 40, item(Material.BARRIER, m(p, "close"), null), e -> p.closeInventory());
        p.openInventory(g.inv);
    }

    private void list(Player p, int page, boolean mine) {
        PlayerSubject sub = PlayerSubject.of(p);
        List<Region> regions = new ArrayList<>();
        for (Region r : plugin.regions().world(p.getWorld()).regions()) if (!mine || r.isMember(sub)) regions.add(r);
        regions.sort(Comparator.comparing(Region::id));
        int per = 36, pages = Math.max(1, (regions.size() + per - 1) / per), pg = Math.max(0, Math.min(pages - 1, page));
        Gui g = gui(p, m(p, mine ? "list.title-mine" : "list.title-all"), 6);
        int slot = 9;
        for (int i = pg * per; i < Math.min(regions.size(), (pg + 1) * per); i++) {
            Region r = regions.get(i);
            set(g, slot++, item(r.owners().contains(sub) ? Material.LIME_BANNER : Material.WHITE_BANNER, Component.text(r.id()),
                    List.of(m(p, "list.tooltip", "flags", r.flags().size(), "shape", r.shape().type()))), e -> region(p, r));
        }
        if (pg > 0) set(g, 45, item(Material.ARROW, m(p, "prev"), null), e -> list(p, pg - 1, mine));
        set(g, 49, item(Material.OAK_DOOR, m(p, "back"), null), e -> main(p));
        if (pg < pages - 1) set(g, 53, item(Material.ARROW, m(p, "next"), null), e -> list(p, pg + 1, mine));
        p.openInventory(g.inv);
    }

    public void region(Player p, Region r) {
        if (!plugin.service().canManage(p, r, "info")) throw new Fail("error.no-permission");
        Gui g = gui(p, m(p, "region.title", "id", r.id()), 5);
        set(g, 19, item(Material.WHITE_BANNER, m(p, "region.flags"), List.of(m(p, "region.people", "owners", r.owners().size(), "members", r.members().size(), "flags", r.flags().size()))), e -> categories(p, r));
        set(g, 20, item(Material.PLAYER_HEAD, m(p, "region.members"), null), e -> prompt(p, "members", s -> memberCommand(p, r, s, false), "id", r.id()));
        set(g, 21, item(Material.GOLDEN_HELMET, m(p, "region.owners"), null), e -> prompt(p, "owners", s -> memberCommand(p, r, s, true), "id", r.id()));
        set(g, 22, item(Material.ANVIL, m(p, "region.settings"), null), e -> prompt(p, "priority", s -> { p.performCommand("rg setpriority -w " + r.world() + " " + r.id() + " " + s); region(p, r); }, "id", r.id()));
        if (!r.isGlobal()) {
            set(g, 23, item(Material.ENDER_PEARL, m(p, "region.teleport"), null), e -> { p.closeInventory(); p.performCommand("rg tp -w " + r.world() + " " + r.id() + " -c"); });
            set(g, 24, item(Material.SPYGLASS, m(p, "region.visualize"), null), e -> { plugin.visualizer().showRegion(p, r, 60); p.closeInventory(); });
            set(g, 25, item(Material.TNT, m(p, "region.delete"), List.of(m(p, "delete.body", "id", r.id()))), e -> {
                if (e.isShiftClick()) { plugin.service().remove(p, r, false); plugin.text().send(p, "region.removed", "id", r.id()); list(p, 0, true); }
                else plugin.text().send(p, "gui.shift-to-confirm");
            });
        }
        set(g, 40, item(Material.OAK_DOOR, m(p, "back"), null), e -> main(p));
        p.openInventory(g.inv);
    }

    private void memberCommand(Player p, Region r, String input, boolean owners) {
        String[] parts = input.trim().split("\\s+", 2);
        boolean remove = parts[0].equalsIgnoreCase("remove") || parts[0].equals("-");
        String who = parts.length > 1 && (remove || parts[0].equalsIgnoreCase("add") || parts[0].equals("+")) ? parts[1] : input;
        List<String> names = new ArrayList<>(List.of(who.split("[,\\s]+")));
        if (remove) plugin.service().removeMembers(p, r, owners, names, false);
        else plugin.service().addMembers(p, r, owners, names);
        plugin.text().send(p, "gui.saved");
        region(p, r);
    }

    private void categories(Player p, Region r) {
        Gui g = gui(p, m(p, "flags.title", "id", r.id()), 6);
        int slot = 9;
        for (FlagCategory c : FlagCategory.values()) {
            int n = Flags.REGISTRY.category(c).size();
            if (n == 0 || slot >= 45) continue;
            set(g, slot++, item(Material.matchMaterial(c.icon), m(p, "flags.category", "emoji", c.emoji, "name", c.display, "count", n), null), e -> flags(p, r, c, 0));
        }
        set(g, 49, item(Material.OAK_DOOR, m(p, "back"), null), e -> region(p, r));
        p.openInventory(g.inv);
    }

    private void flags(Player p, Region r, FlagCategory c, int page) {
        List<Flag<?>> list = new ArrayList<>(Flags.REGISTRY.category(c));
        list.sort(Comparator.comparing(Flag::name));
        int per = 36, pages = Math.max(1, (list.size() + per - 1) / per), pg = Math.max(0, Math.min(pages - 1, page));
        Gui g = gui(p, m(p, "flags.title-category", "emoji", c.emoji, "name", c.display), 6);
        int slot = 9;
        for (int i = pg * per; i < Math.min(list.size(), (pg + 1) * per); i++) {
            Flag<?> f = list.get(i);
            Object v = r.flags().get(f);
            Material mat = v == null ? Material.GRAY_DYE : (v == StateFlag.State.ALLOW || Boolean.TRUE.equals(v)) ? Material.LIME_DYE : (v == StateFlag.State.DENY || Boolean.FALSE.equals(v)) ? Material.RED_DYE : Material.ORANGE_DYE;
            @SuppressWarnings("unchecked") String val = v == null ? "unset" : ((Flag<Object>) f).format(v);
            set(g, slot++, item(mat, Component.text(f.name()), List.of(Component.text(val), m(p, "flags.tooltip", "desc", f.description(), "type", f.typeName()), m(p, "chest-flag-hint"))), e -> {
                if (f instanceof StateFlag || f instanceof BooleanFlag) {
                    String next;
                    if (v == null) next = f instanceof StateFlag ? "allow" : "true";
                    else if (v == StateFlag.State.ALLOW || Boolean.TRUE.equals(v)) next = f instanceof StateFlag ? "deny" : "false";
                    else next = null;
                    plugin.service().setFlag(p, r, f, next, null);
                    flags(p, r, c, pg);
                } else {
                    prompt(p, "flag", s -> { plugin.service().setFlag(p, r, f, s.equalsIgnoreCase("unset") ? null : s, null); plugin.text().send(p, "gui.saved"); flags(p, r, c, pg); },
                            "flag", f.name(), "type", f.typeName());
                }
            });
        }
        if (pg > 0) set(g, 45, item(Material.ARROW, m(p, "prev"), null), e -> flags(p, r, c, pg - 1));
        set(g, 49, item(Material.OAK_DOOR, m(p, "back"), null), e -> categories(p, r));
        if (pg < pages - 1) set(g, 53, item(Material.ARROW, m(p, "next"), null), e -> flags(p, r, c, pg + 1));
        p.openInventory(g.inv);
    }

    private void admin(Player p) {
        Gui g = gui(p, m(p, "admin.title"), 3);
        set(g, 10, item(Material.REPEATER, m(p, "admin.reload"), null), e -> { p.closeInventory(); p.performCommand("fawg reload"); });
        set(g, 11, item(Material.WRITABLE_BOOK, m(p, "admin.save"), null), e -> { p.closeInventory(); p.performCommand("rg save"); });
        set(g, 12, item(Material.CHEST, m(p, "admin.backup"), null), e -> { p.closeInventory(); p.performCommand("fawg backup create hud"); });
        set(g, 13, item(Material.CLOCK, m(p, "admin.benchmark"), null), e -> { p.closeInventory(); p.performCommand("fawg benchmark"); });
        set(g, 14, item(Material.HOPPER, m(p, "admin.import"), null), e -> { p.closeInventory(); p.performCommand("fawg import worldguard"); });
        set(g, 15, item(Material.GRASS_BLOCK, m(p, "admin.global"), null), e -> region(p, plugin.regions().world(p.getWorld()).global()));
        set(g, 22, item(Material.OAK_DOOR, m(p, "back"), null), e -> main(p));
        p.openInventory(g.inv);
    }

    public static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
