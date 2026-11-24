package com.jayptucker.heroesevolved.story.future;

import com.jayptucker.heroesevolved.admin.AdminEvents;
import com.jayptucker.heroesevolved.admin.AdminService;
import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.village.VillageTickets;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class FutureWarningGameTests {
    @SuppressWarnings("unchecked") private static List<net.minecraft.server.level.ServerPlayer> listed(FakePlayer p) {
        try {
            var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            field.setAccessible(true);
            return (List<net.minecraft.server.level.ServerPlayer>) field.get(p.getServer().getPlayerList());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static FakePlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "future-test"));
        p.moveTo(h.absolutePos(new BlockPos(6, 2, 3)).getBottomCenter(), 180, 0);
        p.setOnGround(true); p.setGameMode(GameType.SURVIVAL);
        h.getLevel().addNewPlayer(p);
        return p;
    }
    private static void remove(FakePlayer p) {
        FutureWarning.cancel(p, true);
        listed(p).remove(p);
        p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }
    private static void guarded(FakePlayer p, Runnable work) {
        try { work.run(); } catch (RuntimeException | AssertionError error) { remove(p); throw error; }
    }

    @GameTest(template = "rewind_empty", batch = "future.data", timeoutTicks = 40)
    public static void pendingAndHeardSurviveSavingAndBothClocksWork(GameTestHelper h) {
        var data = new FutureWarningData(); UUID pending = UUID.randomUUID(), done = UUID.randomUUID();
        data.queue(pending, 30_000, 40_000); data.finish(done); data.nextDraw(99_000);
        var copy = FutureWarningData.load(data.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(copy.due(pending).equals(data.due(pending)) && copy.heard(done) && copy.nextDraw() == 99_000, "Both encounters and the random schedule persist");
        h.assertTrue(!copy.due(pending).ready(29_999, 39_999), "Never early");
        h.assertTrue(copy.due(pending).ready(30_000, 20_000), "Sleeping can advance the calendar to the warning");
        h.assertTrue(copy.due(pending).ready(0, 40_000), "Frozen or rewound daylight still allows one game day");
        copy.cancelAll();
        h.assertTrue(copy.due(pending) == null && copy.heard(pending), "Global cancellation consumes offline queued starts");
        copy.reset(pending); h.assertTrue(copy.available(pending), "Explicit reset permits a later natural draw");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "future.selection", timeoutTicks = 40, skyAccess = true)
    public static void randomDrawSkipsHeardAndUnsafePlayersAndUsesSpaceBehindThem(GameTestHelper h) {
        var p = player(h);
        try {
            Vec3 site = FutureWarning.site(p);
            h.assertTrue(site != null && site.subtract(p.position()).dot(p.getLookAngle()) < 0, "The breach is behind the chosen player");
            var data = new FutureWarningData();
            h.assertTrue(FutureWarning.choose(List.of(p), data, RandomSource.create(9)) == p, "An eligible player can be drawn");
            data.finish(p.getUUID());
            h.assertTrue(FutureWarning.choose(List.of(p), data, RandomSource.create(9)) == null, "A completed player is not redrawn");
            data.reset(p.getUUID()); p.setGameMode(GameType.SPECTATOR);
            h.assertTrue(!FutureWarning.eligible(p) && !FutureWarning.start(p, true), "Spectators cannot be forced into a warning");
            p.setGameMode(GameType.SURVIVAL); p.setOnGround(true);
            for (int z = 3; z <= 11; z++) h.setBlock(new BlockPos(6, 3, z), Blocks.STONE);
            h.assertTrue(FutureWarning.site(p) == null && !FutureWarning.start(p, false), "Solid or occluded space defers the encounter without editing it");
            h.succeed();
        } finally { remove(p); }
    }

    @GameTest(template = "rewind_empty", batch = "future.omen", timeoutTicks = 160, skyAccess = true)
    public static void omenQueuesExactlyOneDayAndReleasesItsTicket(GameTestHelper h) {
        var p = player(h); var level = h.getLevel();
        guarded(p, () -> {
            long day = level.getDayTime(), game = level.getGameTime();
            h.assertTrue(FutureWarning.start(p, false), "Omen starts");
            h.assertTrue(!FutureWarning.start(p, false), "Duplicate start is refused");
            var due = FutureWarningData.get(level.getServer()).due(p.getUUID());
            h.assertTrue(due.calendar() == day + 24_000 && due.game() == game + 24_000, "The next breach is one Minecraft day later");
        });
        h.runAfterDelay(100, () -> guarded(p, () -> {
            h.assertTrue(!FutureWarning.active(p) && FutureWarning.pending(p), "Omen is gone, warning still pending");
            h.assertTrue(!VillageTickets.held("heroesevolved_future_warning" + p.getUUID()), "The omen's ticket is released");
            remove(p); h.succeed();
        }));
    }

    @GameTest(template = "rewind_empty", batch = "future.warning", timeoutTicks = 340, skyAccess = true)
    public static void doubleReachesIsDraggedBackAndWarningFinishesOnlyOnce(GameTestHelper h) {
        var p = player(h); Vec3[] site = {null}; StoryActorEntity[] actor = {null};
        guarded(p, () -> {
            site[0] = FutureWarning.site(p);
            h.assertTrue(FutureWarning.start(p, true), "Warning starts");
            actor[0] = h.getLevel().getEntitiesOfClass(StoryActorEntity.class, p.getBoundingBox().inflate(10)).stream()
                    .filter(a -> a.doubleOf().filter(p.getUUID()::equals).isPresent()).findFirst().orElseThrow();
            h.assertTrue(actor[0].getCustomName().getString().equals(p.getGameProfile().getName()), "Future self wears this player's identity");
        });
        h.runAfterDelay(60, () -> guarded(p, () -> {
            h.assertTrue(actor[0].pose() == StoryActorEntity.Pose.HAND_ON_GLASS, "The double reaches one arm toward the player");
            Vec3 normal = p.position().subtract(site[0]).multiply(1, 0, 1).normalize();
            h.assertTrue(actor[0].position().subtract(site[0]).dot(normal) > .5, "The double comes through the breach");
        }));
        h.runAfterDelay(220, () -> guarded(p, () -> {
            h.assertTrue(actor[0].pose() == StoryActorEntity.Pose.PLEAD, "The double struggles as the beams pull");
            Vec3 normal = p.position().subtract(site[0]).multiply(1, 0, 1).normalize();
            h.assertTrue(actor[0].position().subtract(site[0]).dot(normal) < -.5, "The double is hauled back through");
        }));
        h.runAfterDelay(270, () -> guarded(p, () -> {
            h.assertTrue(actor[0].isRemoved() && !FutureWarning.active(p) && !FutureWarning.pending(p), "Actor, live state and pending record all clear");
            h.assertTrue(FutureWarningData.get(p.getServer()).heard(p.getUUID()), "Natural replay is suppressed");
            h.assertTrue(FutureWarningGeometry.BOOM_VOLUME * 16 >= 128, "The closing boom reaches several chunks");
            remove(p); h.succeed();
        }));
    }

    @GameTest(template = "rewind_empty", batch = "future.admin", timeoutTicks = 40, skyAccess = true)
    public static void menuRejectsUnauthorizedRequestsAndCancelsSavedStarts(GameTestHelper h) {
        var p = player(h);
        try {
            var server = p.getServer();
            AdminService.handle(p, new AdminActionPayload(AdminActionPayload.Action.EVENT_START, p.getUUID(), "futurewarning", 0));
            h.assertTrue(!FutureWarning.pending(p), "An ordinary client cannot trigger the encounter through admin requests");
            var node = server.getCommands().getDispatcher().getRoot().getChild("heroesevolved").getChild("futurewarning");
            h.assertTrue(node != null && !node.canUse(p.createCommandSourceStack()), "Debug commands also require operator permission");
            h.assertTrue(AdminEvents.describe(server, p).stream().anyMatch(e -> e.id().equals("futurewarning") && e.story() && e.starts().size() == 3), "Story controls expose both encounters and reset");
            AdminEvents.start(server, p, "futurewarning", 0);
            h.assertTrue(FutureWarning.pending(p), "The real admin dispatcher starts the omen");
            AdminEvents.cancel(server, p, "futurewarning");
            h.assertTrue(!FutureWarning.pending(p) && !FutureWarning.active(p), "Cancellation includes the saved next-day warning");
            AdminEvents.start(server, p, "futurewarning", 2);
            h.assertTrue(FutureWarningData.get(server).available(p.getUUID()), "The menu reset restores natural eligibility");
            h.succeed();
        } finally { remove(p); }
    }

    @GameTest(template = "rewind_empty", batch = "future.resume", timeoutTicks = 40, skyAccess = true)
    public static void dueDeliveryWaitsForItsRecipientAndLogoutKeepsTheWarning(GameTestHelper h) {
        var p = player(h); var server = p.getServer(); var data = FutureWarningData.get(server);
        try {
            data.queue(p.getUUID(), 0, 0);
            FutureWarning.deliver(server);
            h.assertTrue(!FutureWarning.active(p) && FutureWarning.pending(p), "An offline recipient's warning is retained");
            // No tick occurs while listed: FakePlayer's connection needs no custom-channel implementation here.
            listed(p).add(p);
            p.setGameMode(GameType.SPECTATOR); FutureWarning.deliver(server);
            h.assertTrue(!FutureWarning.active(p) && FutureWarning.pending(p), "A due warning waits while the recipient is unsafe");
            p.setGameMode(GameType.SURVIVAL); p.setOnGround(true); FutureWarning.deliver(server);
            h.assertTrue(FutureWarning.active(p), "The normal delivery path starts the saved warning");
            FutureWarning.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
            h.assertTrue(!FutureWarning.active(p) && FutureWarning.pending(p), "Logout removes the actor and keeps the uncompleted encounter");
            h.assertTrue(h.getLevel().getEntitiesOfClass(StoryActorEntity.class, p.getBoundingBox().inflate(10)).stream()
                    .noneMatch(a -> a.doubleOf().filter(p.getUUID()::equals).isPresent()), "Logout leaves no orphan double");
            FutureWarning.deliver(server);
            h.assertTrue(FutureWarning.active(p), "Reconnecting can replay the interrupted warning");
            FutureWarning.cancelAll(server); FutureWarning.deliver(server);
            h.assertTrue(!FutureWarning.active(p) && !FutureWarning.pending(p), "Global cancel also suppresses future delivery");
            h.succeed();
        } finally { remove(p); }
    }
}
