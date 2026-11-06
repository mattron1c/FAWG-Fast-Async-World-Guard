# ━━━ ✦ Permissions ✦ ━━━

## Model
Every `/rg` sub command `<sub>` checks:
- `fawg.region.<sub>`: any region
- `fawg.region.<sub>.own`: regions the player owns
- `fawg.region.<sub>.member`: regions the player is a member of

Sub command permission names: `define`, `redefine`, `claim`, `select`, `info`, `list`, `addowner`, `removeowner`,
`addmember`, `removemember`, `flag`, `setpriority`, `setparent`, `remove`, `teleport`, `bypass`, `load`, `save`,
`migrate`, `wand`, `visualize`, `rename`, `lock`, `menu`.

## Other nodes
| Node | Default | |
|---|---|---|
| `fawg.*` | op | Everything |
| `fawg.admin` | op | `/fawg` tools |
| `fawg.bypass`, `fawg.bypass.<world>` | op | Ignore protection while bypass is on |
| `fawg.lock.bypass` | false | Edit locked regions |
| `fawg.worldedit.bypass` | op | WorldEdit anywhere |
| `fawg.bypass.commands` | — | Ignore `blocked-cmds` / `allowed-cmds` |
| `fawg.fly.bypass` | op | Ignore `fly deny` |
| `fawg.claim.limit.<n>` · `fawg.claim.unlimited` | — | Claim limits |
| `fawg.spy` · `fawg.notify` | op | Alerts |
| `fawg.flag.restrict` + `fawg.flag.<name>` | — | Give `fawg.flag.restrict` to only allow listed flags |
| `fawe.fawg.member` | — | FAWE edits also in regions you're a member of |
| `group.<name>` / `fawg.group.<name>` | — | Membership through `g:<name>` |

## LuckPerms examples
```text
/lp group default permission set fawg.player true
/lp group default permission set fawg.claim.limit.3 true
/lp group vip permission set fawg.claim.limit.10 true
/lp group builder permission set fawg.worldedit.bypass true
/lp group mod permission set fawg.region.* true
/lp group mod permission set fawg.spy true
```
