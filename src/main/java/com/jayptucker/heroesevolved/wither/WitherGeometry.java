package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** The cage, warning shapes and server impacts share one geometry. */
public final class WitherGeometry {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "wither_cage"));
    public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    public static final int INTRO_TICKS = 120, DEATH_TICKS = 60, MINIMUM_LEVEL = 16;
    public static final double RADIUS = 38, FLIGHT_HEIGHT = 46, PULSE_SPEED = .75;
    public static final float VERDICT_RADIUS=1.1F,TETHER_RADIUS=1,LUNGE_RADIUS=2,LUNGE_REACH=13,SLAM_RADIUS=4.3F,
            SKULL_RADIUS=2.3F,RAIN_HEIGHT=9;
    public static final List<BlockPos> ANCHORS = List.of(ORIGIN.offset(-24, 6, 8), ORIGIN.offset(24, 6, 8), ORIGIN.offset(0, 6, -27));
    public static Vec3 floor() { return Vec3.atBottomCenterOf(ORIGIN.above(3)); }
    public static Vec3 center() { return floor().add(0, 7, 0); }
    public static Vec3 entrance() { return floor().add(7, 0, -31); }
    public static Vec3 exit() { return floor().add(0, 1.6, -38); }
    public enum Move {
        VERDICT(28,40), RAIN(30,45), PULSE(32,55), TETHER(22,40), LUNGE(28,55), SLAM(32,60), HORIZON(60,80);
        public final int windup, rest;
        Move(int windup, int rest) { this.windup=windup; this.rest=rest; }
    }
    public static double lineDistance(Vec3 a, Vec3 b, Vec3 point) {
        Vec3 d=b.subtract(a);
        double t=Math.clamp(point.subtract(a).dot(d)/Math.max(1e-7,d.lengthSqr()),0,1);
        return point.distanceTo(a.add(d.scale(t)));
    }
    private WitherGeometry() {}
}
