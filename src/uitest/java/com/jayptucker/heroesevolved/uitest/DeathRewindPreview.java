package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/** Dies with full pockets, presses Rewind on the death screen, and checks the death was undone. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class DeathRewindPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void report(ServerPlayer p, String when) {
        var items = p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(296, -64, -12, 322, -50, 12));
        int drops = items.size();
        for (ItemEntity item : items) System.out.println("DEATH_REWIND   item " + item.getItem() + " at " + item.blockPosition() + " age=" + item.getAge() + " born=" + com.jayptucker.heroesevolved.time.TimeRewindEntities.birth(p.getUUID(), item.getUUID())
                + " now=" + p.getServer().overworld().getGameTime());
        System.out.println("DEATH_REWIND " + when + ": alive=" + p.isAlive() + " pos=" + p.blockPosition()
                + " diamonds=" + p.getInventory().countItem(Items.DIAMOND) + " swords=" + p.getInventory().countItem(Items.DIAMOND_SWORD)
                + " dropsAtDeathSpot=" + drops);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.deathrewind")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("DeathRewind-" + System.currentTimeMillis(),
                    new LevelSettings("Death rewind preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(5L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(6000);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.TIME_MANIPULATION_ID));
                p.setData(ModDataAttachments.TIME_INTRO_SEEN, true);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
                PlayerProgressionService.setLevel(p, 4);
                p.getInventory().clearContent();
                p.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
                p.getInventory().add(new ItemStack(Items.DIAMOND, 32));
                p.teleportTo(p.serverLevel(), 300.5, -60, 0.5, -90, 10);
            });
            // Twenty seconds of history standing here, then walk off and die.
            case 600 -> onServer(mc, p -> p.teleportTo(p.serverLevel(), 309.5, -60, 0.5, -90, 10));
            case 640 -> onServer(mc, p -> { report(p, "before death"); p.hurt(p.damageSources().generic(), 1000); });
            case 660 -> onServer(mc, p -> report(p, "dead"));
            case 665 -> pending = "death-rewind-1-screen.png";
            case 680 -> {
                if (mc.screen instanceof DeathScreen screen) for (var child : screen.children())
                    if (child instanceof Button button && button.getMessage().getString().contains("Rewind")) { button.onPress(); System.out.println("DEATH_REWIND pressed"); }
            }
            case 720 -> onServer(mc, p -> report(p, "after rewind"));
            case 725 -> pending = "death-rewind-2-back.png";
            case 740 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("DEATH_REWIND_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
