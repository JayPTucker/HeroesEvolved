package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.dampener.AbilityDampenerBlockEntity;
import com.jayptucker.heroesevolved.dampener.DampenerGeometry;
import com.jayptucker.heroesevolved.dampener.Dampening;
import com.jayptucker.heroesevolved.dampener.Nullifier;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The Ability Dampener and the Nullifier: a projector spinning up and holding its field (from outside, by day and by
 * night, and from inside), a powered and an unpowered projector side by side, the Nullifier firing at a husk and the
 * hit and collar that follow, the gun drinking from a projector, and the gun held in first person. Staged 1700 blocks
 * from spawn. Screenshots go to build/dampener-preview/screenshots.
 *
 * <pre>./gradlew runClient --init-script tools/dampener-preview.init.gradle</pre>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class DampenerPreview {
    private static final int X = 1700, Y = -60, Z = 0;
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static ArmorStand shooter;
    private static Mob husk;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void shot(String name) { pending = "dampener-" + name + ".png"; }

    private static BlockPos pylon() { return new BlockPos(X, Y, Z + 10); }

    private static void stage(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int dx = -14; dx <= 14; dx++)
            for (int dz = -8; dz <= 26; dz++)
                level.setBlockAndUpdate(new BlockPos(X + dx, Y - 1, Z + dz),
                        ((dx + dz) % 5 == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState());
        // A wall through the middle of the field: it reaches through walls.
        for (int dx = 3; dx <= 4; dx++) for (int dy = 0; dy < 3; dy++) for (int dz = 6; dz <= 14; dz++)
            level.setBlockAndUpdate(new BlockPos(X + dx, Y + dy, Z + dz), Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(pylon(), ModBlocks.ABILITY_DAMPENER.get().defaultBlockState());
        if (level.getBlockEntity(pylon()) instanceof AbilityDampenerBlockEntity field) field.fill(DampenerGeometry.CAPACITY);
        // An unpowered one beside it, to compare the faces.
        level.setBlockAndUpdate(pylon().offset(-2, 0, 0), ModBlocks.ABILITY_DAMPENER.get().defaultBlockState());
    }

    private static void view(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        onServer(mc, p -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.dampener")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Dampener-" + System.currentTimeMillis(),
                    new LevelSettings("Dampener preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        int t = ++worldTicks;
        if (t >= 330 && t <= 372 && t % 8 == 2) onServer(mc, p -> {
            if (PowerWeapons.rounds(shooter.getMainHandItem()) <= 1) PowerWeapons.reload(shooter.getMainHandItem());
            System.out.println("DAMPENER_FIRE " + PowerWeapons.fire(shooter, husk.getBoundingBox().getCenter()));
        });
        switch (t) {
            case 150 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(6000);
                p.getAbilities().invulnerable = true; p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(p.serverLevel(), X + 0.5, Y + 6, Z - 8.5, 0, 22);
                stage(p);
            });
            case 160 -> { mc.options.hideGui = true; mc.gui.getChat().clearMessages(false); }
            case 166 -> shot("1-spin-up");
            case 190 -> shot("2-field-day");
            case 196 -> view(mc, X - 14.5, Y + 3, Z + 2.5, -45, 8);
            case 206 -> shot("3-field-side");
            case 210 -> onServer(mc, p -> p.serverLevel().setDayTime(18000));
            case 224 -> view(mc, X + 0.5, Y + 6, Z - 8.5, 0, 22);
            case 236 -> shot("4-field-night");
            // Inside, looking out (first person).
            case 240 -> view(mc, X + 0.5, Y, Z + 6.5, 160, 4);
            case 254 -> { shot("5-inside"); onServer(mc, p -> System.out.println("DAMPENER_INSIDE dampened=" + Dampening.dampened(p))); }
            // The two faces up close.
            case 258 -> { onServer(mc, p -> p.serverLevel().setDayTime(6000)); view(mc, X - 0.5, Y + 1.6, Z + 7.0, 0, 30); }
            case 270 -> shot("6-faces");
            // The Nullifier: a stand fires at a husk across the range.
            case 300 -> onServer(mc, p -> {
                ServerLevel level = p.serverLevel();
                level.getBlockEntity(pylon(), ModBlocks.ABILITY_DAMPENER_ENTITY.get()).ifPresent(f -> {
                    // Switch the field off so the shot is seen on its own.
                    if (f.enabled()) level.setBlockAndUpdate(pylon().above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                });
                shooter = new ArmorStand(EntityType.ARMOR_STAND, level);
                shooter.moveTo(X - 9.5, Y, Z + 18.5, -90, 0);
                shooter.setShowArms(true);
                shooter.setItemSlot(EquipmentSlot.MAINHAND, PowerWeapons.gun(PowerWeapons.DAMPENER_GUN));
                level.addFreshEntity(shooter);
                PowerWeapons.draw(shooter);
                husk = EntityType.HUSK.create(level);
                husk.moveTo(X + 1.5, Y, Z + 18.5, 90, 0);
                husk.setNoAi(true);
                husk.setPersistenceRequired();
                level.addFreshEntity(husk);
            });
            case 310 -> view(mc, X - 4.5, Y + 3.5, Z + 12.5, 0, 22);
            case 331 -> shot("7-shot-a");
            case 333 -> shot("7-shot-b");
            case 336 -> shot("8-hit");
            case 352 -> shot("9-collar");
            case 376 -> onServer(mc, p -> System.out.println("DAMPENER_HUSK struck=" + Dampening.struck(husk)));
            // Charging the gun from the projector, first person.
            case 380 -> onServer(mc, p -> {
                p.setItemInHand(InteractionHand.MAIN_HAND, PowerWeapons.gun(PowerWeapons.DAMPENER_GUN));
                p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 7.5, 0, 25);
            });
            case 392 -> { mc.options.hideGui = false; onServer(mc, p -> System.out.println("DAMPENER_CHARGE moved="
                    + Nullifier.charge(p, p.getMainHandItem(), pylon()) + " rounds=" + Nullifier.rounds(p.getMainHandItem()))); }
            case 396 -> shot("10-charge-a");
            case 402 -> shot("10-charge-b");
            case 420 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> Nullifier.charge(p, p.getMainHandItem(), pylon())); }
            case 426 -> shot("11-charge-front");
            case 440 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> p.teleportTo(p.serverLevel(), X + 0.5, Y, Z - 4.5, 0, 4)); }
            case 470 -> shot("12-held");
            case 480 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("DAMPENER_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
