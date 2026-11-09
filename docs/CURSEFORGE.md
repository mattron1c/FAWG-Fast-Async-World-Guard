![FastAsyncWorldGuard](https://raw.githubusercontent.com/mattron1c/FAWG-Fast-Async-World-Guard/main/assets/banner.png)

![Paper](https://raster.shields.io/badge/Paper-1.21.8%20to%2026.3-ff5c5c.png?style=flat-square) ![Java](https://raster.shields.io/badge/Java-21%2B-8b0000.png?style=flat-square) ![Flags](https://raster.shields.io/badge/flags-1400%2B-ff5c5c.png?style=flat-square) ![FAWE](https://raster.shields.io/badge/FAWE-native-8b0000.png?style=flat-square) ![Geyser](https://raster.shields.io/badge/Geyser-supported-ff5c5c.png?style=flat-square)

**Lock-free, chunk-indexed region protection for Paper, built for FAWE, Geyser and big servers.**

FastAsyncWorldGuard (FAWG) is a from-scratch region protection plugin. It keeps WorldGuard's commands, flag names and region format, but is rebuilt for speed and flexibility.

**[Download](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/releases/latest)** | **[Documentation](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/tree/main/docs)** | **[Full flag list](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/blob/main/docs/FLAGS.md)** | **[Source code](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard)**

* * *

## ✦ Features

- **⚡ Fast:** a chunk-bucketed spatial index with lock-free reads and zero-allocation flag checks. That's about 100 ns per region lookup plus build check with 3,000 regions loaded. Measure it on your own server with `/fawg benchmark`.
- **✔ No ghost blocks:** denied block placing and breaking never flashes client-side, even on high ping or for Bedrock players.
- **⚑ 1,400+ flags:**
    - every WorldGuard flag
    - every WorldGuard-ExtraFlags-Plus flag
    - WorldGuardRegionProtect locks
    - flags per mob, damage cause, spawn reason, potion effect, block type, item, inventory, teleport cause, explosion and projectile
- **✦ Dialog HUD:** manage regions, flags, members, owners, presets and locks from Minecraft's native dialog screens. Bedrock players automatically get a chest GUI.
- **✦ Red outlines:** selections and regions are highlighted with red particles that only draw near you, so huge regions cost no more than small ones.
- **✦ Shapes:** cuboid, polygon, cylinder and sphere, plus exclusions to cut holes out of regions.
- **✦ FAWE / WorldEdit integration:** async-safe edit protection, a native FAWE mask manager and guarded WorldEdit commands. It also runs fully standalone with the built-in wand.
- **✦ One-command migration:** `/fawg import worldguard` brings over every region, flag, member and parent. Then `/fawg cleanup confirm` removes the old jars.
- **✦ Familiar commands:** the same `/rg` sub-commands as WorldGuard, and every argument tab-completes.
- **✦ Extras:** presets, claim limits, automatic backups, an action log, PlaceholderAPI placeholders and a developer API.

* * *

## ✦ Quick start

```
1. Put FastAsyncWorldGuard.jar in plugins/ and restart
2. /rg wand                  (left / right click two corners)
3. /rg define spawn          (create the region)
4. /rg flag spawn pvp deny   (or just type /rg to open the HUD)
```

* * *

## ✦ Migrating from WorldGuard

```
/fawg import worldguard      (a backup is made first)
/fawg cleanup confirm        (moves the old WorldGuard jars out, then restart)
```

- Imports cuboid and polygon regions, priorities, parents, owners, members, groups and every flag.
- **ExtraFlags / ExtraFlagsPlus** flags keep the same names.
- **WorldGuardRegionProtect** protected regions become `region-lock` levels.
- Flags FAWG doesn't recognise are kept, so nothing is lost.

* * *

## ✦ Flag examples

- **WorldGuard:** `build`, `pvp`, `entry`, `greeting`, `heal-amount`
- **ExtraFlagsPlus:** `fly`, `walk-speed`, `give-effects`, `keep-inventory`, `player-count-limit`
- **Per mob:** `spawn-zombie`, `damage-entity-cow`, `interact-entity-villager`, `target-creeper`, `grief-enderman`
- **Per damage cause:** `damage-cause-fall`, `damage-cause-lava`
- **Per potion effect:** `effect-blindness`, `effect-levitation`
- **Blocks:** `interact-doors`, `place-tnt`, `break-ores`, `grow-wheat`, `spread-sculk`
- **Items:** `use-item-ender-pearl`, `use-item-trident`, `use-item-spawn-eggs`

Specific flags beat general ones, so `interact-doors allow` overrides `interact deny`.

* * *

## ✦ Requirements

- **Server:** Paper (or Purpur) **1.21.8 to 26.3**
- **Java:** 21 or newer (Java 25 for 26.x)
- **Optional:** FastAsyncWorldEdit or WorldEdit, PlaceholderAPI, Geyser + Floodgate

* * *

## ✦ Support

- **Bugs and suggestions:** [GitHub Issues](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/issues)
- **Commands:** [command list](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/blob/main/docs/COMMANDS.md)
- **Permissions:** [permission list](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/blob/main/docs/PERMISSIONS.md)
- **FAQ:** [common questions](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/blob/main/docs/FAQ.md)

* * *

*FastAsyncWorldGuard is an independent clean-room implementation and is not affiliated with EngineHub or IntellectualSites. Licensed under the FAWG Source-Available License 1.0: free to use on any server (including commercial) and to redistribute unmodified. Modified versions may not be distributed.*
