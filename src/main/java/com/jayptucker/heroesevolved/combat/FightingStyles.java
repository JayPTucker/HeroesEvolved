package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import net.minecraft.resources.ResourceLocation;
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
        /** The power this style belongs to (an empath channeling it borrows the style). */
        ResourceLocation power();
        /** Does this player fight this way with their own, awakened power? Use {@link #fights} to include borrowers. */
        default boolean owns(ServerPlayer player) { return hasPower(player, power()); }
        /** Throws the next strike: 1 thrown, 0 too soon, -1 nothing could be thrown at all (the click punches). */
        int strike(ServerPlayer player);
        boolean dodge(ServerPlayer player, Dodge dodge);
        /** The first blow a dodge evaded, and where it came from. */
        void counter(ServerPlayer player, DamageSource source);
        /** A blow taken while {@link StyleMotion#brace bracing}: what is left of it. */
        default float braced(ServerPlayer player, DamageSource source, float amount) { return amount; }
        default void tick(MinecraftServer server) {}
        void forget(ServerPlayer player);
        void clear();
    }

    private static final List<Style> STYLES = List.of(
            com.jayptucker.heroesevolved.pyro.PyroCombat.STYLE,
            com.jayptucker.heroesevolved.electro.ElectroCombat.STYLE,
            com.jayptucker.heroesevolved.hydro.HydroCombat.STYLE,
            com.jayptucker.heroesevolved.speed.SpeedStyle.STYLE,
            com.jayptucker.heroesevolved.quake.QuakeStyle.STYLE,
            com.jayptucker.heroesevolved.cryo.CryoStyle.STYLE,
            com.jayptucker.heroesevolved.phasing.DensityStyle.STYLE,
            com.jayptucker.heroesevolved.regeneration.RegenStyle.STYLE,
            com.jayptucker.heroesevolved.invisibility.ForceStyle.STYLE,
            com.jayptucker.heroesevolved.telekinesis.KineticStyle.STYLE,
            com.jayptucker.heroesevolved.vibe.VibeStyle.STYLE);

    private FightingStyles() {}

    /** An awakened power of the player's own, not switched off by the Eclipse. */
    public static boolean hasPower(ServerPlayer player, ResourceLocation power) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey().equals(power) && entry.getValue().isUnlocked())
                .isPresent() && !EclipseService.arePowersSuppressed(player);
    }

    /** The power an empath is channeling right now, if they may use it. */
    private static ResourceLocation borrowed(ServerPlayer player) {
        if (!MimicryService.isMimic(player) || !PowerRules.canAct(player)) return null;
        return MimicryService.memory(player).current();
    }

    /**
     * The style this player fights with: their own power's, or, for an empath, the style of the power they are
     * channeling (an Echo of firebending fights like a firebender).
     */
    public static Style of(ServerPlayer player) {
        for (Style style : STYLES) if (style.owns(player)) return style;
        ResourceLocation channel = borrowed(player);
        if (channel != null) for (Style style : STYLES) if (style.power().equals(channel)) return style;
        return null;
    }

    /** Does this player fight with {@code style} (their own power, or borrowed)? Each style's own checks use this. */
    public static boolean fights(ServerPlayer player, Style style) { return of(player) == style; }

    /**
     * An empty-handed click. If the power can't strike at all (no stamina, the power switched off), the click is still
     * a plain punch at whatever the crosshair was on, so a tired or dampened fighter isn't left swinging at air.
     */
    public static void strikeInput(ServerPlayer player, int targetId) {
        if (!player.getMainHandItem().isEmpty()) return;
        Style style = of(player);
        int thrown = style == null ? -1 : style.strike(player);
        if (thrown == 1) com.jayptucker.heroesevolved.sounds.PowerSounds.strike(player, style.power());
        if (thrown >= 0) return;
        Entity target = targetId < 0 ? null : player.level().getEntity(targetId);
        if (target != null && target != player && target.isAlive() && target.isAttackable()
                && player.canInteractWithEntity(target, 1)) {
            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    public static boolean dodge(ServerPlayer player, Dodge dodge) {
        Style style = of(player);
        if (style == null || !style.dodge(player, dodge)) return false;
        com.jayptucker.heroesevolved.sounds.PowerSounds.dodge(player, style.power());
        return true;
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
        if (StyleMotion.bracing(victim) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Style style = of(victim);
            if (style != null) event.setAmount(style.braced(victim, event.getSource(), event.getAmount()));
            return;
        }
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
