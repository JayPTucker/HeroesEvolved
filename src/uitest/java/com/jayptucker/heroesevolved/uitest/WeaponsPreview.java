package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.company.CompanyAgentEntity;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.weapons.EmpGrenade;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The power-themed TaCZ guns firing: the Thermal Projector's flame, the Cryo Carbine's frost bolts and freeze, the
 * Company taser's barbs, an EMP grenade putting out a wall of lamps, and a Company guard with his Glock and taser.
 * Each gun is also photographed in first person. Staged 1500 blocks from spawn. Screenshots go to
 * build/weapons-preview/screenshots.
 *
 * <pre>./gradlew runClient --init-script tools/weapons-preview.init.gradle</pre>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class WeaponsPreview {
    private static final int X = 1500, Y = -60, Z = 0;
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static final List<ArmorStand> SHOOTERS = new ArrayList<>();
    private static final List<LivingEntity> TARGETS = new ArrayList<>();
    private static CompanyAgentEntity guard;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void shot(String name) { pending = "weapons-" + name + ".png"; }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) { level.setBlockAndUpdate(new BlockPos(x, y, z), state); }

    /** A firing range: a concrete pad, three lanes, a lamp wall at the end and a gun smith table by the door. */
    private static void range(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -4; dz <= 24; dz++)
                set(level, X + dx, Y - 1, Z + dz, ((dx + dz) % 7 == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState());
        for (int dx = -6; dx <= 6; dx += 4)
            for (int dz = 2; dz <= 14; dz++) set(level, X + dx, Y, Z + dz, Blocks.POLISHED_ANDESITE.defaultBlockState());
        // The lamp wall: lamps on a row of redstone blocks.
        for (int dx = -6; dx <= 6; dx++) {
            set(level, X + dx, Y, Z + 20, Blocks.REDSTONE_BLOCK.defaultBlockState());
            set(level, X + dx, Y + 1, Z + 20, Blocks.REDSTONE_LAMP.defaultBlockState());
            set(level, X + dx, Y + 2, Z + 20, Blocks.REDSTONE_LAMP.defaultBlockState());
        }
        BlockState table = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("tacz", "gun_smith_table")).defaultBlockState();
        set(level, X - 6, Y, Z - 2, table);
    }

    /** A shooter (an armor stand: no AI to get in the way) holding {@code gun} at a lane's start, and a husk at its end. */
    private static void lane(ServerPlayer p, int dx, ResourceLocation gun, int distance) {
        ServerLevel level = p.serverLevel();
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
        stand.moveTo(X + dx + 0.5, Y, Z + 2.5, 0, 0);
        stand.setShowArms(true);
        stand.setItemSlot(EquipmentSlot.MAINHAND, PowerWeapons.gun(gun));
        level.addFreshEntity(stand);
        PowerWeapons.draw(stand);
        SHOOTERS.add(stand);
        Mob husk = EntityType.HUSK.create(level);
        husk.moveTo(X + dx + 0.5, Y, Z + 2.5 + distance, 180, 0);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        husk.setHealth(200);
        level.addFreshEntity(husk);
        TARGETS.add(husk);
    }

    private static void fire(int lane) {
        ArmorStand stand = SHOOTERS.get(lane);
        if (PowerWeapons.rounds(stand.getMainHandItem()) <= 1) PowerWeapons.reload(stand.getMainHandItem());
        System.out.println("WEAPONS_FIRE " + lane + " " + PowerWeapons.fire(stand, TARGETS.get(lane).getBoundingBox().getCenter()));
    }

    private static void hold(Minecraft mc, ResourceLocation gun) {
        onServer(mc, p -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, PowerWeapons.gun(gun));
            p.teleportTo(p.serverLevel(), X + 0.5, Y, Z - 1.5, 0, 4);
        });
    }

    private static void view(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        onServer(mc, p -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.weapons")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Weapons-" + System.currentTimeMillis(),
                    new LevelSettings("Weapons preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        int t = ++worldTicks;
        // The three lanes firing, from behind and to the side.
        if (t >= 236 && t <= 300) {
            if (t % 2 == 0) fireOnServer(mc, 0);
            if (t % 6 == 0) fireOnServer(mc, 1);
            if (t == 250 || t == 290) fireOnServer(mc, 2);
        }
        switch (t) {
            case 150 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(18000);
                p.getAbilities().invulnerable = true; p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(p.serverLevel(), X + 0.5, Y + 4, Z - 6.5, 0, 20);
                range(p);
                lane(p, -4, PowerWeapons.HEAT_GUN, 7);
                lane(p, 0, PowerWeapons.COLD_GUN, 12);
                lane(p, 4, PowerWeapons.TASER, 6);
            });
            // Each gun in first person.
            case 230 -> { mc.options.hideGui = true; mc.gui.getChat().clearMessages(false); view(mc, X + 0.5, Y + 3.5, Z - 3.5, 0, 22); }
            case 242 -> shot("2-lanes-a");
            case 254 -> shot("2-lanes-b");
            case 262 -> view(mc, X - 7.5, Y + 2, Z + 6.5, -110, 12);
            case 268 -> shot("3-side-a");
            case 280 -> shot("3-side-b");
            case 292 -> shot("3-side-c");
            // An EMP grenade into the lamp wall.
            case 310 -> view(mc, X + 0.5, Y + 3, Z + 9.5, 0, 6);
            case 318 -> shot("4-emp-before");
            case 320 -> onServer(mc, p -> {
                EmpGrenade.Thrown grenade = new EmpGrenade.Thrown(p.serverLevel(), p);
                grenade.setItem(new ItemStack(ModItems.EMP_GRENADE.get()));
                grenade.moveTo(X + 0.5, Y + 2, Z + 12.5);
                grenade.setDeltaMovement(0, 0.2, 0.45);
                p.serverLevel().addFreshEntity(grenade);
            });
            case 334 -> shot("4-emp-landed");
            case 345 -> shot("4-emp-pulse-a");
            case 350 -> shot("4-emp-pulse-b");
            case 380 -> shot("4-emp-after");
            // A Company guard: his Glock at range, then the taser up close.
            case 400 -> onServer(mc, p -> {
                for (LivingEntity target : TARGETS) target.discard();
                System.out.println("WEAPONS_LANES " + TARGETS.stream().map(tg -> tg.getHealth() + "/fire" + tg.getRemainingFireTicks()).toList());
                for (ArmorStand s : SHOOTERS) s.discard();
                ServerLevel level = p.serverLevel();
                guard = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), level, CompanyAgentEntity.Role.GUARD,
                        new Vec3(X + 0.5, Y, Z + 2.5), 0, false);
                level.addFreshEntity(guard);
                Mob husk = EntityType.HUSK.create(level);
                husk.moveTo(X + 0.5, Y, Z + 15.5, 180, 0);
                husk.setNoAi(true);
                husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
                husk.setHealth(200);
                level.addFreshEntity(husk);
                TARGETS.clear();
                TARGETS.add(husk);
                guard.setTarget(husk);
            });
            case 402 -> view(mc, X + 3.5, Y + 1.8, Z - 0.5, 18, 6);
            case 440 -> shot("5-guard-aim");
            case 446 -> shot("5-guard-fire-a");
            case 452 -> shot("5-guard-fire-b");
            case 470 -> onServer(mc, p -> TARGETS.get(0).teleportTo(X + 0.5, Y, Z + 7.5));
            case 476 -> view(mc, X + 4.5, Y + 1.8, Z + 4.5, 70, 8);
            case 490 -> shot("6-guard-taser-a");
            case 496 -> shot("6-guard-taser-b");
            case 504 -> shot("6-guard-taser-c");
            case 530 -> onServer(mc, p -> System.out.println("WEAPONS_GUARD hand=" + PowerWeapons.gunId(guard.getMainHandItem()) + " huskHp=" + TARGETS.get(0).getHealth()));
            // Each gun in first person, once the intro titles have gone (hiding the HUD would hide the gun too).
            case 540 -> { mc.options.hideGui = false; hold(mc, PowerWeapons.HEAT_GUN); }
            case 560 -> shot("1-held-heat");
            case 565 -> hold(mc, PowerWeapons.COLD_GUN);
            case 585 -> shot("1-held-cold");
            case 590 -> hold(mc, PowerWeapons.TASER);
            case 610 -> shot("1-held-taser");
            case 620 -> mc.stop();
            default -> { }
        }
        // Keep the guard's target (no AI on the husk, so nothing else would draw his eye).
        if (t > 400 && t < 540 && guard != null && t % 10 == 0 && !TARGETS.isEmpty())
            onServer(mc, p -> { if (!TARGETS.isEmpty() && guard.getTarget() != TARGETS.get(0)) guard.setTarget(TARGETS.get(0)); });
    }

    private static void fireOnServer(Minecraft mc, int lane) {
        onServer(mc, p -> { if (lane < SHOOTERS.size()) fire(lane); });
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("WEAPONS_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
