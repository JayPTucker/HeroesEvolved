package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.time.ProjectileTimeService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Exercises the real entity tick hooks and TaCZ collision/movement code, including the runner's own rounds. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PerceptionProjectileGameTests {
    private static ServerPlayer runner(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "perception-shot"));
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
        PlayerProgressionService.setLevel(player, 5);
        return player;
    }

    private static Projectile bullet(GameTestHelper h) {
        return (Projectile) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("tacz", "bullet"))
                .create(h.getLevel());
    }

    @GameTest(template = "rewind_empty", batch = "speed.perception_owned_bullet")
    public static void theRunnersOwnTaczRoundsSlowAndResume(GameTestHelper h) {
        verifyShot(h, bullet(h), true);
    }

    @GameTest(template = "rewind_empty", batch = "speed.perception_incoming_bullet")
    public static void incomingTaczRoundsSlowAndResume(GameTestHelper h) {
        verifyShot(h, bullet(h), false);
    }

    @GameTest(template = "rewind_empty", batch = "speed.perception_owned_arrow")
    public static void theRunnersOwnArrowsFollowTheSameRule(GameTestHelper h) {
        verifyShot(h, new Arrow(EntityType.ARROW, h.getLevel()), true);
    }

    private static void verifyShot(GameTestHelper h, Projectile shot, boolean owned) {
        var player = runner(h);
        var shooter = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "other-shooter"));
        shot.setOwner(owned ? player : shooter);
        Vec3 fullSpeed = new Vec3(0, 0, ProjectileTimeService.isTaczBullet(shot) ? 60 : 3);
        shot.setPos(player.getEyePosition().add(0, 0, 1));
        shot.setDeltaMovement(fullSpeed);
        try {
            AdvancedTechniques.startPerception(player);
            for (int tick = 0; tick < 4; tick++) {
                Vec3 before = shot.position();
                h.getLevel().tickNonPassenger(shot);
                double travelled = shot.position().distanceTo(before);
                h.assertTrue(!shot.isRemoved() && travelled > 0 && travelled <= ProjectileTimeService.CATCH_SPEED + .001,
                        "Perception must slow the actual shot before its collision trace, tick " + tick + ": " + travelled);
            }
            AdvancedTechniques.endPerception(player);
            h.assertTrue(shot.getDeltaMovement().equals(fullSpeed) && !shot.isNoGravity(),
                    "Ending Perception restores the shot's original full-precision speed and gravity");
            h.succeed();
        } finally {
            shot.discard();
            AdvancedTechniques.endPerception(player);
            SpeedService.forget(player);
        }
    }
}
