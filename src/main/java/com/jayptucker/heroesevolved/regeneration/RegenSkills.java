package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regeneration's new moves and skill tree. The core's follow-ups live here (Blood Stream, the held Blood Gift; Freefall,
 * Indestructible's leap), with the three branch actives and the passive nodes that need their own hooks. Branches:
 * <ul>
 *   <li><b>Martyr</b> (other people's pain): Shared Blood, Bloodletting, Scar Tissue, Take the Wound → Martyr's Grace →
 *   Martyr.</li>
 *   <li><b>Adaptation</b> (a body that learns): Reckless Abandon, Adrenal Rush, Callus, Adaptation → Thick Skin → Perfect
 *   Adaptation.</li>
 *   <li><b>Recovery</b> (getting back up): Back on Your Feet, Play Dead, Quick Recovery, Revive → Field Medic → Second
 *   Wind.</li>
 * </ul>
 * Node ids are kept from the older tree, so purchases and prerequisites carry over.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class RegenSkills {
    public static final String SHARED_BLOOD = "regeneration_0_0", SCAR_TISSUE = "regeneration_0_2",
            RECKLESS_ABANDON = "regeneration_1_0", CALLUS = "regeneration_1_2",
            BACK_ON_YOUR_FEET = "regeneration_2_0", QUICK_RECOVERY = "regeneration_2_2";

    /** Blood Stream: held this long, a Blood Gift becomes a steady flow, for at most this long. */
    public static final int STREAM_HOLD = 6, STREAM_TICKS = 80;
    /** Play Dead: how long she can lie still, and how long the ambush after getting up lasts. */
    public static final int FEIGN_TICKS = 80, AMBUSH_TICKS = 60;
    public static final float AMBUSH_DAMAGE = 6;
    private static final ResourceLocation FEIGN_SPEED = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "play_dead_speed");

    private record Hold(ServerPlayer owner, long start, int tier) {}
    private record Stream(ServerPlayer owner, LivingEntity target, long start, int tier) {}
    private record Fall(ServerPlayer owner, long start) {}

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Stream> STREAMS = new HashMap<>();
    private static final Map<UUID, Fall> FALLS = new HashMap<>();
    /** Freefall is once per Indestructible: who has already leapt. */
    private static final Map<UUID, Long> LEAPT = new HashMap<>();
    private static final Map<UUID, Long> FEIGNING = new HashMap<>(), AMBUSH = new HashMap<>();

    private RegenSkills() {}

    static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    static boolean has(ServerPlayer p, String node) { return SkillTreeService.has(p, node); }

    private static void push(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
        if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
    }

    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, Entity bound) {
        SetPiecePayload.send(level, piece, from, to, (float) size, bound);
    }

    // ------------------------------------------------------------------
    // Blood Gift: a tap is the gift, a hold is Blood Stream
    // ------------------------------------------------------------------

    /** Blood Gift can start: a friend (or the undead) in reach, or an empty syringe to fill. Says why not, when not. */
    public static boolean canGift(ServerPlayer p) {
        if (holdingSyringe(p) || RegenerationService.findBloodGiftTarget(p) != null) return true;
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("There is no one close enough to share your blood with."), true);
        return false;
    }

    private static boolean holdingSyringe(ServerPlayer p) {
        for (var hand : net.minecraft.world.InteractionHand.values())
            if (p.getItemInHand(hand).is(com.jayptucker.heroesevolved.registry.ModItems.EMPTY_SYRINGE.get())) return true;
        return false;
    }

    public static void pressGift(ServerPlayer p, int tier) { HOLDS.put(p.getUUID(), new Hold(p, now(p), tier)); }

    public static boolean streaming(ServerPlayer p) { return STREAMS.containsKey(p.getUUID()); }

    /** The key came up: a short press was the gift; a held one ends the stream. */
    public static void releaseGift(ServerPlayer p) {
        Hold hold = HOLDS.remove(p.getUUID());
        if (STREAMS.remove(p.getUUID()) != null) { ArmPosePayload.clear(p); return; }
        if (hold != null) RegenerationService.bloodGift(p, hold.tier);
    }

    private static void startStream(Hold hold) {
        ServerPlayer p = hold.owner;
        LivingEntity target = RegenerationService.findBloodGiftTarget(p);
        if (target == null) { HOLDS.remove(p.getUUID()); return; }
        STREAMS.put(p.getUUID(), new Stream(p, target, now(p), hold.tier));
        ArmPosePayload.show(p, ArmPose.REGEN_STREAM, 14);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1, 0.6F);
    }

    /**
     * Blood Stream, every half second while held: the friend heals 2 + tier/2 (+ one per 25 Regrowth) and sheds a toxin,
     * the undead burn for the same, and the cut costs her 1 health and gives her 1 Regrowth. It stops at four seconds, or
     * when the friend leaves her sight.
     */
    private static void tickStream(Stream s, long now) {
        ServerPlayer p = s.owner;
        LivingEntity t = s.target;
        long age = now - s.start;
        if (!t.isAlive() || age > STREAM_TICKS || p.distanceToSqr(t) > 14 * 14 || !p.hasLineOfSight(t) || !PowerRules.canAct(p)) {
            STREAMS.remove(p.getUUID()); HOLDS.remove(p.getUUID()); ArmPosePayload.clear(p); return;
        }
        ServerLevel level = p.serverLevel();
        if (age % 2 == 0) show(level, Piece.REGEN_STREAM, p.position().add(0, 1.1, 0), t.getBoundingBox().getCenter(), 1, p);
        if (age % 10 == 0) ArmPosePayload.show(p, ArmPose.REGEN_STREAM, 14);
        if (age % 10 != 5) return;
        float amount = 2 + s.tier * 0.5F + RegenerationService.regrowth(p) / 25F;
        if (t.isInvertedHealAndHarm()) {
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().indirectMagic(p, p), amount);
        } else {
            t.heal(amount);
            for (Holder<MobEffect> toxin : List.of(MobEffects.POISON, MobEffects.WITHER)) t.removeEffect(toxin);
            RegenerationService.share(p, t);
        }
        p.setHealth(Math.max(1, p.getHealth() - 1));
        RegenerationService.addRegrowth(p, 1);
        level.playSound(null, t.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    // ------------------------------------------------------------------
    // Freefall: Indestructible's follow-up
    // ------------------------------------------------------------------

    /**
     * Freefall, once per Indestructible: she throws herself up and forward the way she'd step off a roof, and comes down
     * on whatever is below. The landing hits everything within 3 + a quarter of the fall (up to 7 blocks) for 4 + the
     * fall (up to 16), throws it back, and feeds her a point of Regrowth per block fallen.
     */
    public static boolean freefall(ServerPlayer p) {
        if (!RegenerationService.isIndestructible(p) || LEAPT.containsKey(p.getUUID()) || FALLS.containsKey(p.getUUID())) return false;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
        push(p, flat.scale(0.9).add(0, 1.25, 0));
        LEAPT.put(p.getUUID(), now(p));
        FALLS.put(p.getUUID(), new Fall(p, now(p)));
        ArmPosePayload.show(p, ArmPose.REGEN_LEAP, 30);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1, 0.6F);
        return true;
    }

    public static boolean falling(ServerPlayer p) { return FALLS.containsKey(p.getUUID()); }

    /** Called on landing (or a timeout): the crash. */
    public static void land(ServerPlayer p, float distance) {
        if (FALLS.remove(p.getUUID()) == null) return;
        ServerLevel level = p.serverLevel();
        double radius = Math.min(7, 3 + distance / 4);
        float damage = Math.min(16, 4 + distance);
        for (LivingEntity t : ExpansionSkills.enemies(p, p.position(), radius)) {
            t.invulnerableTime = 0;
            // A crash, not a punch: it mustn't count as one of Indestructible's reckless blows.
            t.hurt(p.damageSources().explosion(p, p), damage);
            Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
            push(t, (away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize()).scale(0.9).add(0, 0.4, 0));
        }
        RegenerationService.addRegrowth(p, Math.round(distance));
        show(level, Piece.REGEN_IMPACT, p.position(), p.position(), radius, null);
        ArmPosePayload.show(p, ArmPose.REGEN_LAND, 14);
        level.playSound(null, p.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1, 0.8F);
        level.playSound(null, p.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1, 0.6F);
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(level, p.position(), 20, 0.6F + distance / 40, 12);
    }

    // ------------------------------------------------------------------
    // The three branch actives
    // ------------------------------------------------------------------

    public static boolean canUse(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        return switch (branch) {
            case 0 -> p.getHealth() > 3;
            case 2 -> !BodyService.isDowned(p) && !feigning(p);
            default -> true;
        };
    }

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        boolean done = switch (branch) {
            case 0 -> bloodletting(p, c.abilityLevel());
            case 1 -> adrenalRush(p);
            default -> playDead(p);
        };
        if (done) MasteryService.markPowerUse(p, ModAbilities.REGENERATION_ID);
        return done;
    }

    /** Play Dead's follow-up: pressed again while lying still, she gets up (free). */
    public static boolean followUp(AbilityUseContext c, int branch) {
        return branch == 2 && feigning(c.player()) && getUp(c.player());
    }

    /**
     * Bloodletting: a cut palm flung across, and a fan of blood six blocks ahead. Friends it splashes heal 4 + level and
     * shed their harmful effects (each one gives her 4 Regrowth); the undead it splashes burn for the same. Costs 2 health.
     */
    static boolean bloodletting(ServerPlayer p, int tier) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), aim = p.getLookAngle();
        float amount = 4 + tier;
        int healed = 0;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6.5), x -> x != p && x.isAlive())) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > 6.5 || to.normalize().dot(aim) < 0.55 || !p.hasLineOfSight(t)) continue;
            if (t.isInvertedHealAndHarm()) {
                t.invulnerableTime = 0;
                t.hurt(p.damageSources().indirectMagic(p, p), amount);
            } else if (RegenerationService.bonded(p, t)) {
                t.heal(amount);
                for (MobEffectInstance e : List.copyOf(t.getActiveEffects()))
                    if (!e.getEffect().value().isBeneficial()) t.removeEffect(e.getEffect());
                RegenerationService.share(p, t);
                if (++healed <= 4) RegenerationService.addRegrowth(p, 4);
            }
        }
        p.setHealth(Math.max(1, p.getHealth() - 2));
        show(level, Piece.REGEN_FAN, eye.add(0, -0.3, 0), eye.add(aim.scale(6)), 1, p);
        ArmPosePayload.show(p, ArmPose.REGEN_FLING, 12);
        level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.playSound(null, p.blockPosition(), SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1, 0.7F);
        return true;
    }

    /**
     * Adrenal Rush: spend up to 40 Regrowth. She heals 4, plus one per 5 spent, and runs on Speed II, Haste and Jump
     * Boost for 8 seconds.
     */
    static boolean adrenalRush(ServerPlayer p) {
        int spent = RegenerationService.spendRegrowth(p, 40);
        p.heal(4 + spent / 5F);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 160, 0));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, 160, 1));
        ArmPosePayload.show(p, ArmPose.REGEN_PUMP, 14);
        com.jayptucker.heroesevolved.effects.AbilityEffects.send(p.serverLevel(),
                com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.REGEN_MEND, p.position(), p.position().add(0, 1, 0), 0.8 + spent / 50.0);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 1.6F);
        return true;
    }

    /**
     * Play Dead: she drops where she stands as if dead, for up to 4 seconds. Monsters within 16 blocks lose her and can't
     * pick her again while she lies there, and she heals 1 every half second. Getting up (pressing again, or when it runs
     * out) sets up an ambush: her next melee hit within 3 seconds lands 6 harder.
     */
    static boolean playDead(ServerPlayer p) {
        FEIGNING.put(p.getUUID(), now(p) + FEIGN_TICKS);
        p.setForcedPose(Pose.SLEEPING);
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(FEIGN_SPEED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        for (Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
            mob.setTarget(null);
            if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getNavigation().stop();
        }
        show(p.serverLevel(), Piece.REGEN_FEIGN, p.position(), p.position(), 1, p);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1, 0.7F);
        BodyService.sync(p);
        return true;
    }

    public static boolean feigning(ServerPlayer p) { return FEIGNING.containsKey(p.getUUID()); }

    public static boolean ambushing(ServerPlayer p) { return AMBUSH.containsKey(p.getUUID()); }

    static boolean getUp(ServerPlayer p) {
        if (FEIGNING.remove(p.getUUID()) == null) return false;
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(FEIGN_SPEED);
        if (!BodyService.isDowned(p)) p.setForcedPose(null);
        BodyService.sync(p);
        AMBUSH.put(p.getUUID(), now(p) + AMBUSH_TICKS);
        ArmPosePayload.show(p, ArmPose.REGEN_RISE, 12);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1, 1.4F);
        return true;
    }

    // ------------------------------------------------------------------
    // Getting back up from being down (Back on Your Feet, Quick Recovery)
    // ------------------------------------------------------------------

    /** Quick Recovery: a third less time on the ground. */
    public static int recovery(ServerPlayer p, int ticks) { return has(p, QUICK_RECOVERY) ? ticks * 2 / 3 : ticks; }

    /** Called when she gets back up on her own (not by giving up). */
    static void stoodUp(ServerPlayer p) {
        if (has(p, QUICK_RECOVERY))
            com.jayptucker.heroesevolved.cooldown.CooldownService.startCooldown(p,
                    ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "regeneration_rapid_mending"), 0);
        if (!has(p, BACK_ON_YOUR_FEET)) return;
        for (LivingEntity t : ExpansionSkills.enemies(p, p.position(), 5)) {
            Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
            push(t, (away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize()).scale(1.1).add(0, 0.45, 0));
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
        show(p.serverLevel(), Piece.REGEN_IMPACT, p.position(), p.position(), 5, null);
    }

    // ------------------------------------------------------------------

    private static boolean regenerator(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getValue().isUnlocked() && e.getKey().equals(ModAbilities.REGENERATION_ID)).isPresent();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        long now = e.getServer().overworld().getGameTime();
        for (Hold hold : List.copyOf(HOLDS.values())) {
            if (hold.owner.isRemoved() || !regenerator(hold.owner)) { HOLDS.remove(hold.owner.getUUID()); continue; }
            if (!streaming(hold.owner) && now - hold.start >= STREAM_HOLD) startStream(hold);
        }
        for (Stream s : List.copyOf(STREAMS.values())) tickStream(s, now);
        for (Fall f : List.copyOf(FALLS.values())) {
            ServerPlayer p = f.owner;
            if (p.isRemoved()) { FALLS.remove(p.getUUID()); continue; }
            long age = now - f.start;
            // Landing is caught by the fall event; this catches a landing with too short a drop for one, and a timeout.
            if (age > 4 && (p.onGround() || p.isInWater()) || age > 100) land(p, Math.max(2, p.fallDistance));
            else if (age % 6 == 0) ArmPosePayload.show(p, ArmPose.REGEN_LEAP, 12);
        }
        LEAPT.entrySet().removeIf(x -> {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(x.getKey());
            return p == null || !RegenerationService.isIndestructible(p) && !falling(p);
        });
        for (var entry : List.copyOf(FEIGNING.entrySet())) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(entry.getKey());
            if (p == null) { FEIGNING.remove(entry.getKey()); continue; }
            long left = entry.getValue() - now;
            if (left <= 0 || !p.isAlive() || BodyService.isDowned(p)) { getUp(p); continue; }
            if (left % 10 == 0) {
                p.heal(1);
                show(p.serverLevel(), Piece.REGEN_FEIGN, p.position(), p.position(), left / (float) FEIGN_TICKS, p);
            }
        }
        AMBUSH.values().removeIf(until -> until < now);
    }

    @SubscribeEvent
    public static void target(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() instanceof ServerPlayer p && feigning(p)) e.setCanceled(true);
    }

    /** She can't swing while lying still; trying gets her up. */
    @SubscribeEvent
    public static void attack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && feigning(p)) { getUp(p); e.setCanceled(true); }
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent e) {
        // The ambush after Play Dead: her next melee hit lands harder.
        if (e.getSource().getEntity() instanceof ServerPlayer p && e.getSource().getDirectEntity() == p
                && AMBUSH.remove(p.getUUID()) != null) {
            e.setAmount(e.getAmount() + AMBUSH_DAMAGE);
            show(p.serverLevel(), Piece.REGEN_IMPACT, e.getEntity().position(), e.getEntity().position(), 1.5, null);
        }
    }

    public static void forget(ServerPlayer p) {
        UUID id = p.getUUID();
        HOLDS.remove(id); STREAMS.remove(id); FALLS.remove(id); LEAPT.remove(id); AMBUSH.remove(id);
        if (FEIGNING.remove(id) != null) {
            var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(FEIGN_SPEED);
            if (!BodyService.isDowned(p)) p.setForcedPose(null);
            BodyService.sync(p);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        HOLDS.clear(); STREAMS.clear(); FALLS.clear(); LEAPT.clear(); FEIGNING.clear(); AMBUSH.clear();
    }
}
