package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.ArrayList;
import java.util.List;

/** Suppresses actual redstone output temporarily, without replacing blocks or losing block entities. */
public final class RadiationEmp {
    public static final int RADIUS = 16, DURATION = 200;
    private record Field(ServerLevel level, BlockPos center, long until) {}
    private static final List<Field> FIELDS = new ArrayList<>();
    private RadiationEmp() {}
    public static boolean jammed(ServerLevel level, BlockPos pos) {
        for (Field field : FIELDS)
            if (field.level == level && level.getGameTime() < field.until
                    && pos.distSqr(field.center) <= RADIUS * RADIUS) return true;
        return false;
    }
    public static void pulse(ServerPlayer player) {
        Field field = new Field(player.serverLevel(), player.blockPosition(), player.serverLevel().getGameTime() + DURATION);
        if (FIELDS.size() >= 32) { Field old = FIELDS.removeFirst(); refresh(old); }
        FIELDS.add(field);
        refresh(field);
        AbilityEffects.at(player, Kind.NUCLEAR_EMP, RADIUS);
    }
    private static void refresh(Field field) {
        for (BlockPos pos : BlockPos.betweenClosed(field.center.offset(-RADIUS, -RADIUS, -RADIUS),
                field.center.offset(RADIUS, RADIUS, RADIUS))) {
            if (pos.distSqr(field.center) > RADIUS * RADIUS || !field.level.hasChunkAt(pos)) continue;
            var state = field.level.getBlockState(pos);
            if (state.isSignalSource() || state.hasProperty(BlockStateProperties.POWERED) || state.is(Blocks.REDSTONE_WIRE))
                field.level.updateNeighborsAt(pos.immutable(), state.getBlock());
        }
    }
    public static void tick(MinecraftServer server) {
        for (Field field : List.copyOf(FIELDS)) {
            if (field.level.getServer() == server && field.level.getGameTime() >= field.until) {
                FIELDS.remove(field);
                refresh(field);
            }
        }
    }
    public static void clear() {
        List<Field> old = List.copyOf(FIELDS);
        FIELDS.clear(); old.forEach(RadiationEmp::refresh);
    }
}
