package com.jayptucker.heroesevolved.story;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.expansion.FacilityData;
import com.jayptucker.heroesevolved.expansion.FacilityService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.TimeIntroPayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryData;
import com.jayptucker.heroesevolved.time.intro.FractureCity;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Avenue at 6:14, the finale. Odessa, four minutes before the light. Noah Bennet holds the
 * Company's street door at the south end of the avenue until 6:14; runners bring him everyone they
 * can, and keep Gabriel Gray from walking up to Sprague and learning how his heat works. The light
 * can't be stopped. Whoever is through the door when it falls lives, and the present remembers them.
 *
 * <p>One run at a time, in the city the Time vision uses, and never during a vision. Unlike the
 * vision it is played: every power works here. A run lives in memory; only return tickets and
 * results are saved, so a crash or shutdown sends everyone home and the evening simply restarts.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class AvenueService {
    public static final int CITIZENS = 24;
    /** 6:10 to 6:14 PM. */
    public static final int LIGHT_TICK = 4 * 60 * 20;
    /**
     * Everyone is pulled back before the shock wave reaches them, as in the vision: each runner when the
     * front is {@link #PULL_GAP} blocks away, and anyone left (nowhere in the city is further) by this tick.
     */
    public static final int RETURN_TICK = LIGHT_TICK + 200;
    public static final int PULL_GAP = 24;
    /** Hits it takes to turn Gray around; time stopped or pushed back also keeps him away. */
    public static final int GRAY_TURNS = 3;
    public static final String TAG = "heroesevolved_avenue";
    public static final Vec3 ARRIVAL = new Vec3(4, 64, 85);
    public static final BlockPos DOOR = new BlockPos(4, 64, 92);
    public static final Vec3 SPRAGUE = new Vec3(4.5, 64, -77.5);
    public static final Vec3 GRAY_START = new Vec3(6.5, 64, 52.5);
    /** Within this many blocks of Sprague, Gray sees how the heat works. */
    public static final double GRAY_SEES = 18;
    private static final int MAX_RUNNERS = 8, HELPER_FIRST = 600, HELPER_EVERY = 900, HELPER_REACH = 48;
    private static final int TURN_COOLDOWN = 40, STAGGER = 50;
    private static final float FOLLOW_SPEED = 0.7F, DOOR_SPEED = 0.65F;
    private static final int SHELTER_X0 = 0, SHELTER_X1 = 7, SHELTER_Z0 = 89, SHELTER_Z1 = 95;
    private static final int CITY_MIN = FractureCity.MIN + 1, CITY_MAX = FractureCity.MIN + FractureCity.WIDTH - 2;

    /** The twenty-four people on the avenue at 6:10. Original characters. */
    public static final List<String> NAMES = List.of(
            "Lena, the baker", "Tomas, a bus driver", "Mrs. Okafor", "Danny, age 8", "Rosa, the florist", "Old Walt",
            "Priya, a nurse", "Jonah, the paper boy", "Mr. Abernathy", "Kim, from the pharmacy", "Sofia, age 6", "Marcus, a mechanic",
            "Ines, a student", "Grandpa Leo", "Hana, a teacher", "Eddie, the mailman", "Beatriz, a cook", "Sam, a cab driver",
            "Nadia, a lawyer", "Felix, age 11", "Ruth, the librarian", "Owen, a busker", "Mei, a dentist", "Carl, the doorman");
    private static final Set<Integer> CHILDREN = Set.of(3, 10, 19);
    /** Where each of them stands, on the avenue and the streets that cross it; nearest the door first. */
    private static final double[][] SPOTS = {
            {2.5, 70.5}, {20.5, 68.5}, {-14.5, 68.5}, {1.5, 50.5}, {6.5, 38.5}, {36.5, 68.5},
            {-28.5, 60.5}, {36.5, 36.5}, {-40.5, 36.5}, {2.5, 20.5}, {5.5, 4.5}, {24.5, 4.5},
            {-20.5, 4.5}, {68.5, 52.5}, {-60.5, 68.5}, {1.5, -12.5}, {6.5, -27.5}, {-30.5, -27.5},
            {40.5, -27.5}, {2.5, -44.5}, {5.5, -59.5}, {-44.5, -59.5}, {52.5, -59.5}, {-60.5, 20.5}};
    private static final VillagerType[] LOOKS = {VillagerType.PLAINS, VillagerType.TAIGA, VillagerType.SAVANNA,
            VillagerType.DESERT, VillagerType.SNOW, VillagerType.JUNGLE, VillagerType.SWAMP};
    private static final String[] FOLLOW_LINES = {
            "I'll follow you. Go!", "Where are we going? ...OK. I'm right behind you.",
            "Is that man glowing? Yes. Yes, I'm coming.", "Bennet? From the paper company? Lead the way."};

    static final class Citizen {
        final String name;
        final Villager body;
        final boolean child;
        boolean following, sent, safe;
        Citizen(String name, Villager body, boolean child) { this.name = name; this.body = body; this.child = child; }
    }

    static final class Run {
        final Set<UUID> runners = new LinkedHashSet<>();
        final List<Citizen> citizens = new ArrayList<>();
        final List<String> safe = new ArrayList<>();
        final Set<UUID> knockedOut = new HashSet<>(), burned = new HashSet<>(), warned = new HashSet<>();
        Villager bennet, mara, ivo, sprague;
        SylarEntity gray;
        int ticks, turns, lastTurn = -TURN_COOLDOWN, staggeredUntil, goneAt = -1, nextHelper = HELPER_FIRST;
        double lastGrayZ = GRAY_START.z;
        int stuckSince;
        boolean grayTurned, grayWatched, maraNext = true;
    }

    private static Run run;

    private AvenueService() {}

    // ------------------------------------------------------------------
    // Who is where
    // ------------------------------------------------------------------

    public static boolean running() { return run != null; }
    public static boolean isRunner(ServerPlayer player) {
        return run != null && run.runners.contains(player.getUUID()) && TimeIntroService.isCity(player.level());
    }
    public static boolean isScene(Entity entity) { return entity.getTags().contains(TAG); }
    /** Hiro's key opens the avenue: it is found by bringing Mara and Ivo out of the Annex. */
    public static boolean unlocked(ServerPlayer player) {
        return FacilityData.get(player.getServer()).discovered(player.getUUID(), "annex_saved");
    }

    // ------------------------------------------------------------------
    // Going and coming back
    // ------------------------------------------------------------------

    public static boolean enter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel city = server.getLevel(TimeIntroService.DIMENSION);
        if (city == null || !PowerRules.canAct(player) || player.isPassenger() || player.isSpectator()
                || TimeIntroService.isCity(player.level()) || FacilityService.atSite(player)
                || com.jayptucker.heroesevolved.story.night.NightThreeService.inNight(player)) return false;
        if (!unlocked(player)) {
            tell(player, "The avenue isn't open to you yet. Hiro's key is in the Hourglass Annex: bring Mara and Ivo out alive first.");
            return false;
        }
        if (!FractureCity.get(city).ready() || TimeIntroService.sceneRunning()) {
            tell(player, "Someone's vision is passing through Odessa. Try again in a few seconds.");
            return false;
        }
        if (run != null && run.ticks >= LIGHT_TICK) {
            tell(player, "The light has already fallen on this evening. Wait a moment for it to end, then walk back in.");
            return false;
        }
        if (run != null && run.runners.size() >= MAX_RUNNERS) {
            tell(player, "Eight people are already on the avenue. Wait for this evening to end.");
            return false;
        }
        AvenueData data = AvenueData.get(server);
        data.tickets.put(player.getUUID(), new FacilityData.ReturnPoint(player.level().dimension().location(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        data.setDirty(); // Saved before crossing dimensions.
        if (run == null) start(city);
        EntityCarryService.release(player);
        player.closeContainer();
        player.stopRiding();
        int slot = run.runners.size();
        player.teleportTo(city, ARRIVAL.x + (slot % 4 - 1.5) * 1.2, ARRIVAL.y, ARRIVAL.z - slot / 4 * 1.5, 180, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        run.runners.add(player.getUUID());
        PacketDistributor.sendToPlayer(player, new TimeIntroPayload(TimeIntroPayload.AVENUE_ARRIVAL));
        player.playNotifySound(ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 0.9F, 1);
        ScreenTintPayload.send(player, 0x887E58B8, 24, ScreenTintPayload.Style.FLASH);
        tell(player, "ODESSA. THE LAST DAY, " + clock(run.ticks) + ". Ted Sprague is glowing at the north end of the avenue. At 6:14 the light falls, and nothing stops it.", ChatFormatting.GOLD);
        tell(player, "Noah Bennet is holding the Company's door behind you until then. Use a person to have them follow you, or sneak + use to carry them. Everyone through his door at 6:14 lives.");
        tell(player, "A man in a dark coat is walking up the avenue toward the light. Nobody else is. Turn him back before he gets there.", ChatFormatting.DARK_PURPLE);
        return true;
    }

    /** Back to the present, mid-run. Returns false if the player wasn't on the avenue. */
    public static boolean leave(ServerPlayer player) {
        AvenueData data = AvenueData.get(player.getServer());
        FacilityData.ReturnPoint point = data.tickets.remove(player.getUUID());
        boolean runner = run != null && run.runners.remove(player.getUUID());
        if (point == null && !runner) return false;
        data.setDirty();
        EntityCarryService.release(player);
        if (TimeIntroService.isCity(player.level())) {
            FacilityService.returnTo(player, point);
            PacketDistributor.sendToPlayer(player, new TimeIntroPayload(TimeIntroPayload.CANCEL));
        }
        return true;
    }

    /** On login (and respawn): nobody wakes up stranded on the avenue, and Hiro's key reaches whoever earned it. */
    public static void recover(ServerPlayer player) {
        if (!(run != null && isRunner(player)) && AvenueData.get(player.getServer()).tickets.containsKey(player.getUUID())) leave(player);
        if (unlocked(player) && !PastRecords.has(player, PastRecords.KEY)) PastRecords.give(player, PastRecords.key());
    }

    public static void logout(ServerPlayer player) {
        if (run != null && run.runners.contains(player.getUUID()) || AvenueData.get(player.getServer()).tickets.containsKey(player.getUUID()))
            leave(player);
    }

    public static void shutdown(MinecraftServer server) {
        ServerLevel city = server.getLevel(TimeIntroService.DIMENSION);
        if (run != null && city != null) finish(server, city, false);
        run = null;
    }

    /** Lethal harm on the avenue throws a runner back to the present instead. */
    public static void knockOut(ServerPlayer player) {
        if (run != null) run.knockedOut.add(player.getUUID());
    }

    // ------------------------------------------------------------------
    // The evening
    // ------------------------------------------------------------------

    private static void start(ServerLevel city) {
        Run next = new Run();
        run = next;
        TimeIntroService.forceCity(city, true);
        // People are never saved to disk mid-run, but a crash can leave a few; clear them first.
        List<Entity> stale = new ArrayList<>();
        for (Entity entity : city.getAllEntities()) if (isScene(entity)) stale.add(entity);
        stale.forEach(Entity::discard);
        buildShelter(city);
        for (int i = 0; i < CITIZENS; i++) {
            Villager body = person(city, SPOTS[i][0], SPOTS[i][1], 180, NAMES.get(i), i, CHILDREN.contains(i));
            if (body != null) next.citizens.add(new Citizen(NAMES.get(i), body, CHILDREN.contains(i)));
        }
        next.bennet = person(city, 1.5, 88.3, 180, "Noah Bennet", 0, false);
        next.mara = person(city, 6.5, 87.5, 180, "Mara, the Cartographer", 2, false);
        next.ivo = person(city, 6.5, 85.5, 180, "Ivo, the Relay Keeper", 1, false);
        next.sprague = person(city, SPRAGUE.x, SPRAGUE.z, 0, "Ted Sprague", 0, false);
        if (next.sprague != null) next.sprague.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false));
        SylarEntity gray = ModEntities.SYLAR.get().create(city);
        if (gray != null) {
            gray.moveTo(GRAY_START.x, GRAY_START.y, GRAY_START.z, 180, 0);
            gray.setCustomName(Component.literal("Gabriel Gray"));
            gray.setCustomNameVisible(true);
            gray.addTag(TAG);
            if (city.addFreshEntity(gray)) next.gray = gray;
        }
    }

    private static Villager person(ServerLevel city, double x, double z, float yaw, String name, int look, boolean child) {
        Villager villager = EntityType.VILLAGER.create(city);
        if (villager == null) return null;
        villager.moveTo(x, 64, z, yaw, 0);
        villager.setYHeadRot(yaw);
        villager.setVillagerData(villager.getVillagerData().setType(LOOKS[look % LOOKS.length])
                .setProfession(VillagerProfession.NONE));
        if (child) villager.setBaby(true);
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(true);
        villager.setNoAi(true); // Standing still, staring north at the glow, until someone comes for them.
        villager.setSilent(true);
        villager.setPersistenceRequired();
        villager.addTag(TAG);
        return city.addFreshEntity(villager) ? villager : null;
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        Run current = run;
        if (current == null) return;
        ServerLevel city = server.getLevel(TimeIntroService.DIMENSION);
        if (city == null) { run = null; return; }
        AvenueData data = AvenueData.get(server);
        current.runners.removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && TimeIntroService.isCity(player.level())) return false;
            if (player != null && data.tickets.remove(id) != null) data.setDirty(); // Left by some other door.
            return true;
        });
        for (UUID id : List.copyOf(current.knockedOut)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            leave(player);
            tell(player, "The light isn't the only thing that can kill you on the Last Day. You're thrown back to the present.", ChatFormatting.RED);
        }
        current.knockedOut.clear();
        if (current.runners.isEmpty()) { finish(server, city, false); return; }

        current.ticks++;
        forEachRunner(server, current, player -> keepInCity(city, player));
        if (current.ticks < LIGHT_TICK) {
            if (current.ticks % 10 == 0) { tickCitizens(server, current); tickGray(server, current); }
            if (current.ticks % 20 == 0) { glow(server, city, current); clockBar(server, current); }
            if (current.ticks >= current.nextHelper) { helper(server, current); current.nextHelper += HELPER_EVERY; }
            if (current.ticks == LIGHT_TICK - 60 * 20) broadcast(server, current, "6:13 PM. One minute. Bennet is looking at his watch.", ChatFormatting.GOLD);
            if (current.ticks == LIGHT_TICK - 15 * 20) broadcast(server, current, "Fifteen seconds. Bennet has his hand on the door.", ChatFormatting.RED);
        }
        if (current.ticks == LIGHT_TICK) light(server, current);
        if (current.ticks == LIGHT_TICK + 10) forEachRunner(server, current, player -> {
            PacketDistributor.sendToPlayer(player, new TimeIntroPayload(TimeIntroPayload.BLAST_SOUND));
            PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.38F, 100));
        });
        if (current.ticks > LIGHT_TICK) {
            // Everyone watches the wave come down the streets, and is pulled home a breath before it reaches them.
            float front = com.jayptucker.heroesevolved.time.intro.IntroBlast.front(current.ticks - LIGHT_TICK);
            for (UUID id : List.copyOf(current.runners)) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) continue;
                double distance = com.jayptucker.heroesevolved.time.intro.IntroBlast.distance(player.getX(), player.getZ());
                double gap = distance - front;
                if (gap <= PULL_GAP + 22 * com.jayptucker.heroesevolved.time.intro.IntroBlast.SPEED && current.warned.add(id)) {
                    player.playNotifySound(ModSounds.TIME_SLOW_STOP.get(), SoundSource.PLAYERS, 0.9F, 1);
                    ScreenTintPayload.send(player, 0x709E80E8, 22, ScreenTintPayload.Style.EDGE);
                    AbilityEffects.at(player, Kind.TEMPORAL_REWIND, 4);
                }
                if (gap <= PULL_GAP) {
                    current.runners.remove(id);
                    home(server, current, player, true);
                }
            }
            if (current.runners.isEmpty()) { finish(server, city, true); return; }
        }
        if (current.ticks >= RETURN_TICK) finish(server, city, true);
    }

    private static void keepInCity(ServerLevel city, ServerPlayer player) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        if (x >= CITY_MIN && x <= CITY_MAX && z >= CITY_MIN && z <= CITY_MAX && y >= 60 && y <= 160) return;
        player.teleportTo(city, Math.clamp(x, CITY_MIN + 1, CITY_MAX - 1), Math.clamp(y, 64, 150),
                Math.clamp(z, CITY_MIN + 1, CITY_MAX - 1), player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    private static void tickCitizens(MinecraftServer server, Run current) {
        List<ServerPlayer> runners = runners(server, current);
        Vec3 door = Vec3.atBottomCenterOf(DOOR);
        for (Citizen citizen : current.citizens) {
            Villager body = citizen.body;
            if (citizen.safe || !body.isAlive()) continue;
            if (inShelter(body.position())) { shelter(server, current, citizen); continue; }
            if (EntityCarryService.isBeingCarried(body)) { citizen.following = true; continue; }
            if (!citizen.following && !citizen.sent) continue;
            if (body.isNoAi()) body.setNoAi(false);
            ServerPlayer guide = !citizen.following ? null : runners.stream()
                    .filter(player -> player.distanceToSqr(body) <= 24 * 24)
                    .min(Comparator.comparingDouble(player -> player.distanceToSqr(body))).orElse(null);
            if (guide != null) {
                body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(guide, FOLLOW_SPEED, 1));
            } else if (citizen.sent || body.position().distanceToSqr(door) <= 40 * 40) {
                // Close enough to see the door: they make for it on their own.
                body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(DOOR, DOOR_SPEED, 0));
            } else {
                body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        }
    }

    static boolean inShelter(Vec3 position) {
        return position.x >= SHELTER_X0 + 0.3 && position.x <= SHELTER_X1 + 0.7
                && position.z >= SHELTER_Z0 + 0.3 && position.z <= SHELTER_Z1 + 0.7 && position.y < 68;
    }

    private static void shelter(MinecraftServer server, Run current, Citizen citizen) {
        citizen.safe = true;
        current.safe.add(citizen.name);
        EntityCarryService.releaseEntity(citizen.body);
        citizen.body.discard();
        ServerLevel city = server.getLevel(TimeIntroService.DIMENSION);
        if (city != null) city.playSound(null, DOOR, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.2F);
        broadcast(server, current, citizen.name + " is through Bennet's door. (" + current.safe.size() + "/" + CITIZENS + " safe)", ChatFormatting.GREEN);
    }

    private static void tickGray(MinecraftServer server, Run current) {
        SylarEntity gray = current.gray;
        if (gray == null || !gray.isAlive() || current.grayWatched) return;
        if (current.grayTurned) {
            if (current.ticks >= current.goneAt) {
                gray.discard();
                current.gray = null;
            } else {
                gray.getNavigation().moveTo(gray.getX(), 64, Math.min(CITY_MAX, gray.getZ() + 12), 0.8);
            }
            return;
        }
        if (current.ticks < current.staggeredUntil) return;
        if (gray.position().distanceToSqr(SPRAGUE) <= GRAY_SEES * GRAY_SEES) {
            current.grayWatched = true;
            gray.getNavigation().stop();
            gray.getLookControl().setLookAt(SPRAGUE.x, SPRAGUE.y + 1.6, SPRAGUE.z);
            broadcast(server, current, "Gray reaches the edge of the light and stands very still, the way a watchmaker watches a movement. He is learning how it works.", ChatFormatting.DARK_PURPLE);
            return;
        }
        // An unhurried walk, about 0.8 blocks a second: a little over two minutes to the light if nobody stops him.
        if (gray.getZ() < current.lastGrayZ - 1) { current.lastGrayZ = gray.getZ(); current.stuckSince = current.ticks; }
        else if (current.ticks - current.stuckSince > 100) {
            // Pathing snagged on something; he steps past it rather than standing still forever.
            gray.teleportTo(6.5, 64, gray.getZ() - 3);
            current.lastGrayZ = gray.getZ();
            current.stuckSince = current.ticks;
        }
        gray.getNavigation().moveTo(6.5, 64, Math.max(gray.getZ() - 16, SPRAGUE.z + GRAY_SEES - 4), 0.55);
    }

    /** A runner lands a blow on Gray. The past won't let him be hurt here, but he can be turned back. */
    static void turnGray(ServerPlayer runner) {
        Run current = run;
        if (current == null || current.gray == null || current.grayTurned || current.grayWatched
                || current.ticks >= LIGHT_TICK || current.ticks - current.lastTurn < TURN_COOLDOWN) return;
        SylarEntity gray = current.gray;
        current.lastTurn = current.ticks;
        current.turns++;
        current.staggeredUntil = current.ticks + STAGGER;
        current.stuckSince = current.ticks + STAGGER;
        gray.getNavigation().stop();
        Vec3 away = gray.position().subtract(runner.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
        gray.setDeltaMovement(away.scale(0.9).add(0, 0.35, 0));
        gray.hurtMarked = true;
        gray.level().playSound(null, gray.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.8F);
        MinecraftServer server = runner.getServer();
        if (current.turns >= GRAY_TURNS) {
            current.grayTurned = true;
            current.goneAt = current.ticks + 120;
            broadcast(server, current, "Gray looks past you at the light for a long moment. Then he turns and walks away from it. He'll never know how it works.", ChatFormatting.DARK_PURPLE);
        } else {
            broadcast(server, current, current.turns == 1
                    ? "Gray: Don't. I only want to see him. The man in the light. How does something like that work?"
                    : "Gray: You're not from here, are you? Everyone else is running away from him. You're running at me.",
                    ChatFormatting.DARK_PURPLE);
        }
    }

    private static void glow(MinecraftServer server, ServerLevel city, Run current) {
        AbilityEffects.send(city, Kind.NUCLEAR_CHARGE, SPRAGUE, SPRAGUE.add(0, 2, 0), 1.5 + 2.5 * current.ticks / (double) LIGHT_TICK);
        for (ServerPlayer player : runners(server, current)) {
            if (player.position().distanceToSqr(SPRAGUE) > 10 * 10) continue;
            player.hurt(city.damageSources().hotFloor(), 2);
            if (current.burned.add(player.getUUID()))
                tell(player, "The heat drives you back. Nobody could reach him in time. Nobody ever could.", ChatFormatting.RED);
        }
    }

    private static void helper(MinecraftServer server, Run current) {
        Villager caller = current.maraNext ? current.mara : current.ivo;
        current.maraNext = !current.maraNext;
        if (caller == null) return;
        Vec3 door = Vec3.atBottomCenterOf(DOOR);
        current.citizens.stream()
                .filter(citizen -> !citizen.safe && !citizen.following && !citizen.sent && citizen.body.isAlive()
                        && citizen.body.position().distanceToSqr(door) <= HELPER_REACH * HELPER_REACH)
                .min(Comparator.comparingDouble(citizen -> citizen.body.position().distanceToSqr(door)))
                .ifPresent(citizen -> {
                    citizen.sent = true;
                    citizen.body.setNoAi(false);
                    broadcast(server, current, first(caller.getName().getString()) + " shouts to " + first(citizen.name)
                            + " from the door, and " + (citizen.child ? "the kid runs" : "they run") + " for it.", ChatFormatting.AQUA);
                });
    }

    private static void clockBar(MinecraftServer server, Run current) {
        String gray = current.grayWatched ? "Gray: watching the light" : current.grayTurned ? "Gray: turned back"
                : current.ticks < current.staggeredUntil ? "Gray: staggered" : "Gray: walking toward the light";
        Component bar = Component.literal(clock(current.ticks)).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("   " + current.safe.size() + "/" + CITIZENS + " safe").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("   " + gray).withStyle(current.grayTurned ? ChatFormatting.GRAY : ChatFormatting.DARK_PURPLE));
        forEachRunner(server, current, player -> player.displayClientMessage(bar, true));
    }

    /** The clock on the avenue: 6:10:00 PM at the start, 6:14:00 PM when the light falls. */
    public static String clock(int ticks) {
        int seconds = 10 * 60 + Math.clamp(ticks, 0, LIGHT_TICK) / 20;
        return String.format("6:%02d:%02d PM", seconds / 60, seconds % 60);
    }

    private static void light(MinecraftServer server, Run current) {
        int left = CITIZENS - current.safe.size();
        forEachRunner(server, current, player -> {
            PacketDistributor.sendToPlayer(player, new TimeIntroPayload(TimeIntroPayload.AVENUE_LIGHT));
            ScreenTintPayload.send(player, 0xB8FFF1D5, 22, ScreenTintPayload.Style.FLASH);
        });
        if (left > 0) broadcast(server, current, "6:14. Bennet closes the door. " + left + (left == 1 ? " person is" : " people are") + " still on the street.", ChatFormatting.RED);
        else broadcast(server, current, "6:14. Bennet closes the door on an empty street.", ChatFormatting.GOLD);
    }

    private static void finish(MinecraftServer server, ServerLevel city, boolean completed) {
        Run current = run;
        run = null;
        for (UUID id : List.copyOf(current.runners)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) home(server, current, player, completed);
        }
        for (Citizen citizen : current.citizens) EntityCarryService.releaseEntity(citizen.body);
        List<Entity> scene = new ArrayList<>();
        for (Entity entity : city.getAllEntities()) if (isScene(entity)) scene.add(entity);
        scene.forEach(Entity::discard);
        restoreShelter(city);
        TimeIntroService.forceCity(city, false);
    }

    /** One runner goes home by their ticket, with whatever the evening wrote for them. */
    private static void home(MinecraftServer server, Run current, ServerPlayer player, boolean completed) {
        List<String> saved = List.copyOf(current.safe);
        // Gray is kept away if he never got close enough to see, however that was done.
        boolean grayKept = !current.grayWatched;
        AvenueData data = AvenueData.get(server);
        FacilityData.ReturnPoint point = data.tickets.remove(player.getUUID());
        data.setDirty();
        EntityCarryService.release(player);
        if (TimeIntroService.isCity(player.level())) FacilityService.returnTo(player, point);
        if (!completed) {
            PacketDistributor.sendToPlayer(player, new TimeIntroPayload(TimeIntroPayload.CANCEL));
            return;
        }
        PacketDistributor.sendToPlayer(player, new TimeIntroPayload(saved.size() == CITIZENS ? TimeIntroPayload.AVENUE_ALL
                : saved.isEmpty() ? TimeIntroPayload.AVENUE_NONE : TimeIntroPayload.AVENUE_SOME));
        player.playNotifySound(ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 0.9F, 0.85F);
        ScreenTintPayload.send(player, 0x908AB6FF, 25, ScreenTintPayload.Style.FLASH);
        AbilityEffects.at(player, Kind.TEMPORAL_BURST, 4);
        reward(player, saved, grayKept);
    }

    /**
     * What one runner brings back. Records are written only when the evening went better than any
     * before it, so a replay can add names but never takes any away.
     */
    static void reward(ServerPlayer player, List<String> saved, boolean grayKept) {
        MinecraftServer server = player.getServer();
        AvenueData.Best before = AvenueData.get(server).record(player.getUUID(), saved.size(), grayKept);
        int count = saved.size();
        if (count > 0) {
            tell(player, "6:15 PM. " + count + " of " + CITIZENS + " people on the avenue lived to see it.", ChatFormatting.GOLD);
            if (count > before.saved() || !PastRecords.has(player, PastRecords.DOOR)) {
                tell(player, "There's a book in your hands that wasn't there a moment ago.", ChatFormatting.LIGHT_PURPLE);
                PastRecords.give(player, PastRecords.door(saved));
            }
            if (!PastRecords.has(player, PastRecords.WHY_YOU)) PastRecords.give(player, PastRecords.whyYou());
            award(player, "story/six_fifteen");
            if (count == CITIZENS) award(player, "story/everyone_home");
            if (before.saved() == 0) // The first evening anyone lived through opens the third night of the lockdown.
                player.sendSystemMessage(Component.literal("A new omen has opened in your Journal (")
                        .append(StoryLead.journalKey())
                        .append("): Night 3, the third night of the lockdown, when Gray reached Claire Bennet in Pod 14.")
                        .withStyle(ChatFormatting.AQUA));
        } else {
            tell(player, "6:15 PM. Nobody on the avenue lived to see it. The avenue is still there: you can walk back to 6:10.", ChatFormatting.GRAY);
        }
        if (grayKept) {
            tell(player, "Gabriel Gray never saw the light. He never learned how Sprague's heat works.", ChatFormatting.DARK_PURPLE);
            if (!PastRecords.has(player, PastRecords.ADDENDUM)) PastRecords.give(player, PastRecords.addendum());
            award(player, "story/he_never_saw_it");
            SylarStoryData sylar = SylarStoryData.get(server);
            if (sylar.forget(ModAbilities.RADIATION_ID))
                tell(player, "In the present, the watchmaker reaches for Sprague's heat and finds nothing he understands. He never did.", ChatFormatting.DARK_PURPLE);
        } else if (!before.grayKept()) {
            tell(player, "Gray saw the light up close, and somewhere in him a watchmaker understood it. You could walk back and stop him.", ChatFormatting.DARK_PURPLE);
        }
    }

    private static void award(ServerPlayer player, String path) {
        var advancement = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path));
        if (advancement != null) player.getAdvancements().award(advancement, "granted");
    }

    // ------------------------------------------------------------------
    // People
    // ------------------------------------------------------------------

    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event) {
        Run current = run;
        if (current == null || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !isRunner(player) || !isScene(event.getTarget()) || player.isShiftKeyDown()) return; // Sneak + use carries.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Entity target = event.getTarget();
        if (current.ticks >= LIGHT_TICK) return;
        for (Citizen citizen : current.citizens) {
            if (citizen.body != target) continue;
            if (!citizen.following) {
                citizen.following = true;
                citizen.body.setNoAi(false);
                tell(player, first(citizen.name) + ": " + (citizen.child ? "Don't let go of my hand."
                        : FOLLOW_LINES[Math.floorMod(citizen.name.hashCode(), FOLLOW_LINES.length)]), ChatFormatting.WHITE);
            }
            return;
        }
        if (target == current.bennet) tell(player, "Noah Bennet: I close this door at 6:14, not a second later. It's " + clock(current.ticks) + ". Bring them.", ChatFormatting.WHITE);
        else if (target == current.mara) tell(player, "Mara: You got us out of the annex. Now we're getting them out of this. I'll shout to anyone close enough to hear.", ChatFormatting.WHITE);
        else if (target == current.ivo) tell(player, "Ivo: That glow is the heat we were holding. Nobody can cool him now. Just get people inside.", ChatFormatting.WHITE);
        else if (target == current.gray) tell(player, "Gray doesn't look at you. He's looking at the light.", ChatFormatting.DARK_PURPLE);
        else if (target == current.sprague) tell(player, "Sprague: Get away from me. Please. I can't hold it.", ChatFormatting.GOLD);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) {
        Run current = run;
        if (current == null || !isScene(event.getEntity())) return;
        event.setCanceled(true); // Nobody in the past can be hurt by someone from the present.
        if (event.getEntity() == current.gray && event.getSource().getEntity() instanceof ServerPlayer player && isRunner(player))
            turnGray(player);
    }

    // ------------------------------------------------------------------
    // Bennet's door
    // ------------------------------------------------------------------

    private static void buildShelter(ServerLevel city) {
        for (int x = SHELTER_X0; x <= SHELTER_X1; x++) for (int z = SHELTER_Z0; z <= SHELTER_Z1; z++) for (int y = 63; y <= 69; y++) {
            boolean edge = x == SHELTER_X0 || x == SHELTER_X1 || z == SHELTER_Z0 || z == SHELTER_Z1;
            boolean doorway = z == SHELTER_Z0 && x >= 2 && x <= 5 && y >= 64 && y <= 66;
            Block block;
            if (y == 63) block = (x + z) % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE;
            else if (y == 68) block = x >= 3 && x <= 4 && z >= 91 && z <= 93 ? Blocks.SEA_LANTERN : Blocks.WHITE_CONCRETE;
            else if (y == 69) block = edge ? Blocks.SMOOTH_STONE_SLAB : Blocks.AIR;
            else if (edge && !doorway) block = y == 64 ? Blocks.GRAY_CONCRETE : y == 66 && z != SHELTER_Z0 && (x + z) % 3 == 0
                    ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.WHITE_CONCRETE;
            else block = Blocks.AIR;
            city.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        sign(city, new BlockPos(3, 67, SHELTER_Z0 - 1), Direction.NORTH, "PRIMATECH", "PAPER CO.");
        sign(city, new BlockPos(4, 67, SHELTER_Z0 - 1), Direction.NORTH, "STAFF ONLY", "SUBLEVEL 4");
        sign(city, new BlockPos(4, 66, SHELTER_Z1 - 1), Direction.NORTH, "STAIRS DOWN", "KEEP MOVING");
    }

    private static void sign(ServerLevel city, BlockPos pos, Direction facing, String first, String second) {
        BlockState state = Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
        city.setBlock(pos, state, Block.UPDATE_CLIENTS);
        if (city.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.setText(new SignText().setColor(DyeColor.WHITE).setHasGlowingText(true)
                    .setMessage(1, Component.literal(first)).setMessage(2, Component.literal(second)), true);
            sign.setChanged();
        }
    }

    private static void restoreShelter(ServerLevel city) {
        for (int x = SHELTER_X0 - 1; x <= SHELTER_X1 + 1; x++)
            for (int z = SHELTER_Z0 - 2; z <= SHELTER_Z1 + 1; z++) FractureCity.buildColumn(city, x, z);
    }

    // ------------------------------------------------------------------
    // The journal
    // ------------------------------------------------------------------

    public static String journal(ServerPlayer player) {
        if (!unlocked(player)) return "Locked in the past. Hiro's key is in the Hourglass Annex: bring Mara and Ivo out alive.";
        if (isRunner(player)) return "Now: " + clock(run.ticks) + ", " + run.safe.size() + "/" + CITIZENS
                + " through Bennet's door at the south end of the avenue. Leave the past returns you early.";
        AvenueData.Best best = AvenueData.get(player.getServer()).best(player.getUUID());
        if (best.runs() == 0) return "Odessa, 6:10 PM. Noah Bennet holds the Company's door at the south end of the avenue until 6:14. "
                + "Bring him everyone you can, and keep the man in the dark coat from the light. Follow omen to go.";
        return "Your best evening: " + best.saved() + "/" + CITIZENS + " reached Bennet's door. "
                + (best.grayKept() ? "Gray never saw the light. " : "Gray saw the light. ")
                + "Follow omen to walk it again; Shift-click it to choose another omen.";
    }

    // ------------------------------------------------------------------
    // Helpers, and hooks for the game tests
    // ------------------------------------------------------------------

    private static List<ServerPlayer> runners(MinecraftServer server, Run current) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : current.runners) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) players.add(player);
        }
        return players;
    }

    private static void forEachRunner(MinecraftServer server, Run current, Consumer<ServerPlayer> action) {
        runners(server, current).forEach(action);
    }

    private static void broadcast(MinecraftServer server, Run current, String message, ChatFormatting colour) {
        forEachRunner(server, current, player -> tell(player, message, colour));
    }

    private static String first(String name) {
        int comma = name.indexOf(',');
        return comma < 0 ? name : name.substring(0, comma);
    }

    private static void tell(ServerPlayer player, String message) { player.sendSystemMessage(Component.literal(message)); }
    private static void tell(ServerPlayer player, String message, ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(message).withStyle(colour));
    }

    static List<Villager> citizens() { return run == null ? List.of() : run.citizens.stream().filter(c -> !c.safe).map(c -> c.body).toList(); }
    static int safeCount() { return run == null ? 0 : run.safe.size(); }
    static SylarEntity gray() { return run == null ? null : run.gray; }
    static boolean grayTurned() { return run != null && run.grayTurned; }
    static boolean grayWatched() { return run != null && run.grayWatched; }
    static int ticks() { return run == null ? -1 : run.ticks; }
    /** Moves the evening's clock, as if that much time had passed. */
    static void setTicks(int ticks) { if (run != null) run.ticks = ticks; }
}
