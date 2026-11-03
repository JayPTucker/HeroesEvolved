package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.CryoRideClient;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A low, carving skating stance. Banking affects the body while the rider's camera stays level. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class CryoRidePoseRenderer {
    private static final Map<Integer, Float> BANK = new HashMap<>();
    private static final Set<Integer> POSED = new HashSet<>();
    private CryoRidePoseRenderer() {}
    @SubscribeEvent public static void pre(RenderPlayerEvent.Pre event) {
        var player = event.getEntity();
        if (!CryoRideClient.isRiding(player.getId()) || !player.isAlive()) return;
        float speed = (float) Math.clamp(CryoRideClient.speed(player.getId()) / 1.1, 0, 1);
        float turn = Mth.wrapDegrees(player.getYRot() - player.yRotO);
        float target = Mth.clamp(-turn * 3.5F, -25, 25) * speed;
        float bank = Mth.lerp(.25F, BANK.getOrDefault(player.getId(), 0F), target);
        BANK.put(player.getId(), bank);
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0, .85, 0);
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(-player.getYRot()));
        event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(bank));
        event.getPoseStack().mulPose(Axis.XP.rotationDegrees(7 * speed));
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(player.getYRot()));
        event.getPoseStack().translate(0, -.85, 0);
        POSED.add(player.getId());
    }
    @SubscribeEvent public static void post(RenderPlayerEvent.Post event) {
        if (POSED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) { POSED.clear(); BANK.clear(); }
    }
}
