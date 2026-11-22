package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.jayptucker.heroesevolved.phasing.DensityGameTests.*;

/** Density's earthbending tree: Boulder, Mountain and Listener, each node checked where it changes the kit. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class DensitySkillsGameTests {
    private static ServerPlayer bender(GameTestHelper h, BlockPos at, String... nodes) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "earth-skills")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(at))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.PHASING_ID).unlock(ModAbilities.PHASING_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.PHASING_ID.toString(),
                new SkillTreeData.Build(15, Set.of(nodes), Map.of())));
        p.setOnGround(true);
        try {
            var grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true);
            grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return p;
    }

    /**
     * A blow from {@code attacker} on the bender, through the tree's own damage hooks in their priority order. (The test
     * server's difficulty scales a mob's blow on a player to nothing, so a real hurt never reaches the event.)
     */
    private static LivingIncomingDamageEvent blow(ServerPlayer p, net.minecraft.world.entity.LivingEntity attacker, float amount) {
        var event = new LivingIncomingDamageEvent(p, new DamageContainer(p.damageSources().mobAttack(attacker), amount));
        DensitySkills.heard(event);
        if (!event.isCanceled()) DensitySkills.damage(event);
        return event;
    }

    // ------------------------------------------------------------------ Boulder

    @GameTest(template = "rewind_empty", batch = "density.skills.stacked")
    public static void stackedStonesLiftThreeDiscsThrownOneAPress(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 2), DensitySkills.STACKED);
        try {
            h.assertTrue(Stones.lift(p, 5) && Stones.held(p) == 3, "Three discs come up: " + Stones.held(p));
            h.assertTrue(Stones.strike(context(p, 5)) && Stones.held(p) == 2, "Each press throws one");
            h.succeed();
        } finally { done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.know", timeoutTicks = 60)
    public static void knowYourStoneSandBlinds(GameTestHelper h) {
        floor(h, Blocks.STONE);
        fill(h, 4, 1, 1, 6, 1, 3, Blocks.SAND);
        var p = bender(h, new BlockPos(5, 2, 2), DensitySkills.KNOW_STONE);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 7));
        try {
            look(p, t);
            h.assertTrue(Stones.lift(p, 5), "A stone of sand comes up");
            h.assertTrue(Stones.strike(context(p, 5)), "Thrown");
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.succeedWhen(() -> {
            h.assertTrue(t.hasEffect(MobEffects.BLINDNESS), "Sand in the eyes blinds");
            done(p, t);
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.gauntlet", timeoutTicks = 60)
    public static void anEarthGauntletPinsAFoeToTheWallBehindIt(GameTestHelper h) {
        floor(h, Blocks.STONE);
        fill(h, 3, 2, 9, 7, 4, 9, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 2), DensitySkills.GAUNTLET);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 7));
        try {
            look(p, t);
            h.assertTrue(Stones.gauntlet(p, 5), "The glove flies");
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.succeedWhen(() -> {
            h.assertTrue(Stones.pinned(t), "Pinned to the wall behind it");
            h.assertTrue(t.getHealth() < 100, "And hurt");
            done(p, t);
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.avalanche", timeoutTicks = 60)
    public static void anAvalancheRollsOverWhatIsInItsWay(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 1), DensitySkills.AVALANCHE);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 8));
        try {
            h.assertTrue(Stones.avalanche(p, 5), "Five boulders tear free");
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.succeedWhen(() -> {
            h.assertTrue(t.getHealth() < 100, "A boulder rolled over the foe");
            done(p, t);
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.shatter", timeoutTicks = 60)
    public static void shatterpointBurstsARockIntoShrapnelOffAWall(GameTestHelper h) {
        floor(h, Blocks.STONE);
        fill(h, 2, 2, 8, 8, 4, 8, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 2), DensitySkills.SHATTERPOINT);
        try {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 3, 8))));
            h.assertTrue(Stones.lift(p, 5) && Stones.strike(context(p, 5)), "Thrown at the wall");
        } catch (RuntimeException e) { done(p); throw e; }
        h.succeedWhen(() -> {
            h.assertTrue(Stones.flying().stream().anyMatch(r -> Stones.kind(r) == Stones.Kind.SHRAPNEL), "It bursts into shrapnel");
            done(p);
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.metal")
    public static void metalbendersStandOnMetal(GameTestHelper h) {
        floor(h, Blocks.IRON_BLOCK);
        var p = bender(h, new BlockPos(3, 2, 3));
        var toph = bender(h, new BlockPos(6, 2, 6), DensitySkills.METAL);
        try {
            h.assertTrue(DensityService.footing(p) == null, "Iron isn't earth to an ordinary bender");
            h.assertTrue(DensityService.footing(toph) != null, "A metalbender stands on it as earth");
            h.assertTrue(EarthGeometry.Material.of(Blocks.IRON_BLOCK.defaultBlockState()) == EarthGeometry.Material.METAL, "And it is metal");
            h.succeed();
        } finally { done(p); done(toph); }
    }

    // ------------------------------------------------------------------ Mountain

    @GameTest(template = "rewind_empty", batch = "density.skills.root", timeoutTicks = 60)
    public static void seiPingMaRootsFaster(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, new BlockPos(3, 2, 3));
        var deep = bender(h, new BlockPos(7, 2, 7), DensitySkills.SEI_PING_MA);
        DensityService.track(p);
        DensityService.track(deep);
        h.runAfterDelay(16, () -> {
            try {
                float a = DensityService.root(p), b = DensityService.root(deep);
                h.assertTrue(b > a * 1.3F, "Half again as fast: " + a + " vs " + b);
                h.succeed();
            } finally { done(p); done(deep); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.harden")
    public static void stoneSkinBanksBlowsAndPileDriverRootsTheRebound(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 5), DensitySkills.HARDEN, DensitySkills.PILE_DRIVER);
        var attacker = mob(h, EntityType.ZOMBIE, new BlockPos(5, 2, 8));
        var near = mob(h, EntityType.PILLAGER, new BlockPos(6, 2, 6));
        try {
            h.assertTrue(DensitySkills.harden(p) && DensityService.stance(p), "Stone Skin holds Horse Stance");
            blow(p, attacker, 6);
            h.assertTrue(DensitySkills.hardened(p) > 0, "The blow is banked: " + DensitySkills.hardened(p));
            h.assertTrue(DensitySkills.rebound(p), "Rebound");
            h.assertTrue(near.getHealth() < 100 && DensityService.embedded(near), "The shockwave roots the foe (Pile Driver)");
            h.succeed();
        } finally { done(p, attacker, near); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.pile")
    public static void aPunchInStanceDrivesAFoeIntoTheGround(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 5), DensitySkills.PILE_DRIVER);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 6));
        try {
            DensityService.setNotch(p, DensityService.FULL_DENSE, 200);
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), 4);
            h.assertTrue(DensityService.embedded(t), "Driven knee-deep");
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.quicksand", timeoutTicks = 60)
    public static void quicksandSwallowsWhoeverStandsInIt(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, new BlockPos(2, 2, 2), DensitySkills.SINKHOLE);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(7, 2, 7));
        try {
            DensitySkills.sinkholeAt(p, t.position());
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.runAfterDelay(DensityGeometry.SINKHOLE_WARN + 6, () -> {
            try {
                h.assertTrue(DensityService.embedded(t), "Waist-deep in quicksand");
                h.succeed();
            } finally { done(p, t); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.entomb")
    public static void setInStoneHoldsAndEntombedClosesOverACrushedFoe(GameTestHelper h) {
        floor(h, Blocks.STONE);
        fill(h, 0, -2, 0, 11, -1, 11, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 2), DensitySkills.SET_IN_STONE, DensitySkills.ENTOMBED);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 6));
        try {
            look(p, t);
            h.assertTrue(DensityService.shackle(p, 5, 8), "Shackled");
            look(p, t);
            h.assertTrue(DensityService.crush(context(p, 5)) && DensityService.crushed(t), "Crushed");
            look(p, t);
            h.assertTrue(DensityService.crush(context(p, 5)) && DensityService.buried(t), "A press on a crushed foe closes the ground over it");
            h.succeed();
        } finally { done(p, t); }
    }

    // ------------------------------------------------------------------ Listener

    @GameTest(template = "rewind_empty", batch = "density.skills.stomp")
    public static void seismicStompFeelsTheGround(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 5), DensitySkills.SEISMIC);
        try {
            h.assertTrue(DensityService.stanceKey(p, true) && DensitySkills.sensing(p), "Shift + G: the stomp, and four seconds of sight");
            h.assertTrue(!DensityService.stance(p), "The stomp isn't the stance");
            h.succeed();
        } finally { done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.tunnel")
    public static void graspFromBelowAndUprootTearAFoeOutOfTheGround(GameTestHelper h) {
        fill(h, 1, 1, 1, 6, 3, 6, Blocks.STONE);
        var p = bender(h, new BlockPos(3, 4, 3), DensitySkills.TUNNEL, DensitySkills.GRASP, DensitySkills.UPROOT);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 4, 4));
        try {
            h.assertTrue(DensityService.sinkOrSurface(p, 5) && DensityService.sinking(p), "Into the ground");
            h.assertTrue(DensitySkills.grasp(p, 5) && DensityService.embedded(t), "Grasped by the ankles");
            float before = t.getHealth();
            h.assertTrue(DensityService.sinkOrSurface(p, 5), "Up again");
            h.assertTrue(!DensityService.embedded(t) && Math.abs(before - t.getHealth() - 2 * (3 + 5)) < .01F, "Torn out for double the balanced uppercut: " + (before - t.getHealth()));
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.surface")
    public static void aTunnelAlwaysHasAWayUp(GameTestHelper h) {
        fill(h, 1, 1, 1, 6, 3, 6, Blocks.STONE);
        var p = bender(h, new BlockPos(3, 4, 3), DensitySkills.TUNNEL);
        double floor = p.getY();
        try {
            h.assertTrue(DensityService.sinkOrSurface(p, 5) && DensityService.sinking(p), "Into the ground");
            // The Tunnel key is on its cooldown now: its second press is a free follow-up that ignores it.
            h.assertTrue(DensitySkills.followUp(context(p, 5), 2, "") && !DensityService.sinking(p) && p.getY() >= floor - .01,
                    "Tunnel's second press brings you up, free");
            p.teleportTo(h.getLevel(), p.getX(), floor, p.getZ(), 0, 0);
            p.setOnGround(true);
            h.assertTrue(DensityService.sinkOrSurface(p, 5) && DensityService.sinking(p), "Down again");
            h.assertTrue(DensityService.stanceKey(p, false) && !DensityService.sinking(p) && !DensityService.stance(p),
                    "Horse Stance brings you up too (and doesn't drop you into the stance)");
            h.succeed();
        } finally { done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.surf")
    public static void earthSurfCarriesYouAlong(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 5), DensitySkills.SURF);
        try {
            h.assertTrue(Earthworks.surf(p, 5) && Earthworks.surfing(p), "The ground carries you");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED) && p.getAttributeValue(Attributes.STEP_HEIGHT) >= 1.5, "Fast, and a block at a step");
            h.succeed();
        } finally { done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.listen", timeoutTicks = 100)
    public static void waitAndListenHearsTheBlowAndAnswersIt(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, new BlockPos(5, 2, 3), DensitySkills.LISTEN);
        var attacker = mob(h, EntityType.ZOMBIE, new BlockPos(5, 2, 7));
        var t = mob(h, EntityType.PILLAGER, new BlockPos(2, 2, 2));
        try {
            DensityService.stanceKey(p, false);
        } catch (RuntimeException e) { done(p, attacker, t); throw e; }
        h.runAfterDelay(DensitySkills.LISTEN_STILL + 5, () -> {
            try {
                h.assertTrue(DensitySkills.listening(p), "Standing still in stance, you listen");
                h.assertTrue(blow(p, attacker, 6).isCanceled(), "The blow you heard never lands");
                h.assertTrue(DensityService.root(p) > .99F, "Your Root fills");
                t.invulnerableTime = 0;
                t.hurt(p.damageSources().playerAttack(p), 4);
                h.assertTrue(t.getHealth() <= 100 - 6 + .01F, "Your next blow lands half again as hard: " + t.getHealth());
            } catch (RuntimeException e) { done(p, attacker, t); throw e; }
            h.runAfterDelay(5, () -> {
                try {
                    h.assertTrue(attacker.getHealth() < 100, "A pillar answered the attacker");
                    h.succeed();
                } finally { done(p, attacker, t); }
            });
        });
    }
}
