package dev.mattron1c.fawg.flags;

import dev.mattron1c.fawg.core.flag.BooleanFlag;
import dev.mattron1c.fawg.core.flag.DoubleFlag;
import dev.mattron1c.fawg.core.flag.EnumFlag;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.FlagCategory;
import dev.mattron1c.fawg.core.flag.FlagRegistry;
import dev.mattron1c.fawg.core.flag.IntFlag;
import dev.mattron1c.fawg.core.flag.InvalidFlagFormatException;
import dev.mattron1c.fawg.core.flag.LocationFlag;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.flag.StringFlag;
import dev.mattron1c.fawg.core.flag.StringSetFlag;
import dev.mattron1c.fawg.core.region.ProtectLevel;
import dev.mattron1c.fawg.core.region.RegionGroup;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.WeatherType;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import static dev.mattron1c.fawg.core.flag.FlagCategory.*;

/**
 * Every built-in hand written flag. Names of WorldGuard and WorldGuard-ExtraFlags(Plus) flags are kept
 * identical so imported regions keep working. Generated flag families live in {@link FlagFamilies}.
 */
public final class Flags {
    public static final FlagRegistry REGISTRY = new FlagRegistry();

    private Flags() {}

    // ------------------------------------------------------------------------------------------ helpers
    static StateFlag state(String n, FlagCategory c, boolean def, String action, String desc) {
        return REGISTRY.register(new StateFlag(n, c, def).<StateFlag>desc(desc).<StateFlag>action(action));
    }

    static StateFlag gated(String n, FlagCategory c, String action, String desc, StateFlag... parents) {
        return REGISTRY.register(new StateFlag(n, c, true).gated().parents(parents).<StateFlag>desc(desc).<StateFlag>action(action));
    }

    static StateFlag child(String n, FlagCategory c, boolean def, String action, String desc, StateFlag... parents) {
        return REGISTRY.register(new StateFlag(n, c, def).parents(parents).<StateFlag>desc(desc).<StateFlag>action(action));
    }

    static BooleanFlag bool(String n, FlagCategory c, String desc) { return REGISTRY.register(new BooleanFlag(n, c).<BooleanFlag>desc(desc)); }

    static StringFlag str(String n, FlagCategory c, String desc) { return REGISTRY.register(new StringFlag(n, c).<StringFlag>desc(desc)); }

    static IntFlag num(String n, FlagCategory c, int min, int max, String desc) { return REGISTRY.register(new IntFlag(n, c, min, max).<IntFlag>desc(desc)); }

    static DoubleFlag dbl(String n, FlagCategory c, double min, double max, String desc) { return REGISTRY.register(new DoubleFlag(n, c, min, max).<DoubleFlag>desc(desc)); }

    static LocationFlag loc(String n, FlagCategory c, String desc) { return REGISTRY.register(new LocationFlag(n, c).<LocationFlag>desc(desc)); }

    static StringSetFlag set(String n, FlagCategory c, java.util.function.Function<String, String> norm, Supplier<Collection<String>> hints, String desc) {
        return REGISTRY.register(new StringSetFlag(n, c, norm, hints).<StringSetFlag>desc(desc));
    }

    static StringSetFlag commands(String n, FlagCategory c, String desc) {
        return REGISTRY.register(new CommandListFlag(n, c).<StringSetFlag>desc(desc));
    }

    // ---------------------------------------------------------------------------------- normalizers
    public static String material(String s) {
        Material m = Material.matchMaterial(s.trim());
        return m == null || m.isLegacy() ? null : m.name();
    }

    public static Collection<String> materialHints() {
        List<String> l = new ArrayList<>();
        for (Material m : Material.values()) if (!m.isLegacy()) l.add(m.name().toLowerCase(Locale.ROOT));
        return l;
    }

    public static String entity(String s) {
        String k = s.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace('-', '_');
        if (k.equals("ALL_HOSTILE") || k.equals("ALL_NEUTRAL") || k.equals("ALL_PEACEFUL") || k.equals("ALL") || k.equals("HOSTILE") || k.equals("ANIMALS") || k.equals("PASSIVE") || k.equals("MONSTERS")) return k;
        try { return EntityType.valueOf(k).name(); } catch (IllegalArgumentException e) { return null; }
    }

    public static Collection<String> entityHints() {
        List<String> l = new ArrayList<>(List.of("all_hostile", "all_neutral", "all_peaceful", "all"));
        for (EntityType t : EntityType.values()) if (t != EntityType.UNKNOWN) l.add(t.name().toLowerCase(Locale.ROOT));
        return l;
    }

    public static String effect(String s) {
        String base = s.trim().toLowerCase(Locale.ROOT);
        String key = base.split(":")[0].equals("minecraft") && base.split(":").length > 1 ? base.split(":")[1] : base.split(":")[0];
        PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(key));
        if (t == null) t = byLegacyEffectName(key);
        if (t == null) return null;
        String rest = base.contains(":") && !base.startsWith("minecraft:") ? base.substring(base.indexOf(':')) : "";
        return t.getKey().getKey() + rest;
    }

    private static PotionEffectType byLegacyEffectName(String k) {
        return switch (k) {
            case "slow" -> PotionEffectType.SLOWNESS;
            case "fast_digging" -> PotionEffectType.HASTE;
            case "slow_digging" -> PotionEffectType.MINING_FATIGUE;
            case "increase_damage" -> PotionEffectType.STRENGTH;
            case "heal" -> PotionEffectType.INSTANT_HEALTH;
            case "harm" -> PotionEffectType.INSTANT_DAMAGE;
            case "jump" -> PotionEffectType.JUMP_BOOST;
            case "confusion" -> PotionEffectType.NAUSEA;
            case "damage_resistance" -> PotionEffectType.RESISTANCE;
            default -> null;
        };
    }

    public static Collection<String> effectHints() {
        List<String> l = new ArrayList<>();
        for (PotionEffectType t : Registry.EFFECT) l.add(t.getKey().getKey());
        return l;
    }

    public static String command(String s) {
        String c = s.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty()) return null;
        return c.startsWith("/") ? c : "/" + c;
    }

    public static String upper(String s) { return s.trim().toUpperCase(Locale.ROOT).replace('-', '_'); }

    /** Commands for entry / exit hooks: separated by ';' or '|' (or ',' when no other separator is used). */
    static final class CommandListFlag extends StringSetFlag {
        CommandListFlag(String n, FlagCategory c) { super(n, c, x -> x.startsWith("/") ? x.substring(1) : x, null); }

        @Override public Set<String> parse(String in) throws InvalidFlagFormatException {
            String s = in.trim();
            if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
            String sep = s.contains(";") ? ";" : s.contains("|") ? "\\|" : ",";
            Set<String> out = new LinkedHashSet<>();
            for (String p : s.split(sep)) {
                String t = p.trim();
                if (t.startsWith("/")) t = t.substring(1);
                if (!t.isEmpty()) out.add(t);
            }
            if (out.isEmpty()) throw new InvalidFlagFormatException("Expected at least one command");
            return java.util.Collections.unmodifiableSet(out);
        }

        @Override public String typeName() { return "commands (; separated)"; }
    }

    public enum GlideMode { ALLOW, DENY, FORCE }

    // =================================================================================================
    // WorldGuard core flags (names identical to WorldGuard 7)
    // =================================================================================================
    public static final StateFlag PASSTHROUGH = state("passthrough", PROTECTION, false, "pass through", "Makes the region ignored by membership checks (it only applies its flags).");
    public static final StateFlag BUILD = gated("build", PROTECTION, "build", "Master build flag. Owners and members may build; everybody else may not unless allowed.");
    public static final StateFlag INTERACT = gated("interact", INTERACTION, "interact with that", "Any interaction with blocks and entities.", BUILD);
    public static final StateFlag BLOCK_BREAK = gated("block-break", BLOCKS, "break blocks", "Breaking blocks.", BUILD);
    public static final StateFlag BLOCK_PLACE = gated("block-place", BLOCKS, "place blocks", "Placing blocks.", BUILD);
    public static final StateFlag USE = gated("use", INTERACTION, "use that", "Using doors, buttons, levers, pressure plates and other mechanisms.", INTERACT);
    public static final StateFlag DAMAGE_ANIMALS = gated("damage-animals", ENTITY_DAMAGE, "harm animals", "Hurting passive mobs (animals, villagers, golems...).", INTERACT);
    public static final StateFlag CHEST_ACCESS = gated("chest-access", INVENTORY, "open that", "Opening containers (chests, barrels, hoppers, furnaces...).", INTERACT);
    public static final StateFlag RIDE = gated("ride", VEHICLES, "ride that", "Riding vehicles and mounts.", INTERACT);
    public static final StateFlag PVP = state("pvp", PVP_CAT(), true, "fight players", "Player versus player combat.");
    public static final StateFlag SLEEP = gated("sleep", PLAYER, "sleep", "Sleeping in beds.", INTERACT);
    public static final StateFlag RESPAWN_ANCHORS = gated("respawn-anchors", INTERACTION, "use respawn anchors", "Charging / using respawn anchors.", INTERACT);
    public static final StateFlag TNT = state("tnt", EXPLOSIONS, true, "use TNT", "TNT and TNT minecart explosions.");
    public static final StateFlag VEHICLE_PLACE = gated("vehicle-place", VEHICLES, "place vehicles", "Placing boats and minecarts.", BUILD);
    public static final StateFlag VEHICLE_DESTROY = gated("vehicle-destroy", VEHICLES, "break vehicles", "Destroying boats and minecarts.", BUILD);
    public static final StateFlag LIGHTER = gated("lighter", ITEMS, "light fires", "Using flint and steel / fire charges.", BUILD);
    public static final StateFlag BLOCK_TRAMPLING = gated("block-trampling", BLOCKS, "trample crops", "Trampling farmland and turtle eggs.", BUILD);
    public static final StateFlag FROSTED_ICE_FORM = gated("frosted-ice-form", NATURE, "use frost walker", "Frost walker creating frosted ice.", BUILD);
    public static final StateFlag ITEM_FRAME_ROTATE = gated("item-frame-rotation", ENTITIES, "rotate item frames", "Rotating items in item frames.", INTERACT);
    public static final StateFlag FIREWORK_DAMAGE = gated("firework-damage", ENTITY_DAMAGE, "damage with fireworks", "Firework explosions damaging entities.", BUILD);
    public static final StateFlag USE_ANVIL = gated("use-anvil", INTERACTION, "use anvils", "Using anvils.", USE);
    public static final StateFlag USE_DRIPLEAF = gated("use-dripleaf", INTERACTION, "tilt dripleaf", "Tilting big dripleaf.", USE);
    public static final StateFlag WIND_CHARGE_BURST = gated("wind-charge-burst", EXPLOSIONS, "use wind charges", "Wind charges interacting with blocks (doors, buttons...).", USE);
    public static final StateFlag POTION_SPLASH = state("potion-splash", EFFECTS, true, "splash potions", "Splash and lingering potions affecting entities.");
    public static final StateFlag CREEPER_EXPLOSION = state("creeper-explosion", EXPLOSIONS, true, "", "Creeper explosions.");
    public static final StateFlag ENDERDRAGON_BLOCK_DAMAGE = state("enderdragon-block-damage", MOB_GRIEF, true, "", "Ender dragon destroying blocks.");
    public static final StateFlag GHAST_FIREBALL = state("ghast-fireball", EXPLOSIONS, true, "", "Ghast fireball explosions.");
    public static final StateFlag OTHER_EXPLOSION = state("other-explosion", EXPLOSIONS, true, "", "Every other explosion (beds, anchors, crystals, withers...).");
    public static final StateFlag WITHER_DAMAGE = state("wither-damage", MOB_GRIEF, true, "", "Wither and wither skulls damaging blocks.");
    public static final StateFlag ENDERMAN_GRIEF = state("enderman-grief", MOB_GRIEF, true, "", "Endermen picking up / placing blocks.");
    public static final StateFlag SNOWMAN_TRAILS = state("snowman-trails", MOB_GRIEF, true, "", "Snow golems leaving snow trails.");
    public static final StateFlag RAVAGER_GRIEF = state("ravager-grief", MOB_GRIEF, true, "", "Ravagers destroying crops and leaves.");
    public static final StateFlag BREEZE_CHARGE_EXPLOSION = state("breeze-charge-explosion", EXPLOSIONS, true, "", "Breeze wind charges interacting with blocks.");
    public static final StateFlag MOB_DAMAGE = state("mob-damage", PLAYER_DAMAGE, true, "", "Mobs damaging players.");
    public static final StateFlag MOB_SPAWNING = state("mob-spawning", SPAWNING, true, "", "Natural / spawner / egg mob spawning.");
    public static final StringSetFlag DENY_SPAWN = set("deny-spawn", SPAWNING, Flags::entity, Flags::entityHints, "Entity types that may not spawn.");
    public static final StateFlag ENTITY_PAINTING_DESTROY = state("entity-painting-destroy", MOB_GRIEF, true, "", "Non-player entities breaking paintings.");
    public static final StateFlag ENTITY_ITEM_FRAME_DESTROY = state("entity-item-frame-destroy", MOB_GRIEF, true, "", "Non-player entities breaking item frames.");
    public static final StateFlag LAVA_FIRE = state("lava-fire", WORLD, true, "", "Lava setting blocks on fire.");
    public static final StateFlag LIGHTNING = state("lightning", WORLD, true, "", "Lightning strikes.");
    public static final StateFlag WATER_FLOW = state("water-flow", WORLD, true, "", "Water flowing.");
    public static final StateFlag LAVA_FLOW = state("lava-flow", WORLD, true, "", "Lava flowing.");
    public static final StateFlag SNOW_FALL = state("snow-fall", NATURE, true, "", "Snow layers forming from weather.");
    public static final StateFlag SNOW_MELT = state("snow-melt", NATURE, true, "", "Snow melting.");
    public static final StateFlag ICE_FORM = state("ice-form", NATURE, true, "", "Ice forming.");
    public static final StateFlag ICE_MELT = state("ice-melt", NATURE, true, "", "Ice melting.");
    public static final StateFlag FROSTED_ICE_MELT = state("frosted-ice-melt", NATURE, true, "", "Frosted ice melting.");
    public static final StateFlag MUSHROOM_GROWTH = state("mushroom-growth", NATURE, true, "", "Mushrooms spreading.");
    public static final StateFlag LEAF_DECAY = state("leaf-decay", NATURE, true, "", "Leaves decaying.");
    public static final StateFlag GRASS_GROWTH = state("grass-growth", NATURE, true, "", "Grass spreading.");
    public static final StateFlag MYCELIUM_SPREAD = state("mycelium-spread", NATURE, true, "", "Mycelium spreading.");
    public static final StateFlag VINE_GROWTH = state("vine-growth", NATURE, true, "", "Vines growing.");
    public static final StateFlag ROCK_GROWTH = state("rock-growth", NATURE, true, "", "Pointed dripstone and budding amethyst growing.");
    public static final StateFlag SCULK_GROWTH = state("sculk-growth", NATURE, true, "", "Sculk spreading.");
    public static final StateFlag CROP_GROWTH = state("crop-growth", NATURE, true, "", "Crops growing.");
    public static final StateFlag SOIL_DRY = state("soil-dry", NATURE, true, "", "Farmland drying out.");
    public static final StateFlag CORAL_FADE = state("coral-fade", NATURE, true, "", "Coral dying out of water.");
    public static final StateFlag COPPER_FADE = state("copper-fade", NATURE, true, "", "Copper oxidizing.");
    public static final StateFlag MOISTURE_CHANGE = state("moisture-change", NATURE, true, "", "Farmland moisture changing.");
    public static final StateFlag FIRE_SPREAD = state("fire-spread", WORLD, true, "", "Fire spreading and burning blocks.");
    public static final StateFlag ITEM_PICKUP = state("item-pickup", ITEMS, true, "pick up items", "Players picking up items.");
    public static final StateFlag ITEM_DROP = state("item-drop", ITEMS, true, "drop items", "Players dropping items.");
    public static final StateFlag EXP_DROPS = state("exp-drops", PLAYER, true, "", "Experience orbs dropping.");
    public static final StringFlag DENY_MESSAGE = str("deny-message", MESSAGES, "Message shown when an action is denied. Use %what% for the action.");
    public static final StateFlag ENTRY = REGISTRY.register(new StateFlag("entry", ENTRY_EXIT, true).<StateFlag>group(RegionGroup.NON_MEMBERS).<StateFlag>desc("Whether players may enter the region.").<StateFlag>action("enter"));
    public static final StateFlag EXIT = REGISTRY.register(new StateFlag("exit", ENTRY_EXIT, true).<StateFlag>group(RegionGroup.NON_MEMBERS).<StateFlag>desc("Whether players may leave the region.").<StateFlag>action("leave"));
    public static final BooleanFlag EXIT_OVERRIDE = bool("exit-override", ENTRY_EXIT, "Always allow leaving, even if a higher priority region denies exit.");
    public static final StateFlag EXIT_VIA_TELEPORT = state("exit-via-teleport", ENTRY_EXIT, true, "teleport out", "Leaving the region by teleporting.");
    public static final StringFlag ENTRY_DENY_MESSAGE = str("entry-deny-message", ENTRY_EXIT, "Message when entry is denied.");
    public static final StringFlag EXIT_DENY_MESSAGE = str("exit-deny-message", ENTRY_EXIT, "Message when exit is denied.");
    public static final BooleanFlag NOTIFY_ENTER = bool("notify-enter", MESSAGES, "Notify staff with fawg.notify when a player enters.");
    public static final BooleanFlag NOTIFY_LEAVE = bool("notify-leave", MESSAGES, "Notify staff with fawg.notify when a player leaves.");
    public static final StringFlag GREETING = str("greeting", MESSAGES, "Chat message on entry.");
    public static final StringFlag FAREWELL = str("farewell", MESSAGES, "Chat message on exit.");
    public static final StringFlag GREETING_TITLE = str("greeting-title", MESSAGES, "Title on entry (use \\n to add a subtitle).");
    public static final StringFlag FAREWELL_TITLE = str("farewell-title", MESSAGES, "Title on exit (use \\n to add a subtitle).");
    public static final StateFlag ENDERPEARL = state("enderpearl", TELEPORT, true, "use ender pearls", "Ender pearl teleports into / inside the region.");
    public static final StateFlag CHORUS_TELEPORT = state("chorus-fruit-teleport", TELEPORT, true, "teleport with chorus fruit", "Chorus fruit teleports.");
    public static final StateFlag INVINCIBILITY = state("invincible", PLAYER_DAMAGE, false, "", "Players take no damage.");
    public static final StateFlag FALL_DAMAGE = state("fall-damage", PLAYER_DAMAGE, true, "", "Players take fall damage.");
    public static final EnumFlag<GameMode> GAME_MODE = REGISTRY.register(new EnumFlag<>("game-mode", PLAYER, GameMode.class).<EnumFlag<GameMode>>desc("Forces a game mode inside the region."));
    public static final StringFlag TIME_LOCK = str("time-lock", PLAYER, "Per-player time (ticks, or +/- relative). Example: 6000 for noon.");
    public static final EnumFlag<WeatherType> WEATHER_LOCK = REGISTRY.register(new EnumFlag<>("weather-lock", PLAYER, WeatherType.class).<EnumFlag<WeatherType>>desc("Per-player weather (clear / downfall)."));
    public static final IntFlag HEAL_DELAY = num("heal-delay", PLAYER, 0, 86400, "Seconds between heals.");
    public static final IntFlag HEAL_AMOUNT = num("heal-amount", PLAYER, -1000, 1000, "Health added per heal (negative hurts).");
    public static final DoubleFlag MIN_HEAL = dbl("heal-min-health", PLAYER, 0, 1024, "Healing only happens above this health.");
    public static final DoubleFlag MAX_HEAL = dbl("heal-max-health", PLAYER, 0, 1024, "Healing stops at this health.");
    public static final IntFlag FEED_DELAY = num("feed-delay", PLAYER, 0, 86400, "Seconds between feeds.");
    public static final IntFlag FEED_AMOUNT = num("feed-amount", PLAYER, -20, 20, "Hunger points added per feed.");
    public static final IntFlag MIN_FOOD = num("feed-min-hunger", PLAYER, 0, 20, "Feeding only happens above this hunger.");
    public static final IntFlag MAX_FOOD = num("feed-max-hunger", PLAYER, 0, 20, "Feeding stops at this hunger.");
    public static final LocationFlag TELE_LOC = loc("teleport", TELEPORT, "Location used by /rg teleport.");
    public static final LocationFlag SPAWN_LOC = loc("spawn", TELEPORT, "Respawn location for players who die inside.");
    public static final StringFlag TELE_MESSAGE = str("teleport-message", TELEPORT, "Message after /rg teleport. %id% = region.");
    public static final StringSetFlag BLOCKED_CMDS = set("blocked-cmds", CHAT_COMMANDS, Flags::command, () -> List.of("/home", "/spawn", "/tpa"), "Commands that may not be used.");
    public static final StringSetFlag ALLOWED_CMDS = set("allowed-cmds", CHAT_COMMANDS, Flags::command, () -> List.of("/msg", "/spawn"), "Only these commands may be used.");
    public static final StringSetFlag NONPLAYER_PROTECTION_DOMAINS = set("nonplayer-protection-domains", PROTECTION, s -> s.toLowerCase(Locale.ROOT), () -> List.of(), "Region ids treated as one protection domain for pistons, fluids, trees...");
    public static final StateFlag HEALTH_REGEN = state("natural-health-regen", PLAYER, true, "", "Natural health regeneration.");
    public static final StateFlag HUNGER_DRAIN = state("natural-hunger-drain", PLAYER, true, "", "Natural hunger drain.");
    public static final StateFlag PISTONS = state("pistons", FlagCategory.REDSTONE, true, "", "Pistons moving blocks.");
    public static final StateFlag SEND_CHAT = state("send-chat", CHAT_COMMANDS, true, "chat", "Sending chat messages.");
    public static final StateFlag RECEIVE_CHAT = state("receive-chat", CHAT_COMMANDS, true, "", "Receiving chat messages.");

    private static FlagCategory PVP_CAT() { return FlagCategory.PVP; }

    // =================================================================================================
    // WorldGuard ExtraFlags Plus (names identical)
    // =================================================================================================
    public static final LocationFlag TELEPORT_ON_ENTRY = loc("teleport-on-entry", ENTRY_EXIT, "Teleports players who enter.");
    public static final LocationFlag TELEPORT_ON_EXIT = loc("teleport-on-exit", ENTRY_EXIT, "Teleports players who leave by walking.");
    public static final LocationFlag JOIN_LOCATION = loc("join-location", ENTRY_EXIT, "Players logging in inside are moved here.");
    public static final LocationFlag RESPAWN_LOCATION = loc("respawn-location", TELEPORT, "Overrides the respawn point of players dying inside.");
    public static final StringSetFlag COMMAND_ON_ENTRY = commands("command-on-entry", AUTOMATION, "Commands run as the player on entry (%player%, %uuid%, %region%).");
    public static final StringSetFlag COMMAND_ON_EXIT = commands("command-on-exit", AUTOMATION, "Commands run as the player on exit.");
    public static final StringSetFlag CONSOLE_COMMAND_ON_ENTRY = commands("console-command-on-entry", AUTOMATION, "Console commands run on entry.");
    public static final StringSetFlag CONSOLE_COMMAND_ON_EXIT = commands("console-command-on-exit", AUTOMATION, "Console commands run on exit.");
    public static final DoubleFlag WALK_SPEED = dbl("walk-speed", MOVEMENT, -1, 1, "Walk speed inside (default 0.2).");
    public static final DoubleFlag FLY_SPEED = dbl("fly-speed", MOVEMENT, -1, 1, "Fly speed inside (default 0.1).");
    public static final StateFlag FLY = state("fly", MOVEMENT, true, "fly", "Allow (grants) or deny (removes) flight.");
    public static final EnumFlag<GlideMode> GLIDE = REGISTRY.register(new EnumFlag<>("glide", MOVEMENT, GlideMode.class).<EnumFlag<GlideMode>>desc("allow / deny elytra gliding, force = elytra disabled entirely."));
    public static final StateFlag FROSTWALKER = state("frostwalker", NATURE, true, "use frost walker", "Frost walker creating frosted ice.");
    public static final StateFlag GODMODE = state("godmode", PLAYER_DAMAGE, false, "", "Players are invulnerable (allow = god mode).");
    public static final BooleanFlag KEEP_INVENTORY = bool("keep-inventory", PLAYER, "Keep inventory on death.");
    public static final BooleanFlag KEEP_EXP = bool("keep-exp", PLAYER, "Keep experience on death.");
    public static final StateFlag ITEM_DURABILITY = state("item-durability", ITEMS, true, "", "Items lose durability.");
    public static final StringFlag CHAT_PREFIX = str("chat-prefix", CHAT_COMMANDS, "Prefix added to chat messages of players inside.");
    public static final StringFlag CHAT_SUFFIX = str("chat-suffix", CHAT_COMMANDS, "Suffix added to chat messages of players inside.");
    public static final StringSetFlag BLOCKED_EFFECTS = set("blocked-effects", EFFECTS, Flags::effect, Flags::effectHints, "Potion effects removed / blocked inside.");
    public static final StringSetFlag GIVE_EFFECTS = set("give-effects", EFFECTS, Flags::effect, Flags::effectHints, "Effects given inside: effect[:amplifier[:particles]].");
    public static final StringSetFlag PLAY_SOUNDS = set("play-sounds", MESSAGES, s -> s.trim().toLowerCase(Locale.ROOT), () -> List.of("minecraft:block.note_block.pling", "minecraft:ambient.cave"), "Sounds played periodically: sound[:interval_seconds[:volume[:pitch]]].");
    public static final StateFlag WORLDEDIT = state("worldedit", WORLDEDIT_CAT(), true, "use WorldEdit", "WorldEdit / FAWE editing inside.");
    public static final StateFlag NETHER_PORTALS = state("nether-portals", TELEPORT, true, "use nether portals", "Creating / using nether portals.");
    public static final StateFlag CHUNK_UNLOAD = state("chunk-unload", WORLD, true, "", "deny = chunks of the region are kept loaded.");
    public static final StateFlag VILLAGER_TRADE = state("villager-trade", ENTITIES, true, "trade with villagers", "Trading with villagers / wandering traders.");
    public static final StateFlag ENDERCHEST_ACCESS = state("enderchest-access", INVENTORY, true, "open ender chests", "Opening ender chests.");
    public static final StateFlag INVENTORY_CRAFT = state("inventory-craft", INVENTORY, true, "craft", "Crafting in the 2x2 inventory grid.");
    public static final StringSetFlag ALLOW_BLOCK_PLACE = set("allow-block-place", BLOCKS, Flags::material, Flags::materialHints, "Only these blocks may be placed.");
    public static final StringSetFlag DENY_BLOCK_PLACE = set("deny-block-place", BLOCKS, Flags::material, Flags::materialHints, "These blocks may not be placed.");
    public static final StringSetFlag ALLOW_BLOCK_BREAK = set("allow-block-break", BLOCKS, Flags::material, Flags::materialHints, "Only these blocks may be broken.");
    public static final StringSetFlag DENY_BLOCK_BREAK = set("deny-block-break", BLOCKS, Flags::material, Flags::materialHints, "These blocks may not be broken.");
    public static final StringSetFlag DENY_ITEM_DROPS = set("deny-item-drops", ITEMS, Flags::material, Flags::materialHints, "Items that may not be dropped.");
    public static final StringSetFlag DENY_ITEM_PICKUP = set("deny-item-pickup", ITEMS, Flags::material, Flags::materialHints, "Items that may not be picked up.");
    public static final StringSetFlag DISABLE_COMPLETELY = set("disable-completely", ITEMS, Flags::material, () -> List.of("mace", "firework_rocket", "wind_charge", "totem_of_undying", "trident", "goat_horn", "ender_pearl"), "Items that may not be used at all (any click / launch / totem pop).");
    public static final StringSetFlag DISABLE_THROW = set("disable-throw", ITEMS, Flags::material, () -> List.of("egg", "snowball", "ender_pearl", "experience_bottle", "splash_potion", "lingering_potion", "trident", "wind_charge"), "Items that may not be thrown.");
    public static final StringSetFlag PERMIT_WORKBENCHES = set("permit-workbenches", INVENTORY, Flags::upper, () -> List.of("all", "anvil", "cartography", "craft", "ender", "grindstone", "loom", "smithing", "stonecutter", "enchanting", "brewing", "beacon", "crafter", "clear"), "Workbench types that are blocked (ALL / CLEAR supported).");
    public static final IntFlag ENTRY_MIN_LEVEL = num("entry-min-level", ENTRY_EXIT, 0, 1_000_000, "Minimum XP level needed to enter.");
    public static final IntFlag ENTRY_MAX_LEVEL = num("entry-max-level", ENTRY_EXIT, 0, 1_000_000, "Maximum XP level allowed to enter.");
    public static final StringFlag ENTRY_PERMISSION = str("entry-permission", ENTRY_EXIT, "Permission required to enter.");
    public static final StringFlag ENTRY_DENY_PERMISSION = str("entry-deny-permission", ENTRY_EXIT, "Players with this permission may not enter.");
    public static final IntFlag PLAYER_COUNT_LIMIT = num("player-count-limit", ENTRY_EXIT, 0, 100_000, "Maximum players inside at once.");
    public static final BooleanFlag DISABLE_COLLISION = bool("disable-collision", PLAYER, "Disables player collision inside.");
    public static final BooleanFlag HIDE_PLAYERS = bool("hide-players", PLAYER, "Players inside cannot see each other.");
    public static final StateFlag CHAMBERED_ENDERPEARL = state("chambered-enderpearl", TELEPORT, true, "", "deny = pearls thrown before entering are removed (stasis chamber bypass fix).");
    public static final StateFlag LIGHTNING_DAMAGE = state("lightning-damage", PLAYER_DAMAGE, true, "", "Lightning damaging players.");
    public static final StringSetFlag DENY_MOBSPAWN = set("deny-mobspawn", SPAWNING, Flags::entity, Flags::entityHints, "Mob categories / types that may not spawn (ALL_HOSTILE, ALL_NEUTRAL, ALL_PEACEFUL, ALL or types).");
    public static final StringFlag CONSOLE_COMMAND_REPEAT = str("console-command-repeat", AUTOMATION, "\"<seconds> <command>\" repeated while inside; cooldown persists after leaving.");
    public static final StringFlag CONSOLE_COMMAND_TIMER = str("console-command-timer", AUTOMATION, "\"<seconds> <command>\" repeated while inside; resets on exit.");

    private static FlagCategory WORLDEDIT_CAT() { return FlagCategory.WORLDEDIT; }

    // =================================================================================================
    // WorldGuard Region Protect (absorbed)
    // =================================================================================================
    public static final EnumFlag<ProtectLevel> REGION_LOCK = REGISTRY.register(new EnumFlag<>("region-lock", PROTECTION, ProtectLevel.class).<EnumFlag<ProtectLevel>>desc("Hard lock: server = only console / fawg.lock.bypass, player = owners too, break-allowed, break-only."));
    public static final StateFlag WORLDEDIT_COMMANDS = state("worldedit-commands", FlagCategory.WORLDEDIT, true, "use WorldEdit commands", "WorldEdit / FAWE commands whose selection touches the region.");
    public static final StateFlag SUPERPICKAXE = state("superpickaxe", FlagCategory.WORLDEDIT, true, "use the super pickaxe", "WorldEdit super pickaxe.");
    public static final StateFlag FLUID_FLOW_IN = state("fluid-flow-in", WORLD, true, "", "Water / lava flowing into the region from outside.");
    public static final StateFlag COBBLE_GENERATION = state("cobble-generation", WORLD, true, "", "Cobblestone, stone, obsidian and basalt generation.");
    public static final BooleanFlag ADMIN_SPY = bool("admin-spy", PROTECTION, "Staff with fawg.spy are told about denied actions here.");
    public static final StateFlag LECTERN_TAKE = gated("lectern-take", INVENTORY, "take lectern books", "Taking books from lecterns.", CHEST_ACCESS);
    public static final StateFlag STONECUTTER_SELECT = gated("stonecutter-select", INVENTORY, "use stonecutters", "Selecting stonecutter recipes.", USE);
    public static final StateFlag LOOM_SELECT = gated("loom-select", INVENTORY, "use looms", "Selecting loom patterns.", USE);
    public static final StateFlag FLOWER_POT = gated("flower-pot", INTERACTION, "use flower pots", "Taking / placing plants in flower pots.", INTERACT);
    public static final StateFlag VEHICLE_COLLISION = state("vehicle-collision", VEHICLES, true, "", "Players pushing vehicles by walking into them.");
    public static final StateFlag ITEM_FRAME_INTERACT = gated("item-frame-interact", ENTITIES, "use item frames", "Placing / taking items in item frames.", INTERACT);
    public static final StateFlag HANGING_PLACE = gated("hanging-place", BLOCKS, "place item frames or paintings", "Placing item frames and paintings.", BUILD);
    public static final StateFlag HANGING_BREAK = gated("hanging-break", BLOCKS, "break item frames or paintings", "Breaking item frames and paintings.", BUILD);

    // =================================================================================================
    // FastAsyncWorldGuard extras
    // =================================================================================================
    // --- generic lists
    public static final StringSetFlag DENY_ITEM_USE = set("deny-item-use", ITEMS, Flags::material, Flags::materialHints, "Items that may not be used (right click).");
    public static final StringSetFlag ALLOW_ITEM_USE = set("allow-item-use", ITEMS, Flags::material, Flags::materialHints, "Only these items may be used.");
    public static final StringSetFlag DENY_BLOCK_INTERACT = set("deny-block-interact", INTERACTION, Flags::material, Flags::materialHints, "Blocks that may not be right clicked.");
    public static final StringSetFlag DENY_ENTITY_INTERACT = set("deny-entity-interact", ENTITIES, Flags::entity, Flags::entityHints, "Entity types that may not be right clicked.");
    public static final StringSetFlag DENY_ENTITY_DAMAGE = set("deny-entity-damage", ENTITY_DAMAGE, Flags::entity, Flags::entityHints, "Entity types players may not hurt.");
    public static final StringSetFlag DENY_CRAFT = set("deny-craft", INVENTORY, Flags::material, Flags::materialHints, "Items that may not be crafted.");
    public static final StringSetFlag DENY_EQUIP = set("deny-equip", ITEMS, Flags::material, Flags::materialHints, "Armor / items that may not be equipped via right click.");
    // --- movement
    public static final StateFlag SPRINT = state("sprint", MOVEMENT, true, "sprint", "Sprinting.");
    public static final StateFlag JUMP = state("jump", MOVEMENT, true, "jump", "Jumping.");
    public static final StateFlag SWIM = state("swim", MOVEMENT, true, "swim", "Swimming (sprint swimming).");
    public static final StateFlag ELYTRA_BOOST = state("elytra-boost", MOVEMENT, true, "boost with fireworks", "Firework boosting while gliding.");
    public static final StateFlag RIPTIDE = state("riptide", MOVEMENT, true, "use riptide", "Riptide trident launches.");
    public static final StateFlag WIND_CHARGE_LAUNCH = state("wind-charge-launch", MOVEMENT, true, "", "Wind charges launching players.");
    public static final StateFlag VEHICLE_ENTER = gated("vehicle-enter", VEHICLES, "enter vehicles", "Entering any vehicle / mount.", RIDE);
    public static final StateFlag VEHICLE_EXIT = state("vehicle-exit", VEHICLES, true, "leave vehicles", "Leaving vehicles inside the region.");
    public static final StateFlag VEHICLE_MOVE_IN = state("vehicle-entry", VEHICLES, true, "", "Vehicles crossing into the region.");
    // --- teleport
    public static final StateFlag TELEPORT_IN = state("teleport-in", TELEPORT, true, "teleport here", "Any teleport into the region.");
    public static final StateFlag TELEPORT_OUT = state("teleport-out", TELEPORT, true, "teleport away", "Any teleport out of the region.");
    public static final StateFlag END_PORTALS = state("end-portals", TELEPORT, true, "use end portals", "Using end portals / gateways.");
    public static final StateFlag ENTITY_PORTALS = state("entity-portals", TELEPORT, true, "", "Non-player entities using portals.");
    public static final StateFlag PET_TELEPORT = state("pet-teleport", TELEPORT, true, "", "Tamed pets teleporting to their owner.");
    // --- player state
    public static final StateFlag EXP_PICKUP = state("exp-pickup", PLAYER, true, "", "Collecting experience orbs.");
    public static final StateFlag ITEM_CONSUME = state("item-consume", ITEMS, true, "eat or drink", "Eating / drinking anything.");
    public static final StateFlag TOTEM_USE = state("totem-use", ITEMS, true, "", "Totems of undying saving players.");
    public static final StateFlag HUNGER = state("hunger", PLAYER, true, "", "Any hunger change (deny freezes the food bar).");
    public static final StateFlag ITEM_DESPAWN = state("item-despawn", ITEMS, true, "", "Dropped items despawning.");
    public static final StateFlag ITEM_FRAME_DROP = state("item-frame-drop", ITEMS, true, "", "Item frames dropping their item.");
    public static final StateFlag SWAP_HANDS = state("swap-hands", ITEMS, true, "swap hands", "Swapping items to the off hand.");
    public static final BooleanFlag GLOWING = bool("glowing", PLAYER, "Players inside glow.");
    public static final IntFlag CHAT_COOLDOWN = num("chat-cooldown", CHAT_COMMANDS, 0, 3600, "Seconds between chat messages for players inside.");
    public static final StateFlag SIGN_EDIT = gated("sign-edit", INTERACTION, "edit signs", "Editing signs.", INTERACT);
    public static final StateFlag BOOK_EDIT = state("book-edit", ITEMS, true, "write books", "Editing / signing books.");
    public static final StateFlag ANVIL_RENAME = state("anvil-rename", INVENTORY, true, "rename items", "Renaming items in anvils.");
    public static final StateFlag ENCHANTING = state("enchanting", INVENTORY, true, "enchant", "Enchanting items.");
    public static final StateFlag BREWING = state("brewing", INVENTORY, true, "", "Potions brewing.");
    public static final StateFlag WORKBENCH_CRAFT = state("workbench-craft", INVENTORY, true, "craft", "Crafting in crafting tables / crafters.");
    public static final StateFlag SMITHING = state("smithing", INVENTORY, true, "smith", "Using smithing tables.");
    public static final StateFlag FISHING = state("fishing", ITEMS, true, "fish", "Fishing (and hooking entities).");
    public static final StateFlag RAID_TRIGGER = state("raid-trigger", MOB_GRIEF, true, "", "Raids starting.");
    public static final StateFlag ARMOR_STAND_MANIPULATE = gated("armor-stand-manipulate", ENTITIES, "use armor stands", "Taking / placing armor stand equipment.", INTERACT);
    public static final StateFlag ENTITY_LEASH = gated("entity-leash", ENTITIES, "leash mobs", "Leashing / unleashing mobs.", INTERACT);
    public static final StateFlag ENTITY_SHEAR = gated("entity-shear", ENTITIES, "shear that", "Shearing sheep, mooshrooms, snow golems, bogged...", INTERACT);
    public static final StateFlag ENTITY_BUCKET = gated("entity-bucket", ENTITIES, "bucket that", "Catching fish / axolotls / tadpoles and milking.", INTERACT);
    public static final StateFlag ENTITY_NAME = gated("entity-name", ENTITIES, "name that", "Using name tags on mobs.", INTERACT);
    public static final StateFlag ENTITY_FEED = gated("entity-feed", ENTITIES, "feed that", "Feeding / breeding animals.", INTERACT);
    public static final StateFlag ENTITY_TAME = state("entity-tame", ENTITIES, true, "tame that", "Taming animals.");
    public static final StateFlag ENTITY_BREED = state("entity-breed", ENTITIES, true, "", "Animals breeding.");
    public static final StateFlag ENTITY_DYE = gated("entity-dye", ENTITIES, "dye that", "Dyeing sheep / wolf / cat collars.", INTERACT);
    // --- mobs & world
    public static final StateFlag MOB_TARGET_PLAYERS = state("mob-target-players", MOB_GRIEF, true, "", "Mobs targeting players.");
    public static final StateFlag MOB_PICKUP_ITEMS = state("mob-pickup-items", MOB_GRIEF, true, "", "Mobs picking up items.");
    public static final StateFlag MOB_DOOR_BREAK = state("mob-door-break", MOB_GRIEF, true, "", "Zombies breaking doors.");
    public static final StateFlag VILLAGER_FARMING = state("villager-farming", MOB_GRIEF, true, "", "Villagers harvesting / planting crops.");
    public static final StateFlag SILVERFISH_INFEST = state("silverfish-infest", MOB_GRIEF, true, "", "Silverfish entering / leaving blocks.");
    public static final StateFlag SHEEP_EAT_GRASS = state("sheep-eat-grass", MOB_GRIEF, true, "", "Sheep eating grass.");
    public static final StateFlag RABBIT_EAT_CROPS = state("rabbit-eat-crops", MOB_GRIEF, true, "", "Rabbits eating carrots.");
    public static final StateFlag TURTLE_EGG_TRAMPLE = state("turtle-egg-trample", MOB_GRIEF, true, "", "Mobs trampling turtle eggs.");
    public static final StateFlag FALLING_BLOCKS = state("falling-blocks", WORLD, true, "", "Sand / gravel / anvils falling and landing.");
    public static final StateFlag CREEPER_CHARGE = state("creeper-charge", MOB_GRIEF, true, "", "Lightning charging creepers.");
    public static final StateFlag SLIME_SPLIT = state("slime-split", SPAWNING, true, "", "Slimes / magma cubes splitting.");
    public static final StateFlag SHEEP_REGROW = state("sheep-regrow", NATURE, true, "", "Sheep regrowing wool.");
    public static final StateFlag CHICKEN_EGGS = state("chicken-eggs", NATURE, true, "", "Chickens laying eggs.");
    public static final StateFlag FIRE_BURN = state("fire-burn", WORLD, true, "", "Fire destroying blocks.");
    public static final StateFlag BONE_MEAL = gated("bone-meal", NATURE, "use bone meal", "Players fertilizing with bone meal.", BUILD);
    public static final StateFlag STRUCTURE_GROWTH = state("structure-growth", NATURE, true, "", "Trees / huge mushrooms growing.");
    public static final StateFlag SPONGE_ABSORB = state("sponge-absorb", WORLD, true, "", "Sponges absorbing water.");
    public static final StateFlag DRAGON_EGG_TELEPORT = state("dragon-egg-teleport", WORLD, true, "", "Dragon eggs teleporting.");
    public static final StateFlag BLOCK_EXPLOSIONS = state("block-explosions", EXPLOSIONS, true, "", "Beds / respawn anchors exploding.");
    public static final StateFlag EXPLOSION_BLOCK_DAMAGE = state("explosion-block-damage", EXPLOSIONS, true, "", "Any explosion destroying blocks (keeps knockback / damage).");
    public static final StateFlag EXPLOSION_ENTITY_DAMAGE = state("explosion-entity-damage", EXPLOSIONS, true, "", "Explosions damaging entities.");
    public static final StateFlag CROSS_BORDER_EXPLOSIONS = state("cross-border-explosions", EXPLOSIONS, false, "", "Explosions from outside the region may damage it (default deny = protected).");
    // --- redstone
    public static final StateFlag REDSTONE_SIGNALS = state("redstone", FlagCategory.REDSTONE, true, "", "Redstone signals changing.");
    public static final StateFlag DISPENSERS = state("dispensers", FlagCategory.REDSTONE, true, "", "Dispensers / droppers firing.");
    public static final StateFlag HOPPERS = state("hoppers", FlagCategory.REDSTONE, true, "", "Hoppers moving items across the region border.");
    public static final StateFlag SCULK_SENSORS = state("sculk-sensors", FlagCategory.REDSTONE, true, "", "Sculk sensors / shriekers activating.");
    public static final StateFlag TNT_PRIME = state("tnt-prime", EXPLOSIONS, true, "prime TNT", "TNT being primed.");
    public static final StateFlag BELL_RING = state("bell-ring", FlagCategory.REDSTONE, true, "ring bells", "Bells ringing.");
    public static final StateFlag CROSS_BORDER_PISTONS = state("cross-border-pistons", FlagCategory.REDSTONE, false, "", "Pistons outside pushing blocks into the region (default deny = protected).");
    // --- protection behaviour
    public static final BooleanFlag ANTI_GHOST_BLOCKS = bool("anti-ghost-blocks", PROTECTION, "true = players who cannot build here are put in adventure-style lock so blocks never flash client side.");
    public static final BooleanFlag SHOW_DENY_MESSAGES = bool("show-deny-messages", MESSAGES, "false hides deny messages in this region.");
    public static final StringFlag GREETING_ACTIONBAR = str("greeting-actionbar", MESSAGES, "Action bar on entry.");
    public static final StringFlag FAREWELL_ACTIONBAR = str("farewell-actionbar", MESSAGES, "Action bar on exit.");
    public static final StringFlag GREETING_SOUND = str("greeting-sound", MESSAGES, "Sound played on entry (eg. minecraft:entity.player.levelup).");
    public static final StringFlag FAREWELL_SOUND = str("farewell-sound", MESSAGES, "Sound played on exit.");
    public static final StringFlag ENTRY_BOSSBAR = str("entry-bossbar", MESSAGES, "Boss bar shown while inside (MiniMessage).");
    public static final StringFlag DISPLAY_NAME = str("display-name", MESSAGES, "Pretty name used in messages / HUD.");
    public static final IntFlag ENTRY_COOLDOWN = num("entry-cooldown", ENTRY_EXIT, 0, 86400, "Seconds a player must wait before re-entering after leaving.");
    public static final StateFlag WORLD_EDIT_BRUSHES = state("worldedit-brushes", FlagCategory.WORLDEDIT, true, "use brushes", "WorldEdit / FAWE brush tools.");

    /** Forces class init. */
    public static void init() {
        REGISTRY.alias("enderman-griefing", ENDERMAN_GRIEF);
        REGISTRY.alias("item-frame-rotate", ITEM_FRAME_ROTATE);
        REGISTRY.alias("invincibility", INVINCIBILITY);
        REGISTRY.alias("blocked-commands", BLOCKED_CMDS);
        REGISTRY.alias("allowed-commands", ALLOWED_CMDS);
        REGISTRY.alias("heal-min", MIN_HEAL);
        REGISTRY.alias("heal-max", MAX_HEAL);
        REGISTRY.alias("feed-min", MIN_FOOD);
        REGISTRY.alias("feed-max", MAX_FOOD);
        REGISTRY.alias("gamemode", GAME_MODE);
        REGISTRY.alias("god", GODMODE);
        REGISTRY.alias("trample", BLOCK_TRAMPLING);
        FlagFamilies.init();
    }

    public static int builtinHandWritten() { return 0; }

    public static Flag<?> get(String name) { return REGISTRY.get(name); }
}
