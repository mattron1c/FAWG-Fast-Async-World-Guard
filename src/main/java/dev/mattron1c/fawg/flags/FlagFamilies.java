package dev.mattron1c.fawg.flags;

import dev.mattron1c.fawg.core.flag.FlagCategory;
import dev.mattron1c.fawg.core.flag.StateFlag;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static dev.mattron1c.fawg.flags.Flags.*;

/**
 * Generated flag families. Each family is enforced by a single listener through an O(1) EnumMap lookup,
 * so 1000+ flags cost no more per event than one. Families are generated from the running server's own
 * enums / registries: new mobs, blocks and effects of future Minecraft versions get flags automatically.
 */
public final class FlagFamilies {
    private FlagFamilies() {}

    private static final StateFlag[] NONE = new StateFlag[0];

    public static final EnumMap<EntityDamageEvent.DamageCause, StateFlag> PLAYER_DAMAGE_CAUSE = new EnumMap<>(EntityDamageEvent.DamageCause.class);
    public static final EnumMap<EntityDamageEvent.DamageCause, StateFlag> ENTITY_DAMAGE_CAUSE = new EnumMap<>(EntityDamageEvent.DamageCause.class);
    public static final EnumMap<CreatureSpawnEvent.SpawnReason, StateFlag> SPAWN_REASON = new EnumMap<>(CreatureSpawnEvent.SpawnReason.class);
    public static final EnumMap<EntityType, StateFlag> SPAWN = new EnumMap<>(EntityType.class);
    public static final EnumMap<EntityType, StateFlag> DAMAGE_ENTITY = new EnumMap<>(EntityType.class);
    public static final EnumMap<EntityType, StateFlag> INTERACT_ENTITY = new EnumMap<>(EntityType.class);
    public static final EnumMap<EntityType, StateFlag> RIDE_ENTITY = new EnumMap<>(EntityType.class);
    public static final EnumMap<EntityType, StateFlag> GRIEF = new EnumMap<>(EntityType.class);
    public static final EnumMap<EntityType, StateFlag> TARGET = new EnumMap<>(EntityType.class);
    public static final EnumMap<EntityType, StateFlag> DAMAGE_FROM = new EnumMap<>(EntityType.class);
    public static final EnumMap<EntityType, StateFlag> LAUNCH = new EnumMap<>(EntityType.class);
    public static final EnumMap<EntityType, StateFlag> EXPLOSION = new EnumMap<>(EntityType.class);
    public static final Map<String, StateFlag> EFFECT = new HashMap<>();
    public static final EnumMap<InventoryType, StateFlag> OPEN = new EnumMap<>(InventoryType.class);
    public static final EnumMap<PlayerTeleportEvent.TeleportCause, StateFlag> TELEPORT_CAUSE = new EnumMap<>(PlayerTeleportEvent.TeleportCause.class);
    public static final EnumMap<BlockIgniteEvent.IgniteCause, StateFlag> IGNITE = new EnumMap<>(BlockIgniteEvent.IgniteCause.class);
    public static final EnumMap<EntityTransformEvent.TransformReason, StateFlag> TRANSFORM = new EnumMap<>(EntityTransformEvent.TransformReason.class);
    public static final EnumMap<Material, StateFlag[]> INTERACT_BLOCK = new EnumMap<>(Material.class);
    public static final EnumMap<Material, StateFlag[]> USE_ITEM = new EnumMap<>(Material.class);
    public static final EnumMap<Material, StateFlag[]> PLACE = new EnumMap<>(Material.class);
    public static final EnumMap<Material, StateFlag[]> BREAK = new EnumMap<>(Material.class);
    public static final EnumMap<Material, StateFlag> GROW = new EnumMap<>(Material.class);
    public static final EnumMap<Material, StateFlag> FORM = new EnumMap<>(Material.class);
    public static final EnumMap<Material, StateFlag> FADE = new EnumMap<>(Material.class);
    public static final EnumMap<Material, StateFlag> SPREAD = new EnumMap<>(Material.class);
    public static StateFlag EXPLOSION_BED, EXPLOSION_ANCHOR;

    private static int generated;

    public static int generatedCount() { return generated; }

    static String key(String enumName) { return enumName.toLowerCase(Locale.ROOT).replace('_', '-'); }

    private static StateFlag reg(String name, FlagCategory c, boolean gated, String action, String desc, StateFlag... parents) {
        if (REGISTRY.get(name) != null) {
            return REGISTRY.get(name) instanceof StateFlag s ? s : null;
        }
        StateFlag f = new StateFlag(name, c, true).parents(parents).<StateFlag>desc(desc).<StateFlag>action(action);
        if (gated) f.gated();
        REGISTRY.register(f);
        generated++;
        return f;
    }

    private static String pretty(String enumName) { return enumName.toLowerCase(Locale.ROOT).replace('_', ' '); }

    // =================================================================================================
    static void init() {
        damageCauses();
        spawning();
        entities();
        effects();
        inventories();
        teleportAndIgnite();
        blockCategories();
        itemCategories();
        natureCategories();
        explosions();
    }

    private static void damageCauses() {
        for (EntityDamageEvent.DamageCause c : EntityDamageEvent.DamageCause.values()) {
            PLAYER_DAMAGE_CAUSE.put(c, reg("damage-cause-" + key(c.name()), FlagCategory.PLAYER_DAMAGE, false, "", "Players taking " + pretty(c.name()) + " damage."));
            ENTITY_DAMAGE_CAUSE.put(c, reg("entity-damage-cause-" + key(c.name()), FlagCategory.ENTITY_DAMAGE, false, "", "Non-player mobs taking " + pretty(c.name()) + " damage."));
        }
    }

    private static void spawning() {
        for (CreatureSpawnEvent.SpawnReason r : CreatureSpawnEvent.SpawnReason.values()) {
            SPAWN_REASON.put(r, reg("spawn-reason-" + key(r.name()), FlagCategory.SPAWNING, false, "", "Mobs spawning because of: " + pretty(r.name()) + "."));
        }
    }

    private static boolean living(EntityType t) {
        Class<? extends Entity> c = t.getEntityClass();
        return c != null && LivingEntity.class.isAssignableFrom(c) && !Player.class.isAssignableFrom(c) && t != EntityType.ARMOR_STAND;
    }

    private static boolean is(EntityType t, Class<?> k) {
        Class<? extends Entity> c = t.getEntityClass();
        return c != null && k.isAssignableFrom(c);
    }

    private static void entities() {
        for (EntityType t : EntityType.values()) {
            if (t == EntityType.UNKNOWN || t == EntityType.PLAYER || t.getEntityClass() == null) continue;
            String k = key(t.name());
            String p = pretty(t.name());
            boolean living = living(t);
            boolean hostile = is(t, Enemy.class);
            if (living) {
                SPAWN.put(t, reg("spawn-" + k, FlagCategory.SPAWNING, false, "", p + " spawning.", MOB_SPAWNING));
                StateFlag griefParent = switch (t.name()) {
                    case "ENDERMAN" -> ENDERMAN_GRIEF;
                    case "RAVAGER" -> RAVAGER_GRIEF;
                    case "WITHER" -> WITHER_DAMAGE;
                    case "ENDER_DRAGON" -> ENDERDRAGON_BLOCK_DAMAGE;
                    case "SNOW_GOLEM" -> SNOWMAN_TRAILS;
                    case "SILVERFISH" -> SILVERFISH_INFEST;
                    case "SHEEP" -> SHEEP_EAT_GRASS;
                    case "RABBIT" -> RABBIT_EAT_CROPS;
                    case "VILLAGER" -> VILLAGER_FARMING;
                    default -> null;
                };
                GRIEF.put(t, griefParent == null
                        ? reg("grief-" + k, FlagCategory.MOB_GRIEF, false, "", p + " changing blocks.")
                        : reg("grief-" + k, FlagCategory.MOB_GRIEF, false, "", p + " changing blocks.", griefParent));
                if (is(t, Mob.class)) {
                    TARGET.put(t, reg("target-" + k, FlagCategory.MOB_GRIEF, false, "", p + " targeting players.", MOB_TARGET_PLAYERS));
                    DAMAGE_FROM.put(t, reg("damage-from-" + k, FlagCategory.PLAYER_DAMAGE, false, "", p + " hurting players.", MOB_DAMAGE));
                }
                if (hostile) {
                    DAMAGE_ENTITY.put(t, reg("damage-entity-" + k, FlagCategory.ENTITY_DAMAGE, false, "hurt that", "Players hurting " + p + "."));
                    INTERACT_ENTITY.put(t, reg("interact-entity-" + k, FlagCategory.ENTITIES, false, "interact with that", "Players right clicking " + p + "."));
                } else {
                    DAMAGE_ENTITY.put(t, reg("damage-entity-" + k, FlagCategory.ENTITY_DAMAGE, true, "hurt that", "Players hurting " + p + ".", DAMAGE_ANIMALS));
                    StateFlag ip = t == EntityType.VILLAGER || t == EntityType.WANDERING_TRADER ? VILLAGER_TRADE : INTERACT;
                    INTERACT_ENTITY.put(t, reg("interact-entity-" + k, FlagCategory.ENTITIES, true, "interact with that", "Players right clicking " + p + ".", ip == VILLAGER_TRADE ? new StateFlag[]{VILLAGER_TRADE, INTERACT} : new StateFlag[]{INTERACT}));
                }
            } else if (t == EntityType.ARMOR_STAND) {
                DAMAGE_ENTITY.put(t, reg("damage-entity-" + k, FlagCategory.ENTITY_DAMAGE, true, "break that", "Players breaking armor stands.", BUILD));
                INTERACT_ENTITY.put(t, reg("interact-entity-" + k, FlagCategory.ENTITIES, true, "use that", "Players using armor stands.", ARMOR_STAND_MANIPULATE));
            } else if (is(t, Hanging.class)) {
                DAMAGE_ENTITY.put(t, reg("damage-entity-" + k, FlagCategory.ENTITY_DAMAGE, true, "break that", "Players breaking " + p + ".", HANGING_BREAK));
                INTERACT_ENTITY.put(t, reg("interact-entity-" + k, FlagCategory.ENTITIES, true, "use that", "Players right clicking " + p + ".",
                        is(t, ItemFrame.class) ? ITEM_FRAME_INTERACT : INTERACT));
            } else if (is(t, Vehicle.class)) {
                DAMAGE_ENTITY.put(t, reg("damage-entity-" + k, FlagCategory.ENTITY_DAMAGE, true, "break that", "Players breaking " + p + ".", VEHICLE_DESTROY));
                INTERACT_ENTITY.put(t, reg("interact-entity-" + k, FlagCategory.ENTITIES, true, "use that", "Players right clicking " + p + ".", RIDE));
            } else if (is(t, EnderCrystal.class)) {
                DAMAGE_ENTITY.put(t, reg("damage-entity-" + k, FlagCategory.ENTITY_DAMAGE, true, "break that", "Players breaking end crystals.", BUILD));
            }
            if (is(t, Vehicle.class) && (living || !is(t, Projectile.class))) {
                RIDE_ENTITY.put(t, reg("ride-" + k, FlagCategory.VEHICLES, true, "ride that", "Riding / mounting " + p + ".", VEHICLE_ENTER));
            }
            if (is(t, Projectile.class)) {
                LAUNCH.put(t, reg("launch-" + k, FlagCategory.ITEMS, false, "launch that", p + " being launched (by players or mobs)."));
            }
        }
    }

    private static void effects() {
        for (PotionEffectType t : Registry.EFFECT) {
            String k = t.getKey().getKey();
            EFFECT.put(k, reg("effect-" + k.replace('_', '-'), FlagCategory.EFFECTS, false, "", "Players receiving the " + k.replace('_', ' ') + " effect."));
        }
    }

    private static void inventories() {
        for (InventoryType t : InventoryType.values()) {
            String n = t.name();
            if (n.equals("PLAYER") || n.equals("CREATIVE") || n.equals("CRAFTING")) continue;
            StateFlag parent = switch (n) {
                case "CHEST", "DISPENSER", "DROPPER", "FURNACE", "BLAST_FURNACE", "SMOKER", "HOPPER", "SHULKER_BOX", "BARREL", "BREWING",
                     "CHISELED_BOOKSHELF", "DECORATED_POT", "CRAFTER", "JUKEBOX", "LECTERN", "COMPOSTER", "SHELF" -> CHEST_ACCESS;
                case "ENDER_CHEST" -> ENDERCHEST_ACCESS;
                case "MERCHANT" -> VILLAGER_TRADE;
                default -> USE;
            };
            OPEN.put(t, reg("open-" + key(n), FlagCategory.INVENTORY, parent.membershipGated() || parent.chainGated(), "open that", "Opening " + pretty(n) + " inventories.", parent));
        }
    }

    private static void teleportAndIgnite() {
        for (PlayerTeleportEvent.TeleportCause c : PlayerTeleportEvent.TeleportCause.values()) {
            TELEPORT_CAUSE.put(c, reg("teleport-cause-" + key(c.name()), FlagCategory.TELEPORT, false, "teleport here", "Teleports caused by " + pretty(c.name()) + " ending inside.", TELEPORT_IN));
        }
        for (BlockIgniteEvent.IgniteCause c : BlockIgniteEvent.IgniteCause.values()) {
            IGNITE.put(c, reg("ignite-" + key(c.name()), FlagCategory.WORLD, false, "set fires", "Fire ignited by " + pretty(c.name()) + "."));
        }
        for (EntityTransformEvent.TransformReason r : EntityTransformEvent.TransformReason.values()) {
            TRANSFORM.put(r, reg("transform-" + key(r.name()), FlagCategory.MOB_GRIEF, false, "", "Mobs transforming because of " + pretty(r.name()) + "."));
        }
    }

    // ------------------------------------------------------------------------------------- material DSL
    private record Cat(String id, String action, Predicate<String> match, StateFlag[] parents) {}

    private static Cat cat(String id, String action, Predicate<String> m, StateFlag... parents) { return new Cat(id, action, m, parents); }

    private static Predicate<String> eq(String... names) { java.util.Set<String> s = java.util.Set.of(names); return s::contains; }
    private static Predicate<String> ends(String... suf) { return n -> { for (String s : suf) if (n.endsWith(s)) return true; return false; }; }
    private static Predicate<String> has(String... parts) { return n -> { for (String s : parts) if (n.contains(s)) return true; return false; }; }

    private static void bind(EnumMap<Material, StateFlag[]> map, Material m, StateFlag f) {
        StateFlag[] cur = map.get(m);
        if (cur == null) { map.put(m, new StateFlag[]{f}); return; }
        StateFlag[] n = java.util.Arrays.copyOf(cur, cur.length + 1);
        n[cur.length] = f;
        map.put(m, n);
    }

    private static List<Material> materials(boolean blocks, boolean items) {
        List<Material> l = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy()) continue;
            if ((blocks && m.isBlock()) || (items && m.isItem())) l.add(m);
        }
        return l;
    }

    private static void blockCategories() {
        List<Cat> interact = List.of(
                cat("doors", "open doors", ends("_DOOR"), USE),
                cat("trapdoors", "open trapdoors", ends("_TRAPDOOR"), USE),
                cat("fence-gates", "open gates", ends("_FENCE_GATE"), USE),
                cat("buttons", "press buttons", ends("_BUTTON"), USE),
                cat("levers", "flip levers", eq("LEVER"), USE),
                cat("pressure-plates", "use pressure plates", ends("_PRESSURE_PLATE"), USE),
                cat("tripwires", "trigger tripwires", eq("TRIPWIRE", "TRIPWIRE_HOOK"), USE),
                cat("chests", "open chests", n -> (n.equals("CHEST") || n.equals("TRAPPED_CHEST") || n.endsWith("COPPER_CHEST")), CHEST_ACCESS),
                cat("ender-chests", "open ender chests", eq("ENDER_CHEST"), ENDERCHEST_ACCESS),
                cat("barrels", "open barrels", eq("BARREL"), CHEST_ACCESS),
                cat("shulker-boxes", "open shulker boxes", ends("SHULKER_BOX"), CHEST_ACCESS),
                cat("hoppers", "open hoppers", eq("HOPPER"), CHEST_ACCESS),
                cat("droppers", "open droppers", eq("DROPPER"), CHEST_ACCESS),
                cat("dispensers", "open dispensers", eq("DISPENSER"), CHEST_ACCESS),
                cat("crafters", "use crafters", eq("CRAFTER"), CHEST_ACCESS),
                cat("furnaces", "use furnaces", eq("FURNACE"), CHEST_ACCESS),
                cat("blast-furnaces", "use blast furnaces", eq("BLAST_FURNACE"), CHEST_ACCESS),
                cat("smokers", "use smokers", eq("SMOKER"), CHEST_ACCESS),
                cat("brewing-stands", "use brewing stands", eq("BREWING_STAND"), CHEST_ACCESS),
                cat("chiseled-bookshelves", "use bookshelves", eq("CHISELED_BOOKSHELF"), CHEST_ACCESS),
                cat("decorated-pots", "use decorated pots", eq("DECORATED_POT"), CHEST_ACCESS),
                cat("shelves", "use shelves", n -> n.endsWith("_SHELF") && !n.contains("BOOK"), CHEST_ACCESS),
                cat("lecterns", "use lecterns", eq("LECTERN"), USE),
                cat("beacons", "use beacons", eq("BEACON"), USE),
                cat("enchanting-tables", "enchant", eq("ENCHANTING_TABLE"), USE),
                cat("anvils", "use anvils", eq("ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL"), USE_ANVIL),
                cat("grindstones", "use grindstones", eq("GRINDSTONE"), USE),
                cat("stonecutters", "use stonecutters", eq("STONECUTTER"), USE),
                cat("looms", "use looms", eq("LOOM"), USE),
                cat("cartography-tables", "use cartography tables", eq("CARTOGRAPHY_TABLE"), USE),
                cat("smithing-tables", "use smithing tables", eq("SMITHING_TABLE"), USE),
                cat("crafting-tables", "use crafting tables", eq("CRAFTING_TABLE"), USE),
                cat("beds", "use beds", ends("_BED"), SLEEP),
                cat("respawn-anchors", "use respawn anchors", eq("RESPAWN_ANCHOR"), RESPAWN_ANCHORS),
                cat("jukeboxes", "use jukeboxes", eq("JUKEBOX"), USE),
                cat("note-blocks", "tune note blocks", eq("NOTE_BLOCK"), USE),
                cat("composters", "use composters", eq("COMPOSTER"), USE),
                cat("cauldrons", "use cauldrons", ends("CAULDRON"), USE),
                cat("bells", "ring bells", eq("BELL"), USE),
                cat("cakes", "eat cake", n -> n.equals("CAKE") || n.endsWith("CANDLE_CAKE"), USE),
                cat("candles", "light candles", n -> n.endsWith("CANDLE") && !n.endsWith("CANDLE_CAKE"), USE),
                cat("flower-pots", "use flower pots", n -> n.equals("FLOWER_POT") || n.startsWith("POTTED_"), FLOWER_POT),
                cat("signs", "edit signs", ends("_SIGN"), SIGN_EDIT),
                cat("vaults", "open vaults", eq("VAULT"), USE),
                cat("spawners", "change spawners", eq("SPAWNER", "TRIAL_SPAWNER"), USE),
                cat("comparators", "change comparators", eq("COMPARATOR"), USE),
                cat("repeaters", "change repeaters", eq("REPEATER"), USE),
                cat("daylight-detectors", "flip daylight detectors", eq("DAYLIGHT_DETECTOR"), USE),
                cat("redstone-wire", "change redstone", eq("REDSTONE_WIRE"), USE),
                cat("campfires", "use campfires", ends("CAMPFIRE"), USE),
                cat("dragon-egg", "touch the dragon egg", eq("DRAGON_EGG"), USE),
                cat("beehives", "harvest hives", eq("BEEHIVE", "BEE_NEST"), USE),
                cat("suspicious-blocks", "brush blocks", has("SUSPICIOUS_"), USE),
                cat("sweet-berry-bushes", "pick berries", eq("SWEET_BERRY_BUSH"), USE),
                cat("cave-vines", "pick glow berries", eq("CAVE_VINES", "CAVE_VINES_PLANT"), USE),
                cat("copper-bulbs", "toggle copper bulbs", ends("COPPER_BULB"), USE),
                cat("end-portal-frames", "fill portal frames", eq("END_PORTAL_FRAME"), USE),
                cat("lodestones", "use lodestones", eq("LODESTONE"), USE),
                cat("pumpkins", "carve pumpkins", eq("PUMPKIN"), USE),
                cat("command-blocks", "use command blocks", n -> n.endsWith("COMMAND_BLOCK") || n.equals("STRUCTURE_BLOCK") || n.equals("JIGSAW"), USE),
                cat("big-dripleaf", "tilt dripleaf", eq("BIG_DRIPLEAF", "BIG_DRIPLEAF_STEM"), USE_DRIPLEAF),
                cat("farmland", "trample farmland", eq("FARMLAND"), BLOCK_TRAMPLING),
                cat("turtle-eggs", "trample eggs", eq("TURTLE_EGG", "SNIFFER_EGG"), BLOCK_TRAMPLING),
                cat("tnt", "light TNT", eq("TNT"), LIGHTER),
                cat("logs", "strip logs", ends("_LOG", "_WOOD", "_STEM", "_HYPHAE"), BUILD),
                cat("copper-blocks", "scrape copper", n -> n.contains("COPPER") && !n.endsWith("ORE") && !n.endsWith("BULB") && !n.endsWith("CHEST"), BUILD),
                cat("dirt-blocks", "till dirt", eq("DIRT", "GRASS_BLOCK", "DIRT_PATH", "COARSE_DIRT", "ROOTED_DIRT", "PODZOL", "MYCELIUM"), BUILD),
                cat("crops", "harvest crops", eq("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA", "TORCHFLOWER_CROP", "PITCHER_CROP"), BUILD)
        );
        for (Cat c : interact) {
            StateFlag f = reg("interact-" + c.id, FlagCategory.INTERACTION, chainGated(c.parents), c.action, "Right clicking " + c.id.replace('-', ' ') + ".", c.parents);
            for (Material m : materials(true, false)) if (c.match.test(m.name())) bind(INTERACT_BLOCK, m, f);
        }

        List<Cat> build = List.of(
                cat("logs", "", ends("_LOG", "_WOOD", "_STEM", "_HYPHAE")),
                cat("planks", "", ends("_PLANKS")),
                cat("leaves", "", ends("_LEAVES")),
                cat("saplings", "", n -> n.endsWith("_SAPLING") || n.endsWith("PROPAGULE")),
                cat("flowers", "", n -> List.of("DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "WITHER_ROSE", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY", "TORCHFLOWER", "PITCHER_PLANT", "PINK_PETALS", "SPORE_BLOSSOM", "OPEN_EYEBLOSSOM", "CLOSED_EYEBLOSSOM", "WILDFLOWERS").contains(n) || n.endsWith("_TULIP")),
                cat("crops", "", eq("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "MELON_STEM", "PUMPKIN_STEM", "ATTACHED_MELON_STEM", "ATTACHED_PUMPKIN_STEM", "TORCHFLOWER_CROP", "PITCHER_CROP", "COCOA", "SWEET_BERRY_BUSH")),
                cat("melons-pumpkins", "", eq("MELON", "PUMPKIN", "CARVED_PUMPKIN", "JACK_O_LANTERN")),
                cat("cactus-cane-bamboo", "", eq("CACTUS", "SUGAR_CANE", "BAMBOO", "BAMBOO_SAPLING", "KELP", "KELP_PLANT", "CACTUS_FLOWER")),
                cat("ores", "", n -> n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS")),
                cat("valuables", "", eq("DIAMOND_BLOCK", "EMERALD_BLOCK", "GOLD_BLOCK", "IRON_BLOCK", "NETHERITE_BLOCK", "LAPIS_BLOCK", "RAW_GOLD_BLOCK", "RAW_IRON_BLOCK", "RAW_COPPER_BLOCK", "AMETHYST_BLOCK")),
                cat("glass", "", n -> n.equals("GLASS") || n.endsWith("_GLASS") || n.endsWith("GLASS_PANE")),
                cat("wool", "", ends("_WOOL")),
                cat("carpets", "", ends("_CARPET")),
                cat("concrete", "", has("CONCRETE")),
                cat("terracotta", "", has("TERRACOTTA")),
                cat("stone", "", n -> !n.endsWith("_ORE") && (n.contains("STONE") && !n.contains("REDSTONE") && !n.contains("GLOWSTONE") && !n.contains("LODESTONE") && !n.contains("SANDSTONE") && !n.contains("END_STONE") || n.contains("DEEPSLATE") || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE") || n.contains("TUFF") || n.equals("CALCITE") || n.contains("BLACKSTONE") || n.contains("BASALT"))),
                cat("dirt", "", eq("DIRT", "GRASS_BLOCK", "PODZOL", "MYCELIUM", "COARSE_DIRT", "ROOTED_DIRT", "MUD", "FARMLAND", "DIRT_PATH", "MOSS_BLOCK", "PALE_MOSS_BLOCK", "CLAY", "MUDDY_MANGROVE_ROOTS")),
                cat("sand-gravel", "", n -> n.equals("SAND") || n.equals("RED_SAND") || n.equals("GRAVEL") || n.startsWith("SUSPICIOUS_")),
                cat("ice-snow", "", eq("ICE", "PACKED_ICE", "BLUE_ICE", "SNOW", "SNOW_BLOCK", "POWDER_SNOW", "FROSTED_ICE")),
                cat("beds", "", ends("_BED")),
                cat("signs", "", ends("_SIGN")),
                cat("banners", "", ends("_BANNER")),
                cat("doors", "", ends("_DOOR")),
                cat("trapdoors", "", ends("_TRAPDOOR")),
                cat("fences", "", ends("_FENCE", "_FENCE_GATE")),
                cat("walls", "", n -> n.endsWith("_WALL") && !n.contains("SIGN") && !n.contains("BANNER") && !n.contains("TORCH") && !n.contains("HEAD") && !n.contains("SKULL") && !n.contains("FAN")),
                cat("stairs", "", ends("_STAIRS")),
                cat("slabs", "", ends("_SLAB")),
                cat("rails", "", n -> n.equals("RAIL") || n.endsWith("_RAIL")),
                cat("redstone", "", n -> n.contains("REDSTONE") || n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE") || List.of("REPEATER", "COMPARATOR", "OBSERVER", "LEVER", "DAYLIGHT_DETECTOR", "TARGET", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR", "TRIPWIRE_HOOK", "TRIPWIRE", "LIGHTNING_ROD", "CRAFTER").contains(n)),
                cat("pistons", "", eq("PISTON", "STICKY_PISTON", "PISTON_HEAD", "MOVING_PISTON")),
                cat("tnt", "", eq("TNT")),
                cat("fire", "", eq("FIRE", "SOUL_FIRE")),
                cat("lighting", "", n -> n.endsWith("TORCH") || n.endsWith("LANTERN") || n.endsWith("CANDLE") || List.of("GLOWSTONE", "SEA_LANTERN", "SHROOMLIGHT", "END_ROD", "REDSTONE_LAMP", "OCHRE_FROGLIGHT", "VERDANT_FROGLIGHT", "PEARLESCENT_FROGLIGHT").contains(n)),
                cat("containers", "", n -> List.of("CHEST", "TRAPPED_CHEST", "BARREL", "HOPPER", "DISPENSER", "DROPPER", "FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING_STAND", "CRAFTER", "CHISELED_BOOKSHELF", "DECORATED_POT", "LECTERN", "JUKEBOX").contains(n) || n.endsWith("COPPER_CHEST")),
                cat("shulker-boxes", "", ends("SHULKER_BOX")),
                cat("spawners", "", eq("SPAWNER", "TRIAL_SPAWNER", "VAULT")),
                cat("beacons", "", eq("BEACON", "CONDUIT")),
                cat("obsidian", "", eq("OBSIDIAN", "CRYING_OBSIDIAN", "RESPAWN_ANCHOR")),
                cat("unbreakables", "", eq("BEDROCK", "BARRIER", "END_PORTAL_FRAME", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK", "JIGSAW", "LIGHT", "STRUCTURE_VOID", "REINFORCED_DEEPSLATE")),
                cat("slime-honey", "", eq("SLIME_BLOCK", "HONEY_BLOCK")),
                cat("scaffolding", "", eq("SCAFFOLDING")),
                cat("heads", "", ends("_HEAD", "_SKULL")),
                cat("coral", "", has("CORAL")),
                cat("amethyst", "", has("AMETHYST")),
                cat("sculk", "", has("SCULK")),
                cat("copper", "", n -> n.contains("COPPER") && !n.endsWith("ORE")),
                cat("climbables", "", n -> List.of("LADDER", "VINE", "TWISTING_VINES", "TWISTING_VINES_PLANT", "WEEPING_VINES", "WEEPING_VINES_PLANT", "CAVE_VINES", "CAVE_VINES_PLANT", "GLOW_LICHEN").contains(n)),
                cat("mushrooms", "", n -> n.contains("MUSHROOM") || n.endsWith("FUNGUS") || n.endsWith("_ROOTS")),
                cat("bookshelves", "", n -> n.equals("BOOKSHELF") || n.equals("CHISELED_BOOKSHELF")),
                cat("anvils", "", eq("ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL")),
                cat("nether-blocks", "", n -> n.startsWith("NETHER") || n.contains("NETHERRACK") || n.contains("SOUL_S") || n.contains("CRIMSON") || n.contains("WARPED") || n.equals("MAGMA_BLOCK")),
                cat("end-blocks", "", n -> n.contains("END_STONE") || n.contains("PURPUR") || n.startsWith("CHORUS")),
                cat("prismarine", "", has("PRISMARINE")),
                cat("quartz", "", has("QUARTZ")),
                cat("sandstone", "", has("SANDSTONE")),
                cat("bricks", "", has("BRICK")),
                cat("workstations", "", eq("CRAFTING_TABLE", "SMITHING_TABLE", "FLETCHING_TABLE", "CARTOGRAPHY_TABLE", "LOOM", "STONECUTTER", "GRINDSTONE", "ENCHANTING_TABLE", "COMPOSTER", "CAULDRON", "BELL")),
                cat("hay-and-farm", "", eq("HAY_BLOCK", "DRIED_KELP_BLOCK", "BEEHIVE", "BEE_NEST", "TARGET", "SPONGE", "WET_SPONGE"))
        );
        List<Material> blocks = materials(true, false);
        for (Cat c : build) {
            StateFlag place = reg("place-" + c.id, FlagCategory.BLOCKS, true, "place " + c.id.replace('-', ' '), "Placing " + c.id.replace('-', ' ') + ".", BLOCK_PLACE);
            StateFlag brk = reg("break-" + c.id, FlagCategory.BLOCKS, true, "break " + c.id.replace('-', ' '), "Breaking " + c.id.replace('-', ' ') + ".", BLOCK_BREAK);
            for (Material m : blocks) if (c.match.test(m.name())) { bind(PLACE, m, place); bind(BREAK, m, brk); }
        }
    }

    private static boolean chainGated(StateFlag[] parents) {
        for (StateFlag p : parents) if (p.chainGated()) return true;
        return false;
    }

    private static void itemCategories() {
        List<Cat> items = List.of(
                cat("ender-pearl", "throw ender pearls", eq("ENDER_PEARL"), ENDERPEARL),
                cat("ender-eye", "throw eyes of ender", eq("ENDER_EYE")),
                cat("chorus-fruit", "eat chorus fruit", eq("CHORUS_FRUIT"), CHORUS_TELEPORT),
                cat("trident", "use tridents", eq("TRIDENT")),
                cat("bow", "shoot bows", eq("BOW")),
                cat("crossbow", "shoot crossbows", eq("CROSSBOW")),
                cat("fishing-rod", "fish", eq("FISHING_ROD"), FISHING),
                cat("snowball", "throw snowballs", eq("SNOWBALL")),
                cat("egg", "throw eggs", n -> n.equals("EGG") || n.endsWith("_EGG") && !n.endsWith("SPAWN_EGG") && !n.contains("TURTLE") && !n.contains("SNIFFER") && !n.contains("DRAGON") && !n.contains("FROG")),
                cat("splash-potion", "throw potions", eq("SPLASH_POTION"), POTION_SPLASH),
                cat("lingering-potion", "throw potions", eq("LINGERING_POTION"), POTION_SPLASH),
                cat("potion", "drink potions", eq("POTION")),
                cat("experience-bottle", "throw xp bottles", eq("EXPERIENCE_BOTTLE")),
                cat("fire-charge", "use fire charges", eq("FIRE_CHARGE"), LIGHTER),
                cat("flint-and-steel", "light fires", eq("FLINT_AND_STEEL"), LIGHTER),
                cat("water-bucket", "empty water", eq("WATER_BUCKET"), BLOCK_PLACE),
                cat("lava-bucket", "empty lava", eq("LAVA_BUCKET"), BLOCK_PLACE),
                cat("powder-snow-bucket", "place powder snow", eq("POWDER_SNOW_BUCKET"), BLOCK_PLACE),
                cat("fish-buckets", "release mobs", n -> n.endsWith("_BUCKET") && !List.of("WATER_BUCKET", "LAVA_BUCKET", "POWDER_SNOW_BUCKET", "MILK_BUCKET").contains(n), BLOCK_PLACE),
                cat("empty-bucket", "fill buckets", eq("BUCKET"), BLOCK_BREAK),
                cat("milk-bucket", "drink milk", eq("MILK_BUCKET")),
                cat("bone-meal", "use bone meal", eq("BONE_MEAL"), BONE_MEAL),
                cat("shears", "use shears", eq("SHEARS")),
                cat("spawn-eggs", "use spawn eggs", ends("_SPAWN_EGG"), BUILD),
                cat("firework-rocket", "launch fireworks", eq("FIREWORK_ROCKET")),
                cat("goat-horn", "play goat horns", eq("GOAT_HORN")),
                cat("spyglass", "use spyglasses", eq("SPYGLASS")),
                cat("brush", "use brushes", eq("BRUSH"), BUILD),
                cat("wind-charge", "throw wind charges", eq("WIND_CHARGE")),
                cat("shield", "block with shields", eq("SHIELD")),
                cat("armor-stand", "place armor stands", eq("ARMOR_STAND"), BUILD),
                cat("boats", "place boats", n -> n.endsWith("_BOAT") || n.endsWith("_RAFT"), VEHICLE_PLACE),
                cat("minecarts", "place minecarts", n -> n.equals("MINECART") || n.endsWith("_MINECART"), VEHICLE_PLACE),
                cat("item-frames", "place item frames", eq("ITEM_FRAME", "GLOW_ITEM_FRAME"), HANGING_PLACE),
                cat("paintings", "place paintings", eq("PAINTING"), HANGING_PLACE),
                cat("leads", "use leads", eq("LEAD"), ENTITY_LEASH),
                cat("name-tags", "use name tags", eq("NAME_TAG"), ENTITY_NAME),
                cat("saddles", "use saddles", eq("SADDLE")),
                cat("hoes", "till soil", ends("_HOE"), BUILD),
                cat("axes", "strip blocks", ends("_AXE"), BUILD),
                cat("shovels", "make paths", ends("_SHOVEL"), BUILD),
                cat("honeycomb", "wax blocks", eq("HONEYCOMB"), BUILD),
                cat("books", "write books", eq("WRITABLE_BOOK", "WRITTEN_BOOK"), BOOK_EDIT),
                cat("maps", "use maps", eq("MAP", "FILLED_MAP")),
                cat("compass", "use compasses", eq("COMPASS", "RECOVERY_COMPASS")),
                cat("bundles", "use bundles", n -> n.equals("BUNDLE") || n.endsWith("_BUNDLE")),
                cat("food", "eat", n -> false, ITEM_CONSUME),
                cat("music-discs", "play music", n -> n.startsWith("MUSIC_DISC")),
                cat("end-crystal", "place end crystals", eq("END_CRYSTAL"), BUILD),
                cat("carrot-on-a-stick", "steer mounts", eq("CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK")),
                cat("glass-bottle", "fill bottles", eq("GLASS_BOTTLE")),
                cat("dyes", "use dyes", n -> n.endsWith("_DYE") || n.equals("INK_SAC") || n.equals("GLOW_INK_SAC"), ENTITY_DYE),
                cat("elytra", "equip elytra", eq("ELYTRA")),
                cat("armor", "equip armor", ends("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS")),
                cat("ominous-bottle", "drink ominous bottles", eq("OMINOUS_BOTTLE")),
                cat("debug-stick", "use the debug stick", eq("DEBUG_STICK"), BUILD),
                cat("spears", "use spears", ends("_SPEAR")),
                cat("mace", "use maces", eq("MACE")),
                cat("harness", "use harnesses", ends("_HARNESS")),
                cat("totem", "use totems", eq("TOTEM_OF_UNDYING"), TOTEM_USE)
        );
        List<Material> all = materials(false, true);
        for (Cat c : items) {
            StateFlag f = reg("use-item-" + c.id, FlagCategory.ITEMS, chainGated(c.parents), c.action, "Using " + c.id.replace('-', ' ') + ".", c.parents);
            for (Material m : all) {
                boolean match = c.id.equals("food") ? m.isEdible() : c.match.test(m.name());
                if (match) bind(USE_ITEM, m, f);
            }
        }
    }

    private static void natureCategories() {
        record N(String id, StateFlag parent, Predicate<String> m) {}
        List<N> grow = List.of(
                new N("wheat", CROP_GROWTH, eq("WHEAT")), new N("carrots", CROP_GROWTH, eq("CARROTS")), new N("potatoes", CROP_GROWTH, eq("POTATOES")),
                new N("beetroots", CROP_GROWTH, eq("BEETROOTS")), new N("nether-wart", CROP_GROWTH, eq("NETHER_WART")),
                new N("melons", CROP_GROWTH, has("MELON")), new N("pumpkins", CROP_GROWTH, has("PUMPKIN")),
                new N("sugar-cane", CROP_GROWTH, eq("SUGAR_CANE")), new N("cactus", CROP_GROWTH, eq("CACTUS", "CACTUS_FLOWER")),
                new N("bamboo", CROP_GROWTH, eq("BAMBOO", "BAMBOO_SAPLING")), new N("kelp", CROP_GROWTH, eq("KELP", "KELP_PLANT")),
                new N("sweet-berries", CROP_GROWTH, eq("SWEET_BERRY_BUSH")), new N("cocoa", CROP_GROWTH, eq("COCOA")),
                new N("chorus", CROP_GROWTH, eq("CHORUS_FLOWER", "CHORUS_PLANT")), new N("torchflowers", CROP_GROWTH, eq("TORCHFLOWER_CROP", "TORCHFLOWER")),
                new N("pitcher-plants", CROP_GROWTH, eq("PITCHER_CROP", "PITCHER_PLANT")), new N("sea-pickles", CROP_GROWTH, eq("SEA_PICKLE")),
                new N("vines", VINE_GROWTH, n -> n.contains("VINES") || n.equals("VINE")), new N("glow-lichen", VINE_GROWTH, eq("GLOW_LICHEN")),
                new N("amethyst", ROCK_GROWTH, n -> n.contains("AMETHYST_BUD") || n.equals("AMETHYST_CLUSTER")),
                new N("dripstone", ROCK_GROWTH, eq("POINTED_DRIPSTONE")), new N("turtle-eggs", CROP_GROWTH, eq("TURTLE_EGG")),
                new N("sniffer-eggs", CROP_GROWTH, eq("SNIFFER_EGG")), new N("dripleaf", CROP_GROWTH, eq("BIG_DRIPLEAF", "BIG_DRIPLEAF_STEM", "SMALL_DRIPLEAF")),
                new N("mangrove-propagules", CROP_GROWTH, eq("MANGROVE_PROPAGULE")), new N("dried-ghast", CROP_GROWTH, eq("DRIED_GHAST"))
        );
        List<N> form = List.of(
                new N("snow", SNOW_FALL, eq("SNOW")), new N("ice", ICE_FORM, eq("ICE")), new N("frosted-ice", FROSTED_ICE_FORM, eq("FROSTED_ICE")),
                new N("obsidian", COBBLE_GENERATION, eq("OBSIDIAN")), new N("cobblestone", COBBLE_GENERATION, eq("COBBLESTONE")),
                new N("stone", COBBLE_GENERATION, eq("STONE")), new N("basalt", COBBLE_GENERATION, eq("BASALT")),
                new N("concrete", null, n -> n.endsWith("_CONCRETE")), new N("copper-oxidation", COPPER_FADE, n -> n.contains("COPPER")),
                new N("powder-snow", SNOW_FALL, eq("POWDER_SNOW", "POWDER_SNOW_CAULDRON"))
        );
        List<N> fade = List.of(
                new N("snow", SNOW_MELT, eq("SNOW", "SNOW_BLOCK")), new N("ice", ICE_MELT, eq("ICE")), new N("frosted-ice", FROSTED_ICE_MELT, eq("FROSTED_ICE")),
                new N("coral", CORAL_FADE, has("CORAL")), new N("fire", null, eq("FIRE", "SOUL_FIRE")), new N("farmland", SOIL_DRY, eq("FARMLAND")),
                new N("copper", COPPER_FADE, n -> n.contains("COPPER")), new N("grass", GRASS_GROWTH, eq("GRASS_BLOCK", "MYCELIUM", "NYLIUM", "CRIMSON_NYLIUM", "WARPED_NYLIUM"))
        );
        List<N> spread = List.of(
                new N("fire", FIRE_SPREAD, eq("FIRE", "SOUL_FIRE")), new N("grass", GRASS_GROWTH, eq("GRASS_BLOCK")), new N("mycelium", MYCELIUM_SPREAD, eq("MYCELIUM")),
                new N("mushrooms", MUSHROOM_GROWTH, has("MUSHROOM")), new N("vines", VINE_GROWTH, n -> n.contains("VINE") || n.equals("GLOW_LICHEN")),
                new N("sculk", SCULK_GROWTH, has("SCULK")), new N("bamboo", CROP_GROWTH, eq("BAMBOO", "BAMBOO_SAPLING")),
                new N("kelp", CROP_GROWTH, eq("KELP", "KELP_PLANT")), new N("chorus", CROP_GROWTH, eq("CHORUS_FLOWER", "CHORUS_PLANT")),
                new N("nylium", GRASS_GROWTH, has("NYLIUM")), new N("moss", GRASS_GROWTH, eq("MOSS_BLOCK", "MOSS_CARPET", "PALE_MOSS_BLOCK", "PALE_MOSS_CARPET"))
        );
        List<Material> blocks = materials(true, false);
        Object[][] fams = {{"grow-", grow, GROW, FlagCategory.NATURE}, {"form-", form, FORM, FlagCategory.NATURE}, {"fade-", fade, FADE, FlagCategory.NATURE}, {"spread-", spread, SPREAD, FlagCategory.NATURE}};
        for (Object[] fam : fams) {
            @SuppressWarnings("unchecked") List<N> list = (List<N>) fam[1];
            @SuppressWarnings("unchecked") EnumMap<Material, StateFlag> map = (EnumMap<Material, StateFlag>) fam[2];
            for (N n : list) {
                String name = fam[0] + n.id;
                StateFlag f = n.parent == null ? reg(name, (FlagCategory) fam[3], false, "", Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('-', ' ') + ".")
                        : reg(name, (FlagCategory) fam[3], false, "", Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('-', ' ') + ".", n.parent);
                for (Material m : blocks) if (n.m.test(m.name()) && !map.containsKey(m)) map.put(m, f);
            }
        }
    }

    private static void explosions() {
        for (EntityType t : EntityType.values()) {
            String n = t.name();
            StateFlag parent = switch (n) {
                case "TNT", "TNT_MINECART" -> TNT;
                case "CREEPER" -> CREEPER_EXPLOSION;
                case "FIREBALL" -> GHAST_FIREBALL;
                case "WITHER", "WITHER_SKULL" -> WITHER_DAMAGE;
                case "BREEZE_WIND_CHARGE" -> BREEZE_CHARGE_EXPLOSION;
                case "END_CRYSTAL", "SMALL_FIREBALL", "DRAGON_FIREBALL", "WIND_CHARGE" -> OTHER_EXPLOSION;
                default -> null;
            };
            if (parent != null) EXPLOSION.put(t, reg("explosion-" + key(n), FlagCategory.EXPLOSIONS, false, "", pretty(n) + " explosions.", parent));
        }
        EXPLOSION_BED = reg("explosion-bed", FlagCategory.EXPLOSIONS, false, "", "Beds exploding in the nether / end.", BLOCK_EXPLOSIONS);
        EXPLOSION_ANCHOR = reg("explosion-respawn-anchor", FlagCategory.EXPLOSIONS, false, "", "Respawn anchors exploding.", BLOCK_EXPLOSIONS);
    }

    // ------------------------------------------------------------------------------------ lookups
    public static StateFlag[] interactBlock(Material m) { StateFlag[] f = INTERACT_BLOCK.get(m); return f == null ? NONE : f; }
    public static StateFlag[] useItem(Material m) { StateFlag[] f = USE_ITEM.get(m); return f == null ? NONE : f; }
    public static StateFlag[] place(Material m) { StateFlag[] f = PLACE.get(m); return f == null ? NONE : f; }
    public static StateFlag[] breaking(Material m) { StateFlag[] f = BREAK.get(m); return f == null ? NONE : f; }
}
