package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * Odessa's sky: the overworld's dusk, without the blocky cloud layer, so nothing flat floats over
 * the city or in front of the mushroom cloud. The city's dimension type names these effects.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class IntroSky {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_fracture");

    private IntroSky() {}

    @SubscribeEvent public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new Effects());
    }

    private static final class Effects extends DimensionSpecialEffects {
        Effects() { super(Float.NaN, true, SkyType.NORMAL, false, false); }

        @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
            return color.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
        }

        @Override public boolean isFoggyAt(int x, int y) { return false; }
    }
}
