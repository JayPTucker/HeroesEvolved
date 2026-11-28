package com.jayptucker.heroesevolved.ability;

import com.jayptucker.heroesevolved.energy.StaminaDrain;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * The floating-too-long kick on servers ({@link Airborne}, {@code mixin.ServerFloatingMixin}): it still counts floating
 * the mod didn't cause, and lets a player be while a power holds them up and for a grace period after.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class AirborneGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "airborne-test"));
        p.connection = new ServerGamePacketListenerImpl(p.getServer(), new Connection(PacketFlow.SERVERBOUND), p,
                CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public boolean hasChannel(ResourceLocation id) { return false; }
            @Override public void send(Packet<?> packet) {}
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 4, 2))));
        return p;
    }

    private static Field field(String name) {
        try {
            Field field = ServerGamePacketListenerImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Puts the connection where vanilla's movement check leaves a floating player: floating, {@code ticks} counted. */
    private static void floating(ServerPlayer p, int ticks) {
        try {
            field("clientIsFloating").setBoolean(p.connection, true);
            field("aboveGroundTickCount").setInt(p.connection, ticks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static int counted(ServerPlayer p) {
        try {
            return field("aboveGroundTickCount").getInt(p.connection);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @GameTest(template = "rewind_empty", batch = "airborne", timeoutTicks = Airborne.GRACE_TICKS + 60)
    public static void floatingKickSparesPowersButStillCountsTheRest(GameTestHelper h) {
        var p = player(h);
        floating(p, 10);
        p.connection.tick();
        h.assertTrue(counted(p) == 11, "Floating the mod didn't cause still counts toward the kick, got " + counted(p));

        Airborne.moved(p);
        floating(p, 79);
        p.connection.tick();
        h.assertTrue(counted(p) == 0, "Right after a power is used the air time is excused and the count starts over, got " + counted(p));

        h.runAfterDelay(Airborne.GRACE_TICKS + 5, () -> {
            floating(p, 10);
            p.connection.tick();
            h.assertTrue(counted(p) == 11, "Once the grace runs out, floating counts again, got " + counted(p));

            StaminaDrain.start(p, "airborne_test", 0, false, () -> {});
            try {
                floating(p, 79);
                p.connection.tick();
                h.assertTrue(counted(p) == 0, "A held power (a stamina drain) keeps its player excused, got " + counted(p));
            } finally {
                StaminaDrain.forget(p);
            }
            h.succeed();
        });
    }
}
