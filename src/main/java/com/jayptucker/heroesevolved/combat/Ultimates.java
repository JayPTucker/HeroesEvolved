package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.network.UltimateCameraPayload;
import com.jayptucker.heroesevolved.sounds.PowerCue;
import com.jayptucker.heroesevolved.sounds.PowerSounds;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The class Ultimates: every power's Offense, Defense and Support ability, each a showpiece with a long cooldown.
 *
 * <p>Every Ultimate is a {@link Run} on the shared {@link Signatures} runner (so it ticks on the server tick with its
 * {@code ServerPlayer} kept, and is cancelled quietly on death, logout, a change of dimension or a dampened power),
 * keyed so a player has one going at a time. This class adds what a cinematic needs on top:</p>
 * <ul>
 *   <li><b>A held foe.</b> An Offense Ultimate takes the foe in front of you and holds it for the sequence (its mind
 *       switched off, as a finisher does), moving it where the beats say ({@link Run#place}, {@link Run#lift}),
 *       always swept, never into a wall. A boss takes the blows but is never held, moved or thrown.</li>
 *   <li><b>A camera.</b> {@link Cam} is a few keyframes in the Ultimate's own frame (its start and facing); your client
 *       glides between them with letterbox bars and your feet still ({@code client/UltimateCamera}).</li>
 *   <li><b>The caster's body.</b> Glided along a path ({@link Takedowns#glide}), held up, hidden and shown again.</li>
 *   <li><b>Protection.</b> While a cinematic plays, the held foe can't hurt you and other blows are halved. Defense and
 *       Support runs answer blows to the people they protect through {@link Run#incoming} and {@link Run#lethal}.</li>
 * </ul>
 * Each power's three live in one class in its own package ({@code speed/SpeedUltimates} and so on), with the beats
 * its art reads as public constants; {@link #ULTIMATES} routes each class mechanic to its Ultimate.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Ultimates {
    /** The key every Ultimate runs under on the {@link Signatures} runner, so a player has one going at a time. */
    public static final String KEY = "ultimate";
    /** A mob whose mind an Ultimate switched off; cleared if it is ever loaded without one holding it. */
    private static final String HELD = "heroesevolved_ultimate_held";
    /** How far an Offense Ultimate reaches for its foe. */
    public static final double REACH = 9;

    public enum Kind { OFFENSE, DEFENSE, SUPPORT }

    /** Builds a run: the foe is the one an Offense Ultimate picked (null for Defense and Support). */
    @FunctionalInterface public interface Factory { Run make(ServerPlayer player, int tier, LivingEntity foe); }

    /** One Ultimate: whether it needs a foe, and how to begin it. */
    public record Ultimate(Kind kind, Factory factory, double reach) {
        public static Ultimate offense(Factory f) { return new Ultimate(Kind.OFFENSE, f, REACH); }
        public static Ultimate offense(Factory f, double reach) { return new Ultimate(Kind.OFFENSE, f, reach); }
        public static Ultimate defense(Factory f) { return new Ultimate(Kind.DEFENSE, f, 0); }
        public static Ultimate support(Factory f) { return new Ultimate(Kind.SUPPORT, f, 0); }
    }

    /** Every class mechanic and its Ultimate. */
    private static final Map<String, Ultimate> ULTIMATES = UltimateTable.build();

    /** Runs going now (also on the {@link Signatures} runner, which ticks them); the damage hooks walk this. */
    private static final List<Run> LIVE = new ArrayList<>();

    private Ultimates() {}

    public static boolean handles(String mechanic) { return ULTIMATES.containsKey(mechanic); }

    public static Ultimate of(String mechanic) { return ULTIMATES.get(mechanic); }

    public static java.util.Set<String> mechanics() { return ULTIMATES.keySet(); }

    public static boolean running(ServerPlayer player) { return Signatures.running(KEY, player); }

    public static Run run(ServerPlayer player) { return Signatures.get(KEY, player); }

    public static boolean canUse(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        Ultimate u = ULTIMATES.get(mechanic);
        if (u == null || p.isPassenger() || !PowerRules.canAct(p) || running(p) || Takedowns.running(p)) return false;
        return u.kind() != Kind.OFFENSE || pick(p, u.reach()) != null;
    }

    public static boolean cast(AbilityUseContext c, String mechanic) {
        Ultimate u = ULTIMATES.get(mechanic);
        if (u == null) return false;
        ServerPlayer p = c.player();
        LivingEntity foe = u.kind() == Kind.OFFENSE ? pick(p, u.reach()) : null;
        if (u.kind() == Kind.OFFENSE && foe == null) return false;
        Run run = u.factory().make(p, Math.max(1, c.abilityLevel()), foe);
        return run != null && start(run);
    }

    /** Starts a run now (tests and previews call this with a run they built). */
    public static boolean start(Run run) {
        if (!Signatures.start(KEY, run)) return false;
        LIVE.add(run);
        try {
            if (run.foe != null) run.hold(run.foe);
            run.open();
        } catch (RuntimeException broken) {
            HeroesEvolved.LOGGER.error("Ultimate {} failed to open", run.mechanic, broken);
            Signatures.end(KEY, run.player);
            run.close(true);
            return false;
        }
        return true;
    }

    /**
     * The foe an Offense Ultimate takes: the one under the crosshair within {@code reach}, else the nearest in a
     * 40-degree cone in front with a clear line, else none.
     */
    public static LivingEntity pick(ServerPlayer p, double reach) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity t : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1),
                e -> StyleOps.isFoe(p, e) && e.isAlive())) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > reach + t.getBbWidth() * .5) continue;
            double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
            if (angle > 40 || !Signatures.clear(p.serverLevel(), eye, t.getBoundingBox().getCenter())) continue;
            double score = angle * .15 + d;
            if (score < bestScore) { bestScore = score; best = t; }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // A run
    // ------------------------------------------------------------------

    /** One Ultimate playing out. {@link #beat} is called every tick from age 1; it ends after {@link #length}. */
    public abstract static class Run extends Signatures.Run {
        public final String mechanic;
        public final String power;
        /** Where the caster stood and faced (level) when it began; the frame every beat and camera key is measured in. */
        public final Vec3 origin, facing, side;
        /** The held foe of an Offense Ultimate (null for the rest). */
        public LivingEntity foe;
        /** Where the held foe is kept this tick. */
        protected Vec3 anchor;
        private boolean aiOff, held, glided, floating, hidden, foeHidden, closed, cinematic;

        protected Run(ServerPlayer player, int tier, String power, String mechanic, LivingEntity foe) {
            super(player, tier);
            this.power = power;
            this.mechanic = mechanic;
            this.foe = foe;
            this.origin = player.position();
            Vec3 f = foe != null ? foe.position().subtract(origin).multiply(1, 0, 1) : player.getLookAngle().multiply(1, 0, 1);
            this.facing = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
            this.side = new Vec3(-facing.z, 0, facing.x);
        }

        /** How many ticks it runs. */
        protected abstract int length();

        /** How many ticks it runs (for the admin console and tests). */
        public final int ticks() { return length(); }

        /** What happens at {@code age} (1 on the first tick after it was cast). */
        protected abstract void beat(int age);

        /** The moment it is cast (age 0): the opening piece, the camera, the first pose and sound. */
        protected void open() {}

        /** It ran to the end. */
        protected void finish() {}

        /** Tidy-up after either ending (attribute modifiers, wards); {@code broken} if it was cut short. */
        protected void tidy(boolean broken) {}

        /**
         * A blow on someone, while this run is going: change it or cancel it. Called for every live run, so check the
         * victim is yours to protect. The cinematic's own protection of the caster has already been applied.
         */
        protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {}

        /** A blow that would take the rest of {@code victim}'s health: return the damage that should land instead. */
        protected float lethal(LivingEntity victim, DamageSource source, float damage) { return damage; }

        @Override protected final boolean tick() {
            if (held && foe != null) {
                if (!foe.isAlive() || foe.isRemoved() || foe.level() != level) release();
                else pin();
            }
            if (cinematic) player.resetFallDistance();
            if (hidden && age % 8 == 0) sendHide();
            if (foeHidden && foe != null && age % 8 == 0) SetPiecePayload.sendSeeded(level, Piece.ULT_HIDE, foe.position(), foe.position(), 1, foe, 0, 0);
            beat(age);
            if (age >= length()) {
                close(false);
                return false;
            }
            return true;
        }

        @Override protected final void cancel() { close(true); }

        final void close(boolean broken) {
            if (closed) return;
            closed = true;
            LIVE.remove(this);
            release();
            if (hidden) show();
            if (foeHidden) hideFoe(false);
            if (floating) Takedowns.hover(player, false);
            if (glided) Takedowns.still(player);
            if (cinematic && player.connection != null)
                PacketDistributor.sendToPlayer(player, UltimateCameraPayload.end(player.getId()));
            if (broken) {
                ArmPosePayload.clear(player);
                SetPiecePayload.sendSeeded(level, Piece.ULT_CUT, player.position(), player.position(), 1, player, 0, 0);
                if (foe != null) SetPiecePayload.sendSeeded(level, Piece.ULT_CUT, foe.position(), foe.position(), 1, foe, 0, 0);
            }
            try {
                tidy(broken);
                if (!broken) finish();
            } catch (RuntimeException e) {
                HeroesEvolved.LOGGER.error("Ultimate {} failed to end", mechanic, e);
            }
        }

        // ---------------------------------------------------------------- the frame

        /** A point in the run's frame: {@code f} blocks along its facing, {@code s} to the right, {@code u} up. */
        public Vec3 at(double f, double s, double u) { return origin.add(facing.scale(f)).add(side.scale(s)).add(0, u, 0); }

        /** A point in the frame around another point. */
        public Vec3 around(Vec3 base, double f, double s, double u) { return base.add(facing.scale(f)).add(side.scale(s)).add(0, u, 0); }

        /** Where the foe is now (its start if it is gone). */
        public Vec3 foeAt() { return foe != null ? foe.position() : origin.add(facing.scale(3)); }

        public Vec3 foeCentre() { return foe != null ? foe.getBoundingBox().getCenter() : foeAt().add(0, 1, 0); }

        public int foeId() { return foe != null ? foe.getId() : -1; }

        // ---------------------------------------------------------------- the held foe

        void hold(LivingEntity target) {
            foe = target;
            anchor = target.position();
            base = anchor;
            if (Signatures.boss(target)) return;
            held = true;
            if (target instanceof Mob mob && !mob.isNoAi()) {
                mob.setNoAi(true);
                mob.getPersistentData().putBoolean(HELD, true);
                aiOff = true;
            }
        }

        /** Whether the foe is held (a boss never is). */
        public boolean held() { return held; }

        /** Lets the foe go now (it falls, walks and fights again). */
        public void release() {
            held = false;
            if (aiOff && foe instanceof Mob mob) {
                mob.setNoAi(false);
                mob.getPersistentData().remove(HELD);
            }
            aiOff = false;
        }

        private void pin() {
            foe.setDeltaMovement(Vec3.ZERO);
            foe.resetFallDistance();
            if (foe.position().distanceToSqr(anchor) < 1e-4) return;
            if (foe instanceof ServerPlayer other && other.connection != null)
                other.connection.teleport(anchor.x, anchor.y, anchor.z, other.getYRot(), other.getXRot());
            else foe.teleportTo(anchor.x, anchor.y, anchor.z);
            foe.absMoveTo(anchor.x, anchor.y, anchor.z);
        }

        /** Moves the held foe toward {@code to}, swept so it stops at a wall; returns where it is. */
        public Vec3 place(Vec3 to) {
            if (!held || foe == null) return foeAt();
            Vec3 step = to.subtract(anchor);
            if (step.lengthSqr() < 1e-6) return anchor;
            anchor = SpeedService.sweptFrom(foe, anchor, step, p -> true, false);
            pin();
            return anchor;
        }

        /** Holds the foe {@code height} above where it was first taken, lower under a ceiling. */
        public Vec3 lift(double height) {
            if (!held || foe == null) return foeAt();
            return place(new Vec3(anchor.x, base.y + Math.max(0, height), anchor.z));
        }

        /** Where the foe stood when it was taken. */
        protected Vec3 base;

        /** Drops the foe straight to the ground under it (a slam); returns where it lands. */
        public Vec3 slam() {
            if (!held || foe == null) return foeAt();
            Vec3 ground = StyleOps.ground(level, anchor.add(0, .2, 0));
            return place(ground);
        }

        /** Lets the foe go and throws it (bosses keep their footing). */
        public void throwFoe(Vec3 velocity) {
            release();
            if (foe != null) Signatures.fling(foe, velocity);
        }

        // ---------------------------------------------------------------- the caster

        /** One tick of the caster along a path: {@code now} this tick, {@code next} the next (swept, smooth). */
        public void glide(Vec3 now, Vec3 next) {
            glided = true;
            Takedowns.glide(player, now, next);
        }

        /** Stops a glide where it is. */
        public void still() {
            if (glided) Takedowns.still(player);
        }

        /** Holds the caster up in the air (or lets them fall). Always undone when the run ends. */
        public void hover(boolean on) {
            floating = on;
            Takedowns.hover(player, on);
            if (on) player.setDeltaMovement(Vec3.ZERO);
        }

        /** Moves the caster at once, never into a wall: as far toward {@code to} as is clear. */
        public Vec3 blink(Vec3 to) {
            Vec3 at = SpeedService.safe(player, to) ? to : SpeedService.sweptFrom(player, player.position(), to.subtract(player.position()), p -> true, true);
            StyleHits.blink(player, at);
            return at;
        }

        /** Hides the caster's body (a speedster gone round the world, a shifter under a beast) until {@link #show}. */
        public void hide() {
            hidden = true;
            sendHide();
        }

        public void show() {
            hidden = false;
            SetPiecePayload.sendSeeded(level, Piece.ULT_SHOW, player.position(), player.position(), 1, player, 0, 0);
        }

        private void sendHide() {
            SetPiecePayload.sendSeeded(level, Piece.ULT_HIDE, player.position(), player.position(), 1, player, 0, 0);
        }

        /** Hides the held foe's body (gone through a breach) or shows it again. */
        public void hideFoe(boolean on) {
            if (foe == null) return;
            foeHidden = on;
            SetPiecePayload.sendSeeded(level, on ? Piece.ULT_HIDE : Piece.ULT_SHOW, foe.position(), foe.position(), 1, foe, 0, 0);
        }

        /** Turns the caster to look at a point. */
        public void face(Vec3 at) { Takedowns.face(player, at); }

        // ---------------------------------------------------------------- camera, pieces, poses, sound

        /** Starts the camera path (only the caster sees it; off in their settings, they keep their own view). */
        public void cinema(Cam cam) {
            cinematic = true;
            if (player.connection != null) PacketDistributor.sendToPlayer(player, cam.payload(player.getId(), length()));
        }

        /** A short hero shot for a Defense or Support Ultimate: the view swings out and round you, then hands back. */
        public void heroShot(int ticks, double height) {
            Cam cam = cam()
                    .key(0, -3.2, 1.4, 1.9, 0, 0, 1.3)
                    .key(ticks / 2, -4.2, 3.6, 2.4 + height * .3, 0, 0, 1.2 + height * .3)
                    .key(ticks, -5.5, 5.2, 3.2 + height * .5, 0, 0, 1.4 + height * .3)
                    .until(ticks);
            cinema(cam);
        }

        /** A camera path in this run's frame. */
        public Cam cam() { return new Cam(origin, facing); }

        /** Shows a piece bound to the caster, the foe's id in the seed so the art can find both bodies. */
        public void piece(Piece piece, Vec3 from, Vec3 to, float scale) {
            SetPiecePayload.sendSeeded(level, piece, from, to, scale, player, 0, foeId());
        }

        public void piece(Piece piece, Vec3 from, Vec3 to, float scale, Entity bound, int seed) {
            SetPiecePayload.sendSeeded(level, piece, from, to, scale, bound, 0, seed);
        }

        public void pose(ArmPose pose, int ticks) { ArmPosePayload.show(player, pose, ticks); }

        public void sound(Vec3 at, SoundEvent sound, float volume, float pitch) {
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
        }

        /** The power's own heavy cue, where you stand. */
        /** The power's heavy cue as the Ultimate opens, for everyone near (the press itself makes none). */
        public void cue() { cue(false); }

        /** The heavy cue; {@code alone}, only the caster hears it (a stealthy Ultimate). */
        public void cue(boolean alone) { PowerSounds.play(player, power, PowerCue.HEAVY, 1.2F, alone); }

        public void shake(Vec3 at, double radius, float strength, int ticks) {
            CameraShakePayload.sendAround(level, at, radius, strength, ticks);
        }

        // ---------------------------------------------------------------- damage

        /** A blow from the caster (a body blow, with the cooldown reset so each beat lands). */
        public void hit(LivingEntity target, float amount) {
            if (target == null || !target.isAlive()) return;
            StyleOps.hurt(player, target, amount);
        }

        /** A blow of power rather than a body (magic, for powers that don't touch). */
        public void zap(LivingEntity target, float amount) {
            if (target == null || !target.isAlive()) return;
            StyleOps.magic(player, target, amount);
        }

        /** Every foe within {@code radius} of a point but the held one, nearest first. */
        public List<LivingEntity> others(Vec3 at, double radius) {
            List<LivingEntity> list = new ArrayList<>(StyleOps.foes(player, at, radius));
            list.remove(foe);
            return list;
        }

        /** Hits every other foe within {@code radius} of a point, then does {@code then} to each. */
        public List<LivingEntity> splash(Vec3 at, double radius, float amount, Consumer<LivingEntity> then) {
            List<LivingEntity> hit = others(at, radius);
            for (LivingEntity t : hit) {
                hit(t, amount);
                if (then != null) then.accept(t);
            }
            return hit;
        }

        /** The caster and their friends within {@code radius}. */
        public List<LivingEntity> friends(double radius) { return StyleOps.friends(player, player.position(), radius); }

        /** Hostile shots within {@code radius} of a point. */
        public List<Projectile> shots(Vec3 at, double radius) { return StyleOps.incoming(player, at, radius); }

        /** A shove away from a point, unless the body is a boss. */
        public void shove(LivingEntity target, Vec3 from, double speed, double lift) {
            if (!Signatures.boss(target)) StyleOps.shove(target, from, speed, lift);
        }

        /** Seconds this run has gone on (for things that pulse every second). */
        public boolean everySecond() { return age % 20 == 0; }
    }

    // ------------------------------------------------------------------
    // The camera path
    // ------------------------------------------------------------------

    /**
     * A camera path for one Ultimate: keys at ticks, each an eye and a point to look at, measured in the run's frame
     * ({@code f} along its facing, {@code s} to the right, {@code u} up from where it began), or following a body (the
     * eye kept at an offset from it, looking at it). The client eases between keys.
     */
    public static final class Cam {
        private final Vec3 origin, facing, side;
        private final List<UltimateCameraPayload.Key> keys = new ArrayList<>();
        private int until = -1;

        Cam(Vec3 origin, Vec3 facing) {
            this.origin = origin;
            this.facing = facing;
            this.side = new Vec3(-facing.z, 0, facing.x);
        }

        private Vec3 at(double f, double s, double u) { return origin.add(facing.scale(f)).add(side.scale(s)).add(0, u, 0); }

        /** The eye at (f, s, u), looking at (lf, ls, lu), from {@code tick}. */
        public Cam key(int tick, double f, double s, double u, double lf, double ls, double lu) {
            keys.add(new UltimateCameraPayload.Key(tick, at(f, s, u), at(lf, ls, lu), -1, false));
            return this;
        }

        /** The eye at absolute points. */
        public Cam key(int tick, Vec3 eye, Vec3 look) {
            keys.add(new UltimateCameraPayload.Key(tick, eye, look, -1, false));
            return this;
        }

        /** The eye at (f, s, u) in the frame, looking at {@code body} wherever it goes. */
        public Cam watch(int tick, double f, double s, double u, Entity body) {
            keys.add(new UltimateCameraPayload.Key(tick, at(f, s, u), Vec3.ZERO, body == null ? -1 : body.getId(), false));
            return this;
        }

        /** The eye kept at an offset (f, s, u, in the frame's axes) from {@code body}, looking at it. */
        public Cam follow(int tick, double f, double s, double u, Entity body) {
            Vec3 offset = facing.scale(f).add(side.scale(s)).add(0, u, 0);
            keys.add(new UltimateCameraPayload.Key(tick, offset, Vec3.ZERO, body == null ? -1 : body.getId(), true));
            return this;
        }

        /** The view hands back at this tick (default: the run's end). */
        public Cam until(int tick) {
            until = tick;
            return this;
        }

        UltimateCameraPayload payload(int player, int length) {
            List<UltimateCameraPayload.Key> sorted = new ArrayList<>(keys);
            sorted.sort(Comparator.comparingInt(UltimateCameraPayload.Key::tick));
            return new UltimateCameraPayload(player, until < 0 ? length : until, sorted);
        }
    }

    // ------------------------------------------------------------------
    // Damage hooks
    // ------------------------------------------------------------------

    /** The held foe can't hurt the caster of a cinematic, other blows on them are halved, then each run has its say. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (LIVE.isEmpty()) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource source = event.getSource();
        for (Run run : List.copyOf(LIVE)) {
            if (run.closed) continue;
            if (victim == run.player && run.cinematic && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                if (run.foe != null && (source.getEntity() == run.foe || source.getDirectEntity() == run.foe)) {
                    event.setCanceled(true);
                    return;
                }
                if (source.getEntity() != run.player) event.setAmount(event.getAmount() * .5F);
            }
            try { run.incoming(event, victim); }
            catch (RuntimeException e) { HeroesEvolved.LOGGER.error("Ultimate {} failed on a blow", run.mechanic, e); }
            if (event.isCanceled()) return;
        }
    }

    /** The last word on a killing blow, for runs that keep someone alive. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void lethal(LivingDamageEvent.Pre event) {
        if (LIVE.isEmpty()) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        float health = victim.getHealth() + victim.getAbsorptionAmount();
        if (event.getNewDamage() < health) return;
        for (Run run : List.copyOf(LIVE)) {
            if (run.closed) continue;
            float after = run.lethal(victim, event.getSource(), event.getNewDamage());
            if (after != event.getNewDamage()) {
                event.setNewDamage(Math.max(0, after));
                return;
            }
        }
    }

    /** Whether an Ultimate is bending shots away from this body. */
    public static boolean deflects(LivingEntity body) {
        for (Run run : LIVE) if (!run.closed && run instanceof Deflecting d && d.deflects(body)) return true;
        return false;
    }

    /**
     * A shot that never flies as an entity (a TaCZ round) is about to hit {@code body}: if an Ultimate guards it, that
     * Ultimate answers the shot where it struck and the hit is called off. Returns whether it was.
     */
    public static boolean deflect(LivingEntity body, net.minecraft.world.entity.Entity shot) {
        for (Run run : List.copyOf(LIVE))
            if (!run.closed && run instanceof Deflecting d && d.deflects(body)) {
                d.turn(shot);
                return true;
            }
        return false;
    }

    /** A run that turns shots aside from someone (asked by {@link #deflects} and {@link #deflect}). */
    public interface Deflecting {
        boolean deflects(LivingEntity body);

        /** Answers a shot that struck the guard: the flash and sound where it hit, and the shot gone. */
        void turn(net.minecraft.world.entity.Entity shot);
    }

    /** A mob saved while an Ultimate held it (a crash mid-move) gets its mind back when it loads. */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        if (!mob.getPersistentData().getBoolean(HELD)) return;
        for (Run run : LIVE) if (run.foe == mob && run.held) return;
        mob.getPersistentData().remove(HELD);
        mob.setNoAi(false);
    }

    /** Every run going now, for the admin console. */
    public static List<Run> live() { return List.copyOf(LIVE); }

    /** Ends a player's Ultimate now, without its payoff (admin, tests). */
    public static void stop(ServerPlayer player) {
        Run run = run(player);
        if (run == null) return;
        Signatures.end(KEY, player);
        run.close(true);
    }
}
