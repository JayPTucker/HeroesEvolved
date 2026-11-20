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
import com.jayptucker.heroesevolved.story.bunker.BunkerLayout;
import com.jayptucker.heroesevolved.story.bunker.HelixWard;
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
 *   Everyone meets in the lobby downstairs; the street doors open when all of you are there (or a minute after the
 *   last of you woke).</li>
 *   <li><b>Evening.</b> The doors open. Odessa is having an ordinary evening; its people talk about their day. You're
 *   sent for groceries at the City Market, up the avenue: the thread of light leads in at its door to the checkout
 *   counter, then back out front. Twenty seconds after the last of you has been to the counter, the light appears (or
 *   when the evening's time is up). Wooden doors open and shut by hand all day ({@link #useDoor}).</li>
 *   <li><b>The light.</b> Far up the avenue, a man begins to glow. Sirens. Primatech opens its doors and its agents wave
 *   people in. Get down the stair in the lobby, through the vault, before the light grows too bright: it surges, again and again, but you
 *   never see it go. Whoever is still out when time runs out is darted by an agent and carried down.</li>
 *   <li><b>The safe house.</b> The stasis bunker under Primatech, the same one you will wake in. Noah Bennet seals his
 *   daughter in Pod 14 while she begs to go and help, then turns to you: this is for your own good. A stun gun.</li>
 *   <li><b>The pods.</b> You come to sealed in your own pod. Screaming far above, the ground shaking, one terrible blow,
 *   silence. The pod hums on, frost creeps across your sight, and it all goes black. <i>103 years later...</i></li>
 * </ol>
 * Then the bunker under spawn wakes you, in the pod with the same number ({@link BunkerService#handOff}). One of a group
 * may be drawn for the sealed Helix Ward instead ({@link HelixWard}): carried to its private pod and dosed.
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
        /** Said they're ready, standing in the lobby; told how to (the chat button) once each time they come down. */
        boolean ready, prompted, inside, stowed, shot, shopped;
        /** The instruction line they were last given (a TimeIntroPayload stage), so it's sent only when it changes. */
        int hint = -1;
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
        /** The light's run: never shorter than the fight, the wave and a minute and a half to get down. */
        final int run = PrologueTimeline.run(20 * HeroesEvolvedConfig.COMMON.prologueRunSeconds.get());
        /** Ticks since the light appeared (-1 before it), whatever phase the day is in, and the evening clock it came at. */
        int sinceLight = -1, lightClock;
        /** The fight's facades, the people the rubble catches, the wave, the thread of light, and what the day broke. */
        LastDayFight.Facades facades = LastDayFight.Facades.GUESS;
        LastDayRescue rescue;
        LastDayWave wave = new LastDayWave();
        /** The buildings coming apart after the wave. */
        final LastDayCrumble crumble = new LastDayCrumble();
        final LastDayGuide guide = new LastDayGuide();
        final LastDayDamage damage = new LastDayDamage();
        /** Odessa's people who come down Primatech's stair after the sirens: into the hall, shut out, then the pods. */
        final LastDayShelter shelter = new LastDayShelter();
        /** The agents have drawn their guns; the sirens have started; the vault stands open. */
        boolean drawn, sirens, vaultOpen;
        VisionCrowd crowd;
        StoryActorEntity noah, claire;
        final List<CompanyAgentEntity> agents = new ArrayList<>();
        final List<Bubble> bubbles = new ArrayList<>();
        /** Who is to be stunned, by whom, and when the last shot was fired. */
        final List<Tase> tases = new ArrayList<>();
        int nextShot;
        /** How far the farthest shooter stood from whoever they stunned: tests check they walked up first. */
        double firedFrom = -1;
        /** The stasis hall's lights as they were when the blow came, for the flicker. */
        final Map<BlockPos, BlockState> lamps = new java.util.HashMap<>();
        /** Claire's pleas while people come down: how many she has made, and when the next may come. */
        int pleas, nextPlea;
        /** The vault at the foot of the stair has shut behind the last of them, and the safe house's age when it did. */
        boolean sealed;
        int sealedAt = -1;
        /** The evening's clock tick the light appears at, once everyone has reached the beacon (-1 until then). */
        int lightAt = -1;
        /** The one drawn for the Helix Ward's private pod when the light appeared, if anyone was. */
        UUID ward;
        /** When the first of them said they were ready (-1 until then), and whether the vote to go on has been offered. */
        int firstReady = -1;
        boolean voteOffered;
    }

    private record Bubble(Display.TextDisplay display, int until) {}

    private static Run run;
    /** New players waiting for the city, or for a run already under way to end. */
    private static final Set<UUID> WAITING = new LinkedHashSet<>();
    /** The last word each waiting player was given, so they hear only when it changes. */
    private static final Map<UUID, String> NOTICE = new java.util.HashMap<>();
    private static boolean forced;
    /** An admin asked for the next day to start at the pods (toPods while the city was still being built). */
    private static boolean podsNext;

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

    /**
     * Ready to go out: the chat button ({@code /lastday ready}) or typing "ready", standing in the lobby. When it's
     * everyone, the street doors open; until then the ready ones are told to wait for their friends.
     */
    public static void ready(ServerPlayer player) {
        Member member = run == null ? null : run.members.get(player.getUUID());
        if (member == null || run.phase != Phase.APARTMENT || member.ready) return;
        if (!Apartments.inLobby(player.position())) {
            player.sendSystemMessage(Component.translatable("prologue.heroesevolved.ready_downstairs").withStyle(ChatFormatting.GRAY));
            return;
        }
        member.ready = true;
        if (run.firstReady < 0) run.firstReady = run.age;
        long waiting = run.members.values().stream().filter(m -> !m.ready).count();
        if (waiting == 0) return;   // Everyone: the doors simply open on the next tick.
        player.sendSystemMessage(Component.translatable("prologue.heroesevolved.wait_for_friends", waiting).withStyle(ChatFormatting.GOLD));
        // The others hear who is waiting on them.
        Component word = Component.translatable("prologue.heroesevolved.is_ready", player.getGameProfile().getName(),
                run.members.size() - waiting, run.members.size()).withStyle(ChatFormatting.GRAY);
        forEach(player.getServer(), run, (other, m) -> { if (other != player) other.sendSystemMessage(word); });
    }

    /** Whether a member may break this block: only the rubble the fight brought down on someone. */
    public static boolean mayBreak(ServerPlayer player, BlockPos pos) {
        return run != null && run.rescue != null && run.members.containsKey(player.getUUID()) && run.rescue.breakable(pos);
    }

    /** Whether {@code target} is one of the people the rubble caught. */
    public static boolean isVictim(Entity target) {
        if (run == null || run.rescue == null) return false;
        for (LastDayRescue.Victim v : run.rescue.victims) if (v.body == target) return true;
        return false;
    }

    /**
     * Sneak and right-click someone freed from the rubble, or the ground beside them: they're lifted into the member's
     * arms. Trying while they're still buried says to dig them out first. True if it was handled (lifted or told why not).
     */
    public static boolean lift(ServerPlayer player, Vec3 near) {
        if (run == null || run.rescue == null || !run.members.containsKey(player.getUUID()) || !player.isShiftKeyDown()
                || com.jayptucker.heroesevolved.entity.EntityCarryService.isCarrying(player)) return false;
        LastDayRescue.Victim free = run.rescue.near(near, 2.5, LastDayRescue.State.FREE);
        if (free != null && player.position().distanceToSqr(free.body.position()) <= 4.5 * 4.5) {
            if (run.rescue.lift(player, free)) return true;
            player.displayClientMessage(Component.translatable("prologue.heroesevolved.no_room"), true);
            return true;
        }
        LastDayRescue.Victim buried = run.rescue.near(near, 2.5, LastDayRescue.State.PINNED);
        if (buried == null) return false;
        player.sendSystemMessage(Component.translatable("prologue.heroesevolved.dig_first", buried.shortName()).withStyle(ChatFormatting.GRAY));
        return true;
    }

    /** Whether a member has someone in their arms (so they may set them down on the city's blocks). */
    public static boolean carrying(ServerPlayer player) {
        return run != null && run.members.containsKey(player.getUUID()) && com.jayptucker.heroesevolved.entity.EntityCarryService.isCarrying(player);
    }

    /** "ready", typed in chat in the lobby, counts as the button. True if it was taken that way. */
    public static boolean chat(ServerPlayer player, String message) {
        Member member = run == null ? null : run.members.get(player.getUUID());
        if (member == null || run.phase != Phase.APARTMENT || member.ready) return false;
        String said = message.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z' ]", "");
        if (!READY_WORDS.contains(said)) return false;
        ready(player);
        return member.ready;
    }

    private static final Set<String> READY_WORDS = Set.of("ready", "im ready", "i'm ready", "rdy", "ready up", "lets go", "let's go");

    /**
     * Ready players who have waited this long for the rest may go without them ({@code /lastday go}): the doors open, and
     * whoever is still upstairs follows when they come down.
     */
    public static void goWithout(ServerPlayer player) {
        Member member = run == null ? null : run.members.get(player.getUUID());
        if (member == null || run.phase != Phase.APARTMENT || !member.ready || !run.voteOffered) return;
        Component word = Component.translatable("prologue.heroesevolved.went_without", player.getGameProfile().getName()).withStyle(ChatFormatting.GRAY);
        forEach(player.getServer(), run, (p, m) -> p.sendSystemMessage(word));
        evening(player.getServer(), city(player.getServer()), run, true);
    }

    /** The lobby's line: where you are, and the button to say you're ready (or type it). */
    private static Component readyPrompt(boolean alone) {
        Component button = Component.translatable("prologue.heroesevolved.ready_button").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lastday ready"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("prologue.heroesevolved.ready_hover"))));
        return Component.translatable(alone ? "prologue.heroesevolved.lobby_alone" : "prologue.heroesevolved.lobby", button)
                .withStyle(ChatFormatting.GOLD);
    }

    /** The vote to go on without whoever hasn't readied. */
    private static Component votePrompt(long waiting) {
        Component button = Component.translatable("prologue.heroesevolved.go_button").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lastday go"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("prologue.heroesevolved.go_hover"))));
        return Component.translatable("prologue.heroesevolved.vote", waiting, button).withStyle(ChatFormatting.GRAY);
    }

    /** Whether a hand can open or shut this: a wooden door or a fence gate (iron needs power; the vault is iron). */
    public static boolean openableByHand(BlockState state) {
        return state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()
                || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    /**
     * Someone living the day opening or shutting a door (or a gate) with their own hand, as anywhere else in the game.
     * Logged with the day's damage, so the next day finds every door as the city left it. True if it moved.
     */
    public static boolean useDoor(ServerPlayer player, BlockPos pos) {
        Run current = run;
        if (current == null || !current.members.containsKey(player.getUUID())) return false;
        ServerLevel city = player.serverLevel();
        BlockState state = city.getBlockState(pos);
        if (!openableByHand(state)) return false;
        float pitch = city.random.nextFloat() * 0.1F + 0.9F;
        boolean open;
        if (state.getBlock() instanceof DoorBlock door) {
            // Both halves, written as one: the log writes without neighbour updates, so neither half would follow the other.
            BlockPos lower = state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockState low = city.getBlockState(lower), up = city.getBlockState(lower.above());
            if (low.getBlock() != door || up.getBlock() != door) return false;
            open = !low.getValue(DoorBlock.OPEN);
            current.damage.set(city, lower, low.setValue(DoorBlock.OPEN, open));
            current.damage.set(city, lower.above(), up.setValue(DoorBlock.OPEN, open));
            city.playSound(null, pos, open ? door.type().doorOpen() : door.type().doorClose(), SoundSource.BLOCKS, 1, pitch);
        } else {
            var gate = net.minecraft.world.level.block.FenceGateBlock.OPEN;
            var facing = net.minecraft.world.level.block.FenceGateBlock.FACING;
            open = !state.getValue(gate);
            BlockState next = state.setValue(gate, open);
            // A gate swings away from whoever opens it, as in vanilla.
            if (open && state.getValue(facing) == player.getDirection().getOpposite()) next = next.setValue(facing, player.getDirection());
            current.damage.set(city, pos, next);
            city.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1, pitch);
        }
        city.gameEvent(player, open ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN : net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE, pos);
        return true;
    }

    /** For tests: whether the day has changed this block (and will put it back). */
    public static boolean changedToday(BlockPos pos) { return run != null && run.damage.changed(pos); }

    /**
     * A right-click on a block in the city. Before the evening the Arms' street doors are shut: trying them says who
     * the lobby is still waiting for, and how to say you're ready. True if it was handled.
     */
    public static boolean useBlock(ServerPlayer player, BlockPos pos) {
        Member member = run == null ? null : run.members.get(player.getUUID());
        if (member == null || run.phase != Phase.APARTMENT) return false;
        boolean front = Apartments.frontDoors().stream().anyMatch(door -> pos.equals(door) || pos.equals(door.above()));
        if (!front) return false;
        long waiting = run.members.values().stream().filter(m -> !m.ready).count();
        if (!member.ready) player.sendSystemMessage(readyPrompt(run.members.size() == 1));
        else player.sendSystemMessage(Component.translatable("prologue.heroesevolved.front_locked", waiting).withStyle(ChatFormatting.GRAY));
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
        player.sendSystemMessage(Component.translatable("prologue.heroesevolved.wake", Apartments.number(member.unit))
                .withStyle(ChatFormatting.GOLD));
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
            if (!WAITING.isEmpty() && cityData.ready() && !TimeIntroService.sceneRunning() && !AvenueService.running()) {
                start(server, city);
                if (podsNext && run != null) skipTo(server, "pods", 0);
                podsNext = false;
            }
            return;
        }
        Run current = run;
        current.age++;
        if (current.phase.ordinal() >= Phase.EVENING.ordinal()) current.clock++;
        // The light's own clock runs from the light on, whatever phase the day is in (a jump sets it with the phase's age).
        if (current.phase.ordinal() >= Phase.LIGHT.ordinal()) current.sinceLight++;
        // One new route map a tick, in every phase.
        current.guide.tick();
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
        if (current.phase.ordinal() >= Phase.LIGHT.ordinal() && current.phase.ordinal() <= Phase.NOAH.ordinal())
            current.shelter.tick(city, current.guide, vaultGoal(), hallGoal(), current.vaultOpen && !current.sealed, listeners(server, current));
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
    }

    private static void start(MinecraftServer server, ServerLevel city) {
        run = new Run();
        force(city, true);
        // A previous run's leftovers, even after a crash: its rubble and broken windows, its people.
        LastDayDamage.recover(city);
        List<Entity> stale = new ArrayList<>();
        for (Entity entity : city.getAllEntities()) if (isScene(entity)) stale.add(entity);
        stale.forEach(Entity::discard);
        LastDayData.get(city).reset(city);
        // Apartment doors open, so people can come down; the street doors shut until everyone is in the lobby.
        doors(city, Apartments.doors(), true);
        doors(city, Apartments.frontDoors(), false);
        for (int i = 0; i < Apartments.UNITS; i++) plate(city, i, "");
        for (UUID id : List.copyOf(WAITING)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            WAITING.remove(id);
            if (player != null) add(player, run);
        }
        if (run.members.isEmpty()) end(server);
    }

    /**
     * Everyone meets in the lobby and says they're ready (the chat button, or typing it). Coming down gives you the
     * button; going back up takes your word back. Nothing moves on a timer: once the ready ones have waited
     * {@code prologueReadySeconds} for the rest, they're offered the choice to go on without them.
     */
    private static void apartment(MinecraftServer server, ServerLevel city, Run current) {
        forEach(server, current, (player, member) -> {
            boolean lobby = Apartments.inLobby(player.position());
            if (lobby && !member.prompted && !member.ready) {
                member.prompted = true;
                player.sendSystemMessage(readyPrompt(current.members.size() == 1));
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.4F, 1.4F);
            } else if (!lobby && member.ready && current.age % 10 == 0) {
                member.ready = member.prompted = false;
                player.sendSystemMessage(Component.translatable("prologue.heroesevolved.unready").withStyle(ChatFormatting.GRAY));
            } else if (!lobby && !member.ready) member.prompted = false;
            // The thread of light down the stairwell to the lobby, until they're there.
            if (current.age % 10 == 0) {
                current.guide.guide(city, player, lobby ? null : lobbyGoal());
                if (!lobby) hint(player, member, TimeIntroPayload.PROLOGUE_LOBBY);
            }
        });
        boolean everyone = current.members.values().stream().allMatch(m -> m.ready);
        if (everyone) { evening(server, city, current, true); return; }
        // Someone isn't coming down: after a while, the ready ones may go on without them.
        boolean anyReady = current.members.values().stream().anyMatch(m -> m.ready);
        if (!anyReady) { current.firstReady = -1; current.voteOffered = false; return; }
        if (current.firstReady < 0) current.firstReady = current.age;
        if (!current.voteOffered && current.age - Math.max(current.firstReady, current.lastJoin) >= current.ready) {
            current.voteOffered = true;
            long waiting = current.members.values().stream().filter(m -> !m.ready).count();
            forEach(server, current, (player, member) -> { if (member.ready) player.sendSystemMessage(votePrompt(waiting)); });
        }
    }

    // ------------------------------------------------------------------
    // Where the thread of light leads
    // ------------------------------------------------------------------

    /** The lobby of the Odessa Arms, from anywhere in the building. */
    private static LastDayGuide.Goal lobbyGoal() {
        return new LastDayGuide.Goal(BlockPos.containing(Apartments.lobby()), new BoundingBox(Apartments.X0 - 1, Apartments.Y,
                Apartments.Z0 - 1, Apartments.X0 + Apartments.SIZE, Apartments.Y + Apartments.STOREY * (Apartments.FLOORS + 1) + 2,
                Apartments.Z0 + Apartments.DEPTH));
    }

    /** Odessa's streets where the members roam, at street level (the Arms' lobby and Primatech's included). */
    private static final BoundingBox STREETS = new BoundingBox(ROAM_X0, CityGrid.STREET_Y, ROAM_Z0, ROAM_X1, CityGrid.STREET_Y + 3,
            Math.min(ROAM_Z1, CityGrid.MAX));

    private static LastDayGuide.Goal street(Vec3 at) { return new LastDayGuide.Goal(BlockPos.containing(at), STREETS); }

    /** The head of the stair in Primatech's lobby. */
    private static LastDayGuide.Goal stairHead() { return new LastDayGuide.Goal(new BlockPos(CityGrid.STAIR_X0 - 1, CityGrid.STREET_Y + 1, 110), STREETS); }

    /** Down the stair to the vault, from anywhere on it. */
    private static LastDayGuide.Goal vaultGoal() {
        BlockPos o = LastDayData.origin();
        return new LastDayGuide.Goal(BlockPos.containing(landing()), new BoundingBox(o.getX() - 4, o.getY(), o.getZ() - 8,
                o.getX() + 16, CityGrid.STREET_Y + 2, o.getZ() + 4));
    }

    /** Through the bunker to the stasis hall. */
    private static LastDayGuide.Goal hallGoal() {
        BlockPos o = LastDayData.origin();
        return new LastDayGuide.Goal(BlockPos.containing(at(o, BunkerLayout.Scene.GATHER)), PastBunker.bounds(o));
    }

    /** Whether someone is on the stair under Primatech's lobby, below the street. */
    private static boolean onStair(ServerPlayer player) {
        return player.getY() < CityGrid.STREET_Y - 0.5 && stairwell().isInside(player.blockPosition());
    }

    /**
     * Where a member's thread leads once the light is up: nowhere while they watch and while they're down; then to the
     * nearest person still under the rubble, or freed and waiting to be lifted; with someone in their arms, or nobody
     * left to help, to Primatech's stair and down it; and below, to the hall.
     */
    private static LastDayGuide.Goal lightGoal(Run current, ServerPlayer player, Member member) {
        int t = current.sinceLight;
        if (member.dartedAt >= 0 || member.stowed) return null;
        if (member.inside) return inHall(player, LastDayData.origin()) ? null : hallGoal();
        if (current.wave.downNow(player, t)) return null;
        LastDayRescue rescue = current.rescue;
        boolean carrying = rescue != null && rescue.carrying(player);
        if (!carrying && rescue != null && t >= LastDayFight.pinnedAt(0)) {
            LastDayRescue.Victim trapped = rescue.nearestPinned(player.position());
            if (trapped == null) trapped = rescue.nearestFree(player.position());
            if (trapped != null) return street(trapped.spot);
        }
        if (t < PrologueTimeline.SHELTER && !carrying) return null;
        return onStair(player) ? vaultGoal() : stairHead();
    }

    /**
     * What the line tells a member to do once they're up after the wave: dig out whoever is trapped (or lift whoever is
     * free), carry them down, or just get to the stair once it's open. -1 for nothing new.
     */
    private static int lightHint(Run current, ServerPlayer player, Member member) {
        int t = current.sinceLight;
        if (member.inside || member.dartedAt >= 0 || member.stowed || !current.wave.reached(player) || current.wave.downNow(player, t)) return -1;
        LastDayRescue rescue = current.rescue;
        if (rescue != null && rescue.carrying(player)) return TimeIntroPayload.PROLOGUE_CARRYING;
        if (rescue != null && rescue.nearestPinned(player.position()) != null) return TimeIntroPayload.PROLOGUE_DIG;
        if (rescue != null && rescue.nearestFree(player.position()) != null) return TimeIntroPayload.PROLOGUE_LIFT;
        return t >= PrologueTimeline.SHELTER ? TimeIntroPayload.PROLOGUE_GO : -1;
    }

    /** Sends a member a new instruction line, if it changed. */
    private static void hint(ServerPlayer player, Member member, int stage) {
        if (stage < 0 || stage == member.hint) return;
        member.hint = stage;
        cue(player, stage);
    }

    /** The doors open on an ordinary evening. */
    private static void evening(MinecraftServer server, ServerLevel city, Run current, boolean begin) {
        current.phase = Phase.EVENING;
        current.age = 0;
        current.clock = 0;
        doors(city, Apartments.doors(), true);
        doors(city, Apartments.frontDoors(), true);
        // The doors opened: the way out of the Arms is new.
        current.guide.forget();
        current.crowd = VisionCrowd.spawn(city, TAG, TimeIntroService.CROWD_SIZE, new VisionCrowd.Timing(current.evening,
                current.evening + PrologueTimeline.SIRENS, Integer.MAX_VALUE, (int) ENTRANCE.z - 4, ROAM_Z0), 0);
        // An ordinary evening goes at an ordinary pace: fewer conversations, with pauses between lines.
        if (current.crowd != null) current.crowd.calm();
        // When the sirens go, Odessa's people run for Primatech too: in through its doors and down the stair.
        // At the head of the stair they're handed on (LastDayShelter), and go on down into the safe house.
        if (current.crowd != null) current.crowd.shelter(ENTRANCE, new Vec3(CityGrid.STAIR_X0 + .5, CityGrid.STREET_Y, CityGrid.STAIR_Z0 + 1.5),
                current.shelter::take);
        // The Company's men on Primatech's door: "Guards on every door at Primatech." "For a paper company?"
        for (double dx : new double[] {-2.5, 2.5}) agent(city, ENTRANCE.add(dx, 0, 0), 180);
        forEach(server, current, (player, member) -> {
            cue(player, TimeIntroPayload.PROLOGUE_EVENING);
            player.playNotifySound(SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1);
        });
    }

    private static void evening(MinecraftServer server, ServerLevel city, Run current) {
        // The errand: groceries from the City Market. The thread of light shows everyone the way up the avenue, in at the
        // door and down to the checkout counter, then back out front to wait; when the last of them has been to the counter,
        // the light comes twenty seconds later.
        if (current.age % 10 == 0) {
            Vec3 checkout = com.jayptucker.heroesevolved.time.intro.city.Market.checkout();
            Vec3 outFront = com.jayptucker.heroesevolved.time.intro.city.Market.beacon().add(0, 1, 0);
            forEach(server, current, (player, member) -> {
                if (!member.shopped && com.jayptucker.heroesevolved.time.intro.city.Market.arrived(player.position())) {
                    member.shopped = true;
                    player.sendSystemMessage(Component.translatable("prologue.heroesevolved.groceries").withStyle(ChatFormatting.GOLD));
                    player.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1);
                }
                current.guide.guide(city, player, !member.shopped ? street(checkout)
                        : com.jayptucker.heroesevolved.time.intro.city.Market.outFront(player.position()) ? null : street(outFront));
            });
            if (current.lightAt < 0 && current.members.values().stream().allMatch(m -> m.shopped)) current.lightAt = current.clock + LIGHT_DELAY;
        }
        if (current.clock >= current.evening || current.lightAt >= 0 && current.clock >= current.lightAt) light(server, city, current, true);
    }

    /**
     * Far up the avenue, a man begins to glow. The vault below stays shut until the sirens: first the fight, the rubble
     * and the wave ({@link LastDayFight}, {@link PrologueTimeline}).
     */
    private static void light(MinecraftServer server, ServerLevel city, Run current, boolean begin) {
        current.phase = Phase.LIGHT;
        current.age = 0;
        current.sinceLight = 0;
        current.lightClock = current.clock;
        BlockPos origin = LastDayData.origin();
        // The Company's list: one of a group is drawn for the Helix Ward (never someone living the day alone).
        current.ward = HelixWard.draw(BunkerData.get(server), List.copyOf(current.members.keySet()), city.random);
        // The facades the fight will tear at, read before anything breaks; and the people on the sidewalks under them.
        current.facades = LastDayFight.find(city);
        current.rescue = LastDayRescue.start(city, current.facades, current.members.size(), current.damage, TAG);
        // Odessa's people stare from now, and run when the sirens go.
        if (current.crowd != null) current.crowd.timing(new VisionCrowd.Timing(current.clock, current.clock + PrologueTimeline.SIRENS,
                Integer.MAX_VALUE, (int) ENTRANCE.z - 4, ROAM_Z0));
        // Down below: guards at the head of the stair, by the vault and along the way to the stasis hall, and the Bennets
        // by Pod 14.
        agent(city, new Vec3(CityGrid.STAIR_X0 - .5, CityGrid.STREET_Y + 1, CityGrid.STAIR_Z1 + 2.5), 160);
        for (int i = 0; i < BunkerLayout.Scene.GUARDS.size(); i++)
            agent(city, at(origin, BunkerLayout.Scene.GUARDS.get(i)), BunkerLayout.Scene.GUARD_YAW[i]);
        current.noah = StoryActorEntity.spawn(city, Role.NOAH, at(origin, BunkerLayout.Scene.NOAH), 0, TAG);
        current.claire = StoryActorEntity.spawn(city, Role.CLAIRE, at(origin, BunkerLayout.Scene.CLAIRE), 180, TAG);
        if (current.claire != null) current.claire.pose(StoryActorEntity.Pose.PLEAD);
        if (current.noah != null && current.claire != null) current.noah.face(current.claire.position());
        forEach(server, current, (player, member) -> lightCue(player, current));
    }

    /** The light's cue: the run's length, how far into it we are, and the fight's facades, so every client draws the same. */
    private static void lightCue(ServerPlayer player, Run current) {
        List<Integer> data = new ArrayList<>(List.of(current.run, Math.max(0, current.sinceLight)));
        data.addAll(current.facades.encode());
        PacketDistributor.sendToPlayer(player, new TimeIntroPayload(TimeIntroPayload.PROLOGUE_LIGHT, data));
    }

    private static void light(MinecraftServer server, ServerLevel city, Run current) {
        int t = current.age;
        // The fight begins: the Company's men draw.
        if (t >= LastDayFight.ACTION && !current.drawn) {
            current.drawn = true;
            current.agents.forEach(CompanyAgentEntity::draw);
        }
        if (t >= PrologueTimeline.SIRENS && !current.sirens) {
            current.sirens = true;
            forEach(server, current, (player, member) -> cue(player, TimeIntroPayload.PROLOGUE_SIRENS));
        }
        // The vault opens with the sirens, and the stair counts as shelter from then.
        if (t >= PrologueTimeline.SHELTER && !current.vaultOpen) {
            current.vaultOpen = true;
            vault(city, true);
            vaultSound(city, true);
            current.guide.forget();
        }
        rescue(server, city, current);
        // Ted's wave: the windows, and everyone thrown flat.
        List<ServerPlayer> everyone = new ArrayList<>();
        forEach(server, current, (player, member) -> { if (member.dartedAt < 0 && !member.stowed) everyone.add(player); });
        current.wave.tick(city, t, everyone, p -> {
            Member m = current.members.get(p.getUUID());
            return m != null && m.inside || p.getY() < CityGrid.STREET_Y - 4;
        }, current.damage, current.crowd, current.clock);
        // And after it, the city coming apart round everyone still on the street.
        List<ServerPlayer> street = new ArrayList<>();
        forEach(server, current, (player, member) -> {
            if (member.dartedAt < 0 && !member.inside && !member.stowed && player.getY() > CityGrid.STREET_Y - 4) street.add(player);
        });
        current.crumble.tick(city, t, street, current.damage, pos -> spared(current, pos), TAG);
        // The street changed under the thread's maps: drawn again now and then, not every tick.
        if (current.crumble.changed && t % 40 == 0) {
            current.crumble.changed = false;
            current.guide.forget();
        }
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
        // The agents call people in once everyone is back on their feet: a few calls, spaced out, each said once.
        String[] calls = {"Inside! Get inside, now!", "Bring them with you! Down the stairs in the lobby!", "Don't look at it! Move!"};
        int callsFrom = PrologueTimeline.WAVE + PrologueTimeline.DAZE + 40;
        int call = (t - callsFrom) / 400;
        if (t >= callsFrom && (t - callsFrom) % 400 == 0 && call < calls.length && !current.agents.isEmpty())
            shout(server, current, current.agents.get(call % 2), calls[call]);
        if (t >= PrologueTimeline.SHELTER) sheltered(server, city, current);
        darts(server, city, current);
        // The thread of light, and the line saying what to do: to whoever is trapped, then to the stair.
        if (t % 10 == 0) forEach(server, current, (player, member) -> {
            current.guide.guide(city, player, lightGoal(current, player, member));
            hint(player, member, lightHint(current, player, member));
        });
        // Claire, below, already begging.
        if (current.claire != null && anyInside(current) && current.pleas < CLAIRE_PLEAS.length && current.age >= current.nextPlea) {
            say(server, current, current.claire, CLAIRE_PLEAS[current.pleas], false);
            current.pleas++;
            current.nextPlea = current.age + 220;
        }
        boolean everyoneSafe = current.members.values().stream().allMatch(m -> m.inside || m.stowed);
        if (t >= current.run) {
            // Time's up: whoever is still outside is darted where they stand, and whoever they couldn't reach is lost.
            if (t == current.run && current.rescue != null) current.rescue.deadline(listeners(server, current), p -> { Member m = current.members.get(p.getUUID()); return m != null && m.inside; });
            forEach(server, current, (player, member) -> { if (!member.inside && member.dartedAt < 0) dart(city, current, player, member); });
        }
        if (everyoneSafe && t >= PrologueTimeline.SHELTER || t >= current.run + 60) {
            if (t < current.run && current.rescue != null) current.rescue.deadline(listeners(server, current), p -> { Member m = current.members.get(p.getUUID()); return m != null && m.inside; });
            next(current, Phase.SAFE_HOUSE);
        }
    }

    /** The people under the rubble: what the fight does to them, and what the players do for them. */
    private static void rescue(MinecraftServer server, ServerLevel city, Run current) {
        if (current.rescue == null || current.sinceLight < 0) return;
        BlockPos origin = LastDayData.origin();
        current.rescue.inHall = p -> inHall(p, origin);
        int before = current.rescue.rubble.size();
        current.rescue.tick(city, current.sinceLight, p -> {
            Member m = current.members.get(p.getUUID());
            return m != null && m.inside;
        }, k -> rescuedSpot(city, k), listeners(server, current));
        // New stone in the street: the thread's maps go round it.
        if (current.rescue.rubble.size() != before) current.guide.forget();
    }

    /** Cells the crumbling city leaves alone: round the people under the rubble, and their stone. */
    private static boolean spared(Run current, BlockPos pos) {
        if (current.rescue == null) return false;
        if (current.rescue.rubble.contains(pos)) return true;
        for (LastDayRescue.Victim v : current.rescue.victims)
            if (Math.abs(pos.getX() + .5 - v.spot.x) < 3.5 && Math.abs(pos.getZ() + .5 - v.spot.z) < 4.5 && pos.getY() < CityGrid.STREET_Y + 4) return true;
        return false;
    }

    /** Where the {@code k}th person carried down is laid in the stasis hall. */
    private static Vec3 rescuedSpot(ServerLevel city, int k) {
        List<Vec3> spots = BunkerLayout.Scene.RESCUED;
        Vec3 spot = at(LastDayData.origin(), spots.get(Math.floorMod(k, spots.size())));
        return LastDayRescue.standable(city, spot);
    }

    private static List<ServerPlayer> listeners(MinecraftServer server, Run current) {
        List<ServerPlayer> out = new ArrayList<>();
        forEach(server, current, (player, member) -> out.add(player));
        return out;
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
            if (member.inside && !member.stowed && player.level() == city && player.getY() > CityGrid.STREET_Y - 3) move(player, landing());
        });
    }

    /** A dart from an agent behind them; the world goes dark; they are carried down and sealed in. */
    private static void dart(ServerLevel city, Run current, ServerPlayer player, Member member) {
        member.dartedAt = current.clock;
        if (com.jayptucker.heroesevolved.entity.EntityCarryService.isCarrying(player)) com.jayptucker.heroesevolved.entity.EntityCarryService.release(player);
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
        rescue(server, city, current);
        if (current.age % 10 == 0) forEach(server, current, (player, member) -> current.guide.guide(city, player,
                member.inside && !member.stowed && !inHall(player, LastDayData.origin()) ? hallGoal() : null));
        BlockPos origin = LastDayData.origin();
        sealVault(server, city, current, origin);
        // Anyone still standing by the vault, listening to them, is moved on.
        if (current.sealedAt >= 0 && current.age == current.sealedAt + 140 && current.members.values().stream()
                .anyMatch(m -> m.inside && !m.stowed && !inHall(server.getPlayerList().getPlayer(m.id), origin)))
            shout(server, current, null, "Keep moving! Down the hall, all of you! Don't look back!");
        boolean gathered = current.members.values().stream().allMatch(m -> m.stowed
                || m.inside && inHall(server.getPlayerList().getPlayer(m.id), origin));
        boolean pending = current.members.values().stream().anyMatch(m -> m.dartedAt >= 0 && !m.stowed);
        // Said once each, not on a loop, while the last of them come in.
        if (current.age == 60 && current.claire != null) say(server, current, current.claire, "Peter's still out there! There has to be another way!", false);
        if (pending || !gathered && current.age < 600) return;
        // Anyone still dawdling in the corridor is brought into the hall, and whoever they carried is laid down there.
        if (current.rescue != null) current.rescue.layAll(k -> rescuedSpot(city, k));
        forEach(server, current, (player, member) -> {
            if (!member.stowed && !inHall(player, origin)) gather(player, origin);
        });
        current.guide.clear(listeners(server, current));
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
                move(player, new Vec3(origin.getX() + .5, origin.getY() + 1, origin.getZ() + 3.5));
        });
        current.sealed = true;
        current.sealedAt = current.age;
        // The guard by the door steps back through as it swings.
        for (CompanyAgentEntity agent : current.agents)
            if (agent.isAlive() && agent.getZ() <= origin.getZ() + 1.2 && agent.getY() < CityGrid.STREET_Y - 4)
                agent.teleportTo(origin.getX() - 1.5, origin.getY() + 1, origin.getZ() + 4.5);
        vault(city, false);
        vaultSound(city, false);
        // Too soon: whoever is still on the stair is shut out, pounding on the steel.
        current.shelter.shut(city);
        shout(server, current, null, "That's all we can take! Seal it!");
        shout(server, current, null, "You! Don't stop! Move down the hall, now!");
        forEach(server, current, (player, member) -> {
            if (member.inside && !member.stowed) PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.12F, 14));
        });
        if (current.claire != null) say(server, current, current.claire, "No... no, no, no. Dad, they shut it!", false);
    }

    /**
     * Noah's scene by Pod 14, in ticks from its start: slow enough to read every line before the next (four to six seconds
     * a line). After "for your own good" he draws the stun gun and walks up to them; alone, he does it himself; with more
     * than one, he orders his guards in, and each walks up to someone and fires.
     */
    public static final int NOAH_PLEA = 0, NOAH_LISTEN = 100, NOAH_HEAL = 200, NOAH_UNCLE = 320, NOAH_NOBODY = 430, NOAH_KNEW = 540,
            NOAH_PLEASE = 650, NOAH_SHOVE = 730, NOAH_SEAL = 734, NOAH_GLASS = 750, NOAH_PROMISE = 790, NOAH_TURN = 880,
            NOAH_OWN_GOOD = 960, NOAH_APPROACH = 1050, NOAH_ORDER = 1190;
    /** How close the one firing walks up before raising the stun gun, how long the aim, and how long they'll walk at most. */
    static final double TASE_REACH = 2.0;
    static final int TASE_AIM = 30, TASE_WALK = 140;
    /**
     * How briskly Noah walks up to them: a mob's walk goes with the square of its speed, and at the scene's usual 0.5
     * he covered about a block a second and fired from eight blocks off.
     */
    static final double STRIDE = 0.95;

    /** Someone to be stunned, and who fires: Noah or one of his guards. */
    private static final class Tase {
        final net.minecraft.world.entity.LivingEntity shooter;
        final UUID target;
        int began = -1, aimAt = -1;
        boolean fired;
        Tase(net.minecraft.world.entity.LivingEntity shooter, UUID target) { this.shooter = shooter; this.target = target; }
    }

    /** The scene by Pod 14. */
    private static void noah(MinecraftServer server, ServerLevel city, Run current) {
        int t = current.age;
        BlockPos origin = LastDayData.origin();
        StoryActorEntity noah = current.noah, claire = current.claire;
        // Nobody leaves the hall while it happens: anyone who tries is brought back in.
        forEach(server, current, (player, member) -> {
            if (!member.stowed && !inHall(player, origin)) {
                gather(player, origin);
                player.displayClientMessage(Component.translatable("prologue.heroesevolved.no_way_out"), true);
            }
        });
        if (noah == null || claire == null) { if (t > 20) next(current, Phase.PODS); return; }
        BlockPos podBase = origin.offset(BunkerLayout.STASIS.podOffset(CLAIRE));
        Vec3 inPod = CryoChamberBlock.standing(podBase);
        Vec3 front = inPod.add(-1.3, 0, 0);
        switch (t) {
            case NOAH_PLEA -> {
                claire.face(noah.position()); claire.pose(StoryActorEntity.Pose.PLEAD);
                noah.face(claire.position());
                say(server, current, claire, "Dad, I don't want to go, please! I can stop this!", true);
            }
            case NOAH_LISTEN -> say(server, current, noah, "Claire-bear. Listen to me. You have to get in.", false);
            case NOAH_HEAL -> say(server, current, claire, "I can heal! Whatever it does to me, I'll heal. Let me go up there and help Peter!", true);
            case NOAH_UNCLE -> say(server, current, claire, "He's my uncle! He'll die up there if I'm not with him!", true);
            case NOAH_NOBODY -> {
                say(server, current, noah, "Nobody walks away from that. Not even you.", false);
                claire.pose(StoryActorEntity.Pose.IDLE);
                claire.walkTo(front, 0.45);
                noah.walkTo(front.add(-1.2, 0, -0.6), 0.45);
            }
            case NOAH_KNEW -> say(server, current, noah, "Peter knew what he was walking into. I won't lose you too.", false);
            case NOAH_PLEASE -> {
                claire.face(noah.position()); claire.pose(StoryActorEntity.Pose.PLEAD);
                noah.face(claire.position());
                say(server, current, claire, "Dad, please! Don't do this!", true);
            }
            case NOAH_SHOVE -> noah.pose(StoryActorEntity.Pose.SHOVE);
            // He pushes her in and seals the pod.
            case NOAH_SEAL -> sealClaire(city, claire, origin);
            case NOAH_GLASS -> { noah.pose(StoryActorEntity.Pose.HAND_ON_GLASS); noah.face(inPod.add(0, 1.4, 0)); }
            case NOAH_PROMISE -> say(server, current, noah, "I'll come back for you. I promise.", false);
            case NOAH_TURN -> {
                noah.pose(StoryActorEntity.Pose.IDLE);
                noah.walkTo(at(origin, BunkerLayout.Scene.NOAH_TURN), 0.5);
            }
            case NOAH_OWN_GOOD -> {
                Vec3 crowd = watchersCentre(server, current);
                if (crowd != null) noah.face(crowd.add(0, 1.5, 0));
                say(server, current, noah, "I know you don't understand. This is for your own good.", false);
            }
            case NOAH_APPROACH -> {
                // He draws the stun gun and walks up to them.
                noah.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.jayptucker.heroesevolved.registry.ModItems.STUN_GUN.get()));
                city.playSound(null, noah.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.HOSTILE, 0.8F, 1.1F);
                Vec3 crowd = watchersCentre(server, current);
                if (crowd != null) noah.walkTo(short_(noah.position(), crowd, TASE_REACH), STRIDE);
            }
            case NOAH_ORDER -> {
                Vec3 crowd = watchersCentre(server, current);
                com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.debug("Last Day: Noah gives the order {} blocks from them",
                        crowd == null ? "?" : String.format("%.1f", noah.position().distanceTo(crowd)));
                order(server, city, current, noah);
            }
            default -> { }
        }
        if (t >= NOAH_ORDER) {
            boolean done = tase(server, city, current, t);
            if (done && t >= current.nextShot + 40) next(current, Phase.PODS);
        }
    }

    /** {@code to}, less {@code by} blocks along the way from {@code from}: where to stop short of someone. */
    private static Vec3 short_(Vec3 from, Vec3 to, double by) {
        Vec3 d = to.subtract(from).multiply(1, 0, 1);
        double len = d.length();
        return len <= by ? from : from.add(d.scale((len - by) / len));
    }

    /**
     * Who fires at whom: alone, Noah does it himself; with more than one of them, he calls his guards in, and each guard
     * takes someone (in turn, if there are more of them than guards).
     */
    private static void order(MinecraftServer server, ServerLevel city, Run current, StoryActorEntity noah) {
        current.tases.clear();
        List<UUID> targets = new ArrayList<>();
        current.members.values().stream().filter(m -> !m.stowed).forEach(m -> targets.add(m.id));
        if (targets.isEmpty()) return;
        if (targets.size() == 1) {
            say(server, current, noah, "Hold still. It'll be over in a second.", false);
            current.tases.add(new Tase(noah, targets.getFirst()));
            return;
        }
        say(server, current, noah, "Guards! Take them down. Gently.", true);
        BoundingBox bunker = PastBunker.bounds(LastDayData.origin());
        Vec3 crowd = watchersCentre(server, current);
        List<CompanyAgentEntity> guards = new ArrayList<>(current.agents.stream()
                .filter(a -> !a.isRemoved() && !a.outCold() && bunker.isInside(a.blockPosition())).toList());
        if (crowd != null) guards.sort(java.util.Comparator.comparingDouble(a -> a.distanceToSqr(crowd)));
        if (guards.isEmpty()) {
            for (UUID id : targets) current.tases.add(new Tase(noah, id));
            return;
        }
        for (CompanyAgentEntity guard : guards) {
            guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.world.item.ItemStack(com.jayptucker.heroesevolved.registry.ModItems.STUN_GUN.get()));
        }
        for (int i = 0; i < targets.size(); i++) current.tases.add(new Tase(guards.get(i % guards.size()), targets.get(i)));
    }

    /**
     * Every tasing, a step at a time: the one firing walks up to their target (following them round the hall), raises the
     * stun gun, holds it a moment, and fires. One at a time for each shooter. True once every shot has been fired.
     */
    private static boolean tase(MinecraftServer server, ServerLevel city, Run current, int t) {
        boolean done = true;
        java.util.Set<net.minecraft.world.entity.LivingEntity> busy = new java.util.HashSet<>();
        int left = (int) current.tases.stream().filter(x -> !x.fired).count();
        for (Tase tase : current.tases) {
            if (tase.fired) continue;
            done = false;
            if (!busy.add(tase.shooter)) continue;   // this one has someone else to deal with first
            ServerPlayer player = server.getPlayerList().getPlayer(tase.target);
            Member member = current.members.get(tase.target);
            if (player == null || member == null || member.stowed) { tase.fired = true; continue; }
            if (tase.began < 0) tase.began = t;
            Vec3 at = player.position();
            // A walk ends a block or so short of where it was aimed (and one that short never starts), so an arm's length
            // and a block and a half count as there.
            boolean close = tase.shooter.position().distanceToSqr(at) <= (TASE_REACH + 1.5) * (TASE_REACH + 1.5);
            if (tase.aimAt < 0) {
                if (close || t - tase.began >= TASE_WALK) {
                    tase.aimAt = t;
                    com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.debug("Last Day: {} raises the stun gun at {} from {} blocks, {} ticks after the order",
                            tase.shooter.getName().getString(), player.getName().getString(), String.format("%.1f", tase.shooter.distanceTo(player)), t - NOAH_ORDER);
                    aim(tase.shooter, player, true);
                    city.playSound(null, tase.shooter.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.HOSTILE, 0.7F, 1.6F);
                } else walk(tase.shooter, at, t);
                continue;
            }
            face(tase.shooter, player.getEyePosition());
            if (t - tase.aimAt < TASE_AIM) continue;
            current.firedFrom = Math.max(current.firedFrom, tase.shooter.distanceTo(player));
            shoot(city, tase.shooter, player, member, left - 1);
            aim(tase.shooter, player, false);
            tase.fired = true;
            left--;
            current.nextShot = t;
        }
        return done;
    }

    /** One step closer to someone: Noah walks there, a guard is moved a stride at a time (he has no will of his own). */
    private static void walk(net.minecraft.world.entity.LivingEntity shooter, Vec3 to, int t) {
        Vec3 stop = short_(shooter.position(), to, TASE_REACH);
        if (shooter instanceof StoryActorEntity actor) {
            if (t % 10 == 0) actor.walkTo(stop, STRIDE);
            return;
        }
        Vec3 d = stop.subtract(shooter.position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 0.01) return;
        Vec3 step = d.length() > 0.16 ? d.normalize().scale(0.16) : d;
        shooter.move(net.minecraft.world.entity.MoverType.SELF, step);
        face(shooter, to.add(0, 1.5, 0));
    }

    /** Turns the shooter, body and head, toward {@code at}. */
    private static void face(net.minecraft.world.entity.LivingEntity shooter, Vec3 at) {
        if (shooter instanceof StoryActorEntity actor) { actor.face(at); return; }
        Vec3 d = at.subtract(shooter.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        shooter.setYRot(yaw); shooter.setYBodyRot(yaw); shooter.setYHeadRot(yaw);
    }

    /** The stun gun raised (Noah's arm straight out, a guard's two-handed hold), or lowered again. */
    private static void aim(net.minecraft.world.entity.LivingEntity shooter, ServerPlayer at, boolean up) {
        face(shooter, at.getEyePosition());
        if (shooter instanceof StoryActorEntity actor) actor.pose(up ? StoryActorEntity.Pose.AIM : StoryActorEntity.Pose.IDLE);
        else if (shooter instanceof CompanyAgentEntity agent) agent.aiming(up);
    }

    /** Claire in Pod 14, its lid shut and its sign set. */
    private static void sealClaire(ServerLevel city, StoryActorEntity claire, BlockPos origin) {
        BlockPos podBase = origin.offset(BunkerLayout.STASIS.podOffset(CLAIRE));
        // On the pod's floor, her feet in its base plate, and held there: stood on the plate, her head met the lid.
        Vec3 inPod = Vec3.atBottomCenterOf(podBase);
        BlockState state = city.getBlockState(podBase);
        float yaw = state.hasProperty(CryoChamberBlock.FACING) ? CryoChamberBlock.yaw(state.getValue(CryoChamberBlock.FACING)) : 270;
        claire.place(inPod, yaw);
        claire.setNoGravity(true);
        claire.setDeltaMovement(Vec3.ZERO);
        claire.pose(StoryActorEntity.Pose.SEALED);
        if (state.getBlock() instanceof CryoChamberBlock) CryoChamberBlock.setOpen(city, podBase, false);
        city.playSound(null, podBase, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1, 0.6F);
        city.playSound(null, podBase, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.6F);
        city.playSound(null, podBase, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.BLOCKS, 0.8F, 0.8F);
        city.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, inPod.x, inPod.y + 1, inPod.z, 30, .3, .6, .3, .02);
        signPod(city, CLAIRE, "SUBJECT 14", "C. BENNET", "STASIS", "SEALED");
    }

    /** The stun gun: the barbs, the current, the floor. */
    private static void shoot(ServerLevel city, net.minecraft.world.entity.LivingEntity shooter, ServerPlayer player, Member member, int after) {
        face(shooter, player.getEyePosition());
        Vec3 look = player.getEyePosition().subtract(shooter.getEyePosition()).normalize();
        Vec3 muzzle = shooter.getEyePosition().add(look.scale(0.7)).add(0, -0.3, 0);
        SetPiecePayload.send(city, Piece.GUN_TASER, muzzle, player.position().add(0, 1.1, 0), 1, player);
        city.playSound(null, shooter.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 0.7F, 1.8F);
        city.playSound(null, player.blockPosition(), SoundEvents.BEEHIVE_WORK, SoundSource.HOSTILE, 1, 2);
        member.shot = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 6, false, false));
        ScreenTintPayload.send(player, 0xD0B8DCFF, 12, ScreenTintPayload.Style.FLASH);
        // Dark until the pods, however many are still to be shot after them (each walk, aim and shot, then the pause).
        ScreenTintPayload.send(player, 0xFF000000, 160 + (TASE_WALK + TASE_AIM) * after, ScreenTintPayload.Style.HOLD);
        PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.35F, 18));
        player.sendSystemMessage(Component.translatable("prologue.heroesevolved.shot").withStyle(ChatFormatting.AQUA));
    }

    /**
     * Sealed in their own pod: the number they will wake under, 103 years from now. The one drawn for the Helix Ward is
     * carried to its private pod instead and given the dose there; the ward is theirs for good.
     */
    private static void seal(ServerLevel city, ServerPlayer player, Member member, boolean blink) {
        BlockPos origin = LastDayData.origin();
        BunkerData data = BunkerData.get(player.getServer());
        if (run != null && player.getUUID().equals(run.ward) && data.labOpen()) {
            data.claimLab(player.getUUID());
            HelixWard.dose(player);
        }
        member.pod = data.pod(player.getUUID(), BunkerBuilder.PODS, CLAIRE);
        BlockPos base = origin.offset(BunkerLayout.STASIS.podOffset(member.pod));
        if (!city.getBlockState(base).is(ModBlocks.CRYOCHAMBER.get())) BunkerBuilder.placePod(city, origin, BunkerLayout.STASIS, member.pod, true);
        CryoChamberService.enter(player, base, 20 * 60 * 10);
        signPod(city, member.pod, String.format("SUBJECT %02d", member.pod + 1), player.getGameProfile().getName(), "STASIS", "SEALED");
        member.stowed = true;
        player.getPersistentData().putBoolean(SLEPT, true);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        ScreenTintPayload.send(player, 0xFF000000, blink ? 30 : 400, blink ? ScreenTintPayload.Style.FLASH : ScreenTintPayload.Style.HOLD);
    }

    /**
     * The pods' last beats, in ticks from the blink: the title "103 years later..." and its music, then the waking. The
     * waking waits for the music (story_years, nine and a half seconds) to finish: it takes them to the bunker under spawn,
     * another dimension, and the client stops every sound on the way.
     */
    public static final int PODS_YEARS = 395, PODS_WAKE = PODS_YEARS + 190;

    /** Sealed in, as the city dies above them. */
    private static void pods(MinecraftServer server, ServerLevel city, Run current) {
        int t = current.age;
        BlockPos origin = LastDayData.origin();
        if (t == 0) {
            if (current.noah != null) { current.noah.discard(); current.noah = null; }
            current.agents.forEach(Entity::discard);
            current.agents.clear();
            // Those the players carried down stay below (into the pods, after the players have theirs).
            List<net.minecraft.world.entity.npc.Villager> saved = current.rescue != null ? current.rescue.takeSaved() : List.of();
            if (current.rescue != null) current.rescue.discard();
            current.guide.clear(listeners(server, current));
            // They blink, and they are in their pods: the shot ones and the darted ones together.
            forEach(server, current, (player, member) -> {
                // Whatever dark they went under in lifts for the blink (a zero-length tint cancels its match).
                ScreenTintPayload.send(player, 0xFF000000, 0, ScreenTintPayload.Style.HOLD);
                ScreenTintPayload.send(player, 0xC0000000, 0, ScreenTintPayload.Style.EDGE);
                if (!member.stowed) seal(city, player, member, true);
                else ScreenTintPayload.send(player, 0xFF000000, 30, ScreenTintPayload.Style.FLASH);
                cue(player, member.pod == BunkerLayout.LAB_POD ? TimeIntroPayload.PROLOGUE_WARD : TimeIntroPayload.PROLOGUE_POD);
                player.sendSystemMessage(Component.translatable(member.pod == BunkerLayout.LAB_POD ? "prologue.heroesevolved.pod_ward"
                        : "prologue.heroesevolved.pod").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            });
            // And Odessa's people in the pods nobody else was given (LastDayShelter): a hundred and three years later,
            // those pods still hold them (PodSleepers).
            current.shelter.seal(city, BunkerData.get(server), saved);
        }
        // The ground shakes harder and harder; dust sifts from the ceiling.
        if (t > 20 && t < 170 && t % 20 == 0) {
            float k = (t - 20) / 150F;
            forEach(server, current, (player, member) -> PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.04F + 0.18F * k, 24)));
            for (int i = 0; i < 6; i++) {
                Vec3 at = at(origin, -5 + city.random.nextInt(11), 10, BunkerLayout.Scene.RUBBLE_Z0 + city.random.nextInt(28));
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
            forEach(server, current, (player, member) -> ScreenTintPayload.send(player, alpha << 24, PODS_WAKE + 20 - t, ScreenTintPayload.Style.HOLD));
        }
        if (t == PODS_YEARS) {
            forEach(server, current, (player, member) -> {
                cue(player, TimeIntroPayload.PROLOGUE_YEARS);
                // Over the swell and the chime as the counter turns, gone as the chord dies away.
                player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 130, 30));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("prologue.heroesevolved.years_later")
                        .withStyle(ChatFormatting.WHITE)));
            });
        }
        if (t >= PODS_WAKE) {
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
            var hall = BunkerLayout.Scene.HALL;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(hall.minX(), hall.minY(), hall.minZ()), origin.offset(hall.maxX(), hall.maxY(), hall.maxZ()))) {
                BlockState state = city.getBlockState(pos);
                // Every light the bunker's power feeds (froglights, bulbs, lanterns, rods), and its beacons.
                if (com.jayptucker.heroesevolved.story.bunker.BunkerPower.off(state) != null
                        || state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                        && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))
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
            if (k == 0) city.playSound(null, origin.offset(0, 4, 50), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.6F);
            if (k % 8 == 4) city.playSound(null, origin.offset(0, 6, 38 + city.random.nextInt(28)), SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.6F, 0.8F + city.random.nextFloat() * 0.5F);
        }
        // Rubble: a burst at the blow, then a trickle, down the aisle between the rows of pods.
        int pieces = k == 0 ? 9 : k <= 60 && k % 6 == 0 ? 1 + city.random.nextInt(2) : 0;
        BlockState[] stone = {net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES.defaultBlockState()};
        for (int i = 0; i < pieces; i++) {
            int x = origin.getX() + BunkerLayout.Scene.RUBBLE_X0 + city.random.nextInt(BunkerLayout.Scene.RUBBLE_X1 - BunkerLayout.Scene.RUBBLE_X0 + 1);
            int z = origin.getZ() + BunkerLayout.Scene.RUBBLE_Z0 + city.random.nextInt(BunkerLayout.Scene.RUBBLE_Z1 - BunkerLayout.Scene.RUBBLE_Z0 + 1);
            BlockPos at = null;
            for (int y = origin.getY() + 2; y <= origin.getY() + 11; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!city.getBlockState(pos).isAir()) break;
                at = pos;
            }
            if (at == null || at.getY() < origin.getY() + 3) continue;
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
                Vec3 at = at(origin, -5 + city.random.nextInt(11), 9, BunkerLayout.Scene.RUBBLE_Z0 + city.random.nextInt(30));
                city.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                        net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState()), at.x, at.y, at.z, 10, 1.2, .2, 1.2, 0);
            }
    }

    /** Puts a hall light on or off: as the bunker's own power-out leaves it (BunkerPower.off), or by its lit state. */
    private static void light(ServerLevel city, BlockPos pos, BlockState original, boolean on) {
        BlockState dark = com.jayptucker.heroesevolved.story.bunker.BunkerPower.off(original);
        if (dark == null && original.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))
            dark = original.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false);
        BlockState state = on ? original : dark == null ? original : dark;
        if (city.getBlockState(pos) != state) city.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    /** Out of the Last Day and into the bunker under spawn, 103 years later. */
    private static void handOff(ServerPlayer player, Member member) {
        player.setTicksFrozen(0);
        if (player.getForcedPose() == net.minecraft.world.entity.Pose.SLEEPING) player.setForcedPose(null);
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
        if (current.rescue != null) current.rescue.discard();
        current.shelter.discard();
        current.crumble.discard();
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
            // The rubble, the torn facades and every burst window, as they were.
            current.damage.restore(city);
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
        // Walk a copy: adding a player to the run teleports them into the city, and the dimension change that causes
        // removes them from WAITING too, which crashed the server mid-loop when it walked the set itself.
        for (UUID id : List.copyOf(WAITING)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) { WAITING.remove(id); continue; }
            if (run != null && open(run)) {
                WAITING.remove(id);
                add(player, run);
                continue;
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
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static ServerLevel city(MinecraftServer server) { return server.getLevel(TimeIntroService.DIMENSION); }

    private static Vec3 at(BlockPos origin, double x, double y, double z) { return new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z); }

    private static Vec3 at(BlockPos origin, Vec3 offset) { return at(origin, offset.x, offset.y, offset.z); }

    private static boolean inHall(ServerPlayer player, BlockPos origin) {
        if (player == null) return false;
        var hall = BunkerLayout.Scene.HALL;
        BlockPos at = player.blockPosition().subtract(origin);
        return at.getY() >= 0 && at.getY() <= hall.maxY() && at.getX() >= hall.minX() && at.getX() <= hall.maxX()
                && at.getZ() >= hall.minZ() - 1 && at.getZ() <= hall.maxZ();
    }

    /** Brings someone left outside the stasis hall to just inside its door. */
    private static void gather(ServerPlayer player, BlockPos origin) { move(player, at(origin, BunkerLayout.Scene.GATHER)); }

    /** Moves a member within the city, and whoever is in their arms with them. */
    private static void move(ServerPlayer player, Vec3 to) {
        com.jayptucker.heroesevolved.entity.EntityCarryService.travelWithCargo(player, player.serverLevel(), to, player.getYRot(), () -> {
            player.teleportTo(to.x, to.y, to.z);
            return true;
        });
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
        // Until the fight starts nobody draws: the Company's men stand easy, guns on their hips.
        if (run != null && !run.drawn) agent.holster();
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
        BunkerBuilder.signPod(city, LastDayData.origin(), BunkerLayout.STASIS, index, net.minecraft.world.item.DyeColor.LIGHT_BLUE, lines);
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
        if (player.getForcedPose() == net.minecraft.world.entity.Pose.SLEEPING) player.setForcedPose(null);
        if (run.members.isEmpty()) end(player.getServer());
    }

    public static void shutdown(MinecraftServer server) {
        WAITING.clear();
        NOTICE.clear();
        end(server);
        forced = false;
        podsNext = false;
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
                    gather(player, origin);
                    member.inside = true;
                    cue(player, TimeIntroPayload.PROLOGUE_UNDERGROUND);
                });
                run.phase = Phase.SAFE_HOUSE;
            }
            case "noah" -> { if (run.phase.ordinal() < Phase.SAFE_HOUSE.ordinal()) skipTo(server, "safe", 0); run.phase = Phase.NOAH; run.age = 0; }
            case "pods" -> {
                if (run.phase.ordinal() < Phase.SAFE_HOUSE.ordinal()) skipTo(server, "safe", 0);
                // What the skipped scenes would have done: the vault shut behind them, Claire in Pod 14.
                boolean claireOut = run.phase.ordinal() < Phase.NOAH.ordinal() || run.phase == Phase.NOAH && run.age < NOAH_SEAL;
                if (!run.sealed) { run.sealed = true; vault(city, false); }
                if (claireOut && run.claire != null) sealClaire(city, run.claire, LastDayData.origin());
                run.phase = Phase.PODS;
                run.age = 0;
            }
            default -> { return false; }
        }
        // The next tick counts one on, so it plays the beat at exactly {@code ticks}.
        run.age = ticks - 1;
        if (run.phase == Phase.LIGHT) {
            run.sinceLight = ticks - 1;
            // Every client's drawing of the fight runs from the light's start: move it to match.
            Run jumped = run;
            forEach(server, jumped, (player, member) -> {
                jumped.sinceLight = ticks;
                lightCue(player, jumped);
                jumped.sinceLight = ticks - 1;
            });
        }
        return true;
    }

    /** What {@link #toPods} did. */
    public enum PodJump { JUMPED, QUEUED, ALREADY, NONE }

    /**
     * The admin menu's "To cryopod": the Last Day jumps to everyone being sealed in their pods (the Helix Ward's
     * sleeper drawn as usual), then plays on: the blast, "103 years later", and the waking in the bunker. While new
     * players are still waiting for the city to be built, the jump is made the moment their day begins.
     */
    public static PodJump toPods(MinecraftServer server) {
        if (run == null) {
            if (WAITING.isEmpty()) return PodJump.NONE;
            podsNext = true;
            return PodJump.QUEUED;
        }
        if (run.phase == Phase.PODS) return PodJump.ALREADY;
        return skipTo(server, "pods", 0) ? PodJump.JUMPED : PodJump.NONE;
    }

    /** Whether "To cryopod" has anything to do: a day before its pods, or new players waiting for one. */
    public static boolean podsAhead() { return run == null ? !WAITING.isEmpty() && !podsNext : run.phase != Phase.PODS; }

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

    /** How long after everyone reaches the market's beacon the light appears: twenty seconds. */
    public static final int LIGHT_DELAY = 20 * 20;

    /** For tests: ticks until the light appears because everyone reached the beacon, or -1 if not everyone has. */
    public static int lightIn() { return run == null || run.lightAt < 0 ? -1 : Math.max(0, run.lightAt - run.clock); }

    /** For tests: the people the rubble catches (their bodies), in order. */
    public static List<net.minecraft.world.entity.npc.Villager> victims() {
        return run == null || run.rescue == null ? List.of() : run.rescue.victims.stream().map(v -> v.body).toList();
    }

    /** For tests: how each of them is ("waiting", "pinned", "free", "carried", "saved", "lost"). */
    public static List<String> victimStates() {
        return run == null || run.rescue == null ? List.of()
                : run.rescue.victims.stream().map(v -> v.state.name().toLowerCase(java.util.Locale.ROOT)).toList();
    }

    /** For tests: the stone holding victim {@code i} down. */
    public static List<BlockPos> pins(int i) {
        return run == null || run.rescue == null || i >= run.rescue.victims.size() ? List.of() : List.copyOf(run.rescue.victims.get(i).pins);
    }

    /** For tests: how far the farthest stun gun was from its target when it fired (-1 before any has). */
    public static double firedFrom() { return run == null ? -1 : run.firedFrom; }

    /** For tests: how many of Odessa's people have reached the stasis hall (or the commons), and how many are shut out. */
    public static int gathered() { return run == null ? 0 : run.shelter.gathered(); }

    public static int shutOut() { return run == null ? 0 : run.shelter.shutOut(); }

    /** For tests: someone from the street at {@code at}, handed straight to the shelter to be led down. */
    public static net.minecraft.world.entity.npc.Villager shelterSomeone(ServerLevel city, Vec3 at) {
        if (run == null) return null;
        var villager = LastDayShelter.person(city, run.shelter.people.size());
        if (villager == null) return null;
        villager.moveTo(at.x, at.y, at.z, 90, 0);
        if (!city.addFreshEntity(villager)) return null;
        run.shelter.take(villager);
        return villager;
    }

    /** For tests and previews: how long this day's light lasts before the agents dart whoever is still out. */
    public static int runTicks() { return run == null ? PrologueTimeline.run(20 * HeroesEvolvedConfig.COMMON.prologueRunSeconds.get()) : run.run; }

    /** For tests: how many blocks the crumbling city has torn loose, and how many it has landed or strewn as rubble. */
    public static int crumbled() { return run == null ? 0 : run.crumble.loosened; }
    public static int strewn() { return run == null ? 0 : run.crumble.landed; }

    /** For tests: the first block the crumbling city tore loose, or null. */
    public static BlockPos firstCrumbled() { return run == null ? null : run.crumble.first; }

    /** For tests: how many shards of glass lie about, and the first pane the wave burst. */
    public static int shards() { return run == null ? 0 : run.wave.shards(); }
    public static BlockPos firstWindow() { return run == null ? null : run.wave.firstWindow; }

    /** For previews: where to stand to look at the nearest hole the wave tore in a facade (null if there's none). */
    public static Vec3[] nearestHole(Vec3 from) {
        if (run == null) return null;
        LastDayCrumble c = run.crumble;
        int best = -1;
        for (int i = 0; i < c.holes.size(); i++)
            if (best < 0 || c.holes.get(i).distToCenterSqr(from) < c.holes.get(best).distToCenterSqr(from)) best = i;
        if (best < 0) return null;
        Vec3 hole = Vec3.atCenterOf(c.holes.get(best));
        net.minecraft.core.Direction out = c.holeFacing.get(best);
        Vec3 eye = hole.add(out.getStepX() * 8, 2, out.getStepZ() * 8);
        return new Vec3[] {eye, hole};
    }

    /** For previews: what the day has broken round a point. */
    public static String damageNear(Vec3 at, double r) { return run == null ? "no day" : run.damage.near(city(serverOf()), at, r); }

    private static MinecraftServer serverOf() { return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(); }

    /** For tests: how many windows the wave has burst. */
    public static int windowsBroken() { return run == null ? 0 : run.wave.windows(); }

    /** For tests: whether the wave has this player on the ground right now. */
    public static boolean dazed(ServerPlayer player) { return run != null && run.wave.downNow(player, run.sinceLight); }

    /** For tests: the Company's men above and below. */
    public static List<CompanyAgentEntity> agents() { return run == null ? List.of() : List.copyOf(run.agents); }

    /** For tests: the facades the fight tore at, as found when the light came. */
    public static LastDayFight.Facades facades() { return run == null ? LastDayFight.Facades.GUESS : run.facades; }

    /** For tests: who was drawn for the Helix Ward this run, or null. */
    public static UUID ward() { return run == null ? null : run.ward; }

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
        /** Bumped when the bunker under Primatech must be rebuilt from the rock up. 2: the Sublevel 4 rebuild. */
        static final int LAYOUT = 2;
        int layout;

        static LastDayData get(ServerLevel city) {
            return city.getDataStorage().computeIfAbsent(new Factory<>(LastDayData::new, (tag, registries) -> {
                LastDayData data = new LastDayData();
                data.layout = tag.getInt("layout");
                return data;
            }), "heroesevolved_last_day");
        }

        private static BlockPos origin;

        /**
         * The bunker's floor under the shaft: under Primatech's lobby, eighteen below the street, where the stairwell
         * blueprint ({@code lastday_stairwell}) meets the vault. The rooms keep their roof below the street
         * ({@code PrologueGameTests.apartmentsShaftAndPodsFit}).
         */
        static BlockPos origin() {
            if (origin == null) origin = new BlockPos(CityGrid.SHAFT_X, CityGrid.STREET_Y - 18, CityGrid.SHAFT_Z);
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
            BunkerLayout layout = BunkerLayout.STASIS;
            for (int i = 0; i < layout.pods(); i++) {
                BlockPos base = o.offset(layout.podOffset(i));
                if (!city.getBlockState(base).is(ModBlocks.CRYOCHAMBER.get())) BunkerBuilder.placePod(city, o, layout, i, true);
                else CryoChamberBlock.setOpen(city, base, true);
                BunkerBuilder.signPod(city, o, layout, i, net.minecraft.world.item.DyeColor.LIGHT_BLUE,
                        String.format("SUBJECT %02d", i + 1), i == CLAIRE ? "C. BENNET" : "", "STANDBY", "");
            }
        }

        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("layout", layout);
            return tag;
        }
    }
}
