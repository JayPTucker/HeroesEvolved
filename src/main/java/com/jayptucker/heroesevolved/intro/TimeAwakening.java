package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.events.AwakeningService;
import com.jayptucker.heroesevolved.intro.Danger.Peril;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A dormant traveller's first stop. Someone within twenty blocks is under two hearts, or a creeper is about to
 * kill someone, and time simply stops: everything round the one in danger hangs where it is, fuses included,
 * and only the traveller can move. When it starts again the creeper's fuse has run backwards, monsters have lost
 * the thread, and the people it was for get their second wind. Then the Odessa vision follows, as it always has.
 */
public final class TimeAwakening {
    /** How long the first stop lasts: long enough to pull someone clear, whatever the traveller's level. */
    public static final int FREEZE_TICKS = 20 * 8;

    private record Stop(ServerPlayer owner, LivingEntity ward, Peril peril, long until) {}

    /** Owners whose awakening is being decided right now: the vision must wait for the stop. */
    private static final Set<UUID> ARMED = new HashSet<>();
    private static final Map<UUID, Stop> STOPS = new HashMap<>();
    /** Travellers who died while time was stopped: their vision starts when they respawn. */
    private static final Set<UUID> VISION_AFTER_DEATH = new HashSet<>();

    private TimeAwakening() {}

    /** Called before the unlock, so the unlock's vision is held back. */
    public static void arm(ServerPlayer owner) { ARMED.add(owner.getUUID()); }

    public static void disarm(ServerPlayer owner) { ARMED.remove(owner.getUUID()); }

    /** True while the first stop is about to start or running: the vision and the old reflexive slow wait. */
    public static boolean claims(ServerPlayer owner) {
        return ARMED.contains(owner.getUUID()) || STOPS.containsKey(owner.getUUID());
    }

    public static boolean stopping(ServerPlayer owner) { return STOPS.containsKey(owner.getUUID()); }

    public static void begin(ServerPlayer owner, LivingEntity ward, Peril peril) {
        ARMED.remove(owner.getUUID());
        Vec3 center = ward.position();
        TimeStopService.awaken(owner, center, FREEZE_TICKS);
        STOPS.put(owner.getUUID(), new Stop(owner, ward, peril, owner.serverLevel().getGameTime() + FREEZE_TICKS));
        // Nothing builds up. It just happens: a jolt, every sound cut off, the colour drained out of the world.
        ScreenTintPayload.send(owner, 0x8890A8D0, 16, ScreenTintPayload.Style.FLASH);
        CameraShakePayload.sendAround(owner.serverLevel(), owner.position(), 8, 0.8F, 10);
        owner.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1, 0.5F);
        AbilityEffects.send(owner.serverLevel(), Kind.PROPHECY_THREAD, owner.getEyePosition(), ward.getEyePosition(), 1.5);
        owner.sendSystemMessage(Component.translatable("awakening.heroesevolved.time_stop")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
    }

    static void tick(MinecraftServer server) {
        // A traveller who died during the stop sees the vision once they are back on their feet.
        for (UUID id : List.copyOf(VISION_AFTER_DEATH)) {
            ServerPlayer back = server.getPlayerList().getPlayer(id);
            if (back != null && back.isAlive()) {
                VISION_AFTER_DEATH.remove(id);
                TimeIntroService.acquired(back, ModAbilities.TIME_MANIPULATION_ID);
            }
        }
        for (Stop stop : List.copyOf(STOPS.values())) {
            ServerPlayer owner = stop.owner;
            if (owner.getServer() != server) continue;
            if (owner.hasDisconnected()) { forget(owner); continue; }
            if (owner.isRemoved() || !owner.isAlive()) {
                forget(owner);
                VISION_AFTER_DEATH.add(owner.getUUID());
                continue;
            }
            long now = owner.serverLevel().getGameTime();
            // The stop is over when its time runs out, or if anything ended the field early.
            if (now < stop.until && TimeStopService.hasActiveField(owner)) {
                // The traveller is the one thing still moving, so a traveller who was falling hangs where time stopped.
                if (stop.peril == Peril.FALL && stop.ward == owner && !owner.onGround()) {
                    Vec3 motion = owner.getDeltaMovement();
                    Danger.push(owner, new Vec3(motion.x * 0.5, Math.max(motion.y * 0.3, -0.03), motion.z * 0.5));
                    owner.resetFallDistance();
                }
                if ((stop.until - now) % 20 == 0) owner.playNotifySound(SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.PLAYERS, 0.6F, 0.5F);
                continue;
            }
            end(stop);
        }
    }

    private static void end(Stop stop) {
        ServerPlayer owner = stop.owner;
        STOPS.remove(owner.getUUID());
        TimeStopService.stop(owner);
        boolean ward = stop.ward != owner && stop.ward.isAlive() && stop.ward.level() == owner.level();
        for (LivingEntity saved : ward ? List.of(owner, stop.ward) : List.of(owner)) {
            AwakeningService.secondWind(saved);
            if (stop.peril == Peril.FIRE) saved.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100));
            if (stop.peril == Peril.DROWNING) saved.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200));
            // Time runs on, but not quite where it left off: the fuse is back at the start.
            for (Creeper creeper : saved.level().getEntitiesOfClass(Creeper.class, saved.getBoundingBox().inflate(12))) {
                Danger.rewind(creeper);
                AbilityEffects.send(owner.serverLevel(), Kind.TEMPORAL_FIELD, creeper.position(), creeper.getEyePosition(), 1.5);
            }
            for (PrimedTnt tnt : saved.level().getEntitiesOfClass(PrimedTnt.class, saved.getBoundingBox().inflate(10)))
                tnt.setFuse(80);
            // Whatever was on them falls a heartbeat behind.
            for (Mob mob : saved.level().getEntitiesOfClass(Mob.class, saved.getBoundingBox().inflate(12),
                    mob -> mob.isAlive() && (mob instanceof Enemy || mob.getTarget() == saved))) {
                mob.setTarget(null);
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
            }
        }
        // Then the city: the vision this power always opens with.
        TimeIntroService.acquired(owner, ModAbilities.TIME_MANIPULATION_ID);
    }

    /** Logout, death or a test's cleanup: the stop ends, and the vision waits for the next login. */
    public static void forget(ServerPlayer owner) {
        ARMED.remove(owner.getUUID());
        if (STOPS.remove(owner.getUUID()) != null) TimeStopService.stop(owner);
    }

    static void clear() {
        ARMED.clear();
        STOPS.clear();
        VISION_AFTER_DEATH.clear();
    }
}
