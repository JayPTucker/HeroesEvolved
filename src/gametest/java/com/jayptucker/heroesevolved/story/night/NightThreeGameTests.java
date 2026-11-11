package com.jayptucker.heroesevolved.story.night;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.company.CompanyAgentEntity;
import com.jayptucker.heroesevolved.company.SuppressorField;
import com.jayptucker.heroesevolved.company.Tranquilizer;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.StoryDiscoveries;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.story.AvenueData;
import com.jayptucker.heroesevolved.story.PastRecords;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryData;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Night Three: the bunker as it was, Gray's walk to Pod 14, and what the present remembers of it. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class NightThreeGameTests {
    @SuppressWarnings("unchecked")
    private static <T> T field(PlayerList list, String name) {
        try {
            Field field = PlayerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(list);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** A real, listed player that can cross dimensions (see AvenueGameTests). */
    private static ServerPlayer runner(GameTestHelper helper, Vec3 pos, String name) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch);
                absMoveTo(x, y, z, yaw, pitch);
            }
        };
        try {
            Connection connection = player.connection.getConnection();
            if (connection.channel() == null) {
                Field channel = Connection.class.getDeclaredField("channel");
                channel.setAccessible(true);
                channel.set(connection, new EmbeddedChannel());
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        player.setPos(pos);
        helper.getLevel().addNewPlayer(player);
        PlayerList list = helper.getLevel().getServer().getPlayerList();
        NightThreeGameTests.<List<ServerPlayer>>field(list, "players").add(player);
        NightThreeGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
        // Creative: the night's guards and Gray leave the tester alone, so the test drives the night itself.
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    private static void remove(ServerPlayer player) {
        NightThreeService.logout(player);
        PlayerList list = player.getServer().getPlayerList();
        NightThreeGameTests.<List<ServerPlayer>>field(list, "players").remove(player);
        NightThreeGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Someone could stand here: two open blocks over a floor. */
    private static boolean standable(ServerLevel level, Vec3 spot) {
        BlockPos feet = BlockPos.containing(spot);
        return open(level, feet) && open(level, feet.above()) && !open(level, feet.below());
    }

    @GameTest(template = "rewind_empty", batch = "story.night.bunker", timeoutTicks = 400)
    public static void theThirdNightBunkerIsWholeAndLit(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel night = server.getLevel(NightThreeBunker.DIMENSION);
        h.assertTrue(night != null, "Night Three's dimension must load from the datapack");
        h.assertTrue(!NightThreeService.running(), "No night is running while the bunker is checked");
        NightThreeBunker.build(server, night);
        h.assertTrue(NightThreeData.get(server).layout == NightThreeData.LAYOUT, "The rock around the bunker is built once");

        BlockState pod = night.getBlockState(NightThreeBunker.pod());
        h.assertTrue(pod.is(ModBlocks.CRYOCHAMBER.get()) && !pod.getValue(CryoChamberBlock.OPEN), "Pod 14 stands sealed: " + pod);
        h.assertTrue(NightThreeBunker.cageBars().stream().allMatch(pos -> night.getBlockState(pos).is(Blocks.IRON_BARS)),
                "Gray's cage still has all its bars");

        // Nothing the centuries brought: no cave, no moss, no cobwebs, no bones, no blood trail.
        BoundingBox box = NightThreeBunker.bounds();
        Set<net.minecraft.world.level.block.Block> later = Set.of(Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET, Blocks.CAVE_VINES,
                Blocks.CAVE_VINES_PLANT, Blocks.GLOW_LICHEN, Blocks.COBWEB, Blocks.BONE_BLOCK, Blocks.SKELETON_SKULL, Blocks.REDSTONE_WIRE,
                Blocks.MANGROVE_ROOTS, Blocks.HANGING_ROOTS, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.SOUL_LANTERN,
                Blocks.RED_STAINED_GLASS);
        List<String> found = new ArrayList<>();
        int lamps = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = night.getBlockState(pos);
            if (later.contains(state.getBlock()) && found.size() < 5) found.add(state + " at " + pos);
            if (state.getLightEmission() > 0) lamps++;
        }
        h.assertTrue(found.isEmpty(), "The bunker in the past has nothing that came later: " + found);
        h.assertTrue(lamps > 0, "The lights are on");
        h.assertTrue(night.getBlockState(NightThreeBunker.at(4, 12, 34)).is(Blocks.DEEPSLATE_BRICKS), "The stasis hall's ceiling is whole where the cave broke in later");

        // Everywhere the night puts someone, they can stand.
        for (int i = 0; i < NightThreeService.MAX_RUNNERS; i++)
            h.assertTrue(standable(night, NightThreeService.slot(i)), "Runner " + i + " arrives on open floor at " + NightThreeService.slot(i));
        h.assertTrue(standable(night, NightThreeService.CAGE), "Gray starts inside his cage");
        for (Vec3 step : NightThreeService.ROUTE) {
            // His first step is through the side of the cage he unmakes; before that, the bars are there.
            if (NightThreeBunker.cageBars().contains(BlockPos.containing(step))) {
                h.assertTrue(night.getBlockState(BlockPos.containing(step)).is(Blocks.IRON_BARS), "Gray's first step out is through his bars");
                continue;
            }
            h.assertTrue(standable(night, step), "Gray's way to Pod 14 is open at " + step);
        }
        for (Vec3 post : NightThreeService.postSpots()) h.assertTrue(standable(night, post), "A guard can stand his post at " + post);

        // Pod 14's sign, before anyone drew her blood.
        boolean signed = false;
        for (BlockPos pos : BlockPos.betweenClosed(NightThreeBunker.pod().offset(-1, 0, -1), NightThreeBunker.pod().offset(1, 3, 1)))
            if (night.getBlockEntity(pos) instanceof SignBlockEntity sign && sign.getFrontText().getMessage(2, false).getString().equals("STASIS SEALED"))
                signed = true;
        h.assertTrue(signed, "Pod 14's sign reads as it did on the third night");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 1200, batch = "story.night.kept")
    public static void aNightThatKeepsPodFourteenShut(GameTestHelper h) {
        ServerLevel source = h.getLevel();
        MinecraftServer server = source.getServer();
        ServerLevel night = server.getLevel(NightThreeBunker.DIMENSION);
        NightThreeData data = NightThreeData.get(server);
        SylarStoryData sylar = SylarStoryData.get(server);
        boolean knewRegeneration = sylar.knows(ModAbilities.REGENERATION_ID);
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)));
        ServerPlayer[] player = new ServerPlayer[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!NightThreeService.running(), "Waiting for the bunker"))
                .thenExecute(() -> {
                    data.forgetAll();
                    sylar.learn(ModAbilities.REGENERATION_ID);
                    ServerPlayer p = runner(h, origin, "night-runner");
                    player[0] = p;
                    h.assertTrue(!NightThreeService.enter(p), "The third night waits until you have lived to see 6:15");
                    AvenueData.get(server).record(p.getUUID(), 2, true);
                    h.assertTrue(NightThreeService.enter(p), "Living to 6:15 opens the third night");
                    h.assertTrue(NightThreeService.inNight(p) && p.position().distanceToSqr(NightThreeService.slot(0)) < 1,
                            "Runners arrive in the generator hall: " + p.position());
                    h.assertTrue(data.tickets.containsKey(p.getUUID()), "The way home is saved before crossing");
                    h.assertTrue(NightThreeService.isRunner(p) && PowerRules.canAct(p) && !SuppressorField.suppresses(p),
                            "Powers work in the bunker, away from the man in the gray suit");
                    SylarEntity gray = NightThreeService.gray();
                    h.assertTrue(gray != null && gray.position().distanceToSqr(NightThreeService.CAGE) < 2, "Gray is in his cage");
                    h.assertTrue(NightThreeService.agents().size() == 7 && NightThreeService.suppressor() != null
                                    && NightThreeService.agents().stream().allMatch(CompanyAgentEntity::nonLethal),
                            "Six guards and the Suppressor stand watch, and none of them can be killed: " + NightThreeService.agents().size());
                    h.assertTrue(NightThreeService.clock(0).equals("2:57:00 AM") && NightThreeService.clock(NightThreeService.END_TICK).equals("3:00:00 AM"),
                            "The clock runs from 2:57 to 3:00");
                    NightThreeService.setTicks(NightThreeService.ESCAPE_TICK - 2);
                })
                .thenExecuteAfter(4, () -> {
                    h.assertTrue(NightThreeService.loose() && NightThreeService.gray().getTags().contains(CompanyAgentEntity.LOOSE_TAG),
                            "At 2:57:10 Gray gets out");
                    h.assertTrue(NightThreeBunker.cageBars().stream().allMatch(pos -> night.getBlockState(pos).isAir()), "The bars of his cage are unmade");
                    SylarEntity gray = NightThreeService.gray();
                    float health = gray.getHealth();
                    gray.hurt(player[0].damageSources().playerAttack(player[0]), 4);
                    h.assertTrue(NightThreeService.staggered() && gray.getHealth() == health, "A blow staggers Gray; the past won't let him be hurt");
                    h.assertTrue(NightThreeService.busy() && !NightThreeService.deflects(gray), "While he is staggered, darts can reach him");
                    int before = Tranquilizer.doses(gray);
                    h.assertTrue(Tranquilizer.dart(NightThreeService.agents().getFirst(), gray) == before + 1, "A dart lands while he is busy");
                })
                .thenExecuteAfter(NightThreeService.STAGGER + 2, () -> {
                    SylarEntity gray = NightThreeService.gray();
                    h.assertTrue(!NightThreeService.staggered(), "The stagger passes");
                    h.assertTrue(NightThreeService.deflects(gray) == (NightThreeService.outcome() == NightThreeService.Outcome.NIGHT && !NightThreeService.busy()),
                            "Left alone, he turns darts aside");
                    NightThreeService.setTicks(NightThreeService.END_TICK - 2);
                })
                .thenExecuteAfter(4, () -> h.assertTrue(NightThreeService.outcome() == NightThreeService.Outcome.KEPT,
                        "At 3:00 Bennet comes down the shaft and Pod 14 is still shut: " + NightThreeService.outcome()))
                .thenWaitUntil(() -> h.assertTrue(!NightThreeService.running(), "The night ends"))
                .thenExecute(() -> {
                    ServerPlayer p = player[0];
                    try {
                        h.assertTrue(p.level() == source && p.position().distanceToSqr(origin) < 0.01, "Everyone comes home to where they left: " + p.position());
                        h.assertTrue(!data.tickets.containsKey(p.getUUID()), "The ticket is spent");
                        h.assertTrue(data.best(p.getUUID()).kept() && data.podKept(), "The present remembers: " + data.best(p.getUUID()));
                        h.assertTrue(PastRecords.has(p, PastRecords.POD_FOURTEEN), "Claire's letter is in the runner's hands");
                        h.assertTrue(!sylar.knows(ModAbilities.REGENERATION_ID)
                                        && "story.heroesevolved.sylar.no_regen".equals(SylarStoryService.outOfReach(server, ModAbilities.REGENERATION_ID)),
                                "The watchmaker never took Claire's gift, and can't take regeneration now");
                        h.assertTrue(night.getEntities((Entity) null, AABB.encapsulatingFullBlocks(
                                        new BlockPos(-40, 40, -20), new BlockPos(40, 100, 60)), NightThreeService::isScene).isEmpty(),
                                "Everyone from the night is gone");
                        h.succeed();
                    } finally {
                        data.forgetAll();
                        if (knewRegeneration) sylar.learn(ModAbilities.REGENERATION_ID);
                        remove(p);
                    }
                });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 1200, batch = "story.night.taken")
    public static void aNightWhenGrayOpensThePod(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel night = server.getLevel(NightThreeBunker.DIMENSION);
        NightThreeData data = NightThreeData.get(server);
        SylarStoryData sylar = SylarStoryData.get(server);
        boolean knewRegeneration = sylar.knows(ModAbilities.REGENERATION_ID);
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)));
        ServerPlayer[] player = new ServerPlayer[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!NightThreeService.running(), "Waiting for the bunker"))
                .thenExecute(() -> {
                    data.forgetAll();
                    sylar.learn(ModAbilities.REGENERATION_ID);
                    ServerPlayer p = runner(h, origin, "night-loser");
                    player[0] = p;
                    AvenueData.get(server).record(p.getUUID(), 1, false);
                    h.assertTrue(NightThreeService.enter(p), "The third night opens");
                    NightThreeService.setTicks(NightThreeService.ESCAPE_TICK - 1);
                })
                .thenExecuteAfter(3, () -> {
                    h.assertTrue(NightThreeService.loose(), "Gray is out");
                    NightThreeService.suppressor().knockOut();
                    h.assertTrue(!SuppressorField.suppresses(NightThreeService.gray()), "With the man in the gray suit down, nothing quiets Gray");
                    NightThreeService.sendGrayToPod(NightThreeService.CHANNEL_TICKS - 6);
                })
                .thenExecuteAfter(12, () -> {
                    h.assertTrue(NightThreeService.outcome() == NightThreeService.Outcome.TAKEN, "Left alone at Pod 14, Gray opens it: "
                            + NightThreeService.outcome() + " at " + NightThreeService.channel());
                    BlockState pod = night.getBlockState(NightThreeBunker.pod());
                    h.assertTrue(pod.getValue(CryoChamberBlock.OPEN), "Pod 14 stands open");
                })
                .thenWaitUntil(() -> h.assertTrue(!NightThreeService.running(), "The night ends"))
                .thenExecute(() -> {
                    ServerPlayer p = player[0];
                    try {
                        h.assertTrue(data.best(p.getUUID()).runs() == 1 && !data.best(p.getUUID()).kept() && !data.podKept(),
                                "A night Gray wins changes nothing in the present");
                        h.assertTrue(!PastRecords.has(p, PastRecords.POD_FOURTEEN), "No letter is written");
                        h.assertTrue(sylar.knows(ModAbilities.REGENERATION_ID) && SylarStoryService.outOfReach(server, ModAbilities.REGENERATION_ID) == null,
                                "The watchmaker still has Claire's gift");
                        h.succeed();
                    } finally {
                        data.forgetAll();
                        if (!knewRegeneration) sylar.forget(ModAbilities.REGENERATION_ID);
                        remove(p);
                    }
                });
    }

    @GameTest(template = "rewind_empty", batch = "story.night.briefing")
    public static void theBriefingSaysHowToStopGray(GameTestHelper h) {
        List<net.minecraft.network.chat.Component> lines = NightThreeService.briefing(0);
        String said = lines.stream().map(net.minecraft.network.chat.Component::getString).collect(Collectors.joining("\n"));
        h.assertTrue(said.contains("2:57:00 AM") && said.contains("Pod 14") && said.contains("until 3:00"), "The goal and the clock: " + said);
        h.assertTrue(said.contains("hit him and he staggers") && said.contains("throw"), "How to stop him: " + said);
        h.assertTrue(said.contains("Four put him to sleep") && said.contains("three and you're back"), "What the darts do: " + said);
        h.assertTrue(said.contains("gray suit") && said.contains("no power works"), "The quiet by the pod: " + said);
        h.assertTrue(lines.size() == 4, "One line for each: " + lines.size());
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "story.night.rewards")
    public static void theLetterAndTheWorldChangeOnlyOnce(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        NightThreeData data = NightThreeData.get(server);
        SylarStoryData sylar = SylarStoryData.get(server);
        boolean knewRegeneration = sylar.knows(ModAbilities.REGENERATION_ID);
        data.forgetAll();
        try {
            h.assertTrue(StoryDiscoveries.ORDER.getLast().title().equals(PastRecords.POD_FOURTEEN)
                    && StoryDiscoveries.ORDER.getLast().place() == StoryDiscoveries.Place.PAST, "Claire's letter is the last record the past writes");
            WrittenBookContent letter = PastRecords.podFourteen().get(DataComponents.WRITTEN_BOOK_CONTENT);
            String text = letter.pages().stream().map(page -> page.raw().getString()).collect(Collectors.joining("\n"));
            h.assertTrue(letter.author().equals("Claire") && text.contains("Still me"), "Claire wrote it: " + text);

            ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "night-reward"));
            p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
            p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
            NightThreeService.reward(p, false);
            h.assertTrue(!PastRecords.has(p, PastRecords.POD_FOURTEEN) && !data.podKept() && data.best(p.getUUID()).runs() == 1,
                    "Losing writes nothing");
            NightThreeService.reward(p, true);
            h.assertTrue(PastRecords.has(p, PastRecords.POD_FOURTEEN) && data.podKept() && data.best(p.getUUID()).kept(), "Winning writes the letter");
            NightThreeService.reward(p, false);
            h.assertTrue(data.best(p.getUUID()).kept() && data.podKept() && data.best(p.getUUID()).runs() == 3,
                    "A worse night never takes back a better one");
            h.assertTrue(p.getInventory().items.stream().filter(stack -> stack.has(DataComponents.WRITTEN_BOOK_CONTENT)).count() == 1,
                    "The letter is written once");
            h.succeed();
        } finally {
            data.forgetAll();
            if (knewRegeneration) sylar.learn(ModAbilities.REGENERATION_ID);
        }
    }
}
