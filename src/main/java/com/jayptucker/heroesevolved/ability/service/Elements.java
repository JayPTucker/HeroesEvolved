package com.jayptucker.heroesevolved.ability.service;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.pyro.PyroGeometry;
import com.jayptucker.heroesevolved.pyro.PyroService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The elements' own limits. Water boils away in the Nether's heat, so Hydrokinesis does nothing there; rain puts fire
 * out, so Pyrokinesis does nothing under it (a roof keeps you dry). Every press, strike, dodge and finisher of a
 * snuffed power fizzles instead, held moves end, and the stored Heat or water goes up in steam.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Elements {
    /** How often a press may fizzle again; mashing a key doesn't stack the puffs. */
    static final int FIZZLE_GAP = 6;
    /** A snuffed bender gives off a wisp of steam this often, so the reason is visible while they stand there. */
    static final int WISP_EVERY = 40;

    private static final Map<UUID, Long> LAST_FIZZLE = new HashMap<>();
    private static final Set<UUID> SNUFFED = new HashSet<>();

    private Elements() {}

    /** The Nether (any ultra-warm dimension) boils water off before it can be gathered. */
    public static boolean dried(Entity entity) { return entity.level().dimensionType().ultraWarm(); }

    /** Rain falling on this body, at the feet or the head, as vanilla's own "in rain" test asks. */
    public static boolean doused(Entity entity) {
        Level level = entity.level();
        return level.isRainingAt(entity.blockPosition())
                || level.isRainingAt(BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ()));
    }

    /** Rain falling on this point (a phoenix, a falling sun). */
    public static boolean raining(Level level, Vec3 at) { return level.isRainingAt(BlockPos.containing(at)); }

    /** The power this player is working: their own awakened one, or the one an empath is channeling. */
    public static ResourceLocation power(ServerPlayer player) {
        var own = PlayerAbilityService.getData(player).assignedPower();
        if (own.isEmpty() || !own.get().getValue().isUnlocked()) return null;
        if (MimicryService.isMimic(player)) return MimicryService.memory(player).current();
        return own.get().getKey();
    }

    /** Is {@code power} put out where this player stands? */
    public static boolean snuffed(ServerPlayer player, ResourceLocation power) {
        if (ModAbilities.HYDROKINESIS_ID.equals(power)) return dried(player);
        if (ModAbilities.PYROKINESIS_ID.equals(power)) return doused(player);
        return false;
    }

    /** Is the power this player is working put out where they stand? */
    public static boolean snuffed(ServerPlayer player) { return snuffed(player, power(player)); }

    /**
     * A press of a snuffed power: it fizzles and says why. True when the press was refused. Call this before any
     * cost or cooldown, so a fizzle is free.
     */
    public static boolean refuse(ServerPlayer player, ResourceLocation power) {
        if (!snuffed(player, power)) return false;
        fizzle(player, power, false);
        PowerFeedback.explain(player, reason(power));
        return true;
    }

    public static Component reason(ResourceLocation power) {
        return Component.translatable(ModAbilities.HYDROKINESIS_ID.equals(power)
                ? "power.heroesevolved.fizzle.hydro" : "power.heroesevolved.fizzle.pyro");
    }

    /**
     * The fizzle: a puff from both palms (smoke and steam for fire put out, a hiss of steam for water boiled off) and an
     * extinguishing hiss. {@code big} is the moment the element goes out, and ignores the throttle.
     */
    public static void fizzle(ServerPlayer player, ResourceLocation power, boolean big) {
        long now = player.serverLevel().getGameTime();
        if (!big && now - LAST_FIZZLE.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < FIZZLE_GAP) return;
        LAST_FIZZLE.put(player.getUUID(), now);
        ServerLevel level = player.serverLevel();
        boolean water = ModAbilities.HYDROKINESIS_ID.equals(power);
        int puff = big ? 10 : 5;
        for (boolean main : new boolean[] {true, false}) {
            Vec3 hand = PyroGeometry.hand(player, 1, main);
            if (water) {
                level.sendParticles(ParticleTypes.CLOUD, hand.x, hand.y, hand.z, puff, .08, .06, .08, .03);
                level.sendParticles(ParticleTypes.FALLING_WATER, hand.x, hand.y, hand.z, puff / 2, .1, .05, .1, 0);
            } else {
                level.sendParticles(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, puff + 2, .06, .05, .06, .02);
                level.sendParticles(ParticleTypes.WHITE_SMOKE, hand.x, hand.y, hand.z, puff / 2, .05, .05, .05, .02);
            }
        }
        if (big) {
            level.sendParticles(water ? ParticleTypes.CLOUD : ParticleTypes.LARGE_SMOKE, player.getX(),
                    player.getY() + player.getBbHeight() * .55, player.getZ(), 14, .3, .45, .3, .02);
        }
        level.playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, big ? .7F : .45F, water ? .9F + level.random.nextFloat() * .2F : 1.5F + level.random.nextFloat() * .3F);
    }

    /** A thin wisp off the shoulders while the bender stands where their element can't live. */
    private static void wisp(ServerPlayer player, ResourceLocation power) {
        boolean water = ModAbilities.HYDROKINESIS_ID.equals(power);
        player.serverLevel().sendParticles(water ? ParticleTypes.CLOUD : ParticleTypes.SMOKE, player.getX(),
                player.getY() + player.getBbHeight() * .8, player.getZ(), 4, .22, .12, .22, .01);
    }

    /**
     * Once a check for one player: going out (Heat and water lost, a big fizzle and one line), and the wisps while it
     * stays out. Tests call it directly for players outside the player list.
     */
    public static void check(ServerPlayer player) {
        ResourceLocation power = power(player);
        boolean out = snuffed(player, power);
        UUID id = player.getUUID();
        if (!out) { SNUFFED.remove(id); return; }
        boolean lost = false;
        if (ModAbilities.PYROKINESIS_ID.equals(power) && PyroService.heat(player) > 0) {
            PyroService.quench(player);
            lost = true;
        }
        if (ModAbilities.HYDROKINESIS_ID.equals(power) && HydroService.water(player) > 0) {
            HydroService.fill(player, -HydroService.capacity(player));
            lost = true;
        }
        if (SNUFFED.add(id) || lost) {
            fizzle(player, power, true);
            player.displayClientMessage(reason(power).copy().withStyle(ChatFormatting.GRAY), true);
        } else if (player.serverLevel().getGameTime() % WISP_EVERY < 5) {
            wisp(player, power);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 5 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (player.isAlive() && !player.isSpectator()) check(player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_FIZZLE.remove(event.getEntity().getUUID());
        SNUFFED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) { LAST_FIZZLE.clear(); SNUFFED.clear(); }
}
