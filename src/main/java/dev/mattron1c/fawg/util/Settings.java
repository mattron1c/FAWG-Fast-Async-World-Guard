package dev.mattron1c.fawg.util;

import org.bukkit.configuration.file.FileConfiguration;

/** Hot-path config values cached as plain fields. */
public final class Settings {
    public boolean redstoneFlag;
    public boolean fireDisabled;

    public void load(FileConfiguration c) {
        redstoneFlag = c.getBoolean("performance.redstone-flag", true);
        fireDisabled = c.getBoolean("runtime.fire-disabled", false);
    }
}
