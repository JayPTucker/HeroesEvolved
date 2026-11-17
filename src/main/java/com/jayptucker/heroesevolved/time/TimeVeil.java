package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.TimeVeilPayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Moving faster than time. While someone stops time (Still World), everyone else sees them frozen where they stood:
 * their real body is hidden, and a stand-in with their skin holds still on the spot. Only those who move in time
 * themselves (Superspeed, Space-Time, or a Mimic channeling either) see them as they really are.
 * <p>
 * Slowed time (Between Seconds, a speedster's Perception) hides nobody: everyone sees the caster move, smeared into a
 * blur of afterimages ({@link TimeFlow}, drawn by {@code SlowTimeEffects}). It still keeps the veil's other parts: the
 * hints in chat, the slowed mobs aiming at where they last saw the caster ({@link #perceivedTarget}), and the tint.
 * <p>
 * When a stop ends, a caster back on their spot simply carries on. Anyone else vanishes from the spot: a speedster in a
 * blur of afterimages and a gust of wind, a time traveller in a blink; they are wherever they really are.
 * <p>
 * While time is slowed, every player's screen takes a faint amber tint, so everyone can tell it is happening.
 */
public final class TimeVeil {
    public enum Style { SPEED, TIME }

    public static final String SLOW = "slow", STOP = "stop", PERCEPTION = "perception";
    /** A guest's reason ({@link TemporalSharing}) is this and the owner's id. */
    static final String SHARED = "shared:";
    /** How close to the spot counts as "back where they started". */
    static final double HOME = 1.5;
    /** Effects drawn this close to a hidden caster are hidden with them, so a lightning trail can't give them away. */
    private static final double MASK = 3.2;

    private static final class Veil {
        final UUID owner;
        final ResourceKey<Level> dimension;
        Vec3 at;
        float yaw, pitch;
        final Style style;
        net.neoforged.neoforge.common.util.FakePlayer shadow;
        final Set<String> reasons = new HashSet<>();
        /** Who was given a hint that something odd was happening, and so is told when it is over. */
        final Set<UUID> puzzled = new HashSet<>();

        Veil(ServerPlayer player, Style style) {
            owner = player.getUUID();
            dimension = player.level().dimension();
            at = player.position();
            yaw = player.yBodyRot;
            pitch = player.getXRot();
            this.style = style;
        }

        /** Only stopped time hides anyone (its caster, and the guests it was shared with); slowed time shows them as a blur. */
        boolean hiding() {
            for (String reason : reasons)
                if (reason.equals(STOP) || reason.startsWith(SHARED) && TimeStopService.holds(UUID.fromString(reason.substring(SHARED.length()))))
                    return true;
            return false;
        }

        /** The stand-in stands where they were when time stopped, not where an earlier slow began. */
        void anchor(ServerPlayer player) {
            at = player.position();
            yaw = player.yBodyRot;
            pitch = player.getXRot();
            if (shadow != null) shadow.moveTo(at.x, at.y, at.z, yaw, pitch);
        }
    }

    private static final Map<UUID, Veil> VEILS = new HashMap<>();

    private static final Map<net.minecraft.world.entity.LivingEntity, ServerPlayer> SHADOWS = new java.util.WeakHashMap<>();

    public static net.minecraft.world.entity.LivingEntity shadow(UUID id) {
        return SHADOWS.keySet().stream().filter(e -> e.getUUID().equals(id)).findFirst().orElse(null);
    }

    private TimeVeil() {}

    /** Server-wide unless the config keeps time fields to an area (game tests do, so parallel tests can't freeze each other). */
    public static boolean serverWide() { return HeroesEvolvedConfig.COMMON.timeFieldsServerWide.get(); }

    /** Server-only remembered body: never spawned, registered as a player, or sent to a client. */
    public static net.minecraft.world.entity.LivingEntity perceivedTarget(net.minecraft.world.entity.Mob mob,
            net.minecraft.world.entity.LivingEntity target) {
        if (target == null) return null;
        // Brain memories may still hold the old shadow after the field ends.
        ServerPlayer original = SHADOWS.get(target);
        if (original != null) {
            Veil current = VEILS.get(original.getUUID());
            return current != null && current.shadow == target ? target : perceivedTarget(mob, original);
        }
        if (!(target instanceof ServerPlayer player)) return target;
        Veil veil = VEILS.get(player.getUUID());
        if (veil == null || mob.level().dimension() != veil.dimension) return target;
        if (veil.shadow == null) {
            veil.shadow = new net.neoforged.neoforge.common.util.FakePlayer(mob.level() instanceof ServerLevel level ? level : player.serverLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TimeShadow"));
            veil.shadow.moveTo(veil.at.x, veil.at.y, veil.at.z, veil.yaw, veil.pitch);
            SHADOWS.put(veil.shadow, player);
        }
        return veil.shadow;
    }

    /** Starts (or extends) hiding {@code player} behind a stand-in, for one {@code reason}. The first reason fixes the spot. */
    public static void begin(ServerPlayer player, Style style, String reason) {
        Veil veil = VEILS.get(player.getUUID());
        boolean fresh = veil == null;
        if (fresh) {
            veil = new Veil(player, style);
            VEILS.put(player.getUUID(), veil);
            hint(player, veil, reason.equals(STOP));
        }
        boolean wasHiding = veil.hiding();
        veil.reasons.add(reason);
        if (!wasHiding && veil.hiding()) {
            if (!fresh) veil.anchor(player);
            show(player.getServer(), veil);
        } else if (fresh) show(player.getServer(), veil);
    }

    /** Ends one reason; when none are left, the stand-in goes and the caster is seen where they really are. */
    public static void end(ServerPlayer player, String reason) {
        Veil veil = VEILS.get(player.getUUID());
        if (veil == null) return;
        boolean wasHiding = veil.hiding();
        if (!veil.reasons.remove(reason)) return;
        if (!veil.reasons.isEmpty()) {
            // Time starts again but stays slowed: the stand-in goes, and from now on they are seen as a blur.
            if (wasHiding && !veil.hiding()) reveal(player, veil);
            return;
        }
        VEILS.remove(player.getUUID());
        TemporalSharing.end(player);
        if (wasHiding) reveal(player, veil);
        else for (ServerPlayer viewer : player.getServer().getPlayerList().getPlayers())
            PacketDistributor.sendToPlayer(viewer, TimeVeilPayload.clear(veil.owner));
        over(player.getServer(), veil);
    }

    /** Drops a veil without any effect (logout, death, a changed dimension). */
    public static void forget(ServerPlayer player) {
        TemporalSharing.end(player);
        Veil veil = VEILS.remove(player.getUUID());
        if (veil == null) return;
        for (ServerPlayer viewer : player.getServer().getPlayerList().getPlayers())
            PacketDistributor.sendToPlayer(viewer, TimeVeilPayload.clear(veil.owner));
        over(player.getServer(), veil);
    }

    // ------------------------------------------------------------------
    // Hints in chat: grey italics, never naming who did it
    // ------------------------------------------------------------------

    private static final int SLOW_LINES = 4, STOP_LINES = 4, AFTER_LINES = 4, SENSE_LINES = 3;

    /**
     * Everyone the field reaches gets a quiet line in chat. Those who can't move in time feel their body lag behind
     * their thoughts (or stop altogether); speedsters and time travellers sense that someone is bending time.
     */
    private static void hint(ServerPlayer caster, Veil veil, boolean stopped) {
        var random = caster.getRandom();
        for (ServerPlayer other : caster.getServer().getPlayerList().getPlayers()) {
            if (other == caster || !reached(caster, other)) continue;
            if (movesInTime(other)) {
                say(other, "time.heroesevolved.veil.sense." + (1 + random.nextInt(SENSE_LINES)));
                continue;
            }
            say(other, "time.heroesevolved.veil." + (stopped ? "stop." + (1 + random.nextInt(STOP_LINES))
                    : "slow." + (1 + random.nextInt(SLOW_LINES))));
            veil.puzzled.add(other.getUUID());
        }
    }

    /** Those who were puzzled get a line once it is over. */
    private static void over(MinecraftServer server, Veil veil) {
        var random = server.overworld().getRandom();
        for (UUID id : veil.puzzled) {
            ServerPlayer other = server.getPlayerList().getPlayer(id);
            if (other != null) say(other, "time.heroesevolved.veil.after." + (1 + random.nextInt(AFTER_LINES)));
        }
        veil.puzzled.clear();
    }

    /** Server-wide, everyone; kept to an area, only those near enough to be caught in it. */
    private static boolean reached(ServerPlayer caster, ServerPlayer other) {
        return serverWide() || other.level() == caster.level() && other.distanceToSqr(caster) <= 64 * 64;
    }

    private static void say(ServerPlayer player, String key) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key)
                .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
    }

    /** Package-visible for tests: who is waiting to be told it is over. */
    static Set<UUID> puzzled(ServerPlayer caster) {
        Veil veil = VEILS.get(caster.getUUID());
        return veil == null ? Set.of() : Set.copyOf(veil.puzzled);
    }

    public static boolean veiled(ServerPlayer player) { return VEILS.containsKey(player.getUUID()); }

    /** True while {@code player} is hidden behind a stand-in (only in stopped time). */
    public static boolean hiding(ServerPlayer player) {
        Veil veil = VEILS.get(player.getUUID());
        return veil != null && veil.hiding();
    }

    /** Whether {@code viewer} sees {@code owner} as they really are while time is slowed or stopped. */
    public static boolean seesThrough(ServerPlayer viewer, UUID owner) {
        return viewer.getUUID().equals(owner) || movesInTime(viewer);
    }

    /** Speedsters and time travellers live in the same fast time, and so does a Mimic channeling either of them. */
    public static boolean movesInTime(ServerPlayer viewer) {
        if (TemporalSharing.shared(viewer)) return true;
        if (SpeedService.awakened(viewer) || SpaceTimeService.awakened(viewer)) return true;
        if (!MimicryService.isMimic(viewer)) return false;
        ResourceLocation channel = MimicryService.memory(viewer).current();
        return ModAbilities.SUPERSPEED_ID.equals(channel) || ModAbilities.TIME_MANIPULATION_ID.equals(channel);
    }

    /**
     * True when an effect drawn at {@code at} belongs to a caster {@code viewer} can't see (it is right by their real,
     * hidden body), so the viewer isn't sent it.
     */
    public static boolean masks(ServerPlayer viewer, Vec3 at) {
        if (VEILS.isEmpty()) return false;
        for (Veil veil : VEILS.values()) {
            if (!veil.hiding() || seesThrough(viewer, veil.owner)) continue;
            ServerPlayer owner = viewer.getServer().getPlayerList().getPlayer(veil.owner);
            if (owner != null && owner.level() == viewer.level() && owner.position().distanceToSqr(at) < MASK * MASK) return true;
        }
        return false;
    }

    /** Sends a field's own visual (a bubble, a dome) only to those who can see its caster: everyone, unless time is stopped. */
    public static void showToSeers(ServerPlayer owner, AbilityEffectPayload.Kind kind, Vec3 origin, Vec3 target, double scale) {
        var payload = new AbilityEffectPayload(kind, origin, target, (float) Math.clamp(scale, 0.1D, 64.0D));
        boolean hidden = hiding(owner);
        for (ServerPlayer viewer : owner.serverLevel().players())
            if ((!hidden || seesThrough(viewer, owner.getUUID())) && viewer.distanceToSqr(origin) < 128 * 128) PacketDistributor.sendToPlayer(viewer, payload);
    }

    public static void tick(MinecraftServer server) {
        if (VEILS.isEmpty() || server.overworld().getGameTime() % 10 != 0) return;
        boolean slowed = false, stopped = false;
        for (Veil veil : List.copyOf(VEILS.values())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(veil.owner);
            if (owner == null || !owner.isAlive() || owner.level().dimension() != veil.dimension) {
                // Gone, dead or through a portal: the trick can't hold, so nobody is left looking at an empty statue.
                if (owner != null) forget(owner);
                else { VEILS.remove(veil.owner); over(server, veil); }
                continue;
            }
            // Re-sent, so a player who logs in, comes into range or loses their own speed sees the right thing.
            if (veil.hiding()) show(server, veil);
            slowed |= !veil.reasons.contains(STOP) || veil.reasons.size() > 1;
            stopped |= veil.reasons.contains(STOP);
        }
        if (!slowed && !stopped || !serverWide()) return;
        // Everyone can tell: a faint amber cast while time is slowed, a drained cold one while it is stopped.
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            ScreenTintPayload.send(player, stopped ? 0x40506A90 : 0x1CFF9A30, 14, ScreenTintPayload.Style.HOLD);
    }

    private static void show(MinecraftServer server, Veil veil) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) show(viewer, veil);
    }

    /** A touch changes perception immediately, without waiting for the ten-tick periodic refresh. */
    public static void refresh(ServerPlayer viewer) {
        for (Veil veil : VEILS.values()) show(viewer, veil);
    }

    private static void show(ServerPlayer viewer, Veil veil) {
        boolean own = viewer.getUUID().equals(veil.owner);
        // The caster sees a faint copy of their own stand-in, so they can find their spot again. Slowed time has none.
        if (veil.hiding() && (own || !seesThrough(viewer, veil.owner)))
            PacketDistributor.sendToPlayer(viewer, TimeVeilPayload.show(veil.owner, veil.dimension.location(), veil.at, veil.yaw, veil.pitch, veil.style));
        else PacketDistributor.sendToPlayer(viewer, TimeVeilPayload.clear(veil.owner));
    }

    private static void reveal(ServerPlayer player, Veil veil) {
        boolean home = player.level().dimension() == veil.dimension && player.position().distanceTo(veil.at) <= HOME;
        Vec3 now = player.position();
        for (ServerPlayer viewer : player.getServer().getPlayerList().getPlayers())
            PacketDistributor.sendToPlayer(viewer, home ? TimeVeilPayload.clear(veil.owner)
                    : TimeVeilPayload.vanish(veil.owner, veil.at, now, veil.style));
        if (home) return;
        // Back on the spot, nobody notices a thing. Anywhere else, the stand-in is gone in an instant.
        ServerLevel spotLevel = player.getServer().getLevel(veil.dimension);
        if (spotLevel == null) return;
        if (veil.style == Style.SPEED) {
            spotLevel.playSound(null, veil.at.x, veil.at.y, veil.at.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.9F, 1.3F);
            spotLevel.sendParticles(ParticleTypes.CLOUD, veil.at.x, veil.at.y + 1, veil.at.z, 14, 0.35, 0.6, 0.35, 0.08);
            spotLevel.sendParticles(ParticleTypes.GUST, veil.at.x, veil.at.y + 1, veil.at.z, 1, 0, 0, 0, 0);
            if (spotLevel == player.serverLevel())
                AbilityEffects.send(spotLevel, AbilityEffectPayload.Kind.SPEED_TRAIL, veil.at.add(0, 1, 0), now.add(0, 1, 0), 1);
        } else {
            spotLevel.playSound(null, veil.at.x, veil.at.y, veil.at.z, ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
            AbilityEffects.send(spotLevel, AbilityEffectPayload.Kind.TEMPORAL_BURST, veil.at, veil.at.add(0, 1, 0), 1.2);
            AbilityEffects.at(player, AbilityEffectPayload.Kind.TEMPORAL_BURST, 1.2);
        }
    }

    public static void clear() { VEILS.clear(); SHADOWS.clear(); TemporalSharing.clear(); TemporalVoiceState.clear(); }
}
