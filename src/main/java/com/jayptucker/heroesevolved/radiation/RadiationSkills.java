package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.ability.AbilityRanges;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Induced Radioactivity's skill tree, built around the kit rather than stat bonuses. Three branches:
 * <ul>
 *   <li><b>Meltdown</b> (heat and the beam): Searing Palms, Gamma Lance, Focused Beam, Heat Vent → Chain Vent →
 *   Supercritical.</li>
 *   <li><b>Reactor</b> (running Core): Hot Start, Lead Skin, Safe Venting, Reactor Core → Power Transfer →
 *   Breeder Reactor.</li>
 *   <li><b>Fallout</b> (the leap and contaminated ground): Fallout Boots, Decontaminate, Second Leap, Glowing Path →
 *   Half-Life → Catastrophe.</li>
 * </ul>
 * Node ids are kept from the older tree, so purchases and prerequisites carry over.
 */
public final class RadiationSkills {
    // Meltdown
    public static final String SEARING_PALMS = "radiation_0_0", FOCUSED_BEAM = "radiation_0_2",
            CHAIN_VENT = "radiation_clean_vent", SUPERCRITICAL = "radiation_containment";
    // Reactor
    public static final String HOT_START = "radiation_1_0", SAFE_VENTING = "radiation_1_2",
            POWER_TRANSFER = "radiation_power_transfer", BREEDER = "radiation_reactor";
    // Fallout
    public static final String FALLOUT_BOOTS = "radiation_2_0", SECOND_LEAP = "radiation_2_2",
            HALF_LIFE = "radiation_wider_path", CATASTROPHE = "radiation_catastrophe";

    static final int LEAD_SKIN_TICKS = 160, GLOWING_PATH_TICKS = 160, OVERDRIVE_TICKS = 200, OVERDRIVE_COOLDOWN = 1200;
    private static final Map<UUID, Long> LEAD_SKIN = new HashMap<>(), PATHS = new HashMap<>(), OVERDRIVE = new HashMap<>(),
            OVERDRIVE_READY = new HashMap<>();

    private RadiationSkills() {}

    public static boolean has(ServerPlayer p, String id) { return SkillTreeService.has(p, id); }

    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    // ------------------------------------------------------------------
    // The three branch actives (Gamma Lance, Lead Skin, Decontaminate)
    // ------------------------------------------------------------------

    public static boolean canUse(AbilityUseContext c, int branch) {
        return !c.player().isSpectator();
    }

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        return switch (branch) {
            case 0 -> gammaLance(p, c.abilityLevel());
            case 1 -> leadSkin(p);
            default -> decontaminate(p);
        };
    }

    /**
     * Gamma Lance: a narrow lance of gamma light up to 40 blocks, through everything on the line: 8 + level damage
     * and a stage of sickness. With 15 Core to spend it spends it and strikes twice as hard.
     */
    static boolean gammaLance(ServerPlayer p, int tier) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        double reach = AbilityRanges.ranged(40);
        var hit = level.clip(new ClipContext(eye, eye.add(look.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : eye.add(look.scale(reach));
        boolean charged = RadiationService.vent(p, 15) == 15;
        float damage = (8 + tier) * (charged ? 2 : 1) * RadiationService.multiplier(p);
        int struck = 0;
        for (Entity e : level.getEntities(p, new AABB(eye, end).inflate(1), Entity::isAlive)) {
            if (!(e instanceof LivingEntity t) || !RadiationService.hostile(p, t)) continue;
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            if (along < 0 || to.subtract(look.scale(along)).length() > 0.8 + t.getBbWidth() / 2) continue;
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().indirectMagic(p, p), damage);
            RadiationService.irradiate(t, p, charged ? 2 : 1, 140);
            struck++;
        }
        SetPiecePayload.send(level, Piece.RAD_LANCE, RadiationService.hand(p), end, charged ? 1.4F : 0.9F, null);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, charged ? 0.6F : 1.2F);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
        ArmPosePayload.show(p, ArmPose.ONE_HAND, 8);
        if (!charged) RadiationService.addMass(p, 6);
        MasteryService.markPowerUse(p, ModAbilities.RADIATION_ID);
        return true;
    }

    /** Test hook: Gamma Lance straight from the player's aim. */
    public static boolean gammaLanceForTest(ServerPlayer p, int tier) { return gammaLance(p, tier); }

    /** Lead Skin: Resistance II for eight seconds, and anything that hits you in melee meanwhile is dosed. */
    static boolean leadSkin(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LEAD_SKIN_TICKS, 1));
        LEAD_SKIN.put(p.getUUID(), now(p) + LEAD_SKIN_TICKS);
        SetPiecePayload.send(p.serverLevel(), Piece.RAD_EMP, p.position(), p.position().add(0, 1, 0), 3, null);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
        ArmPosePayload.show(p, ArmPose.ARMS_WIDE, 8);
        return true;
    }

    /** Decontaminate: purges harmful effects and flames from you and friends within 8 blocks, then Fire Resistance. */
    static boolean decontaminate(ServerPlayer p) {
        for (ServerPlayer ally : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(8),
                a -> a == p || p.isAlliedTo(a))) {
            for (var effect : List.copyOf(ally.getActiveEffects()))
                if (!effect.getEffect().value().isBeneficial()) ally.removeEffect(effect.getEffect());
            ally.clearFire();
            ally.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 160, 0));
        }
        SetPiecePayload.send(p.serverLevel(), Piece.RAD_EMP, p.position(), p.position().add(0, 1, 0), 8, null);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);
        return true;
    }

    // ------------------------------------------------------------------
    // The three techniques (Heat Vent, Reactor Core, Glowing Path)
    // ------------------------------------------------------------------

    public static boolean technique(ServerPlayer p, String mechanic) {
        return switch (mechanic) {
            case "heat_vent" -> heatVent(p);
            case "reactor_core" -> reactorCore(p);
            case "glowing_path" -> glowingPath(p);
            default -> false;
        };
    }

    /**
     * Heat Vent: dump up to 30 Core into a roaring cone eight blocks ahead. Damage grows with what was vented, and it
     * throws enemies hard and sets them alight. With Chain Vent every sick enemy it hits bursts as if it had died.
     */
    static boolean heatVent(ServerPlayer p) {
        int vented = RadiationService.vent(p, 30);
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), aim = p.getLookAngle().normalize();
        boolean chain = has(p, CHAIN_VENT);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9), e -> RadiationService.hostile(p, e))) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > 9 || to.length() > 1.2 && to.normalize().dot(aim) < 0.6) continue;
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().indirectMagic(p, p), 3 + vented / 5F);
            t.igniteForSeconds(4);
            double resist = Math.max(0.2, 1 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            t.setDeltaMovement(new Vec3(aim.x, 0, aim.z).normalize().scale((0.6 + vented / 30.0) * resist).add(0, 0.35 * resist, 0));
            t.hurtMarked = true;
            if (t instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
            if (chain && RadiationSickness.stage(t) >= 0) RadiationSickness.burst(t, p);
        }
        SetPiecePayload.send(level, Piece.RAD_HEAT, eye.add(aim.scale(0.6)).add(0, -0.3, 0), eye.add(aim.scale(9)), 1.2F + vented / 30F, null);
        level.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4F, 0.5F);
        CameraShakePayload.sendAround(level, p.position(), 12, 0.5F, 6);
        ArmPosePayload.show(p, ArmPose.TWO_HANDS, 10);
        if (vented > 0) p.displayClientMessage(Component.literal("You vent " + vented + " Core.").withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Reactor Core: turn up to 20 Core (40 with Power Transfer) into stamina, and give friends within 6 blocks a burst. */
    static boolean reactorCore(ServerPlayer p) {
        int vented = RadiationService.vent(p, has(p, POWER_TRANSFER) ? 40 : 20);
        PlayerEnergyService.restore(p, vented);
        for (ServerPlayer ally : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(6), a -> a != p && p.isAlliedTo(a))) {
            PlayerEnergyService.restore(ally, 10 + vented / 4);
            if (has(p, POWER_TRANSFER)) ally.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 1));
        }
        SetPiecePayload.send(p.serverLevel(), Piece.RAD_CHARGE, p.position(), p.position().add(0, 1, 0), 14, p);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 1.2F);
        return true;
    }

    /** Glowing Path: for eight seconds you leave contaminated ground wherever you walk. */
    static boolean glowingPath(ServerPlayer p) {
        PATHS.put(p.getUUID(), now(p) + GLOWING_PATH_TICKS);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        return true;
    }

    /** How long the ground you contaminate stays dangerous, and how many stages it doses. */
    public static int zoneTicks(ServerPlayer p, int ticks) { return has(p, HALF_LIFE) ? ticks * 2 : ticks; }
    public static int zoneStages(ServerPlayer p) { return p != null && has(p, HALF_LIFE) ? 2 : 1; }

    // ------------------------------------------------------------------
    // Passives and capstones
    // ------------------------------------------------------------------

    /** Lead Skin is up: an attacker who hit you in melee is dosed. */
    public static void onHurt(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
        Long until = LEAD_SKIN.get(p.getUUID());
        if (until == null || now(p) > until) return;
        if (source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity()
                && RadiationService.hostile(p, attacker)) {
            RadiationService.irradiate(attacker, p, 1, 120);
        }
    }

    /** Supercritical: while Critical, everything you kill bursts in a small blast that feeds you 5 Core. */
    public static void onKill(ServerPlayer p, LivingEntity dead) {
        if (!has(p, SUPERCRITICAL) || RadiationService.mass(p) < RadiationService.CRITICAL) return;
        Vec3 at = dead.position();
        RadiationLeap.blast(p, at, 3.5, 6 * RadiationService.multiplier(p), 1, 0.8);
        SetPiecePayload.send(p.serverLevel(), Piece.RAD_SLAM, at, at.add(0, 1, 0), 2.5F, null);
        p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.3F);
        RadiationService.addMass(p, 5);
    }

    /**
     * Breeder Reactor: once a minute, reaching 100 Core runs you hot instead of failing: ten seconds of Overdrive in
     * which your radiation hits twice as hard. Then you must vent, or containment fails as usual.
     */
    public static boolean overdrive(ServerPlayer p) {
        if (!has(p, BREEDER)) return false;
        long now = now(p);
        if (OVERDRIVE.containsKey(p.getUUID()) || now < OVERDRIVE_READY.getOrDefault(p.getUUID(), Long.MIN_VALUE)) return false;
        OVERDRIVE.put(p.getUUID(), now + OVERDRIVE_TICKS);
        OVERDRIVE_READY.put(p.getUUID(), now + OVERDRIVE_COOLDOWN);
        SetPiecePayload.send(p.serverLevel(), Piece.RAD_CHARGE, p.position(), p.position().add(0, 1, 0), 20, p);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.4F, 0.6F);
        p.sendSystemMessage(Component.literal("Overdrive! Your radiation burns twice as hot. Vent before it ends.")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        RadiationService.syncVisuals(p);
        return true;
    }

    public static boolean overdriven(ServerPlayer p) {
        Long until = OVERDRIVE.get(p.getUUID());
        return until != null && now(p) < until;
    }

    /** Ticks the trails and the end of Overdrive. */
    static void tick(net.minecraft.server.MinecraftServer server) {
        for (var e : List.copyOf(PATHS.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || now(p) > e.getValue()) { PATHS.remove(e.getKey()); continue; }
            if (now(p) % 10 == 0 && p.onGround()) RadiationService.contaminate(p, p.position(), 2.5, zoneTicks(p, 100), 6);
        }
        for (var e : List.copyOf(OVERDRIVE.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) { OVERDRIVE.remove(e.getKey()); continue; }
            if (now(p) < e.getValue()) continue;
            OVERDRIVE.remove(e.getKey());
            RadiationService.syncVisuals(p);
            // Still at the top: containment fails as usual.
            if (RadiationService.mass(p) >= RadiationService.MAXIMUM_MASS) RadiationService.addMass(p, 0);
        }
    }

    public static void forget(ServerPlayer p) {
        LEAD_SKIN.remove(p.getUUID()); PATHS.remove(p.getUUID()); OVERDRIVE.remove(p.getUUID());
    }

    public static void clear() { LEAD_SKIN.clear(); PATHS.clear(); OVERDRIVE.clear(); OVERDRIVE_READY.clear(); }
}
