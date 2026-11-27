package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Last One Standing (Regeneration, Adaptation signature): Claire can't stay hurt, so she makes herself the target. For
 * six seconds every foe within 12 blocks turns on her; she braces, taking every blow at half strength, and banks all of
 * it (it feeds her Regrowth too). Then she lets it go, or presses again to let it go early: a crimson heartbeat bursts
 * out over the ground, hurting everyone within 8 blocks for 4 + tier and up to 16 more from what she banked, throwing
 * them back.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class LastOneStanding {
    public static final int STAND = 120, MAX_TAUNTED = 12;
    public static final double RADIUS = 12, RELEASE_RADIUS = 8;
    /** How much of what she banks comes back out, and the most it adds. */
    public static final float BANKED_SHARE = .5F, BANKED_CAP = 16;

    private static final class Stand {
        final ServerPlayer player; final ServerLevel level; final int tier; final List<Mob> taunted = new ArrayList<>();
        float banked; int age;
        Stand(ServerPlayer player, int tier) { this.player = player; this.level = player.serverLevel(); this.tier = tier; }
    }
    private static final Map<UUID, Stand> STANDS = new HashMap<>();

    private LastOneStanding() {}

    public static boolean standing(ServerPlayer player) { return STANDS.containsKey(player.getUUID()); }

    public static float banked(ServerPlayer player) {
        Stand s = STANDS.get(player.getUUID());
        return s == null ? 0 : s.banked;
    }

    public static boolean canUse(ServerPlayer player) { return !standing(player) && PowerRules.canAct(player); }

    public static boolean activate(ServerPlayer player, int tier) {
        if (!canUse(player)) return false;
        Stand stand = new Stand(player, Math.clamp(tier, 1, 5));
        STANDS.put(player.getUUID(), stand);
        taunt(stand);
        ArmPosePayload.show(player, ArmPose.REGEN_BRACE, 20);
        stand.level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2, .8F);
        stand.level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, .6F, 1.6F);
        player.displayClientMessage(Component.literal("Come on, then. All of you.").withStyle(ChatFormatting.RED), true);
        show(stand);
        return true;
    }

    /** The free second press: let it go now. */
    public static boolean followUp(ServerPlayer player) {
        Stand stand = STANDS.remove(player.getUUID());
        if (stand == null) return false;
        release(stand);
        return true;
    }

    /** Every foe in reach turns on her (bounded, nearest first). */
    private static void taunt(Stand stand) {
        ServerPlayer player = stand.player;
        stand.taunted.removeIf(m -> !m.isAlive() || m.level() != stand.level);
        for (LivingEntity e : stand.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
                t -> t instanceof Mob && ExpansionSkills.hostile(player, t) && t.distanceToSqr(player) <= RADIUS * RADIUS)) {
            Mob mob = (Mob) e;
            if (!stand.taunted.contains(mob)) {
                if (stand.taunted.size() >= MAX_TAUNTED) continue;
                stand.taunted.add(mob);
            }
            mob.setTarget(player);
        }
    }

    private static void show(Stand stand) {
        ServerPlayer player = stand.player;
        SetPiecePayload.sendSeeded(stand.level, Piece.REGEN_STAND, player.position(), player.position().add(0, 1, 0), (float) RADIUS, player, 0,
                Math.min(255, stand.age / 20));
        for (Mob mob : stand.taunted)
            if (mob.isAlive()) SetPiecePayload.sendSeeded(stand.level, Piece.REGEN_TAUNT, mob.position(), player.position(), 1, mob, 0, player.getId());
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        if (STANDS.isEmpty()) return;
        for (Stand stand : List.copyOf(STANDS.values())) {
            ServerPlayer player = stand.player;
            if (player.getServer() != server) continue;
            if (!player.isAlive() || player.isRemoved() || player.serverLevel() != stand.level || !PowerRules.canAct(player)) {
                STANDS.remove(player.getUUID());
                continue;
            }
            stand.age++;
            if (stand.age % 10 == 0) {
                taunt(stand);
                show(stand);
                ArmPosePayload.show(player, ArmPose.REGEN_BRACE, 15);
            }
            if (stand.age % 20 == 0) stand.level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                    1.2F + stand.age / 60F, .8F + stand.age / 400F);
            if (stand.age >= STAND) {
                STANDS.remove(player.getUUID());
                release(stand);
            }
        }
    }

    /** Every blow she takes while standing lands at half strength, and all of it is banked. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && standing(player)) event.setAmount(braced(player, event.getAmount()));
    }

    /** A blow on a standing Claire: banked in full, taken at half. The damage event's entry point (tests call it too). */
    public static float braced(ServerPlayer player, float amount) {
        Stand stand = STANDS.get(player.getUUID());
        if (stand == null || amount <= 0) return amount;
        stand.banked += amount;
        return amount * .5F;
    }

    /** Lets the banked wounds go: a heartbeat bursting out over the ground. Public for tests. */
    static float release(Stand stand) {
        ServerPlayer player = stand.player;
        ServerLevel level = stand.level;
        float damage = 4 + stand.tier + Math.min(BANKED_CAP, stand.banked * BANKED_SHARE);
        Vec3 feet = player.position();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RELEASE_RADIUS),
                t -> ExpansionSkills.hostile(player, t) && t.distanceToSqr(player) <= RELEASE_RADIUS * RELEASE_RADIUS && player.hasLineOfSight(t))) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().explosion(player, player), damage);
            Vec3 out = target.position().subtract(feet).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
            target.setDeltaMovement(out.scale(1.1).add(0, .45, 0));
            target.hurtMarked = true;
        }
        RegenerationService.addRegrowth(player, Math.round(stand.banked));
        SetPiecePayload.send(level, Piece.REGEN_RELEASE, feet, feet.add(0, 1, 0), (float) RELEASE_RADIUS, null);
        CameraShakePayload.sendAround(level, feet, RELEASE_RADIUS * 2, 1.1F, 12);
        ArmPosePayload.show(player, ArmPose.REGEN_PAYBACK, 14);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1, .7F);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.5F, .6F);
        player.displayClientMessage(Component.literal("Last One Standing: " + Math.round(stand.banked) + " banked, "
                + Math.round(damage) + " back to each of them.").withStyle(ChatFormatting.RED), true);
        return damage;
    }

    public static void forget(ServerPlayer player) { STANDS.remove(player.getUUID()); }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { STANDS.clear(); }
}
