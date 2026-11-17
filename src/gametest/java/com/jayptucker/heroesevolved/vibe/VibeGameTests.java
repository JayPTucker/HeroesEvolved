package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.events.EntityCarryEvents;
import com.jayptucker.heroesevolved.grid.GridGameTests;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VibeGameTests {
    private static ServerPlayer viber(GameTestHelper h) {
        ServerPlayer player = GridGameTests.player(h, ModAbilities.VIBE_ID, 5);
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 2))));
        player.setYRot(0);
        player.setXRot(0);
        player.setOnGround(true);
        return player;
    }

    private static Pillager target(GameTestHelper h, ServerPlayer player, double x, double z) {
        Pillager mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.setPos(player.position().add(x, 0, z));
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = "rewind_empty", batch = "vibe.combat")
    public static void vibingMarksARecordedAggressorAndPalmBlastRespectsCover(GameTestHelper h) {
        ServerPlayer p = viber(h);
        Pillager attacker = target(h, p, 0, 4);
        Pillager witness = target(h, p, 0, 2);
        Pillager covered = target(h, p, 0, 8);
        h.setBlock(new BlockPos(5, 2, 8), Blocks.STONE);
        h.setBlock(new BlockPos(5, 3, 8), Blocks.STONE);
        witness.hurt(h.getLevel().damageSources().mobAttack(attacker), 2);
        h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false) == AbilityUseResult.SUCCESS,
                "Read the Vibe must cast from its normal key");
        h.assertTrue(VibeSight.marked(p, attacker), "The damage event must record and reveal the attacker behind the witness");
        h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                "Vibe Blast must queue");
        h.assertTrue(attacker.getHealth() == 24, "The palm has a windup");
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(attacker.getHealth() < 14, "The replay mark amplifies damage");
                h.assertTrue(covered.getHealth() == 24, "A solid wall stops the wave");
                h.succeed();
            } finally {
                VibeService.forget(p);
                attacker.discard();
                witness.discard();
                covered.discard();
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.guard")
    public static void guardCatchesArrowsAndFastBulletsAndReturnFireIsFree(GameTestHelper h) {
        ServerPlayer p = viber(h);
        Pillager attacker = target(h, p, 0, 6);
        Arrow arrow = EntityType.ARROW.create(h.getLevel());
        var bullet = PowerWeapons.testBullet(h.getLevel(), 0);
        try {
            h.assertTrue(bullet != null, "TaCZ provides a live bullet");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUATERNARY, false) == AbilityUseResult.SUCCESS,
                    "Guard must open from its normal key");
            Vec3 end = p.position().add(0, 1.23, 0);
            Vec3 start = end.add(0, 0, 6);
            arrow.setOwner(p);
            h.assertFalse(VibeService.intercept(h.getLevel(), arrow, start, end), "The caster's own shot can pass");
            arrow.setOwner(attacker);
            h.assertFalse(VibeService.intercept(h.getLevel(), arrow, start.add(3, 0, 0), end.add(3, 0, 0)),
                    "The shot must cross the visible aperture");
            h.assertTrue(VibeService.intercept(h.getLevel(), arrow, start, end) && arrow.isRemoved(),
                    "The guard catches the arrow on a swept crossing");
            h.assertTrue(VibeService.intercept(h.getLevel(), bullet, start, end) && bullet.isRemoved(),
                    "The guard catches a TaCZ round even if neither endpoint is inside");
            h.assertTrue(EvolutionTrials.progress(p, "vibe") == 2, "Each hostile interception advances the Evolved trial");
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUATERNARY, false) == AbilityUseResult.SUCCESS
                    && PlayerEnergyService.getEnergy(p) == energy,
                    "Return Fire spends the stored impacts without charging stamina or waiting for cooldown");
        } catch (RuntimeException failure) {
            attacker.discard();
            VibeService.forget(p);
            throw failure;
        } finally {
            if (bullet != null) bullet.discard();
            arrow.discard();
        }
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(attacker.getHealth() < 24, "The return wave damages the attacker as it reaches them");
                h.succeed();
            } finally {
                attacker.discard();
                VibeService.forget(p);
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.breach")
    public static void breachCrossesBothWaysAndClosesForFree(GameTestHelper h) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "vibe-traveler")) {
            @Override public boolean teleportTo(ServerLevel world, double x, double y, double z,
                                                Set<RelativeMovement> relative, float yaw, float pitch) {
                if (world != serverLevel()) return false;
                absMoveTo(x, y, z, yaw, pitch);
                return true;
            }
        };
        equip(h, p);
        h.getLevel().addNewPlayer(p);
        Vec3 start = p.position();
        var pig = EntityType.PIG.create(h.getLevel());
        pig.setPos(start.add(1.5, 0, .5));
        h.getLevel().addFreshEntity(pig);
        Runnable cleanup = () -> {
            EntityCarryService.release(p);
            pig.discard();
            clean(p);
        };
        p.setXRot(13);
        try {
            p.setShiftKeyDown(true);
            var pickup = new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, pig);
            EntityCarryEvents.onEntityInteract(pickup);
            p.setShiftKeyDown(false);
            h.assertTrue(pickup.isCanceled() && EntityCarryService.isBeingCarried(pig), "Sneak and right-click takes the pig in hand");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, true) == AbilityUseResult.SUCCESS,
                    "The carrier can open a linked breach while holding the pig");
        } catch (RuntimeException | AssertionError failure) {
            cleanup.run();
            throw failure;
        }
        var anchor = (CarryAnchorEntity) pig.getVehicle();
        h.runAfterDelay(12, () -> p.setPos(start.add(0, 0, 2.35)));
        h.runAfterDelay(14, () -> {
            try {
                h.assertTrue(p.position().distanceToSqr(start) > 25, "Walking through the entry sends the player to the exit");
                h.assertTrue(pig.getVehicle() == anchor && anchor.carrier() == p
                        && pig.position().distanceToSqr(anchor.seatPosition(p)) < .01, "The same carry comes through with the player");
                p.setPos(p.position().subtract(0, 0, 1.48));
            } catch (RuntimeException | AssertionError failure) {
                cleanup.run();
                throw failure;
            }
        });
        h.runAfterDelay(17, () -> {
            try {
                h.assertTrue(p.position().distanceToSqr(start) < 14, "Crossing the other mouth sends the player back");
                h.assertTrue(EntityCarryService.isBeingCarried(pig) && pig.position().distanceToSqr(p.position()) < 2,
                        "The pig is still held after the return trip");
                int energy = PlayerEnergyService.getEnergy(p);
                h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS
                        && PlayerEnergyService.getEnergy(p) == energy, "A second press closes the pair before cooldown and energy checks");
                h.succeed();
            } finally { cleanup.run(); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.blocked")
    public static void blockedEntryCostsNothing(GameTestHelper h) {
        ServerPlayer p = viber(h);
        try {
            h.setBlock(new BlockPos(5, 3, 4), Blocks.STONE);
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, true) == AbilityUseResult.ACTIVATION_REJECTED
                    && PlayerEnergyService.getEnergy(p) == energy, "A mouth inside a wall must reject the cast without spending stamina");
            h.succeed();
        } finally { VibeService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "vibe.lock")
    public static void frequencyLockWarnsBeforeDamagingAndSlowingAnEnemy(GameTestHelper h) {
        ServerPlayer p = viber(h);
        p.setXRot(18);
        Pillager caught = target(h, p, 0, 5);
        Pillager outside = target(h, p, 5.7, 0);
        h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUINARY, false) == AbilityUseResult.SUCCESS,
                "Frequency Lock casts from the normal slot");
        h.runAfterDelay(VibeGeometry.LOCK_WARNING_TICKS - 2, () -> {
            try {
                h.assertTrue(caught.getHealth() == 24 && !caught.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "Enemies have time to leave the telegraph");
            } catch (RuntimeException failure) {
                VibeService.forget(p);
                caught.discard();
                outside.discard();
                throw failure;
            }
        });
        h.runAfterDelay(VibeGeometry.LOCK_WARNING_TICKS + 2, () -> {
            try {
                h.assertTrue(caught.getHealth() < 24 && caught.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "The collapse damages and slows the caught enemy");
                h.assertTrue(outside.getHealth() == 24 && !p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "The collapse leaves enemies outside its radius and the caster alone");
                h.succeed();
            } finally {
                VibeService.forget(p);
                caught.discard();
                outside.discard();
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.world", timeoutTicks = 150)
    public static void aFrequencyBreachCrossesDimensionsAndKeepsItsWayBack(GameTestHelper h) {
        ServerLevel origin = h.getLevel();
        ServerLevel nether = origin.getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "Test requires a Nether");
        BlockPos anchor = new BlockPos(8, 128, 8);
        ChunkPos chunk = new ChunkPos(anchor);
        boolean wasForced = nether.getForcedChunks().contains(chunk.toLong());
        nether.setChunkForced(chunk.x, chunk.z, true);
        // Entity ticking needs full chunks in a two-chunk radius. Load them before starting the test's tick budget.
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) nether.getChunk(chunk.x + x, chunk.z + z);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 3; z++) {
                nether.setBlockAndUpdate(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y < 3; y++) nether.setBlockAndUpdate(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }

        var profile = new GameProfile(UUID.randomUUID(), "vibe-dimension");
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        var player = new ServerPlayer(origin.getServer(), origin, profile, ClientInformation.createDefault());
        var cargo = EntityType.PIG.create(origin);
        Runnable cleanup = () -> {
            EntityCarryService.release(player);
            Entity inNether = nether.getEntity(cargo.getUUID());
            Entity inOrigin = origin.getEntity(cargo.getUUID());
            if (inNether != null) inNether.discard();
            if (inOrigin != null) inOrigin.discard();
            clean(player);
            channel.close();
            if (!wasForced) nether.setChunkForced(chunk.x, chunk.z, false);
        };
        player.connection = new ServerGamePacketListenerImpl(player.getServer(), connection, player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) { }
        };
        equip(h, player);
        origin.addNewPlayer(player);
        Vec3 start = player.position();
        Vec3 attuned = Vec3.atBottomCenterOf(anchor);
        player.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.VIBE_ID.toString(),
                new SkillTreeData.Build(15, Set.of("vibe_1_0", "vibe_1_1", "vibe_world_frequency"), Map.of())));
        try {
            player.teleportTo(nether, attuned.x, attuned.y, attuned.z, 0, 0);
            player.setOnGround(true);
            h.assertTrue(VibeFrequencies.mark(player, 0), "A frequency tunes in the Nether");
            player.teleportTo(origin, start.x, start.y, start.z, 0, 0);
        } catch (RuntimeException | AssertionError failure) {
            cleanup.run();
            throw failure;
        }
        // GameTest ticks can run much faster than chunk generation. Give the Nether fixture time to finish first.
        h.runAfterDelay(140, cleanup);
        var travel = h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(nether.isPositionEntityTicking(anchor), "The Nether fixture is ticking"))
                .thenExecute(() -> {
                    VibeFrequencies.choose(player, com.jayptucker.heroesevolved.network.FrequencyChoicePayload.BREACH, 0, "");
                    h.assertTrue(VibeBreaches.kind(player) == VibeBreaches.Kind.FREQUENCY,
                            "With World Frequency, picking the Nether frequency opens a breach there");
                })
                .thenWaitUntil(() -> h.assertTrue(VibeBreaches.view(player, 0) != null, "Both mouths are open"));
        travel.thenExecute(() -> {
            // Use a real TaCZ entity so its runtime-only round data must make the same dimension change as the shot.
            Projectile round = (Projectile) PowerWeapons.testBullet(origin, 2.75F);
            round.setOwner(player);
            round.setPos(start.add(0, 1.2, .7));
            round.setDeltaMovement(0, 0, 18);
            origin.addFreshEntity(round);
            try {
                h.assertTrue(BreachProjectiles.beforeTick(origin, round), "TaCZ rounds can cross a dimension link");
                Entity exit = nether.getEntity(round.getUUID());
                h.assertTrue(round.isRemoved() && exit instanceof Projectile && ((Projectile) exit).getOwner() == player
                        && exit.getDeltaMovement().distanceToSqr(new Vec3(0, 0, 18)) < 1e-8,
                        "One round emerges with its original shooter and full speed: removed=" + round.isRemoved()
                                + ", exit=" + exit + (exit instanceof Projectile shot
                                ? ", owner=" + shot.getOwner() + ", motion=" + shot.getDeltaMovement() : ""));
                float[] data = PowerWeapons.bulletExplosion(exit);
                h.assertTrue(data != null && data[0] == 2.75F, "The bullet keeps its explosive ammo attributes");
                cargo.setPos(start.add(1.5, 0, .5));
                origin.addFreshEntity(cargo);
                h.assertTrue(EntityCarryService.pickUp(player, cargo, EntityCarryService.ORDINARY_REACH),
                        "Pick up a mob before crossing the dimension link");
                player.setPos(start.add(0, 0, 2.3));
            } catch (RuntimeException | AssertionError failure) {
                cleanup.run();
                throw failure;
            } finally {
                round.discard();
                Entity exit = nether.getEntity(round.getUUID());
                if (exit != null) exit.discard();
            }
        });
        travel.thenExecuteAfter(3, () -> {
            try {
                h.assertTrue(player.serverLevel() == nether && player.position().distanceToSqr(attuned) < 3,
                        "A real player crosses to the saved world and leaves the destination mouth");
                Entity carried = nether.getEntity(cargo.getUUID());
                h.assertTrue(origin.getEntity(cargo.getUUID()) == null && carried != null
                        && EntityCarryService.isBeingCarried(carried), "The held mob has one copy and remains held in the Nether");
                player.setPos(player.position().subtract(0, 0, 1.45));
            } catch (RuntimeException | AssertionError failure) {
                cleanup.run();
                throw failure;
            }
        });
        travel.thenExecuteAfter(5, () -> {
            try {
                h.assertTrue(player.serverLevel() == origin && player.position().distanceToSqr(start) < 14,
                        "The same portal stays open after dimension change and returns the traveler");
                Entity returned = origin.getEntity(cargo.getUUID());
                h.assertTrue(nether.getEntity(cargo.getUUID()) == null && returned != null
                        && EntityCarryService.isBeingCarried(returned), "The held mob returns without duplication");
                EntityCarryService.release(player);
                h.assertTrue(!returned.isNoGravity() && !returned.noPhysics && !returned.isInvulnerable(),
                        "Releasing cargo restores its original flags after both dimension transitions");
                h.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    private static void equip(GameTestHelper h, ServerPlayer player) {
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.VIBE_ID).unlock(ModAbilities.VIBE_ID));
        PlayerProgressionService.setLevel(player, 5);
        PlayerEnergyService.restore(player, 999);
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 2))));
        player.setYRot(0);
        player.setOnGround(true);
    }

    private static void clean(ServerPlayer p) {
        VibeService.forget(p);
        p.serverLevel().removePlayerImmediately(p, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }
}
