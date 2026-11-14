package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.ClientVisualSettings;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.PyroCombatPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A pyrokinetic's hands and feet. An empty-handed left click that isn't digging a block throws the next firebending
 * strike instead of a punch; double-tapping left, right or back dodges. Both can be switched off in the client config.
 * The server decides what each one actually does.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class PyroCombatClient {
    /** Two presses of one direction this many ticks apart or less are a dodge. */
    private static final int DOUBLE_TAP = 6;
    /** The client sends no faster than the quickest strike the server allows. */
    private static final int STRIKE_EVERY = 4;
    private static final boolean[] WAS_DOWN = new boolean[3];
    private static final long[] LAST_PRESS = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
    private static long lastStrike = Long.MIN_VALUE;

    private PyroCombatClient() {}

    private static boolean pyro() {
        return ClientPowerState.getSnapshot().abilities().stream()
                .anyMatch(a -> a.unlocked() && a.abilityId().equals(ModAbilities.PYROKINESIS_ID));
    }

    private static boolean free(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null && !mc.isPaused() && mc.player.isAlive()
                && !mc.player.isSpectator() && TelepathyClient.role() == 0;
    }

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND || !free(mc)
                || !ClientVisualSettings.FIRE_STRIKES.get() || !mc.player.getMainHandItem().isEmpty() || !pyro()) return;
        // A block under the crosshair is still dug by hand.
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        long now = mc.level.getGameTime();
        if (lastStrike != Long.MIN_VALUE && now - lastStrike < STRIKE_EVERY && now >= lastStrike) return;
        lastStrike = now;
        int target = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
        PacketDistributor.sendToServer(new PyroCombatPayload(PyroCombatPayload.STRIKE, target));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!free(mc) || !ClientVisualSettings.FIRE_DODGES.get() || !pyro()) {
            java.util.Arrays.fill(WAS_DOWN, false);
            java.util.Arrays.fill(LAST_PRESS, Long.MIN_VALUE);
            return;
        }
        KeyMapping[] keys = {mc.options.keyLeft, mc.options.keyRight, mc.options.keyDown};
        long now = mc.level.getGameTime();
        for (int i = 0; i < keys.length; i++) {
            boolean down = keys[i].isDown();
            if (down && !WAS_DOWN[i]) {
                if (LAST_PRESS[i] != Long.MIN_VALUE && now - LAST_PRESS[i] <= DOUBLE_TAP && now >= LAST_PRESS[i]) {
                    PacketDistributor.sendToServer(new PyroCombatPayload(PyroCombatPayload.DODGE, i));
                    LAST_PRESS[i] = Long.MIN_VALUE;
                } else {
                    LAST_PRESS[i] = now;
                }
                // Tapping another direction in between starts the count again.
                for (int j = 0; j < keys.length; j++) if (j != i) LAST_PRESS[j] = Long.MIN_VALUE;
            }
            WAS_DOWN[i] = down;
        }
    }
}
