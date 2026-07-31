package com.jayptucker.heroesevolved.registry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers server entities that also have dedicated client renderers. */
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, HeroesEvolved.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TemporalEchoEntity>>
            TEMPORAL_ECHO = ENTITY_TYPES.register("temporal_echo", id ->
                    EntityType.Builder.<TemporalEchoEntity>of(
                                    TemporalEchoEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(0.6F, 1.8F)
                            .noSummon()
                            .noSave()
                            .clientTrackingRange(64)
                            .updateInterval(20)
                            .build(id.toString())
            );

    public static final DeferredHolder<EntityType<?>, EntityType<CarryAnchorEntity>>
            CARRY_ANCHOR = ENTITY_TYPES.register("carry_anchor", id ->
                    EntityType.Builder.<CarryAnchorEntity>of(
                                    CarryAnchorEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(0.01F, 0.01F)
                            .noSummon()
                            .noSave()
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(id.toString())
            );

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TEMPORAL_ECHO.get(), TemporalEchoEntity.createAttributes().build());
    }
}
