package com.jayptucker.heroesevolved.dampener;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The one question every power asks: is this body dampened? It is when it stands inside a live Ability Dampener's
 * field, or while a dampener round's {@link #DAMPENED} effect lasts. {@code PowerRules} asks it for players (so every
 * key, sustained power and passive that respects the gate goes quiet), Sylar and Elle ask it before they cast, and
 * Empathic Mimicry finds nothing to copy in a dampened body.
 *
 * <p>The field reaches through walls, as a Suppressor's quiet does: it is a field, not a beam.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Dampening {
    private static final Set<AbilityDampenerBlockEntity> FIELDS = Collections.newSetFromMap(new WeakHashMap<>());
    /** Bodies a round has marked, so the server can keep their mark drawn while the effect lasts. */
    private static final Set<LivingEntity> MARKED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Players who were inside a field last tick, to tell them once when they cross its edge. */
    private static final Set<UUID> INSIDE = new HashSet<>();

    private Dampening() {}

    // ------------------------------------------------------------------
    // The question
    // ------------------------------------------------------------------

    /** True if {@code entity}'s power is dampened: by a field, or by a round. Server side for fields. */
    public static boolean dampened(Entity entity) {
        if (entity instanceof LivingEntity living && living.hasEffect(ModItems.DAMPENED)) return true;
        return field(entity) != null;
    }

    /** True while a dampener round's effect lasts on {@code entity}. */
    public static boolean struck(LivingEntity entity) { return entity.hasEffect(ModItems.DAMPENED); }

    /** The live projector whose field {@code entity} stands in, or null. */
    public static @Nullable AbilityDampenerBlockEntity field(Entity entity) {
        if (FIELDS.isEmpty() || entity.level().isClientSide()) return null;
        for (AbilityDampenerBlockEntity field : List.copyOf(FIELDS)) {
            if (field.isRemoved() || field.getLevel() == null) { FIELDS.remove(field); continue; }
            if (field.getLevel() == entity.level() && field.covers(entity.position().add(0, entity.getBbHeight() * 0.5, 0))) return field;
        }
        return null;
    }

    /** Every projector that is standing and powered, for tests and diagnostics. */
    public static List<AbilityDampenerBlockEntity> fields() {
        List<AbilityDampenerBlockEntity> live = new ArrayList<>();
        for (AbilityDampenerBlockEntity field : List.copyOf(FIELDS)) if (!field.isRemoved() && field.live()) live.add(field);
        return live;
    }

    static void track(AbilityDampenerBlockEntity field, boolean live) {
        if (live) FIELDS.add(field); else FIELDS.remove(field);
    }

    // ------------------------------------------------------------------
    // A round lands
    // ------------------------------------------------------------------

    /**
     * A dampener round hit {@code target}: its power goes quiet for {@link DampenerGeometry#ROUND_PLAYER_TICKS} (a
     * player) or {@link DampenerGeometry#ROUND_MOB_TICKS}. A second round refreshes the time; it doesn't add to it.
     * Returns the ticks it holds.
     */
    public static int strike(@Nullable LivingEntity attacker, LivingEntity target, Vec3 at) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive()) return 0;
        int ticks = target instanceof ServerPlayer ? DampenerGeometry.ROUND_PLAYER_TICKS : DampenerGeometry.ROUND_MOB_TICKS;
        boolean fresh = !struck(target);
        target.addEffect(new MobEffectInstance(ModItems.DAMPENED, ticks, 0, false, false, true), attacker);
        MARKED.add(target);
        SetPiecePayload.send(level, Piece.DAMPENER_HIT, at, target.getBoundingBox().getCenter(), target.getBbHeight(), target);
        mark(level, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 1.7F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        if (target instanceof ServerPlayer player) {
            ScreenTintPayload.send(player, 0x55303A44, 30, ScreenTintPayload.Style.EDGE);
            if (fresh) player.displayClientMessage(Component.translatable("dampener.heroesevolved.struck").withStyle(ChatFormatting.GRAY), true);
        }
        return ticks;
    }

    /** Draws the dampened body's mark for the next {@link DampenerGeometry#MARK_PIECE} ticks. */
    private static void mark(ServerLevel level, LivingEntity target) {
        SetPiecePayload.send(level, Piece.DAMPENER_MARK, target.position(), target.position(), target.getBbHeight(), target);
    }

    // ------------------------------------------------------------------
    // Each tick
    // ------------------------------------------------------------------

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        if (!MARKED.isEmpty()) {
            for (LivingEntity target : List.copyOf(MARKED)) {
                if (target.getServer() != server) continue;
                if (target.isRemoved() || !target.isAlive() || !struck(target)) { MARKED.remove(target); continue; }
                if (target.level() instanceof ServerLevel level && target.tickCount % DampenerGeometry.MARK_RESEND == 0) mark(level, target);
            }
        }
        if (FIELDS.isEmpty() && INSIDE.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean inside = !player.isSpectator() && field(player) != null;
            boolean was = INSIDE.contains(player.getUUID());
            if (inside == was) continue;
            if (inside) {
                INSIDE.add(player.getUUID());
                player.displayClientMessage(Component.translatable("dampener.heroesevolved.enter").withStyle(ChatFormatting.GRAY), true);
                player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 0.7F);
                ScreenTintPayload.send(player, 0x40303A44, 20, ScreenTintPayload.Style.EDGE);
            } else {
                INSIDE.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("dampener.heroesevolved.leave").withStyle(ChatFormatting.GRAY), true);
                player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4F, 1.6F);
            }
        }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { clear(); }

    public static void clear() {
        FIELDS.clear();
        MARKED.clear();
        INSIDE.clear();
    }

    // ------------------------------------------------------------------

    /** The effect a dampener round leaves. Milk doesn't wash it out: it wears off. */
    public static final class Effect extends MobEffect {
        public Effect() { super(MobEffectCategory.HARMFUL, 0x5C6B78); }

        @Override public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {}
    }
}
