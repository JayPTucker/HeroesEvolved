package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.ability.types.FlightAbility;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server behavior for the airspeed loop, release-controlled launch and telegraphed combat moves. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class FlightReworkGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        return setupPlayer(h, new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "flight-rework")));
    }

    private static ServerPlayer setupPlayer(GameTestHelper h, ServerPlayer p) {
        attachChannel(p);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 40, 3))));
        p.setYRot(0); p.setXRot(0); p.setOnGround(false);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.FLIGHT_ID).unlock(ModAbilities.FLIGHT_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        FlightService.toggleFlight(p);
        return p;
    }

    private static void attachChannel(ServerPlayer player) {
        try {
            Connection connection = player.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void forget(ServerPlayer p) {
        PowerRules.rewinding(p, false);
        DiveBombService.forget(p);
        FlightService.stopForPowerRemoval(p);
        FlightDynamicsService.forget(p);
    }

    private static LivingEntity target(GameTestHelper h, Vec3 at) {
        var target = EntityType.PILLAGER.create(h.getLevel());
        target.setNoAi(true); target.setPersistenceRequired();
        target.moveTo(at.x, at.y, at.z, 0, 0);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    private static void flyFast(ServerPlayer p, int ticks) {
        FlightService.setForwardInput(p, true);
        for (int i = 0; i < 4; i++) FlightService.changeSpeed(p, 1);
        for (int i = 0; i < ticks; i++) {
            p.setDeltaMovement(new Vec3(0, 0, 1.5));
            p.setPos(p.position().add(0, 0, 1.5));
            FlightDynamicsService.tick(p);
        }
    }

    @GameTest(template = "rewind_empty", batch = "flight.momentum")
    public static void momentumRequiresActualAirspeedAndIsSpentOnce(GameTestHelper h) {
        ServerPlayer p = player(h);
        try {
            FlightService.setForwardInput(p, true);
            FlightService.changeSpeed(p, 1);
            for (int i = 0; i < 30; i++) {
                p.setDeltaMovement(Vec3.ZERO);
                FlightDynamicsService.tick(p);
            }
            h.assertTrue(FlightDynamicsService.momentum(p) == 0,
                    "An input packet without actual movement must not manufacture Momentum");
            flyFast(p, 180);
            h.assertTrue(FlightDynamicsService.momentum(p) > 50 && FlightDynamicsService.momentum(p) <= 100,
                    "Real forward flight builds a bounded Momentum reserve");
            FlightDynamicsService.pressSkyburst(p);
            for (int i = 0; i < 20; i++) FlightDynamicsService.tick(p);
            h.assertTrue(FlightDynamicsService.isCharging(p), "Holding Skyburst prepares a slingshot");
            h.assertTrue(p.getDeltaMovement().length() < .1, "Charging brakes the flier to a controlled hover");
            p.setYRot(90);
            FlightDynamicsService.releaseSkyburst(p);
            h.assertTrue(!FlightDynamicsService.isCharging(p) && FlightDynamicsService.momentum(p) == 0,
                    "Releasing spends the stored Momentum and ends the charge");
            Vec3 velocity = p.getDeltaMovement();
            h.assertTrue(velocity.x < -.5 && Math.abs(velocity.z) < .1,
                    "The released slingshot follows the new aim, not the old flight direction");
            FlightDynamicsService.releaseSkyburst(p);
            h.assertTrue(p.getDeltaMovement().equals(velocity), "Duplicate release packets must not create a second burst");
            h.succeed();
        } finally { forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.burst_cruise")
    public static void tappingSkyburstKeepsCruiseSettingsAndForwardReleaseBrakes(GameTestHelper h) {
        ServerPlayer p = player(h);
        try {
            flyFast(p, 5);
            FlightDynamicsService.pressSkyburst(p);
            FlightDynamicsService.releaseSkyburst(p);
            PlayerFlightData flight = p.getData(ModDataAttachments.PLAYER_FLIGHT.get());
            h.assertTrue(flight.speedStep() == 4 && flight.movingForward(),
                    "Tap Skyburst must preserve the selected speed and held Forward input");
            for (int i = 0; i < 32 && FlightDynamicsService.isBursting(p); i++) FlightDynamicsService.tick(p);
            FlightService.tick(p);
            double speed = p.getDeltaMovement().length();
            h.assertTrue(speed > 1 && p.getData(ModDataAttachments.PLAYER_FLIGHT.get()).visualPoseActive(),
                    "Cruise resumes when the burst ends");
            FlightService.setForwardInput(p, false);
            h.assertTrue(p.getDeltaMovement().length() < speed * .5,
                    "Releasing Forward must brake even at the highest selected speed");
            h.succeed();
        } finally { forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.skyburst_dispatch")
    public static void skyburstKeyDispatchPaysOnceAndCancelBypassesCooldown(GameTestHelper h) {
        ServerPlayer p = player(h);
        try {
            int initial = PlayerEnergyService.getEnergy(p);
            var action = new FlightAbility().actions().get(AbilitySlot.SECONDARY);
            var id = action.definition().id();
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS,
                    "The assigned Skyburst key starts its held action");
            int paid = PlayerEnergyService.getEnergy(p);
            h.assertTrue(paid < initial && FlightDynamicsService.isCharging(p), "Pressing pays once and begins gathering wind");
            for (int i = 0; i < 20; i++) FlightDynamicsService.tick(p);
            AbilityUseService.releaseAssignedAction(p, AbilitySlot.SECONDARY);
            h.assertTrue(!FlightDynamicsService.isCharging(p) && PlayerEnergyService.getEnergy(p) == paid,
                    "The release packet fires the maneuver without paying stamina a second time");
            h.assertTrue(CooldownService.isOnCooldown(p, id), "Release keeps the cooldown started by the press");
            CooldownService.clearAll(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS,
                    "A new ready Skyburst begins another charge");
            int beforeCancel = PlayerEnergyService.getEnergy(p);
            long cooldown = CooldownService.getExpirationGameTime(p, id);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.ON_COOLDOWN,
                    "Repeated press packets cannot buy or begin another charge during cooldown");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, true) == AbilityUseResult.SUCCESS,
                    "Shift + Skyburst can cancel a charge even while its cooldown is active");
            h.assertTrue(!FlightDynamicsService.isCharging(p) && PlayerEnergyService.getEnergy(p) == beforeCancel
                            && CooldownService.getExpirationGameTime(p, id) == cooldown,
                    "Canceling neither charges again nor restarts or removes the cooldown");
            h.succeed();
        } finally { forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.skyburst_release")
    public static void skyburstTapAndLostReleaseBothFinishTheManeuver(GameTestHelper h) {
        ServerPlayer p = player(h);
        try {
            FlightDynamicsService.pressSkyburst(p);
            FlightDynamicsService.releaseSkyburst(p);
            h.assertTrue(!FlightDynamicsService.isCharging(p) && p.getDeltaMovement().length() > 1,
                    "A tap immediately becomes an aerial dash");
            FlightDynamicsService.pressSkyburst(p);
            for (int i = 0; i < 41; i++) FlightDynamicsService.tick(p);
            h.assertTrue(!FlightDynamicsService.isCharging(p) && p.getDeltaMovement().length() > 1,
                    "The maximum hold releases automatically so a lost release packet cannot strand a hovering player");
            h.succeed();
        } finally { forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.skyburst_travel")
    public static void skyburstSustainsThrustAndStopsAtWalls(GameTestHelper h) {
        ServerPlayer p = player(h);
        Vec3 start = p.position();
        BlockPos wall = BlockPos.containing(start).south(4);
        try {
            for (int z = 0; z <= 224; z += 16) h.getLevel().getChunkAt(BlockPos.containing(start).south(z));
            FlightDynamicsService.pressSkyburst(p);
            FlightDynamicsService.releaseSkyburst(p);
            double tapSpeed = p.getDeltaMovement().length();
            double tapRange = travelSkyburst(p);
            h.assertTrue(tapSpeed > 6 && tapRange > 85, "A tap must sustain its high-speed launch in clear air");

            p.setPos(start);
            FlightDynamicsService.addMomentum(p, 100);
            FlightDynamicsService.pressSkyburst(p);
            for (int i = 0; i < FlightDynamicsService.SKYBURST_FULL_CHARGE_TICKS; i++) FlightDynamicsService.tick(p);
            FlightDynamicsService.releaseSkyburst(p);
            h.assertTrue(p.getDeltaMovement().length() > tapSpeed * 1.4,
                    "Full charge and Momentum must give a much faster launch");
            h.assertTrue(travelSkyburst(p) > tapRange * 1.7, "A fully powered launch must carry much farther");

            p.setPos(start);
            h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
            FlightDynamicsService.pressSkyburst(p);
            FlightDynamicsService.releaseSkyburst(p);
            double blockedRange = travelSkyburst(p);
            h.assertTrue(blockedRange > 2 && p.getBoundingBox().maxZ <= wall.getZ(),
                    "The high-speed sweep must stop the whole player at a wall within the first impulse");
            h.succeed();
        } finally {
            h.getLevel().removeBlock(wall, false);
            h.getLevel().removeBlock(wall.above(), false);
            forget(p);
        }
    }

    private static double travelSkyburst(ServerPlayer p) {
        Vec3 start = p.position();
        for (int i = 0; i < 32 && FlightDynamicsService.isBursting(p); i++) {
            p.move(MoverType.SELF, p.getDeltaMovement());
            FlightDynamicsService.tick(p);
        }
        return p.position().distanceTo(start);
    }

    @GameTest(template = "rewind_empty", batch = "flight.skyburst_witnesses")
    public static void skyburstBoomCarriesFartherThanItsTremor(GameTestHelper h) {
        ServerPlayer source = player(h);
        List<ServerPlayer> online = onlinePlayers(source.getServer().getPlayerList());
        var close = witness(h, source.position().add(8, 0, 0), online);
        var nearby = witness(h, source.position().add(48, 0, 0), online);
        var distant = witness(h, source.position().add(155, 0, 0), online);
        var outside = witness(h, source.position().add(193, 0, 0), online);
        try {
            FlightDynamicsService.pressSkyburst(source);
            FlightDynamicsService.releaseSkyburst(source);
            FlightDynamicsService.releaseSkyburst(source);
            h.assertTrue(close.booms() == 1 && nearby.booms() == 1 && distant.booms() == 1 && outside.booms() == 0,
                    "Skyburst's positional boom must reach distant players once, with a 192-block cutoff");
            h.assertTrue(close.shake() > nearby.shake() && nearby.shake() > 0,
                    "All players in range receive a tremor that gets weaker farther from the launch");
            h.assertTrue(distant.shake() == 0 && outside.shake() == 0,
                    "The tremor must stay local even when the sound can still be heard");
            h.succeed();
        } finally {
            for (var witness : List.of(close, nearby, distant, outside)) {
                online.remove(witness.player);
                h.getLevel().removePlayerImmediately(witness.player, Entity.RemovalReason.DISCARDED);
            }
            forget(source);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayers(PlayerList list) {
        try {
            Field players = PlayerList.class.getDeclaredField("players");
            players.setAccessible(true);
            return (List<ServerPlayer>) players.get(list);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static BurstWitness witness(GameTestHelper h, Vec3 position, List<ServerPlayer> online) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "burst-witness"));
        attachChannel(player);
        List<Packet<?>> packets = new ArrayList<>();
        player.connection = new ServerGamePacketListenerImpl(player.getServer(), player.connection.getConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) { packets.add(packet); }
        };
        player.setPos(position);
        h.getLevel().addNewPlayer(player);
        online.add(player);
        return new BurstWitness(player, packets);
    }

    private record BurstWitness(ServerPlayer player, List<Packet<?>> packets) {
        long booms() {
            return packets.stream().filter(packet -> packet instanceof ClientboundSoundPacket sound
                    && sound.getSound().value() == ModSounds.SKYBURST_BOOM.get()).count();
        }

        double shake() {
            return packets.stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                            && custom.payload() instanceof CameraShakePayload)
                    .mapToDouble(packet -> ((CameraShakePayload) ((ClientboundCustomPayloadPacket) packet).payload()).intensity())
                    .max().orElse(0);
        }
    }

    @GameTest(template = "rewind_empty", batch = "flight.fall_immunity")
    public static void unlockedFlightPreventsFallDamageWithoutAnActiveSession(GameTestHelper h) {
        ServerPlayer p = setupPlayer(h, new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "flight-landing")) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
        });
        try {
            Field spawnProtection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawnProtection.setAccessible(true);
            spawnProtection.setInt(p, 0);
            FlightService.toggleFlight(p);
            h.assertTrue(!FlightService.hasActiveSession(p), "The landing fixture must have flying switched off");
            float health = p.getHealth();
            p.causeFallDamage(30, 1, p.damageSources().fall());
            h.assertTrue(p.getHealth() == health, "An unlocked flier must survive a normal landing at full health");
            EclipseService.beginTotality(p.getServer());
            p.hurt(p.damageSources().stalagmite(), 8);
            h.assertTrue(p.getHealth() == health, "Direct fall-tag damage must be blocked even during an Eclipse");
            p.hurt(p.damageSources().generic(), 2);
            h.assertTrue(p.getHealth() < health, "Flight must still allow ordinary damage");

            p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.FLIGHT_ID));
            p.setHealth(health);
            p.invulnerableTime = 0;
            p.causeFallDamage(8, 1, p.damageSources().fall());
            h.assertTrue(p.getHealth() < health, "A dormant Flight assignment must not grant immunity");
            h.succeed();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        } finally { EclipseService.end(p.getServer()); forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.skyhook_attachment")
    public static void skyhookGrabsFromTheSlotAndKeepsItsCargoThroughSkyburst(GameTestHelper h) {
        ServerPlayer p = player(h);
        LivingEntity target = target(h, p.position().add(0, 0, 4));
        try {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUATERNARY, false) == AbilityUseResult.SUCCESS,
                    "The assigned Skyhook slot attaches the living entity in the crosshair");
            Vec3 seatOffset = target.position().subtract(p.position());
            h.assertTrue(target.getRootVehicle() == p && EntityCarryService.isBeingCarried(target),
                    "Skyhook must create a real attachment before returning success");
            FlightDynamicsService.pressSkyburst(p);
            FlightDynamicsService.releaseSkyburst(p);
            for (int i = 0; i < 4; i++) {
                p.move(MoverType.SELF, p.getDeltaMovement());
                FlightDynamicsService.tick(p);
                EntityCarryService.tick(p);
                h.assertTrue(target.getRootVehicle() == p && target.position().subtract(p.position()).distanceTo(seatOffset) < .01,
                        "The attachment must follow the carrier immediately through a high-speed move");
            }
            CooldownService.clearAll(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUATERNARY, false) == AbilityUseResult.SUCCESS,
                    "Pressing Skyhook again throws the attached cargo");
            h.assertTrue(!target.isPassenger() && !EntityCarryService.isBeingCarried(target)
                            && target.getDeltaMovement().z > 1,
                    "The throw must detach cargo and restore its physics before applying the impulse");
            h.succeed();
        } finally {
            EntityCarryService.release(p);
            target.discard();
            forget(p);
        }
    }

    @GameTest(template = "rewind_empty", batch = "flight.boost_protection")
    public static void boostBoomProtectsOwnerAlliesAndPassengersFromDamageAndImpulse(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.getLevel().addNewPlayer(p);
        LivingEntity ally = target(h, p.position().add(1.5, 0, 0));
        LivingEntity hostile = target(h, p.position().add(-1.5, 0, 0));
        LivingEntity passenger = target(h, p.position().add(0, 0, 1.5));
        var scoreboard = p.getScoreboard();
        var team = scoreboard.addPlayerTeam("wind-" + UUID.randomUUID().toString().substring(0, 8));
        scoreboard.addPlayerToTeam(p.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
        try {
            h.assertTrue(p.isAlliedTo(ally), "The ally fixture must share the flier's team");
            h.assertTrue(EntityCarryService.pickUp(p, passenger), "The rescue passenger must be carried before takeoff");
            EntityCarryService.tick(p);
            p.setDeltaMovement(Vec3.ZERO);
            ally.setDeltaMovement(Vec3.ZERO);
            hostile.setDeltaMovement(Vec3.ZERO);
            passenger.setDeltaMovement(Vec3.ZERO);
            float ownerHealth = p.getHealth();
            float allyHealth = ally.getHealth();
            float passengerHealth = passenger.getHealth();
            FlightService.setForwardInput(p, true);
            FlightService.changeSpeed(p, 1);
            h.assertTrue(p.getHealth() == ownerHealth && p.getDeltaMovement().equals(Vec3.ZERO),
                    "Entering fast Flight must not damage or knock its owner off course");
            h.assertTrue(ally.getHealth() == allyHealth && ally.getDeltaMovement().equals(Vec3.ZERO),
                    "A nearby teammate is protected from both the boost damage and its separate knockback path");
            h.assertTrue(passenger.getHealth() == passengerHealth && passenger.getDeltaMovement().equals(Vec3.ZERO),
                    "The takeoff blast must not jolt or damage a carried rescue passenger");
            h.assertTrue(hostile.getHealth() < hostile.getMaxHealth() && hostile.getDeltaMovement().length() > .05,
                    "Protection must preserve the sonic boom's damage and impulse against nearby foes");
            h.succeed();
        } finally {
            EntityCarryService.release(p);
            scoreboard.removePlayerTeam(team);
            ally.discard(); hostile.discard(); passenger.discard();
            forget(p);
            h.getLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(template = "rewind_empty", batch = "flight.charge_input")
    public static void forwardHeartbeatsAndWheelCannotTriggerBoostDuringACharge(GameTestHelper h) {
        ServerPlayer p = player(h);
        try {
            FlightService.changeSpeed(p, 1);
            int selected = p.getData(ModDataAttachments.PLAYER_FLIGHT).speedStep();
            FlightDynamicsService.pressSkyburst(p);
            int stamina = PlayerEnergyService.getEnergy(p);
            for (int i = 0; i < 12; i++) {
                FlightService.setForwardInput(p, i % 2 == 0);
                FlightService.changeSpeed(p, 1);
                FlightDynamicsService.tick(p);
            }
            var flight = p.getData(ModDataAttachments.PLAYER_FLIGHT);
            h.assertTrue(FlightDynamicsService.isCharging(p) && !flight.visualPoseActive(),
                    "Forward heartbeats must leave the maneuver in its charging pose");
            h.assertTrue(flight.speedStep() == selected && p.getDeltaMovement().length() < .01,
                    "Wheel requests are ignored and do not override the controlled charging hover");
            h.assertTrue(PlayerEnergyService.getEnergy(p) == stamina && !CooldownService.isOnCooldown(p,
                            ResourceLocation.fromNamespaceAndPath("heroesevolved", "flight_sonic_boom")),
                    "Input heartbeats must not emit or charge for a cruise sonic boom during preparation");
            h.succeed();
        } finally { forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.cleanup")
    public static void endingFlightClearsMomentumAndPendingRelease(GameTestHelper h) {
        ServerPlayer p = player(h);
        try {
            flyFast(p, 80);
            h.assertTrue(FlightDynamicsService.momentum(p) > 0, "The fixture has built Momentum");
            FlightDynamicsService.pressSkyburst(p);
            FlightService.stopForPowerRemoval(p);
            h.assertTrue(!FlightDynamicsService.isCharging(p) && FlightDynamicsService.momentum(p) == 0,
                    "Power removal clears both Momentum and the held maneuver");
            p.setDeltaMovement(Vec3.ZERO);
            FlightDynamicsService.releaseSkyburst(p);
            h.assertTrue(p.getDeltaMovement().equals(Vec3.ZERO), "A late key release cannot restart removed Flight");
            h.succeed();
        } finally { forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.cancellation")
    public static void immobilizingASlingshotCancelsInsteadOfReleasingIt(GameTestHelper h) {
        ServerPlayer p = player(h);
        try {
            FlightDynamicsService.pressSkyburst(p);
            for (int i = 0; i < 16; i++) FlightDynamicsService.tick(p);
            PowerRules.rewinding(p, true);
            FlightDynamicsService.tick(p);
            h.assertTrue(!FlightDynamicsService.isCharging(p), "Immobilization cancels the held charge immediately");
            p.setDeltaMovement(Vec3.ZERO);
            PowerRules.rewinding(p, false);
            FlightDynamicsService.releaseSkyburst(p);
            h.assertTrue(p.getDeltaMovement().equals(Vec3.ZERO), "Ending immobilization cannot release an old charge");
            h.succeed();
        } finally { forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.dive_windup")
    public static void diveNeedsAirspaceAndWarnsBeforeItHits(GameTestHelper h) {
        ServerPlayer p = player(h);
        LivingEntity target = target(h, p.position().add(1, 0, 0));
        try {
            p.setOnGround(true);
            h.assertTrue(!DiveBombService.canStart(p), "Dive cannot be used as a grounded instant area attack");
            p.setOnGround(false);
            p.setXRot(65);
            FlightDynamicsService.addMomentum(p, 60);
            DiveBombService.start(p, 5);
            h.assertTrue(DiveBombService.isDiving(p), "An airborne flier can begin the dive");
            h.assertTrue(!new FlightAbility().actions().get(AbilitySlot.SECONDARY)
                            .canUse(new AbilityUseContext(p, ModAbilities.FLIGHT_ID, 5, false)),
                    "Skyburst cannot overwrite an active dive");
            for (int i = 0; i < 8; i++) DiveBombService.tick(p);
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "Dive preparation never damages nearby foes");
            h.assertTrue(p.getDeltaMovement().length() < .2, "The preparation hangs visibly in the air");
            h.assertTrue(DiveBombService.cancel(p) && !DiveBombService.isDiving(p), "Shift + Dive cancels the preparation");
            h.assertTrue(FlightDynamicsService.momentum(p) == 60,
                    "Canceling Meteor Drop before the plunge keeps the banked airspeed");
            for (int i = 0; i < 16; i++) DiveBombService.tick(p);
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "A canceled dive cannot detonate later");
            h.succeed();
        } finally { target.discard(); forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.dive_occlusion")
    public static void sweptDiveHitsSolidGroundWithoutReachingThroughIt(GameTestHelper h) {
        ServerPlayer p = player(h);
        BlockPos floor = p.blockPosition().below(2);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
            h.getLevel().setBlock(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
        LivingEntity hidden = target(h, Vec3.atBottomCenterOf(floor.below(2)));
        try {
            p.setXRot(90);
            DiveBombService.start(p, 5);
            for (int i = 0; i < 40 && DiveBombService.isDiving(p); i++) {
                DiveBombService.tick(p);
                if (DiveBombService.isDiving(p)) p.move(MoverType.SELF, p.getDeltaMovement());
            }
            h.assertTrue(!DiveBombService.isDiving(p), "The swept probe must detect a floor in the plunge path");
            h.assertTrue(hidden.getHealth() == hidden.getMaxHealth(), "A solid floor blocks the shockwave from hurting foes beneath it");
            h.assertTrue(p.getDeltaMovement().length() < .1, "Contact ends the plunge instead of retaining downward thrust");
            h.succeed();
        } finally {
            hidden.discard(); forget(p);
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
                h.getLevel().setBlock(floor.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @GameTest(template = "rewind_empty", batch = "flight.dive_interrupt")
    public static void suppressedDiveEndsWithoutAnImpactOrStoredFallImmunity(GameTestHelper h) {
        ServerPlayer p = player(h);
        LivingEntity target = target(h, p.position().add(1, 0, 0));
        try {
            DiveBombService.start(p, 5);
            EclipseService.beginTotality(p.getServer());
            DiveBombService.tick(p);
            h.assertTrue(!DiveBombService.isDiving(p), "Suppression cancels an in-progress plunge");
            h.assertTrue(!DiveBombService.consumeCushion(p), "Suppression cannot leave indefinite Dive fall immunity");
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "Canceled dives do not explode during suppression");
            h.succeed();
        } finally { EclipseService.end(p.getServer()); target.discard(); forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.dive_cushion", timeoutTicks = 150)
    public static void missingADiveCannotBankPermanentFallImmunity(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setXRot(35);
        DiveBombService.start(p, 5);
        for (int i = 0; i < 90; i++) DiveBombService.tick(p);
        h.runAfterDelay(125, () -> {
            try {
                h.assertTrue(!DiveBombService.consumeCushion(p), "Dive cushioning must expire even when no fall event ever arrived");
                h.succeed();
            } finally { forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "flight.tempest")
    public static void tempestHasAWindupAndCanBeCanceledWithoutAFinale(GameTestHelper h) {
        ServerPlayer p = player(h);
        LivingEntity target = target(h, p.position().add(0, 0, 4));
        try {
            FlightService.startCyclone(p);
            for (int i = 0; i < 10; i++) FlightService.tick(p);
            h.assertTrue(FlightService.isCycloneActive(p), "Tempest gathers before the active orbit");
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "Its wind-up must give targets a chance to escape");
            h.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "A warning funnel cannot start pulling early");
            h.assertTrue(FlightService.cancelCyclone(p) && !FlightService.isCycloneActive(p), "Shift + Tempest ends the effect immediately");
            for (int i = 0; i < 130; i++) FlightService.tick(p);
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "Canceling Tempest must never execute its damaging finale");
            h.succeed();
        } finally { target.discard(); forget(p); }
    }
}
