package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.sound.FlightBoostWindSound;
import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.ActivateAbilitySlotPayload;
import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.network.ReleaseAbilitySlotPayload;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.jayptucker.heroesevolved.network.FlightForwardInputPayload;
import com.jayptucker.heroesevolved.network.FlightSpeedInputPayload;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
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
    private static boolean ability1WasDown;
    private static boolean ability2WasDown;
    private static boolean ability3WasDown;
    private static boolean ability4WasDown;
    private static boolean ability5WasDown;
    private static boolean flightForwardWasDown;
    private static boolean jumpWasDown;
    private static double flightScrollRemainder;
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
            flightScrollRemainder = 0;
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
                && ClientFlightVisualState.isFlightEnabled(minecraft.player.getId())
                && minecraft.options.keyUp.isDown();
        syncFlightForwardInput(movingForward);
        FlightBoostWindSound.update(movingForward
                && ClientFlightVisualState.isFlightActive(minecraft.player.getId()));

        boolean allowOverexertion =
                minecraft.options.keyShift.isDown();

        ability1WasDown = sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_1,
                AbilitySlot.PRIMARY,
                allowOverexertion,
                ability1WasDown
        );

        ability2WasDown = sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_2,
                AbilitySlot.SECONDARY,
                allowOverexertion,
                ability2WasDown
        );

        ability3WasDown = sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_3,
                AbilitySlot.TERTIARY,
                allowOverexertion,
                ability3WasDown
        );

        ability4WasDown = sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_4,
                AbilitySlot.QUATERNARY,
                allowOverexertion,
                ability4WasDown
        );

        ability5WasDown = sendAbilityRequestIfPressed(
                ModKeyMappings.ABILITY_5,
                AbilitySlot.QUINARY,
                allowOverexertion,
                ability5WasDown
        );
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.isPaused() || !hasAwakenedFlight()
                || !ClientFlightVisualState.isFlightEnabled(mc.player.getId())
                || ClientFlightVisualState.isCycloneActive(mc.player.getId())
                || mc.player.onGround() || !mc.player.isAlive() || mc.player.isSpectator()) {
            flightScrollRemainder = 0; return;
        }
        double delta = event.getScrollDeltaY();
        if (!Double.isFinite(delta) || delta == 0) return;
        event.setCanceled(true);
        if (Math.signum(delta) != Math.signum(flightScrollRemainder)) flightScrollRemainder = 0;
        flightScrollRemainder += delta;
        int notches = Math.min(4, (int) Math.abs(flightScrollRemainder));
        for (int i = 0; i < notches; i++)
            PacketDistributor.sendToServer(new FlightSpeedInputPayload(delta > 0 ? 1 : -1));
        flightScrollRemainder = Math.copySign(Math.abs(flightScrollRemainder) % 1, delta);
    }

    /**
     * Opening a screen hides key releases from us, so end any held
     * (channelled) ability now instead of leaving it running.
     */
    private static void releaseHeldAbilities() {
        boolean[] held = {
                ability1WasDown, ability2WasDown, ability3WasDown,
                ability4WasDown, ability5WasDown
        };
        AbilitySlot[] slots = AbilitySlot.values();
        for (int index = 0; index < held.length; index++) {
            if (held[index]) {
                PacketDistributor.sendToServer(
                        new ReleaseAbilitySlotPayload(slots[index])
                );
            }
        }
        ability1WasDown = false;
        ability2WasDown = false;
        ability3WasDown = false;
        ability4WasDown = false;
        ability5WasDown = false;
    }

    private static void handleFlightDoubleTap(Minecraft minecraft) {
        boolean jumpIsDown = minecraft.options.keyJump.isDown();

        if (jumpIsDown && !jumpWasDown && hasNativeFlight()) {
            long gameTime = minecraft.level.getGameTime();

            if (lastJumpPressGameTime != Long.MIN_VALUE
                    && gameTime - lastJumpPressGameTime
                    <= FLIGHT_DOUBLE_TAP_WINDOW_TICKS) {
                PacketDistributor.sendToServer(
                        new ActivateAbilitySlotPayload(
                                AbilitySlot.PRIMARY,
                                minecraft.options.keyShift.isDown()
                        )
                );

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
            AbilitySlot slot,
            boolean allowOverexertion,
            boolean wasDown
    ) {
        boolean isDown = keyMapping.isDown();

        // Key-repeat events must not activate a toggle more than once.
        // A request is sent only when the key changes from up to down.
        if (isDown && !wasDown) {
            PacketDistributor.sendToServer(
                    new ActivateAbilitySlotPayload(
                            slot,
                            allowOverexertion
                    )
            );
        } else if (!isDown && wasDown) {
            // Held abilities (Quake Blast) end when the key is let go.
            // Everything else simply ignores the release.
            PacketDistributor.sendToServer(new ReleaseAbilitySlotPayload(slot));
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
