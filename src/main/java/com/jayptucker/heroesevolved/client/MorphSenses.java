package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftForm;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftTraits;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftTraits.Trait;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The traits only the shapeshifter's own client can play: walking up walls in a spider's shape (movement is the
 * client's), and the glow of what a wolf smells (hurt creatures within 16 blocks) or a warden feels (anything moving
 * within 20, through walls). Only the shapeshifter sees the glow.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class MorphSenses {
    public static final double SCENT_RANGE = 16, VIBRATION_RANGE = 20;
    private static final Map<EntityType<?>, Set<Trait>> TRAITS = new HashMap<>();

    private MorphSenses() {}

    /** The traits of the form this player wears on this client. */
    public static Set<Trait> traits(Player player) {
        ShapeshiftForm form = MorphClient.form(player.getId());
        if (form == null || form.person()) return Set.of();
        return TRAITS.computeIfAbsent(form.type(), type -> {
            Entity made;
            try { made = type.create(player.level()); } catch (RuntimeException e) { made = null; }
            return made instanceof LivingEntity living ? ShapeshiftTraits.of(living) : EnumSet.noneOf(Trait.class);
        });
    }

    /** Whether {@code entity} glows for the local player's nose or vibration sense. */
    public static boolean sensed(Entity entity) {
        var player = Minecraft.getInstance().player;
        if (player == null || entity == player || !(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        Set<Trait> traits = traits(player);
        if (traits.isEmpty()) return false;
        double d2 = entity.distanceToSqr(player);
        if (traits.contains(Trait.SCENT) && d2 < SCENT_RANGE * SCENT_RANGE && living.getHealth() < living.getMaxHealth()) return true;
        if (traits.contains(Trait.VIBRATIONS) && d2 < VIBRATION_RANGE * VIBRATION_RANGE) {
            double dx = entity.getX() - entity.xo, dy = entity.getY() - entity.yo, dz = entity.getZ() - entity.zo;
            return dx * dx + dy * dy + dz * dz > 1.0E-4 || living.swinging;
        }
        return false;
    }

    /** A spider walks up any wall it walks into; holding sneak clings in place. */
    @SubscribeEvent public static void climb(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getAbilities().flying || !traits(player).contains(Trait.CLIMB) || !player.horizontalCollision) return;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, player.isShiftKeyDown() ? Math.max(motion.y, 0) : Math.max(motion.y, .2), motion.z);
        player.resetFallDistance();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { TRAITS.clear(); }
}
