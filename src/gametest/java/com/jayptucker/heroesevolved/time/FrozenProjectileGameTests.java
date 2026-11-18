package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class FrozenProjectileGameTests {
    @GameTest(template = "rewind_empty", batch = "time.frozen_catch_remote")
    public static void serverWideTimeAllowsCatchesAfterWalkingAwayFromTheCastLocation(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "remote-catcher"));
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)));
        player.setPos(origin); player.setYRot(0); player.setXRot(0);
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
        PlayerProgressionService.setLevel(player, 5); PlayerEnergyService.restore(player, 999);
        var arrow = new Arrow(EntityType.ARROW, h.getLevel());
        try {
            TimeFieldTestSupport.serverWide(true);
            TimeStopService.start(player, 5);
            player.setPos(origin.add(100, 0, 0));
            arrow.setPos(player.getEyePosition().add(0, 0, 4)); arrow.setDeltaMovement(0, 0, -3);
            h.assertTrue(TimeStopService.shouldStopEntityTick(arrow), "The server-wide stop reaches the distant shot");
            h.assertTrue(ProjectileTimeService.canCatch(player, arrow), "Catching follows the actual server-wide field, not the original cast radius");
            TimeStopService.stop(player);
            player.setPos(origin); TimeSlowService.start(player, 5); player.setPos(origin.add(100, 0, 0));
            TimeStopService.shouldStopEntityTick(arrow);
            h.assertTrue(ProjectileTimeService.canCatch(player, arrow), "The same rule applies to server-wide slowed time");
            h.succeed();
        } finally {
            arrow.discard(); TimeStopService.stop(player); TimeSlowService.stop(player);
            TimeFieldTestSupport.serverWide(false); SpeedService.forget(player);
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.frozen_catch")
    public static void stillWorldCatchesIncomingAndOwnedProjectilesIncludingMotionlessFireballs(GameTestHelper h) {
        for (boolean owned : new boolean[] {false, true}) {
            Projectile[] shots = {
                    new Arrow(EntityType.ARROW, h.getLevel()), new Snowball(EntityType.SNOWBALL, h.getLevel()),
                    new SmallFireball(EntityType.SMALL_FIREBALL, h.getLevel()),
                    EntityType.SPECTRAL_ARROW.create(h.getLevel()), EntityType.TRIDENT.create(h.getLevel()),
                    EntityType.EGG.create(h.getLevel()), EntityType.ENDER_PEARL.create(h.getLevel()),
                    EntityType.EXPERIENCE_BOTTLE.create(h.getLevel()), EntityType.POTION.create(h.getLevel()),
                    EntityType.FIREBALL.create(h.getLevel()), EntityType.DRAGON_FIREBALL.create(h.getLevel()),
                    EntityType.WITHER_SKULL.create(h.getLevel()), EntityType.SHULKER_BULLET.create(h.getLevel()),
                    EntityType.WIND_CHARGE.create(h.getLevel()), EntityType.BREEZE_WIND_CHARGE.create(h.getLevel()),
                    EntityType.LLAMA_SPIT.create(h.getLevel()),
                    new FireworkRocketEntity(h.getLevel(), 0, 0, 0, new net.minecraft.world.item.ItemStack(Items.FIREWORK_ROCKET)),
                    com.jayptucker.heroesevolved.registry.ModEntities.EMP_GRENADE.get().create(h.getLevel()),
                    (Projectile) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("tacz", "bullet")).create(h.getLevel())
            };
            for (Projectile shot : shots) {
                var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "frozen-catcher"));
                player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
                player.setYRot(0); player.setXRot(0);
                player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
                PlayerProgressionService.setLevel(player, 5);
                PlayerEnergyService.restore(player, 999);
                shot.setPos(player.getEyePosition().add(0, 0, 4));
                if (owned) shot.setOwner(player);
                // Fireballs can start at rest and accelerate using their own power; freezing must still allow a catch.
                shot.setDeltaMovement(shot instanceof SmallFireball ? Vec3.ZERO : new Vec3(0, 0, -3));
                // Even skeleton arrows (DISALLOWED pickup in vanilla) can be taken out of stopped time.
                if (shot instanceof AbstractArrow arrow) arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                try {
                    TimeStopService.start(player, 5);
                    h.assertTrue(TimeStopService.shouldStopEntityTick(shot) && shot.getDeltaMovement().equals(Vec3.ZERO), "The shot is suspended");
                    h.assertTrue(ProjectileTimeService.canCatch(player, shot), "Still World grants a catch: " + shot.getType() + " owned=" + owned);
                    BlockPos cover = BlockPos.containing(player.getEyePosition().add(0, 0, 2));
                    h.getLevel().setBlockAndUpdate(cover, Blocks.STONE.defaultBlockState());
                    h.assertFalse(SpeedService.catchShot(player, shot), "Frozen shots cannot be caught through cover");
                    h.getLevel().removeBlock(cover, false);
                    h.assertTrue(SpeedService.catchShot(player, shot) && shot.isRemoved(), "Right-click catches the suspended projectile");
                    if (shot instanceof Arrow) h.assertTrue(player.getInventory().countItem(Items.ARROW) == 1, "The arrow is recovered");
                    if (shot instanceof Snowball) h.assertTrue(player.getInventory().countItem(Items.SNOWBALL) == 1, "The thrown item is recovered");
                    if (shot instanceof ThrownTrident) h.assertTrue(player.getInventory().countItem(Items.TRIDENT) == 1, "Tridents can be picked up");
                    if (shot instanceof SmallFireball || shot instanceof LargeFireball)
                        h.assertTrue(player.getInventory().countItem(Items.FIRE_CHARGE) == 1, "A caught fireball becomes a fire charge");
                    if (shot instanceof WitherSkull) h.assertTrue(player.getInventory().isEmpty(), "Wither skulls disappear without becoming items");
                    if (shot instanceof FireworkRocketEntity)
                        h.assertTrue(player.getInventory().countItem(Items.FIREWORK_ROCKET) == 1, "A caught rocket is recovered");
                } finally {
                    shot.discard(); TimeStopService.stop(player); SpeedService.forget(player);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "time.frozen_catch")
    public static void catchingRequiresControlOfTimeAndEmbeddedArrowsStayInTheirBlocks(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "embedded-catch"));
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        player.setYRot(0); player.setXRot(0);
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
        PlayerProgressionService.setLevel(player, 5); PlayerEnergyService.restore(player, 999);
        var arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setPos(player.getEyePosition().add(0, 0, 1)); arrow.setDeltaMovement(0, 0, 3);
        try {
            h.assertFalse(SpeedService.catchShot(player, arrow), "Having Space-Time alone does not grant a catch in ordinary time");
            BlockPos wall = BlockPos.containing(player.getEyePosition().add(0, 0, 3));
            h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            h.getLevel().tickNonPassenger(arrow);
            TimeStopService.start(player, 5); TimeStopService.shouldStopEntityTick(arrow);
            h.assertFalse(ProjectileTimeService.canCatch(player, arrow), "Embedded arrows are not floating projectiles");
            h.succeed();
        } finally { arrow.discard(); TimeStopService.stop(player); SpeedService.forget(player); }
    }
}
