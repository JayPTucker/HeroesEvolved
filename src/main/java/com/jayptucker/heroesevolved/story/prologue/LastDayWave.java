package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.DazePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ted's big wave ({@link PrologueTimeline#WAVE}), on the server: as its front ({@link PrologueTimeline#front}) passes,
 * about half the glass it reaches bursts, pane by pane, so every window is left jagged (round the players, where anyone
 * can see it; logged in {@link LastDayDamage} and put back when the day ends), and the shards lie on the ground below
 * (flat glass block displays, no collision, gone with the day); everyone it reaches is thrown flat on their back for
 * {@link PrologueTimeline#DAZE} ticks, ears
 * ringing ({@link DazePayload}), and held there. Odessa's people go down with them. Anyone already below ground feels it
 * through the rock.
 */
final class LastDayWave {
    /** How far round the players the glass goes. */
    static final int REACH = 96;
    private float swept;
    /** Who the wave has reached, and when each of them gets up (ticks after the light). */
    private final Map<UUID, Integer> down = new HashMap<>();
    private int windows, shards;
    /** The first pane the wave burst, for tests. */
    BlockPos firstWindow;
    /** How much of the glass gives way, and how many shards lie about at most. */
    static final float BURSTS = 0.5F;
    static final int MAX_SHARDS = 360;

    /** Whether the wave has reached this player yet. */
    boolean reached(ServerPlayer player) { return down.containsKey(player.getUUID()); }

    /** Whether this player is still on the ground. */
    boolean downNow(ServerPlayer player, int t) {
        Integer until = down.get(player.getUUID());
        return until != null && t < until;
    }

    /** When this player got (or gets) back up, or -1 if the wave hasn't reached them. */
    int upAt(ServerPlayer player) { return down.getOrDefault(player.getUUID(), -1); }

    int windows() { return windows; }

    int shards() { return shards; }

    /**
     * {@code t}: ticks since the light. {@code players}: everyone in the day, with whether each is below ground already.
     * Returns the ones it knocked down this tick (for the day's own lines).
     */
    void tick(ServerLevel city, int t, List<ServerPlayer> players, java.util.function.Predicate<ServerPlayer> below,
              LastDayDamage damage, com.jayptucker.heroesevolved.time.intro.VisionCrowd crowd, int crowdClock) {
        if (t < PrologueTimeline.WAVE) return;
        float front = PrologueTimeline.front(t - PrologueTimeline.WAVE);
        if (front <= 400) {
            sweep(city, players, swept, front, damage, t);
            swept = front;
            if (crowd != null) crowd.knockDown(front, crowdClock + PrologueTimeline.DAZE - 10);
        }
        for (ServerPlayer p : players) {
            if (down.containsKey(p.getUUID())) continue;
            if (IntroBlast.distance(p.getX(), p.getZ()) > front) continue;
            if (below.test(p)) {
                // Through a hundred feet of rock: the floor jumps, dust sifts down.
                down.put(p.getUUID(), t);
                PacketDistributor.sendToPlayer(p, new CameraShakePayload(0.35F, 30));
                // The pod sequence owns the single underground blast cue.
                continue;
            }
            knock(p, t);
        }
        // Held down while they come to; up again after.
        for (ServerPlayer p : players) {
            Integer until = down.get(p.getUUID());
            if (until == null) continue;
            if (t < until) {
                p.setForcedPose(Pose.SLEEPING);
                if (t > until - PrologueTimeline.DAZE + 6) p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
                p.fallDistance = 0;
            } else if (p.getForcedPose() == Pose.SLEEPING) {
                // Up again (on the very tick, or the first one after it if the day jumped).
                p.setForcedPose(null);
                p.sendSystemMessage(Component.translatable("prologue.heroesevolved.come_to").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    /** Thrown off their feet, back first, away from the light: then flat on the ground. */
    private void knock(ServerPlayer p, int t) {
        down.put(p.getUUID(), t + PrologueTimeline.DAZE);
        if (EntityCarryService.isCarrying(p)) EntityCarryService.release(p);
        Vec3 away = p.position().subtract(IntroBlast.CENTER).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
        p.setDeltaMovement(away.scale(0.9).add(0, 0.35, 0));
        p.hurtMarked = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        PacketDistributor.sendToPlayer(p, new DazePayload(PrologueTimeline.DAZE, true, PrologueTimeline.BLACKOUT));
        PacketDistributor.sendToPlayer(p, new CameraShakePayload(0.9F, 34));
        ScreenTintPayload.send(p, 0xF0FFF6E8, 18, ScreenTintPayload.Style.FLASH);
        p.sendSystemMessage(Component.translatable("prologue.heroesevolved.wave").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /**
     * The glass from a burst pane, lying on the ground in front of its wall: a few flat shards, scattered and turned every
     * which way, where the street (or a ledge) catches them.
     */
    private void shatter(ServerLevel city, BlockPos pane) {
        net.minecraft.core.Direction out = null;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos n = pane.relative(d), back = pane.relative(d.getOpposite());
            // The side that's open to the outside: air there, and building (or more glass) behind.
            if (city.getBlockState(n).isAir() && city.getBlockState(n.relative(d)).isAir() && !city.getBlockState(back).isAir()) { out = d; break; }
        }
        if (out == null) return;
        int count = 1 + Math.floorMod(CityGrid.hash(pane.getX(), pane.getY(), pane.getZ(), 29), 3);
        for (int i = 0; i < count && shards < MAX_SHARDS; i++) {
            float h1 = CityGrid.hash(pane.getX(), pane.getY(), pane.getZ(), 31 + i) / (float) Integer.MAX_VALUE;
            float h2 = CityGrid.hash(pane.getX(), pane.getY(), pane.getZ(), 41 + i) / (float) Integer.MAX_VALUE;
            float h3 = CityGrid.hash(pane.getX(), pane.getY(), pane.getZ(), 53 + i) / (float) Integer.MAX_VALUE;
            double reach = 0.6 + h1 * 2.6, along = (h2 - 0.5) * 1.6;
            double x = pane.getX() + .5 + out.getStepX() * reach + (out.getStepZ() != 0 ? along : 0);
            double z = pane.getZ() + .5 + out.getStepZ() * reach + (out.getStepX() != 0 ? along : 0);
            // Down to whatever catches it.
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(Mth.floor(x), pane.getY(), Mth.floor(z));
            while (at.getY() > CityGrid.STREET_Y && city.getBlockState(at.below()).getCollisionShape(city, at.below()).isEmpty()) at.move(0, -1, 0);
            if (!city.getBlockState(at).getCollisionShape(city, at).isEmpty()) continue;
            BlockPos floor = at.below();
            double top = floor.getY() + city.getBlockState(floor).getCollisionShape(city, floor).max(net.minecraft.core.Direction.Axis.Y);
            shard(city, new Vec3(x, top + 0.005, z), h3, h1, h2);
        }
    }

    /** One shard of glass lying flat: a block display of glass, thin as a pane, turned and tipped a little. */
    private void shard(ServerLevel city, Vec3 at, float turn, float size, float tilt) {
        net.minecraft.world.entity.Display.BlockDisplay shard = net.minecraft.world.entity.EntityType.BLOCK_DISPLAY.create(city);
        if (shard == null) return;
        float angle = turn * Mth.TWO_PI, tip = (tilt - 0.5F) * 0.25F;
        float sx = 0.16F + 0.34F * size, sz = 0.1F + 0.26F * tilt;
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.put("block_state", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.GLASS.defaultBlockState()));
        net.minecraft.nbt.CompoundTag form = new net.minecraft.nbt.CompoundTag();
        form.put("left_rotation", floats(0, Mth.sin(angle / 2), 0, Mth.cos(angle / 2)));
        form.put("right_rotation", floats(Mth.sin(tip / 2), 0, 0, Mth.cos(tip / 2)));
        form.put("translation", floats(-sx / 2, 0, -sz / 2));
        form.put("scale", floats(sx, 0.015F, sz));
        data.put("transformation", form);
        shard.load(data);
        shard.moveTo(at.x, at.y, at.z, 0, 0);
        shard.addTag(PrologueService.TAG);
        if (city.addFreshEntity(shard)) shards++;
    }

    private static net.minecraft.nbt.ListTag floats(float... values) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (float v : values) list.add(net.minecraft.nbt.FloatTag.valueOf(v));
        return list;
    }

    /** About half the glass between {@code from} and {@code to} blocks from ground zero, near enough to a player to be seen, bursts. */
    private void sweep(ServerLevel city, List<ServerPlayer> players, float from, float to, LastDayDamage damage, int t) {
        if (players.isEmpty()) return;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (ServerPlayer p : players) {
            if (p.level() != city) continue;
            x0 = Math.min(x0, p.getBlockX() - REACH); x1 = Math.max(x1, p.getBlockX() + REACH);
            z0 = Math.min(z0, p.getBlockZ() - REACH); z1 = Math.max(z1, p.getBlockZ() + REACH);
        }
        x0 = Math.max(x0, CityGrid.MIN); x1 = Math.min(x1, CityGrid.MAX);
        z0 = Math.max(z0, CityGrid.MIN); z1 = Math.min(z1, CityGrid.MAX);
        double cx = IntroBlast.CENTER.x, cz = IntroBlast.CENTER.z;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int sounds = 0, bursts = 0;
        for (int x = x0; x <= x1; x++) {
            double dx = x + 0.5 - cx;
            for (int z = z0; z <= z1; z++) {
                double d = Math.sqrt(dx * dx + (z + 0.5 - cz) * (z + 0.5 - cz));
                if (d < from || d >= to) continue;
                boolean near = false;
                for (ServerPlayer p : players) if (Math.abs(p.getX() - x) <= REACH && Math.abs(p.getZ() - z) <= REACH) { near = true; break; }
                if (!near) continue;
                int top = city.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = CityGrid.STREET_Y; y < top; y++) {
                    pos.set(x, y, z);
                    BlockState state = city.getBlockState(pos);
                    if (!state.is(Tags.Blocks.GLASS_BLOCKS) && !state.is(Tags.Blocks.GLASS_PANES)) continue;
                    // About half of it gives way, pane by pane: what's left stands jagged in the frames.
                    if (CityGrid.hash(x, y, z, 17) / (float) Integer.MAX_VALUE >= BURSTS) continue;
                    damage.set(city, pos, Blocks.AIR.defaultBlockState());
                    windows++;
                    if (firstWindow == null) firstWindow = pos.immutable();
                    double closest = Double.MAX_VALUE;
                    for (ServerPlayer p : players) closest = Math.min(closest, p.distanceToSqr(x + .5, y + .5, z + .5));
                    if (closest < 40 * 40 && y < CityGrid.STREET_Y + 16 && shards < MAX_SHARDS && Math.floorMod(CityGrid.hash(x, y, z, 23), 5) < 2)
                        shatter(city, pos);
                    // A burst of shards and a crash for the windows close to someone; the rest go quietly in the din.
                    double close = Double.MAX_VALUE;
                    for (ServerPlayer p : players) close = Math.min(close, p.distanceToSqr(x + .5, y + .5, z + .5));
                    if (close < 40 * 40 && bursts < 120 && Math.floorMod(x * 7 + y * 13 + z * 3, 3) == 0) {
                        bursts++;
                        city.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x + .5, y + .5, z + .5, 4, 0.3, 0.3, 0.3, 0.25);
                    }
                    if (close < 28 * 28 && sounds < 4 && Math.floorMod(x + z * 5 + y, 7) == 0) {
                        sounds++;
                        city.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.4F, 0.7F + Math.floorMod(x + y, 5) * 0.08F);
                    }
                }
            }
        }
    }
}
