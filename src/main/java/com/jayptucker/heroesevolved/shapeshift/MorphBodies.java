package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which form each player wears, on each side, so both sides give them the same hitbox and eye height. The server's map
 * is filled by {@link ShapeshiftService}; the client's by {@code MorphStatePayload}. They are kept apart because the
 * integrated server and its client share one JVM and the same entity ids.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MorphBodies {
    private static final Map<Integer, EntityType<?>> SERVER = new ConcurrentHashMap<>();
    private static final Map<Integer, EntityType<?>> CLIENT = new ConcurrentHashMap<>();

    private MorphBodies() {}

    /** Sets (or with null clears) the form {@code entity} wears on its own side, and resizes it. */
    public static void set(Entity entity, @Nullable EntityType<?> form) {
        set(entity.level().isClientSide(), entity.getId(), form);
        entity.refreshDimensions();
    }

    public static void set(boolean client, int entity, @Nullable EntityType<?> form) {
        Map<Integer, EntityType<?>> map = client ? CLIENT : SERVER;
        if (form == null) map.remove(entity); else map.put(entity, form);
    }

    public static @Nullable EntityType<?> form(Entity entity) {
        var level = entity.level();
        if (level == null) return null;
        return (level.isClientSide() ? CLIENT : SERVER).get(entity.getId());
    }

    public static Map<Integer, EntityType<?>> client() { return CLIENT; }

    public static void clear(boolean client) { (client ? CLIENT : SERVER).clear(); }

    /** The form's own size. A crouching wearer of a form taller than a block ducks a little, as a player does. */
    public static EntityDimensions dimensions(EntityType<?> form, Pose pose) {
        EntityDimensions own = form.getDimensions();
        return pose == Pose.CROUCHING && own.height() > 1 ? own.scale(1, 0.85F) : own;
    }

    @SubscribeEvent public static void size(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getPose() == Pose.SLEEPING || event.getPose() == Pose.DYING) return;
        EntityType<?> form = form(player);
        // Apex Form grows the body through the scale attribute; a form's own size has to be scaled to match.
        if (form != null) event.setNewSize(dimensions(form, event.getPose()).scale(player.getScale()));
    }
}
