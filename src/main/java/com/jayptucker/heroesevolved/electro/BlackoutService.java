package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.jayptucker.heroesevolved.electro.ElectroGeometry.chest;
import static com.jayptucker.heroesevolved.electro.ElectroGeometry.show;

/**
 * Blackout (Electrokinesis, Groundkeeper signature). Elle drinks the grid.
 *
 * <p>For one second every powered thing around her (Forge Energy machines, Create machinery, redstone
 * blocks, lit lamps, live wire) throws an arc into her chest while she lifts her arms. Then the pulse:
 * a twenty-block electromagnetic field that lasts twelve seconds. Redstone dies, machines are drained and
 * stop, Create's shafts seize, enemies are shocked and plunged into darkness, and the energy she pulled out
 * becomes Charge. Drink a big enough grid and she comes out of it Overcharged.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class BlackoutService {
    public static final int RADIUS = 20, FIELD_TICKS = 240, WIND_UP = 20, ENEMY_RADIUS = 12;
    private static final int MAX_SOURCES = 24;

    private record Siphon(ServerPlayer player, ServerLevel level, long releaseAt, int powerLevel, List<BlockPos> sources) {}
    private static final Map<UUID, Siphon> SIPHONS = new HashMap<>();

    private BlackoutService() {}

    public static boolean winding(ServerPlayer player) { return SIPHONS.containsKey(player.getUUID()); }

    public static boolean activate(ServerPlayer player, int powerLevel) {
        if (!ElectroService.canStartCast(player)) return false;
        ServerLevel level = player.serverLevel();
        List<BlockPos> sources = sources(level, player.blockPosition());
        SIPHONS.put(player.getUUID(), new Siphon(player, level, level.getGameTime() + WIND_UP, powerLevel, sources));
        ArmPosePayload.show(player, ArmPose.ELECTRO_COIL, WIND_UP + 2);
        show(level, Piece.ELECTRO_COIL, player.position(), player.position(), .2, player);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.literal(sources.isEmpty() ? "You pull at the dark. There is nothing here to drink."
                : "You pull the current out of everything around you...").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** Powered things worth an arc: machines first, then live redstone. Bounded, nearest first. */
    static List<BlockPos> sources(ServerLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        int r = 12;
        for (BlockPos pos : BlockPos.withinManhattan(center, r, r / 2, r)) {
            if (found.size() >= MAX_SOURCES) break;
            if (!level.hasChunkAt(pos) || pos.distSqr(center) > r * r) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            boolean live = state.is(Blocks.REDSTONE_BLOCK)
                    || state.getBlock() instanceof RedstoneLampBlock && state.getValue(RedstoneLampBlock.LIT)
                    || state.is(Blocks.REDSTONE_WIRE) && state.getValue(BlockStateProperties.POWER) > 7
                    || state.hasBlockEntity() && GridDisruption.isMachine(level, pos);
            if (live) found.add(pos.immutable());
        }
        return found;
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        if (SIPHONS.isEmpty()) return;
        for (Siphon siphon : List.copyOf(SIPHONS.values())) {
            ServerPlayer player = siphon.player;
            if (player.getServer() != server) continue;
            if (!player.isAlive() || player.isRemoved() || player.serverLevel() != siphon.level || !PowerRules.canAct(player)) {
                forget(player);
                continue;
            }
            long now = siphon.level.getGameTime(), left = siphon.releaseAt - now;
            Vec3 chest = chest(player.position());
            if (left > 0) {
                // The arcs come faster as the pull builds, a few more each pair of ticks.
                if (left % 4 == 0) {
                    int age = (int) (WIND_UP - left);
                    int arcs = Math.min(siphon.sources.size(), 2 + age / 5);
                    for (int i = 0; i < arcs; i++) {
                        BlockPos from = siphon.sources.get((age + i) % siphon.sources.size());
                        show(siphon.level, Piece.ELECTRO_SIPHON, Vec3.atCenterOf(from).add(0, .52, 0), chest,
                                .25 + age / (double) WIND_UP, player, i % 2);
                    }
                    show(siphon.level, Piece.ELECTRO_COIL, player.position(), player.position(), .25 + age / (double) WIND_UP, player);
                }
                if (left % 5 == 0) siphon.level.playSound(null, player.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS,
                        0.5F, 0.6F + (WIND_UP - left) / (float) WIND_UP);
                continue;
            }
            SIPHONS.remove(player.getUUID());
            release(player, siphon.powerLevel);
        }
    }

    /** The pulse itself. Public for tests. */
    public static GridDisruption.Pulse release(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 chest = chest(player.position());
        GridDisruption.Pulse pulse = GridDisruption.emp(player, level, player.blockPosition(), RADIUS, FIELD_TICKS);
        for (BlockPos machine : pulse.shown())
            show(level, Piece.ELECTRO_SIPHON, Vec3.atCenterOf(machine).add(0, .52, 0), chest, 1, player);

        int struck = 0;
        float damage = 5.0F + powerLevel;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ENEMY_RADIUS),
                target -> ElectroService.hostile(player, target) && target.distanceToSqr(player) <= ENEMY_RADIUS * ENEMY_RADIUS
                        && player.hasLineOfSight(target))) {
            if (struck++ >= 24) break;
            com.jayptucker.heroesevolved.expansion.PowerReactions.electric(player, target);
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            if (target instanceof Mob mob) mob.getNavigation().stop();
            show(level, Piece.ELECTRO_ARC, chest, target.getBoundingBox().getCenter(), 1.2, null);
        }

        // Charge: a base surge, then whatever the grid gave her.
        int gained = Math.min(ElectroService.MAX_CHARGE, 20 + pulse.charge() + 3 * Math.min(10, pulse.machines()));
        ElectroService.addCharge(player, gained);

        show(level, Piece.ELECTRO_BLACKOUT, player.position(), chest, RADIUS, null);
        CameraShakePayload.sendAround(level, chest, RADIUS * 1.5, 1.6F, 14);
        // The lights go out: everyone caught in it sees the edges of the world close in.
        ScreenTintPayload.sendAround(level, chest, RADIUS, 0xC0000008, 50, ScreenTintPayload.Style.VIGNETTE);
        ScreenTintPayload.send(player, 0x70BFE6FF, 6, ScreenTintPayload.Style.FLASH);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.4F, 0.5F);
        ArmPosePayload.show(player, ArmPose.ELECTRO_RELEASE, 14);

        List<String> parts = new ArrayList<>();
        if (pulse.machines() > 0) parts.add(pulse.machines() + (pulse.machines() == 1 ? " machine" : " machines"));
        if (pulse.items() > 0) parts.add(pulse.items() + (pulse.items() == 1 ? " charged enemy" : " charged enemies"));
        player.displayClientMessage(Component.literal(parts.isEmpty() ? "Blackout. +" + gained + " Charge."
                : "Blackout: " + String.join(", ", parts) + " go dark. +" + gained + " Charge.").withStyle(ChatFormatting.AQUA), true);
        return pulse;
    }

    public static void forget(ServerPlayer player) {
        if (SIPHONS.remove(player.getUUID()) != null) ArmPosePayload.clear(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { SIPHONS.clear(); }
}
