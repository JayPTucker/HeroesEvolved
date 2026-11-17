package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.hazard.BodyBurn;
import com.jayptucker.heroesevolved.hazard.HazardDamage;
import com.jayptucker.heroesevolved.hazard.SuitFabricatorMenu;
import com.jayptucker.heroesevolved.hazard.SuitItems;
import com.jayptucker.heroesevolved.radiation.MeltdownField;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Photographs the protective suits (worn, on stands, dyed), a player's skin burning away, a meltdown's field and shock
 * waves on a scattered patch of ground, a body burning away, the Suit Fabricator's screen and the new blocks
 * (flag {@code heroes.preview.suits}). Shots go to {@code build/suit-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SuitPreview {
    private static final int STAGE = 150;
    private static boolean started;
    private static int startup, age;
    private static String capture;
    private static final Vec3 ANCHOR = new Vec3(STAGE + .5, -60, STAGE + .5);
    private static final List<net.minecraft.world.entity.Entity> PROPS = new ArrayList<>();
    private static volatile LivingEntity core;
    private static volatile int coreAge;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.suits"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static ServerLevel world(ServerPlayer p) { return p.getServer().overworld(); }

    private static void log(String what) { System.out.println("SUIT_PREVIEW " + what); }

    private static void side(Vec3 at, Vec3 look) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> mc.execute(() -> TempestCamera.fix(at, look)));
    }

    private static void suit(LivingEntity e, boolean velocity, int dye) {
        ItemStack[] pieces = velocity
                ? new ItemStack[] {new ItemStack(SuitItems.VELOCITY_COWL.get()), new ItemStack(SuitItems.VELOCITY_SUIT.get()),
                        new ItemStack(SuitItems.VELOCITY_LEGGINGS.get()), new ItemStack(SuitItems.VELOCITY_BOOTS.get())}
                : new ItemStack[] {new ItemStack(SuitItems.RADIATION_HOOD.get()), new ItemStack(SuitItems.RADIATION_COAT.get()),
                        new ItemStack(SuitItems.RADIATION_TROUSERS.get()), new ItemStack(SuitItems.RADIATION_BOOTS.get())};
        if (dye >= 0) for (ItemStack s : pieces) s.set(DataComponents.DYED_COLOR, new DyedItemColor(dye, false));
        e.setItemSlot(EquipmentSlot.HEAD, pieces[0]);
        e.setItemSlot(EquipmentSlot.CHEST, pieces[1]);
        e.setItemSlot(EquipmentSlot.LEGS, pieces[2]);
        e.setItemSlot(EquipmentSlot.FEET, pieces[3]);
    }

    private static ArmorStand stand(ServerPlayer p, double dx, double dz, boolean velocity, int dye) {
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, world(p));
        stand.moveTo(ANCHOR.x + dx, -60, ANCHOR.z + dz, 0, 0);
        stand.setShowArms(true);
        suit(stand, velocity, dye);
        world(p).addFreshEntity(stand);
        PROPS.add(stand);
        return stand;
    }

    private static Mob actor(ServerPlayer p, EntityType<? extends Mob> type, double dx, double dz) {
        Mob mob = type.create(world(p));
        mob.setNoAi(true);
        mob.moveTo(ANCHOR.x + dx, -60, ANCHOR.z + dz, 180, 0);
        world(p).addFreshEntity(mob);
        PROPS.add(mob);
        return mob;
    }

    private static void clear() {
        PROPS.forEach(net.minecraft.world.entity.Entity::discard);
        PROPS.clear();
    }

    /** A patch of ground with things to burn: grass, flowers, a pool, sand, a little tree. */
    private static void garden(ServerPlayer p, int cx, int cz) {
        ServerLevel level = world(p);
        var random = level.random;
        for (int dx = -9; dx <= 9; dx++)
            for (int dz = -9; dz <= 9; dz++) {
                BlockPos ground = new BlockPos(cx + dx, -61, cz + dz), above = ground.above();
                int r = random.nextInt(10);
                if (Math.abs(dx - 5) <= 1 && Math.abs(dz + 4) <= 1) level.setBlockAndUpdate(ground, Blocks.WATER.defaultBlockState());
                else if (dx <= -4 && dz >= 3) level.setBlockAndUpdate(ground, Blocks.SAND.defaultBlockState());
                else if (r < 3) level.setBlockAndUpdate(above, Blocks.SHORT_GRASS.defaultBlockState());
                else if (r == 3) level.setBlockAndUpdate(above, Blocks.POPPY.defaultBlockState());
                else if (r == 4) level.setBlockAndUpdate(above, Blocks.COBBLESTONE.defaultBlockState());
            }
        for (int y = -60; y <= -57; y++) level.setBlockAndUpdate(new BlockPos(cx - 5, y, cz - 5), Blocks.OAK_LOG.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            level.setBlockAndUpdate(new BlockPos(cx - 5 + dx, -56, cz - 5 + dz), Blocks.OAK_LEAVES.defaultBlockState());
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        LivingEntity c = core;
        if (c == null || !c.isAlive()) return;
        coreAge++;
        MeltdownField.tick(c, Math.min(1, 0.55F + coreAge / 160F), false);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(70);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.options.renderDistance().set(6);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Suits-" + System.currentTimeMillis(),
                    new LevelSettings("Suit Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(37L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip); return; }
        if (age == 110) {
            server(mc, p -> {
                CryoChamberService.release(p);
                var world = world(p);
                for (int cx = (STAGE - 32) >> 4; cx <= (STAGE + 48) >> 4; cx++)
                    for (int cz = (STAGE - 32) >> 4; cz <= (STAGE + 48) >> 4; cz++) world.setChunkForced(cx, cz, true);
                p.setGameMode(GameType.SURVIVAL);
                p.getAbilities().invulnerable = true;
                p.onUpdateAbilities();
                p.teleportTo(world, ANCHOR.x, ANCHOR.y, ANCHOR.z, 0, 0);
                world.setDayTime(6000);
                log("staged in " + p.level().dimension().location() + " at " + p.blockPosition() + " on " + world.getBlockState(p.blockPosition().below()));
            });
            return;
        }
        if (age < 170) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 170);
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            // The suits: the player in the velocity suit between a radiation suit and a blue-dyed velocity suit.
            case 10 -> server(mc, p -> {
                p.teleportTo(world(p), ANCHOR.x, ANCHOR.y, ANCHOR.z, 0, 0);
                suit(p, true, -1);
                stand(p, -1.6, 0, false, -1);
                stand(p, 1.6, 0, true, 0x2050C8);
            });
            case 12 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 30 -> capture = "01-suits-front.png";
            case 32 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 38 -> capture = "02-suits-back.png";
            case 40 -> side(ANCHOR.add(0.4, 1.6, 2.6), ANCHOR.add(0, 1.3, 0));
            case 46 -> capture = "03-suit-close.png";
            // Burning up: the skin flakes away over the charred body.
            case 50 -> {
                side(null, null);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> {
                    clear();
                    for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                        p.setItemSlot(slot, ItemStack.EMPTY);
                    BodyBurn.atLeast(p, 0.3F);
                });
            }
            case 66 -> capture = "04-burning-30.png";
            case 68 -> server(mc, p -> BodyBurn.atLeast(p, 0.65F));
            case 84 -> capture = "05-burning-65.png";
            case 86 -> server(mc, p -> BodyBurn.atLeast(p, 0.95F));
            case 104 -> capture = "06-burnt.png";
            case 106 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 112 -> capture = "07-burnt-back.png";
            // A meltdown's field on a patch of ground with things to burn, from the side.
            case 120 -> {
                server(mc, p -> {
                    BodyBurn.heal(p);
                    int cx = (int) ANCHOR.x, cz = (int) ANCHOR.z + 18;
                    garden(p, cx, cz);
                    Mob c = actor(p, EntityType.ZOMBIE, 0, 18);
                    c.setInvulnerable(true);
                    actor(p, EntityType.PILLAGER, 4, 15);
                    actor(p, EntityType.PILLAGER, -7, 20);
                    actor(p, EntityType.SHEEP, 9, 22);
                    coreAge = 0;
                    core = c;
                });
                side(ANCHOR.add(17, 9, 5), ANCHOR.add(0, 0.5, 18));
            }
            case 150, 165, 180, 195 -> capture = String.format("08-field-%03d.png", t);
            case 200 -> side(ANCHOR.add(0, 30, 17.9), ANCHOR.add(0, 0, 18));
            default -> {
                if (t >= 204 && t <= 236 && t % 2 == 0) capture = String.format("09-waves-top-%03d.png", t);
                if (t >= 270 && t <= 330 && t % 6 == 0) capture = String.format("10-burn-away-%03d.png", t);
            }
        }
        switch (t) {
            case 240 -> server(mc, p -> { core = null; clear(); });
            // A body burning away, from close by.
            case 262 -> {
                server(mc, p -> {
                    Mob victim = actor(p, EntityType.PILLAGER, 0, 6);
                    victim.setYRot(200);
                    victim.yBodyRot = 200;
                });
                side(ANCHOR.add(3.5, 2, 3), ANCHOR.add(0, 1, 6));
            }
            case 268 -> server(mc, p -> {
                for (var e : PROPS) if (e instanceof Mob m && m.isAlive())
                    m.hurt(HazardDamage.source(world(p), HazardDamage.MELTDOWN), 1000);
            });
            // The Suit Fabricator's screen, the velocity suit's pattern picked and its materials in.
            case 340 -> {
                side(null, null);
                server(mc, p -> {
                    clear();
                    BlockPos at = BlockPos.containing(ANCHOR).offset(0, 0, 2);
                    world(p).setBlockAndUpdate(at, SuitItems.SUIT_FABRICATOR.get().defaultBlockState());
                    p.openMenu(world(p).getBlockState(at).getMenuProvider(world(p), at));
                    if (p.containerMenu instanceof SuitFabricatorMenu menu) {
                        menu.input().setItem(0, new ItemStack(SuitItems.ARAMID_FIBER.get(), 8));
                        menu.input().setItem(1, new ItemStack(SuitItems.CERAMIC_PLATE.get(), 2));
                        menu.input().setItem(2, new ItemStack(SuitItems.LEAD_INGOT.get(), 3));
                        menu.input().setItem(3, new ItemStack(Items.LEATHER_HELMET));
                        menu.clickMenuButton(p, 5);
                        menu.broadcastChanges();
                    }
                });
            }
            case 356 -> capture = "11-fabricator.png";
            // The new blocks in a row.
            case 360 -> {
                mc.setScreen(null);
                server(mc, p -> {
                    p.closeContainer();
                    var level = world(p);
                    BlockPos base = BlockPos.containing(ANCHOR).offset(-2, 0, 5);
                    level.setBlockAndUpdate(base, SuitItems.LEAD_ORE.get().defaultBlockState());
                    level.setBlockAndUpdate(base.east(), SuitItems.DEEPSLATE_LEAD_ORE.get().defaultBlockState());
                    level.setBlockAndUpdate(base.east(2), SuitItems.LEADED_GLASS.get().defaultBlockState());
                    level.setBlockAndUpdate(base.east(3), SuitItems.SUIT_FABRICATOR.get().defaultBlockState()
                            .setValue(com.jayptucker.heroesevolved.hazard.SuitFabricatorBlock.FACING, net.minecraft.core.Direction.NORTH));
                    level.setBlockAndUpdate(base.east(4), SuitItems.LEADED_GLASS.get().defaultBlockState());
                });
                side(ANCHOR.add(0, 2.2, 1.2), ANCHOR.add(0, 0.5, 5.5));
            }
            case 372 -> capture = "12-blocks.png";
            case 380 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null || !on()) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            log("CAPTURE " + capture);
        } catch (Exception e) {
            log("CAPTURE FAILED " + capture + " " + e);
        }
        capture = null;
    }
}
