package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registration keeps rendering code out of dedicated servers. */
@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class ModEntityRenderers {
    private ModEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.jayptucker.heroesevolved.registry.ModBlocks.ALARM_BEACON_ENTITY.get(),
                com.jayptucker.heroesevolved.client.block.AlarmBeaconRenderer::new);
        event.registerEntityRenderer(ModEntities.SYLAR.get(), SylarRenderer::new);
        event.registerEntityRenderer(ModEntities.COMPANY_AGENT.get(), CompanyAgentRenderer::new);
        event.registerEntityRenderer(ModEntities.ELLE.get(), ElleRenderer::new);
        event.registerEntityRenderer(ModEntities.SHAPE_DECOY.get(), ShapeDecoyRenderer::new);
        event.registerEntityRenderer(ModEntities.STORY_ACTOR.get(), StoryActorRenderer::new);
        event.registerEntityRenderer(ModEntities.EMP_GRENADE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(
                ModEntities.TEMPORAL_ECHO.get(),
                TemporalEchoRenderer::new
        );
        event.registerEntityRenderer(
                ModEntities.CARRY_ANCHOR.get(),
                CarryAnchorRenderer::new
        );
        event.registerEntityRenderer(
                ModEntities.TEMPORAL_GHOST_BLOCK.get(),
                TemporalGhostBlockRenderer::new
        );
        event.registerEntityRenderer(
                ModEntities.QUAKE_DEBRIS.get(),
                QuakeDebrisRenderer::new
        );
    }
}
