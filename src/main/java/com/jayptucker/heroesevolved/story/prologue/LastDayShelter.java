package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.BunkerLayout;
import com.jayptucker.heroesevolved.story.bunker.PodSleepers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Odessa's people taking shelter under Primatech on the Last Day. The crowd runs for Primatech when the sirens go
 * ({@code time/intro/VisionCrowd}); at the head of the stair in Primatech's lobby each runner is handed here and goes on
 * down, the way the players go (the thread of light's own maps, {@link LastDayGuide#route}): the stair, the vault, the
 * corridor, and into the stasis hall, where they stand along the walls out of the aisle with everyone else.
 * <ul>
 *   <li><b>Shut out.</b> The vault shuts as soon as the last player is through ({@code PrologueService.sealVault}): too
 *   soon. Whoever is still on the stair side stays there, and a few always are. They pound on the steel and beg (heard in
 *   chat by whoever is near the vault inside) while the guards yell at the players to keep moving down the hall.</li>
 *   <li><b>The pods.</b> When the players blink into their pods, the people in the hall are sealed in the hall pods
 *   nobody has a claim on ({@link PodSleepers}), whoever the players carried down first; a pod left over gets someone who
 *   came down before them. A hundred and three years later those pods still hold them.</li>
 * </ul>
 * Everyone here is the scene's ({@code PrologueService.TAG}) and goes when the day ends.
 */
final class LastDayShelter {
    enum State { DOWN, IN, GATHERED, SHUT_OUT }

    static final class Person {
        final Villager body;
        State state = State.DOWN;
        Vec3 spot;
        Person(Villager body) { this.body = body; }
    }

    /** What the people shut out cry through the steel: each once, a few seconds apart. Original lines. */
    private static final String[] CRIES = {"(pounding on the steel) Let us in! Please, let us in!",
            "There's still people out here! Open the door!", "My boy's in there! Please! Open it!",
            "(fainter) It's getting brighter... please... please..."};
    /** How many people the hall takes before the rest wait in the commons. */
    static final int HALL_ROOM = 24;
    /** At least this many are on the wrong side of the vault when it shuts. */
    static final int SHUT_OUT = 3;

    final List<Person> people = new ArrayList<>();
    /** Sealed in the pods, by pod. */
    final List<Villager> sealed = new ArrayList<>();
    private List<Vec3> hallSpots, commonsSpots;
    private boolean shut;
    private int age, nextBang, burst, cries, nextCry;

    /** A runner from the street, at the head of the stair: theirs to lead down from here. */
    void take(Villager body) {
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        people.add(new Person(body));
    }

    /** Whether the vault has shut on whoever is still outside. */
    boolean isShut() { return shut; }

    /** How many have reached the stasis hall (or the commons, when the hall is full). */
    int gathered() { return (int) people.stream().filter(p -> p.state == State.GATHERED).count(); }

    /** How many are shut out on the stair side of the vault. */
    int shutOut() { return (int) people.stream().filter(p -> p.state == State.SHUT_OUT).count(); }

    /**
     * Every tick of the day once the sirens have gone: leads everyone on down (every few ticks), and once the vault is
     * shut, the pounding and the begging on its far side.
     */
    void tick(ServerLevel city, LastDayGuide guide, LastDayGuide.Goal vault, LastDayGuide.Goal hall, boolean vaultOpen,
              List<ServerPlayer> listeners) {
        age++;
        people.removeIf(p -> p.body.isRemoved());
        if (age % 5 == 0) for (Person p : people) steer(city, guide, p, vault, hall, vaultOpen);
        if (shut) pound(city, listeners);
    }

    private void steer(ServerLevel city, LastDayGuide guide, Person p, LastDayGuide.Goal vault, LastDayGuide.Goal hall, boolean vaultOpen) {
        Villager body = p.body;
        if (body.isNoAi()) return;
        Brain<Villager> brain = body.getBrain();
        BlockPos o = PrologueService.LastDayData.origin();
        boolean through = body.getZ() > o.getZ() + 1.2;
        Vec3 landing = PrologueService.landing();
        switch (p.state) {
            case DOWN -> {
                if (through) { p.state = State.IN; return; }
                if (shut) { p.state = State.SHUT_OUT; return; }
                if (body.position().distanceToSqr(landing) < 6.25) {
                    // At the foot of the stair: through the vault if it's open, otherwise waiting at the door.
                    walk(brain, vaultOpen ? at(o, .5, 1, 3.5) : landing, vaultOpen ? 0.9F : 0.6F, vaultOpen ? 0 : 1);
                    return;
                }
                Vec3 next = ahead(guide.route(city, vault, body.position()), body.position());
                walk(brain, next != null ? next : landing, 0.95F, 0);
            }
            case IN -> {
                if (p.spot == null) p.spot = spot(city);
                Vec3 gather = at(o, BunkerLayout.Scene.GATHER);
                // Into the hall (or the commons, when the hall is full): straight to their place once it's in sight.
                boolean close = body.position().distanceToSqr(gather) < 16 || body.position().distanceToSqr(p.spot) < 64;
                if (close) { p.state = State.GATHERED; return; }
                Vec3 next = ahead(guide.route(city, hall, body.position()), body.position());
                walk(brain, next != null ? next : gather, 0.9F, 0);
            }
            case GATHERED -> {
                if (body.position().distanceToSqr(p.spot) > 1.2) walk(brain, p.spot, 0.6F, 0);
                else {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    // Watching the door they came in by, and whoever comes through it.
                    brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(BlockPos.containing(at(o, .5, 2.5, 34.5))));
                }
            }
            case SHUT_OUT -> {
                // Pressed up against the steel.
                if (p.spot == null) p.spot = at(o, -0.5 + (people.indexOf(p) % 3), 1, -1.5);
                if (body.position().distanceToSqr(p.spot) > 1) {
                    if (body.position().distanceToSqr(landing) > 30) {
                        Vec3 next = ahead(guide.route(city, vault, body.position()), body.position());
                        walk(brain, next != null ? next : landing, 0.95F, 0);
                    } else walk(brain, p.spot, 0.9F, 0);
                } else {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(PrologueService.vaultDoor()));
                }
            }
        }
    }

    /** The first corner of a route at least three blocks off (or its last), or null with no route yet. */
    private static Vec3 ahead(List<Vec3> route, Vec3 from) {
        if (route == null || route.isEmpty()) return null;
        for (Vec3 corner : route) if (corner.distanceToSqr(from) >= 9) return corner;
        return route.getLast();
    }

    private static void walk(Brain<Villager> brain, Vec3 to, float speed, int close) {
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, speed, close));
    }

    private static Vec3 at(BlockPos o, double x, double y, double z) { return new Vec3(o.getX() + x, o.getY() + y, o.getZ() + z); }

    private static Vec3 at(BlockPos o, Vec3 v) { return at(o, v.x, v.y, v.z); }

    /**
     * A place to wait: along the hall's walls in front of the pods, clear of the aisle, of where the carried are laid and
     * of Pod 14's end, where Noah and Claire are; when the hall is full, the commons.
     */
    private Vec3 spot(ServerLevel city) {
        BlockPos o = PrologueService.LastDayData.origin();
        if (hallSpots == null) {
            hallSpots = new ArrayList<>();
            for (int z = 39; z <= 53; z += 2) {
                hallSpots.add(LastDayRescue.standable(city, at(o, -5.5, 1, z + .5)));
                hallSpots.add(LastDayRescue.standable(city, at(o, -4.5, 1, z + 1.5)));
                if (z <= 49) hallSpots.add(LastDayRescue.standable(city, at(o, 5.5, 1, z + 1.5)));
            }
            commonsSpots = new ArrayList<>();
            for (int z = 14; z <= 28; z += 2)
                for (double x : new double[] {-6.5, 7.5}) commonsSpots.add(LastDayRescue.standable(city, at(o, x, 1, z + .5)));
        }
        int taken = (int) people.stream().filter(p -> p.spot != null && p.state != State.SHUT_OUT).count();
        if (taken < Math.min(HALL_ROOM, hallSpots.size())) return hallSpots.get(taken);
        int k = taken - Math.min(HALL_ROOM, hallSpots.size());
        return commonsSpots.get(k % commonsSpots.size()).add(0, 0, (k / commonsSpots.size()) * 0.6);
    }

    /**
     * The vault has shut. Whoever is still on its stair side is shut out, and never fewer than {@link #SHUT_OUT}: if the
     * stair is empty, people who were just behind come down it to the door (put on the landing, out of anyone's sight
     * behind the steel).
     */
    void shut(ServerLevel city) {
        if (shut) return;
        shut = true;
        nextBang = age + 6;
        nextCry = age + 30;
        BlockPos o = PrologueService.LastDayData.origin();
        int outside = 0;
        for (Person p : people) {
            if (p.state == State.DOWN && p.body.getZ() <= o.getZ() + 1.2) { p.state = State.SHUT_OUT; p.spot = null; }
            if (p.state == State.SHUT_OUT) outside++;
        }
        for (int k = outside; k < SHUT_OUT; k++) {
            Villager late = person(city, k);
            if (late == null) continue;
            Vec3 at = at(o, -0.5 + k % 3, 1, -2.5 - k / 3);
            late.moveTo(at.x, at.y, at.z, 180, 0);
            if (!city.addFreshEntity(late)) continue;
            Person p = new Person(late);
            p.state = State.SHUT_OUT;
            people.add(p);
        }
    }

    /** The fists on the steel, in bursts, and every few seconds a voice through it to whoever is near the vault inside. */
    private void pound(ServerLevel city, List<ServerPlayer> listeners) {
        Vec3 landing = PrologueService.landing();
        if (people.stream().noneMatch(p -> p.state == State.SHUT_OUT && p.body.position().distanceToSqr(landing) < 25)) return;
        BlockPos door = PrologueService.vaultDoor();
        if (age >= nextBang) {
            city.playSound(null, door, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 1.3F, 0.7F + city.random.nextFloat() * 0.25F);
            // Two to four blows close together, then a breath.
            if (burst <= 0) burst = 1 + city.random.nextInt(3);
            else burst--;
            nextBang = age + (burst > 0 ? 6 + city.random.nextInt(5) : 22 + city.random.nextInt(30));
        }
        if (cries < CRIES.length && age >= nextCry) {
            Component line = Component.literal("<").append(Component.literal("Someone outside").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("> " + CRIES[cries]).withStyle(ChatFormatting.ITALIC));
            for (ServerPlayer p : listeners)
                if (p.level() == city && p.distanceToSqr(Vec3.atCenterOf(door)) < 40 * 40) p.sendSystemMessage(line);
            cries++;
            nextCry = age + 100;
        }
    }

    /**
     * The players are in their pods: everyone below is sealed in the hall pods nobody has a claim on, those the players
     * carried down ({@code saved}) first, then whoever reached the hall; a pod still empty gets someone who came down
     * before the players did. Whoever was shut out is gone. Returns how many were sealed in.
     */
    int seal(ServerLevel city, BunkerData data, List<Villager> saved) {
        BlockPos o = PrologueService.LastDayData.origin();
        BunkerLayout layout = BunkerLayout.STASIS;
        Deque<Villager> who = new ArrayDeque<>(saved);
        for (Person p : people) if (p.state == State.GATHERED || p.state == State.IN) who.add(p.body);
        for (Person p : people) if (p.state == State.SHUT_OUT) p.body.discard();
        int count = 0;
        for (int i = 0; i < BunkerLayout.HALL_PODS; i++) {
            if (!PodSleepers.hallPod(i) || data.pods.containsValue(i)) continue;
            Villager body = who.poll();
            boolean fresh = body == null;
            if (fresh) body = PodSleepers.body(city, i);
            if (body == null) continue;
            body.addTag(PrologueService.TAG);
            PodSleepers.seal(city, o, layout, i, body, false);
            if (fresh && !city.addFreshEntity(body)) continue;
            sealed.add(body);
            BunkerBuilder.signPod(city, o, layout, i, DyeColor.LIGHT_BLUE, String.format("SUBJECT %02d", i + 1), PodSleepers.name(i), "STASIS", "SEALED");
            count++;
        }
        people.removeIf(p -> sealed.contains(p.body) || p.body.isRemoved());
        return count;
    }

    /** Someone else from the street, as the crowd's people look. */
    static Villager person(ServerLevel city, int k) {
        Villager villager = EntityType.VILLAGER.create(city);
        if (villager == null) return null;
        VillagerType[] looks = {VillagerType.PLAINS, VillagerType.TAIGA, VillagerType.SAVANNA, VillagerType.DESERT, VillagerType.SWAMP};
        VillagerProfession[] jobs = {VillagerProfession.NONE, VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.BUTCHER};
        villager.setVillagerData(villager.getVillagerData().setType(looks[k % looks.length]).setProfession(jobs[k % jobs.length]).setLevel(2));
        villager.setVillagerXp(1);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setPersistenceRequired();
        villager.addTag(PrologueService.TAG);
        return villager;
    }

    /** The day is over: everyone goes, the sealed included. */
    void discard() {
        people.forEach(p -> { p.body.ejectPassengers(); p.body.discard(); });
        people.clear();
        sealed.forEach(net.minecraft.world.entity.Entity::discard);
        sealed.clear();
    }
}
