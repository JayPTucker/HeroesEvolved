package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.MorphClient;
import com.jayptucker.heroesevolved.client.UnseenClient;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.dampener.AbilityDampenerBlockEntity;
import com.jayptucker.heroesevolved.dampener.DampenerGeometry;
import com.jayptucker.heroesevolved.entity.BlockDebris;
import com.jayptucker.heroesevolved.formula.CentrifugeBlockEntity;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Rarity;
import com.jayptucker.heroesevolved.hazard.SuitFabricatorMenu;
import com.jayptucker.heroesevolved.hazard.SuitItems;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftForm;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Heroes Evolved with JEI and Jade: what Jade names for our machines, a gifted mob, a villager melting down, a creature
 * wearing a borrowed face, a hidden sheep and a flying rock; then JEI's list of our items, our three recipe
 * categories, an info page, the EMP grenade at TaCZ's workbench, the Suit Fabricator beside JEI's list, and a
 * check that none of our keys clash with JEI's. Each Jade scene checks what Jade is really targeting and reports PASS
 * or FAIL. Run with {@code -PnoJei -PnoJade} it stages and photographs the same scenes without either mod, which is
 * the check that the mod runs without them. Staged 2400 blocks from spawn; screenshots go to
 * build/compat-preview/screenshots.
 *
 * <pre>./gradlew runClient --init-script tools/compat-preview.init.gradle</pre>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class CompatPreview {
    private static final int X = 2400, Y = -60, Z = 0;
    private static boolean started;
    private static int ticks, worldTicks, passed, failed, photos;
    private static String pending;
    private static Mob gifted, faced;
    private static Mob hidden;
    private static Villager melting;
    private static final List<Runnable> LATER = new ArrayList<>();

    static void check(String what, boolean ok) {
        if (ok) passed++; else failed++;
        PreviewQuiet.report("COMPAT_CHECK " + (ok ? "PASS" : "FAIL") + ": " + what);
    }

    private static boolean jei() { return ModList.get().isLoaded("jei"); }

    private static boolean jade() { return ModList.get().isLoaded("jade"); }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                action.accept(server.getPlayerList().getPlayer(mc.player.getUUID()));
            } catch (Throwable t) {
                PreviewQuiet.report("COMPAT_CHECK FAIL: server step threw " + t);
            }
        });
    }

    private static void shot(String name) { pending = "compat-" + name + ".png"; }

    /** Stands the player {@code back} blocks south of {@code at}, looking at its height {@code aim}. */
    private static void face(Minecraft mc, BlockPos at, double back, double aim) {
        double eye = 1.62, dy = (at.getY() + aim) - (Y + eye);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, back));
        onServer(mc, p -> {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.teleportTo(p.serverLevel(), at.getX() + 0.5, Y, at.getZ() + 0.5 - back, 0, pitch);
        });
    }

    /** Checks what Jade is targeting against what the eye should see. */
    private static void jadeSees(String scene, String expected) {
        if (!jade()) { System.out.println("COMPAT_JADE " + scene + " skipped: Jade is not loaded"); return; }
        String target = CompatPreviewJade.target();
        System.out.println("COMPAT_JADE " + scene + " -> " + target);
        check("Jade on " + scene + " shows " + expected + " (" + target + ")", target.startsWith(expected));
    }

    private static BlockPos spot(int dx) { return new BlockPos(X + dx, Y, Z + 5); }

    private static void stage(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int dx = -14; dx <= 50; dx++)
            for (int dz = -8; dz <= 14; dz++)
                level.setBlockAndUpdate(new BlockPos(X + dx, Y - 1, Z + dz),
                        ((dx + dz) % 5 == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState());
        // A back wall behind every subject, so a look that goes through something ends on a block.
        for (int dx = -14; dx <= 50; dx++) for (int dy = 0; dy < 4; dy++)
            level.setBlockAndUpdate(new BlockPos(X + dx, Y + dy, Z + 7), Blocks.SMOOTH_STONE.defaultBlockState());
        // The Ability Dampener, full and switched on.
        level.setBlockAndUpdate(spot(0), ModBlocks.ABILITY_DAMPENER.get().defaultBlockState());
        if (level.getBlockEntity(spot(0)) instanceof AbilityDampenerBlockEntity field) field.fill(DampenerGeometry.CAPACITY / 2);
        // The Centrifuge, spinning a sample.
        level.setBlockAndUpdate(spot(4), ModBlocks.CENTRIFUGE.get().defaultBlockState());
        if (level.getBlockEntity(spot(4)) instanceof CentrifugeBlockEntity centrifuge) {
            centrifuge.setItem(CentrifugeBlockEntity.INPUT, new ItemStack(ModItems.BLOOD_SAMPLE.get()));
            centrifuge.setItem(CentrifugeBlockEntity.FUEL, new ItemStack(Items.REDSTONE, 4));
        }
        level.setBlockAndUpdate(spot(28), SuitItems.SUIT_FABRICATOR.get().defaultBlockState());
        // A gifted pillager.
        gifted = EntityType.PILLAGER.create(level);
        gifted.moveTo(X + 8.5, Y, Z + 5.5, 180, 0);
        gifted.setNoAi(true);
        gifted.setPersistenceRequired();
        level.addFreshEntity(gifted);
        GiftedMobs.bestow(gifted, ModAbilities.PYROKINESIS_ID, Rarity.RARE, false, Gift.Origin.ADMIN, null, null);
        // A pig that this client will see wearing a cow's face: a Borrowed Face, set on the client below. (Not a
        // monster: a zombie burns by day, and the friendly gifted mob next to it hunts monsters.)
        faced = EntityType.PIG.create(level);
        faced.moveTo(X + 12.5, Y, Z + 5.5, 180, 0);
        faced.setNoAi(true);
        faced.setPersistenceRequired();
        level.addFreshEntity(faced);
        // A sheep this client will be told is hidden, in front of a crafting table. (Not a villager: the meltdown
        // gathers every villager near it to lead them to safety.)
        hidden = EntityType.SHEEP.create(level);
        hidden.moveTo(X + 16.5, Y, Z + 5.5, 180, 0);
        hidden.setNoAi(true);
        hidden.setPersistenceRequired();
        level.addFreshEntity(hidden);
        level.setBlockAndUpdate(new BlockPos(X + 16, Y, Z + 6), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(X + 16, Y + 1, Z + 6), Blocks.CRAFTING_TABLE.defaultBlockState());
        // A villager melting down, on a long fuse, well clear of the others: even on a long fuse his heat kills what
        // stands near him (an earlier run lost the hidden subject to it).
        melting = EntityType.VILLAGER.create(level);
        melting.setVillagerData(melting.getVillagerData().setProfession(VillagerProfession.FARMER));
        melting.moveTo(X + 44.5, Y, Z + 5.5, 180, 0);
        melting.setNoAi(true);
        level.addFreshEntity(melting);
        var meltdown = com.jayptucker.heroesevolved.village.VillagerMeltdown.start(melting, 20 * 60 * 20);
        System.out.println("COMPAT_MELTDOWN started=" + (meltdown != null));
        // A rock in the air (Density's earthbending): a block display, which Jade must look straight past.
        BlockDebris rock = new BlockDebris(level, Blocks.COBBLESTONE.defaultBlockState(), 0.8F);
        rock.setPos(X + 24.5, Y + 1.6, Z + 4.0);
        level.addFreshEntity(rock);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.compat")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            System.out.println("COMPAT_PREVIEW jei=" + jei() + " jade=" + jade());
            mc.options.pauseOnLostFocus = false;
            mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Compat-" + System.currentTimeMillis(),
                    new LevelSettings("Compat preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        int t = ++worldTicks;
        if (t >= 100) mc.getToasts().clear();
        // Park the pointer in an empty corner, so no tooltip covers JEI's pages.
        if (t >= 500 && t < 700) org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 2, 2);
        switch (t) {
            // A new player is enrolled in the Last Day (another dimension): end it and let them out of the bunker's pod.
            case 104 -> onServer(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
            case 110 -> {
                com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
                onServer(mc, com.jayptucker.heroesevolved.story.bunker.CryoChamberService::release);
            }
            case 150 -> onServer(mc, p -> {
                ServerLevel world = p.getServer().overworld();
                for (int cx = (X - 16) >> 4; cx <= (X + 56) >> 4; cx++)
                    for (int cz = (Z - 16) >> 4; cz <= (Z + 16) >> 4; cz++) world.setChunkForced(cx, cz, true);
                world.setDayTime(6000);
                p.getAbilities().invulnerable = true;
                p.onUpdateAbilities();
                p.teleportTo(world, X + 0.5, Y, Z + 0.5, 0, 20);
                p.removeAllEffects();
                stage(p);
                System.out.println("COMPAT_PREVIEW staged in " + p.level().dimension().location() + " on "
                        + p.level().getBlockState(p.blockPosition().below()));
            });
            case 200 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                // What this client is told about two of the subjects: one wears a cow's face, one is hidden.
                MorphClient.update(faced.getId(), ShapeshiftForm.of(EntityType.COW), false);
                UnseenClient.update(hidden.getId(), true, false);
                face(mc, spot(0), 2.5, 0.5);
            }
            case 240 -> { shot("jade-1-dampener"); jadeSees("the Ability Dampener", "block heroesevolved:ability_dampener"); }
            case 246 -> face(mc, spot(4), 2.5, 0.5);
            case 290 -> { shot("jade-2-centrifuge"); jadeSees("a spinning Centrifuge", "block heroesevolved:centrifuge"); }
            case 296 -> face(mc, spot(8), 2.6, 1.0);
            case 330 -> { shot("jade-3-gifted"); jadeSees("a gifted pillager", "entity minecraft:pillager"); }
            case 336 -> face(mc, spot(12), 2.6, 0.5);
            case 370 -> {
                shot("jade-4-borrowed-face");
                check("the client draws the pig as a cow", MorphClient.seenAs(mc.level.getEntity(faced.getId())) instanceof net.minecraft.world.entity.animal.Cow);
                jadeSees("a pig wearing a cow's face", "entity minecraft:cow");
            }
            case 376 -> face(mc, spot(16), 2.6, 0.7);
            case 410 -> {
                shot("jade-5-hidden");
                var sheep = mc.level.getEntity(hidden.getId());
                check("the hidden sheep is on this client and concealed (" + sheep + ")", sheep != null && UnseenClient.concealed(sheep));
                jadeSees("a hidden sheep", "block minecraft:crafting_table");
            }
            case 416 -> face(mc, spot(44), 2.6, 1.0);
            case 450 -> {
                shot("jade-6-meltdown");
                boolean live = SetPieceClient.boundTo(Piece.VILLAGE_MELTDOWN, melting.getId()) != null;
                System.out.println("COMPAT_MELTDOWN piece on the client=" + live);
                jadeSees("a villager melting down", "entity minecraft:villager");
            }
            case 456 -> face(mc, spot(24), 2.6, 1.6);
            case 490 -> { shot("jade-7-rock"); jadeSees("a flying rock", "block minecraft:smooth_stone"); }
            // JEI.
            case 500 -> {
                MorphClient.update(faced.getId(), null, false);
                UnseenClient.update(hidden.getId(), false, false);
                checkKeys(mc);
                if (!jei()) { System.out.println("COMPAT_JEI skipped: JEI is not loaded"); worldTicks = 700; return; }
                if (!CompatPreviewJei.ready()) check("JEI's runtime is up", false);
                mc.setScreen(new InventoryScreen(mc.player));
                CompatPreviewJei.filterOurs();
            }
            case 520 -> { shot("jei-1-list"); CompatPreviewJei.checkList(); }
            case 526 -> CompatPreviewJei.showType("suit_fabricator");
            case 545 -> shot("jei-2-fabricator");
            case 550 -> CompatPreviewJei.showType("centrifuge");
            case 565 -> shot("jei-3-centrifuge");
            case 570 -> CompatPreviewJei.showType("synthesis");
            case 585 -> shot("jei-4-synthesis");
            case 590 -> CompatPreviewJei.showGunSmith();
            case 605 -> shot("jei-5-gun-smith-table");
            case 610 -> CompatPreviewJei.showUsesOf(ModItems.SYLARS_WATCH.get());
            case 625 -> shot("jei-6-info");
            case 630 -> {
                mc.setScreen(null);
                onServer(mc, p -> p.openMenu(new SimpleMenuProvider((id, inventory, player) -> new SuitFabricatorMenu(id, inventory,
                        ContainerLevelAccess.create(p.level(), spot(28))), Component.translatable("block.heroesevolved.suit_fabricator"))));
            }
            case 650 -> shot("jei-7-fabricator-screen");
            case 660 -> { CompatPreviewJei.clearFilter(); mc.setScreen(null); }
            case 720 -> {
                PreviewQuiet.report("COMPAT_PREVIEW_RESULT " + (failed == 0 ? "PASS" : "FAIL") + ": " + passed + " checks passed, "
                        + failed + " failed, " + photos + " photos, jei=" + jei() + " jade=" + jade());
                PreviewQuiet.quit();
            }
            default -> { }
        }
    }

    /**
     * Our keys may share a key with JEI's (NeoForge's Controls screen marks any two bindings on one key red), but never
     * in a context where both are live: ours only in the world (and our wheels), JEI's only in menus.
     */
    private static void checkKeys(Minecraft mc) {
        List<String> shared = new ArrayList<>(), live = new ArrayList<>();
        for (KeyMapping ours : mc.options.keyMappings) {
            if (!ours.getName().startsWith("key.heroesevolved.") || ours.isUnbound()) continue;
            check(ours.getName() + " is in-game only", ours.getKeyConflictContext() == ModKeyMappings.PLAY);
            for (KeyMapping other : mc.options.keyMappings) {
                if (other == ours || other.isUnbound() || !other.getName().startsWith("key.jei.") || !ours.getKey().equals(other.getKey())) continue;
                shared.add(ours.getName() + "/" + other.getName());
                if (ours.getKeyConflictContext().conflicts(other.getKeyConflictContext())
                        || other.getKeyConflictContext().conflicts(ours.getKeyConflictContext())) live.add(ours.getName() + "/" + other.getName());
            }
        }
        System.out.println("COMPAT_KEYS shared with JEI=" + shared + " live together=" + live);
        check("none of our keys is live where a JEI key on the same key is", live.isEmpty());
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            photos++;
            System.out.println("COMPAT_CAPTURE " + pending);
        } catch (Exception ex) {
            PreviewQuiet.report("COMPAT_CHECK FAIL: screenshot " + pending + " " + ex);
        }
        pending = null;
    }
}
