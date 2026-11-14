package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.company.CompanyAgentEntity;
import com.jayptucker.heroesevolved.company.Tranquilizer;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.effects.SpeechBubble;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.network.TimeIntroPayload;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.story.AvenueService;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity.Role;
import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.BunkerService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.bunker.PastBunker;
import com.jayptucker.heroesevolved.story.night.NightThreeBunker;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.intro.FractureCity;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import com.jayptucker.heroesevolved.time.intro.VisionCrowd;
import com.jayptucker.heroesevolved.time.intro.city.Apartments;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Last Day: how every new player's story begins.
 * <ol>
 *   <li><b>Apartment.</b> You wake in your apartment in the Odessa Arms, a block from Primatech Paper, on the Last Day.
 *   The door is shut until everyone who woke with you is ready (or a minute after the last of them woke).</li>
 *   <li><b>Evening.</b> The doors open. Odessa is having an ordinary evening; its people talk about their day.</li>
 *   <li><b>The light.</b> Far up the avenue, a man begins to glow. Sirens. Primatech opens its doors and its agents wave
 *   people in. Get down the stair in the lobby, through the vault, before the light grows too bright: it surges, again and again, but you
 *   never see it go. Whoever is still out when time runs out is darted by an agent and carried down.</li>
 *   <li><b>The safe house.</b> The stasis bunker under Primatech, the same one you will wake in. Noah Bennet seals his
 *   daughter in Pod 14 while she begs to go and help, then turns to you: this is for your own good. A stun gun.</li>
 *   <li><b>The pods.</b> You come to sealed in your own pod. Screaming far above, the ground shaking, one terrible blow,
 *   silence. The pod hums on, frost creeps across your sight, and it all goes black. <i>103 years later...</i></li>
 * </ol>
 * Then the bunker under spawn wakes you, in the pod with the same number ({@link BunkerService#handOff}).
 */
public final class PrologueService {
    public static final String TAG = "heroesevolved_last_day";
    /** Set on a player once they are sealed in their pod, so a crash past that point goes straight to waking. */
    public static final String SLEPT = "heroesevolved_last_day_slept";
    /** Where the survivors are waved in, in front of Primatech's doors. */
    public static final Vec3 ENTRANCE = new Vec3(CityGrid.SHAFT_X + .5, CityGrid.STREET_Y + .5, 105.5);
    /** Members above ground stay in the southern city, as the Time vision's visitors once did, with the south blocks open. */
    public static final int ROAM_X0 = -92, ROAM_X1 = 96, ROAM_Z0 = -4, ROAM_Z1 = 126;
    /** Claire, below, while the others come down: Peter is up there with Ted, and he's her uncle. Each said once. */
    static final String[] CLAIRE_PLEAS = {
            "Dad, that's Peter up there. He's trying to stop it on his own!",
            "I can help Peter. He'll die if I'm not there!",
            "He's my uncle, Dad. He's family. You can't just leave him!"};

    /** Pod 14: Claire's. */
    public static final int CLAIRE = NightThreeBunker.CLAIRE;

    enum Phase { APARTMENT, EVENING, LIGHT, SAFE_HOUSE, NOAH, PODS }

    static final class Member {
        final UUID id;
        final int unit;
        boolean ready, inside, stowed, shot;
        int dartedAt = -1, pod = -1;
        Member(UUID id, int unit) { this.id = id; this.unit = unit; }
    }

    static final class Run {
        Phase phase = Phase.APARTMENT;
        /** Ticks in the current phase; ticks since the evening began (the crowd's clock); when the last member arrived. */
        int age, clock, lastJoin;
        final Map<UUID, Member> members = new LinkedHashMap<>();
        final int ready = 20 * HeroesEvolvedConfig.COMMON.prologueReadySeconds.get();
        final int evening = 20 * HeroesEvolvedConfig.COMMON.prologueEveningSeconds.get();
        final int run = 20 * HeroesEvolvedConfig.COMMON.prologueRunSeconds.get();
        VisionCrowd crowd;
        StoryActorEntity noah, claire;
        final List<CompanyAgentEntity> agents = new ArrayList<>();
        final List<Bubble> bubbles = new ArrayList<>();
        /** Noah's targets in order, and when he fires at the next. */
        final List<UUID> targets = new ArrayList<>();
        int nextShot;
        /** The stasis hall's lights as they were when the blow came, for the flicker. */
        final Map<BlockPos, BlockState> lamps = new java.util.HashMap<>();
        /** Claire's pleas while people come down: how many she has made, and when the next may come. */
        int pleas, nextPlea;
        /** The vault at the foot of the stair has shut behind the last of them. */
        boolean sealed;
    }

    private record Bubble(Display.TextDisplay display, int until) {}

    private static Run run;
    /** New players waiting for the city, or for a run already under way to end. */
    private static final Set<UUID> WAITING = new LinkedHashSet<>();
    /** The last word each waiting player was given, so they hear only when it changes. */
    private static final Map<UUID, String> NOTICE = new java.util.HashMap<>();
    private static boolean forced;

    private PrologueService() {}

    // ------------------------------------------------------------------
    // Who is in it
    // ------------------------------------------------------------------

    /** A Last Day is playing in the city. */
    public static boolean running() { return run != null; }

    public static boolean isMember(ServerPlayer player) { return run != null && run.members.containsKey(player.getUUID()); }

    public static boolean isWaiting(ServerPlayer player) { return WAITING.contains(player.getUUID()); }

    /** The scene's own people, agents and actors, allowed in the city. */
    public static boolean isScene(Entity entity) { return entity.getTags().contains(TAG); }

    /** Whether this new player should live the Last Day before waking (rather than waking at once). */
    public static boolean wanted(ServerPlayer player) {
        return HeroesEvolvedConfig.COMMON.bunkerEnabled.get() && HeroesEvolvedConfig.COMMON.prologueEnabled.get()
                && !player.getPersistentData().getBoolean(SLEPT) && player.getServer().getLevel(TimeIntroService.DIMENSION) != null;
    }

    /** A new player is ready to begin: into the run waiting in its apartments, or into the queue. */
    public static void join(ServerPlayer player) {
        if (run != null && open(run)) {
            add(player, run);
            return;
        }
        WAITING.add(player.getUUID());
        // Held until the city has room: a black screen, nothing to do but wait.
        ScreenTintPayload.send(player, 0xFF000000, 60, ScreenTintPayload.Style.HOLD);
    }

    /** Ends a player's part in the Last Day at once and wakes them in the bunker, 103 years later. */
    public static void skip(ServerPlayer player) {
        WAITING.remove(player.getUUID());
        if (run != null && run.members.containsKey(player.getUUID())) {
            Member member = run.members.remove(player.getUUID());
            handOff(player, member);
            if (run.members.isEmpty()) end(player.getServer());
        } else {
            player.getPersistentData().putBoolean(SLEPT, true);
            BunkerService.handOff(player);
        }
    }

    /** Who arrives late, after the doors open, still has an evening to live if this much of it is left. */
    static final int LATE_JOIN_TICKS = 20 * 40;

    /** Whether a newcomer can still join this run: while its apartments wait, or early enough in the evening. */
    private static boolean open(Run current) {
        return current.phase == Phase.APARTMENT || current.phase == Phase.EVENING && current.evening - current.clock >= LATE_JOIN_TICKS;
    }

    /** Ready to go out: by the chat button or by trying the apartment door. */
    public static void ready(ServerPlayer player) {
        Member member = run == null ? null : run.members.get(player.getUUID());
        if (member == null || run.phase != Phase.APARTMENT || member.ready) return;
        member.ready = true;
        // Only worth a line when others are still getting up; when it's everyone, the door simply opens.
        long waiting = run.members.values().stream().filter(m -> !m.ready).count();
        if (waiting > 0) player.sendSystemMessage(Component.translatable("prologue.heroesevolved.waiting_for", waiting).withStyle(ChatFormatting.GRAY));
    }

    /** A right-click on a block in the city: your apartment door says you are ready. True if it was handled. */
    public static boolean useBlock(ServerPlayer player, BlockPos pos) {
        Member member = run == null ? null : run.members.get(player.getUUID());
        if (member == null || run.phase != Phase.APARTMENT) return false;
        BlockPos door = Apartments.door(member.unit);
        if (!pos.equals(door) && !pos.equals(door.above())) return false;
        ready(player);
        return true;
    }

    private static void add(ServerPlayer player, Run current) {
        NOTICE.remove(player.getUUID());
        // The emptiest apartment, lowest first: one each for the first twelve, then they are shared.
        int unit = 0, fewest = Integer.MAX_VALUE;
        for (int u = 0; u < Apartments.UNITS; u++) {
            final int k = u;
            int living = (int) current.members.values().stream().filter(m -> m.unit == k).count();
            if (living < fewest) { fewest = living; unit = u; }
        }
        Member member = new Member(player.getUUID(), unit);
        current.members.put(player.getUUID(), member);
        current.lastJoin = current.age;
        ServerLevel city = city(player.getServer());
        player.closeContainer();
        player.stopRiding();
        player.ejectPassengers();
        Vec3 wake = Apartments.wake(member.unit, fewest);
        player.teleportTo(city, wake.x, wake.y, wake.z, Apartments.wakeYaw(member.unit), 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.setPortalCooldown();
        plate(city, member.unit, residents(player.getServer(), current, member.unit));
        // Eyes open on an ordinary room at the end of an ordinary day.
        ScreenTintPayload.send(player, 0xFF000000, 50, ScreenTintPayload.Style.FLASH);
        player.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 0.3F, 1.4F);
        cue(player, TimeIntroPayload.PROLOGUE_APARTMENT);
        if (current.phase == Phase.EVENING) {
            // Late: the others are already out, so the door is open and the evening has begun.
            member.ready = true;
            cue(player, TimeIntroPayload.PROLOGUE_EVENING);
            player.sendSystemMessage(Component.translatable("prologue.heroesevolved.wake_late", Apartments.number(member.unit)).withStyle(ChatFormatting.GOLD));
            return;
        }
        Component button = Component.translatable("prologue.heroesevolved.ready_button").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lastday ready"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("prologue.heroesevolved.ready_hover"))));
        player.sendSystemMessage(Component.translatable("prologue.heroesevolved.wake", Apartments.number(member.unit))
                .withStyle(ChatFormatting.GOLD).append(" ").append(button));
    }

    // ------------------------------------------------------------------
    // The day
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        ServerLevel city = city(server);
        if (city == null) return;
        FractureCity cityData = FractureCity.get(city);
        // Someone is waiting on a city that isn't built yet: build it faster.
        cityData.rush(!WAITING.isEmpty());
        if (cityData.ready()) LastDayData.get(city).build(city);
        holdWaiting(server, cityData);
        if (run == null) {
            if (!WAITING.isEmpty() && cityData.ready() && !TimeIntroService.sceneRunning() && !AvenueService.running()) start(server, city);
            return;
        }
        Run current = run;
        current.age++;
        if (current.phase.ordinal() >= Phase.EVENING.ordinal()) current.clock++;
        current.members.values().removeIf(member -> {
            ServerPlayer player = server.getPlayerList().getPlayer(member.id);
            if (player == null) return true;
            if (!TimeIntroService.isCity(player.level())) {
                // Left the city some other way (a command, a portal): their Last Day is over; they wake.
                handOff(player, member);
                return true;
            }
            care(player);
            return false;
        });
        if (current.members.isEmpty()) { end(server); return; }
        current.bubbles.removeIf(bubble -> {
            if (current.age < bubble.until && !bubble.display.isRemoved()) return false;
            bubble.display.discard();
            return true;
        });
        if (current.crowd != null && current.phase.ordinal() <= Phase.SAFE_HOUSE.ordinal())
            current.crowd.tick(city, current.clock, aboveGround(server, current), TAG);
        switch (current.phase) {
            case APARTMENT -> apartment(server, city, current);
            case EVENING -> evening(server, city, current);
            case LIGHT -> light(server, city, current);
            case SAFE_HOUSE -> safeHouse(server, city, current);
            case NOAH -> noah(server, city, current);
            case PODS -> pods(server, city, current);
        }
    }

    /** Keeps a member fed, unhurt and where they belong. */
    private static void care(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setExhaustion(0);
        player.fallDistance = 0;
        if (player.getY() >= CityGrid.STREET_Y - 2) {
            double x = Math.clamp(player.getX(), ROAM_X0, ROAM_X1), z = Math.clamp(player.getZ(), ROAM_Z0, ROAM_Z1);
            if (x != player.getX() || z != player.getZ()) {
                player.teleportTo(x, player.getY(), z);
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private static void start(MinecraftServer server, ServerLevel city) {
        run = new Run();
        force(city, true);
        // A previous run's leftovers, even after a crash.
        List<Entity> stale = new ArrayList<>();
        for (Entity entity : city.getAllEntities()) if (isScene(entity)) stale.add(entity);
        stale.forEach(Entity::discard);
        LastDayData.get(city).reset(city);
        doors(city, Apartments.doors(), false);
        for (int i = 0; i < Apartments.UNITS; i++) plate(city, i, "");
        for (UUID id : List.copyOf(WAITING)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            WAITING.remove(id);
            if (player != null) add(player, run);
        }
        if (run.members.isEmpty()) end(server);
    }

    private static void apartment(MinecraftServer server, ServerLevel city, Run current) {
        boolean everyone = current.members.values().stream().allMatch(m -> m.ready);
        if (everyone || current.age - current.lastJoin >= current.ready) evening(server, city, current, true);
    }

    /** The doors open on an ordinary evening. */
    private static void evening(MinecraftServer server, ServerLevel city, Run current, boolean begin) {
        current.phase = Phase.EVENING;
        current.age = 0;
        current.clock = 0;
        doors(city, Apartments.doors(), true);
        current.crowd = VisionCrowd.spawn(city, TAG, TimeIntroService.CROWD_SIZE, new VisionCrowd.Timing(current.evening,
                current.evening + PrologueTimeline.SIRENS, Integer.MAX_VALUE, (int) ENTRANCE.z - 4, ROAM_Z0), 0);
        // An ordinary evening goes at an ordinary pace: fewer conversations, with pauses between lines.
        if (current.crowd != null) current.crowd.calm();
        // The Company's men on Primatech's door: "Guards on every door at Primatech." "For a paper company?"
        for (double dx : new double[] {-2.5, 2.5}) agent(city, ENTRANCE.add(dx, 0, 0), 180);
        forEach(server, current, (player, member) -> {
            cue(player, TimeIntroPayload.PROLOGUE_EVENING);
            player.playNotifySound(SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1);
        });
    }

    private static void evening(MinecraftServer server, ServerLevel city, Run current) {
        if (current.clock >= current.evening) light(server, city, current, true);
    }

    /** Far up the avenue, a man begins to glow. */
    private static void light(MinecraftServer server, ServerLevel city, Run current, boolean begin) {
        current.phase = Phase.LIGHT;
        current.age = 0;
        vault(city, true);
        vaultSound(city, true);
        BlockPos origin = LastDayData.origin();
        // Down below: guards at the head of the stair, by the vault and along the way to the stasis hall, and the Bennets
        // by Pod 14.
        agent(city, new Vec3(CityGrid.STAIR_X0 - .5, CityGrid.STREET_Y + 1, CityGrid.STAIR_Z1 + 2.5), 160);
        agent(city, at(origin, -1.5, 1, -1.5), 160);
        agent(city, at(origin, -2.5, 1, -5.5), 180);
        agent(city, at(origin, -1.5, 1, 2.5), 160);   // just inside Records, beside the door (not in the desk)
        agent(city, at(origin, 0.5, 1, 15.5), 0);
        agent(city, at(origin, -1.5, 1, 19.5), 0);
        agent(city, at(origin, 2.5, 1, 19.5), 0);
        // Noah stands just past the hall's lectern (the orientation log at 0 1 26), by his daughter.
        current.noah = StoryActorEntity.spawn(city, Role.NOAH, at(origin, 0.5, 1, 28.5), 0, TAG);
        current.claire = StoryActorEntity.spawn(city, Role.CLAIRE, at(origin, 1.5, 1, 27.5), 180, TAG);
        if (current.claire != null) current.claire.pose(StoryActorEntity.Pose.PLEAD);
        if (current.noah != null && current.claire != null) current.noah.face(current.claire.position());
        forEach(server, current, (player, member) ->
                PacketDistributor.sendToPlayer(player, new TimeIntroPayload(TimeIntroPayload.PROLOGUE_LIGHT, List.of(current.run))));
    }

    private static void light(MinecraftServer server, ServerLevel city, Run current) {
        int t = current.age;
        if (t == PrologueTimeline.SIRENS) forEach(server, current, (player, member) -> cue(player, TimeIntroPayload.PROLOGUE_SIRENS));
        // The surges: a flash and a tremor for everyone still in the open, a shudder through the rock for the rest.
        for (int i = 0; i < PrologueTimeline.surges(); i++) {
            if (t != PrologueTimeline.surgeTick(i, current.run)) continue;
            float power = PrologueTimeline.surgePower(i);
            forEach(server, current, (player, member) -> {
                if (!member.inside && member.dartedAt < 0) {
                    int alpha = (int) (60 + 150 * power);
                    ScreenTintPayload.send(player, alpha << 24 | 0xFFF2DA, 16 + (int) (20 * power), ScreenTintPayload.Style.FLASH);
                    PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.06F + 0.25F * power, 24));
                } else PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.03F + 0.06F * power, 20));
            });
        }
        // Where to go: a column of light over Primatech's doors, and the agents calling people in.
        if (t % 10 == 0) {
            SetPiecePayload.send(city, Piece.VILLAGE_SAFE, ENTRANCE, ENTRANCE.add(0, 1, 0), 3, null);
            Vec3 head = new Vec3(CityGrid.STAIR_X0 + .5, CityGrid.STREET_Y, (CityGrid.STAIR_Z0 + CityGrid.STAIR_Z1) / 2.0 + .5);
            SetPiecePayload.send(city, Piece.VILLAGE_SAFE, head, head.add(0, 1, 0), 2, null);
        }
        // The agents call people in once the sirens start: a few calls, spaced out, each said once.
        String[] calls = {"Inside! Get inside, now!", "Down the stairs in the lobby! Go!", "Don't look at it! Move!"};
        int call = (t - PrologueTimeline.SIRENS) / 400;
        if (t >= PrologueTimeline.SIRENS && (t - PrologueTimeline.SIRENS) % 400 == 0 && call < calls.length && !current.agents.isEmpty())
            shout(server, current, current.agents.get(call % 2), calls[call]);
        sheltered(server, city, current);
        darts(server, city, current);
        // Claire, below, already begging.
        if (current.claire != null && anyInside(current) && current.pleas < CLAIRE_PLEAS.length && current.age >= current.nextPlea) {
            say(server, current, current.claire, CLAIRE_PLEAS[current.pleas], false);
            current.pleas++;
            current.nextPlea = current.age + 220;
        }
        boolean everyoneSafe = current.members.values().stream().allMatch(m -> m.inside || m.stowed);
        if (t >= current.run) {
            // Time's up: whoever is still outside is darted where they stand.
            forEach(server, current, (player, member) -> { if (!member.inside && member.dartedAt < 0) dart(city, current, player, member); });
        }
        if (everyoneSafe || t >= current.run + 60) next(current, Phase.SAFE_HOUSE);
    }

    /** On to the next phase, whose first tick (age 0) plays on the next server tick. */
    private static void next(Run current, Phase phase) {
        current.phase = phase;
        current.age = -1;
    }

    /** Anyone who has come down the stair past the turn is safe, and knows it. */
    private static void sheltered(MinecraftServer server, ServerLevel city, Run current) {
        BoundingBox box = PastBunker.bounds(LastDayData.origin()), stair = stairwell();
        forEach(server, current, (player, member) -> {
            if (member.inside || member.dartedAt >= 0) return;
            BlockPos at = player.blockPosition();
            if (player.getY() > CityGrid.STREET_Y - 5 || !box.isInside(at) && !stair.isInside(at)) return;
            member.inside = true;
            cue(player, TimeIntroPayload.PROLOGUE_UNDERGROUND);
            award(player, "safe_house");
        });
        // Down is the only way: nobody climbs back up into the light.
        forEach(server, current, (player, member) -> {
            if (member.inside && !member.stowed && player.level() == city && player.getY() > CityGrid.STREET_Y - 3) {
                Vec3 to = landing();
                player.teleportTo(to.x, to.y, to.z);
            }
        });
    }

    /** A dart from an agent behind them; the world goes dark; they are carried down and sealed in. */
    private static void dart(ServerLevel city, Run current, ServerPlayer player, Member member) {
        member.dartedAt = current.clock;
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        Vec3 behind = player.position().subtract(look.normalize().scale(4));
        CompanyAgentEntity agent = agent(city, new Vec3(behind.x, player.getY(), behind.z), 0);
        if (agent != null) {
            Vec3 d = player.position().subtract(agent.position());
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            agent.setYRot(yaw); agent.setYHeadRot(yaw); agent.setYBodyRot(yaw);
            Tranquilizer.trail(city, agent.getEyePosition(), player.getEyePosition().subtract(0, 0.5, 0));
        }
        city.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 0.8F, 1.6F);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 5, false, false));
        // The last thing they see: the biggest surge yet, and then nothing.
        ScreenTintPayload.send(player, 0xE0FFF4E0, 30, ScreenTintPayload.Style.FLASH);
        ScreenTintPayload.send(player, 0xC0000000, 60, ScreenTintPayload.Style.EDGE);
        PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.3F, 30));
        player.sendSystemMessage(Component.translatable("prologue.heroesevolved.darted").withStyle(ChatFormatting.AQUA));
    }

    private static void darts(MinecraftServer server, ServerLevel city, Run current) {
        forEach(server, current, (player, member) -> {
            if (member.dartedAt >= 0 && !member.stowed && current.clock - member.dartedAt >= 50) {
                cue(player, TimeIntroPayload.PROLOGUE_UNDERGROUND);
                seal(city, player, member, false);
            }
        });
    }

    /** The safe house: everyone gathers in the stasis hall. */
    private static void safeHouse(MinecraftServer server, ServerLevel city, Run current) {
        sheltered(server, city, current);
        darts(server, city, current);
        BlockPos origin = LastDayData.origin();
        sealVault(server, city, current, origin);
        boolean gathered = current.members.values().stream().allMatch(m -> m.stowed
                || m.inside && inHall(server.getPlayerList().getPlayer(m.id), origin));
        boolean pending = current.members.values().stream().anyMatch(m -> m.dartedAt >= 0 && !m.stowed);
        // Said once each, not on a loop, while the last of them come in.
        if (current.age == 60 && current.claire != null) say(server, current, current.claire, "Peter's still out there! There has to be another way!", false);
        if (pending || !gathered && current.age < 600) return;
        // Anyone still dawdling in the corridor is brought into the hall.
        forEach(server, current, (player, member) -> {
            if (!member.stowed && !inHall(player, origin)) player.teleportTo(origin.getX() + .5, origin.getY() + 1, origin.getZ() + 20.5);
        });
        boolean watchers = current.members.values().stream().anyMatch(m -> !m.stowed);
        if (watchers) forEach(server, current, (player, member) -> { if (!member.stowed) cue(player, TimeIntroPayload.PROLOGUE_NOAH); });
        next(current, watchers ? Phase.NOAH : Phase.PODS);
    }

    /**
     * The vault shuts once everyone who came down is through it, or after ten seconds: anyone still on the stair side is
     * waved through first, so nobody is shut out.
     */
    private static void sealVault(MinecraftServer server, ServerLevel city, Run current, BlockPos origin) {
        if (current.sealed) return;
        boolean through = current.members.values().stream().allMatch(m -> {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            return m.stowed || !m.inside || p == null || p.getZ() > origin.getZ() + 1.2;
        });
        if (!through && current.age < 200) return;
        forEach(server, current, (player, member) -> {
            if (member.inside && !member.stowed && player.getZ() <= origin.getZ() + 1.2)
                player.teleportTo(origin.getX() + .5, origin.getY() + 1, origin.getZ() + 3.5);
        });
        current.sealed = true;
        vault(city, false);
        vaultSound(city, false);
        forEach(server, current, (player, member) -> {
            if (member.inside && !member.stowed) PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.12F, 14));
        });
        if (current.claire != null) say(server, current, current.claire, "No... no, no, no. Dad, they shut it!", false);
    }

    /** The scene by Pod 14. */
    private static void noah(MinecraftServer server, ServerLevel city, Run current) {
        int t = current.age;
        BlockPos origin = LastDayData.origin();
        StoryActorEntity noah = current.noah, claire = current.claire;
        // Nobody leaves the hall while it happens.
        forEach(server, current, (player, member) -> {
            if (!member.stowed && !inHall(player, origin)) player.teleportTo(origin.getX() + .5, origin.getY() + 1, origin.getZ() + 20.5);
        });
        if (noah == null || claire == null) { if (t > 20) next(current, Phase.PODS); return; }
        BlockPos podBase = origin.offset(BunkerBuilder.podOffset(CLAIRE));
        Vec3 inPod = CryoChamberBlock.standing(podBase);
        Vec3 front = inPod.add(-1.3, 0, 0);
        switch (t) {
            case 0 -> {
                claire.face(noah.position()); claire.pose(StoryActorEntity.Pose.PLEAD);
                noah.face(claire.position());
                say(server, current, claire, "Dad, I don't want to go, please! I can stop this!", true);
            }
            case 60 -> say(server, current, noah, "Claire-bear. Listen to me. You have to get in.", false);
            case 115 -> say(server, current, claire, "I can heal! Whatever it does to me, I'll heal. Let me go up there and help Peter!", true);
            case 150 -> say(server, current, claire, "He's my uncle! He'll die up there if I'm not with him!", true);
            case 180 -> {
                say(server, current, noah, "Nobody walks away from that. Not even you.", false);
                claire.pose(StoryActorEntity.Pose.IDLE);
                claire.walkTo(front, 0.45);
                noah.walkTo(front.add(-1.2, 0, -0.6), 0.45);
            }
            case 215 -> say(server, current, noah, "Peter knew what he was walking into. I won't lose you too.", false);
            case 245 -> {
                claire.face(noah.position()); claire.pose(StoryActorEntity.Pose.PLEAD);
                noah.face(claire.position());
                say(server, current, claire, "Dad, please! Don't do this!", true);
            }
            case 275 -> noah.pose(StoryActorEntity.Pose.SHOVE);
            case 279 -> {
                // He pushes her in and seals the pod.
                BlockState state = city.getBlockState(podBase);
                float yaw = state.hasProperty(CryoChamberBlock.FACING) ? CryoChamberBlock.yaw(state.getValue(CryoChamberBlock.FACING)) : 270;
                claire.place(inPod, yaw);
                claire.pose(StoryActorEntity.Pose.PLEAD);
                if (state.getBlock() instanceof CryoChamberBlock) CryoChamberBlock.setOpen(city, podBase, false);
                city.playSound(null, podBase, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1, 0.6F);
                city.playSound(null, podBase, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.6F);
                city.playSound(null, podBase, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.BLOCKS, 0.8F, 0.8F);
                city.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, inPod.x, inPod.y + 1, inPod.z, 30, .3, .6, .3, .02);
                signPod(city, CLAIRE, "SUBJECT 14", "C. BENNET", "STASIS", "SEALED");
            }
            case 290 -> { noah.pose(StoryActorEntity.Pose.HAND_ON_GLASS); noah.face(inPod.add(0, 1.4, 0)); }
            case 300 -> say(server, current, noah, "I'll come back for you. I promise.", false);
            case 350 -> {
                noah.pose(StoryActorEntity.Pose.IDLE);
                noah.walkTo(at(origin, 0.5, 1, 23.5), 0.5);
            }
            case 400 -> {
                Vec3 crowd = watchersCentre(server, current);
                if (crowd != null) noah.face(crowd.add(0, 1.5, 0));
                say(server, current, noah, "I know you don't understand. This is for your own good.", false);
            }
            case 455 -> {
                noah.setItemInHand(InteractionHand.MAIN_HAND, PowerWeapons.gun(PowerWeapons.TASER));
                noah.pose(StoryActorEntity.Pose.AIM);
                current.targets.clear();
                current.members.values().stream().filter(m -> !m.stowed).forEach(m -> current.targets.add(m.id));
                current.nextShot = t + 16;
            }
            default -> { }
        }
        if (t >= 455 && t >= current.nextShot && !current.targets.isEmpty()) {
            UUID id = current.targets.removeFirst();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Member member = current.members.get(id);
            if (player != null && member != null) shoot(city, noah, player, member, current.targets.size());
            current.nextShot = t + 14;
        }
        if (t >= 455 && current.targets.isEmpty() && t >= current.nextShot + 30) next(current, Phase.PODS);
    }

    /** His stun gun: the barbs, the current, the floor. */
    private static void shoot(ServerLevel city, StoryActorEntity noah, ServerPlayer player, Member member, int after) {
        noah.face(player.getEyePosition());
        Vec3 look = noah.getLookAngle();
        Vec3 muzzle = noah.getEyePosition().add(look.scale(0.7)).add(0, -0.3, 0);
        SetPiecePayload.send(city, Piece.GUN_TASER, muzzle, player.position().add(0, 1.1, 0), 1, player);
        city.playSound(null, noah.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 0.7F, 1.8F);
        city.playSound(null, player.blockPosition(), SoundEvents.BEEHIVE_WORK, SoundSource.HOSTILE, 1, 2);
        member.shot = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 6, false, false));
        ScreenTintPayload.send(player, 0xD0B8DCFF, 12, ScreenTintPayload.Style.FLASH);
        // Dark until the pods, however many are shot after them (14 ticks apart, then the pause before the blink).
        ScreenTintPayload.send(player, 0xFF000000, 120 + 14 * after, ScreenTintPayload.Style.HOLD);
        PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.35F, 18));
        player.sendSystemMessage(Component.translatable("prologue.heroesevolved.shot").withStyle(ChatFormatting.AQUA));
    }

    /** Sealed in their own pod: the number they will wake under, 103 years from now. */
    private static void seal(ServerLevel city, ServerPlayer player, Member member, boolean blink) {
        BlockPos origin = LastDayData.origin();
        member.pod = BunkerData.get(player.getServer()).pod(player.getUUID(), BunkerBuilder.PODS, CLAIRE);
        BlockPos base = origin.offset(BunkerBuilder.podOffset(member.pod));
        if (!city.getBlockState(base).is(ModBlocks.CRYOCHAMBER.get())) BunkerBuilder.placePod(city, origin, member.pod, true);
        CryoChamberService.enter(player, base, 20 * 60 * 10);
        signPod(city, member.pod, String.format("SUBJECT %02d", member.pod + 1), player.getGameProfile().getName(), "STASIS", "SEALED");
        member.stowed = true;
        player.getPersistentData().putBoolean(SLEPT, true);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        ScreenTintPayload.send(player, 0xFF000000, blink ? 30 : 400, blink ? ScreenTintPayload.Style.FLASH : ScreenTintPayload.Style.HOLD);
    }

    /** Sealed in, as the city dies above them. */
    private static void pods(MinecraftServer server, ServerLevel city, Run current) {
        int t = current.age;
        BlockPos origin = LastDayData.origin();
        if (t == 0) {
            if (current.noah != null) { current.noah.discard(); current.noah = null; }
            current.agents.forEach(Entity::discard);
            current.agents.clear();
            // They blink, and they are in their pods: the shot ones and the darted ones together.
            forEach(server, current, (player, member) -> {
                // Whatever dark they went under in lifts for the blink (a zero-length tint cancels its match).
                ScreenTintPayload.send(player, 0xFF000000, 0, ScreenTintPayload.Style.HOLD);
                ScreenTintPayload.send(player, 0xC0000000, 0, ScreenTintPayload.Style.EDGE);
                if (!member.stowed) seal(city, player, member, true);
                else ScreenTintPayload.send(player, 0xFF000000, 30, ScreenTintPayload.Style.FLASH);
                cue(player, TimeIntroPayload.PROLOGUE_POD);
                player.sendSystemMessage(Component.translatable("prologue.heroesevolved.pod").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            });
        }
        // The ground shakes harder and harder; dust sifts from the ceiling.
        if (t > 20 && t < 170 && t % 20 == 0) {
            float k = (t - 20) / 150F;
            forEach(server, current, (player, member) -> PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.04F + 0.18F * k, 24)));
            for (int i = 0; i < 6; i++) {
                Vec3 at = at(origin, -4 + city.random.nextInt(9), 11, 20 + city.random.nextInt(13));
                city.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                        net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState()), at.x, at.y, at.z, 6, 1.5, 0.1, 1.5, 0);
            }
        }
        if (t == 120 && current.claire != null)
            say(server, current, current.claire, "(muffled, through the glass) Uncle Peter... please...", false);
        if (t == 170) {
            // One blow through a hundred feet of rock.
            forEach(server, current, (player, member) -> {
                cue(player, TimeIntroPayload.PROLOGUE_BLAST);
                PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.6F, 60));
                ScreenTintPayload.send(player, 0x90FFE0C0, 14, ScreenTintPayload.Style.FLASH);
                ScreenTintPayload.send(player, 0x70000000, 40, ScreenTintPayload.Style.VIGNETTE);
            });
        }
        if (t >= 170 && t <= 280) quake(city, current, origin, t - 170);
        if (t == 240) {
            // The pods take over: a hum, the hiss of coolant.
            forEach(server, current, (player, member) -> {
                player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 0.6F);
                player.playNotifySound(SoundEvents.POWDER_SNOW_PLACE, SoundSource.BLOCKS, 1, 0.7F);
                player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 0.5F);
            });
        }
        if (t >= 240 && t <= 380) {
            // Frost creeps in from the edges of sight (the powder-snow frost, never quite enough to hurt).
            int frost = Math.min(139, (t - 240) * 139 / 110);
            forEach(server, current, (player, member) -> player.setTicksFrozen(frost));
        }
        if (t == 320 || t == 345 || t == 365) {
            int alpha = t == 320 ? 0x50 : t == 345 ? 0x90 : 0xFF;
            forEach(server, current, (player, member) -> ScreenTintPayload.send(player, alpha << 24, 400 - t + 120, ScreenTintPayload.Style.HOLD));
        }
        if (t == 395) {
            forEach(server, current, (player, member) -> {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 70, 15));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("prologue.heroesevolved.years_later")
                        .withStyle(ChatFormatting.WHITE)));
            });
        }
        if (t >= 500) {
            forEach(server, current, (player, member) -> handOff(player, member));
            current.members.clear();
            end(server);
        }
    }

    /**
     * The blow reaches the stasis hall ({@code k} ticks after it): every light dies at once, then flickers back, some for
     * good and some not; stone breaks from the ceiling and comes down the aisle, and dust hangs in the air. The next run's
     * rebuild puts the hall back as it was.
     */
    private static void quake(ServerLevel city, Run current, BlockPos origin, int k) {
        if (k == 0) {
            current.lamps.clear();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 1, 18), origin.offset(7, 13, 40))) {
                BlockState state = city.getBlockState(pos);
                if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                        && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                        || state.getBlock() instanceof net.minecraft.world.level.block.LanternBlock)
                    current.lamps.put(pos.immutable(), state);
            }
        }
        // The lights: all out for a moment, then a stutter, then most come back; the broken ones stay dark.
        if (k <= 90 && k % 2 == 0) {
            for (var lamp : current.lamps.entrySet()) {
                boolean broken = Math.floorMod(lamp.getKey().hashCode(), 3) == 0;
                boolean on = k >= 6 && (k >= 70 ? !broken : city.random.nextFloat() < 0.25F + 0.6F * k / 70F);
                light(city, lamp.getKey(), lamp.getValue(), on);
            }
            if (k == 0) city.playSound(null, origin.offset(0, 4, 26), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.6F);
            if (k % 8 == 4) city.playSound(null, origin.offset(0, 6, 22 + city.random.nextInt(16)), SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.6F, 0.8F + city.random.nextFloat() * 0.5F);
        }
        // Rubble: a burst at the blow, then a trickle, down the aisle between the rows of pods.
        int pieces = k == 0 ? 9 : k <= 60 && k % 6 == 0 ? 1 + city.random.nextInt(2) : 0;
        BlockState[] stone = {net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES.defaultBlockState()};
        for (int i = 0; i < pieces; i++) {
            int x = origin.getX() - 3 + city.random.nextInt(7), z = origin.getZ() + 20 + city.random.nextInt(16);
            BlockPos at = null;
            for (int y = origin.getY() + 2; y <= origin.getY() + 13; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!city.getBlockState(pos).isAir()) break;
                at = pos;
            }
            if (at == null || at.getY() < origin.getY() + 4) continue;
            BlockState piece = stone[city.random.nextInt(stone.length)];
            var falling = net.minecraft.world.entity.item.FallingBlockEntity.fall(city, at, piece);
            falling.disableDrop();
            falling.addTag(TAG);
            city.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, piece),
                    at.getX() + .5, at.getY() + .9, at.getZ() + .5, 14, .4, .1, .4, .05);
            city.playSound(null, at, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1, 0.7F + city.random.nextFloat() * 0.3F);
        }
        if (k % 4 == 0 && k <= 100)
            for (int i = 0; i < 4; i++) {
                Vec3 at = at(origin, -5 + city.random.nextInt(11), 9, 20 + city.random.nextInt(17));
                city.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                        net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState()), at.x, at.y, at.z, 10, 1.2, .2, 1.2, 0);
            }
    }

    /** Puts a hall light on or off: a bulb or lamp by its lit state, a lantern by being there or not. */
    private static void light(ServerLevel city, BlockPos pos, BlockState original, boolean on) {
        BlockState state = original.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                ? original.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, on)
                : on ? original : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        if (city.getBlockState(pos) != state) city.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    /** Out of the Last Day and into the bunker under spawn, 103 years later. */
    private static void handOff(ServerPlayer player, Member member) {
        player.setTicksFrozen(0);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (CryoChamberService.isInside(player)) CryoChamberService.release(player);
        cue(player, TimeIntroPayload.PROLOGUE_END);
        player.getPersistentData().putBoolean(SLEPT, true);
        BunkerService.handOff(player);
    }

    /** The run is over: the city is put back for the next. */
    private static void end(MinecraftServer server) {
        Run current = run;
        run = null;
        if (current == null) return;
        ServerLevel city = city(server);
        if (current.crowd != null) current.crowd.discard();
        current.bubbles.forEach(b -> b.display.discard());
        current.agents.forEach(Entity::discard);
        if (current.noah != null) current.noah.discard();
        if (current.claire != null) current.claire.discard();
        if (city != null) {
            List<Entity> stale = new ArrayList<>();
            for (Entity entity : city.getAllEntities()) if (isScene(entity)) stale.add(entity);
            stale.forEach(Entity::discard);
            doors(city, Apartments.doors(), false);
            vault(city, false);
            force(city, false);
        }
    }

    // ------------------------------------------------------------------
    // Waiting
    // ------------------------------------------------------------------

    /** New players with nowhere to go yet: a dark screen and a word on why. */
    private static void holdWaiting(MinecraftServer server, FractureCity cityData) {
        if (WAITING.isEmpty()) return;
        long time = server.overworld().getGameTime();
        WAITING.removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return true;
            if (run != null && open(run)) {
                add(player, run);
                return true;
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            player.getFoodData().setFoodLevel(20);
            if (time % 40 == 0) {
                ScreenTintPayload.send(player, 0xFF000000, 60, ScreenTintPayload.Style.HOLD);
                // One line for each kind of wait (the city being built, another group's day), not a running count.
                String news = cityData.ready() ? "queued" : "building";
                if (!news.equals(NOTICE.put(id, news)))
                    player.sendSystemMessage(Component.translatable("prologue.heroesevolved." + news).withStyle(ChatFormatting.GRAY));
            }
            return false;
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static ServerLevel city(MinecraftServer server) { return server.getLevel(TimeIntroService.DIMENSION); }

    private static Vec3 at(BlockPos origin, double x, double y, double z) { return new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z); }

    private static boolean inHall(ServerPlayer player, BlockPos origin) {
        return player != null && player.getY() < origin.getY() + 12 && player.getY() >= origin.getY()
                && player.getZ() >= origin.getZ() + 18 && Math.abs(player.getX() - origin.getX() - .5) < 9;
    }

    private static boolean anyInside(Run current) { return current.members.values().stream().anyMatch(m -> m.inside); }

    private static Vec3 watchersCentre(MinecraftServer server, Run current) {
        Vec3 sum = Vec3.ZERO;
        int n = 0;
        for (Member m : current.members.values()) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p == null || m.stowed) continue;
            sum = sum.add(p.position());
            n++;
        }
        return n == 0 ? null : sum.scale(1.0 / n);
    }

    private static List<ServerPlayer> aboveGround(MinecraftServer server, Run current) {
        List<ServerPlayer> out = new ArrayList<>();
        for (Member m : current.members.values()) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p != null && !m.inside && p.getY() > CityGrid.STREET_Y - 3) out.add(p);
        }
        return out;
    }

    private interface Each { void accept(ServerPlayer player, Member member); }

    private static void forEach(MinecraftServer server, Run current, Each action) {
        for (Member member : List.copyOf(current.members.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.id);
            if (player != null) action.accept(player, member);
        }
    }

    private static CompanyAgentEntity agent(ServerLevel city, Vec3 at, float yaw) {
        CompanyAgentEntity agent = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), city, CompanyAgentEntity.Role.GUARD, at, yaw, true);
        if (agent == null) return null;
        agent.setNoAi(true);
        agent.setInvulnerable(true);
        agent.setPersistenceRequired();
        agent.addTag(TAG);
        if (!city.addFreshEntity(agent)) return null;
        if (run != null) run.agents.add(agent);
        return agent;
    }

    /** A Company agent calls out, in chat to everyone in the run. */
    private static void shout(MinecraftServer server, Run current, Entity speaker, String text) {
        Component line = Component.literal("<").append(Component.literal("Company Agent").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("> " + text));
        forEach(server, current, (player, member) -> player.sendSystemMessage(line));
    }

    /** An actor speaks: in chat to everyone in the run, and over their head. */
    private static void say(MinecraftServer server, Run current, StoryActorEntity actor, String line, boolean shout) {
        List<ServerPlayer> listeners = new ArrayList<>();
        forEach(server, current, (player, member) -> listeners.add(player));
        actor.say(line, shout, listeners, TAG);
    }

    private static void cue(ServerPlayer player, int stage) { PacketDistributor.sendToPlayer(player, new TimeIntroPayload(stage)); }

    private static void award(ServerPlayer player, String id) {
        AdvancementHolder advancement = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, id));
        if (advancement != null) player.getAdvancements().award(advancement, "granted");
    }

    /** Opens or shuts every door at {@code positions} (either half). */
    private static void doors(ServerLevel city, List<BlockPos> positions, boolean open) {
        for (BlockPos pos : positions) {
            BlockState state = city.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.OPEN) != open) door.setOpen(null, city, state, pos, open);
        }
    }

    /** The middle of the vault door at the foot of the stair, in the safe house's north wall. */
    public static BlockPos vaultDoor() { return LastDayData.origin().above(2); }

    /** Where people stand in front of the vault, at the foot of the stair. */
    public static Vec3 landing() { return at(LastDayData.origin(), .5, 1, -2.5); }

    /** Whether the vault at the foot of the stair stands open. */
    public static boolean vaultOpen(ServerLevel city) { return city.getBlockState(vaultDoor()).isAir(); }

    /**
     * Swings the vault door: shut, a round steel plug in its frame with the wheel on the stair side; open, the plug stands
     * out from its hinge on the east side of the frame, wheel and all.
     */
    static void vault(ServerLevel city, boolean open) {
        BlockPos o = LastDayData.origin();
        BlockState steel = net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState();
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockState wheel = net.minecraft.world.level.block.Blocks.GRINDSTONE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.GrindstoneBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL);
        int flags = net.minecraft.world.level.block.Block.UPDATE_ALL;
        // Wheels first, so neither is left hanging on air while the plug moves.
        city.setBlock(o.offset(0, 2, -1), air, flags);
        city.setBlock(o.offset(1, 2, -2), air, flags);
        for (int d = -1; d <= 1; d++)
            for (int dy = 1; dy <= 3; dy++) {
                city.setBlock(o.offset(d, dy, 0), open ? air : steel, flags);
                city.setBlock(o.offset(2, dy, -2 + d), open ? steel : air, flags);
            }
        if (open) city.setBlock(o.offset(1, 2, -2), wheel.setValue(net.minecraft.world.level.block.GrindstoneBlock.FACING, net.minecraft.core.Direction.WEST), flags);
        else city.setBlock(o.offset(0, 2, -1), wheel.setValue(net.minecraft.world.level.block.GrindstoneBlock.FACING, net.minecraft.core.Direction.NORTH), flags);
    }

    /** The vault swinging, heard on both sides of it. */
    private static void vaultSound(ServerLevel city, boolean open) {
        BlockPos at = vaultDoor();
        if (open) {
            city.playSound(null, at, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.4F, 0.5F);
            city.playSound(null, at, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.4F, 0.6F);
        } else {
            city.playSound(null, at, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.4F, 0.5F);
            city.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.2F, 0.5F);
            city.playSound(null, at, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.4F, 0.6F);
        }
    }

    /** The stair from the lobby to the vault, rock and all. */
    private static BoundingBox stairwell() {
        BlockPos o = LastDayData.origin();
        return new BoundingBox(o.getX() - 4, o.getY() - 1, o.getZ() - 8, o.getX() + 15, CityGrid.STREET_Y - 1, o.getZ());
    }

    /** Who lives in apartment {@code unit} this evening, in the order they arrived. */
    private static List<String> residents(MinecraftServer server, Run current, int unit) {
        List<String> names = new ArrayList<>();
        for (Member m : current.members.values()) {
            if (m.unit != unit) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p != null) names.add(p.getGameProfile().getName());
        }
        return names;
    }

    private static void plate(ServerLevel city, int unit, String name) { plate(city, unit, name.isEmpty() ? List.of() : List.of(name)); }

    /** The name plate by apartment {@code unit}'s door: its number, and who lives there (two names, then "& N more"). */
    private static void plate(ServerLevel city, int unit, List<String> names) {
        BlockPos pos = Apartments.plate(unit);
        if (city.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText().setColor(net.minecraft.world.item.DyeColor.BLACK);
            text = text.setMessage(1, Component.literal(Apartments.number(unit)));
            if (!names.isEmpty()) text = text.setMessage(2, Component.literal(names.get(0)));
            if (names.size() == 2) text = text.setMessage(3, Component.literal(names.get(1)));
            if (names.size() > 2) text = text.setMessage(3, Component.literal("& " + (names.size() - 1) + " more"));
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
            city.sendBlockUpdated(pos, city.getBlockState(pos), city.getBlockState(pos), 3);
        }
    }

    /** A pod's sign in the past bunker. */
    static void signPod(ServerLevel city, int index, String... lines) {
        BunkerBuilder.signPod(city, LastDayData.origin(), index, net.minecraft.world.item.DyeColor.LIGHT_BLUE, lines);
    }

    /** City chunks, and the bunker's beyond the city's edge, held loaded while a run plays. */
    private static void force(ServerLevel city, boolean on) {
        if (forced == on) return;
        forced = on;
        TimeIntroService.forceCity(city, on);
        BoundingBox box = PastBunker.bounds(LastDayData.origin());
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) city.setChunkForced(cx, cz, on);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public static void logout(ServerPlayer player) {
        WAITING.remove(player.getUUID());
        NOTICE.remove(player.getUUID());
        if (run == null) return;
        Member member = run.members.remove(player.getUUID());
        if (member == null) return;
        // Leaving mid-day: back to the start next time, unless they were already asleep.
        if (CryoChamberService.isInside(player)) CryoChamberService.release(player);
        player.setTicksFrozen(0);
        if (run.members.isEmpty()) end(player.getServer());
    }

    public static void shutdown(MinecraftServer server) {
        WAITING.clear();
        NOTICE.clear();
        end(server);
        forced = false;
    }

    // ------------------------------------------------------------------
    // For previews and tests
    // ------------------------------------------------------------------

    /** Jumps the running day to the start of a phase ("evening", "light", "safe", "noah", "pods"), or {@code ticks} into it. */
    public static boolean skipTo(MinecraftServer server, String phase, int ticks) {
        if (run == null) return false;
        ServerLevel city = city(server);
        switch (phase) {
            case "evening" -> { if (run.phase == Phase.APARTMENT) evening(server, city, run, true); }
            case "light" -> {
                if (run.phase == Phase.APARTMENT) evening(server, city, run, true);
                if (run.phase == Phase.EVENING) { run.clock = run.evening; light(server, city, run, true); }
                if (run.phase != Phase.LIGHT) return false;
            }
            case "safe" -> {
                if (run.phase.ordinal() < Phase.LIGHT.ordinal()) skipTo(server, "light", 0);
                BlockPos origin = LastDayData.origin();
                forEach(server, run, (player, member) -> {
                    player.teleportTo(origin.getX() + .5, origin.getY() + 1, origin.getZ() + 20.5);
                    member.inside = true;
                    cue(player, TimeIntroPayload.PROLOGUE_UNDERGROUND);
                });
                run.phase = Phase.SAFE_HOUSE;
            }
            case "noah" -> { if (run.phase.ordinal() < Phase.SAFE_HOUSE.ordinal()) skipTo(server, "safe", 0); run.phase = Phase.NOAH; run.age = 0; }
            case "pods" -> { if (run.phase.ordinal() < Phase.SAFE_HOUSE.ordinal()) skipTo(server, "safe", 0); run.phase = Phase.PODS; run.age = 0; }
            default -> { return false; }
        }
        // The next tick counts one on, so it plays the beat at exactly {@code ticks}.
        run.age = ticks - 1;
        return true;
    }

    /** For tests: whether a member has made it down into the safe house (or been carried there). */
    public static boolean sheltered(ServerPlayer player) {
        Member member = run == null ? null : run.members.get(player.getUUID());
        return member != null && (member.inside || member.stowed);
    }

    /** For previews: ticks into the phase now playing, or -1. */
    public static int age() { return run == null ? -1 : run.age; }

    /** For previews: the safe house's origin, the floor under the shaft. */
    public static BlockPos safeHouse() { return LastDayData.origin(); }

    /** The phase now playing, or "none". */
    public static String phase() { return run == null ? "none" : run.phase.name().toLowerCase(java.util.Locale.ROOT); }

    /** For tests: the pod a member was sealed in, or -1. */
    public static int pod(ServerPlayer player) {
        Member member = run == null ? null : run.members.get(player.getUUID());
        return member == null ? -1 : member.pod;
    }

    // ------------------------------------------------------------------
    // The bunker under Primatech
    // ------------------------------------------------------------------

    /** Whether the past bunker under Primatech has been built, saved with the city. */
    static final class LastDayData extends SavedData {
        /** Bumped when the bunker under Primatech must be rebuilt from the rock up. */
        static final int LAYOUT = 1;
        int layout;

        static LastDayData get(ServerLevel city) {
            return city.getDataStorage().computeIfAbsent(new Factory<>(LastDayData::new, (tag, registries) -> {
                LastDayData data = new LastDayData();
                data.layout = tag.getInt("layout");
                return data;
            }), "heroesevolved_last_day");
        }

        private static BlockPos origin;

        /** The bunker's floor under the shaft: under Primatech's lobby, as deep as keeps its roof below the street. */
        static BlockPos origin() {
            if (origin == null) {
                int top = PastBunker.bounds(BlockPos.ZERO).maxY();
                origin = new BlockPos(CityGrid.SHAFT_X, CityGrid.STREET_Y - 3 - top, CityGrid.SHAFT_Z);
            }
            return origin;
        }

        /** Builds the rock and the rooms the first time, once the city stands. */
        void build(ServerLevel city) {
            if (layout == LAYOUT) return;
            PastBunker.rock(city, origin());
            reset(city);
            layout = LAYOUT;
            setDirty();
            HeroesEvolved.LOGGER.info("Built the Last Day's safe house under Primatech at {}", origin());
        }

        /** Every room as it was that evening, every pod open and waiting, and the stair up to the lobby behind a shut vault. */
        void reset(ServerLevel city) {
            BlockPos o = origin();
            PastBunker.rooms(city, o);
            // The switchback from the lobby, which also fills in the old ladder shaft of earlier versions.
            com.jayptucker.heroesevolved.structure.Blueprint.load("lastday_stairwell").place(city, o, net.minecraft.world.level.block.Rotation.NONE);
            // Records' north door, three wide and three high to meet the vault.
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 1; dy <= 3; dy++)
                    city.setBlock(o.offset(dx, dy, 1), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            vault(city, false);
            for (int i = 0; i < BunkerBuilder.PODS; i++) {
                BlockPos base = o.offset(BunkerBuilder.podOffset(i));
                if (!city.getBlockState(base).is(ModBlocks.CRYOCHAMBER.get())) BunkerBuilder.placePod(city, o, i, true);
                else CryoChamberBlock.setOpen(city, base, true);
                BunkerBuilder.signPod(city, o, i, net.minecraft.world.item.DyeColor.LIGHT_BLUE,
                        String.format("SUBJECT %02d", i + 1), i == CLAIRE ? "C. BENNET" : "", "STANDBY", "");
            }
        }

        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("layout", layout);
            return tag;
        }
    }
}
