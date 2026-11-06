# ━━━ ✦ Configuration ✦ ━━━

| Section | Highlights |
|---|---|
| `style` | Prefix, divider (`dash`, `length`, `star`, `outer-color`, `inner-color`, `star-color`), Bedrock small caps |
| `gui` | `mode: auto\|dialog\|chest`, columns, button width, page size |
| `commands` | `bare-command-opens-menu` |
| `bypass` | `enabled-by-default` |
| `flags` | `worldguard-strict-deny`, `disabled` flag list |
| `protection` | Deny messages `actionbar\|chat`, cooldown, sound, `block-plugin-spawns` |
| `anti-ghost` | `prediction-lock`, `lock-by-default`, `resync`, `max-resync-delay-ticks` |
| `selection` / `claims` | Wand item, prefer WorldEdit, claim limits, default claim flags |
| `visualizer` | Colours, particle size, interval, range, particle budget, durations |
| `worldedit` | `require-build`, `fawe-mask-manager`, guarded / radius commands |
| `spy`, `logging` | Admin alerts, action log in `logs/` |
| `performance` | `large-region-chunks`, `redstone-flag`, `max-forced-chunks-per-world` |
| `storage`, `backups` | Autosave interval, daily backups, how many to keep |
| `presets` | Flag bundles for `/rg preset` and the HUD — add your own |

## Styling messages
`messages.yml` uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html) plus:
- `<sc>text</sc>`: small caps (ᴛᴇxᴛ)
- `<prefix>`, `<divider>`, `<divider-short>`, `<star>`
- `<name>` placeholders, and `%name%` inside click / hover arguments

Flag values such as `greeting` accept MiniMessage and legacy `&` colour codes.
