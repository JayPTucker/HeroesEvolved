package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.story.bunker.*;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.time.intro.city.CityAudit;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.Files;
import java.nio.file.Path;

@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class OdessaRuinsPreview {
    private static boolean started, ready, staged;
    private static int ticks, worldTicks, shot, wait;
    private static String pending;
    private record View(String name, double x, double y, double z, float yaw, float pitch) {}
    private static final View[] VIEWS = {
            new View("01-city-around-primatech", 90, 137, 160, 145, 28),
            new View("02-out-of-the-bunker", 4, 65, 102, 180, -5),
            new View("03-primatech-from-avenue", 4, 75, 82, 0, 8),
            new View("04-odessa-arms", -44, 78, 96, 0, 20),
            new View("05-arms-journal", -40, 65, 113, -160, 30),
            new View("06-market", 4, 64, 53, -90, -6),
            new View("07-church", 37, 78, 43, -62, 8),
            new View("08-towers", 4, 65, 30, 180, -18),
            new View("09-kirby-plaza", 27, 65, -74, 90, -4),
            new View("10-city-from-north", -20, 131, -150, -10, 30),
            new View("11-reclaimed-street", -30, 65, 68, -90, -5),
            new View("12-terrain-edge", 139, 93, 117, 100, 22)
    };
    private static final String ONLY = System.getProperty("heroes.preview.odessaruins.only", "");
    private static final View[] TOUR = ONLY.isBlank() ? VIEWS : java.util.Arrays.stream(VIEWS)
            .filter(v -> java.util.Arrays.asList(ONLY.split(",")).contains(v.name)).toArray(View[]::new);

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.odessaruins")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(20);
            mc.options.gamma().set(1.0);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Ruined-Odessa-" + System.currentTimeMillis(),
                    new LevelSettings("Ruined Odessa preview", GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2026L, true, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        if (++worldTicks == 30) server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                PrologueService.skip(player);
                CryoChamberService.release(player);
                player.setGameMode(GameType.SPECTATOR);
                player.removeAllEffects();
                var data = BunkerService.ensureBuilt(server);
                player.teleportTo(server.overworld(), data.origin.getX(), data.origin.getY() + 80, data.origin.getZ(), 180, 30);
                server.overworld().setDayTime(2000);
                staged = true;
            } catch (Throwable error) { PreviewQuiet.report("ODESSA_RUINS FAIL stage: " + error); }
        });
        if (!staged) return;
        OdessaRuins city = OdessaRuins.get(server.overworld());
        if (!city.complete()) {
            if (worldTicks % 100 == 0) System.out.println("ODESSA_RUINS_PROGRESS " + city.completedBlocks());
            if (worldTicks > 2400) { PreviewQuiet.report("ODESSA_RUINS FAIL build timed out"); PreviewQuiet.quit(); }
            return;
        }
        if (!ready) {
            ready = true;
            for (var journal : OdessaCaches.JOURNALS) {
                var pages = BookViewScreen.BookAccess.fromItem(OdessaCaches.book(journal)).pages();
                for (int i = 0; i < pages.size(); i++) {
                    int lines = mc.font.split(pages.get(i), 114).size();
                    if (lines > 14) PreviewQuiet.report("ODESSA_BOOK FAIL " + journal.title() + " page " + (i + 1) + ": " + lines + " lines");
                }
            }
            server.execute(() -> {
                try {
                    BlockPos o = city.origin();
                    int faults = 0;
                    // Audit each build at its own footing. A box down to the lowest valley also audits untouched caves
                    // and natural stone specks under the high districts, which this generator deliberately preserves.
                    for (int bx = 0; bx < 8; bx++) for (int bz = 0; bz < 8; bz++) {
                        if (com.jayptucker.heroesevolved.time.intro.city.CityPlan.kind(bx, bz) == 'Q') continue;
                        int x = o.getX() + CityGrid.originX(bx), z = o.getZ() + CityGrid.originZ(bz), floor = city.floor(bx, bz);
                        var report = CityAudit.run(server.overworld(), x, Math.max(server.overworld().getMinBuildHeight(), floor - 4), z, x + 31, floor + 64, z + 31);
                        faults += report.total();
                        if (report.total() > 0) System.out.println("ODESSA_DISTRICT " + bx + "," + bz + " floor " + floor + " " + report);
                        report.found().forEach((kind, positions) -> positions.forEach(p -> System.out.println("ODESSA_FAULT " + kind + " " + p.toShortString() + " " + server.overworld().getBlockState(p))));
                    }
                    var primatech = CityAudit.run(server.overworld(), o.getX() + CityGrid.PRIMATECH_LOT_X0, o.getY(), o.getZ() + CityGrid.PRIMATECH_LOT_Z0,
                            o.getX() + CityGrid.PRIMATECH_LOT_X1, o.getY() + 64, o.getZ() + CityGrid.PRIMATECH_LOT_Z1);
                    faults += primatech.total();
                    PreviewQuiet.report("ODESSA_RUINS_AUDIT " + (faults == 0 ? "PASS clean" : "FAIL " + faults + " faults; Primatech " + primatech));
                } catch (Throwable error) { PreviewQuiet.report("ODESSA_RUINS FAIL audit: " + error); }
            });
        }
        if (shot >= TOUR.length) {
            if (wait == 0) {
                mc.options.hideGui = false;
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    BlockPos p = city.journals().get("The Spare Key");
                    player.setGameMode(GameType.CREATIVE);
                    player.teleportTo(server.overworld(), p.getX() + 2.5, p.getY() + 1, p.getZ() + 2.5, 135, 25);
                    if (server.overworld().getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity box) player.openMenu(box);
                });
            }
            if (wait == 25) pending = "odessa-13-cache-contents.png";
            if (wait == 35) mc.player.closeContainer();
            if (wait == 40) mc.setScreen(new BookViewScreen(BookViewScreen.BookAccess.fromItem(OdessaCaches.book(OdessaCaches.JOURNALS.getFirst()))));
            if (wait == 65) pending = "odessa-14-signed-journal.png";
            if (wait == 75) {
                var book = new BookViewScreen(BookViewScreen.BookAccess.fromItem(OdessaCaches.book(OdessaCaches.JOURNALS.get(1))));
                mc.setScreen(book); book.setPage(3);
            }
            if (wait == 100) pending = "odessa-15-unfinished-journal.png";
            if (++wait > 120) { PreviewQuiet.report("ODESSA_RUINS photographed " + (TOUR.length + 3) + " views; checked all journal pages"); PreviewQuiet.quit(); }
            return;
        }
        View v = TOUR[shot];
        if (wait == 0) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.removeAllEffects();
            BlockPos o = city.origin();
            double x = o.getX() + v.x, z = o.getZ() + v.z;
            double y = v.y > 85 ? o.getY() + v.y - CityGrid.STREET_Y
                    : OdessaRuins.ground(server.overworld(), (int) Math.floor(x), (int) Math.floor(z)) + v.y - 64;
            if (v.name.equals("05-arms-journal")) {
                BlockPos p = city.journals().get("The Spare Key");
                x = p.getX() + .5; y = p.getY() + .2; z = p.getZ() + 3.5;
            }
            player.teleportTo(server.overworld(), x, y, z, v.yaw, v.pitch);
        });
        int delay = v.name.equals("01-city-around-primatech") ? 180 : 90;
        if (++wait == delay) pending = "odessa-" + v.name + ".png";
        if (wait > delay + 4) { shot++; wait = 0; }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        Minecraft mc = Minecraft.getInstance();
        try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(path.getParent());
            shot.writeToFile(path);
            System.out.println("ODESSA_RUINS_CAPTURE " + pending);
        } catch (Exception error) { PreviewQuiet.report("ODESSA_RUINS FAIL screenshot: " + error); }
        pending = null;
    }
}
