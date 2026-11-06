<div align="center">

<img src="assets/banner.svg" alt="FastAsyncWorldGuard" width="100%">

**Lock-free, chunk-indexed region protection for Paper — built for FAWE, Geyser and big servers.**

[![Build](https://img.shields.io/github/actions/workflow/status/mattron1c/FAWG-Fast-Async-World-Guard/build.yml?branch=main&style=for-the-badge&label=build&color=ff5c5c)](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/mattron1c/FAWG-Fast-Async-World-Guard?style=for-the-badge&color=8b0000)](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/mattron1c/FAWG-Fast-Async-World-Guard/total?style=for-the-badge&color=ff5c5c)](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/releases)
<br>
![Paper](https://img.shields.io/badge/Paper-1.21.8%20→%2026.3-ff5c5c?style=flat-square)
![Java](https://img.shields.io/badge/Java-21%2B-8b0000?style=flat-square)
![Flags](https://img.shields.io/badge/flags-1400%2B-ff5c5c?style=flat-square)
![FAWE](https://img.shields.io/badge/FAWE-native-8b0000?style=flat-square)
![Bedrock](https://img.shields.io/badge/Geyser-supported-ff5c5c?style=flat-square)
[![License](https://img.shields.io/badge/license-FAWG--SAL%201.0-555?style=flat-square)](LICENSE)

**[⬇ Download](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/releases/latest)** ·
**[📖 Docs](docs/README.md)** ·
**[⚑ Flag list](docs/FLAGS.md)** ·
**[⇩ Migrate from WorldGuard](docs/MIGRATION.md)** ·
**[💬 Discussions](https://github.com/mattron1c/FAWG-Fast-Async-World-Guard/discussions)**

</div>

FastAsyncWorldGuard (**FAWG**) is a from-scratch region protection plugin that keeps WorldGuard's
commands, flag names and region format, but is rebuilt for speed and flexibility:

- ⚡ **Faster lookups.** A chunk-bucketed spatial index with lock-free reads, allocation-free flag
  resolution, and sparse flag storage. Run `/fawg benchmark` to measure it against WorldGuard on your own regions.
- 🧱 **No ghost blocks.** Denied placing/breaking never flashes client-side, even on high ping or Bedrock.
- ⚑ **1,400+ flags.** All WorldGuard flags, all ExtraFlagsPlus flags, RegionProtect locks, plus ~900
  fine-grained flags generated from the server's own mob / block / item / effect lists.
- 🪟 **Dialog HUD.** Manage everything from Minecraft's native dialog screens. Bedrock players automatically get a chest GUI.
- 🔴 **Red outlines.** Selections and regions are highlighted with red particles that only draw near
  you, so huge regions cost no more than small ones.
- 🪓 **FAWE-native.** Async-safe protection of WorldEdit / FAWE edits, a FAWE mask manager, guarded WE
  commands — or run completely **standalone** with the built-in wand.
- ⇩ **One-command migration.** `/fawg import worldguard` brings over every region, flag, member and
  parent, then `/fawg cleanup confirm` removes the old jars.

> FAWG is an independent clean-room implementation. It contains no WorldGuard code and is not affiliated with EngineHub or IntellectualSites.

---

## ✦ Contents
- [Install](#-install) · [Migrating from WorldGuard](#-migrating-from-worldguard) · [The HUD](#-the-hud)
- [Commands](#-commands) · [Permissions](#-permissions) · [Flags](#-flags) · [Selections](#-selections--visualizer)
- [Anti ghost blocks](#-anti-ghost-blocks) · [WorldEdit / FAWE](#-worldedit--fawe) · [Performance](#-performance)
- [Configuration](#-configuration) · [Developer API](#-developer-api) · [Building](#-building) · [License](#-license)

## ✦ Quick start
```text
1. Put FastAsyncWorldGuard.jar in plugins/ and restart
2. /rg wand                      ← left / right click two corners
3. /rg define spawn              ← create the region
4. /rg flag spawn pvp deny       ← or just type /rg and use the HUD
```

## ✦ Install
1. Paper **1.21.8 – 26.3** (Java 21+; Paper 26.x needs Java 25).
2. Drop `FastAsyncWorldGuard-x.y.z.jar` into `plugins/` and restart.
3. Optional: WorldEdit **or** FastAsyncWorldEdit, PlaceholderAPI, Geyser + Floodgate.

## ✦ Migrating from WorldGuard
```text
/fawg import worldguard            ← imports every world (a backup is made first)
/fawg import worldguard world_nether --replace
/fawg cleanup confirm              ← moves WorldGuard + addon jars to plugins/FastAsyncWorldGuard/legacy-worldguard
```
- Reads `plugins/WorldGuard/worlds/*/regions.yml` directly, so it works whether WorldGuard is installed or not.
- Cuboid + polygon regions, priorities, parents, owners / members / groups, every flag and `-group` setting.
- **WorldGuard ExtraFlags(Plus)** flags carry over with the same names.
- **WorldGuardRegionProtect** protected-region lists become `region-lock` levels.
- Flags FAWG doesn't know are **kept** in the file and survive saves.
- Using MySQL in WorldGuard? Run `/rg migratedb sql yaml` in WorldGuard first.

## ✦ The HUD
`/rg` (or `/rg menu`, `/fawg menu`) opens the HUD:

| Screen | What you can do |
|---|---|
| 📍 Region here / 📜 My regions / 🌍 All regions | Browse, search and open regions |
| ⚑ Flags | 26 categories, search, “set flags only”, typed editors (allow/deny/unset pickers, text, lists, locations “use my location”, enums) and region groups |
| 👥 Members / 👑 Owners | Add / remove players, UUIDs or `g:groups`, clear all |
| ⚙ Settings | Priority, parent, rename, display name |
| ✧ Presets | One-click flag bundles (spawn, safezone, arena, shop, farm, lobby, protected, frozen-nature, no-mobs) |
| 🔒 Lock | RegionProtect-style hard locks |
| ✚ Create · 🪄 Selection | Create from your selection, switch shapes, set points, expand vertically |
| 🛠 Admin | Reload, save, backup, benchmark, stats, import WorldGuard, remove old jars, edit the global region |

Every label, colour and emoji lives in `messages.yml` → `gui.*`.

## ✦ Commands
All WorldGuard `/rg` sub-commands work with the same names and aliases, and every argument tab-completes (region ids, 1,000+ flags, flag values, players, groups, shapes, directions).

| Command | Description |
|---|---|
| `/rg define\|d <id> [owners…]` | Create a region from your selection |
| `/rg redefine\|update\|move <id>` | Change a region's area |
| `/rg claim <id>` | Claim with per-player limits (count, volume, overlap) |
| `/rg info\|i [id] [-s] [-u]` | Region details with clickable buttons |
| `/rg flag\|f <id> <flag> [-g group] [-e] [value]` | Set / clear a flag (omit value to unset) |
| `/rg flags <id> [page]` | List set flags |
| `/rg addowner\|ao`, `removeowner\|ro`, `addmember\|am`, `removemember\|rm` | Manage owners / members (`-a` removes all) |
| `/rg setpriority\|pri`, `/rg setparent\|par` | Priority / inheritance |
| `/rg remove\|del <id> [-f]` | Remove (with `-f` children too) |
| `/rg select\|sel <id>` | Load into your selection and highlight in red |
| `/rg teleport\|tp <id> [-s] [-c]` | Teleport (spawn flag / centre) |
| `/rg list [page] [-p player] [-w world] [-n filter]` | List regions |
| `/rg toggle-bypass\|bypass [on\|off]` · `/rg save` · `/rg load` | Bypass / storage |
| `/rg migrateuuid` · `/rg migrateheights` · `/rg migratedb` | WorldGuard migration helpers |
| **Extras** | |
| `/rg here` · `/rg near [radius]` · `/rg search <text>` · `/rg count` | Find regions |
| `/rg wand` · `/rg pos1\|pos2` · `/rg seltype <cuboid\|poly\|cylinder\|sphere>` | Built-in selections |
| `/rg expand\|contract\|shift <n\|vert> [dir]` · `/rg selinfo` · `/rg clearsel` | Edit selections |
| `/rg exclude <id> add\|remove\|list\|clear` | Carve holes out of regions |
| `/rg visualize\|vis [id\|nearby\|off]` · `/rg hide` | Red outlines |
| `/rg rename` · `/rg copyflags <a> <b> [-r]` · `/rg clearflags` · `/rg preset <id> <preset>` | Bulk editing |
| `/rg lock <id> [server\|player\|break-allowed\|break-only]` · `/rg unlock` | Hard locks |
| `/rg setteleport` · `/rg setspawn` | Location flags at your position |
| `/fawg import\|cleanup\|backup\|benchmark\|test\|flags\|stats\|reload\|dumpflags` | Admin tools |
| `/fawg god\|ungod\|heal\|slay\|stoplag\|stopfire\|allowfire` | WorldGuard-style utilities |

## ✦ Permissions
| Permission | Default | Meaning |
|---|---|---|
| `fawg.*` | op | Everything |
| `fawg.player` | — | Recommended kit for survival players (claim + manage own regions) |
| `fawg.region.<sub>` | op | Use a sub-command on **any** region |
| `fawg.region.<sub>.own` / `.member` | — | Only on regions you own / are a member of |
| `fawg.bypass` (`.<world>`) | op | Ignore protection while `/rg bypass` is on |
| `fawg.lock.bypass` | false | Edit / build in locked regions |
| `fawg.worldedit.bypass` | op | WorldEdit inside protected regions |
| `fawg.claim.limit.<n>` · `fawg.claim.unlimited` | — | Claim limits |
| `fawg.spy` · `fawg.notify` | op | Denied-action / WorldEdit alerts · enter / leave alerts |
| `fawg.admin` | op | `/fawg` admin tools |
| `group.<name>` | — | Membership through `g:<name>` (works with LuckPerms) |

## ✦ Flags
There are **1,400+ flags**; see [`docs/FLAGS.md`](docs/FLAGS.md) for the full list. Families include:

| Family | Example | Count* |
|---|---|---|
| WorldGuard core | `build`, `pvp`, `entry`, `greeting`, `heal-amount` … | 95 |
| ExtraFlagsPlus | `fly`, `walk-speed`, `give-effects`, `keep-inventory`, `disable-completely`, `player-count-limit` … | 51 |
| RegionProtect | `region-lock`, `worldedit-commands`, `fluid-flow-in`, `cobble-generation`, `admin-spy` … | 14 |
| Per mob | `spawn-zombie`, `damage-entity-cow`, `interact-entity-villager`, `target-creeper`, `damage-from-skeleton`, `grief-enderman`, `ride-horse` | ~650 |
| Per damage cause | `damage-cause-fall`, `entity-damage-cause-fire` | ~70 |
| Per spawn reason / transform | `spawn-reason-spawner`, `transform-drowned` | ~70 |
| Per potion effect | `effect-blindness`, `effect-levitation` | ~40 |
| Block categories | `interact-doors`, `place-tnt`, `break-ores`, `grow-wheat`, `spread-sculk`, `fade-coral` | ~250 |
| Item categories | `use-item-ender-pearl`, `use-item-trident`, `use-item-spawn-eggs` | ~60 |
| Inventories / teleports / ignitions / explosions / projectiles | `open-anvil`, `teleport-cause-ender-pearl`, `ignite-lava`, `explosion-end-crystal`, `launch-snowball` | ~100 |

<sub>*Counts depend on the Minecraft version: families are generated from the running server, so new mobs and blocks get flags automatically.</sub>

**Resolution rules.** The most specific flag that is set wins: `interact-doors allow` beats `interact deny`.
Set `flags.worldguard-strict-deny: true` for WorldGuard's “any deny wins” behaviour. Priorities, parent
inheritance, region groups (`-g members|owners|nonmembers|nonowners|all|none`) and membership work the same as in WorldGuard.

## ✦ Selections & visualizer
- `/rg wand` gives the FAWG wand (works without WorldEdit): left click = point 1, right click = point 2 or the next polygon point, sneak + right click air = change shape.
- Shapes: **cuboid, polygon, cylinder, sphere**, plus **exclusions** (`/rg exclude`) to cut holes.
- If WorldEdit / FAWE is installed, `/rg define` also accepts the WorldEdit selection (`selection.prefer-worldedit`), and `/rg select` sends the region back to WorldEdit / CUI.
- Regions you inspect or select are outlined in **red** dust particles; `/rg vis nearby` outlines everything around you. Bedrock players see them too.

## ✦ Anti ghost blocks
Normally a client predicts a block place / break before the server answers. With 200 ms ping or on Geyser, a denied block can appear and then vanish. FAWG fixes this two ways:
1. **Prediction lock.** Survival players standing where they can't build are switched to adventure mode, so the client never predicts the change. Doors, chests, buttons and combat still work. The original game mode is stored on the player, so a crash can't strand anyone. Turn it off per region with `/rg flag <id> anti-ghost-blocks false`.
2. **Ping-aware resync.** After every denied action the real block, its neighbours and the inventory are re-sent immediately, and again after the player's round trip time.

## ✦ WorldEdit / FAWE
- Protects regions against WorldEdit **and** FAWE edit sessions (async-safe, cached per chunk). Players need `worldedit allow` and build rights in a region to edit it.
- With FAWE: registers a **native mask manager**, so `//set` and friends are limited to regions you own (like FAWE's WorldGuard hook).
- RegionProtect-style **command guard**: `//set`, `//replace`, `//paste`, radius commands and more are blocked when the selection touches a locked or `worldedit-commands deny` region, with admin spy alerts. Also covers the super pickaxe (`superpickaxe` flag) and brushes (`worldedit-brushes`).

## ✦ Performance
| | WorldGuard 7 | FAWG |
|---|---|---|
| Spatial index | priority R-tree, cached per query | chunk buckets + pre-sorted arrays + pre-computed “fully covered” columns |
| Read path | locks / allocations per query | lock-free immutable snapshots, zero allocation for empty / fully covered chunks |
| Flag storage | `HashMap` per region | sparse sorted `int[]` per region (~150 bytes for 10 flags) |
| Threading | main thread | any thread (FAWE workers, async chat) |
| Saves | sync / async | async + atomic (temp file + move) + rolling zip backups |

`/fawg benchmark [n]` times region lookup + build checks on your own data. If WorldGuard is installed it times both side by side. The CI imports 3,000 WorldGuard regions and runs this comparison on every commit.

## ✦ Configuration
- `config.yml`: style (prefix, divider colours, small caps), HUD, anti-ghost, claims, visualizer, WorldEdit guard lists, presets, performance knobs.
- `messages.yml`: every message and HUD label (MiniMessage, `<sc>small caps</sc>`, `<divider>`, `<prefix>`).
- `regions/<world>.yml`: region data (human readable, WorldGuard-like layout).
- `backups/`: zipped snapshots (daily + before imports / restores). `logs/`: action audit log.

## ✦ Developer API
```java
StateFlag MY_FLAG = FAWGApi.registerFlag(new StateFlag("my-flag", FlagCategory.MISC, true));

if (!FAWGApi.test(player, location, MY_FLAG)) { /* denied */ }
ApplicableSet set = FAWGApi.query(location);          // thread safe
boolean build = FAWGApi.canBuild(player, location);
```
Events: `RegionEnterEvent`, `RegionLeaveEvent` (cancellable), `RegionChangeEvent` (create / remove / flag / members…, cancellable).
PlaceholderAPI: `%fawg_region%`, `%fawg_region_name%`, `%fawg_regions%`, `%fawg_is_owner%`, `%fawg_can_build%`, `%fawg_flag_<flag>%`, `%fawg_owned_count%`…

## ✦ Building
```bash
./gradlew build            # → build/libs/FastAsyncWorldGuard-<version>.jar
```
CI builds against Paper 1.21.8, compiles against 1.21.11 and 26.x, and boots real servers (1.21.8 + WorldGuard import, 1.21.11 + FAWE, 26.1.2, 26.3) on every push.

## ✦ License
**FAWG Source-Available License 1.0** — see [`LICENSE`](LICENSE).
✔ use on any server, including commercial ones · ✔ redistribute unmodified copies, even for money · ✔ build plugins against the API
✖ modify the code · ✖ distribute modified versions or forks.
Pull requests to this repository are welcome (see [`CONTRIBUTING.md`](CONTRIBUTING.md)).

<div align="center"><sub>━━━━━━ ✦ ━━━━━━</sub></div>
