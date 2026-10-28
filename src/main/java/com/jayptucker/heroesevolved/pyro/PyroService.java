package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.expansion.FacilityService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Pyrokinesis, inspired by Meredith and Flint Gordon. Heat builds while the
 * power is used and cools quickly; at the blue threshold the flames burn hotter.
 *
 * <p>World fire is real vanilla fire placed through {@link WorldMutationPolicy},
 * so protection, build permission and doFireTick all apply. Crouching while
 * casting contains the flames: creatures still burn, but no blocks catch.
 * Every search and placement has a fixed bound.</p>
 */
public final class PyroService {
    public static final ResourceLocation PALM_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "pyro_flame_palm");
    public static final int MAX_HEAT = 100;
    private static final int BLUE_HEAT = 80, WHITE_HEAT = 60;
    private static final float BLUE_MULTIPLIER = 1.5F;

    private static final class Heat { int value; long lastGain; boolean blue; }
    private record Channel(ServerPlayer player, int level, long until, ResourceKey<Level> dimension) {}
    private record Charge(ServerPlayer player, int level, long releaseAt, ResourceKey<Level> dimension) {}
    private record Ring(ServerPlayer owner, ServerLevel level, Vec3 center, double radius, long until) {}
    private record Mantle(ServerPlayer owner, long until) {}
    private record Mine(ServerPlayer owner, ServerLevel level, Vec3 position, long until) {}

    private static final Map<UUID, Heat> HEAT = new HashMap<>();
    private static final Map<UUID, Channel> PALMS = new HashMap<>();
    private static final Map<UUID, Charge> INFERNOS = new HashMap<>();
    private static final Map<UUID, Ring> RINGS = new HashMap<>();
    private static final Map<UUID, Mantle> MANTLES = new HashMap<>();
    private static final Map<UUID, List<Mine>> MINES = new HashMap<>();
    private static final Map<UUID, Long> CUSHIONS = new HashMap<>();
    private static int powerDamageDepth;

    private PyroService() {}

    // ------------------------------------------------------------------
    // Ownership, heat and shared helpers
    // ------------------------------------------------------------------

    public static boolean isPyrokinetic(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey().equals(ModAbilities.PYROKINESIS_ID) && entry.getValue().isUnlocked())
                .isPresent() && !EclipseService.arePowersSuppressed(player);
    }

    /** True while one of this power's own hits is landing, so passives do not stack on top. */
    public static boolean isDealingPowerDamage() { return powerDamageDepth > 0; }

    public static int heat(ServerPlayer player) {
        Heat heat = HEAT.get(player.getUUID());
        return heat == null ? 0 : heat.value;
    }

    public static boolean isBlue(ServerPlayer player) {
        return heat(player) >= (SkillTreeService.has(player, "pyrokinesis_white_heat") ? WHITE_HEAT : BLUE_HEAT);
    }

    public static void addHeat(ServerPlayer player, int amount) {
        Heat heat = HEAT.computeIfAbsent(player.getUUID(), ignored -> new Heat());
        heat.value = Math.min(MAX_HEAT, heat.value + amount);
        heat.lastGain = player.getServer().overworld().getGameTime();
        announce(player, heat);
    }

    public static void quench(ServerPlayer player) {
        Heat heat = HEAT.get(player.getUUID());
        if (heat == null || heat.value == 0) return;
        heat.value = 0;
        announce(player, heat);
    }

    private static void announce(ServerPlayer player, Heat heat) {
        boolean blue = isBlue(player);
        if (blue == heat.blue) return;
        heat.blue = blue;
        player.displayClientMessage(Component.translatable(blue ? "pyro.heroesevolved.blue" : "pyro.heroesevolved.cooled")
                .withStyle(blue ? ChatFormatting.AQUA : ChatFormatting.GOLD), true);
        if (blue) sound(player, SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.8F);
    }

    private static float scaled(ServerPlayer player, float damage) {
        return isBlue(player) ? damage * BLUE_MULTIPLIER : damage;
    }

    static boolean hostile(ServerPlayer player, LivingEntity target) {
        return ExpansionSkills.hostile(player, target);
    }

    private static DamageSource fire(ServerPlayer player) {
        // An attributed fire source: armor still applies, and fire-immune creatures resist it.
        return new DamageSource(player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.IN_FIRE), player);
    }

    /** One pyrokinetic hit: a thermal-shock check, attributed fire damage, then ignition. */
    public static void burn(ServerPlayer player, LivingEntity target, float damage, int fireSeconds) {
        if (!target.isAlive()) return;
        powerDamageDepth++;
        try {
            com.jayptucker.heroesevolved.expansion.PowerReactions.thermal(player, target);
            target.invulnerableTime = 0;
            target.hurt(fire(player), damage);
        } finally {
            powerDamageDepth--;
        }
        if (fireSeconds > 0 && target.isAlive()) target.igniteForSeconds(fireSeconds);
    }

    private static List<LivingEntity> enemies(ServerPlayer player, Vec3 center, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                        target -> hostile(player, target) && target.position().distanceToSqr(center) <= radius * radius)
                .stream().limit(32).toList();
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void push(LivingEntity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
    }

    // ------------------------------------------------------------------
    // Real fire
    // ------------------------------------------------------------------

    /** Fire is never placed in story dimensions, which have their own mission-owned terrain. */
    public static boolean worldFireAllowed(ServerPlayer player, ServerLevel level) {
        return COMMON.pyroWorldFire.get() && !player.isShiftKeyDown() && !TimeIntroService.isCity(level)
                && !TemporalSnapshotService.isTemporalDimension(level) && !level.dimension().equals(FacilityService.DIMENSION);
    }

    /** Lights the open space at or above {@code pos}. Returns true if a fire block was placed. */
    public static boolean ignite(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!worldFireAllowed(player, level)) return false;
        for (BlockPos candidate : new BlockPos[] {pos, pos.above()}) {
            BlockState current = level.getBlockState(candidate);
            if (!current.isAir() || !level.hasChunkAt(candidate)) continue;
            BlockState fire = BaseFireBlock.getState(level, candidate);
            if (!fire.canSurvive(level, candidate)) continue;
            return WorldMutationPolicy.place(player, level, candidate, fire);
        }
        return false;
    }

    /** The first open block above solid ground near a column, within two blocks up or down. */
    private static BlockPos surface(ServerLevel level, BlockPos around) {
        for (int dy = 2; dy >= -2; dy--) {
            BlockPos pos = around.offset(0, dy, 0);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
                return pos;
        }
        return null;
    }

    private static int igniteCircle(ServerPlayer player, Vec3 center, double radius, int points) {
        if (!worldFireAllowed(player, player.serverLevel())) return 0;
        int lit = 0;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            BlockPos pos = surface(player.serverLevel(), BlockPos.containing(center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius)));
            if (pos != null && ignite(player, player.serverLevel(), pos)) lit++;
        }
        return lit;
    }

    // ------------------------------------------------------------------
    // Core actions
    // ------------------------------------------------------------------

    /** Level 1: a fire bolt that burns the first creature or lights the first surface it reaches. */
    public static boolean flare(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        LivingEntity target = Targeting.livingEntityInSight(player, 32, t -> hostile(player, t));
        Vec3 end;
        if (target != null) {
            end = target.getBoundingBox().getCenter();
            burn(player, target, scaled(player, 4 + powerLevel), 4);
        } else {
            var hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(32)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            end = hit.getLocation();
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = hit.getBlockPos();
                if (quenching(level, pos)) steam(player, level, Vec3.atCenterOf(pos));
                else ignite(player, level, pos.relative(hit.getDirection()));
            }
        }
        AbilityEffects.send(level, isBlue(player) ? Kind.BLUE_FLAME : Kind.FIRE_BOLT, eye, end, 1);
        sound(player, SoundEvents.BLAZE_SHOOT, 0.9F, 1.1F);
        addHeat(player, 12);
        return true;
    }

    public static boolean isChanneling(ServerPlayer player) { return PALMS.containsKey(player.getUUID()); }

    /** Level 1: a held flamethrower. Release, Shift, or empty stamina ends it; five seconds at most. */
    public static void startPalm(ServerPlayer player, int powerLevel) {
        PALMS.put(player.getUUID(), new Channel(player, powerLevel,
                player.getServer().overworld().getGameTime() + 100, player.level().dimension()));
        ArmPosePayload.show(player, ArmPose.TWO_HANDS, 100);
        sound(player, SoundEvents.FIRECHARGE_USE, 1, 0.8F);
    }

    public static boolean stopPalm(ServerPlayer player) {
        if (PALMS.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clear(player);
        return true;
    }

    private static void palmPulse(Channel channel, long now) {
        ServerPlayer player = channel.player;
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        var hit = level.clip(new ClipContext(eye, eye.add(aim.scale(6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        double length = hit.getType() == HitResult.Type.BLOCK ? eye.distanceTo(hit.getLocation()) : 6;
        int affected = 0;
        for (LivingEntity target : enemies(player, eye, 6.5)) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
            double along = delta.dot(aim);
            if (along < 0 || along > length + 0.6 || delta.normalize().dot(aim) < 0.82 || !player.hasLineOfSight(target)) continue;
            if (++affected > 12) break;
            burn(player, target, scaled(player, 1.5F + channel.level * 0.3F), 3);
        }
        if (hit.getType() == HitResult.Type.BLOCK && now % 10 == 0) {
            BlockPos pos = hit.getBlockPos();
            if (quenching(level, pos)) steam(player, level, Vec3.atCenterOf(pos));
            else ignite(player, level, pos.relative(hit.getDirection()));
        }
        AbilityEffects.send(level, isBlue(player) ? Kind.BLUE_FLAME : Kind.FIRE_STREAM, eye.add(aim.scale(0.6)), eye.add(aim.scale(length)), 1.2);
        if (now % 10 == 0) sound(player, SoundEvents.BLAZE_BURN, 0.8F, 0.9F);
        addHeat(player, 3);
    }

    public static boolean hasRing(ServerPlayer player) { return RINGS.containsKey(player.getUUID()); }

    /** Level 2: a ring of fire at your feet. Hostile projectiles crossing it burn up. */
    public static boolean ringOfFire(ServerPlayer player, int powerLevel) {
        Vec3 center = player.position();
        RINGS.put(player.getUUID(), new Ring(player, player.serverLevel(), center, 4,
                player.getServer().overworld().getGameTime() + 160));
        igniteCircle(player, center, 4, 24);
        AbilityEffects.send(player.serverLevel(), Kind.FIRE_RING, center, center.add(0, 1, 0), 4);
        sound(player, SoundEvents.FIRECHARGE_USE, 1.2F, 0.6F);
        addHeat(player, 15);
        return true;
    }

    /** Level 3: a fire-propelled leap. The landing is cushioned; the launch point scorches attackers. */
    public static boolean backdraft(ServerPlayer player, int powerLevel) {
        boolean pillar = SkillTreeService.has(player, "pyrokinesis_pillar_of_flame");
        Vec3 look = player.getLookAngle();
        Vec3 velocity = new Vec3(look.x, 0, look.z).normalize().scale(1.2 + powerLevel * 0.12)
                .add(0, (pillar ? 1.35 : 0.75) + Math.max(0, look.y) * 0.5, 0);
        Vec3 start = player.position();
        for (LivingEntity target : enemies(player, start, 3)) burn(player, target, scaled(player, 3), 3);
        BlockPos launch = surface(player.serverLevel(), player.blockPosition());
        if (launch != null) ignite(player, player.serverLevel(), launch);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.resetFallDistance();
        if (pillar) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        CUSHIONS.put(player.getUUID(), player.getServer().overworld().getGameTime() + 200);
        AbilityEffects.send(player.serverLevel(), Kind.FIRE_STREAM, start, start.subtract(velocity.scale(2)), 1.5);
        sound(player, SoundEvents.FIRECHARGE_USE, 1, 1.2F);
        addHeat(player, 8);
        return true;
    }

    public static boolean cushion(ServerPlayer player) {
        Long until = CUSHIONS.remove(player.getUUID());
        return until != null && player.getServer().overworld().getGameTime() <= until;
    }

    public static boolean isCharging(ServerPlayer player) { return INFERNOS.containsKey(player.getUUID()); }

    /** Level 5: a three-second charge, then a fire nova. Shift cancels while charging. */
    public static boolean startInferno(ServerPlayer player, int powerLevel) {
        INFERNOS.put(player.getUUID(), new Charge(player, powerLevel,
                player.getServer().overworld().getGameTime() + 60, player.level().dimension()));
        ArmPosePayload.show(player, ArmPose.TWO_HANDS_STRAIN, 60);
        sound(player, SoundEvents.BLAZE_AMBIENT, 1.4F, 0.5F);
        return true;
    }

    public static boolean cancelInferno(ServerPlayer player) {
        if (INFERNOS.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clear(player);
        player.displayClientMessage(Component.translatable("pyro.heroesevolved.inferno_cancelled"), true);
        return true;
    }

    private static void releaseInferno(Charge charge) {
        ServerPlayer player = charge.player;
        ServerLevel level = player.serverLevel();
        boolean fury = SkillTreeService.has(player, "pyrokinesis_flints_fury");
        boolean blue = fury || isBlue(player);
        double radius = 10 + (fury ? 3 : 0);
        Vec3 center = player.position();
        float peak = (blue ? 18 : 12) + charge.level * 2;
        for (LivingEntity target : enemies(player, center, radius)) {
            if (!player.hasLineOfSight(target)) continue;
            double falloff = 1 - 0.5 * target.position().distanceTo(center) / radius;
            burn(player, target, (float) (peak * falloff), 8);
            push(target, target.position().subtract(center).multiply(1, 0, 1).normalize().scale(blue ? 1.3 : 0.9).add(0, 0.35, 0));
        }
        if (worldFireAllowed(player, level)) {
            for (int i = 0; i < 48; i++) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2, distance = 2 + player.getRandom().nextDouble() * (radius - 2);
                BlockPos pos = surface(level, BlockPos.containing(center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance)));
                if (pos != null) ignite(player, level, pos);
            }
        }
        AbilityEffects.send(level, blue ? Kind.BLUE_FLAME : Kind.FIRE_NOVA, center, center.add(0, 1, 0), radius);
        AbilityEffects.send(level, Kind.FIRE_NOVA, center, center.add(0, 1, 0), radius);
        CameraShakePayload.sendAround(level, center, radius * 3, blue ? 0.8F : 0.55F, 25);
        sound(player, SoundEvents.GENERIC_EXPLODE.value(), 2, blue ? 0.6F : 0.8F);
        sound(player, SoundEvents.BLAZE_SHOOT, 2, 0.5F);
        ArmPosePayload.show(player, ArmPose.ARMS_WIDE, 20);
        if (blue) player.displayClientMessage(Component.translatable("pyro.heroesevolved.blue_inferno").withStyle(ChatFormatting.AQUA), true);
        quench(player);
    }

    /** First awakening: the flames escape all at once in a ring of real fire. */
    public static void awaken(ServerPlayer player) {
        Vec3 center = player.position();
        for (LivingEntity target : enemies(player, center, 8)) {
            burn(player, target, 6, 5);
            push(target, target.position().subtract(center).multiply(1, 0, 1).normalize().scale(1.1).add(0, 0.3, 0));
        }
        igniteCircle(player, center, 5, 20);
        igniteCircle(player, center, 8, 28);
        sound(player, SoundEvents.BLAZE_SHOOT, 2, 0.6F);
        sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 1.1F);
    }

    /** Overexertion: the heat turns inward. */
    public static void overheat(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
        player.causeFoodExhaustion(6);
        quench(player);
    }

    /** Water, ice and snow turn fire into steam instead of catching. */
    private static boolean quenching(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getFluidState().isEmpty() || state.is(BlockTags.ICE) || state.is(BlockTags.SNOW);
    }

    /** Fire meeting water or ice becomes a short blinding steam cloud. */
    public static void steam(ServerPlayer player, ServerLevel level, Vec3 at) {
        com.jayptucker.heroesevolved.expansion.PowerReactions.steamVeil(player, level, at);
    }

    // ------------------------------------------------------------------
    // Skill tree: three opening techniques and three named techniques
    // ------------------------------------------------------------------

    public static boolean skill(AbilityUseContext context, int branch) {
        ServerPlayer player = context.player();
        int level = context.abilityLevel();
        switch (branch) {
            case 0 -> {
                LivingEntity target = Targeting.livingEntityInSight(player, 40, t -> hostile(player, t));
                if (target == null) return false;
                burn(player, target, scaled(player, 6 + level), 5);
                push(target, player.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(0.45).add(0, 0.15, 0));
                AbilityEffects.send(player.serverLevel(), isBlue(player) ? Kind.BLUE_FLAME : Kind.FIRE_BOLT,
                        player.getEyePosition(), target.getEyePosition(), 1.2);
                sound(player, SoundEvents.FIRECHARGE_USE, 0.9F, 1.4F);
                addHeat(player, 10);
            }
            case 1 -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 0));
                MANTLES.put(player.getUUID(), new Mantle(player, player.getServer().overworld().getGameTime() + 160));
                AbilityEffects.send(player.serverLevel(), Kind.FIRE_RING, player.position(), player.getEyePosition(), 1.6);
                sound(player, SoundEvents.FIRECHARGE_USE, 0.9F, 0.7F);
                addHeat(player, 6);
            }
            case 2 -> {
                for (MobEffectInstance effect : List.copyOf(player.getActiveEffects()))
                    if (!effect.getEffect().value().isBeneficial()) player.removeEffect(effect.getEffect());
                player.heal(4 + level);
                player.clearFire();
                AbilityEffects.send(player.serverLevel(), Kind.FIRE_NOVA, player.position(), player.getEyePosition(), 1.4);
                sound(player, SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.3F);
            }
            default -> { return false; }
        }
        ArmPosePayload.show(player, branch == 0 ? ArmPose.ONE_HAND : ArmPose.ARMS_WIDE, 16);
        return true;
    }

    public static boolean technique(AbilityUseContext context, String mechanic) {
        ServerPlayer player = context.player();
        return switch (mechanic) {
            case "ember_mine" -> plantMine(player, context.abilityLevel());
            case "blue_lance" -> blueLance(player, context.abilityLevel());
            case "fire_shepherd" -> fireShepherd(player);
            default -> false;
        };
    }

    private static boolean plantMine(ServerPlayer player, int powerLevel) {
        BlockPos aimed = ExpansionSkills.aim(player);
        BlockPos pos = surface(player.serverLevel(), aimed.above());
        if (pos == null) {
            player.displayClientMessage(Component.translatable("pyro.heroesevolved.no_ground"), true);
            return false;
        }
        List<Mine> mines = MINES.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
        int limit = SkillTreeService.has(player, "pyrokinesis_long_fuse") ? 3 : 2;
        while (mines.size() >= limit) mines.removeFirst();
        Vec3 position = Vec3.atBottomCenterOf(pos);
        mines.add(new Mine(player, player.serverLevel(), position, player.getServer().overworld().getGameTime() + 20 * 40));
        PacketDistributor.sendToPlayer(player, new com.jayptucker.heroesevolved.network.AbilityEffectPayload(
                Kind.FIRE_RING, position, position.add(0, 1, 0), 0.6F));
        sound(player, SoundEvents.FLINTANDSTEEL_USE, 0.7F, 0.8F);
        addHeat(player, 6);
        return true;
    }

    private static void erupt(Mine mine, List<Mine> remaining, int depth) {
        ServerPlayer player = mine.owner;
        boolean longFuse = SkillTreeService.has(player, "pyrokinesis_long_fuse");
        double radius = longFuse ? 4 : 2.5;
        int level = com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(player);
        for (LivingEntity target : enemies(player, mine.position, radius)) burn(player, target, scaled(player, 6 + level), 5);
        AbilityEffects.send(mine.level, Kind.FIRE_NOVA, mine.position, mine.position.add(0, 1, 0), radius);
        mine.level.playSound(null, mine.position.x, mine.position.y, mine.position.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1, 1.3F);
        if (!SkillTreeService.has(player, "pyrokinesis_wildfire") || depth > 2) return;
        igniteCircle(player, mine.position, radius, 8);
        for (Mine other : List.copyOf(remaining)) {
            if (other.level == mine.level && other.position.distanceToSqr(mine.position) <= 64 && remaining.remove(other)) {
                AbilityEffects.send(mine.level, Kind.FIRE_STREAM, mine.position.add(0, .3, 0), other.position.add(0, .3, 0), 1);
                erupt(other, remaining, depth + 1);
            }
        }
    }

    private static boolean blueLance(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        var hit = level.clip(new ClipContext(eye, eye.add(aim.scale(24)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double length = hit.getType() == HitResult.Type.BLOCK ? eye.distanceTo(hit.getLocation()) : 24;
        int struck = 0;
        for (LivingEntity target : enemies(player, eye, length + 1)) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
            double along = delta.dot(aim);
            if (along < 0 || along > length + 0.5 || delta.subtract(aim.scale(along)).length() > 0.9 + target.getBbWidth() / 2) continue;
            if (++struck > 8) break;
            burn(player, target, (8 + powerLevel) * BLUE_MULTIPLIER, 6);
        }
        if (hit.getType() == HitResult.Type.BLOCK) ignite(player, level, hit.getBlockPos().relative(hit.getDirection()));
        AbilityEffects.send(level, Kind.BLUE_FLAME, eye.add(aim.scale(0.5)), eye.add(aim.scale(length)), 1.4);
        sound(player, SoundEvents.SOUL_ESCAPE.value(), 1.4F, 0.6F);
        sound(player, SoundEvents.BLAZE_SHOOT, 1, 0.7F);
        addHeat(player, 15);
        return true;
    }

    /** Draws nearby fire into your hands, putting it out. Restores stamina and health; the firefighter's technique. */
    private static boolean fireShepherd(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int drawn = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -4, -8), origin.offset(8, 4, 8))) {
            if (drawn >= 32) break;
            if (!level.getBlockState(pos).is(BlockTags.FIRE) || !WorldMutationPolicy.canEdit(player, level, pos)) continue;
            level.removeBlock(pos, false);
            drawn++;
            if (drawn % 4 == 1) AbilityEffects.send(level, Kind.FIRE_STREAM, Vec3.atCenterOf(pos), player.getEyePosition(), 0.6);
        }
        if (drawn > 0) {
            PlayerEnergyService.restore(player, Math.min(40, drawn * 2));
            player.heal(drawn * 0.5F);
            sound(player, SoundEvents.FIRE_EXTINGUISH, 1, 0.8F);
        }
        if (SkillTreeService.has(player, "pyrokinesis_thaw")) {
            for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5),
                    ally -> ExpansionSkills.friendly(player, ally) && ally.distanceToSqr(player) <= 25)) {
                if (CryoService.isFrozen(ally)) CryoService.releaseEntity(ally);
                ally.setTicksFrozen(0);
                ally.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                AbilityEffects.send(level, Kind.STEAM, ally.position(), ally.getEyePosition(), 1);
            }
        }
        player.displayClientMessage(Component.translatable("pyro.heroesevolved.shepherd", drawn), true);
        AbilityEffects.send(level, Kind.FIRE_RING, player.position(), player.getEyePosition(), 2);
        return true;
    }

    // ------------------------------------------------------------------
    // Tick and cleanup
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();

        for (Channel channel : List.copyOf(PALMS.values())) {
            ServerPlayer player = channel.player;
            if (!player.isAlive() || player.isRemoved() || now >= channel.until || !PowerRules.canAct(player)
                    || !channel.dimension.equals(player.level().dimension()) || !isPyrokinetic(player) || player.isInWaterOrBubble()) {
                stopPalm(player);
                continue;
            }
            if (now % 5 != 0) continue;
            if (!PlayerEnergyService.tryConsume(player, 3)) { stopPalm(player); continue; }
            MasteryService.awardEnergySpent(player, 3);
            palmPulse(channel, now);
        }

        for (Charge charge : List.copyOf(INFERNOS.values())) {
            ServerPlayer player = charge.player;
            if (!player.isAlive() || player.isRemoved() || !PowerRules.canAct(player) || !isPyrokinetic(player)
                    || !charge.dimension.equals(player.level().dimension())) {
                INFERNOS.remove(player.getUUID());
                ArmPosePayload.clear(player);
                continue;
            }
            long remaining = charge.releaseAt - now;
            if (remaining <= 0) {
                INFERNOS.remove(player.getUUID());
                releaseInferno(charge);
                continue;
            }
            if (remaining % 4 == 0) AbilityEffects.send(player.serverLevel(), Kind.PYRO_AWAKEN_CHARGE,
                    player.position(), player.getEyePosition(), 1 - remaining / 60.0);
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.4, 1, 0.4));
            addHeat(player, 1);
        }

        RINGS.values().removeIf(ring -> now >= ring.until || !ring.owner.isAlive() || ring.owner.isRemoved());
        for (Ring ring : RINGS.values()) {
            if (now % 5 != 0) continue;
            ServerPlayer owner = ring.owner;
            for (LivingEntity target : ring.level.getEntitiesOfClass(LivingEntity.class, new AABB(ring.center, ring.center).inflate(ring.radius + 1.2, 3, ring.radius + 1.2),
                    target -> hostile(owner, target))) {
                double distance = Math.sqrt(target.position().distanceToSqr(ring.center.x, target.getY(), ring.center.z));
                if (Math.abs(distance - ring.radius) <= 1.2 && now % 20 == 0) burn(owner, target, 2, 4);
            }
            int burned = 0;
            for (Projectile shot : ring.level.getEntitiesOfClass(Projectile.class, new AABB(ring.center, ring.center).inflate(ring.radius + 1, 4, ring.radius + 1),
                    shot -> shot.getOwner() != owner && !(shot.getOwner() instanceof LivingEntity shooter && owner.isAlliedTo(shooter)))) {
                if (++burned > 16) break;
                ring.level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, shot.getX(), shot.getY(), shot.getZ(), 4, .1, .1, .1, .01);
                shot.discard();
            }
            if (now % 20 == 0) AbilityEffects.send(ring.level, Kind.FIRE_RING, ring.center, ring.center.add(0, 1, 0), ring.radius);
            com.jayptucker.heroesevolved.expansion.PowerReactions.firestorm(owner, ring.level, ring.center, ring.radius);
        }

        MANTLES.values().removeIf(mantle -> now >= mantle.until || !mantle.owner.isAlive() || mantle.owner.isRemoved());
        if (now % 5 == 0) {
            for (Mantle mantle : MANTLES.values()) {
                ServerPlayer owner = mantle.owner;
                for (Projectile shot : owner.serverLevel().getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(3),
                        shot -> shot.getOwner() != owner)) {
                    owner.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, shot.getX(), shot.getY(), shot.getZ(), 4, .1, .1, .1, .01);
                    shot.discard();
                }
                if (now % 20 == 0) {
                    for (LivingEntity target : enemies(owner, owner.position(), 2.5)) burn(owner, target, 1.5F, 3);
                    AbilityEffects.send(owner.serverLevel(), Kind.FIRE_RING, owner.position(), owner.getEyePosition(), 1.2);
                }
            }
        }

        for (var entry : List.copyOf(MINES.entrySet())) {
            List<Mine> mines = entry.getValue();
            mines.removeIf(mine -> now >= mine.until || !mine.owner.isAlive() || mine.owner.isRemoved());
            for (Mine mine : List.copyOf(mines)) {
                if (!mines.contains(mine)) continue;
                boolean triggered = !mine.level.getEntitiesOfClass(LivingEntity.class, new AABB(mine.position, mine.position).inflate(1.6, 1.5, 1.6),
                        target -> hostile(mine.owner, target)).isEmpty();
                if (triggered) {
                    mines.remove(mine);
                    erupt(mine, mines, 0);
                } else if (now % 40 == 0) {
                    PacketDistributor.sendToPlayer(mine.owner, new com.jayptucker.heroesevolved.network.AbilityEffectPayload(
                            Kind.FIRE_RING, mine.position, mine.position.add(0, 1, 0), 0.5F));
                }
            }
            if (mines.isEmpty()) MINES.remove(entry.getKey());
        }

        // Heat cools after three seconds without use: one point every half second.
        if (now % 10 == 0) {
            for (var entry : List.copyOf(HEAT.entrySet())) {
                Heat heat = entry.getValue();
                if (now - heat.lastGain < 60) continue;
                heat.value = Math.max(0, heat.value - 1);
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) announce(player, heat);
                if (heat.value == 0) HEAT.remove(entry.getKey());
            }
        }
        CUSHIONS.values().removeIf(until -> until < now);
    }

    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        stopPalm(player);
        if (INFERNOS.remove(id) != null) ArmPosePayload.clear(player);
        HEAT.remove(id);
        RINGS.remove(id);
        MANTLES.remove(id);
        MINES.remove(id);
        CUSHIONS.remove(id);
    }

    public static void clear() {
        HEAT.clear();
        PALMS.clear();
        INFERNOS.clear();
        RINGS.clear();
        MANTLES.clear();
        MINES.clear();
        CUSHIONS.clear();
        powerDamageDepth = 0;
    }
}
