package dev.mattron1c.fawg.session;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.StateFlag.State;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.protect.Protection;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.WeatherType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies "while inside" flags (game mode, flight, speeds, time, weather, boss bars, collision, effects,
 * heal / feed, sounds, repeating commands) and restores everything when the player leaves.
 */
public final class RegionEffects {
    private static final String TEAM = "fawg_nocollide";
    private final FAWG plugin;
    private final Map<UUID, Set<String>> givenEffects = new ConcurrentHashMap<>();

    public RegionEffects(FAWG plugin) { this.plugin = plugin; }

    public boolean isOurs(Player p, PotionEffect e) {
        Set<String> s = givenEffects.get(p.getUniqueId());
        return s != null && s.contains(e.getType().getKey().getKey());
    }

    // ------------------------------------------------------------------------------------ on change
    public void refresh(Player p, PlayerSession s, ApplicableSet set) {
        Protection pr = plugin.protection();
        boolean bypass = pr.bypasses(p);

        // game mode
        GameMode gm = bypass ? null : pr.regionValue(p, set, Flags.GAME_MODE);
        if (gm != null) {
            if (s.ghostLocked) plugin.ghost().release(p, s);
            if (s.originalGameMode == null) s.originalGameMode = p.getGameMode();
            if (p.getGameMode() != gm) p.setGameMode(gm);
        } else if (s.originalGameMode != null) {
            if (p.getGameMode() != s.originalGameMode) p.setGameMode(s.originalGameMode);
            s.originalGameMode = null;
        }

        // flight
        State fly = pr.regionValue(p, set, Flags.FLY);
        boolean flyBypass = p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.hasPermission("fawg.fly.bypass");
        if (fly == State.ALLOW && !p.getAllowFlight()) {
            if (s.originalAllowFlight == null) s.originalAllowFlight = false;
            p.setAllowFlight(true);
            s.flightGranted = true;
        } else if (fly == State.DENY && !flyBypass && (p.getAllowFlight() || p.isFlying())) {
            if (s.originalAllowFlight == null) s.originalAllowFlight = p.getAllowFlight();
            p.setFlying(false);
            p.setAllowFlight(false);
        } else if (fly == null && s.originalAllowFlight != null) {
            boolean orig = s.originalAllowFlight;
            s.originalAllowFlight = null;
            if (!flyBypass) {
                if (!orig) p.setFlying(false);
                p.setAllowFlight(orig);
            }
            s.flightGranted = false;
        }

        // glide
        Flags.GlideMode glide = bypass ? null : pr.regionValue(p, set, Flags.GLIDE);
        if ((glide == Flags.GlideMode.DENY || glide == Flags.GlideMode.FORCE) && p.isGliding()) p.setGliding(false);

        // speeds
        Double ws = pr.regionValue(p, set, Flags.WALK_SPEED);
        if (ws != null) { if (s.originalWalkSpeed == null) s.originalWalkSpeed = p.getWalkSpeed(); safeWalk(p, ws.floatValue()); }
        else if (s.originalWalkSpeed != null) { safeWalk(p, s.originalWalkSpeed); s.originalWalkSpeed = null; }
        Double fs = pr.regionValue(p, set, Flags.FLY_SPEED);
        if (fs != null) { if (s.originalFlySpeed == null) s.originalFlySpeed = p.getFlySpeed(); safeFly(p, fs.floatValue()); }
        else if (s.originalFlySpeed != null) { safeFly(p, s.originalFlySpeed); s.originalFlySpeed = null; }

        // glowing
        Boolean glow = pr.regionValue(p, set, Flags.GLOWING);
        if (glow != null && glow) { if (!p.isGlowing()) { p.setGlowing(true); s.glowingSet = true; } }
        else if (s.glowingSet) { p.setGlowing(false); s.glowingSet = false; }

        // time / weather
        String time = pr.value(p, set, Flags.TIME_LOCK);
        if (time != null && !time.isBlank()) {
            try {
                boolean rel = time.startsWith("+") || time.startsWith("-");
                long t = Long.parseLong(time.startsWith("+") ? time.substring(1) : time);
                p.setPlayerTime(t, rel);
                s.timeSet = true;
            } catch (NumberFormatException ignored) { }
        } else if (s.timeSet) { p.resetPlayerTime(); s.timeSet = false; }
        WeatherType wt = pr.value(p, set, Flags.WEATHER_LOCK);
        if (wt != null) { p.setPlayerWeather(wt); s.weatherSet = true; }
        else if (s.weatherSet) { p.resetPlayerWeather(); s.weatherSet = false; }

        // boss bar
        String bar = pr.value(p, set, Flags.ENTRY_BOSSBAR);
        if (bar != null && !bar.isBlank()) {
            if (s.bossBar == null) {
                s.bossBar = BossBar.bossBar(plugin.text().userText(bar, p, "player", p.getName()), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                p.showBossBar(s.bossBar);
            } else if (!bar.equals(s.bossBarText)) {
                s.bossBar.name(plugin.text().userText(bar, p, "player", p.getName()));
            }
            s.bossBarText = bar;
        } else if (s.bossBar != null) {
            p.hideBossBar(s.bossBar);
            s.bossBar = null;
            s.bossBarText = null;
        }

        // collision
        Boolean noCollide = pr.value(p, set, Flags.DISABLE_COLLISION);
        if (noCollide != null && noCollide) addToTeam(p, s); else removeFromTeam(p, s);

        // hide players
        Boolean hide = pr.value(p, set, Flags.HIDE_PLAYERS);
        boolean h = hide != null && hide && plugin.getConfig().getBoolean("features.hide-players", true);
        if (h != s.hidden) { s.hidden = h; updateVisibility(p, s); }

        // chambered pearls
        if (pr.regionValue(p, set, Flags.CHAMBERED_ENDERPEARL) == State.DENY) {
            for (var pearl : p.getWorld().getEntitiesByClass(org.bukkit.entity.EnderPearl.class)) {
                if (p.equals(pearl.getShooter())) pearl.remove();
            }
        }

        // effects that must disappear immediately
        removeBlockedEffects(p, set);
        plugin.ghost().update(p, s, set);
    }

    private static void safeWalk(Player p, float v) { try { p.setWalkSpeed(Math.max(-1f, Math.min(1f, v))); } catch (IllegalArgumentException ignored) { } }
    private static void safeFly(Player p, float v) { try { p.setFlySpeed(Math.max(-1f, Math.min(1f, v))); } catch (IllegalArgumentException ignored) { } }

    private void addToTeam(Player p, PlayerSession s) {
        if (s.collisionTeam != null) return;
        Scoreboard sb = p.getScoreboard();
        if (sb.getEntryTeam(p.getName()) != null) return; // already managed by another plugin (TAB etc.)
        Team t = sb.getTeam(TEAM);
        if (t == null) {
            t = sb.registerNewTeam(TEAM);
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        t.addEntry(p.getName());
        s.collisionTeam = TEAM;
    }

    private void removeFromTeam(Player p, PlayerSession s) {
        if (s.collisionTeam == null) return;
        Team t = p.getScoreboard().getTeam(TEAM);
        if (t != null) t.removeEntry(p.getName());
        s.collisionTeam = null;
    }

    private void updateVisibility(Player p, PlayerSession s) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            PlayerSession os = plugin.sessions().get(o);
            boolean hideEachOther = s.hidden && os.hidden;
            if (hideEachOther) { p.hidePlayer(plugin, o); o.hidePlayer(plugin, p); }
            else { p.showPlayer(plugin, o); o.showPlayer(plugin, p); }
        }
    }

    private void removeBlockedEffects(Player p, ApplicableSet set) {
        Set<String> blocked = plugin.protection().value(p, set, Flags.BLOCKED_EFFECTS);
        if (blocked == null || blocked.isEmpty()) return;
        for (String b : blocked) {
            PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(b.split(":")[0]));
            if (t != null && p.hasPotionEffect(t)) p.removePotionEffect(t);
        }
    }

    /** Restores everything (quit / disable). */
    public void cleanup(Player p, PlayerSession s) {
        if (s.originalGameMode != null) { p.setGameMode(s.originalGameMode); s.originalGameMode = null; }
        if (s.originalAllowFlight != null) { p.setAllowFlight(s.originalAllowFlight); s.originalAllowFlight = null; }
        if (s.originalWalkSpeed != null) { safeWalk(p, s.originalWalkSpeed); s.originalWalkSpeed = null; }
        if (s.originalFlySpeed != null) { safeFly(p, s.originalFlySpeed); s.originalFlySpeed = null; }
        if (s.glowingSet) { p.setGlowing(false); s.glowingSet = false; }
        if (s.timeSet) p.resetPlayerTime();
        if (s.weatherSet) p.resetPlayerWeather();
        if (s.bossBar != null) { p.hideBossBar(s.bossBar); s.bossBar = null; }
        removeFromTeam(p, s);
        if (s.hidden) { s.hidden = false; updateVisibility(p, s); }
        Set<String> given = givenEffects.remove(p.getUniqueId());
        if (given != null) for (String k : given) {
            PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(k));
            if (t != null) p.removePotionEffect(t);
        }
        plugin.ghost().release(p, s);
    }

    // ------------------------------------------------------------------------------------ per second
    public void tick() {
        long now = System.currentTimeMillis();
        Protection pr = plugin.protection();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerSession s = plugin.sessions().get(p);
            ApplicableSet set = s.current;
            if (set == null) continue;
            if (p.isDead()) continue;
            // heal
            Integer hd = pr.value(p, set, Flags.HEAL_DELAY), ha = pr.value(p, set, Flags.HEAL_AMOUNT);
            if (hd != null && ha != null && ha != 0 && now - s.lastHeal >= hd * 1000L) {
                s.lastHeal = now;
                AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
                double max = maxAttr == null ? 20 : maxAttr.getValue();
                Double minH = pr.value(p, set, Flags.MIN_HEAL), maxH = pr.value(p, set, Flags.MAX_HEAL);
                double cap = maxH == null ? max : Math.min(max, maxH);
                double floor = minH == null ? 0 : minH;
                double hp = p.getHealth();
                if (ha > 0 && hp < cap && hp >= floor) p.setHealth(Math.min(cap, hp + ha));
                else if (ha < 0 && hp > floor) p.setHealth(Math.max(Math.max(floor, 0.5), hp + ha));
            }
            // feed
            Integer fd = pr.value(p, set, Flags.FEED_DELAY), fa = pr.value(p, set, Flags.FEED_AMOUNT);
            if (fd != null && fa != null && fa != 0 && now - s.lastFeed >= fd * 1000L) {
                s.lastFeed = now;
                Integer minF = pr.value(p, set, Flags.MIN_FOOD), maxF = pr.value(p, set, Flags.MAX_FOOD);
                int cap = maxF == null ? 20 : maxF, floor = minF == null ? 0 : minF;
                int food = p.getFoodLevel();
                if (fa > 0 && food < cap && food >= floor) { p.setFoodLevel(Math.min(cap, food + fa)); p.setSaturation(Math.min(p.getSaturation() + fa, cap)); }
                else if (fa < 0 && food > floor) p.setFoodLevel(Math.max(floor, food + fa));
            }
            // give effects
            Set<String> give = pr.value(p, set, Flags.GIVE_EFFECTS);
            Set<String> mine = givenEffects.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            Set<String> active = new HashSet<>();
            if (give != null) for (String g : give) {
                String[] parts = g.split(":");
                PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0]));
                if (t == null) continue;
                int amp = parts.length > 1 ? parseInt(parts[1], 0) : 0;
                boolean particles = parts.length <= 2 || Boolean.parseBoolean(parts[2]);
                active.add(t.getKey().getKey());
                PotionEffect cur = p.getPotionEffect(t);
                if (cur == null || cur.getDuration() < 220 && cur.getDuration() != PotionEffect.INFINITE_DURATION || cur.getAmplifier() < amp) {
                    mine.add(t.getKey().getKey());
                    p.addPotionEffect(new PotionEffect(t, 400, amp, true, particles, true));
                }
            }
            for (String k : new HashSet<>(mine)) {
                if (!active.contains(k)) {
                    mine.remove(k);
                    PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(k));
                    PotionEffect cur = t == null ? null : p.getPotionEffect(t);
                    if (cur != null && cur.isAmbient() && cur.getDuration() <= 400) p.removePotionEffect(t);
                }
            }
            removeBlockedEffects(p, set);
            // sounds
            Set<String> sounds = pr.value(p, set, Flags.PLAY_SOUNDS);
            if (sounds != null) for (String snd : sounds) {
                String[] parts = snd.split(":(?=[0-9.]+$)|:(?=[0-9.]+:)");
                String key = parts[0];
                int interval = parts.length > 1 ? parseInt(parts[1], 10) : 10;
                float vol = parts.length > 2 ? (float) parseDouble(parts[2], 1) : 1f;
                float pitch = parts.length > 3 ? (float) parseDouble(parts[3], 1) : 1f;
                String tk = "sound:" + snd;
                Long last = s.timers.get(tk);
                if (last == null || now - last >= interval * 1000L) {
                    s.timers.put(tk, now);
                    try { p.playSound(Sound.sound(Key.key(key), Sound.Source.AMBIENT, vol, pitch)); } catch (Exception ignored) { }
                }
            }
            // repeating console commands
            repeat(p, s, set, Flags.CONSOLE_COMMAND_REPEAT, "repeat:", now, 60);
            repeat(p, s, set, Flags.CONSOLE_COMMAND_TIMER, "timer:", now, 300);
            // keep glide forced off
            Flags.GlideMode glide = pr.bypasses(p) ? null : pr.regionValue(p, set, Flags.GLIDE);
            if (glide == Flags.GlideMode.FORCE && p.isGliding()) p.setGliding(false);
        }
    }

    private void repeat(Player p, PlayerSession s, ApplicableSet set, dev.mattron1c.fawg.core.flag.StringFlag flag, String prefix, long now, int maxInterval) {
        String v = plugin.protection().value(p, set, flag);
        if (v == null || v.isBlank()) return;
        String t = v.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() > 1) t = t.substring(1, t.length() - 1);
        int sp = t.indexOf(' ');
        if (sp < 0) return;
        int interval = Math.max(1, Math.min(maxInterval, parseInt(t.substring(0, sp), 10)));
        String cmd = t.substring(sp + 1).replace("%player%", p.getName()).replace("{player}", p.getName()).replace("%uuid%", p.getUniqueId().toString());
        String key = prefix + v;
        Long last = s.timers.get(key);
        if (last == null) { s.timers.put(key, now); return; }
        if (now - last >= interval * 1000L) {
            s.timers.put(key, now);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
    }

    /** Timer flags reset on exit; repeat flags keep their cooldown. */
    public void onLeave(PlayerSession s) {
        s.timers.keySet().removeIf(k -> k.startsWith("timer:"));
    }

    private static int parseInt(String s, int d) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; } }
    private static double parseDouble(String s, double d) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return d; } }
}
