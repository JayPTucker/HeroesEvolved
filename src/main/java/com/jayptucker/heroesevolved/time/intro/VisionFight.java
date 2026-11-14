package com.jayptucker.heroesevolved.time.intro;

import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity.Pose;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity.Role;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Ground zero in the Time vision, as the visitors watch it from the vantage: Peter Petrelli and Sylar fighting in the sky
 * over Kirby Plaza ({@link SkyFight#VISION}), Peter hurling Sylar into the tower west of the plaza (a real crater, put
 * back when the fireball covers it), diving to Ted Sprague and trying to talk him down, and then the twist: the time
 * traveler whose power this vision is (a double of that player, with their skin and their name over their head) folds
 * in beside Peter and takes him away the instant before the light falls. The powers' own effects draw it: Flight's
 * pressure fronts and landings, Elle's lightning, the Watchmaker's telekinetic grip, the Space-Time rings and fold.
 */
final class VisionFight {
    private static final SkyFight.Script S = SkyFight.VISION;
    /** When the traveler arrives, takes hold of Peter, and the two of them are gone. */
    static final int TRAVELER_ARRIVES = 588, TRAVELER_TAKES = 612, TRAVELER_GONE = 620;

    private final ServerLevel city;
    private final String tag;
    private final Supplier<List<ServerPlayer>> viewers;
    private final BiConsumer<StoryActorEntity, String> say;
    /** Who the traveler is: the player whose power the vision is (or, if they've gone, someone still watching). */
    private final Supplier<ServerPlayer> traveler;
    private StoryActorEntity peter, sylar, ted, double_;
    private final Vec3 impact;
    private BlockPos craterMin, craterMax;

    private VisionFight(ServerLevel city, String tag, Supplier<List<ServerPlayer>> viewers, BiConsumer<StoryActorEntity, String> say,
                        Supplier<ServerPlayer> traveler) {
        this.city = city;
        this.tag = tag;
        this.viewers = viewers;
        this.say = say;
        this.traveler = traveler;
        this.impact = findImpact(city);
        ted = StoryActorEntity.spawn(city, Role.TED, IntroBlast.CENTER, 0, tag);
        if (ted != null) ted.pose(Pose.KNEEL);
        Vec3[] at = SkyFight.at(S, 0, impact);
        peter = StoryActorEntity.spawn(city, Role.PETER, at[0], 90, tag);
        sylar = StoryActorEntity.spawn(city, Role.SYLAR, at[1], 270, tag);
        for (StoryActorEntity flyer : new StoryActorEntity[] {peter, sylar}) if (flyer != null) { flyer.glide(flyer.position(), null); flyer.pose(Pose.HOVER); }
    }

    static VisionFight start(ServerLevel city, String tag, Supplier<List<ServerPlayer>> viewers, BiConsumer<StoryActorEntity, String> say,
                             Supplier<ServerPlayer> traveler) {
        return new VisionFight(city, tag, viewers, say, traveler);
    }

    /**
     * Where Sylar hits the tower: straight on from where Peter throws him, at the first solid block. The far view draws
     * the script's {@code impactGuess}; this is the same wall.
     */
    static Vec3 findImpact(ServerLevel city) {
        Vec3 from = SkyFight.at(S, S.throwAt() + SkyFight.GRAB + SkyFight.WIND, S.impactGuess())[1];
        Vec3 dir = S.impactGuess().subtract(from).normalize();
        BlockHitResult hit = city.clip(new ClipContext(from, from.add(dir.scale(48)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(dir.scale(0.4)) : S.impactGuess();
    }

    Vec3 impact() { return impact; }

    void tick(int t) {
        if (t <= S.landAt()) fly(t);
        for (int k = 0; k < S.passes(); k++) {
            if (t == S.clashAt(k)) clash(k);
            if (t == S.clashAt(k) + 6 && !SkyFight.peterWins(k) && sylar != null && peter != null)
                SetPiecePayload.send(city, Piece.FLIGHT_SKYBURST, peter.position().add(0, 1, 0),
                        peter.position().add(peter.position().subtract(sylar.position()).normalize()).add(0, 1, 0), 3, null);
        }
        if (t == S.throwAt() + SkyFight.GRAB && peter != null) city.playSound(null, peter.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 3, 0.7F);
        if (t == S.throwAt() + SkyFight.GRAB + SkyFight.WIND && peter != null && sylar != null) {
            Vec3 from = sylar.position().add(0, 1, 0);
            SetPiecePayload.send(city, Piece.FLIGHT_SKYBURST, from, from.add(impact.subtract(from).normalize()), 5, null);
            city.playSound(null, sylar.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 4, 0.6F);
        }
        if (t == S.impactAt()) crash();
        if (t == S.landAt() && peter != null) {
            peter.land();
            peter.teleportTo(SkyFight.LANDING.x, SkyFight.LANDING.y, SkyFight.LANDING.z);
            peter.pose(Pose.IDLE);
            SetPiecePayload.send(city, Piece.FLIGHT_IMPACT, SkyFight.LANDING, SkyFight.LANDING.add(0, 1, 0), 1.8F, null);
            city.playSound(null, BlockPos.containing(SkyFight.LANDING), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 1.5F);
            shake(0.1F, 16);
        }
        if (ted != null && t == TimeIntroService.GLOW_TICK) ted.pose(Pose.GLOW);
        talk(t);
    }

    private void fly(int t) {
        Vec3[] at = SkyFight.at(S, t, impact);
        int[] moves = SkyFight.moves(S, t);
        if (peter != null && t < S.landAt()) {
            Vec3 look = at[1] != null ? at[1] : IntroBlast.CENTER;
            peter.glide(at[0], look);
            peter.pose(pose(moves[0]));
        }
        if (sylar != null && at[1] != null) {
            sylar.glide(at[1], at[0]);
            sylar.pose(pose(moves[1]));
        }
    }

    private static Pose pose(int move) {
        return switch (move) {
            case 1 -> Pose.FLY;
            case 2 -> Pose.TUMBLE;
            case 3 -> Pose.STRIKE;
            case 4 -> Pose.REACH;
            default -> Pose.HOVER;
        };
    }

    /** Pass {@code k} meets: Peter's fist and Elle's lightning, or Sylar's grip from across the sky. */
    private void clash(int k) {
        if (peter == null || sylar == null) return;
        Vec3 p = peter.position().add(0, 1.1, 0), q = sylar.position().add(0, 1.1, 0), mid = p.add(q).scale(0.5);
        if (SkyFight.peterWins(k)) {
            SetPiecePayload.send(city, Piece.FLIGHT_SKYBURST, mid, mid.add(q.subtract(p).normalize()), 4, null);
            SetPiecePayload.send(city, Piece.ELLE_BOLT, p.add(peter.getLookAngle().scale(0.6)), q, 1.2F, null);
            city.playSound(null, BlockPos.containing(mid), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 3, 1.4F);
            city.playSound(null, BlockPos.containing(mid), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3, 1.3F);
        } else {
            SetPiecePayload.send(city, Piece.WATCH_HOLD, p, sylar.getEyePosition(), 0.3F, peter);
            city.playSound(null, BlockPos.containing(mid), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 3, 0.6F);
        }
        shake(0.05F, 10);
    }

    /** Sylar into the tower: the wall caves in round him, glass and stone burst out, and he doesn't come out. */
    private void crash() {
        if (sylar != null) { sylar.discard(); sylar = null; }
        BlockPos centre = BlockPos.containing(impact);
        craterMin = centre.offset(-3, -3, -3);
        craterMax = centre.offset(3, 3, 3);
        BlockState first = null;
        for (BlockPos pos : BlockPos.betweenClosed(craterMin, craterMax)) {
            if (pos.getY() <= FractureCity.STREET_Y || pos.distSqr(centre) > 2.6 * 2.6) continue;
            BlockState state = city.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(city, pos) < 0) continue;
            if (first == null) first = state;
            city.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        Vec3 out = impact.subtract(S.centre()).multiply(-1, 0, -1).normalize();
        SetPiecePayload.send(city, Piece.FLIGHT_IMPACT, impact, impact.add(out), 3.2F, null);
        city.playSound(null, centre, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6, 0.8F);
        city.playSound(null, centre, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 5, 0.7F);
        ParticleOptions rubble = new BlockParticleOption(ParticleTypes.BLOCK, first != null ? first : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        for (ServerPlayer viewer : viewers.get()) {
            city.sendParticles(viewer, ParticleTypes.EXPLOSION_EMITTER, true, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
            city.sendParticles(viewer, rubble, true, impact.x, impact.y, impact.z, 120, 1.6, 1.6, 1.6, 0.4);
            city.sendParticles(viewer, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, impact.x, impact.y, impact.z, 30, 1.2, 1.2, 1.2, 0.02);
        }
        shake(0.18F, 24);
    }

    /** On the ground beside Ted: Peter talks to him until the traveler comes for him. */
    private void talk(int t) {
        if (peter != null && ted != null && t > S.landAt() && t < TRAVELER_ARRIVES && t % 20 == 0) peter.face(ted.getEyePosition());
        switch (t) {
            case 282 -> { if (peter != null) { peter.pose(Pose.REACH); speak(peter, "Ted! Ted, look at me!"); } }
            case 322 -> speak(ted, "Stay back! I can't hold it!");
            case 368 -> speak(peter, "Breathe. You can control this. I've held it before.");
            case 418 -> speak(ted, "It's burning... everything's burning!");
            case 466 -> speak(peter, "I'm not leaving you. We do this together.");
            case 516 -> speak(ted, "Peter, it's too much. Run!");
            case 562 -> speak(peter, "I've got you. Hold on to me!");
            case TRAVELER_ARRIVES -> travelerArrives();
            case TRAVELER_ARRIVES + 3 -> speak(double_, "Peter! It's too late. Come with me!");
            case TRAVELER_ARRIVES + 10 -> {
                if (peter != null && double_ != null) peter.face(double_.getEyePosition());
                speak(peter, "Who... who are you?");
            }
            case TRAVELER_ARRIVES + 18 -> speak(double_, "Someone who's already seen how this ends.");
            case TRAVELER_TAKES -> {
                if (double_ != null) double_.pose(Pose.REACH);
                speak(peter, "No! Ted-");
            }
            case TRAVELER_GONE -> travelerGone();
            case TRAVELER_GONE + 8 -> speak(ted, "I'm sorry...");
            default -> { }
        }
    }

    /** The twist: the time traveler, from further along than now, folds in beside Peter in their power's violet rings. */
    private void travelerArrives() {
        ServerPlayer who = traveler.get();
        if (peter == null || who == null) return;
        Vec3 at = SkyFight.LANDING.add(1.5, 0, 1.4);
        double_ = StoryActorEntity.spawn(city, Role.TRAVELER, at, 0, tag);
        if (double_ == null) return;
        double_.doubleOf(who.getUUID(), who.getGameProfile().getName());
        double_.face(peter.position());
        SetPiecePayload.send(city, Piece.TIME_SECOND, at, at.add(0, 1, 0), 1, double_);
        SetPiecePayload.send(city, Piece.TIME_SNAP, at.add(0, 1, 0), at.add(0, 2, 0), 1.6F, null);
        city.playSound(null, BlockPos.containing(at), ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 3, 1.1F);
    }

    /** Space folds shut on the two of them: gone, a heartbeat before the light. */
    private void travelerGone() {
        // Without a traveler (nobody left to be one), Peter stays with Ted to the end, as he said he would.
        if (double_ == null) return;
        for (StoryActorEntity who : new StoryActorEntity[] {peter, double_}) {
            if (who == null) continue;
            Vec3 at = who.position().add(0, 1, 0);
            SetPiecePayload.send(city, Piece.TIME_SNAP, at, at.add(0, 1, 0), 1.6F, null);
            who.discard();
        }
        city.playSound(null, BlockPos.containing(SkyFight.LANDING), ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 3, 0.8F);
        peter = null;
        double_ = null;
    }

    private void speak(StoryActorEntity who, String line) { if (who != null) say.accept(who, line); }

    private void shake(float strength, int ticks) {
        for (ServerPlayer viewer : viewers.get()) PacketDistributor.sendToPlayer(viewer, new CameraShakePayload(strength, ticks));
    }

    /** The tower whole again (the fireball covers the moment), and nothing of the crater left for the next visit. */
    void restore() {
        if (craterMin == null) return;
        FractureCity.rebuild(city, craterMin.getX(), craterMin.getZ(), craterMax.getX(), craterMax.getZ());
        craterMin = craterMax = null;
    }

    void end() {
        for (StoryActorEntity who : new StoryActorEntity[] {peter, sylar, ted, double_}) if (who != null) who.discard();
        peter = sylar = ted = double_ = null;
        restore();
    }
}
