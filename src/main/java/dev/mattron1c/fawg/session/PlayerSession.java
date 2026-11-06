package dev.mattron1c.fawg.session;

import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.region.PlayerSubject;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player runtime state. Only touched from the player's own (main) thread. */
public final class PlayerSession {
    public final UUID uuid;
    public PlayerSubject subject;
    public ApplicableSet current;
    public String world = "";
    public long blockKey = Long.MIN_VALUE;
    public Location lastValid;
    public boolean bypass;
    public boolean bypassInitialized;
    public boolean visualizeNearby;
    public final Map<String, Long> cooldowns = new HashMap<>();
    public final Map<String, Long> timers = new HashMap<>();
    public final Map<String, Long> exitTimes = new HashMap<>();
    public long lastHeal, lastFeed, lastChat;
    // restore state for region effects
    public Float originalWalkSpeed, originalFlySpeed;
    public Boolean originalAllowFlight;
    public GameMode originalGameMode;
    public boolean ghostLocked;
    public boolean flightGranted;
    public boolean glowingSet;
    public boolean timeSet, weatherSet;
    public BossBar bossBar;
    public String bossBarText;
    public String collisionTeam;
    public boolean hidden;
    public long joinedAt = System.currentTimeMillis();
    /** Pending chat prompt (Bedrock / chest GUI text input). */
    public java.util.function.Consumer<String> chatPrompt;

    public PlayerSession(Player p) {
        this.uuid = p.getUniqueId();
        this.subject = PlayerSubject.of(p);
    }

    public boolean cooldown(String key, long millis) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < millis) return false;
        cooldowns.put(key, now);
        return true;
    }
}
