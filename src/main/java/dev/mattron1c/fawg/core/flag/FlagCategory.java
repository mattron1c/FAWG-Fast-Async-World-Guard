package dev.mattron1c.fawg.core.flag;

/** Categories used by the HUD / GUIs / docs to browse 1000+ flags comfortably. */
public enum FlagCategory {
    PROTECTION("Protection", "🛡", "SHIELD"),
    BLOCKS("Blocks", "🧱", "BRICKS"),
    INTERACTION("Block Interaction", "🖐", "OAK_DOOR"),
    ITEMS("Item Use", "🗡", "DIAMOND_SWORD"),
    INVENTORY("Inventories", "🎒", "CHEST"),
    ENTITIES("Entity Interaction", "🐄", "LEAD"),
    ENTITY_DAMAGE("Entity Damage", "⚔", "IRON_SWORD"),
    SPAWNING("Spawning", "🥚", "SPAWNER"),
    MOB_GRIEF("Mob Griefing", "💀", "CREEPER_HEAD"),
    EXPLOSIONS("Explosions", "💥", "TNT"),
    PLAYER_DAMAGE("Player Damage", "❤", "RED_DYE"),
    PVP("PvP & Combat", "⚔", "NETHERITE_SWORD"),
    EFFECTS("Potion Effects", "🧪", "POTION"),
    PLAYER("Player State", "👤", "PLAYER_HEAD"),
    MOVEMENT("Movement", "🏃", "FEATHER"),
    ENTRY_EXIT("Entry & Exit", "🚪", "IRON_DOOR"),
    MESSAGES("Messages & Titles", "💬", "OAK_SIGN"),
    CHAT_COMMANDS("Chat & Commands", "⌨", "WRITABLE_BOOK"),
    TELEPORT("Teleportation", "🌀", "ENDER_PEARL"),
    VEHICLES("Vehicles & Mounts", "🐎", "SADDLE"),
    REDSTONE("Redstone & Mechanics", "⚡", "REDSTONE"),
    NATURE("Nature & Growth", "🌱", "OAK_SAPLING"),
    WORLD("World & Environment", "🌍", "GRASS_BLOCK"),
    WORLDEDIT("WorldEdit / FAWE", "🪓", "WOODEN_AXE"),
    AUTOMATION("Automation", "⚙", "COMMAND_BLOCK"),
    MISC("Miscellaneous", "✦", "NETHER_STAR");

    public final String display;
    public final String emoji;
    public final String icon;

    FlagCategory(String display, String emoji, String icon) {
        this.display = display; this.emoji = emoji; this.icon = icon;
    }
}
