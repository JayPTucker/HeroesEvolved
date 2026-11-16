package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The power isn't finished when the intro's burst is over. For about four seconds after a fight awakening it keeps
 * fighting through its owner, who only watches: the body turns to each attacker in turn and the power's own moves come
 * out at them. A power with a fighting style lunges in and throws its real combo (firebending's jabs and kicks, a
 * boxer's sparks, waterbending's whips); the rest fire their own kit, one move per attacker (Matter
 * Assembly's lances and a cage, Sonic Resonance's cries). Then it lets go, and the owner has their body back.
 */
public final class Surge {
    /** Powers that fight through a fighting style. Ranged styles strike from further off. */
    private static final Set<String> RANGED = Set.of("pyrokinesis", "electrokinesis", "hydrokinesis", "telekinesis");
    private static final Set<String> STYLED = Set.of("pyrokinesis", "electrokinesis", "hydrokinesis", "quake", "cryokinesis",
            "phasing", "invisibility", "telekinesis");
    /** The kit moves each other power throws, in order, one at each attacker in turn. */
    private static final Map<String, List<AbilitySlot>> KITS = Map.of(
            "sonic_resonance", List.of(AbilitySlot.PRIMARY, AbilitySlot.PRIMARY, AbilitySlot.PRIMARY, AbilitySlot.PRIMARY),
            "telepathy", List.of(AbilitySlot.QUINARY, AbilitySlot.SECONDARY, AbilitySlot.SECONDARY),
            "matter_assembly", List.of(AbilitySlot.SECONDARY, AbilitySlot.SECONDARY, AbilitySlot.SECONDARY, AbilitySlot.QUATERNARY),
            "technopathy", List.of(AbilitySlot.QUATERNARY, AbilitySlot.TERTIARY),
            "shapeshifting", List.of(AbilitySlot.TERTIARY, AbilitySlot.QUATERNARY));

    private static final int STYLE_TICKS = 80, KIT_STEP = 14, SWITCH_EVERY = 3;
    private static final double SEEK = 24;

    private static final class Run {
        final ServerPlayer owner;
        final LivingEntity ward;
        final ResourceLocation power;
        final String path;
        int age, thrown, step;
        LivingEntity target;
        Run(ServerPlayer owner, LivingEntity ward, ResourceLocation power) {
            this.owner = owner; this.ward = ward; this.power = power; this.path = power.getPath();
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    private Surge() {}

    /** Whether this power keeps fighting after its intro. */
    static boolean has(String power) { return STYLED.contains(power) || KITS.containsKey(power); }

    public static boolean active(ServerPlayer owner) { return RUNS.containsKey(owner.getUUID()); }

    static void begin(ServerPlayer owner, LivingEntity ward, ResourceLocation power) {
        Run run = new Run(owner, ward, power);
        if (pick(run) == null) return;
        RUNS.put(owner.getUUID(), run);
        owner.displayClientMessage(Component.translatable("awakening.heroesevolved.surge")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
        ScreenTintPayload.send(owner, 0x60B00000, 20, ScreenTintPayload.Style.VIGNETTE);
        owner.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1, 1.6F);
    }

    static void tick(MinecraftServer server) {
        for (Run run : List.copyOf(RUNS.values())) {
            ServerPlayer owner = run.owner;
            if (owner.getServer() != server) continue;
            if (owner.isRemoved() || !owner.isAlive() || owner.hasDisconnected() || !FightingStyles.hasPower(owner, run.power)) {
                RUNS.remove(owner.getUUID());
                continue;
            }
            boolean going = STYLED.contains(run.path) ? fight(run) : kit(run);
            if (!going || ++run.age > limit(run)) end(run);
        }
    }

    private static int limit(Run run) {
        return STYLED.contains(run.path) ? STYLE_TICKS : KITS.get(run.path).size() * KIT_STEP + 6;
    }

    // ------------------------------------------------------------------
    // A fighting style: lunge in, throw the combo, turn to the next one
    // ------------------------------------------------------------------

    private static boolean fight(Run run) {
        ServerPlayer owner = run.owner;
        FightingStyles.Style style = FightingStyles.of(owner);
        LivingEntity target = target(run);
        if (style == null || target == null) return false;
        face(owner, target);
        PlayerEnergyService.restore(owner, 999);
        double reach = RANGED.contains(run.path) ? 6 : 2.6;
        Vec3 to = target.position().subtract(owner.position()).multiply(1, 0, 1);
        if (to.length() > reach) {
            // The body throws itself at them.
            Vec3 lunge = to.normalize().scale(Math.min(0.9, to.length() * 0.35));
            Danger.push(owner, new Vec3(lunge.x, owner.onGround() ? 0.12 : owner.getDeltaMovement().y, lunge.z));
            return true;
        }
        if (style.strike(owner) > 0 && ++run.thrown % SWITCH_EVERY == 0) run.target = next(run);
        return true;
    }

    // ------------------------------------------------------------------
    // Everyone else: their own kit, one move at each attacker
    // ------------------------------------------------------------------

    private static boolean kit(Run run) {
        ServerPlayer owner = run.owner;
        List<AbilitySlot> moves = KITS.get(run.path);
        if (run.step >= moves.size()) return false;
        LivingEntity target = target(run);
        if (target == null) return false;
        face(owner, target);
        int beat = run.age % KIT_STEP;
        AbilityUseContext context = new AbilityUseContext(owner, run.power, 5, false);
        if (run.path.equals("sonic_resonance")) {
            // A tap of the Cry: a short, aimed scream at whoever is in front.
            if (beat == 0) com.jayptucker.heroesevolved.sonic.SonicService.press(owner, 5);
            if (beat == 3) {
                com.jayptucker.heroesevolved.sonic.SonicService.release(owner);
                advance(run);
            }
            return true;
        }
        if (beat != 0) return true;
        AbilityAction action = AbilityRegistry.ABILITIES.get(run.power).action(moves.get(run.step)).orElse(null);
        if (action != null) {
            PlayerEnergyService.restore(owner, 999);
            try {
                if (action.canUse(context)) action.activate(context);
            } catch (RuntimeException failure) {
                com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.warn("Awakening surge move failed for {}", run.path, failure);
            }
        }
        advance(run);
        return true;
    }

    private static void advance(Run run) {
        run.step++;
        run.target = next(run);
    }

    // ------------------------------------------------------------------

    private static List<LivingEntity> threats(Run run) {
        LivingEntity around = run.ward.isAlive() && run.ward.level() == run.owner.level() ? run.ward : run.owner;
        List<LivingEntity> found = new java.util.ArrayList<>(Danger.threats(run.owner, around, SEEK));
        if (around != run.owner) for (LivingEntity near : Danger.threats(run.owner, run.owner, SEEK))
            if (!found.contains(near)) found.add(near);
        found.removeIf(threat -> threat.distanceToSqr(run.owner) > SEEK * SEEK);
        found.sort(Comparator.comparingDouble(threat -> threat.distanceToSqr(run.owner)));
        return found;
    }

    private static LivingEntity pick(Run run) {
        List<LivingEntity> threats = threats(run);
        run.target = threats.isEmpty() ? null : threats.getFirst();
        return run.target;
    }

    private static LivingEntity target(Run run) {
        if (run.target == null || !run.target.isAlive() || run.target.distanceToSqr(run.owner) > SEEK * SEEK) pick(run);
        return run.target;
    }

    /** The nearest attacker that isn't the one just dealt with (or the same one if it's the only one left). */
    private static LivingEntity next(Run run) {
        List<LivingEntity> threats = threats(run);
        for (LivingEntity threat : threats) if (threat != run.target) return threat;
        return threats.isEmpty() ? null : threats.getFirst();
    }

    /**
     * Turns the whole body to face {@code target}, without moving it (the client's own position is kept). The client is
     * only told when the turn is big, or now and then: a turn packet every tick would hold up its movement.
     */
    private static void face(ServerPlayer owner, LivingEntity target) {
        Vec3 look = target.getBoundingBox().getCenter().subtract(owner.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.multiply(1, 0, 1).length()));
        boolean turn = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - owner.getYRot())) > 12
                || Math.abs(pitch - owner.getXRot()) > 12 || owner.tickCount % 6 == 0;
        owner.setYRot(yaw);
        owner.setXRot(pitch);
        owner.setYHeadRot(yaw);
        owner.yBodyRot = yaw;
        if (turn && owner.connection != null && !owner.isFakePlayer())
            // The position is absolute on the server and only sent as relative (a zero change), so the client keeps its own.
            owner.connection.teleport(owner.getX(), owner.getY(), owner.getZ(), yaw, pitch,
                    Set.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z));
    }

    private static void end(Run run) {
        RUNS.remove(run.owner.getUUID());
        run.owner.displayClientMessage(Component.translatable("awakening.heroesevolved.surge_end")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        run.owner.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.5F, 0.7F);
    }

    static void forget(ServerPlayer owner) { RUNS.remove(owner.getUUID()); }

    static void clear() { RUNS.clear(); }
}
