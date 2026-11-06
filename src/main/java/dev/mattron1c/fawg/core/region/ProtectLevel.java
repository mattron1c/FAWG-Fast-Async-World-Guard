package dev.mattron1c.fawg.core.region;

/**
 * Hard-lock levels (absorbs the WorldGuardRegionProtect addon).
 * SERVER  - nobody except console / fawg.lock.bypass may modify the region, its blocks, or use WorldEdit in it.
 * PLAYER  - like SERVER, but region owners keep full access.
 * BREAK_ALLOWED - region is locked, but block breaking is still allowed for everyone allowed by flags.
 * BREAK_ONLY    - only breaking is allowed (no placing / interacting with protected types).
 */
public enum ProtectLevel {
    NONE, SERVER, PLAYER, BREAK_ALLOWED, BREAK_ONLY;

    public static ProtectLevel parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
