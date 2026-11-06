# ━━━ ✦ Contributing to FastAsyncWorldGuard ✦ ━━━

Thanks for wanting to help! FAWG is **source-available**: you can read, build and contribute to the code here,
but modified versions may not be distributed anywhere else (see [LICENSE](LICENSE)). Pull requests to this
repository are welcome and are licensed to the project under section 3 of the license.

## Getting started
```bash
git clone https://github.com/mattron1c/FAWG-Fast-Async-World-Guard
cd FAWG-Fast-Async-World-Guard
./gradlew build
```
Requirements: JDK 21+. The jar lands in `build/libs/`.

## Project layout
| Package | Purpose |
|---|---|
| `core.*` | Bukkit-free engine: shapes, regions, spatial index, flag types, resolver (unit tested) |
| `flags` | Built-in flags (`Flags`) and generated families (`FlagFamilies`) |
| `listener` | Event listeners — one cheap lookup per event |
| `protect` | `Protection` (central checks), `GhostBlockGuard` |
| `region` | Storage, manager, `RegionService` (all mutations) |
| `command`, `gui` | `/rg`, `/fawg`, dialog HUD, Bedrock chest GUI |
| `hook`, `migrate` | WorldEdit/FAWE, PlaceholderAPI, Geyser detection, WorldGuard importer |

## Guidelines
- **Hot paths must not allocate** (listeners, resolver, index). Use the cached `ApplicableSet` / arrays.
- **All player text goes in `messages.yml`.** Use `<sc>` for small caps and the red palette.
- **Mutations go through `RegionService`** so permissions, locks and events are applied once.
- New flags: add them to `Flags` or a family in `FlagFamilies`, give them a category and a description, and enforce them in a listener.
- Add or extend unit tests in `src/test` for engine changes.

## Commit / PR
- One topic per PR, descriptive title, link the issue.
- CI builds against Paper 1.21.8 and 26.x and boots real servers. Please keep it green.
