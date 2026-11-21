package com.jayptucker.heroesevolved.village.overload;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cryo.TemporaryBlocks;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A cryokinetic's overload: a whiteout out to {@link OverloadGeometry#reach}. Anyone out in the open in it freezes (the
 * vanilla frozen state, as in powder snow: the cold gains on the thaw by {@link OverloadGeometry#chill} a tick), unless
 * they are under a roof, warm (within {@link OverloadGeometry#WARM_REACH} of a fire, a lit campfire, lava or a
 * pyrokinetic), or wearing leather (vanilla's {@code canFreeze}). Snow settles and still water freezes over, both
 * temporary ({@link TemporaryBlocks}), only where {@code mobGriefing} allows. Its village goes indoors, as for a raid's
 * bell. The eye is hidden in the snow from all but telepaths and Sonic Resonance users. The break is the Deep Freeze.
 */
final class Blizzard implements Weather {
    /** Most snow and ice one blizzard lays at once; how long it lasts unless the storm keeps it. */
    static final int MOST_PLACED = 2400, SNOW_TICKS = 900;

    static final class State {
        /** The fraction of a frozen tick each creature has gained and not yet been given. */
        final Map<UUID, Float> chill = new HashMap<>();
        /** Whether each was warm when last looked (every ten ticks). */
        final Map<UUID, Boolean> warm = new HashMap<>();
        final List<BlockPos> placed = new ArrayList<>();
    }

    static State state(Overload o) {
        if (!(o.weather instanceof State)) o.weather = new State();
        return (State) o.weather;
    }

    @Override
    public void begin(Overload o) {
        state(o);
        indoors(o, o.level.getGameTime());
    }

    @Override public double reach(Overload o) { return o.stage == Overload.Stage.BURNING ? OverloadGeometry.reach(1) + 8 : OverloadGeometry.reach(o.progress()); }

    /**
     * Out in the open: nothing solid over their head. Read from the heightmap, which is exact the moment a roof is built;
     * sky light ({@code canSeeSky}) lags behind the light engine and once let the cold through a fresh roof.
     */
    static boolean exposed(ServerLevel level, LivingEntity e) {
        BlockPos head = BlockPos.containing(e.getEyePosition());
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, head.getX(), head.getZ()) <= head.getY();
    }

    @Override
    public boolean sees(Overload o, ServerPlayer p) {
        return Overloads.holds(p, ModAbilities.TELEPATHY_ID) || Overloads.holds(p, ModAbilities.SONIC_RESONANCE_ID)
                || Overloads.holds(p, ModAbilities.CRYOKINESIS_ID);
    }

    @Override
    public void tick(Overload o, long now) {
        if (!o.harms()) return;
        State s = state(o);
        float power = o.power();
        double reach = reach(o);
        Vec3 eye = o.eye();
        for (LivingEntity e : o.level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(reach, 24, reach),
                e -> !Overloads.spared(o, e) && e.canFreeze() && e.position().subtract(eye).horizontalDistanceSqr() <= reach * reach)) {
            UUID id = e.getUUID();
            if (now % 10 == 0 || !s.warm.containsKey(id)) s.warm.put(id, warm(o, e));
            if (s.warm.get(id) || !exposed(o.level, e)) continue;
            // Vanilla thaws two a tick out of powder snow: give it back, and the chill on top.
            float gain = s.chill.getOrDefault(id, 0F) + 2 + OverloadGeometry.chill(power);
            int add = (int) gain;
            s.chill.put(id, gain - add);
            e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + add));
        }
        if (now % 200 == 0) indoors(o, now);
        if (now % 200 == 100) for (BlockPos p : s.placed) TemporaryBlocks.refresh(o.level, p, SNOW_TICKS, null);
        if (Wildfire.griefing(o.level) && now % 3 == 0) settle(o, s, reach, 2 + (int) (3 * power));
        if (now % 24 == 0) {
            o.level.playSound(null, eye.x, eye.y + 2, eye.z, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 2.0F + power, 0.4F);
            o.level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.POWDER_SNOW_STEP, SoundSource.NEUTRAL, 1.5F, 0.5F);
        }
    }

    /**
     * Warm: within {@link OverloadGeometry#WARM_REACH} of a pyrokinetic, or of fire, lava or a lit campfire (a few blocks
     * up and down).
     */
    static boolean warm(Overload o, LivingEntity e) {
        double r = OverloadGeometry.WARM_REACH;
        for (ServerPlayer p : o.level.players())
            if (p != e && p.distanceToSqr(e) <= r * r && Overloads.holds(p, ModAbilities.PYROKINESIS_ID)) return true;
        if (e instanceof ServerPlayer self && Overloads.holds(self, ModAbilities.PYROKINESIS_ID)) return true;
        BlockPos at = e.blockPosition();
        int n = (int) r;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-n, -2, -n), at.offset(n, 2, n))) {
            BlockState s = o.level.getBlockState(p);
            if (s.is(BlockTags.FIRE) || s.is(Blocks.LAVA)
                    || s.is(BlockTags.CAMPFIRES) && s.getValue(BlockStateProperties.LIT)) return true;
        }
        return false;
    }

    /** Everyone of the village goes indoors, as when the bell rings for a raid (and stays while it lasts). */
    private static void indoors(Overload o, long now) {
        for (UUID id : o.village)
            if (o.level.getEntity(id) instanceof Villager v && v.isAlive() && !v.isSleeping())
                v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
    }

    @Override
    public void villagers(Overload o, List<Villager> inDanger) {
        // Caught out in it: home, at a run.
        long now = o.level.getGameTime();
        for (Villager v : inDanger) v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
    }

    /**
     * Snow settles on open ground in its reach, in drifts (a spot and the four round it) that lie thickest near the eye,
     * and still water freezes over. Both melt away once it stops keeping them.
     */
    private static void settle(Overload o, State s, double reach, int n) {
        if (s.placed.size() >= MOST_PLACED) return;
        Vec3 eye = o.eye();
        for (int i = 0; i < n && s.placed.size() < MOST_PLACED; i++) {
            double a = o.level.random.nextDouble() * Math.PI * 2, d = Math.pow(o.level.random.nextDouble(), 0.8) * reach;
            int x = (int) Math.floor(eye.x + Math.cos(a) * d), z = (int) Math.floor(eye.z + Math.sin(a) * d);
            // A drift: the spot, most of the four beside it and some of the corners, so drifts join up unevenly.
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    float keep = dx == 0 && dz == 0 ? 1 : dx == 0 || dz == 0 ? 0.8F : 0.45F;
                    if (o.level.random.nextFloat() < keep) settleAt(o, s, new BlockPos(x + dx, 0, z + dz));
                }
        }
    }

    private static void settleAt(Overload o, State s, BlockPos column) {
        if (s.placed.size() >= MOST_PLACED || !o.level.hasChunkAt(column)) return;
        BlockPos top = o.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column), below = top.below();
        BlockState under = o.level.getBlockState(below);
        if (under.is(Blocks.WATER) && under.getFluidState().isSource()) {
            if (TemporaryBlocks.place(o.level, below, Blocks.ICE.defaultBlockState(), SNOW_TICKS, null)) s.placed.add(below);
        } else if (o.level.getBlockState(top).isAir() && under.isFaceSturdy(o.level, below, Direction.UP)
                && !under.is(Blocks.ICE) && !under.is(BlockTags.LEAVES)) {
            if (TemporaryBlocks.place(o.level, top, Blocks.SNOW.defaultBlockState(), SNOW_TICKS, null)) s.placed.add(top);
        }
    }

    @Override
    public void breaks(Overload o) {
        Vec3 eye = o.eye();
        double reach = OverloadGeometry.reach(1) + 8;
        for (LivingEntity e : o.level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(reach, 24, reach),
                e -> !Overloads.spared(o, e) && e.canFreeze() && e.position().subtract(eye).horizontalDistanceSqr() <= reach * reach)) {
            if (!exposed(o.level, e) || warm(o, e)) continue;
            e.setTicksFrozen(e.getTicksRequiredToFreeze() + 100);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        }
        SetPiecePayload.send(o.level, Piece.OVERLOAD_DEEP_FREEZE, eye, eye.add(0, 1, 0), (float) reach, null);
        o.level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 3.0F, 0.4F);
        o.level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.NEUTRAL, 3.0F, 0.5F);
        CameraShakePayload.sendAround(o.level, eye, reach * 1.5, 0.5F, 16);
        if (Wildfire.griefing(o.level)) settle(o, state(o), reach, 300);
    }

    @Override
    public void end(Overload o) {
        State s = state(o);
        s.chill.clear();
        s.warm.clear();
    }
}
