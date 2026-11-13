package com.jayptucker.heroesevolved.sonic;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.ResonancePayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sonic Resonance, after Emma Coolidge and Black Canary.
 *
 * <p>Emma sees sound as colour (Sound Sight, drawn on the client), and her music calls people to it: a
 * Resonant Note lures enemies in and bursts. Canary's voice is force: the Cry (tap) and the sustained
 * scream (hold), a Wall of Sound that turns shots back and can be shoved forward, and a Sonic Vault that
 * can turn into a dive. Crescendo pours everything into one wave.</p>
 *
 * <p>The moves feed each other. A Cry through your Note breaks it harmonic for free, and a held scream feeds
 * it bigger. Crescendo breaks every Note in its path. Ringing (left by Focused Shriek, Shatter Note, a braced
 * Cry and Deep Echo) shatters on the next sonic hit for extra damage and Resonance. All drawing is
 * {@code SonicArt}'s; the numbers it shares with the server live in {@link SonicGeometry}.</p>
 *
 * <p><b>Resonance</b> (0 to 100) builds from sonic hits that land and loud explosions nearby, fades
 * when it is quiet, makes a Note's burst harmonic, and feeds Crescendo.</p>
 */
public final class SonicService {
    public static final int MAX_RESONANCE = 100, HARMONIC_COST = 30, CRESCENDO_TICKS = 60, NOTE_TICKS = 160;
    private static final int HOLD_TICKS = 6, LURE_RADIUS = 16, MAX_FED = 8;
    /** How hard a cry pushes its singer back: a tap, then the held scream every three ticks. */
    public static final double CRY_RECOIL = 0.75, SCREAM_RECOIL = 0.25;
    private static final Set<UUID> BRACE_HINTED = new HashSet<>();
    private static final int[] MELODY = {0, 4, 7, 12, 7, 4, 2, 5};

    // Skill nodes the core kit reads.
    static final String BRACED_VOICE = "sonic_resonance_0_0", FEEDBACK_LOOP = "sonic_resonance_0_2",
            PERFECT_PITCH = "sonic_resonance_1_0", SIREN_SONG = "sonic_resonance_1_2",
            SYMPATHETIC = "sonic_resonance_2_0", SUSTAIN = "sonic_resonance_2_2";

    private record Hold(ServerPlayer player, int tier, long start) {}
    private record Channel(ServerPlayer player, int level, long until, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private static final class Note {
        final ServerPlayer owner; final ServerLevel level; Vec3 pos; final long placed; final long until; final int tier;
        int fed; boolean moved;
        Note(ServerPlayer owner, ServerLevel level, Vec3 pos, long placed, long until, int tier) {
            this.owner = owner; this.level = level; this.pos = pos; this.placed = placed; this.until = until; this.tier = tier;
        }
    }
    private static final class Wall {
        final ServerPlayer owner; final ServerLevel level; Vec3 center; final Vec3 normal; final long until; final int tier;
        long pushed = -1; Vec3 start; final Set<UUID> struck = new HashSet<>();
        Wall(ServerPlayer owner, ServerLevel level, Vec3 center, Vec3 normal, long until, int tier) {
            this.owner = owner; this.level = level; this.center = center; this.normal = normal; this.until = until; this.tier = tier;
        }
    }
    private record Crescendo(ServerPlayer player, int tier, long start) {}
    private record Dive(ServerPlayer player, int tier, long start) {}
    private record Ring(UUID owner, LivingEntity target, long until) {}
    private static final class Meter { int value; long lastGain; int sent = -1; }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static final Map<UUID, Long> CUSHION = new HashMap<>(), LANDED = new HashMap<>();
    private static final Map<UUID, Note> NOTES = new HashMap<>();
    /** Mobs drawn to a Resonant Note, by mob, with the Note's owner. */
    private static final Map<UUID, UUID> LURED = new HashMap<>();
    private static final Map<UUID, Wall> WALLS = new HashMap<>();
    private static final Map<UUID, Crescendo> CRESCENDOS = new HashMap<>();
    private static final Map<UUID, Dive> DIVES = new HashMap<>();
    private static final Map<UUID, Ring> RINGING = new HashMap<>();
    private static final Set<UUID> STRUNG = new HashSet<>();
    private static final Map<UUID, Meter> METERS = new HashMap<>();

    private SonicService() {}

    static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }

    static boolean has(ServerPlayer p, String node) { return SkillTreeService.has(p, node); }

    static boolean canHit(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isSpectator() && !player.isAlliedTo(target)
                && !(target instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID()))
                && !(target instanceof com.jayptucker.heroesevolved.entity.TemporalEchoEntity)
                && (!(target instanceof ServerPlayer other) || player.getServer().isPvpAllowed() && player.canHarmPlayer(other));
    }

    public static boolean singer(ServerPlayer player) {
        return PowerRules.canAct(player) && PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getValue().isUnlocked() && e.getKey().equals(ModAbilities.SONIC_RESONANCE_ID)).isPresent();
    }

    static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ------------------------------------------------------------------
    // Resonance and Ringing
    // ------------------------------------------------------------------

    public static int resonance(ServerPlayer player) { Meter m = METERS.get(player.getUUID()); return m == null ? 0 : m.value; }

    public static void addResonance(ServerPlayer player, int amount) {
        Meter m = METERS.computeIfAbsent(player.getUUID(), ignored -> new Meter());
        m.value = Math.clamp(m.value + amount, 0, MAX_RESONANCE);
        if (amount > 0) m.lastGain = now(player);
        syncMeter(player);
    }

    private static void syncMeter(ServerPlayer player) {
        Meter m = METERS.get(player.getUUID());
        int value = m == null ? 0 : m.value;
        if (m != null && m.sent == value) return;
        if (m != null) m.sent = value;
        PacketDistributor.sendToPlayer(player, new ResonancePayload(value));
    }

    /** A loud sound nearby (an explosion) feeds every singer who hears it. */
    public static void loudSound(ServerLevel level, Vec3 at, int amount) {
        for (ServerPlayer player : level.players())
            if (player.position().distanceToSqr(at) < 16 * 16 && singer(player)) addResonance(player, amount);
    }

    /** Leaves {@code target} Ringing: your next sonic hit on it shatters the ring for extra damage and Resonance. */
    public static void ring(ServerPlayer owner, LivingEntity target) {
        if (!target.isAlive()) return;
        boolean fresh = !RINGING.containsKey(target.getUUID());
        RINGING.put(target.getUUID(), new Ring(owner.getUUID(), target, now(owner) + SonicGeometry.RING_TICKS));
        if (fresh) SonicGeometry.show(owner.serverLevel(), Piece.SONIC_RING, target.position(), target.position(), target.getBbWidth(), target);
    }

    public static boolean ringing(LivingEntity target) { return RINGING.containsKey(target.getUUID()); }

    /**
     * Every sonic hit goes through here: it lands, and if the target was Ringing for this singer the ring shatters
     * for {@link SonicGeometry#RING_DAMAGE} more and {@link SonicGeometry#RING_RESONANCE} Resonance.
     */
    public static float hit(ServerPlayer owner, LivingEntity target, float damage) {
        Ring ring = RINGING.get(target.getUUID());
        if (ring != null && ring.owner.equals(owner.getUUID())) {
            RINGING.remove(target.getUUID());
            damage += SonicGeometry.RING_DAMAGE;
            addResonance(owner, SonicGeometry.RING_RESONANCE);
            Vec3 mid = target.getBoundingBox().getCenter();
            SonicGeometry.show(owner.serverLevel(), Piece.SONIC_NOTE_BURST, mid, mid, 1.6, null);
            sound(owner.serverLevel(), mid, SoundEvents.BELL_BLOCK, 1.0F, 1.8F);
        }
        target.invulnerableTime = 0;
        target.hurt(owner.damageSources().playerAttack(owner), damage);
        return damage;
    }

    // ------------------------------------------------------------------
    // Canary Cry: tap for the cry, hold for the scream
    // ------------------------------------------------------------------

    public static boolean screaming(ServerPlayer player) { return CHANNELS.containsKey(player.getUUID()); }

    public static boolean holdingCry(ServerPlayer player) { return HOLDS.containsKey(player.getUUID()) || screaming(player); }

    public static void press(ServerPlayer player, int tier) {
        HOLDS.put(player.getUUID(), new Hold(player, tier, now(player)));
    }

    public static void release(ServerPlayer player) {
        Hold hold = HOLDS.remove(player.getUUID());
        if (screaming(player)) { stop(player); return; }
        if (hold != null) cry(player, hold.tier, false);
    }

    private static boolean braced(ServerPlayer player) { return player.onGround() && (player.isShiftKeyDown() || player.isCrouching()); }

    /**
     * A focused cone of voice: damage and shove, and glass in its path shatters. Through your own Wall (Feedback
     * Loop) it comes out amplified; through your own Note it breaks it harmonic (a tap) or feeds it (the scream).
     */
    public static void cry(ServerPlayer player, int powerLevel, boolean sustained) {
        var level = player.serverLevel();
        Vec3 origin = player.getEyePosition(), aim = player.getLookAngle().normalize();
        boolean focused = !sustained && braced(player) && has(player, BRACED_VOICE);
        double range = sustained ? 40 : 24 + powerLevel * 4;
        boolean amplified = has(player, FEEDBACK_LOOP) && throughWall(player, origin, aim, range);
        if (amplified) range += 12;
        var clip = level.clip(new ClipContext(origin, origin.add(aim.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double length = clip.getType() == HitResult.Type.BLOCK ? origin.distanceTo(clip.getLocation()) : range;
        if (clip.getType() == HitResult.Type.BLOCK) {
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
                com.jayptucker.heroesevolved.expansion.PowerReactions.resonate(level, clip.getBlockPos().offset(x, y, 0), now(player) + 160);
        }
        double cone = focused ? .95 : amplified ? .85 : .90;
        float damage = (sustained ? 3.5F : 4 + powerLevel * 1.2F) * (amplified ? 1.5F : 1) * (focused ? 1.4F : 1);
        int affected = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                target -> canHit(player, target))) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
            double along = delta.dot(aim);
            if (along < 0 || along > length + .5 || delta.length() > range
                    || delta.normalize().dot(aim) < cone || !player.hasLineOfSight(target)) continue;
            if (++affected > 64) break;
            hit(player, target, damage);
            push(target, target.getDeltaMovement().add(aim.scale(sustained ? .35 : .9)).add(0, .12, 0));
            if (focused) ring(player, target);
        }
        if (affected > 0) addResonance(player, sustained ? Math.min(3, affected) : Math.min(12, affected * 4));
        Note note = NOTES.get(player.getUUID());
        if (note != null && note.level == level && onLine(note.pos, origin, aim, length, 1.6)) {
            if (sustained) { if (note.fed < MAX_FED) note.fed++; }
            else { NOTES.remove(player.getUUID()); burstNote(note, true); }
        }
        if (!sustained) {
            SonicGeometry.show(level, Piece.SONIC_CRY, SonicGeometry.mouth(player, 1), origin.add(aim.scale(length)), amplified ? 1.5 : 1, player);
            ArmPosePayload.show(player, ArmPose.SONIC_CRY, 9);
            voice(player, false);
            if (!focused) recoil(player, CRY_RECOIL);
        }
        // Glass resonance has a small fixed work bound, rather than scanning the whole cone.
        int checks = Math.min(96, (int) Math.ceil(length * 2));
        for (int i = 1; i <= checks; i++) shatter(player, level, BlockPos.containing(origin.add(aim.scale(i * .5))), false);
    }

    /** Whether a point lies within {@code width} of the line from {@code origin} along {@code aim} for {@code length}. */
    static boolean onLine(Vec3 point, Vec3 origin, Vec3 aim, double length, double width) {
        Vec3 to = point.subtract(origin);
        double along = to.dot(aim);
        return along > 0 && along < length + width && to.subtract(aim.scale(along)).length() <= width;
    }

    /** Whether a line out from {@code origin} crosses the singer's own standing Wall of Sound. */
    static boolean throughWall(ServerPlayer player, Vec3 origin, Vec3 aim, double range) {
        Wall wall = WALLS.get(player.getUUID());
        if (wall == null || wall.level != player.level()) return false;
        double facing = aim.dot(wall.normal);
        if (Math.abs(facing) < .2) return false;
        double at = wall.center.subtract(origin).dot(wall.normal) / facing;
        if (at <= 0 || at > range) return false;
        Vec3 crossing = origin.add(aim.scale(at)).subtract(wall.center);
        Vec3 side = new Vec3(-wall.normal.z, 0, wall.normal.x);
        if (Math.abs(crossing.dot(side)) > SonicGeometry.WALL_HALF || Math.abs(crossing.y) > 1.9) return false;
        Vec3 point = wall.center.add(crossing);
        SonicGeometry.show(wall.level, Piece.SONIC_NOTE_BURST, point, point, 2.2, null);
        sound(wall.level, point, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.6F);
        return true;
    }

    /**
     * Your own cry shoves you backward unless you crouch to brace against it. In the air there is
     * nothing to brace on, so it always pushes.
     */
    public static boolean recoil(ServerPlayer player, double strength) {
        if (braced(player)) return false;
        Vec3 back = player.getLookAngle().multiply(1, 0, 1);
        if (back.lengthSqr() < 1.0E-4) return false;
        back = back.normalize().scale(-strength);
        push(player, player.getDeltaMovement().multiply(0.5, 1, 0.5).add(back.x, player.onGround() ? 0.05 : 0, back.z));
        if (BRACE_HINTED.add(player.getUUID()))
            player.displayClientMessage(Component.literal("Your own cry throws you back. Crouch to brace against it.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /** Glass always gives to the right note; ice and leaves too when {@code soft}. Containers and protected ground never. */
    static boolean shatter(ServerPlayer player, ServerLevel level, BlockPos pos, boolean soft) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) return false;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        boolean breaks = path.contains("glass") || soft && (path.contains("ice") || path.contains("leaves"));
        if (!breaks || !WorldMutationPolicy.canEdit(player, level, pos)) return false;
        return level.destroyBlock(pos, true, player);
    }

    /** An outward scream (the awakening): pushes everything back and turns shots around. */
    public static void burst(ServerPlayer player, int powerLevel, boolean awakening) {
        double radius = awakening ? 14 : 5 + powerLevel * .5;
        var level = player.serverLevel();
        int affected = 0;
        for (var target : level.getEntities(player, player.getBoundingBox().inflate(radius), target -> target.isAlive() && !target.isSpectator())) {
            if (target.distanceToSqr(player) > radius * radius || !player.hasLineOfSight(target)) continue;
            if (target instanceof LivingEntity living && !canHit(player, living)) continue;
            if (!(target instanceof LivingEntity) && !(target instanceof Projectile)) continue;
            Vec3 away = target.position().subtract(player.position()).normalize();
            if (target instanceof Projectile projectile) {
                if (projectile.getOwner() == player || projectile.getDeltaMovement().dot(away) >= 0) continue;
                projectile.setOwner(player);
            }
            if (++affected > 64) break;
            if (target instanceof LivingEntity living && !awakening) hit(player, living, 3 + powerLevel);
            push(target, away.scale(awakening ? 1.1 : .85).add(0, .25, 0));
        }
        AbilityEffects.send(level, Kind.SONIC_BURST, player.position(), player.getEyePosition(), radius);
        voice(player, true);
    }

    // ------------------------------------------------------------------
    // Resonant Note
    // ------------------------------------------------------------------

    public static boolean hasNote(ServerPlayer player) { return NOTES.containsKey(player.getUUID()); }

    /** Where the singer's Note is playing, or null. */
    public static Vec3 notePosition(ServerPlayer player) { Note n = NOTES.get(player.getUUID()); return n == null ? null : n.pos; }

    /** Whether {@code mob} is drawn to {@code owner}'s Note right now. */
    public static boolean luredBy(Entity mob, ServerPlayer owner) { return owner.getUUID().equals(LURED.get(mob.getUUID())); }

    private static Vec3 aimPoint(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition(), far = eye.add(player.getLookAngle().scale(reach));
        var clip = player.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (clip.getType() != HitResult.Type.BLOCK) return far;
        return clip.getLocation().subtract(player.getLookAngle().scale(0.6)).add(0, 0.8, 0);
    }

    /** Sets a humming note where you aim. Enemies nearby forget you and walk to it. */
    public static boolean placeNote(ServerPlayer player, int tier) {
        if (hasNote(player)) return false;
        placeNoteAt(player, aimPoint(player, 20), tier);
        return true;
    }

    public static void placeNoteAt(ServerPlayer player, Vec3 pos, int tier) {
        long now = now(player);
        int life = NOTE_TICKS + (has(player, SIREN_SONG) ? 80 : 0);
        NOTES.put(player.getUUID(), new Note(player, player.serverLevel(), pos, now, now + life, tier));
        SonicGeometry.show(player.serverLevel(), Piece.SONIC_SHATTER, SonicGeometry.hand(player, 1, .6), pos, 0.2, player);
        showNote(NOTES.get(player.getUUID()));
        sound(player.serverLevel(), pos, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.7F);
        ArmPosePayload.show(player, ArmPose.SONIC_PLUCK, 10);
        player.displayClientMessage(Component.literal("The note hums. Hunters come to it. Press again to break it, Shift to move it.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void showNote(Note note) {
        float scale = 1 + note.fed / (float) MAX_FED + (resonance(note.owner) >= HARMONIC_COST ? SonicGeometry.HARMONIC_FLAG : 0);
        SonicGeometry.show(note.level, Piece.SONIC_NOTE, note.pos, note.pos, scale, null);
    }

    /** The Note's follow-up: break it, or with Shift carry it once to where you aim (the lured follow it). */
    public static boolean noteFollowUp(ServerPlayer player, boolean shift) {
        if (!hasNote(player)) return false;
        if (shift) return moveNote(player);
        ArmPosePayload.show(player, ArmPose.SONIC_CLENCH, 10);
        return detonate(player);
    }

    /** Carries the Note to where you aim, once. Everything it has drawn turns and follows. */
    public static boolean moveNote(ServerPlayer player) {
        Note note = NOTES.get(player.getUUID());
        if (note == null) return false;
        if (note.moved) {
            player.displayClientMessage(Component.literal("The note has already moved once.").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        Vec3 from = note.pos, to = aimPoint(player, 20);
        note.pos = to; note.moved = true;
        SonicGeometry.show(note.level, Piece.SONIC_SHRIEK, from, to, 1, null);
        showNote(note);
        ArmPosePayload.show(player, ArmPose.SONIC_PLUCK, 10);
        sound(note.level, to, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 1.2F);
        return true;
    }

    /** True if {@code mob} is drawn to a Resonant Note right now. */
    public static boolean lured(Entity mob) { return LURED.containsKey(mob.getUUID()); }

    /** Breaks the note: everything gathered around it is hit, harder and wider if you have the Resonance for a harmonic. */
    public static boolean detonate(ServerPlayer player) {
        Note note = NOTES.remove(player.getUUID());
        if (note == null) return false;
        burstNote(note, false);
        return true;
    }

    /** Breaks every one of the singer's Notes within {@code width} of a line, harmonic and free. Returns how many broke. */
    static int breakNotesOnLine(ServerPlayer player, Vec3 origin, Vec3 aim, double length, double width) {
        Note note = NOTES.get(player.getUUID());
        if (note == null || note.level != player.level() || !onLine(note.pos, origin, aim, length, width)) return 0;
        NOTES.remove(player.getUUID());
        burstNote(note, true);
        return 1;
    }

    /** Breaks the singer's Note if it is within {@code reach} of a point, harmonic and free. */
    static boolean breakNoteNear(ServerPlayer player, Vec3 point, double reach) {
        Note note = NOTES.get(player.getUUID());
        if (note == null || note.level != player.level() || note.pos.distanceToSqr(point) > reach * reach) return false;
        NOTES.remove(player.getUUID());
        burstNote(note, true);
        return true;
    }

    private static void burstNote(Note note, boolean free) {
        ServerPlayer p = note.owner;
        LURED.values().removeIf(owner -> owner.equals(p.getUUID()));
        boolean harmonic = free || resonance(p) >= HARMONIC_COST;
        if (harmonic && !free) addResonance(p, -HARMONIC_COST);
        double radius = 4 + note.tier * 0.5 + (harmonic ? 3 : 0) + Math.min(3, note.fed * .4);
        float damage = (5 + note.tier * 1.5F) * (harmonic ? 1.5F : 1.0F) + note.fed * .5F;
        int caught = 0;
        for (LivingEntity target : note.level.getEntitiesOfClass(LivingEntity.class, new AABB(note.pos, note.pos).inflate(radius),
                t -> canHit(p, t) && t.getBoundingBox().getCenter().distanceToSqr(note.pos) <= radius * radius)) {
            if (++caught > 32) break;
            hit(p, target, damage);
            Vec3 away = target.position().subtract(note.pos).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
            push(target, away.scale(0.8).add(0, 0.35, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            if (caught <= 8) EvolutionTrials.advance(p, ModAbilities.SONIC_RESONANCE_ID);
        }
        BlockPos center = BlockPos.containing(note.pos);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) shatter(p, note.level, pos.immutable(), true);
        SonicGeometry.show(note.level, Piece.SONIC_NOTE_BURST, note.pos, note.pos, radius + (harmonic ? SonicGeometry.HARMONIC_FLAG : 0), null);
        sound(note.level, note.pos, SoundEvents.BELL_RESONATE, 1.2F, harmonic ? 1.4F : 1.0F);
        sound(note.level, note.pos, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.8F);
        sound(note.level, note.pos, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.6F);
        if (harmonic) p.displayClientMessage(Component.literal(free ? "Harmonic! (free)" : "Harmonic!")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
    }

    private static void tickNotes(long now) {
        for (var entry : List.copyOf(NOTES.entrySet())) {
            Note note = entry.getValue();
            if (!singer(note.owner) || note.owner.level() != note.level) { NOTES.remove(entry.getKey()); LURED.values().removeIf(o -> o.equals(entry.getKey())); continue; }
            if (now >= note.until) { NOTES.remove(entry.getKey()); burstNote(note, false); continue; }
            long age = now - note.placed;
            if (age % 10 == 0) showNote(note);
            // A slow phrase, over and over, the way Emma's cello carried.
            if (age % 10 == 0) {
                int step = MELODY[(int) (age / 10) % MELODY.length];
                sound(note.level, note.pos, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, (float) Math.pow(2, (step - 12) / 12.0));
                sound(note.level, note.pos, SoundEvents.NOTE_BLOCK_HARP.value(), 0.5F, (float) Math.pow(2, step / 12.0));
            }
            if (age % 5 != 0) continue;
            boolean siren = has(note.owner, SIREN_SONG), pitch = has(note.owner, PERFECT_PITCH);
            int reach = siren ? 24 : LURE_RADIUS;
            float cap = siren ? 200 : 100;
            int drawn = 0;
            for (Mob mob : note.level.getEntitiesOfClass(Mob.class, new AABB(note.pos, note.pos).inflate(reach),
                    m -> m instanceof Enemy && canHit(note.owner, m) && m.getMaxHealth() <= cap
                            && !(m instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity))) {
                if (++drawn > 24) break;
                LURED.put(mob.getUUID(), note.owner.getUUID());
                mob.setTarget(null);
                if (pitch) PrivateGlow.glow(note.owner, mob, 12);
                if (mob.distanceToSqr(note.pos) > 2.5 * 2.5) mob.getNavigation().moveTo(note.pos.x, note.pos.y - 0.8, note.pos.z, pitch ? 1.35 : 1.15);
                else mob.getNavigation().stop();
            }
        }
    }

    // ------------------------------------------------------------------
    // Wall of Sound
    // ------------------------------------------------------------------

    public static boolean hasWall(ServerPlayer player) { return WALLS.containsKey(player.getUUID()); }

    public static boolean pushing(ServerPlayer player) { Wall w = WALLS.get(player.getUUID()); return w != null && w.pushed >= 0; }

    /** A standing wall of sound a couple of blocks ahead: shots turn back, enemies are pushed away. */
    public static boolean wall(ServerPlayer player, int tier) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 center = SonicGeometry.wallCenter(player.position(), look);
        int life = 100 + 10 * tier;
        if (has(player, SUSTAIN)) life = life * 3 / 2;
        Wall wall = new Wall(player, player.serverLevel(), center, look, now(player) + life, tier);
        WALLS.put(player.getUUID(), wall);
        showWall(wall);
        voice(player, true);
        ArmPosePayload.show(player, ArmPose.SONIC_WALL, 14);
        return true;
    }

    private static void showWall(Wall wall) {
        SonicGeometry.show(wall.level, Piece.SONIC_WALL, wall.center, wall.center.add(wall.normal), SonicGeometry.WALL_HALF, wall.owner);
    }

    public static boolean dropWall(ServerPlayer player) { return WALLS.remove(player.getUUID()) != null; }

    /** The Wall's follow-up: shove it, and it rolls forward twelve blocks, carrying everything in front of it. */
    public static boolean pushWall(ServerPlayer player) {
        Wall wall = WALLS.get(player.getUUID());
        if (wall == null || wall.pushed >= 0) return false;
        wall.pushed = now(player);
        wall.start = wall.center;
        SonicGeometry.show(wall.level, Piece.SONIC_WALL_PUSH, wall.start, wall.start.add(wall.normal), SonicGeometry.WALL_HALF, player);
        ArmPosePayload.show(player, ArmPose.SONIC_SHOVE, 12);
        voice(player, true);
        sound(wall.level, wall.center, SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.6F);
        return true;
    }

    private static boolean friendlyShot(ServerPlayer owner, Projectile shot) {
        Entity shooter = shot.getOwner();
        return shooter != null && shooter != owner && (shooter instanceof LivingEntity l && (owner.isAlliedTo(l)
                || l instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID())));
    }

    private static void tickWalls(long now) {
        for (var entry : List.copyOf(WALLS.entrySet())) {
            Wall wall = entry.getValue();
            boolean rolling = wall.pushed >= 0;
            long rolled = rolling ? now - wall.pushed : 0;
            if (!rolling && now >= wall.until || rolling && rolled > SonicGeometry.WALL_PUSH_TICKS
                    || !singer(wall.owner) || wall.owner.level() != wall.level) { WALLS.remove(entry.getKey()); continue; }
            if (rolling) {
                Vec3 next = SonicGeometry.pushed(wall.start, wall.normal, rolled);
                if (!wall.level.getBlockState(BlockPos.containing(next)).getCollisionShape(wall.level, BlockPos.containing(next)).isEmpty()) {
                    SonicGeometry.show(wall.level, Piece.SONIC_LANDING, wall.center.subtract(0, 1.2, 0), wall.center, 3, null);
                    sound(wall.level, wall.center, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.7F);
                    WALLS.remove(entry.getKey());
                    continue;
                }
                wall.center = next;
            }
            Vec3 side = new Vec3(-wall.normal.z, 0, wall.normal.x);
            var area = new AABB(wall.center, wall.center).inflate(4);
            boolean strings = has(wall.owner, SYMPATHETIC);
            for (Projectile shot : wall.level.getEntitiesOfClass(Projectile.class, area)) {
                if (shot.getOwner() == wall.owner) continue;
                Vec3 rel = shot.position().subtract(wall.center), v = shot.getDeltaMovement();
                if (Math.abs(rel.dot(side)) > SonicGeometry.WALL_HALF || Math.abs(rel.y) > 1.9) continue;
                double d = rel.dot(wall.normal), toward = v.dot(wall.normal);
                if (strings && friendlyShot(wall.owner, shot)) {
                    // A friend's shot passes through and comes out faster and harder, once.
                    if (Math.abs(d) < 1.2 && STRUNG.add(shot.getUUID())) {
                        push(shot, v.scale(1.5));
                        if (shot instanceof AbstractArrow arrow) arrow.setBaseDamage(arrow.getBaseDamage() * 1.5);
                        SonicGeometry.show(wall.level, Piece.SONIC_RING, shot.position(), shot.position(), 0.3, shot);
                        sound(wall.level, shot.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.8F);
                    }
                    continue;
                }
                if (rolling) {
                    // A rolling wall scoops shots up and carries them along its way, yours now.
                    if (Math.abs(d) > 1.2) continue;
                    push(shot, wall.normal.scale(Math.max(1.2, v.length())));
                } else {
                    if (toward >= 0 || d < -0.4 || d > 0.8 - toward) continue;
                    // Turned back the way it came, and it is yours now.
                    push(shot, v.subtract(wall.normal.scale(2 * toward)).scale(1.1));
                }
                shot.setOwner(wall.owner);
                sound(wall.level, shot.position(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.6F);
                addResonance(wall.owner, 4);
            }
            if (!rolling && now % 10 == 0) showWall(wall);
            if (now % 20 == 0) sound(wall.level, wall.center, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.7F);
            if (rolling) {
                for (LivingEntity target : wall.level.getEntitiesOfClass(LivingEntity.class, area, t -> canHit(wall.owner, t))) {
                    Vec3 rel = target.getBoundingBox().getCenter().subtract(wall.center);
                    if (Math.abs(rel.dot(wall.normal)) > 1.3 || Math.abs(rel.dot(side)) > SonicGeometry.WALL_HALF + .3 || Math.abs(rel.y) > 2.2) continue;
                    if (wall.struck.add(target.getUUID())) hit(wall.owner, target, 4 + wall.tier);
                    push(target, wall.normal.scale(1.1).add(0, 0.25, 0));
                }
                continue;
            }
            if (now % 10 != 0) continue;
            for (LivingEntity target : wall.level.getEntitiesOfClass(LivingEntity.class, area, t -> canHit(wall.owner, t))) {
                Vec3 rel = target.getBoundingBox().getCenter().subtract(wall.center);
                double d = rel.dot(wall.normal);
                if (d < -1.0 || d > 1.4 || Math.abs(rel.dot(side)) > 2.8 || Math.abs(rel.y) > 2.2) continue;
                hit(wall.owner, target, 2 + wall.tier * 0.5F);
                push(target, wall.normal.scale(0.9).add(0, 0.2, 0));
            }
        }
    }

    // ------------------------------------------------------------------
    // Sonic Vault and Sonic Dive
    // ------------------------------------------------------------------

    /** A cry at the ground behind you throws you the other way; your next landing comes down on a shockwave. */
    public static void vault(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 velocity = player.getDeltaMovement().add(look.reverse().scale(1.25)).add(0, .45, 0);
        player.setDeltaMovement(velocity); player.connection.send(new ClientboundSetEntityMotionPacket(player));
        CUSHION.put(player.getUUID(), player.level().getGameTime() + 200); player.resetFallDistance();
        Vec3 eye = player.getEyePosition(), down = new Vec3(look.x, -1.2, look.z).normalize();
        var clip = player.level().clip(new ClipContext(eye, eye.add(down.scale(6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 ground = clip.getType() == HitResult.Type.BLOCK ? clip.getLocation() : eye.add(down.scale(6));
        SonicGeometry.show(player.serverLevel(), Piece.SONIC_VAULT, player.position(), ground, 1, player);
        ArmPosePayload.show(player, ArmPose.SONIC_VAULT, 12);
        voice(player, true);
    }

    public static boolean diving(ServerPlayer player) { return DIVES.containsKey(player.getUUID()); }

    /** The Vault's follow-up: in the air after a Vault, scream straight down and plunge onto a wider, harder shockwave. */
    public static boolean dive(ServerPlayer player, int tier) {
        Long cushion = CUSHION.get(player.getUUID());
        if (cushion == null || player.onGround() || diving(player)) return false;
        DIVES.put(player.getUUID(), new Dive(player, tier, now(player)));
        push(player, new Vec3(0, -SonicGeometry.DIVE_SPEED, 0));
        ArmPosePayload.show(player, ArmPose.SONIC_DIVE, 8);
        SonicGeometry.show(player.serverLevel(), Piece.SONIC_DIVE, player.position(), player.position(), 1, player);
        voice(player, false);
        return true;
    }

    private static void tickDives(long now) {
        for (Dive dive : List.copyOf(DIVES.values())) {
            ServerPlayer p = dive.player;
            if (!singer(p) || p.isRemoved()) { DIVES.remove(p.getUUID()); continue; }
            long age = now - dive.start;
            boolean landed = age > 1 && (p.onGround() || p.verticalCollision || p.isInWater() || p.isInLava()) || age >= SonicGeometry.DIVE_MAX;
            if (landed) {
                DIVES.remove(p.getUUID());
                CUSHION.remove(p.getUUID());
                LANDED.put(p.getUUID(), now + 10);
                p.resetFallDistance();
                shockwave(p, dive.tier, SonicGeometry.DIVE_RADIUS, 1.5F);
                continue;
            }
            push(p, new Vec3(p.getDeltaMovement().x * .3, -SonicGeometry.DIVE_SPEED, p.getDeltaMovement().z * .3));
            p.resetFallDistance();
            if (age % 2 == 0) SonicGeometry.show(p.serverLevel(), Piece.SONIC_DIVE, p.position(), p.position(), 1, p);
            if (age % 6 == 0) ArmPosePayload.show(p, ArmPose.SONIC_DIVE, 8);
        }
    }

    /** Called on landing: true cancels fall damage. A real fall after a Vault lands on a shockwave. */
    public static boolean cushion(ServerPlayer player, float distance) {
        Long landed = LANDED.get(player.getUUID());
        if (landed != null && player.level().getGameTime() <= landed) return true;
        if (diving(player)) return true;
        Long until = CUSHION.remove(player.getUUID());
        if (until == null || player.level().getGameTime() > until) return false;
        if (distance > 2.5F) shockwave(player, com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(player), 4, 1);
        return true;
    }

    private static void shockwave(ServerPlayer player, int tier, double radius, float scale) {
        var level = player.serverLevel();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                t -> canHit(player, t) && t.distanceToSqr(player) <= radius * radius)) {
            hit(player, target, (3 + tier) * scale);
            Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
            push(target, (away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize()).scale(0.9 * scale).add(0, 0.35, 0));
        }
        SonicGeometry.show(level, Piece.SONIC_LANDING, player.position(), player.position(), radius, null);
        sound(level, player.position(), SoundEvents.MACE_SMASH_GROUND, 0.9F, scale > 1 ? 1.0F : 1.4F);
        if (scale > 1) com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(level, player.position(), 24, 0.25F, 10);
    }

    // ------------------------------------------------------------------
    // Crescendo
    // ------------------------------------------------------------------

    public static boolean building(ServerPlayer player) { return CRESCENDOS.containsKey(player.getUUID()); }

    public static void startCrescendo(ServerPlayer player, int tier) {
        CRESCENDOS.put(player.getUUID(), new Crescendo(player, tier, now(player)));
        ArmPosePayload.show(player, ArmPose.SONIC_GATHER, CRESCENDO_TICKS + 10);
    }

    /** The key came up: release everything that was built. */
    public static void releaseCrescendo(ServerPlayer player) {
        Crescendo c = CRESCENDOS.remove(player.getUUID());
        if (c != null) fireCrescendo(player, c.tier, Math.min(1F, (now(player) - c.start) / (float) CRESCENDO_TICKS));
    }

    public static boolean cancelCrescendo(ServerPlayer player) {
        if (CRESCENDOS.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clear(player);
        return true;
    }

    /**
     * One wave of everything: a wide cone, longer and harder the longer it was built, and harder still
     * for every point of Resonance (all of it is spent). It breaks your Note harmonic if it passes it, and
     * with Feedback Loop comes out of your own Wall amplified.
     */
    public static int fireCrescendo(ServerPlayer player, int tier, float built) {
        built = Math.max(0.25F, built);
        int resonance = resonance(player);
        if (resonance > 0) addResonance(player, -resonance);
        var level = player.serverLevel();
        Vec3 origin = player.getEyePosition(), aim = player.getLookAngle().normalize();
        double range = 16 + 16 * built;
        boolean amplified = has(player, FEEDBACK_LOOP) && throughWall(player, origin, aim, range);
        if (amplified) range += 8;
        float damage = (6 + 10 * built + resonance / 10F + tier) * (amplified ? 1.3F : 1);
        breakNotesOnLine(player, origin, aim, range, 4);
        int struck = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), t -> canHit(player, t))) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
            if (delta.length() > range || delta.normalize().dot(aim) < 0.8 || !player.hasLineOfSight(target)) continue;
            if (++struck > 48) break;
            hit(player, target, damage);
            push(target, aim.scale(1.2 + 1.2 * built).add(0, 0.35, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        }
        int checks = Math.min(160, (int) Math.ceil(range * 2));
        for (int i = 2; i <= checks; i++) {
            BlockPos center = BlockPos.containing(origin.add(aim.scale(i * 0.5)));
            for (BlockPos pos : new BlockPos[] {center, center.above(), center.below(), center.east(), center.west(), center.north(), center.south()})
                shatter(player, level, pos, true);
        }
        SonicGeometry.show(level, Piece.SONIC_CRESCENDO, SonicGeometry.mouth(player, 1), origin.add(aim.scale(range)), 1 + 2 * built, player);
        sound(level, origin, com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_CRY.get(), 1.4F, 0.85F);
        sound(level, origin, SoundEvents.BELL_RESONATE, 1.5F, 0.6F);
        sound(level, origin, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.3F);
        ArmPosePayload.show(player, ArmPose.SONIC_RELEASE, 16);
        com.jayptucker.heroesevolved.network.ScreenTintPayload.send(player, 0x40C080FF, 12, com.jayptucker.heroesevolved.network.ScreenTintPayload.Style.FLASH);
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(level, origin, 24, 0.2F + 0.3F * built, 12);
        return struck;
    }

    private static void tickCrescendos(long now) {
        for (var entry : List.copyOf(CRESCENDOS.entrySet())) {
            Crescendo c = entry.getValue();
            if (!singer(c.player) || c.player.isRemoved()) { CRESCENDOS.remove(entry.getKey()); continue; }
            long held = now - c.start;
            c.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false, false));
            float built = Math.min(1F, held / (float) CRESCENDO_TICKS);
            if (held % 3 == 0) SonicGeometry.show(c.player.serverLevel(), Piece.SONIC_GATHER, c.player.position(), c.player.position(), built, c.player);
            if (held % 10 == 0) ArmPosePayload.show(c.player, ArmPose.SONIC_GATHER, CRESCENDO_TICKS + 10);
            if (held % 5 == 0) sound(c.player.serverLevel(), c.player.getEyePosition(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.5F + built * 1.5F);
            if (held >= CRESCENDO_TICKS) { CRESCENDOS.remove(entry.getKey()); fireCrescendo(c.player, c.tier, 1F); }
        }
    }

    // ------------------------------------------------------------------
    // The sustained scream and the voice
    // ------------------------------------------------------------------

    public static void start(ServerPlayer player, int level) {
        CHANNELS.put(player.getUUID(), new Channel(player, level, now(player) + 100, player.level().dimension()));
        voiceState(player, true);
        ArmPosePayload.show(player, ArmPose.SONIC_SCREAM, 14);
    }

    public static void voice(ServerPlayer player, boolean burst) {
        player.serverLevel().playSound(null, player.blockPosition(), burst
                ? com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_BURST.get()
                : com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_CRY.get(), SoundSource.PLAYERS, .85F, 1);
    }

    private static void voiceState(ServerPlayer player, boolean active) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new com.jayptucker.heroesevolved.network.SonicVoicePayload(player.getId(), player.getUUID(), active));
    }

    public static void stop(ServerPlayer player) {
        if (CHANNELS.remove(player.getUUID()) != null) {
            voiceState(player, false);
            ArmPosePayload.clear(player);
        }
    }

    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        stop(player);
        HOLDS.remove(id); CUSHION.remove(id); LANDED.remove(id); NOTES.remove(id); WALLS.remove(id); CRESCENDOS.remove(id); DIVES.remove(id);
        LURED.values().removeIf(owner -> owner.equals(id));
        RINGING.values().removeIf(ring -> ring.owner.equals(id));
        SonicSkills.forget(player);
        if (METERS.remove(id) != null) PacketDistributor.sendToPlayer(player, new ResonancePayload(0));
    }

    /** Whether something keeps the singer's Resonance from fading (Sustain with a Wall or Guard standing). */
    private static boolean sustained(ServerPlayer player) {
        return has(player, SUSTAIN) && (hasWall(player) || SonicSkills.guarding(player));
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        // A held Cry becomes the sustained scream.
        for (Hold hold : List.copyOf(HOLDS.values())) {
            if (!singer(hold.player) || hold.player.isRemoved()) { HOLDS.remove(hold.player.getUUID()); continue; }
            if (!screaming(hold.player) && now - hold.start >= HOLD_TICKS) start(hold.player, hold.tier);
        }
        for (Channel channel : List.copyOf(CHANNELS.values())) {
            ServerPlayer p = channel.player;
            if (!p.isAlive() || p.isRemoved() || now >= channel.until
                    || !channel.dimension.equals(p.level().dimension()) || !PowerRules.canAct(p)) {
                HOLDS.remove(p.getUUID());
                stop(p);
            } else {
                if (now % 20 == 0) voiceState(p, true);
                if (now % 10 == 0) ArmPosePayload.show(p, ArmPose.SONIC_SCREAM, 14);
                if (now % 2 == 0) {
                    Vec3 eye = p.getEyePosition(), aim = p.getLookAngle();
                    var clip = p.level().clip(new ClipContext(eye, eye.add(aim.scale(40)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                    Vec3 end = clip.getType() == HitResult.Type.BLOCK ? clip.getLocation() : eye.add(aim.scale(40));
                    SonicGeometry.show(p.serverLevel(), Piece.SONIC_SCREAM, SonicGeometry.mouth(p, 1), end, 1, p);
                }
                // The scream keeps pushing while it lasts: steadily on the ground, in gusts in the air.
                if (p.onGround() ? now % 3 == 0 : now % 10 == 0)
                    recoil(p, p.onGround() ? SCREAM_RECOIL : SCREAM_RECOIL * 1.6);
            }
        }
        if (now % 10 == 0) for (Channel channel : List.copyOf(CHANNELS.values())) {
            if (!PlayerEnergyService.tryConsume(channel.player, 6)) { stop(channel.player); continue; }
            MasteryService.awardEnergySpent(channel.player, 6); cry(channel.player, channel.level, true);
        }
        CUSHION.values().removeIf(until -> until < now);
        LANDED.values().removeIf(until -> until < now);
        tickNotes(now);
        tickWalls(now);
        tickDives(now);
        tickCrescendos(now);
        SonicSkills.tick(server, now);
        LURED.entrySet().removeIf(e -> !NOTES.containsKey(e.getValue()));
        for (var entry : List.copyOf(RINGING.entrySet())) {
            Ring ring = entry.getValue();
            if (now >= ring.until || !ring.target.isAlive() || ring.target.isRemoved()) { RINGING.remove(entry.getKey()); continue; }
            if ((ring.until - now) % 10 == 0 && ring.target.level() instanceof ServerLevel level)
                SonicGeometry.show(level, Piece.SONIC_RING, ring.target.position(), ring.target.position(), ring.target.getBbWidth(), ring.target);
        }
        if (now % 200 == 0) STRUNG.clear();
        for (var entry : List.copyOf(METERS.entrySet())) {
            Meter m = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            if (!singer(player)) { forget(player); continue; }
            if (m.value > 0 && now - m.lastGain > 200 && now % 20 == 0 && !sustained(player)) { m.value--; syncMeter(player); }
        }
    }

    public static void clear() {
        for (var channel : List.copyOf(CHANNELS.values())) stop(channel.player);
        BRACE_HINTED.clear(); HOLDS.clear(); CUSHION.clear(); LANDED.clear(); NOTES.clear(); LURED.clear(); WALLS.clear();
        CRESCENDOS.clear(); DIVES.clear(); RINGING.clear(); STRUNG.clear(); METERS.clear();
        SonicSkills.clear();
    }
}
