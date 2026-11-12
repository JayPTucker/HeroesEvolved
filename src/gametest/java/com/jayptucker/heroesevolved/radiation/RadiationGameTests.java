package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.types.RadiationAbility;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.CommonListenerCookie;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.NuclearCloudPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class RadiationGameTests {
    private static final Map<UUID, Set<net.minecraft.world.level.ChunkPos>> FIXTURE_CHUNKS = new HashMap<>();
    private static void keepLoaded(ServerPlayer player, BlockPos pos) {
        var chunk = new net.minecraft.world.level.ChunkPos(pos);
        if (!player.serverLevel().getForcedChunks().contains(chunk.toLong())) {
            player.serverLevel().setChunkForced(chunk.x, chunk.z, true);
            FIXTURE_CHUNKS.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(chunk);
        }
        player.serverLevel().getChunk(chunk.x, chunk.z);
    }
    @SuppressWarnings("unchecked") private static <T> T field(PlayerList list, String name) {
        try { Field field = PlayerList.class.getDeclaredField(name); field.setAccessible(true); return (T) field.get(list); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static ServerPlayer player(GameTestHelper helper, int height, boolean first) {
        NuclearFallout.clear();
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "nuclear-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Connection connection = player.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        try {
            // FakePlayer never ticks down the normal login damage grace period.
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(player, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, height, 5))));
        // Fake players have no network-driven view tickets. Keep the actual fixture active
        // regardless of the random test structure's alignment with chunk boundaries.
        for (int x = -16; x <= 16; x += 16) for (int z = -16; z <= 16; z += 16)
            keepLoaded(player, player.blockPosition().offset(x, 0, z));
        player.setNoGravity(true);
        player.setGameMode(GameType.SURVIVAL);
        helper.getLevel().addNewPlayer(player);
        PlayerList list = player.getServer().getPlayerList();
        RadiationGameTests.<List<ServerPlayer>>field(list, "players").add(player);
        RadiationGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
        if (!first) player.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.RADIATION_ID));
        PlayerAbilityService.grantAbility(player, ModAbilities.RADIATION_ID);
        return player;
    }
    private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, Vec3 pos) {
        T target = type.create(helper.getLevel()); target.setPos(pos); target.setNoAi(true); target.setNoGravity(true);
        helper.getLevel().addFreshEntity(target); return target;
    }
    private static void remove(ServerPlayer player, Entity... targets) {
        com.jayptucker.heroesevolved.PowerOwnershipTestSupport.release(player);
        PowerIntroService.forget(player); RadiationService.stopAll(player);
        PlayerList list = player.getServer().getPlayerList();
        RadiationGameTests.<List<ServerPlayer>>field(list, "players").remove(player);
        RadiationGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
        for (Entity target : targets) target.discard();
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        var chunks = FIXTURE_CHUNKS.remove(player.getUUID());
        if (chunks != null) for (var chunk : chunks) player.serverLevel().setChunkForced(chunk.x, chunk.z, false);
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 1500, batch = "RadiationGameTests.firstAwakeningCannotCalmForThirtySeconds")
    public static void firstAwakeningCannotCalmForThirtySeconds(GameTestHelper helper) {
        ServerPlayer player = player(helper, 220, true);
        Cow cow = mob(helper, EntityType.COW, player.position().add(5, 0, 0));
        Zombie undead = mob(helper, EntityType.ZOMBIE, player.position().add(-5, 0, 0));
        BlockPos center = player.blockPosition();
        BlockPos near = center.offset(40, 0, 0), outside = center.offset(50, 0, 0), protectedBlock = center.offset(16, 0, 0);
        for (BlockPos pos : List.of(near, outside, protectedBlock)) {
            keepLoaded(player, pos); helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
        BlockPos bedrock = center.offset(18, 0, 0);
        helper.getLevel().setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 3);
        Consumer<BlockEvent.BreakEvent> guard = event -> { if (event.getPos().equals(protectedBlock)) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, guard);
        float health = player.getHealth();
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(!cow.isAlive() && undead.getHealth() < undead.getMaxHealth(), "The uncontrolled aura must harm both ordinary and poison-immune mobs");
            player.hurt(player.damageSources().generic(), 1000);
            helper.assertTrue(player.isAlive() && player.getHealth() == health, "First awakening must block lethal damage through the real hurt path");
            helper.assertTrue(!RadiationService.holdItIn(player), "First awakening cannot be canceled by holding it in");
        });
        helper.runAfterDelay(590, () -> {
            RadiationService.tickPlayer(player, helper.getLevel().getGameTime());
            helper.assertTrue(PowerIntroService.radiationChargeRemaining(player) > 0, "Must still charge near the end of thirty seconds");
            helper.assertTrue(player.hasGlowingTag(), "Normal passive updates must not extinguish the awakening glow");
            helper.assertTrue(RadiationService.visualState(player).remaining() > 0, "Observers must receive an active charge sound state");
            helper.assertTrue(AbilityUseService.activateAssignedAction(player, AbilitySlot.QUINARY, true) == AbilityUseResult.CANNOT_USE,
                    "Shift must not bypass the first thirty-second loss of control");
            helper.assertTrue(helper.getLevel().getBlockState(near).is(Blocks.STONE), "Blast must wait for the whole charge");
        });
        helper.runAfterDelay(1250, () -> {
            try {
                helper.assertTrue(!PowerIntroService.isActive(player), "Awakening must settle after the release");
                helper.assertTrue(helper.getLevel().getBlockState(near).isAir(), "Nuclear blast must destroy ordinary terrain forty blocks away");
                helper.assertTrue(helper.getLevel().getBlockState(outside).is(Blocks.STONE), "Terrain outside the forty-eight-block awakening radius must survive");
                helper.assertTrue(helper.getLevel().getBlockState(bedrock).is(Blocks.BEDROCK), "Unbreakable terrain must survive");
                helper.assertTrue(helper.getLevel().getBlockState(protectedBlock).is(Blocks.STONE), "Nuclear damage must respect protection events");
                helper.assertTrue(player.getHealth() == health, "Own blast and fallout must not harm the owner");
                player.invulnerableTime = 0;
                player.hurt(player.damageSources().generic(), 2);
                helper.assertTrue(player.getHealth() < health, "Ordinary vulnerability must return after the first intro");
                PlayerAbilityService.removeAssignedAbility(player);
                PlayerAbilityService.grantAbility(player, ModAbilities.RADIATION_ID);
                helper.assertTrue(!PowerIntroService.isActive(player), "Regranting must not replay a free nuclear awakening");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(guard); remove(player, cow, undead); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 1810, batch = "RadiationGameTests.injuryBuildsStressLosesControlAndLeavesTemporaryFallout")
    public static void injuryBuildsStressLosesControlAndLeavesTemporaryFallout(GameTestHelper helper) {
        ServerPlayer player = player(helper, 140, false);
        player.hurt(player.damageSources().generic(), 4);
        helper.assertTrue(RadiationService.mass(player) == 12, "Actual injury must add three stress per point of damage");
        float health = player.getHealth();
        RadiationService.addMass(player, 88);
        helper.runAfterDelay(495, () -> {
            try {
                helper.assertTrue(RadiationService.isDetonating(player), "An involuntary charge must last until the end of the 25-second countdown");
                helper.assertTrue(!RadiationService.holdItIn(player), "An involuntary meltdown cannot be canceled like a voluntary charge");
            } catch (RuntimeException e) { remove(player); throw e; }
        });
        Cow[] entering = new Cow[1], outside = new Cow[1];
        helper.runAfterDelay(505, () -> {
            try {
                helper.assertTrue(!RadiationService.isDetonating(player) && RadiationService.mass(player) == 0,
                        "Detonation must release accumulated stress at 25 seconds");
                helper.assertTrue(player.getHealth() == health, "The caster must not be reduced to one health by their own explosion");
                entering[0] = mob(helper, EntityType.COW, player.position().add(18, 0, 0));
                outside[0] = mob(helper, EntityType.COW, player.position().add(26, 0, 0));
            } catch (RuntimeException e) { remove(player); throw e; }
        });
        helper.runAfterDelay(540, () -> {
            helper.assertTrue(entering[0].getHealth() < entering[0].getMaxHealth(), "Entering the crater after the shockwave must still cause radiation damage");
            helper.assertTrue(outside[0].getHealth() == outside[0].getMaxHealth(), "Fallout must stay within its radius");
            entering[0].discard();
        });
        helper.runAfterDelay(1720, () -> {
            outside[0].setPos(player.position().add(18, 0, 0));
            outside[0].removeAllEffects();
        });
        helper.runAfterDelay(1760, () -> {
            try {
                helper.assertTrue(outside[0].getHealth() == outside[0].getMaxHealth(), "Fallout must expire after one minute");
                helper.succeed();
            } finally { remove(player, outside[0]); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 260, batch = "RadiationGameTests.empDisablesRedstoneThenRestoresIt")
    public static void empDisablesRedstoneThenRestoresIt(GameTestHelper helper) {
        ServerPlayer player = player(helper, 100, false);
        BlockPos source = player.blockPosition().offset(3, 0, 0), lamp = source.above();
        helper.getLevel().setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.getLevel().setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true), 3);
        helper.assertTrue(helper.getLevel().getBlockState(source).getSignal(helper.getLevel(), source, Direction.UP) == 15, "Fixture must produce a real redstone signal");
        RadiationService.emp(player);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(source).getSignal(helper.getLevel(), source, Direction.UP) == 0, "EMP must suppress actual signal output");
            helper.assertTrue(!helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "Powered lamp must go dark");
            helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.REDSTONE_BLOCK), "EMP must preserve the source block");
        });
        helper.runAfterDelay(225, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(source).getSignal(helper.getLevel(), source, Direction.UP) == 15, "Signal must recover when the field ends");
                helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "Lamp must relight after the EMP expires");
                helper.succeed();
            } finally { remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "RadiationGameTests.heatBoilsWaterThawsIceAndMeltsMetal")
    public static void heatBoilsWaterThawsIceAndMeltsMetal(GameTestHelper helper) {
        ServerPlayer player = player(helper, 300, false);
        BlockPos target = player.blockPosition().offset(0, 1, 6);
        player.setYRot(0); player.setXRot(0);
        try {
            helper.getLevel().setBlock(target, Blocks.WATER.defaultBlockState(), 3);
            helper.assertTrue(RadiationService.radiantTouch(player, 1) && helper.getLevel().getBlockState(target).isAir(), "Heat must boil aimed-at source water");
            helper.getLevel().setBlock(target, Blocks.BLUE_ICE.defaultBlockState(), 3);
            helper.assertTrue(RadiationService.radiantTouch(player, 1) && helper.getLevel().getBlockState(target).is(Blocks.WATER), "Heat must melt ice into water");
            helper.getLevel().setBlock(target, Blocks.IRON_BARS.defaultBlockState(), 3);
            player.getAbilities().mayBuild = false;
            helper.assertTrue(!RadiationService.radiantTouch(player, 1), "Heat must respect inability to modify terrain");
            player.getAbilities().mayBuild = true;
            helper.assertTrue(RadiationService.radiantTouch(player, 1) && helper.getLevel().getBlockState(target).isAir(), "Heat must melt iron bars");
            helper.succeed();
        } finally { player.getAbilities().mayBuild = true; remove(player); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "RadiationGameTests.fissionLeapThrowsAndDosesAndStressPersists")
    public static void fissionLeapThrowsAndDosesAndStressPersists(GameTestHelper helper) {
        ServerPlayer player = player(helper, 260, false);
        Cow cow = mob(helper, EntityType.COW, player.position().add(2.5, 0, 0));
        try {
            var ability = new RadiationAbility();
            helper.assertTrue(ability.actions().get(AbilitySlot.TERTIARY).definition().id().getPath().equals("radiation_emp"), "The third skill must be EMP, not power mimicry");
            int core = RadiationService.mass(player);
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationLeap.launch(player, 2), "The leap launches");
            helper.assertTrue(player.getDeltaMovement().y > 1, "The blast throws the caster up: " + player.getDeltaMovement());
            helper.assertTrue(cow.getHealth() < cow.getMaxHealth() && cow.hasEffect(com.jayptucker.heroesevolved.registry.ModItems.RADIATION_SICKNESS),
                    "The blast under your feet hurts and sickens what is close");
            helper.assertTrue(cow.getDeltaMovement().x > 0.3, "And throws it away from the blast: " + cow.getDeltaMovement());
            helper.assertTrue(RadiationService.mass(player) > core, "Leaping builds Core");
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationLeap.cushioned(player), "No fall damage while leaping");
            helper.assertTrue(!com.jayptucker.heroesevolved.radiation.RadiationLeap.launch(player, 2), "No second launch while still in the air");
            com.jayptucker.heroesevolved.radiation.RadiationLeap.forget(player);
            RadiationService.beginDetonation(player, 5, true);
            helper.assertTrue(RadiationService.isDetonating(player) && RadiationService.holdItIn(player),
                    "After the awakening, a voluntary charge must remain cancellable");
            helper.assertTrue(RadiationService.visualState(player).remaining() == 0,
                    "Canceling a voluntary charge must stop its sound immediately");
            RadiationService.beginDetonation(player, 5, true);
            RadiationService.hurtStress(player, 5);
            helper.assertTrue(RadiationService.isDetonating(player), "A light hit does not break a charge");
            RadiationService.hurtStress(player, 4);
            helper.assertTrue(!RadiationService.isDetonating(player), "Hit hard enough, a voluntary charge is broken");
            int stress = RadiationService.mass(player);
            RadiationService.disconnect(player);
            helper.assertTrue(RadiationService.mass(player) == stress && stress > 0, "Logout must preserve accumulated stress");
            helper.succeed();
        } finally { remove(player, cow); }
    }

    private static ServerPlayer observer(GameTestHelper helper, Vec3 pos, List<Packet<?>> packets) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "nuke-witness"));
        Connection connection = player.connection.getConnection();
        try {
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        player.connection = new ServerGamePacketListenerImpl(player.getServer(), connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) { packets.add(packet); }
        };
        player.setPos(pos); helper.getLevel().addNewPlayer(player); return player;
    }
    @GameTest(template = "rewind_empty", batch = "radiation.witnesses", timeoutTicks = 70)
    public static void nuclearShockReachesThreeHundredBlocksAndCloudIsSentIndependently(GameTestHelper helper) {
        ServerPlayer source = player(helper, 275, false);
        List<Packet<?>> nearPackets = new ArrayList<>(), edgePackets = new ArrayList<>(), farPackets = new ArrayList<>();
        ServerPlayer near = observer(helper, source.position().add(250, 0, 0), nearPackets);
        ServerPlayer edge = observer(helper, source.position().add(300, 0, 0), edgePackets);
        ServerPlayer outside = observer(helper, source.position().add(300.01, 0, 0), farPackets);
        nearPackets.clear(); edgePackets.clear(); farPackets.clear();
        RadiationService.awakeningRelease(source);
        try {
            for (var packets : List.of(nearPackets, edgePackets)) {
                helper.assertTrue(packets.stream().anyMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                                && custom.payload() instanceof CameraShakePayload shake && shake.intensity() > 0 && shake.durationTicks() == 120),
                        "Every witness within three hundred blocks, including the exact boundary, must receive the six-second tremor");
                helper.assertTrue(packets.stream().anyMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                                && custom.payload() instanceof NuclearCloudPayload cloud && cloud.radius() == 48 && cloud.scale() > 1),
                        "Distant witnesses must receive an independent full-size mushroom cloud");
            }
            helper.assertTrue(farPackets.stream().noneMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof CameraShakePayload), "Beyond three hundred blocks must not receive ground shake");
            helper.succeed();
        } finally { remove(source); remove(near); remove(edge); remove(outside); }
    }
    @GameTest(template = "rewind_empty", batch = "radiation.charge_decay", timeoutTicks = 540)
    public static void chargingDecaysTerrainBeforeDetonationAndCancellationStopsIt(GameTestHelper helper) {
        ServerPlayer source = player(helper, 180, false);
        List<BlockPos> stones = new ArrayList<>();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            BlockPos pos = source.blockPosition().offset(x, -1, z);
            helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3); stones.add(pos);
        }
        RadiationService.beginDetonation(source, 5, true);
        long[] remaining = {0};
        // A failed check still cleans up, or the test player would detonate for real among the other tests.
        helper.runAfterDelay(470, () -> {
            try {
                remaining[0] = stones.stream().filter(pos -> helper.getLevel().getBlockState(pos).is(Blocks.STONE)).count();
                helper.assertTrue(remaining[0] < stones.size() && RadiationService.isDetonating(source), "Terrain must crumble during the charge, before the explosion");
                helper.assertTrue(RadiationService.holdItIn(source), "Voluntary detonation can still be canceled");
            } catch (RuntimeException e) { remove(source); throw e; }
        });
        helper.runAfterDelay(505, () -> {
            try {
                helper.assertTrue(stones.stream().filter(pos -> helper.getLevel().getBlockState(pos).is(Blocks.STONE)).count() == remaining[0],
                        "Canceling must clear cracks and stop further decay");
                helper.succeed();
            } finally { remove(source); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "radiation.damage", timeoutTicks = 60)
    public static void nuclearWaveDealsHeavyDamageWithoutHarmingItsOwner(GameTestHelper helper) {
        ServerPlayer source = player(helper, 200, false);
        var target = mob(helper, EntityType.IRON_GOLEM, source.position().add(10, 0, 0));
        float health = source.getHealth();
        NuclearFallout.begin(source);
        helper.runAfterDelay(12, () -> {
            try {
                helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 50, "Nuclear wave must take at least fifty health from a ten-block target");
                helper.assertTrue(source.getHealth() == health, "The much stronger wave must still spare its owner");
                helper.succeed();
            } finally { remove(source, target); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "radiation.fixed_cloud", timeoutTicks = 65)
    public static void cloudRemainsAtOriginalDetonationAfterOwnerMoves(GameTestHelper helper) {
        ServerPlayer source = player(helper, 240, false);
        Vec3 origin = source.position();
        List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer witness = observer(helper, origin.add(100, 0, 0), packets);
        packets.clear();
        NuclearFallout.begin(source);
        NuclearCloudPayload first = packets.stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof NuclearCloudPayload).map(packet -> (NuclearCloudPayload) ((ClientboundCustomPayloadPacket) packet).payload())
                .findFirst().orElseThrow();
        packets.clear(); source.setPos(origin.add(60, 0, 0));
        helper.runAfterDelay(45, () -> {
            try {
                helper.assertTrue(first.center().equals(origin), "Cloud must start at the exact detonation position");
                helper.assertTrue(packets.stream().anyMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof NuclearCloudPayload cloud && cloud.id().equals(first.id())
                        && cloud.age() >= 40 && cloud.center().equals(origin)), "Cloud heartbeat must stay at the original blast after the owner moves");
                helper.succeed();
            } finally { remove(source); remove(witness); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "radiation.terrain_budget", timeoutTicks = 400)
    public static void denseCraterIsGradualAndSharesOneBudgetAcrossBlasts(GameTestHelper helper) {
        ServerPlayer first = player(helper, 170, false);
        List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer second = observer(helper, first.position().add(70, 0, 0), packets);
        second.setNoGravity(true);
        List<BlockPos> stones = new ArrayList<>();
        Set<net.minecraft.world.level.ChunkPos> forced = new HashSet<>();
        for (ServerPlayer source : List.of(first, second))
            for (int x = -5; x <= 5; x++) for (int y = 0; y <= 5; y++) for (int z = -5; z <= 5; z++) {
                BlockPos pos = source.blockPosition().offset(x, y, z);
                // Fake observers do not maintain normal player chunk tickets outside the test structure.
                var chunk = new net.minecraft.world.level.ChunkPos(pos);
                if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                    helper.getLevel().setChunkForced(chunk.x, chunk.z, true); forced.add(chunk);
                }
                helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3); stones.add(pos);
            }
        Map<Long, Integer> breaks = new HashMap<>();
        Consumer<BlockEvent.BreakEvent> counter = event -> {
            if (event.getPlayer() == first || event.getPlayer() == second) {
                int count = breaks.merge(helper.getLevel().getGameTime(), 1, Integer::sum);
                helper.assertTrue(count <= NuclearFallout.MAX_BREAKS_PER_TICK, "Simultaneous dense craters must share a single per-tick break limit");
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, counter);
        packets.clear();
        NuclearFallout.begin(first); NuclearFallout.begin(second);
        helper.runAfterDelay(NuclearFallout.WAVE_TICKS, () -> helper.assertTrue(stones.stream().anyMatch(pos -> helper.getLevel().getBlockState(pos).is(Blocks.STONE)),
                "Dense terrain must break gradually rather than in one burst"));
        helper.runAfterDelay(330, () -> {
            try {
                helper.assertTrue(stones.stream().allMatch(pos -> helper.getLevel().getBlockState(pos).isAir()), "Both gradual craters must finish destroying ordinary blocks");
                helper.assertTrue(breaks.size() > 1, "Terrain destruction must span several ticks");
                helper.assertTrue(packets.stream().noneMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundLevelEventPacket event
                        && event.getType() == 2001), "Nuclear terrain removal must not send a separate block-break sound and particle event per block");
                helper.succeed();
            } finally {
                NeoForge.EVENT_BUS.unregister(counter); remove(first); remove(second);
                for (var chunk : forced) helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "radiation.silent_rewind", timeoutTicks = 180)
    public static void silentCraterRemovalStillRewindsBlocksAndContainerContents(GameTestHelper helper) {
        ServerPlayer source = player(helper, 195, false);
        BlockPos stone = source.blockPosition().offset(3, 0, 0), chest = source.blockPosition().offset(4, 0, 0);
        helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getLevel().getBlockEntity(chest)).setItem(0,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        // Dedicated timeline watcher, as in the rewind journal fixtures. The radiation
        // owner cannot record a timeline through the ordinary Time Manipulation service.
        ServerPlayer watcher = observer(helper, source.position().add(10, 0, 0), new ArrayList<>());
        watcher.setInvulnerable(true); watcher.setNoGravity(true);
        long before = com.jayptucker.heroesevolved.time.TimeRewindBlocks.track(watcher);
        NuclearFallout.begin(source);
        helper.runAfterDelay(140, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(stone).isAir() && helper.getLevel().getBlockState(chest).isAir(),
                        "Silent crater must remove both ordinary blocks and containers");
                var plan = com.jayptucker.heroesevolved.time.TimeRewindBlocks.plan(watcher, before);
                helper.assertTrue(plan != null, "The witness must retain a timeline of the crater");
                com.jayptucker.heroesevolved.time.TimeRewindBlocks.restore(watcher, plan);
                helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "Gradual silent removal must remain visible to Time Rewind");
                var restored = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getLevel().getBlockEntity(chest);
                helper.assertTrue(restored != null && restored.getItem(0).is(net.minecraft.world.item.Items.DIAMOND)
                        && restored.getItem(0).getCount() == 3, "Rewinding the crater must preserve original container contents");
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(chest).inflate(5)).isEmpty(), "Restored container contents must not duplicate as loose drops");
                helper.succeed();
            } finally { com.jayptucker.heroesevolved.time.TimeRewindBlocks.forget(watcher.getUUID()); remove(source); remove(watcher); }
        });
    }

    // ------------------------------------------------------------------
    // The reworked kit
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "radiation.cone")
    public static void radiantHeatBurnsACrowdInFrontButNotBehind(GameTestHelper helper) {
        ServerPlayer player = player(helper, 320, false);
        player.setYRot(0); player.setXRot(0);
        Cow front = mob(helper, EntityType.COW, player.position().add(0.6, 0, 4)), side = mob(helper, EntityType.COW, player.position().add(-1.4, 0, 6));
        Cow behind = mob(helper, EntityType.COW, player.position().add(0, 0, -4));
        try {
            helper.assertTrue(RadiationService.radiantTouch(player, 2), "There are creatures in the cone");
            helper.assertTrue(front.getHealth() < front.getMaxHealth() && side.getHealth() < side.getMaxHealth(), "The cone burns everything in front");
            helper.assertTrue(RadiationSickness.stage(front) >= 0, "And doses it");
            helper.assertTrue(behind.getHealth() == behind.getMaxHealth(), "Nothing behind you is touched");
            helper.assertTrue(front.getDeltaMovement().z > 0.2, "The heat throws them back: " + front.getDeltaMovement());
            helper.succeed();
        } finally { remove(player, front, side, behind); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "radiation.core")
    public static void aHotterCoreHitsHarder(GameTestHelper helper) {
        ServerPlayer player = player(helper, 340, false);
        player.setYRot(0); player.setXRot(0);
        Cow cold = mob(helper, EntityType.COW, player.position().add(0, 0, 3));
        try {
            RadiationService.vent(player, 100);
            RadiationService.radiantTouch(player, 1);
            float coldDamage = cold.getMaxHealth() - cold.getHealth();
            cold.setHealth(cold.getMaxHealth()); cold.invulnerableTime = 0; cold.removeAllEffects();
            RadiationService.addMass(player, RadiationService.CRITICAL - RadiationService.mass(player));
            RadiationService.radiantTouch(player, 1);
            float hotDamage = cold.getMaxHealth() - cold.getHealth();
            helper.assertTrue(hotDamage > coldDamage * 1.3F, "Critical Core must hit much harder: " + coldDamage + " then " + hotDamage);
            helper.assertTrue(cold.isOnFire(), "Critical heat sets targets alight");
            helper.succeed();
        } finally { remove(player, cold); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "radiation.sickness")
    public static void sicknessStacksSparesTheCasterAndSpreadsFromTheDead(GameTestHelper helper) {
        ServerPlayer player = player(helper, 360, false);
        Cow carrier = mob(helper, EntityType.COW, player.position().add(4, 0, 0)), neighbour = mob(helper, EntityType.COW, player.position().add(5.5, 0, 0));
        try {
            RadiationSickness.dose(carrier, player, 1, 200);
            RadiationSickness.dose(carrier, player, 2, 200);
            helper.assertTrue(RadiationSickness.stage(carrier) == 2, "Doses stack: stage " + RadiationSickness.stage(carrier));
            helper.assertTrue(!RadiationSickness.dose(player, player, 1, 200), "The caster cannot sicken from their own radiation");
            float before = neighbour.getHealth();
            RadiationSickness.chainReaction(carrier);
            helper.assertTrue(neighbour.getHealth() < before && RadiationSickness.stage(neighbour) >= 0, "A sick creature's death bursts onto its neighbours");
            helper.succeed();
        } finally { remove(player, carrier, neighbour); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 160, batch = "radiation.zone")
    public static void aSlamLandsInABlastThatLeavesTheGroundContaminated(GameTestHelper helper) {
        ServerPlayer player = player(helper, 380, false);
        try {
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationLeap.launch(player, 2), "The leap launches");
            helper.assertTrue(!com.jayptucker.heroesevolved.radiation.RadiationLeap.slam(player), "Too soon to slam on the launch tick");
            helper.runAfterDelay(5, () -> {
                try {
                    helper.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationLeap.slam(player), "In the air, the second press slams");
                    helper.assertTrue(player.getDeltaMovement().y < -1, "Driving down: " + player.getDeltaMovement());
                } catch (RuntimeException e) { remove(player); throw e; }
            });
            // A test player never reaches the ground, so the slam lands when its time runs out.
            Cow[] late = new Cow[1];
            helper.runAfterDelay(50, () -> {
                helper.assertTrue(!com.jayptucker.heroesevolved.radiation.RadiationLeap.leaping(player), "The slam has landed");
                // Someone walks in afterwards.
                late[0] = mob(helper, EntityType.COW, player.position().add(1.5, 0, 0));
            });
            helper.runAfterDelay(100, () -> {
                try {
                    helper.assertTrue(RadiationSickness.stage(late[0]) >= 0, "Contaminated ground doses whoever stands in it later");
                    helper.succeed();
                } finally { remove(player, late[0]); }
            });
        } catch (RuntimeException e) { remove(player); throw e; }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "radiation.emission")
    public static void controlledEmissionVentsCoreWhileHeld(GameTestHelper helper) {
        ServerPlayer player = player(helper, 400, false);
        player.setYRot(0); player.setXRot(0);
        Cow target = mob(helper, EntityType.COW, player.position().add(0, 0, 10));
        RadiationService.addMass(player, 70 - RadiationService.mass(player));
        int before = RadiationService.mass(player);
        helper.assertTrue(RadiationService.startEmission(player, 4) && RadiationService.isEmitting(player), "The beam starts on the key");
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(RadiationService.isEmitting(player), "It keeps going while held");
                helper.assertTrue(RadiationService.mass(player) < before - 10, "Holding vents Core: " + before + " -> " + RadiationService.mass(player));
                helper.assertTrue(target.getHealth() < target.getMaxHealth(), "The beam hits what is in its line");
                RadiationService.releaseEmission(player);
                helper.runAfterDelay(3, () -> {
                    try {
                        helper.assertTrue(!RadiationService.isEmitting(player), "Letting go ends it");
                        helper.succeed();
                    } finally { remove(player, target); }
                });
            } catch (RuntimeException e) { remove(player, target); throw e; }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "radiation.emp_vent")
    public static void empVentsCoreAndThrowsEnemiesOff(GameTestHelper helper) {
        ServerPlayer player = player(helper, 420, false);
        Cow near = mob(helper, EntityType.COW, player.position().add(4, 0, 0));
        try {
            RadiationService.addMass(player, 60 - RadiationService.mass(player));
            RadiationService.emp(player);
            helper.assertTrue(RadiationService.mass(player) <= 35, "The pulse carries 25 Core out: " + RadiationService.mass(player));
            helper.assertTrue(near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && near.hasEffect(MobEffects.GLOWING), "Enemies nearby are thrown off balance and revealed");
            helper.assertTrue(near.getDeltaMovement().x > 0.3, "And shoved away from the pulse: " + near.getDeltaMovement());
            helper.succeed();
        } finally { remove(player, near); }
    }

    // ------------------------------------------------------------------
    // The skill tree
    // ------------------------------------------------------------------

    private static void learn(ServerPlayer player, String... ids) {
        player.setData(ModDataAttachments.SKILL_TREES, com.jayptucker.heroesevolved.skills.SkillTreeData.empty().with(ModAbilities.RADIATION_ID.toString(),
                new com.jayptucker.heroesevolved.skills.SkillTreeData.Build(15, Set.of(ids), Map.of())));
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "radiation.tree.lance")
    public static void gammaLancePiercesALineAndSpendsCoreToHitHarder(GameTestHelper helper) {
        ServerPlayer player = player(helper, 230, false);
        player.setYRot(0); player.setXRot(0);
        var near = mob(helper, EntityType.IRON_GOLEM, player.position().add(0, 0, 5)); Cow far = mob(helper, EntityType.COW, player.position().add(0, 0, 11));
        try {
            RadiationService.vent(player, 100);
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationSkills.gammaLanceForTest(player, 3), "The lance fires");
            float cold = near.getMaxHealth() - near.getHealth();
            helper.assertTrue(cold > 0 && far.getHealth() < far.getMaxHealth(), "It pierces everything on the line");
            near.setHealth(near.getMaxHealth()); near.invulnerableTime = 0;
            RadiationService.addMass(player, 30 - RadiationService.mass(player));
            com.jayptucker.heroesevolved.radiation.RadiationSkills.gammaLanceForTest(player, 3);
            float hot = near.getMaxHealth() - near.getHealth();
            helper.assertTrue(hot >= cold * 1.8F, "With Core to spend it strikes twice as hard: " + cold + " then " + hot);
            helper.assertTrue(RadiationService.mass(player) == 15, "And spends 15 Core: " + RadiationService.mass(player));
            helper.succeed();
        } finally { remove(player, near, far); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "radiation.tree.vent")
    public static void chainVentBurstsTheSickItHits(GameTestHelper helper) {
        ServerPlayer player = player(helper, 245, false);
        player.setYRot(0); player.setXRot(0);
        learn(player, "radiation_heat_vent", "radiation_clean_vent");
        Cow carrier = mob(helper, EntityType.COW, player.position().add(0, 0, 4));
        Cow neighbour = mob(helper, EntityType.COW, player.position().add(3.5, 0, 1.5));
        try {
            RadiationSickness.dose(carrier, player, 3, 200);
            RadiationService.addMass(player, 30 - RadiationService.mass(player));
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationSkills.technique(player, "heat_vent"), "Heat Vent fires");
            helper.assertTrue(RadiationService.mass(player) == 0, "It vents the Core");
            helper.assertTrue(carrier.getHealth() < carrier.getMaxHealth() && carrier.getDeltaMovement().z > 0.3, "The cone burns and throws what is in front");
            helper.assertTrue(neighbour.getHealth() < neighbour.getMaxHealth() && RadiationSickness.stage(neighbour) >= 0,
                    "The sick enemy bursts onto its neighbour, outside the cone");
            helper.succeed();
        } finally { remove(player, carrier, neighbour); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "radiation.tree.leap")
    public static void falloutUpgradesChangeTheLeap(GameTestHelper helper) {
        ServerPlayer player = player(helper, 290, false);
        learn(player, "radiation_2_0", "radiation_2_2", "radiation_glowing_path", "radiation_wider_path", "radiation_catastrophe");
        // Out of reach of an ordinary slam (5 blocks at tier 2), inside a Catastrophe (8).
        Cow outer = mob(helper, EntityType.COW, player.position().add(7, 0, 0));
        try {
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationLeap.launch(player, 2), "The leap launches");
            helper.assertTrue(RadiationService.inOwnZone(player, player.position()), "Fallout Boots leave the ground contaminated");
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationLeap.launch(player, 2), "Second Leap launches once more in the air");
            helper.assertTrue(!com.jayptucker.heroesevolved.radiation.RadiationLeap.launch(player, 2), "But only once");
            helper.runAfterDelay(5, () -> {
                try {
                    helper.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationLeap.slam(player), "The slam starts");
                } catch (RuntimeException e) { remove(player, outer); throw e; }
            });
            helper.runAfterDelay(55, () -> {
                try {
                    helper.assertTrue(outer.getHealth() < outer.getMaxHealth(), "Slamming into your own contaminated ground sets it off, wider");
                    helper.succeed();
                } finally { remove(player, outer); }
            });
        } catch (RuntimeException e) { remove(player, outer); throw e; }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "radiation.tree.breeder")
    public static void breederReactorRunsHotInsteadOfFailing(GameTestHelper helper) {
        ServerPlayer player = player(helper, 305, false);
        learn(player, "radiation_reactor_core", "radiation_power_transfer", "radiation_reactor");
        try {
            RadiationService.addMass(player, 100);
            helper.assertTrue(RadiationService.multiplier(player) == 2.0F, "Overdrive doubles radiation damage");
        } catch (RuntimeException e) { remove(player); throw e; }
        helper.runAfterDelay(75, () -> {
            try {
                helper.assertTrue(!RadiationService.isDetonating(player), "No meltdown while running in Overdrive");
                helper.succeed();
            } finally { remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "radiation.tree.supercritical")
    public static void supercriticalKillsBurstAndFeedTheCore(GameTestHelper helper) {
        ServerPlayer player = player(helper, 330, false);
        learn(player, "radiation_heat_vent", "radiation_clean_vent", "radiation_containment");
        Cow dead = mob(helper, EntityType.COW, player.position().add(4, 0, 0)), near = mob(helper, EntityType.COW, player.position().add(6, 0, 0));
        try {
            RadiationService.addMass(player, 85 - RadiationService.mass(player));
            com.jayptucker.heroesevolved.radiation.RadiationSkills.onKill(player, dead);
            helper.assertTrue(near.getHealth() < near.getMaxHealth(), "A kill while Critical bursts onto what is near");
            helper.assertTrue(RadiationService.mass(player) == 90, "And feeds 5 Core: " + RadiationService.mass(player));
            RadiationService.vent(player, 100);
            float before = near.getHealth();
            com.jayptucker.heroesevolved.radiation.RadiationSkills.onKill(player, dead);
            helper.assertTrue(near.getHealth() == before, "Below Critical nothing bursts");
            helper.succeed();
        } finally { remove(player, dead, near); }
    }

    // ------------------------------------------------------------------
    // A speedster running the heat out
    // ------------------------------------------------------------------

    /** A tier-{@code tier} speedster beside the radioactive player, with stamina and health to spare (or not). */
    private static ServerPlayer speedster(GameTestHelper helper, ServerPlayer target, int tier) {
        return holder(helper, target.position().add(4, 0, 0), ModAbilities.SUPERSPEED_ID, tier);
    }

    /** A test player holding {@code power} at {@code tier}, at {@code at}, who can be hurt. */
    private static ServerPlayer holder(GameTestHelper helper, Vec3 at, net.minecraft.resources.ResourceLocation power, int tier) {
        ServerPlayer runner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "power-holder")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            // FakePlayer never ticks down the normal login damage grace period.
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(runner, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        runner.setPos(at);
        runner.setNoGravity(true);
        runner.setGameMode(GameType.SURVIVAL);
        runner.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty()
                .assignDormant(power).unlock(power));
        com.jayptucker.heroesevolved.progression.PlayerProgressionService.setLevel(runner, tier);
        com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(runner, 999);
        helper.getLevel().addNewPlayer(runner);
        return runner;
    }

    private static void dropRunner(ServerPlayer runner) {
        com.jayptucker.heroesevolved.speed.SpeedService.forget(runner);
        runner.serverLevel().removePlayerImmediately(runner, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "radiation.contain.cool")
    public static void aSpeedsterRunsTheHeatOutOfAHotPlayer(GameTestHelper helper) {
        ServerPlayer target = player(helper, 350, false);
        ServerPlayer runner = speedster(helper, target, 5);
        try {
            RadiationService.addMass(target, 70 - RadiationService.mass(target));
            int stamina = com.jayptucker.heroesevolved.energy.PlayerEnergyService.getEnergy(runner);
            helper.assertTrue(com.jayptucker.heroesevolved.speed.SpeedContainment.start(runner, 5), "A hot radioactive player in reach is run round");
        } catch (RuntimeException e) { dropRunner(runner); remove(target); throw e; }
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(RadiationService.mass(target) <= 40, "The run drags the Core out: " + RadiationService.mass(target));
                helper.assertTrue(com.jayptucker.heroesevolved.energy.PlayerEnergyService.getEnergy(runner)
                        < com.jayptucker.heroesevolved.energy.PlayerEnergyService.getMaximumEnergy(runner), "And costs the runner stamina");
                com.jayptucker.heroesevolved.speed.SpeedContainment.release(runner);
                helper.assertTrue(!com.jayptucker.heroesevolved.speed.SpeedContainment.running(runner), "Letting go of a cooling run stops it");
                helper.succeed();
            } finally { dropRunner(runner); remove(target); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 180, batch = "radiation.contain.hold")
    public static void aTopSpeedsterHoldsInABlastAndLives(GameTestHelper helper) {
        ServerPlayer target = player(helper, 365, false);
        ServerPlayer runner = speedster(helper, target, 5);
        try {
            RadiationService.beginDetonation(target, 5, true);
            helper.assertTrue(com.jayptucker.heroesevolved.speed.SpeedContainment.start(runner, 5), "A player about to blow is run round");
        } catch (RuntimeException e) { dropRunner(runner); remove(target); throw e; }
        helper.runAfterDelay(50, () -> {
            try {
                helper.assertTrue(RadiationService.isDetonating(target), "While the run holds it, the charge never runs out");
                com.jayptucker.heroesevolved.speed.SpeedContainment.release(runner);
                helper.assertTrue(com.jayptucker.heroesevolved.speed.SpeedContainment.running(runner), "A blast being contained cannot be let go");
            } catch (RuntimeException e) { dropRunner(runner); remove(target); throw e; }
        });
        helper.runAfterDelay(90, () -> {
            try {
                helper.assertTrue(RadiationService.isDetonating(target), "Defusing takes five seconds of holding");
            } catch (RuntimeException e) { dropRunner(runner); remove(target); throw e; }
        });
        helper.runAfterDelay(120, () -> {
            try {
                helper.assertTrue(!RadiationService.critical(target), "Held for five seconds, the blast is defused");
                helper.assertTrue(RadiationService.mass(target) <= 30, "And the Core settles: " + RadiationService.mass(target));
                helper.assertTrue(runner.getHealth() > 0, "A tier-5 runner with stamina to spare lives: " + runner.getHealth());
                helper.succeed();
            } finally { dropRunner(runner); remove(target); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "radiation.contain.burn")
    public static void anExhaustedRunnerBurnsAwayButTheBlastStops(GameTestHelper helper) {
        ServerPlayer target = player(helper, 395, false);
        ServerPlayer runner = speedster(helper, target, 4);
        try {
            com.jayptucker.heroesevolved.energy.PlayerEnergyService.tryConsume(runner, com.jayptucker.heroesevolved.energy.PlayerEnergyService.getEnergy(runner));
            runner.setHealth(4);
            RadiationService.beginDetonation(target, 5, true);
            helper.assertTrue(com.jayptucker.heroesevolved.speed.SpeedContainment.start(runner, 4), "The run starts");
        } catch (RuntimeException e) { dropRunner(runner); remove(target); throw e; }
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(com.jayptucker.heroesevolved.speed.SpeedContainment.burningAway(runner), "With nothing left, the heat beats the runner");
                helper.assertTrue(!RadiationService.critical(target), "But it takes the blast with it, at once");
                helper.assertTrue(runner.getHealth() > 0, "They stand on the ring and burn away before they die");
                com.jayptucker.heroesevolved.speed.SpeedContainment.release(runner);
                helper.assertTrue(com.jayptucker.heroesevolved.speed.SpeedContainment.running(runner), "Letting go can't save them now");
            } catch (RuntimeException e) { dropRunner(runner); remove(target); throw e; }
        });
        helper.runAfterDelay(170, () -> {
            try {
                helper.assertTrue(runner.getHealth() <= 0 || runner.isDeadOrDying(), "When the burning is done, they are gone: " + runner.getHealth());
                helper.assertTrue(!com.jayptucker.heroesevolved.speed.SpeedContainment.running(runner), "The run is over");
                helper.succeed();
            } finally { dropRunner(runner); remove(target); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "radiation.shields")
    public static void shieldMakersHighEnoughSurviveTheBlast(GameTestHelper helper) {
        // The test server keeps PvP off, so a player's blast can't hurt test players here: ask the shield directly what
        // it lets through of a hit the wave would deal ten blocks out (55 health).
        ServerPlayer source = player(helper, 410, false);
        ServerPlayer strong = holder(helper, source.position().add(10, 0, 0), ModAbilities.INVISIBILITY_ID, 5);
        ServerPlayer able = holder(helper, source.position().add(-10, 0, 0), ModAbilities.TELEKINESIS_ID, 4);
        ServerPlayer young = holder(helper, source.position().add(0, 0, 10), ModAbilities.INVISIBILITY_ID, 3);
        ServerPlayer bare = holder(helper, source.position().add(0, 0, -10), ModAbilities.SUPERSPEED_ID, 5);
        try {
            float five = com.jayptucker.heroesevolved.radiation.NuclearShields.brace(strong, source.position(), 55);
            float four = com.jayptucker.heroesevolved.radiation.NuclearShields.brace(able, source.position(), 55);
            helper.assertTrue(five < strong.getHealth() && four < able.getHealth(), "A tier 4 or 5 shield holds the blast: " + five + ", " + four);
            helper.assertTrue(five < four, "A tier-5 shield lets less through: " + five + " vs " + four);
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.NuclearShields.brace(able, source.position(), 500) <= able.getHealth() - 2,
                    "However big the blast, a shield never lets through enough to kill");
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.NuclearShields.brace(young, source.position(), 55) == 55, "Below tier 4 the shield can't hold it");
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.NuclearShields.brace(bare, source.position(), 55) == 55, "Without a shield there is nothing to hold it");
            helper.assertTrue(com.jayptucker.heroesevolved.radiation.NuclearShields.shielded(strong)
                    && !com.jayptucker.heroesevolved.radiation.NuclearShields.shielded(young), "The shield stays up against the fallout");
            helper.succeed();
        } finally { dropRunner(strong); dropRunner(able); dropRunner(young); dropRunner(bare); remove(source); }
    }
}
