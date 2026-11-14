package com.jayptucker.heroesevolved.time.intro;

import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The people on Odessa's streets in the vision's last half-minute. Some stand in twos and threes
 * talking, and their talk drops hints about the day: Sprague looking sick, Isaac's painting, the paper
 * company's guards. Some walk round the block; some look in shop windows. When the light starts they
 * stop and stare north; when the sirens start they run south down their street, shouting, and their
 * screams come from where they run. Speech hangs over a speaker's head for a few seconds; only the
 * people near a visitor talk, and never more than a couple of conversations at once.
 */
public final class VisionCrowd {
    enum Role { TALK, WALK, BROWSE }

    /**
     * When the crowd notices the light, when it runs, and when the dust takes it (scene ticks), how far south it runs,
     * and how far north the curious may walk toward the light.
     */
    public record Timing(int glow, int siren, int detonation, int runToZ, int stareFloorZ) {}

    /** The Time vision's half-minute. */
    static Timing vision() {
        return new Timing(TimeIntroService.GLOW_TICK, TimeIntroService.SIREN_TICK, TimeIntroService.DETONATION_TICK,
                TimeIntroService.CROWD_RUN_Z, TimeIntroService.CROWD_STARE_Z);
    }

    static final class Person {
        final Villager body;
        final Role role;
        final Vec3 spot;
        final int group;
        int leg;
        Person(Villager body, Role role, Vec3 spot, int group) { this.body = body; this.role = role; this.spot = spot; this.group = group; }
    }

    private record Bubble(Display.TextDisplay display, Villager speaker, int until) {}
    private static final class Talk {
        final List<Person> people;
        final String[] lines;
        int index, nextAt;
        Talk(List<Person> people, String[] lines, int startAt) { this.people = people; this.lines = lines; this.nextAt = startAt; }
    }

    /** Before the light: everyday talk, and in it, what this day is. Original lines. */
    private static final String[][] TALK = {
            {"Did you see Ted Sprague today?", "He looked sick. Burning up, he said."},
            {"Mendez painted this street on fire.", "Isaac paints a lot of things."},
            {"Guards on every door at Primatech.", "For a paper company?"},
            {"A man asked me what time it was.", "And?", "He said, \"Not yet.\""},
            {"The watchmaker fixed my watch for free.", "Gray? He stares at people."},
            {"Bennet's girl walked out of that fire.", "Without a scratch, they say."},
            {"They tested the sirens twice this week.", "And nobody's said why."},
            {"Mara at the map shop drew the skyline burning.", "Everyone's dreaming about fire lately."},
            {"Dinner at seven?", "If the bus ever comes."},
            {"Warm for this late in the day.", "Feels like it's coming from downtown."},
            {"Men in suits were asking about Sprague.", "Company men. Don't get involved."}};
    /** When the light starts. */
    private static final String[] STARE = {"What is that, up the avenue?", "Is that a fire?", "There's a man in the street... glowing.",
            "Someone call 911!", "Why is it so bright?", "It's getting hot.", "Don't look straight at it."};
    /** When the sirens start. */
    private static final String[] SHOUT = {"RUN!", "Go south! Go!", "Get off the street!", "Where's my daughter?!",
            "The paper company has a basement!", "Don't look back!", "Keep running!", "Help us!"};

    private static final VillagerType[] LOOKS = {VillagerType.PLAINS, VillagerType.TAIGA, VillagerType.SAVANNA,
            VillagerType.DESERT, VillagerType.SNOW, VillagerType.JUNGLE, VillagerType.SWAMP};
    private static final VillagerProfession[] JOBS = {VillagerProfession.NONE, VillagerProfession.NITWIT, VillagerProfession.LIBRARIAN,
            VillagerProfession.CLERIC, VillagerProfession.FARMER, VillagerProfession.CARTOGRAPHER, VillagerProfession.BUTCHER,
            VillagerProfession.WEAPONSMITH, VillagerProfession.FLETCHER, VillagerProfession.SHEPHERD};

    final List<Person> people = new ArrayList<>();
    private final List<List<Person>> groups = new ArrayList<>();
    private final List<Bubble> bubbles = new ArrayList<>();
    private final List<Talk> talks = new ArrayList<>();
    private final Map<UUID, Integer> nextTalk = new HashMap<>();
    private final Set<Integer> spoken = new HashSet<>();
    private final Set<List<Person>> used = new HashSet<>();
    private int nextShout, script, now;
    /** Ticks between one conversation and the next near a visitor, the pause between lines, and between stares. */
    private int talkGap = 70, firstTalk = 80, linePause = 4, stareGap = 44;

    /** An ordinary evening's pace (the Last Day): conversations further apart, with a breath between lines. */
    public void calm() { talkGap = 260; firstTalk = 200; linePause = 30; stareGap = 100; }
    private Timing timing;
    private int shift;

    // ------------------------------------------------------------------
    // Who is where
    // ------------------------------------------------------------------

    /** Where groups stand: sidewalks around the arrival and the streets nearby (x, z). */
    private static final double[][] GROUPS = {
            {9.5, 76.5}, {-1.5, 82.5}, {9.5, 58.5}, {-1.5, 46.5}, {9.5, 36.5}, {20.5, 73.5},
            {-18.5, 73.5}, {-1.5, 20.5}, {9.5, 12.5}, {30.5, 41.5}, {-30.5, 62.5}, {40.5, 73.5}};
    /** Blocks whose sidewalks people walk round (block x, block z). */
    private static final int[][] LOOPS = {{4, 6}, {3, 6}, {4, 5}, {3, 5}, {5, 6}, {2, 6}, {4, 4}, {3, 4}};
    /** Shop windows people stop at (x, z, the way they face). */
    private static final double[][] WINDOWS = {{12.5, 87.5, 90}, {-5.5, 66.5, 270}, {12.5, 52.5, 90}, {-5.5, 40.5, 270}};

    static VisionCrowd spawn(ServerLevel city, String tag, int size) {
        return spawn(city, tag, size, vision(), TimeIntroService.CROWD_SHIFT);
    }

    /**
     * Puts {@code size} people on the streets, every place {@code shift} blocks further south than the plan's own (a
     * whole number of city blocks, so sidewalks stay sidewalks), living by {@code timing}.
     */
    public static VisionCrowd spawn(ServerLevel city, String tag, int size, Timing timing, int shift) {
        VisionCrowd crowd = new VisionCrowd();
        crowd.timing = timing;
        crowd.shift = shift;
        int made = 0;
        for (int g = 0; g < GROUPS.length && made < size; g++) {
            List<Person> group = new ArrayList<>();
            int members = 2 + g % 2;
            for (int i = 0; i < members && made < size; i++, made++) {
                double a = i * Math.PI * 2 / members + g;
                Vec3 at = new Vec3(GROUPS[g][0] + Math.cos(a) * 0.9, CityGrid.STREET_Y + 1, GROUPS[g][1] + shift + Math.sin(a) * 0.9);
                Person person = crowd.add(city, tag, made, at, Role.TALK, crowd.groups.size());
                if (person != null) group.add(person);
            }
            if (group.size() > 1) crowd.groups.add(group);
        }
        for (int w = 0; w < WINDOWS.length && made < size; w++, made++)
            crowd.add(city, tag, made, new Vec3(WINDOWS[w][0], CityGrid.STREET_Y + 1, WINDOWS[w][1] + shift), Role.BROWSE, -1);
        for (int k = 0; made < size; k++, made++) {
            int[] loop = LOOPS[k % LOOPS.length];
            Vec3 start = crowd.corner(loop, k);
            Person person = crowd.add(city, tag, made, start, Role.WALK, -1);
            if (person != null) person.leg = k % 4;
        }
        return crowd;
    }

    /** The four corners of a block's sidewalk loop, one step in from the curb. */
    private Vec3 corner(int[] loop, int leg) {
        int ox = CityGrid.originX(loop[0]), oz = CityGrid.originZ(loop[1]) + shift;
        return switch (Math.floorMod(leg, 4)) {
            case 0 -> new Vec3(ox + 9.5, CityGrid.STREET_Y + 1, oz + 9.5);
            case 1 -> new Vec3(ox + 30.5, CityGrid.STREET_Y + 1, oz + 9.5);
            case 2 -> new Vec3(ox + 30.5, CityGrid.STREET_Y + 1, oz + 30.5);
            default -> new Vec3(ox + 9.5, CityGrid.STREET_Y + 1, oz + 30.5);
        };
    }

    private Person add(ServerLevel city, String tag, int i, Vec3 at, Role role, int group) {
        Villager villager = EntityType.VILLAGER.create(city);
        if (villager == null) return null;
        villager.moveTo(at.x, at.y, at.z, city.random.nextFloat() * 360, 0);
        villager.setVillagerData(villager.getVillagerData().setType(LOOKS[i % LOOKS.length])
                .setProfession(JOBS[CityGrid.hash(i, 9) % JOBS.length]).setLevel(2));
        villager.setVillagerXp(1);
        if (i % 11 == 6) villager.setAge(-24000);
        villager.setInvulnerable(true);
        villager.setSilent(true); // Their voices are the scene's own, not trading chatter.
        villager.setPersistenceRequired();
        villager.addTag(tag);
        if (!city.addFreshEntity(villager)) return null;
        Person person = new Person(villager, role, at, group);
        people.add(person);
        return person;
    }

    // ------------------------------------------------------------------
    // The half-minute
    // ------------------------------------------------------------------

    public void tick(ServerLevel city, int ticks, List<ServerPlayer> visitors, String tag) {
        now = ticks;
        bubbles.removeIf(b -> {
            if (ticks < b.until && b.speaker.isAlive() && !b.display.isRemoved()) return false;
            b.display.discard();
            return true;
        });
        if (ticks >= timing.detonation()) { talks.clear(); return; }
        boolean stare = ticks >= timing.glow(), run = ticks >= timing.siren();
        if (ticks % 5 == 0)
            for (int i = 0; i < people.size(); i++) steer(people.get(i), i, ticks, stare, run);
        if (!stare) converse(city, ticks, visitors, tag);
        else {
            talks.clear();
            if (ticks >= nextShout) {
                // Someone near a visitor says what everyone is thinking.
                Person who = near(visitors, 22);
                if (who != null) say(city, who.body, run ? SHOUT[CityGrid.hash(ticks, 3) % SHOUT.length]
                        : STARE[CityGrid.hash(ticks, 5) % STARE.length], run, 50, tag);
                nextShout = ticks + (run ? 26 : stareGap);
            }
        }
    }

    private void steer(Person person, int i, int ticks, boolean stare, boolean run) {
        Villager body = person.body;
        if (!body.isAlive()) return;
        var brain = body.getBrain();
        if (run) {
            // Away from the light, down whichever street they are on.
            double x = body.getX() + Math.sin(i * 1.7) * 2, z = Math.min(timing.runToZ(), body.getZ() + 45);
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(x, CityGrid.STREET_Y, z), 0.95F, 2));
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            return;
        }
        if (stare) {
            // Everyone stops to look; a few walk a little way toward it.
            BlockPos light = BlockPos.containing(IntroBlast.CENTER.x, IntroBlast.CENTER.y + 18, IntroBlast.CENTER.z);
            brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(light));
            if (i % 4 == 0) brain.setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(BlockPos.containing(body.getX(), CityGrid.STREET_Y, Math.max(timing.stareFloorZ(), body.getZ() - 10)), 0.4F, 2));
            else brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            return;
        }
        switch (person.role) {
            case TALK -> {
                if (body.position().distanceToSqr(person.spot) > 2.5) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(person.spot, 0.45F, 0));
                else brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                // Face the others in the group.
                Person other = partner(person);
                if (other != null) brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(other.body, true));
            }
            case BROWSE -> {
                if (body.position().distanceToSqr(person.spot) > 2.5) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(person.spot, 0.4F, 0));
                else {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    double yaw = Math.toRadians(WINDOWS[i % WINDOWS.length][2]);
                    brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(BlockPos.containing(
                            body.getX() - Math.sin(yaw) * 3, body.getEyeY(), body.getZ() + Math.cos(yaw) * 3)));
                }
            }
            case WALK -> {
                int[] loop = LOOPS[i % LOOPS.length];
                Vec3 target = corner(loop, person.leg);
                if (body.position().distanceToSqr(target) < 3) { person.leg = (person.leg + 1) % 4; target = corner(loop, person.leg); }
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 1));
            }
        }
    }

    private Person partner(Person person) {
        if (person.group < 0 || person.group >= groups.size()) return null;
        for (Person other : groups.get(person.group)) if (other != person && other.body.isAlive()) return other;
        return null;
    }

    /** Starts conversations near visitors, and plays the ones running. */
    private void converse(ServerLevel city, int ticks, List<ServerPlayer> visitors, String tag) {
        for (ServerPlayer visitor : visitors) {
            int next = nextTalk.getOrDefault(visitor.getUUID(), firstTalk);
            if (ticks < next || talks.size() >= 2) continue;
            List<Person> group = null;
            double best = 24 * 24;
            for (List<Person> g : groups) {
                if (used.contains(g) || g.isEmpty() || !g.getFirst().body.isAlive()) continue;
                double d = g.getFirst().body.distanceToSqr(visitor);
                if (d < best) { best = d; group = g; }
            }
            if (group == null) { nextTalk.put(visitor.getUUID(), ticks + 40); continue; }
            int pick = -1;
            for (int k = 0; k < TALK.length; k++) {
                int candidate = (script + k) % TALK.length;
                if (!spoken.contains(candidate)) { pick = candidate; break; }
            }
            if (pick < 0) { spoken.clear(); pick = script % TALK.length; }
            spoken.add(pick);
            script = pick + 1;
            used.add(group);
            talks.add(new Talk(group, TALK[pick], ticks));
            nextTalk.put(visitor.getUUID(), ticks + talkGap);
        }
        talks.removeIf(talk -> {
            if (ticks < talk.nextAt) return false;
            if (talk.index >= talk.lines.length) return true;
            Person speaker = talk.people.get(talk.index % talk.people.size());
            String line = talk.lines[talk.index++];
            int hold = 46 + line.length();
            if (speaker.body.isAlive()) say(city, speaker.body, line, false, hold, tag);
            talk.nextAt = ticks + hold + linePause;
            return false;
        });
    }

    /** Someone alive within {@code reach} of any visitor, at random. */
    private Person near(List<ServerPlayer> visitors, double reach) {
        List<Person> close = new ArrayList<>();
        for (Person p : people) {
            if (!p.body.isAlive()) continue;
            for (ServerPlayer v : visitors) if (p.body.distanceToSqr(v) < reach * reach) { close.add(p); break; }
        }
        return close.isEmpty() ? null : close.get(CityGrid.hash(close.size(), nextShout, script) % close.size());
    }

    // ------------------------------------------------------------------
    // Speech
    // ------------------------------------------------------------------

    /** Words over someone's head for a few seconds, riding with them as they move. */
    private void say(ServerLevel city, Villager speaker, String text, boolean shout, int ticks, String tag) {
        for (Bubble b : bubbles) if (b.speaker == speaker) { b.display.discard(); }
        bubbles.removeIf(b -> b.speaker == speaker);
        Display.TextDisplay display = com.jayptucker.heroesevolved.effects.SpeechBubble.show(city, speaker, text, shout, tag);
        if (display == null) return;
        bubbles.add(new Bubble(display, speaker, now + ticks));
    }

    /** People the dust has reached are gone, and so are their words. */
    public void engulf(float front) {
        people.removeIf(p -> {
            if (p.body.isAlive() && IntroBlast.distance(p.body.getX(), p.body.getZ()) > front) return false;
            p.body.ejectPassengers();
            p.body.discard();
            return true;
        });
        bubbles.removeIf(b -> {
            if (b.speaker.isAlive()) return false;
            b.display.discard();
            return true;
        });
    }

    public void discard() {
        bubbles.forEach(b -> b.display.discard());
        bubbles.clear();
        people.forEach(p -> { p.body.ejectPassengers(); p.body.discard(); });
        people.clear();
    }
}
