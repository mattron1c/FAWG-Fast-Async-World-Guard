package dev.mattron1c.fawg.gui;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.BooleanFlag;
import dev.mattron1c.fawg.core.flag.EnumFlag;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.FlagCategory;
import dev.mattron1c.fawg.core.flag.LocationFlag;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.Domain;
import dev.mattron1c.fawg.core.region.ProtectLevel;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.region.RegionGroup;
import dev.mattron1c.fawg.core.shape.Shape;
import dev.mattron1c.fawg.flags.FlagFamilies;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.migrate.WorldGuardImporter;
import dev.mattron1c.fawg.region.PlayerSubject;
import dev.mattron1c.fawg.region.RegionService;
import dev.mattron1c.fawg.region.RegionService.Fail;
import dev.mattron1c.fawg.select.Selection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The FAWG HUD built on Minecraft's native dialog screens (Paper Dialog API, 1.21.6+).
 * Every screen, label and colour comes from messages.yml (gui.*) so it can be fully restyled.
 */
public final class DialogMenus {
    private final FAWG plugin;
    private final ClickCallback.Options opts = ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(30)).build();

    public DialogMenus(FAWG plugin) { this.plugin = plugin; }

    // =========================================================================================== building
    private Component m(Player p, String key, Object... ph) { return plugin.text().component("gui." + key, p, ph); }

    private Component raw(Player p, String template, Object... ph) { return plugin.text().componentRaw(template, p, ph); }

    private ActionButton button(Component label, Component tooltip, int width, BiConsumer<DialogResponseView, Player> click) {
        ActionButton.Builder b = ActionButton.builder(label).width(width).action(DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player pl)) return;
            Runnable r = () -> {
                try {
                    click.accept(view, pl);
                } catch (Fail f) {
                    notice(pl, plugin.text().component(f.key, pl, f.ph), () -> main(pl));
                } catch (Throwable t) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "HUD action failed", t);
                    notice(pl, plugin.text().component("error.internal", pl, "error", t.toString()), () -> main(pl));
                }
            };
            if (Bukkit.isPrimaryThread()) r.run(); else Bukkit.getScheduler().runTask(plugin, r);
        }, opts));
        if (tooltip != null) b.tooltip(tooltip);
        return b.build();
    }

    private ActionButton nav(Player p, String key, int width, Runnable go, Object... ph) {
        return button(m(p, key, ph), null, width, (v, pl) -> go.run());
    }

    private void show(Player p, Component title, List<DialogBody> body, List<DialogInput> inputs, List<ActionButton> buttonList, int columns, ActionButton exit) {
        final List<ActionButton> buttons = buttonList.isEmpty() ? List.of(exit) : buttonList;
        Dialog d = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE).body(body).inputs(inputs).build())
                .type(DialogType.multiAction(buttons).columns(columns).exitAction(exit).build()));
        p.showDialog(d);
    }

    private DialogBody line(Component c) { return DialogBody.plainMessage(c, 320); }

    private DialogBody divider(Player p) { return line(raw(p, "<divider>")); }

    private void notice(Player p, Component msg, Runnable back) {
        Dialog d = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(m(p, "notice-title")).canCloseWithEscape(true).body(List.of(divider(p), line(msg), divider(p))).build())
                .type(DialogType.notice(button(m(p, "back"), null, 200, (v, pl) -> back.run()))));
        p.showDialog(d);
    }

    private int width() { return plugin.getConfig().getInt("gui.button-width", 150); }

    private int columns() { return plugin.getConfig().getInt("gui.columns", 2); }

    // =========================================================================================== main
    public void main(Player p) {
        ApplicableSet here = plugin.regions().query(p.getLocation());
        PlayerSubject sub = PlayerSubject.of(p);
        int owned = 0;
        for (Region r : plugin.regions().world(p.getWorld()).regions()) if (r.owners().contains(sub)) owned++;
        List<DialogBody> body = new ArrayList<>();
        body.add(divider(p));
        body.add(line(m(p, "main.welcome", "player", p.getName())));
        body.add(line(m(p, "main.location", "region", here.isEmpty() ? "wilderness" : here.highest().displayName(), "count", here.size())));
        body.add(line(m(p, "main.stats", "owned", owned, "total", plugin.regions().totalRegions(), "flags", Flags.REGISTRY.size())));
        body.add(divider(p));
        List<ActionButton> b = new ArrayList<>();
        int w = width();
        b.add(nav(p, "main.here", w, () -> {
            if (here.isEmpty()) notice(p, m(p, "main.no-region-here"), () -> main(p)); else region(p, here.highest());
        }));
        b.add(nav(p, "main.mine", w, () -> regionList(p, 0, true, null)));
        if (p.hasPermission("fawg.region.list")) b.add(nav(p, "main.all", w, () -> regionList(p, 0, false, null)));
        if (p.hasPermission("fawg.region.define") || p.hasPermission("fawg.region.claim")) b.add(nav(p, "main.create", w, () -> create(p)));
        if (p.hasPermission("fawg.region.wand")) b.add(nav(p, "main.selection", w, () -> selection(p)));
        b.add(nav(p, "main.library", w, () -> categories(p, null)));
        b.add(nav(p, "main.visualizer", w, () -> { plugin.visualizer().toggleNearby(p); main(p); }));
        b.add(nav(p, "main.settings", w, () -> settings(p)));
        if (p.hasPermission("fawg.admin")) b.add(nav(p, "main.admin", w, () -> admin(p)));
        show(p, m(p, "main.title"), body, List.of(), b, columns(), button(m(p, "close"), null, 200, (v, pl) -> pl.closeDialog()));
    }

    // =========================================================================================== lists
    public void regionList(Player p, int page, boolean mine, String query) {
        RegionWorld rw = plugin.regions().world(p.getWorld());
        PlayerSubject sub = PlayerSubject.of(p);
        List<Region> list = new ArrayList<>();
        for (Region r : rw.regions()) {
            if (mine && !r.isMember(sub)) continue;
            if (query != null && !r.id().contains(query.toLowerCase(Locale.ROOT))) continue;
            list.add(r);
        }
        list.sort(Comparator.comparing(Region::id));
        int per = plugin.getConfig().getInt("gui.page-size", 16);
        int pages = Math.max(1, (list.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(pages - 1, page));
        List<DialogBody> body = List.of(divider(p), line(m(p, "list.info", "count", list.size(), "page", pg + 1, "pages", pages, "world", p.getWorld().getName())), divider(p));
        List<ActionButton> b = new ArrayList<>();
        for (int i = pg * per; i < Math.min(list.size(), (pg + 1) * per); i++) {
            Region r = list.get(i);
            String role = r.owners().contains(sub) ? "owner" : r.members().contains(sub) ? "member" : "";
            b.add(button(m(p, "list.entry", "id", r.id(), "role", role, "priority", r.priority()), m(p, "list.tooltip", "flags", r.flags().size(), "shape", r.shape().type()), width(), (v, pl) -> region(pl, r)));
        }
        if (pg > 0) b.add(nav(p, "prev", width(), () -> regionList(p, pg - 1, mine, query)));
        if (pg < pages - 1) b.add(nav(p, "next", width(), () -> regionList(p, pg + 1, mine, query)));
        b.add(nav(p, "search", width(), () -> search(p, mine)));
        show(p, m(p, mine ? "list.title-mine" : "list.title-all"), body, List.of(), b, columns(), nav(p, "back", 200, () -> main(p)));
    }

    private void search(Player p, boolean mine) {
        List<DialogInput> in = List.of(DialogInput.text("q", m(p, "search-label")).width(300).maxLength(64).build());
        List<ActionButton> b = List.of(button(m(p, "search"), null, 200, (v, pl) -> regionList(pl, 0, mine, v.getText("q"))));
        show(p, m(p, "search-title"), List.of(divider(p)), in, b, 1, nav(p, "back", 200, () -> regionList(p, 0, mine, null)));
    }

    // =========================================================================================== region
    public void region(Player p, Region r) {
        RegionService svc = plugin.service();
        if (!svc.canManage(p, r, "info")) throw new Fail("error.no-permission");
        Shape s = r.shape();
        List<DialogBody> body = new ArrayList<>();
        body.add(divider(p));
        body.add(line(m(p, "region.summary", "id", r.id(), "name", r.displayName(), "world", r.world())));
        if (!r.isGlobal()) {
            body.add(line(m(p, "region.shape", "type", s.type(), "priority", r.priority(), "parent", r.parent() == null ? "-" : r.parent().id())));
            body.add(line(m(p, "region.bounds", "min", s.minX() + ", " + s.minY() + ", " + s.minZ(), "max", s.maxX() + ", " + s.maxY() + ", " + s.maxZ(), "volume", String.format("%,d", s.volume()))));
        }
        body.add(line(m(p, "region.people", "owners", r.owners().size(), "members", r.members().size(), "flags", r.flags().size())));
        ProtectLevel lock = r.flags().get(Flags.REGION_LOCK);
        if (lock != null && lock != ProtectLevel.NONE) body.add(line(m(p, "region.locked", "level", lock.name().toLowerCase(Locale.ROOT))));
        body.add(divider(p));
        List<ActionButton> b = new ArrayList<>();
        int w = width();
        b.add(nav(p, "region.flags", w, () -> categories(p, r)));
        b.add(nav(p, "region.members", w, () -> members(p, r, false)));
        b.add(nav(p, "region.owners", w, () -> members(p, r, true)));
        b.add(nav(p, "region.settings", w, () -> regionSettings(p, r)));
        if (!r.isGlobal()) {
            b.add(nav(p, "region.teleport", w, () -> { p.closeDialog(); p.performCommand("rg tp -w " + r.world() + " " + r.id() + (r.flags().get(Flags.TELE_LOC) == null ? " -c" : "")); }));
            b.add(nav(p, "region.visualize", w, () -> { plugin.visualizer().showRegion(p, r, 60); p.closeDialog(); plugin.text().send(p, "visualize.region", "id", r.id()); }));
            b.add(nav(p, "region.select", w, () -> { p.closeDialog(); p.performCommand("rg select " + r.id()); }));
        }
        b.add(nav(p, "region.presets", w, () -> presets(p, r)));
        b.add(nav(p, "region.lock", w, () -> lock(p, r)));
        if (!r.isGlobal()) b.add(nav(p, "region.delete", w, () -> confirmDelete(p, r)));
        show(p, m(p, "region.title", "id", r.id()), body, List.of(), b, columns(), nav(p, "back", 200, () -> main(p)));
    }

    private void confirmDelete(Player p, Region r) {
        Dialog d = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(m(p, "delete.title", "id", r.id())).canCloseWithEscape(true).body(List.of(divider(p), line(m(p, "delete.body", "id", r.id())), divider(p))).build())
                .type(DialogType.confirmation(
                        button(m(p, "delete.yes"), null, 150, (v, pl) -> { plugin.service().remove(pl, r, false); plugin.text().send(pl, "region.removed", "id", r.id()); regionList(pl, 0, true, null); }),
                        button(m(p, "delete.no"), null, 150, (v, pl) -> region(pl, r)))));
        p.showDialog(d);
    }

    private void regionSettings(Player p, Region r) {
        List<DialogInput> in = new ArrayList<>();
        in.add(DialogInput.text("priority", m(p, "settings.priority")).initial(String.valueOf(r.priority())).width(200).maxLength(11).build());
        if (!r.isGlobal()) {
            in.add(DialogInput.text("parent", m(p, "settings.parent")).initial(r.parent() == null ? "" : r.parent().id()).width(200).maxLength(64).build());
            in.add(DialogInput.text("id", m(p, "settings.rename")).initial(r.id()).width(200).maxLength(64).build());
        }
        String dn = r.flags().get(Flags.DISPLAY_NAME);
        in.add(DialogInput.text("display", m(p, "settings.display")).initial(dn == null ? "" : dn).width(300).maxLength(128).build());
        List<ActionButton> b = List.of(button(m(p, "save"), null, 200, (v, pl) -> {
            RegionService svc = plugin.service();
            Region cur = r;
            String pri = v.getText("priority");
            if (pri != null && !pri.isBlank() && !pri.equals(String.valueOf(cur.priority()))) {
                try { svc.priority(pl, cur, Integer.parseInt(pri.trim())); } catch (NumberFormatException e) { throw new Fail("error.not-a-number", "value", pri); }
            }
            if (!cur.isGlobal()) {
                String par = v.getText("parent");
                String curPar = cur.parent() == null ? "" : cur.parent().id();
                if (par != null && !par.trim().equalsIgnoreCase(curPar)) {
                    Region parent = par.isBlank() ? null : plugin.regions().world(cur.world()).get(par.trim());
                    if (!par.isBlank() && parent == null) throw new Fail("error.unknown-region", "id", par);
                    svc.parent(pl, cur, parent);
                }
            }
            String disp = v.getText("display");
            if (disp != null && !disp.equals(dn == null ? "" : dn)) svc.setFlag(pl, cur, Flags.DISPLAY_NAME, disp.isBlank() ? null : disp, null);
            if (!cur.isGlobal()) {
                String id = v.getText("id");
                if (id != null && !id.isBlank() && !id.equalsIgnoreCase(cur.id())) cur = svc.rename(pl, cur, id.trim());
            }
            plugin.text().send(pl, "gui.saved");
            region(pl, cur);
        }));
        show(p, m(p, "settings.title", "id", r.id()), List.of(divider(p)), in, b, 1, nav(p, "back", 200, () -> region(p, r)));
    }

    // =========================================================================================== members
    private String names(Domain d) {
        List<String> l = new ArrayList<>();
        for (UUID u : d.players()) { OfflinePlayer op = Bukkit.getOfflinePlayer(u); l.add(op.getName() == null ? u.toString().substring(0, 8) : op.getName()); }
        l.addAll(d.names());
        for (String g : d.groups()) l.add("g:" + g);
        return l.isEmpty() ? "-" : String.join(", ", l);
    }

    private void members(Player p, Region r, boolean owners) {
        Domain d = owners ? r.owners() : r.members();
        List<DialogBody> body = List.of(divider(p), line(m(p, owners ? "members.current-owners" : "members.current-members", "list", names(d))), line(m(p, "members.hint")), divider(p));
        List<DialogInput> in = List.of(
                DialogInput.text("who", m(p, "members.who")).width(300).maxLength(512).build(),
                DialogInput.singleOption("op", m(p, "members.action"), List.of(
                        SingleOptionDialogInput.OptionEntry.create("add", m(p, "members.add"), true),
                        SingleOptionDialogInput.OptionEntry.create("remove", m(p, "members.remove"), false))).width(200).build());
        List<ActionButton> b = new ArrayList<>();
        b.add(button(m(p, "apply"), null, 150, (v, pl) -> {
            String who = v.getText("who");
            if (who == null || who.isBlank()) throw new Fail("gui.members.empty");
            List<String> names = new ArrayList<>();
            for (String n : who.split("[,\\s]+")) if (!n.isBlank()) names.add(n.trim());
            if ("remove".equals(v.getText("op"))) plugin.service().removeMembers(pl, r, owners, names, false);
            else plugin.service().addMembers(pl, r, owners, names);
            members(pl, r, owners);
        }));
        b.add(button(m(p, "members.clear"), null, 150, (v, pl) -> { plugin.service().removeMembers(pl, r, owners, List.of(), true); members(pl, r, owners); }));
        show(p, m(p, owners ? "members.title-owners" : "members.title-members", "id", r.id()), body, in, b, 2, nav(p, "back", 200, () -> region(p, r)));
    }

    // =========================================================================================== flags
    /** region == null → flag library (read only). */
    public void categories(Player p, Region r) {
        List<DialogBody> body = List.of(divider(p), line(m(p, "flags.intro", "count", Flags.REGISTRY.size(), "set", r == null ? 0 : r.flags().size())), divider(p));
        List<ActionButton> b = new ArrayList<>();
        if (r != null) b.add(button(m(p, "flags.set-only", "count", r.flags().size()), null, width(), (v, pl) -> flagList(pl, r, null, null, true, 0)));
        b.add(button(m(p, "flags.search"), null, width(), (v, pl) -> flagSearch(pl, r)));
        for (FlagCategory c : FlagCategory.values()) {
            int n = Flags.REGISTRY.category(c).size();
            if (n == 0) continue;
            b.add(button(m(p, "flags.category", "emoji", c.emoji, "name", c.display, "count", n), null, width(), (v, pl) -> flagList(pl, r, c, null, false, 0)));
        }
        show(p, m(p, r == null ? "flags.title-library" : "flags.title", "id", r == null ? "" : r.id()), body, List.of(), b, columns(),
                nav(p, "back", 200, () -> { if (r == null) main(p); else region(p, r); }));
    }

    private void flagSearch(Player p, Region r) {
        List<DialogInput> in = List.of(DialogInput.text("q", m(p, "search-label")).width(300).maxLength(64).build());
        show(p, m(p, "flags.search-title"), List.of(divider(p)), in, List.of(button(m(p, "search"), null, 200, (v, pl) -> flagList(pl, r, null, v.getText("q"), false, 0))), 1,
                nav(p, "back", 200, () -> categories(p, r)));
    }

    private void flagList(Player p, Region r, FlagCategory cat, String query, boolean setOnly, int page) {
        List<Flag<?>> list = new ArrayList<>();
        if (setOnly && r != null) list.addAll(r.flags().snapshot(Flags.REGISTRY).keySet());
        else if (cat != null) list.addAll(Flags.REGISTRY.category(cat));
        else if (query != null) {
            String q = query.toLowerCase(Locale.ROOT);
            for (Flag<?> f : Flags.REGISTRY.all()) if (f.name().contains(q) || f.description().toLowerCase(Locale.ROOT).contains(q)) list.add(f);
        }
        list.sort(Comparator.comparing(Flag::name));
        int per = plugin.getConfig().getInt("gui.page-size", 16);
        int pages = Math.max(1, (list.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(pages - 1, page));
        List<DialogBody> body = List.of(divider(p), line(m(p, "flags.page", "count", list.size(), "page", pg + 1, "pages", pages)), divider(p));
        List<ActionButton> b = new ArrayList<>();
        for (int i = pg * per; i < Math.min(list.size(), (pg + 1) * per); i++) {
            Flag<?> f = list.get(i);
            Object v = r == null ? null : r.flags().get(f);
            @SuppressWarnings("unchecked") String val = v == null ? "-" : ((Flag<Object>) f).format(v);
            String key = v == null ? "flags.entry-unset" : v == StateFlag.State.ALLOW || Boolean.TRUE.equals(v) ? "flags.entry-allow" : v == StateFlag.State.DENY || Boolean.FALSE.equals(v) ? "flags.entry-deny" : "flags.entry-value";
            b.add(button(m(p, key, "flag", f.name(), "value", val.length() > 18 ? val.substring(0, 17) + "…" : val), m(p, "flags.tooltip", "desc", f.description(), "type", f.typeName()), width(),
                    (vv, pl) -> { if (r == null) flagInfo(pl, f, cat, query, pg); else flagEdit(pl, r, f, () -> flagList(pl, r, cat, query, setOnly, pg)); }));
        }
        if (pg > 0) b.add(nav(p, "prev", width(), () -> flagList(p, r, cat, query, setOnly, pg - 1)));
        if (pg < pages - 1) b.add(nav(p, "next", width(), () -> flagList(p, r, cat, query, setOnly, pg + 1)));
        Component title = cat != null ? m(p, "flags.title-category", "emoji", cat.emoji, "name", cat.display) : m(p, "flags.title-results");
        show(p, title, body, List.of(), b, columns(), nav(p, "back", 200, () -> categories(p, r)));
    }

    private void flagInfo(Player p, Flag<?> f, FlagCategory cat, String query, int page) {
        notice(p, m(p, "flags.info", "flag", f.name(), "desc", f.description(), "type", f.typeName(), "category", f.category().display), () -> flagList(p, null, cat, query, false, page));
    }

    private void flagEdit(Player p, Region r, Flag<?> f, Runnable back) {
        Object cur = r.flags().get(f);
        @SuppressWarnings("unchecked") String curStr = cur == null ? "" : ((Flag<Object>) f).format(cur);
        RegionGroup g = r.flags().group(f);
        List<DialogBody> body = List.of(divider(p), line(m(p, "edit.body", "flag", f.name(), "desc", f.description(), "type", f.typeName(), "value", cur == null ? "unset" : curStr,
                "group", g == null ? f.defaultGroup().id() : g.id())), divider(p));
        List<DialogInput> in = new ArrayList<>();
        if (f instanceof StateFlag || f instanceof BooleanFlag) {
            boolean state = f instanceof StateFlag;
            String a = state ? "allow" : "true", d = state ? "deny" : "false";
            in.add(DialogInput.singleOption("value", m(p, "edit.value"), List.of(
                    SingleOptionDialogInput.OptionEntry.create(a, m(p, "edit.allow", "label", a), curStr.equals(a)),
                    SingleOptionDialogInput.OptionEntry.create(d, m(p, "edit.deny", "label", d), curStr.equals(d)),
                    SingleOptionDialogInput.OptionEntry.create("__unset", m(p, "edit.unset"), cur == null))).width(250).build());
        } else if (f instanceof EnumFlag<?> ef) {
            List<SingleOptionDialogInput.OptionEntry> opts = new ArrayList<>();
            for (String s : ef.suggestions()) opts.add(SingleOptionDialogInput.OptionEntry.create(s, Component.text(s), s.equals(curStr)));
            opts.add(SingleOptionDialogInput.OptionEntry.create("__unset", m(p, "edit.unset"), cur == null));
            in.add(DialogInput.singleOption("value", m(p, "edit.value"), opts).width(250).build());
        } else {
            TextDialogInput.Builder tb = DialogInput.text("value", m(p, f instanceof LocationFlag ? "edit.location" : "edit.text")).width(320).maxLength(2048).initial(curStr);
            if (f.typeName().startsWith("text") || f.typeName().startsWith("list") || f.typeName().startsWith("commands")) tb.multiline(TextDialogInput.MultilineOptions.create(6, 80));
            in.add(tb.build());
        }
        List<SingleOptionDialogInput.OptionEntry> groups = new ArrayList<>();
        RegionGroup effective = g == null ? f.defaultGroup() : g;
        for (RegionGroup rg : RegionGroup.values()) groups.add(SingleOptionDialogInput.OptionEntry.create(rg.id(), Component.text(rg.id()), rg == effective));
        in.add(DialogInput.singleOption("group", m(p, "edit.group"), groups).width(250).build());
        List<ActionButton> b = new ArrayList<>();
        b.add(button(m(p, "save"), null, 150, (v, pl) -> {
            String val = v.getText("value");
            RegionGroup grp = RegionGroup.parse(v.getText("group"));
            if (val == null || val.equals("__unset") || (val.isBlank() && !(f instanceof dev.mattron1c.fawg.core.flag.StringFlag))) plugin.service().setFlag(pl, r, f, null, null);
            else plugin.service().setFlag(pl, r, f, val, grp == f.defaultGroup() ? null : grp);
            plugin.text().send(pl, "gui.saved");
            back.run();
        }));
        if (f instanceof LocationFlag) b.add(button(m(p, "edit.here"), null, 150, (v, pl) -> { plugin.service().setFlag(pl, r, f, "here", null); back.run(); }));
        b.add(button(m(p, "edit.clear"), null, 150, (v, pl) -> { plugin.service().setFlag(pl, r, f, null, null); back.run(); }));
        show(p, m(p, "edit.title", "flag", f.name()), body, in, b, 3, nav(p, "back", 200, back));
    }

    // =========================================================================================== presets / lock
    private void presets(Player p, Region r) {
        var sec = plugin.getConfig().getConfigurationSection("presets");
        List<ActionButton> b = new ArrayList<>();
        if (sec != null) for (String k : sec.getKeys(false)) {
            var ps = sec.getConfigurationSection(k);
            int n = ps == null ? 0 : ps.getKeys(false).size();
            b.add(button(m(p, "presets.entry", "preset", k, "count", n), m(p, "presets.tooltip", "flags", ps == null ? "" : String.join(", ", ps.getKeys(false))), width(),
                    (v, pl) -> { int c = plugin.service().preset(pl, r, k); plugin.text().send(pl, "flag.preset", "id", r.id(), "preset", k, "count", c); region(pl, r); }));
        }
        show(p, m(p, "presets.title", "id", r.id()), List.of(divider(p), line(m(p, "presets.body")), divider(p)), List.of(), b, columns(), nav(p, "back", 200, () -> region(p, r)));
    }

    private void lock(Player p, Region r) {
        ProtectLevel cur = r.flags().get(Flags.REGION_LOCK);
        List<SingleOptionDialogInput.OptionEntry> o = new ArrayList<>();
        for (ProtectLevel l : ProtectLevel.values()) o.add(SingleOptionDialogInput.OptionEntry.create(l.name(), m(p, "lock.level-" + l.name().toLowerCase(Locale.ROOT)), l == (cur == null ? ProtectLevel.NONE : cur)));
        List<DialogInput> in = List.of(DialogInput.singleOption("level", m(p, "lock.label"), o).width(300).build());
        show(p, m(p, "lock.title", "id", r.id()), List.of(divider(p), line(m(p, "lock.body")), divider(p)), in,
                List.of(button(m(p, "save"), null, 200, (v, pl) -> { plugin.service().lock(pl, r, ProtectLevel.valueOf(v.getText("level"))); region(pl, r); })), 1,
                nav(p, "back", 200, () -> region(p, r)));
    }

    // =========================================================================================== create / selection
    private void create(Player p) {
        Selection sel = plugin.selections().peek(p);
        String info = sel == null ? "-" : sel.describe();
        Shape we = plugin.worldEdit() != null ? plugin.worldEdit().selection(p) : null;
        List<DialogBody> body = List.of(divider(p), line(m(p, "create.selection", "info", info, "worldedit", we == null ? "-" : we.describe())), line(m(p, "create.hint")), divider(p));
        List<DialogInput> in = new ArrayList<>();
        in.add(DialogInput.text("id", m(p, "create.id")).width(250).maxLength(64).build());
        in.add(DialogInput.bool("vert", m(p, "create.vertical")).initial(true).build());
        if (p.hasPermission("fawg.region.define")) in.add(DialogInput.bool("claim", m(p, "create.claim")).initial(!p.hasPermission("fawg.region.define")).build());
        in.add(DialogInput.text("priority", m(p, "create.priority")).initial("0").width(120).maxLength(11).build());
        List<ActionButton> b = List.of(button(m(p, "create.button"), null, 200, (v, pl) -> {
            String id = v.getText("id");
            if (id == null || id.isBlank()) throw new Fail("usage.define");
            Boolean claimB = v.getBoolean("claim");
            boolean claim = !pl.hasPermission("fawg.region.define") || (claimB != null && claimB);
            Shape shape;
            Selection s = plugin.selections().peek(pl);
            if (s != null && s.complete() && pl.getWorld().getName().equals(s.world)) shape = s.toShape();
            else if (plugin.worldEdit() != null && plugin.worldEdit().selection(pl) != null) shape = plugin.worldEdit().selection(pl);
            else throw new Fail("selection.none");
            Boolean vert = v.getBoolean("vert");
            if (vert != null && vert) shape = shape.withHeight(pl.getWorld().getMinHeight(), pl.getWorld().getMaxHeight() - 1);
            Region r = plugin.service().define(pl, pl.getWorld(), id.trim(), shape, claim, null);
            try { int pri = Integer.parseInt(v.getText("priority").trim()); if (pri != r.priority()) plugin.service().priority(pl, r, pri); } catch (Exception ignored) { }
            plugin.visualizer().showRegion(pl, r, 15);
            plugin.text().send(pl, "region.created", "id", r.id(), "shape", r.shape().describe(), "volume", String.format("%,d", r.shape().volume()));
            region(pl, r);
        }));
        show(p, m(p, "create.title"), body, in, b, 1, nav(p, "back", 200, () -> main(p)));
    }

    private void selection(Player p) {
        Selection s = plugin.selections().get(p);
        List<DialogBody> body = List.of(divider(p), line(m(p, "selection.info", "type", s.type.name().toLowerCase(Locale.ROOT), "info", s.describe())), line(m(p, "selection.hint")), divider(p));
        List<SingleOptionDialogInput.OptionEntry> types = new ArrayList<>();
        for (Selection.Type t : Selection.Type.values()) types.add(SingleOptionDialogInput.OptionEntry.create(t.name(), m(p, "selection.type-" + t.name().toLowerCase(Locale.ROOT)), t == s.type));
        List<DialogInput> in = List.of(DialogInput.singleOption("type", m(p, "selection.type"), types).width(250).build());
        int w = width();
        List<ActionButton> b = new ArrayList<>();
        b.add(button(m(p, "selection.apply-type"), null, w, (v, pl) -> {
            Selection.Type t = Selection.Type.valueOf(v.getText("type"));
            if (t != plugin.selections().get(pl).type) plugin.selections().get(pl).reset(t);
            selection(pl);
        }));
        b.add(nav(p, "selection.wand", w, () -> { p.getInventory().addItem(plugin.selections().wand()); plugin.text().send(p, "wand.given"); selection(p); }));
        b.add(nav(p, "selection.pos1", w, () -> { p.performCommand("rg pos1"); selection(p); }));
        b.add(nav(p, "selection.pos2", w, () -> { p.performCommand("rg pos2"); selection(p); }));
        b.add(nav(p, "selection.vert", w, () -> { p.performCommand("rg expand vert"); selection(p); }));
        b.add(nav(p, "selection.show", w, () -> { plugin.visualizer().showSelection(p); p.closeDialog(); }));
        b.add(nav(p, "selection.clear", w, () -> { p.performCommand("rg clearsel"); selection(p); }));
        show(p, m(p, "selection.title"), body, in, b, columns(), nav(p, "back", 200, () -> main(p)));
    }

    // =========================================================================================== settings / admin
    private void settings(Player p) {
        var sess = plugin.sessions().get(p);
        List<DialogInput> in = new ArrayList<>();
        if (p.hasPermission("fawg.bypass")) in.add(DialogInput.bool("bypass", m(p, "prefs.bypass")).initial(plugin.protection().bypasses(p)).build());
        in.add(DialogInput.bool("nearby", m(p, "prefs.nearby")).initial(sess.visualizeNearby).build());
        show(p, m(p, "prefs.title"), List.of(divider(p), line(m(p, "prefs.body")), divider(p)), in, List.of(button(m(p, "save"), null, 200, (v, pl) -> {
            var s = plugin.sessions().get(pl);
            Boolean by = v.getBoolean("bypass");
            if (by != null) { s.bypassInitialized = true; s.bypass = by; plugin.refreshPlayer(pl); }
            Boolean nb = v.getBoolean("nearby");
            if (nb != null && nb != s.visualizeNearby) plugin.visualizer().toggleNearby(pl);
            plugin.text().send(pl, "gui.saved");
            main(pl);
        })), 1, nav(p, "back", 200, () -> main(p)));
    }

    private void admin(Player p) {
        WorldGuardImporter imp = new WorldGuardImporter(plugin);
        List<DialogBody> body = List.of(divider(p), line(m(p, "admin.body", "regions", plugin.regions().totalRegions(), "flags", Flags.REGISTRY.size(), "generated", FlagFamilies.generatedCount(),
                "wg", imp.available() ? String.join(", ", imp.worldsAvailable()) : "-")), divider(p));
        int w = width();
        List<ActionButton> b = new ArrayList<>();
        b.add(nav(p, "admin.reload", w, () -> { p.performCommand("fawg reload"); admin(p); }));
        b.add(nav(p, "admin.save", w, () -> { p.performCommand("rg save"); admin(p); }));
        b.add(nav(p, "admin.backup", w, () -> { p.performCommand("fawg backup create hud"); admin(p); }));
        b.add(nav(p, "admin.benchmark", w, () -> { p.closeDialog(); p.performCommand("fawg benchmark"); }));
        b.add(nav(p, "admin.stats", w, () -> { p.closeDialog(); p.performCommand("fawg stats"); }));
        if (imp.available()) b.add(nav(p, "admin.import", w, () -> { p.closeDialog(); p.performCommand("fawg import worldguard"); }));
        if (!imp.legacyJars().isEmpty()) b.add(nav(p, "admin.cleanup", w, () -> { p.closeDialog(); p.performCommand("fawg cleanup confirm"); }));
        RegionWorld rw = plugin.regions().world(p.getWorld());
        b.add(button(m(p, "admin.global"), null, w, (v, pl) -> region(pl, rw.global())));
        show(p, m(p, "admin.title"), body, List.of(), b, columns(), nav(p, "back", 200, () -> main(p)));
    }

    public static String unused(Map<String, String> m) { return String.valueOf(m); }
}
