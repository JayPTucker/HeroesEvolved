package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import com.jayptucker.heroesevolved.time.SpaceTimeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VibeBreaches {
    private static final String ANCHOR = "heroesevolved_vibe_anchor";
    private static final int LOAD_TIMEOUT = 60, LIFETIME = 180;
    private static final TicketType<UUID> BREACH_TICKET = TicketType.create("heroesevolved_breach", Comparator.<UUID>naturalOrder());
    private static final Map<UUID, Gate> GATES = new HashMap<>();
    private static final Set<UUID> CROSSING = new HashSet<>();

    private record Mouth(ServerLevel level, Vec3 feet, Vec3 direction) {
        Vec3 center() { return feet.add(0, VibeGeometry.PORTAL_HEIGHT, 0); }
        Vec3 arrival() { return arrival(1); }
        /** The side of this mouth a traveler comes out on: +1 ahead of it, −1 behind it. */
        Vec3 arrival(double side) { return feet.add(direction.scale(1.14 * side)); }
        float yaw() { return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z)); }
        ChunkPos chunk() { return new ChunkPos(BlockPos.containing(feet)); }
    }

    private record Approach(ServerLevel world, Vec3 center) {}

    /** Where a wave passes into one mouth ({@code through}) and where, heading which way, it leaves the other. */
    record Seam(Vec3 through, Vec3 out, Vec3 direction) {}

    private static final class Gate {
        final ServerPlayer owner;
        final Mouth entry, exit;
        final boolean tuned, sanctuary;
        final long started;
        final Map<UUID, Approach> previous = new HashMap<>();
        long opened = -1;

        Gate(ServerPlayer owner, Mouth entry, Mouth exit, boolean tuned, boolean sanctuary) {
            this.owner = owner;
            this.entry = entry;
            this.exit = exit;
            this.tuned = tuned;
            this.sanctuary = sanctuary;
            started = entry.level().getGameTime();
        }
    }

    private VibeBreaches() {}

    public static boolean open(ServerPlayer player, boolean tune, boolean sanctuary) {
        if (!VibeService.ready(player) || GATES.containsKey(player.getUUID())) return false;
        if (!travelWorld(player.serverLevel())) return fail(player, "restricted");
        Vec3 direction = StyleOps.facing(player);
        Vec3 feet = player.position().add(direction.scale(2));
        Mouth entry = new Mouth(player.serverLevel(), feet, direction);
        if (!clearMouth(player, entry) || !clearLine(player, feet.add(0, player.getEyeHeight(), 0))
                || !safe(player, entry, entry.arrival()))
            return fail(player, "blocked");

        boolean tuned = tune && VibeService.nativePower(player) && SkillTreeService.has(player, "vibe_world_frequency");
        Mouth exit;
        if (tuned) {
            exit = frequency(player);
            if (exit == null) return fail(player, "no_frequency");
        } else {
            double range = 30;
            Vec3 destination = aimedGround(player, range);
            if (destination == null && sanctuary) destination = ground(player, player.position().add(direction.scale(5)));
            if (destination == null) return fail(player, "no_exit");
            Vec3 back = player.position().subtract(destination).multiply(1, 0, 1).normalize();
            exit = null;
            for (int offset = 0; offset < 4; offset++) {
                Vec3 floor = offset == 0 ? destination : ground(player, destination.add(back.scale(offset)).add(0, .7, 0));
                if (floor == null || floor.distanceToSqr(entry.feet()) < 9) continue;
                Mouth candidate = new Mouth(player.serverLevel(), floor, direction);
                if (safe(player, candidate, candidate.arrival()) && clearMouth(player, candidate)) {
                    exit = candidate;
                    break;
                }
            }
            if (exit == null) return fail(player, "no_exit");
        }
        if (entry.level() == exit.level() && entry.feet().distanceToSqr(exit.feet()) < 9) return fail(player, "too_close");

        Gate gate = new Gate(player, entry, exit, tuned, sanctuary);
        GATES.put(player.getUUID(), gate);
        if (tuned) exit.level().getChunkSource().addRegionTicket(BREACH_TICKET, exit.chunk(), 3, player.getUUID());
        ArmPosePayload.show(player, ArmPose.VIBE_OPEN, 18);
        SetPiecePayload.send(entry.level(), Piece.VIBE_BREACH_SEED, player.position(), entry.center(), 1, player);
        StyleOps.sound(entry.level(), entry.center(), ModSounds.VIBE_BREACH.get(), .85F, 1.42F);
        return true;
    }

    public static boolean attune(ServerPlayer player) {
        if (!VibeService.nativePower(player) || !travelWorld(player.serverLevel())) return fail(player, "restricted");
        Mouth mouth = new Mouth(player.serverLevel(), player.position(), StyleOps.facing(player));
        if (!player.onGround() || !safe(player, mouth, mouth.arrival()) || !clearMouth(player, mouth))
            return fail(player, "no_exit");
        var tag = new CompoundTag();
        tag.putString("dimension", mouth.level().dimension().location().toString());
        tag.putDouble("x", mouth.feet().x);
        tag.putDouble("y", mouth.feet().y);
        tag.putDouble("z", mouth.feet().z);
        tag.putFloat("yaw", player.getYRot());
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(ANCHOR, tag);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ArmPosePayload.show(player, ArmPose.VIBE_READ, 18);
        VibeGeometry.privatePiece(player, Piece.VIBE_BREACH_ARRIVE, mouth.center(), mouth.center().add(mouth.direction()), 1, -1, 0);
        StyleOps.sound(mouth.level(), mouth.center(), SoundEvents.LODESTONE_COMPASS_LOCK, 1, 1.4F);
        player.displayClientMessage(Component.translatable("vibe.heroesevolved.attuned",
                mouth.level().dimension().location().toString(), player.getBlockX(), player.getBlockY(), player.getBlockZ()), true);
        return true;
    }

    private static Mouth frequency(ServerPlayer player) {
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(ANCHOR)) return null;
        var tag = persisted.getCompound(ANCHOR);
        ResourceLocation key = ResourceLocation.tryParse(tag.getString("dimension"));
        if (key == null) return null;
        ServerLevel world = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        if (world == null || !travelWorld(world)) return null;
        Vec3 feet = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        if (!Float.isFinite(tag.getFloat("yaw")) || !Double.isFinite(feet.lengthSqr()) || !world.getWorldBorder().isWithinBounds(BlockPos.containing(feet))
                || feet.y < world.getMinBuildHeight() || feet.y + 3 >= world.getMaxBuildHeight()) return null;
        return new Mouth(world, feet, Vec3.directionFromRotation(0, tag.getFloat("yaw")));
    }

    static boolean travelWorld(ServerLevel level) {
        // The mod's story/rewind worlds own their own entrances and exits.
        return !level.dimension().location().getNamespace().equals(HeroesEvolved.MOD_ID);
    }

    static Vec3 aimedGround(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 aim = player.getLookAngle();
        Vec3 end = eye.add(aim.scale(range));
        // March only through chunks already on the server, before asking for block collision.
        for (double d = 2; d <= range; d += 2) {
            Vec3 sample = eye.add(aim.scale(d));
            if (!player.serverLevel().hasChunkAt(BlockPos.containing(sample))) {
                end = eye.add(aim.scale(d - 2));
                break;
            }
        }
        Vec3 hit = VibeGeometry.clip(player, eye, end);
        if (hit.distanceToSqr(end) > .0001) hit = hit.subtract(aim.scale(.65));
        return ground(player, hit);
    }

    private static Vec3 ground(ServerPlayer player, Vec3 point) {
        if (!player.serverLevel().hasChunkAt(BlockPos.containing(point))) return null;
        Vec3 floor = StyleOps.ground(player.serverLevel(), point);
        if (floor.equals(point) || !SpaceTimeService.safe(player, floor)) return null;
        return floor;
    }

    public static boolean flash(ServerPlayer player) {
        if (!VibeService.ready(player) || !travelWorld(player.serverLevel())) return false;
        Vec3 point = aimedGround(player, 11);
        if (point == null || point.distanceToSqr(player.position()) < 4) return fail(player, "no_exit");
        Vec3 facing = StyleOps.facing(player);
        Vec3 origin = player.position().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        ArmPosePayload.show(player, ArmPose.VIBE_STEP, 13);
        SetPiecePayload.send(player.serverLevel(), Piece.VIBE_BREACH_ARRIVE, origin, origin.add(facing), 1, null);
        if (!move(player, player.serverLevel(), point)) return false;
        arrival(player, facing);
        return true;
    }

    static void rescue(ServerPlayer owner, net.minecraft.world.entity.LivingEntity ward, List<net.minecraft.world.entity.LivingEntity> threats) {
        Vec3 escape = threats.isEmpty() ? StyleOps.facing(owner)
                : ward.position().subtract(threats.getFirst().position()).multiply(1, 0, 1).normalize();
        if (escape.lengthSqr() < .1) escape = StyleOps.facing(owner);
        if (ward.isPassenger() || ward.isVehicle()) return;
        // swept returns the furthest safe position, not a displacement.
        Vec3 move = com.jayptucker.heroesevolved.speed.SpeedService.swept(ward, escape.scale(7)).subtract(ward.position());
        if (move.lengthSqr() < 9) return;
        Vec3 to = StyleOps.ground(owner.serverLevel(), ward.position().add(move).add(0, .35, 0));
        if (!SpaceTimeService.safe(ward, to) || Math.abs(to.y - ward.getY()) > 1.1) return;
        Vec3 from = ward.position().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        SetPiecePayload.send(owner.serverLevel(), Piece.VIBE_BREACH_ARRIVE, from, from.add(escape), 1, null);
        if (ward instanceof ServerPlayer player) {
            if (move(player, owner.serverLevel(), to)) arrival(player, escape);
        } else {
            ward.teleportTo(to.x, to.y, to.z);
            ward.resetFallDistance();
            SetPiecePayload.send(owner.serverLevel(), Piece.VIBE_BREACH_ARRIVE, to.add(0, VibeGeometry.PORTAL_HEIGHT, 0),
                    to.add(escape).add(0, VibeGeometry.PORTAL_HEIGHT, 0), 1, null);
        }
    }

    public static boolean close(ServerPlayer player) {
        Gate gate = GATES.remove(player.getUUID());
        if (gate == null) return false;
        if (gate.tuned) gate.exit.level().getChunkSource().removeRegionTicket(BREACH_TICKET, gate.exit.chunk(), 3, player.getUUID());
        if (gate.opened >= 0) {
            for (Mouth mouth : List.of(gate.entry, gate.exit)) {
                show(mouth, Piece.VIBE_BREACH_CLOSE);
                StyleOps.sound(mouth.level(), mouth.center(), ModSounds.VIBE_SHUT.get(), .65F, 1.55F);
            }
        } else {
            SetPiecePayload.send(gate.entry.level(), Piece.VIBE_BREACH_CLOSE, gate.entry.center(),
                    gate.entry.center().add(gate.entry.direction()), 1, null);
        }
        return true;
    }

    static void tick(MinecraftServer server) {
        for (Gate gate : List.copyOf(GATES.values())) {
            if (gate.owner.server != server) continue;
            long now = gate.entry.level().getGameTime();
            if (!VibeService.active(gate.owner) || gate.owner.isPassenger()
                    || gate.owner.level() != gate.entry.level() && gate.owner.level() != gate.exit.level()
                    || gate.opened >= 0 && now - gate.opened >= LIFETIME) {
                close(gate.owner);
                continue;
            }
            if (gate.opened < 0) {
                if (now - gate.started < VibeGeometry.BREACH_OPEN_TICKS) continue;
                if (gate.exit.level().getChunkSource().getChunkNow(gate.exit.chunk().x, gate.exit.chunk().z) == null) {
                    if (now - gate.started < LOAD_TIMEOUT) continue;
                    fail(gate.owner, "no_exit");
                    close(gate.owner);
                    continue;
                }
                if (!clearMouth(gate.owner, gate.entry) || !clearMouth(gate.owner, gate.exit)
                        || !safe(gate.owner, gate.entry, gate.entry.arrival())
                        || !safe(gate.owner, gate.exit, gate.exit.arrival())) {
                    fail(gate.owner, "no_exit");
                    close(gate.owner);
                    continue;
                }
                gate.opened = now;
                for (Mouth mouth : List.of(gate.entry, gate.exit)) {
                    show(mouth, Piece.VIBE_BREACH_ARRIVE);
                    StyleOps.sound(mouth.level(), mouth.center(), SoundEvents.AMETHYST_BLOCK_CHIME, .44F, 1.65F);
                }
            }
            if ((now - gate.opened) % 10 == 0) {
                if (!clearMouth(gate.owner, gate.entry) || !clearMouth(gate.owner, gate.exit)) {
                    close(gate.owner);
                    continue;
                }
                show(gate.entry, Piece.VIBE_BREACH_OPEN);
                show(gate.exit, Piece.VIBE_BREACH_OPEN);
            }
            transit(gate);
        }
    }

    private static void transit(Gate gate) {
        Map<UUID, Approach> next = new HashMap<>();
        Set<UUID> moved = new HashSet<>();
        for (Mouth from : List.of(gate.entry, gate.exit)) {
            Mouth to = from == gate.entry ? gate.exit : gate.entry;
            for (ServerPlayer traveler : List.copyOf(from.level().players())) {
                if (moved.contains(traveler.getUUID()) || !PowerRules.canAct(traveler) || traveler.isPassenger()
                        || traveler.isVehicle() || traveler.isSleeping() || EntityCarryService.isCarrying(traveler)
                        || !StyleOps.isFriend(gate.owner, traveler)) continue;
                Vec3 center = traveler.getBoundingBox().getCenter();
                if (center.distanceToSqr(from.center()) > 196) continue;
                next.put(traveler.getUUID(), new Approach(from.level(), center));
                Approach previous = gate.previous.get(traveler.getUUID());
                if (previous == null || previous.world() != from.level() || previous.center().distanceToSqr(center) > 196) continue;
                if (VibeGeometry.crossing(previous.center(), center, from.center(), from.direction(), VibeGeometry.PORTAL_WIDTH,
                        VibeGeometry.PORTAL_HEIGHT) == null) continue;
                // Come out on the side you were heading for, turned by the angle between the mouths, so walking
                // back through the exit lands you behind the entry instead of in front of it (and bouncing back).
                double side = center.subtract(previous.center()).dot(from.direction()) < 0 ? -1 : 1;
                Vec3 arrival = to.arrival(side);
                if (!clearMouth(traveler, from) || !safe(traveler, to, arrival) || !clearMouth(traveler, to)) {
                    fail(traveler, "no_exit");
                    continue;
                }
                if (!move(traveler, to.level(), arrival, traveler.getYRot() + to.yaw() - from.yaw())) continue;
                moved.add(traveler.getUUID());
                next.put(traveler.getUUID(), new Approach(to.level(), traveler.getBoundingBox().getCenter()));
                arrival(traveler, to.direction().scale(side));
                if (traveler == gate.owner) VibeService.prime(traveler);
                if (SkillTreeService.has(gate.owner, "vibe_wayfarer"))
                    StyleOps.effect(traveler, net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 100, 0);
                if (gate.sanctuary || SkillTreeService.has(gate.owner, "vibe_horizon")) {
                    traveler.clearFire();
                    StyleOps.effect(traveler, net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 80, 0);
                }
            }
        }
        gate.previous.clear();
        gate.previous.putAll(next);
    }

    private static boolean move(ServerPlayer player, ServerLevel level, Vec3 to) {
        return move(player, level, to, player.getYRot());
    }

    private static boolean move(ServerPlayer player, ServerLevel level, Vec3 to, float yaw) {
        CROSSING.add(player.getUUID());
        boolean moved;
        try {
            yaw = Mth.wrapDegrees(yaw);
            moved = player.serverLevel() == level
                    ? player.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, player.getXRot())
                    : player.changeDimension(new DimensionTransition(level, to, Vec3.ZERO, yaw,
                            player.getXRot(), DimensionTransition.DO_NOTHING)) == player;
        } finally {
            CROSSING.remove(player.getUUID());
        }
        if (!moved) return false;
        player.resetFallDistance();
        StyleOps.push(player, Vec3.ZERO);
        return true;
    }

    static boolean crossing(ServerPlayer player) { return CROSSING.contains(player.getUUID()); }

    /**
     * Through the Seam: the first of the owner's open mouths the segment crosses, mapped onto the other mouth at the
     * same place across its face. Only links whose two mouths are in the owner's world carry a wave.
     */
    static Seam seam(ServerPlayer owner, Vec3 from, Vec3 to) {
        Gate gate = GATES.get(owner.getUUID());
        if (gate == null || gate.opened < 0 || gate.entry.level() != owner.serverLevel()
                || gate.exit.level() != owner.serverLevel()) return null;
        Seam best = null;
        double nearest = Double.MAX_VALUE;
        for (Mouth mouth : List.of(gate.entry, gate.exit)) {
            Vec3 hit = VibeGeometry.crossing(from, to, mouth.center(), mouth.direction(), VibeGeometry.PORTAL_WIDTH,
                    VibeGeometry.PORTAL_HEIGHT);
            if (hit == null || hit.distanceToSqr(from) >= nearest) continue;
            Mouth other = mouth == gate.entry ? gate.exit : gate.entry;
            float turn = other.yaw() - mouth.yaw();
            Vec3 offset = hit.subtract(mouth.center());
            double across = offset.dot(VibeGeometry.side(mouth.direction()));
            Vec3 out = other.center().add(VibeGeometry.side(other.direction()).scale(across)).add(0, offset.y, 0);
            best = new Seam(hit, out, turn(to.subtract(from).normalize(), turn));
            nearest = hit.distanceToSqr(from);
        }
        return best;
    }

    /** Turns a direction about the vertical by {@code degrees} of yaw, the way a traveler is turned between mouths. */
    static Vec3 turn(Vec3 direction, float degrees) {
        if (Math.abs(degrees) < 1e-3) return direction;
        double yaw = Math.atan2(-direction.x, direction.z) + Math.toRadians(degrees);
        double flat = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return new Vec3(-Math.sin(yaw) * flat, direction.y, Math.cos(yaw) * flat);
    }

    private static void arrival(ServerPlayer player, Vec3 direction) {
        Vec3 center = player.position().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        SetPiecePayload.send(player.serverLevel(), Piece.VIBE_TRANSIT, center, center.add(direction), 1, player);
        ScreenTintPayload.send(player, 0x4329B8FF, 12, ScreenTintPayload.Style.VIGNETTE);
        StyleOps.sound(player.serverLevel(), center, ModSounds.VIBE_SHUT.get(), .65F, 1.55F);
    }

    private static boolean clearMouth(ServerPlayer player, Mouth mouth) {
        Vec3 side = VibeGeometry.side(mouth.direction()).scale(.7);
        for (Vec3 foot : List.of(mouth.feet(), mouth.feet().add(side), mouth.feet().subtract(side))) {
            var box = new net.minecraft.world.phys.AABB(foot.x - .12, foot.y + .05, foot.z - .12,
                    foot.x + .12, foot.y + VibeGeometry.PORTAL_HEIGHT * 2, foot.z + .12);
            if (!clear(player, mouth.level(), box)) return false;
        }
        return true;
    }

    private static boolean safe(ServerPlayer player, Mouth mouth, Vec3 at) {
        var box = player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(at);
        BlockPos floor = BlockPos.containing(at.subtract(0, .05, 0));
        var level = mouth.level();
        if (!clear(player, level, box) || !level.hasChunkAt(floor)) return false;
        var state = level.getBlockState(floor);
        return !state.getCollisionShape(level, floor).isEmpty() && !state.is(BlockTags.FIRE)
                && !state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && !state.is(net.minecraft.world.level.block.Blocks.CACTUS)
                && !state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
    }

    private static boolean clear(ServerPlayer player, ServerLevel level, net.minecraft.world.phys.AABB box) {
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty() || level.getBlockState(pos).is(BlockTags.FIRE)
                    || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) return false;
        }
        return !level.getBlockCollisions(player, box).iterator().hasNext();
    }

    private static boolean clearLine(ServerPlayer player, Vec3 to) {
        return VibeGeometry.clip(player, player.getEyePosition(), to).distanceToSqr(to) < .0001;
    }

    private static void show(Mouth mouth, Piece piece) {
        SetPiecePayload.send(mouth.level(), piece, mouth.center(), mouth.center().add(mouth.direction()), 1, null);
    }

    static boolean fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("vibe.heroesevolved." + key), true);
        return false;
    }

    static void clear() {
        for (Gate gate : List.copyOf(GATES.values())) close(gate.owner);
        CROSSING.clear();
    }
}
