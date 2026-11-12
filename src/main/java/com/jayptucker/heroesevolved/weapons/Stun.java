package com.jayptucker.heroesevolved.weapons;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A taser's hold on a body: for a couple of seconds the target barely moves, can't jump, and can't land a blow
 * (a creature also loses its path and its target). A weak Electrokinesis hit; an Electrokinetic shrugs off half of it.
 * Sparks crawl over whoever is held.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Stun {
    public static final int MOB_TICKS = 50, PLAYER_TICKS = 30;
    private static final ResourceLocation NO_JUMP = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "taser_no_jump");
    private static final Map<UUID, Held> HELD = new HashMap<>();

    private record Held(LivingEntity target, long until) {}

    private Stun() {}

    /** Locks {@code target} up. Returns the ticks it holds (0 for a target it can't hold). */
    public static int apply(LivingEntity target, @Nullable LivingEntity source) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive()) return 0;
        int ticks = target instanceof ServerPlayer ? PLAYER_TICKS : MOB_TICKS;
        if (target instanceof ServerPlayer player && electric(player)) ticks /= 2;
        long until = level.getGameTime() + ticks;
        Held old = HELD.get(target.getUUID());
        HELD.put(target.getUUID(), new Held(target, old == null ? until : Math.max(old.until, until)));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 4, false, false, true), source);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false, true), source);
        AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null && !jump.hasModifier(NO_JUMP))
            jump.addTransientModifier(new AttributeModifier(NO_JUMP, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.HOSTILE, 0.8F, 2.0F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.HOSTILE, 0.9F, 1.4F);
        if (target instanceof ServerPlayer player) {
            ScreenTintPayload.send(player, 0x5584D5FF, ticks, ScreenTintPayload.Style.EDGE);
            player.displayClientMessage(Component.literal("Tased! Every muscle locks.").withStyle(ChatFormatting.YELLOW), true);
        }
        return ticks;
    }

    /** True while a taser holds {@code target}. */
    public static boolean held(LivingEntity target) {
        Held held = HELD.get(target.getUUID());
        return held != null && held.target == target && target.level().getGameTime() < held.until;
    }

    private static boolean electric(ServerPlayer player) {
        return !EclipseService.arePowersSuppressed(player) && PlayerAbilityService.getData(player)
                .ability(ModAbilities.ELECTROKINESIS_ID).filter(progress -> progress.isUnlocked()).isPresent();
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        if (HELD.isEmpty()) return;
        for (Iterator<Held> it = HELD.values().iterator(); it.hasNext(); ) {
            Held held = it.next();
            LivingEntity target = held.target;
            if (target.getServer() != server) continue;
            if (!target.isAlive() || target.isRemoved() || target.level().getGameTime() >= held.until) {
                release(target);
                it.remove();
                continue;
            }
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
            }
            if (target.tickCount % 4 == 0 && target.level() instanceof ServerLevel level)
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ(),
                        4, target.getBbWidth() * 0.4, target.getBbHeight() * 0.35, target.getBbWidth() * 0.4, 0.15);
        }
    }

    private static void release(LivingEntity target) {
        AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(NO_JUMP);
    }

    /** A held body can't land a blow. */
    @SubscribeEvent public static void blow(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && event.getSource().getDirectEntity() == attacker && held(attacker))
            event.setCanceled(true);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { clear(); }

    public static void clear() {
        for (Held held : HELD.values()) release(held.target);
        HELD.clear();
    }
}
