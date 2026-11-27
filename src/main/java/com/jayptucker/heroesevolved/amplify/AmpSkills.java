package com.jayptucker.heroesevolved.amplify;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Power Amplification's skill tree and classes (see {@code docs/power-amplification.md}).
 *
 * <ul>
 *   <li><b>Sidekick</b> (making a friend stronger): Close Friends, Partner, Not Too Much, Along for the Ride, Hold Hands,
 *   Breakthrough.</li>
 *   <li><b>Red Lightning</b> (fighting alone): Bigger Ball, Van Full, Opposite Current, Burn Through, Overcharge the
 *   Enemy, The Second Coming.</li>
 *   <li><b>Fixer</b> (useful anywhere): Steady Hands, Burnout, Sparks for Show, Mechanic, Doorbuster, Grounded.</li>
 *   <li>Classes: Got Your Back (support), Lightning Cannon (offense), Lights Back On (defense).</li>
 * </ul>
 *
 * <p>The passives are read where the core already decides things ({@link AmpService}, {@link Amplification}); this
 * class holds the actives, the class abilities and what ticks.</p>
 */
public final class AmpSkills {
    public static final String CLOSE_FRIENDS = "amp_0_0", PARTNER = "amp_0_1", NOT_TOO_MUCH = "amp_0_2",
            ALONG_FOR_THE_RIDE = "amp_along_for_the_ride", HOLD_HANDS = "amp_hold_hands", BREAKTHROUGH = "amp_breakthrough",
            BIGGER_BALL = "amp_1_0", VAN_FULL = "amp_1_1", OPPOSITE_CURRENT = "amp_1_2", BURN_THROUGH = "amp_burn_through",
            OVERCHARGE_ENEMY = "amp_overcharge_enemy", SECOND_COMING = "amp_second_coming",
            STEADY_HANDS = "amp_2_0", BURNOUT = "amp_2_1", SPARKS_FOR_SHOW = "amp_2_2", MECHANIC = "amp_mechanic",
            DOORBUSTER = "amp_doorbuster", GROUNDED = "amp_grounded",
            GOT_YOUR_BACK = "amp_got_your_back", LIGHTNING_CANNON = "amp_lightning_cannon", LIGHTS_BACK_ON = "amp_lights_back_on";
    /** Second Wind is part of Red Lightning's capstone row: every Supercharge arms the next bolt (The Second Coming's partner). */
    public static final String SECOND_WIND = "amp_second_wind";
    public static final double VAN_RADIUS = 5;
    public static final int BURNOUT_TICKS = 80, WILD_TICKS = 200;

    private static final Map<UUID, Long> HESITATED = new HashMap<>();

    private AmpSkills() {}

    public static boolean has(ServerPlayer player, String id) {
        if (id.equals(SECOND_WIND)) return SkillTreeService.has(player, LIGHTNING_CANNON);
        return SkillTreeService.has(player, id);
    }

    // ------------------------------------------------------------------
    // Tree actives
    // ------------------------------------------------------------------

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .canUse(c -> canUse(c, node.id()))
                .activate(c -> activate(c, node.id())).build();
    }

    static boolean canUse(AbilityUseContext c, String id) {
        ServerPlayer p = c.player();
        if (!AmpService.canCast(p)) return false;
        return switch (id) {
            case PARTNER -> Targeting.livingEntityInRange(p, 16, ClipContext.Fluid.NONE, e -> AmpService.ally(p, e)) != null
                    || AmpService.explain(p, "no_partner");
            case HOLD_HANDS -> partnerBoost(p) != null || AmpService.explain(p, "partner_not_surged");
            case OVERCHARGE_ENEMY -> poweredFoe(p, 16) != null || AmpService.explain(p, "no_powered_foe");
            case BURNOUT -> poweredFoe(p, 12) != null || dampener(p) != null || AmpService.explain(p, "no_powered_foe");
            case DOORBUSTER -> door(p) != null || AmpService.explain(p, "no_door");
            default -> true;
        };
    }

    static boolean activate(AbilityUseContext c, String id) {
        ServerPlayer p = c.player();
        return switch (id) {
            case PARTNER -> partner(p);
            case HOLD_HANDS -> holdHands(p) > 0;
            case VAN_FULL -> { vanFull(p); yield true; }
            case OVERCHARGE_ENEMY -> overchargeEnemy(p, poweredFoe(p, 16));
            case BURNOUT -> {
                LivingEntity foe = poweredFoe(p, 12);
                yield foe != null ? burnout(p, foe) : burnDampener(p, dampener(p));
            }
            case DOORBUSTER -> doorbuster(p, door(p));
            default -> false;
        };
    }

    /** Partner: bond with the ally you look at, for good (until you choose another). */
    static boolean partner(ServerPlayer p) {
        LivingEntity ally = Targeting.livingEntityInRange(p, 16, ClipContext.Fluid.NONE, e -> AmpService.ally(p, e));
        if (ally == null) return false;
        AmpService.bond(p, ally);
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_THREAD, AmpGeometry.hand(p), ally.getBoundingBox().getCenter(), 1, p, ally.getId());
        ArmPosePayload.show(p, ArmPose.AMP_ZAP, 12);
        AmpService.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1, 1.4F);
        p.displayClientMessage(Component.translatable("amp.heroesevolved.partnered", ally.getDisplayName()).withStyle(ChatFormatting.RED), true);
        if (ally instanceof ServerPlayer friend)
            friend.displayClientMessage(Component.translatable("amp.heroesevolved.partnered_you", p.getDisplayName()).withStyle(ChatFormatting.RED), true);
        return true;
    }

    /** The partner's running supercharge from {@code p}, or null. */
    static Amplification.Boost partnerBoost(ServerPlayer p) {
        LivingEntity partner = AmpService.partner(p);
        if (partner == null) return null;
        Amplification.Boost b = Amplification.BOOSTS.get(partner.getUUID());
        return b != null && b.target == partner && b.by == p ? b : null;
    }

    /** Hold Hands: everyone touching your partner, a chain of friends each within reach of the last, shares their Surge. */
    public static int holdHands(ServerPlayer p) {
        Amplification.Boost b = partnerBoost(p);
        if (b == null) return 0;
        int left = (int) Math.max(20, b.until - p.serverLevel().getGameTime());
        List<LivingEntity> chain = new ArrayList<>();
        Deque<LivingEntity> open = new ArrayDeque<>();
        open.add(b.target);
        while (!open.isEmpty() && chain.size() < 8) {
            LivingEntity link = open.poll();
            for (LivingEntity next : link.level().getEntitiesOfClass(LivingEntity.class, link.getBoundingBox().inflate(2.5),
                    e -> e != b.target && AmpService.ally(p, e) && !chain.contains(e))) {
                if (chain.size() >= 8) break;
                chain.add(next);
                open.add(next);
            }
        }
        for (LivingEntity e : chain) {
            Amplification.surge(p, e, left);
            SetPiecePayload.send(p.serverLevel(), Piece.AMP_ZAP, b.target.getBoundingBox().getCenter(), e.getBoundingBox().getCenter(), .8F, b.target, e.getId());
        }
        ArmPosePayload.show(p, ArmPose.AMP_ZAP, 12);
        AmpService.sound(p, SoundEvents.BEACON_POWER_SELECT, 1, 1.5F);
        return chain.size();
    }

    /** Van Full: a burst that bounces off nearby walls, double where you're closed in; it knocks out instead of killing. */
    public static int vanFull(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        float spent = AmpService.spend(p, 30);
        boolean enclosed = enclosed(level, p.getEyePosition());
        boolean lethal = has(p, SECOND_COMING);
        float damage = (spent / 30F) * (enclosed ? 2 : 1) * (lethal ? 2 : 1);
        int hit = 0;
        for (LivingEntity e : StyleOps.foes(p, p.position(), VAN_RADIUS)) {
            float dealt = lethal ? damage : Math.min(damage, e.getHealth() - 1);
            if (dealt > 0) StyleHits.hurt(e, p.damageSources().indirectMagic(p, p), dealt);
            AmpService.stun(e, lethal ? 40 : 100);
            StyleOps.shove(e, p.position(), enclosed ? .5 : .9, .25);
            hit++;
        }
        SetPiecePayload.send(level, Piece.AMP_BURST, p.position(), p.position().add(0, 1, 0), (float) VAN_RADIUS * (enclosed ? 1.2F : 1), p);
        ArmPosePayload.show(p, ArmPose.AMP_RAISE, 10);
        AmpService.sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.1F, 1.4F);
        CameraShakePayload.sendAround(level, p.position(), 16, enclosed ? .3F : .18F, 6);
        return hit;
    }

    /** Closed in: most of 26 rays from the eye meet a block within five blocks (a room, a cave, a vehicle). */
    public static boolean enclosed(ServerLevel level, Vec3 eye) {
        int blocked = 0, total = 0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) continue;
            total++;
            Vec3 dir = new Vec3(x, y, z).normalize();
            if (level.clip(new ClipContext(eye, eye.add(dir.scale(VAN_RADIUS)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.BLOCK) blocked++;
        }
        return blocked >= total * .6;
    }

    /** A powered foe in sight: a player with a power (where PvP allows), a gifted mob or Sylar. */
    static LivingEntity poweredFoe(ServerPlayer p, double range) {
        return Targeting.livingEntityInRange(p, range, ClipContext.Fluid.NONE, e -> StyleOps.isFoe(p, e) && AmpService.powered(e));
    }

    /** Overcharge the Enemy: pour red energy into a powered foe; its next move goes wild. */
    public static boolean overchargeEnemy(ServerPlayer p, LivingEntity foe) {
        if (foe == null) return false;
        Amplification.markWild(foe, WILD_TICKS);
        // Story fighters cast through their own code; the overload stalls them instead.
        if (foe instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity)
            foe.addEffect(new MobEffectInstance(ModItems.DAMPENED, 40, 0));
        AmpService.zap(p, foe, 1.4F);
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_BURNOUT, foe.position(), foe.getEyePosition(), Math.max(.6F, foe.getBbHeight()), foe, 1);
        ArmPosePayload.show(p, ArmPose.AMP_THROW, 10);
        AmpService.sound(p, foe.position(), SoundEvents.BEACON_POWER_SELECT, 1, .6F);
        return true;
    }

    /** Burnout: a powered foe's power switches off for four seconds (a dampener on one body). */
    public static boolean burnout(ServerPlayer p, LivingEntity foe) {
        foe.addEffect(new MobEffectInstance(ModItems.DAMPENED, BURNOUT_TICKS, 0, false, true, true));
        AmpService.zap(p, foe, 1.6F);
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_BURNOUT, foe.position(), foe.getEyePosition(), Math.max(.6F, foe.getBbHeight()), foe);
        ArmPosePayload.show(p, ArmPose.AMP_THROW, 10);
        AmpService.sound(p, foe.position(), SoundEvents.BEACON_DEACTIVATE, 1, .7F);
        return true;
    }

    /** An Ability Dampener's projector in sight within 12 blocks. */
    static BlockPos dampener(ServerPlayer p) {
        BlockPos pos = AmpService.aimBlock(p, 12);
        return pos != null && p.serverLevel().getBlockEntity(pos) instanceof com.jayptucker.heroesevolved.dampener.AbilityDampenerBlockEntity ? pos : null;
    }

    /** Burnout on a projector: its breaker blows, and it stays dark for a minute. */
    static boolean burnDampener(ServerPlayer p, BlockPos pos) {
        if (pos == null) return false;
        com.jayptucker.heroesevolved.grid.GridDisruption.stall(p, p.serverLevel(), pos, 1200);
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_JUMP, AmpGeometry.hand(p), Vec3.atCenterOf(pos), 1.6F, p);
        ArmPosePayload.show(p, ArmPose.AMP_THROW, 10);
        AmpService.sound(p, Vec3.atCenterOf(pos), SoundEvents.GENERIC_EXPLODE.value(), .6F, 1.6F);
        return true;
    }

    /** A door, gate, trapdoor or Company lock within eight blocks. */
    static BlockPos door(ServerPlayer p) {
        BlockPos pos = AmpService.aimBlock(p, 8);
        if (pos == null) return null;
        BlockState state = p.serverLevel().getBlockState(pos);
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock
                ? pos : null;
    }

    /** Doorbuster: the door comes off its hinges, and whoever stands near it is stunned. */
    public static boolean doorbuster(ServerPlayer p, BlockPos pos) {
        if (pos == null) return false;
        ServerLevel level = p.serverLevel();
        if (!com.jayptucker.heroesevolved.effects.WorldMutationPolicy.canEdit(p, level, pos)) return AmpService.explain(p, "protected");
        Vec3 at = Vec3.atCenterOf(pos);
        level.destroyBlock(pos, true, p);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5), e -> e != p && e.isAlive())) {
            if (StyleOps.isFoe(p, e)) {
                e.invulnerableTime = 0;
                e.hurt(p.damageSources().indirectMagic(p, p), 1.5F);
            }
            AmpService.stun(e, 60);
            StyleOps.shove(e, p.position(), .7, .2);
        }
        SetPiecePayload.send(level, Piece.AMP_DOOR, AmpGeometry.hand(p), at, 1, p);
        ArmPosePayload.show(p, ArmPose.AMP_BEAM, 10);
        AmpService.sound(p, at, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1, .8F);
        AmpService.sound(p, at, SoundEvents.LIGHTNING_BOLT_IMPACT, .6F, 1.8F);
        return true;
    }

    // ------------------------------------------------------------------
    // What ticks
    // ------------------------------------------------------------------

    static void tick(ServerPlayer p, AmpService.Amp a, long now) {
        // Sparks for Show: crouched, empty-handed, with a little Charge: sparks dance between the fingers.
        boolean showing = has(p, SPARKS_FOR_SHOW) && p.isCrouching() && p.getMainHandItem().isEmpty() && a.charge >= 10;
        if (showing != a.showing) { a.showing = showing; AmpService.sync(a, true); }
        if (showing && now % 20 == 0) {
            if (now % 40 == 0) ArmPosePayload.show(p, ArmPose.AMP_SHOW, 44);
            for (Villager v : p.serverLevel().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(8), v -> v.hasLineOfSight(p)))
                if (AmpService.impress(a, v, now)) v.playSound(SoundEvents.VILLAGER_CELEBRATE, 1, 1);
            for (Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8), m -> m.getTarget() == p && m.getHealth() < 10)) {
                Long last = HESITATED.get(mob.getUUID());
                if (last != null && now - last < 200) continue;
                HESITATED.put(mob.getUUID(), now);
                mob.getNavigation().stop();
                mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false));
            }
        }
        follow(p, a);
    }

    /** Along for the Ride: when your supercharged partner folds, runs, flies or breaches away, you go with them. */
    private static void follow(ServerPlayer p, AmpService.Amp a) {
        if (!has(p, ALONG_FOR_THE_RIDE)) return;
        Amplification.Boost b = partnerBoost(p);
        if (b == null) {
            LivingEntity partner = AmpService.partner(p);
            if (partner != null) {
                Amplification.Boost other = Amplification.BOOSTS.get(partner.getUUID());
                if (other != null && other.by == p) b = other;
            }
        }
        if (b == null) { a.partnerLast = null; return; }
        LivingEntity partner = b.target;
        if (a.partnerLast != null && a.partnerLevel != null) {
            boolean jumped = partner.level().dimension() != a.partnerLevel || partner.position().distanceTo(a.partnerLast) > 6;
            boolean beside = p.level().dimension() == a.partnerLevel && p.position().distanceTo(a.partnerLast) <= 8;
            if (jumped && beside && partner.level() instanceof ServerLevel there) {
                Vec3 back = partner.getLookAngle().multiply(1, 0, 1);
                back = back.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : back.normalize().scale(-1.3);
                Vec3 to = partner.position().add(back);
                if (!SpeedService.safe(p, to)) to = partner.position();
                AmpService.following(p, true);
                if (there != p.serverLevel()) p.teleportTo(there, to.x, to.y, to.z, p.getYRot(), p.getXRot());
                else StyleHits.blink(p, to);
                SetPiecePayload.send(there, Piece.AMP_ZAP, to.add(0, 1, 0), partner.getBoundingBox().getCenter(), .7F, p, partner.getId());
            }
        }
        a.partnerLast = partner.position();
        a.partnerLevel = partner.level().dimension();
    }

    static void forget(ServerPlayer p) { HESITATED.remove(p.getUUID()); }

    static void clear() { HESITATED.clear(); }
}
