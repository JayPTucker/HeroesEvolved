package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Sound Sight, Emma Coolidge's way of hearing: every sound the game plays near you blooms where it was
 * made, in a colour that says what made it. Monsters are red and coral, animals green, players violet,
 * blocks and footsteps gold, the weather and the world blue, music every colour at once. It works through
 * walls, since sound does. Only the singer sees it, and it is purely visual.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SoundSight {
    private static final int PER_TICK = 10;
    private static final double RANGE = 32;
    private static int shownThisTick;

    private SoundSight() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { shownThisTick = 0; }

    private static boolean singer() {
        return ClientPowerState.getSnapshot().abilities().stream()
                .anyMatch(a -> a.unlocked() && a.abilityId().equals(ModAbilities.SONIC_RESONANCE_ID));
    }

    @SubscribeEvent
    public static void sound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        Minecraft mc = Minecraft.getInstance();
        if (sound == null || mc.level == null || mc.player == null || shownThisTick >= PER_TICK
                || sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE || !singer()) return;
        SoundSource source = sound.getSource();
        if (source == SoundSource.MASTER || source == SoundSource.MUSIC) return;
        Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double distance = at.distanceTo(camera);
        // Your own sounds are at your ears; they would only fill the view.
        if (distance > RANGE || distance < 1.6) return;
        shownThisTick++;
        String path = sound.getLocation().getPath();
        float hue = switch (source) {
            case HOSTILE -> 0.0F;
            case NEUTRAL -> 0.33F;
            case PLAYERS -> 0.8F;
            case BLOCKS -> 0.12F;
            case WEATHER, AMBIENT -> 0.58F;
            case RECORDS -> (mc.level.getGameTime() % 60) / 60F;
            case VOICE -> 0.9F;
            default -> 0.5F;
        } + (Math.floorMod(path.hashCode(), 100) / 100F) * 0.08F;
        boolean step = path.contains("step");
        float loudness = path.contains("explode") || path.contains("thunder") ? 3.0F
                : step ? 0.35F
                : path.contains("break") || path.contains("hurt") || path.contains("death") ? 1.2F : 0.8F;
        SonicEffects.ripple(step ? at.subtract(0, 0.05, 0) : at, loudness, hue, step, mc.level.random);
    }
}
