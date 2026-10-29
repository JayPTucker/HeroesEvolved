package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.regeneration.RegenerationService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/** Shows each Regeneration power at dusk on a flat world, with friends and a zombie, and photographs it. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RegenerationPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static LivingEntity villager, cow, zombie;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
    }

    private static LivingEntity spawn(ServerPlayer p, EntityType<? extends LivingEntity> type, double x, double z) {
        var mob = type.create(p.serverLevel());
        if (mob instanceof net.minecraft.world.entity.Mob m) { m.setNoAi(true); m.setPersistenceRequired(); }
        mob.moveTo(x + 0.5, -60, z + 0.5, 180, 0);
        p.serverLevel().addFreshEntity(mob);
        return mob;
    }

    private static void face(ServerPlayer p, LivingEntity target) {
        var delta = target.getBoundingBox().getCenter().subtract(p.getEyePosition());
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), (float) Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.regeneration")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Regeneration-" + System.currentTimeMillis(),
                    new LevelSettings("Regeneration preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(33L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(12800);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.REGENERATION_ID));
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.REGENERATION_ID).unlock(ModAbilities.REGENERATION_ID));
                PlayerProgressionService.setLevel(p, 5);
                place(p, 300, 0, 0, 10);
                villager = spawn(p, EntityType.VILLAGER, 302, 7);
                cow = spawn(p, EntityType.COW, 297, 5);
                zombie = spawn(p, EntityType.ZOMBIE, 300, 2);
            });
            // Hurt, then Rapid Mending: the wound closes (seen from the front).
            case 230 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 312, 0, 0, 10); p.hurt(p.damageSources().generic(), 10); }); }
            case 232 -> pending = "regen-1-hurt.png";
            case 245 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); RegenerationService.rapidMending(p, 5); });
            case 247 -> pending = "regen-2-mend-a.png";
            case 251 -> pending = "regen-2-mend-b.png";
            // Blood Gift to the villager, from behind.
            case 265 -> { mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { villager.setHealth(4); place(p, 296, -3, 0, 10); face(p, villager); RegenerationService.bloodGift(p, 5); }); }
            case 268 -> pending = "regen-3-gift.png";
            // Indestructible: a reckless punch on the zombie, and an arrow pushed out.
            case 290 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 300, 0, 0, 10); RegenerationService.indestructible(p, 5); zombie.hurt(p.damageSources().playerAttack(p), 2); }); }
            case 291 -> pending = "regen-4-reckless.png";
            case 300 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> {
                Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
                arrow.setPos(p.position().add(0, 1, 2)); arrow.setDeltaMovement(0, 0, -1);
                p.serverLevel().addFreshEntity(arrow);
                p.invulnerableTime = 0; p.hurt(p.damageSources().arrow(arrow, null), 6); }); }
            case 302 -> pending = "regen-5-arrow-out.png";
            // Blood Bond with the HUD showing Regrowth.
            case 320 -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> { place(p, 300, -2, 0, 22); RegenerationService.addRegrowth(p, 70); RegenerationService.healingAura(p, 5); }); }
            case 330 -> onServer(mc, p -> { villager.hurt(p.serverLevel().damageSources().generic(), 6); cow.hurt(p.serverLevel().damageSources().generic(), 4); });
            case 332 -> pending = "regen-6-bond.png";
            // Second Life into the villager, then the killing blow that does not take.
            case 360 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { place(p, 296, -2, 0, 10); face(p, villager);
                RegenerationService.addRegrowth(p, 60); RegenerationService.secondLife(p); }); }
            case 363 -> pending = "regen-7-second-life.png";
            case 380 -> onServer(mc, p -> villager.hurt(p.serverLevel().damageSources().generic(), 200));
            case 382 -> pending = "regen-8-friend-revived.png";
            // Her own death, and Resurrection.
            case 400 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 300, 0, 0, 10); p.hurt(p.damageSources().generic(), 1000); }); }
            case 402 -> pending = "regen-9-resurrection-a.png";
            case 418 -> pending = "regen-9-resurrection-b.png";
            case 440 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("REGEN_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
