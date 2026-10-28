package com.jayptucker.heroesevolved.registry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.formula.CentrifugeMenu;
import com.jayptucker.heroesevolved.formula.SynthesisStationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menus for the Formula laboratory blocks. */
public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HeroesEvolved.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CentrifugeMenu>> CENTRIFUGE =
            MENUS.register("centrifuge", () -> new MenuType<>(CentrifugeMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<SynthesisStationMenu>> SYNTHESIS_STATION =
            MENUS.register("synthesis_station", () -> new MenuType<>(SynthesisStationMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}

    static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
