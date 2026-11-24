package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.gui.HudGauges;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.jayptucker.heroesevolved.network.ClientLoadout;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.ClientStamina;
import com.jayptucker.heroesevolved.network.FlightRollPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Flight's Barrel Roll: double-tap Strafe Left or Right (whatever they are bound to) while flying. This only asks;
 * the server checks the flight, pays for the roll and performs it. Any open screen, chat included, stops it. The roll
 * has no key tile, so a small ROLL ring beside the Flight ring says whether it is ready.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FlightRollClient {
    /** The same window as a fighting style's dodge. */
    private static final int DOUBLE_TAP = 6;
    private static final DoubleTaps TAPS = new DoubleTaps(2, DOUBLE_TAP);
    private static final int READY_COLOR = 0xFF8EDAF0, RECHARGE_COLOR = 0xFF4F7F8C, PALE = 0xFFCBEFF8, LOW_COLOR = 0xFFFF6A6A;

    /** What a double-tap would do right now. */
    public enum RollStatus { READY, COOLING, BUSY, LOW }

    private FlightRollClient() {}

    /** In the air on the player's own Flight, where the server would consider a roll. */
    private static boolean airborne(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.player.isAlive()
                && !mc.player.isSpectator() && !mc.player.onGround() && !mc.player.isPassenger()
                && !mc.player.isInWaterOrBubble() && !mc.player.isFallFlying() && TelepathyClient.role() == 0
                && ClientFlightVisualState.isFlightEnabled(mc.player.getId())
                && ClientPowerState.getSnapshot().abilities().stream()
                        .anyMatch(a -> a.unlocked() && a.abilityId().equals(ModAbilities.FLIGHT_ID));
    }

    private static boolean flying(Minecraft mc) {
        return airborne(mc) && mc.screen == null && !mc.isPaused();
    }

    /** The roll's state for the HUD, or null when the player isn't flying. Matches the server's order of refusals. */
    public static RollStatus status() {
        Minecraft mc = Minecraft.getInstance();
        if (!airborne(mc)) return null;
        var roll = ClientLoadout.innate(FlightSkillService.ROLL);
        if (roll == null) return null;
        if (roll.cooldownEnd() > mc.level.getGameTime()) return RollStatus.COOLING;
        int id = mc.player.getId();
        if (ClientFlightVisualState.isCharging(id) || ClientFlightVisualState.isDiving(id)
                || ClientFlightVisualState.isCycloneActive(id) || ClientFlightVisualState.isGatheringVacuum(id)
                || ClientFlightVisualState.isTakingOff(id)) return RollStatus.BUSY;
        if (ClientStamina.known() && !ClientStamina.unlimited() && (int) ClientStamina.stamina() < roll.energy())
            return RollStatus.LOW;
        return RollStatus.READY;
    }

    /** OK when a double-tap would roll, a countdown while it recharges, LOW without the stamina, a dash mid-maneuver. */
    public static void gauges(List<HudGauges.Gauge> gauges) {
        RollStatus status = status();
        if (status == null) return;
        var roll = ClientLoadout.innate(FlightSkillService.ROLL);
        gauges.add(switch (status) {
            case READY -> HudGauges.Gauge.ring(1, READY_COLOR).middle("OK").label("ROLL");
            case COOLING -> {
                long left = roll.cooldownEnd() - Minecraft.getInstance().level.getGameTime();
                yield HudGauges.Gauge.ring(1 - left / (float) Math.max(1, roll.cooldownTicks()), RECHARGE_COLOR)
                        .middle((left + 19) / 20 + "s", PALE).label("ROLL");
            }
            case BUSY -> HudGauges.Gauge.ring(0, RECHARGE_COLOR).middle("-", HudGauges.LABEL).label("ROLL");
            case LOW -> HudGauges.Gauge.ring(1, LOW_COLOR).middle("LOW").label("ROLL", LOW_COLOR);
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!flying(mc)) {
            TAPS.reset();
            return;
        }
        KeyMapping[] keys = {mc.options.keyLeft, mc.options.keyRight};
        TAPS.poll(keys, mc.level.getGameTime(),
                i -> PacketDistributor.sendToServer(new FlightRollPayload(i == 0 ? -1 : 1)));
    }
}
