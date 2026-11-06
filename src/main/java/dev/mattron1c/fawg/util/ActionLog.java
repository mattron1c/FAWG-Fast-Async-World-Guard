package dev.mattron1c.fawg.util;

import dev.mattron1c.fawg.FAWG;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Lightweight async audit log (logs/actions-YYYY-MM-DD.log): region changes, denials, WorldEdit blocks. */
public final class ActionLog {
    private final FAWG plugin;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean enabled = true;

    public ActionLog(FAWG plugin) { this.plugin = plugin; }

    public void reload() { enabled = plugin.getConfig().getBoolean("logging.actions", true); }

    public void log(String line) {
        if (!enabled) return;
        queue.add(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + line);
    }

    /** Called from the IO thread periodically. */
    public void flush() {
        if (queue.isEmpty()) return;
        File dir = new File(plugin.getDataFolder(), "logs");
        if (!dir.exists() && !dir.mkdirs()) return;
        File f = new File(dir, "actions-" + LocalDate.now() + ".log");
        try (BufferedWriter w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String s;
            while ((s = queue.poll()) != null) { w.write(s); w.newLine(); }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write action log: " + e.getMessage());
        }
    }
}
