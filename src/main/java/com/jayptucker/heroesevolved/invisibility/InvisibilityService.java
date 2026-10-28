package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.UnseenPayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invisibility, inspired by Claude Rains: unseen by eyes and by mobs, but not by every sense.
 * Fully unseen players are hidden on other clients (armor, held items and name tag too) and
 * mobs farther than two blocks cannot target them. Taking damage flickers you visible for a
 * second; Echo Sense, Tremor Sense and a telepath's Memory Walk reveal you for longer.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class InvisibilityService {
    /** {@code drains} is false for borrowed or timed invisibility, such as Shroud. */
    private record Fade(ServerPlayer player, long until, boolean drains) {}
    private record Cloak(ServerPlayer owner, ServerLevel level, Vec3 center, double radius, long until) {}
    private static final Map<UUID, Fade> FADES = new HashMap<>();
    private static final Map<UUID, Long> REVEALED = new HashMap<>();
    private static final Map<UUID, Long> STRIKES = new HashMap<>();
    private static final Map<UUID, Cloak> CLOAKS = new HashMap<>();
    private static final Map<UUID, Boolean> SYNCED = new HashMap<>();
    private static final long FOREVER = Long.MAX_VALUE;

    private InvisibilityService() {}

    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }

    public static boolean isInvisibilityOwner(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.INVISIBILITY_ID) && e.getValue().isUnlocked()).isPresent()
                && !EclipseService.arePowersSuppressed(player);
    }

    public static boolean isFaded(ServerPlayer player) { return FADES.containsKey(player.getUUID()); }

    /** Faded and not currently revealed. */
    public static boolean isUnseen(ServerPlayer player) {
        return isFaded(player) && REVEALED.getOrDefault(player.getUUID(), 0L) <= now(player.getServer());
    }

    /** Makes {@code player} unseen for {@code ticks}, or until ended when {@code drains}. */
    public static void fade(ServerPlayer player, int ticks, boolean drains) {
        long until = drains ? FOREVER : now(player.getServer()) + ticks;
        Fade existing = FADES.get(player.getUUID());
        if (existing != null && existing.drains && !drains) return; // a toggled fade outlasts a timed one
        FADES.put(player.getUUID(), new Fade(player, existing == null || drains ? until : Math.max(until, existing.until), drains));
        loseTargets(player, 32);
        AbilityEffects.send(player.serverLevel(), Kind.MIND_PULSE, player.position(), player.getEyePosition(), 1.2);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.6F, 1.4F);
        sync(player);
    }

    public static boolean unfade(ServerPlayer player) {
        if (FADES.remove(player.getUUID()) == null) return false;
        player.removeEffect(MobEffects.INVISIBILITY);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.5F, 1.2F);
        sync(player);
        return true;
    }

    /** Senses that pierce invisibility: the target shows for {@code ticks}. */
    public static void reveal(ServerPlayer player, int ticks) {
        if (!isFaded(player)) return;
        REVEALED.merge(player.getUUID(), now(player.getServer()) + ticks, Math::max);
        sync(player);
    }

    /** Reveals every unseen player within {@code radius} of {@code center}. Used by sensing powers. */
    public static void revealAround(ServerLevel level, Vec3 center, double radius, int ticks) {
        for (ServerPlayer player : level.players())
            if (player.position().distanceToSqr(center) <= radius * radius) reveal(player, ticks);
    }

    private static void sync(ServerPlayer player) {
        boolean hidden = isUnseen(player);
        if (SYNCED.getOrDefault(player.getUUID(), false) == hidden) return;
        SYNCED.put(player.getUUID(), hidden);
        if (hidden) player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new UnseenPayload(player.getId(), hidden));
    }

    private static void loseTargets(ServerPlayer player, double radius) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                mob -> mob.getTarget() == player)) mob.setTarget(null);
    }

    // ------------------------------------------------------------------
    // Core actions
    // ------------------------------------------------------------------

    /** Level 1, Fade: a toggle; pressing again (or Shift) ends it. */
    public static boolean toggleFade(ServerPlayer player) {
        if (unfade(player)) return true;
        fade(player, 0, true);
        player.displayClientMessage(Component.translatable("invisibility.heroesevolved.faded").withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    /** Level 1, Silent Step: a collision-checked dash that does not break invisibility. */
    public static boolean silentStep(ServerPlayer player, int level) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 end = SpeedService.swept(player, look.scale(6 + level * 2));
        if (end.distanceToSqr(player.position()) < 1) return false;
        player.teleportTo(end.x, end.y, end.z);
        player.resetFallDistance();
        if (!isUnseen(player)) AbilityEffects.send(player.serverLevel(), Kind.PSYCHIC_PUSH, player.position(), end, 0.6);
        return true;
    }

    /** Level 2, Unseen Strike: the next melee hit within ten seconds lands with surprise. */
    public static boolean armStrike(ServerPlayer player) {
        STRIKES.put(player.getUUID(), now(player.getServer()) + 200);
        player.displayClientMessage(Component.translatable("invisibility.heroesevolved.strike_ready").withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    /** Level 3, Shroud: share the unseen with the nearest friend, or deepen your own. */
    public static boolean shroud(ServerPlayer player) {
        ServerPlayer friend = player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(4),
                        other -> other != player && ExpansionSkills.friendly(player, other))
                .stream().findFirst().orElse(player);
        fade(friend, 20 * 15, false);
        if (friend != player) friend.displayClientMessage(Component.translatable("invisibility.heroesevolved.shrouded", player.getName()), true);
        return true;
    }

    /** Level 5, Ghost Protocol: you and nearby friends vanish; every hunting mob loses the trail. */
    public static boolean ghostProtocol(ServerPlayer player) {
        for (ServerPlayer friend : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(6),
                other -> other == player || ExpansionSkills.friendly(player, other))) fade(friend, 160, false);
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24))) {
            if (mob.getTarget() instanceof ServerPlayer target && FADES.containsKey(target.getUUID())) mob.setTarget(null);
        }
        for (ServerPlayer enemy : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(16),
                other -> ExpansionSkills.hostile(player, other))) enemy.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80));
        AbilityEffects.send(player.serverLevel(), Kind.PSYCHIC_FIELD, player.position(), player.getEyePosition(), 6);
        return true;
    }

    /** First awakening: you blink out of sight for a few seconds without meaning to. */
    public static void awaken(ServerPlayer player) {
        fade(player, 100, false);
        player.displayClientMessage(Component.translatable("invisibility.heroesevolved.awakened").withStyle(ChatFormatting.GRAY), false);
    }

    // ------------------------------------------------------------------
    // Skill tree
    // ------------------------------------------------------------------

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        switch (branch) {
            case 0 -> {
                LivingEntity target = Targeting.livingEntityInSight(p, 40, t -> ExpansionSkills.hostile(p, t));
                if (target == null) return false;
                target.hurt(p.damageSources().playerAttack(p), 6 + c.abilityLevel());
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80));
                AbilityEffects.send(p.serverLevel(), Kind.PSYCHIC_IMPACT, p.getEyePosition(), target.getEyePosition(), 1);
            }
            case 1 -> { p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0)); fade(p, 120, false); }
            case 2 -> { p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1)); fade(p, 100, false); }
            default -> { return false; }
        }
        return true;
    }

    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        switch (mechanic) {
            case "smoke_and_mirrors" -> {
                boolean lingering = SkillTreeService.has(p, "invisibility_lingering_mirage");
                double radius = lingering ? 18 : 12;
                for (Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(radius),
                        mob -> ExpansionSkills.hostile(p, mob))) {
                    mob.setTarget(null);
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, lingering ? 120 : 60, 1));
                }
                AbilityEffects.send(p.serverLevel(), Kind.PSYCHIC_FIELD, p.position(), p.getEyePosition(), radius / 2);
                return true;
            }
            case "pickpocket" -> {
                double reach = SkillTreeService.has(p, "invisibility_light_fingers") ? 8 : 4;
                LivingEntity target = Targeting.livingEntityInSight(p, reach, t -> t instanceof Mob && !t.getMainHandItem().isEmpty());
                if (!(target instanceof Mob mob)) {
                    p.displayClientMessage(Component.translatable("invisibility.heroesevolved.nothing_to_take"), true);
                    return false;
                }
                ItemStack taken = mob.getMainHandItem().copy();
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (!p.getInventory().add(taken)) p.drop(taken, false);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.6F);
                return true;
            }
            case "cloak_field" -> {
                double radius = SkillTreeService.has(p, "invisibility_wider_cloak") ? 6 : 4;
                CLOAKS.put(p.getUUID(), new Cloak(p, p.serverLevel(), p.position(), radius, now(p.getServer()) + 300));
                AbilityEffects.send(p.serverLevel(), Kind.PSYCHIC_FIELD, p.position(), p.getEyePosition(), radius);
                return true;
            }
            default -> { return false; }
        }
    }

    // ------------------------------------------------------------------
    // Events and tick
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = now(server);
        for (Fade fade : List.copyOf(FADES.values())) {
            ServerPlayer player = fade.player;
            if (!player.isAlive() || player.isRemoved() || now >= fade.until) { unfade(player); continue; }
            if (fade.drains) {
                if (!isInvisibilityOwner(player)) { unfade(player); continue; }
                boolean frugal = SkillTreeService.has(player, "invisibility_claudes_lesson");
                if (now % (frugal ? 20 : 10) == 0 && !PlayerEnergyService.tryConsume(player, 1)) {
                    unfade(player);
                    player.displayClientMessage(Component.translatable("invisibility.heroesevolved.exhausted"), true);
                    continue;
                }
            }
            if (now % 40 == 0 && isUnseen(player)) player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
            sync(player);
        }
        REVEALED.values().removeIf(until -> until <= now);
        STRIKES.values().removeIf(until -> until <= now);
        CLOAKS.values().removeIf(cloak -> now >= cloak.until || !cloak.owner.isAlive());
        if (now % 10 == 0) for (Cloak cloak : List.copyOf(CLOAKS.values()))
            for (ServerPlayer friend : cloak.level.getEntitiesOfClass(ServerPlayer.class, new AABB(cloak.center, cloak.center).inflate(cloak.radius),
                    other -> other == cloak.owner || ExpansionSkills.friendly(cloak.owner, other))) fade(friend, 30, false);
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    /** Mobs cannot pick out an unseen player beyond arm's reach. */
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && isUnseen(player)
                && event.getEntity().distanceToSqr(player) > 4) event.setCanceled(true);
    }

    /** Even when visible, an invisibility owner is harder to notice. */
    @SubscribeEvent public static void visibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInvisibilityOwner(player))
            event.modifyVisibility(isUnseen(player) ? 0.05 : 0.6);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void damage(LivingIncomingDamageEvent event) {
        // A surprise strike from an armed attacker.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getSource().getDirectEntity() == attacker
                && event.getSource().is(DamageTypes.PLAYER_ATTACK) && STRIKES.remove(attacker.getUUID()) != null) {
            float multiplier = isUnseen(attacker) ? (SkillTreeService.has(attacker, "invisibility_assassin") ? 3F : 1.5F) : 1F;
            int level = com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(attacker);
            event.setAmount((event.getAmount() + 3 + level) * multiplier);
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
        }
        if (event.getEntity() instanceof ServerPlayer victim && isFaded(victim)) {
            if (SkillTreeService.has(victim, "invisibility_phantom") && isUnseen(victim)) event.setAmount(event.getAmount() * 0.7F);
            reveal(victim, 20);
        }
    }

    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer && isUnseen(target))
            PacketDistributor.sendToPlayer(viewer, new UnseenPayload(target.getId(), true));
    }

    public static void forget(ServerPlayer player) {
        unfade(player);
        STRIKES.remove(player.getUUID());
        CLOAKS.remove(player.getUUID());
        REVEALED.remove(player.getUUID());
        SYNCED.remove(player.getUUID());
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        FADES.clear(); REVEALED.clear(); STRIKES.clear(); CLOAKS.clear(); SYNCED.clear();
    }

    /** Players currently fading, for tests and sensing powers. */
    public static List<ServerPlayer> faded() {
        List<ServerPlayer> list = new ArrayList<>();
        for (Fade fade : FADES.values()) list.add(fade.player);
        return list;
    }
}
