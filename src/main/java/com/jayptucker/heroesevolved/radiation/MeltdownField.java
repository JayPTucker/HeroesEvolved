package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.hazard.BodyBurn;
import com.jayptucker.heroesevolved.hazard.HazardDamage;
import com.jayptucker.heroesevolved.hazard.Suits;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.speed.SpeedContainment;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What pours off someone melting down (a radioactive player's Critical Mass, voluntary or not, or a villager the Eclipse
 * woke a power in), building as the countdown runs out ({@code progress} 0 to 1):
 * <ul>
 *   <li><b>Shock waves</b> roll out from them, further, harder and closer together the less time is left: they throw
 *   people back, burn them, and dose them with radiation.</li>
 *   <li><b>The near-field</b>: within {@link #nearRadius} (3 blocks at first, 12 at the end) the heat and radiation
 *   kill anyone unprotected within seconds. Its edge is marked on the ground.</li>
 *   <li><b>The ground</b> round them decays: blocks crack and crumble away, grass scorches, water boils off, snow and
 *   ice melt, plants burn and fires start.</li>
 * </ul>
 * A full suit of either kind keeps the radiation and heat off (and wears as it does); radioactive players are immune;
 * Regeneration users burn and grow back, and can't die of it. A held meltdown (contained, frozen, drawn, time-stopped)
 * still leaks, but its waves are weaker and slower. A player's meltdown breaks only what they could break; a villager's
 * follows {@code mobGriefing}, like a creeper's.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MeltdownField {
    /** Ticks a shock wave takes to reach its edge; how often the near-field burns and the ground decays. */
    public static final int WAVE_TICKS = 6, FIELD_EVERY = 10, DECAY_EVERY = 4;
    static final int MAX_CRUMBLING = 32, FLAKE_EVERY = 8, WARN_EVERY = 60;

    public static float nearRadius(float progress) { return 3 + 9 * Mth.clamp(progress, 0, 1); }
    public static float waveRadius(float progress) { return 6 + 16 * Mth.clamp(progress, 0, 1); }
    public static float waveStrength(float progress) { return 0.12F + 0.88F * (float) Math.pow(Mth.clamp(progress, 0, 1), 1.6); }
    public static int waveEvery(float progress) { return Math.round(Mth.lerp((float) Math.pow(Mth.clamp(progress, 0, 1), 1.3), 90, 14)); }
    public static float decayRadius(float progress) { return 2 + 8 * Mth.clamp(progress, 0, 1); }

    private record Crumble(BlockPos pos, BlockState original, int animation, int stage) {}
    private static final class Core {
        LivingEntity entity;
        ServerLevel level;
        long lastTick = Long.MIN_VALUE, nextWave = Long.MIN_VALUE;
        float progress;
        boolean held;
        final LinkedHashMap<BlockPos, Crumble> crumbling = new LinkedHashMap<>();
    }
    private record Wave(LivingEntity core, ServerLevel level, Vec3 center, float radius, float strength, long born, Set<UUID> struck) {}

    private static final Map<UUID, Core> CORES = new HashMap<>();
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final Map<UUID, Long> FLAKED = new HashMap<>(), WARNED = new HashMap<>();
    private static int nextAnimation = Integer.MIN_VALUE / 2;

    private MeltdownField() {}

    // ------------------------------------------------------------------
    // Driven by whatever is melting down, every tick
    // ------------------------------------------------------------------

    /** One tick of {@code core}'s meltdown, {@code progress} of the way to going off. */
    public static void tick(LivingEntity core, float progress, boolean held) {
        if (!(core.level() instanceof ServerLevel level) || !core.isAlive() || !enabled()
                || TimeIntroService.isCity(level) || TemporalSnapshotService.isTemporalDimension(level)) return;
        Core c = CORES.computeIfAbsent(core.getUUID(), id -> new Core());
        if (c.level != level) { forgetCrumbles(c); c.level = level; }
        long now = level.getGameTime();
        if (c.lastTick == now) return;
        c.entity = core;
        c.lastTick = now;
        c.progress = Mth.clamp(progress, 0, 1);
        c.held = held;
        if (c.nextWave == Long.MIN_VALUE) c.nextWave = now + 20;
        if (now >= c.nextWave) {
            wave(c, now);
            c.nextWave = now + (long) waveEvery(c.progress) * (held ? 2 : 1);
        }
        if (now % FIELD_EVERY == 0) field(c);
        if (now % DECAY_EVERY == 0) decay(c);
    }

    /** The server's {@code hazards.meltdownHazards} switch (on if the config isn't loaded yet). */
    static boolean enabled() {
        try { return com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.meltdownHazards.get(); }
        catch (IllegalStateException notLoaded) { return true; }
    }

    /** It's over (gone off or stopped): the cracks it left stop crumbling. Waves already rolling finish. */
    public static void end(LivingEntity core) {
        Core c = CORES.remove(core.getUUID());
        if (c != null) forgetCrumbles(c);
    }

    public static boolean active(LivingEntity core) { return CORES.containsKey(core.getUUID()); }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        for (Core c : List.copyOf(CORES.values())) {
            if (c.level.getServer() != event.getServer()) continue;
            if (c.level.getGameTime() - c.lastTick > 5 || !c.entity.isAlive()) end(c.entity);
        }
        for (Wave w : List.copyOf(WAVES)) {
            if (w.level.getServer() != event.getServer()) continue;
            long age = w.level.getGameTime() - w.born;
            if (age > WAVE_TICKS) { WAVES.remove(w); continue; }
            double front = Math.min(w.radius, (age + 1) * w.radius / (double) WAVE_TICKS);
            for (LivingEntity e : w.level.getEntitiesOfClass(LivingEntity.class, new AABB(w.center, w.center).inflate(front + 1),
                    e -> e != w.core && e.isAlive())) {
                double d = e.position().distanceTo(w.center);
                if (d <= front && w.struck.add(e.getUUID())) shock(w, e, d);
            }
        }
        FLAKED.values().removeIf(at -> event.getServer().overworld().getGameTime() - at > 200);
        WARNED.values().removeIf(at -> event.getServer().overworld().getGameTime() - at > 600);
    }

    // ------------------------------------------------------------------
    // Shock waves
    // ------------------------------------------------------------------

    private static void wave(Core c, long now) {
        LivingEntity core = c.entity;
        float s = waveStrength(c.progress) * (c.held ? 0.35F : 1), r = waveRadius(c.progress);
        Vec3 ground = NuclearFallout.groundUnder(core);
        WAVES.add(new Wave(core, c.level, ground, r, s, now, new HashSet<>()));
        // Strength rides in the target's height above the origin.
        SetPiecePayload.send(c.level, Piece.RAD_SHOCK, ground, ground.add(0, s, 0), r, null);
        CameraShakePayload.sendAround(c.level, ground, r * 2, 0.15F + 1.2F * s, 8);
        c.level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE,
                0.6F + 1.6F * s, 0.45F + 0.5F * (1 - s));
        if (s > 0.5F) c.level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, s, 0.55F);
    }

    private static void shock(Wave w, LivingEntity e, double distance) {
        if (e.isSpectator() || e instanceof Player p && p.getAbilities().invulnerable) return;
        float closeness = (float) Mth.clamp(1 - distance / w.radius, 0, 1), s = w.strength;
        expose(w.core, w.level, e, (0.6F + 7.4F * s) * (0.35F + 0.65F * closeness), s > 0.6F ? 2 : 1, 0, true);
        if (braced(w.core, e)) return;
        double resist = Math.max(0, 1 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        double push = (0.35 + 1.5 * s) * (0.4 + 0.6 * closeness) * resist * (e.isCrouching() ? 0.45 : 1);
        Vec3 away = e.position().subtract(w.center).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        e.setDeltaMovement(e.getDeltaMovement().scale(0.3).add(away.scale(push)).add(0, (0.15 + 0.35 * s) * resist, 0));
        e.hurtMarked = true;
        if (e instanceof ServerPlayer p) p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }

    /**
     * Who a wave can't throw: a speedster out on a containment ring, a radioactive player crouched beside them drawing the
     * heat, and whoever is carrying them.
     */
    static boolean braced(LivingEntity core, LivingEntity e) {
        if (core.getRootVehicle() == e) return true;
        if (!(e instanceof ServerPlayer p)) return false;
        return SpeedContainment.running(p) || Suits.radioactive(p) && p.isCrouching() && p.distanceToSqr(core) <= 16;
    }

    // ------------------------------------------------------------------
    // The near-field
    // ------------------------------------------------------------------

    private static void field(Core c) {
        LivingEntity core = c.entity;
        float r = nearRadius(c.progress) * (c.held ? 0.8F : 1);
        Vec3 center = core.position().add(0, core.getBbHeight() * 0.5, 0);
        SetPiecePayload.send(c.level, Piece.RAD_FIELD, NuclearFallout.groundUnder(core), center, r, core);
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r * 1.8),
                e -> e != core && e.isAlive() && !e.isSpectator())) {
            double d = e.getBoundingBox().getCenter().distanceTo(center);
            if (d > r) {
                if (d <= r * 1.8 && e instanceof ServerPlayer p) warn(p, r, false);
                continue;
            }
            float closeness = (float) (1 - d / r);
            float heat = (0.5F + 5.5F * c.progress) * (0.4F + 0.6F * closeness);
            expose(core, c.level, e, heat, c.progress > 0.5F ? 2 : 1, c.progress > 0.35F ? 2 + 3 * c.progress : 0, false);
            if (e instanceof ServerPlayer p) warn(p, r, true);
        }
    }

    /** Tells an unprotected player how close is too close, now and then. */
    private static void warn(ServerPlayer p, float r, boolean inside) {
        if (Suits.meltdownProof(p)) return;
        long now = p.serverLevel().getGameTime();
        Long last = WARNED.get(p.getUUID());
        if (last != null && now - last < WARN_EVERY) return;
        WARNED.put(p.getUUID(), now);
        Component line = Suits.regenerates(p)
                ? Component.literal(inside ? "Your skin burns away and grows back. You can take this; no one else can."
                        : "Lethal radiation within " + Math.round(r) + " blocks. Your body will grow back. Others need a suit.")
                : Component.literal(inside ? "The radiation is cooking you. Get out, or get a suit."
                        : "Lethal radiation within " + Math.round(r) + " blocks of them. You need a suit to get closer.");
        p.displayClientMessage(line.copy().withStyle(inside ? ChatFormatting.RED : ChatFormatting.GOLD), true);
    }

    /**
     * Heat and radiation from {@code core} reach {@code e}: suits keep their share off and wear for it, radioactive players
     * shrug it off, everyone else burns, takes a dose, and maybe catches fire.
     */
    static void expose(LivingEntity core, ServerLevel level, LivingEntity e, float heat, int stages, float igniteSeconds, boolean wave) {
        if (!e.isAlive() || e.isSpectator() || e instanceof Player p && p.getAbilities().invulnerable || Suits.radioactive(e)) return;
        float shield = Suits.radiation(e);
        if (shield > 0) Suits.wear(e, heat * shield);
        if (shield >= 1) return;
        float dealt = heat * (1 - shield);
        RadiationDeaths.mark(e);
        if (wave) e.invulnerableTime = 0;
        e.hurt(source(level, core), dealt);
        if (stages > 0) RadiationSickness.dose(e, null, stages, 160);
        if (igniteSeconds > 0 && shield < 0.5F && !e.isInWaterRainOrBubble()) e.igniteForSeconds(igniteSeconds);
        if (e instanceof ServerPlayer p) BodyBurn.raise(p, dealt * 0.03F, 0.3F + 0.7F * Mth.clamp(dealt / 6, 0, 1));
        flake(e, Mth.clamp(dealt / 5, 0.2F, 1));
    }

    /** Nobody's blow when a player melts down (so PvP rules don't apply); the villager's name when it's him. */
    private static DamageSource source(ServerLevel level, LivingEntity core) {
        return core instanceof Player ? HazardDamage.source(level, HazardDamage.MELTDOWN) : HazardDamage.source(level, HazardDamage.MELTDOWN, core);
    }

    /** Cubes of a burning body peeling off it and drifting up, at most every {@link #FLAKE_EVERY} ticks a body. */
    public static void flake(LivingEntity e, float intensity) {
        if (!(e.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Long last = FLAKED.get(e.getUUID());
        if (last != null && now - last < FLAKE_EVERY) return;
        FLAKED.put(e.getUUID(), now);
        SetPiecePayload.send(level, Piece.RAD_FLAKE, e.position(), e.position().add(0, e.getBbHeight(), 0), Mth.clamp(intensity, 0.1F, 1), e);
    }

    /** A Regeneration user can't die of a meltdown's heat: it leaves them at half a heart, and they grow back. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void regrow(LivingDamageEvent.Pre event) {
        LivingEntity e = event.getEntity();
        if (!event.getSource().is(HazardDamage.MELTDOWN) || !Suits.regenerates(e)) return;
        event.setNewDamage(Math.min(event.getNewDamage(), Math.max(0, e.getHealth() - 1)));
    }

    // ------------------------------------------------------------------
    // The ground
    // ------------------------------------------------------------------

    private static void decay(Core c) {
        LivingEntity core = c.entity;
        ServerLevel level = c.level;
        float p = c.progress;
        int radius = (int) Math.ceil(decayRadius(p));
        int samples = Math.round((3 + 10 * p) * (c.held ? 0.5F : 1));
        BlockPos origin = core.blockPosition();
        for (int i = 0; i < samples; i++) {
            BlockPos pos = origin.offset(level.random.nextInt(radius * 2 + 1) - radius, level.random.nextInt(6) - 3,
                    level.random.nextInt(radius * 2 + 1) - radius);
            if (pos.distSqr(origin) > (double) radius * radius || !level.hasChunkAt(pos) || !mayEdit(core, level, pos)) continue;
            scorch(c, pos, (float) Math.sqrt(pos.distSqr(origin)) / radius);
        }
        // Cracks spread through what is already crumbling, faster as the end comes.
        int step = 1 + Math.round(2 * p);
        List<Crumble> next = new ArrayList<>();
        for (Iterator<Crumble> it = c.crumbling.values().iterator(); it.hasNext(); ) {
            Crumble k = it.next();
            it.remove();
            if (!level.getBlockState(k.pos).equals(k.original) || !mayEdit(core, level, k.pos)) {
                level.destroyBlockProgress(k.animation, k.pos, -1);
                continue;
            }
            int stage = k.stage + step;
            level.sendParticles(ParticleTypes.SMOKE, k.pos.getX() + .5, k.pos.getY() + 1.02, k.pos.getZ() + .5, 1, .25, 0, .25, .01);
            if (stage < 10) { level.destroyBlockProgress(k.animation, k.pos, stage); next.add(new Crumble(k.pos, k.original, k.animation, stage)); continue; }
            level.destroyBlockProgress(k.animation, k.pos, -1);
            if (core instanceof ServerPlayer owner
                    && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, k.pos, k.original, owner)).isCanceled()) continue;
            level.destroyBlock(k.pos, false, core);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, k.pos.getX() + .5, k.pos.getY() + .5, k.pos.getZ() + .5, 2, .3, .3, .3, .02);
        }
        for (Crumble k : next) c.crumbling.put(k.pos, k);
    }

    /** What the heat does to one block, {@code out} of the way from the middle to the edge (0 to 1). */
    private static void scorch(Core c, BlockPos pos, float out) {
        ServerLevel level = c.level;
        float p = c.progress;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            if (BaseFireBlock.canBePlacedAt(level, pos, Direction.UP) && (flammableBeside(level, pos) || level.random.nextFloat() < 0.25F * p))
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            return;
        }
        if (state.is(Blocks.WATER) && state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource()) {
            if (p > 0.4F && out < 0.6F) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, 6, .3, .3, .3, .03);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 0.7F);
            }
            return;
        }
        if (!state.getFluidState().isEmpty()) return;
        if (state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW)) { level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); return; }
        if (state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.ICE)) {
            level.setBlockAndUpdate(pos, level.dimensionType().ultraWarm() || state.is(Blocks.SNOW_BLOCK)
                    ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState());
            return;
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL) || state.is(Blocks.DIRT_PATH)) {
            level.setBlockAndUpdate(pos, (p > 0.6F ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState());
            return;
        }
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CROPS)
                || state.canBeReplaced()) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            if (BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            return;
        }
        if ((state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) && p > 0.7F && out < 0.5F) {
            level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
            return;
        }
        // Everything else cracks and, a few moments later, crumbles away. The block under their feet holds.
        BlockPos delta = pos.subtract(c.entity.blockPosition());
        float hardness = state.getDestroySpeed(level, pos);
        if (delta.getY() < 0 && delta.getX() * delta.getX() + delta.getZ() * delta.getZ() <= 2 || hardness < 0 || hardness > 30
                || level.getBlockEntity(pos) != null || c.crumbling.containsKey(pos) || c.crumbling.size() >= MAX_CRUMBLING) return;
        c.crumbling.put(pos.immutable(), new Crumble(pos.immutable(), state, nextAnimation++, 0));
    }

    private static boolean flammableBeside(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.getBlockState(n).isFlammable(level, n, d.getOpposite())) return true;
        }
        return false;
    }

    /** A player's meltdown breaks only what they could; anyone else's follows mobGriefing. */
    private static boolean mayEdit(LivingEntity core, ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (core instanceof ServerPlayer p) return p.mayBuild() && level.mayInteract(p, pos);
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private static void forgetCrumbles(Core c) {
        if (c.level != null) for (Crumble k : c.crumbling.values()) c.level.destroyBlockProgress(k.animation, k.pos, -1);
        c.crumbling.clear();
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { clear(); }

    public static void clear() {
        for (Core c : CORES.values()) forgetCrumbles(c);
        CORES.clear();
        WAVES.clear();
        FLAKED.clear();
        WARNED.clear();
    }
}
