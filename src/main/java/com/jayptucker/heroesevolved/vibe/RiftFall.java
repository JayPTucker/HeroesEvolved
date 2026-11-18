package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rift Fall, Vibe's ultimate (the action keeps Frequency Lock's id, {@code vibe_frequency_lock}). Cisco throws both hands
 * up and a blast tears a great breach open in the sky over a zone he marks on the ground. The ring fills for
 * {@link #WARNING} ticks, time to get out. Then a tear opens under every foe still in it, and one after another they
 * drop through and plummet out of the sky breach, slamming into the ground for {@code 6 + 2 per tier} (more if read)
 * and interference that slows them and knocks a speedster out of phase. Anything too big for a breach (a boss, a
 * ravager, someone frozen or held in stopped time) takes the slam where it stands.
 * <p>
 * The fall is driven here, a few blocks a tick on a fixed path, so it lands the same for mobs with no AI and for
 * players, and no vanilla fall damage is added on top.
 */
public final class RiftFall {
    public static final int WARNING = 20, STEP = 2, FALL = 9, MOST = 12, AFTER = 14;
    public static final int LIFE = WARNING + MOST * STEP + FALL + AFTER;
    public static final double RADIUS = 5, SKY = 15;

    private static final class Drop {
        final LivingEntity foe;
        final int index;
        Vec3 top, ground;
        long start = -1;
        boolean done;

        Drop(LivingEntity foe, int index) {
            this.foe = foe;
            this.index = index;
        }
    }

    private static final class Cast {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 center, sky;
        final double radius;
        final int tier;
        final long start;
        List<Drop> drops;

        Cast(ServerPlayer owner, Vec3 center, Vec3 sky, double radius, int tier) {
            this.owner = owner;
            this.level = owner.serverLevel();
            this.center = center;
            this.sky = sky;
            this.radius = radius;
            this.tier = tier;
            start = level.getGameTime();
        }
    }

    private static final Map<UUID, Cast> CASTS = new HashMap<>();

    private RiftFall() {}

    public static boolean cast(AbilityUseContext context) {
        ServerPlayer player = context.player();
        if (!VibeService.ready(player) || CASTS.containsKey(player.getUUID())) return false;
        ServerLevel level = player.serverLevel();
        Vec3 center = VibeBreaches.aimedGround(player, 18);
        if (center == null) center = StyleOps.ground(level, player.position());
        Vec3 sky = center.add(0, headroom(level, center), 0);
        Cast cast = new Cast(player, center, sky, RADIUS, context.abilityLevel());
        CASTS.put(player.getUUID(), cast);
        ArmPosePayload.show(player, ArmPose.VIBE_RIFT, WARNING + 10);
        SetPiecePayload.send(level, Piece.VIBE_RIFT_ZONE, center, center, (float) RADIUS, null);
        // The sky breach: bound to the caster, whose palms feed it while the ring fills. Its seed is how long it stays.
        var sky_ = new SetPiecePayload(Piece.VIBE_RIFT_SKY.ordinal(), sky, center, (float) mouth(), player.getId(), LIFE, 0);
        for (ServerPlayer viewer : level.players())
            if (viewer.distanceToSqr(center) < 160 * 160 && !com.jayptucker.heroesevolved.time.TimeVeil.masks(viewer, center))
                PacketDistributor.sendToPlayer(viewer, sky_);
        StyleOps.sound(level, center, SoundEvents.RESPAWN_ANCHOR_CHARGE, .9F, 1.1F);
        StyleOps.sound(level, sky, ModSounds.VIBE_BREACH.get(), 1.4F, .55F);
        return true;
    }

    /** How wide the sky breach is. */
    static double mouth() { return 1.6 + RADIUS * .3; }

    /** Clear air over the zone, up to {@link #SKY}: a breach in a cave or a hall opens just under the ceiling. */
    private static double headroom(ServerLevel level, Vec3 center) {
        for (double up = 2; up <= SKY; up += 1) {
            BlockPos cell = BlockPos.containing(center.add(0, up, 0));
            if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) return Math.max(3.2, up - 2.4);
        }
        return SKY;
    }

    /** Whether a breach can take this body: not a boss, nothing too big, nothing held still by another power. */
    static boolean fits(LivingEntity foe) {
        return foe.isAlive() && !foe.isPassenger() && !foe.isVehicle() && foe.getBbWidth() <= 1.6 && foe.getBbHeight() <= 2.6
                && !foe.getType().is(Tags.EntityTypes.BOSSES)
                && !com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(foe)
                && !com.jayptucker.heroesevolved.time.TimeStopService.isStopped(foe);
    }

    static void tick(MinecraftServer server) {
        for (Cast cast : List.copyOf(CASTS.values())) {
            if (cast.owner.server != server) continue;
            long age = cast.level.getGameTime() - cast.start;
            if (cast.drops == null) {
                if (!VibeService.active(cast.owner) || cast.owner.serverLevel() != cast.level) {
                    // The caster was stopped before the rift opened: nothing falls.
                    end(cast);
                    continue;
                }
                if (age >= WARNING) open(cast);
                continue;
            }
            for (Drop drop : cast.drops) {
                if (drop.done) continue;
                if (drop.start < 0) {
                    if (age >= WARNING + (long) drop.index * STEP) swallow(cast, drop);
                } else {
                    fall(cast, drop);
                }
            }
            if (age >= LIFE && cast.drops.stream().allMatch(drop -> drop.done)) end(cast);
        }
    }

    /** The ring is full: everyone still in it is taken, nearest first. */
    private static void open(Cast cast) {
        cast.drops = new ArrayList<>();
        int index = 0;
        for (LivingEntity foe : StyleOps.foes(cast.owner, cast.center, cast.radius + 1.5)) {
            if (!VibeService.hostile(cast.owner, foe) || Math.abs(foe.getY() - cast.center.y) > 3) continue;
            Vec3 offset = foe.position().subtract(cast.center).multiply(1, 0, 1);
            if (offset.length() > cast.radius + foe.getBbWidth() * .5) continue;
            cast.drops.add(new Drop(foe, index++));
            if (index >= MOST) break;
        }
        CameraShakePayload.sendAround(cast.level, cast.center, 30, .18F, 8);
        StyleOps.sound(cast.level, cast.center, ModSounds.VIBE_COLLAPSE.get(), .8F, .6F);
    }

    /** A tear opens under the foe and it drops through, to come out of the sky breach. */
    private static void swallow(Cast cast, Drop drop) {
        LivingEntity foe = drop.foe;
        if (!foe.isAlive() || foe.level() != cast.level) {
            drop.done = true;
            return;
        }
        Vec3 feet = foe.position();
        SetPiecePayload.send(cast.level, Piece.VIBE_BREACH_ARRIVE, feet.add(0, .06, 0), feet.add(0, 1.06, 0), 1, null);
        SetPiecePayload.send(cast.level, Piece.VIBE_BREACH_CLOSE, feet.add(0, .06, 0), feet.add(0, 1.06, 0), 1, null, 3);
        StyleOps.sound(cast.level, feet, ModSounds.VIBE_SHUT.get(), .8F, .8F);
        if (!fits(foe)) {
            land(cast, drop, feet, false);
            return;
        }
        // Out of the sky breach in a loose spiral, so a crowd doesn't land in one heap.
        double angle = drop.index * 2.39996, ring = drop.index == 0 ? 0 : Math.min(cast.radius * .55, .7 + .28 * drop.index);
        Vec3 top = cast.sky.add(Math.cos(angle) * ring, -foe.getBbHeight() - .3, Math.sin(angle) * ring);
        if (!cast.level.noCollision(foe, foe.getDimensions(foe.getPose()).makeBoundingBox(top)))
            top = cast.sky.add(0, -foe.getBbHeight() - .3, 0);
        Vec3 ground = StyleOps.ground(cast.level, top);
        if (ground.equals(top) || ground.y > top.y) ground = feet;
        drop.top = top;
        drop.ground = ground;
        drop.start = cast.level.getGameTime();
        if (foe instanceof Mob mob) mob.getNavigation().stop();
        move(cast.level, foe, top);
        SetPiecePayload.send(cast.level, Piece.VIBE_TRANSIT, cast.sky, cast.sky.add(0, -1, 0), 1.2F, null);
        SetPiecePayload.send(cast.level, Piece.VIBE_RIFT_FALL, top, ground, Math.max(.6F, foe.getBbHeight()), foe);
    }

    /** Down out of the sky, faster and faster, on a fixed line. */
    private static void fall(Cast cast, Drop drop) {
        LivingEntity foe = drop.foe;
        if (!foe.isAlive() || foe.level() != cast.level) {
            drop.done = true;
            return;
        }
        double f = Math.min(1, (cast.level.getGameTime() - drop.start) / (double) FALL);
        if (f >= 1) {
            move(cast.level, foe, drop.ground);
            land(cast, drop, drop.ground, true);
            return;
        }
        move(cast.level, foe, new Vec3(drop.top.x, drop.top.y + (drop.ground.y - drop.top.y) * f * f, drop.top.z));
    }

    private static void land(Cast cast, Drop drop, Vec3 at, boolean fell) {
        drop.done = true;
        LivingEntity foe = drop.foe;
        ServerPlayer owner = cast.owner;
        foe.resetFallDistance();
        foe.setDeltaMovement(Vec3.ZERO);
        boolean read = VibeSight.marked(owner, foe);
        float damage = (6 + 2 * cast.tier) * (read ? 1.3F : 1) * (fell ? 1 : .8F);
        foe.hurt(owner.damageSources().indirectMagic(owner, owner), damage);
        int duration = read && SkillTreeService.has(owner, "vibe_fixed_point") ? 100 : 55;
        VibeService.desync(foe, duration);
        Vec3 away = foe.position().subtract(cast.center).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-3 ? new Vec3(0, 0, 0) : away.normalize();
        double resistance = foe.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        StyleOps.push(foe, away.scale(.3).add(0, .28, 0).scale(1 - resistance));
        Vec3 body = foe.getBoundingBox().getCenter();
        SetPiecePayload.send(cast.level, Piece.VIBE_HIT, body, body.add(0, 1, 0), 1.2F, foe);
        SetPiecePayload.send(cast.level, Piece.VIBE_LOCK, at, at, 1.8F, null);
        CameraShakePayload.sendAround(cast.level, at, 18, fell ? .22F : .14F, 6);
        StyleOps.sound(cast.level, at, ModSounds.VIBE_COLLAPSE.get(), .9F, fell ? .8F : 1F);
        StyleOps.sound(cast.level, at, SoundEvents.GENERIC_EXPLODE.value(), .35F, 1.5F);
    }

    /** Moves a falling body, and tells every client at once (a mob's position otherwise goes out every few ticks). */
    private static void move(ServerLevel level, LivingEntity foe, Vec3 at) {
        if (foe instanceof ServerPlayer player) {
            player.teleportTo(level, at.x, at.y, at.z, player.getYRot(), player.getXRot());
            player.absMoveTo(at.x, at.y, at.z);
        } else {
            foe.teleportTo(at.x, at.y, at.z);
            level.getChunkSource().broadcast(foe, new ClientboundTeleportEntityPacket(foe));
        }
        foe.resetFallDistance();
        foe.setDeltaMovement(0, -.4, 0);
    }

    private static void end(Cast cast) {
        CASTS.remove(cast.owner.getUUID());
        StyleOps.sound(cast.level, cast.sky, ModSounds.VIBE_SHUT.get(), 1.2F, .6F);
    }

    /** Package-visible for tests: whether a rift is open over this caster's zone. */
    static boolean open(ServerPlayer player) { return CASTS.containsKey(player.getUUID()); }

    /** Package-visible for tests: the sky breach's centre. */
    static Vec3 sky(ServerPlayer player) {
        Cast cast = CASTS.get(player.getUUID());
        return cast == null ? null : cast.sky;
    }

    static void forget(ServerPlayer player) {
        Cast cast = CASTS.get(player.getUUID());
        // Bodies already falling are put down where they would land; nothing is left hanging in the air.
        if (cast != null && cast.drops != null)
            for (Drop drop : cast.drops) if (!drop.done && drop.start >= 0) move(cast.level, drop.foe, drop.ground);
        CASTS.remove(player.getUUID());
    }

    static void clear() {
        for (Cast cast : List.copyOf(CASTS.values())) forget(cast.owner);
        CASTS.clear();
    }
}
