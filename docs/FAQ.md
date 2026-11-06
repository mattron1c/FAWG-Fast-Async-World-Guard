# ━━━ ✦ FAQ & troubleshooting ✦ ━━━

**Players get switched to adventure mode.**
That's the anti ghost-block lock: survival players standing where they can't build can't predict blocks client-side.
They switch back as soon as they leave. Disable it with `anti-ghost.prediction-lock: false` or `/rg flag <id> anti-ghost-blocks false`.

**`/rg` opens WorldGuard instead of FAWG.**
Both are installed and WorldGuard registered first. Use `/fawg import worldguard`, then `/fawg cleanup confirm` and restart.

**A flag doesn't do what I expect.**
Run `/fawg test <flag> <player>` while standing at the spot. It shows the chain, which region decides, membership and bypass.

**Ops ignore all protection.**
Ops have `fawg.bypass`. Toggle it with `/rg bypass`, or set `bypass.enabled-by-default: false`.

**Bedrock players see boxes instead of letters.**
Keep `style.bedrock-small-caps: false` (default) so they get normal letters.

**Dialogs don't open.**
Dialogs need a Java client on 1.21.6+. Bedrock / older clients through ViaVersion get the chest GUI if `gui.mode` is `auto`.
Force it for everyone with `gui.mode: chest`.

**Where is my data?**
`plugins/FastAsyncWorldGuard/regions/<world>.yml`. Backups are in `backups/` and can be restored with `/fawg backup restore <file>`.

**Is it a WorldGuard fork?**
No. It's a clean-room implementation that is compatible with WorldGuard's commands, flag names and data files.
