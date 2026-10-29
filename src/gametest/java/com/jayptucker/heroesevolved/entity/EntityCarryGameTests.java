package com.jayptucker.heroesevolved.entity;

import com.jayptucker.heroesevolved.events.EntityCarryEvents;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

/** Actual vehicle graphs and temporary flags, including both sides of a multiplayer carry. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class EntityCarryGameTests {
    private static final Map<UUID, Integer> TELEPORT_PACKETS = new HashMap<>();
    private static int teleports(ServerPlayer player) { return TELEPORT_PACKETS.getOrDefault(player.getUUID(), 0); }
    /** Real ServerPlayers are essential here: FakePlayer deliberately refuses startRiding. */
    private static ServerPlayer unaddedPlayer(GameTestHelper h, GameProfile profile) {
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        TELEPORT_PACKETS.put(p.getUUID(), 0);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(p.getServer(), connection, p,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundPlayerPositionPacket) TELEPORT_PACKETS.merge(p.getUUID(), 1, Integer::sum);
            }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static ServerPlayer player(GameTestHelper h, Vec3 at) {
        ServerPlayer p = unaddedPlayer(h, new GameProfile(UUID.randomUUID(), "carry-test"));
        p.setPos(at); p.setYRot(0); p.yBodyRot = 0; p.setOnGround(false);
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static Vec3 origin(GameTestHelper h) { return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 40, 3))); }
    private static Mob mob(GameTestHelper h, Vec3 at) {
        Mob target = EntityType.VILLAGER.create(h.getLevel());
        target.setPersistenceRequired(); target.setPos(at);
        h.getLevel().addFreshEntity(target);
        return target;
    }
    private static void cleanup(ServerPlayer p, Entity... targets) {
        EntityCarryService.release(p);
        for (Entity target : targets) {
            if (target instanceof ServerPlayer player) {
                EntityCarryService.release(player);
                player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                TELEPORT_PACKETS.remove(player.getUUID());
            } else target.discard();
        }
        p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        TELEPORT_PACKETS.remove(p.getUUID());
    }
    private static Flags flags(Entity target) {
        return new Flags(target.isNoGravity(), target.noPhysics, target.isInvulnerable(), target instanceof Mob mob && mob.isNoAi());
    }
    private record Flags(boolean noGravity, boolean noPhysics, boolean invulnerable, boolean noAi) {}
    private static void assertRestored(GameTestHelper h, Entity target, Flags before) {
        h.assertTrue(flags(target).equals(before), "Every temporary carry flag must return to its original value");
        h.assertTrue(!EntityCarryService.isBeingCarried(target) && !target.isPassenger(), "The recipient must leave both the carry index and vehicle graph");
    }
    private static CarryAnchorEntity anchor(Entity target) { return (CarryAnchorEntity) target.getVehicle(); }

    @GameTest(template = "rewind_empty", batch = "carry.graph")
    public static void carryUsesOneNestedVehicleGraphAndRejectsDuplicateOrCyclicOwners(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        ServerPlayer other = player(h, p.position().add(1, 0, 0));
        Mob target = mob(h, p.position().add(0, 0, 2));
        Mob second = mob(h, p.position().add(-1, 0, 1));
        try {
            h.assertTrue(EntityCarryService.pickUp(p, target), "A nearby free recipient can be picked up");
            CarryAnchorEntity anchor = anchor(target);
            h.assertTrue(anchor.getVehicle() == p && p.hasPassenger(anchor) && anchor.hasPassenger(target),
                    "The recipient and anchor must form a single carrier -> anchor -> recipient riding graph");
            h.assertTrue(EntityCarryService.isCarrying(p) && EntityCarryService.isBeingCarried(target), "Both ownership directions must agree");
            h.assertTrue(!EntityCarryService.pickUp(other, target) && !EntityCarryService.pickUp(p, second),
                    "One recipient cannot have two owners and one carrier cannot have two recipients");
            h.assertTrue(!EntityCarryService.pickUp(other, p) && !EntityCarryService.pickUp(p, p),
                    "A carrier cannot become cargo and a player cannot carry themself");
            h.succeed();
        } finally { cleanup(p, other, target, second); }
    }

    @GameTest(template = "rewind_empty", batch = "carry.validation")
    public static void pickupValidatesReachAndCoverWithoutPartialState(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        Mob distant = mob(h, p.position().add(0, 0, 30));
        Mob covered = mob(h, p.position().add(0, 0, 2.5));
        BlockPos wall = p.blockPosition().offset(0, 0, 1);
        try {
            h.assertTrue(!EntityCarryService.pickUp(p, distant), "Calling the carry service directly cannot bypass pickup reach");
            h.assertTrue(!EntityCarryService.canPickUp(p, distant, 1_000)
                            && !EntityCarryService.canPickUp(p, covered, Double.NaN)
                            && !EntityCarryService.canPickUp(p, covered, Double.POSITIVE_INFINITY),
                    "Caller-supplied reach must remain finite and cannot bypass the service's hard maximum");
            for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++)
                h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(!EntityCarryService.pickUp(p, covered), "A nearby recipient behind a solid wall cannot be grabbed");
            h.assertTrue(!EntityCarryService.isCarrying(p) && !EntityCarryService.isBeingCarried(covered)
                            && !covered.isInvulnerable(), "Rejected pickup must not leave temporary protections or a partial graph");
            h.succeed();
        } finally {
            for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++)
                h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
            cleanup(p, distant, covered);
        }
    }

    @GameTest(template = "rewind_empty", batch = "carry.flags")
    public static void releasingAMobRestoresBothNormalAndPreexistingSpecialFlags(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        Mob target = mob(h, p.position().add(0, 0, 2));
        try {
            for (boolean special : new boolean[]{false, true}) {
                target.setNoGravity(special); target.noPhysics = special; target.setInvulnerable(special); target.setNoAi(special);
                target.setPos(p.position().add(0, 0, 2));
                Flags before = flags(target);
                h.assertTrue(EntityCarryService.pickUp(p, target), "The carry must accept this free mob");
                EntityCarryService.tick(p);
                h.assertTrue(target.isNoGravity() && target.noPhysics && target.isInvulnerable() && target.isNoAi(),
                        "Temporary carry constraints hold the recipient still and protected");
                EntityCarryService.release(p);
                assertRestored(h, target, before);
                h.assertTrue(!EntityCarryService.isCarrying(p) && p.getPassengers().stream().noneMatch(CarryAnchorEntity.class::isInstance),
                        "Releasing cargo must also remove the carrier's invisible anchor");
            }
            target.setPos(p.position().add(0, 0, 2));
            Flags beforeShutdown = flags(target);
            h.assertTrue(EntityCarryService.pickUp(p, target), "The shutdown fixture must have an active carry");
            EntityCarryEvents.onServerStopping(new ServerStoppingEvent(p.getServer()));
            assertRestored(h, target, beforeShutdown);
            h.assertTrue(!EntityCarryService.isCarrying(p) && p.getPassengers().isEmpty(),
                    "Server stopping releases temporary carry graphs before the world and players are saved");
            h.succeed();
        } finally { cleanup(p, target); }
    }

    @GameTest(template = "rewind_empty", batch = "carry.reverse_release")
    public static void releasingTheCarriedPlayerEndsTheCarriersStateToo(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        ServerPlayer held = player(h, p.position().add(0, 0, 2));
        Flags before = flags(held);
        try {
            h.assertTrue(EntityCarryService.pickUp(p, held), "A nearby player can be carried");
            EntityCarryService.release(held);
            assertRestored(h, held, before);
            h.assertTrue(!EntityCarryService.isCarrying(p) && p.getPassengers().isEmpty(),
                    "Releasing the recipient must clear the reverse owner index and the owner's anchor");
            h.succeed();
        } finally { cleanup(p, held); }
    }

    @GameTest(template = "rewind_empty", batch = "carry.placement")
    public static void blockedSetDownIsTransactionalAndSuccessfulPlacementRestoresFlags(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        Mob target = mob(h, p.position().add(0, 0, 2));
        Flags before = flags(target);
        Vec3 destination = p.position().add(0, 0, 3);
        BlockPos block = BlockPos.containing(destination);
        try {
            h.assertTrue(EntityCarryService.pickUp(p, target), "The placement fixture must start carried");
            CarryAnchorEntity anchor = anchor(target);
            h.getLevel().setBlock(block, Blocks.STONE.defaultBlockState(), 3);
            h.getLevel().setBlock(block.above(), Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(!EntityCarryService.setDown(p, destination), "A solid destination must reject set-down");
            h.assertTrue(EntityCarryService.isCarrying(p) && EntityCarryService.isBeingCarried(target)
                            && target.getVehicle() == anchor && target.isInvulnerable(),
                    "Failed set-down must leave the original carry and protection intact");
            h.getLevel().setBlock(block, Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(block.above(), Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(EntityCarryService.setDown(p, destination), "The cleared destination accepts set-down");
            assertRestored(h, target, before);
            h.assertTrue(target.position().distanceTo(destination) < .1, "Set-down places the recipient at the requested safe point");
            h.succeed();
        } finally {
            h.getLevel().setBlock(block, Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(block.above(), Blocks.AIR.defaultBlockState(), 3);
            cleanup(p, target);
        }
    }

    @GameTest(template = "rewind_empty", batch = "carry.motion")
    public static void carriedPlayersFollowMovementAndTurningWithoutCrossingEitherViewRay(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        ServerPlayer held = player(h, p.position().add(0, 0, 2));
        try {
            int beforeMount = teleports(held);
            h.assertTrue(EntityCarryService.pickUp(p, held), "The movement fixture must start carried");
            h.assertTrue(teleports(held) == beforeMount + 1, "Mounting a real player uses vanilla's single initial position synchronization");
            int mountedTeleports = teleports(held);
            EntityCarryService.tick(p);
            Vec3 initialOffset = held.position().subtract(p.position());
            for (int step = 0; step < 8; step++) {
                float yaw = step * 45;
                p.setYRot(yaw); p.yBodyRot = yaw; p.yBodyRotO = yaw;
                held.setYRot(yaw);
                p.setPos(p.position().add(step % 2 == 0 ? 1.1 : -.4, step % 2 == 0 ? .6 : -.2, .8));
                p.setDeltaMovement(new Vec3(1.1, .3, .8));
                p.setSprinting(step % 2 == 0);
                EntityCarryService.tick(p);
                CarryAnchorEntity anchor = anchor(held);
                p.positionRider(anchor); anchor.positionRider(held);
                h.assertTrue(held.distanceTo(p) < 3 && anchor.getVehicle() == p,
                        "Walking, sprinting, jumping and fast turns keep one locally followable riding graph");
                h.assertTrue(held.getRootVehicle() == p && held.getControlledVehicle() == null,
                        "The recipient follows the carrier as root vehicle but cannot steer or send controlling movement for it");
                h.assertTrue(Math.abs(held.position().subtract(p.position()).length() - initialOffset.length()) < .15,
                        "The carry offset must not stretch or lag as its owner moves and rotates");
                Vec3 carrierEye = p.getEyePosition();
                Vec3 recipientEye = held.getEyePosition();
                h.assertTrue(held.getBoundingBox().inflate(.05).clip(carrierEye,
                                carrierEye.add(p.getLookAngle().scale(5))).isEmpty(),
                        "The recipient's hitbox must stay outside the carrier's forward view and interaction ray");
                h.assertTrue(p.getBoundingBox().inflate(.05).clip(recipientEye,
                                recipientEye.add(held.getLookAngle().scale(5))).isEmpty(),
                        "The carrier must also stay outside the recipient's forward view ray");
            }
            h.assertTrue(teleports(held) == mountedTeleports,
                    "Normal carried movement and turns must use riding synchronization without per-tick correction teleports");
            EntityCarryService.release(p);
            h.assertTrue(teleports(held) == mountedTeleports + 1,
                    "Releasing a player sends one final placement synchronization");
            h.succeed();
        } finally { cleanup(p, held); }
    }

    @GameTest(template = "rewind_empty", batch = "carry.dismount")
    public static void externalDismountAndAnchorRemovalRestoreTheRecipient(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        Mob target = mob(h, p.position().add(0, 0, 2));
        ServerPlayer held = player(h, p.position().add(1, 0, 2));
        var boat = EntityType.BOAT.create(h.getLevel());
        boat.setPos(p.position().add(0, 0, 4)); boat.setNoGravity(true); h.getLevel().addFreshEntity(boat);
        Flags before = flags(target), playerBefore = flags(held);
        try {
            h.assertTrue(EntityCarryService.pickUp(p, target), "The first carry must begin");
            target.stopRiding(); EntityCarryService.tick(p);
            assertRestored(h, target, before);
            h.assertTrue(!EntityCarryService.isCarrying(p), "An external dismount clears the owner's stale carry");
            target.setPos(p.position().add(0, 0, 2));
            h.assertTrue(EntityCarryService.pickUp(p, target), "The recipient can be picked up after a clean dismount");
            anchor(target).discard(); EntityCarryService.tick(p);
            assertRestored(h, target, before);
            h.assertTrue(!EntityCarryService.isCarrying(p) && p.getPassengers().isEmpty(),
                    "Removing an invisible anchor cannot strand protection or ownership state");
            h.assertTrue(EntityCarryService.pickUp(p, held), "The external player teleport fixture must begin carried");
            held.stopRiding();
            Vec3 externalPosition = p.position().add(15, 5, 0);
            held.setPos(externalPosition);
            int beforeCleanup = teleports(held);
            EntityCarryService.tick(p);
            assertRestored(h, held, playerBefore);
            h.assertTrue(held.position().distanceTo(externalPosition) < .001 && teleports(held) == beforeCleanup,
                    "Retiring an externally dismounted carry must preserve a new position instead of snapping the player back");
            held.setPos(p.position().add(0, 0, 2));
            h.assertTrue(EntityCarryService.pickUp(p, held), "The external remount fixture must begin carried");
            held.stopRiding();
            h.assertTrue(held.startRiding(boat, true), "A real player must be able to switch to the external boat");
            Vec3 boatSeat = held.position();
            int beforeRemountCleanup = teleports(held);
            EntityCarryService.tick(p);
            h.assertTrue(flags(held).equals(playerBefore) && !EntityCarryService.isBeingCarried(held)
                            && !EntityCarryService.isCarrying(p) && held.getVehicle() == boat,
                    "Retiring the old carry restores flags while preserving a new system's boat mount");
            h.assertTrue(held.position().distanceTo(boatSeat) < .001 && teleports(held) == beforeRemountCleanup,
                    "Old-owner cleanup cannot teleport or unseat a player who has already remounted elsewhere");
            h.succeed();
        } finally { cleanup(p, target, held, boat); }
    }

    @GameTest(template = "rewind_empty", batch = "carry.player_lifecycle")
    public static void eitherPlayersLogoutDeathOrDimensionEventEndsBothSides(GameTestHelper h) {
        for (int fixture = 0; fixture < 6; fixture++) {
            ServerPlayer p = player(h, origin(h));
            ServerPlayer held = player(h, p.position().add(0, 0, 2));
            Flags before = flags(held);
            try {
                h.assertTrue(EntityCarryService.pickUp(p, held), "Each lifecycle fixture must begin with a carried player");
                ServerPlayer leaving = fixture % 2 == 0 ? p : held;
                if (fixture < 2) EntityCarryEvents.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(leaving));
                else if (fixture < 4) EntityCarryEvents.onPlayerDeath(new LivingDeathEvent(leaving, leaving.damageSources().generic()));
                else EntityCarryEvents.onPlayerChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(leaving, Level.OVERWORLD, Level.NETHER));
                assertRestored(h, held, before);
                h.assertTrue(!EntityCarryService.isCarrying(p) && p.getPassengers().isEmpty(),
                        "Lifecycle cleanup must work when either the carrier or the carried player leaves");
            } finally { cleanup(p, held); }
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "carry.dead_recipient")
    public static void dyingOrRemovedMobCannotLeaveAStaleCarrier(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        Mob target = mob(h, p.position().add(0, 0, 2));
        try {
            h.assertTrue(EntityCarryService.pickUp(p, target), "The mob must begin carried");
            target.setHealth(0); EntityCarryService.tick(p);
            h.assertTrue(!EntityCarryService.isCarrying(p) && !EntityCarryService.isBeingCarried(target) && p.getPassengers().isEmpty(),
                    "A dead recipient must not remain in either index or keep its carrier tethered");
            h.succeed();
        } finally { cleanup(p, target); }
    }

    @GameTest(template = "rewind_empty", batch = "carry.hurl")
    public static void carryProtectsFromDamageAndHurlRestoresFlagsBeforeApplyingVelocity(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        ServerPlayer held = player(h, p.position().add(0, 0, 2));
        Flags before = flags(held);
        try {
            h.assertTrue(EntityCarryService.pickUp(p, held), "The damage fixture must begin with a carried player");
            float health = held.getHealth();
            held.hurt(held.damageSources().generic(), 6);
            h.assertTrue(held.getHealth() == health, "A carried player must remain protected from ordinary attacks");
            Vec3 velocity = new Vec3(1.25, .4, -.5);
            h.assertTrue(EntityCarryService.hurl(p, velocity) == held, "Hurl returns the released recipient");
            assertRestored(h, held, before);
            h.assertTrue(held.getDeltaMovement().distanceTo(velocity) < .001 && !EntityCarryService.isCarrying(p),
                    "Hurl restores control and applies the requested synced motion without retaining a carry edge");
            h.assertTrue(EntityCarryService.hurl(p, velocity) == null, "A repeated hurl cannot act on an already released recipient");
            held.invulnerableTime = 0;
            held.hurt(held.damageSources().generic(), 2);
            h.assertTrue(held.getHealth() < health, "Releasing the player must restore ordinary damage vulnerability too");
            h.succeed();
        } finally { cleanup(p, held); }
    }

    @GameTest(template = "rewind_empty", batch = "carry.player_reload")
    public static void reconnectingAPlayerFromACarrySnapshotRestoresOriginalProtectionFlags(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        ServerPlayer held = player(h, p.position().add(0, 0, 2));
        Flags before = flags(held);
        ServerPlayer copy = null;
        try {
            h.assertTrue(EntityCarryService.pickUp(p, held), "The reconnect fixture must begin with a carried player");
            CompoundTag saved = held.saveWithoutId(new CompoundTag());
            EntityCarryService.release(p);
            h.getLevel().removePlayerImmediately(held, Entity.RemovalReason.DISCARDED);
            copy = unaddedPlayer(h, new GameProfile(held.getUUID(), "carry-reload"));
            copy.load(saved);
            EntityCarryService.recover(copy);
            assertRestored(h, copy, before);
            h.assertTrue(!EntityCarryService.isCarrying(p), "Recovery cannot recreate an old carry ownership edge");
            h.succeed();
        } finally {
            if (copy != null) { copy.discard(); TELEPORT_PACKETS.remove(copy.getUUID()); }
            cleanup(p, held);
        }
    }

    @GameTest(template = "rewind_empty", batch = "carry.orphan_save")
    public static void savedCarryFlagsCannotMakeAReloadedMobPermanentlyInvulnerable(GameTestHelper h) {
        ServerPlayer p = player(h, origin(h));
        Mob target = mob(h, p.position().add(0, 0, 2));
        Flags before = flags(target);
        Entity restored = null;
        try {
            h.assertTrue(EntityCarryService.pickUp(p, target), "The saved mob fixture must begin carried");
            CompoundTag saved = target.saveWithoutId(new CompoundTag());
            saved.putString("id", "minecraft:villager");
            EntityCarryService.release(p); target.discard();
            restored = EntityType.loadEntityRecursive(saved, h.getLevel(), entity -> entity);
            h.assertTrue(restored != null && h.getLevel().addFreshEntity(restored), "The saved recipient must load as a real world entity");
            assertRestored(h, restored, before);
            h.succeed();
        } finally { if (restored != null) restored.discard(); cleanup(p, target); }
    }
}
