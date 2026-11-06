# ━━━ ✦ Commands ✦ ━━━

`/rg` = `/region` = `/regions`. Options: `-w <world>` (other world), `-g <group>` (flag group), `-e` (empty value), `-h <page>`.

## Regions
| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/rg define <id> [owners…]` | `d`, `create` | `fawg.region.define` | Create a region from your selection |
| `/rg claim <id>` | | `fawg.region.claim` | Create + own a region, with claim limits |
| `/rg redefine <id>` | `update`, `move` | `fawg.region.redefine[.own]` | Replace the area with your selection |
| `/rg remove <id> [-f] [-u]` | `delete`, `del`, `rem` | `fawg.region.remove[.own]` | Remove (`-f` also removes children) |
| `/rg rename <id> <new>` | | `fawg.region.rename[.own]` | Rename keeping everything |
| `/rg setpriority <id> <n>` | `priority`, `pri` | `fawg.region.setpriority[.own]` | Priority |
| `/rg setparent <id> [parent]` | `parent`, `par` | `fawg.region.setparent[.own]` | Inheritance (empty = none) |
| `/rg lock <id> [level]` · `/rg unlock <id>` | `protect` | `fawg.region.lock` | Hard lock: `server`, `player`, `break-allowed`, `break-only` |
| `/rg exclude <id> add\|remove\|list\|clear` | `carve` | `fawg.region.redefine[.own]` | Cut holes using your selection |

## Information
| Command | Description |
|---|---|
| `/rg info [id] [-s] [-u]` | Details (`-s` also selects, `-u` shows UUIDs) |
| `/rg flags <id> [page]` | Flags set on the region (click to edit) |
| `/rg list [page] [-p player] [-w world] [-n filter]` | Region list |
| `/rg search <text>` · `/rg count` | Search all worlds · count per world |
| `/rg here` · `/rg near [radius]` | Regions at / around you |

## Members & flags
| Command | Aliases | Description |
|---|---|---|
| `/rg addowner <id> <who…>` | `ao` | `who` = player, UUID or `g:group` |
| `/rg removeowner <id> <who…> [-a]` | `ro` | `-a` removes all |
| `/rg addmember <id> <who…>` | `am`, `addmem` | |
| `/rg removemember <id> <who…> [-a]` | `rm`, `remmember`, `removemem` | |
| `/rg flag <id> <flag> [-g group] [value]` | `f` | No value = unset · `here` for location flags |
| `/rg copyflags <from> <to> [-r]` | `cf` | `-r` replaces all |
| `/rg clearflags <id>` | `resetflags` | Remove every flag |
| `/rg preset <id> <preset>` | `template` | Apply a flag bundle from config |
| `/rg setteleport [id]` · `/rg setspawn [id]` | | Location flags at your position |

## Selection & visualizer
| Command | Description |
|---|---|
| `/rg wand` | FAWG wand |
| `/rg pos1` · `/rg pos2` | Points at your feet |
| `/rg seltype <cuboid\|poly\|cylinder\|sphere>` | Selection shape |
| `/rg expand <n\|vert> [dir]` · `/rg contract <n> [dir]` · `/rg shift <n> [dir]` | dir: `me`, `up`, `down`, `north`, `south`, `east`, `west` |
| `/rg selinfo` · `/rg clearsel` | Show / clear |
| `/rg select [id]` | Load a region into your selection (+ WorldEdit) and highlight it |
| `/rg visualize [id\|nearby\|off]` · `/rg hide` | Red outlines |

## Misc
| Command | Description |
|---|---|
| `/rg teleport <id> [-s] [-c]` | `-s` spawn flag, `-c` centre |
| `/rg bypass [on\|off]` | Toggle bypass (needs `fawg.bypass`) |
| `/rg save` · `/rg load` | Save / reload region files |
| `/rg migrateuuid` · `/rg migrateheights` · `/rg migratedb` | WorldGuard-compatible helpers |
| `/rg menu [id]` | Open the HUD |

## Admin — `/fawg`
| Command | Description |
|---|---|
| `/fawg menu` | HUD |
| `/fawg reload` · `/fawg version` · `/fawg stats` | |
| `/fawg import worldguard [world] [--replace]` | Import WorldGuard data |
| `/fawg cleanup [confirm]` | Move old WorldGuard jars out of plugins/ |
| `/fawg backup create\|list\|restore <file>` | Zip backups |
| `/fawg benchmark [n]` | Speed test (side by side with WorldGuard if installed) |
| `/fawg test <flag> [player]` | Explain how a flag resolves for a player |
| `/fawg flags [category\|search] [page]` · `/fawg dumpflags` | Browse flags · write FLAGS.md |
| `/fawg god\|ungod\|heal\|slay [player]` · `/fawg stoplag [-c]` · `/fawg stopfire\|allowfire` | Utilities |
