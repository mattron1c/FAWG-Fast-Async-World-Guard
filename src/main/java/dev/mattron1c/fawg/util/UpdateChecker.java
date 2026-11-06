package dev.mattron1c.fawg.util;

import dev.mattron1c.fawg.FAWG;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks Modrinth for a newer version (async, once at startup). */
public final class UpdateChecker {
    private final FAWG plugin;
    private volatile String latest;

    public UpdateChecker(FAWG plugin) { this.plugin = plugin; }

    public String latest() { return latest; }

    public boolean outdated() {
        return latest != null && !latest.equalsIgnoreCase(plugin.getPluginMeta().getVersion()) && compare(latest, plugin.getPluginMeta().getVersion()) > 0;
    }

    public void check() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) return;
        String project = plugin.getConfig().getString("update-checker.modrinth-project", "fawg");
        plugin.regions().runIo(() -> {
            try {
                HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + project + "/version?featured=true"))
                        .header("User-Agent", "FastAsyncWorldGuard/" + plugin.getPluginMeta().getVersion()).timeout(Duration.ofSeconds(8)).build();
                HttpResponse<String> r = c.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) return;
                Matcher m = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"").matcher(r.body());
                if (m.find()) {
                    latest = m.group(1);
                    if (outdated()) plugin.getLogger().info("A new version is available on Modrinth: " + latest);
                }
            } catch (Exception ignored) { }
        });
    }

    static int compare(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\."), y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int u = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0, v = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (u != v) return Integer.compare(u, v);
        }
        return 0;
    }
}
