package dev.mattron1c.fawg.listener;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.flag.StringSetFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.flags.FlagFamilies;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.protect.Protection;
import io.papermc.paper.event.player.PlayerLoomPatternSelectEvent;
import io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class InteractListener implements Listener {
    private final FAWG plugin;
    private final Protection pr;
    /** Block interaction categories that only matter while holding a matching tool. */
    private final Map<String, Predicate<Material>> conditional = new HashMap<>();

    public InteractListener(FAWG plugin) {
        this.plugin = plugin;
        this.pr = plugin.protection();
        conditional.put("interact-logs", m -> m.name().endsWith("_AXE"));
        conditional.put("interact-copper-blocks", m -> m.name().endsWith("_AXE") || m == Material.HONEYCOMB);
        conditional.put("interact-dirt-blocks", m -> m.name().endsWith("_HOE") || m.name().endsWith("_SHOVEL"));
        conditional.put("interact-crops", m -> m == Material.BONE_MEAL);
        conditional.put("interact-tnt", m -> m == Material.FLINT_AND_STEEL || m == Material.FIRE_CHARGE);
        conditional.put("interact-pumpkins", m -> m == Material.SHEARS);
        conditional.put("interact-farmland", m -> false);
        conditional.put("interact-turtle-eggs", m -> false);
    }

    private boolean listed(Player p, ApplicableSet set, StringSetFlag f, String key) {
        if (pr.bypasses(p)) return false;
        Set<String> s = pr.value(p, set, f);
        return s != null && s.contains(key);
    }

    private boolean disabledCompletely(Player p, ApplicableSet set, Material m) {
        return m != null && listed(p, set, Flags.DISABLE_COMPLETELY, m.name());
    }

    // ==================================================================================== interact
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        Block b = e.getClickedBlock();
        ItemStack item = e.getItem();
        Material im = item == null ? Material.AIR : item.getType();

        if (a == Action.PHYSICAL) {
            if (b == null) return;
            ApplicableSet set = pr.at(b);
            Material bm = b.getType();
            StateFlag[] fl = FlagFamilies.interactBlock(bm);
            if (fl.length == 0) return;
            StateFlag d = pr.firstDenied(p, set, fl);
            if (d != null) {
                e.setUseInteractedBlock(Event.Result.DENY);
                e.setCancelled(true);
                if (bm == Material.FARMLAND || bm == Material.TURTLE_EGG) pr.deny(p, set, d, b.getLocation());
            }
            return;
        }

        ApplicableSet set = b != null ? pr.at(b) : pr.at(p.getLocation());

        if (item != null && disabledCompletely(p, set, im)) {
            e.setCancelled(true);
            e.setUseItemInHand(Event.Result.DENY);
            pr.deny(p, set, Flags.ITEM_CONSUME, p.getLocation());
            p.updateInventory();
            return;
        }

        if (a == Action.RIGHT_CLICK_BLOCK && b != null && e.useInteractedBlock() != Event.Result.DENY) {
            boolean sneakPlace = p.isSneaking() && item != null && !im.isAir();
            if (!sneakPlace) {
                Material bm = b.getType();
                StateFlag denied = null;
                for (StateFlag f : FlagFamilies.interactBlock(bm)) {
                    Predicate<Material> cond = conditional.get(f.name());
                    if (cond != null && !cond.test(im)) continue;
                    if (!pr.can(p, set, f)) { denied = f; break; }
                }
                if (denied == null && listed(p, set, Flags.DENY_BLOCK_INTERACT, bm.name())) denied = Flags.INTERACT;
                if (denied != null) {
                    e.setUseInteractedBlock(Event.Result.DENY);
                    if (e.getHand() == EquipmentSlot.HAND || e.getHand() == null) pr.deny(p, set, denied, b.getLocation());
                    plugin.ghost().resync(p, b);
                    if (bm.name().endsWith("_DOOR")) plugin.ghost().resync(p, b.getRelative(org.bukkit.block.BlockFace.UP));
                }
            }
        }

        if (item != null && !im.isAir() && (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && e.useItemInHand() != Event.Result.DENY) {
            StateFlag denied = null;
            ApplicableSet useSet = set;
            if (b != null && (im.name().endsWith("_BOAT") || im.name().endsWith("_RAFT") || im.name().endsWith("MINECART") || im == Material.ARMOR_STAND || im == Material.END_CRYSTAL || im.name().endsWith("_SPAWN_EGG"))) {
                useSet = pr.at(b.getRelative(e.getBlockFace()));
            }
            for (StateFlag f : FlagFamilies.useItem(im)) {
                // block-targeting tools only matter when actually clicking a block
                if (b == null && (f.name().equals("use-item-hoes") || f.name().equals("use-item-axes") || f.name().equals("use-item-shovels") || f.name().equals("use-item-honeycomb") || f.name().equals("use-item-brush") || f.name().equals("use-item-bone-meal"))) continue;
                if (f.name().startsWith("use-item-") && (f.name().endsWith("-bucket") || f.name().equals("use-item-fish-buckets"))) continue; // handled by bucket events
                if (f.name().equals("use-item-food") || f.name().equals("use-item-potion") || f.name().equals("use-item-milk-bucket") || f.name().equals("use-item-chorus-fruit")) continue; // consume event
                if (!pr.can(p, useSet, f)) { denied = f; break; }
            }
            if (denied == null && !pr.bypasses(p)) {
                Set<String> deny = pr.value(p, useSet, Flags.DENY_ITEM_USE);
                Set<String> allow = pr.value(p, useSet, Flags.ALLOW_ITEM_USE);
                if ((deny != null && deny.contains(im.name())) || (allow != null && !allow.isEmpty() && !allow.contains(im.name()))) denied = Flags.INTERACT;
                Set<String> eq = pr.value(p, useSet, Flags.DENY_EQUIP);
                if (eq != null && eq.contains(im.name())) denied = Flags.INTERACT;
            }
            if (denied == null && im == Material.TRIDENT && !pr.can(p, useSet, Flags.RIPTIDE)) {
                if (item.containsEnchantment(org.bukkit.enchantments.Enchantment.RIPTIDE)) denied = Flags.RIPTIDE;
            }
            if (denied != null) {
                e.setUseItemInHand(Event.Result.DENY);
                if (b != null && a == Action.RIGHT_CLICK_BLOCK) plugin.ghost().resync(p, b.getRelative(e.getBlockFace()));
                pr.deny(p, useSet, denied, b == null ? p.getLocation() : b.getLocation());
                p.updateInventory();
            }
        }

        if (a == Action.LEFT_CLICK_BLOCK && b != null) {
            Block rel = b.getRelative(e.getBlockFace());
            if (rel.getType() == Material.FIRE || rel.getType() == Material.SOUL_FIRE) {
                ApplicableSet rs = pr.at(rel);
                if (!pr.check(p, rs, Flags.BLOCK_BREAK, rel.getLocation())) { e.setCancelled(true); plugin.ghost().resync(p, rel); }
            } else if (b.getType() == Material.DRAGON_EGG) {
                for (StateFlag f : FlagFamilies.interactBlock(Material.DRAGON_EGG)) if (!pr.check(p, set, f, b.getLocation())) { e.setCancelled(true); break; }
            }
        }
    }

    // ==================================================================================== entities
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Entity en = e.getRightClicked();
        ApplicableSet set = pr.at(en.getLocation());
        ItemStack hand = p.getInventory().getItem(e.getHand());
        Material im = hand == null ? Material.AIR : hand.getType();
        StateFlag denied = null;
        if (disabledCompletely(p, set, im)) denied = Flags.INTERACT;
        StateFlag fam = FlagFamilies.INTERACT_ENTITY.get(en.getType());
        if (denied == null && fam != null && !pr.can(p, set, fam)) denied = fam;
        if (denied == null && fam == null && !(en instanceof Player) && !pr.can(p, set, Flags.INTERACT)) denied = Flags.INTERACT;
        if (denied == null && listed(p, set, Flags.DENY_ENTITY_INTERACT, en.getType().name())) denied = Flags.INTERACT;
        if (denied == null) {
            if (im == Material.NAME_TAG && !pr.can(p, set, Flags.ENTITY_NAME)) denied = Flags.ENTITY_NAME;
            else if ((im.name().endsWith("_DYE")) && !pr.can(p, set, Flags.ENTITY_DYE)) denied = Flags.ENTITY_DYE;
            else if (en instanceof Animals an && hand != null && an.isBreedItem(hand) && !pr.can(p, set, Flags.ENTITY_FEED)) denied = Flags.ENTITY_FEED;
            else if (en instanceof ItemFrame frame && frame.getItem().getType() != Material.AIR && !pr.can(p, set, Flags.ITEM_FRAME_ROTATE)) denied = Flags.ITEM_FRAME_ROTATE;
        }
        if (denied != null) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) pr.deny(p, set, denied, en.getLocation());
            p.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        ApplicableSet set = pr.at(e.getRightClicked().getLocation());
        if (!pr.check(e.getPlayer(), set, Flags.ARMOR_STAND_MANIPULATE, e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent e) {
        ApplicableSet set = pr.at(e.getEntity().getLocation());
        if (!pr.check(e.getPlayer(), set, Flags.ENTITY_LEASH, e.getEntity().getLocation())) { e.setCancelled(true); e.getPlayer().updateInventory(); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent e) {
        ApplicableSet set = pr.at(e.getEntity().getLocation());
        if (!pr.check(e.getPlayer(), set, Flags.ENTITY_LEASH, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        ApplicableSet set = pr.at(e.getEntity().getLocation());
        if (!pr.check(e.getPlayer(), set, Flags.ENTITY_SHEAR, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent e) {
        ApplicableSet set = pr.at(e.getEntity().getLocation());
        if (!pr.check(e.getPlayer(), set, Flags.ENTITY_BUCKET, e.getEntity().getLocation())) { e.setCancelled(true); e.getPlayer().updateInventory(); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Hanging h = e.getEntity();
        ApplicableSet set = pr.at(h.getLocation());
        Entity remover = e.getRemover();
        if (remover instanceof Projectile pj && pj.getShooter() instanceof Entity sh) remover = sh;
        if (remover instanceof Player p) {
            StateFlag f = FlagFamilies.DAMAGE_ENTITY.get(h.getType());
            if (!pr.check(p, set, f != null ? f : Flags.HANGING_BREAK, h.getLocation())) e.setCancelled(true);
            return;
        }
        StateFlag f = h instanceof ItemFrame ? Flags.ENTITY_ITEM_FRAME_DESTROY : Flags.ENTITY_PAINTING_DESTROY;
        if (!pr.natural(set, f)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreakOther(HangingBreakEvent e) {
        if (e instanceof HangingBreakByEntityEvent) return;
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            ApplicableSet set = pr.at(e.getEntity().getLocation());
            if (!pr.natural(set, Flags.EXPLOSION_BLOCK_DAMAGE) || !pr.natural(set, Flags.OTHER_EXPLOSION)) e.setCancelled(true);
        }
    }

    // ==================================================================================== vehicles
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        Entity at = e.getAttacker();
        if (at instanceof Projectile pj && pj.getShooter() instanceof Entity sh) at = sh;
        if (!(at instanceof Player p)) return;
        ApplicableSet set = pr.at(e.getVehicle().getLocation());
        StateFlag f = FlagFamilies.DAMAGE_ENTITY.get(e.getVehicle().getType());
        if (!pr.check(p, set, f != null ? f : Flags.VEHICLE_DESTROY, e.getVehicle().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (!(e.getEntered() instanceof Player p)) return;
        ApplicableSet set = pr.at(e.getVehicle().getLocation());
        StateFlag f = FlagFamilies.RIDE_ENTITY.get(e.getVehicle().getType());
        if (!pr.check(p, set, f != null ? f : Flags.VEHICLE_ENTER, e.getVehicle().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent e) {
        if (!(e.getExited() instanceof Player p)) return;
        ApplicableSet set = pr.at(e.getVehicle().getLocation());
        if (!pr.check(p, set, Flags.VEHICLE_EXIT, e.getVehicle().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleCollide(VehicleEntityCollisionEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ApplicableSet set = pr.at(e.getVehicle().getLocation());
        if (!pr.can(p, set, Flags.VEHICLE_COLLISION)) e.setCancelled(true);
    }

    // ==================================================================================== inventories
    private static String workbenchToken(InventoryType t) {
        return switch (t.name()) {
            case "ANVIL" -> "ANVIL";
            case "CARTOGRAPHY" -> "CARTOGRAPHY";
            case "WORKBENCH" -> "CRAFT";
            case "ENDER_CHEST" -> "ENDER";
            case "GRINDSTONE" -> "GRINDSTONE";
            case "LOOM" -> "LOOM";
            case "SMITHING" -> "SMITHING";
            case "STONECUTTER" -> "STONECUTTER";
            case "ENCHANTING" -> "ENCHANTING";
            case "BREWING" -> "BREWING";
            case "BEACON" -> "BEACON";
            case "CRAFTER" -> "CRAFTER";
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.PLAYER || t == InventoryType.CRAFTING || t == InventoryType.CREATIVE) return;
        Location l = e.getInventory().getLocation();
        if (l == null || l.getWorld() == null) {
            if (t != InventoryType.ENDER_CHEST && t != InventoryType.MERCHANT && workbenchToken(t) == null) return; // virtual / plugin GUIs
            l = p.getLocation();
        }
        ApplicableSet set = pr.at(l);
        StateFlag f = FlagFamilies.OPEN.get(t);
        StateFlag denied = f != null && !pr.can(p, set, f) ? f : null;
        if (denied == null && t == InventoryType.ENDER_CHEST && !pr.can(p, set, Flags.ENDERCHEST_ACCESS)) denied = Flags.ENDERCHEST_ACCESS;
        if (denied == null && !pr.bypasses(p)) {
            String token = workbenchToken(t);
            Set<String> blocked = pr.value(p, set, Flags.PERMIT_WORKBENCHES);
            if (token != null && blocked != null && !blocked.contains("CLEAR") && (blocked.contains("ALL") || blocked.contains(token))) denied = Flags.USE;
        }
        if (denied != null) {
            e.setCancelled(true);
            pr.deny(p, set, denied, l);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ApplicableSet set = pr.at(p.getLocation());
        InventoryType t = e.getInventory().getType();
        StateFlag f = t == InventoryType.CRAFTING ? Flags.INVENTORY_CRAFT : Flags.WORKBENCH_CRAFT;
        if (!pr.check(p, set, f, p.getLocation())) { e.setCancelled(true); return; }
        if (listed(p, set, Flags.DENY_CRAFT, e.getRecipe().getResult().getType().name())) {
            e.setCancelled(true);
            pr.deny(p, set, f, p.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        ApplicableSet set = pr.at(e.getEnchantBlock());
        if (!pr.check(e.getEnchanter(), set, Flags.ENCHANTING, e.getEnchantBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnvil(PrepareAnvilEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        String rename = e.getView().getRenameText();
        if (rename == null || rename.isEmpty()) return;
        ApplicableSet set = pr.at(p.getLocation());
        if (!pr.can(p, set, Flags.ANVIL_RENAME)) e.setResult(null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSmith(PrepareSmithingEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        if (!pr.can(p, pr.at(p.getLocation()), Flags.SMITHING)) e.setResult(null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        if (!pr.natural(e.getBlock(), Flags.BREWING)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLectern(PlayerTakeLecternBookEvent e) {
        ApplicableSet set = pr.at(e.getLectern().getBlock());
        if (!pr.check(e.getPlayer(), set, Flags.LECTERN_TAKE, e.getLectern().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onStonecutter(PlayerStonecutterRecipeSelectEvent e) {
        Player p = e.getPlayer();
        Location l = e.getStonecutterInventory().getLocation() == null ? p.getLocation() : e.getStonecutterInventory().getLocation();
        if (!pr.check(p, pr.at(l), Flags.STONECUTTER_SELECT, l)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLoom(PlayerLoomPatternSelectEvent e) {
        Player p = e.getPlayer();
        Location l = e.getLoomInventory().getLocation() == null ? p.getLocation() : e.getLoomInventory().getLocation();
        if (!pr.check(p, pr.at(l), Flags.LOOM_SELECT, l)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent e) {
        if (!pr.check(e.getPlayer(), pr.at(e.getPlayer().getLocation()), Flags.BOOK_EDIT, e.getPlayer().getLocation())) e.setCancelled(true);
    }

    // ==================================================================================== items
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        Location l = e.getHook().getLocation();
        if (!pr.check(p, pr.at(l), Flags.FISHING, l)) { e.setCancelled(true); return; }
        if (e.getCaught() != null && e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && !(e.getCaught() instanceof org.bukkit.entity.Item)) {
            ApplicableSet set = pr.at(e.getCaught().getLocation());
            StateFlag f = FlagFamilies.INTERACT_ENTITY.get(e.getCaught().getType());
            if (f != null && !pr.check(p, set, f, e.getCaught().getLocation())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        ApplicableSet set = pr.at(p.getLocation());
        Material m = e.getItem().getType();
        StateFlag denied = pr.can(p, set, Flags.ITEM_CONSUME) ? null : Flags.ITEM_CONSUME;
        if (denied == null) for (StateFlag f : FlagFamilies.useItem(m)) if (!pr.can(p, set, f)) { denied = f; break; }
        if (denied == null && disabledCompletely(p, set, m)) denied = Flags.ITEM_CONSUME;
        if (denied == null && listed(p, set, Flags.DENY_ITEM_USE, m.name())) denied = Flags.ITEM_CONSUME;
        if (denied != null) {
            e.setCancelled(true);
            pr.deny(p, set, denied, p.getLocation());
            p.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerLaunch(PlayerLaunchProjectileEvent e) {
        Player p = e.getPlayer();
        ApplicableSet set = pr.at(p.getLocation());
        Material m = e.getItemStack().getType();
        StateFlag denied = null;
        StateFlag lf = FlagFamilies.LAUNCH.get(e.getProjectile().getType());
        if (lf != null && !pr.can(p, set, lf)) denied = lf;
        if (denied == null) for (StateFlag f : FlagFamilies.useItem(m)) if (!pr.can(p, set, f)) { denied = f; break; }
        if (denied == null && (listed(p, set, Flags.DISABLE_THROW, m.name()) || disabledCompletely(p, set, m))) denied = Flags.INTERACT;
        if (denied == null && e.getProjectile() instanceof ThrownPotion && !pr.can(p, set, Flags.POTION_SPLASH)) denied = Flags.POTION_SPLASH;
        if (denied != null) {
            e.setCancelled(true);
            e.setShouldConsume(false);
            pr.deny(p, set, denied, p.getLocation());
            p.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        Projectile pj = e.getEntity();
        if (pj.getShooter() instanceof Player) return; // handled above / bow event
        StateFlag f = FlagFamilies.LAUNCH.get(pj.getType());
        if (f != null && !pr.natural(pr.at(pj.getLocation()), f)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBow(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            StateFlag f = FlagFamilies.LAUNCH.get(e.getProjectile().getType());
            if (f != null && !pr.natural(pr.at(e.getEntity().getLocation()), f)) e.setCancelled(true);
            return;
        }
        ApplicableSet set = pr.at(p.getLocation());
        StateFlag denied = null;
        StateFlag lf = FlagFamilies.LAUNCH.get(e.getProjectile().getType());
        if (lf != null && !pr.can(p, set, lf)) denied = lf;
        if (denied == null && e.getBow() != null) {
            for (StateFlag f : FlagFamilies.useItem(e.getBow().getType())) if (!pr.can(p, set, f)) { denied = f; break; }
            if (denied == null && disabledCompletely(p, set, e.getBow().getType())) denied = Flags.INTERACT;
        }
        if (denied != null) {
            e.setCancelled(true);
            e.setConsumeItem(false);
            pr.deny(p, set, denied, p.getLocation());
            p.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!pr.check(e.getPlayer(), pr.at(e.getPlayer().getLocation()), Flags.SWAP_HANDS, null)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        ApplicableSet set = pr.at(p.getLocation());
        if (!pr.check(p, set, Flags.ITEM_DROP, p.getLocation())) { e.setCancelled(true); return; }
        if (listed(p, set, Flags.DENY_ITEM_DROPS, e.getItemDrop().getItemStack().getType().name())) {
            e.setCancelled(true);
            pr.deny(p, set, Flags.ITEM_DROP, p.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        ApplicableSet set = pr.at(e.getItem().getLocation());
        if (e.getEntity() instanceof Player p) {
            if (!pr.can(p, set, Flags.ITEM_PICKUP) || listed(p, set, Flags.DENY_ITEM_PICKUP, e.getItem().getItemStack().getType().name())) e.setCancelled(true);
        } else if (!pr.natural(set, Flags.MOB_PICKUP_ITEMS)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExp(PlayerPickupExperienceEvent e) {
        if (!pr.can(e.getPlayer(), pr.at(e.getExperienceOrb().getLocation()), Flags.EXP_PICKUP)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDurability(PlayerItemDamageEvent e) {
        if (!pr.can(e.getPlayer(), pr.at(e.getPlayer().getLocation()), Flags.ITEM_DURABILITY)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ApplicableSet set = pr.at(p.getLocation());
        if (!pr.can(p, set, Flags.TOTEM_USE) || disabledCompletely(p, set, Material.TOTEM_OF_UNDYING)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent e) {
        if (!pr.check(e.getPlayer(), pr.at(e.getBed()), Flags.SLEEP, e.getBed().getLocation())) e.setCancelled(true);
    }
}
