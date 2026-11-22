package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.radio.BandChatter;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A guardian's day: a villager whose gift woke good uses it for their village's work, where players can see it. Each
 * power has a {@link Job}; every minute or so of daylight the guardian finds something to do, walks to it, does it
 * (the power's own effect, and a real change: a lit furnace, wet farmland, ice in the barrel), and goes back to village
 * life. Run from {@code GiftedMob.idle} (a good gifted villager with no foe in sight), so a fight, sleep, a Suppressor
 * or a Dampener stops it. What the player sees: {@code docs/gifted-villagers.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class GiftedWork {
    public enum Job {
        SMITH("smith", "{name} kept the forge going all night again."),
        WATERER("waterer", "{name}'s had the fields soaked since sunrise."),
        ICE_HOUSE("ice-house keeper", "{name} has the ice house full. Come and get it while it's cold."),
        COURIER("courier", "{name} is out with the post again. Look up."),
        POST("post runner", "Post's been and gone. {name} doesn't hang about."),
        HEALER("healer", "{name} patched up half the village this morning."),
        KEEPER("golem keeper", "Our golem's humming. {name} gave it a charge."),
        HARVESTER("harvester", "{name} brought the harvest in without touching it."),
        TILLER("tiller", "Felt the ground hum this morning. That's {name}, working the fields."),
        BELL("bell ringer", "That was {name} on the bell. Everyone home."),
        LOOKOUT("lookout", "{name} is keeping watch on the edge of town. Nothing gets past.");

        public final String title, boast;
        Job(String title, String boast) { this.title = title; this.boast = boast; }
    }

    public enum Stage { RESTING, GOING, ACTING, FLYING }

    /** Between jobs, at least and at most; how far from the bell work is looked for; how long a job takes. */
    public static final int REST_MIN = 900, REST_MAX = 1500, REACH = 24, ACT = 40, GIVE_UP = 400;
    /** The ice house holds at most this much ice. */
    public static final int ICE_CAP = 20;
    /** The courier's reach, cruise speed (blocks a tick) and how long they stay out of sight. */
    public static final int COURIER_RANGE = 256, AWAY_TICKS = 2400;
    public static final double CRUISE = 0.55;
    /** Further than this from their bell, a guardian makes for home before anything else. */
    public static final double HOME_REACH = 64;
    public static final String COURIER_TAG = HeroesEvolved.MOD_ID + "_courier";

    /** One guardian's work in hand. */
    public static final class Work {
        final Job job;
        Stage stage = Stage.RESTING;
        long restUntil, lastAlarm = -1000, lastRang = -1;
        int age;
        BlockPos target;
        Entity patient;
        final List<BlockPos> rounds = new ArrayList<>();
        // The courier.
        BlockPos home, dest;
        Vec3 flyTo;
        int leg;               // 0 rising, 1 out, 2 at the far village, 3 back, 4 landing; 10+ a round of the village
        long awayUntil;
        Work(Job job) { this.job = job; }
        public Job job() { return job; }
        public Stage stage() { return stage; }
        public BlockPos target() { return target; }
        public int leg() { return leg; }
        public BlockPos dest() { return dest; }
    }

    private static final Map<Villager, Work> WORK = new WeakHashMap<>();

    private GiftedWork() {}

    /** The job a gift does. */
    public static Job job(ResourceLocation power) {
        if (power == null) return Job.LOOKOUT;
        if (power.equals(ModAbilities.PYROKINESIS_ID)) return Job.SMITH;
        if (power.equals(ModAbilities.HYDROKINESIS_ID)) return Job.WATERER;
        if (power.equals(ModAbilities.CRYOKINESIS_ID)) return Job.ICE_HOUSE;
        if (power.equals(ModAbilities.FLIGHT_ID)) return Job.COURIER;
        if (power.equals(ModAbilities.SUPERSPEED_ID)) return Job.POST;
        if (power.equals(ModAbilities.REGENERATION_ID)) return Job.HEALER;
        if (power.equals(ModAbilities.ELECTROKINESIS_ID) || power.equals(ModAbilities.TECHNOPATHY_ID)) return Job.KEEPER;
        if (power.equals(ModAbilities.TELEKINESIS_ID) || power.equals(ModAbilities.MATTER_ASSEMBLY_ID)) return Job.HARVESTER;
        if (power.equals(ModAbilities.QUAKE_ID) || power.equals(ModAbilities.PHASING_ID)) return Job.TILLER;
        if (power.equals(ModAbilities.SONIC_RESONANCE_ID)) return Job.BELL;
        return Job.LOOKOUT;
    }

    /** The job of a good gifted villager, or null for anyone else. */
    public static Job job(Villager v) { return GiftedMobs.isGood(v) ? job(GiftedMobs.powerOf(v)) : null; }

    /** {@code v} rests for {@code ticks} before looking for work (previews, tests). */
    public static void pause(Villager v, int ticks) {
        Job job = job(v);
        if (job == null) return;
        Work w = WORK.computeIfAbsent(v, k -> new Work(job));
        w.stage = Stage.RESTING;
        w.restUntil = v.level().getGameTime() + ticks;
    }

    /** What {@code v} is doing (tests, previews), or null. */
    public static Work of(Villager v) { return WORK.get(v); }

    // ------------------------------------------------------------------
    // The day
    // ------------------------------------------------------------------

    /** From {@code GiftedMob.idle}, every tick: a good gifted villager with no foe in sight. */
    public static void tick(Villager v, ResourceLocation power, BlockPos home) {
        if (!(v.level() instanceof ServerLevel level) || v.isBaby()) return;
        // A traveller (a passing healer) works for their party on the road, not for a village (travel/Travellers).
        if (v.getTags().contains(com.jayptucker.heroesevolved.travel.Travellers.TAG)) return;
        Work w = WORK.computeIfAbsent(v, k -> new Work(job(power)));
        if (w.stage == Stage.FLYING) { fly(level, v, w); return; }
        if (v.isSleeping() || v.getTags().contains(com.jayptucker.heroesevolved.company.CompanyOps.PRISONER_TAG)) { rest(level, w); return; }
        BlockPos bell = home != null ? home : Villages.bell(level, v.blockPosition());
        long now = level.getGameTime();
        // A long way from home (a freed prisoner, a courier who came down elsewhere): home first, work after.
        if (home != null && v.position().distanceToSqr(Vec3.atBottomCenterOf(home)) > HOME_REACH * HOME_REACH) {
            if (now % 20 == 0) goHome(level, v, w, home);
            return;
        }
        if (now % 100 == 0) Favours.tick(level, v, w.job);
        switch (w.stage) {
            case RESTING -> {
                if (now >= w.restUntil && now % 20 == 0 && onDuty(level, w)) begin(level, v, w, bell);
            }
            case GOING -> {
                w.age++;
                Vec3 to = w.patient != null ? w.patient.position() : Vec3.atBottomCenterOf(w.target);
                if (v.position().distanceToSqr(to) <= reach(w) * reach(w) || w.age > GIVE_UP) { w.stage = Stage.ACTING; w.age = 0; }
                else walk(v, to, w.job == Job.POST ? 2.6F : 0.6F);
                if (w.job == Job.POST && w.age % 3 == 0)
                    AbilityEffects.send(level, Kind.SPEED_TRAIL, v.position().add(0, 1, 0), v.position().add(v.getDeltaMovement().scale(4)).add(0, 1, 0), 0.6);
            }
            case ACTING -> act(level, v, w, bell);
            case FLYING -> { }
        }
    }

    /** Daylight work; the smith also lights the forge for the night; the bell ringer works dawn and dusk. */
    static boolean onDuty(ServerLevel level, Work w) {
        long t = level.getDayTime() % 24_000;
        return switch (w.job) {
            case SMITH -> t < 13_000;
            case BELL -> t < 1_500 || (t >= 11_800 && t < 13_000);
            case LOOKOUT -> t < 13_500;
            default -> t >= 500 && t < 11_500;
        };
    }

    private static double reach(Work w) {
        return switch (w.job) {
            case HEALER, KEEPER -> 3.5;
            case WATERER, TILLER, HARVESTER, ICE_HOUSE -> 4;
            case LOOKOUT -> 3;
            default -> 2.2;
        };
    }

    static void rest(ServerLevel level, Work w) {
        w.stage = Stage.RESTING;
        w.target = null;
        w.patient = null;
        w.rounds.clear();
        w.restUntil = level.getGameTime() + REST_MIN + level.random.nextInt(REST_MAX - REST_MIN + 1);
    }

    /** Looks for something to do; finding nothing, rests again. */
    static boolean begin(ServerLevel level, Villager v, Work w, BlockPos bell) {
        w.age = 0;
        w.patient = null;
        BlockPos near = jobSite(level, v, bell);
        switch (w.job) {
            case SMITH -> w.target = find(level, near, 16, s -> unlit(s));
            case WATERER -> w.target = find(level, bell, REACH, s -> s.getBlock() instanceof FarmBlock && s.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE);
            case ICE_HOUSE -> {
                w.target = find(level, near, 12, s -> s.getBlock() instanceof BarrelBlock);
                if (w.target == null) w.target = find(level, near, 12, s -> s.getBlock() instanceof ChestBlock);
                if (w.target != null && ice(level, w.target) >= ICE_CAP) w.target = null;
            }
            case HARVESTER -> w.target = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                    ? find(level, bell, REACH, s -> s.getBlock() instanceof CropBlock c && c.isMaxAge(s)) : null;
            case TILLER -> w.target = find(level, bell, REACH, s -> s.getBlock() instanceof CropBlock c && !c.isMaxAge(s));
            case HEALER -> {
                w.patient = hurt(level, v, LivingEntity.class);
                w.target = w.patient == null ? null : w.patient.blockPosition();
            }
            case KEEPER -> {
                IronGolem golem = nearest(level, v, IronGolem.class, 32);
                w.patient = golem;
                w.target = golem == null ? null : golem.blockPosition();
            }
            case BELL -> {
                long t = level.getDayTime() % 24_000, phase = level.getDayTime() / 12_000;
                w.target = level.getBlockState(bell).getBlock() instanceof BellBlock && w.lastRang != phase ? bell : null;
                if (w.target != null) w.lastRang = phase;
                if (t >= 1_500 && t < 11_800) w.target = null;
            }
            case POST -> {
                w.rounds.clear();
                level.getPoiManager().getInRange(p -> p.is(PoiTypes.HOME), bell, 48, PoiManager.Occupancy.ANY)
                        .map(r -> r.getPos()).limit(12).forEach(w.rounds::add);
                java.util.Collections.shuffle(w.rounds, new java.util.Random(level.random.nextLong()));
                while (w.rounds.size() > 5) w.rounds.removeLast();
                w.target = w.rounds.isEmpty() ? null : w.rounds.removeFirst();
            }
            case LOOKOUT -> {
                double a = level.random.nextDouble() * Math.PI * 2;
                w.target = Villages.bell(level, bell).offset((int) (Math.cos(a) * 20), 0, (int) (Math.sin(a) * 20));
                w.target = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, w.target);
            }
            case COURIER -> {
                takeOff(level, v, w, bell);
                return true;
            }
        }
        if (w.target == null) { rest(level, w); return false; }
        w.target = w.target.immutable();
        w.stage = Stage.GOING;
        return true;
    }

    /** Makes {@code v} do their job now, wherever they are (admin, tests). Returns false if there is nothing to do. */
    public static boolean now(Villager v) {
        if (!(v.level() instanceof ServerLevel level)) return false;
        Job job = job(v);
        if (job == null) return false;
        Work w = WORK.computeIfAbsent(v, k -> new Work(job));
        if (w.stage == Stage.FLYING) return true;
        BlockPos bell = GiftedMobs.of(v) != null && GiftedMobs.of(v).gift().home() != null ? GiftedMobs.of(v).gift().home() : Villages.bell(level, v.blockPosition());
        if (job == Job.BELL) w.lastRang = -1;
        return begin(level, v, w, bell);
    }

    /** Where their work is: their job site, or the bell. */
    static BlockPos jobSite(ServerLevel level, Villager v, BlockPos bell) {
        var brain = v.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.JOB_SITE)) {
            GlobalPos at = brain.getMemory(MemoryModuleType.JOB_SITE).orElse(null);
            if (at != null && at.dimension() == level.dimension()) return at.pos();
        }
        return bell;
    }

    /** Walks {@code v} toward {@code to}, whatever their own schedule wants. */
    static void walk(Villager v, Vec3 to, float speed) {
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, speed, 1));
    }

    /** Stands {@code v} still, looking at {@code at}. */
    static void hold(Villager v, Vec3 at) {
        var brain = v.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(at));
        v.getNavigation().stop();
    }

    // ------------------------------------------------------------------
    // Doing it
    // ------------------------------------------------------------------

    static void act(ServerLevel level, Villager v, Work w, BlockPos bell) {
        w.age++;
        Vec3 at = w.patient != null ? w.patient.getBoundingBox().getCenter() : Vec3.atCenterOf(w.target);
        if (w.patient != null) v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(w.patient, true));
        else hold(v, at);
        if (w.patient != null) { v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); v.getNavigation().stop(); }
        Vec3 hand = hand(v);
        switch (w.job) {
            case SMITH -> {
                if (w.age % 6 == 1) AbilityEffects.send(level, Kind.FIRE_STREAM, hand, at, 0.7);
                if (w.age == 2) level.playSound(null, w.target, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.6F, 1.2F);
                if (w.age == ACT / 2) light(level, w.target);
            }
            case WATERER -> {
                if (w.age == 1) SetPiecePayload.send(level, Piece.HYDRO_FLOW, hand, at.add(0, 0.6, 0), 1.0F, v);
                if (w.age == ACT / 2) {
                    SetPiecePayload.send(level, Piece.HYDRO_SPLASH, at.add(0, 0.5, 0), at.add(0, 1.5, 0), 1.6F, null);
                    level.playSound(null, w.target, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.1F);
                    water(level, w.target, 3);
                }
            }
            case ICE_HOUSE -> {
                if (w.age % 8 == 1) AbilityEffects.send(level, Kind.CRYO_RIME_STREAM, hand, at, 0.6);
                if (w.age == ACT / 2) {
                    AbilityEffects.send(level, Kind.CRYO_CHILL, at, at.add(0, 1, 0), 0.8);
                    level.playSound(null, w.target, SoundEvents.GLASS_PLACE, SoundSource.NEUTRAL, 0.7F, 1.4F);
                    freeze(level, w.target);
                }
            }
            case HARVESTER -> {
                if (w.age == 1) AbilityEffects.send(level, Kind.TK_GATHER, at, hand, 1);
                if (w.age == ACT / 2) harvest(level, v, w.target, 4);
            }
            case TILLER -> {
                if (w.age == 1) AbilityEffects.send(level, GiftedMobs.powerOf(v) != null && GiftedMobs.powerOf(v).equals(ModAbilities.PHASING_ID)
                        ? Kind.DENSITY_RIPPLE : Kind.QUAKE_PULSE, v.position(), at, 1.0);
                if (w.age == ACT / 2) {
                    level.playSound(null, v.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 0.8F, 0.6F);
                    grow(level, v.blockPosition(), 5, 0.5F);
                }
            }
            case HEALER -> {
                if (w.age == 1) AbilityEffects.send(level, Kind.REGEN_MEND, w.patient.position(), w.patient.getEyePosition(), 1.0);
                if (w.age == ACT / 2 && w.patient instanceof LivingEntity l && l.isAlive()) {
                    l.heal(6);
                    level.playSound(null, l.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.3F);
                }
            }
            case KEEPER -> {
                if (w.age % 5 == 1) AbilityEffects.send(level, Kind.ELECTRIC_ARC, hand, at, 0.8);
                if (w.age == ACT / 2 && w.patient instanceof IronGolem golem && golem.isAlive()) {
                    golem.heal(10);
                    level.playSound(null, golem.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.5F, 1.6F);
                }
            }
            case BELL -> {
                if (w.age == 1) AbilityEffects.send(level, Kind.SONIC_NOTE, hand, at, 1.0);
                if (w.age == ACT / 2) ring(level, v, w.target);
            }
            case POST -> {
                if (w.age == 2) {
                    level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), at.x, at.y + 0.5, at.z, 6, 0.3, 0.2, 0.3, 0.05);
                    level.playSound(null, w.target, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.8F, 1.3F);
                }
                if (w.age >= 12) {
                    if (w.rounds.isEmpty()) { rest(level, w); return; }
                    w.target = w.rounds.removeFirst();
                    w.stage = Stage.GOING;
                    w.age = 0;
                }
                return;
            }
            case LOOKOUT -> {
                if (w.age == 10) AbilityEffects.send(level, lookout(GiftedMobs.powerOf(v)), v.position(), v.getEyePosition(), 0.8);
            }
            case COURIER -> { }
        }
        if (w.age >= ACT) rest(level, w);
    }

    static Vec3 hand(Villager v) {
        Vec3 look = Vec3.directionFromRotation(0, v.getYRot());
        return v.position().add(0, 1.1, 0).add(look.scale(0.45));
    }

    private static Kind lookout(ResourceLocation power) {
        if (power == null) return Kind.PSYCHIC_FIELD;
        String p = power.getPath();
        if (p.contains("invisibility")) return Kind.OPTIC_RIPPLE;
        if (p.contains("time")) return Kind.TIME_SLOW_PULSE;
        if (p.contains("telepathy") || p.contains("vibe")) return Kind.MIND_PULSE;
        if (p.contains("radiation")) return Kind.RADIATION_PULSE;
        return Kind.PSYCHIC_FIELD;
    }

    /** The nearest block within {@code r} of {@code around} that matches, or null. */
    static BlockPos find(ServerLevel level, BlockPos around, int r, java.util.function.Predicate<BlockState> wanted) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-r, -4, -r), around.offset(r, 4, r))) {
            if (!level.isLoaded(p) || !wanted.test(level.getBlockState(p))) continue;
            double d = p.distSqr(around);
            if (d < bestD) { bestD = d; best = p.immutable(); }
        }
        return best;
    }

    static boolean unlit(BlockState s) {
        boolean forge = s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER);
        boolean fire = s.getBlock() instanceof CampfireBlock && !s.getValue(CampfireBlock.WATERLOGGED);
        return (forge || fire) && s.hasProperty(BlockStateProperties.LIT) && !s.getValue(BlockStateProperties.LIT);
    }

    /** The forge lit: it stays lit (a furnace with nothing burning keeps the look until something changes it). */
    static void light(ServerLevel level, BlockPos at) {
        BlockState s = level.getBlockState(at);
        if (!unlit(s) || PlayerBuilt.is(level, at)) return;
        level.setBlock(at, s.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.FLAME, at.getX() + .5, at.getY() + .8, at.getZ() + .5, 8, 0.25, 0.2, 0.25, 0.01);
    }

    /** Farmland round {@code at} soaked, and some crops on it a stage further. */
    static void water(ServerLevel level, BlockPos at, int r) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, 1, r))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof FarmBlock && s.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE)
                level.setBlock(p, s.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
        }
        grow(level, at, r, 0.3F);
    }

    /** Crops within {@code r} of {@code at}, each a stage further with chance {@code chance}. */
    static int grow(ServerLevel level, BlockPos at, int r, float chance) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 2, r))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof CropBlock c && !c.isMaxAge(s) && level.random.nextFloat() < chance) {
                level.setBlock(p, c.getStateForAge(c.getAge(s) + 1), Block.UPDATE_CLIENTS);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + .5, p.getY() + .5, p.getZ() + .5, 2, 0.2, 0.2, 0.2, 0);
                n++;
            }
        }
        return n;
    }

    /** Ice and packed ice in the container at {@code at}. */
    static int ice(ServerLevel level, BlockPos at) {
        if (!(level.getBlockEntity(at) instanceof Container box)) return ICE_CAP;
        int n = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack s = box.getItem(i);
            if (s.is(Items.ICE) || s.is(Items.PACKED_ICE)) n += s.getCount();
        }
        return n;
    }

    /** Two more ice in the ice house (one packed, now and then). */
    static void freeze(ServerLevel level, BlockPos at) {
        if (!(level.getBlockEntity(at) instanceof Container box) || ice(level, at) >= ICE_CAP) return;
        ItemStack add = new ItemStack(level.random.nextInt(3) == 0 ? Items.PACKED_ICE : Items.ICE, 2);
        for (int i = 0; i < box.getContainerSize() && !add.isEmpty(); i++) {
            ItemStack s = box.getItem(i);
            if (s.isEmpty()) { box.setItem(i, add.copy()); add.setCount(0); }
            else if (ItemStack.isSameItemSameComponents(s, add) && s.getCount() < s.getMaxStackSize()) {
                int moved = Math.min(add.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(moved);
                add.shrink(moved);
            }
        }
        box.setChanged();
    }

    /** Up to {@code most} ripe crops round {@code at} lifted out (their drops fall) and replanted. */
    static int harvest(ServerLevel level, Villager v, BlockPos at, int most) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2))) {
            if (n >= most) break;
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof CropBlock c) || !c.isMaxAge(s)) continue;
            BlockPos q = p.immutable();
            if (PlayerBuilt.is(level, q)) continue;   // a player's own crops are theirs to harvest
            level.destroyBlock(q, true, v);
            level.setBlock(q, c.getStateForAge(0), Block.UPDATE_ALL);
            AbilityEffects.send(level, Kind.TK_GATHER, Vec3.atCenterOf(q), Vec3.atCenterOf(q).add(0, 1.5, 0), 0.6);
            n++;
        }
        return n;
    }

    /** The bell rung: at dusk a real ring, which sends everyone indoors; at dawn its sound, to wake them. */
    static void ring(ServerLevel level, Villager v, BlockPos bell) {
        long t = level.getDayTime() % 24_000;
        if (t >= 11_800 && level.getBlockState(bell).getBlock() instanceof BellBlock b) b.attemptToRing(v, level, bell, null);
        else for (int i = 0; i < 2; i++) level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.4F, 1);
    }

    /** The hurt creature nearest {@code v} that belongs to the village: a villager, a golem or an animal. */
    static LivingEntity hurt(ServerLevel level, Villager v, Class<LivingEntity> type) {
        LivingEntity best = null;
        for (LivingEntity e : level.getEntitiesOfClass(type, v.getBoundingBox().inflate(REACH),
                e -> e != v && e.isAlive() && (e instanceof Villager || e instanceof IronGolem || e instanceof Animal)
                        && e.getHealth() < e.getMaxHealth() - 1))
            if (best == null || e.distanceToSqr(v) < best.distanceToSqr(v)) best = e;
        return best;
    }

    static <T extends Entity> T nearest(ServerLevel level, Villager v, Class<T> type, double r) {
        T best = null;
        for (T e : level.getEntitiesOfClass(type, v.getBoundingBox().inflate(r), Entity::isAlive))
            if (best == null || e.distanceToSqr(v) < best.distanceToSqr(v)) best = e;
        return best;
    }

    /** From {@code GiftedMob}, every two seconds, fighting or not: a lookout shouts when monsters come near. */
    public static void alarm(Villager v, ResourceLocation power, BlockPos home) {
        if (!(v.level() instanceof ServerLevel level) || v.isSleeping() || job(power) != Job.LOOKOUT) return;
        Work w = WORK.computeIfAbsent(v, k -> new Work(Job.LOOKOUT));
        watch(level, v, w, home != null ? home : Villages.bell(level, v.blockPosition()));
    }

    /** The lookout shouts when monsters come near the village. */
    static void watch(ServerLevel level, Villager v, Work w, BlockPos bell) {
        long now = level.getGameTime();
        if (now - w.lastAlarm < 600) return;
        Mob monster = null;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(bell).inflate(REACH, 8, REACH), m -> m instanceof Enemy && m.isAlive()))
            if (monster == null || m.distanceToSqr(Vec3.atCenterOf(bell)) < monster.distanceToSqr(Vec3.atCenterOf(bell))) monster = m;
        if (monster == null) return;
        w.lastAlarm = now;
        Shouts.say(level, v, plural(monster.getType().getDescription().getString()) + " at the " + compass(bell, monster.blockPosition()) + " edge!", 80);
        level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.2F, 0.8F);
    }

    /** "Zombies", "Witches". */
    static String plural(String what) {
        if (what.endsWith("s")) return what;
        if (what.endsWith("ch") || what.endsWith("sh") || what.endsWith("x")) return what + "es";
        return what + "s";
    }

    static String compass(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        String[] points = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return points[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    // ------------------------------------------------------------------
    // The courier
    // ------------------------------------------------------------------

    /**
     * Up and away with the post: to the nearest other village within {@link #COURIER_RANGE}, or (with none) a round of
     * their own village. While anyone can see them they really fly; out of sight (or with the way ahead not loaded) they
     * are away over the horizon for {@link #AWAY_TICKS}, parked out of the world, and come back from that way.
     */
    static void takeOff(ServerLevel level, Villager v, Work w, BlockPos bell) {
        w.home = bell.immutable();
        w.dest = destination(level, bell);
        w.stage = Stage.FLYING;
        w.leg = w.dest == null ? 10 : 0;
        w.age = 0;
        v.addTag(COURIER_TAG);
        v.setNoAi(true);
        v.setNoGravity(true);
        AbilityEffects.send(level, Kind.FLIGHT_BURST, v.position(), v.position().add(0, 1, 0), 1.0);
        level.playSound(null, v.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 1, 1.2F);
        if (w.dest != null) BandChatter.news(level, bell, "Courier's left " + VillageMemory.of(level, bell).name() + " for "
                + VillageMemory.of(level, w.dest).name() + " with the post.", 6000);
    }

    /** The nearest other bell within {@link #COURIER_RANGE} of {@code bell} (a remembered village, or one nearby). */
    static BlockPos destination(ServerLevel level, BlockPos bell) {
        BlockPos best = null;
        long bestD = (long) COURIER_RANGE * COURIER_RANGE;
        for (VillageMemory.Village v : VillageMemory.all(level)) {
            long d = (long) v.bell().distSqr(bell);
            if (!v.fallen() && d > 64L * 64 && d < bestD) { bestD = d; best = v.bell(); }
        }
        if (best != null) return best;
        return level.getPoiManager().getInRange(p -> p.is(PoiTypes.MEETING), bell, 160, PoiManager.Occupancy.ANY)
                .map(r -> r.getPos()).filter(p -> p.distSqr(bell) > 64L * 64)
                .min(java.util.Comparator.comparingDouble(p -> p.distSqr(bell))).orElse(null);
    }

    static void fly(ServerLevel level, Villager v, Work w) {
        w.age++;
        long now = level.getGameTime();
        double cruise = Math.max(w.home.getY(), w.dest == null ? w.home.getY() : w.dest.getY()) + 22;
        switch (w.leg) {
            case 0, 3 -> {   // rising, then on the way (out or back)
                BlockPos to = w.leg == 0 ? w.dest : w.home;
                Vec3 at = v.position();
                if (at.y < cruise - 1) { move(v, at.add(0, Math.min(0.4, cruise - at.y), 0)); return; }
                Vec3 goal = new Vec3(to.getX() + .5, cruise, to.getZ() + .5);
                Vec3 step = goal.subtract(at);
                if (step.horizontalDistanceSqr() < 4) { w.leg = w.leg == 0 ? 2 : 4; w.age = 0; return; }
                Vec3 next = at.add(step.normalize().scale(CRUISE));
                // Out of sight, or the way ahead not running: over the horizon.
                if (!level.isPositionEntityTicking(BlockPos.containing(next)) || (seen(level, v) == false && at.distanceTo(Vec3.atCenterOf(w.home)) > 48)) {
                    park(level, v, w);
                    return;
                }
                move(v, next);
                if (w.age % 4 == 0) level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0);
            }
            case 2 -> {   // at the far village: down, hand over the post, back up
                Vec3 at = v.position();
                Vec3 ground = Vec3.atBottomCenterOf(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, w.dest));
                if (w.age < 200 && at.y > ground.y + 0.1) { move(v, at.add(0, -Math.min(0.35, at.y - ground.y), 0)); return; }
                if (w.age == 200 || (w.age < 200 && at.y <= ground.y + 0.1)) {
                    if (w.age < 200) w.age = 200;
                    Shouts.talk(level, v, "Post from " + VillageMemory.of(level, w.home).name() + "!", 80);
                    level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), at.x, at.y + 1, at.z, 10, 0.4, 0.3, 0.4, 0.05);
                }
                if (w.age > 300) { w.leg = 3; w.age = 0; }
            }
            case 4 -> land(level, v, w, w.home);
            case 5 -> {   // away, parked out of sight
                if (now < w.awayUntil) return;
                unpark(level, v, w);
            }
            default -> {  // a round of their own village: a slow circle over the roofs, then down
                double a = w.age * 0.02;
                Vec3 centre = Vec3.atCenterOf(w.home);
                if (w.age > 600) { land(level, v, w, w.home); return; }
                Vec3 goal = new Vec3(centre.x + Math.cos(a) * 18, w.home.getY() + 12, centre.z + Math.sin(a) * 18);
                Vec3 at = v.position(), step = goal.subtract(at);
                move(v, step.length() > CRUISE ? at.add(step.normalize().scale(CRUISE)) : goal);
            }
        }
    }

    private static boolean seen(ServerLevel level, Villager v) { return seen(level, v, 128); }

    /** One tick of flight: moved, facing the way they go. */
    static void move(Villager v, Vec3 to) {
        Vec3 d = to.subtract(v.position());
        if (d.horizontalDistanceSqr() > 1.0E-4) {
            float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
            v.setYRot(yaw);
            v.setYBodyRot(yaw);
            v.setYHeadRot(yaw);
        }
        v.setDeltaMovement(d);
        v.setPos(to.x, to.y, to.z);
        v.fallDistance = 0;
    }

    /** Down to the ground at {@code at}, and back to village life. */
    static void land(ServerLevel level, Villager v, Work w, BlockPos at) {
        Vec3 ground = Vec3.atBottomCenterOf(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at))
                .add(1.5, 0, 1.5);
        Vec3 here = v.position();
        if (here.y > ground.y + 0.1 && w.age < 400) {
            Vec3 step = ground.subtract(here);
            move(v, here.add(step.length() > 0.4 ? step.normalize().scale(0.4) : step));
            return;
        }
        ground(v);
        AbilityEffects.send(level, Kind.FLIGHT_BURST, v.position(), v.position().add(0, 1, 0), 0.7);
        rest(level, w);
    }

    /** Over the horizon: out of the world for a while (invisible, still, high above home), coming back later. */
    static void park(ServerLevel level, Villager v, Work w) {
        w.leg = 5;
        w.awayUntil = level.getGameTime() + AWAY_TICKS;
        v.setInvisible(true);
        v.getPersistentData().putBoolean(COURIER_TAG + "_parked", true);
        move(v, Vec3.atCenterOf(w.home).add(0, 60, 0));
    }

    /** Back from over the horizon, coming in from the way they went. */
    static void unpark(ServerLevel level, Villager v, Work w) {
        v.setInvisible(false);
        v.getPersistentData().remove(COURIER_TAG + "_parked");
        Vec3 home = Vec3.atCenterOf(w.home);
        Vec3 from = w.dest != null ? Vec3.atCenterOf(w.dest).subtract(home).multiply(1, 0, 1).normalize().scale(44) : new Vec3(30, 0, 0);
        Vec3 start = home.add(from).add(0, 22, 0);
        if (!level.isPositionEntityTicking(BlockPos.containing(start))) start = home.add(0, 22, 0);
        move(v, start);
        w.leg = w.dest == null ? 4 : 3;
        w.age = 0;
    }

    /**
     * Back toward home: walking while anyone can see them; with nobody near and home loaded and ticking, they are simply
     * there (nobody saw the walk). With home not loaded they wait, and go when it is.
     */
    static void goHome(ServerLevel level, Villager v, Work w, BlockPos home) {
        if (w.stage != Stage.RESTING) rest(level, w);
        if (seen(level, v, 64)) { walk(v, Vec3.atBottomCenterOf(home), 0.6F); return; }
        if (!level.isPositionEntityTicking(home)) return;
        BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.offset(2, 0, 2));
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        v.getNavigation().stop();
        v.teleportTo(ground.getX() + .5, ground.getY(), ground.getZ() + .5);
    }

    /** Whether any player is within {@code r} of {@code v}. */
    static boolean seen(ServerLevel level, Villager v, double r) {
        for (var p : level.players()) if (!p.isSpectator() && p.distanceToSqr(v) <= r * r) return true;
        return false;
    }

    /** Feet back on the ground, AI back on. */
    static void ground(Villager v) {
        v.setNoAi(false);
        v.setNoGravity(false);
        v.setInvisible(false);
        v.removeTag(COURIER_TAG);
        v.getPersistentData().remove(COURIER_TAG + "_parked");
        v.setDeltaMovement(Vec3.ZERO);
    }

    /** Away now, as if a long way off (tests). */
    public static void away(Villager v) {
        Work w = WORK.get(v);
        if (w != null && w.stage == Stage.FLYING && v.level() instanceof ServerLevel level) park(level, v, w);
    }

    /** Back now from over the horizon (tests, previews). */
    public static void hurry(Villager v) {
        Work w = WORK.get(v);
        if (w != null && w.leg == 5) w.awayUntil = 0;
    }

    /**
     * A courier saved mid-flight (a restart, an unloaded chunk) loads with their AI off and floating: put them back on
     * their feet, at home if they were away over the horizon.
     */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Villager v) || event.getLevel().isClientSide() || !v.getTags().contains(COURIER_TAG)) return;
        Work w = WORK.get(v);
        if (w != null && w.stage == Stage.FLYING) return;
        boolean parked = v.getPersistentData().getBoolean(COURIER_TAG + "_parked");
        ground(v);
        if (parked && event.getLevel() instanceof ServerLevel level) {
            BlockPos below = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, v.blockPosition());
            v.setPos(below.getX() + 1.5, below.getY(), below.getZ() + 1.5);
        }
    }

    public static void forget(Villager v) {
        Work w = WORK.remove(v);
        if (w != null && w.stage == Stage.FLYING) ground(v);
    }
}
