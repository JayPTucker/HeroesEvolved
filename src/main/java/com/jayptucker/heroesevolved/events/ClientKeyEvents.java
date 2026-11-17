package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.TelepathyClient;
import com.jayptucker.heroesevolved.client.sound.FlightBoostWindSound;
import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.gui.screen.AbilityWheelScreen;
import com.jayptucker.heroesevolved.network.ActionInputPayload;
import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.network.ClientLoadout;
import com.jayptucker.heroesevolved.skills.Loadout;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.jayptucker.heroesevolved.network.FlightForwardInputPayload;
import com.jayptucker.heroesevolved.network.FlightSpeedInputPayload;
import com.jayptucker.heroesevolved.network.SpeedStrideInputPayload;
import com.jayptucker.heroesevolved.speed.SpeedMovement;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT
)
public final class ClientKeyEvents {
    private static final boolean[] ABILITY_WAS_DOWN = new boolean[ModKeyMappings.ABILITY_KEYS.length];
    /** Game tick the wheel key went down, or -1 while it is up. */
    private static long wheelDownSince = -1;
    private static boolean wheelOpenedForPress;
    // Held longer than this, the wheel key opens the wheel; a shorter tap quick-casts.
    private static final long WHEEL_HOLD_TICKS = 4L;
    private static boolean flightForwardWasDown;
    private static boolean jumpWasDown;
    private static double speedScrollRemainder;
    private static boolean strideScrollWasActive;
    private static int forwardInputResendTicks;
    private static long lastJumpPressGameTime = Long.MIN_VALUE;

    // Minecraft's normal double-tap window is short enough to feel natural
    // without accidentally enabling Flight during ordinary jumping.
    private static final long FLIGHT_DOUBLE_TAP_WINDOW_TICKS = 7L;

    private ClientKeyEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        while (ModKeyMappings.OPEN_POWERS.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new PowersScreen());
            }
        }

        while (ModKeyMappings.OPEN_JOURNAL.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                com.jayptucker.heroesevolved.gui.screen.JournalScreen.open(null);
            }
        }

        while (ModKeyMappings.OPEN_ADMIN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null
                    && minecraft.player.hasPermissions(2)) {
                // The server answers with fresh state and opens the menu.
                PacketDistributor.sendToServer(AdminActionPayload.of(
                        AdminActionPayload.Action.OPEN,
                        minecraft.player.getUUID()
                ));
            }
        }

        if (minecraft.player == null || minecraft.screen != null) {
            speedScrollRemainder = 0;
            if (minecraft.player != null) {
                releaseHeldAbilities();
            }
            syncFlightForwardInput(false);
            FlightBoostWindSound.update(false);
            jumpWasDown = false;
            return;
        }

        handleFlightDoubleTap(minecraft);

        // Forward flight uses the wheel-selected speed. Sprint/Ctrl is not a boost input.
        boolean movingForward = hasAwakenedFlight()
                && ClientFlightVisualState.isFlightEnabled(com.jayptucker.heroesevolved.client.TelepathyClient.bodyId())
                && minecraft.options.keyUp.isDown();
        syncFlightForwardInput(movingForward);
        FlightBoostWindSound.update(movingForward
                && ClientFlightVisualState.isFlightActive(com.jayptucker.heroesevolved.client.TelepathyClient.bodyId()));
        if (strideScrollWasActive && !canScrollStride(minecraft)) speedScrollRemainder = 0;

        boolean allowOverexertion =
                minecraft.options.keyShift.isDown();

        for (int index = 0; index < ABILITY_WAS_DOWN.length; index++) {
            ABILITY_WAS_DOWN[index] = sendAbilityRequestIfPressed(
                    ModKeyMappings.ABILITY_KEYS[index],
                    index + 1,
                    allowOverexertion,
                    ABILITY_WAS_DOWN[index]
            );
        }

        handleWheelKey(minecraft, allowOverexertion);
        handleMemoryKey(minecraft);
    }

    private static long memoryDownSince = -1;

    /**
     * Empathic Mimicry's memory wheel: held, it opens the wheel of remembered powers; tapped, it steps the channel
     * to the next one.
     */
    private static void handleMemoryKey(Minecraft minecraft) {
        boolean down = ModKeyMappings.MEMORY_WHEEL.isDown();
        if (!com.jayptucker.heroesevolved.gui.screen.MemoryWheelScreen.isMimic()) { memoryDownSince = -1; return; }
        long now = minecraft.level.getGameTime();
        if (down && memoryDownSince < 0) {
            memoryDownSince = now;
        } else if (down && now - memoryDownSince >= WHEEL_HOLD_TICKS) {
            memoryDownSince = -1;
            minecraft.setScreen(new com.jayptucker.heroesevolved.gui.screen.MemoryWheelScreen());
        } else if (!down && memoryDownSince >= 0) {
            memoryDownSince = -1;
            com.jayptucker.heroesevolved.gui.screen.MemoryWheelScreen.stepNext();
        }
    }

    /**
     * The wheel key: held, it opens the ability wheel (which takes over
     * from there); tapped, it casts the pinned favorite or recasts the last pick.
     */
    private static void handleWheelKey(Minecraft minecraft, boolean allowOverexertion) {
        if (wheelOpenedForPress) {
            // Closing through mouse release or Escape must consume the original wheel press.
            if (!AbilityWheelScreen.wheelKeyDown(minecraft)) wheelOpenedForPress = false;
            return;
        }
        boolean down = ModKeyMappings.ABILITY_WHEEL.isDown();
        long now = minecraft.level.getGameTime();
        if (down && wheelDownSince < 0) {
            wheelDownSince = now;
        } else if (down && now - wheelDownSince >= WHEEL_HOLD_TICKS) {
            wheelDownSince = -1;
            if (ClientLoadout.known() && !ClientLoadout.get().entries().isEmpty()) {
                wheelOpenedForPress = true;
                minecraft.setScreen(new AbilityWheelScreen(allowOverexertion));
            }
        } else if (!down && wheelDownSince >= 0) {
            wheelDownSince = -1;
            AbilityWheelScreen.recast(allowOverexertion);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.isPaused()) {
            speedScrollRemainder = 0;
            return;
        }
        boolean flight = hasAwakenedFlight()
                && ClientFlightVisualState.isFlightEnabled(TelepathyClient.bodyId())
                && !ClientFlightVisualState.isCycloneActive(mc.player.getId())
                && !mc.player.onGround() && mc.player.isAlive() && !mc.player.isSpectator();
        boolean stride = !flight && canScrollStride(mc);
        if (stride != strideScrollWasActive || (!flight && !stride)) speedScrollRemainder = 0;
        strideScrollWasActive = stride;
        if (!flight && !stride) return;

        double delta = event.getScrollDeltaY();
        if (!Double.isFinite(delta) || delta == 0) return;
        event.setCanceled(true);
        if (Math.signum(delta) != Math.signum(speedScrollRemainder)) speedScrollRemainder = 0;
        speedScrollRemainder += delta;
        int direction = delta > 0 ? 1 : -1;
        int notches = Math.min(4, (int) Math.abs(speedScrollRemainder));
        for (int i = 0; i < notches; i++) {
            if (stride) PacketDistributor.sendToServer(new SpeedStrideInputPayload(direction));
            else PacketDistributor.sendToServer(new FlightSpeedInputPayload(direction));
        }
        speedScrollRemainder = Math.copySign(Math.abs(speedScrollRemainder) % 1, delta);
    }

    private static boolean canScrollStride(Minecraft mc) {
        if (TelepathyClient.role() == 2)
            return mc.level.getEntity(TelepathyClient.bodyId()) instanceof Player runner
                    && SpeedMovement.canAdjustSpeed(runner);
        return !TelepathyClient.immobilized() && SpeedMovement.canAdjustSpeed(mc.player);
    }

    /**
     * Opening a screen hides key releases from us, so end any held
     * (channelled) ability now instead of leaving it running.
     */
    private static void releaseHeldAbilities() {
        for (int index = 0; index < ABILITY_WAS_DOWN.length; index++) {
            if (ABILITY_WAS_DOWN[index]) {
                PacketDistributor.sendToServer(
                        new ActionInputPayload(index + 1, "", false, false)
                );
            }
            ABILITY_WAS_DOWN[index] = false;
        }
        // A screen swallows the wheel key's release: that is not a tap.
        wheelDownSince = -1;
    }

    private static void handleFlightDoubleTap(Minecraft minecraft) {
        boolean jumpIsDown = minecraft.options.keyJump.isDown();

        if (jumpIsDown && !jumpWasDown && hasNativeFlight()) {
            long gameTime = minecraft.level.getGameTime();

            if (lastJumpPressGameTime != Long.MIN_VALUE
                    && gameTime - lastJumpPressGameTime
                    <= FLIGHT_DOUBLE_TAP_WINDOW_TICKS) {
                // Always Flight's own toggle, whatever is on key 1.
                PacketDistributor.sendToServer(new ActionInputPayload(
                        0, Loadout.BASE + AbilitySlot.PRIMARY.name(), true,
                        minecraft.options.keyShift.isDown()
                ));

                // A completed double tap cannot combine with the next jump.
                lastJumpPressGameTime = Long.MIN_VALUE;
            } else {
                lastJumpPressGameTime = gameTime;
            }
        }

        jumpWasDown = jumpIsDown;
    }

    private static boolean hasAwakenedFlight() {
        if (hasNativeFlight()) return true;
        var player = Minecraft.getInstance().player;
        // The server grants this flag only after a real Flight Echo begins a temporary session.
        return player != null && ClientFlightVisualState.isFlightEnabled(player.getId())
                && ClientPowerState.getSnapshot().abilities().stream().anyMatch(
                        ability -> ability.unlocked() && ability.abilityId().equals(ModAbilities.MIMICRY_ID));
    }

    private static boolean hasNativeFlight() {
        return ClientPowerState.getSnapshot().abilities().stream().anyMatch(
                ability -> ability.unlocked()
                        && ability.abilityId().equals(ModAbilities.FLIGHT_ID)
        );
    }

    private static boolean sendAbilityRequestIfPressed(
            net.minecraft.client.KeyMapping keyMapping,
            int key,
            boolean allowOverexertion,
            boolean wasDown
    ) {
        boolean isDown = keyMapping.isDown();

        // Key-repeat events must not activate a toggle more than once.
        // A request is sent only when the key changes from up to down.
        if (isDown && !wasDown) {
            PacketDistributor.sendToServer(
                    new ActionInputPayload(key, "", true, allowOverexertion)
            );
        } else if (!isDown && wasDown) {
            // Held abilities (Quake Blast) end when the key is let go.
            // Everything else simply ignores the release.
            PacketDistributor.sendToServer(
                    new ActionInputPayload(key, "", false, false)
            );
        }

        return isDown;
    }

    private static void syncFlightForwardInput(boolean movingForward) {
        boolean stateChanged = flightForwardWasDown != movingForward;
        boolean shouldResend = movingForward
                && ++forwardInputResendTicks >= 5;

        if (!stateChanged && !shouldResend) {
            return;
        }

        flightForwardWasDown = movingForward;
        forwardInputResendTicks = 0;
        PacketDistributor.sendToServer(
                new FlightForwardInputPayload(movingForward)
        );
    }
}
