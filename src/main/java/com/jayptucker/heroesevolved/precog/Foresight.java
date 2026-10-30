package com.jayptucker.heroesevolved.precog;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ClarityPayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Danger Sense and Clarity, Isaac Mendez's passive.
 *
 * <p>Danger Sense shows, privately, what is already set in motion against you: the path of a
 * projectile that will hit you and where it lands, a creeper's blast before it goes off, a bow being
 * drawn at you, Sylar on your trail. These are real predictions, simulated from the world as it is,
 * never guesses about what a player will decide.</p>
 *
 * <p>Changing a foreseen future earns <b>Clarity</b> (0 to 100): a foreseen shot that misses, a foreseen
 * blast you are clear of. Clarity powers Paint the Future and The Future Is Written, counts toward the
 * Evolved trial, and fades when nothing is coming.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Foresight {
    public static final int MAX_CLARITY = 100, PROJECTILE_CLARITY = 8, BLAST_CLARITY = 12;
    private static final int LOOKAHEAD = 30, SCAN_INTERVAL = 2, MAX_SHOTS = 24;

    private enum Source { PROJECTILE, BLAST }
    private static final class Threat {
        final ServerPlayer watcher; final UUID entity; final Source source; final Vec3 impact; final long until; boolean hit;
        Threat(ServerPlayer watcher, UUID entity, Source source, Vec3 impact, long until) {
            this.watcher = watcher; this.entity = entity; this.source = source; this.impact = impact; this.until = until;
        }
    }
    private static final class State { int clarity; long lastThreat; int sent = -1; }

    /** Threats by (watcher, source entity). */
    private static final Map<String, Threat> THREATS = new HashMap<>();
    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Last time a bow draw was shown, by (watcher, archer). */
    private static final Map<String, Long> DRAWS = new HashMap<>();

    private Foresight() {}

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    private static String key(UUID watcher, UUID source) { return watcher + "|" + source; }

    /** True if the player's awakened power is Precognition and powers are not suppressed. */
    public static boolean seer(ServerPlayer player) {
        return PowerRules.canAct(player) && PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getValue().isUnlocked() && e.getKey().equals(ModAbilities.PRECOGNITION_ID)).isPresent();
    }

    // ------------------------------------------------------------------
    // Clarity
    // ------------------------------------------------------------------

    public static int clarity(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? 0 : s.clarity;
    }

    public static void addClarity(ServerPlayer player, int amount) {
        State s = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        s.clarity = Math.clamp(s.clarity + amount, 0, MAX_CLARITY);
        if (amount > 0) s.lastThreat = now(player);
        sync(player);
    }

    /** Spends everything; returns how much there was. */
    public static int drainClarity(ServerPlayer player) {
        int had = clarity(player);
        if (had > 0) addClarity(player, -had);
        return had;
    }

    private static void sync(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        int value = s == null ? 0 : s.clarity;
        if (s != null && s.sent == value) return;
        if (s != null) s.sent = value;
        PacketDistributor.sendToPlayer(player, new ClarityPayload(value));
    }

    /**
     * The future changed: something you saw coming did not happen to you. Clarity, Mastery, a step on
     * the Evolved trial, and a private crossed-out mark where it would have landed.
     */
    public static void changed(ServerPlayer player, Vec3 where, int clarity) {
        addClarity(player, clarity);
        MasteryService.grantMastery(player, 2);
        EvolutionTrials.advance(player, ModAbilities.PRECOGNITION_ID);
        show(player, Kind.PRECOG_CROSS, where, where, 1.0F);
        player.playNotifySound(SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    // ------------------------------------------------------------------
    // Danger Sense
    // ------------------------------------------------------------------

    /** How far Danger Sense reaches: 16 blocks at tier 1, 32 at tier 5, half again during Premonition. */
    public static double radius(ServerPlayer player) {
        double base = 12 + 4 * PlayerProgressionService.getLevel(player);
        return PrecognitionService.premonition(player) ? base * 1.5 : base;
    }

    static void show(ServerPlayer player, Kind kind, Vec3 origin, Vec3 target, float scale) {
        PacketDistributor.sendToPlayer(player, new AbilityEffectPayload(kind, origin, target, scale));
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean seer = seer(player);
            // Omen Relay lends a teammate the sight for a while; they see threats but earn no Clarity.
            if (!seer && !PrecognitionService.sharedSight(player)) {
                if (STATES.containsKey(player.getUUID()) && !seer) forget(player);
                continue;
            }
            if ((now + player.getId()) % SCAN_INTERVAL == 0) scan(player);
            if (seer) {
                State s = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
                // Clarity fades when nothing has been coming for ten seconds.
                if (s.clarity > 0 && now - s.lastThreat > 200 && now % 20 == 0) s.clarity--;
                sync(player);
            }
        }
        resolve(server, now);
        DRAWS.values().removeIf(at -> now - at > 200);
    }

    /** Looks for what is coming at {@code player} right now. Runs every other tick; tests call it directly. */
    public static void scan(ServerPlayer player) {
        long now = now(player);
        double radius = radius(player);
        AABB area = player.getBoundingBox().inflate(radius);
        AABB body = player.getBoundingBox().inflate(0.35);
        var level = player.serverLevel();

        int shots = 0;
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, area)) {
            if (++shots > MAX_SHOTS) break;
            Entity owner = shot.getOwner();
            if (owner == player || owner != null && player.isAlliedTo(owner)) continue;
            if (shot.getDeltaMovement().lengthSqr() < 0.01) continue;
            String key = key(player.getUUID(), shot.getUUID());
            if (THREATS.containsKey(key)) continue;
            Optional<Vec3> impact = predictHit(shot, body);
            if (impact.isEmpty()) continue;
            int ticks = (int) Math.max(1, Math.round(shot.position().distanceTo(impact.get()) / Math.max(0.2, shot.getDeltaMovement().length())));
            THREATS.put(key, new Threat(player, shot.getUUID(), Source.PROJECTILE, impact.get(), now + ticks + 10));
            show(player, Kind.PRECOG_THREAT, shot.position(), impact.get(), Math.max(1, ticks));
            touch(player, now);
        }

        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(8))) {
            if (creeper.getSwellDir() <= 0) continue;
            String key = key(player.getUUID(), creeper.getUUID());
            if (THREATS.containsKey(key)) continue;
            float blast = creeper.isPowered() ? 6 : 3;
            THREATS.put(key, new Threat(player, creeper.getUUID(), Source.BLAST, creeper.position(), now + 60));
            show(player, Kind.PRECOG_RING, creeper.position(), creeper.position(), blast * 1.4F);
            touch(player, now);
        }

        for (Mob archer : level.getEntitiesOfClass(Mob.class, area, m -> m.isUsingItem() && m.getTarget() == player)) {
            String key = key(player.getUUID(), archer.getUUID());
            Long shown = DRAWS.get(key);
            if (shown != null && now - shown < 30) continue;
            DRAWS.put(key, now);
            // A thin line: the aim, not yet a shot.
            show(player, Kind.PRECOG_THREAT, archer.getEyePosition(), player.getEyePosition(), 0.2F);
        }

        // Sylar on your trail is always felt, and seen for what he is.
        var hunter = com.jayptucker.heroesevolved.story.sylar.SylarStoryService.hunter();
        if (hunter != null && hunter.level() == level && hunter.distanceToSqr(player) < 64 * 64 && hunter.getTarget() == player) {
            PrivateGlow.glow(player, hunter, 10);
            if (now % 40 == 0) show(player, Kind.PRECOG_THREAT, hunter.getEyePosition(), player.getEyePosition(), 0.2F);
        }
    }

    private static void touch(ServerPlayer player, long now) {
        STATES.computeIfAbsent(player.getUUID(), ignored -> new State()).lastThreat = now;
    }

    /**
     * Steps the projectile forward the way vanilla moves it (velocity, then drag and gravity) and
     * returns where it would meet {@code body}, unless a block is in the way first.
     */
    public static Optional<Vec3> predictHit(Projectile shot, AABB body) {
        Vec3 position = shot.position(), velocity = shot.getDeltaMovement();
        double drag = shot instanceof AbstractArrow ? (shot.isInWater() ? 0.6 : 0.99) : 1.0;
        double gravity = shot.getGravity();
        for (int tick = 0; tick < LOOKAHEAD; tick++) {
            Vec3 next = position.add(velocity);
            Optional<Vec3> hit = body.clip(position, next);
            if (hit.isPresent() || body.contains(next)) {
                Vec3 at = hit.orElse(next);
                return blocked(shot, at) ? Optional.empty() : Optional.of(at);
            }
            position = next;
            velocity = velocity.scale(drag).subtract(0, gravity, 0);
        }
        return Optional.empty();
    }

    /** A wall between the shot and its foreseen impact means it was never coming. */
    private static boolean blocked(Projectile shot, Vec3 impact) {
        var result = shot.level().clip(new ClipContext(shot.position(), impact, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shot));
        return result.getType() == HitResult.Type.BLOCK && result.getLocation().distanceToSqr(shot.position()) < impact.distanceToSqr(shot.position()) - 0.25;
    }

    private static void resolve(MinecraftServer server, long now) {
        for (Iterator<Map.Entry<String, Threat>> it = THREATS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Threat threat = entry.getValue();
            ServerPlayer watcher = threat.watcher;
            Entity entity = watcher.isRemoved() ? null : watcher.serverLevel().getEntity(threat.entity);
            boolean done = entity == null || !entity.isAlive() || now > threat.until;
            if (!done) continue;
            it.remove();
            if (watcher.isRemoved() || threat.hit || !seer(watcher) || !watcher.isAlive()) continue;
            // A blast only counts if it actually went off.
            if (threat.source == Source.BLAST && entity != null && entity.isAlive()) continue;
            changed(watcher, threat.impact, threat.source == Source.BLAST ? BLAST_CLARITY : PROJECTILE_CLARITY);
        }
    }

    /** Damage that actually lands means that future came true. */
    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity direct = event.getSource().getDirectEntity();
        if (direct != null) {
            Threat threat = THREATS.get(key(player.getUUID(), direct.getUUID()));
            if (threat != null) threat.hit = true;
        }
    }

    /** How many foreseen harms are still on their way to {@code player}. */
    public static int foreseen(ServerPlayer player) {
        return (int) THREATS.values().stream().filter(t -> t.watcher == player).count();
    }

    public static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
        String prefix = player.getUUID() + "|";
        THREATS.keySet().removeIf(k -> k.startsWith(prefix));
        DRAWS.keySet().removeIf(k -> k.startsWith(prefix));
        PacketDistributor.sendToPlayer(player, new ClarityPayload(0));
    }

    public static void clear() { THREATS.clear(); STATES.clear(); DRAWS.clear(); }
}
