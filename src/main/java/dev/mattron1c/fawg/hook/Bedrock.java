package dev.mattron1c.fawg.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects Bedrock (Geyser / Floodgate) players without a compile time dependency.
 * Falls back to the Floodgate UUID prefix (00000000-0000-0000-...) when no API is present.
 */
public final class Bedrock {
    private Object floodgate;
    private Method floodgateCheck;
    private Object geyser;
    private Method geyserCheck;
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    public void init() {
        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgate = c.getMethod("getInstance").invoke(null);
                floodgateCheck = c.getMethod("isFloodgatePlayer", UUID.class);
            }
        } catch (Throwable ignored) { floodgate = null; }
        try {
            if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
                Class<?> c = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyser = c.getMethod("api").invoke(null);
                geyserCheck = c.getMethod("isBedrockPlayer", UUID.class);
            }
        } catch (Throwable ignored) { geyser = null; }
    }

    public boolean available() { return floodgate != null || geyser != null; }

    public boolean isBedrock(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), this::check);
    }

    private boolean check(UUID id) {
        try {
            if (floodgate != null && (boolean) floodgateCheck.invoke(floodgate, id)) return true;
            if (geyser != null && (boolean) geyserCheck.invoke(geyser, id)) return true;
        } catch (Throwable ignored) { }
        return id.getMostSignificantBits() == 0L;
    }

    public void forget(UUID id) { cache.remove(id); }
}
