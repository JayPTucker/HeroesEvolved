package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
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
import java.util.function.Consumer;

/** The ice is real terrain; its control, counterplay and recovery remain server-owned. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class CryoReworkGameTests {
    private static ServerPlayer player(GameTestHelper h, int x, boolean cryo) {
        return player(h, x, cryo, packet -> {});
    }

    private static ServerPlayer player(GameTestHelper h, int x, boolean cryo, Consumer<Packet<?>> packets) {
        for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)),
                h.absolutePos(new BlockPos(11, 1, 11)))) h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ice-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) { return !isAlliedTo(other); }
        };
        try {
            Connection c = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (c.channel() == null) channel.set(c, new EmbeddedChannel());
            p.connection = new ServerGamePacketListenerImpl(p.server, c, p,
                    CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) { packets.accept(packet); }
            };
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, 3))));
        p.setNoGravity(true); p.setYRot(0); p.setXRot(0); p.setGameMode(GameType.SURVIVAL);
        if (cryo) {
            p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                    .assignDormant(ModAbilities.CRYOKINESIS_ID).unlock(ModAbilities.CRYOKINESIS_ID));
            p.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                    p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.CRYOKINESIS_ID));
        }
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        h.getLevel().addNewPlayer(p);
        return p;
    }
    private static Mob enemy(GameTestHelper h, ServerPlayer p, double distance) {
        var t = EntityType.PILLAGER.create(h.getLevel());
        t.setNoAi(true); t.setNoGravity(true); t.setPersistenceRequired();
        t.setPos(p.position().add(0, 0, distance)); h.getLevel().addFreshEntity(t); return t;
    }
    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 delta = point.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }
    private static void clean(ServerPlayer... players) {
        for (ServerPlayer p : players) { CryoService.stopAll(p); p.discard(); }
    }

    private static void assign(ServerPlayer player, ResourceLocation power) {
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        player.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                player.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
    }

    @SuppressWarnings("unchecked")
    private static void registerReactor(ServerPlayer player, boolean register) {
        try {
            PlayerList list = player.server.getPlayerList();
            Field players = PlayerList.class.getDeclaredField("players");
            Field byId = PlayerList.class.getDeclaredField("playersByUUID");
            players.setAccessible(true);
            byId.setAccessible(true);
            var connected = (List<ServerPlayer>) players.get(list);
            var indexed = (Map<UUID, ServerPlayer>) byId.get(list);
            if (register) {
                connected.add(player);
                indexed.put(player.getUUID(), player);
            } else {
                connected.remove(player);
                indexed.remove(player.getUUID());
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void cleanReactor(ServerPlayer caster, ServerPlayer reactor) {
        RadiationService.stopAll(reactor);
        registerReactor(reactor, false);
        clean(caster, reactor);
    }
    private static String ownerState(ServerPlayer p) {
        return " alive=" + p.isAlive() + " removed=" + p.isRemoved()
                + " canAct=" + com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(p)
                + " authorized=" + CryoKitService.authorized(p) + " at=" + p.position()
                + " below=" + p.level().getBlockState(p.blockPosition().below())
                + " body=" + p.level().getBlockState(p.blockPosition());
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_stream")
    public static void heldIceBlastBuildsFrostAndReleaseStopsFeedingIt(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true); Mob t = enemy(h, p, 4);
        aim(p, t.getBoundingBox().getCenter());
        h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                "The primary key must start Ice Blast");
        int initial = CryoService.frostAmount(t);
        h.runAfterDelay(12, () -> {
            h.assertTrue(CryoService.frostAmount(t) > initial, "Holding swept cold must keep building real Frost after its first pulse");
            AbilityUseService.releaseAssignedAction(p, AbilitySlot.PRIMARY);
            int before = CryoService.frostAmount(t);
            h.runAfterDelay(8, () -> {
                try {
                    h.assertTrue(!CryoBlastService.isStreaming(p) && CryoService.frostAmount(t) <= before,
                            "Releasing the key ends the stream and its Frost application");
                    h.succeed();
                } finally { t.discard(); clean(p); }
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "cryo.rework_stream", timeoutTicks = 430)
    public static void heldIceBlastFreezesSolidsByHardness(GameTestHelper h) {
        ServerPlayer caster = player(h, 3, true);
        BlockPos stone = h.absolutePos(new BlockPos(3, 3, 6));
        BlockPos edge = stone.above();
        BlockPos corner = edge.east();
        BlockPos covered = stone.west();
        BlockPos obsidian = h.absolutePos(new BlockPos(5, 3, 6));
        BlockPos softerNeighbor = obsidian.east();
        var level = h.getLevel();
        for (BlockPos pos : List.of(stone, edge, corner, covered))
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(covered.north(), Blocks.BEDROCK.defaultBlockState());
        level.setBlockAndUpdate(obsidian, Blocks.OBSIDIAN.defaultBlockState());
        level.setBlockAndUpdate(softerNeighbor, Blocks.DIRT.defaultBlockState());
        aim(caster, Vec3.atCenterOf(stone));

        try {
            h.assertTrue(AbilityUseService.activateAssignedAction(caster, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                    "Ice Blast starts from its assigned key");
        } catch (RuntimeException e) {
            clean(caster);
            throw e;
        }
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(level.getBlockState(stone).is(Blocks.STONE), "A short hit does not freeze stone");
            } catch (RuntimeException e) {
                clean(caster);
                throw e;
            }
        });
        h.runAfterDelay(36, () -> {
            try {
                var frozen = level.getBlockState(stone);
                h.assertTrue(FrostConstructBlock.isFrost(frozen) && frozen.getDestroySpeed(level, stone) <= 1,
                        "Sustained cold turns the wall into brittle ice, including a block covered above");
                h.assertTrue(level.getBlockState(edge).is(Blocks.STONE) && level.getBlockState(corner).is(Blocks.STONE),
                        "The block under the aim freezes before the surrounding area");
            } catch (RuntimeException e) {
                clean(caster);
                throw e;
            }
        });
        h.runAfterDelay(52, () -> {
            try {
                h.assertTrue(FrostConstructBlock.isFrost(level.getBlockState(edge)) && level.getBlockState(corner).is(Blocks.STONE),
                        "The closest neighbor freezes before the corner");
            } catch (RuntimeException e) {
                clean(caster);
                throw e;
            }
        });
        h.runAfterDelay(65, () -> {
            try {
                h.assertTrue(FrostConstructBlock.isFrost(level.getBlockState(corner)), "Holding aim also freezes the corners");
                h.assertTrue(level.getBlockState(covered).is(Blocks.STONE), "Cold splash cannot reach through a covered face");
                h.assertTrue(caster.gameMode.destroyBlock(stone), "The brittle block can be broken by hand");
                aim(caster, Vec3.atCenterOf(obsidian));
            } catch (RuntimeException e) {
                clean(caster);
                throw e;
            }
        });
        h.runAfterDelay(125, () -> {
            try {
                h.assertTrue(level.getBlockState(obsidian).is(Blocks.OBSIDIAN), "Obsidian needs longer than stone");
                h.assertTrue(level.getBlockState(softerNeighbor).is(Blocks.DIRT),
                        "A softer neighbor cannot complete its freeze before the targeted block");
            } catch (RuntimeException e) {
                clean(caster);
                throw e;
            }
        });
        h.runAfterDelay(152, () -> {
            try {
                h.assertTrue(FrostConstructBlock.isFrost(level.getBlockState(obsidian))
                                && FrostConstructBlock.isFrost(level.getBlockState(softerNeighbor)),
                        "Sustained cold freezes the targeted obsidian and then its softer neighbor");
            } finally {
                clean(caster);
            }
        });
        h.runAfterDelay(405, () -> {
            h.assertTrue(level.getBlockState(stone).isAir(), "Shattered ice stays broken when the thaw timer expires");
            h.assertTrue(level.getBlockState(obsidian).is(Blocks.OBSIDIAN), "Unbroken ice thaws to its original block");
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "cryo.rework_stream", timeoutTicks = 180)
    public static void heldIceBlastTurnsWaterToIceAndLavaToObsidian(GameTestHelper h) {
        ServerPlayer caster = player(h, 3, true);
        BlockPos water = h.absolutePos(new BlockPos(3, 3, 6));
        BlockPos lava = h.absolutePos(new BlockPos(6, 3, 6));
        var level = h.getLevel();
        for (BlockPos pos : List.of(water, lava)) {
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            for (Direction side : Direction.Plane.HORIZONTAL)
                level.setBlockAndUpdate(pos.relative(side), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(lava, Blocks.LAVA.defaultBlockState());
        caster.setPos(caster.position().add(0, 3, 0));
        aim(caster, Vec3.atBottomCenterOf(water).add(0, .78, 0));
        try {
            h.assertTrue(CryoBlastService.start(caster, 1), "Ice Blast starts while aiming at the tank");
        } catch (RuntimeException e) {
            clean(caster);
            throw e;
        }

        h.runAfterDelay(16, () -> {
            try {
                h.assertTrue(level.getBlockState(water).is(Blocks.ICE), "Aimed cold turns water directly into ice");
                aim(caster, Vec3.atBottomCenterOf(lava).add(0, .78, 0));
            } catch (RuntimeException e) {
                clean(caster);
                throw e;
            }
        });
        h.runAfterDelay(50, () -> {
            try {
                h.assertTrue(level.getBlockState(lava).is(Blocks.OBSIDIAN), "Aimed cold hardens lava into obsidian");
            } catch (RuntimeException e) {
                clean(caster);
                throw e;
            }
        });
        h.runAfterDelay(130, () -> {
            try {
                h.assertTrue(level.getBlockState(lava).is(Blocks.OBSIDIAN), "Continued contact leaves the newly cooled lava solid");
                h.succeed();
            } finally {
                clean(caster);
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "cryo.rework_terrain_fade", timeoutTicks = 160)
    public static void partialColdGradesAcrossThePatchAndFades(GameTestHelper h) {
        ServerPlayer caster = player(h, 3, true);
        var level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(3, 3, 6));
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, 0), center.offset(1, 1, 0)))
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        BlockPos edge = center.above(), corner = center.above().east();
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(center).add(0, 0, -.5), Direction.NORTH, center, false);
        try {
            // Three shard hits: well short of the thirty that stone needs.
            for (int i = 0; i < 3; i++) TerrainFrost.projectile(level, hit, caster, false);
            double middle = TerrainFrost.progress(level, center), side = TerrainFrost.progress(level, edge),
                    far = TerrainFrost.progress(level, corner);
            h.assertTrue(middle > side && side > far && far > 0,
                    "Cold builds fastest where it hits, then the edges, then the corners: " + middle + ", " + side + ", " + far);
            h.assertTrue(level.getBlockState(center).is(Blocks.STONE), "A few hits leave stone standing");
        } catch (RuntimeException e) {
            clean(caster);
            throw e;
        }
        h.runAfterDelay(TerrainFrost.CONTACT_TIMEOUT + 5, () -> {
            try {
                h.assertTrue(TerrainFrost.progress(level, center) == 0 && TerrainFrost.progress(level, corner) == 0,
                        "Unfinished cold fades once the hits stop");
                h.assertTrue(level.getBlockState(center).is(Blocks.STONE) && level.getBlockState(edge).is(Blocks.STONE),
                        "Faded cold leaves the terrain as it was");
                // Starting again starts from nothing.
                TerrainFrost.projectile(level, hit, caster, false);
                h.assertTrue(TerrainFrost.progress(level, center) < .2, "Cold that faded does not come back");
                h.succeed();
            } finally {
                clean(caster);
            }
        });
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_reactor", timeoutTicks=740)
    public static void iceBlastPausesAndDefusesALateInvoluntaryDetonation(GameTestHelper h) {
        ServerPlayer caster = player(h, 3, true), reactor = player(h, 3, false);
        reactor.setPos(caster.position().add(0, 0, 4));
        assign(reactor, ModAbilities.RADIATION_ID);
        registerReactor(reactor, true);
        RadiationService.addMass(reactor, RadiationService.MAXIMUM_MASS);
        RadiationService.beginDetonation(reactor, 5, false);
        int start = RadiationService.visualState(reactor).remaining() - 12;
        float health = reactor.getHealth();
        // Four blocks from a meltdown near its end is inside its lethal near-field: the cryokinetic needs a suit.
        com.jayptucker.heroesevolved.radiation.MeltdownFieldGameTests.suit(caster, false);
        aim(caster, reactor.getBoundingBox().getCenter());
        PlayerProgressionService.setLevel(caster, 1);
        h.runAfterDelay(start, () -> {
            try {
                h.assertTrue(RadiationService.isDetonating(reactor), "The involuntary charge must be approaching detonation");
                h.assertTrue(AbilityUseService.activateAssignedAction(caster, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                        "A tier-one Ice Blast must be able to start the rescue");
            } catch (RuntimeException | AssertionError e) {
                cleanReactor(caster, reactor);
                throw e;
            }
        });
        h.runAfterDelay(start + 210, () -> {
            try {
                h.assertTrue(RadiationService.isDetonating(reactor) && RadiationService.mass(reactor) < 100,
                        "The aimed beam must keep the live charge on hold and still be cooling after ten seconds");
            } catch (RuntimeException | AssertionError e) {
                cleanReactor(caster, reactor);
                throw e;
            }
        });
        h.runAfterDelay(start + 248, () -> {
            try {
                h.assertTrue(!RadiationService.critical(reactor) && RadiationService.mass(reactor) <= 30,
                        "Twelve seconds of continuous contact must end the detonation and lower its Core");
                h.assertTrue(reactor.getHealth() == health && !CryoService.isFrozen(reactor),
                        "Cooling a reactor must be safe even in co-op");
                h.assertTrue(CryoBlastService.spendCharge(caster) && !CryoBlastService.spendCharge(caster),
                        "Absorbed Core must prime exactly one enhanced shatter");
                h.succeed();
            } finally { cleanReactor(caster, reactor); }
        });
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_pyro_quench")
    public static void solidFreezeCancelsPyroChannelsAndHeat(GameTestHelper h) {
        ServerPlayer caster = player(h, 3, true), pyro = player(h, 5, false);
        try {
            assign(pyro, ModAbilities.PYROKINESIS_ID);
            h.assertTrue(PyroService.ringOfFire(pyro, 5) && PyroService.startPalm(pyro, 5),
                    "A Pyro user must be able to maintain a storm and a breath channel");
            PyroService.addHeat(pyro, 85);
            h.assertTrue(CryoService.freezeSolid(pyro, 45, caster), "The Cryo shell must form around the Pyro user");
            h.assertTrue(PyroService.heat(pyro) == 0 && !PyroService.stopPalm(pyro)
                            && !PyroService.collapseRing(pyro, 5) && !PyroService.startPalm(pyro, 5),
                    "Freezing must immediately quench Heat and cancel active flames");
            h.succeed();
        } finally {
            PyroService.forget(pyro);
            clean(caster, pyro);
        }
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_fire_cover")
    public static void iceBlastExtinguishesExposedFireButRespectsCover(GameTestHelper h) {
        ServerPlayer caster = player(h, 3, true);
        BlockPos near = h.absolutePos(new BlockPos(3, 3, 5));
        BlockPos far = h.absolutePos(new BlockPos(3, 3, 8));
        var level = h.getLevel();
        level.setBlock(near.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(far.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(near.south(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(near, Blocks.FIRE.defaultBlockState(), 3);
        level.setBlock(far, Blocks.SOUL_FIRE.defaultBlockState(), 3);
        level.setBlock(far.below(), Blocks.SOUL_SOIL.defaultBlockState(), 3);
        aim(caster, Vec3.atCenterOf(near));
        CryoBlastService.start(caster, 1);
        h.runAfterDelay(10, () -> {
            try {
                h.assertTrue(!level.getBlockState(near).is(Blocks.FIRE) && level.getBlockState(far).is(Blocks.SOUL_FIRE),
                        "Ice Blast must put out exposed fire and stop at cover");
                h.succeed();
            } finally { clean(caster); }
        });
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_sculpt")
    public static void glacialSculptGrowsSolidCoverWithoutOccupyingItsCaster(GameTestHelper h) {
        List<SetPiecePayload> sprays = new ArrayList<>();
        ServerPlayer p = player(h, 3, true, packet -> {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof SetPiecePayload effect
                    && effect.piece() == Piece.CRYO_CAST.ordinal()) sprays.add(effect);
        });
        p.setXRot(42);
        h.runAfterDelay(15, () -> {
            try {
                var blocks = CryoKitService.constructBlocks(p);
                h.assertTrue(blocks.size() > 1 && blocks.stream().allMatch(pos -> FrostConstructBlock.isFrost(h.getLevel().getBlockState(pos))),
                        "Sculpt creates actual temporary solid ice, not only particles");
                h.assertTrue(blocks.stream().noneMatch(pos -> p.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))),
                        "Growing cover must leave the caster's body clear");
                h.assertTrue(sprays.size() > 1 && sprays.stream().allMatch(spray -> spray.entity() == p.getId()),
                        "The wall cast and its growing rows must send frost from the caster");
                SetPiecePayload first = sprays.getFirst();
                h.assertTrue(first.origin().distanceToSqr(p.getEyePosition()) < .01 && first.target().z > first.origin().z,
                        "The first frost spray must travel from the caster toward the aimed wall");
                h.assertTrue(sprays.stream().skip(1).anyMatch(spray -> spray.target().y > first.target().y),
                        "The frost spray must follow the ice up as the wall grows");
                h.succeed();
            } finally { clean(p); }
        });
        if (!CryoKitService.sculpt(p, 3, false)) {
            clean(p);
            h.fail("Sculpt must accept a clear aimed foundation");
        }
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_build_permission")
    public static void sculptHonorsBuildPermissions(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true);
        p.setGameMode(GameType.ADVENTURE); p.setXRot(42);
        CryoKitService.sculpt(p, 3, false);
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(CryoKitService.constructBlocks(p).stream().noneMatch(pos -> FrostConstructBlock.isFrost(h.getLevel().getBlockState(pos))),
                        "A player without build permission cannot grow terrain through Sculpt");
                h.succeed();
            } finally { clean(p); }
        });
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_reshape")
    public static void aimingAtOwnedIceReshapesItAndCleanupPreservesPlayerEdits(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true); p.setXRot(42);
        h.assertTrue(CryoKitService.sculpt(p, 3, false), "A first sculpt must establish solid cover");
        h.runAfterDelay(15, () -> {
            var wall = CryoKitService.constructBlocks(p);
            h.assertTrue(!wall.isEmpty(), "The first wall must finish growing");
            aim(p, Vec3.atCenterOf(wall.get(wall.size() / 2)));
            var aimed = h.getLevel().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),
                    p.getEyePosition().add(p.getLookAngle().scale(16)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            h.assertTrue(wall.contains(aimed.getBlockPos()), "The fixture must aim at owned ice before reshaping: aimed="
                    + aimed.getBlockPos() + " wanted=" + wall.get(wall.size() / 2) + " eye=" + p.getEyePosition()
                    + " look=" + p.getLookAngle());
            p.setGameMode(GameType.ADVENTURE);
            h.assertTrue(!CryoKitService.sculpt(p, 3, true)
                            && CryoKitService.constructCount(p) == 1
                            && wall.stream().allMatch(pos -> FrostConstructBlock.isFrost(h.getLevel().getBlockState(pos))),
                    "A denied reshape must leave the existing owned cover intact");
            p.setGameMode(GameType.SURVIVAL);
            h.assertTrue(CryoKitService.sculpt(p, 3, true), "Aiming at owned ice can reshape it into a ramp");
            h.runAfterDelay(20, () -> {
                try {
                    var ramp = CryoKitService.constructBlocks(p);
                    h.assertTrue(CryoKitService.constructCount(p) == 1 && !ramp.isEmpty(),
                            "Reshaping replaces the owned construct rather than duplicating its record: count="
                                    + CryoKitService.constructCount(p) + " cells=" + ramp.size() + ownerState(p));
                    h.assertTrue(ramp.stream().mapToInt(BlockPos::getY).max().orElse(0)
                                    > ramp.stream().mapToInt(BlockPos::getY).min().orElse(0),
                            "The new ramp has real rising collision cells");
                    BlockPos edited = ramp.getFirst();
                    h.getLevel().setBlock(edited, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                    CryoKitService.forget(p);
                    h.assertTrue(h.getLevel().getBlockState(edited).is(Blocks.GOLD_BLOCK),
                            "Cleanup preserves a block deliberately changed after ice placement");
                    h.assertTrue(ramp.stream().filter(pos -> !pos.equals(edited)).allMatch(pos -> h.getLevel().getBlockState(pos).isAir()),
                            "The remaining shaped ice restores its exact air foundation");
                    h.succeed();
                } finally { clean(p); }
            });
        });
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_reshape_dispatch")
    public static void anOwnedConstructCanBeReshapedDuringTheNewWallCooldown(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true); p.setXRot(42);
        h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS,
                "The actual secondary action must create a new wall");
        h.runAfterDelay(45, () -> {
            try {
                var wall = CryoKitService.constructBlocks(p);
                h.assertTrue(!wall.isEmpty(), "The cast must leave owned solid cover");
                aim(p, Vec3.atCenterOf(wall.get(wall.size() / 2)));
                h.assertTrue(CryoKitService.isAimingConstruct(p), "The follow-up must be aimed at owned ice");
                var actionId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "cryo_flash_freeze");
                h.assertTrue(CooldownService.isOnCooldown(p, actionId), "The initial new-wall cooldown must still be running");
                int before = PlayerEnergyService.getEnergy(p);
                var secondary = com.jayptucker.heroesevolved.ability.registry.AbilityRegistry.ABILITIES
                        .get(ModAbilities.CRYOKINESIS_ID).actions().get(AbilitySlot.SECONDARY);
                var context = new com.jayptucker.heroesevolved.ability.AbilityUseContext(p, ModAbilities.CRYOKINESIS_ID, 5, false);
                h.assertTrue(!secondary.deactivate(context) && PlayerEnergyService.getEnergy(p) == before
                                && CryoKitService.constructBlocks(p).equals(wall),
                        "Loadout cleanup cannot reshape or charge aimed ice through deactivate");
                h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS
                                && CryoKitService.constructCount(p) == 1,
                        "Aiming at prepared ice must reach reshape before the new-wall cooldown gate");
                h.assertTrue(PlayerEnergyService.getEnergy(p) == before - 8,
                        "A successful quick reshape charges exactly eight stamina");
                int after = PlayerEnergyService.getEnergy(p);
                AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false);
                h.assertTrue(PlayerEnergyService.getEnergy(p) == after,
                        "Repeating the key before the ice settles cannot charge another reshape");
                h.succeed();
            } finally { clean(p); }
        });
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_prison")
    public static void prisonUsesPreparedFrostAndASecondCastShattersIt(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true); Mob t = enemy(h, p, 4);
        aim(p, t.getBoundingBox().getCenter());
        try {
            h.assertTrue(CryoKitService.prison(p, 4), "An unprepared cast can lay down its initial Frost");
            h.assertTrue(!CryoService.isFrozen(t) && CryoService.frostAmount(t) > 0,
                    "A fresh target must first accumulate Frost rather than being instantly imprisoned");
            h.assertTrue(CryoKitService.prison(p, 4) && CryoService.isFrozen(t), "Prepared Frost forms a brittle prison");
            float before = t.getHealth();
            h.assertTrue(CryoKitService.shatterPrison(p) && !CryoService.isFrozen(t), "The deliberate follow-up consumes the shell");
            h.assertTrue(t.getHealth() < before && !CryoKitService.hasPrison(p), "Shatter damages its target and clears the stored prison");
            h.succeed();
        } finally { t.discard(); clean(p); }
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_prison_cover")
    public static void aStoredPrisonCannotBeShatteredThroughSolidCover(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true); Mob t = enemy(h, p, 4);
        aim(p, t.getBoundingBox().getCenter()); CryoService.chill(t, 60, p);
        BlockPos cover = p.blockPosition().offset(0, 0, 2);
        try {
            h.assertTrue(CryoKitService.prison(p, 4), "Prepared visible Frost must form a prison");
            float before = t.getHealth();
            for (int y = 0; y < 3; y++) h.getLevel().setBlock(cover.above(y), Blocks.STONE.defaultBlockState(), 3);
            CryoKitService.shatterPrison(p);
            h.assertTrue(CryoService.isFrozen(t) && t.getHealth() == before,
                    "Stored targeting must not let the follow-up shatter through newly placed cover");
            h.succeed();
        } finally {
            for (int y = 0; y < 3; y++) h.getLevel().removeBlock(cover.above(y), false);
            t.discard(); clean(p);
        }
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_prison_allies")
    public static void deliberateShatterLeavesNearbyAlliesAndCasterUnharmed(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true), friend = player(h, 6, false);
        Mob t = enemy(h, p, 2.5);
        friend.setPos(t.position().add(1, 0, 0));
        var board = p.getScoreboard();
        var team = board.addPlayerTeam("ice-" + UUID.randomUUID().toString().substring(0, 8));
        board.addPlayerToTeam(p.getScoreboardName(), team); board.addPlayerToTeam(friend.getScoreboardName(), team);
        try {
            aim(p, t.getBoundingBox().getCenter()); CryoService.chill(t, 60, p);
            h.assertTrue(CryoKitService.prison(p, 4), "The hostile target must be imprisoned");
            float casterHealth = p.getHealth(), friendHealth = friend.getHealth(), targetHealth = t.getHealth();
            CryoKitService.shatterPrison(p);
            h.assertTrue(t.getHealth() < targetHealth, "The prepared hostile target receives the fracture");
            h.assertTrue(p.getHealth() == casterHealth && friend.getHealth() == friendHealth
                            && CryoService.frostAmount(friend) == 0,
                    "The fracture does not damage or chill the caster or nearby allies");
            h.succeed();
        } finally { board.removePlayerTeam(team); t.discard(); clean(p, friend); }
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_struggle")
    public static void frozenPlayersCanCrackTheirShellButCannotSpamEscapePackets(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true), trapped = player(h, 6, false);
        h.assertTrue(CryoService.freezeSolid(trapped, 60, p), "The opposing player must be encased");
        h.assertTrue(CryoService.struggle(trapped), "A real first struggle press contributes a crack");
        h.assertTrue(!CryoService.struggle(trapped) && CryoService.isFrozen(trapped),
                "Repeated packets in the same tick cannot instantly break the shell");
        h.onEachTick(() -> { if (h.getLevel().getGameTime() % 4 == 0) CryoService.struggle(trapped); });
        h.runAfterDelay(44, () -> {
            try {
                h.assertTrue(!CryoService.isFrozen(trapped), "Repeated deliberate input can free a trapped player before expiry");
                h.succeed();
            } finally { clean(p, trapped); }
        });
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_heat_counter")
    public static void heatThawsAPlayerPrisonWithoutLeavingMovementLocked(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true), trapped = player(h, 6, false);
        try {
            CryoService.freezeSolid(trapped, 60, p);
            trapped.igniteForSeconds(2); CryoService.tick(h.getLevel().getServer());
            h.assertTrue(!CryoService.isFrozen(trapped), "Heat remains a counter to the ice shell");
            trapped.clearFire();
            h.assertTrue(!CryoService.shouldPauseMob(trapped), "Thaw must not leave a hidden simulation lock");
            h.succeed();
        } finally { clean(p, trapped); }
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.rework_whiteout")
    public static void whiteoutWarnsBeforeBuildingFrostAndCanCollapse(GameTestHelper h) {
        ServerPlayer p = player(h, 16, true);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(16, 2, 16))));
        Mob t = enemy(h, p, 4);
        p.setXRot(25);
        h.assertTrue(CryoKitService.whiteout(p, 5), "Whiteout accepts a visible location");
        h.runAfterDelay(10, () -> h.assertTrue(CryoKitService.hasWhiteout(p) && CryoService.frostAmount(t) == 0,
                "The growing storm gives a real wind-up before its cold takes hold"));
        h.runAfterDelay(35, () -> {
            try {
                h.assertTrue(CryoService.frostAmount(t) > 0 || CryoService.isFrozen(t),
                        "A mature storm builds Frost inside its region: active=" + CryoKitService.hasWhiteout(p) + ownerState(p));
                h.assertTrue(CryoKitService.endWhiteout(p, true) && !CryoKitService.hasWhiteout(p),
                        "The deliberate collapse ends the storm exactly once");
                h.succeed();
            } finally { t.discard(); clean(p); }
        });
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.rework_whiteout_cleanup")
    public static void losingThePowerRemovesWhiteoutAndItsOwnedSpires(GameTestHelper h) {
        ServerPlayer p = player(h, 16, true);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(16, 2, 16)))); p.setXRot(25);
        h.assertTrue(CryoKitService.whiteout(p, 5), "Whiteout must establish its prepared field");
        h.runAfterDelay(45, () -> {
            try {
                var spires = new java.util.ArrayList<BlockPos>();
                for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-13, 0, -13),
                        p.blockPosition().offset(13, 4, 18))) {
                    if (FrostConstructBlock.isFrost(h.getLevel().getBlockState(pos)) && TemporaryBlocks.isTemporary(h.getLevel(), pos))
                        spires.add(pos.immutable());
                }
                h.assertTrue(!spires.isEmpty(), "A mature Whiteout must grow real owned ice spires: active="
                        + CryoKitService.hasWhiteout(p) + ownerState(p));
                CryoService.stopAll(p);
                h.assertTrue(!CryoKitService.hasWhiteout(p)
                                && spires.stream().allMatch(pos -> h.getLevel().getBlockState(pos).isAir()),
                        "Power cleanup ends the field and removes its still-owned spires");
                h.succeed();
            } finally { clean(p); }
        });
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.rework_ride")
    public static void glacierRideUsesPhysicalIceAndRestoresItsMovementFlags(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true); p.setNoGravity(false); p.setOnGround(true);
        Vec3 start = p.position();
        h.assertTrue(CryoRideService.toggle(p, 5), "A grounded cryokinetic can step onto the ice ribbon" + ownerState(p));
        h.onEachTick(() -> {
            if (CryoRideService.isRiding(p)) {
                CryoRideService.input(p, 1, 0, false, false, false);
                CryoRideService.tick(p); // FakePlayer.tick() does not dispatch PlayerTickEvent.Post.
                p.move(MoverType.SELF, p.getDeltaMovement());
            }
        });
        h.runAfterDelay(16, () -> {
            try {
                h.assertTrue(p.position().distanceTo(start) > 1 && p.position().distanceTo(start) < 16 && p.getY() < start.y + 2,
                        "Skating produces movement along an actual ribbon without becoming free flight");
                h.assertTrue(CryoRideService.ribbonCells(p) > 1
                                && FrostConstructBlock.isFrost(h.getLevel().getBlockState(BlockPos.containing(p.position().add(0,-.08,0)))),
                        "The moving skater has actual solid ice beneath their feet");
                CryoRideService.stop(p, true);
                h.assertTrue(!CryoRideService.isRiding(p) && !p.isNoGravity(), "Stopping restores the original gravity flag");
                h.succeed();
            } finally { clean(p); }
        });
    }

    @GameTest(template="cryo_rework_arena", timeoutTicks=65, batch="cryo.rework_ride_slopes")
    public static void glacierRideKeepsMovingFromClimbToDescent(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true);
        PlayerProgressionService.setLevel(p, 3);
        Vec3 start = p.position();
        h.assertTrue(CryoRideService.toggle(p, 3), "Glacier Ride must start at its unlock level");
        double[] minTravel = {Double.POSITIVE_INFINITY};
        double[] peak = {start.y};
        h.onEachTick(() -> {
            long tick = h.getTick();
            Vec3 before = p.position();
            // Space and Shift alone carry the rider through the slope change.
            CryoRideService.input(p, 0, 0, tick <= 20, tick > 20, false);
            CryoRideService.tick(p);
            p.move(MoverType.SELF, p.getDeltaMovement());
            if (tick > 8) minTravel[0] = Math.min(minTravel[0], p.getZ() - before.z);
            peak[0] = Math.max(peak[0], p.getY());
        });
        h.runAfterDelay(43, () -> {
            try {
                h.assertTrue(peak[0] >= start.y + 2 && p.getY() < peak[0] - 1.5,
                        "Space must climb and Shift must descend at tier three, peak=" + peak[0] + ", end=" + p.getY());
                h.assertTrue(CryoRideService.isRiding(p) && minTravel[0] >= .4,
                        "Every tick after acceleration must retain forward progress, minimum=" + minTravel[0]);
                BlockPos feet = BlockPos.containing(p.position().add(0,-.08,0));
                h.assertTrue(List.of(feet, feet.below()).stream().anyMatch(pos -> {
                    var state = h.getLevel().getBlockState(pos);
                    if (!FrostConstructBlock.isFrost(state)) return false;
                    double clearance = p.getY() - (pos.getY() + state.getValue(FrostConstructBlock.FORM).top);
                    return clearance >= -.001 && clearance <= .55;
                }), "A physical ice tread must be within a half step of the descending skater at " + p.position());
                h.succeed();
            } finally { clean(p); }
        });
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.rework_walkable_ramp")
    public static void sculptRampCanBeWalkedWithoutJumping(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true);
        double startY = p.getY();
        p.setXRot(36);
        h.assertTrue(CryoKitService.sculpt(p, 5, true), "Sculpt must place the first ramp tread");
        h.runAfterDelay(25, () -> {
            try {
                var blocks = CryoKitService.constructBlocks(p);
                h.assertTrue(blocks.stream().anyMatch(pos -> h.getLevel().getBlockState(pos)
                                .getValue(FrostConstructBlock.FORM) == FrostConstructBlock.Form.LOW),
                        "The route must use real half-height steps");
                double highest = startY;
                p.setOnGround(true);
                for (int i = 0; i < 36; i++) {
                    p.move(MoverType.SELF, new Vec3(0, -.08, .22));
                    highest = Math.max(highest, p.getY());
                }
                h.assertTrue(highest >= startY + 2.5, "A player must be able to walk up the ramp without jumping");
                h.succeed();
            } finally { clean(p); }
        });
    }

    @GameTest(template="cryo_rework_arena", timeoutTicks=150, batch="cryo.rework_ride_ceiling")
    public static void risingRideBuildsSolidStepsButCannotClimbWithoutLimit(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true); p.setNoGravity(false); p.setOnGround(true);
        double groundHeight = p.getY();
        h.assertTrue(CryoRideService.toggle(p, 5), "Rising skating must begin on actual ground" + ownerState(p));
        h.onEachTick(() -> {
            if (CryoRideService.isRiding(p)) {
                CryoRideService.input(p, 1, 0, true, false, false);
                CryoRideService.tick(p);
                p.move(MoverType.SELF, p.getDeltaMovement());
            }
        });
        h.runAfterDelay(110, () -> {
            try {
                h.assertTrue(p.getY() > groundHeight + 3 && p.getY() <= groundHeight + 6.3,
                        "Held rise must create a useful ramp while respecting its six-block ceiling");
                h.assertTrue(CryoRideService.isRiding(p)
                                && FrostConstructBlock.isFrost(h.getLevel().getBlockState(BlockPos.containing(p.position().add(0,-.08,0)))),
                        "The bounded rising path continues to support the rider with real ice");
                CryoRideService.stop(p, true);
                h.assertTrue(!p.isNoGravity(), "Leaving the raised ribbon restores ordinary gravity");
                h.succeed();
            } finally { clean(p); }
        });
    }

    @GameTest(template="rewind_empty", batch="cryo.rework_nodes")
    public static void interactionBranchesKeepSavedSkillIdentitiesAndRemoveOldStatBonuses(GameTestHelper h) {
        ServerPlayer p = player(h, 3, true);
        try {
            var all = SkillCatalog.nodes("cryokinesis");
            var nodes = all.stream().filter(n -> !n.signature()).toList();
            h.assertTrue(nodes.size() == 18 && nodes.stream().filter(SkillCatalog.Node::active).count() == 6,
                    "The redesigned kit retains eighteen saved nodes and six optional actions");
            h.assertTrue(all.stream().filter(SkillCatalog.Node::signature).map(SkillCatalog.Node::id).toList().equals(java.util.List.of("cryokinesis_absolute_zero")),
                    "Beside them, one signature: Absolute Zero");
            h.assertTrue(nodes.stream().anyMatch(n -> n.id().equals("cryokinesis_stillheart"))
                            && nodes.stream().anyMatch(n -> n.id().equals("cryokinesis_suspension_stitch")),
                    "Cocoon and woven-path purchases retain their existing IDs");
            p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.CRYOKINESIS_ID.toString(),
                    new SkillTreeData.Build(15, Set.of("cryokinesis_0_0", "cryokinesis_0_2", "cryokinesis_1_0",
                            "cryokinesis_1_2", "cryokinesis_2_0", "cryokinesis_2_2"), Map.of())));
            for (int branch = 0; branch < 3; branch++) h.assertTrue(SkillTreeService.bonus(p, ModAbilities.CRYOKINESIS_ID, branch) == 0,
                    "Interaction nodes must no longer grant silent blanket cost, damage or cooldown bonuses");
            h.succeed();
        } finally { clean(p); }
    }
}
