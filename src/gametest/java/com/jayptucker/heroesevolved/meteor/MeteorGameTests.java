package com.jayptucker.heroesevolved.meteor;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.EclipseCalendar;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Starfall: the night it falls on, falling stars landing (and what they leave), and every answer to one. Meteors land
 * on the test's own floor; each test has its own batch, since a blast reaches past a plot.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class MeteorGameTests {
    private static Vec3 floor(GameTestHelper helper, double dx, double dz) {
        // The arena's stone floor is at absolutePos y 1 (a gap of air lies under it): stand on top of it.
        return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))).add(dx, 0, dz);
    }

    private static ServerPlayer player(GameTestHelper helper, Vec3 at, ResourceLocation power) {
        ServerPlayer p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "starfall-helper")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(at);
        p.setNoGravity(true);
        p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty()
                .assignDormant(power).unlock(power));
        helper.getLevel().addNewPlayer(p);
        return p;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig p = EntityType.PIG.create(helper.getLevel());
        if (p == null) throw new AssertionError("No pig");
        p.setPos(at);
        helper.getLevel().addFreshEntity(p);
        return p;
    }

    /** Crouches and looks at the meteor, every tick for {@code ticks}. */
    private static void work(GameTestHelper helper, ServerPlayer p, Meteor m, int ticks) {
        p.setShiftKeyDown(true);
        for (int t = 0; t <= ticks; t++) helper.runAfterDelay(t, () -> p.lookAt(EntityAnchorArgument.Anchor.EYES, m.sky()));
    }

    private static void cleanup(Meteor m, Entity... entities) {
        if (m != null) Meteors.cancel(m);
        for (Entity e : entities) {
            if (e == null) continue;
            if (e instanceof ServerPlayer p) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else e.discard();
        }
    }

    private static boolean meteoriteNear(GameTestHelper helper, Vec3 at, int r) {
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(at).offset(-r, -r, -r), BlockPos.containing(at).offset(r, r, r)))
            if (helper.getLevel().getBlockState(p).is(ModBlocks.HELIXITE_METEORITE.get())) return true;
        return false;
    }

    /** The blocks down the column under {@code at}, for messages. */
    private static String column(GameTestHelper helper, Vec3 at) {
        StringBuilder out = new StringBuilder();
        BlockPos top = BlockPos.containing(at);
        for (int dy = 2; dy >= -5; dy--)
            out.append(top.getY() + dy).append('=').append(helper.getLevel().getBlockState(top.above(dy)).getBlock().getDescriptionId()
                    .replace("block.minecraft.", "").replace("block.heroesevolved.", "")).append(' ');
        return out.toString();
    }

    /** What the crater floor round {@code at} is made of, for messages. */
    private static String survey(GameTestHelper helper, Vec3 at) {
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(at).offset(-7, -7, -7), BlockPos.containing(at).offset(7, 4, 7))) {
            var b = helper.getLevel().getBlockState(p).getBlock();
            if (b == net.minecraft.world.level.block.Blocks.AIR || b == net.minecraft.world.level.block.Blocks.STONE) continue;
            counts.merge(b.getDescriptionId().replace("block.minecraft.", "").replace("block.heroesevolved.", ""), 1, Integer::sum);
        }
        return counts.toString() + " top " + helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(at).getX(), BlockPos.containing(at).getZ());
    }

    private static int shardsNear(GameTestHelper helper, Vec3 at, double r) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(r),
                i -> i.getItem().is(ModItems.HELIXITE_SHARD.get())).stream().mapToInt(i -> i.getItem().getCount()).sum();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "meteor.night")
    public static void starfallIsTheNightBeforeAnEclipse(GameTestHelper helper) {
        int days = EclipseCalendar.intervalDays();
        long eclipseDay = days - 1, before = eclipseDay - 1, tpd = EclipseCalendar.TICKS_PER_DAY;
        helper.assertTrue(EclipseCalendar.isEclipseDay(eclipseDay), "Day " + eclipseDay + " is an Eclipse day");
        helper.assertTrue(Starfall.night(before * tpd + 14_000) && Starfall.night(before * tpd + 22_900),
                "The night before it, dusk to dawn, is starfall");
        helper.assertTrue(!Starfall.night(before * tpd + 12_000), "Not before dusk");
        helper.assertTrue(!Starfall.night((before - 1) * tpd + 14_000) && !Starfall.night(eclipseDay * tpd + 14_000),
                "Not on other nights, nor the Eclipse's own");
        Vec3 start = new Vec3(0, 200, 0), land = new Vec3(100, 0, 0);
        helper.assertTrue(MeteorGeometry.position(start, land, 0).equals(start) && MeteorGeometry.position(start, land, 1).equals(land),
                "Its path runs from where it starts to where it lands");
        helper.assertTrue(MeteorGeometry.position(start, land, 0.5F).distanceTo(start) < start.distanceTo(land) * 0.3,
                "Slow at first, faster as it nears");
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "meteor.lands")
    public static void aFallingStarLandsHurtsAndLeavesMeteorite(GameTestHelper helper) {
        Vec3 land = floor(helper, 0, 0);
        Pig close = pig(helper, floor(helper, 1.5, 0)), far = pig(helper, floor(helper, -9, -9));
        Meteor m = Meteors.drop(helper.getLevel(), land, false, 40, 0.5, null);
        float health = close.getHealth();
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertTrue(Meteors.get(m.id()) == m && m.left() > 0 && m.left() <= 12, "Still falling, nearly down: " + m.left());
                helper.assertTrue(m.sky().y > land.y && m.sky().distanceTo(land) < m.start().distanceTo(land), "Coming down out of the sky");
            } catch (RuntimeException e) { cleanup(m, close, far); throw e; }
        });
        helper.runAfterDelay(46, () -> {
            try {
                helper.assertTrue(Meteors.get(m.id()) == null, "It has landed");
                helper.assertTrue(!close.isAlive() || close.getHealth() < health, "Close by, it hurts: " + close.getHealth());
                helper.assertTrue(far.getHealth() >= far.getMaxHealth(), "Past its blast, nothing");
                helper.assertTrue(meteoriteNear(helper, land, 5), "It leaves Helixite meteorite in its crater: " + column(helper, land) + " " + survey(helper, land));
                helper.succeed();
            } finally { cleanup(null, close, far); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "meteor.gentle")
    public static void withoutMobGriefingItDropsItsShardsInstead(GameTestHelper helper) {
        var griefing = helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean was = griefing.get();
        griefing.set(false, helper.getLevel().getServer());
        Vec3 land = floor(helper, 0, 0);
        Meteor m = Meteors.drop(helper.getLevel(), land, false, 20, 1.2, null);
        helper.runAfterDelay(26, () -> {
            try {
                helper.assertTrue(!meteoriteNear(helper, land, 5), "No crater, no meteorite block");
                helper.assertTrue(helper.getLevel().getBlockState(BlockPos.containing(land).below()).isSolid(), "The ground is untouched: " + column(helper, land));
                helper.assertTrue(shardsNear(helper, land, 6) >= 3, "Its shards lie on the ground: " + shardsNear(helper, land, 6));
                helper.succeed();
            } finally { griefing.set(was, helper.getLevel().getServer()); cleanup(m); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "meteor.push")
    public static void aTelekineticPushesItAside(GameTestHelper helper) {
        Vec3 land = floor(helper, 0, 0);
        Meteor m = Meteors.drop(helper.getLevel(), land, false, 400, 0.3, null);
        ServerPlayer mover = player(helper, floor(helper, -4, 0).add(0, 1, 0), ModAbilities.TELEKINESIS_ID);
        ServerPlayer idle = player(helper, floor(helper, 4, 0).add(0, 1, 0), ModAbilities.QUAKE_ID);
        idle.lookAt(EntityAnchorArgument.Anchor.EYES, m.sky());
        work(helper, mover, m, 40);
        helper.runAfterDelay(42, () -> {
            try {
                helper.assertTrue(m.pushed() > 1.6 && m.land().distanceTo(land) > 1.6, "Two seconds of it, two blocks aside: " + m.pushed());
                helper.assertTrue(m.land().x > land.x, "Away from whoever pushes (they stand to its west): " + m.land());
                helper.assertTrue(m.helpers().stream().anyMatch(h -> h.player() == mover && h.help() == Meteor.Help.PUSH), "They are working on it");
                helper.succeed();
            } finally { cleanup(m, mover, idle); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "meteor.hold")
    public static void stoppedTimeHoldsItInTheSky(GameTestHelper helper) {
        Meteor m = Meteors.drop(helper.getLevel(), floor(helper, 0, 0), false, 400, 2.0, null);
        ServerPlayer keeper = player(helper, floor(helper, 3, 3).add(0, 1, 0), ModAbilities.TIME_MANIPULATION_ID);
        work(helper, keeper, m, 30);
        int[] before = new int[1];
        helper.runAfterDelay(5, () -> before[0] = m.elapsed());
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(m.elapsed() == before[0], "Held, its time doesn't run: " + before[0] + " then " + m.elapsed());
                keeper.setShiftKeyDown(false);
            } catch (RuntimeException e) { cleanup(m, keeper); throw e; }
        });
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(m.elapsed() > before[0] + 10, "Let go, it falls again: " + m.elapsed());
                helper.succeed();
            } finally { cleanup(m, keeper); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "meteor.shatter")
    public static void aSonicCracksAGreatOneIntoFallingStars(GameTestHelper helper) {
        Vec3 land = floor(helper, 0, 0);
        Meteor m = Meteors.drop(helper.getLevel(), land, true, 1200, 4.0, BlockPos.containing(land));
        ServerPlayer singer = player(helper, floor(helper, 5, 0).add(0, 1, 0), ModAbilities.SONIC_RESONANCE_ID);
        int before = Meteors.all().size();
        // Most of the way cracked already: the last of it is this sonic's.
        m.crack = MeteorGeometry.SHATTER_TICKS - 3;
        work(helper, singer, m, 6);
        helper.runAfterDelay(8, () -> {
            try {
                helper.assertTrue(Meteors.get(m.id()) == null, "Cracked through, the great meteor is gone");
                long stars = Meteors.all().stream().filter(x -> onRing(x, land)).count();
                helper.assertTrue(stars >= MeteorGeometry.SHARDS, "It falls as " + MeteorGeometry.SHARDS + " falling stars round its ring: " + stars
                        + " (" + before + " before)");
                helper.assertTrue(!EmergencyBand.live(m.call()), "Its own call ends");
                helper.succeed();
            } finally {
                for (Meteor x : Meteors.all()) if (onRing(x, land)) Meteors.cancel(x);
                cleanup(m, singer);
            }
        });
    }

    /** A falling star from this great meteor's ring. Across the ground only: a star lands on whatever is tallest there. */
    private static boolean onRing(Meteor star, Vec3 land) {
        return !star.great() && Math.hypot(star.land().x - land.x, star.land().z - land.z) <= MeteorGeometry.SHARD_SPREAD + 1;
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "meteor.brace")
    public static void aQuakerBracesTheGround(GameTestHelper helper) {
        Vec3 land = floor(helper, 0, 0);
        Meteor m = Meteors.drop(helper.getLevel(), land, false, 140, 5.0, null);
        ServerPlayer quaker = player(helper, floor(helper, 2, 2), ModAbilities.QUAKE_ID);
        quaker.setShiftKeyDown(true);
        helper.runAfterDelay(MeteorGeometry.BRACE_TICKS + 5, () -> {
            try {
                helper.assertTrue(m.braced() >= 1, "Five seconds crouched under it braces the ground: " + m.braced());
                quaker.setShiftKeyDown(false);
            } catch (RuntimeException e) { cleanup(m, quaker); throw e; }
        });
        helper.runAfterDelay(150, () -> {
            try {
                helper.assertTrue(Meteors.get(m.id()) == null, "It has landed");
                helper.assertTrue(!meteoriteNear(helper, land, 5), "The ground held: no crater");
                helper.assertTrue(shardsNear(helper, land, 6) >= 3, "Its shards lie on the ground instead");
                helper.succeed();
            } finally { cleanup(null, quaker); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "meteor.great")
    public static void aGreatMeteorCallsForHelpAndItsVillageRuns(GameTestHelper helper) {
        Villager v = EntityType.VILLAGER.create(helper.getLevel());
        v.moveTo(floor(helper, 2, 1));
        helper.getLevel().addFreshEntity(v);
        Meteor m = Meteors.great(helper.getLevel(), BlockPos.containing(floor(helper, 0, 0)), 1200);
        // The run is a walk target set every five ticks, which the brain drops when it can't path: look over a window.
        boolean[] ran = new boolean[1];
        for (int t = 1; t <= 24; t++)
            helper.runAfterDelay(t, () -> v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                    .ifPresent(w -> { if (w.getSpeedModifier() >= 0.9F) ran[0] = true; }));
        helper.runAfterDelay(26, () -> {
            try {
                helper.assertTrue(m.great() && EmergencyBand.live(m.call()) && m.call().trouble() == EmergencyBand.Trouble.METEOR,
                        "It calls for help, with a meteor's flare");
                helper.assertTrue(m.inVillage(v), "Its village is everyone round where it lands");
                helper.assertTrue(v.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "Inside its ring, they glow");
                helper.assertTrue(ran[0], "And run");
                helper.assertTrue(m.fall() == 1200 && MeteorGeometry.blast(true) == MeteorGeometry.GREAT_BLAST, "Its blast is a great one's");
                helper.succeed();
            } finally { cleanup(m, v); }
        });
    }
}
