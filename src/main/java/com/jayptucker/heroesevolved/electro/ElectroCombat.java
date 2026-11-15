package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.ElectroEvents;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Elle's fighting style: lightning thrown from the fists at close range, built on Pyrokinesis's firebending.
 *
 * <p>An empty-handed left click throws the next strike of a combo: a spark jab from the lead fist and a spark cross from
 * the rear, each an arc that seeks the nearest body near the aim; then (from level 2) a Thunderclap, both palms slammed
 * together to throw a fan of lightning across everything in front. Crouching turns the click into a Grounding Strike, a
 * palm driven into the floor that sends a ring of current out along the ground. Double-tapping left or right is an Arc
 * Step, a flash sideways as a bolt; back (level 2) is a Flashback, a snap backward that leaves a charge of static where
 * you stood, which bursts a moment later. A blow evaded during a dodge is a Ground Out: Charge, and the next spark arcs
 * through the attacker and on to the foes beside them.</p>
 *
 * <p>Every hit feeds Charge like the rest of the kit; at full Charge a Thunderclap spends it to reach half again as
 * far and hit harder. Fighting with wet hands still sends the current back through you.</p>
 */
public final class ElectroCombat {
    public enum Strike { JAB, CROSS, CLAP, GROUND }

    /** Level from which the combo ends in a Thunderclap, crouching grounds, and a back dodge leaves a charge. */
    public static final int CLAP_LEVEL = 2;
    static final int COMBO_WINDOW = 18, JAB_GAP = 5, CLAP_RECOVERY = 12, GROUND_RECOVERY = 14;
    static final int EVADE_TICKS = 8, CUSHION_TICKS = 30, LOADED_TICKS = 40, GROUND_OUT_CHARGE = 15;

    private static final class Fighter {
        int step;
        long lastStrike = -1000, readyAt = -1000, dodgeReady = -1000, loadedUntil = -1000;
        UUID grounded;
    }
    /** A Flashback's static, left where the dodge began, bursting at {@code due}. */
    private record Decoy(UUID owner, ServerLevel level, Vec3 at, long due, int powerLevel) {}

    private static final Map<UUID, Fighter> FIGHTERS = new HashMap<>();
    private static final List<Decoy> DECOYS = new ArrayList<>();

    public static final FightingStyles.Style STYLE = new FightingStyles.Style() {
        @Override public boolean owns(ServerPlayer player) {
            return ElectroEvents.hasAwakenedElectrokinesis(player) && !com.jayptucker.heroesevolved.events.EclipseService.arePowersSuppressed(player);
        }
        @Override public int strike(ServerPlayer player) { return ElectroCombat.strike(player); }
        @Override public boolean dodge(ServerPlayer player, Dodge dodge) { return ElectroCombat.dodge(player, dodge); }
        @Override public void counter(ServerPlayer player, DamageSource source) { groundOut(player, source); }
        @Override public void tick(MinecraftServer server) { ElectroCombat.tick(server); }
        @Override public void forget(ServerPlayer player) { ElectroCombat.forget(player); }
        @Override public void clear() { FIGHTERS.clear(); DECOYS.clear(); }
    };

    private ElectroCombat() {}

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    private static Fighter fighter(ServerPlayer player) { return FIGHTERS.computeIfAbsent(player.getUUID(), id -> new Fighter()); }

    /** The style needs the power, and arms not already busy with a stream, a surge or a Blackout's wind-up. */
    static boolean canFight(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && STYLE.owns(player) && PowerRules.canAct(player)
                && ElectroService.canStartCast(player);
    }

    public static boolean isLoaded(ServerPlayer player) {
        Fighter fighter = FIGHTERS.get(player.getUUID());
        return fighter != null && now(player) < fighter.loadedUntil;
    }

    public static boolean isDodging(ServerPlayer player) { return StyleMotion.dashing(player); }

    public static int pendingDecoys(ServerPlayer player) {
        return (int) DECOYS.stream().filter(d -> d.owner.equals(player.getUUID())).count();
    }

    /** The strike the next click throws. */
    public static Strike next(ServerPlayer player) {
        Fighter fighter = fighter(player);
        int level = PlayerProgressionService.getLevel(player);
        if (player.isCrouching() && level >= CLAP_LEVEL) return Strike.GROUND;
        int step = now(player) - fighter.lastStrike > COMBO_WINDOW ? 0 : fighter.step;
        return step == 2 && level >= CLAP_LEVEL ? Strike.CLAP : step == 1 ? Strike.CROSS : Strike.JAB;
    }

    // ------------------------------------------------------------------
    // Strikes
    // ------------------------------------------------------------------

    /** Throws the next strike. Returns 1 if thrown, 0 if it's too soon, and -1 if no lightning can be thrown at all. */
    public static int strike(ServerPlayer player) {
        if (!player.getMainHandItem().isEmpty() || !canFight(player)) return -1;
        long now = now(player);
        Fighter fighter = fighter(player);
        if (now < fighter.readyAt) return 0;
        Strike move = next(player);
        boolean loaded = now < fighter.loadedUntil && move != Strike.GROUND;
        int cost = loaded ? 0 : move == Strike.JAB || move == Strike.CROSS
                ? COMMON.electroStrikeEnergyCost.get() : COMMON.electroClapEnergyCost.get();
        if (cost > 0) {
            if (!PlayerEnergyService.tryConsume(player, cost)) {
                player.displayClientMessage(Component.translatable("electro.heroesevolved.too_tired"), true);
                return -1;
            }
            MasteryService.awardEnergySpent(player, cost);
        }
        int level = Math.max(1, PlayerProgressionService.getLevel(player));
        // A loaded spark is always the cross, even when the combo was due a clap.
        if (loaded && move == Strike.CLAP) move = Strike.CROSS;
        switch (move) {
            case JAB -> spark(player, level, false, loaded ? fighter : null);
            case CROSS -> spark(player, level, true, loaded ? fighter : null);
            case CLAP -> thunderclap(player, level);
            case GROUND -> ground(player, level);
        }
        if (loaded) { fighter.loadedUntil = -1000; fighter.grounded = null; }
        fighter.lastStrike = now;
        fighter.step = move == Strike.CLAP || move == Strike.GROUND ? 0 : (fighter.step + 1) % (level >= CLAP_LEVEL ? 3 : 2);
        fighter.readyAt = now + switch (move) {
            case CLAP -> CLAP_RECOVERY;
            case GROUND -> GROUND_RECOVERY;
            default -> JAB_GAP;
        };
        backfireIfWet(player, level);
        return 1;
    }

    /**
     * A spark from the fist. It seeks the nearest exposed foe within a wide tube round the aim, as lightning finds the
     * nearest conductor; with nothing there it cracks against the first block, or fizzles out in the air. A spark
     * Ground Out loaded arcs through the attacker first and leaps on to up to three foes near them.
     */
    private static void spark(ServerPlayer player, int level, boolean rear, Fighter loaded) {
        ServerLevel world = player.serverLevel();
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        var hit = world.clip(new ClipContext(eye, eye.add(aim.scale(ElectroGeometry.SPARK_REACH)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double length = eye.distanceTo(hit.getLocation());
        LivingEntity target = loaded == null ? null : grounded(player, loaded);
        if (target == null) target = seek(player, eye, aim, length);
        Vec3 fist = ElectroGeometry.fist(player, 1, rear);
        float damage = 2 + .4F * level + (ElectroService.charge(player) >= ElectroService.CHARGED ? 1 : 0);
        if (loaded != null) damage *= 1.6F;
        Vec3 end;
        if (target != null) {
            end = target.getBoundingBox().getCenter();
            ElectroService.shock(player, target, damage);
            StyleMotion.push(target, aim.scale(.18).add(0, .05, 0).add(target.getDeltaMovement().scale(.4)));
            if (loaded != null) chain(player, target, damage * .7F);
        } else {
            end = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation()
                    : eye.add(aim.scale(Math.min(length, 5.5)));
        }
        ElectroGeometry.show(world, Piece.ELECTRO_SPARK, fist, end,
                (target == null ? .7 : 1) + (loaded != null ? ElectroGeometry.CHAINED : 0), player);
        ArmPosePayload.show(player, rear ? ArmPose.ELECTRO_CROSS : ArmPose.ELECTRO_JAB, 9);
        player.swing(InteractionHand.MAIN_HAND, false);
        sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, loaded != null ? .55F : .3F, loaded != null ? 1.6F : rear ? 1.85F : 2.0F);
        if (target != null) sound(player, SoundEvents.COPPER_BULB_TURN_ON, .4F, 1.9F);
        ElectroService.addCharge(player, 1);
    }

    /** The foe Ground Out remembers, if they are still alive, near and in sight. */
    private static LivingEntity grounded(ServerPlayer player, Fighter fighter) {
        if (fighter.grounded == null) return null;
        var entity = player.serverLevel().getEntity(fighter.grounded);
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.distanceToSqr(player) > 16 * 16
                || !ElectroService.visible(player, player.getEyePosition(), living.getBoundingBox().getCenter())) return null;
        return living;
    }

    private static LivingEntity seek(ServerPlayer player, Vec3 eye, Vec3 aim, double length) {
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        AABB box = new AABB(eye, eye.add(aim.scale(length))).inflate(ElectroGeometry.SPARK_SEEK + 1);
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && ElectroService.hostile(player, e))) {
            Vec3 center = candidate.getBoundingBox().getCenter(), delta = center.subtract(eye);
            double along = delta.dot(aim), reach = ElectroGeometry.SPARK_SEEK + candidate.getBbWidth() * .5;
            double off = delta.subtract(aim.scale(along)).lengthSqr();
            if (along < 0 || along > length + .4 || off > reach * reach
                    || !ElectroService.visible(player, eye, center)) continue;
            // Nearer the line wins over nearer the fighter: the arc goes where it was thrown.
            double score = along + off * 3;
            if (score < best) { best = score; target = candidate; }
        }
        return target;
    }

    /** Leaps from the first foe to up to three more within six blocks of the last. */
    private static void chain(ServerPlayer player, LivingEntity first, float damage) {
        ServerLevel world = player.serverLevel();
        Set<UUID> struck = new HashSet<>();
        struck.add(first.getUUID());
        LivingEntity from = first;
        for (int i = 0; i < 3; i++) {
            LivingEntity next = null;
            double best = 36;
            for (LivingEntity candidate : world.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(6),
                    e -> e != player && !struck.contains(e.getUUID()) && ElectroService.hostile(player, e))) {
                double d = candidate.distanceToSqr(from);
                if (d < best && from.hasLineOfSight(candidate)) { best = d; next = candidate; }
            }
            if (next == null) return;
            struck.add(next.getUUID());
            ElectroGeometry.show(world, Piece.ELECTRO_ARC, from.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(),
                    .8, null, 1 + i);
            ElectroService.shock(player, next, damage);
            from = next;
        }
    }

    /** Both palms slammed together: a fan of lightning thrown across everything in front. Full Charge doubles it. */
    private static void thunderclap(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        Vec3 forward = ElectroGeometry.level(player.getLookAngle(), player.getYRot());
        Vec3 palms = ElectroGeometry.clap(player, 1, forward);
        boolean overcharged = ElectroService.spendOvercharge(player);
        double reach = ElectroGeometry.CLAP_REACH * (overcharged ? 1.5 : 1);
        float damage = (3 + level) * (overcharged ? 1.6F : 1);
        int struck = 0;
        for (LivingEntity target : world.getEntitiesOfClass(LivingEntity.class, new AABB(palms, palms).inflate(reach + 1),
                e -> e != player && ElectroService.hostile(player, e))) {
            AABB box = target.getBoundingBox();
            if (!ElectroGeometry.inCone(palms, forward, reach + target.getBbWidth() * .5, ElectroGeometry.CLAP_HALF_ANGLE,
                    target.position()) || box.maxY < palms.y - 1.8 || box.minY > palms.y + 1.6
                    || !ElectroService.visible(player, palms, box.getCenter())) continue;
            ElectroService.shock(player, target, damage);
            Vec3 out = target.position().subtract(player.position()).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? forward : out.normalize();
            StyleMotion.push(target, out.scale(.7).add(forward.scale(.15)).add(0, .3, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
            if (++struck >= 8) break;
        }
        ElectroGeometry.show(world, Piece.ELECTRO_CLAP, palms, palms.add(forward.scale(reach)), reach, player);
        ArmPosePayload.show(player, ArmPose.ELECTRO_CLAP, 12);
        sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, overcharged ? .9F : .45F, overcharged ? 1.3F : 1.75F);
        sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, .8F, 1.2F);
        CameraShakePayload.sendAround(world, palms, 12, overcharged ? .3F : .14F, 5);
    }

    /** A palm driven into the floor: a ring of current runs out along the ground and jolts everyone on it. */
    private static void ground(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        Vec3 feet = player.position();
        double radius = ElectroGeometry.GROUND_RADIUS;
        float damage = 2.5F + .5F * level;
        int struck = 0;
        for (LivingEntity target : world.getEntitiesOfClass(LivingEntity.class, new AABB(feet, feet).inflate(radius + 1, 2, radius + 1),
                e -> e != player && ElectroService.hostile(player, e))) {
            if (Math.abs(target.getY() - feet.y) > 1.5
                    || target.position().subtract(feet).horizontalDistance() > radius + target.getBbWidth() * .5
                    || !ElectroService.visible(player, feet.add(0, .4, 0), target.getBoundingBox().getCenter())) continue;
            ElectroService.shock(player, target, damage);
            Vec3 out = target.position().subtract(feet).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? ElectroGeometry.level(player.getLookAngle(), player.getYRot()) : out.normalize();
            StyleMotion.push(target, out.scale(.45).add(0, .45, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            if (++struck >= 12) break;
        }
        ElectroGeometry.show(world, Piece.ELECTRO_GROUND_ARC, feet, feet.add(ElectroGeometry.level(player.getLookAngle(),
                player.getYRot())), radius, player);
        ArmPosePayload.show(player, ArmPose.ELECTRO_SLAM, 14);
        sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, .9F, .9F);
        sound(player, SoundEvents.COPPER_BULB_TURN_OFF, 1, .6F);
        CameraShakePayload.sendAround(world, feet, 10, .12F, 4);
    }

    // ------------------------------------------------------------------
    // Dodges
    // ------------------------------------------------------------------

    /** A double-tapped direction: an Arc Step to the side, or (from level 2) a Flashback that leaves static behind. */
    public static boolean dodge(ServerPlayer player, Dodge dodge) {
        if (!canFight(player) || player.isPassenger() || player.isFallFlying() || StyleMotion.dashing(player)) return false;
        Fighter fighter = fighter(player);
        long now = now(player);
        if (now < fighter.dodgeReady) return false;
        int level = Math.max(1, PlayerProgressionService.getLevel(player));
        boolean flashback = dodge == Dodge.BACK && level >= CLAP_LEVEL;
        int cost = COMMON.electroDodgeEnergyCost.get() + (flashback ? 3 : 0);
        if (cost > 0) {
            if (!PlayerEnergyService.tryConsume(player, cost)) {
                player.displayClientMessage(Component.translatable("electro.heroesevolved.too_tired"), true);
                return false;
            }
            MasteryService.awardEnergySpent(player, cost);
        }
        Vec3 forward = ElectroGeometry.level(player.getLookAngle(), player.getYRot());
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 direction = switch (dodge) {
            case LEFT -> right.scale(-1);
            case RIGHT -> right;
            case BACK -> forward.scale(-1);
        };
        // A flash, not a jump: the body is pushed hard and almost level, for three ticks.
        double speed = flashback ? 1.95 : 1.65;
        Vec3 from = player.position();
        StyleMotion.dash(player, direction.scale(speed), player.onGround() ? .12 : .04, ElectroGeometry.STEP_TICKS,
                ElectroGeometry.STEP_TICKS + 2, false, false);
        StyleMotion.evade(player, EVADE_TICKS);
        StyleMotion.cushion(player, CUSHION_TICKS);
        fighter.dodgeReady = now + COMMON.electroDodgeCooldownTicks.get();

        double travel = speed * ElectroGeometry.STEP_TICKS;
        ServerLevel world = player.serverLevel();
        ElectroGeometry.show(world, Piece.ELECTRO_ARC_STEP, from, from.add(direction.scale(travel)), flashback ? 2 : 1, player);
        if (flashback) {
            DECOYS.add(new Decoy(player.getUUID(), world, from, now + ElectroGeometry.FLASHBACK_FUSE, level));
            ElectroGeometry.show(world, Piece.ELECTRO_DECOY, from, from.add(forward), ElectroGeometry.FLASHBACK_RADIUS, null);
        }
        ArmPosePayload.show(player, flashback ? ArmPose.ELECTRO_FLASH : ArmPose.ELECTRO_STEP, 10);
        sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, .55F, 1.7F);
        sound(player, SoundEvents.BEACON_DEACTIVATE, .4F, 2F);
        ElectroService.addCharge(player, 2);
        return true;
    }

    /** A blow passed through a flash: the attacker is grounded, and the next spark goes through them to their friends. */
    private static void groundOut(ServerPlayer player, DamageSource source) {
        Fighter fighter = fighter(player);
        long now = now(player);
        fighter.loadedUntil = now + LOADED_TICKS;
        fighter.grounded = source.getEntity() instanceof LivingEntity attacker && attacker != player ? attacker.getUUID() : null;
        // The rear fist carries the charge, so the counter is always the cross.
        fighter.step = 1;
        fighter.lastStrike = now;
        fighter.readyAt = Math.min(fighter.readyAt, now);
        ElectroService.addCharge(player, GROUND_OUT_CHARGE);
        ElectroGeometry.show(player.serverLevel(), Piece.ELECTRO_GROUND_OUT, player.position(), player.position(), 1, player);
        sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, .7F, 1.95F);
        sound(player, SoundEvents.BEACON_POWER_SELECT, .5F, 1.9F);
        player.displayClientMessage(Component.translatable("electro.heroesevolved.ground_out").withStyle(ChatFormatting.AQUA), true);
    }

    private static void backfireIfWet(ServerPlayer player, int level) {
        if (!player.isInWaterOrBubble()) return;
        player.hurt(player.damageSources().magic(), .5F + level * .25F);
        player.displayClientMessage(Component.literal("The water carries the current back into you!")
                .withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------
    // Tick and cleanup
    // ------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Decoy decoy : List.copyOf(DECOYS)) {
            if (now < decoy.due) continue;
            DECOYS.remove(decoy);
            ServerPlayer owner = server.getPlayerList().getPlayer(decoy.owner);
            if (owner == null || owner.serverLevel() != decoy.level || !PowerRules.canAct(owner)) continue;
            burst(owner, decoy);
        }
        if (now % 5 == 0) {
            for (var entry : List.copyOf(FIGHTERS.entrySet())) {
                if (now >= entry.getValue().loadedUntil) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                Vec3 fist = ElectroGeometry.fist(player, 1, true);
                ElectroGeometry.show(player.serverLevel(), Piece.ELECTRO_LOADED, fist, fist, 1, player);
            }
        }
    }

    /** The static a Flashback left goes off: anyone standing in it is jolted and locked up for a moment. */
    private static void burst(ServerPlayer owner, Decoy decoy) {
        float damage = 3 + .6F * decoy.powerLevel;
        Vec3 at = decoy.at;
        double radius = ElectroGeometry.FLASHBACK_RADIUS;
        for (LivingEntity target : decoy.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1, 2, radius + 1),
                e -> e != owner && ElectroService.hostile(owner, e))) {
            if (target.position().subtract(at).horizontalDistance() > radius + target.getBbWidth() * .5
                    || Math.abs(target.getY() - at.y) > 2) continue;
            ElectroService.shock(owner, target, damage);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 4));
            StyleMotion.push(target, target.position().subtract(at).multiply(1, 0, 1).normalize().scale(.35).add(0, .3, 0));
        }
        decoy.level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, .9F, 1.4F);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void forget(ServerPlayer player) {
        FIGHTERS.remove(player.getUUID());
        DECOYS.removeIf(d -> d.owner.equals(player.getUUID()));
    }
}
