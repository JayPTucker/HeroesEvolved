package com.jayptucker.heroesevolved.trailer;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.company.CompanyAttention;
import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.company.Convoy;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.endgame.SealGeometry;
import com.jayptucker.heroesevolved.endgame.TrailerEnd;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.AwakeningService;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.meteor.Meteors;
import com.jayptucker.heroesevolved.meteor.Starfall;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.BunkerPower;
import com.jayptucker.heroesevolved.story.bunker.BunkerService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.bunker.OdessaRuins;
import com.jayptucker.heroesevolved.story.prologue.LastDayFight;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.story.sylar.CollectionArena;
import com.jayptucker.heroesevolved.story.sylar.LevelFive;
import com.jayptucker.heroesevolved.story.sylar.SylarGeometry;
import com.jayptucker.heroesevolved.story.sylar.SylarStory;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import com.jayptucker.heroesevolved.village.Villagers;
import com.jayptucker.heroesevolved.village.overload.Overloads;
import com.jayptucker.heroesevolved.village.overload.Storm;
import com.jayptucker.heroesevolved.wither.WitherArena;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Supplier;

/**
 * The story's shots, each in its own place: the Last Day's city (Act I), the bunker and Odessa's ruins under spawn, a
 * real village found near the hilltop, Level 5, the Collection, the hilltop for the Wither's breach and Still World,
 * and the End. Each is staged as the preview of that place stages it (named on each class), at normal speed, and left
 * cleanly after its cut. Their cameras are fixed moves ({@link #dolly}) with no ground lift: inside the bunker or
 * Level 5 the surface heightmap would throw the camera through the ceiling.
 */
final class StoryShots {
    private StoryShots() {}

    static List<Shot> all() {
        return List.of(new LastDayStreet(), new TheLight(), new TheClash(), new Pods(), new Odessa(), new Awakening(),
                new Convoy(), new Wildfire(), new Starfall(), new LevelFiveShot(), new Collection(), new WitherBreach(),
                new StillWorld(), new Unmaking(), new Inheritance(), new Stinger());
    }

    /** A move between two camera spots, each looking at its own point. */
    static FilmCamera.Move dolly(int ticks, Vec3 from, Vec3 fromLook, Vec3 to, Vec3 toLook, double fov0, double fov1) {
        return FilmCamera.Move.of(ticks, FilmCamera.Ease.BOTH,
                new FilmCamera.Key(0, from, fromLook, fov0, 0), new FilmCamera.Key(1, to, toLook, fov1, 0));
    }

    /** A story shot: its own place, a fixed camera (no follow, so no ground lift), the overworld clock untouched. */
    abstract static class Place extends Shot {
        /** Camera spots and the like, worked out on the server while staging and read by the client at the roll. */
        volatile Vec3 a = Vec3.ZERO, b = Vec3.ZERO, c = Vec3.ZERO, d = Vec3.ZERO;

        Place(String name, int length, long time) { super(name, length, time); }

        @Override Supplier<Vec3> follow(Minecraft mc) { return null; }

        @Override int settle() { return 60; }

        @Override void cast(ServerPlayer p) {}

        /** The screen tints a story place lays over the view (the Last Day's black holds) never reach the film. */
        @Override void watch(Minecraft mc, int tick) { com.jayptucker.heroesevolved.network.ClientScreenTintState.clear(); }
    }

    static void move(ServerPlayer p, ServerLevel level, Vec3 at, float yaw, float pitch) {
        p.teleportTo(level, at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    // ================================================================== Act I: the Last Day (ProloguePreview)

    /** Into the Last Day's city if not already there; the run is driven from its own phases. */
    abstract static class LastDay extends Place {
        LastDay(String name, int length) { super(name, length, 12000); }

        static ServerLevel city(ServerPlayer p) { return p.getServer().getLevel(TimeIntroService.DIMENSION); }

        @Override void stage(ServerPlayer p) {
            if (!PrologueService.isMember(p)) PrologueService.join(p);
        }

        boolean inCity(ServerPlayer p) { return PrologueService.isMember(p) && p.level().dimension() == TimeIntroService.DIMENSION; }
    }

    /** 2. An ordinary evening on the avenue, the lights coming on: a low dolly up the street behind a walker. */
    static final class LastDayStreet extends LastDay {
        LastDayStreet() { super("02-last-day-street", 110); }

        @Override boolean ready(ServerPlayer p) {
            if (!inCity(p)) return false;
            if (PrologueService.phase().equals("apartment")) PrologueService.skipTo(p.getServer(), "evening", 0);
            return PrologueService.phase().equals("evening") && PrologueService.age() >= 40;
        }

        @Override void cast(ServerPlayer p) { move(p, city(p), new Vec3(4.5, 65, 56.5), 180, 0); }

        @Override FilmCamera.Move camera() {
            return dolly(length, new Vec3(5.6, 66.1, 66), new Vec3(4.5, 68, -10), new Vec3(4.9, 66.5, 59.5), new Vec3(4.5, 68.5, -10), 50, 44);
        }
    }

    /** 4. The light at the far end of the avenue, Ted in it, its first waves rolling off him: a long lens pushing in. */
    static final class TheLight extends LastDay {
        TheLight() { super("04-the-light", 120); }

        @Override boolean ready(ServerPlayer p) {
            if (!inCity(p)) return false;
            if (!PrologueService.phase().equals("light")) PrologueService.skipTo(p.getServer(), "light", 0);
            return PrologueService.phase().equals("light") && PrologueService.age() >= 95;
        }

        @Override void cast(ServerPlayer p) { move(p, city(p), new Vec3(4.5, 65, 52.5), 180, -4); }

        @Override FilmCamera.Move camera() {
            Vec3 ted = new Vec3(4.5, 66.5, -77.5);
            return dolly(length, new Vec3(6.2, 66.3, 58.5), ted, new Vec3(5.2, 66.2, 55.5), ted, 32, 22);
        }
    }

    /** Peter and Sylar meeting over the cross street: an arc round the clash, slow motion as they meet. */
    static final class TheClash extends LastDay {
        private volatile int clashAt = -1;

        TheClash() { super("04b-the-clash", 96); }

        @Override boolean ready(ServerPlayer p) {
            if (!inCity(p)) return false;
            if (!PrologueService.phase().equals("light")) PrologueService.skipTo(p.getServer(), "light", 0);
            return PrologueService.phase().equals("light") && PrologueService.age() >= LastDayFight.CLASH - 34;
        }

        @Override void cast(ServerPlayer p) { clashAt = -1; move(p, city(p), new Vec3(4.5, 65, 52.5), 180, -30); }

        @Override FilmCamera.Move camera() {
            Vec3 clash = new Vec3(4.0, 84.0, 30.9);
            return dolly(length, new Vec3(10.5, 85.8, 38.5), clash, new Vec3(7.6, 84.4, 34.4), clash, 58, 50);
        }

        @Override double speed(double tick) { return clashAt < 0 ? 1 : slow(tick, clashAt - 2, clashAt + 20, .35); }

        @Override void act(ServerPlayer p, int tick) {
            if (clashAt < 0 && PrologueService.age() >= LastDayFight.CLASH) {
                clashAt = tick;
                Stage.mark("clash");
            }
        }

        /** The last of the Last Day: wake in the bunker, 103 years later, out of the pod. */
        @Override void leave(ServerPlayer p) {
            PrologueService.skip(p);
            CryoChamberService.release(p);
        }
    }

    // ================================================================== Act I: under spawn (BunkerPreview, OdessaRuinsPreview)

    /**
     * 6. The pod hall: a slow dolly between the pods. With the power on (on emergency power the hall filmed almost
     * black), and stopping short of the hall's far end (a closed door filled the last frames). The power comes back only
     * when the waking player leaves their pod, which a trailer's player never does, so it is started here and given a
     * few seconds for its wave of lights to reach the hall.
     */
    static final class Pods extends Place {
        Pods() { super("06-pods", 110, 6000); }

        @Override int settle() { return 160; }

        @Override void stage(ServerPlayer p) {
            BunkerData data = BunkerService.ensureBuilt(p.getServer());
            BlockPos o = data.origin;
            BunkerPower.start(p.getServer().overworld(), data);
            move(p, p.getServer().overworld(), Vec3.atBottomCenterOf(o.offset(0, 1, 31)), 0, 0);
            a = Vec3.atBottomCenterOf(o).add(0, 2.3, 35.5);
            b = Vec3.atBottomCenterOf(o).add(0, 2.0, 46.5);
            c = Vec3.atBottomCenterOf(o).add(0, 1.4, 62);
        }

        @Override FilmCamera.Move camera() { return dolly(length, a, c, b, c, 62, 54); }
    }

    /** 7. Odessa's ruins at dawn: from the street outside the bunker, a crane up and back over the whole ruined city. */
    static final class Odessa extends Place {
        private volatile boolean placed;

        Odessa() { super("07-odessa", 150, 23400); }

        @Override void stage(ServerPlayer p) {
            placed = false;
            var data = BunkerService.ensureBuilt(p.getServer());
            OdessaRuins.get(p.getServer().overworld());
            move(p, p.getServer().overworld(), Vec3.atBottomCenterOf(data.origin).add(0, 80, 0), 180, 30);
        }

        @Override int settle() { return 100; }

        @Override boolean ready(ServerPlayer p) {
            ServerLevel level = p.getServer().overworld();
            OdessaRuins city = OdessaRuins.get(level);
            if (!city.complete()) return false;
            if (!placed) {
                BlockPos o = city.origin();
                int street = OdessaRuins.ground(level, o.getX() + 4, o.getZ() + 99);
                double high = o.getY() - CityGrid.STREET_Y;
                move(p, level, new Vec3(o.getX() + 4.5, street, o.getZ() + 99.5), 180, 0);
                // Starting over the street's rubble (from the street itself a mound filled the frame), rising clear of the
                // buildings round the bunker (a straight line skimmed one) to the whole city.
                a = new Vec3(o.getX() + 10, street + 14, o.getZ() + 116);
                b = new Vec3(o.getX() + 4.5, street + 8, o.getZ() + 40);
                c = new Vec3(o.getX() + 62, high + 128, o.getZ() + 150);
                d = new Vec3(o.getX() + 4, high + 70, o.getZ() + 90);
                placed = true;
            }
            return true;
        }

        @Override FilmCamera.Move camera() {
            Vec3 over = a.lerp(c, .45).add(0, 24, 0);
            return FilmCamera.Move.of(length, FilmCamera.Ease.BOTH, new FilmCamera.Key(0, a, b, 54, 0),
                    new FilmCamera.Key(.45, over, b.lerp(d, .5), 58, 0), new FilmCamera.Key(1, c, d, 62, 0));
        }
    }

    // ================================================================== Shot 9: an awakening (AwakeningPreview)

    /**
     * 9. A creeper hissing beside you and a dormant power tearing out to stop it: Cryokinesis freezes it and runs its fuse
     * back. Close on the face, then pulling back as it wakes; slow motion from the moment it does.
     */
    static final class Awakening extends Shot {
        private volatile int awokeAt = -1;

        Awakening() { super("09-awakening", 150, Shots.SUNSET); }

        @Override void cast(ServerPlayer p) {
            awokeAt = -1;
            PowerIntroService.forget(p);
            AwakeningService.forget(p);
            PlayerAbilityService.removeAssignedAbility(p);
            p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty());
            PlayerAbilityService.assignDormantAbility(p, ModAbilities.CRYOKINESIS_ID);
            // The Odessa vision isn't this shot: marked as seen, as the awakening preview does.
            p.setData(ModDataAttachments.TIME_INTRO_SEEN, true);
            // A blast counts as a danger only if, worked out the vanilla way, it would take them: no Resistance, a
            // hurt fighter, Normal difficulty as the awakening preview has, and the creeper close.
            p.removeAllEffects();
            p.setHealth(8);
            p.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            Creeper creeper = (Creeper) Stage.puppet(p, EntityType.CREEPER, .4, 2.4);
            creeper.goalSelector.removeAllGoals(g -> true);
            creeper.setTarget(p);
            creeper.ignite();
            Stage.aim(p, creeper.getEyePosition());
        }

        @Override FilmCamera.Move camera() {
            return dolly(length, Stage.at(1.3, 1.75, 2.1), Stage.at(0, 1.6, 0), Stage.at(5.5, 3.2, 7.5), Stage.at(0, 1.1, 2.2), 40, 56);
        }

        @Override double followRate() { return .1; }

        @Override double speed(double tick) { return awokeAt < 0 ? 1 : slow(tick, awokeAt - 1, awokeAt + 30, .4); }

        @Override void act(ServerPlayer p, int tick) {
            if (awokeAt < 0 && !AwakeningService.hasDormantPower(p)) {
                awokeAt = tick;
                Stage.mark("awaken");
            }
        }

        @Override void leave(ServerPlayer p) {
            PowerIntroService.forget(p);
            AwakeningService.forget(p);
            p.getServer().setDifficulty(net.minecraft.world.Difficulty.EASY, true);
        }
    }

    // ================================================================== Act III: a village (CompanyPreview, OverloadPreview, MeteorPreview)

    /**
     * The real village nearest the hilltop and its bell, found once per seed (the generator's locate, then a search for
     * the bell once its chunks are in) and kept in {@code build/trailer-preview/village-<seed>.txt}.
     */
    static final class Village {
        static volatile BlockPos bell;
        private static BlockPos near;
        /** Set once it is clear there is no village (or no bell in it) to film. */
        static volatile boolean missing;

        /** Off to the village, if there is one; false if there is none. */
        static boolean go(ServerPlayer p) {
            ServerLevel level = p.getServer().overworld();
            var cache = p.getServer().getServerDirectory().resolve("village-" + TrailerPreview.SEED + ".txt");
            if (bell == null && java.nio.file.Files.exists(cache)) {
                try {
                    String[] xyz = java.nio.file.Files.readString(cache).trim().split(" ");
                    bell = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                } catch (java.io.IOException | RuntimeException e) {
                    System.out.println("TRAILER_NOTE could not read " + cache + ": " + e);
                }
            }
            if (bell == null && near == null) {
                near = level.findNearestMapStructure(StructureTags.VILLAGE, Stage.ground, 100, false);
                System.out.println("TRAILER_VILLAGE found near " + near);
                if (near == null) { PreviewQuiet.report("TRAILER FAIL no village within 1600 blocks of the hilltop"); missing = true; return false; }
            }
            BlockPos at = bell != null ? bell : near;
            for (int cx = (at.getX() >> 4) - 3; cx <= (at.getX() >> 4) + 3; cx++)
                for (int cz = (at.getZ() >> 4) - 3; cz <= (at.getZ() >> 4) + 3; cz++) level.setChunkForced(cx, cz, true);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            move(p, level, Vec3.atBottomCenterOf(new BlockPos(at.getX(), Math.max(y, at.getY()) + 1, at.getZ())).add(0, 0, -28), 0, 0);
            p.setNoGravity(true);
            return true;
        }

        /** Whether the bell is known (searching round the village once its chunks are in). */
        static boolean found(ServerPlayer p) {
            if (bell != null) return true;
            if (near == null) return false;
            ServerLevel level = p.getServer().overworld();
            if (!level.hasChunkAt(near.offset(-40, 0, -40)) || !level.hasChunkAt(near.offset(40, 0, 40))) return false;
            BlockPos best = null;
            var cursor = new BlockPos.MutableBlockPos();
            for (int dx = -48; dx <= 48; dx++)
                for (int dz = -48; dz <= 48; dz++) {
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX() + dx, near.getZ() + dz);
                    for (int y = top - 6; y <= top + 2; y++) {
                        cursor.set(near.getX() + dx, y, near.getZ() + dz);
                        if (level.getBlockState(cursor).is(Blocks.BELL) && (best == null || cursor.distSqr(near) < best.distSqr(near)))
                            best = cursor.immutable();
                    }
                }
            if (best == null) { PreviewQuiet.report("TRAILER FAIL no bell within 48 blocks of the village at " + near); missing = true; return false; }
            bell = best;
            System.out.println("TRAILER_VILLAGE bell at " + bell);
            try {
                java.nio.file.Files.writeString(p.getServer().getServerDirectory().resolve("village-" + TrailerPreview.SEED + ".txt"),
                        bell.getX() + " " + bell.getY() + " " + bell.getZ());
            } catch (java.io.IOException e) { System.out.println("TRAILER_NOTE could not keep the village: " + e); }
            return true;
        }

        static Vec3 bellAt(double dx, double dy, double dz) { return Vec3.atBottomCenterOf(bell).add(dx, dy, dz); }

        static Vec3 ground(ServerLevel level, double x, double z) {
            return new Vec3(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)), z);
        }
    }

    abstract static class InVillage extends Place {
        InVillage(String name, int length, long time) { super(name, length, time); }

        @Override void stage(ServerPlayer p) { if (!Village.go(p)) impossible = true; }

        @Override boolean ready(ServerPlayer p) {
            if (Village.missing) { impossible = true; return false; }
            return Village.found(p) && placed(p);
        }

        /** Once the bell is known: the scene's own placing and camera spots. */
        abstract boolean placed(ServerPlayer p);
    }

    /**
     * 31. A Company convoy marching out of a village at dusk, side-on and keeping pace. It sets out while the place is
     * still staging and marches ten seconds before the roll: filmed the moment it started, it was one agent among houses.
     */
    static final class Convoy extends InVillage {
        private volatile com.jayptucker.heroesevolved.company.Convoy convoy;
        private boolean set;
        private int marching;

        Convoy() { super("31-convoy", 130, 12800); }

        @Override void stage(ServerPlayer p) { set = false; convoy = null; marching = 0; super.stage(p); }

        /** Asked every ten ticks while staging: the convoy has marched for 200 ticks after 20 asks. */
        @Override boolean placed(ServerPlayer p) {
            if (!set) {
                p.setNoGravity(false);
                move(p, p.serverLevel(), Village.ground(p.serverLevel(), Village.bell.getX() + .5, Village.bell.getZ() - 44.5), 0, 10);
                setOut(p);
                set = true;
            }
            return convoy == null || ++marching >= 20;
        }

        private void setOut(ServerPlayer p) {
            ServerLevel level = p.serverLevel();
            CompanyOps.callOffAll();
            Villager guardian = level.getEntitiesOfClass(Villager.class, new AABB(Village.bell).inflate(24)).stream().findFirst().orElse(null);
            if (guardian == null) {
                guardian = Villagers.actor(level);
                Vec3 at = Village.ground(level, Village.bell.getX() + 2.5, Village.bell.getZ() + 2.5);
                guardian.moveTo(at.x, at.y, at.z, 0, 0);
                level.addFreshEntity(guardian);
            }
            Renegades.guard(guardian, ModAbilities.CRYOKINESIS_ID, Village.bell);
            CompanyAttention.set(p, 58);
            convoy = com.jayptucker.heroesevolved.company.Convoy.start(level, p, Village.bell, new Vec3(1, 0, .25));
            PreviewQuiet.check("TRAILER_STORY", convoy != null, name + " sets out");
        }

        /** Side-on from the convoy's left, as the company preview frames it marching. */
        @Override void cast(ServerPlayer p) {
            Vec3 at = convoy == null ? Village.bellAt(0, 0, 0) : convoy.center();
            a = at.add(10, 2.8, -8);
            b = at.add(0, 1.0, .5);
        }

        @Override FilmCamera.Move camera() { return dolly(length, a, b, a.add(-2, -.3, 3), b.add(1.5, 0, .4), 50, 44); }

        @Override Supplier<Vec3> follow(Minecraft mc) {
            return () -> convoy == null ? null : convoy.center();
        }

        @Override double followRate() { return .08; }

        @Override void leave(ServerPlayer p) { CompanyOps.callOffAll(); CompanyAttention.set(p, 0); p.setNoGravity(false); }
    }

    /** 32. A village overload: a wildfire with a frightened villager at its centre, the camera craning down through it. */
    static final class Wildfire extends InVillage {
        private Villager eye;
        private boolean set;

        Wildfire() { super("32-wildfire", 120, 13200); }

        @Override void stage(ServerPlayer p) { set = false; super.stage(p); }

        @Override boolean placed(ServerPlayer p) {
            if (!set) {
                p.setNoGravity(false);
                move(p, p.serverLevel(), Village.ground(p.serverLevel(), Village.bell.getX() - 9.5, Village.bell.getZ() - 11.5), -40, 0);
                set = true;
            }
            return true;
        }

        @Override void cast(ServerPlayer p) {
            ServerLevel level = p.serverLevel();
            eye = Villagers.actor(level);
            Vec3 at = Village.ground(level, Village.bell.getX() + .5, Village.bell.getZ() + 3.5);
            eye.moveTo(at.x, at.y, at.z, 180, 0);
            level.addFreshEntity(eye);
            var storm = Overloads.start(eye, Storm.WILDFIRE, 6000);
            PreviewQuiet.check("TRAILER_STORY", storm != null, name + " overloads");
            if (storm != null) Overloads.progress(storm, .55F);
            a = at.add(16, 18, 15);
            b = at.add(0, 1.4, 0);
            c = at.add(7, 4.5, 8.5);
        }

        @Override FilmCamera.Move camera() { return dolly(length, a, b, c, b.add(0, .4, 0), 52, 44); }

        @Override void leave(ServerPlayer p) {
            if (eye != null) { Overloads.cancel(eye); eye.discard(); }
        }
    }

    /** 33. Starfall over the village: the sky full of falling stars, one coming down outside it. */
    static final class Starfall extends InVillage {
        private boolean set;
        private volatile Object meteor;

        Starfall() { super("33-starfall", 110, 18000); }

        @Override void stage(ServerPlayer p) { set = false; super.stage(p); }

        @Override boolean placed(ServerPlayer p) {
            if (!set) {
                p.setNoGravity(false);
                move(p, p.serverLevel(), Village.ground(p.serverLevel(), Village.bell.getX() + 2.5, Village.bell.getZ() - 3.5), 0, -30);
                set = true;
            }
            return true;
        }

        @Override void cast(ServerPlayer p) {
            com.jayptucker.heroesevolved.meteor.Starfall.force(p.getServer(), true);
            Vec3 land = Village.ground(p.serverLevel(), Village.bell.getX() + 14.5, Village.bell.getZ() + 30.5);
            a = Village.bellAt(-3, 2.2, -8);
            b = land.add(-4, 26, 8);
            c = Village.bellAt(-2, 3.0, -9);
            d = land;
        }

        @Override FilmCamera.Move camera() { return dolly(length, a, b, c, d, 64, 54); }

        @Override void act(ServerPlayer p, int tick) {
            if (tick == 6) {
                var m = Meteors.drop(p.serverLevel(), d, false, 600, Math.PI * .35, null);
                Meteors.fallen(m, .55F);
                meteor = m;
            }
            if (tick == 60 && meteor instanceof com.jayptucker.heroesevolved.meteor.Meteor m) Meteors.fallen(m, .93F);
            if (tick == 72 && meteor instanceof com.jayptucker.heroesevolved.meteor.Meteor m) {
                Meteors.hurry(m, 2);
                Stage.mark("impact");
            }
        }

        @Override void leave(ServerPlayer p) {
            Meteors.cancelAll();
            com.jayptucker.heroesevolved.meteor.Starfall.force(p.getServer(), false);
        }
    }

    // ================================================================== Act III: Level 5 and the Collection (LevelFivePreview, SylarCollectionPreview)

    /** 34. Level 5: the prisoner behind glass, the camera pushing in over the fighter's shoulder. */
    static final class LevelFiveShot extends Place {
        LevelFiveShot() { super("34-level-five", 120, 6000); }

        @Override void stage(ServerPlayer p) {
            TrailerPreview.grant(p, ModAbilities.QUAKE_ID, null, 10);
            SylarStory.wake(p);
            LevelFive.descend(p);
        }

        @Override boolean ready(ServerPlayer p) { return p.level().dimension() == LevelFive.DIMENSION; }

        @Override void cast(ServerPlayer p) {
            Vec3 g = LevelFive.glassMiddle(), cell = LevelFive.cell().add(0, 1.3, 0);
            move(p, p.serverLevel(), new Vec3(g.x - .6, LevelFive.arrival().y, g.z + 2.6), 180, 0);
            a = g.add(-.6, .9, 8.5);
            // Stopping short of the fighter's shoulder: closer, their head filled the frame.
            b = g.add(-.5, .7, 5.8);
            c = cell;
        }

        @Override FilmCamera.Move camera() { return dolly(length, a, c, b, c, 52, 42); }

        @Override void leave(ServerPlayer p) { LevelFive.ascend(p); }
    }

    /** 36. The Collection: powers under glass, Sylar's brass schematics drawn and landing, the camera tracking past. */
    static final class Collection extends Place {
        Collection() { super("36-collection", 130, 6000); }

        @Override void stage(ServerPlayer p) {
            TrailerPreview.grant(p, ModAbilities.QUAKE_ID, null, 10);
            CollectionArena.open(p);
        }

        @Override boolean ready(ServerPlayer p) { return CollectionArena.sylar() != null && CollectionArena.inCollection(p); }

        @Override void cast(ServerPlayer p) {
            stand(p, 6, 10);
            Vec3 c0 = SylarGeometry.center();
            a = c0.add(-10, 6, 15);
            b = c0.add(9, 5, 13);
            c = c0.add(0, 2.5, 0);
        }

        @Override FilmCamera.Move camera() { return dolly(length, a, c, b, c, 52, 46); }

        @Override void act(ServerPlayer p, int tick) {
            var s = CollectionArena.sylar();
            if (s == null) return;
            if (tick == 10) { s.castNow("lattice"); Stage.mark("lattice"); }
            if (tick == 70) { s.castNow("second_hand"); Stage.mark("second-hand"); }
        }

        @Override void leave(ServerPlayer p) { CollectionArena.end(p.getServer(), CollectionArena.Outcome.ABANDONED); }
    }

    /** Stands the player on the Collection's dial at clock angle {@code hours} and radius {@code r}, facing the dais. */
    static void stand(ServerPlayer p, double hours, double r) {
        double g = hours * Math.PI / 6;
        Vec3 c = SylarGeometry.center(), at = c.add(SylarGeometry.dx(g, r), 0, SylarGeometry.dz(g, r));
        move(p, p.serverLevel(), at, (float) Math.toDegrees(Math.atan2(-(c.x - at.x), c.z - at.z)), 8);
    }

    /** 44. After the title: a case in the Collection, a power kept under glass, the camera creeping toward it. */
    static final class Stinger extends Place {
        Stinger() { super("44-stinger", 80, 6000); }

        @Override void stage(ServerPlayer p) {
            TrailerPreview.grant(p, ModAbilities.QUAKE_ID, null, 10);
            CollectionArena.open(p);
        }

        @Override boolean ready(ServerPlayer p) { return CollectionArena.sylar() != null && CollectionArena.inCollection(p); }

        @Override void cast(ServerPlayer p) {
            stand(p, 0, 12);
            Vec3 c0 = SylarGeometry.center();
            a = c0.add(12.0, 2.5, -2.6);
            b = c0.add(13.0, 2.0, -5.0);
            c = c0.add(14, 1.4, -8);
        }

        @Override FilmCamera.Move camera() { return dolly(length, a, c, b, c, 46, 38); }

        @Override void leave(ServerPlayer p) { CollectionArena.end(p.getServer(), CollectionArena.Outcome.ABANDONED); }
    }

    // ================================================================== Act III and the drop, on the hilltop (WitherPreview, TemporalInteractionPreview)

    /** 37. The Wither knitting together, a seam splitting behind it, the breach tearing open and dragging it through. */
    static final class WitherBreach extends Shot {
        WitherBreach() { super("37-wither-breach", 150, 18000); }

        @Override void cast(ServerPlayer p) {
            WitherArena.reset(p.getServer(), false);
            TrailerPreview.grant(p, ModAbilities.FLIGHT_ID, null, 20);
            ServerLevel world = p.server.overworld();
            Vec3 at = Stage.at(0, 0, 9);
            var w = EntityType.WITHER.create(world);
            w.moveTo(at.x, Stage.groundAt(world, at), at.z, Stage.FORWARD + 180, 0);
            w.makeInvulnerable();
            world.addFreshEntity(w);
        }

        @Override FilmCamera.Move camera() {
            return arc(length, Stage.at(8, 3.5, 2).add(0, Stage.lift, 0), Stage.at(0, 2.6, 11), 18, 1.1, .95, 60, 54);
        }

        @Override double speed(double tick) { return slow(tick, 60, 80, .5); }

        @Override void act(ServerPlayer p, int tick) { if (tick == 64) Stage.mark("breach"); }

        @Override void leave(ServerPlayer p) { WitherArena.reset(p.getServer(), false); Stage.clear(p); }
    }

    /**
     * 39 and 40. Still World: a volley stopped in the air round the fighter (arrows, fireballs, a skull, a trident, a
     * firework, rounds), the camera gliding through it; then time starts again and everything lands at once.
     */
    static final class StillWorld extends Shot {
        static final int STOP = 8, GO = 104;

        StillWorld() { super("39-still-world", 128, Shots.DUSK); }

        @Override void cast(ServerPlayer p) {
            TrailerPreview.grant(p, ModAbilities.TIME_MANIPULATION_ID, null, 10);
            PlayerEnergyService.setUnlimitedEnergy(p, true);
            Stage.puppet(p, EntityType.PILLAGER, 4, 9);
            Stage.puppet(p, EntityType.PILLAGER, -5, 8);
            Stage.puppet(p, EntityType.VINDICATOR, 0, 12);
        }

        @Override FilmCamera.Move camera() {
            Vec3 feet = Stage.feet();
            return FilmCamera.Move.orbit(length, FilmCamera.Ease.BOTH, feet, feet.add(-1.2, 1.5, 0), 20, 150,
                    5.6, 4.4, 1.8 + Stage.lift, 1.5 + Stage.lift, 52, 46, 0);
        }

        @Override double speed(double tick) { return slow(tick, GO - 2, GO + 14, .45); }

        @Override void act(ServerPlayer p, int tick) {
            if (tick == STOP) {
                TimeStopService.start(p, 5);
                volley(p);
                Stage.mark("stop");
            }
            if (tick == GO) {
                TimeStopService.stop(p);
                Stage.mark("resume");
            }
        }

        /** Shots on their way in from every side, spawned inside stopped time so they hang where they are. */
        private static void volley(ServerPlayer p) {
            ServerLevel level = p.serverLevel();
            Entity owner = Stage.ACTORS.isEmpty() ? p : Stage.ACTORS.getFirst();
            Vec3 chest = p.position().add(0, 1.3, 0);
            fly(level, EntityType.ARROW.create(level), owner, Stage.at(3.2, 1.6, 6.5), chest, 2.2);
            fly(level, EntityType.ARROW.create(level), owner, Stage.at(-3.8, 1.5, 6), chest, 2.2);
            fly(level, EntityType.ARROW.create(level), owner, Stage.at(.4, 1.8, 8.5), chest, 2.2);
            fly(level, EntityType.SMALL_FIREBALL.create(level), owner, Stage.at(1.5, 2.2, 4.5), chest, .6);
            fly(level, EntityType.SMALL_FIREBALL.create(level), owner, Stage.at(-2, 2.6, 5.5), chest, .6);
            fly(level, EntityType.WITHER_SKULL.create(level), owner, Stage.at(-.8, 2.4, 3.5), chest, .5);
            fly(level, EntityType.TRIDENT.create(level), owner, Stage.at(2.4, 1.9, 3), chest, 2.0);
            fly(level, new FireworkRocketEntity(level, 0, 0, 0, new ItemStack(Items.FIREWORK_ROCKET)), owner, Stage.at(.8, 3.2, 6), chest, .8);
            fly(level, EntityType.ARROW.create(level), owner, Stage.at(-1.6, 1.7, 3.2), chest, 2.2);
            fly(level, EntityType.SPECTRAL_ARROW.create(level), owner, Stage.at(1.1, 1.5, 2.4), chest, 2.2);
            // No TaCZ rounds: a bullet made without a gun has no start point, and its damage sum crashes the server
            // (EntityKineticBullet.getDamage) the moment it hits someone after time starts again.
        }

        private static void fly(ServerLevel level, Entity shot, Entity owner, Vec3 from, Vec3 to, double speed) {
            if (shot == null) return;
            if (shot instanceof Projectile projectile) projectile.setOwner(owner);
            shot.setPos(from);
            shot.setDeltaMovement(to.subtract(from).normalize().scale(speed));
            level.addFreshEntity(shot);
        }

        @Override void leave(ServerPlayer p) {
            TimeStopService.stop(p);
            PlayerEnergyService.setUnlimitedEnergy(p, false);
            Stage.clear(p);
        }
    }

    // ================================================================== Finale: the End (EndgamePreview, through endgame/TrailerEnd)

    /** 41. The Living Seal's Unmaking over the island, seen wide from high on its side. */
    static final class Unmaking extends Place {
        Unmaking() { super("41-unmaking", 120, 6000); }

        @Override void stage(ServerPlayer p) { TrailerEnd.stage(p); }

        @Override boolean ready(ServerPlayer p) { return TrailerEnd.ready(p); }

        @Override int settle() { return 120; }

        @Override void cast(ServerPlayer p) {
            TrailerEnd.finale();
            TrailerEnd.land(p);
            Vec3 chest = TrailerEnd.chest();
            a = chest.add(26, 14, 26);
            b = chest.add(17, 8, 17);
            c = chest;
        }

        @Override FilmCamera.Move camera() { return dolly(length, a, c, b, c.add(0, 4, 0), 58, 52); }

        @Override void act(ServerPlayer p, int tick) {
            if (tick == 6) { TrailerEnd.move(SealGeometry.Move.UNMAKING); Stage.mark("unmaking"); }
            if (tick == 6 + SealGeometry.Move.UNMAKING.windup) Stage.mark("impact");
        }
    }

    /** 42. The rupture: the Dragon's light drawn out of it into Sylar's brass, the Inheritance unfurling. */
    static final class Inheritance extends Place {
        Inheritance() { super("42-inheritance", 170, 6000); }

        @Override void stage(ServerPlayer p) { if (!TrailerEnd.ready(p)) TrailerEnd.stage(p); }

        @Override boolean ready(ServerPlayer p) { return TrailerEnd.ready(p); }

        @Override int settle() { return 120; }

        @Override void cast(ServerPlayer p) {
            // Framed on the Dragon where it is (a fixed point 25 blocks off left the light small and to one side).
            c = TrailerEnd.chest();
            a = c.add(14, 5, 14);
            b = c.add(9, 3, 9);
        }

        @Override FilmCamera.Move camera() { return dolly(length, a, c, b, c.add(0, 3, 0), 60, 52); }

        @Override void act(ServerPlayer p, int tick) {
            if (tick == 8) { TrailerEnd.inherit(); Stage.mark("inherit"); }
        }

        @Override void leave(ServerPlayer p) {
            TrailerEnd.leave(p);
            Stage.place(p, 0, 0);
        }
    }
}
