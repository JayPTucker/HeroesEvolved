package com.jayptucker.heroesevolved.registry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.jayptucker.heroesevolved.entity.QuakeDebrisEntity;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.entity.TemporalGhostBlockEntity;
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
                            // Echoes replay movement continuously, so a
                            // twenty-tick sync interval visibly stutters for
                            // remote players. Send tracked updates each tick.
                            .updateInterval(1)
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

    public static final DeferredHolder<
            EntityType<?>, EntityType<TemporalGhostBlockEntity>>
            TEMPORAL_GHOST_BLOCK = ENTITY_TYPES.register(
                    "temporal_ghost_block", id ->
                            EntityType.Builder.<TemporalGhostBlockEntity>of(
                                            TemporalGhostBlockEntity::new,
                                            MobCategory.MISC
                                    )
                                    .sized(1.0F, 1.0F)
                                    .noSummon()
                                    .noSave()
                                    .clientTrackingRange(64)
                                    .updateInterval(1)
                                    .build(id.toString())
            );

    public static final DeferredHolder<EntityType<?>, EntityType<QuakeDebrisEntity>>
            QUAKE_DEBRIS = ENTITY_TYPES.register("quake_debris", id ->
                    EntityType.Builder.<QuakeDebrisEntity>of(
                                    QuakeDebrisEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(0.5F, 0.5F)
                            .noSummon()
                            .noSave()
                            .clientTrackingRange(64)
                            // Clients simulate the arc themselves; occasional
                            // corrections are enough.
                            .updateInterval(10)
                            .build(id.toString())
            );

    private ModEntities() {
    }

    public static final DeferredHolder<EntityType<?>, EntityType<com.jayptucker.heroesevolved.story.sylar.SylarEntity>> SYLAR =
            ENTITY_TYPES.register("sylar", id -> EntityType.Builder.<com.jayptucker.heroesevolved.story.sylar.SylarEntity>of(
                    com.jayptucker.heroesevolved.story.sylar.SylarEntity::new, MobCategory.MISC)
                    .sized(.6F, 1.8F).noSave().noSummon().clientTrackingRange(96).updateInterval(3).build(id.toString()));

    /** Company agents: guards with tranquilizer rifles, and the Suppressor. Summonable for testing and building scenes. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jayptucker.heroesevolved.company.CompanyAgentEntity>> COMPANY_AGENT =
            ENTITY_TYPES.register("company_agent", id -> EntityType.Builder.<com.jayptucker.heroesevolved.company.CompanyAgentEntity>of(
                    com.jayptucker.heroesevolved.company.CompanyAgentEntity::new, MobCategory.MISC)
                    .sized(.6F, 1.8F).clientTrackingRange(64).updateInterval(2).build(id.toString()));

    /** Elle Bishop, sent by the Company: the boss of Primatech's test floor (story/elle). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jayptucker.heroesevolved.story.elle.ElleEntity>> ELLE =
            ENTITY_TYPES.register("elle_bishop", id -> EntityType.Builder.<com.jayptucker.heroesevolved.story.elle.ElleEntity>of(
                    com.jayptucker.heroesevolved.story.elle.ElleEntity::new, MobCategory.MISC)
                    .sized(.6F, 1.8F).noSave().noSummon().clientTrackingRange(96).updateInterval(2).build(id.toString()));

    /** The EMP grenade in flight (weapons/EmpGrenade): thrown, stuck where it lands, then a pulse. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.jayptucker.heroesevolved.weapons.EmpGrenade.Thrown>> EMP_GRENADE =
            ENTITY_TYPES.register("emp_grenade", id -> EntityType.Builder.<com.jayptucker.heroesevolved.weapons.EmpGrenade.Thrown>of(
                    com.jayptucker.heroesevolved.weapons.EmpGrenade.Thrown::new, MobCategory.MISC)
                    .sized(.25F, .25F).clientTrackingRange(64).updateInterval(5).build(id.toString()));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TEMPORAL_ECHO.get(), TemporalEchoEntity.createAttributes().build());
        event.put(SYLAR.get(), com.jayptucker.heroesevolved.story.sylar.SylarEntity.createAttributes().build());
        event.put(COMPANY_AGENT.get(), com.jayptucker.heroesevolved.company.CompanyAgentEntity.createAttributes().build());
        event.put(ELLE.get(), com.jayptucker.heroesevolved.story.elle.ElleEntity.createAttributes().build());
    }
}
