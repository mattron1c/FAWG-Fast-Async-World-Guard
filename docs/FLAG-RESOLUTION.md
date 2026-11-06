# ━━━ ✦ How flags resolve ✦ ━━━

1. **Regions at the point** are found through the chunk index and sorted by priority (highest first).
2. **Priority.** Only the highest priority regions that set a value count. Lower ones are shadowed.
3. **Parents.** A region without the flag inherits it from its parent chain. If a parent and its child
   both contain the point, the child wins.
4. **Groups.** `-g members|owners|nonmembers|nonowners|all|none` limits who a value applies to. Owners of a parent own its children.
5. **Same priority:** for allow/deny flags, `deny` wins.
6. **Specific beats general.** Every allow/deny flag has a chain such as `interact-doors → use → interact → build`.
   The first flag in the chain that is set decides. Set `flags.worldguard-strict-deny: true` to use WorldGuard's
   rule instead (any `deny` anywhere in the chain wins).
7. **Membership.** If nothing in a build-type chain is set, members / owners are allowed and everyone else is denied.
   Regions with `passthrough allow` are ignored for this.
8. **Global region** (`__global__`) and finally the flag default apply when no region decides.

Debug anything with `/fawg test <flag> [player]`. It prints every flag in the chain, the membership result and bypass state.
