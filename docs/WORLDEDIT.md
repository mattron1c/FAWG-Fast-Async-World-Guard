# ━━━ ✦ WorldEdit & FAWE ✦ ━━━

FAWG works **without** WorldEdit (built-in wand and selections). When WorldEdit or FastAsyncWorldEdit is installed:

## Edit protection
Every edit session from a player is wrapped. Blocks are only changed where:
- the `worldedit` flag allows it,
- the region isn't locked (`region-lock`),
- and (with `worldedit.require-build: true`) the player could build there.

Results are cached per chunk column, so a 10-million-block `//set` costs a handful of region lookups. Bypass: `fawg.worldedit.bypass`.

## FAWE mask manager
With FAWE and its `region-restrictions` enabled, FAWG registers a native mask: players can edit inside the
region they stand in and own (members too with `fawe.fawg.member`). This works like FAWE's WorldGuard hook, without needing WorldGuard.

## Guarded commands
The commands in `worldedit.guarded-commands` are checked against the player's selection, and `worldedit.radius-commands`
against a radius around the player. If they touch a locked region or one with `worldedit-commands deny`, the command is cancelled
and staff with `fawg.spy` get an alert and sound. Super pickaxe (`superpickaxe` flag) and brushes (`worldedit-brushes` flag) are covered too.

## Selections
- `/rg define` uses the FAWG selection, or the WorldEdit selection if there is none (`selection.prefer-worldedit` to swap).
- `/rg select <id>` also sets your WorldEdit selection (WorldEdit CUI shows it).
