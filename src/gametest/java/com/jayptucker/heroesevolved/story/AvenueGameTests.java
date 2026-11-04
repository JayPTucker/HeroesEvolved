package com.jayptucker.heroesevolved.story;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.expansion.FacilityData;
import com.jayptucker.heroesevolved.progression.StoryDiscoveries;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryData;
import com.jayptucker.heroesevolved.time.intro.FractureCity;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The finale: Hiro's key, the avenue at 6:14, and what the present remembers of it. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class AvenueGameTests {
    @SuppressWarnings("unchecked")
    private static <T> T field(PlayerList list, String name) {
        try {
            Field field = PlayerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(list);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** A real, listed player that can cross dimensions (see TimeIntroGameTests). */
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
        AvenueGameTests.<List<ServerPlayer>>field(list, "players").add(player);
        AvenueGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
        return player;
    }

    private static void remove(ServerPlayer player) {
        TimeIntroService.logout(player);
        PlayerList list = player.getServer().getPlayerList();
        AvenueGameTests.<List<ServerPlayer>>field(list, "players").remove(player);
        AvenueGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    private static ServerPlayer awakened(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "avenue-test"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
        return p;
    }

    private static void discover(ServerPlayer p, String title) {
        p.setData(ModDataAttachments.POWER_JOURNEY, p.getData(ModDataAttachments.POWER_JOURNEY).discover(title));
    }

    @GameTest(template = "rewind_empty", batch = "story.avenue.rewards")
    public static void theAvenueWritesItsOutcomeIntoThePresent(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        AvenueData data = AvenueData.get(server);
        SylarStoryData sylar = SylarStoryData.get(server);
        boolean knewHeat = sylar.knows(ModAbilities.RADIATION_ID);
        data.forgetAll();
        try {
            h.assertTrue(StoryDiscoveries.ORDER.stream().filter(note -> note.place() == StoryDiscoveries.Place.PAST)
                            .map(StoryDiscoveries.Note::title).toList()
                            .equals(List.of(PastRecords.KEY, PastRecords.DOOR, PastRecords.ADDENDUM, PastRecords.WHY_YOU)),
                    "The past writes four records, in story order");
            h.assertTrue(AvenueService.NAMES.size() == AvenueService.CITIZENS && AvenueService.NAMES.stream().distinct().count() == AvenueService.CITIZENS,
                    "Twenty-four different people stand on the avenue");

            List<String> twelve = AvenueService.NAMES.subList(0, 12);
            WrittenBookContent door = PastRecords.door(twelve).get(DataComponents.WRITTEN_BOOK_CONTENT);
            String text = door.pages().stream().map(page -> page.raw().getString()).collect(Collectors.joining("\n"));
            h.assertTrue(text.contains("12 came down the stairs alive") && twelve.stream().allMatch(text::contains),
                    "Bennet's revised order counts and names everyone who came through his door: " + text);
            h.assertTrue(door.pages().size() == 5, "Header, count, two pages of names and the sign-off: " + door.pages().size());
            String everyone = PastRecords.door(AvenueService.NAMES).get(DataComponents.WRITTEN_BOOK_CONTENT).pages().get(1).raw().getString();
            h.assertTrue(everyone.contains("All twenty-four"), "Saving everyone reads differently: " + everyone);

            ServerPlayer p = awakened(h);
            sylar.learn(ModAbilities.RADIATION_ID);
            AvenueService.reward(p, twelve.subList(0, 3), true);
            h.assertTrue(PastRecords.has(p, PastRecords.DOOR) && PastRecords.has(p, PastRecords.WHY_YOU) && PastRecords.has(p, PastRecords.ADDENDUM),
                    "Coming home with people saved and Gray kept away writes Bennet's order, Gray's addendum and Hiro's last note");
            h.assertTrue(p.getInventory().items.stream().filter(stack -> stack.has(DataComponents.WRITTEN_BOOK_CONTENT)).count() == 3,
                    "The records are in the runner's hands");
            h.assertTrue(data.best(p.getUUID()).equals(new AvenueData.Best(3, true, 1)), "The evening is remembered: " + data.best(p.getUUID()));
            h.assertTrue(data.grayNeverSaw() && !sylar.knows(ModAbilities.RADIATION_ID),
                    "Gray never saw the light, so the watchmaker in the present never learned Sprague's heat");

            AvenueService.reward(p, twelve.subList(0, 1), false);
            h.assertTrue(data.best(p.getUUID()).equals(new AvenueData.Best(3, true, 2)) && data.grayNeverSaw(),
                    "A worse evening never takes back a better one: " + data.best(p.getUUID()));
            h.assertTrue(p.getInventory().items.stream().filter(stack -> stack.has(DataComponents.WRITTEN_BOOK_CONTENT)).count() == 3,
                    "Nothing new is written when the past went worse");
            h.succeed();
        } finally {
            data.forgetAll();
            if (knewHeat) sylar.learn(ModAbilities.RADIATION_ID);
        }
    }

    @GameTest(template = "rewind_empty", batch = "story.avenue.lead")
    public static void theLeadWalksFromTheAnnexToTheAvenue(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        AvenueData data = AvenueData.get(server);
        data.forgetAll();
        try {
            ServerPlayer p = awakened(h);
            for (var note : StoryDiscoveries.ORDER) if (note.place() != StoryDiscoveries.Place.PAST) discover(p, note.title());
            FacilityData annex = FacilityData.get(server);
            annex.discover(p.getUUID(), "unfinished_hour");
            h.assertTrue(StoryLead.next(p).contains("Annex"), "Before the key, the Annex: " + StoryLead.next(p));
            h.assertTrue(!AvenueService.unlocked(p) && AvenueService.journal(p).startsWith("Locked"), "The avenue waits for Hiro's key");

            annex.discover(p.getUUID(), "annex_saved");
            h.assertTrue(AvenueService.unlocked(p), "Saving the Annex opens the avenue");
            h.assertTrue(StoryLead.next(p).contains("6:10"), "Then Bennet's door: " + StoryLead.next(p));

            data.record(p.getUUID(), 5, false);
            h.assertTrue(StoryLead.next(p).contains("Gray"), "If Gray saw the light, stopping him is next: " + StoryLead.next(p));
            data.record(p.getUUID(), 5, true);
            h.assertTrue(StoryLead.next(p).startsWith("19 people"), "Then everyone left on the street: " + StoryLead.next(p));
            data.record(p.getUUID(), AvenueService.CITIZENS, true);
            h.assertTrue(StoryLead.next(p).startsWith("Everyone on the avenue"), "And at last, nothing left: " + StoryLead.next(p));
            h.assertTrue(AvenueService.journal(p).contains("24/24"), "The journal shows the best evening: " + AvenueService.journal(p));

            AvenueService.recover(p);
            h.assertTrue(PastRecords.has(p, PastRecords.KEY), "Anyone who saved the Annex before the key existed is given it");
            h.succeed();
        } finally {
            data.forgetAll();
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 1200, batch = "story.avenue.run")
    public static void anEveningOnTheAvenue(GameTestHelper h) {
        ServerLevel source = h.getLevel();
        MinecraftServer server = source.getServer();
        ServerLevel city = server.getLevel(TimeIntroService.DIMENSION);
        h.assertTrue(city != null, "The city must load from the datapack");
        AvenueData data = AvenueData.get(server);
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)));
        ServerPlayer[] player = new ServerPlayer[1];
        Villager[] follower = new Villager[1];
        float[] grayHealth = new float[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(FractureCity.get(city).ready() && !TimeIntroService.sceneRunning() && !AvenueService.running(),
                        "Waiting for the city"))
                .thenExecute(() -> {
                    data.forgetAll();
                    ServerPlayer p = runner(h, origin, "avenue-runner");
                    player[0] = p;
                    h.assertTrue(!AvenueService.enter(p), "The avenue is locked until the Annex is saved");
                    FacilityData.get(server).discover(p.getUUID(), "annex_saved");
                    h.assertTrue(AvenueService.enter(p), "Hiro's key opens the avenue");
                    h.assertTrue(TimeIntroService.isCity(p.level()) && p.position().distanceToSqr(AvenueService.ARRIVAL) < 9,
                            "Runners arrive in front of Bennet's door: " + p.position());
                    h.assertTrue(AvenueService.isRunner(p) && PowerRules.canAct(p) && !TimeIntroService.blocksPowers(p),
                            "Unlike the vision, the avenue is played with every power");
                    h.assertTrue(data.tickets.containsKey(p.getUUID()), "The way home is saved before crossing");
                    h.assertTrue(AvenueService.citizens().size() == AvenueService.CITIZENS && AvenueService.gray() != null,
                            "Twenty-four people and Gray are on the avenue");
                    h.assertTrue(city.getBlockState(new BlockPos(0, 65, 92)).is(Blocks.WHITE_CONCRETE), "Bennet's door is built");
                    h.assertTrue(AvenueService.clock(0).equals("6:10:00 PM") && AvenueService.clock(AvenueService.LIGHT_TICK).equals("6:14:00 PM"),
                            "The clock runs from 6:10 to 6:14");
                })
                .thenExecute(() -> {
                    // Using a person makes them follow; someone through the door is safe.
                    follower[0] = AvenueService.citizens().getFirst();
                    NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player[0], InteractionHand.MAIN_HAND, follower[0]));
                    h.assertTrue(!follower[0].isNoAi(), "A person you use comes with you");
                    follower[0].teleportTo(AvenueService.DOOR.getX() + 0.5, 64, AvenueService.DOOR.getZ() + 0.5);
                    SylarEntity gray = AvenueService.gray();
                    grayHealth[0] = gray.getHealth();
                    gray.hurt(player[0].damageSources().playerAttack(player[0]), 4);
                })
                .thenExecuteAfter(12, () -> {
                    h.assertTrue(AvenueService.safeCount() == 1 && follower[0].isRemoved(), "Through Bennet's door, they are safe");
                    h.assertTrue(AvenueService.gray().getHealth() == grayHealth[0] && !AvenueService.grayTurned(),
                            "One blow staggers Gray, but the past won't let him be hurt");
                    AvenueService.gray().hurt(player[0].damageSources().playerAttack(player[0]), 4);
                })
                .thenExecuteAfter(45, () -> AvenueService.gray().hurt(player[0].damageSources().playerAttack(player[0]), 4))
                .thenExecuteAfter(45, () -> {
                    h.assertTrue(!AvenueService.grayTurned(), "Blows land at most every two seconds");
                    AvenueService.gray().hurt(player[0].damageSources().playerAttack(player[0]), 4);
                    h.assertTrue(AvenueService.grayTurned() && !AvenueService.grayWatched(), "Three blows turn Gray back from the light");
                    SylarStoryData.get(server).learn(ModAbilities.RADIATION_ID);
                    AvenueService.setTicks(AvenueService.LIGHT_TICK - 2);
                })
                .thenExecuteAfter(4, () -> {
                    h.assertTrue(AvenueService.ticks() >= AvenueService.LIGHT_TICK, "The light falls at 6:14");
                    AvenueService.setTicks(AvenueService.RETURN_TICK - 2);
                })
                .thenWaitUntil(() -> h.assertTrue(!AvenueService.running(), "The evening ends"))
                .thenExecute(() -> {
                    ServerPlayer p = player[0];
                    try {
                        h.assertTrue(p.level() == source && p.position().distanceToSqr(origin) < 0.01,
                                "Everyone comes home to where they left: " + p.position());
                        h.assertTrue(!data.tickets.containsKey(p.getUUID()), "The ticket is spent");
                        h.assertTrue(data.best(p.getUUID()).saved() == 1 && data.best(p.getUUID()).grayKept() && data.grayNeverSaw(),
                                "The present remembers: " + data.best(p.getUUID()));
                        h.assertTrue(PastRecords.has(p, PastRecords.DOOR) && PastRecords.has(p, PastRecords.WHY_YOU)
                                && PastRecords.has(p, PastRecords.ADDENDUM), "The past is written into the runner's hands");
                        h.assertTrue(!SylarStoryData.get(server).knows(ModAbilities.RADIATION_ID), "Sylar never learned the heat");
                        h.assertTrue(!city.getBlockState(new BlockPos(0, 65, 92)).is(Blocks.WHITE_CONCRETE)
                                && city.getEntities((Entity) null, new net.minecraft.world.phys.AABB(-96, 60, -96, 96, 120, 96), AvenueService::isScene).isEmpty(),
                                "The street is put back and everyone from the evening is gone");
                        h.succeed();
                    } finally {
                        data.forgetAll();
                        remove(p);
                    }
                });
    }
}
