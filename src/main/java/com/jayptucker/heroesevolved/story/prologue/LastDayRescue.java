package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The people on the sidewalks by the City Market when Peter and Sylar's fight comes down the avenue: one for every two
 * players. They stand staring at the light until the chunks Sylar throws smash into the facades above them and bring the
 * stone down ({@link LastDayFight#pinnedAt}): then they're on the ground under it, calling for help.
 * <ul>
 *   <li><b>Dig them out.</b> Their legs are buried in gravel, more of it heaped on top and against them: it breaks by hand
 *   in under a second (and drops nothing in the city), and what's heaped above slides down to fill the hole, as gravel
 *   does. When the last of it is off their legs they're free, but they can't stand.</li>
 *   <li><b>Carry them.</b> Sneak and right-click them, or the ground beside them ({@link #lift}): they're stood up into the
 *   ordinary two-arm carry, no clear sight of a body lying among the stones needed. Take them down Primatech's stair.
 *   Set down, or dropped when the carrier is darted, they lie where they are until someone picks them up again.</li>
 *   <li><b>Saved.</b> Down past the turn of the stair they're safe; in the stasis hall they're laid by the wall.</li>
 * </ul>
 * Whoever is still out when the agents dart the stragglers is lost. The stone is logged in {@link LastDayDamage} and goes
 * when the day ends.
 */
final class LastDayRescue {
    enum State { WAITING, PINNED, FREE, CARRIED, SAVED, LOST }

    /** Their names: ordinary people on an ordinary evening. */
    static final String[] NAMES = {"Marisol, a cashier", "Mr. Pruitt", "Gus, a welder", "June, a nurse", "Ray, a night clerk",
            "Nell, a retired teacher"};
    private static final String[] CRIES = {"Help! Somebody, please!", "I can't move my legs!", "Is anyone there? Help me!",
            "Please... it's on my chest...", "Don't leave me here!"};

    static final class Victim {
        final int index;
        final String name;
        final Villager body;
        final Vec3 spot;
        State state = State.WAITING;
        /** The stone on them: every piece must go before they're free. */
        final Set<BlockPos> pins = new LinkedHashSet<>();
        UUID carrier;
        int nextCry, bubbleUntil, slot;
        /** Has asked to be taken to Primatech; is lying in the stasis hall. */
        boolean laid, inHall;
        /** A cry over their head; it rides them, so it must go before anyone can lift them. */
        net.minecraft.world.entity.Display.TextDisplay bubble;

        void hush() { if (bubble != null) { bubble.discard(); bubble = null; } }

        Victim(int index, String name, Villager body, Vec3 spot) { this.index = index; this.name = name; this.body = body; this.spot = spot; }
        boolean waiting() { return state == State.PINNED || state == State.FREE || state == State.CARRIED; }
        /** Their name without the description, for the story's own lines ("Marisol", "Mr. Pruitt"). */
        String shortName() { int comma = name.indexOf(','); return comma < 0 ? name : name.substring(0, comma); }
    }

    final List<Victim> victims = new ArrayList<>();
    /** Every block of rubble the day put down (pins and the loose stone round them): the only blocks anyone may break. */
    final Set<BlockPos> rubble = new LinkedHashSet<>();
    final LastDayFight.Facades facades;
    private final LastDayDamage damage;
    /** Which facades Sylar has torn, and which chunks have hit. */
    private final boolean[] torn = new boolean[2], smashed = new boolean[2];

    private LastDayRescue(LastDayFight.Facades facades, LastDayDamage damage) { this.facades = facades; this.damage = damage; }

    /** How many people the rubble catches for a group this size: one for every two players. */
    static int count(int players) { return Math.clamp((players + 1) / 2, 1, NAMES.length); }

    /** Puts the people on the sidewalks, staring up the avenue at the light. */
    static LastDayRescue start(ServerLevel city, LastDayFight.Facades facades, int players, LastDayDamage damage, String tag) {
        LastDayRescue rescue = new LastDayRescue(facades, damage);
        List<Vec3> spots = LastDayFight.victims(facades, count(players));
        for (int i = 0; i < spots.size(); i++) {
            Vec3 spot = pileSpot(city, spots.get(i));
            Villager body = com.jayptucker.heroesevolved.village.Villagers.actor(city);
            if (body == null) continue;
            body.moveTo(spot.x, spot.y, spot.z, 180, 0);
            body.setYHeadRot(180);
            body.setYBodyRot(180);
            VillagerType[] looks = {VillagerType.PLAINS, VillagerType.SAVANNA, VillagerType.TAIGA, VillagerType.DESERT, VillagerType.SNOW, VillagerType.JUNGLE};
            VillagerProfession[] jobs = {VillagerProfession.NONE, VillagerProfession.MASON, VillagerProfession.CLERIC, VillagerProfession.LIBRARIAN,
                    VillagerProfession.NITWIT, VillagerProfession.CARTOGRAPHER};
            body.setVillagerData(body.getVillagerData().setType(looks[i % looks.length]).setProfession(jobs[i % jobs.length]).setLevel(2));
            body.setVillagerXp(1);
            body.setNoAi(true);
            body.setInvulnerable(true);
            body.setSilent(true);
            body.setPersistenceRequired();
            body.setCustomName(Component.literal(NAMES[i]));
            body.addTag(tag);
            if (!city.addFreshEntity(body)) continue;
            rescue.victims.add(new Victim(i, NAMES[i], body, spot));
        }
        return rescue;
    }

    /** The nearest spot to {@code at} with a floor and two blocks of air above it, so nobody stands in a lamp post. */
    static Vec3 standable(ServerLevel city, Vec3 at) {
        for (int r = 0; r <= 3; r++)
            for (int dz = -r; dz <= r; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (Math.abs(dz) != r && Math.abs(dx) != r) continue;
                    BlockPos feet = BlockPos.containing(at.x + dx, at.y, at.z + dz);
                    if (clear(city, feet) && clear(city, feet.offset(0, 0, 1)) && !city.getBlockState(feet.below()).getCollisionShape(city, feet.below()).isEmpty())
                        return new Vec3(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
                }
        return at;
    }

    /**
     * Where someone can lie with their legs buried: room for them (and on the road side, for whoever comes to dig), and
     * every cell of the gravel over their legs empty with something solid under it, so the pile stays where it lands.
     */
    static Vec3 pileSpot(ServerLevel city, Vec3 at) {
        int side = side(at.x);
        for (int r = 0; r <= 4; r++)
            for (int dz = -r; dz <= r; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (Math.abs(dz) != r && Math.abs(dx) != r) continue;
                    BlockPos feet = BlockPos.containing(at.x + dx, at.y, at.z + dz);
                    BlockPos legs = feet.offset(side, 0, 0);
                    if (clear(city, feet) && clear(city, feet.offset(-side, 0, 0)) && sturdy(city, feet.below())
                            && open(city, legs) && open(city, legs.above()) && sturdy(city, legs.below())
                            && open(city, legs.offset(0, 0, 1)) && sturdy(city, legs.offset(0, -1, 1)))
                        return new Vec3(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
                }
        return standable(city, at);
    }

    /** Which way the building is from a sidewalk spot: +1 east of the avenue's middle, -1 west. */
    static int side(double x) { return x > 4 ? 1 : -1; }

    private static boolean open(ServerLevel city, BlockPos pos) {
        BlockState state = city.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    /** Something under it that gravel won't fall through (the sidewalks are slabs, which hold it fine). */
    private static boolean sturdy(ServerLevel city, BlockPos pos) {
        BlockState state = city.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static boolean clear(ServerLevel city, BlockPos feet) {
        return city.getBlockState(feet).getCollisionShape(city, feet).isEmpty() && city.getBlockState(feet.above()).getCollisionShape(city, feet.above()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Every tick of the light
    // ------------------------------------------------------------------

    /**
     * {@code t}: ticks since the light. {@code inside}: whether a player is down past the turn of the stair. {@code hall}:
     * where in the stasis hall to lay the {@code k}th saved person. {@code listeners}: who hears what they say.
     */
    void tick(ServerLevel city, int t, Predicate<ServerPlayer> inside, java.util.function.IntFunction<Vec3> hall, List<ServerPlayer> listeners) {
        // Once each, even if the day jumps past the very tick (a preview, a test).
        for (int which = 0; which < 2; which++) {
            if (!torn[which] && t >= (which == 0 ? LastDayFight.TEAR_1 : LastDayFight.TEAR_2)) { torn[which] = true; tear(city, which); }
            if (!smashed[which] && t >= (which == 0 ? LastDayFight.RUBBLE_EAST : LastDayFight.RUBBLE_WEST)) { smashed[which] = true; smash(city, which, listeners); }
        }
        for (Victim v : victims) {
            if (v.body.isRemoved()) continue;
            if (v.bubble != null && (t >= v.bubbleUntil || v.bubble.isRemoved())) v.hush();
            switch (v.state) {
                case WAITING -> {
                    // Staring at the light, until the stone comes down on them.
                    if (t >= LastDayFight.pinnedAt(v.index)) pin(city, v, listeners);
                }
                case PINNED -> {
                    v.pins.removeIf(pos -> {
                        if (!city.getBlockState(pos).isAir()) return false;
                        com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.debug("Last Day: the gravel at {} is off {} (tick {})", pos.toShortString(), v.name, t);
                        return true;
                    });
                    if (v.pins.isEmpty()) {
                        v.hush();
                        v.state = State.FREE;
                        say(listeners, v, "I'm out... I can't stand. Please, don't leave me here.");
                        tell(listeners, Component.translatable("prologue.heroesevolved.freed", v.shortName()).withStyle(ChatFormatting.GOLD));
                        city.playSound(null, v.body.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.8F, 0.9F);
                    } else if (t >= v.nextCry && t > PrologueTimeline.SIRENS) cry(city, v, t);
                }
                case FREE -> {
                    ServerPlayer carrier = carrier(city, v);
                    if (carrier != null) {
                        v.state = State.CARRIED;
                        v.carrier = carrier.getUUID();
                        v.body.setPose(Pose.STANDING);
                        carrier.sendSystemMessage(Component.translatable("prologue.heroesevolved.picked_up", v.shortName()).withStyle(ChatFormatting.GRAY));
                        if (!v.laid) say(listeners, v, "Primatech... everyone's going to Primatech. The stairs in the lobby.");
                        v.laid = true;
                    } else if (v.body.getPose() != Pose.SLEEPING) lie(v);
                }
                case CARRIED -> {
                    ServerPlayer carrier = carrier(city, v);
                    if (carrier == null) {
                        // Set down, or dropped: on the ground again until someone lifts them.
                        v.state = State.FREE;
                        v.carrier = null;
                        lie(v);
                    } else if (inside.test(carrier)) {
                        v.state = State.SAVED;
                        v.slot = savedSoFar++;
                        carrier.sendSystemMessage(Component.translatable("prologue.heroesevolved.saved", v.shortName()).withStyle(ChatFormatting.GOLD));
                        say(listeners, v, "You came back for me. You didn't have to.");
                    }
                }
                default -> { }
            }
        }
        // Down in the hall (or once Noah's scene begins: {@link #layAll}), the saved are laid by the wall.
        for (Victim v : victims) {
            if (v.state != State.SAVED || v.inHall || v.body.isRemoved()) continue;
            ServerPlayer carrier = carrier(city, v);
            if (carrier != null && !inHall.test(carrier)) continue;
            lay(v, hall);
        }
    }

    /** Where carried people count as down in the hall; set by the day each tick. */
    Predicate<ServerPlayer> inHall = p -> false;
    private int savedSoFar;

    /** Everyone saved is laid in the hall now, out of whoever's arms they're still in (Noah's scene is starting). */
    void layAll(java.util.function.IntFunction<Vec3> hall) {
        for (Victim v : victims) if (v.state == State.SAVED && !v.inHall && !v.body.isRemoved()) lay(v, hall);
    }

    private void lay(Victim v, java.util.function.IntFunction<Vec3> hall) {
        ServerPlayer carrier = EntityCarryService.carrierOf(v.body);
        if (carrier != null) EntityCarryService.release(carrier);
        Vec3 at = hall.apply(v.slot);
        v.carrier = null;
        v.inHall = true;
        v.body.stopRiding();
        v.body.teleportTo(at.x, at.y, at.z);
        v.body.setYRot(90);
        v.body.setYBodyRot(90);
        v.body.setNoAi(true);
        v.body.setNoGravity(false);
        lie(v);
    }

    private static ServerPlayer carrier(ServerLevel city, Victim v) { return EntityCarryService.carrierOf(v.body); }

    /** The day is over for whoever is still out on the street. */
    void deadline(List<ServerPlayer> listeners, Predicate<ServerPlayer> inside) {
        boolean lost = false;
        for (Victim v : victims) {
            if (!v.waiting()) continue;
            // Carried down past the turn on this very tick: saved, not lost.
            ServerPlayer carrier = carrier(null, v);
            if (v.state == State.CARRIED && carrier != null && inside.test(carrier)) {
                v.state = State.SAVED;
                v.slot = savedSoFar++;
                continue;
            }
            v.state = State.LOST;
            lost = true;
        }
        Component line = Component.translatable(lost ? "prologue.heroesevolved.left_behind" : "prologue.heroesevolved.all_saved")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        if (!victims.isEmpty()) tell(listeners, line);
    }

    /** Whether a player may break this block: only the stone the day put down. */
    boolean breakable(BlockPos pos) { return rubble.contains(pos); }

    /** Someone still trapped (to send players to), nearest {@code from}; null if nobody is. */
    Victim nearestPinned(Vec3 from) {
        Victim best = null;
        for (Victim v : victims)
            if (v.state == State.PINNED && (best == null || v.body.distanceToSqr(from) < best.body.distanceToSqr(from))) best = v;
        return best;
    }

    /** Someone freed and waiting to be carried, nearest {@code from}. */
    Victim nearestFree(Vec3 from) {
        Victim best = null;
        for (Victim v : victims)
            if (v.state == State.FREE && (best == null || v.body.distanceToSqr(from) < best.body.distanceToSqr(from))) best = v;
        return best;
    }

    boolean carrying(ServerPlayer player) {
        for (Victim v : victims) if (v.state == State.CARRIED && player.getUUID().equals(v.carrier)) return true;
        return false;
    }

    int saved() { return (int) victims.stream().filter(v -> v.state == State.SAVED).count(); }

    /**
     * Those carried down to the hall, let go of: no longer this rescue's, so {@link #discard} leaves them (they go into the
     * pods, {@link LastDayShelter#seal}).
     */
    List<Villager> takeSaved() {
        List<Villager> saved = new ArrayList<>();
        victims.removeIf(v -> {
            if (v.state != State.SAVED || v.body.isRemoved()) return false;
            v.hush();
            saved.add(v.body);
            return true;
        });
        return saved;
    }

    void discard() { victims.forEach(v -> { v.hush(); if (!v.body.isRemoved()) { v.body.ejectPassengers(); v.body.discard(); } }); }

    // ------------------------------------------------------------------
    // The stone
    // ------------------------------------------------------------------

    /** What comes down on them: gravel and grit from the facade, which anyone can dig by hand. */
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();

    /** Sylar's grip tears a chunk out of the facade: a ragged hole, a burst of dust and grit. */
    private void tear(ServerLevel city, int which) {
        Vec3 at = which == 0 ? facades.grabWest() : facades.grabEast();
        int dx = which == 0 ? -1 : 1;
        BlockPos face = BlockPos.containing(at.x + dx * 0.6, at.y, at.z);
        BlockState first = null;
        for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++)
                for (int depth = 0; depth <= 1; depth++) {
                    if (Math.abs(dy) + Math.abs(dz) == 2 && depth == 1) continue;
                    BlockPos pos = face.offset(dx * depth, dy, dz);
                    BlockState state = city.getBlockState(pos);
                    if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(city, pos) < 0) continue;
                    if (first == null) first = state;
                    damage.set(city, pos, Blocks.AIR.defaultBlockState());
                }
        BlockState dust = first != null ? first : Blocks.STONE_BRICKS.defaultBlockState();
        city.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), at.x, at.y, at.z, 60, 0.8, 0.8, 0.8, 0.15);
        city.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 8, 0.6, 0.6, 0.6, 0.01);
        city.playSound(null, face, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 3, 0.55F);
        city.playSound(null, face, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2, 0.8F);
    }

    /** The chunk smashes into the facade across the street: stone and glass come down on the sidewalk below it. */
    private void smash(ServerLevel city, int which, List<ServerPlayer> listeners) {
        Vec3 at = LastDayFight.impact(which, facades);
        int dx = which == 0 ? 1 : -1;
        BlockPos face = BlockPos.containing(at.x + dx * 0.6, at.y, at.z);
        for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = face.offset(0, dy, dz);
                BlockState state = city.getBlockState(pos);
                if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(city, pos) < 0) continue;
                damage.set(city, pos, Blocks.AIR.defaultBlockState());
            }
        city.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        city.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), at.x, at.y, at.z, 120, 1.4, 1.4, 1.4, 0.3);
        city.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), at.x, at.y - 1, at.z, 60, 1.2, 0.8, 1.6, 0.2);
        city.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y - 2, at.z, 20, 1.4, 1.4, 1.4, 0.02);
        city.playSound(null, face, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4, 0.75F);
        city.playSound(null, face, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 4, 0.7F);
        city.playSound(null, face, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 4, 0.6F);
        for (ServerPlayer p : listeners)
            if (p.level() == city && p.distanceToSqr(at) < 48 * 48)
                PacketDistributor.sendToPlayer(p, new CameraShakePayload((float) (0.35 * (1 - Math.sqrt(p.distanceToSqr(at)) / 48)), 18));
        if (which == 0) tell(listeners, Component.translatable("prologue.heroesevolved.rubble").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** The stone lands on them: down on their back under it. */
    private void pin(ServerLevel city, Victim v, List<ServerPlayer> listeners) {
        v.state = State.PINNED;
        BlockPos feet = BlockPos.containing(v.spot);
        v.body.teleportTo(v.spot.x, v.spot.y, v.spot.z);
        // On their back, head toward the road, legs toward the building the stone came off.
        int side = side(v.spot.x);
        float yaw = side > 0 ? LYING_YAW : LYING_YAW - 180;
        v.body.setYRot(yaw);
        v.body.setYBodyRot(yaw);
        v.body.setYHeadRot(yaw);
        lie(v);
        // Their legs buried in gravel with more heaped on top (it slides down into the hole as the lower gravel is dug), and
        // a second heap beside it; loose gravel round about that needn't be moved. The road side stays open, so whoever
        // comes to dig can reach them.
        BlockPos legs = feet.offset(side, 0, 0);
        List<BlockPos> pins = List.of(legs, legs.above(), legs.offset(0, 0, 1));
        List<BlockPos> loose = List.of(legs.offset(0, 1, 1), legs.offset(0, 0, -1), feet.offset(0, 0, 1));
        for (BlockPos pos : pins) if (place(city, pos, GRAVEL)) v.pins.add(pos);
        for (BlockPos pos : loose) place(city, pos, GRAVEL);
        place(city, feet.offset(-side, 0, 2), Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
        com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.debug("Last Day: {} pinned at {} by {}", v.name, feet.toShortString(),
                v.pins.stream().map(pos -> pos.toShortString() + "=" + city.getBlockState(pos).getBlock().getName().getString()
                        + " over " + city.getBlockState(pos.below()).getBlock().getName().getString()).toList());
        city.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, GRAVEL), v.spot.x, v.spot.y + 1, v.spot.z + 1, 50, 0.8, 0.5, 0.8, 0.12);
        city.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, v.spot.x, v.spot.y + 0.5, v.spot.z, 6, 0.8, 0.3, 0.8, 0.01);
        city.playSound(null, feet, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 2.5F, 0.6F);
        city.playSound(null, feet, SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.2F, 0.9F);
        say(listeners, v, CRIES[v.index % 2]);
        v.nextCry = PrologueTimeline.SIRENS + 40 + v.index * 30;
        // A pile whose pieces all fell somewhere else (a lamp post, a bench) still holds them down by one.
        if (v.pins.isEmpty() && place(city, legs, GRAVEL)) v.pins.add(legs);
    }

    /** Stone where there's room for it (air, or a plant the street's dressing put there). */
    private boolean place(ServerLevel city, BlockPos pos, BlockState state) {
        BlockState there = city.getBlockState(pos);
        if (!there.isAir() && !there.canBeReplaced()) return false;
        damage.set(city, pos, state);
        rubble.add(pos.immutable());
        return true;
    }

    /**
     * Which way they lie, east of the avenue: head toward the road (west), legs east toward the building, where the gravel
     * buries them; west of it, turned round. (A body lying in the sleeping pose with no bed lies along its yaw this way.)
     */
    static final float LYING_YAW = 270;

    /** On their back where they are. */
    private static void lie(Victim v) { v.body.setPose(Pose.SLEEPING); }

    /** Someone in {@code state} within {@code reach} of {@code at}, nearest first; null if nobody is. */
    Victim near(Vec3 at, double reach, State state) {
        Victim best = null;
        for (Victim v : victims)
            if (v.state == state && !v.body.isRemoved() && v.body.position().distanceToSqr(at) <= reach * reach
                    && (best == null || v.body.position().distanceToSqr(at) < best.body.position().distanceToSqr(at))) best = v;
        return best;
    }

    /**
     * Lifts {@code v} into {@code player}'s arms. They're stood up first, so the carry takes them as a person (a body lying
     * flat is only a hand's breadth tall, and would be held against the chest like a cat), and someone lying among the
     * stones at your feet needn't be in clear sight of your eyes.
     */
    boolean lift(ServerPlayer player, Victim v) {
        if (v.state != State.FREE) return false;
        v.body.setPose(Pose.STANDING);
        if (EntityCarryService.pickUpBeside(player, v.body)) return true;
        lie(v);
        return false;
    }

    /** A cry for help over their head (the street's voice, not a story line): every few seconds while they're under it. */
    private static void cry(ServerLevel city, Victim v, int t) {
        v.hush();
        v.bubble = com.jayptucker.heroesevolved.effects.SpeechBubble.show(city, v.body, CRIES[(t / 7 + v.index) % CRIES.length], true, PrologueService.TAG);
        v.bubbleUntil = t + 60;
        city.playSound(null, v.body.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.9F, 0.85F + (v.index % 3) * 0.08F);
        v.nextCry = t + 150 + v.index * 13;
    }

    private static void say(List<ServerPlayer> listeners, Victim v, String line) {
        Component chat = Component.literal("<").append(Component.literal(v.name).withStyle(ChatFormatting.YELLOW)).append(Component.literal("> " + line));
        tell(listeners, chat);
    }

    private static void tell(List<ServerPlayer> listeners, Component line) { for (ServerPlayer p : listeners) p.sendSystemMessage(line); }
}
