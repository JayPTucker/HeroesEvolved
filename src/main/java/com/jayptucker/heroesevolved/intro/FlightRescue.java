package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.network.SkyburstMotionPayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Flight's awakening. Out under open sky, the flyer swoops to whoever is going down (themself, or an
 * ally in reach), takes them in a Skyhook carry, and climbs away from the fight in a straight line at
 * Skyburst speed. They end in a normal flight session, still carrying their ward.
 *
 * <p>Movement is the Skyburst's: each tick's step is swept ({@link SpeedService#swept}) and sent to the
 * flyer's client in full precision ({@link SkyburstMotionPayload}), because it outruns the vanilla motion
 * packet.
 */
final class FlightRescue implements PowerIntroService.Rescue {
    /** Blocks a tick while swooping to the ward. */
    static final double SWOOP = 4.5;
    /** The climb away starts this fast (blocks a tick) and eases down to {@link #EXIT}. */
    static final double PEAK = 8.5;
    static final double EXIT = 1.25;
    static final int SWOOP_TICKS = 60;
    static final int CLIMB_TICKS = 36;
    /** How steeply the climb rises: up this much for every block outward. */
    static final double RISE = 1.6;
    /** The Skyhook's reach. */
    static final double REACH = 6;

    private enum Phase { SWOOP, CLIMB, DONE }

    private final ServerPlayer flyer;
    private final LivingEntity ward;
    private final ServerLevel level;
    private Phase phase;
    private Vec3 direction;
    private int age, climb, stuck;

    FlightRescue(ServerPlayer flyer, LivingEntity ward) {
        this.flyer = flyer;
        this.ward = ward;
        this.level = flyer.serverLevel();
        FlightService.launch(flyer);
        if (ward != flyer) {
            phase = Phase.SWOOP;
            ArmPosePayload.show(flyer, ArmPose.FLIGHT_BRACE, SWOOP_TICKS);
        } else {
            climb();
        }
    }

    private boolean wardHere() {
        return ward.isAlive() && !ward.isRemoved() && ward.level() == level;
    }

    @Override
    public boolean tick() {
        if (phase == Phase.DONE) return true;
        age++;
        if (!flyer.isAlive() || flyer.level() != level) {
            finish();
            return true;
        }
        flyer.resetFallDistance();
        if (phase == Phase.SWOOP) swoop();
        else if (phase == Phase.CLIMB) climbStep();
        if (ward != flyer && wardHere()) ward.resetFallDistance();
        return phase == Phase.DONE;
    }

    @Override
    public void stop() {
        if (phase != Phase.DONE) finish();
    }

    private void swoop() {
        if (!wardHere()) {
            climb();
            return;
        }
        // Aim just above the ward: the flyer stays airborne and takes them from above.
        Vec3 target = ward.getBoundingBox().getCenter().add(0, ward.getBbHeight() * 0.5 + 1.2, 0);
        Vec3 offset = target.subtract(flyer.position());
        if (offset.length() <= REACH && EntityCarryService.canPickUp(flyer, ward, REACH)
                || offset.length() <= 1.5 || age >= SWOOP_TICKS) {
            grab();
            return;
        }
        double speed = Math.min(SWOOP, Math.max(0.6, offset.length() - 1));
        Vec3 start = flyer.position();
        Vec3 step = SpeedService.swept(flyer, offset.normalize().scale(speed)).subtract(start);
        if (step.lengthSqr() < 0.04) {
            // Something in the way (a tree, a roof edge): rise over it.
            step = SpeedService.swept(flyer, new Vec3(0, Math.min(SWOOP, 2 + stuck), 0)).subtract(start);
            stuck++;
        } else {
            stuck = 0;
        }
        drive(step);
    }

    private void grab() {
        Vec3 catchAt = ward.getBoundingBox().getCenter();
        boolean carried = wardHere() && EntityCarryService.pickUp(flyer, ward, REACH);
        if (carried) {
            SetPiecePayload.send(level, Piece.FLIGHT_RESCUE, catchAt, ward.getBoundingBox().getCenter(), .8F, null);
            level.playSound(null, flyer.blockPosition(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, .9F, 1.4F);
            ward.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 12, 0));
        }
        // Out of reach or too big to lift: the downdraft still flings everything off them.
        gust(wardHere() ? ward.position() : flyer.position(), 9);
        climb();
    }

    /** Straight out and up, away from the fight, at Skyburst speed. */
    private void climb() {
        phase = Phase.CLIMB;
        climb = 0;
        Vec3 from = wardHere() ? ward.position() : flyer.position();
        Vec3 away = Danger.away(from, Danger.threats(flyer, ward, 24), flyer.getLookAngle());
        direction = new Vec3(away.x, RISE, away.z).normalize();
        Vec3 center = FlightVisualGeometry.bodyCenter(flyer.position());
        SetPiecePayload.send(level, Piece.FLIGHT_SKYBURST, center, center.add(direction), 6.2F, null);
        level.playSound(null, center.x, center.y, center.z, ModSounds.SKYBURST_BOOM.get(), SoundSource.PLAYERS, 1, .82F);
        level.playSound(null, center.x, center.y, center.z, ModSounds.FLIGHT_BOOST.get(), SoundSource.PLAYERS, 1.15F, 1.1F);
        CameraShakePayload.sendAround(level, center, 64, .55F, 10);
        ArmPosePayload.show(flyer, ward != flyer && EntityCarryService.isCarrying(flyer) ? ArmPose.TWO_HANDS
                : ArmPose.FLIGHT_BRACE, CLIMB_TICKS);
        if (ward == flyer) gust(flyer.position(), 7);
    }

    private void climbStep() {
        double progress = climb++ / (double) (CLIMB_TICKS - 1);
        double speed = PEAK + (EXIT - PEAK) * progress * progress;
        Vec3 start = flyer.position();
        Vec3 step = SpeedService.swept(flyer, direction.scale(speed),
                point -> EntityCarryService.canTravelTo(flyer, point)).subtract(start);
        if (step.lengthSqr() < 1.0E-4 || climb >= CLIMB_TICKS) {
            drive(direction.scale(EXIT).multiply(1, 0, 1));
            finish();
            return;
        }
        drive(step);
    }

    private void finish() {
        if (phase == Phase.DONE) return;
        phase = Phase.DONE;
        ArmPosePayload.clear(flyer);
        if (ward != flyer && wardHere()) {
            ward.resetFallDistance();
            if (!ward.hasEffect(MobEffects.SLOW_FALLING)) ward.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 8, 0));
            if (ward instanceof ServerPlayer saved && EntityCarryService.isBeingCarried(saved)) {
                saved.displayClientMessage(Component.translatable("intro.heroesevolved.flight.carried",
                        flyer.getDisplayName()), true);
            }
        }
    }

    /** One tick of server-chosen flight, in full precision. */
    private void drive(Vec3 velocity) {
        flyer.resetFallDistance();
        flyer.setDeltaMovement(velocity);
        if (flyer instanceof FakePlayer) {
            // Nothing on a test's fake client will move it.
            flyer.setPos(flyer.position().add(velocity));
        } else {
            PacketDistributor.sendToPlayer(flyer, new SkyburstMotionPayload(velocity));
        }
    }

    /** The downdraft of the takeoff throws the ward's attackers clear. */
    private void gust(Vec3 center, double radius) {
        List<LivingEntity> threats = Danger.threats(flyer, ward, radius);
        for (LivingEntity threat : threats) {
            Vec3 away = threat.position().subtract(center).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            threat.setDeltaMovement(away.scale(1.5).add(0, .55, 0));
            threat.hurtMarked = true;
            if (threat instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        FlightService.showRelease(level, center.add(0, .2, 0), new Vec3(0, 1, 0), 2.2F);
    }
}
