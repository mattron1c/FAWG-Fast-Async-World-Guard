package dev.mattron1c.fawg.text;

import dev.mattron1c.fawg.FAWG;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All player facing text. Messages live in messages.yml and use MiniMessage plus three extra helpers:
 * &lt;sc&gt;small caps&lt;/sc&gt;, &lt;prefix&gt; and &lt;divider&gt;. Bedrock players automatically get the
 * plain letter version when configured, because some small caps glyphs are missing from the Bedrock font.
 */
public final class Text {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final FAWG plugin;
    private YamlConfiguration messages;
    private final Map<String, String> javaCache = new ConcurrentHashMap<>();
    private final Map<String, String> bedrockCache = new ConcurrentHashMap<>();
    private String prefix, divider, dividerShort, star;
    private boolean bedrockSmallCaps;

    public Text(FAWG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(f);
        // fill in any keys added by newer versions
        try (var in = plugin.getResource("messages.yml")) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                boolean changed = false;
                for (String k : def.getKeys(true)) {
                    if (!def.isConfigurationSection(k) && !messages.contains(k)) { messages.set(k, def.get(k)); changed = true; }
                }
                if (changed) messages.save(f);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not merge messages.yml defaults: " + e.getMessage());
        }
        javaCache.clear();
        bedrockCache.clear();
        var cfg = plugin.getConfig();
        star = cfg.getString("style.divider.star", "✦");
        String dash = cfg.getString("style.divider.dash", "━");
        int len = Math.max(2, cfg.getInt("style.divider.length", 12));
        String outer = cfg.getString("style.divider.outer-color", "#5a0000");
        String inner = cfg.getString("style.divider.inner-color", "#ff5c5c");
        String starColor = cfg.getString("style.divider.star-color", "#ffb3b3");
        String dashes = dash.repeat(len), half = dash.repeat(Math.max(2, len / 2));
        divider = "<gradient:" + outer + ":" + inner + ">" + dashes + "</gradient> <" + starColor + "><b>" + star + "</b></" + starColor + "> <gradient:" + inner + ":" + outer + ">" + dashes + "</gradient>";
        dividerShort = "<gradient:" + outer + ":" + inner + ">" + half + "</gradient> <" + starColor + "><b>" + star + "</b></" + starColor + "> <gradient:" + inner + ":" + outer + ">" + half + "</gradient>";
        prefix = cfg.getString("style.prefix", "<gradient:#8b0000:#ff5555><b><sc>FAWG</sc></b></gradient> <dark_gray>»</dark_gray> ");
        bedrockSmallCaps = cfg.getBoolean("style.bedrock-small-caps", false);
    }

    public String divider() { return divider; }
    public String dividerShort() { return dividerShort; }

    /** Raw template for a key (unconverted). */
    public String raw(String key) {
        String s = messages.getString(key);
        if (s == null) {
            List<String> l = messages.getStringList(key);
            s = l.isEmpty() ? "<red>Missing message: " + key : String.join("\n", l);
        }
        return s;
    }

    public boolean has(String key) { return messages.contains(key); }

    private String prepared(String key, boolean bedrock) {
        Map<String, String> cache = bedrock ? bedrockCache : javaCache;
        return cache.computeIfAbsent(key, k -> prepare(raw(k), bedrock));
    }

    /** Applies prefix / divider / small caps to a template. */
    public String prepare(String template, boolean bedrock) {
        String s = template.replace("<prefix>", prefix).replace("<divider>", divider).replace("<divider-short>", dividerShort).replace("<star>", star);
        return SmallCaps.apply(s, !bedrock || bedrockSmallCaps);
    }

    public Component component(String key, CommandSender to, Object... placeholders) {
        return parse(prepared(key, isBedrock(to)), placeholders);
    }

    public Component componentRaw(String template, CommandSender to, Object... placeholders) {
        return parse(prepare(template, isBedrock(to)), placeholders);
    }

    public static Component parse(String prepared, Object... ph) {
        String s = prepared;
        if (ph != null && s.indexOf('%') >= 0) {
            // %key% tokens are substituted textually so they also work inside click / hover arguments
            for (int i = 0; i + 1 < ph.length; i += 2) {
                if (ph[i + 1] instanceof Component) continue;
                String v = String.valueOf(ph[i + 1]).replace("'", "").replace("\"", "").replace("<", "").replace(">", "");
                s = s.replace("%" + ph[i] + "%", v);
            }
        }
        return MM.deserialize(s, resolvers(ph)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Placeholders are given as name, value pairs. Components are inserted as is, everything else unparsed. */
    public static TagResolver resolvers(Object... ph) {
        if (ph == null || ph.length == 0) return TagResolver.empty();
        List<TagResolver> l = new ArrayList<>(ph.length / 2);
        for (int i = 0; i + 1 < ph.length; i += 2) {
            String k = String.valueOf(ph[i]);
            Object v = ph[i + 1];
            if (v instanceof Component c) l.add(Placeholder.component(k, c));
            else if (v instanceof TagResolver t) l.add(t);
            else l.add(Placeholder.unparsed(k, String.valueOf(v)));
        }
        return TagResolver.resolver(l);
    }

    public void send(CommandSender to, String key, Object... ph) {
        if (to == null) return;
        String t = raw(key);
        if (t.isEmpty()) return;
        to.sendMessage(component(key, to, ph));
    }

    public void sendRaw(CommandSender to, String template, Object... ph) {
        to.sendMessage(componentRaw(template, to, ph));
    }

    public void actionBar(Player p, String key, Object... ph) {
        p.sendActionBar(component(key, p, ph));
    }

    public void title(Player p, String titleKey, String subKey, int fadeIn, int stay, int fadeOut, Object... ph) {
        Component t = titleKey == null ? Component.empty() : component(titleKey, p, ph);
        Component s = subKey == null ? Component.empty() : component(subKey, p, ph);
        p.showTitle(Title.title(t, s, Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    /** Renders user supplied text (flags like greeting): MiniMessage first, legacy &amp; codes as fallback. */
    public Component userText(String s, CommandSender to, Object... ph) {
        if (s == null) return Component.empty();
        if (s.indexOf('&') >= 0 && s.indexOf('<') < 0) {
            String r = s;
            for (int i = 0; i + 1 < ph.length; i += 2) r = r.replace("%" + ph[i] + "%", String.valueOf(ph[i + 1])).replace("{" + ph[i] + "}", String.valueOf(ph[i + 1]));
            return LEGACY.deserialize(r);
        }
        String r = s;
        for (int i = 0; i + 1 < ph.length; i += 2) r = r.replace("%" + ph[i] + "%", "<" + ph[i] + ">").replace("{" + ph[i] + "}", "<" + ph[i] + ">");
        try {
            return parse(prepare(r, isBedrock(to)), ph);
        } catch (Exception e) {
            return Component.text(s);
        }
    }

    public static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }

    public boolean isBedrock(CommandSender s) {
        return s instanceof Player p && plugin.bedrock().isBedrock(p);
    }

    /** Broadcast to everyone holding a permission (and console). */
    public void notify(String permission, String key, Object... ph) {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission(permission)) send(p, key, ph);
        send(Bukkit.getConsoleSender(), key, ph);
    }

    public static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static Audience console() { return Bukkit.getConsoleSender(); }
}
