package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Places one blueprint in a flat daylight world and photographs it: four corners, straight down,
 * and from inside its centre. With a cut layer, everything above that layer is left off so the
 * rooms can be seen from above. Run through tools/blueprint-preview.init.gradle.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class BlueprintPreview {
    private record Shot(String name, Vec3 eye, Vec3 look) {}
    private static boolean started, placed;
    private static int ticks, worldTicks, shot;
    private static String pending;
    private static final List<Shot> SHOTS = new ArrayList<>();

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        String name = System.getProperty("heroes.preview.blueprint");
        if (name == null || name.isEmpty()) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.gamma().set(1.0);
            mc.options.hideGui = true;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Blueprint-" + System.currentTimeMillis(),
                    new LevelSettings("Blueprint preview", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        var server = mc.getSingleplayerServer();
        worldTicks++;
        if (worldTicks == 20 && !placed) {
            placed = true;
            int cut = Integer.getInteger("heroes.preview.cut", 999);
            Blueprint blueprint = Blueprint.load(name);
            BlockPos at = new BlockPos(blueprint.anchorX, -60 + blueprint.anchorY, blueprint.anchorZ);
            server.execute(() -> {
                server.overworld().setDayTime(6000);
                blueprint.place(server.overworld(), at, Rotation.NONE, cut);
            });
            double w = blueprint.width(), d = blueprint.depth(), h = Math.min(blueprint.height(), cut + 1);
            Vec3 center = new Vec3(w / 2, -60 + h / 2, d / 2);
            double r = Math.max(w, d) * 0.75 + 6, up = h * 0.6 + 5;
            SHOTS.add(new Shot("se", center.add(r, up, r), center));
            SHOTS.add(new Shot("sw", center.add(-r, up, r), center));
            SHOTS.add(new Shot("ne", center.add(r, up, -r), center));
            SHOTS.add(new Shot("nw", center.add(-r, up, -r), center));
            SHOTS.add(new Shot("top", center.add(0.01, Math.max(w, d) + 8, 0), center));
            SHOTS.add(new Shot("inside", new Vec3(w / 2, -59.4, 1.5), new Vec3(w / 2, -59.4, d)));
        }
        if (worldTicks < 60 || pending != null) return;
        if (shot >= SHOTS.size() * 15) { mc.stop(); return; }
        Shot current = SHOTS.get(shot / 15);
        if (shot % 15 == 0) {
            Vec3 delta = current.look.subtract(current.eye);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.getAbilities().flying = true; player.onUpdateAbilities();
                player.teleportTo(server.overworld(), current.eye.x, current.eye.y, current.eye.z, yaw, pitch);
            });
        }
        if (shot % 15 == 14) pending = name + "-" + current.name + ".png";
        shot++;
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("BLUEPRINT_CAPTURE " + out);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
