package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.events.RadiationEvents;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The held ice blast and the heat it steals from flames and unstable reactors. */
public final class CryoBlastService {
    private static final int PULSE_TICKS = 4, PULSE_COST = 2, MAX_TICKS = 160;
    private static final int STABILIZE_TICKS = 20, SAFE_CORE = 30, CORE_PER_PULSE = 10;
    private static final int HEAT_FOR_CHARGE = 16, CHARGE_TICKS = 240;
    private static final Map<UUID, Stream> STREAMS = new HashMap<>();
    private static final Map<UUID, Long> CHARGES = new HashMap<>();

    private static final class Stream {
        final ServerPlayer owner;
        final ServerLevel level;
        final int tier;
        final long began;
        ServerPlayer reactor;
        int contactTicks, absorbed;
        boolean rescued;

        Stream(ServerPlayer owner, int tier) {
            this.owner = owner;
            this.level = owner.serverLevel();
            this.tier = Math.clamp(tier, 1, 5);
            this.began = level.getGameTime();
        }
    }

    private record Contact(Vec3 end, LivingEntity target, BlockHitResult block) {}

    private CryoBlastService() {}

    public static boolean isStreaming(ServerPlayer player) { return STREAMS.containsKey(player.getUUID()); }

    public static boolean start(ServerPlayer player, int tier) {
        if (!PowerRules.canAct(player) || !CryoKitService.authorized(player) || isStreaming(player)) return false;
        STREAMS.put(player.getUUID(), new Stream(player, tier));
        ArmPosePayload.show(player, ArmPose.CRYO_WEAVE, MAX_TICKS);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_BREAK,
                SoundSource.PLAYERS, .7F, .85F);
        return true;
    }

    public static boolean stop(ServerPlayer player) {
        if (STREAMS.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clearIf(player, ArmPose.CRYO_WEAVE);
        return true;
    }

    public static boolean spendCharge(ServerPlayer player) {
        Long until = CHARGES.remove(player.getUUID());
        return until != null && until > player.server.overworld().getGameTime();
    }

    static void tick(long now) {
        CHARGES.values().removeIf(until -> until <= now);
        for (Stream stream : List.copyOf(STREAMS.values())) {
            ServerPlayer player = stream.owner;
            long age = stream.level.getGameTime() - stream.began;
            if (age >= MAX_TICKS || player.level() != stream.level || player.isRemoved()
                    || !PowerRules.canAct(player) || !CryoKitService.authorized(player)) {
                stop(player);
                continue;
            }
            if (age < 2) continue;
            boolean pulse = (age - 2) % PULSE_TICKS == 0;
            if (pulse && !PlayerEnergyService.tryConsume(player, PULSE_COST)) {
                stop(player);
                continue;
            }
            if (pulse) MasteryService.awardEnergySpent(player, PULSE_COST);
            Contact contact = trace(player, stream.level);
            boolean cooling = coolReactor(stream, contact.target, pulse);
            if (pulse) applyPulse(stream, contact, cooling);
            if ((age - 2) % 3 == 0) {
                float style = cooling ? 2 : contact.target != null || contact.block.getType() == HitResult.Type.BLOCK ? 1 : .5F;
                SetPiecePayload.send(stream.level, Piece.CRYO_BLAST, player.getEyePosition(), contact.end, style, player);
            }
            if ((age - 2) % 12 == 0) stream.level.playSound(null, player.blockPosition(),
                    SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, .6F, cooling ? .7F : 1.35F);
        }
    }

    private static Contact trace(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getLookAngle().scale(CryoBlastGeometry.REACH));
        BlockHitResult block = level.clip(new ClipContext(eye, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        Vec3 end = block.getLocation();
        LivingEntity first = null;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(.5),
                entity -> entity != player && entity.isAlive() && !entity.isSpectator() && !(entity instanceof TemporalEchoEntity))) {
            var hit = target.getBoundingBox().inflate(CryoBlastGeometry.AIM_PADDING).clip(eye, end);
            if (hit.isEmpty() || !player.hasLineOfSight(target)) continue;
            first = target;
            end = hit.get();
        }
        return new Contact(end, first, block);
    }

    /** Emergency cooling is safe in co-op. Once latched, the same beam cannot hurt the person it just rescued. */
    private static boolean coolReactor(Stream stream, LivingEntity contact, boolean pulse) {
        if (stream.reactor != contact) {
            stream.reactor = null;
            stream.contactTicks = 0;
            stream.rescued = false;
        }
        if (!(contact instanceof ServerPlayer target) || !RadiationEvents.hasAwakenedRadiation(target)
                || EclipseService.arePowersSuppressed(target) || PowerIntroService.radiationChargeRemaining(target) > 0) return false;
        boolean critical = RadiationService.critical(target);
        if (stream.reactor == null && !critical && (RadiationService.mass(target) < RadiationService.HOT
                || !target.isShiftKeyDown() && !FlightSkillService.isFriend(stream.owner, target)
                        && !FlightSkillService.hostile(stream.owner, target))) return false;
        stream.reactor = target;
        if (stream.rescued) return true;
        if (critical) {
            RadiationService.hold(target);
            stream.contactTicks++;
        } else stream.contactTicks = 0;
        if (pulse && RadiationService.mass(target) > SAFE_CORE) {
            int removed = RadiationService.vent(target, Math.clamp(RadiationService.mass(target) - SAFE_CORE, 0, CORE_PER_PULSE));
            absorb(stream, removed);
        }
        if (critical && stream.contactTicks >= STABILIZE_TICKS && RadiationService.mass(target) <= SAFE_CORE) {
            RadiationService.defuse(target, SAFE_CORE);
            stream.rescued = true;
            primeCharge(stream);
            shatterCue(stream, target);
            stream.owner.displayClientMessage(Component.translatable("cryo.heroesevolved.reactor_stable",
                    target.getDisplayName()), true);
        } else if (critical && pulse) {
            stream.owner.displayClientMessage(Component.translatable("cryo.heroesevolved.reactor_cooling",
                    target.getDisplayName(), RadiationService.mass(target)), true);
        }
        return true;
    }

    private static void applyPulse(Stream stream, Contact contact, boolean cooling) {
        var player = stream.owner;
        int absorbed = snuffFire(stream, contact.end);
        if (!cooling && contact.target != null) {
            LivingEntity target = contact.target;
            if (FlightSkillService.hostile(player, target)) {
                if (target instanceof ServerPlayer pyro) {
                    int heat = PyroService.freezeFlames(pyro);
                    absorbed += heat;
                    if (heat > 0) shatterCue(stream, target);
                }
                if (target.isOnFire()) {
                    target.clearFire();
                    absorbed += 4;
                }
                CryoService.coldHit(player, target, 1F + stream.tier * .2F);
                CryoService.chill(target, 20 + (SkillTreeService.has(player, "cryokinesis_2_0") ? 8 : 0), player);
            } else if (target.isOnFire()) {
                target.clearFire();
                absorbed += 4;
            }
        }
        absorb(stream, absorbed);
        if (contact.target != null || contact.block.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = contact.block.getBlockPos();
        if (stream.level.getBlockState(pos).is(Blocks.WATER)) TemporaryBlocks.freezeWater(stream.level, pos, player);
        else if (stream.level.getBlockState(pos.above()).isAir()) TemporaryBlocks.frostSurface(stream.level, pos, 100, player);
    }

    private static int snuffFire(Stream stream, Vec3 end) {
        Vec3 start = stream.owner.getEyePosition();
        Vec3 span = end.subtract(start);
        int steps = (int) Math.ceil(span.length() * 3);
        BlockPos previous = null;
        int absorbed = 0;
        for (int step = 0; step <= steps && absorbed < 16; step++) {
            BlockPos pos = BlockPos.containing(start.add(span.scale(step / (double) Math.max(1, steps))));
            if (pos.equals(previous)) continue;
            previous = pos;
            if (stream.level.getBlockState(pos).is(BlockTags.FIRE)
                    && WorldMutationPolicy.canEdit(stream.owner, stream.level, pos)
                    && stream.level.removeBlock(pos, false)) {
                absorbed += 4;
                SetPiecePayload.send(stream.level, Piece.CRYO_QUENCH, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos), .65F, null);
                stream.level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .55F, 1.3F);
            }
        }
        return absorbed;
    }

    private static void absorb(Stream stream, int amount) {
        if (amount <= 0) return;
        PlayerEnergyService.restore(stream.owner, Math.min(2, (amount + 3) / 4));
        stream.absorbed += amount;
        if (stream.absorbed >= HEAT_FOR_CHARGE) {
            stream.absorbed = 0;
            primeCharge(stream);
        }
    }

    private static void primeCharge(Stream stream) {
        boolean fresh = CHARGES.put(stream.owner.getUUID(), stream.owner.server.overworld().getGameTime() + CHARGE_TICKS) == null;
        if (fresh) stream.owner.displayClientMessage(Component.translatable("cryo.heroesevolved.siphon_charged"), true);
    }

    private static void shatterCue(Stream stream, LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        SetPiecePayload.send(stream.level, Piece.CRYO_QUENCH, center, center, 1, target);
        stream.level.playSound(null, target.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .8F, .8F);
    }

    static void forget(ServerPlayer player) {
        stop(player);
        CHARGES.remove(player.getUUID());
    }

    static void clear() {
        for (Stream stream : List.copyOf(STREAMS.values())) stop(stream.owner);
        CHARGES.clear();
    }
}
