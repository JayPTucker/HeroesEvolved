package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftTraits.Trait;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;
import java.util.Set;

/**
 * Puts a form's traits into play on the server: the attribute changes (gentle falls, swimming, breath), flight, the
 * senses carried as effects, and the weaknesses ticked once a second (sunlight, water, drying out). The client handles
 * what only it can (climbing walls, the glow of scent and vibration) in {@code client/MorphSenses}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ShapeshiftTraitService {
    private static final ResourceLocation GRAVITY = id("shapeshift_gravity"), FALL = id("shapeshift_fall"),
            SWIM = id("shapeshift_swim"), BREATH = id("shapeshift_breath");
    /** Effects a form keeps topped up; long enough to never flicker, short enough to clear soon after. */
    private static final int SENSE_TICKS = 300;
    /** Ticks of air a drying creature loses on land, net of the player's own recovery. */
    private static final int DRY_RATE = 9;

    private ShapeshiftTraitService() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    /** Applies a form's lasting changes as it is taken. */
    static void begin(ServerPlayer player, Set<Trait> traits) {
        if (traits.contains(Trait.SLOW_FALL)) {
            set(player, Attributes.GRAVITY, GRAVITY, -.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            set(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        if (traits.contains(Trait.SWIM)) set(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM, 1, AttributeModifier.Operation.ADD_VALUE);
        if (traits.contains(Trait.DEEP_BREATH)) set(player, Attributes.OXYGEN_BONUS, BREATH, 4, AttributeModifier.Operation.ADD_VALUE);
        if (traits.contains(Trait.FLIGHT) || traits.contains(Trait.FLUTTER)) {
            var abilities = player.getAbilities();
            abilities.mayfly = true;
            abilities.setFlyingSpeed(traits.contains(Trait.FLIGHT) ? .05F : .022F);
            player.onUpdateAbilities();
        }
        senses(player, traits);
    }

    /** Takes a form's changes away again as it is left. */
    static void end(ServerPlayer player, Set<Trait> traits) {
        for (var attribute : List.of(Attributes.GRAVITY, Attributes.FALL_DAMAGE_MULTIPLIER, Attributes.WATER_MOVEMENT_EFFICIENCY, Attributes.OXYGEN_BONUS)) {
            var instance = player.getAttribute(attribute);
            if (instance != null) for (ResourceLocation id : List.of(GRAVITY, FALL, SWIM, BREATH)) instance.removeModifier(id);
        }
        if (traits.contains(Trait.FLIGHT) || traits.contains(Trait.FLUTTER)) {
            var abilities = player.getAbilities();
            abilities.setFlyingSpeed(.05F);
            if (!player.isCreative() && !player.isSpectator()) { abilities.mayfly = false; abilities.flying = false; }
            player.onUpdateAbilities();
            // Whoever was flying lands gently, not from a height.
            player.fallDistance = 0;
        }
        if (traits.contains(Trait.NIGHT_EYES)) clear(player, MobEffects.NIGHT_VISION);
        if (traits.contains(Trait.BLIND)) clear(player, MobEffects.DARKNESS);
        if (traits.contains(Trait.DRIES_OUT)) player.setAirSupply(Math.max(player.getAirSupply(), 0));
    }

    /** Once a tick while a form is worn. */
    static void tick(ServerPlayer player, Set<Trait> traits, long now) {
        if (traits.isEmpty()) return;
        boolean wet = player.isInWaterRainOrBubble(), under = player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        if (traits.contains(Trait.GILLS) && under) player.setAirSupply(player.getMaxAirSupply());
        if (traits.contains(Trait.DRIES_OUT) && !player.isInWaterOrBubble()) {
            player.setAirSupply(player.getAirSupply() - DRY_RATE);
            if (player.getAirSupply() <= -20) {
                player.setAirSupply(0);
                player.hurt(player.damageSources().dryOut(), 2);
            }
        }
        if (traits.contains(Trait.CLIMB) && player.horizontalCollision) player.fallDistance = 0;
        if (traits.contains(Trait.FIRE_PROOF) && player.isOnFire()) player.clearFire();
        if (now % 20 != 0) return;
        senses(player, traits);
        if (traits.contains(Trait.SUN_BURN) && burnsNow(player)) player.igniteForSeconds(8);
        if (traits.contains(Trait.WATER_HURTS) && wet) player.hurt(player.damageSources().drown(), 1);
        if (traits.contains(Trait.CREEPER_REPELLENT)) scare(player);
    }

    /** Daylight on an unhelmeted head, out of water and rain, as a zombie burns. */
    static boolean burnsNow(ServerPlayer player) {
        var level = player.level();
        if (!level.isDay() || player.isInWaterRainOrBubble() || player.isInPowderSnow || player.wasInPowderSnow) return false;
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return false;
        var eyes = net.minecraft.core.BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return level.canSeeSky(eyes) && level.getMaxLocalRawBrightness(eyes) > 11;
    }

    private static void senses(ServerPlayer player, Set<Trait> traits) {
        if (traits.contains(Trait.NIGHT_EYES)) keep(player, MobEffects.NIGHT_VISION);
        if (traits.contains(Trait.BLIND)) keep(player, MobEffects.DARKNESS);
    }

    private static void keep(ServerPlayer player, Holder<MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < SENSE_TICKS - 40)
            player.addEffect(new MobEffectInstance(effect, SENSE_TICKS, 0, true, false, true));
    }

    /** Clears an effect the form gave (an ambient one no longer than ours), never a potion's. */
    private static void clear(ServerPlayer player, Holder<MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.isAmbient() && current.getDuration() <= SENSE_TICKS) player.removeEffect(effect);
    }

    /** Creepers near a cat back away from it, as they do from a real one. */
    private static void scare(ServerPlayer player) {
        for (Creeper creeper : player.serverLevel().getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(10))) {
            if (creeper.getTarget() == player) creeper.setTarget(null);
            creeper.setSwellDir(-1);
            Vec3 away = creeper.position().subtract(player.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
            Vec3 to = creeper.position().add(away.normalize().scale(8));
            creeper.getNavigation().moveTo(to.x, to.y, to.z, 1.2);
        }
    }

    private static void set(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    // ---- Events ----

    /** A creeper won't pick a fight with a cat. */
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Creeper && event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
                && ShapeshiftService.traits(player).contains(Trait.CREEPER_REPELLENT))
            event.setNewAboutToBeSetTarget(null);
    }

    /** Fire can't touch a blaze; shots pass an enderman by; Smite cuts the undead deeper. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Set<Trait> traits = ShapeshiftService.traits(player);
        if (traits.isEmpty()) return;
        var source = event.getSource();
        if (traits.contains(Trait.FIRE_PROOF) && source.is(DamageTypeTags.IS_FIRE)) { event.setCanceled(true); return; }
        if (traits.contains(Trait.PROJECTILE_PROOF) && source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) { event.setCanceled(true); return; }
        if (traits.contains(Trait.SLOW_FALL) && source.is(DamageTypeTags.IS_FALL)) { event.setCanceled(true); return; }
        if (traits.contains(Trait.UNDEAD) && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            var smite = player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.SMITE);
            int level = smite.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, attacker.getMainHandItem())).orElse(0);
            if (level > 0) event.setAmount(event.getAmount() + 2.5F * level);
        }
    }

    /** A snowball stings a blaze. */
    @SubscribeEvent public static void snowball(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof Snowball snowball && event.getRayTraceResult() instanceof EntityHitResult hit
                && hit.getEntity() instanceof ServerPlayer player && ShapeshiftService.traits(player).contains(Trait.SNOWBALL_HURTS))
            player.hurt(player.damageSources().thrown(snowball, snowball.getOwner()), 3);
    }

    /** Whether {@code player}'s own form inverts healing and harm (asked by the client and server alike). */
    public static boolean inverted(Player player) {
        var type = MorphBodies.form(player);
        return type != null && type.is(net.minecraft.tags.EntityTypeTags.INVERTED_HEALING_AND_HARM);
    }
}
