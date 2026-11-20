package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.story.prologue.LastDayFight;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.story.prologue.PrologueTimeline;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
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
import java.util.function.BiConsumer;

/**
 * Plays the Last Day in a fresh world and photographs it, beat by beat, into build/prologue-preview/screenshots/: the
 * apartment, the evening street, the light and a surge, the agents at Primatech's doors, Noah and Claire, the stun gun,
 * the pod, the frost, the title card and the bunker waking.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ProloguePreview {
    private static boolean started, overworld;
    private static int ticks, step, wakeTicks;
    private static String pending, shot;
    private static int shotAt = -1;

    private record Step(String phase, int age, BiConsumer<MinecraftServer, ServerPlayer> action, String shot, CameraType camera) {}

    /** The run's length on the default config. STEPS is built when this class loads, before any config is: never {@code get()} here. */
    private static int run() { return PrologueTimeline.run(20 * com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.prologueRunSeconds.getDefault()); }

    private static void stand(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
    }

    /** Relative to the safe house's origin. */
    private static void below(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        BlockPos o = PrologueService.safeHouse();
        stand(p, o.getX() + x, o.getY() + y, o.getZ() + z, yaw, pitch);
    }

    /** A still camera for a view the player's own path doesn't give (cleared with nulls); see {@code TempestCamera.fix}. */
    private static void camera(net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 lookAt) {
        Minecraft.getInstance().execute(() -> com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(at, lookAt));
    }

    private static net.minecraft.world.phys.Vec3 v(double x, double y, double z) { return new net.minecraft.world.phys.Vec3(x, y, z); }

    /** Relative to the Odessa Arms' north-west corner, on the first floor (apartment 1A, where a lone player wakes). */
    private static net.minecraft.world.phys.Vec3 arms(double x, double y, double z) {
        return v(com.jayptucker.heroesevolved.time.intro.city.Apartments.X0 + x,
                com.jayptucker.heroesevolved.time.intro.city.Apartments.Y + com.jayptucker.heroesevolved.time.intro.city.Apartments.STOREY + y,
                com.jayptucker.heroesevolved.time.intro.city.Apartments.Z0 + z);
    }

    /** The City Market's door on the avenue (its west face) nearest the checkout, or null. */
    private static BlockPos marketDoor(ServerPlayer p) {
        BlockPos best = null;
        double mid = com.jayptucker.heroesevolved.time.intro.city.Market.checkout().z;
        for (BlockPos door : com.jayptucker.heroesevolved.time.intro.city.Market.doors(p.serverLevel()))
            if (door.getX() == com.jayptucker.heroesevolved.time.intro.city.Market.X0
                    && (best == null || Math.abs(door.getZ() - mid) < Math.abs(best.getZ() - mid))) best = door;
        return best;
    }

    /** The fight's beats, and when the wave reaches someone standing by the market's beacon. */
    private static final int WAVE_HERE = PrologueTimeline.hits(4.5, 52.5);

    /** Digs whatever gravel is still on the first victim, standing on the road beside them. */
    private static void dig(MinecraftServer s, ServerPlayer p) {
        var victims = PrologueService.victims();
        if (victims.isEmpty()) return;
        var v = victims.get(0);
        double x = v.getX() > 4 ? v.getX() - 2 : v.getX() + 2;
        stand(p, x, 65, v.getZ(), v.getX() > 4 ? -90 : 90, 35);
        for (BlockPos pin : PrologueService.pins(0)) if (!p.serverLevel().getBlockState(pin).isAir()) p.gameMode.destroyBlock(pin);
        System.out.println("PROLOGUE_DIG " + PrologueService.victimStates());
    }

    /** Sneaks and right-clicks the ground beside the first victim (the real way), then frames the carry from in front. */
    private static void lift(MinecraftServer s, ServerPlayer p) {
        var victims = PrologueService.victims();
        if (victims.isEmpty()) return;
        var v = victims.get(0);
        int dx = v.getX() > 4 ? -1 : 1;
        p.setShiftKeyDown(true);
        boolean lifted = PrologueService.lift(p, v.position().add(dx, 0, 0));
        p.setShiftKeyDown(false);
        System.out.println("PROLOGUE_LIFT " + lifted + " " + PrologueService.victimStates());
        stand(p, p.getX() + dx * 2.5, 65, p.getZ() + 4, 180, 5);
        camera(v(p.getX() + dx * 1.2, 66.6, p.getZ() - 3.2), v(p.getX(), 65.9, p.getZ()));
    }

    /** A field of view for a shot (the zoomed ones check the far fight; players see it at their own). */
    private static void zoom(int fov) { Minecraft.getInstance().execute(() -> Minecraft.getInstance().options.fov().set(fov)); }

    private static final Step[] STEPS = {
            new Step("apartment", 60, null, "1-apartment", CameraType.FIRST_PERSON),
            new Step("apartment", 70, (s, p) -> p.setYRot(p.getYRot() + 180), null, null),
            new Step("apartment", 80, null, "1b-window", CameraType.FIRST_PERSON),
            // The whole room from its far corner: bed under the window, kitchen, table, radio.
            new Step("apartment", 84, (s, p) -> camera(arms(6.8, 2.4, 4.8), arms(1.5, 1.0, 1.5)), null, null),
            new Step("apartment", 88, null, "1c-apartment-room", null),
            new Step("apartment", 92, (s, p) -> camera(null, null), null, null),
            // Trying on Noah's glasses, seen from the front.
            new Step("apartment", 96, (s, p) -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new net.minecraft.world.item.ItemStack(com.jayptucker.heroesevolved.registry.ModItems.HORN_RIMMED_GLASSES.get())), null, CameraType.THIRD_PERSON_FRONT),
            new Step("apartment", 102, null, "1d-glasses-on-you", CameraType.THIRD_PERSON_FRONT),
            new Step("apartment", 108, (s, p) -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY), null, CameraType.FIRST_PERSON),
            // Down in the lobby: the prompt to say you're ready, then saying it.
            new Step("apartment", 112, (s, p) -> stand(p, arms(12.5, -4, 4.5).x, 65, arms(12.5, -4, 4.5).z, 0, 4), "1e-lobby-ready", CameraType.FIRST_PERSON),
            new Step("apartment", 130, (s, p) -> PrologueService.ready(p), null, null),
            // Out of the Arms: the thread of light up the street toward the market.
            new Step("evening", 30, (s, p) -> stand(p, -44.5, 65, 103.5, -90, 12), "2-odessa-arms", CameraType.FIRST_PERSON),
            new Step("evening", 40, (s, p) -> stand(p, -44.5, 65, 103.5, -90, 12), "2a-thread-behind", CameraType.THIRD_PERSON_BACK),
            // A guard close up: the pistol in its holster on his right hip.
            new Step("evening", 42, (s, p) -> camera(v(1.0, 65.6, 103.6), v(1.9, 65.3, 105.5)), null, null),
            new Step("evening", 45, null, "2c-holster-close", null),
            // The Company's men at Primatech's doors, guns on their hips.
            new Step("evening", 54, (s, p) -> camera(v(4.5, 66.3, 101.2), v(4.5, 65.4, 105.5)), null, null),
            new Step("evening", 58, null, "2b-agents-holstered", null),
            new Step("evening", 66, (s, p) -> camera(null, null), null, null),
            // The errand: the thread in at the market's avenue door, the door shut and opened by hand, the checkout inside.
            new Step("evening", 70, (s, p) -> {
                BlockPos door = marketDoor(p);
                System.out.println("PROLOGUE_MARKET_DOORS " + com.jayptucker.heroesevolved.time.intro.city.Market.doors(p.serverLevel()));
                if (door != null) stand(p, door.getX() - 3.5, 65, door.getZ() + 0.5, -90, 8);
            }, null, CameraType.THIRD_PERSON_BACK),
            new Step("evening", 76, null, "2d-thread-to-market-door", null),
            new Step("evening", 84, (s, p) -> {
                BlockPos door = marketDoor(p);
                if (door == null) return;
                stand(p, door.getX() - 2.5, 65, door.getZ() + 0.5, -90, 8);
                System.out.println("PROLOGUE_DOOR " + PrologueService.useDoor(p, door) + " open=" + p.serverLevel().getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN));
            }, null, CameraType.FIRST_PERSON),
            new Step("evening", 88, null, "2e-market-door-used", null),
            new Step("evening", 94, (s, p) -> {
                BlockPos door = marketDoor(p);
                if (door != null) System.out.println("PROLOGUE_DOOR " + PrologueService.useDoor(p, door)
                        + " open=" + p.serverLevel().getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN));
            }, null, null),
            // Inside, along the front of the aisles: the thread ends at the counter, just out of reach; then a step up to it.
            new Step("evening", 98, (s, p) -> {
                Vec3 c = com.jayptucker.heroesevolved.time.intro.city.Market.checkout();
                stand(p, c.x + 4, c.y, c.z - 1, 90, 14);
            }, null, CameraType.FIRST_PERSON),
            new Step("evening", 108, null, "2f-thread-to-checkout", null),
            new Step("evening", 114, (s, p) -> {
                Vec3 c = com.jayptucker.heroesevolved.time.intro.city.Market.checkout();
                stand(p, c.x + 2, c.y, c.z, 90, 14);
            }, null, null),
            new Step("evening", 126, null, "2g-groceries", null),
            new Step("evening", 132, (s, p) -> stand(p, -1.5, 65, 79.5, 0, 0), null, null),
            new Step("evening", 140, null, "3-conversation", CameraType.FIRST_PERSON),
            new Step("evening", 150, (s, p) -> PrologueService.skipTo(s, "light", 0), null, null),
            // The light at the far end of the avenue, Ted in it, his first waves rolling off him; then zoomed in on him.
            new Step("light", 60, (s, p) -> stand(p, 4.5, 65, 54.5, 180, -4), "4-light", CameraType.FIRST_PERSON),
            // Ted close up, kneeling in the light while it's still faint.
            new Step("light", 64, (s, p) -> camera(v(4.5 + 2.2, 66.6, -77.5 + 3.8), v(4.5, 65.0, -77.5)), null, CameraType.THIRD_PERSON_BACK),
            new Step("light", 68, null, "4-ted-close", null),
            new Step("light", 74, (s, p) -> camera(null, null), null, CameraType.FIRST_PERSON),
            new Step("light", PrologueTimeline.PULSES[1] + 8, (s, p) -> zoom(30), "4a-ted-zoom", CameraType.FIRST_PERSON),
            new Step("light", PrologueTimeline.PULSES[1] + 14, (s, p) -> zoom(70), null, null),
            // Peter streaks in overhead, and meets Sylar over the plaza (zoomed).
            new Step("light", LastDayFight.PETER_IN + 30, (s, p) -> stand(p, 4.5, 65, 54.5, 180, -38), "4b-peter-arrives", CameraType.FIRST_PERSON),
            new Step("light", LastDayFight.PLAZA.clashAt(1) - 4, (s, p) -> { stand(p, 4.5, 65, 54.5, 180, -12); zoom(30); }, null, null),
            new Step("light", LastDayFight.PLAZA.clashAt(1) + 1, null, "4c-plaza-zoom", CameraType.FIRST_PERSON),
            new Step("light", LastDayFight.PLAZA.clashAt(1) + 6, (s, p) -> zoom(70), null, null),
            // The agents at Primatech have drawn.
            new Step("light", LastDayFight.PLAZA.clashAt(1) + 10, (s, p) -> camera(v(4.5, 66.3, 101.2), v(4.5, 65.4, 105.5)), null, null),
            new Step("light", LastDayFight.PLAZA.clashAt(1) + 16, null, "4d-agents-drawn", null),
            new Step("light", LastDayFight.PLAZA.clashAt(1) + 22, (s, p) -> camera(null, null), null, null),
            // Sylar flings Peter down the avenue toward the market; the clash right overhead.
            new Step("light", LastDayFight.HURL + 26, (s, p) -> stand(p, 4.5, 65, 54.5, 180, -22), "4e-hurled", CameraType.FIRST_PERSON),
            // Peter and Sylar close up, just before they meet over the cross street.
            new Step("light", LastDayFight.CLASH - 10, (s, p) -> camera(v(8.0, 84.6, 35.0), v(4.0, 84.0, 30.9)), null, CameraType.THIRD_PERSON_BACK),
            new Step("light", LastDayFight.CLASH - 7, null, "4f-fighters-close", null),
            new Step("light", LastDayFight.CLASH - 2, (s, p) -> camera(null, null), null, CameraType.FIRST_PERSON),
            new Step("light", LastDayFight.CLASH, (s, p) -> stand(p, 4.5, 65, 52.5, 180, -38), "4f-clash-overhead", CameraType.FIRST_PERSON),
            // The first chunk, torn from the west side and thrown, from across the street.
            new Step("light", LastDayFight.TEAR_1 + 2, (s, p) -> camera(v(5.5, 70.0, 57.0), v(2.0, 78.0, 47.0)), null, null),
            new Step("light", LastDayFight.FLING_1 - 4, null, "4g-chunk-held", null),
            new Step("light", LastDayFight.FLING_1 - 1, null, "4g-chunk-flung", null),
            new Step("light", LastDayFight.RUBBLE_EAST - 1, null, "4h-chunk-hits", null),
            // The stone comes down on whoever was standing under it.
            new Step("light", LastDayFight.pinnedAt(0) + 4, (s, p) -> camera(v(5.0, 67.5, 51.5), v(9.5, 65.3, 47.5)), null, null),
            new Step("light", LastDayFight.pinnedAt(0) + 8, null, "4i-pinned", null),
            new Step("light", LastDayFight.pinnedAt(0) + 12, (s, p) -> camera(null, null), null, null),
            // Peter throws Sylar back up the avenue into the tower, and dives to Ted (zoomed).
            new Step("light", LastDayFight.TOWER - 6, (s, p) -> { stand(p, 4.5, 65, 54.5, 180, -10); zoom(30); }, null, null),
            new Step("light", LastDayFight.TOWER + 3, null, "4j-tower", CameraType.FIRST_PERSON),
            new Step("light", LastDayFight.LAND + 10, null, "4k-peter-with-ted", CameraType.FIRST_PERSON),
            new Step("light", LastDayFight.LAND + 14, (s, p) -> zoom(70), null, null),
            // The wave: coming down the avenue, then on you.
            new Step("light", PrologueTimeline.WAVE + 8, (s, p) -> stand(p, 4.5, 65, 52.5, 180, -2), "5-wave-coming", CameraType.FIRST_PERSON),
            // Looking the other way as it comes: the rumble builds, and the head is turned back to it, held there.
            new Step("light", PrologueTimeline.WAVE + 14, (s, p) -> stand(p, 4.5, 65, 52.5, 0, 4), "5-turned-away", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE - 26, null, "5a-head-turning", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE - 13, null, "5a-wave-close", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE - 4, null, "5a-wave-here", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE + 20, null, "5b-out-cold", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE + PrologueTimeline.BLACKOUT * 82 / 100 - 4, null, "5b-trying-to-wake", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE + 100, null, "5c-blinking", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 12, null, "5d-up", CameraType.FIRST_PERSON),
            // Back on your feet: the thread to whoever is trapped; the burst windows round the market.
            // The city after it: facades torn open, rubble across the street, dust in the air, smoke rising.
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 20, (s, p) -> stand(p, 4.5, 65, 60.5, 180, -18), "5e-crumbling", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 26, (s, p) -> { stand(p, -1.5, 65, 66.5, 160, 8);
                System.out.println("PROLOGUE_DAMAGE " + PrologueService.damageNear(p.position(), 32)); }, "5f-debris-street", CameraType.FIRST_PERSON),
            // Straight at the nearest hole the wave tore in a building, to see the damage close.
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 30, (s, p) -> {
                Vec3[] look = PrologueService.nearestHole(p.position());
                System.out.println("PROLOGUE_HOLE " + (look == null ? "none" : look[1]));
                if (look != null) camera(look[0], look[1]);
            }, null, CameraType.THIRD_PERSON_BACK),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 31, null, "5g-hole-close", null),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 35, (s, p) -> camera(null, null), null, CameraType.FIRST_PERSON),
            // The thread to whoever is trapped, and the gravel on them.
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 36, (s, p) -> stand(p, 4.5, 65, 56.5, 200, 12), "6-dig-thread", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 44, (s, p) -> stand(p, 4.5, 65, 56.5, 200, 12), "6a-dig-thread-behind", CameraType.THIRD_PERSON_BACK),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 50, (s, p) -> camera(v(5.5, 67.4, 52.5), v(9.5, 65.2, 48.5)), null, null),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 54, null, "6b-buried-in-gravel", null),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 60, (s, p) -> camera(null, null), null, null),
            // Dug out in two goes (what's heaped above slides down into the hole), lifted, carried.
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 64, ProloguePreview::dig, null, null),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 76, ProloguePreview::dig, null, null),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 88, ProloguePreview::dig, null, null),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 92, null, "6c-dug-out", CameraType.FIRST_PERSON),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 100, ProloguePreview::lift, null, CameraType.THIRD_PERSON_BACK),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 106, null, "6c-carrying", null),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 112, (s, p) -> camera(null, null), null, null),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 120, (s, p) -> stand(p, 4.5, 65, 90.5, 0, 6), null, null),
            new Step("light", WAVE_HERE + PrologueTimeline.DAZE + 132, null, "6d-to-primatech", CameraType.THIRD_PERSON_BACK),
            new Step("light", PrologueTimeline.surgeTick(3, run()) - 1, (s, p) -> stand(p, 4.5, 65, 97.5, 0, 4), "6e-primatech-doors", CameraType.FIRST_PERSON),
            // The head of the stair in the lobby, looking down the top flight.
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 34, (s, p) -> {
                for (int k = 0; k < 4; k++) PrologueService.shelterSomeone(p.serverLevel(), new Vec3(8.5, 64, 109.5 + k % 3));
            }, null, null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 40, (s, p) -> stand(p, 6.5, 65, 110.5, -90, 28), "6f-stairwell", CameraType.FIRST_PERSON),
            // The lower flight from its foot, looking up to the turn, and the open vault from the foot of the stair, with the player still upstairs.
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 50, (s, p) -> camera(v(7.3, 49.2, 106.5), v(16.5, 56.5, 106.5)), null, null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 54, null, "6g-lower-flight", null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 60, (s, p) -> camera(v(6.6, 49.4, 105.4), v(4.5, 48.0, 112.0)), null, null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 64, null, "6h-landing", null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 70, (s, p) -> camera(null, null), null, null),
            // At the foot of the stair, facing the open vault: being down here ends the light phase at once.
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 80, (s, p) -> below(p, 0.5, 1, -5.5, 0, 6), null, null),
            new Step("safe_house", 3, null, "6i-vault-open", CameraType.FIRST_PERSON),
            // Through it, looking back as it shuts.
            new Step("safe_house", 10, (s, p) -> below(p, 0.5, 1, 3.5, 180, 4), null, null),
            // People who came down before them, in the hall already.
            new Step("safe_house", 12, (s, p) -> { BlockPos o = PrologueService.safeHouse();
                for (int k = 0; k < 10; k++) PrologueService.shelterSomeone(p.serverLevel(), new Vec3(o.getX() + 0.5 + (k % 3) - 1, o.getY() + 1, o.getZ() + 37.5 + k / 3)); }, null, null),
            new Step("safe_house", 18, null, "6j-vault-shut", CameraType.FIRST_PERSON),
            new Step("safe_house", 30, (s, p) -> below(p, 0.5, 1, 20.5, 0, 6), "7-stasis-hall", CameraType.FIRST_PERSON),
            new Step("noah", 12, null, "8-claire-pleads", CameraType.FIRST_PERSON),
            // Noah close up, glasses and all, from beside Claire.
            new Step("noah", 26, (s, p) -> { BlockPos o = PrologueService.safeHouse();
                camera(v(o.getX() + 6.2, o.getY() + 2.7, o.getZ() + 58.0), v(o.getX() + 4.5, o.getY() + 2.55, o.getZ() + 58.5)); }, null, null),
            new Step("noah", 32, null, "8b-noah-close", null),
            new Step("noah", 38, (s, p) -> camera(null, null), null, null),
            // The far side of the shut vault, a few seconds on: the people shut out, pounding on it.
            new Step("noah", 140, (s, p) -> { BlockPos o = PrologueService.safeHouse();
                camera(v(o.getX() + 0.5, o.getY() + 2.8, o.getZ() - 6.5), v(o.getX() + 0.5, o.getY() + 1.4, o.getZ() + 0.5)); }, null, null),
            new Step("noah", 144, null, "6k-shut-out", null),
            new Step("noah", 150, (s, p) -> camera(null, null), null, null),
            new Step("noah", 300, (s, p) -> { BlockPos o = PrologueService.safeHouse();
                camera(v(o.getX() + 0.5, o.getY() + 5.5, o.getZ() + 35.5), v(o.getX() + 0.5, o.getY() + 1.2, o.getZ() + 50.5)); }, null, null),
            new Step("noah", 304, null, "8c-hall-sheltered", null),
            new Step("noah", 310, (s, p) -> camera(null, null), null, null),
            new Step("noah", PrologueService.NOAH_PLEASE + 5, null, "9-dad-please", CameraType.FIRST_PERSON),
            new Step("noah", PrologueService.NOAH_SEAL + 10, null, "10-sealed", CameraType.FIRST_PERSON),
            new Step("noah", PrologueService.NOAH_SEAL + 14, (s, p) -> { BlockPos o = PrologueService.safeHouse();
                camera(v(o.getX() + 5.2, o.getY() + 2.2, o.getZ() + 62.5), v(o.getX() + 8.5, o.getY() + 1.9, o.getZ() + 62.5)); }, null, null),
            new Step("noah", PrologueService.NOAH_SEAL + 18, null, "10b-claire-in-pod", null),
            new Step("noah", PrologueService.NOAH_SEAL + 26, (s, p) -> camera(null, null), null, null),
            new Step("noah", PrologueService.NOAH_PROMISE + 10, null, "11-hand-on-glass", CameraType.FIRST_PERSON),
            new Step("noah", PrologueService.NOAH_OWN_GOOD + 10, null, "12-for-your-own-good", CameraType.FIRST_PERSON),
            // He walks up with the stun gun; then from the side, raised at us.
            new Step("noah", PrologueService.NOAH_APPROACH + 60, null, "13-walks-up", CameraType.FIRST_PERSON),
            new Step("noah", PrologueService.NOAH_ORDER + 2, (s, p) -> camera(p.position().add(2.6, 1.7, -1.4), p.position().add(0, 1.2, 1.6)), null, CameraType.THIRD_PERSON_BACK),
            new Step("noah", PrologueService.NOAH_ORDER + 8, null, "13b-stun-gun-raised", null),
            new Step("noah", PrologueService.NOAH_ORDER + 18, (s, p) -> camera(null, null), null, CameraType.FIRST_PERSON),
            new Step("pods", 45, null, "15-pod", CameraType.FIRST_PERSON),
            new Step("pods", 50, (s, p) -> { BlockPos o = PrologueService.safeHouse();
                camera(v(o.getX() + 0.5, o.getY() + 3.0, o.getZ() + 44.5), v(o.getX() - 8.0, o.getY() + 1.5, o.getZ() + 48.5)); }, null, null),
            new Step("pods", 54, null, "15b-pods-filled", null),
            new Step("pods", 60, (s, p) -> camera(null, null), null, null),
            new Step("pods", 160, null, "16-shaking", CameraType.FIRST_PERSON),
            // Just after the blow, down the aisle: lights stuttering, stone coming down.
            new Step("pods", 182, (s, p) -> { BlockPos o = PrologueService.safeHouse();
                camera(v(o.getX() + 0.5, o.getY() + 5.5, o.getZ() + 19.5), v(o.getX() + 0.5, o.getY() + 1.5, o.getZ() + 34.0)); }, null, null),
            new Step("pods", 186, null, "16b-blast-hall", null),
            new Step("pods", 236, null, "16c-after-blast", null),
            new Step("pods", 240, (s, p) -> camera(null, null), null, null),
            new Step("pods", 300, null, "17-frost", CameraType.FIRST_PERSON),
            new Step("pods", 410, null, "18-years-later", CameraType.FIRST_PERSON),
    };

    /** The day's phases in order; "none" (before and after) sorts first. */
    private static final java.util.List<String> ORDER = java.util.List.of("none", "apartment", "evening", "light", "safe_house", "noah", "pods");

    private static void onServer(Minecraft mc, BiConsumer<MinecraftServer, ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) action.accept(server, p);
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.prologue")) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(12);
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Prologue-" + System.currentTimeMillis(),
                    new LevelSettings("Prologue preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(5L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (overworld) {
            // Handed back: the bunker under spawn, 103 years later.
            if (++wakeTicks == 70) pending = "prologue-19-bunker-wake.png";
            if (wakeTicks == 150) pending = "prologue-20-bunker-awake.png";
            // Pod 14 in the present: open, its glass clawed through, Claire's note on the lectern beside it.
            if (wakeTicks == 155) {
                BlockPos o = com.jayptucker.heroesevolved.story.bunker.BunkerData.get(mc.getSingleplayerServer()).origin;
                camera(v(o.getX() + 2.2, o.getY() + 2.5, o.getZ() + 30.6), v(o.getX() + 5.0, o.getY() + 1.6, o.getZ() + 32.8));
            }
            if (wakeTicks == 165) pending = "prologue-21-pod-14.png";
            if (wakeTicks == 170) {
                BlockPos o = com.jayptucker.heroesevolved.story.bunker.BunkerData.get(mc.getSingleplayerServer()).origin;
                camera(v(o.getX() + 0.5, o.getY() + 3.0, o.getZ() + 40.5), v(o.getX() - 8.0, o.getY() + 1.6, o.getZ() + 46.5));
            }
            if (wakeTicks == 180) pending = "prologue-22-pods-deceased.png";
            if (wakeTicks == 184) {
                BlockPos o = com.jayptucker.heroesevolved.story.bunker.BunkerData.get(mc.getSingleplayerServer()).origin;
                camera(v(o.getX() - 5.0, o.getY() + 2.4, o.getZ() + 42.5), v(o.getX() - 8.0, o.getY() + 2.3, o.getZ() + 42.5));
            }
            if (wakeTicks == 194) pending = "prologue-23-deceased-close.png";
            if (wakeTicks == 198) {
                camera(null, null);
                onServer(mc, (s, p) -> {
                    BlockPos o = com.jayptucker.heroesevolved.story.bunker.BunkerData.get(s).origin;
                    p.teleportTo(s.overworld(), o.getX() + 6.0, o.getY() + 1, o.getZ() + 62.5, -90, 10);
                });
            }
            if (wakeTicks == 215) onServer(mc, (s, p) -> System.out.println("PROLOGUE_CLAIRE_NOTE "
                    + p.getPersistentData().getBoolean(com.jayptucker.heroesevolved.story.bunker.CryoChamberService.CLAIRE_NOTE)));
            if (wakeTicks == 218) pending = "prologue-24-claire-note.png";
            if (wakeTicks > 230) PreviewQuiet.quit();
            return;
        }
        String phase = PrologueService.phase();
        int age = PrologueService.age();
        if (shot != null && age >= shotAt) { pending = shot; shot = null; }
        if (step >= STEPS.length) {
            if (phase.equals("none") && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                overworld = true;
                System.out.println("PROLOGUE_HOME");
            }
            return;
        }
        Step s = STEPS[step];
        // The day moved past this step's phase (a shortcut ended it early): skip the step rather than wait forever.
        if (ORDER.indexOf(phase) > ORDER.indexOf(s.phase)) {
            step++;
            System.out.println("PROLOGUE_SKIPPED " + step + " " + s.phase + " (now " + phase + ")");
            return;
        }
        if (!phase.equals(s.phase) || age < s.age) return;
        step++;
        if (s.camera != null) mc.options.setCameraType(s.camera);
        if (s.action != null) onServer(mc, s.action);
        if (s.shot != null) { shotAt = age + 3; shot = "prologue-" + s.shot + ".png"; }
        System.out.println("PROLOGUE_STEP " + step + " " + phase + " at " + age);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        Minecraft mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("PROLOGUE_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
