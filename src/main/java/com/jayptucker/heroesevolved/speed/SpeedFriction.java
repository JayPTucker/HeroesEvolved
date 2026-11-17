package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.hazard.BodyBurn;
import com.jayptucker.heroesevolved.hazard.HazardDamage;
import com.jayptucker.heroesevolved.hazard.Suits;
import com.jayptucker.heroesevolved.radiation.RadiationDeaths;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Friction at a speedster's top speeds, and a speedster's quickened healing.
 * <p>
 * Friction: Running faster than {@link #FRICTION_PACE} (about 37 blocks a second: the top
 * run speeds, or a lower one with a lot of Momentum) heats their body, faster the faster they go, and a Lightspeed streak
 * adds a burst of it. Heat shows as smoke, then their skin scorches and peels away, and past {@link #BURN} it burns them
 * every half-second until they ease off. It cools within a second or two at ordinary speeds (faster in water or rain).
 * The velocity suit keeps a quarter of it off for each piece; the whole suit, all of it. The heat is sent to the speedster
 * for the burn meter on their screen.
 * <p>
 * Healing: a speedster's body heals a little on its own, {@link #HEAL} health every {@link #healEvery} ticks (70 at tier
 * 1, 30 at tier 5: from a seventh to a third of a heart a second, on top of ordinary regeneration), once they haven't been
 * hurt for two seconds, aren't burning from friction or on a containment ring, and aren't starving. It costs hunger, as
 * ordinary healing does. Their burnt skin also grows back faster ({@code BodyBurn.healRate}).
 */
public final class SpeedFriction {
    /** Blocks a tick above which friction builds heat. */
    public static final double FRICTION_PACE = 1.85;
    public static final float SMOKE = 25, SCORCH = 50, BURN = 70, MAX = 100;
    /** Heat a Lightspeed streak adds. */
    public static final float LIGHTSPEED_HEAT = 60;

    /** Health a speedster's body knits back each time, and the hunger it costs. */
    static final float HEAL = 0.5F, HEAL_EXHAUSTION = 1.5F;

    /** Ticks between a speedster's heals at {@code tier}: 70 at tier 1, 30 at tier 5. */
    static int healEvery(int tier) { return Math.max(30, 80 - 10 * tier); }

    private static final class Heat {
        float heat, sent = -1;
        Vec3 last;
        double pace;
        long warned = -1000;
        ServerLevel level;
    }
    private static final Map<UUID, Heat> HEATS = new HashMap<>();

    private SpeedFriction() {}

    /** The server's {@code hazards.frictionBurns} switch (on if the config isn't loaded yet). */
    static boolean enabled() {
        try { return com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.frictionBurns.get(); }
        catch (IllegalStateException notLoaded) { return true; }
    }

    public static float heat(ServerPlayer p) {
        Heat h = HEATS.get(p.getUUID());
        return h == null ? 0 : h.heat;
    }

    /** A burst of friction at once (Lightspeed). */
    public static void burst(ServerPlayer p, float heat) {
        if (!enabled()) return;
        Heat h = HEATS.computeIfAbsent(p.getUUID(), id -> fresh(p));
        float protect = Suits.friction(p);
        h.heat = Math.min(MAX, h.heat + heat * (1 - protect));
        if (protect > 0) Suits.scuff(p, heat * protect * 0.05F);
    }

    /** One tick of friction for a runner running {@code pace} blocks a tick (exposed for tests). */
    static void run(ServerPlayer p, double pace, long now) {
        Heat h = HEATS.computeIfAbsent(p.getUUID(), id -> fresh(p));
        float protect = Suits.friction(p);
        if (pace > FRICTION_PACE && p.isAlive()) {
            float gain = (float) (0.9 + 2.2 * (pace - FRICTION_PACE) / FRICTION_PACE);
            h.heat = Math.min(MAX, h.heat + gain * (1 - protect));
            if (protect > 0) Suits.scuff(p, gain * protect * 0.1F);
        } else {
            h.heat = Math.max(0, h.heat - (p.isInWaterRainOrBubble() ? 6 : 2.5F));
        }
        effects(p, h, now);
        if (Math.abs(h.heat - h.sent) >= 2 || h.heat == 0 && h.sent != 0) {
            h.sent = h.heat;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new com.jayptucker.heroesevolved.network.FrictionPayload(h.heat));
        }
    }

    /** One tick of a speedster's quickened healing (exposed for tests). */
    static void recover(ServerPlayer p, long now) {
        if (!p.isAlive() || p.getHealth() >= p.getMaxHealth() || p.getLastDamageSource() != null || heat(p) >= SCORCH
                || SpeedContainment.running(p) || p.getFoodData().getFoodLevel() <= 6) return;
        int tier = com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(p);
        if (now % healEvery(tier) != 0) return;
        p.heal(HEAL);
        p.causeFoodExhaustion(HEAL_EXHAUSTION);
    }

    private static Heat fresh(ServerPlayer p) {
        Heat h = new Heat();
        h.last = p.position();
        h.level = p.serverLevel();
        return h;
    }

    static void tick(MinecraftServer server, long now) {
        boolean friction = enabled();
        if (!friction) HEATS.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Suits.speedster(p)) recover(p, now);
            if (!friction) continue;
            boolean running = SpeedService.active(p);
            Heat h = HEATS.get(p.getUUID());
            if (h == null) {
                if (running) HEATS.put(p.getUUID(), fresh(p));
                continue;
            }
            if (h.level != p.level()) { h.level = p.serverLevel(); h.last = p.position(); }
            double step = p.position().subtract(h.last).horizontalDistance();
            h.last = p.position();
            // Teleports, dashes and rides aren't running: Blitz and the like add no friction.
            if (step > 8 || p.isPassenger()) step = 0;
            h.pace = h.pace * 0.6 + step * 0.4;
            run(p, running ? h.pace : 0, now);
            if (h.heat <= 0 && !running) HEATS.remove(p.getUUID());
        }
        HEATS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static void effects(ServerPlayer p, Heat h, long now) {
        ServerLevel level = p.serverLevel();
        if (h.heat >= SMOKE && now % 4 == 0)
            level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 1 + (int) (h.heat / 25), 0.3, 0.5, 0.3, 0.01);
        if (h.heat < SCORCH) return;
        if (now % 3 == 0) level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
        BodyBurn.raise(p, 0.01F, (h.heat - SCORCH) / (MAX - SCORCH) * 0.8F);
        if (now - h.warned > 200) {
            h.warned = now;
            p.displayClientMessage(Component.literal("Friction is burning you up. Ease off, or wear a velocity suit.")
                    .withStyle(ChatFormatting.GOLD), true);
        }
        if (h.heat >= BURN && now % 10 == 0) {
            float damage = 1 + (h.heat - BURN) / 15;
            RadiationDeaths.mark(p);
            p.hurt(HazardDamage.source(level, HazardDamage.FRICTION), damage);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
    }

    static void forget(ServerPlayer p) { HEATS.remove(p.getUUID()); }

    static void clear() { HEATS.clear(); }
}
