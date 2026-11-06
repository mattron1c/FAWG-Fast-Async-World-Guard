# ━━━ ✦ Getting started ✦ ━━━

## 1. Install
- Paper **1.21.8 – 26.3** (Purpur works too). Java 21+, Java 25 for 26.x.
- Put `FastAsyncWorldGuard-<version>.jar` into `plugins/` and restart (don't use `/reload`).
- Optional: **FastAsyncWorldEdit** or **WorldEdit**, **PlaceholderAPI**, **Geyser + Floodgate**.

Coming from WorldGuard? Read [Migration](MIGRATION.md) first.

## 2. Your first region
```text
/rg wand                       get the FAWG wand (blaze rod)
left click a block             corner 1
right click a block            corner 2
/rg expand vert                bedrock to sky (optional)
/rg define spawn               create the region
/rg flag spawn pvp deny
/rg flag spawn greeting <red><b>Welcome to spawn!
/rg preset spawn spawn         or apply a whole bundle at once
```
WorldEdit users can use `//wand` instead; `/rg define` picks up the WorldEdit selection.

## 3. The HUD
Type `/rg` (or `/rg menu`). From the dialog screens you can browse regions, set any flag with pickers,
manage members and owners, change priority / parent / name, apply presets, lock regions, create regions
from your selection and use admin tools. Bedrock players get the same menus as a chest GUI.

## 4. Shapes
`/rg seltype cuboid|poly|cylinder|sphere` (or sneak + right click air with the wand).
- **poly**: left click the first point, right click every next point.
- **cylinder / sphere**: point 1 = centre, point 2 = radius.
- **Holes**: select an area inside a region and run `/rg exclude <id> add`.

## 5. Letting players claim land
Give players the `fawg.player` permission kit. Players can then:
```text
/rg wand · select · /rg claim mybase · /rg addmember mybase Steve · /rg flag mybase pvp deny
```
Limits are in `config.yml → claims` (count, volume, overlap); use `fawg.claim.limit.<n>` for per-rank limits.

## 6. Seeing regions
- `/rg here` lists regions at your position, `/rg near` shows nearby ones.
- `/rg vis nearby` toggles red outlines of every region around you.
- `/rg info <id>` prints details with clickable buttons and highlights the region.
