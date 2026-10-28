package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.events.CryoEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stasis inside a Cryochamber. A sealed sleeper takes no ordinary damage, does not hunger,
 * slowly heals, and cannot attack or use powers. Sneaking steps out, unless the bunker's
 * revival protocol still holds the pod shut. A supercharged pod heals faster and purges effects.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class CryoChamberService {
    private record Occupant(ServerPlayer player, ResourceKey<Level> dimension, BlockPos base, long lockedUntil) {}
    private static final Map<UUID, Occupant> OCCUPANTS = new HashMap<>();
    /** A Cryokinetic's charge lasts five minutes. */
    private static final int CHARGE_TICKS = 20 * 60 * 5;
    private static final Map<String, Long> CHARGES = new HashMap<>();

    private CryoChamberService() {}

    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }

    public static boolean isInside(ServerPlayer player) { return OCCUPANTS.containsKey(player.getUUID()); }

    private static boolean occupied(ServerLevel level, BlockPos base) {
        return OCCUPANTS.values().stream().anyMatch(o -> o.base.equals(base) && o.dimension.equals(level.dimension()));
    }

    /** A right-click on either half of the pod at {@code base}. */
    public static boolean use(ServerPlayer player, BlockPos base) {
        ServerLevel level = player.serverLevel();
        var state = level.getBlockState(base);
        if (!(state.getBlock() instanceof CryoChamberBlock)) return false;
        if (player.isShiftKeyDown() && CryoEvents.hasAwakenedCryokinesis(player)) return charge(player, base);
        Occupant mine = OCCUPANTS.get(player.getUUID());
        if (mine != null && mine.base.equals(base)) return exit(player);
        if (occupied(level, base)) {
            player.displayClientMessage(Component.translatable("cryochamber.heroesevolved.occupied"), true);
            return false;
        }
        if (!state.getValue(CryoChamberBlock.OPEN)) {
            CryoChamberBlock.setOpen(level, base, true);
            hiss(level, base, true);
            return true;
        }
        if (player.distanceToSqr(CryoChamberBlock.standing(base)) > 9) return false;
        enter(player, base, 0);
        player.setRespawnPosition(level.dimension(), base, CryoChamberBlock.yaw(state.getValue(CryoChamberBlock.FACING)), false, false);
        player.displayClientMessage(Component.translatable("cryochamber.heroesevolved.entered").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** Seals {@code player} inside; {@code lockTicks} keeps the door shut even if they sneak. */
    public static void enter(ServerPlayer player, BlockPos base, int lockTicks) {
        ServerLevel level = player.serverLevel();
        var facing = level.getBlockState(base).getValue(CryoChamberBlock.FACING);
        Vec3 stand = CryoChamberBlock.standing(base);
        player.stopRiding();
        player.teleportTo(level, stand.x, stand.y, stand.z, CryoChamberBlock.yaw(facing), 0);
        player.setDeltaMovement(Vec3.ZERO);
        OCCUPANTS.put(player.getUUID(), new Occupant(player, level.dimension(), base.immutable(), now(level.getServer()) + lockTicks));
        CryoChamberBlock.setOpen(level, base, false);
        hiss(level, base, false);
    }

    public static boolean exit(ServerPlayer player) {
        Occupant occupant = OCCUPANTS.get(player.getUUID());
        if (occupant == null) return false;
        if (now(player.getServer()) < occupant.lockedUntil) {
            player.displayClientMessage(Component.translatable("cryochamber.heroesevolved.locked"), true);
            return false;
        }
        release(occupant, true);
        return true;
    }

    /** Opens the pod and lets the sleeper out; {@code step} nudges them clear of the glass. */
    public static void release(ServerPlayer player) {
        Occupant occupant = OCCUPANTS.get(player.getUUID());
        if (occupant != null) release(occupant, false);
    }

    private static void release(Occupant occupant, boolean step) {
        OCCUPANTS.remove(occupant.player.getUUID());
        ServerLevel level = occupant.player.getServer().getLevel(occupant.dimension);
        if (level == null) return;
        CryoChamberBlock.setOpen(level, occupant.base, true);
        hiss(level, occupant.base, true);
        var state = level.getBlockState(occupant.base);
        if (step && state.getBlock() instanceof CryoChamberBlock && occupant.player.level() == level) {
            Vec3 out = CryoChamberBlock.standing(occupant.base).add(Vec3.atLowerCornerOf(state.getValue(CryoChamberBlock.FACING).getNormal()).scale(0.9));
            occupant.player.teleportTo(out.x, out.y, out.z);
        }
    }

    private static boolean charge(ServerPlayer player, BlockPos base) {
        ServerLevel level = player.serverLevel();
        CHARGES.put(key(level, base), now(level.getServer()) + CHARGE_TICKS);
        CryoChamberBlock.setCharged(level, base, true);
        level.sendParticles(ParticleTypes.SNOWFLAKE, base.getX() + .5, base.getY() + 1, base.getZ() + .5, 40, .4, .8, .4, .02);
        level.playSound(null, base, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.BLOCKS, 1, 0.7F);
        player.displayClientMessage(Component.translatable("cryochamber.heroesevolved.charged").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private static String key(ServerLevel level, BlockPos base) { return level.dimension().location() + "@" + base.asLong(); }

    private static void hiss(Level level, BlockPos base, boolean opening) {
        level.playSound(null, base, opening ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.6F);
        level.playSound(null, base, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, opening ? 1.4F : 1.8F);
        if (level instanceof ServerLevel server)
            server.sendParticles(ParticleTypes.CLOUD, base.getX() + .5, base.getY() + 1, base.getZ() + .5, opening ? 18 : 6, .3, .6, .3, .02);
    }

    public static void tick(MinecraftServer server) {
        long now = now(server);
        for (Occupant occupant : List.copyOf(OCCUPANTS.values())) {
            ServerPlayer player = occupant.player;
            ServerLevel level = server.getLevel(occupant.dimension);
            boolean intact = level != null && level.getBlockState(occupant.base).getBlock() instanceof CryoChamberBlock;
            if (!intact || !player.isAlive() || player.isRemoved() || player.level() != level
                    || player.position().distanceToSqr(CryoChamberBlock.standing(occupant.base)) > 1) {
                OCCUPANTS.remove(player.getUUID());
                if (intact) CryoChamberBlock.setOpen(level, occupant.base, true);
                continue;
            }
            if (player.isShiftKeyDown() && now >= occupant.lockedUntil) { release(occupant, true); continue; }
            player.getFoodData().setExhaustion(0);
            boolean charged = CHARGES.getOrDefault(key(level, occupant.base), 0L) > now;
            if (now % (charged ? 10 : 40) == 0 && player.getHealth() < player.getMaxHealth()) player.heal(1);
            if (charged && now % 20 == 0) {
                for (MobEffectInstance effect : List.copyOf(player.getActiveEffects()))
                    if (!effect.getEffect().value().isBeneficial()) player.removeEffect(effect.getEffect());
                player.clearFire();
            }
            if (now % 40 == 0) level.sendParticles(charged ? ParticleTypes.SNOWFLAKE : ParticleTypes.WHITE_ASH,
                    player.getX(), player.getY() + 1, player.getZ(), 4, .2, .5, .2, 0);
        }
        if (now % 100 == 0) CHARGES.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) return false;
            String[] parts = entry.getKey().split("@");
            ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(parts[0])));
            if (level != null) {
                BlockPos base = BlockPos.of(Long.parseLong(parts[1]));
                if (level.getBlockState(base).getBlock() instanceof CryoChamberBlock) CryoChamberBlock.setCharged(level, base, false);
            }
            return true;
        });
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInside(player)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInside(player)) event.setCanceled(true);
    }

    @SubscribeEvent public static void useItem(PlayerInteractEvent.RightClickItem event) {
        // The Formula is the one thing a sleeper may use: a sealed pod improves its odds.
        if (event.getEntity() instanceof ServerPlayer player && isInside(player)
                && !event.getItemStack().is(com.jayptucker.heroesevolved.registry.ModItems.FORMULA_VIAL.get())) event.setCanceled(true);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) release(player);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { OCCUPANTS.clear(); CHARGES.clear(); }
}
