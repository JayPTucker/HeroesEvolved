package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity.Pose;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.jayptucker.heroesevolved.story.sylar.SylarGeometry.*;

/**
 * Sylar's moves in the Collection. One idea runs through all of them: <b>he sees how it works</b>. Every telegraph is a
 * brass schematic (a ring, a line, a hand, a wedge) whose parts float apart and slide together, and the move lands as
 * the last part seats. The windup is exactly the schematic's piece length, so what you see is what you get, and every
 * spot is fixed when the windup starts, so moving beats it.
 *
 * <p>His own telekinesis is always his: Hurl, Reel In, Appraisal and the shove. Every other move draws on one of his
 * cases ({@link Move#hour}); while it winds up, that case is open, and breaking its glass backfires the move into him.</p>
 */
final class SylarMoves {
    private SylarMoves() {}

    interface Start { boolean run(SylarEntity s, ServerLevel l); }
    interface Act { void run(SylarEntity s, ServerLevel l); }
    interface Weight { int of(SylarEntity s, List<ServerPlayer> fighters); }

    /**
     * One move: its id (kept in tests and docs), the pose he winds it up in, its windup (its telegraph's length), its
     * cooldown, the case it draws on (-1 for his own; {@link #YOURS} works out its own), when he wants it, and what it
     * does when the windup starts, each tick of it, when it lands, and if it is broken off.
     */
    record Move(String id, Pose pose, int windup, int cooldown, int hour, Weight want, Start start, Act pulse, Act land, Act cancel) {
        int caseHour(SylarEntity s) { return this == YOURS ? s.yours : hour; }

        int weight(SylarEntity s, List<ServerPlayer> fighters) {
            if (hour >= 0 && !s.caseWhole(hour) && !(this == LIGHT && lightFails(s))) return 0;
            int w = want.of(s, fighters);
            if (w <= 0 || hour < 0) return w;
            // He is careful with his collection: a case with someone beside it is a case he'd rather not open.
            Vec3 at = specimen(hour);
            for (ServerPlayer p : fighters) if (p.position().distanceToSqr(at) < 4.5 * 4.5) return Math.max(1, w / 2);
            return w;
        }
    }

    private static final Act NOTHING = (s, l) -> {};

    private static float power(SylarEntity s) { return s.phase() == 1 ? 1.0F : s.phase() == 2 ? 1.15F : 1.3F; }

    static Vec3 floorAt(Vec3 p) { return new Vec3(p.x, floorY(), p.z); }

    /** Moves anyone to {@code at}, test players included (their connection ignores teleports). */
    static void place(Entity e, Vec3 at) {
        if (e instanceof ServerPlayer p) {
            p.teleportTo(p.serverLevel(), at.x, at.y, at.z, p.getYRot(), p.getXRot());
            p.absMoveTo(at.x, at.y, at.z);
        } else e.teleportTo(at.x, at.y, at.z);
        e.fallDistance = 0;
    }

    /** The fighter he aims at: whoever he can see, nearest first, and whoever hasn't been aimed at for a while. */
    private static ServerPlayer aim(SylarEntity s) {
        List<ServerPlayer> fighters = s.fighters();
        ServerPlayer best = null;
        double score = Double.MAX_VALUE;
        for (ServerPlayer p : fighters) {
            double d = s.distanceToSqr(p) + (s.hasLineOfSight(p) ? 0 : 400) + (p == s.lastAimed ? 120 : 0);
            if (d < score) { score = d; best = p; }
        }
        s.lastAimed = best;
        return best;
    }

    private static boolean lightFails(SylarEntity s) {
        return !s.triedLight && s.level() instanceof ServerLevel l && !SylarData.holds(l.getServer(), ModAbilities.RADIATION_ID)
                && !s.caseWhole(RADIATION_CASE);
    }

    // ------------------------------------------------------------------
    // His own: telekinesis
    // ------------------------------------------------------------------

    /** Hurl: he tears a slab out of the floor beside him, lifts it over his shoulder and throws it where you stood. */
    static final Move HURL = new Move("hurl", Pose.RAISED, HURL_WINDUP, 110, -1, (s, f) -> 3,
            (s, l) -> {
                ServerPlayer t = aim(s);
                if (t == null) return false;
                s.focus = t;
                s.mark = floorAt(t.position());
                if (!s.chunk.tear(s, l, t)) return false;
                SetPiecePayload.sendSeeded(l, Piece.SYLAR_HURL_MARK, s.mark, s.hand(), (float) HURL_RADIUS, null, 0, SylarEntity.colour(ModAbilities.TELEKINESIS_ID));
                s.sound(s.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.4F, 0.5F);
                s.sound(s.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 0.6F);
                return true;
            },
            NOTHING,
            (s, l) -> {
                Vec3 at = s.mark;
                s.chunk.land(l, at);
                SetPiecePayload.send(l, Piece.SYLAR_IMPACT, at, at.add(0, 1, 0), (float) HURL_RADIUS, null);
                for (LivingEntity hit : s.foesNear(at, HURL_RADIUS + 1)) {
                    if (Math.hypot(hit.getX() - at.x, hit.getZ() - at.z) > HURL_RADIUS || hit.getY() > at.y + 3) continue;
                    s.hurtFoe(hit, 8 * power(s));
                    SylarEntity.push(hit, SylarEntity.flat(hit.position().subtract(at)).scale(0.6).add(0, 0.55, 0));
                }
                CameraShakePayload.sendAround(l, at, 40, 0.7F, 12);
                s.sound(at, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 0.6F);
                s.sound(at, SoundEvents.DEEPSLATE_BREAK, 1.6F, 0.5F);
            },
            (s, l) -> s.chunk.putBack(l));

    /** Reel In: whoever climbs out of reach, keeps far off or hides is hooked and pulled down in front of him. */
    static final Move REEL = new Move("reel", Pose.ONE_HAND, REEL_WINDUP, 100, -1,
            (s, f) -> reelTarget(s, f) == null ? 0 : 6,
            (s, l) -> {
                ServerPlayer t = reelTarget(s, s.fighters());
                if (t == null) return false;
                s.focus = t;
                SetPiecePayload.sendSeeded(l, Piece.SYLAR_REEL_MARK, t.position(), s.hand(), 1, t, 0, SylarEntity.colour(ModAbilities.TELEKINESIS_ID));
                s.sound(t.position(), SoundEvents.CHAIN_PLACE, 1.4F, 0.5F);
                return true;
            },
            NOTHING,
            (s, l) -> {
                if (!(s.focus instanceof ServerPlayer t) || !t.isAlive() || t.level() != l) return;
                s.reeled = t; s.reelFrom = t.position(); s.reelAge = 0;
                s.seen(t);
                SetPiecePayload.send(l, Piece.SYLAR_REEL, s.hand(), t.getBoundingBox().getCenter(), 1, t);
                s.sound(t.position(), SoundEvents.CHAIN_BREAK, 1.2F, 0.6F);
            },
            NOTHING);

    private static ServerPlayer reelTarget(SylarEntity s, List<ServerPlayer> fighters) {
        ServerPlayer best = null;
        double height = 0;
        for (ServerPlayer p : fighters) {
            double up = p.getY() - floorY();
            boolean out = up >= REEL_HEIGHT || s.distanceTo(p) > REEL_FAR || s.unseen(p) >= REEL_UNSEEN;
            if (out && (best == null || up > height)) { best = p; height = up; }
        }
        return best;
    }

    /**
     * Appraisal, his signature: a ring under you, then you hang in the air while a schematic of your power assembles round
     * you. Struggle (sneak, again and again) or let a friend break his concentration; too slow, and your power goes into
     * one of his empty cases until somebody breaks it.
     */
    static final Move APPRAISE = new Move("appraise", Pose.ONE_HAND, APPRAISE_WINDUP, 320, -1,
            (s, f) -> s.phase() >= 2 && s.held == null && s.freeCase() && appraisable(s, f) != null ? 3 : 0,
            (s, l) -> {
                ServerPlayer t = appraisable(s, s.fighters());
                if (t == null) return false;
                s.focus = t;
                s.mark = floorAt(t.position());
                SetPiecePayload.sendSeeded(l, Piece.SYLAR_APPRAISE_MARK, s.mark, s.hand(), (float) APPRAISE_RADIUS, t, 0, powerColour(t));
                s.sound(t.position(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.0F, 0.6F);
                SylarVoice.warn(t, "story.heroesevolved.sylar.warn.appraise");
                return true;
            },
            NOTHING,
            (s, l) -> {
                if (!(s.focus instanceof ServerPlayer t) || !t.isAlive() || t.level() != l
                        || Math.hypot(t.getX() - s.mark.x, t.getZ() - s.mark.z) > APPRAISE_RADIUS + 0.3) {
                    // A miss: he overreaches.
                    s.staggerFor(20);
                    return;
                }
                grab(s, l, t);
            },
            NOTHING);

    private static ServerPlayer appraisable(SylarEntity s, List<ServerPlayer> fighters) {
        ServerPlayer best = null;
        for (ServerPlayer p : fighters) {
            if (CollectionArena.stolen(p) || power(p) == null) continue;
            if (best == null || s.distanceToSqr(p) < s.distanceToSqr(best)) best = p;
        }
        return best;
    }

    static ResourceLocation power(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(e -> e.getValue().isUnlocked()).map(Map.Entry::getKey).orElse(null);
    }

    private static int powerColour(ServerPlayer p) { return SylarEntity.colour(power(p)); }

    static void grab(SylarEntity s, ServerLevel l, ServerPlayer t) {
        s.held = t; s.holdTicks = 0; s.struggle = 0; s.heldShift = t.isShiftKeyDown();
        s.holdAt = floorAt(t.position()).add(0, HELD_HEIGHT, 0);
        s.pose(Pose.HOLD);
        SylarVoice.warn(t, "story.heroesevolved.sylar.warn.held");
        s.sound(t.position(), SoundEvents.WARDEN_ATTACK_IMPACT, 1.0F, 0.7F);
        for (ServerPlayer p : s.bar().getPlayers()) if (p != t) SylarVoice.narrate(p, "story.heroesevolved.sylar.narrate.held", t.getDisplayName());
    }

    static void tickHold(SylarEntity s, ServerLevel l) {
        ServerPlayer t = s.held;
        s.getNavigation().stop();
        if (t == null || !t.isAlive() || t.level() != l) { release(s, l, false); return; }
        s.holdTicks++;
        s.getLookControl().setLookAt(t, 30, 30);
        place(t, s.holdAt);
        t.setDeltaMovement(Vec3.ZERO);
        boolean shift = t.isShiftKeyDown();
        if (shift && !s.heldShift) s.struggle += 1;
        s.heldShift = shift;
        if (s.holdTicks % 10 == 1) {
            float progress = Math.min(1, s.holdTicks / (float) HOLD_TICKS);
            int fought = Math.min(99, (int) (s.struggle / STRUGGLE_NEEDED * 99));
            SetPiecePayload.sendSeeded(l, Piece.SYLAR_HOLD, s.holdAt, s.hand(), Math.max(0.05F, progress), t, 0, powerColour(t) * 100 + fought);
        }
        if (s.struggle >= STRUGGLE_NEEDED) {
            release(s, l, true);
            s.staggerFor(30);
            SylarVoice.warn(t, "story.heroesevolved.sylar.warn.free");
            s.sound(t.position(), SoundEvents.CHAIN_BREAK, 1.2F, 0.7F);
            return;
        }
        if (s.holdTicks >= HOLD_TICKS) take(s, l, t);
    }

    /** Too slow: what you can do goes into a case. It is still yours: break the case, or wait for the fight to end. */
    private static void take(SylarEntity s, ServerLevel l, ServerPlayer t) {
        release(s, l, false);
        s.hurtFoe(t, 6 * power(s));
        ResourceLocation power = power(t);
        if (power != null && HeroesEvolvedConfig.COMMON.sylarCanStealPowers.get()) {
            int h = s.keep(l, t, power);
            if (h >= 0) {
                CollectionArena.steal(t);
                SetPiecePayload.sendSeeded(l, Piece.SYLAR_TAKE, t.getBoundingBox().getCenter(), specimen(h), 1, null, 0, SylarEntity.colour(power));
                ScreenTintPayload.send(t, 0x50402018, 40, ScreenTintPayload.Style.VIGNETTE);
                SylarVoice.warn(t, "story.heroesevolved.sylar.warn.taken");
                SylarVoice.say(t, "story.heroesevolved.sylar.say.taken");
            }
        }
        s.sound(t.position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.6F);
        s.holdOff(30);
    }

    static void release(SylarEntity s, ServerLevel l, boolean shove) {
        ServerPlayer t = s.held;
        s.held = null; s.holdTicks = 0; s.struggle = 0;
        s.pose(Pose.NONE);
        if (t != null) {
            t.fallDistance = 0;
            if (shove) SylarEntity.push(t, SylarEntity.flat(t.position().subtract(s.position())).scale(0.8).add(0, 0.2, 0));
            t.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
        }
    }

    /** Anyone who stands too close is thrown back. */
    static void shove(SylarEntity s, ServerLevel l, LivingEntity t) {
        s.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        SetPiecePayload.send(l, Piece.SYLAR_SHOVE, s.hand(), t.getBoundingBox().getCenter(), 1, s);
        s.hurtFoe(t, 5);
        SylarEntity.push(t, SylarEntity.flat(t.position().subtract(s.position())).scale(1.1).add(0, 0.35, 0));
        s.sound(t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.7F);
    }

    // ------------------------------------------------------------------
    // From the cases
    // ------------------------------------------------------------------

    /** Arc Lattice (Electrokinesis, one o'clock): lightning strung between the cases, a line through each of you. */
    static final Move LATTICE = new Move("lattice", Pose.ONE_HAND, LATTICE_WINDUP, 150, caseOf(ModAbilities.ELECTROKINESIS_ID),
            (s, f) -> 3,
            (s, l) -> {
                List<ServerPlayer> fighters = s.fighters();
                if (fighters.isEmpty()) return false;
                int lines = s.phase() == 1 ? 2 : 3;
                Set<Long> used = new HashSet<>();
                for (int i = 0; i < lines; i++) {
                    Vec3 through = i < fighters.size() ? fighters.get(i).position()
                            : SylarGeometry.center().add((s.getRandom().nextDouble() - 0.5) * 20, 0, (s.getRandom().nextDouble() - 0.5) * 20);
                    int[] pair = chord(through, used);
                    used.add(pair[0] * 16L + pair[1]);
                    s.lines.add(new Vec3[] {post(pair[0]), post(pair[1])});
                }
                for (Vec3[] line : s.lines)
                    SetPiecePayload.sendSeeded(l, Piece.SYLAR_LATTICE_MARK, line[0], line[1], 1, null, 0, SylarEntity.colour(ModAbilities.ELECTROKINESIS_ID));
                s.sound(s.position(), SoundEvents.BEACON_ACTIVATE, 0.8F, 2.0F);
                return true;
            },
            NOTHING,
            (s, l) -> {
                Set<UUID> struck = new HashSet<>();
                for (Vec3[] line : s.lines) {
                    SetPiecePayload.send(l, Piece.SYLAR_LATTICE, line[0], line[1], 1, null);
                    for (LivingEntity hit : s.foesNear(line[0].lerp(line[1], 0.5), 26)) {
                        if (struck.contains(hit.getUUID()) || hit.getY() > floorY() + 2.5 || flatDistance(hit.position(), line[0], line[1]) > LATTICE_REACH) continue;
                        struck.add(hit.getUUID());
                        s.hurtFoe(hit, 7 * power(s));
                        hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                    }
                    s.sound(line[0].lerp(line[1], 0.5), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.4F);
                }
                s.sound(s.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.6F);
            },
            NOTHING);

    /** The top of hour {@code h}'s case at waist height, where the lattice is strung. */
    static Vec3 post(int h) { return new Vec3(caseGlass(h).getX() + 0.5, floorY() + 1.0, caseGlass(h).getZ() + 0.5); }

    /** The chord between two cases three to six hours apart that passes nearest {@code through}. */
    private static int[] chord(Vec3 through, Set<Long> used) {
        int[] best = {0, 6};
        double d = Double.MAX_VALUE;
        for (int a = 0; a < 12; a++) for (int gap = 3; gap <= 6; gap++) {
            int b = (a + gap) % 12;
            int lo = Math.min(a, b), hi = Math.max(a, b);
            if (used.contains(lo * 16L + hi)) continue;
            double e = flatDistance(through, post(lo), post(hi));
            if (e < d) { d = e; best = new int[] {lo, hi}; }
        }
        return best;
    }

    /** Icefall (Cryokinesis, two o'clock): a ring under each of you, then ice bursts up through it and the floor stays frozen. */
    static final Move ICEFALL = new Move("icefall", Pose.TWO_HANDS, ICE_WINDUP, 170, caseOf(ModAbilities.CRYOKINESIS_ID),
            (s, f) -> 3,
            (s, l) -> {
                for (ServerPlayer p : s.fighters()) s.spots.add(floorAt(p.position()));
                if (s.spots.isEmpty()) return false;
                for (Vec3 at : s.spots)
                    SetPiecePayload.sendSeeded(l, Piece.SYLAR_ICE_MARK, at, at.add(0, 1, 0), (float) ICE_RADIUS, null, 0, SylarEntity.colour(ModAbilities.CRYOKINESIS_ID));
                s.sound(s.position(), SoundEvents.POWDER_SNOW_PLACE, 1.2F, 0.5F);
                return true;
            },
            NOTHING,
            (s, l) -> {
                Set<UUID> struck = new HashSet<>();
                for (Vec3 at : s.spots) {
                    SetPiecePayload.send(l, Piece.SYLAR_ICE, at, at.add(0, 1, 0), (float) ICE_RADIUS, null);
                    for (LivingEntity hit : s.foesNear(at, ICE_RADIUS + 1)) {
                        if (struck.contains(hit.getUUID()) || Math.hypot(hit.getX() - at.x, hit.getZ() - at.z) > ICE_RADIUS || hit.getY() > at.y + 3) continue;
                        struck.add(hit.getUUID());
                        s.hurtFoe(hit, 6 * power(s));
                        hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                        hit.setTicksFrozen(Math.max(hit.getTicksFrozen(), 140));
                    }
                    freezeFloor(l, at);
                    s.sound(at, SoundEvents.GLASS_BREAK, 1.2F, 0.5F);
                    s.sound(at, SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.6F);
                }
            },
            NOTHING);

    /** The dial plate under {@code at} freezes over (blue ice: slick for the rest of the fight). The dais and the cases stay. */
    private static void freezeFloor(ServerLevel l, Vec3 at) {
        BlockPos middle = BlockPos.containing(at.x, ORIGIN.getY(), at.z);
        int r = (int) Math.ceil(ICE_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-r, 0, -r), middle.offset(r, 0, r))) {
            if (Math.hypot(pos.getX() + 0.5 - at.x, pos.getZ() + 0.5 - at.z) > ICE_RADIUS) continue;
            if (!floorBlock(l, pos)) continue;
            l.setBlock(pos.immutable(), Blocks.BLUE_ICE.defaultBlockState(), 3);
        }
    }

    /** A plain floor block that a move may change: not the dais, not a case's plinth, not the wall. */
    static boolean floorBlock(ServerLevel l, BlockPos pos) {
        double r = Math.hypot(pos.getX() + 0.5 - (ORIGIN.getX() + 0.5), pos.getZ() + 0.5 - (ORIGIN.getZ() + 0.5));
        if (r <= DAIS_R + 0.5 || r > FLOOR_R + 0.5) return false;
        BlockState state = l.getBlockState(pos);
        return state.is(Blocks.POLISHED_DEEPSLATE) || state.is(Blocks.DEEPSLATE_TILES) || state.is(Blocks.DARK_OAK_PLANKS)
                || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS) && !l.getBlockState(pos.above()).is(Blocks.SEA_LANTERN)
                && !l.getBlockState(pos.above()).is(Blocks.POLISHED_BLACKSTONE_BRICKS);
    }

    /** Fault Line (Quake, five o'clock): a crack from his feet through you to the wall, then the floor heaves along it. */
    static final Move FAULT = new Move("fault", Pose.CROUCH, FAULT_WINDUP, 130, caseOf(ModAbilities.QUAKE_ID),
            (s, f) -> 3,
            (s, l) -> {
                ServerPlayer t = aim(s);
                if (t == null) return false;
                s.focus = t;
                Vec3 a = floorAt(s.position());
                Vec3 b = toRim(a, SylarEntity.flat(t.position().subtract(a)));
                s.lines.add(new Vec3[] {a, b});
                SetPiecePayload.sendSeeded(l, Piece.SYLAR_FAULT_MARK, a, b, 1, null, 0, SylarEntity.colour(ModAbilities.QUAKE_ID));
                s.sound(s.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.4F);
                return true;
            },
            NOTHING,
            (s, l) -> {
                Vec3 a = s.lines.getFirst()[0], b = s.lines.getFirst()[1];
                SetPiecePayload.send(l, Piece.SYLAR_FAULT, a, b, 1, null);
                for (LivingEntity hit : s.foesNear(a.lerp(b, 0.5), 24)) {
                    if (hit.getY() > floorY() + 2 || flatDistance(hit.position(), a, b) > FAULT_REACH) continue;
                    s.hurtFoe(hit, 7 * power(s));
                    SylarEntity.push(hit, new Vec3(0, 0.95, 0));
                }
                // The crack stays in the floor.
                double length = a.distanceTo(b);
                Vec3 dir = b.subtract(a).normalize();
                for (double d = 0; d <= length; d += 0.5) {
                    BlockPos pos = BlockPos.containing(a.x + dir.x * d, ORIGIN.getY(), a.z + dir.z * d);
                    if (floorBlock(l, pos)) l.setBlock(pos, Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(), 3);
                }
                CameraShakePayload.sendAround(l, a, 40, 0.6F, 10);
                s.sound(a.lerp(b, 0.5), SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.5F);
            },
            NOTHING);

    /** Where a line from {@code a} along {@code dir} meets the wall. */
    static Vec3 toRim(Vec3 a, Vec3 dir) {
        Vec3 c = center();
        double ox = a.x - c.x, oz = a.z - c.z;
        double b = ox * dir.x + oz * dir.z, cc = ox * ox + oz * oz - FLOOR_R * FLOOR_R;
        double t = -b + Math.sqrt(Math.max(0, b * b - cc));
        return a.add(dir.scale(t));
    }

    /** Resonance (Sonic Resonance, eight o'clock): a ring of sound rolls out from his feet. Jump it; it climbs the walls. */
    static final Move RESONANCE = new Move("resonance", Pose.TWO_HANDS, RING_WINDUP, 150, caseOf(ModAbilities.SONIC_RESONANCE_ID),
            (s, f) -> {
                for (ServerPlayer p : f) if (p.getY() - floorY() >= REEL_HEIGHT) return 5;
                return 2;
            },
            (s, l) -> {
                s.ringCenter = floorAt(s.position());
                SetPiecePayload.sendSeeded(l, Piece.SYLAR_RING_MARK, s.ringCenter, s.ringCenter.add(0, 1, 0), 1, s, 0, SylarEntity.colour(ModAbilities.SONIC_RESONANCE_ID));
                s.sound(s.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 1.2F);
                return true;
            },
            NOTHING,
            (s, l) -> {
                s.ringAge = 0;
                s.hit.clear();
                SetPiecePayload.send(l, Piece.SYLAR_RING, s.ringCenter, s.ringCenter.add(0, 1, 0), 1, null);
                s.sound(s.position(), SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.1F);
            },
            NOTHING);

    /** The Second Hand (Pyrokinesis, four o'clock): a hand of fire from the dais to the wall sweeps a quarter turn. */
    static final Move HAND = new Move("second_hand", Pose.RAISED, HAND_WINDUP, 190, caseOf(ModAbilities.PYROKINESIS_ID),
            (s, f) -> s.phase() >= 2 ? 3 : 0,
            (s, l) -> {
                ServerPlayer t = aim(s);
                if (t == null) return false;
                s.focus = t;
                Vec3 c = center();
                s.angle = angleOf(t.getX() - c.x, t.getZ() - c.z) - HAND_SWEEP * 0.35;
                SetPiecePayload.sendSeeded(l, Piece.SYLAR_HAND_MARK, c, rim(s.angle), (float) HAND_SWEEP, null, 0, SylarEntity.colour(ModAbilities.PYROKINESIS_ID));
                s.sound(s.position(), SoundEvents.BLAZE_AMBIENT, 1.0F, 0.5F);
                return true;
            },
            NOTHING,
            (s, l) -> {
                s.handAge = 0; s.handStart = s.angle;
                s.hit.clear();
                SetPiecePayload.send(l, Piece.SYLAR_HAND, center(), rim(s.angle), (float) HAND_SWEEP, null);
                s.sound(center(), SoundEvents.FIRECHARGE_USE, 1.4F, 0.5F);
            },
            NOTHING);

    /** Blur (Superspeed, nine o'clock): three dashes, each through one of you. */
    static final Move BLUR = new Move("blur", Pose.CROUCH, BLUR_WINDUP, 170, caseOf(ModAbilities.SUPERSPEED_ID),
            (s, f) -> s.phase() >= 2 ? 2 : 0,
            (s, l) -> {
                List<ServerPlayer> fighters = s.fighters();
                if (fighters.isEmpty()) return false;
                s.blurPoints.clear();
                Vec3 from = floorAt(s.position());
                s.blurPoints.add(from);
                for (int k = 0; k < BLUR_LEGS; k++) {
                    Vec3 through = floorAt(fighters.get(k % fighters.size()).position());
                    Vec3 dir = SylarEntity.flat(through.subtract(from));
                    if (through.distanceToSqr(from) < 4) dir = new Vec3(-dir.z, 0, dir.x);
                    Vec3 end = through.add(dir.scale(3));
                    Vec3 c = center();
                    double r = Math.hypot(end.x - c.x, end.z - c.z);
                    if (r > FLOOR_R - 2) end = c.add((end.x - c.x) * (FLOOR_R - 2) / r, 0, (end.z - c.z) * (FLOOR_R - 2) / r);
                    s.blurPoints.add(end);
                    from = end;
                }
                for (int k = 0; k < BLUR_LEGS; k++)
                    SetPiecePayload.sendSeeded(l, Piece.SYLAR_BLUR_MARK, s.blurPoints.get(k), s.blurPoints.get(k + 1), 1, null, 0, k);
                s.sound(s.position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.0F, 1.6F);
                return true;
            },
            NOTHING,
            (s, l) -> { s.blurLeg = 0; s.blurTick = 0; s.hit.clear(); },
            NOTHING);

    /** Every Hour (Space-Time, ten o'clock): stopped time falls over every hour of the floor but two opposite ones. */
    static final Move HOURS = new Move("every_hour", Pose.RAISED, HOURS_WINDUP, 330, caseOf(ModAbilities.TIME_MANIPULATION_ID),
            (s, f) -> s.phase() >= 3 ? 4 : 0,
            (s, l) -> {
                s.safeHour = s.getRandom().nextInt(12);
                SetPiecePayload.send(l, Piece.SYLAR_HOURS_MARK, center(), center().add(0, 1, 0), s.safeHour + 1, s);
                s.sound(center(), SoundEvents.BELL_BLOCK, 1.6F, 0.5F);
                for (ServerPlayer p : s.bar().getPlayers()) SylarVoice.warn(p, "story.heroesevolved.sylar.warn.hours");
                return true;
            },
            NOTHING,
            (s, l) -> {
                SetPiecePayload.send(l, Piece.SYLAR_HOURS, center(), center().add(0, 1, 0), s.safeHour + 1, s);
                Vec3 c = center();
                for (LivingEntity hit : s.foesNear(c, FLOOR_R + 2)) {
                    double dx = hit.getX() - c.x, dz = hit.getZ() - c.z;
                    if (Math.hypot(dx, dz) >= 2 && spared(hourOf(dx, dz), s.safeHour)) continue;
                    s.hurtFoe(hit, 8 * power(s));
                    s.stopped.put(hit.getUUID(), new SylarEntity.Stopped(hit.position(), s.tickCount + STOPPED_TICKS));
                    AbilityEffects.send(l, Kind.TIME_STOP_PULSE, hit.position(), hit.getEyePosition(), 1.0);
                    if (hit instanceof ServerPlayer p) ScreenTintPayload.send(p, 0x605B3FD1, STOPPED_TICKS, ScreenTintPayload.Style.EDGE);
                }
                s.sound(c, SoundEvents.BELL_RESONATE, 2.0F, 0.5F);
                s.spend(SylarEntity.SPENT_TICKS);
            },
            NOTHING);

    /**
     * The Light at 6:14 (Induced Radioactivity, six o'clock): he rises over the dais and burns everyone he can see. Only
     * cover saves you: the pillars of the last phase, or the rubble of his own throws. If he never saw the light (a world
     * that kept him from it on the avenue), he reaches for it once, it comes apart in his hands, and he is spent.
     */
    static final Move LIGHT = new Move("light", Pose.RAISED, LIGHT_WINDUP, 420, RADIATION_CASE,
            (s, f) -> s.phase() < 3 ? 0 : s.caseWhole(RADIATION_CASE) ? 5 : lightFails(s) ? 4 : 0,
            (s, l) -> {
                boolean fails = !s.caseWhole(RADIATION_CASE);
                s.triedLight = true;
                s.lightFails = fails;
                Vec3 c = center();
                AbilityEffects.send(l, Kind.TK_WAVE, s.position().add(0, 1, 0), c.add(0, 1, 0), 1.2);
                s.teleportTo(c.x, c.y, c.z);
                s.hoverY = c.y + LIGHT_RISE;
                s.setNoGravity(true);
                SetPiecePayload.send(l, Piece.SYLAR_LIGHT_MARK, c, c.add(0, LIGHT_RISE + 1.6, 0), fails ? 2 : 1, s);
                s.sound(c, SoundEvents.BEACON_POWER_SELECT, 1.6F, 0.5F);
                for (ServerPlayer p : s.bar().getPlayers()) SylarVoice.warn(p, "story.heroesevolved.sylar.warn.light");
                return true;
            },
            (s, l) -> {
                if (s.lightFails && s.castTicks == (int) (LIGHT_WINDUP * 0.7)) {
                    SetPiecePayload.send(l, Piece.SYLAR_FIZZLE, s.getEyePosition(), s.getEyePosition().add(0, 1, 0), 1, s);
                    s.sound(s.position(), SoundEvents.BEACON_DEACTIVATE, 1.4F, 0.6F);
                }
            },
            (s, l) -> {
                Vec3 eye = s.getEyePosition();
                if (s.lightFails) {
                    for (ServerPlayer p : s.bar().getPlayers()) SylarVoice.narrate(p, "story.heroesevolved.sylar.narrate.light_fails");
                    descend(s);
                    s.spend(SylarEntity.FAILED_LIGHT_TICKS);
                    return;
                }
                SetPiecePayload.send(l, Piece.SYLAR_LIGHT, eye, center(), 1, s);
                for (LivingEntity hit : s.foesNear(center(), FLOOR_R + 2)) {
                    if (!seenFrom(l, s, eye, hit)) continue;
                    s.hurtFoe(hit, 14);
                    hit.igniteForSeconds(5);
                }
                CameraShakePayload.sendAround(l, center(), 64, 1.0F, 24);
                s.sound(center(), SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.4F);
                s.sound(center(), SoundEvents.BEACON_ACTIVATE, 2.0F, 0.5F);
                descend(s);
                s.spend(SylarEntity.SPENT_TICKS);
            },
            (s, l) -> descend(s));

    /** Whether {@code target}'s eyes or middle can be seen from {@code eye} (blocks in between are cover). */
    static boolean seenFrom(ServerLevel l, Entity looker, Vec3 eye, LivingEntity target) {
        for (Vec3 to : new Vec3[] {target.getEyePosition(), target.getBoundingBox().getCenter()}) {
            var hit = l.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private static void descend(SylarEntity s) { s.hoverY = Double.NaN; s.setNoGravity(false); }

    /** Your own power, turned on you (a stolen case): a bolt in its colour down a line from his hand. */
    static final Move YOURS = new Move("yours", Pose.ONE_HAND, YOURS_WINDUP, 110, -2,
            (s, f) -> {
                for (int h : EMPTY_CASES) if (s.stolenPower[h] != null && s.caseWhole(h)) return 3;
                return 0;
            },
            (s, l) -> {
                List<Integer> held = new ArrayList<>();
                for (int h : EMPTY_CASES) if (s.stolenPower[h] != null && s.caseWhole(h)) held.add(h);
                if (held.isEmpty()) return false;
                int h = held.get(s.getRandom().nextInt(held.size()));
                ServerPlayer t = null;
                for (ServerPlayer p : s.fighters()) if (p.getUUID().equals(s.stolenFrom[h])) t = p;
                if (t == null) t = aim(s);
                if (t == null) return false;
                s.yours = h;
                s.focus = t;
                s.mark = t.getBoundingBox().getCenter();
                SetPiecePayload.sendSeeded(l, Piece.SYLAR_YOURS_MARK, s.hand(), s.mark, 1, null, 0, SylarEntity.colour(s.stolenPower[h]));
                if (t.getUUID().equals(s.stolenFrom[h])) SylarVoice.say(t, "story.heroesevolved.sylar.say.yours");
                return true;
            },
            NOTHING,
            (s, l) -> {
                Vec3 from = s.hand(), dir = s.mark.subtract(from).normalize();
                Vec3 to = from.add(dir.scale(24));
                var wall = l.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, s));
                if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
                ResourceLocation power = s.yours >= 0 ? s.stolenPower[s.yours] : null;
                SetPiecePayload.sendSeeded(l, Piece.SYLAR_YOURS, from, to, 1, null, 0, SylarEntity.colour(power));
                for (LivingEntity hit : s.foesNear(from.lerp(to, 0.5), 14)) {
                    if (distanceToSegment(hit.getBoundingBox().getCenter(), from, to) > 1.2) continue;
                    s.hurtFoe(hit, 8 * power(s));
                }
                s.sound(from, SoundEvents.EVOKER_CAST_SPELL, 1.2F, 0.8F);
            },
            NOTHING);

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double k = Math.clamp(p.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr()), 0, 1);
        return p.distanceTo(a.add(ab.scale(k)));
    }

    /** Every move, in the order the docs list them. */
    static final List<Move> ALL = List.of(HURL, REEL, LATTICE, ICEFALL, FAULT, RESONANCE, HAND, BLUR, APPRAISE, YOURS, HOURS, LIGHT);

    // ------------------------------------------------------------------
    // Moves still landing after their windup
    // ------------------------------------------------------------------

    static void tickLanding(SylarEntity s, ServerLevel l) {
        s.chunk.tick(s);
        tickRing(s, l);
        tickHand(s, l);
        tickBlur(s, l);
        tickReel(s, l);
        tickHover(s);
    }

    static void clearLanding(SylarEntity s, ServerLevel l) {
        s.chunk.putBack(l);
        s.ringAge = -1; s.handAge = -1; s.blurLeg = -1; s.reelAge = -1; s.reeled = null;
    }

    private static void tickRing(SylarEntity s, ServerLevel l) {
        if (s.ringAge < 0) return;
        double before = ringRadius(s.ringAge), after = ringRadius(++s.ringAge);
        Vec3 c = s.ringCenter;
        for (LivingEntity foe : s.foesNear(c, after + 1)) {
            double d = Math.hypot(foe.getX() - c.x, foe.getZ() - c.z);
            if (s.hit.contains(foe.getUUID()) || d > after + 0.4 || d < before - 0.4) continue;
            s.hit.add(foe.getUUID());
            double up = foe.getY() - floorY();
            if (up > JUMP_CLEAR && up < REEL_HEIGHT) continue;
            s.hurtFoe(foe, 6 * power(s));
            SylarEntity.push(foe, SylarEntity.flat(foe.position().subtract(c)).scale(0.9).add(0, 0.3, 0));
        }
        if (before > FLOOR_R + 1) s.ringAge = -1;
    }

    private static void tickHand(SylarEntity s, ServerLevel l) {
        if (s.handAge < 0) return;
        double prev = handAngle(s.handStart, s.handAge), cur = handAngle(s.handStart, ++s.handAge);
        Vec3 c = center();
        for (LivingEntity foe : s.foesNear(c, FLOOR_R + 1)) {
            if (s.hit.contains(foe.getUUID())) continue;
            double dx = foe.getX() - c.x, dz = foe.getZ() - c.z, r = Math.hypot(dx, dz);
            if (r < HAND_INNER - 0.3 || r > FLOOR_R + 0.8 || foe.getY() - floorY() > JUMP_CLEAR) continue;
            double margin = HAND_REACH / Math.max(1, r);
            double g = angleOf(dx, dz);
            double from = prev - margin, span = cur - prev + 2 * margin;
            double rel = ((g - from) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            if (rel > span) continue;
            s.hit.add(foe.getUUID());
            s.hurtFoe(foe, 6 * power(s));
            foe.igniteForSeconds(4);
        }
        if (s.handAge >= HAND_TICKS) s.handAge = -1;
    }

    private static void tickBlur(SylarEntity s, ServerLevel l) {
        if (s.blurLeg < 0) return;
        Vec3 a = s.blurPoints.get(s.blurLeg), b = s.blurPoints.get(s.blurLeg + 1);
        if (s.blurTick == 0) SetPiecePayload.send(l, Piece.SYLAR_BLUR, a, b, 1, s);
        Vec3 before = s.position();
        Vec3 want = a.lerp(b, (s.blurTick + 1) / (double) BLUR_LEG);
        Vec3 to = SpeedService.swept(s, want.subtract(before));
        s.teleportTo(to.x, to.y, to.z);
        s.getLookControl().setLookAt(b.x, b.y + 1.5, b.z);
        for (LivingEntity foe : s.foesNear(to, BLUR_REACH + 2)) {
            if (s.hit.contains(foe.getUUID()) || foe.getY() > floorY() + 2.5 || flatDistance(foe.position(), before, to) > BLUR_REACH) continue;
            s.hit.add(foe.getUUID());
            s.hurtFoe(foe, 6 * power(s));
            SylarEntity.push(foe, SylarEntity.flat(b.subtract(a)).scale(0.7).add(0, 0.3, 0));
        }
        if (++s.blurTick >= BLUR_LEG) {
            s.blurTick = 0; s.hit.clear();
            if (++s.blurLeg >= BLUR_LEGS) s.blurLeg = -1;
            s.sound(to, SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.9F, 1.5F);
        }
    }

    private static void tickReel(SylarEntity s, ServerLevel l) {
        if (s.reelAge < 0) return;
        LivingEntity t = s.reeled;
        if (t == null || !t.isAlive() || t.level() != l) { s.reelAge = -1; s.reeled = null; return; }
        Vec3 front = floorAt(s.position().add(SylarEntity.flat(t.position().subtract(s.position())).scale(2.5)));
        double k = Math.min(1, ++s.reelAge / (double) REEL_PULL);
        Vec3 at = s.reelFrom.lerp(front, k).add(0, Math.sin(k * Math.PI) * 1.2, 0);
        place(t, at);
        t.setDeltaMovement(Vec3.ZERO);
        if (s.reelAge >= REEL_PULL) {
            s.hurtFoe(t, 4 * power(s));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
            CameraShakePayload.sendAround(l, at, 16, 0.4F, 6);
            s.sound(at, SoundEvents.ANVIL_LAND, 0.6F, 0.7F);
            s.reelAge = -1; s.reeled = null;
        }
    }

    /** Rising for the light, or settling after it. */
    private static void tickHover(SylarEntity s) {
        if (Double.isNaN(s.hoverY)) return;
        double dy = s.hoverY - s.getY();
        s.setDeltaMovement(0, Math.clamp(dy * 0.25, -0.4, 0.4), 0);
        s.hurtMarked = true;
    }

    // ------------------------------------------------------------------
    // Hurl's slab of floor
    // ------------------------------------------------------------------

    /** The floor he tears up for Hurl: drawn as real blocks in flight, and laid down again as rubble where it lands. */
    static final class Chunk {
        private final List<Display.BlockDisplay> displays = new ArrayList<>();
        private final List<BlockState> states = new ArrayList<>();
        private BlockPos pit;
        private Vec3 from = Vec3.ZERO, apex = Vec3.ZERO, to = Vec3.ZERO;
        private int age = -1;

        boolean active() { return age >= 0; }

        /** Tears a three-by-three slab out of the dial plate beside him, on the side away from {@code target}. */
        boolean tear(SylarEntity s, ServerLevel l, LivingEntity target) {
            putBack(l);
            Vec3 c = center();
            Vec3 away = SylarEntity.flat(s.position().subtract(target.position()));
            for (int attempt = 0; attempt < 6; attempt++) {
                double turn = (attempt - 2.5) * 0.6;
                Vec3 dir = new Vec3(away.x * Math.cos(turn) - away.z * Math.sin(turn), 0, away.x * Math.sin(turn) + away.z * Math.cos(turn));
                Vec3 at = s.position().add(dir.scale(3));
                double r = Math.hypot(at.x - c.x, at.z - c.z);
                if (r < DAIS_R + 2 || r > 13) {
                    double clamp = Math.max(DAIS_R + 2, Math.min(13, r));
                    double g = angleOf(at.x - c.x, at.z - c.z);
                    at = c.add(dx(g, clamp), 0, dz(g, clamp));
                }
                BlockPos middle = BlockPos.containing(at.x, ORIGIN.getY(), at.z);
                List<BlockPos> cells = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) cells.add(middle.offset(dx, 0, dz));
                if (!cells.stream().allMatch(p -> floorBlock(l, p) || l.getBlockState(p).is(Blocks.CRACKED_DEEPSLATE_TILES)
                        || l.getBlockState(p).is(Blocks.WAXED_EXPOSED_CUT_COPPER) || l.getBlockState(p).is(Blocks.BLUE_ICE))) continue;
                if (!l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(middle).inflate(1.5, 2, 1.5), e -> e != s).isEmpty()) continue;
                pit = middle;
                states.clear();
                for (BlockPos p : cells) {
                    BlockState state = l.getBlockState(p);
                    states.add(state);
                    l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    Display.BlockDisplay display = new Debris(l, state);
                    display.setPos(Vec3.atLowerCornerOf(p));
                    l.addFreshEntity(display);
                    displays.add(display);
                }
                from = Vec3.atCenterOf(middle);
                apex = s.position().add(0, 3.4, 0).add(SylarEntity.flat(s.getLookAngle()).scale(-0.8));
                to = s.mark.add(0, 0.5, 0);
                age = 0;
                return true;
            }
            return false;
        }

        void tick(SylarEntity s) {
            if (age < 0) return;
            age++;
            Vec3 middle;
            if (age <= HURL_LIFT) {
                double k = age / (double) HURL_LIFT, e = k * k * (3 - 2 * k);
                middle = from.lerp(apex, e);
            } else {
                double k = Math.min(1, (age - HURL_LIFT) / (double) (HURL_WINDUP - HURL_LIFT));
                middle = apex.lerp(to, k).add(0, Math.sin(k * Math.PI) * 3.5, 0);
            }
            for (int i = 0; i < displays.size(); i++) {
                int dx = i / 3 - 1, dz = i % 3 - 1;
                displays.get(i).setPos(middle.add(dx - 0.5, -0.5, dz - 0.5));
            }
        }

        /** It lands: the slab breaks up into a low mound of rubble round {@code at} (never on anyone). */
        void land(ServerLevel l, Vec3 at) {
            BlockPos middle = BlockPos.containing(at.x, ORIGIN.getY() + 1, at.z);
            for (int i = 0; i < states.size(); i++) {
                BlockPos p = middle.offset(i / 3 - 1, 0, i % 3 - 1);
                if ((i % 2 == 1 || i == 4) && l.getBlockState(p).isAir() && l.getBlockState(p.below()).isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP)
                        && onFloor(Vec3.atCenterOf(p)) && l.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(p), e -> !(e instanceof Display)).isEmpty())
                    l.setBlock(p, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 3);
            }
            discard();
            states.clear(); pit = null; age = -1;
        }

        /** Broken off: the slab drops back into its pit. */
        void putBack(ServerLevel l) {
            if (pit != null) {
                for (int i = 0; i < states.size(); i++) {
                    BlockPos p = pit.offset(i / 3 - 1, 0, i % 3 - 1);
                    if (l.getBlockState(p).isAir()) l.setBlock(p, states.get(i), 3);
                }
            }
            discard();
            states.clear(); pit = null; age = -1;
        }

        private void discard() {
            for (Display.BlockDisplay d : displays) d.discard();
            displays.clear();
        }
    }

    /** A block in flight: vanilla's display entity, never saved. */
    private static final class Debris extends Display.BlockDisplay {
        Debris(ServerLevel level, BlockState state) {
            super(EntityType.BLOCK_DISPLAY, level);
            CompoundTag tag = new CompoundTag();
            tag.put("block_state", NbtUtils.writeBlockState(state));
            tag.putInt("teleport_duration", 2);
            tag.putFloat("view_range", 1.5F);
            CompoundTag transform = new CompoundTag();
            ListTag scale = new ListTag(), translation = new ListTag();
            for (int i = 0; i < 3; i++) { scale.add(FloatTag.valueOf(0.98F)); translation.add(FloatTag.valueOf(0.01F)); }
            transform.put("scale", scale); transform.put("translation", translation);
            ListTag rotation = new ListTag();
            for (float value : new float[] {0, 0, 0, 1}) rotation.add(FloatTag.valueOf(value));
            transform.put("left_rotation", rotation); transform.put("right_rotation", rotation.copy());
            tag.put("transformation", transform);
            readAdditionalSaveData(tag);
        }
        @Override public boolean shouldBeSaved() { return false; }
    }
}
