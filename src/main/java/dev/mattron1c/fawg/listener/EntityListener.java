package dev.mattron1c.fawg.listener;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.flags.FlagFamilies;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.protect.Protection;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.potion.PotionEffect;

import java.util.Set;

public final class EntityListener implements Listener {
    private static final Set<String> NEUTRAL = Set.of("BEE", "DOLPHIN", "ENDERMAN", "GOAT", "IRON_GOLEM", "LLAMA", "TRADER_LLAMA", "PANDA", "PIGLIN",
            "POLAR_BEAR", "WOLF", "ZOMBIFIED_PIGLIN", "SPIDER", "CAVE_SPIDER", "FOX", "PUFFERFISH");

    private final FAWG plugin;
    private final Protection pr;

    public EntityListener(FAWG plugin) {
        this.plugin = plugin;
        this.pr = plugin.protection();
    }

    /** Resolves the real attacker (projectile shooter, TNT igniter, cloud source...). */
    public static Entity source(Entity damager) {
        if (damager instanceof Projectile p && p.getShooter() instanceof Entity s) return s;
        if (damager instanceof TNTPrimed t && t.getSource() != null) return t.getSource();
        if (damager instanceof AreaEffectCloud c && c.getSource() instanceof Entity s) return s;
        if (damager instanceof Firework f && f.getShooter() instanceof Entity s) return s;
        return damager;
    }

    // ===================================================================================== spawning
    private static boolean category(Set<String> list, EntityType t) {
        if (list == null || list.isEmpty()) return false;
        if (list.contains(t.name()) || list.contains("ALL")) return true;
        Class<?> c = t.getEntityClass();
        boolean hostile = c != null && Enemy.class.isAssignableFrom(c);
        boolean neutral = NEUTRAL.contains(t.name());
        if ((list.contains("ALL_HOSTILE") || list.contains("HOSTILE") || list.contains("MONSTERS")) && hostile && !neutral) return true;
        if (list.contains("ALL_NEUTRAL") && neutral) return true;
        return (list.contains("ALL_PEACEFUL") || list.contains("PASSIVE") || list.contains("ANIMALS")) && !hostile && !neutral;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        LivingEntity en = e.getEntity();
        ApplicableSet set = pr.at(e.getLocation());
        CreatureSpawnEvent.SpawnReason reason = e.getSpawnReason();
        StateFlag rf = FlagFamilies.SPAWN_REASON.get(reason);
        if (rf != null && !pr.natural(set, rf)) { e.setCancelled(true); return; }
        boolean pluginSpawn = reason == CreatureSpawnEvent.SpawnReason.CUSTOM || reason == CreatureSpawnEvent.SpawnReason.COMMAND || reason == CreatureSpawnEvent.SpawnReason.DEFAULT;
        if (pluginSpawn && !plugin.getConfig().getBoolean("protection.block-plugin-spawns", false)) return;
        if (category(pr.value(set, Flags.DENY_SPAWN), en.getType()) || category(pr.value(set, Flags.DENY_MOBSPAWN), en.getType())) { e.setCancelled(true); return; }
        StateFlag f = FlagFamilies.SPAWN.get(en.getType());
        if (f != null && !pr.natural(set, f)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSlimeSplit(SlimeSplitEvent e) {
        if (!pr.natural(pr.at(e.getEntity().getLocation()), Flags.SLIME_SPLIT)) e.setCancelled(true);
    }

    // ===================================================================================== damage
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        ApplicableSet set = pr.at(victim.getLocation());
        EntityDamageEvent.DamageCause cause = e.getCause();
        Entity damager = e instanceof EntityDamageByEntityEvent be ? be.getDamager() : null;
        Entity attacker = damager == null ? null : source(damager);

        if (victim instanceof Player p) {
            if (pr.enabledFor(p, set, Flags.INVINCIBILITY) || pr.enabledFor(p, set, Flags.GODMODE)) { e.setCancelled(true); fireCheck(p); return; }
            StateFlag cf = FlagFamilies.PLAYER_DAMAGE_CAUSE.get(cause);
            if (cf != null && !pr.allowsFor(p, set, cf)) { e.setCancelled(true); return; }
            if (cause == EntityDamageEvent.DamageCause.FALL && !pr.allowsFor(p, set, Flags.FALL_DAMAGE)) { e.setCancelled(true); return; }
            if (cause == EntityDamageEvent.DamageCause.LIGHTNING && !pr.allowsFor(p, set, Flags.LIGHTNING_DAMAGE)) { e.setCancelled(true); return; }
            if ((cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) && !pr.allowsFor(p, set, Flags.EXPLOSION_ENTITY_DAMAGE)) { e.setCancelled(true); return; }
            if (attacker == null) return;
            if (attacker instanceof Player a) {
                if (a.equals(p)) return;
                ApplicableSet aset = pr.at(a.getLocation());
                if (!pr.allowsFor(a, set, Flags.PVP) || !pr.allowsFor(a, aset, Flags.PVP) || !pr.allowsFor(p, set, Flags.PVP)) {
                    e.setCancelled(true);
                    if (damager instanceof Projectile pj) pj.remove();
                    pr.deny(a, set, Flags.PVP, victim.getLocation());
                }
                return;
            }
            if (damager instanceof Firework && !pr.natural(set, Flags.FIREWORK_DAMAGE)) { e.setCancelled(true); return; }
            if (attacker instanceof LivingEntity) {
                StateFlag f = FlagFamilies.DAMAGE_FROM.get(attacker.getType());
                if (!pr.allowsFor(p, set, f != null ? f : Flags.MOB_DAMAGE)) e.setCancelled(true);
            }
            return;
        }

        // ----- non-player victims
        if (victim instanceof LivingEntity && attacker == null) {
            StateFlag cf = FlagFamilies.ENTITY_DAMAGE_CAUSE.get(cause);
            if (cf != null && !pr.natural(set, cf)) { e.setCancelled(true); return; }
        }
        if ((cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            if (!pr.natural(set, Flags.EXPLOSION_ENTITY_DAMAGE)) { e.setCancelled(true); return; }
            if (!(victim instanceof LivingEntity) || victim.getType() == EntityType.ARMOR_STAND) {
                if (!pr.natural(set, Flags.EXPLOSION_BLOCK_DAMAGE)) { e.setCancelled(true); return; }
            }
        }
        if (attacker instanceof Player a) {
            StateFlag f = FlagFamilies.DAMAGE_ENTITY.get(victim.getType());
            StateFlag denied = f != null && !pr.can(a, set, f) ? f : null;
            if (denied == null && damager instanceof Firework && !pr.can(a, set, Flags.FIREWORK_DAMAGE)) denied = Flags.FIREWORK_DAMAGE;
            if (denied == null && !pr.bypasses(a)) {
                Set<String> list = pr.value(a, set, Flags.DENY_ENTITY_DAMAGE);
                if (category(list, victim.getType())) denied = Flags.DAMAGE_ANIMALS;
            }
            if (denied != null) {
                e.setCancelled(true);
                if (damager instanceof Projectile pj && !(pj instanceof org.bukkit.entity.Trident)) pj.remove();
                pr.deny(a, set, denied, victim.getLocation());
            }
            return;
        }
        if (attacker != null && (victim.getType() == EntityType.ITEM_FRAME || victim.getType() == EntityType.GLOW_ITEM_FRAME)) {
            if (!pr.natural(set, Flags.ENTITY_ITEM_FRAME_DESTROY)) e.setCancelled(true);
        } else if (attacker != null && victim.getType() == EntityType.ARMOR_STAND && !(attacker instanceof Player)) {
            if (!pr.natural(set, Flags.ENTITY_PAINTING_DESTROY)) e.setCancelled(true);
        }
    }

    private void fireCheck(Player p) {
        if (p.getFireTicks() > 0) p.setFireTicks(0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        Entity thrower = e.getPotion().getShooter() instanceof Entity s ? s : null;
        boolean harmful = false;
        for (PotionEffect pe : e.getPotion().getEffects()) {
            String k = pe.getType().getKey().getKey();
            if (k.equals("poison") || k.equals("instant_damage") || k.equals("wither") || k.equals("slowness") || k.equals("weakness") || k.equals("mining_fatigue") || k.equals("blindness") || k.equals("nausea") || k.equals("hunger") || k.equals("levitation") || k.equals("darkness") || k.equals("infested") || k.equals("oozing") || k.equals("weaving") || k.equals("wind_charged")) { harmful = true; break; }
        }
        for (LivingEntity le : e.getAffectedEntities()) {
            ApplicableSet set = pr.at(le.getLocation());
            boolean deny = !pr.natural(set, Flags.POTION_SPLASH);
            if (!deny && harmful && le instanceof Player victim && thrower instanceof Player a && !a.equals(victim)) {
                deny = !pr.allowsFor(a, set, Flags.PVP);
            }
            if (!deny && thrower instanceof Player a && !(le instanceof Player)) {
                StateFlag f = FlagFamilies.DAMAGE_ENTITY.get(le.getType());
                deny = harmful && f != null && !pr.can(a, set, f);
            }
            if (deny) e.setIntensity(le, 0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent e) {
        Entity thrower = e.getEntity().getSource() instanceof Entity s ? s : null;
        e.getAffectedEntities().removeIf(le -> {
            ApplicableSet set = pr.at(le.getLocation());
            if (!pr.natural(set, Flags.POTION_SPLASH)) return true;
            return le instanceof Player victim && thrower instanceof Player a && !a.equals(victim) && !pr.allowsFor(a, set, Flags.PVP);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED && e.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        if (e.getNewEffect() == null || e.getCause() == EntityPotionEffectEvent.Cause.PLUGIN && e.getNewEffect().getDuration() > 0 && plugin.effects().isOurs(p, e.getNewEffect())) return;
        ApplicableSet set = pr.at(p.getLocation());
        String key = e.getNewEffect().getType().getKey().getKey();
        StateFlag f = FlagFamilies.EFFECT.get(key);
        if (f != null && !pr.allowsFor(p, set, f)) { e.setCancelled(true); return; }
        Set<String> blocked = pr.value(p, set, Flags.BLOCKED_EFFECTS);
        if (blocked != null) for (String b : blocked) if (b.split(":")[0].equals(key)) { e.setCancelled(true); return; }
    }

    // ===================================================================================== mobs
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;
        ApplicableSet set = pr.at(p.getLocation());
        StateFlag f = FlagFamilies.TARGET.get(e.getEntity().getType());
        if (!pr.allowsFor(p, set, f != null ? f : Flags.MOB_TARGET_PLAYERS)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        StateFlag f = FlagFamilies.TRANSFORM.get(e.getTransformReason());
        if (f != null && !pr.natural(pr.at(e.getEntity().getLocation()), f)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        ApplicableSet set = pr.at(e.getEntity().getLocation());
        if (!pr.natural(set, Flags.ENTITY_BREED)) { e.setCancelled(true); return; }
        if (e.getBreeder() instanceof Player p && !pr.can(p, set, Flags.ENTITY_FEED)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        ApplicableSet set = pr.at(e.getEntity().getLocation());
        if (e.getOwner() instanceof Player p) { if (!pr.check(p, set, Flags.ENTITY_TAME, e.getEntity().getLocation())) e.setCancelled(true); }
        else if (!pr.natural(set, Flags.ENTITY_TAME)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (!pr.natural(pr.at(e.getFrom()), Flags.ENTITY_PORTALS)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent e) {
        if (e.getTo() == null) return;
        if (e.getEntity() instanceof Tameable t && t.isTamed()) {
            if (!pr.natural(pr.at(e.getTo()), Flags.PET_TELEPORT)) e.setCancelled(true);
        } else if (e.getEntity().getType() == EntityType.ENDERMAN || e.getEntity().getType() == EntityType.SHULKER) {
            if (!pr.natural(pr.at(e.getTo()), Flags.TELEPORT_IN)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreeperPower(CreeperPowerEvent e) {
        if (!pr.natural(pr.at(e.getEntity().getLocation()), Flags.CREEPER_CHARGE)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRegrow(SheepRegrowWoolEvent e) {
        if (!pr.natural(pr.at(e.getEntity().getLocation()), Flags.SHEEP_REGROW)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent e) {
        if (e.getEntity() instanceof Chicken && e.getItemDrop().getItemStack().getType().name().endsWith("EGG")) {
            if (!pr.natural(pr.at(e.getEntity().getLocation()), Flags.CHICKEN_EGGS)) e.setCancelled(true);
        } else if (e.getEntity() instanceof org.bukkit.entity.ItemFrame) {
            if (!pr.natural(pr.at(e.getEntity().getLocation()), Flags.ITEM_FRAME_DROP)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent e) {
        if (!pr.natural(pr.at(e.getLocation()), Flags.ITEM_DESPAWN)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRaid(RaidTriggerEvent e) {
        ApplicableSet set = pr.at(e.getPlayer().getLocation());
        if (!pr.allowsFor(e.getPlayer(), set, Flags.RAID_TRIGGER)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        ApplicableSet set = pr.at(b);
        if (m == Material.FARMLAND) { if (!pr.natural(set, Flags.BLOCK_TRAMPLING)) e.setCancelled(true); }
        else if (m == Material.TURTLE_EGG || m == Material.SNIFFER_EGG) { if (!pr.natural(set, Flags.TURTLE_EGG_TRAMPLE)) e.setCancelled(true); }
        else if (m.name().endsWith("_PRESSURE_PLATE") || m == Material.TRIPWIRE) {
            Entity en = e.getEntity();
            if (en instanceof Projectile pj && pj.getShooter() instanceof Player p) { if (!pr.can(p, set, Flags.USE)) e.setCancelled(true); }
            else if (!pr.natural(set, Flags.REDSTONE_SIGNALS)) e.setCancelled(true);
        } else if (m == Material.BIG_DRIPLEAF) {
            if (e.getEntity() instanceof Projectile pj && pj.getShooter() instanceof Player p && !pr.can(p, set, Flags.USE_DRIPLEAF)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        if (!pr.natural(pr.at(e.getEntity().getLocation()), Flags.EXP_DROPS)) e.setDroppedExp(0);
    }
}
