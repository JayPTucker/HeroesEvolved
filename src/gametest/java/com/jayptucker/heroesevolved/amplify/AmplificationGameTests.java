package com.jayptucker.heroesevolved.amplify;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

/** Power Amplification: the Surge and Overcharge rules, red lightning, the utility keys, the tree and the Tuned powers. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class AmplificationGameTests {
    /** Each test's own players: tests in a batch run side by side, so one test's cleanup must never touch another's. */
    private static final Map<GameTestHelper, List<ServerPlayer>> MADE = new java.util.IdentityHashMap<>();
    private static final Map<GameTestHelper, List<ServerPlayer>> REAL = new java.util.IdentityHashMap<>();

    /** A real player added to the level, so area searches (A Thousand Times, Hold Hands, a fold) find them. */
    private static ServerPlayer findable(GameTestHelper h, ResourceLocation power, double x) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "amp-ally");
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, net.minecraft.server.level.ClientInformation.createDefault());
        Connection connection = new Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(p.getServer(), connection, p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true);
            grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 3))).add(x, 0, 0));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, 5);
        h.getLevel().addNewPlayer(p);
        REAL.computeIfAbsent(h, k -> new ArrayList<>()).add(p);
        return p;
    }

    private static ServerPlayer player(GameTestHelper h, ResourceLocation power, double x, String... learned) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "amp-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true);
            grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 3))).add(x, 0, 0));
        p.setYRot(0);
        p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        if (learned.length > 0) p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty()
                .with(power.toString(), new SkillTreeData.Build(15, Set.of(learned), Map.of())));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        MADE.computeIfAbsent(h, k -> new ArrayList<>()).add(p);
        return p;
    }

    private static ServerPlayer amplifier(GameTestHelper h, String... learned) {
        ServerPlayer p = player(h, ModAbilities.AMPLIFICATION_ID, 0, learned);
        AmpService.addCharge(p, 100);
        return p;
    }

    private static LivingEntity pillager(GameTestHelper h, ServerPlayer near, double ahead, float health) {
        var foe = EntityType.PILLAGER.create(h.getLevel());
        foe.setNoAi(true);
        foe.setPersistenceRequired();
        foe.moveTo(near.getX(), near.getY(), near.getZ() + ahead, 180, 0);
        h.getLevel().addFreshEntity(foe);
        foe.setHealth(health);
        return foe;
    }

    private static final Map<GameTestHelper, List<ServerPlayer>> LISTED = new java.util.IdentityHashMap<>();

    /** Puts a test player in the server's player list (taken out again by {@link #cleanup}). */
    private static void listed(GameTestHelper h, ServerPlayer p) {
        var list = h.getLevel().getServer().getPlayerList();
        AmplificationGameTests.<List<ServerPlayer>>listField(list, "players").add(p);
        AmplificationGameTests.<Map<UUID, ServerPlayer>>listField(list, "playersByUUID").put(p.getUUID(), p);
        LISTED.computeIfAbsent(h, k -> new ArrayList<>()).add(p);
    }

    @SuppressWarnings("unchecked")
    private static <T> T listField(net.minecraft.server.players.PlayerList list, String name) {
        try {
            Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(list);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void cleanup(GameTestHelper h) {
        for (ServerPlayer p : LISTED.getOrDefault(h, List.of())) {
            var list = h.getLevel().getServer().getPlayerList();
            AmplificationGameTests.<List<ServerPlayer>>listField(list, "players").remove(p);
            AmplificationGameTests.<Map<UUID, ServerPlayer>>listField(list, "playersByUUID").remove(p.getUUID());
        }
        LISTED.remove(h);
        for (ServerPlayer p : MADE.getOrDefault(h, List.of())) {
            Amplification.end(p);
            AmpService.forget(p);
        }
        for (ServerPlayer p : REAL.getOrDefault(h, List.of())) {
            Amplification.end(p);
            p.serverLevel().removePlayerImmediately(p, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        MADE.remove(h);
        REAL.remove(h);
    }

    private static void guarded(GameTestHelper h, Runnable test) {
        try {
            test.run();
        } catch (RuntimeException | AssertionError e) {
            cleanup(h);
            throw e;
        }
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void aSurgeMakesAnAllyHitHarderPayLessAndRecoverFaster(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            ServerPlayer ally = player(h, ModAbilities.PYROKINESIS_ID, 1.5);
            LivingEntity foe = pillager(h, ally, 2, 20);
            h.assertTrue(AmpService.surge(amp, ally), "A Surge lands on an ally");
            h.assertTrue(Amplification.surged(ally) && !Amplification.overcharged(ally), "The ally is surged, not overcharged");
            h.assertTrue(ally.hasEffect(ModItems.SUPERCHARGED), "The ally wears the Supercharged icon");
            float before = foe.getHealth();
            foe.invulnerableTime = 0;
            foe.hurt(ally.damageSources().playerAttack(ally), 4);
            h.assertTrue(Math.abs(before - foe.getHealth() - 5) < .01F, "A blow of 4 lands as 5, got " + (before - foe.getHealth()));
            h.assertTrue(Amplification.cost(ally, ModAbilities.PYROKINESIS_ID) == Amplification.SURGE_COST, "Moves cost a quarter less");
            h.assertTrue(Amplification.cooldown(ally, ModAbilities.PYROKINESIS_ID) == Amplification.SURGE_COOLDOWN, "Cooldowns run a quarter faster");
            h.assertTrue(Amplification.drain(ally) == Amplification.SURGE_DRAIN, "Held powers drain half as fast");
            h.assertTrue(com.jayptucker.heroesevolved.pyro.PyroService.isBlue(ally), "A surged pyrokinetic burns blue");
            foe.discard();
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void anAmplifierNeverAmplifiesThemselvesAndTwoDontStack(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            ServerPlayer other = amplifier(h);
            ServerPlayer ally = player(h, ModAbilities.QUAKE_ID, 1.5);
            h.assertFalse(AmpService.surge(amp, amp), "Red lightning never amplifies its own hands");
            h.assertFalse(Amplification.surged(amp), "The amplifier stays unamplified");
            AmpService.surge(amp, ally);
            AmpService.surge(other, ally);
            LivingEntity foe = pillager(h, ally, 2, 20);
            float before = foe.getHealth();
            foe.invulnerableTime = 0;
            foe.hurt(ally.damageSources().playerAttack(ally), 4);
            h.assertTrue(Math.abs(before - foe.getHealth() - 5) < .01F, "Two amplifiers don't stack: still 5, got " + (before - foe.getHealth()));
            foe.discard();
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify", timeoutTicks = 200)
    public static void overchargingTooSoonHurtsAndAnOverchargeEndsInABacklash(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            ServerPlayer telepath = player(h, ModAbilities.TELEPATHY_ID, 1.5);
            telepath.setHealth(20);
            h.assertTrue(AmpService.overcharge(amp, telepath), "An overcharge lands");
            h.assertTrue(Amplification.overcharged(telepath), "The telepath is overcharged");
            h.assertTrue(AmpService.charge(amp) <= 50, "An overcharge spends 50 Charge");
            AmpService.addCharge(amp, 100);
            float before = telepath.getHealth();
            AmpService.overcharge(amp, telepath);
            h.assertTrue(before - telepath.getHealth() >= 3.9F, "Overcharging again within 30 seconds hurts");
            h.runAfterDelay(Amplification.OVERCHARGE_TICKS + 5, () -> guarded(h, () -> {
                h.assertFalse(Amplification.overcharged(telepath), "The overcharge has ended");
                h.assertTrue(Amplification.surged(telepath), "The Surge under it lasts on");
                h.assertTrue(Amplification.locked(telepath, ModAbilities.TELEPATHY_ID), "A thousand voices: telepathy is locked for a moment");
                cleanup(h);
                h.succeed();
            }));
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void aHoldWithoutChargeIsASurgeInstead(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = player(h, ModAbilities.AMPLIFICATION_ID, 0);
            AmpService.spend(amp, 100);
            ServerPlayer ally = player(h, ModAbilities.FLIGHT_ID, 1.5);
            AmpService.overcharge(amp, ally);
            h.assertTrue(Amplification.surged(ally) && !Amplification.overcharged(ally), "Below 50 Charge, a held Supercharge is a Surge");
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void redLightningKnocksOutRatherThanKills(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            LivingEntity foe = pillager(h, amp, 3, 3);
            AmpService.bolt(amp, false);
            h.assertTrue(foe.isAlive(), "A bolt knocks out rather than kills");
            h.assertTrue(foe.getHealth() <= 1.01F, "It leaves half a heart, got " + foe.getHealth());
            h.assertTrue(foe.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The foe is stunned");
            foe.discard();
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void theSecondComingKills(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h, AmpSkills.SECOND_COMING);
            LivingEntity foe = pillager(h, amp, 3, 3);
            AmpService.bolt(amp, false);
            h.assertFalse(foe.isAlive(), "With The Second Coming, red lightning kills");
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void shockTherapyClearsMindControlAndStuns(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            ServerPlayer ally = player(h, ModAbilities.QUAKE_ID, 1.5);
            ally.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
            ally.addEffect(new MobEffectInstance(ModItems.DAMPENED, 200, 0));
            int cleared = AmpService.shock(amp, ally);
            h.assertTrue(cleared >= 2, "Confusion and slowness are cleared");
            h.assertFalse(ally.hasEffect(MobEffects.CONFUSION) || ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Nothing is left");
            h.assertTrue(ally.hasEffect(ModItems.DAMPENED), "A dampener's hold needs Steady Hands");
            ServerPlayer steady = amplifier(h, AmpSkills.STEADY_HANDS);
            AmpService.shock(steady, ally);
            h.assertFalse(ally.hasEffect(ModItems.DAMPENED), "Steady Hands clears it too");
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void jumpstartOpensAnIronDoor(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            BlockPos lower = h.absolutePos(new BlockPos(5, 2, 6));
            var level = h.getLevel();
            level.setBlock(lower, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
            level.setBlock(lower.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            h.assertTrue(AmpService.jumpBlock(amp, lower, AmpService.JUMPSTART_TICKS), "Jumpstart takes the door");
            h.assertTrue(level.getBlockState(lower).getValue(BlockStateProperties.OPEN), "The iron door swings open");
            level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void burnoutAndAnOverloadSwitchAPowerOff(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h, AmpSkills.BURNOUT, AmpSkills.OVERCHARGE_ENEMY);
            LivingEntity foe = pillager(h, amp, 3, 20);
            AmpSkills.burnout(amp, foe);
            h.assertTrue(foe.hasEffect(ModItems.DAMPENED), "Burnout switches a power off (a dampener on one body)");
            Amplification.markWild(foe, 200);
            float before = foe.getHealth();
            h.assertTrue(Amplification.misfire(foe), "An overloaded body's next move goes wild");
            h.assertTrue(foe.getHealth() < before, "A misfire hurts the one who cast it");
            h.assertFalse(Amplification.misfire(foe), "It misfires once");
            foe.discard();
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify", skyAccess = true)
    public static void vanFullKnowsAClosedRoomAndKnocksOut(GameTestHelper h) {
        guarded(h, () -> {
            var level = h.getLevel();
            ServerPlayer amp = amplifier(h, AmpSkills.VAN_FULL);
            h.assertFalse(AmpSkills.enclosed(level, amp.getEyePosition()), "Out under the sky is not closed in");
            BlockPos feet = amp.blockPosition();
            List<BlockPos> walls = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 3, 2))) {
                if (Math.abs(pos.getX() - feet.getX()) == 2 || Math.abs(pos.getZ() - feet.getZ()) == 2 || pos.getY() == feet.getY() + 3) {
                    if (level.getBlockState(pos).isAir()) { level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3); walls.add(pos.immutable()); }
                }
            }
            h.assertTrue(AmpSkills.enclosed(level, amp.getEyePosition()), "A small stone room is closed in");
            LivingEntity foe = pillager(h, amp, 1, 4), weak = pillager(h, amp, -1, 1.5F);
            AmpSkills.vanFull(amp);
            h.assertTrue(Math.abs(foe.getHealth() - 2) < .01F, "30 Charge deals 1, doubled in a closed room: " + foe.getHealth());
            h.assertTrue(weak.isAlive() && Math.abs(weak.getHealth() - 1) < .01F, "Van Full knocks out rather than kills: " + weak.getHealth());
            for (BlockPos pos : walls) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            foe.discard();
            weak.discard();
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify", timeoutTicks = 200)
    public static void aThousandTimesOverchargesEveryoneNearWithoutBacklash(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            ServerPlayer a = findable(h, ModAbilities.QUAKE_ID, 1.5);
            ServerPlayer b = findable(h, ModAbilities.TELEPATHY_ID, -1.5);
            int count = AmpService.thousandRelease(amp);
            h.assertTrue(count >= 2, "Both allies are overcharged, got " + count);
            h.assertTrue(Amplification.overcharged(a) && Amplification.overcharged(b), "Each is overcharged");
            h.assertTrue(Amplification.BOOSTS.get(a.getUUID()).safe, "A Thousand Times gives no backlash");
            h.assertFalse(Amplification.overcharged(amp), "Never the amplifier");
            h.assertTrue(AmpService.charge(amp) == 0, "The Charge is spent");
            h.runAfterDelay(Amplification.OVERCHARGE_TICKS + 5, () -> guarded(h, () -> {
                h.assertFalse(Amplification.locked(b, ModAbilities.TELEPATHY_ID), "No backlash: telepathy is free");
                cleanup(h);
                h.succeed();
            }));
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void daphnesRunComesOutInThePast(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            ServerPlayer speedster = player(h, ModAbilities.SUPERSPEED_ID, 1.5);
            long now = h.getLevel().getGameTime();
            Vec3 then = speedster.position();
            for (int i = 0; i < 20; i++) com.jayptucker.heroesevolved.time.SpaceTimeService.record(speedster, now - 80 + i * 4);
            speedster.teleportTo(then.x + 4, then.y, then.z);
            speedster.absMoveTo(then.x + 4, then.y, then.z);
            AmpService.overcharge(amp, speedster);
            h.assertTrue(AmpTuned.daphne(speedster), "Daphne's run finds a moment three seconds back");
            h.assertTrue(speedster.position().distanceTo(then) < .5, "The speedster comes out where they were, got " + speedster.position());
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void braveNewWorldFoldsFriendsAlong(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            ServerPlayer traveller = player(h, ModAbilities.TIME_MANIPULATION_ID, 1.5);
            ServerPlayer friend = findable(h, ModAbilities.QUAKE_ID, 3);
            AmpService.overcharge(amp, traveller);
            Vec3 from = traveller.position(), to = from.add(0, 0, 5);
            int folded = AmpTuned.folded(traveller, from, to);
            h.assertTrue(folded >= 1, "Friends near the traveller fold along");
            h.assertTrue(friend.position().distanceTo(to) < 3, "The friend lands beside the traveller, got " + friend.position());
            h.assertTrue(AmpTuned.folded(traveller, to, from) == 0, "Only the next fold");
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void tunedPowersGetTheirBiggerVersions(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            ServerPlayer bender = player(h, ModAbilities.HYDROKINESIS_ID, 1.5);
            com.jayptucker.heroesevolved.hydro.HydroService.fill(bender, 30);
            AmpService.surge(amp, bender);
            com.jayptucker.heroesevolved.hydro.HydroService.draw(bender, 25);
            com.jayptucker.heroesevolved.hydro.HydroService.Draw draw = com.jayptucker.heroesevolved.hydro.HydroService.draw(bender, 25);
            h.assertFalse(draw.thin(), "A surged hydrokinetic's moves cost no water");
            ServerPlayer quake = player(h, ModAbilities.QUAKE_ID, -1.5);
            AmpService.overcharge(amp, quake);
            com.jayptucker.heroesevolved.quake.QuakeKit.strain(quake, 40);
            h.assertTrue(com.jayptucker.heroesevolved.quake.QuakeKit.strain(quake) == 0, "Overcharged, Quake builds no Strain");
            h.assertTrue(com.jayptucker.heroesevolved.quake.QuakeKit.power(quake) == 1.5F, "and hits as if at full Strain");
            ServerPlayer vibe = player(h, ModAbilities.VIBE_ID, 3);
            AmpService.addCharge(amp, 100);
            AmpService.overcharge(amp, vibe);
            Vec3 aimed = vibe.position();
            h.assertFalse(Amplification.overshoot(vibe, aimed).equals(aimed), "An overcharged breach overshoots");
            cleanup(h);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void holdHandsSharesAPartnersSurge(GameTestHelper h) {
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h, AmpSkills.PARTNER, AmpSkills.HOLD_HANDS, AmpSkills.BREAKTHROUGH);
            ServerPlayer partner = findable(h, ModAbilities.QUAKE_ID, 1.5);
            ServerPlayer friend = findable(h, ModAbilities.FLIGHT_ID, 3.2);
            AmpService.bond(amp, partner);
            AmpService.surge(amp, partner);
            h.assertTrue(Amplification.tierBonus(partner) == 1, "Breakthrough: a partner on a Surge reaches a tier higher");
            h.assertTrue(AmpSkills.holdHands(amp) >= 1, "Someone is holding the partner's hand");
            h.assertTrue(Amplification.surged(friend), "The friend shares the Surge");
            h.assertTrue(Amplification.tierBonus(friend) == 0, "Only the partner breaks through");
            cleanup(h);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------
    // Every power, boosted by us: one test per power, named boosted.<power>
    // ------------------------------------------------------------------

    /** What a test can see of one of a power's lines: its Surge, its Overcharge, or what is left once it ends. */
    private interface Check { void check(GameTestHelper h, ServerPlayer ally, LivingEntity foe, float foeBefore); }

    /** One row of the table in docs/power-amplification.md, as far as a test can see it. */
    private record Boosted(ResourceLocation power, Check surge, Check over, Check after) {}

    private static final Check NONE = (h, ally, foe, before) -> {};

    private static Boosted boosted(ResourceLocation power, Check surge, Check over, Check after) {
        return new Boosted(power, surge, over, after);
    }

    private static Check hurtFoe(String what) {
        return (h, ally, foe, before) -> h.assertTrue(!foe.isAlive() || foe.getHealth() < before, what + ": the foe was hit");
    }

    private static Check effect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, String what) {
        return (h, ally, foe, before) -> h.assertTrue(ally.hasEffect(effect), "Backlash: " + what);
    }

    private static Check locked(ResourceLocation power, String what) {
        return (h, ally, foe, before) -> h.assertTrue(Amplification.locked(ally, power), "Backlash: " + what);
    }

    private static Check both(Check a, Check b) {
        return (h, ally, foe, before) -> { a.check(h, ally, foe, before); b.check(h, ally, foe, before); };
    }

    /** Every power but our own, in the table's order. {@link #everyPowerIsBoostedByTheTest} keeps it complete. */
    static final List<Boosted> BOOSTED = List.of(
            boosted(ModAbilities.SUPERSPEED_ID, NONE, NONE, (h, a, f, b) ->
                    h.assertTrue(com.jayptucker.heroesevolved.speed.SpeedFriction.heat(a) > 0, "Backlash: the friction catches up")),
            boosted(ModAbilities.TIME_MANIPULATION_ID, NONE, NONE,
                    both(effect(MobEffects.CONFUSION, "everyone lands dazed"), effect(MobEffects.MOVEMENT_SLOWDOWN, "and slowed"))),
            boosted(ModAbilities.TELEPATHY_ID, NONE, NONE, locked(ModAbilities.TELEPATHY_ID, "telepathy won't answer")),
            boosted(ModAbilities.VIBE_ID, NONE, (h, a, f, b) -> {
                Vec3 aimed = a.position();
                h.assertFalse(Amplification.overshoot(a, aimed).equals(aimed), "To the moon: an overcharged breach overshoots");
            }, NONE),
            boosted(ModAbilities.FLIGHT_ID, NONE, NONE, effect(MobEffects.CONFUSION, "your head spins")),
            boosted(ModAbilities.PYROKINESIS_ID,
                    (h, a, f, b) -> h.assertTrue(com.jayptucker.heroesevolved.pyro.PyroService.isBlue(a), "A surged pyrokinetic burns blue"),
                    NONE,
                    (h, a, f, b) -> h.assertTrue(a.getRemainingFireTicks() > 0, "Backlash: the white fire singes you")),
            boosted(ModAbilities.ELECTROKINESIS_ID, NONE, NONE,
                    both(hurtFoe("The storm"), (h, a, f, b) -> h.assertTrue(a.getHealth() < a.getMaxHealth(), "Backlash: the last bolt finds you"))),
            boosted(ModAbilities.HYDROKINESIS_ID,
                    (h, a, f, b) -> {
                        com.jayptucker.heroesevolved.hydro.HydroService.fill(a, 30);
                        com.jayptucker.heroesevolved.hydro.HydroService.draw(a, 25);
                        h.assertFalse(com.jayptucker.heroesevolved.hydro.HydroService.draw(a, 25).thin(), "A surged hydrokinetic's moves cost no water");
                    },
                    (h, a, f, b) -> h.assertTrue(com.jayptucker.heroesevolved.hydro.HydroService.water(a)
                            >= com.jayptucker.heroesevolved.hydro.HydroService.capacity(a), "From the air itself: the reservoir fills"),
                    both((h, a, f, b) -> h.assertTrue(com.jayptucker.heroesevolved.hydro.HydroService.soaked(a), "Backlash: you end up Soaked"),
                            effect(MobEffects.MOVEMENT_SLOWDOWN, "and slowed"))),
            boosted(ModAbilities.CRYOKINESIS_ID, NONE,
                    (h, a, f, b) -> h.assertTrue(com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(f), "Flash freeze: the foe freezes solid"),
                    both((h, a, f, b) -> h.assertTrue(a.getTicksFrozen() > 0, "Backlash: frostbite"), effect(MobEffects.MOVEMENT_SLOWDOWN, "and slowed"))),
            boosted(ModAbilities.QUAKE_ID,
                    (h, a, f, b) -> {
                        com.jayptucker.heroesevolved.quake.QuakeKit.strain(a, 40);
                        int strain = com.jayptucker.heroesevolved.quake.QuakeKit.strain(a);
                        h.assertTrue(strain == 20, "Surged, Strain builds half as fast: 40 became " + strain);
                    },
                    (h, a, f, b) -> h.assertTrue(com.jayptucker.heroesevolved.quake.QuakeKit.power(a) == 1.5F, "Aftershock: every move hits as at full Strain"),
                    (h, a, f, b) -> h.assertTrue(com.jayptucker.heroesevolved.quake.QuakeKit.strain(a) >= 89, "Backlash: all the Strain comes due")),
            boosted(ModAbilities.SONIC_RESONANCE_ID, NONE,
                    (h, a, f, b) -> h.assertTrue(com.jayptucker.heroesevolved.sonic.SonicService.resonance(a) >= 100, "Glass-breaker: Resonance is full"),
                    locked(ModAbilities.SONIC_RESONANCE_ID, "your voice gives out")),
            boosted(ModAbilities.TELEKINESIS_ID, NONE,
                    (h, a, f, b) -> h.assertTrue(f.hasEffect(MobEffects.LEVITATION), "Everything at once: the foe in front rises"),
                    (h, a, f, b) -> h.assertTrue(a.getHealth() < a.getMaxHealth(), "Backlash: a nosebleed while it holds")),
            boosted(ModAbilities.PHASING_ID, NONE, NONE, effect(MobEffects.MOVEMENT_SLOWDOWN, "you come out heavy and slow")),
            boosted(ModAbilities.INVISIBILITY_ID, NONE, NONE,
                    (h, a, f, b) -> h.assertTrue(PlayerEnergyService.getEnergy(a) <= 1, "Backlash: the dome takes all your stamina, left " + PlayerEnergyService.getEnergy(a))),
            boosted(ModAbilities.REGENERATION_ID, NONE,
                    (h, a, f, b) -> com.jayptucker.heroesevolved.regeneration.RegenerationService.addRegrowth(a, 50),
                    (h, a, f, b) -> h.assertTrue(com.jayptucker.heroesevolved.regeneration.RegenerationService.regrowth(a) == 0, "Backlash: the Regrowth is spent")),
            boosted(ModAbilities.RADIATION_ID, NONE, NONE,
                    both((h, a, f, b) -> h.assertTrue(!f.isAlive() || com.jayptucker.heroesevolved.radiation.RadiationSickness.stage(f) > 0, "The Core pours out: the foe is dosed"),
                            (h, a, f, b) -> h.assertTrue(com.jayptucker.heroesevolved.radiation.RadiationService.mass(a) >= 40, "Backlash: 40 Core floods back"))),
            boosted(ModAbilities.TECHNOPATHY_ID, NONE, NONE, NONE),
            boosted(ModAbilities.MATTER_ASSEMBLY_ID, NONE, NONE, effect(MobEffects.WEAKNESS, "you're worn out")),
            boosted(ModAbilities.MIMICRY_ID, NONE,
                    (h, a, f, b) -> h.assertTrue(Amplification.cost(a, ModAbilities.MIMICRY_ID, ResourceLocation.fromNamespaceAndPath(com.jayptucker.heroesevolved.HeroesEvolved.MOD_ID, "mimicry_echo_1")) == 0
                            && Amplification.cost(a, ModAbilities.MIMICRY_ID) > 0, "Free echoes: an overcharged mimic's Echoes (only) cost nothing"),
                    locked(ModAbilities.MIMICRY_ID, "the Echoes go quiet")),
            boosted(ModAbilities.SHAPESHIFTING_ID, NONE,
                    (h, a, f, b) -> h.assertTrue(a.getScale() > 1.5F, "Apex now: the shapeshifter grows, scale " + a.getScale()),
                    both((h, a, f, b) -> h.assertTrue(a.getScale() < 1.05F, "Apex ends with the Overcharge, scale " + a.getScale()),
                            locked(ModAbilities.SHAPESHIFTING_ID, "Shapeshifting won't answer"))));

    /** Surge an ally with this power, then Overcharge them, and check what every power gets and what this one does. */
    private static void boost(GameTestHelper h, Boosted power) {
        String name = power.power().getPath();
        guarded(h, () -> {
            ServerPlayer amp = amplifier(h);
            ServerPlayer ally = player(h, power.power(), 1.5);
            // In the player list, as a real player is: the speedster's friction heat is kept only for listed players.
            listed(h, ally);
            LivingEntity foe = pillager(h, ally, 2.5, 24);
            h.assertTrue(AmpService.surge(amp, ally), name + ": a Surge lands");
            h.assertTrue(Amplification.surged(ally) && !Amplification.overcharged(ally), name + ": surged, not overcharged");
            h.assertTrue(ally.hasEffect(ModItems.SUPERCHARGED), name + ": wears the Supercharged icon");
            h.assertTrue(Amplification.cost(ally, power.power()) == Amplification.SURGE_COST, name + ": moves cost a quarter less");
            h.assertTrue(Amplification.cooldown(ally, power.power()) == Amplification.SURGE_COOLDOWN, name + ": cooldowns run a quarter faster");
            h.assertTrue(Amplification.drain(ally) == Amplification.SURGE_DRAIN, name + ": held powers drain half as fast");
            h.assertTrue(Amplification.reach(ally) == 2, name + ": aims reach twice as far");
            // A plain blow from the ally, so a power's own punch (Electrokinesis's shock) doesn't add to what is measured.
            var plain = new DamageSource(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC), ally);
            float before = foe.getHealth();
            foe.invulnerableTime = 0;
            foe.hurt(plain, 4);
            h.assertTrue(Math.abs(before - foe.getHealth() - 5) < .01F, name + ": a blow of 4 lands as 5, got " + (before - foe.getHealth()));
            power.surge().check(h, ally, foe, before);

            ally.setHealth(ally.getMaxHealth());
            float foeBefore = foe.getHealth();
            h.assertTrue(AmpService.overcharge(amp, ally), name + ": an Overcharge lands");
            h.assertTrue(Amplification.overcharged(ally), name + ": overcharged");
            power.over().check(h, ally, foe, foeBefore);
            h.runAfterDelay(Amplification.OVERCHARGE_TICKS + 5, () -> guarded(h, () -> {
                h.assertFalse(Amplification.overcharged(ally), name + ": the Overcharge has ended");
                h.assertTrue(Amplification.surged(ally), name + ": the Surge under it lasts on");
                power.after().check(h, ally, foe, foeBefore);
                if (foe.isAlive()) foe.discard();
                cleanup(h);
                h.succeed();
            }));
        });
    }

    @net.minecraft.gametest.framework.GameTestGenerator
    public static List<net.minecraft.gametest.framework.TestFunction> everyPowerBoosted() {
        List<net.minecraft.gametest.framework.TestFunction> tests = new ArrayList<>();
        for (Boosted power : BOOSTED) {
            String name = power.power().getPath();
            // A batch each: the storm, the flash freeze and the Core reach six to twelve blocks, past the arena next door.
            tests.add(new net.minecraft.gametest.framework.TestFunction("amplify.boosted." + name, "boosted." + name,
                    "heroesevolved:rewind_empty", net.minecraft.world.level.block.Rotation.NONE, 200, 0, true, false, 1, 1, false,
                    h -> boost(h, power)));
        }
        return tests;
    }

    @GameTest(template = "rewind_empty", batch = "amplify")
    public static void everyPowerIsBoostedByTheTest(GameTestHelper h) {
        Set<ResourceLocation> listed = new java.util.HashSet<>();
        for (Boosted power : BOOSTED) listed.add(power.power());
        for (ResourceLocation power : com.jayptucker.heroesevolved.ability.registry.AbilityRegistry.ABILITIES.keySet())
            if (ModAbilities.rollable(power) && !power.equals(ModAbilities.AMPLIFICATION_ID))
                h.assertTrue(listed.contains(power), "Add " + power + " to AmplificationGameTests.BOOSTED");
        h.succeed();
    }
}
