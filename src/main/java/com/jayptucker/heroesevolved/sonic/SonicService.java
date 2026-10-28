package com.jayptucker.heroesevolved.sonic;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SonicService {
    public static final ResourceLocation SCREAM_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "sonic_scream");
    private record Channel(ServerPlayer player, int level, long until, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static final Map<UUID, Long> CUSHION = new HashMap<>();
    private SonicService() {}
    private static boolean canHit(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isSpectator() && !player.isAlliedTo(target)
                && (!(target instanceof ServerPlayer other) || player.getServer().isPvpAllowed() && player.canHarmPlayer(other));
    }
    public static boolean screaming(ServerPlayer player) { return CHANNELS.containsKey(player.getUUID()); }
    public static void cry(ServerPlayer player, int powerLevel, boolean sustained) {
        var level = player.serverLevel(); Vec3 origin = player.getEyePosition(), aim = player.getLookAngle().normalize();
        double range = sustained ? 40 : 24 + powerLevel * 4;
        var hit = level.clip(new ClipContext(origin, origin.add(aim.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double length = hit.getType() == HitResult.Type.BLOCK ? origin.distanceTo(hit.getLocation()) : range;
        if (hit.getType() == HitResult.Type.BLOCK) {
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
                com.jayptucker.heroesevolved.expansion.PowerReactions.resonate(level, hit.getBlockPos().offset(x, y, 0), player.getServer().overworld().getGameTime() + 160);
        }
        int affected = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                target -> canHit(player, target))) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
            double along = delta.dot(aim);
            if (along < 0 || along > length + .5 || delta.length() > range
                    || delta.normalize().dot(aim) < .90 || !player.hasLineOfSight(target)) continue;
            if (++affected > 64) break;
            target.hurt(player.damageSources().playerAttack(player), sustained ? 3.5F : 4 + powerLevel * 1.2F);
            target.setDeltaMovement(target.getDeltaMovement().add(aim.scale(sustained ? .35 : .9)).add(0, .12, 0));
            target.hurtMarked = true;
            if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        AbilityEffects.send(level, AbilityEffectPayload.Kind.SONIC_WAVE, origin, origin.add(aim.scale(length)), sustained ? .8 : 1.2);
        if (!sustained) voice(player, false);
        // Glass resonance has a small fixed work bound, rather than scanning the whole cone.
        int checks = Math.min(96, (int) Math.ceil(length * 2));
        for (int i = 1; i <= checks; i++) {
            BlockPos pos = BlockPos.containing(origin.add(aim.scale(i * .5)));
            var state = level.getBlockState(pos);
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if (path.contains("glass") && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0
                    && WorldMutationPolicy.canEdit(player, level, pos))
                level.destroyBlock(pos, true, player);
        }
    }
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
            if (target instanceof LivingEntity living && !awakening) living.hurt(player.damageSources().playerAttack(player), 3 + powerLevel);
            target.setDeltaMovement(away.scale(awakening ? 1.1 : .85).add(0, .25, 0)); target.hurtMarked = true;
            if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        AbilityEffects.send(level, AbilityEffectPayload.Kind.SONIC_BURST, player.position(), player.getEyePosition(), radius);
        voice(player, true);
    }
    public static void sense(ServerPlayer player) {
        com.jayptucker.heroesevolved.invisibility.InvisibilityService.revealAround(player.serverLevel(), player.position(), 32, 100);
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32),
                target -> target != player && target.isAlive() && !target.isSpectator() && target.distanceToSqr(player) <= 32 * 32).stream().limit(128).toList())
            PrivateGlow.glow(player, target, 80);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new AbilityEffectPayload(
                AbilityEffectPayload.Kind.SONIC_SENSE, player.position(), player.getEyePosition(), 32));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, .6F, 1.7F);
    }
    public static void vault(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement().add(player.getLookAngle().reverse().scale(1.25)).add(0, .45, 0);
        player.setDeltaMovement(velocity); player.connection.send(new ClientboundSetEntityMotionPacket(player));
        CUSHION.put(player.getUUID(), player.level().getGameTime() + 200); player.resetFallDistance();
        AbilityEffects.send(player.serverLevel(), AbilityEffectPayload.Kind.SONIC_BURST, player.position(), player.getEyePosition(), 3);
        voice(player, true);
    }
    public static boolean cushion(ServerPlayer player) {
        Long until = CUSHION.remove(player.getUUID()); return until != null && player.level().getGameTime() <= until;
    }
    public static void start(ServerPlayer player, int level) {
        CHANNELS.put(player.getUUID(), new Channel(player, level, player.getServer().overworld().getGameTime() + 100, player.level().dimension()));
        voiceState(player, true);
    }
    public static void voice(ServerPlayer player, boolean burst) {
        player.serverLevel().playSound(null, player.blockPosition(), burst
                ? com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_BURST.get()
                : com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_CRY.get(), SoundSource.PLAYERS, .85F, 1);
    }
    private static void voiceState(ServerPlayer player, boolean active) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new com.jayptucker.heroesevolved.network.SonicVoicePayload(player.getId(), player.getUUID(), active));
    }
    public static void stop(ServerPlayer player) {
        if (CHANNELS.remove(player.getUUID()) != null) {
            voiceState(player, false);
            com.jayptucker.heroesevolved.network.ArmPosePayload.clear(player);
        }
    }
    public static void forget(ServerPlayer player) { stop(player); CUSHION.remove(player.getUUID()); }
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Channel channel : java.util.List.copyOf(CHANNELS.values())) {
            if (!channel.player.isAlive() || channel.player.isRemoved() || now >= channel.until
                    || !channel.dimension.equals(channel.player.level().dimension()) || !PowerRules.canAct(channel.player)) stop(channel.player);
            else if (now % 20 == 0) voiceState(channel.player, true);
        }
        if (now % 10 == 0) for (Channel channel : java.util.List.copyOf(CHANNELS.values())) {
            if (!PlayerEnergyService.tryConsume(channel.player, 6)) { stop(channel.player); continue; }
            MasteryService.awardEnergySpent(channel.player, 6); cry(channel.player, channel.level, true);
        }
        CUSHION.values().removeIf(until -> until < now);
    }
    public static void clear() {
        for (var channel : java.util.List.copyOf(CHANNELS.values())) stop(channel.player);
        CUSHION.clear();
    }
}
