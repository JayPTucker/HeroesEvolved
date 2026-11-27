package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Absolute Zero (Cryokinesis, Winterbinder signature): Killer Frost's trick, done as cold rather than violence. The
 * caster raises both palms and, for a second and a half, draws the warmth out of everything round them: an ember leaves
 * every foe in reach and runs along a thread to the palms while frost creeps over it, fires near them go out, and the
 * foes slow to a crawl. Then the palms close: every drained foe freezes solid at once, a frost wave races out over the
 * ground, and the heat comes back to the caster as stamina. A frozen foe shatters for more when struck (Crystal Prison's
 * rule). Bosses are too big to freeze; they take the cold as damage.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class AbsoluteZero {
    /** The channel, the reach, how long the freeze lasts, and how many foes it can drain. */
    public static final int CHANNEL = 30, FREEZE = 60, MAX_FOES = 16;
    public static final double RADIUS = 12;
    /** Stamina returned for each foe drained, up to {@link #MAX_REFUND}. */
    public static final float REFUND_EACH = 4, MAX_REFUND = 24;

    private record Draw(ServerPlayer player, ServerLevel level, long releaseAt, int tier, List<LivingEntity> foes) {}
    private static final Map<UUID, Draw> DRAWS = new HashMap<>();

    private AbsoluteZero() {}

    public static boolean drawing(ServerPlayer player) { return DRAWS.containsKey(player.getUUID()); }

    public static boolean canUse(ServerPlayer player) { return !drawing(player) && PowerRules.canAct(player); }

    /** Starts the channel: every foe in reach and sight gives up an ember. */
    public static boolean activate(ServerPlayer player, int tier) {
        if (!canUse(player)) return false;
        ServerLevel level = player.serverLevel();
        List<LivingEntity> foes = foes(player);
        DRAWS.put(player.getUUID(), new Draw(player, level, level.getGameTime() + CHANNEL, Math.clamp(tier, 1, 5), foes));
        SetPiecePayload.sendSeeded(level, Piece.CRYO_ZERO, player.position(), player.position().add(0, 1, 0), (float) RADIUS, player, 0, player.getId());
        for (LivingEntity foe : foes)
            SetPiecePayload.sendSeeded(level, Piece.CRYO_EMBER, foe.position(), foe.getEyePosition(), foe.getBbHeight(), foe, 0, player.getId());
        ArmPosePayload.show(player, ArmPose.CRYO_WHITEOUT, CHANNEL + 4);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, .55F);
        level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1.4F, .5F);
        player.displayClientMessage(Component.literal(foes.isEmpty() ? "You pull at the warmth round you. There is nothing here to take."
                : "The warmth leaves everything round you...").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    static List<LivingEntity> foes(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
                        t -> com.jayptucker.heroesevolved.expansion.ExpansionSkills.hostile(player, t) && t.distanceToSqr(player) <= RADIUS * RADIUS && player.hasLineOfSight(t))
                .stream().sorted(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(player))).limit(MAX_FOES).toList();
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        if (DRAWS.isEmpty()) return;
        for (Draw draw : List.copyOf(DRAWS.values())) {
            ServerPlayer player = draw.player;
            if (player.getServer() != server) continue;
            if (!player.isAlive() || player.isRemoved() || player.serverLevel() != draw.level || !PowerRules.canAct(player)) {
                forget(player);
                continue;
            }
            long left = draw.releaseAt - draw.level.getGameTime();
            if (left > 0) {
                // The heat leaves: the drained crawl, burning stops, and fires near them die down.
                for (LivingEntity foe : draw.foes) if (foe.isAlive()) {
                    foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 3, false, false));
                    foe.clearFire();
                    if (left % 10 == 0) snuff(player, foe.blockPosition());
                }
                if (left % 6 == 0) draw.level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        .7F, .5F + (CHANNEL - left) / (float) CHANNEL);
                continue;
            }
            DRAWS.remove(player.getUUID());
            release(player, draw);
        }
    }

    /** The freeze: everyone drained freezes solid (bosses take the cold as damage) and the heat comes home as stamina. */
    static void release(ServerPlayer player, Draw draw) {
        ServerLevel level = draw.level;
        int frozen = 0;
        float damage = 4 + draw.tier;
        for (LivingEntity foe : draw.foes) {
            if (!foe.isAlive() || foe.level() != level || foe.distanceToSqr(player) > (RADIUS + 4) * (RADIUS + 4)) continue;
            CryoService.coldHit(player, foe, damage);
            if (!foe.getType().is(Tags.EntityTypes.BOSSES) && CryoService.freezeSolid(foe, FREEZE, player)) frozen++;
            SetPiecePayload.sendSeeded(level, Piece.CRYO_FX, foe.position(), foe.getEyePosition(), Math.max(1, foe.getBbWidth() * 1.6F),
                    null, 0, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.CRYO_FREEZE.ordinal() << 16 | (foe.getId() & 0xFFFF));
        }
        float refund = Math.min(MAX_REFUND, REFUND_EACH * draw.foes.size());
        if (refund > 0) PlayerEnergyService.restore(player, refund);
        CameraShakePayload.sendAround(level, player.position(), RADIUS * 1.5, .9F, 12);
        ScreenTintPayload.send(player, 0x60DDF1F8, 8, ScreenTintPayload.Style.FLASH);
        ArmPosePayload.show(player, ArmPose.CRYO_PRISON, 14);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.6F, .45F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.4F, .6F);
        int shown = frozen;
        player.displayClientMessage(Component.literal(draw.foes.isEmpty() ? "Absolute Zero." : "Absolute Zero: " + shown
                + (shown == 1 ? " foe" : " foes") + " frozen solid. +" + Math.round(refund) + " stamina.").withStyle(ChatFormatting.AQUA), true);
    }

    /** Fire near a drained foe goes out (where the caster could break it). */
    private static void snuff(ServerPlayer player, BlockPos near) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(near.offset(-2, -1, -2), near.offset(2, 2, 2))) {
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(BlockTags.FIRE)) continue;
            if (!player.mayBuild() || !level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
            BlockPos at = pos.immutable();
            if (WorldMutationPolicy.canEdit(player, level, at)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public static void forget(ServerPlayer player) {
        if (DRAWS.remove(player.getUUID()) != null) ArmPosePayload.clear(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { DRAWS.clear(); }

    /** For tests: what a release would drain now, without the wait. */
    static List<LivingEntity> preview(ServerPlayer player) { return new ArrayList<>(foes(player)); }
}
