package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * What a form without hands can't do (docs/shapeshifting.md, "Hands"). People, villagers, illagers, piglins, zombies,
 * skeletons, endermen, witches and vexes keep their hands. Every other form can't use items, place blocks or open
 * containers. It breaks blocks at the bare-hand rate and harvests only what a bare hand can, and it fights with its own
 * blow, never a held weapon's. Both sides ask the same question, so the client never predicts a use the server refuses.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ShapeshiftHands {
    /** Whether each form keeps hands, per side (an example can't be shared between the client and the server). */
    private static final Map<EntityType<?>, Boolean> SERVER = new HashMap<>(), CLIENT = new HashMap<>();

    private ShapeshiftHands() {}

    /** Whether {@code player} is in a form without hands, on whichever side asks. */
    public static boolean handless(Player player) {
        EntityType<?> type = MorphBodies.form(player);
        if (type == null || type == EntityType.PLAYER) return false;
        Level level = player.level();
        Map<EntityType<?>, Boolean> cache = level.isClientSide() ? CLIENT : SERVER;
        return !cache.computeIfAbsent(type, t -> {
            Entity made;
            try { made = t.create(level); } catch (RuntimeException e) { made = null; }
            return made == null || ShapeshiftGeometry.handed(made);
        });
    }

    private static void refuse(PlayerInteractEvent event) {
        if (event instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock block) {
            block.setCanceled(true);
            block.setCancellationResult(InteractionResult.FAIL);
        } else if (event instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem item) {
            item.setCanceled(true);
            item.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** No doors, chests, levers or placing blocks without hands. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) { if (handless(event.getEntity())) refuse(event); }

    /** No eating, drinking, throwing or drawing a bow without hands. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void useItem(PlayerInteractEvent.RightClickItem event) { if (handless(event.getEntity())) refuse(event); }

    /** Digging at the bare-hand rate, whatever is held. */
    @SubscribeEvent public static void dig(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!handless(player)) return;
        float tool = player.getMainHandItem().getDestroySpeed(event.getState());
        if (tool > 1) event.setNewSpeed(event.getNewSpeed() / tool);
    }

    /** ...and only what a bare hand can harvest. */
    @SubscribeEvent public static void harvest(PlayerEvent.HarvestCheck event) {
        if (handless(event.getEntity()) && event.getTargetBlock().requiresCorrectToolForDrops()) event.setCanHarvest(false);
    }

    /** A form without hands strikes with its own blow; a held sword adds nothing. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void blow(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if (!source.is(DamageTypes.PLAYER_ATTACK) || !(source.getEntity() instanceof ServerPlayer attacker)
                || source.getDirectEntity() != attacker || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !handless(attacker)) return;
        LivingEntity example = ShapeshiftService.example(attacker);
        if (example == null) return;
        // The swing's damage already counts the weapon; scale it back to the form's own share of it.
        double own = Math.max(1, ShapeshiftForms.body(example).attack());
        double total = attacker.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (total > own) event.setAmount((float) (event.getAmount() * own / total));
    }

    public static void clear() { SERVER.clear(); }
}
