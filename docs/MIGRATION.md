# ━━━ ✦ Migrating from WorldGuard ✦ ━━━

1. **Back up** your server (FAWG also zips its own data before importing).
2. Install FAWG next to WorldGuard and start the server. FAWG registers `/rg` first, and WorldGuard's commands stay reachable as `/worldguard:rg`.
3. Run:
   ```text
   /fawg import worldguard                  all worlds
   /fawg import worldguard world --replace  one world, overwrite existing ids
   ```
4. Check a few regions: `/rg info <id>`, `/rg flags <id>`, `/fawg test build <player>`.
5. Remove the old plugins: `/fawg cleanup confirm` (moves the jars to `plugins/FastAsyncWorldGuard/legacy-worldguard`), then **restart**.

## What is imported
| From | Data |
|---|---|
| WorldGuard | cuboid + poly2d regions, global regions, priority, parent, owners / members (UUIDs, names, groups), all flags and `-group` values |
| ExtraFlags / ExtraFlagsPlus | All flags. Names are identical; effect / sound lists are converted |
| WorldGuardRegionProtect | `regionProtect` → `region-lock server`, `playerRegionProtect` → `player`, `regionProtectAllow` → `break-allowed`, `regionProtectOnlyBreakAllow` → `break-only` |

Flags FAWG doesn't recognise are stored untouched and kept on save. If the plugin that owns them registers them through the API later, they start working.

## Differences to know
- FAWG resolves **specific before general** (see [Flag resolution](FLAG-RESOLUTION.md)). For exact WorldGuard behaviour set `flags.worldguard-strict-deny: true`.
- `invincible`, `godmode` etc. behave as in WorldGuard / ExtraFlags.
- The "anti ghost block" lock puts survival players into adventure mode where they can't build. Disable it with `anti-ghost.prediction-lock: false` or per region with `anti-ghost-blocks false`.
- WorldGuard with **MySQL** storage: run `/rg migratedb sql yaml` in WorldGuard before importing.
