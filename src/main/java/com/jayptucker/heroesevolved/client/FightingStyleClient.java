package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.ClientVisualSettings;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.FightingStylePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;

/**
 * The hands and feet of a power that fights (Pyrokinesis, Electrokinesis, Hydrokinesis). An empty-handed left click
 * that isn't digging a block throws the next strike of the power's style instead of a punch; double-tapping left,
 * right or back dodges. Both can be switched off in the client config. The server decides what each one does.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FightingStyleClient {
    /** The powers with a fighting style (see combat/FightingStyles). */
    private static final Set<ResourceLocation> STYLED = Set.of(ModAbilities.PYROKINESIS_ID, ModAbilities.ELECTROKINESIS_ID,
            ModAbilities.HYDROKINESIS_ID, ModAbilities.SUPERSPEED_ID, ModAbilities.QUAKE_ID, ModAbilities.CRYOKINESIS_ID,
            ModAbilities.PHASING_ID, ModAbilities.REGENERATION_ID, ModAbilities.INVISIBILITY_ID, ModAbilities.TELEKINESIS_ID);
    /** Two presses of one direction this many ticks apart or less are a dodge. */
    private static final int DOUBLE_TAP = 6;
    /** The client sends no faster than the quickest strike the server allows. */
    private static final int STRIKE_EVERY = 4;
    private static final boolean[] WAS_DOWN = new boolean[3];
    private static final long[] LAST_PRESS = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
    private static long lastStrike = Long.MIN_VALUE;

    private FightingStyleClient() {}

    private static boolean styled() {
        if (ClientPowerState.getSnapshot().abilities().stream().anyMatch(a -> a.unlocked() && STYLED.contains(a.abilityId())))
            return true;
        // An empath fights with the style of whatever power they are channeling.
        var memory = com.jayptucker.heroesevolved.network.ClientMimicMemory.get();
        if (memory.selected() < 0 || memory.selected() >= memory.slots().size()) return false;
        // The memory sends a power's path alone (its namespace is always the mod's).
        String path = memory.slots().get(memory.selected()).power();
        return STYLED.stream().anyMatch(id -> id.getPath().equals(path));
    }

    private static boolean free(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null && !mc.isPaused() && mc.player.isAlive()
                && !mc.player.isSpectator() && TelepathyClient.role() == 0;
    }

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND || !free(mc)
                || !ClientVisualSettings.STYLE_STRIKES.get() || !mc.player.getMainHandItem().isEmpty() || !styled()) return;
        // A block under the crosshair is still dug by hand.
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        long now = mc.level.getGameTime();
        if (lastStrike != Long.MIN_VALUE && now - lastStrike < STRIKE_EVERY && now >= lastStrike) return;
        lastStrike = now;
        int target = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
        PacketDistributor.sendToServer(new FightingStylePayload(FightingStylePayload.STRIKE, target));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!free(mc) || !ClientVisualSettings.STYLE_DODGES.get() || !styled()) {
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
                    PacketDistributor.sendToServer(new FightingStylePayload(FightingStylePayload.DODGE, i));
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
