package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Powers that make shields can outlast a nuclear blast. When the wave reaches a player whose power raises force
 * fields, barriers, ice or walls of sound, and who is at least tier {@link #MIN_TIER}, a shield flares up round them:
 * the wave hurts them far less (a half at tier 4, a quarter at tier 5) and can never leave them below one heart. The
 * shield then stands for {@link #FALLOUT_TICKS} ticks, keeping the fallout off while they get clear.
 */
public final class NuclearShields {
    /** The lowest tier (Power Level 10) whose shield can hold a nuclear blast. */
    public static final int MIN_TIER = 4;
    /** How long the shield keeps the fallout off after the wave. */
    public static final int FALLOUT_TICKS = 200;

    /** The shield-making powers, and the look each one's shield takes (drawn by {@code ShieldArt}). */
    public enum Kind { FORCE, KINETIC, MATTER, ICE, SOUND }
    private static final Map<ResourceLocation, Kind> SHIELDS = Map.of(
            ModAbilities.INVISIBILITY_ID, Kind.FORCE,
            ModAbilities.TELEKINESIS_ID, Kind.KINETIC,
            ModAbilities.MATTER_ASSEMBLY_ID, Kind.MATTER,
            ModAbilities.CRYOKINESIS_ID, Kind.ICE,
            ModAbilities.SONIC_RESONANCE_ID, Kind.SOUND);

    private static final Map<UUID, Long> SHIELDED = new HashMap<>();

    private NuclearShields() {}

    /** The shield this player's power raises, or null if it raises none. */
    public static Kind kind(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked())
                .map(e -> SHIELDS.get(e.getKey())).orElse(null);
    }

    /** Can this player's shield hold a nuclear blast right now? */
    public static boolean canBrace(ServerPlayer player) {
        return kind(player) != null && PlayerProgressionService.getLevel(player) >= MIN_TIER && PowerRules.canAct(player);
    }

    /**
     * The wave reaches {@code target}: if they can shield, the shield goes up and this returns the damage that gets
     * through (never enough to kill); otherwise the damage unchanged.
     */
    public static float brace(LivingEntity target, Vec3 center, float damage) {
        if (!(target instanceof ServerPlayer player) || !canBrace(player)) return damage;
        Kind kind = kind(player);
        int tier = PlayerProgressionService.getLevel(player);
        float through = damage * (tier >= 5 ? 0.25F : 0.5F);
        through = Math.min(through, Math.max(0, player.getHealth() - 2));
        SHIELDED.put(player.getUUID(), player.serverLevel().getGameTime() + FALLOUT_TICKS);
        SetPiecePayload.send(player.serverLevel(), Piece.SHIELD_BRACE, player.position(), center, kind.ordinal() + 1, player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.6F, 0.6F);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4F, 0.5F);
        player.displayClientMessage(Component.literal("Your shield holds against the blast. Get clear before it fails.")
                .withStyle(ChatFormatting.AQUA), true);
        return through;
    }

    /** Is this player still behind the shield that held the blast (so the fallout can't reach them)? */
    public static boolean shielded(LivingEntity target) {
        if (!(target instanceof ServerPlayer player)) return false;
        Long until = SHIELDED.get(player.getUUID());
        if (until == null) return false;
        if (player.serverLevel().getGameTime() > until) { SHIELDED.remove(player.getUUID()); return false; }
        return true;
    }

    public static void forget(ServerPlayer player) { SHIELDED.remove(player.getUUID()); }

    public static void clear() { SHIELDED.clear(); }

    /** For tests: the powers whose shields can hold a blast. */
    public static List<ResourceLocation> powers() { return List.copyOf(SHIELDS.keySet()); }
}
