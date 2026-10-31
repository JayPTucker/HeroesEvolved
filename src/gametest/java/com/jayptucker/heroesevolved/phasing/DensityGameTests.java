package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** The Density dial, walking through walls, Reach Through, Embed, Sink, and the two ultimates. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class DensityGameTests {
    private static ServerPlayer phaser(GameTestHelper h, int tier, BlockPos at) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "density-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(at))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.PHASING_ID).unlock(ModAbilities.PHASING_ID));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true); mob.setPersistenceRequired();
        Vec3 feet = Vec3.atBottomCenterOf(h.absolutePos(at));
        mob.moveTo(feet.x, feet.y, feet.z, 180, 0);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.Block block) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 3);
    }

    @GameTest(template = "rewind_empty", batch = "density.dial")
    public static void theDialTurnsTheBodyFromGhostToStone(GameTestHelper h) {
        var p = phaser(h, 3, new BlockPos(3, 2, 3));
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        try {
            h.assertTrue(DensityService.shift(p, false) && DensityService.notch(p) == DensityService.GHOST, "One press: Ghost");
            h.assertTrue(DensityService.shift(p, false) && DensityService.fullGhost(p), "Two: Full Ghost");
            h.assertTrue(!DensityService.shift(p, false), "There is nothing past Full Ghost");
            float health = p.getHealth();
            p.hurt(p.damageSources().mobAttack(zombie), 6);
            h.assertTrue(p.getHealth() == health, "Blows pass through a Full Ghost");
            for (int i = 0; i < 4; i++) DensityService.shift(p, true);
            h.assertTrue(DensityService.notch(p) == DensityService.FULL_DENSE, "Four presses with Shift: Full Dense");
            h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1 && p.getAttributeValue(Attributes.ARMOR) >= 8,
                    "Full Dense: no knockback, and armour of stone");
            DensityService.shift(p, false); DensityService.shift(p, false);
            h.assertTrue(DensityService.notch(p) == DensityService.NEUTRAL && p.getAttributeValue(Attributes.ARMOR) == 0, "Back to normal, the stone is gone");
            h.succeed();
        } finally { DensityService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.walls")
    public static void aGhostWalksThroughWallsButNeverBedrock(GameTestHelper h) {
        var p = phaser(h, 5, new BlockPos(3, 2, 3));
        var blocked = phaser(h, 5, new BlockPos(8, 2, 3));
        fill(h, 2, 2, 4, 4, 4, 5, Blocks.STONE);
        fill(h, 7, 2, 4, 9, 4, 4, Blocks.BEDROCK);
        try {
            DensityService.setNotch(p, DensityService.FULL_GHOST, 200);
            DensityService.setNotch(blocked, DensityService.FULL_GHOST, 200);
            // Walk up to the wall: a step, then a tick pressed against it.
            for (ServerPlayer walker : new ServerPlayer[] {p, blocked}) {
                walker.setPos(walker.position().add(0, 0, -0.3)); DensityService.tick(h.getLevel().getServer());
                walker.setPos(walker.position().add(0, 0, 0.3)); DensityService.tick(h.getLevel().getServer());
            }
            h.assertTrue(p.getZ() > h.absolutePos(new BlockPos(3, 2, 6)).getZ(), "The Ghost comes out beyond the wall, not inside it: z=" + p.getZ());
            h.assertTrue(blocked.getZ() < h.absolutePos(new BlockPos(8, 2, 4)).getZ(), "Bedrock stays solid even to a Ghost");
            h.assertTrue(EvolutionTrials.progress(p, "phasing") >= 1, "Blocks passed count toward the Evolved trial");
            h.succeed();
        } finally { DensityService.forget(p); DensityService.forget(blocked); }
    }

    @GameTest(template = "rewind_empty", batch = "density.reach")
    public static void reachThroughIgnoresArmourStopsHealingAndPullsGear(GameTestHelper h) {
        var p = phaser(h, 2, new BlockPos(3, 2, 3));
        var zombie = mob(h, EntityType.ZOMBIE, new BlockPos(3, 2, 5));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        try {
            h.assertTrue(DensityService.reach(p, 2), "The hand finds the zombie");
            h.assertTrue(zombie.getHealth() <= 100 - 7 + 0.01F, "Armour does nothing against a hand inside you: " + zombie.getHealth());
            float hurt = zombie.getHealth();
            zombie.heal(10);
            h.assertTrue(zombie.getHealth() == hurt, "For a while afterwards it cannot heal");
            h.assertTrue(zombie.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "Its chestplate came out with the hand");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, zombie.getBoundingBox().inflate(2),
                    i -> i.getItem().is(Items.DIAMOND_CHESTPLATE)).isEmpty(), "And lies at its feet");
            h.succeed();
        } finally { zombie.discard(); DensityService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.chest")
    public static void reachThroughAWallIntoTheChestBehindIt(GameTestHelper h) {
        var p = phaser(h, 1, new BlockPos(3, 2, 3));
        fill(h, 2, 2, 4, 4, 4, 4, Blocks.STONE);
        BlockPos chest = h.absolutePos(new BlockPos(3, 3, 5));
        h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((ChestBlockEntity) h.getLevel().getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 5));
        try {
            h.assertTrue(DensityService.reach(p, 1), "Through the stone into the chest");
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 5, "The diamonds are in your hand");
            h.assertTrue(!DensityService.reach(p, 1), "An empty chest gives nothing");
            h.succeed();
        } finally { DensityService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.embed", timeoutTicks = 60)
    public static void embedHoldsAnEnemyInTheFloorUntilItWorksLoose(GameTestHelper h) {
        var p = phaser(h, 2, new BlockPos(3, 2, 3));
        fill(h, 1, 1, 1, 5, 1, 8, Blocks.STONE);
        var pillager = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 6));
        double ground = pillager.getY();
        h.assertTrue(DensityService.embed(p, 2, 6), "The floor takes it");
        h.assertTrue(DensityService.embedded(pillager) && pillager.getY() < ground - 0.5, "Waist-deep in stone: " + (ground - pillager.getY()));
        pillager.invulnerableTime = 0;
        pillager.hurt(p.damageSources().playerAttack(p), 4);
        h.assertTrue(pillager.getHealth() <= 94.01F, "Stuck in the ground, it takes 50% more: " + pillager.getHealth());
        var second = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 6));
        h.assertTrue(DensityService.embed(second, p, 10, false, 0.45), "A short embed");
        h.runAfterDelay(20, () -> {
            try {
                h.assertTrue(!DensityService.embedded(second) && second.getY() >= ground - 0.05, "When it ends, it is back up on the floor: " + second.getY());
                h.succeed();
            } finally { pillager.discard(); second.discard(); DensityService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.sink")
    public static void sinkTravelsThroughTheGroundAndSurfacesWithAnUppercut(GameTestHelper h) {
        fill(h, 1, 1, 1, 6, 3, 6, Blocks.STONE);
        var p = phaser(h, 3, new BlockPos(3, 4, 3));
        p.setOnGround(true);
        var above = mob(h, EntityType.PILLAGER, new BlockPos(3, 4, 4));
        double floor = p.getY();
        try {
            h.assertTrue(DensityService.sinkOrSurface(p, 3), "Down into the stone");
            h.assertTrue(DensityService.sinking(p) && DensityService.sunk(p) && p.getY() < floor - 2, "Under the ground, flagged on both sides");
            for (int i = 0; i < 10; i++) DensityService.tick(h.getLevel().getServer());
            h.assertTrue(DensityService.breath(p) < DensityService.BREATH, "Breath runs down underground");
            h.assertTrue(DensityService.sinkOrSurface(p, 3), "Up again");
            h.assertTrue(!DensityService.sinking(p) && !DensityService.sunk(p) && p.getY() >= floor - 0.01, "Back on top of the ground: " + p.getY());
            h.assertTrue(above.getHealth() < 100 && above.getDeltaMovement().y > 0, "Whoever stood above you is thrown up");
            h.succeed();
        } finally { above.discard(); DensityService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.fall")
    public static void aFullDenseFallLandsAsAShockwave(GameTestHelper h) {
        fill(h, 1, 1, 1, 6, 1, 6, Blocks.STONE);
        var p = phaser(h, 3, new BlockPos(3, 2, 3));
        var zombie = mob(h, EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        try {
            DensityService.setNotch(p, DensityService.FULL_DENSE, 200);
            float health = p.getHealth();
            p.causeFallDamage(12, 1, p.damageSources().fall());
            h.assertTrue(p.getHealth() == health, "Stone doesn't break its legs");
            h.assertTrue(zombie.getHealth() < 100, "But the ground around it shakes");
            h.succeed();
        } finally { zombie.discard(); DensityService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.hollow")
    public static void hollowGroundBuriesEveryEnemyAroundYou(GameTestHelper h) {
        fill(h, 0, 1, 0, 11, 1, 11, Blocks.STONE);
        var p = phaser(h, 5, new BlockPos(5, 2, 5));
        var a = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 8));
        var b = mob(h, EntityType.PILLAGER, new BlockPos(8, 2, 5));
        try {
            h.assertTrue(DensityService.ultimate(p, 5), "The ground goes soft");
            h.assertTrue(DensityService.embedded(a) && DensityService.embedded(b), "Both enemies sink into it");
            DensityService.setNotch(p, DensityService.FULL_DENSE, 200);
            var c = mob(h, EntityType.PILLAGER, new BlockPos(5, 2, 7));
            h.assertTrue(DensityService.ultimate(p, 5) && c.getHealth() < 100 && !DensityService.embedded(c), "At Dense it is a Bedrock Slam instead");
            c.discard();
            h.succeed();
        } finally { a.discard(); b.discard(); DensityService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "density.trial")
    public static void densityHasItsOwnEvolvedTrial(GameTestHelper h) {
        h.assertTrue(EvolutionTrials.special("phasing") && EvolutionTrials.trial("phasing").goal() == 2000, "Density evolves by travelling 2 km through solid matter");
        h.succeed();
    }
}
