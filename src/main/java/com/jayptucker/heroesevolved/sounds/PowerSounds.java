package com.jayptucker.heroesevolved.sounds;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActionDefinition;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Every power sounds like itself. Each power has a palette of synthesized cues ({@link PowerCue}); this class plays
 * the right one whenever any of its actions is used, so every core move, follow-up, skill-tree active and class
 * ability has a sound, including the many that had none:
 *
 * <ul>
 *   <li><b>Presses</b> ({@link #used}) play the action's cue: {@code HEAVY} for ultimates and signature moves (a
 *       class's Ultimate plays its own as it opens), {@code CHARGE} for wind-ups a release finishes, {@code CAST} for the rest.</li>
 *   <li><b>Follow-ups</b> play {@code IMPACT}, <b>dismissals</b> {@code END}, and a <b>release</b> that finishes a
 *       charge plays the move itself.</li>
 *   <li><b>Blows</b> a power lands ({@link #damaged}) play its {@code IMPACT} where they land; fighting-style
 *       strikes and dodges play {@code STRIKE} and {@code DODGE}.</li>
 * </ul>
 *
 * <p>Most powers layer their cue over whatever vanilla sounds the move already makes. Space-Time, Sonic Resonance and
 * Vibe already have hand-made sounds for their moves, so their palette only fills silence: {@link ServerSoundCountMixin}
 * counts every sound the server plays, and a cue is added only when the action played none.</p>
 *
 * @see com.jayptucker.heroesevolved.mixin.ServerSoundCountMixin
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PowerSounds {
    public enum Phase { PRESS, FOLLOW_UP, DISMISS, RELEASE }

    /** Powers whose own sounds are already good: their cues only fill actions that make no sound at all. */
    private static final Set<String> GAPS_ONLY = Set.of("time_manipulation", "sonic_resonance", "vibe");

    /** Presses that start a wind-up; the release that follows plays the move. */
    private static final Set<String> CHARGES = Set.of(
            "pyro_flare", "pyro_inferno", "flight_launch", "radiation_exploding_man", "speed_cyclone",
            "sonic_crescendo",
            // Power Amplification: Supercharge and Red Lightning both build while held and land on the release.
            "amp_supercharge", "amp_red_lightning");

    /** Presses whose cue is set by hand rather than by tier or node kind. */
    private static final Map<String, PowerCue> PRESS = Map.of(
            // Second Life's key only reports readiness; the revival has its own sounds.
            "regeneration_second_life", PowerCue.CAST,
            "speed_lightspeed", PowerCue.HEAVY,
            "quake_earthquake", PowerCue.HEAVY,
            "flight_dive_bomb", PowerCue.HEAVY);

    /** Follow-ups that end something rather than strike. */
    private static final Map<String, PowerCue> FOLLOW_UP = Map.of(
            "regeneration_2_1", PowerCue.END,
            "cryokinesis_stillheart", PowerCue.END);

    /** Stealthy moves: only the one who used them hears the cue. */
    private static final Set<String> PRIVATE = Set.of(
            "invisibility_fade", "invisibility_0_1", "invisibility_smoke_and_mirrors",
            "telepathy_read_mind", "telepathy_suggestion", "telepathy_mind_link", "telepathy_1_1",
            "telepathy_memory_walk", "vibe_read");

    /** How long a key must be held before its release counts as letting go of a held effect. */
    private static final int HELD = 10;
    /** One impact cue per target this often, and one per attacker per this many ticks (an area blow plays once). */
    private static final int TARGET_GAP = 10;
    private static final int ATTACKER_GAP = 3;
    /** Blows below this are damage over time (a dose, a burn), which shouldn't ping every second. */
    private static final float IMPACT_FLOOR = 1.5F;

    private static int heard;
    private static final Map<UUID, Long> PRESSED_AT = new HashMap<>();
    private static final Map<UUID, Long> SWUNG_AT = new HashMap<>();
    private static final Map<Integer, Long> STRUCK_AT = new HashMap<>();
    private static final Map<UUID, Long> STRUCK_BY = new HashMap<>();

    private PowerSounds() {
    }

    /** Called for every sound a server world plays. */
    public static void heard() {
        heard++;
    }

    /** How many sounds the server has played so far: take this before an action, compare after. */
    public static int mark() {
        return heard;
    }

    /**
     * An action ran: plays the power's cue for it. {@code mark} is {@link #mark()} taken just before it ran, so an
     * action that already made a sound can be told from one that made none.
     */
    public static void used(ServerPlayer player, ResourceLocation power, AbilityAction action, Phase phase, int mark) {
        String powerId = power.getPath();
        String path = action.definition().id().getPath();
        int unlock = action.definition().unlockLevel();
        boolean silent = heard == mark;
        if (powerId.equals("empathic_mimicry") && path.startsWith("mimicry_echo_")) {
            // An Echo sounds like the power it borrows, under a faint chord of the empath's own.
            AbilityActionDefinition copied = MimicryService.shown(player, action);
            ResourceLocation channel = MimicryService.memory(player).current();
            if (copied != null && channel != null && !channel.getPath().equals(powerId)) {
                play(player, powerId, PowerCue.CAST, 0.35F, false);
                powerId = channel.getPath();
                path = copied.id().getPath();
                unlock = copied.unlockLevel();
            }
        }
        if (GAPS_ONLY.contains(powerId) && !silent) return;
        if (path.equals("quake_seamfinder") && SkillTreeService.has(player, "quake_surgical_silence")) return;
        long now = player.serverLevel().getGameTime();
        PowerCue cue = switch (phase) {
            case PRESS -> {
                PRESSED_AT.put(player.getUUID(), now);
                yield press(powerId, path, unlock);
            }
            case FOLLOW_UP -> FOLLOW_UP.getOrDefault(path, PowerCue.IMPACT);
            case DISMISS -> PowerCue.END;
            case RELEASE -> {
                // Only a release that did something (it made a sound) is worth a cue; a tap's release is a no-op.
                if (silent || GAPS_ONLY.contains(powerId)) yield null;
                if (CHARGES.contains(path)) yield unlock >= 5 || PRESS.get(path) == PowerCue.HEAVY ? PowerCue.HEAVY : PowerCue.CAST;
                Long pressed = PRESSED_AT.get(player.getUUID());
                yield pressed != null && now - pressed >= HELD ? PowerCue.END : null;
            }
        };
        if (cue != null) play(player, powerId, cue, 1.0F, PRIVATE.contains(path));
    }

    private static PowerCue press(String power, String path, int unlock) {
        if (CHARGES.contains(path)) return PowerCue.CHARGE;
        PowerCue fixed = PRESS.get(path);
        if (fixed != null) return fixed;
        var node = SkillCatalog.find(power, path);
        // A class's Ultimate plays its own heavy cue as it opens (combat/Ultimates), so the press makes none.
        if (node.isPresent() && node.get().style()) return null;
        if (node.isPresent()) return node.get().signature() ? PowerCue.HEAVY : PowerCue.CAST;
        return unlock >= 5 ? PowerCue.HEAVY : PowerCue.CAST;
    }

    /** A fighting-style strike was thrown. */
    public static void strike(ServerPlayer player, ResourceLocation power) {
        play(player, power.getPath(), PowerCue.STRIKE, 1.0F, false);
    }

    /** A fighting-style dodge was made. */
    public static void dodge(ServerPlayer player, ResourceLocation power) {
        play(player, power.getPath(), PowerCue.DODGE, 1.0F, false);
    }

    /** Plays one of a power's cues at a player (or, when {@code alone}, for that player only). */
    public static void play(ServerPlayer player, String power, PowerCue cue, float scale, boolean alone) {
        if (alone) {
            Supplier<SoundEvent> sound = ModSounds.POWER_CUES.get(PowerCue.event(power, cue));
            if (sound == null) return;
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.get()),
                    SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), cue.volume * scale, pitch(player),
                    player.getRandom().nextLong()));
            return;
        }
        play(player.serverLevel(), player.position(), power, cue, scale);
    }

    /** Plays one of a power's cues at a point, for everyone near it. */
    public static void play(ServerLevel level, Vec3 at, String power, PowerCue cue, float scale) {
        Supplier<SoundEvent> sound = ModSounds.POWER_CUES.get(PowerCue.event(power, cue));
        if (sound == null) return;
        level.playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, cue.volume * scale,
                0.94F + level.getRandom().nextFloat() * 0.12F);
    }

    private static float pitch(ServerPlayer player) {
        return 0.94F + player.getRandom().nextFloat() * 0.12F;
    }

    /** A plain swing with a held item or a bare fist: its blow is a punch, not the power's. */
    @SubscribeEvent
    public static void swung(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SWUNG_AT.put(player.getUUID(), player.serverLevel().getGameTime());
    }

    /** A blow a power landed: its impact cue, where it landed. */
    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || target == player) return;
        if (event.getNewDamage() < IMPACT_FLOOR || !(target.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (source.is(DamageTypes.PLAYER_ATTACK) && SWUNG_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) == now) return;
        Entity direct = source.getDirectEntity();
        // Arrows, tridents and guns are not the power's blow; the mod's own constructs and shards are.
        if (direct != null && direct != player
                && !BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace().equals(HeroesEvolved.MOD_ID)) return;
        String power = powerOf(player);
        if (power == null || GAPS_ONLY.contains(power) || !PowerCue.has(power, PowerCue.IMPACT)) return;
        if (now - STRUCK_AT.getOrDefault(target.getId(), Long.MIN_VALUE / 2) < TARGET_GAP) return;
        if (now - STRUCK_BY.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < ATTACKER_GAP) return;
        STRUCK_AT.put(target.getId(), now);
        STRUCK_BY.put(player.getUUID(), now);
        if (STRUCK_AT.size() > 512) STRUCK_AT.values().removeIf(at -> now - at > TARGET_GAP);
        play(level, target.position().add(0, target.getBbHeight() * 0.5, 0), power, PowerCue.IMPACT, 1.0F);
    }

    /** The power whose sound a player's blows make: their own, or the one an empath is channeling. */
    private static String powerOf(ServerPlayer player) {
        var assigned = PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getValue().isUnlocked()).map(entry -> entry.getKey().getPath()).orElse(null);
        if ("empathic_mimicry".equals(assigned)) {
            ResourceLocation channel = MimicryService.memory(player).current();
            if (channel != null) return channel.getPath();
        }
        return assigned;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        PRESSED_AT.remove(id);
        SWUNG_AT.remove(id);
        STRUCK_BY.remove(id);
    }
}
