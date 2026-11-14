package com.jayptucker.heroesevolved.story.night;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.company.CompanyAgentEntity;
import com.jayptucker.heroesevolved.company.CompanyAgentEntity.Role;
import com.jayptucker.heroesevolved.company.SuppressorField;
import com.jayptucker.heroesevolved.company.Tranquilizer;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.expansion.FacilityData;
import com.jayptucker.heroesevolved.expansion.FacilityService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.story.AvenueData;
import com.jayptucker.heroesevolved.story.PastRecords;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarPowers;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryData;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Night Three: Sublevel 4 on the third night of the lockdown, 2:57 AM. Gabriel Gray unmakes the bars
 * of his cage and walks to Pod 14, where Claire Bennet is sleeping, to take what makes her heal. Noah
 * Bennet comes down the shaft at 3:00. Keep Gray from her until then.
 *
 * <p>The Company's guards are at their posts, and they don't know you: they dart any stranger they
 * see, and three darts send you back to the present. They dart Gray too, once he's out, but his
 * telekinesis turns darts aside unless something has him busy: a blow that staggers him, the pod he is
 * opening, or the Suppressor's quiet. Four darts that land put him down. By Pod 14 stands the
 * Suppressor, the man in the gray suit, and near him no power answers, Gray's included. Gray won't walk
 * into that quiet; he tries to put the man down from outside it.</p>
 *
 * <p>As on the avenue, nobody from the present can kill anyone here: guards go down out cold, Gray only
 * staggers, and lethal harm throws a runner home. One night runs at a time; a run lives in memory, and
 * only return tickets and outcomes are saved.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class NightThreeService {
    public static final String TAG = "heroesevolved_night_three";
    /** 2:57:10 AM: the bars come apart. */
    public static final int ESCAPE_TICK = 10 * 20;
    /** 3:00 AM: Bennet comes down the shaft. */
    public static final int END_TICK = 3 * 60 * 20;
    public static final int RETURN_DELAY = 5 * 20;
    /** How long Gray needs at Pod 14, uninterrupted. */
    public static final int CHANNEL_TICKS = 15 * 20;
    /** Darts that put Gray down. */
    public static final int SEDATION = 4;
    public static final int MAX_RUNNERS = 8;
    static final int STAGGER = 30, STAGGER_IMMUNITY = 60, CHANNEL_SETBACK = 80;
    static final int TK_WINDUP = 16, TK_COOLDOWN = 50, TK_RECOVER = 20;
    static final double TK_RANGE = 14, TK_PLAYER_RANGE = 8;
    static final float TK_AGENT_DAMAGE = 9, TK_PLAYER_DAMAGE = 4;

    /** The generator hall, where runners arrive: lit, humming, and nobody on watch. */
    public static final Vec3 ARRIVAL = spot(9.5, 1, 4.5);
    /** Inside Gray's cage. */
    public static final Vec3 CAGE = spot(-4.5, 1, 14.5);
    /** On the floor in front of Pod 14. */
    public static final Vec3 POD_FRONT = spot(4.5, 1, 32.5);
    /** Gray's way from his cage to Pod 14: out of the cell, down the corridor, along the east pods. */
    static final List<Vec3> ROUTE = List.of(spot(-3.5, 1, 14.5), spot(-1.5, 1, 14.5), spot(0.5, 1, 16.5), spot(0.5, 1, 19.5),
            spot(4.5, 1, 21.5), spot(4.5, 1, 30.5), POD_FRONT);

    private record Post(Role role, Vec3 at, float yaw, String name) {}

    /** Where the night watch stands: records, the security checkpoint, the corridor, the stasis hall, and the quiet by Pod 14. */
    private static final List<Post> POSTS = List.of(
            new Post(Role.GUARD, spot(1.5, 1, 9.5), 180, "Company guard"),
            new Post(Role.GUARD, spot(-7.5, 1, 4.5), 90, "Company guard"),
            new Post(Role.GUARD, spot(-11.5, 1, 6.5), -90, "Company guard"),
            new Post(Role.GUARD, spot(0.5, 1, 16.5), 0, "Company guard"),
            new Post(Role.GUARD, spot(-3.5, 1, 22.5), 0, "Company guard"),
            new Post(Role.GUARD, spot(2.5, 1, 34.5), 180, "Company guard"),
            new Post(Role.SUPPRESSOR, spot(2.5, 1, 31.5), 180, "The man in the gray suit"));

    public enum Outcome { NIGHT, KEPT, TAKEN }

    static final class Run {
        final Set<UUID> runners = new LinkedHashSet<>();
        final Map<UUID, String> knockedOut = new HashMap<>();
        final List<CompanyAgentEntity> agents = new ArrayList<>();
        CompanyAgentEntity suppressor;
        SylarEntity gray;
        LivingEntity castTarget;
        int ticks, waypoint, staggeredUntil, castTicks, nextCast, channel, stuckSince, outcomeAt, darts, staggers;
        int lastStagger = -STAGGER_IMMUNITY, lastReach = -400;
        double bestDistance = Double.MAX_VALUE;
        boolean loose, holding, channelling, toldDeflect;
        Outcome outcome = Outcome.NIGHT;
    }

    private static Run run;

    static {
        Tranquilizer.onGoingUnder(NightThreeService::wentUnder);
        Tranquilizer.onDeflect(NightThreeService::deflects);
    }

    private NightThreeService() {}

    private static Vec3 spot(double x, double y, double z) {
        BlockPos origin = NightThreeBunker.ORIGIN;
        return new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    }

    // ------------------------------------------------------------------
    // Who is where
    // ------------------------------------------------------------------

    public static boolean running() { return run != null; }
    public static boolean inNight(Level level) { return level.dimension().equals(NightThreeBunker.DIMENSION); }
    public static boolean inNight(Entity entity) { return inNight(entity.level()); }
    public static boolean isRunner(ServerPlayer player) {
        return run != null && run.runners.contains(player.getUUID()) && inNight(player);
    }
    public static boolean isScene(Entity entity) { return entity.getTags().contains(TAG); }
    /** The third night opens to anyone who has lived to see 6:15 on the avenue. */
    public static boolean unlocked(ServerPlayer player) {
        return AvenueData.get(player.getServer()).best(player.getUUID()).saved() > 0;
    }

    // ------------------------------------------------------------------
    // Going and coming back
    // ------------------------------------------------------------------

    public static boolean enter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel night = server.getLevel(NightThreeBunker.DIMENSION);
        if (night == null || !PowerRules.canAct(player) || player.isPassenger() || player.isSpectator()
                || inNight(player) || TimeIntroService.isCity(player.level()) || FacilityService.atSite(player)) return false;
        if (!unlocked(player)) {
            tell(player, "That night isn't open to you yet. Live to see 6:15 on the avenue first.");
            return false;
        }
        if (run != null && run.outcome != Outcome.NIGHT) {
            tell(player, "This night is already over. Wait a moment for it to end, then walk back in.");
            return false;
        }
        if (run != null && run.runners.size() >= MAX_RUNNERS) {
            tell(player, "Eight people are already in the bunker that night. Wait for it to end.");
            return false;
        }
        NightThreeData data = NightThreeData.get(server);
        data.tickets.put(player.getUUID(), new FacilityData.ReturnPoint(player.level().dimension().location(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        data.setDirty(); // Saved before crossing dimensions.
        if (run == null) start(server, night);
        EntityCarryService.release(player);
        player.closeContainer();
        player.stopRiding();
        Vec3 at = slot(run.runners.size());
        player.teleportTo(night, at.x, at.y, at.z, 135, 0); // Facing the door to the records room.
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        run.runners.add(player.getUUID());
        player.playNotifySound(ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 0.9F, 0.8F);
        ScreenTintPayload.send(player, 0x88203048, 24, ScreenTintPayload.Style.FLASH);
        for (Component line : briefing(run.ticks)) player.sendSystemMessage(line);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.jayptucker.heroesevolved.network.TimeIntroPayload(
                com.jayptucker.heroesevolved.network.TimeIntroPayload.NIGHT_THREE));
        return true;
    }

    /**
     * What a runner is told on arrival: the goal, how to stop Gray, how the guards help and hurt,
     * and the quiet by the pod. One line each, so a player reading it mid-fight can find what they need.
     */
    static List<Component> briefing(int ticks) {
        return List.of(
                Component.literal("SUBLEVEL 4, THE THIRD NIGHT OF THE LOCKDOWN, " + clock(ticks) + ". Tonight Gabriel Gray gets out of his "
                        + "cage and walks to Pod 14, where Claire Bennet sleeps. Keep him from her until 3:00.").withStyle(ChatFormatting.GOLD),
                Component.literal("You can't hurt him, but hit him and he staggers: it stops him, breaks a throw he's winding up, and undoes "
                        + "some of his work on the pod."),
                Component.literal("While he's staggered or at the pod, the guards' darts get through to him. Four put him to sleep. Their darts "
                        + "hit you too: three and you're back in the present.").withStyle(ChatFormatting.AQUA),
                Component.literal("By Pod 14 stands a man in a gray suit. Near him no power works, yours or Gray's. Gray won't walk into that "
                        + "quiet; he'll try to knock the man out from outside it.").withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Back to the present, mid-run. Returns false if the player wasn't in the night. */
    public static boolean leave(ServerPlayer player) {
        NightThreeData data = NightThreeData.get(player.getServer());
        FacilityData.ReturnPoint point = data.tickets.remove(player.getUUID());
        boolean runner = run != null && run.runners.remove(player.getUUID());
        boolean here = inNight(player);
        if (point == null && !runner && !here) return false;
        data.setDirty();
        EntityCarryService.release(player);
        player.removeEffect(ModItems.TRANQUILIZED);
        if (here) FacilityService.returnTo(player, point);
        return true;
    }

    /** On login and respawn: nobody wakes up stranded on the third night. */
    public static void recover(ServerPlayer player) {
        if (isRunner(player)) return;
        if (inNight(player) || NightThreeData.get(player.getServer()).tickets.containsKey(player.getUUID())) leave(player);
    }

    public static void logout(ServerPlayer player) {
        if (run != null && run.runners.contains(player.getUUID()) || NightThreeData.get(player.getServer()).tickets.containsKey(player.getUUID()))
            leave(player);
    }

    public static void shutdown(MinecraftServer server) {
        ServerLevel night = server.getLevel(NightThreeBunker.DIMENSION);
        if (run != null && night != null) finish(server, night, false);
        run = null;
    }

    /** Lethal harm, or a third dart, throws a runner back to the present. */
    public static void knockOut(ServerPlayer player, String why) {
        if (run != null) run.knockedOut.put(player.getUUID(), why);
    }

    private static boolean wentUnder(ServerPlayer player) {
        if (!isRunner(player)) return false;
        knockOut(player, "The third dart. The floor tilts up to meet you, and when your eyes open you are home. The third night goes on without you.");
        return true;
    }

    // ------------------------------------------------------------------
    // The night
    // ------------------------------------------------------------------

    private static void start(MinecraftServer server, ServerLevel night) {
        Run next = new Run();
        run = next;
        forceChunks(night, true);
        // Nobody from a night is saved on purpose, but a crash can leave some behind; clear them first.
        List<Entity> stale = new ArrayList<>();
        for (Entity entity : night.getAllEntities()) if (isScene(entity)) stale.add(entity);
        stale.forEach(Entity::discard);
        NightThreeBunker.build(server, night);
        for (Post post : POSTS) {
            CompanyAgentEntity agent = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), night, post.role(), post.at(), post.yaw(), true);
            if (agent == null) continue;
            agent.addTag(TAG);
            agent.setCustomName(Component.literal(post.name()));
            if (!night.addFreshEntity(agent)) continue;
            next.agents.add(agent);
            if (post.role() == Role.SUPPRESSOR) next.suppressor = agent;
        }
        SylarEntity gray = ModEntities.SYLAR.get().create(night);
        if (gray != null) {
            gray.moveTo(CAGE.x, CAGE.y, CAGE.z, -90, 0);
            gray.setCustomName(Component.literal("Gabriel Gray"));
            gray.setCustomNameVisible(true);
            gray.addTag(TAG);
            if (night.addFreshEntity(gray)) next.gray = gray;
        }
    }

    private static void forceChunks(ServerLevel night, boolean force) {
        BoundingBox box = NightThreeBunker.bounds();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) night.setChunkForced(cx, cz, force);
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        Run current = run;
        if (current == null) return;
        ServerLevel night = server.getLevel(NightThreeBunker.DIMENSION);
        if (night == null) { run = null; return; }
        NightThreeData data = NightThreeData.get(server);
        current.runners.removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && inNight(player)) return false;
            if (player != null && data.tickets.remove(id) != null) data.setDirty(); // Left by some other door.
            return true;
        });
        for (Map.Entry<UUID, String> out : List.copyOf(current.knockedOut.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(out.getKey());
            if (player == null) continue;
            leave(player);
            tell(player, out.getValue(), ChatFormatting.RED);
        }
        current.knockedOut.clear();
        if (current.runners.isEmpty()) { finish(server, night, false); return; }

        current.ticks++;
        forEachRunner(server, current, NightThreeService::keepInBunker);
        if (current.outcome == Outcome.NIGHT) {
            if (current.ticks == ESCAPE_TICK - 60) forEachRunner(server, current,
                    player -> player.playNotifySound(ModSounds.SYLAR_WATCH.get(), SoundSource.AMBIENT, 0.6F, 1));
            if (current.ticks == ESCAPE_TICK) escape(server, night, current);
            if (current.loose) tickGray(server, night, current);
            if (current.ticks % 20 == 0) bar(server, current);
            if (current.ticks == END_TICK - 60 * 20) broadcast(server, current, "2:59 AM. One minute. Somewhere above you, a hatch wheel starts to turn.", ChatFormatting.GOLD);
            if (current.ticks == END_TICK - 15 * 20) broadcast(server, current, "Fifteen seconds. Boots on the ladder.", ChatFormatting.GOLD);
            if (current.ticks >= END_TICK && current.outcome == Outcome.NIGHT)
                resolve(server, night, current, Outcome.KEPT, "3:00 AM. The hatch at the top of the shaft bangs open and Noah Bennet comes down the ladder "
                        + "with four men and a syringe. Gray goes back behind new bars. Pod 14 is still sealed.", ChatFormatting.GOLD);
        } else if (current.ticks >= current.outcomeAt + RETURN_DELAY) {
            finish(server, night, true);
        }
    }

    private static void keepInBunker(ServerPlayer player) {
        if (player.getY() > NightThreeBunker.ORIGIN.getY() - 12) return;
        // Through the floor and out of the rock: back where you came in.
        player.teleportTo(player.serverLevel(), ARRIVAL.x, ARRIVAL.y, ARRIVAL.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    private static void escape(MinecraftServer server, ServerLevel night, Run current) {
        current.loose = true;
        current.stuckSince = current.ticks;
        SylarEntity gray = current.gray;
        for (BlockPos pos : NightThreeBunker.cageBars()) {
            night.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            if (gray != null) AbilityEffects.send(night, Kind.TK_GRIP, gray.getEyePosition(), Vec3.atCenterOf(pos), 0.5);
        }
        BlockPos cage = NightThreeBunker.cageBars().getFirst();
        night.playSound(null, cage, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
        night.playSound(null, cage, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.HOSTILE, 1.0F, 0.5F);
        if (gray != null) gray.addTag(CompanyAgentEntity.LOOSE_TAG);
        broadcast(server, current, "The ticking stops. In the cell west of the corridor, the bars of Gray's cage come apart one by one, "
                + "the way a watchmaker takes down a movement. He steps out.", ChatFormatting.DARK_PURPLE);
    }

    // ------------------------------------------------------------------
    // Gray
    // ------------------------------------------------------------------

    private static boolean suppressorAwake(Run current) {
        CompanyAgentEntity suppressor = current.suppressor;
        return suppressor != null && suppressor.isAlive() && !suppressor.isRemoved() && !suppressor.outCold();
    }

    /**
     * Something has Gray's attention: darts reach him. A runner's blow, the pod under his hands, or the
     * quiet that takes his power. Throwing at the guards doesn't count; he can do that and turn darts aside
     * at once, so the guards alone never put him down.
     */
    static boolean busy(Run current) {
        SylarEntity gray = current.gray;
        return current.ticks < current.staggeredUntil || current.channelling
                || gray != null && SuppressorField.suppresses(gray);
    }

    /** Gray turns a dart aside with his telekinesis while nothing has him busy. */
    static boolean deflects(LivingEntity target) {
        Run current = run;
        if (current == null || target != current.gray || current.outcome != Outcome.NIGHT || busy(current)) return false;
        if (!current.toldDeflect) {
            current.toldDeflect = true;
            MinecraftServer server = target.getServer();
            if (server != null) broadcast(server, current, "The dart stops in the air a foot from Gray and drops. Nothing reaches him "
                    + "unless something keeps him busy: a blow that staggers him, the pod, or the quiet.", ChatFormatting.GRAY);
        }
        return true;
    }

    private static void tickGray(MinecraftServer server, ServerLevel night, Run current) {
        SylarEntity gray = current.gray;
        if (gray == null || !gray.isAlive()) return;
        current.channelling = false;
        int doses = Tranquilizer.doses(gray);
        if (doses > current.darts) broadcast(server, current, "A dart takes Gray in the shoulder. (" + Math.min(doses, SEDATION) + "/" + SEDATION + ")", ChatFormatting.AQUA);
        current.darts = doses;
        if (doses >= SEDATION) {
            resolve(server, night, current, Outcome.KEPT, "Gray sways, catches himself on a pod, and goes down on one knee. The guards drag him back "
                    + "toward his cell. Above you, Bennet's men are already on the ladder.", ChatFormatting.GOLD);
            return;
        }
        if (current.ticks < current.staggeredUntil) { gray.getNavigation().stop(); return; }
        if (current.castTarget != null) { tickCast(night, current, gray); return; }
        boolean quiet = SuppressorField.suppresses(gray);
        boolean awake = suppressorAwake(current);
        if (quiet && awake) { stepOutOfTheQuiet(current, gray); return; }
        if (gray.position().distanceToSqr(POD_FRONT) <= 1.3 * 1.3 && current.waypoint >= ROUTE.size() - 1) {
            if (!quiet) { openPod(server, night, current, gray); return; }
        }
        if (!quiet && current.ticks >= current.nextCast) {
            LivingEntity target = chooseTarget(server, current, gray);
            if (target != null) { beginCast(server, current, gray, target); return; }
        }
        walk(current, gray, awake);
    }

    /** Who Gray throws first: a guard aiming at him, then the man in the gray suit, then the stranger in his way. */
    private static LivingEntity chooseTarget(MinecraftServer server, Run current, SylarEntity gray) {
        Comparator<LivingEntity> nearest = Comparator.comparingDouble(gray::distanceToSqr);
        LivingEntity guard = current.agents.stream()
                .filter(agent -> agent.isAlive() && !agent.outCold() && agent.role() == Role.GUARD && agent.getTarget() == gray)
                .filter(agent -> gray.distanceToSqr(agent) <= TK_RANGE * TK_RANGE && gray.hasLineOfSight(agent))
                .min(nearest).orElse(null);
        if (guard != null) return guard;
        if (suppressorAwake(current) && gray.distanceToSqr(current.suppressor) <= TK_RANGE * TK_RANGE && gray.hasLineOfSight(current.suppressor))
            return current.suppressor;
        return runners(server, current).stream()
                .filter(player -> !player.isCreative() && !player.isSpectator())
                .filter(player -> gray.distanceToSqr(player) <= TK_PLAYER_RANGE * TK_PLAYER_RANGE && gray.hasLineOfSight(player))
                .min(nearest).orElse(null);
    }

    private static void beginCast(MinecraftServer server, Run current, SylarEntity gray, LivingEntity target) {
        current.castTarget = target;
        current.castTicks = 0;
        gray.getNavigation().stop();
        gray.scenePose(SylarPowers.Pose.ONE_HAND);
        gray.level().playSound(null, gray.getX(), gray.getY(), gray.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 0.8F, 0.6F);
        if (target == current.suppressor && current.ticks - current.lastReach > 200) {
            current.lastReach = current.ticks;
            broadcast(server, current, "Gray stops at the edge of the quiet and lifts one hand toward the man in the gray suit.", ChatFormatting.DARK_PURPLE);
        }
    }

    private static void cancelCast(Run current, SylarEntity gray) {
        current.castTarget = null;
        current.castTicks = 0;
        current.nextCast = Math.max(current.nextCast, current.ticks + TK_RECOVER);
        gray.scenePose(SylarPowers.Pose.NONE);
    }

    private static void tickCast(ServerLevel night, Run current, SylarEntity gray) {
        LivingEntity target = current.castTarget;
        boolean valid = target.isAlive() && !target.isRemoved() && target.level() == gray.level()
                && !(target instanceof CompanyAgentEntity agent && agent.outCold())
                && gray.distanceToSqr(target) <= (TK_RANGE + 2) * (TK_RANGE + 2) && gray.hasLineOfSight(target)
                && !SuppressorField.suppresses(gray);
        if (!valid) { cancelCast(current, gray); return; }
        gray.getNavigation().stop();
        gray.getLookControl().setLookAt(target, 30, 30);
        current.castTicks++;
        Vec3 hand = gray.getEyePosition().subtract(0, 0.4, 0);
        if (current.castTicks % 4 == 1) AbilityEffects.send(night, Kind.TK_GRIP, hand, target.getBoundingBox().getCenter(), 0.6);
        if (current.castTicks < TK_WINDUP) return;
        Vec3 away = target.position().subtract(gray.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
        target.invulnerableTime = 0;
        target.hurt(gray.damageSources().mobAttack(gray), target instanceof CompanyAgentEntity ? TK_AGENT_DAMAGE : TK_PLAYER_DAMAGE);
        target.setDeltaMovement(away.scale(1.1).add(0, 0.6, 0));
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player)
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        AbilityEffects.send(night, Kind.TK_LAUNCH, target.getBoundingBox().getCenter(), target.getBoundingBox().getCenter().add(away.scale(4)), 1.0);
        night.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 0.8F, 0.7F);
        current.castTarget = null;
        current.castTicks = 0;
        current.nextCast = current.ticks + TK_COOLDOWN;
        gray.scenePose(SylarPowers.Pose.NONE);
    }

    /** He won't stay in the quiet: back out the way he came, away from the man who makes it. */
    private static void stepOutOfTheQuiet(Run current, SylarEntity gray) {
        current.holding = true;
        Vec3 away = gray.position().subtract(current.suppressor.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, -1) : away.normalize();
        if (current.ticks % 10 == 0) gray.getNavigation().moveTo(gray.getX() + away.x * 3, gray.getY(), gray.getZ() + away.z * 3, 0.7);
        current.stuckSince = current.ticks;
    }

    private static void walk(Run current, SylarEntity gray, boolean suppressorAwake) {
        Vec3 next = ROUTE.get(Math.min(current.waypoint, ROUTE.size() - 1));
        if (gray.position().distanceToSqr(next) < 1.2 * 1.2 && current.waypoint < ROUTE.size() - 1) {
            current.waypoint++;
            current.bestDistance = Double.MAX_VALUE;
            current.stuckSince = current.ticks;
            next = ROUTE.get(current.waypoint);
        }
        // While the man in the gray suit is awake, Gray waits at the edge of his quiet, where he can reach him.
        if (suppressorAwake && next.distanceToSqr(current.suppressor.position()) <= (SuppressorField.RADIUS + 0.5) * (SuppressorField.RADIUS + 0.5)) {
            current.holding = true;
            CompanyAgentEntity suppressor = current.suppressor;
            gray.getLookControl().setLookAt(suppressor, 30, 30);
            if (gray.distanceToSqr(suppressor) > (TK_RANGE - 1) * (TK_RANGE - 1) || !gray.hasLineOfSight(suppressor)) {
                Vec3 out = gray.position().subtract(suppressor.position()).multiply(1, 0, 1);
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, -1) : out.normalize();
                Vec3 edge = suppressor.position().add(out.scale(SuppressorField.RADIUS + 1.5));
                if (current.ticks % 10 == 0) gray.getNavigation().moveTo(edge.x, gray.getY(), edge.z, 0.55);
            } else {
                gray.getNavigation().stop();
            }
            current.stuckSince = current.ticks;
            return;
        }
        current.holding = false;
        double distance = gray.position().distanceTo(next);
        if (distance < current.bestDistance - 0.5) { current.bestDistance = distance; current.stuckSince = current.ticks; }
        else if (current.ticks - current.stuckSince > 100) {
            // Pathing snagged on something; he steps past it rather than standing still all night.
            gray.teleportTo(next.x, next.y, next.z);
            current.bestDistance = 0;
            current.stuckSince = current.ticks;
        }
        if (current.ticks % 10 == 0 || gray.getNavigation().isDone()) gray.getNavigation().moveTo(next.x, next.y, next.z, 0.55);
    }

    private static void openPod(MinecraftServer server, ServerLevel night, Run current, SylarEntity gray) {
        current.channelling = true;
        gray.getNavigation().stop();
        Vec3 pod = Vec3.atCenterOf(NightThreeBunker.pod().above());
        gray.getLookControl().setLookAt(pod.x, pod.y, pod.z);
        gray.scenePose(SylarPowers.Pose.TWO_HANDS);
        if (current.channel == 0) broadcast(server, current, "Gray reaches Pod 14 and lays both hands on the glass, the way he'd open the back of a watch.", ChatFormatting.DARK_PURPLE);
        current.channel++;
        if (current.channel % 20 == 1) {
            night.playSound(null, pod.x, pod.y, pod.z, ModSounds.SYLAR_WATCH.get(), SoundSource.HOSTILE, 0.8F, 1);
            AbilityEffects.send(night, Kind.TK_GRIP, gray.getEyePosition().subtract(0, 0.4, 0), pod, 0.8);
        }
        if (current.channel == CHANNEL_TICKS / 2) broadcast(server, current, "The seals on Pod 14 hiss. Frost runs off the glass.", ChatFormatting.RED);
        if (current.channel >= CHANNEL_TICKS) {
            NightThreeBunker.setPodOpen(night, true);
            night.playSound(null, pod.x, pod.y, pod.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 1.0F, 0.6F);
            resolve(server, night, current, Outcome.TAKEN, "Pod 14 opens with a long hiss. Gray leans in over Claire Bennet, and the ticking stops. "
                    + "When he straightens up, the cut on his hand is already closing.", ChatFormatting.RED);
        }
    }

    /** A runner lands a blow on Gray. The past won't let him be hurt, but it staggers him and breaks whatever he was doing. */
    static void stagger(ServerPlayer runner) {
        Run current = run;
        if (current == null || current.gray == null || !current.loose || current.outcome != Outcome.NIGHT
                || current.ticks - current.lastStagger < STAGGER_IMMUNITY) return;
        SylarEntity gray = current.gray;
        current.lastStagger = current.ticks;
        current.staggeredUntil = current.ticks + STAGGER;
        current.stuckSince = current.ticks + STAGGER;
        current.staggers++;
        if (current.castTarget != null) cancelCast(current, gray);
        if (current.channel > 0) current.channel = Math.max(0, current.channel - CHANNEL_SETBACK);
        gray.getNavigation().stop();
        gray.scenePose(SylarPowers.Pose.NONE);
        Vec3 away = gray.position().subtract(runner.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, -1) : away.normalize();
        gray.setDeltaMovement(away.scale(0.8).add(0, 0.3, 0));
        gray.hurtMarked = true;
        gray.level().playSound(null, gray.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.8F);
        MinecraftServer server = runner.getServer();
        if (current.staggers == 1) broadcast(server, current, AvenueData.get(server).grayNeverSaw()
                ? "Gray: You. You were on the avenue. You kept getting in my way."
                : "Gray: Who let you in here? Nobody comes down here at night.", ChatFormatting.DARK_PURPLE);
        else if (current.staggers == 3) broadcast(server, current,
                "Gray: She's asleep. She won't feel a thing. I only want to understand how she works.", ChatFormatting.DARK_PURPLE);
        else if (current.staggers == 6) broadcast(server, current,
                "Gray: Every clock runs down. I can wait longer than you can.", ChatFormatting.DARK_PURPLE);
    }

    private static void resolve(MinecraftServer server, ServerLevel night, Run current, Outcome outcome, String message, ChatFormatting colour) {
        current.outcome = outcome;
        current.outcomeAt = current.ticks;
        current.castTarget = null;
        current.channelling = false;
        SylarEntity gray = current.gray;
        if (gray != null) {
            gray.removeTag(CompanyAgentEntity.LOOSE_TAG);
            gray.getNavigation().stop();
            gray.scenePose(outcome == Outcome.KEPT ? SylarPowers.Pose.CROUCH : SylarPowers.Pose.NONE);
            if (outcome == Outcome.KEPT) gray.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RETURN_DELAY + 20, 6, false, false));
        }
        // The night is over: the watch stands down, and nobody else is darted before the pull home.
        for (CompanyAgentEntity agent : current.agents) if (agent.isAlive()) { agent.setTarget(null); agent.setNoAi(true); }
        broadcast(server, current, message, colour);
        forEachRunner(server, current, player -> {
            player.removeEffect(ModItems.TRANQUILIZED);
            if (outcome == Outcome.TAKEN) PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.2F, 30));
        });
    }

    private static void finish(MinecraftServer server, ServerLevel night, boolean completed) {
        Run current = run;
        run = null;
        boolean kept = current.outcome == Outcome.KEPT;
        NightThreeData data = NightThreeData.get(server);
        for (UUID id : List.copyOf(current.runners)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            FacilityData.ReturnPoint point = data.tickets.remove(id);
            data.setDirty();
            EntityCarryService.release(player);
            player.removeEffect(ModItems.TRANQUILIZED);
            if (inNight(player)) FacilityService.returnTo(player, point);
            if (!completed) continue;
            player.playNotifySound(ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 0.9F, 0.85F);
            ScreenTintPayload.send(player, 0x908AB6FF, 25, ScreenTintPayload.Style.FLASH);
            AbilityEffects.at(player, Kind.TEMPORAL_BURST, 4);
            reward(player, kept);
        }
        List<Entity> scene = new ArrayList<>();
        for (Entity entity : night.getAllEntities()) if (isScene(entity)) scene.add(entity);
        scene.forEach(Entity::discard);
        forceChunks(night, false);
    }

    /** What one runner brings back. The world changes only once, and a worse night never undoes a better one. */
    static void reward(ServerPlayer player, boolean kept) {
        MinecraftServer server = player.getServer();
        NightThreeData data = NightThreeData.get(server);
        boolean firstInWorld = kept && !data.podKept();
        data.record(player.getUUID(), kept);
        if (!kept) {
            tell(player, "Gray opened Pod 14. In the present, the blood trail still runs from it to his cell. The third night is still there to walk.", ChatFormatting.GRAY);
            return;
        }
        tell(player, "3:01 AM. Pod 14 is still sealed, and Claire Bennet slept through all of it. Almost all of it.", ChatFormatting.GOLD);
        if (!PastRecords.has(player, PastRecords.POD_FOURTEEN)) {
            tell(player, "There's a letter in your hands that wasn't there a moment ago.", ChatFormatting.LIGHT_PURPLE);
            PastRecords.give(player, PastRecords.podFourteen());
        }
        award(player, "story/pod_fourteen");
        if (firstInWorld) {
            NightThreeBunker.resealPresent(server);
            tell(player, "In the present, Pod 14 in the stasis hall is sealed again. Someone is still asleep in it.", ChatFormatting.AQUA);
        }
        if (SylarStoryData.get(server).forget(ModAbilities.REGENERATION_ID))
            tell(player, "In the present, the watchmaker's wounds stop closing on their own. Whatever has kept him alive all these years, "
                    + "it was never Claire's gift.", ChatFormatting.DARK_PURPLE);
    }

    private static void award(ServerPlayer player, String path) {
        var advancement = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path));
        if (advancement != null) player.getAdvancements().award(advancement, "granted");
    }

    private static void bar(MinecraftServer server, Run current) {
        int percent = current.channel * 100 / CHANNEL_TICKS;
        String gray = !current.loose ? "Gray: in his cage"
                : current.ticks < current.staggeredUntil ? "Gray: staggered"
                : current.castTarget != null ? "Gray: reaching for " + current.castTarget.getName().getString()
                : current.channel > 0 && current.gray != null && current.gray.position().distanceToSqr(POD_FRONT) <= 2 ? "Gray: opening Pod 14 (" + percent + "%)"
                : current.holding ? "Gray: at the edge of the quiet" : "Gray: walking to Pod 14";
        Component line = Component.literal(clock(current.ticks)).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("   " + gray).withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal("   Darts in Gray: " + Math.min(current.darts, SEDATION) + "/" + SEDATION).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("   Suppressor: " + (suppressorAwake(current) ? "awake" : "down")).withStyle(ChatFormatting.GRAY));
        forEachRunner(server, current, player -> player.displayClientMessage(line, true));
    }

    /** The clock in the bunker: 2:57:00 AM when you arrive, 3:00:00 AM when Bennet comes down. */
    public static String clock(int ticks) {
        int seconds = 57 * 60 + Math.clamp(ticks, 0, END_TICK) / 20;
        return String.format("%d:%02d:%02d AM", seconds >= 60 * 60 ? 3 : 2, seconds / 60 % 60, seconds % 60);
    }

    // ------------------------------------------------------------------
    // The journal
    // ------------------------------------------------------------------

    public static String journal(ServerPlayer player) {
        if (!unlocked(player)) return "Locked in the past. Live to see 6:15 on the avenue first.";
        if (isRunner(player)) return "Now: " + clock(run.ticks) + ". Keep Gray from Pod 14 until 3:00. Leave the past returns you early.";
        NightThreeData.Best best = NightThreeData.get(player.getServer()).best(player.getUUID());
        if (best.runs() == 0) return "Sublevel 4, the third night of the lockdown, 2:57 AM. Gabriel Gray gets out of his cage tonight and walks to "
                + "Pod 14, where Claire Bennet sleeps. Keep him from her until Noah Bennet comes down the shaft at 3:00.";
        return best.kept() ? "Pod 14 stayed shut: Gray never took Claire's gift. The third night is still there to walk."
                : "Gray opened Pod 14 on your last night. Walk the third night again, and keep him from her until 3:00.";
    }

    // ------------------------------------------------------------------
    // The rules of the past
    // ------------------------------------------------------------------

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) recover(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) recover(player);
    }
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) logout(player);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { shutdown(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) {
        Run current = run;
        if (current == null || event.getEntity() != current.gray) return;
        event.setCanceled(true); // Nobody from the present can hurt anyone in the past.
        Entity source = event.getSource().getEntity();
        if (source instanceof Projectile projectile) source = projectile.getOwner();
        if (source instanceof ServerPlayer player && isRunner(player)) stagger(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isRunner(player)) {
            event.setCanceled(true);
            player.setHealth(Math.max(1, player.getHealth()));
            knockOut(player, "The third night isn't the only thing that can kill you in the past. You're thrown back to the present.");
        }
    }

    @SubscribeEvent public static void spawn(EntityJoinLevelEvent event) {
        // Server only: tags aren't sent to clients, so a client would turn away everyone the night put there.
        if (!event.getLevel().isClientSide() && inNight(event.getLevel()) && event.getEntity() instanceof Mob && !isScene(event.getEntity()))
            event.setCanceled(true);
    }

    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (inNight(event.getPlayer().level())) event.setCanceled(true);
    }

    @SubscribeEvent public static void placing(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() != null && inNight(event.getEntity().level())) event.setCanceled(true);
    }

    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!inNight(event.getLevel())) return;
        // A runner may set down someone they carry, and open doors and hatches; nothing else here is theirs to use.
        if (event.getEntity() instanceof ServerPlayer player && EntityCarryService.isCarrying(player)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent public static void rightItem(PlayerInteractEvent.RightClickItem event) {
        if (!inNight(event.getLevel())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event) {
        Run current = run;
        if (current == null || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !isRunner(player) || !isScene(event.getTarget()) || player.isShiftKeyDown()) return; // Sneak + use carries.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Entity target = event.getTarget();
        if (target == current.gray) tell(player, current.loose ? "Gray doesn't look at you. He's looking past you, toward Pod 14."
                : "Gray sits on the edge of his bed, listening to a watch you can't see.", ChatFormatting.DARK_PURPLE);
        else if (target instanceof CompanyAgentEntity agent && agent.outCold())
            tell(player, "Out cold. He'll wake up with a headache and no idea who you were.", ChatFormatting.GRAY);
        else if (target == current.suppressor) tell(player, "The man in the gray suit looks at you and says nothing at all.", ChatFormatting.GRAY);
        else if (target instanceof CompanyAgentEntity) tell(player, "Guard: Hands where I can see them!", ChatFormatting.GRAY);
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

    private static void tell(ServerPlayer player, String message) { player.sendSystemMessage(Component.literal(message)); }
    private static void tell(ServerPlayer player, String message, ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(message).withStyle(colour));
    }

    /** Where the {@code slot}th runner arrives: a row across the generator hall, then a second row behind it. */
    static Vec3 slot(int slot) { return slot < 6 ? spot(7.5 + slot, 1, 4.5) : spot(1.5 + slot, 1, 3.5); }
    static List<Vec3> postSpots() { return POSTS.stream().map(Post::at).toList(); }
    static SylarEntity gray() { return run == null ? null : run.gray; }
    static List<CompanyAgentEntity> agents() { return run == null ? List.of() : List.copyOf(run.agents); }
    static CompanyAgentEntity suppressor() { return run == null ? null : run.suppressor; }
    static boolean loose() { return run != null && run.loose; }
    static boolean staggered() { return run != null && run.ticks < run.staggeredUntil; }
    static boolean busy() { return run != null && busy(run); }
    static Outcome outcome() { return run == null ? null : run.outcome; }
    static int ticks() { return run == null ? -1 : run.ticks; }
    static int channel() { return run == null ? 0 : run.channel; }
    /** Moves the night's clock, as if that much time had passed. */
    static void setTicks(int ticks) { if (run != null) run.ticks = ticks; }
    /** Puts Gray at the end of his route, as if he had walked it. */
    static void sendGrayToPod(int channel) {
        if (run == null || run.gray == null) return;
        run.waypoint = ROUTE.size() - 1;
        run.channel = channel;
        if (run.castTarget != null) cancelCast(run, run.gray);
        run.nextCast = run.ticks + 200;
        run.gray.teleportTo(POD_FRONT.x, POD_FRONT.y, POD_FRONT.z);
    }
}
