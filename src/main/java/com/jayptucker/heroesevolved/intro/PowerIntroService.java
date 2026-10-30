package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.cryo.ColdDomeService;
import com.jayptucker.heroesevolved.cryo.TemporaryBlocks;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.telepathy.TelepathyService;
import com.jayptucker.heroesevolved.entity.QuakeDebrisEntity;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.PowerIntroPayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Short, server-authoritative first-acquisition sequences. Time retains its city cinematic. */
public final class PowerIntroService {
    private record Spec(int chargeTicks, int releaseTicks, int color, ArmPose pose, Kind charge, Kind release) {}
    private static final Map<String, Spec> SPECS = Map.ofEntries(
            Map.entry("quake", new Spec(60, 40, 0xB1E9FA, ArmPose.TWO_HANDS_STRAIN, Kind.QUAKE_AWAKEN_CHARGE, Kind.QUAKE_AWAKEN_RELEASE)),
            Map.entry("flight", new Spec(30, 60, 0xD7F1FF, ArmPose.GROUND, Kind.FLIGHT_AWAKEN_CHARGE, Kind.FLIGHT_AWAKEN_RELEASE)),
            Map.entry("electrokinesis", new Spec(50, 40, 0x60B5FF, ArmPose.ARMS_WIDE, Kind.ELECTRIC_AWAKEN_CHARGE, Kind.ELECTRIC_AWAKEN_RELEASE)),
            Map.entry("telekinesis", new Spec(40, 50, 0xBC86F5, ArmPose.TEMPLE, Kind.PSYCHIC_AWAKEN_CHARGE, Kind.PSYCHIC_AWAKEN_RELEASE)),
            // Telepathy shares the psychic visuals, tinted toward a softer violet.
            Map.entry("telepathy", new Spec(60, 50, 0xA070FF, ArmPose.TEMPLE, Kind.PSYCHIC_AWAKEN_CHARGE, Kind.PSYCHIC_AWAKEN_RELEASE)),
            Map.entry("cryokinesis", new Spec(60, 50, 0xBFEFFF, ArmPose.TWO_HANDS_STRAIN, Kind.CRYO_AWAKEN_CHARGE, Kind.CRYO_AWAKEN_RELEASE)),
            Map.entry("radiation", new Spec(600, 50, 0xFF781C, ArmPose.ARMS_WIDE, Kind.NUCLEAR_CHARGE, Kind.NUCLEAR_SHOCKWAVE)),
            Map.entry("sonic_resonance", new Spec(45, 35, 0xE5CB78, ArmPose.ARMS_WIDE, Kind.SONIC_SENSE, Kind.SONIC_BURST)),
            Map.entry("superspeed", new Spec(40, 40, 0xFFBE45, ArmPose.ARMS_WIDE, Kind.SPEED_BURST, Kind.SPEED_VORTEX)),
            Map.entry("regeneration", new Spec(50, 40, 0xFFD89B, ArmPose.ARMS_WIDE, Kind.REGEN_AWAKEN_CHARGE, Kind.REGEN_AWAKEN_RELEASE)),
            Map.entry("pyrokinesis", new Spec(50, 40, 0xFF8A2A, ArmPose.TWO_HANDS_STRAIN, Kind.PYRO_AWAKEN_CHARGE, Kind.PYRO_AWAKEN_RELEASE)),
            Map.entry("invisibility", new Spec(40, 40, 0xCFD8E0, ArmPose.ARMS_WIDE, Kind.MIND_PULSE, Kind.PSYCHIC_FIELD)),
            Map.entry("phasing", new Spec(40, 40, 0x9FE3D6, ArmPose.TWO_HANDS, Kind.TEMPORAL_FIELD, Kind.TEMPORAL_BURST)),
            Map.entry("empathic_mimicry", new Spec(50, 40, 0xE8C27A, ArmPose.TEMPLE, Kind.PROPHECY_THREAD, Kind.PSYCHIC_AWAKEN_RELEASE)));
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> SOFT_LANDINGS = new HashMap<>();

    private static final class Session {
        final ServerPlayer player;
        final ResourceLocation power;
        final Spec spec;
        ResourceKey<Level> dimension;
        final List<Entity> lifted = new ArrayList<>();
        Vec3 releaseOrigin;
        QuakeIntroBlast blast;
        int age;
        Session(ServerPlayer player, ResourceLocation power, Spec spec) {
            this.player = player; this.power = power; this.spec = spec;
            dimension = player.level().dimension();
        }
    }

    private PowerIntroService() {}

    public static void acquired(ServerPlayer player, ResourceLocation power) {
        if (power.getPath().equals("precognition")) { com.jayptucker.heroesevolved.precog.PrecognitionService.awaken(player); return; }
        Session current = SESSIONS.get(player.getUUID());
        if (current != null && !current.power.equals(power)) cancel(player);
        if (power.equals(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_manipulation"))) {
            TimeIntroService.acquired(player, power);
            return;
        }
        if (!power.getNamespace().equals(HeroesEvolved.MOD_ID) || !SPECS.containsKey(power.getPath())
                || player.getData(ModDataAttachments.POWER_INTRO_HISTORY).seen().contains(power)) return;
        SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session(player, power, SPECS.get(power.getPath())));
    }

    private static boolean matches(Session session) {
        return PlayerAbilityService.getData(session.player).ability(session.power)
                .filter(progress -> progress.isUnlocked()).isPresent();
    }

    public static boolean isActive(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && matches(session) && !TimeIntroService.isVisiting(player)
                && !TimeIntroService.isCity(player.level()) && !EclipseService.arePowersSuppressed(player);
    }

    public static int radiationChargeRemaining(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.power.getPath().equals("radiation") && isActive(player)
                ? Math.max(0, session.spec.chargeTicks - session.age) : 0;
    }

    /** Consumed once, and expires even if the owner never takes a fall. */
    public static boolean cushionFall(ServerPlayer player) {
        return SOFT_LANDINGS.remove(player.getUUID()) != null;
    }

    public static void tick(MinecraftServer server) {
        SOFT_LANDINGS.replaceAll((id, ticks) -> ticks - 1);
        SOFT_LANDINGS.values().removeIf(ticks -> ticks <= 0);
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (session.player.getServer() != server) continue;
            tick(session);
        }
    }

    private static void tick(Session session) {
        ServerPlayer player = session.player;
        if (!player.isAlive() || player.isRemoved() || player.hasDisconnected() || !matches(session)
                || session.age > 0 && !player.level().dimension().equals(session.dimension)) { cancel(player); return; }
        if (TimeIntroService.isVisiting(player) || TimeIntroService.isCity(player.level())
                || EclipseService.arePowersSuppressed(player)) {
            if (session.age > 0) cancel(player);
            return;
        }
        Spec spec = session.spec;
        int age = ++session.age;
        if (age == 1) begin(session);
        if (age < spec.chargeTicks) {
            if (age % 4 == 0) {
                AbilityEffects.send(player.serverLevel(), spec.charge, player.position(), player.getEyePosition(),
                        age / (double) spec.chargeTicks);
            }
            if (age % 10 == 0) ArmPosePayload.show(player, spec.pose, 14);
            Vec3 motion = player.getDeltaMovement();
            velocity(player, new Vec3(motion.x * 0.45, motion.y, motion.z * 0.45));
            if (session.power.getPath().equals("quake") && age % 12 == 0)
                CameraShakePayload.sendAround(player.serverLevel(), player.position(), 18, age / 150.0F, 12);
            if (session.power.getPath().equals("regeneration") && age % 6 == 0) player.heal(0.75F);
            if (session.power.getPath().equals("telepathy")) chargeTelepathy(session, age);
            if (session.power.getPath().equals("cryokinesis")) chargeCryokinesis(session, age);
            if (session.power.getPath().equals("radiation")) chargeRadiation(session, age);
        }
        if (age == spec.chargeTicks) release(session);
        if (age >= spec.chargeTicks) aftermath(session, age - spec.chargeTicks);
        if (age >= spec.chargeTicks + spec.releaseTicks && (session.blast == null
                || session.blast.finished())) finish(session);
    }

    private static void begin(Session session) {
        ServerPlayer player = session.player;
        session.dimension = player.level().dimension();
        player.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                player.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(session.power));
        // Persist before the first effect: reconnects cannot repeatedly trigger a terrain blast or free healing.
        if (!player.isFakePlayer()) ((com.jayptucker.heroesevolved.mixin.PlayerListSaveAccess)
                player.getServer().getPlayerList()).heroesEvolved$savePlayer(player);
        caption(session, 0);
        player.playNotifySound(ModSounds.NEW_BEGINNING.get(), SoundSource.MUSIC, 0.35F, 1);
        ScreenTintPayload.send(player, 0x24000000 | session.spec.color, session.spec.chargeTicks,
                ScreenTintPayload.Style.PULSE);
        ArmPosePayload.show(player, session.spec.pose, 15);
        SoundEvent sound = switch (session.power.getPath()) {
            case "quake" -> SoundEvents.WARDEN_SONIC_CHARGE;
            case "electrokinesis" -> SoundEvents.BEACON_POWER_SELECT;
            case "telekinesis" -> SoundEvents.BEACON_ACTIVATE;
            case "regeneration" -> SoundEvents.TOTEM_USE;
            case "telepathy" -> SoundEvents.ILLUSIONER_CAST_SPELL;
            case "cryokinesis" -> SoundEvents.PLAYER_HURT_FREEZE;
            case "radiation" -> SoundEvents.BEACON_ACTIVATE;
            case "pyrokinesis" -> SoundEvents.FIRECHARGE_USE;
            default -> SoundEvents.WIND_CHARGE_BURST.value();
        };
        sound(player, sound, 0.9F, 0.7F);
        if (session.power.getPath().equals("radiation")) {
            RadiationService.syncVisuals(player);
            com.jayptucker.heroesevolved.telepathy.FutureVisions.radiationAwakening(player);
        }
    }

    private static void release(Session session) {
        ServerPlayer player = session.player;
        session.releaseOrigin = player.position();
        caption(session, 1);
        if (!session.power.getPath().equals("radiation"))
            AbilityEffects.send(player.serverLevel(), session.spec.release, player.position(), player.getEyePosition(),
                    session.power.getPath().equals("quake") ? QuakeIntroBlast.RADIUS
                            : session.power.getPath().equals("cryokinesis")
                            ? ColdDomeService.AWAKENING_RADIUS : 8);
        ScreenTintPayload.send(player, 0x55000000 | session.spec.color, 18, ScreenTintPayload.Style.FLASH);
        switch (session.power.getPath()) {
            case "quake" -> {
                session.blast = new QuakeIntroBlast(player.blockPosition());
                ArmPosePayload.show(player, ArmPose.ARMS_WIDE, 25);
                sound(player, SoundEvents.WARDEN_SONIC_BOOM, 2.2F, 0.55F);
                sound(player, SoundEvents.GENERIC_EXPLODE.value(), 2.4F, 0.5F);
                CameraShakePayload.sendAround(player.serverLevel(), player.position(), 36, 1.0F, 40);
                repelEnemies(player, 12, 9, 1.7);
            }
            case "flight" -> {
                ArmPosePayload.show(player, ArmPose.ARMS_RAISED, 28);
                FlightService.launch(player); // Preserve the existing launch sound and flight behaviour.
                repelEnemies(player, 6, 0, 1.1);
            }
            case "electrokinesis" -> {
                ArmPosePayload.show(player, ArmPose.ARMS_RAISED, 30);
                lightning(player, player.position());
                sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.8F, 0.7F);
                repelEnemies(player, 10, 8, 1.1);
            }
            case "telekinesis" -> {
                ArmPosePayload.show(player, ArmPose.ARMS_WIDE, 35);
                session.lifted.addAll(player.serverLevel().getEntities(player, player.getBoundingBox().inflate(8),
                        entity -> entity.isAlive() && (entity instanceof Mob || entity instanceof ItemEntity)
                                && entity.distanceToSqr(player) <= 64 && !entity.isPassenger()));
                sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.6F, 0.55F);
            }
            case "regeneration" -> {
                player.setHealth(player.getMaxHealth());
                player.clearFire();
                for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
                    if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) player.removeEffect(effect.getEffect());
                }
                for (LivingEntity ally : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(8), ally -> ally != player && ally.isAlive()
                                && !(ally instanceof Enemy) && ally.distanceToSqr(player) <= 64)) {
                    ally.heal(6);
                    AbilityEffects.send(player.serverLevel(), Kind.BLOOD_THREAD, player.getEyePosition(), ally.getEyePosition(), 1);
                }
                sound(player, SoundEvents.BEACON_POWER_SELECT, 1.4F, 1.25F);
            }
            case "telepathy" -> releaseTelepathy(session);
            case "cryokinesis" -> releaseCryokinesis(session);
            case "superspeed" -> {
                sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 1.2F);
                repelEnemies(player, 6, 0, .8);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("speed.heroesevolved.awakened"));
            }
            case "sonic_resonance" -> com.jayptucker.heroesevolved.sonic.SonicService.burst(player, 1, true);
            case "radiation" -> releaseRadiation(session);
            case "pyrokinesis" -> {
                ArmPosePayload.show(player, ArmPose.ARMS_WIDE, 30);
                com.jayptucker.heroesevolved.pyro.PyroService.awaken(player);
            }
            case "invisibility" -> com.jayptucker.heroesevolved.invisibility.InvisibilityService.awaken(player);
            case "phasing" -> com.jayptucker.heroesevolved.phasing.PhasingService.awaken(player);
            case "empathic_mimicry" -> com.jayptucker.heroesevolved.mimicry.MimicryService.awaken(player);
        }
        SOFT_LANDINGS.put(player.getUUID(), 20 * 30);
    }

    private static void aftermath(Session session, int tick) {
        ServerPlayer player = session.player;
        if (session.blast != null) session.blast.advance(player, tick + 1);
        switch (session.power.getPath()) {
            case "electrokinesis" -> {
                if (tick > 0 && tick <= 36 && tick % 12 == 0) {
                    double angle = tick * 0.53;
                    Vec3 point = session.releaseOrigin.add(Math.cos(angle) * 6, 0, Math.sin(angle) * 6);
                    lightning(player, point);
                    AbilityEffects.send(player.serverLevel(), Kind.ELECTRIC_ARC, point.add(0, 14, 0), point, 1.5);
                }
            }
            case "telekinesis" -> {
                if (tick < 28) {
                    for (Entity entity : session.lifted) {
                        if (!entity.isAlive() || entity.level() != player.level() || entity.distanceToSqr(player) > 225) continue;
                        Vec3 across = entity.position().subtract(session.releaseOrigin).cross(new Vec3(0, 1, 0)).normalize();
                        velocity(entity, across.scale(0.08).add(0, tick < 14 ? 0.13 : 0.03, 0));
                        entity.fallDistance = 0;
                        if (tick % 8 == 0) AbilityEffects.send(player.serverLevel(), Kind.PSYCHIC_BIND,
                                entity.position(), entity.position().add(0, 1, 0), Math.max(0.6, entity.getBbWidth()));
                    }
                    if (tick % 7 == 0) debris(player, tick * 0.8);
                }
                if (tick == 28) {
                    for (Entity entity : session.lifted) {
                        if (!entity.isAlive() || entity.level() != player.level() || entity.distanceToSqr(player) > 225) continue;
                        Vec3 away = entity.position().subtract(session.releaseOrigin).multiply(1, 0, 1).normalize();
                        velocity(entity, away.scale(0.65).add(0, 0.15, 0));
                        if (entity instanceof LivingEntity living) living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100));
                    }
                    AbilityEffects.send(player.serverLevel(), Kind.PSYCHIC_PUSH, session.releaseOrigin,
                            session.releaseOrigin.add(0, 1, 0), 10);
                }
            }
            case "regeneration" -> {
                if (tick % 12 == 0) AbilityEffects.at(player, Kind.HEALING_AURA, 5);
            }
            case "telepathy" -> {
                // Stray thoughts drift past as the new sense settles.
                if (tick > 0 && tick % 12 == 0 && tick <= 36) {
                    List<LivingEntity> minds = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(16), mind -> mind != player && mind.isAlive());
                    if (!minds.isEmpty()) {
                        LivingEntity mind = minds.get(player.getRandom().nextInt(minds.size()));
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                mind.getName().getString() + ": " + TelepathyService.thoughtsOf(mind, player))
                                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE,
                                        net.minecraft.ChatFormatting.ITALIC), true);
                        TelepathyService.whisper(player, 0.9F + tick / 60.0F);
                    }
                }
            }
            case "pyrokinesis" -> {
                if (tick < 30 && tick % 5 == 0) {
                    player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                            session.releaseOrigin.x, session.releaseOrigin.y + 1, session.releaseOrigin.z,
                            24, 3, 1, 3, 0.04);
                }
            }
            case "cryokinesis" -> {
                if (tick < 40 && tick % 4 == 0) {
                    player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                            session.releaseOrigin.x, session.releaseOrigin.y + 1.5, session.releaseOrigin.z,
                            30, 4, 1.5, 4, 0.05);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Telepathy: voices in the static, then every mind lights up.
    // ------------------------------------------------------------------

    private static void chargeTelepathy(Session session, int age) {
        ServerPlayer player = session.player;
        if (age % 8 != 0) return;
        // The whispers grow louder and more numerous as the charge builds.
        TelepathyService.whisper(player, 0.6F + age / (float) session.spec.chargeTicks * 0.8F);
        double reach = 4 + 20.0 * age / session.spec.chargeTicks;
        for (LivingEntity mind : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(reach), mind -> mind != player && mind.isAlive()
                        && mind.distanceToSqr(player) <= reach * reach)) {
            PrivateGlow.glow(player, mind, 20);
        }
    }

    private static void releaseTelepathy(Session session) {
        ServerPlayer player = session.player;
        ArmPosePayload.show(player, ArmPose.ARMS_WIDE, 30);
        for (LivingEntity mind : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(24), mind -> mind != player && mind.isAlive()
                        && mind.distanceToSqr(player) <= 24 * 24)) {
            PrivateGlow.glow(player, mind, 120);
            // Hostile minds nearby reel from the psychic shout.
            if (mind instanceof Enemy && mind.distanceToSqr(player) <= 12 * 12) {
                if (mind instanceof Mob mob) mob.setTarget(null);
                mind.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                mind.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                mind.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            }
        }
        AbilityEffects.send(player.serverLevel(), Kind.PSYCHIC_PUSH, player.position(),
                player.position().add(0, 1, 0), 12);
        sound(player, SoundEvents.EVOKER_PREPARE_ATTACK, 1.5F, 0.8F);
        sound(player, SoundEvents.BEACON_ACTIVATE, 1.2F, 0.7F);
    }

    // ------------------------------------------------------------------
    // Radiation: the Glowing Man burns brighter, then the first flash.
    // ------------------------------------------------------------------

    private static void chargeRadiation(Session session, int age) {
        ServerPlayer player = session.player;
        player.setGlowingTag(true);
        player.clearFire();
        player.resetFallDistance();
        double intensity = age / (double) session.spec.chargeTicks;
        // Lifted slightly off the ground by the heat pouring out of you.
        if (age > session.spec.chargeTicks - 40) {
            Vec3 motion = player.getDeltaMovement();
            velocity(player, new Vec3(motion.x * 0.3, 0.03, motion.z * 0.3));
        }
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1, player.getZ(), (int) (2 + intensity * 10),
                0.4 + intensity, 0.7 + intensity, 0.4 + intensity, 0.01);
        if (age % 10 == 0) {
            RadiationService.awakeningAura(player);
            com.jayptucker.heroesevolved.radiation.RadiationDecay.tick(player, (float) intensity, true);
            CameraShakePayload.sendAround(player.serverLevel(), player.position(), 16,
                    (float) (0.1 + intensity * 0.45), 12);
        }
        if (age % 20 == 0) {
            RadiationService.syncVisuals(player);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.heroesevolved.nuclear_uncontrolled", (session.spec.chargeTicks - age + 19) / 20)
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
    }

    private static void releaseRadiation(Session session) {
        ServerPlayer player = session.player;
        ArmPosePayload.show(player, ArmPose.ARMS_RAISED, 30);
        RadiationService.awakeningRelease(player);
    }

    // ------------------------------------------------------------------
    // Cryokinesis: frost creeps outward, then everything locks in ice.
    // ------------------------------------------------------------------

    private static void chargeCryokinesis(Session session, int age) {
        ServerPlayer player = session.player;
        // Frost creeps across the edges of vision (vanilla freezing overlay).
        player.setTicksFrozen(Math.min(140, age * 3));
        if (age % 6 != 0) return;
        ServerLevel level = player.serverLevel();
        double radius = 1.5 + 5.0 * age / session.spec.chargeTicks;
        for (int point = 0; point < 12; point++) {
            double angle = Math.PI * 2 * point / 12 + age * 0.1;
            BlockPos around = BlockPos.containing(player.getX() + Math.cos(angle) * radius,
                    player.getY(), player.getZ() + Math.sin(angle) * radius);
            BlockPos ground = CryoService.surfaceNear(level, around, 3);
            if (ground != null && !TemporaryBlocks.freezeWater(level, ground)) {
                TemporaryBlocks.place(level, ground.above(), Blocks.SNOW.defaultBlockState(),
                        20 * 20, player);
            }
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                player.getX(), player.getY() + 1, player.getZ(), 20, radius / 2, 0.8, radius / 2, 0.02);
    }

    private static void releaseCryokinesis(Session session) {
        ServerPlayer player = session.player;
        ServerLevel level = player.serverLevel();
        ArmPosePayload.show(player, ArmPose.ARMS_WIDE, 30);

        // The first awakening creates a larger, temporary cold dome around every nearby lifeform.
        ColdDomeService.awakening(player);

        // A crown of ice spikes erupts around the awakening.
        for (int spike = 0; spike < 10; spike++) {
            double angle = Math.PI * 2 * spike / 10;
            BlockPos around = BlockPos.containing(player.getX() + Math.cos(angle) * 4.5,
                    player.getY(), player.getZ() + Math.sin(angle) * 4.5);
            BlockPos ground = CryoService.surfaceNear(level, around, 3);
            if (ground == null) continue;
            TemporaryBlocks.place(level, ground.above(), Blocks.PACKED_ICE.defaultBlockState(), 100, player);
            TemporaryBlocks.place(level, ground.above(2), Blocks.BLUE_ICE.defaultBlockState(), 110, player);
        }

        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                player.getX(), player.getY() + 1, player.getZ(), 200, 5, 2, 5, 0.2);
        sound(player, SoundEvents.GLASS_BREAK, 1.6F, 0.5F);
        sound(player, SoundEvents.POWDER_SNOW_BREAK, 1.6F, 0.6F);
        CameraShakePayload.sendAround(level, player.position(), 20, 0.8F, 14);
    }

    private static void debris(ServerPlayer player, double phase) {
        for (int i = 0; i < 5; i++) {
            double angle = phase + Math.PI * 2 * i / 5;
            BlockPos pos = player.blockPosition().offset((int) (Math.cos(angle) * 4), -1, (int) (Math.sin(angle) * 4));
            var state = player.serverLevel().getBlockState(pos);
            QuakeDebrisEntity.launch(player.serverLevel(), state.isAir() ? Blocks.STONE.defaultBlockState() : state,
                    Vec3.atCenterOf(pos).add(0, 0.7, 0), new Vec3(-Math.sin(angle) * 0.12, 0.5, Math.cos(angle) * 0.12));
        }
    }

    private static void lightning(ServerPlayer owner, Vec3 point) {
        var bolt = EntityType.LIGHTNING_BOLT.create(owner.serverLevel());
        if (bolt == null) return;
        bolt.moveTo(point);
        bolt.setVisualOnly(true); // No fire or unintended damage to nearby companions.
        owner.serverLevel().addFreshEntity(bolt);
    }

    private static void repelEnemies(ServerPlayer player, double radius, float damage, double force) {
        for (LivingEntity enemy : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), entity -> entity instanceof Enemy && entity.isAlive()
                        && entity.distanceToSqr(player) <= radius * radius)) {
            Vec3 away = enemy.position().subtract(player.position()).multiply(1, 0, 1).normalize();
            if (damage > 0) enemy.hurt(player.damageSources().playerAttack(player), damage);
            velocity(enemy, away.scale(force).add(0, 0.4, 0));
        }
    }

    private static void velocity(Entity entity, Vec3 movement) {
        entity.setDeltaMovement(movement);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
    private static void caption(Session session, int phase) {
        PacketDistributor.sendToPlayer(session.player, new PowerIntroPayload(session.power, phase));
    }
    private static void finish(Session session) {
        ArmPosePayload.clear(session.player);
        SESSIONS.remove(session.player.getUUID());
        if (session.power.getPath().equals("radiation")) RadiationService.refreshGlow(session.player);
    }
    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null && session.power.getPath().equals("radiation"))
            com.jayptucker.heroesevolved.radiation.RadiationDecay.clear(player);
        if (session != null && session.age > 0) {
            ArmPosePayload.clear(player);
            caption(session, -1);
            if (session.power.getPath().equals("radiation")) RadiationService.refreshGlow(player);
        }
    }
    public static void forget(ServerPlayer player) { cancel(player); SOFT_LANDINGS.remove(player.getUUID()); }
    public static void assignmentChanged(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && !matches(session)) cancel(player);
    }
    public static void dimensionChanged(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.age > 0) cancel(player);
    }
    public static void clear() {
        for (Session session : List.copyOf(SESSIONS.values())) cancel(session.player);
        SOFT_LANDINGS.clear();
    }
}
