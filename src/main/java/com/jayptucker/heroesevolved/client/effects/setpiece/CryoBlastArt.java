package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.client.effects.intro.CloudMesh;
import com.jayptucker.heroesevolved.cryo.CryoBlastGeometry;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Powder billows from both palms. Jagged ice chips spin through the cold and scatter at impact. */
final class CryoBlastArt {
    private static final float[] SHADOW = Canvas.rgb(0x709DB2);
    private static final float[] ICE = Canvas.rgb(0xA6D3E5);
    private static final float[] MIST = Canvas.rgb(0xDCEBF1);
    private static final float[] WHITE = Canvas.rgb(0xF4FAFC);
    private static final float[] HEAT = Canvas.rgb(0xD89352);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private CryoBlastArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        if (a.piece() == Piece.CRYO_TERRAIN_FROST) {
            if (c.soft()) terrainFrost(c, a, t);
            return;
        }
        if (a.piece() == Piece.CRYO_QUENCH) {
            quench(c, a, t);
            return;
        }
        var mc = Minecraft.getInstance();
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;
        Player player = a.bound(c) instanceof Player caster ? caster : null;
        Vec3 end = a.target();
        float now = c.level.getGameTime() % 2400 + c.partial;
        boolean cast = a.piece() == Piece.CRYO_CAST;
        float alpha = cast ? Canvas.fade(t, a.piece().ticks, 2, 6)
                : .76F * (1 - Canvas.smooth(2, a.piece().ticks, t)) * Canvas.smooth(0, 2, t);
        boolean siphon = !cast && a.scale() > 1.5F;
        if (alpha < .01F || c.glow() && !siphon) return;
        Vec3 right = player == null ? Vec3.ZERO : Vec3.directionFromRotation(0, player.getViewYRot(c.partial) + 90);
        Vec3 aim = player == null ? end.subtract(a.origin()).normalize() : player.getViewVector(c.partial);
        boolean firstPerson = player != null && player == mc.player && mc.options.getCameraType().isFirstPerson();
        float flight = cast ? (float) Math.clamp(a.origin().distanceTo(end) / 3, 2, 6) : 0;
        float front = cast ? Canvas.smooth(0, flight, t) : 1;
        float tail = cast ? Canvas.smooth(6, a.piece().ticks, t) : 0;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 start = a.origin().add(aim.scale(.6)).add(0, -.2, 0);
            if (firstPerson) start = player.getEyePosition(c.partial).add(aim.scale(.61))
                    .add(right.scale(side * .40)).subtract(right.cross(aim).scale(.33));
            else if (player != null) {
                Vec3 shoulder = CryoBlastGeometry.shoulder(player, c.partial, side);
                start = cast ? shoulder.add(end.subtract(shoulder).normalize().scale(CryoBlastGeometry.ARM_REACH))
                        : CryoBlastGeometry.palm(player, c.partial, side);
            }
            Vec3 lead = start.lerp(end, front);
            Vec3 trail = start.lerp(end, tail);
            if (trail.distanceToSqr(lead) > (cast ? .04 : .12))
                stream(c, trail, lead, side, now, alpha, minimal, siphon, a.entity(), cast ? a.scale() : 1);
        }
        if (c.soft() && !minimal && (cast ? t >= flight : a.scale() >= 1)) {
            float spread = cast ? a.scale() : 1;
            float impact = cast ? Canvas.smooth(flight, flight + 2, t) : 1;
            Vec3 landing = cast ? end : end.subtract(aim.scale(.15));
            c.cloud(landing, .44F * spread, .37F * spread, .44F * spread, 2, now * .19F,
                    (cast ? .42F : .28F) * alpha * impact, frost(c));
        }
    }

    private static void terrainFrost(Canvas c, Active a, float age) {
        BlockPos pos = BlockPos.containing(a.origin());
        if (!c.level.hasChunkAt(pos)) return;
        var state = c.level.getBlockState(pos);
        if (state.isAir()) return;
        Vec3 normal = a.target().subtract(a.origin()).normalize();
        Vec3 right = Math.abs(normal.y) > .5 ? new Vec3(1, 0, 0) : normal.cross(UP);
        Vec3 up = normal.cross(right);
        Vec3 center = a.origin().add(normal.scale(.504));
        if (normal.y > .5 && (state.is(Blocks.WATER) || state.is(Blocks.LAVA))) {
            double height = state.getFluidState().getHeight(c.level, pos);
            center = center.add(0, height - 1, 0);
        }

        // The rime starts as a small star and spreads until, just before the block turns, it frosts the whole face.
        float growth = Math.max(0, a.scale() - .12F * (1 - Canvas.smooth(0, 5, age)));
        float alpha = (0.4F + .45F * growth) * (1 - Canvas.smooth(58, a.piece().ticks, age));
        float radius = .74F * (float) Math.sqrt(growth);
        int seed = Long.hashCode(pos.asLong());
        boolean minimal = Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL;
        float rim = alpha * (.38F + .4F * growth);
        for (int i = 0; i < 8; i++) {
            Vec3 left = frostEdge(center, right, up, radius, seed, i);
            Vec3 edge = frostEdge(center, right, up, radius, seed, (i + 1) % 8);
            float[] tint = Canvas.mix(ICE, MIST, Canvas.hash(seed, i + 8));
            c.tri(center, left, edge, tint, alpha, rim, rim);
        }
        if (minimal || growth < .12F) return;
        // Frost feathers: white veins from the impact, each with barbs swept back toward it.
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3 + (Canvas.hash(seed, i + 32) - .5) * .7;
            double reach = radius * (.75 + .3 * Canvas.hash(seed, i + 40));
            Vec3 tip = frostPoint(center, right, up, Math.cos(angle) * reach, Math.sin(angle) * reach);
            c.line(center, tip, .014F, WHITE, alpha * .7F, alpha * .15F);
            for (int b = 1; b <= 3; b++) {
                double along = reach * b / 4, barb = reach * (.22 - .04 * b);
                for (int side = -1; side <= 1; side += 2) {
                    double branch = angle + side * .75;
                    Vec3 root = frostPoint(center, right, up, Math.cos(angle) * along, Math.sin(angle) * along);
                    Vec3 end = frostPoint(center, right, up, Math.cos(angle) * along + Math.cos(branch) * barb,
                            Math.sin(angle) * along + Math.sin(branch) * barb);
                    c.line(root, end, .009F, WHITE, alpha * .5F, alpha * .08F);
                }
            }
        }
    }

    private static Vec3 frostEdge(Vec3 center, Vec3 right, Vec3 up, float radius, int seed, int i) {
        double angle = i * Math.PI / 4 + (Canvas.hash(seed, i) - .5) * .35;
        double reach = radius * (.8 + Canvas.hash(seed, i + 16) * .57);
        return frostPoint(center, right, up, Math.cos(angle) * reach, Math.sin(angle) * reach);
    }

    /** A point on the struck face, kept inside it. */
    private static Vec3 frostPoint(Vec3 center, Vec3 right, Vec3 up, double x, double y) {
        return center.add(right.scale(Math.clamp(x, -.489, .489))).add(up.scale(Math.clamp(y, -.489, .489)));
    }

    private static void stream(Canvas c, Vec3 start, Vec3 end, int side, float now, float alpha,
                               boolean minimal, boolean siphon, int seed, float width) {
        Vec3 path = end.subtract(start), axis = path.normalize();
        Vec3 right = axis.cross(Math.abs(axis.y) > .95 ? new Vec3(0, 0, 1) : UP).normalize();
        Vec3 up = right.cross(axis);
        if (c.soft()) {
            var shade = frost(c);
            int billows = minimal ? 4 : 8;
            for (int i = 0; i < billows; i++) {
                float u = (now * .0875F + (i + (side > 0 ? .5F : 0)) / billows) % 1;
                float spin = i * 2.39996F + side * now * .31F;
                float spread = (.07F + u * .42F) * width;
                Vec3 at = start.lerp(end, u).add(right.scale(Mth.cos(spin) * spread))
                        .add(up.scale(Mth.sin(spin) * spread * .78F)).add(0, -.16F * u * u, 0);
                float radius = (.12F + u * .48F) * (.78F + .44F * Canvas.hash(seed, i)) * width;
                float fade = Canvas.smooth(0, .09F, u) * (1 - Canvas.smooth(.82F, 1, u));
                float stretch = (float) Math.min(.53, path.length() * .06);
                c.cloud(at, radius + stretch * (float) Math.abs(axis.x), radius * .83F + stretch * (float) Math.abs(axis.y),
                        radius + stretch * (float) Math.abs(axis.z), i, spin, .32F * alpha * fade * clearance(c, at), shade);
            }
            int flakes = minimal ? 5 : 16;
            for (int i = 0; i < flakes; i++) {
                float u = (now * .1675F + (i + Canvas.hash(seed, i + 50)) / flakes) % 1;
                float angle = i * 2.39996F + side * now * .24F;
                float spread = (.09F + u * .62F) * Canvas.hash(seed, i + 23) * width;
                Vec3 drift = right.scale(Mth.cos(angle)).add(up.scale(Mth.sin(angle)));
                Vec3 at = start.lerp(end, u).add(drift.scale(spread)).add(0, -.21F * u * u, 0);
                Vec3 tip = at.add(axis.scale(.07F + .14F * Canvas.hash(seed, i + 104)))
                        .add(drift.scale(.06F + Mth.sin(now * .9F + i) * .09F));
                float fade = Canvas.smooth(0, .06F, u) * (1 - Canvas.smooth(.88F, 1, u));
                c.shard(at, tip, .021F + .041F * Canvas.hash(seed, i + 112), 4, angle + now * .46F,
                        i % 3 == 0 ? ICE : WHITE, alpha * fade * clearance(c, at));
            }
        }
        if (c.glow() && siphon) {
            int embers = minimal ? 2 : 5;
            for (int i = 0; i < embers; i++) {
                float u = (now * .0825F + (i + Canvas.hash(seed, i + 80)) / embers + side * .075F + 1) % 1;
                float flare = Mth.sin(u * Mth.PI);
                float angle = now * .34F + i * 2.39996F;
                Vec3 at = end.lerp(start, u).add(right.scale(.28F * flare * Mth.cos(angle)))
                        .add(up.scale(.32F * flare * Mth.sin(angle)));
                c.glow(at, .088F, HEAT, .68F * flare * alpha * clearance(c, at));
            }
        }
    }

    private static CloudMesh.Shade frost(Canvas c) {
        float daylight = c.daylight;
        return (nx, ny, nz, bump, x, y, z, rgb) -> {
            float light = Mth.clamp(.56F + daylight * .2F + ny * .21F + bump * .7F, .22F, 1);
            for (int i = 0; i < rgb.length; i++) rgb[i] = Mth.lerp(light, SHADOW[i], MIST[i]);
        };
    }

    private static float clearance(Canvas c, Vec3 at) {
        return Canvas.smooth(.28F, .9F, (float) at.distanceTo(c.camera()));
    }

    private static void quench(Canvas c, Active a, float t) {
        float age = t / a.piece().ticks;
        float alpha = Canvas.smooth(0, 2, t) * (1 - Canvas.smooth(.3F, 1, age));
        Vec3 center = a.bound(c) instanceof Player target
                ? target.getPosition(c.partial).add(0, target.getBbHeight() * .5, 0) : a.origin();
        float size = a.scale(), radius = size * (.17F + .66F * Canvas.smooth(0, .5F, age));
        if (c.soft()) {
            c.cloud(center.add(0, age * .4, 0), radius, radius * .7F, radius, 3, age,
                    .51F * alpha, frost(c));
            int count = Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL ? 4 : 9;
            for (int i = 0; i < count; i++) {
                double angle = (i + Canvas.hash(a.seed(), i) * .5) * Math.PI * 2 / count;
                Vec3 base = center.add(Math.cos(angle) * radius, age * .47 - .26, Math.sin(angle) * radius);
                Vec3 tip = base.add(Math.cos(angle) * .13, size * (.16 + Canvas.hash(a.seed(), i + count) * .34),
                        Math.sin(angle) * .13);
                c.shard(base, tip, size * .062F, 4, (float) angle, i % 3 == 0 ? ICE : WHITE, alpha);
            }
        }
    }
}
