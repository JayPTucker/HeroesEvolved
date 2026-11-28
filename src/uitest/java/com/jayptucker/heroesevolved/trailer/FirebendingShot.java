package com.jayptucker.heroesevolved.trailer;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.pyro.PyroCombat;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Shot 11, firebending at sunset: a jab, cross and roundhouse into four husks walking in (slow motion on the kick), a
 * jet step, the same combo in blue fire, and a flip kick, while the camera follows low from behind the fighter round
 * to their face with the sun going down behind the husks.
 */
final class FirebendingShot extends Shot {
    FirebendingShot() { super("11-firebending-sunset", 134, 12450); }

    @Override void cast(ServerPlayer p) {
        TrailerPreview.grant(p, ModAbilities.PYROKINESIS_ID, null, 12);
        ServerLevel world = p.server.overworld();
        // West and north-west of the fighter, so they walk in across the sunset and never through the camera's arc (south).
        double[][] at = {{-6.5, -2.4}, {-7.5, .4}, {-9.5, -3.8}, {-9, -1}};
        for (double[] d : at) {
            Husk husk = EntityType.HUSK.create(world);
            Vec3 spot = Stage.feet().add(d[0], 0, d[1]);
            husk.moveTo(spot.x, Stage.groundAt(world, spot), spot.z, -90, 0);
            husk.setYHeadRot(-90);
            husk.yBodyRot = -90;
            husk.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
            husk.setPersistenceRequired();
            husk.goalSelector.removeAllGoals(g -> true);
            husk.targetSelector.removeAllGoals(g -> true);
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
            husk.setHealth(400);
            husk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.16);
            husk.setAggressive(true);
            world.addFreshEntity(husk);
            Stage.ACTORS.add(husk);
        }
    }

    /**
     * Low and close, following the fighter: from behind their left shoulder (south-east of them, the husks and the sun
     * beyond) round past their side to in front of them (west, where the husks come from), so the shot opens on the
     * sunset and ends on their face lit by it, pushing in and narrowing as it goes.
     */
    @Override FilmCamera.Move camera() {
        Vec3 feet = Stage.feet();
        return FilmCamera.Move.orbit(length, FilmCamera.Ease.BOTH, feet, feet.add(-.4, 1.25, -.3),
                60, 168, 4.7, 3.5, 1.6 + Stage.lift, 1.3 + Stage.lift, 44, 38, 0);
    }

    @Override double followRate() { return .12; }

    @Override double near() { return 3.2; }

    /** Real time, with slow motion on the two big kicks. */
    @Override double speed(double tick) { return Math.min(slow(tick, 28, 43, .3), slow(tick, 95, 112, .35)); }

    @Override void act(ServerPlayer p, int tick) {
        steer(p);
        switch (tick) {
            case 16, 23, 64, 71 -> strike(p, tick);
            case 30 -> { strike(p, tick); Stage.mark("kick"); }
            case 33 -> Stage.ON_CLIENT.add(() -> FilmCamera.shake(.08, 10));
            case 50 -> dodge(p, FightingStyles.Dodge.LEFT, tick);
            // The second combo burns blue.
            case 58 -> PyroService.addHeat(p, 100);
            case 78 -> { strike(p, tick); Stage.mark("blue-kick"); }
            case 81 -> Stage.ON_CLIENT.add(() -> FilmCamera.shake(.06, 8));
            case 92 -> Stage.ON_CLIENT.add(() -> FilmCamera.widen(16, 34));
            case 97 -> { dodge(p, FightingStyles.Dodge.BACK, tick); Stage.mark("flip"); }
            case 128 -> Stage.mark("end");
            default -> { }
        }
    }

    private static void strike(ServerPlayer p, int tick) {
        PlayerEnergyService.restore(p, 999);
        PreviewQuiet.check("TRAILER_FIRE", PyroCombat.next(p) != null && PyroCombat.strike(p) == 1, "strike at shot tick " + tick);
    }

    private static void dodge(ServerPlayer p, FightingStyles.Dodge way, int tick) {
        PlayerEnergyService.restore(p, 999);
        PreviewQuiet.check("TRAILER_FIRE", PyroCombat.dodge(p, way), way + " at shot tick " + tick);
    }

    /** The husks keep walking at the fighter and stop at arm's length. */
    private static void steer(ServerPlayer p) {
        for (Mob foe : Stage.ACTORS) {
            if (!foe.isAlive()) continue;
            foe.getLookControl().setLookAt(p, 30, 30);
            if (foe.distanceTo(p) > 2.4) {
                if (foe.tickCount % 4 == 0 || foe.getNavigation().isDone()) foe.getNavigation().moveTo(p, 1.0);
            } else foe.getNavigation().stop();
        }
    }

    /** The fighter turns smoothly to the nearest husk, as a player's mouse would. */
    @Override void watch(Minecraft mc, int tick) {
        var me = mc.player;
        Husk target = mc.level.getEntitiesOfClass(Husk.class, new AABB(me.blockPosition()).inflate(20), Husk::isAlive).stream()
                .min(Comparator.comparingDouble(h -> h.distanceToSqr(me))).orElse(null);
        if (target == null) return;
        Vec3 d = target.getBoundingBox().getCenter().subtract(me.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
        float turn = Mth.clamp(Mth.wrapDegrees(yaw - me.getYRot()), -22, 22);
        me.setYRot(me.getYRot() + turn);
        me.setXRot(Mth.approach(me.getXRot(), pitch, 6));
        me.setYHeadRot(me.getYRot());
    }
}
