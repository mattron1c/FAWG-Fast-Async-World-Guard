# ━━━ ✦ Developer API ✦ ━━━

Add FAWG as a `compileOnly` dependency (jar from Releases) and `depend: [FastAsyncWorldGuard]` / `softdepend` in your plugin.yml.

```java
import dev.mattron1c.fawg.api.FAWGApi;
import dev.mattron1c.fawg.core.flag.*;

public final class MyPlugin extends JavaPlugin {
    public static StateFlag NO_SPELLS;

    @Override public void onLoad() {
        NO_SPELLS = FAWGApi.registerFlag(new StateFlag("spells", FlagCategory.MISC, true)
                .desc("Casting spells").action("cast spells"));
    }

    void cast(Player p) {
        if (!FAWGApi.test(p, p.getLocation(), NO_SPELLS)) return;   // bypass, locks, groups, membership handled
    }
}
```

| Method | |
|---|---|
| `FAWGApi.registerFlag(flag)` | Register a custom flag (any time; stored values are picked up) |
| `FAWGApi.test(player, location, stateFlag)` | Full protection check |
| `FAWGApi.canBuild / canBreak(player, location)` | Shortcuts |
| `FAWGApi.natural(location, stateFlag)` | Check without a player |
| `FAWGApi.value(player, location, flag)` | Any flag value (respects groups) |
| `FAWGApi.query(location)` | `ApplicableSet` of regions (sorted by priority) |
| `FAWGApi.region(world, id)` · `FAWGApi.world(world)` | Direct access |

All query methods are **thread safe** and can be called from async threads.

## Flag types
`StateFlag`, `BooleanFlag`, `StringFlag`, `IntFlag`, `DoubleFlag`, `EnumFlag<E>`, `LocationFlag`, `StringSetFlag`.
State flags can declare `.parents(...)` (fallback chain) and `.gated()` (membership fallback like `build`).

## Events
| Event | Cancellable | |
|---|---|---|
| `RegionEnterEvent` | ✔ | Player entering a region (walk, vehicle, teleport) |
| `RegionLeaveEvent` | ✔ | Player leaving |
| `RegionChangeEvent` | ✔ | Create, remove, redefine, flag, members, owners, priority, parent, rename |

## PlaceholderAPI
`%fawg_region%` `%fawg_region_name%` `%fawg_regions%` `%fawg_priority%` `%fawg_owners%` `%fawg_members%`
`%fawg_is_owner%` `%fawg_is_member%` `%fawg_can_build%` `%fawg_owned_count%` `%fawg_total%` `%fawg_flag_<flag>%`
