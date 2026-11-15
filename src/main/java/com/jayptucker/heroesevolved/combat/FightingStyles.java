package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Fighting with a power instead of a fist. A power with a style answers an empty-handed left click with the next
 * strike of its combo and a double-tapped direction with a dodge; {@link StyleMotion} moves the body and keeps the
 * evasion window, and the first blow a dodge evades is the style's counter.
 *
 * <p>Pyrokinesis's firebending set the pattern (see {@code docs/pyrokinesis.md}); Electrokinesis and Hydrokinesis
 * follow it. To give another power a style, implement {@link Style} and add it to {@link #STYLES}: the input, the
 * payload, the client settings, evasion and fall cushions come for free.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class FightingStyles {
    public enum Dodge { LEFT, RIGHT, BACK }

    /** One power's way of fighting. */
    public interface Style {
        /** Does this player fight this way (their own, awakened power)? */
        boolean owns(ServerPlayer player);
        /** Throws the next strike: 1 thrown, 0 too soon, -1 nothing could be thrown at all (the click punches). */
        int strike(ServerPlayer player);
        boolean dodge(ServerPlayer player, Dodge dodge);
        /** The first blow a dodge evaded, and where it came from. */
        void counter(ServerPlayer player, DamageSource source);
        default void tick(MinecraftServer server) {}
        void forget(ServerPlayer player);
        void clear();
    }

    private static final List<Style> STYLES = List.of(
            com.jayptucker.heroesevolved.pyro.PyroCombat.STYLE,
            com.jayptucker.heroesevolved.electro.ElectroCombat.STYLE,
            com.jayptucker.heroesevolved.hydro.HydroCombat.STYLE);

    private FightingStyles() {}

    public static Style of(ServerPlayer player) {
        for (Style style : STYLES) if (style.owns(player)) return style;
        return null;
    }

    /**
     * An empty-handed click. If the power can't strike at all (no stamina, the power switched off), the click is still
     * a plain punch at whatever the crosshair was on, so a tired or dampened fighter isn't left swinging at air.
     */
    public static void strikeInput(ServerPlayer player, int targetId) {
        if (!player.getMainHandItem().isEmpty()) return;
        Style style = of(player);
        if (style != null && style.strike(player) >= 0) return;
        Entity target = targetId < 0 ? null : player.level().getEntity(targetId);
        if (target != null && target != player && target.isAlive() && target.isAttackable()
                && player.canInteractWithEntity(target, 1)) {
            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    public static boolean dodge(ServerPlayer player, Dodge dodge) {
        Style style = of(player);
        return style != null && style.dodge(player, dodge);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        StyleMotion.tick(event.getServer());
        for (Style style : STYLES) style.tick(event.getServer());
    }

    /** A blow that lands in a dodge's first ticks misses, and the first one is the style's counter. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        StyleMotion.Evasion evasion = StyleMotion.evade(victim, event.getSource());
        if (evasion == StyleMotion.Evasion.NONE) return;
        event.setCanceled(true);
        Style style = of(victim);
        if (evasion == StyleMotion.Evasion.COUNTER && style != null) style.counter(victim, event.getSource());
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && StyleMotion.cushioned(player)) event.setDamageMultiplier(0);
    }

    public static void forget(ServerPlayer player) {
        StyleMotion.forget(player);
        for (Style style : STYLES) style.forget(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        StyleMotion.clear();
        for (Style style : STYLES) style.clear();
    }
}
