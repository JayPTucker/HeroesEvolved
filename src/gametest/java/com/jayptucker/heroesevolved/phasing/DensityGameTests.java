package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Density's earthbending core: Horse Stance and Root, footing, Rock Strike (stone, boulder, pebble), Earth Shackle and
 * Crush, the pillar, the wall and its shove, the Rising Mountain, tunnelling, a stance landing, and the trial.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class DensityGameTests {
    static ServerPlayer bender(GameTestHelper h, int tier, BlockPos at) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "earth-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(at))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.PHASING_ID).unlock(ModAbilities.PHASING_ID));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        p.setOnGround(true);
        return p;
    }

    static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true); mob.setPersistenceRequired();
        Vec3 feet = Vec3.atBottomCenterOf(h.absolutePos(at));
        mob.moveTo(feet.x, feet.y, feet.z, 180, 0);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        mob.setOnGround(true);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 3);
    }

    /** A stone floor (and the layer under it) across the whole arena. */
    static void floor(GameTestHelper h, Block block) { fill(h, 0, 0, 0, 11, 1, 11, block); }

    static void look(ServerPlayer p, Entity at) { p.lookAt(EntityAnchorArgument.Anchor.EYES, at.getBoundingBox().getCenter()); }

    static AbilityUseContext context(ServerPlayer p, int tier) { return new AbilityUseContext(p, ModAbilities.PHASING_ID, tier, false); }

    static void done(ServerPlayer p, Entity... others) {
        DensityService.forget(p);
        for (Entity e : others) if (e != null) { if (e instanceof LivingEntity l) DensityService.release(l); e.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "density.stance", timeoutTicks = 100)
    public static void horseStanceSetsTheFeetAndCostsToHold(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, 3, new BlockPos(5, 2, 5));
        try {
            h.assertTrue(DensityService.stanceKey(p, false) && DensityService.stance(p), "G drops you into Horse Stance");
            h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1, "Nothing knocks you back");
            h.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 6, "Stone-hard skin: " + p.getAttributeValue(Attributes.ARMOR));
        } catch (RuntimeException e) { done(p); throw e; }
        double stamina = PlayerEnergyService.getStamina(p);
        h.runAfterDelay(40, () -> {
            try {
                h.assertTrue(PlayerEnergyService.getStamina(p) < stamina - 3, "Holding it costs stamina: " + stamina + " -> " + PlayerEnergyService.getStamina(p));
                h.assertTrue(DensityService.root(p) > .99F, "Root fills twice as fast in stance, planted on stone: " + DensityService.root(p));
                h.assertTrue(DensityService.stanceKey(p, false) && !DensityService.stance(p), "G again: you stand");
                h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1, "And the stance's body goes");
                h.succeed();
            } finally { done(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.root", timeoutTicks = 120)
    public static void rootFillsOnEarthAndDrainsInTheAir(GameTestHelper h) {
        floor(h, Blocks.STONE);
        fill(h, 8, 1, 8, 10, 1, 10, Blocks.OAK_PLANKS);
        var p = bender(h, 3, new BlockPos(3, 2, 3));
        var planks = bender(h, 3, new BlockPos(9, 2, 9));
        DensityService.track(p);
        DensityService.track(planks);
        h.runAfterDelay(45, () -> {
            try {
                h.assertTrue(DensityService.root(p) > .99F, "Standing planted on stone, Root fills: " + DensityService.root(p));
                h.assertTrue(DensityService.rooted(p) > 1.24F, "Full Root: the earth hits a quarter harder");
                h.assertTrue(DensityService.root(planks) == 0, "On planks there is no earth to root in");
                h.assertTrue(DensityService.footing(planks) == null && DensityService.footing(p) != null, "Footing is earth, not wood");
                p.setOnGround(false);
            } catch (RuntimeException e) { done(p); done(planks); throw e; }
            h.runAfterDelay(5, () -> {
                try {
                    h.assertTrue(DensityService.root(p) < .7F, "In the air it drains: " + DensityService.root(p));
                    h.succeed();
                } finally { done(p); done(planks); }
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.rock", timeoutTicks = 80)
    public static void rockStrikeLiftsAStoneAndThePunchThrowsIt(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, 1, new BlockPos(5, 2, 2));
        var t = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 7));
        try {
            look(p, t);
            h.assertTrue(Stones.lift(p, 1) && Stones.held(p) == 1, "The stomp tears a stone up");
            h.assertTrue(!Stones.lift(p, 1), "One rock at a time");
            Stones.release(p);
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(Stones.strike(context(p, 1)) && Stones.held(p) == 0, "The punch throws it (the follow-up)");
                h.assertTrue(!Stones.strike(context(p, 1)), "Nothing left to throw: the next press lifts again");
            } catch (RuntimeException e) { done(p, t); throw e; }
            h.runAfterDelay(8, () -> {
                try {
                    h.assertTrue(t.getHealth() <= 100 - 7 + .01F, "6 + tier: " + t.getHealth());
                    h.succeed();
                } finally { done(p, t); }
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.boulder", timeoutTicks = 80)
    public static void holdingTheKeyGrowsABoulderAndNoEarthGivesAPebble(GameTestHelper h) {
        floor(h, Blocks.STONE);
        fill(h, 8, 1, 0, 10, 1, 3, Blocks.OAK_PLANKS);
        var p = bender(h, 2, new BlockPos(3, 2, 2));
        var deck = bender(h, 2, new BlockPos(9, 2, 1));
        try {
            h.assertTrue(Stones.lift(p, 2), "Lifted");
            h.assertTrue(Stones.lift(deck, 2), "On planks the stomp still finds something");
            var pebble = Stones.firstHeld(deck);
            h.assertTrue(pebble != null && Stones.kind(pebble) == Stones.Kind.PEBBLE, "With no earth underfoot it is only a pebble");
        } catch (RuntimeException e) { done(p); done(deck); throw e; }
        h.runAfterDelay(EarthGeometry.BOULDER_TICKS + 4, () -> {
            try {
                var rock = Stones.firstHeld(p);
                h.assertTrue(rock != null && Stones.size(rock) >= EarthGeometry.BOULDER_SIZE - .01F, "Held, it grows to a boulder: " + (rock == null ? -1 : Stones.size(rock)));
                h.succeed();
            } finally { done(p); done(deck); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.shackle", timeoutTicks = 80)
    public static void earthShackleHoldsAFoeAndCrushSqueezesIt(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, 2, new BlockPos(3, 2, 2));
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 6));
        double ground = t.getY();
        try {
            look(p, t);
            h.assertTrue(DensityService.shackle(p, 2, 8), "Stone climbs its legs");
            h.assertTrue(DensityService.embedded(t) && DensityService.holder(t) == p && t.getY() < ground - .5, "Held in the ground: " + (ground - t.getY()));
            look(p, t);
            h.assertTrue(DensityService.crush(context(p, 2)), "The second press crushes");
            h.assertTrue(DensityService.crushed(t) && t.getHealth() < 100, "It hurts: " + t.getHealth());
            h.assertTrue(!DensityService.crush(context(p, 2)) || DensityService.buried(t), "Without Entombed a second crush does nothing");
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.pillar", timeoutTicks = 260, skyAccess = true)
    public static void aPillarRisesUnderAFoeThrowsItAndCrumbles(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, 3, new BlockPos(2, 2, 2));
        var t = mob(h, EntityType.PILLAGER, new BlockPos(6, 2, 6));
        BlockPos base = h.absolutePos(new BlockPos(6, 2, 6));
        double floor = t.getY();
        try {
            Earthworks.pillarAt(p, base, Direction.UP, 3, Blocks.STONE.defaultBlockState(), 7);
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.runAfterDelay(6, () -> {
            try {
                for (int k = 0; k < 3; k++)
                    h.assertTrue(h.getLevel().getBlockState(base.above(k)).is(Blocks.STONE), "The pillar stands, block " + k);
                h.assertTrue(Earthworks.isConstruct(h.getLevel(), base.above(2)), "It is earth that will go back");
                h.assertTrue(t.getY() > floor + 2.5, "It carried the foe up: " + (t.getY() - floor));
                h.assertTrue(t.getHealth() < 100, "And hurt it: " + t.getHealth());
            } catch (RuntimeException e) { done(p, t); throw e; }
            h.runAfterDelay(EarthGeometry.PILLAR_TICKS + 10, () -> {
                try {
                    for (int k = 0; k < 3; k++)
                        h.assertTrue(h.getLevel().getBlockState(base.above(k)).isAir(), "It crumbles back to nothing, block " + k);
                    h.succeed();
                } finally { done(p, t); }
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.wall", timeoutTicks = 80)
    public static void aWallRisesStopsTheWayAndShovesForward(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, 3, new BlockPos(5, 2, 1));
        var t = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 6));
        try {
            h.assertTrue(Earthworks.raise(p, 3, true), "Shift + X: a wall");
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.runAfterDelay(5, () -> {
            try {
                var wall = Earthworks.of(p).stream().filter(c -> Earthworks.shape(c) == Earthworks.Shape.WALL).findFirst().orElseThrow();
                var cells = Earthworks.cells(wall);
                h.assertTrue(cells.size() == 15, "Five wide and three high: " + cells.size());
                int z = cells.get(0).getZ();
                h.assertTrue(Earthworks.shove(context(p, 3)), "Pressed again facing it: the shove");
                h.runAfterDelay(14, () -> {
                    try {
                        h.assertTrue(t.getHealth() < 100, "It rammed the foe: " + t.getHealth());
                        var now = Earthworks.cells(wall);
                        h.assertTrue(now.isEmpty() || now.get(0).getZ() > z + 2, "It moved on");
                        h.succeed();
                    } finally { done(p, t); }
                });
            } catch (RuntimeException e) { done(p, t); throw e; }
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.mountain", timeoutTicks = 140, skyAccess = true)
    public static void theRisingMountainLiftsEveryoneOnItAndSlamsThemDown(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, 5, new BlockPos(1, 2, 1));
        var t = mob(h, EntityType.PILLAGER, new BlockPos(7, 2, 7));
        double floor = t.getY();
        try {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(h.absolutePos(new BlockPos(7, 1, 7))));
            h.assertTrue(Earthworks.mountain(p, 5) && Earthworks.raising(p), "The ground cracks in a ring");
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.runAfterDelay(EarthGeometry.MOUNTAIN_WARN + 20, () -> {
            try {
                h.assertTrue(t.getY() > floor + 2.5, "The disc rose with the foe on it: " + (t.getY() - floor));
                Earthworks.releaseMountain(p);
            } catch (RuntimeException e) { done(p, t); throw e; }
            h.runAfterDelay(EarthGeometry.MOUNTAIN_FALL + 3, () -> {
                try {
                    h.assertTrue(!Earthworks.raising(p), "Let go, it slams down");
                    h.assertTrue(t.getHealth() < 85 && Math.abs(t.getY() - floor) < .6, "Everyone on it is slammed: " + t.getHealth());
                    h.succeed();
                } finally { done(p, t); }
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.tunnel")
    public static void aTunnelRunsThroughTheGroundAndSurfacesWithAnUppercut(GameTestHelper h) {
        fill(h, 1, 1, 1, 6, 3, 6, Blocks.STONE);
        var p = bender(h, 3, new BlockPos(3, 4, 3));
        var above = mob(h, EntityType.PILLAGER, new BlockPos(3, 4, 4));
        double floor = p.getY();
        try {
            h.assertTrue(DensityService.sinkOrSurface(p, 3), "Down into the stone");
            h.assertTrue(DensityService.sinking(p) && DensityService.sunk(p) && p.getY() < floor - 2, "Under the ground, flagged on both sides");
            for (int i = 0; i < 10; i++) DensityService.tick(h.getLevel().getServer());
            h.assertTrue(DensityService.breath(p) < DensityService.BREATH, "Breath runs down underground");
            h.assertTrue(DensityService.sinkOrSurface(p, 3), "Up again");
            h.assertTrue(!DensityService.sinking(p) && p.getY() >= floor - 0.01, "Back on top of the ground: " + p.getY());
            h.assertTrue(above.getHealth() < 100 && above.getDeltaMovement().y > 0, "Whoever stood above you is thrown up");
            h.succeed();
        } finally { done(p, above); }
    }

    @GameTest(template = "rewind_empty", batch = "density.fall")
    public static void aFallInHorseStanceLandsAsAShockwave(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, 3, new BlockPos(3, 2, 3));
        var zombie = mob(h, EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        try {
            DensityService.setNotch(p, DensityService.FULL_DENSE, 200);
            float health = p.getHealth();
            p.causeFallDamage(12, 1, p.damageSources().fall());
            h.assertTrue(p.getHealth() == health, "Stone doesn't break its legs");
            h.assertTrue(zombie.getHealth() < 100, "But the ground around it shakes");
            h.succeed();
        } finally { done(p, zombie); }
    }

    @GameTest(template = "rewind_empty", batch = "density.trial", timeoutTicks = 40)
    public static void movingEarthCountsTowardTheTrial(GameTestHelper h) {
        floor(h, Blocks.STONE);
        var p = bender(h, 3, new BlockPos(3, 2, 3));
        h.assertTrue(EvolutionTrials.special("phasing") && EvolutionTrials.trial("phasing").goal() == 2000, "Density evolves by moving 2,000 blocks of earth");
        int before = EvolutionTrials.progress(p, "phasing");
        try {
            Earthworks.pillarAt(p, h.absolutePos(new BlockPos(8, 2, 8)), Direction.UP, 3, Blocks.STONE.defaultBlockState(), 0);
        } catch (RuntimeException e) { done(p); throw e; }
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(EvolutionTrials.progress(p, "phasing") >= before + 3, "Each block raised counts");
                h.succeed();
            } finally { done(p); }
        });
    }
}
