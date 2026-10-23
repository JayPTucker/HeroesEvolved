package com.jayptucker.heroesevolved.quake;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class QuakePropulsionGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "quake-jump"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 35, 3))));
        player.setOnGround(false);
        return player;
    }

    @SuppressWarnings("unchecked")
    private static void beamTick(ServerPlayer player) {
        try {
            var field = QuakeService.class.getDeclaredField("BLASTS");
            field.setAccessible(true);
            Object blast = ((Map<UUID, ?>) field.get(null)).get(player.getUUID());
            var tick = QuakeService.class.getDeclaredMethod("tickQuakeBlast", ServerPlayer.class, blast.getClass(), long.class);
            tick.setAccessible(true);
            tick.invoke(null, player, blast, player.serverLevel().getGameTime());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @GameTest(template = "rewind_empty", batch = "quake.air_shockwave")
    public static void airborneShockwaveLaunchesOppositeEveryAimWithoutContact(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            for (float[] angle : new float[][]{{0, 0}, {90, 0}, {35, 42}, {-70, -35}, {0, 90}, {0, -90}}) {
                player.setYRot(angle[0]); player.setXRot(angle[1]);
                Vec3 jump = new Vec3(.15, .42, -.08);
                player.setDeltaMovement(jump);
                QuakeService.shockwave(player, 5);
                Vec3 impulse = player.getDeltaMovement().subtract(jump);
                Vec3 away = player.getLookAngle().normalize().reverse();
                helper.assertTrue(impulse.dot(away) > 1.8, "Air shot must provide a real launch without ground contact");
                helper.assertTrue(impulse.cross(away).lengthSqr() < .000001, "Recoil must oppose the full three-dimensional aim");
                helper.assertTrue(QuakeService.fallDamageMultiplier(player, 1) == 0, "Propulsion must cushion the next landing");
            }
            helper.succeed();
        } finally { QuakeService.stopAll(player); }
    }

    @GameTest(template = "rewind_empty", batch = "quake.jump_channel")
    public static void jumpDuringHeldBeamLaunchesAndCanSteerInAir(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            player.setOnGround(true); player.setXRot(0); player.setYRot(0);
            player.setDeltaMovement(Vec3.ZERO);
            QuakeService.startQuakeBlast(player, 4);
            beamTick(player);
            helper.assertTrue(player.getDeltaMovement().lengthSqr() < .00001, "Standing horizontal fire remains braced");
            player.setOnGround(false); player.setDeltaMovement(new Vec3(0, .42, 0));
            beamTick(player);
            helper.assertTrue(player.getDeltaMovement().z < -1.2 && Math.abs(player.getDeltaMovement().y - .42) < .00001,
                    "Jumping while holding the beam must launch backward and retain jump height");
            player.setXRot(90);
            double before = player.getDeltaMovement().y;
            beamTick(player);
            helper.assertTrue(player.getDeltaMovement().y > before + .14, "Aiming down midair must steer recoil upward without a block hit");
            QuakeService.stopQuakeBlast(player, false);
            helper.assertTrue(!QuakeService.isBlasting(player), "Release must end the propulsion beam");
            helper.succeed();
        } finally { QuakeService.stopAll(player); }
    }

    @GameTest(template = "rewind_empty", batch = "quake.recoil_limit")
    public static void recoilPreservesOtherMomentumAndCannotStackUnlimitedSpeed(GameTestHelper helper) {
        Vec3 aim = new Vec3(1, -.5, 1).normalize(), away = aim.reverse();
        Vec3 sideways = new Vec3(1, 0, -1).normalize().scale(.6);
        Vec3 velocity = sideways;
        for (int i = 0; i < 200; i++) velocity = QuakePropulsion.recoil(velocity, aim, .16, 2.8);
        helper.assertTrue(Math.abs(velocity.dot(away) - 2.8) < .000001, "Held recoil must have a speed limit");
        helper.assertTrue(velocity.subtract(away.scale(velocity.dot(away))).distanceToSqr(sideways) < .000001,
                "Recoil must preserve momentum perpendicular to the beam");
        Vec3 fast = away.scale(4).add(sideways);
        helper.assertTrue(QuakePropulsion.recoil(fast, aim, 1.4, 2.8).distanceToSqr(fast) < .000001,
                "Recoil must not truncate existing fast movement");
        helper.succeed();
    }
}
