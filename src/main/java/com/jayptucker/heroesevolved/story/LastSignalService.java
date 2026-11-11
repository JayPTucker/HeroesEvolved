package com.jayptucker.heroesevolved.story;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.expansion.PropheticPainting;
import com.jayptucker.heroesevolved.network.StoryJournalPayload;
import com.jayptucker.heroesevolved.progression.StoryDiscoveries;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.BunkerLogs;
import com.jayptucker.heroesevolved.story.bunker.BunkerPower;
import com.jayptucker.heroesevolved.story.bunker.BunkerService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A rescue in a world-generated relay station. Its controls retain their state when the site unloads. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class LastSignalService {
    public enum Phase { UNHEARD, RECEIVED, SEARCHING, ISOLATE, COOL, RESCUE, COMPLETE }

    private static final String ACTOR = "heroesevolved_last_signal_actor";
    private static final String AT_HOME = "heroesevolved_last_signal_home_";
    private static final List<String> NAMES = List.of("Mara", "Ivo");
    private static final Map<BlockPos, Expedition> EXPEDITIONS = new HashMap<>();

    private LastSignalService() {}

    private static final class Sleeper {
        final Villager villager;
        UUID guide;
        boolean safe;

        Sleeper(Villager villager) {
            this.villager = villager;
        }
    }

    private static final class Expedition {
        final SignalSite site;
        final List<ServerPlayer> present = new ArrayList<>();
        final List<Sleeper> sleepers = new ArrayList<>();
        SylarEntity gray;
        long grayArrival;
        long lastVisit;

        Expedition(SignalSite site) {
            this.site = site;
            lastVisit = site.level().getGameTime();
        }

        int saved() {
            return (int) sleepers.stream().filter(sleeper -> sleeper.safe).count();
        }
    }

    public static boolean atSite(ServerPlayer player) {
        return siteOf(player) != null;
    }

    public static boolean completed(MinecraftServer server) {
        return LastSignalData.get(server).rescued;
    }

    public static boolean hasBunker(ServerPlayer player) {
        return HeroesEvolvedConfig.COMMON.bunkerEnabled.get();
    }

    public static BlockPos location(ServerPlayer player) {
        return LastSignalData.get(player.getServer()).leads.get(player.getUUID());
    }

    public static Phase phase(ServerPlayer player) {
        if (completed(player.getServer())) return Phase.COMPLETE;
        SignalSite site = siteOf(player);
        if (site != null) return phase(site);
        return location(player) != null ? Phase.RECEIVED : Phase.UNHEARD;
    }

    private static Phase phase(SignalSite site) {
        if (isPowered(site, "coolant")) return Phase.RESCUE;
        if (isPowered(site, "isolator")) return Phase.COOL;
        return isPowered(site, "relay") ? Phase.ISOLATE : Phase.SEARCHING;
    }

    public static int rescued(ServerPlayer player) {
        if (completed(player.getServer())) return NAMES.size();
        SignalSite site = siteOf(player);
        Expedition run = site == null ? null : EXPEDITIONS.get(site.origin());
        return run == null ? 0 : run.saved();
    }

    private static SignalSite siteOf(ServerPlayer player) {
        return SignalSite.at(player.serverLevel(), player.blockPosition());
    }

    private static Expedition expedition(SignalSite site) {
        return EXPEDITIONS.computeIfAbsent(site.origin(), ignored -> new Expedition(site));
    }

    /** The station encounter includes players who have never awakened a power. */
    public static boolean targetable(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && EXPEDITIONS.values().stream().anyMatch(run -> run.gray != null && run.present.contains(player));
    }

    @SubscribeEvent public static void console(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level() != player.getServer().overworld()) return;
        BlockPos pos = event.getPos();
        BunkerData bunker = BunkerData.get(player.getServer());
        if (hasBunker(player) && bunker.built && pos.equals(BunkerBuilder.receiverPos(bunker.origin))
                && player.serverLevel().getBlockState(pos).is(Blocks.WAXED_WEATHERED_CUT_COPPER)) {
            accept(event);
            receive(player, bunker);
            return;
        }
        SignalSite site = SignalSite.at(player.serverLevel(), pos);
        if (site == null) return;
        String control = List.of("relay", "isolator", "coolant", "exit").stream()
                .filter(name -> site.at(name).equals(pos)).findFirst().orElse(null);
        if (control == null || !site.level().getBlockState(pos).is(Blocks.LEVER)) return;
        accept(event);
        if (completed(player.getServer())) {
            tell(player, "Mara and Ivo are already safe. The extraction stairs lead outside.");
            return;
        }
        Expedition run = expedition(site);
        if (!run.present.contains(player)) run.present.add(player);
        switch (control) {
            case "relay" -> relay(player, run);
            case "isolator" -> isolate(player, run);
            case "coolant" -> cool(player, run);
            case "exit" -> extract(player, run);
        }
    }

    private static void accept(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void receive(ServerPlayer player, BunkerData bunker) {
        if (!bunker.powered) {
            if (!BunkerService.isAwakening(player)) BunkerPower.start(player.serverLevel(), bunker);
            tell(player, "Main power is coming back. Use the receiver when its indicator lights.");
            return;
        }
        LastSignalData data = LastSignalData.get(player.getServer());
        if (data.rescued) {
            tell(player, "Relay station clear. Mara and Ivo are in the infirmary.");
        } else {
            BlockPos lead = location(player);
            if (lead == null) {
                lead = SignalSite.locate(player.serverLevel(), bunker.origin);
                if (lead == null) {
                    tell(player, "The receiver cannot get a bearing on a relay station in this world.");
                    return;
                }
                data.leads.put(player.getUUID(), lead);
                data.setDirty();
                chime(player.serverLevel(), BunkerBuilder.receiverPos(bunker.origin));
                tell(player, "RELAY STATION: Two pods occupied. Life support failing. Requesting extraction.");
            }
            tell(player, "Relay coordinates: X " + lead.getX() + ", Z " + lead.getZ()
                    + ". The Journal recorded the signal. Take the shaft to the surface.");
        }
        StoryJournalPayload.send(player);
    }

    private static void relay(ServerPlayer player, Expedition run) {
        if (phase(run.site) != Phase.SEARCHING) {
            tell(player, "The annex is open. Follow the yellow markings downstairs.");
            return;
        }
        openGate(run.site, "gate");
        power(run.site, "relay");
        announce(run, "Annex unlocked. Go downstairs and use the main isolator.");
    }

    private static void isolate(ServerPlayer player, Expedition run) {
        Phase phase = phase(run.site);
        if (phase != Phase.ISOLATE) {
            tell(player, phase == Phase.SEARCHING ? "Use the relay upstairs first." : "The failed circuit is isolated.");
            return;
        }
        power(run.site, "isolator");
        announce(run, "Fault isolated. Open the coolant valve across the gallery.");
    }

    private static void cool(ServerPlayer player, Expedition run) {
        Phase phase = phase(run.site);
        if (phase != Phase.COOL) {
            tell(player, phase == Phase.RESCUE ? "Life support is stable." : "Isolate the failed circuit before opening the valve.");
            return;
        }
        SignalSite site = run.site;
        power(site, "coolant");
        openGate(site, "rear_gate");
        for (BlockPos pos : site.all("hot")) site.level().setBlockAndUpdate(pos, Blocks.SEA_LANTERN.defaultBlockState());
        for (BlockPos pos : site.all("release")) {
            var state = site.level().getBlockState(pos);
            if (state.hasProperty(CryoChamberBlock.OPEN))
                site.level().setBlockAndUpdate(pos, state.setValue(CryoChamberBlock.OPEN, true));
        }
        spawnSleepers(run);
        announce(run, "Life support stable. Talk to Mara and Ivo, then stay close and lead them up the extraction stairs.");
    }

    /** The manual controls and the power shortcuts act on the same equipment. */
    public static void technique(ServerPlayer player, BlockPos pos, String technique) {
        if (!PowerRules.canAct(player) || completed(player.getServer())) return;
        SignalSite site = siteOf(player);
        if (site == null) return;
        Phase phase = phase(site);
        if (phase == Phase.ISOLATE && technique.equals("current") && pos.distSqr(site.at("isolator")) <= 16) {
            isolate(player, expedition(site));
        } else if (phase == Phase.COOL && technique.equals("cold") && pos.distSqr(site.at("coolant")) <= 16) {
            cool(player, expedition(site));
        }
    }

    private static void openGate(SignalSite site, String marker) {
        for (BlockPos pos : site.all(marker)) site.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private static boolean isPowered(SignalSite site, String marker) {
        return site.level().getBlockState(site.at(marker)).getOptionalValue(BlockStateProperties.POWERED).orElse(false);
    }

    private static void power(SignalSite site, String marker) {
        BlockPos pos = site.at(marker);
        var state = site.level().getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.POWERED))
            site.level().setBlockAndUpdate(pos, state.setValue(BlockStateProperties.POWERED, true));
        chime(site.level(), pos);
    }

    @SubscribeEvent(priority = EventPriority.LOW) public static void people(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player) || player.isShiftKeyDown()) return;
        Entity target = event.getTarget();
        if (target.getTags().stream().anyMatch(tag -> tag.startsWith(AT_HOME))) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            testimony(player, target);
            return;
        }
        if (!target.getTags().contains(ACTOR)) return;
        SignalSite site = siteOf(player);
        Expedition run = site == null ? null : EXPEDITIONS.get(site.origin());
        if (run == null) return;
        for (Sleeper sleeper : run.sleepers) {
            if (sleeper.villager != target) continue;
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            if (!sleeper.safe) {
                sleeper.guide = player.getUUID();
                sleeper.villager.setNoAi(false);
                tell(player, sleeper.villager.getName().getString() + ": I'm with you. Lead me up the extraction stairs.");
            }
            return;
        }
    }

    private static void testimony(ServerPlayer player, Entity target) {
        if (target.getName().getString().equals(NAMES.getFirst())) {
            tell(player, "Mara: I kept sending the signal. I thought we were the last two people awake.");
        } else {
            tell(player, "Ivo: The line kept both sites on backup power. We can finally shut it down.");
        }
        if (!StoryDiscoveries.has(player, StoryDiscoveries.SIGNAL_TESTIMONY)) {
            var book = BunkerLogs.book(StoryDiscoveries.SIGNAL_TESTIMONY, StoryDiscoveries.SIGNAL_AUTHOR,
                    "The Company sealed the relay station the night Odessa burned. It left us enough power to sleep, and a radio to call for help.",
                    "When the coolant failed, someone answered. We walked out of the annex and made it back to Sublevel 4.",
                    "For whoever wakes next: there are people waiting for you in the infirmary. Come say hello.\n\nMara and Ivo");
            if (StoryDiscoveries.found(player, book) && !player.addItem(book)) player.drop(book, false);
        }
    }

    private static void extract(ServerPlayer player, Expedition run) {
        if (run.saved() < NAMES.size()) {
            tell(player, run.saved() + "/2 sleepers at extraction. Bring Mara and Ivo to this platform.");
            return;
        }
        power(run.site, "exit");
        LastSignalData data = LastSignalData.get(player.getServer());
        data.rescued = true;
        data.setDirty();
        PropheticPainting.refreshSignal(player.getServer());
        announce(run, hasBunker(player) ? "Mara: We'll meet you in the bunker infirmary. The stairs lead outside."
                : "Mara: We made it. The stairs lead outside.");
    }

    private static void announce(Expedition run, String message) {
        for (ServerPlayer player : run.present) {
            tell(player, message);
            StoryJournalPayload.send(player);
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 10 != 0) return;
        if (completed(server)) {
            clearExpeditions();
            if (now % 40 == 0) placeSurvivors(server);
            return;
        }
        for (Expedition run : EXPEDITIONS.values()) run.present.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            SignalSite site = siteOf(player);
            if (site != null && phase(site) == Phase.RESCUE) {
                Expedition run = expedition(site);
                run.present.add(player);
                run.lastVisit = now;
            }
        }
        EXPEDITIONS.values().removeIf(run -> {
            if (!run.present.isEmpty()) tickExpedition(run, now);
            if (now - run.lastVisit < 100) return false;
            clear(run);
            return true;
        });
    }

    private static void spawnSleepers(Expedition run) {
        if (!run.sleepers.isEmpty()) return;
        List<BlockPos> pods = run.site.all("pod");
        for (int i = 0; i < NAMES.size(); i++) {
            Villager villager = sleeper(run.site.level(), pods.get(i), NAMES.get(i), ACTOR);
            run.sleepers.add(new Sleeper(villager));
            run.site.level().addFreshEntity(villager);
        }
        if (HeroesEvolvedConfig.COMMON.sylarEnabled.get()) {
            run.grayArrival = run.site.level().getGameTime() + 120;
            run.site.level().playSound(null, run.site.at("gray"), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1, 0.8F);
        }
    }

    private static void tickExpedition(Expedition run, long now) {
        spawnSleepers(run);
        if (run.grayArrival != 0 && now >= run.grayArrival) {
            run.grayArrival = 0;
            bringGray(run);
        }
        if (run.gray != null && run.gray.isAlive() && !run.gray.isRemoved()
                && run.present.stream().noneMatch(player -> player.getUUID().equals(run.gray.quarry()))) {
            run.present.stream().filter(LastSignalService::targetable)
                    .min(Comparator.comparingDouble(run.gray::distanceToSqr)).ifPresent(run.gray::retarget);
        }
        for (Sleeper sleeper : run.sleepers) guide(run, sleeper);
    }

    private static void guide(Expedition run, Sleeper sleeper) {
        Villager villager = sleeper.villager;
        if (sleeper.safe || !villager.isAlive()) return;
        Vec3 extraction = Vec3.atBottomCenterOf(run.site.at("extract"));
        if (villager.distanceToSqr(extraction) <= 9) {
            sleeper.safe = true;
            EntityCarryService.releaseEntity(villager);
            villager.getNavigation().stop();
            villager.setNoAi(true);
            chime(run.site.level(), run.site.at("extract"));
            announce(run, run.saved() == NAMES.size() ? "Both sleepers are safe. Use the extraction console."
                    : villager.getName().getString() + " reached extraction. Bring the other sleeper up.");
        } else if (sleeper.guide != null && !villager.isPassenger()) {
            ServerPlayer guide = run.present.stream().filter(player -> player.getUUID().equals(sleeper.guide))
                    .findFirst().orElse(null);
            if (guide == null) {
                sleeper.guide = null;
                villager.getNavigation().stop();
                villager.setNoAi(true);
            } else if (villager.distanceToSqr(guide) > 4) {
                villager.getNavigation().moveTo(guide, 1.05);
            } else {
                villager.getNavigation().stop();
            }
        }
    }

    private static void bringGray(Expedition run) {
        Vec3 at = Vec3.atBottomCenterOf(run.site.at("gray"));
        ServerPlayer target = run.present.stream().filter(player -> !player.isCreative() && !player.isSpectator())
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(at))).orElse(null);
        if (target == null) return;
        SylarEntity gray = ModEntities.SYLAR.get().create(run.site.level());
        if (gray == null) return;
        gray.addTag(ACTOR);
        gray.setPos(at);
        if (!run.site.level().addFreshEntity(gray)) return;
        run.gray = gray;
        gray.hunt(target);
        run.site.level().playSound(null, gray.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1, 0.8F);
        announce(run, "Gray: I heard the signal too.");
    }

    private static void placeSurvivors(MinecraftServer server) {
        if (!HeroesEvolvedConfig.COMMON.bunkerEnabled.get()) return;
        BunkerData bunker = BunkerData.get(server);
        if (!bunker.built) return;
        LastSignalData data = LastSignalData.get(server);
        ServerLevel home = server.overworld();
        List<BlockPos> positions = BunkerBuilder.survivorPositions(bunker.origin);
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            int bit = 1 << i;
            if ((data.infirmary & bit) != 0 || !home.isPositionEntityTicking(pos)
                    || !home.isEmptyBlock(pos) || !home.isEmptyBlock(pos.above())) continue;
            String tag = AT_HOME + i;
            boolean placed = !home.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(6),
                    villager -> villager.getTags().contains(tag)).isEmpty();
            if (placed || home.addFreshEntity(sleeper(home, pos, NAMES.get(i), tag))) {
                data.infirmary |= bit;
                data.setDirty();
            }
        }
    }

    private static Villager sleeper(ServerLevel level, BlockPos pos, String name, String tag) {
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setPos(Vec3.atBottomCenterOf(pos));
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        villager.setInvulnerable(true);
        villager.setNoAi(true);
        villager.addTag(tag);
        return villager;
    }

    @SubscribeEvent public static void oldActor(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity().getTags().contains(ACTOR))
            event.setCanceled(true);
    }

    /** Keep the authored controls and their supports usable in a survival world. */
    public static boolean protectedBlock(ServerLevel level, BlockPos pos) {
        SignalSite site = SignalSite.at(level, pos);
        if (site == null) return false;
        for (String control : List.of("relay", "isolator", "coolant", "exit")) {
            BlockPos lever = site.at(control);
            if (pos.equals(lever) || pos.distManhattan(lever) == 1) return true;
        }
        return site.all("gate").contains(pos) || site.all("rear_gate").contains(pos)
                || site.all("release").contains(pos);
    }

    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getPlayer().level() instanceof ServerLevel level && protectedBlock(level, event.getPos()))
            event.setCanceled(true);
    }

    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level)
            event.getAffectedBlocks().removeIf(pos -> protectedBlock(level, pos));
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        clearExpeditions();
    }

    private static void clearExpeditions() {
        EXPEDITIONS.values().forEach(LastSignalService::clear);
        EXPEDITIONS.clear();
    }

    private static void clear(Expedition run) {
        for (Sleeper sleeper : run.sleepers) {
            EntityCarryService.releaseEntity(sleeper.villager);
            sleeper.villager.discard();
        }
        if (run.gray != null) run.gray.discard();
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.AQUA));
    }

    private static void chime(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 1, 0.9F);
    }
}
