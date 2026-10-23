package com.jayptucker.heroesevolved.client.effects;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Soft smoke quads replace thousands of overlapping sphere faces. Coordinates are local to the fixed blast. */
public final class DetonationCloudGeometry {
    public record Puff(float x, float y, float z, float width, float height, float heat, float alpha) {}
    private DetonationCloudGeometry() {}
    public static Matrix4f worldTransform(Vec3 origin, Vec3 camera, float scale) {
        return new Matrix4f().translation((float) (origin.x - camera.x),
                (float) (origin.y - camera.y), (float) (origin.z - camera.z)).scale(scale);
    }
    public static List<Puff> puffs(float seconds, float opacity, int detail) {
        float t = Mth.clamp(seconds / 6, 0, 1), growth = 1 - (float) Math.pow(1 - t, 3);
        float rise = Mth.clamp((seconds - .35F) / 5.5F, 0, 1);
        float top = 4 + rise * 46 + Math.min(seconds, 20) * .35F;
        float cap = 3 + growth * 24;
        float heat = Mth.clamp(1 - seconds / 10, 0, 1);
        List<Puff> puffs = new ArrayList<>(64);
        // A rooted turbulent column thickens into the underside of the cap.
        int layers = detail == 0 ? 6 : detail == 1 ? 9 : 15;
        for (int layer = 0; layer < layers; layer++) {
            float along = layer / (float) (layers - 1);
            float width = (2.5F + growth * 4.5F) * (1 + along * along * .5F);
            float height = top / layers + 2;
            double twist = layer * 2.4 + seconds * .16;
            puffs.add(new Puff((float) Math.cos(twist) * width * .12F,
                    Math.max(height + .1F, along * top), (float) Math.sin(twist) * width * .12F,
                    width, height, heat * (.8F - along * .5F), opacity * .85F));
        }
        // Three uneven rows roll outward/down on the rim: a broad crown, dark lip,
        // and hot inner billows instead of a stack of identical flat discs.
        int lobes = detail == 0 ? 6 : detail == 1 ? 9 : 12;
        for (int row = 0; row < 3; row++) for (int i = 0; i < lobes; i++) {
            double a = i * Math.PI * 2 / lobes + row * .31;
            double roll = seconds * .36 + i * 1.73;
            float radial = cap * (row == 0 ? .72F : row == 1 ? .48F : .22F);
            radial += (float) Math.cos(roll) * cap * .045F;
            float width = cap * (.32F + row * .035F) * (1 + .12F * (float) Math.sin(i * 4.1));
            float height = cap * (.22F + row * .035F);
            float y = top + (row - 1) * cap * .15F + (float) Math.sin(roll) * cap * .05F;
            puffs.add(new Puff((float) Math.cos(a) * radial, Math.max(height + .1F, y),
                    (float) Math.sin(a) * radial, width, height,
                    heat * (row == 0 ? .15F : row == 1 ? .5F : .7F), opacity * .78F));
        }
        // Ground-hugging dust expands after the shock front; it never follows the owner.
        int skirt = detail == 0 ? 5 : detail == 1 ? 8 : 12;
        float dustRadius = Math.min(seconds * 24, 34);
        float dustAlpha = opacity * Mth.clamp(seconds / .3F, 0, 1) * Mth.clamp((18 - seconds) / 10, 0, 1);
        for (int i = 0; i < skirt; i++) {
            double a = i * Math.PI * 2 / skirt;
            float height = 1 + growth * 2;
            puffs.add(new Puff((float) Math.cos(a) * dustRadius, height + .1F,
                    (float) Math.sin(a) * dustRadius, 2 + dustRadius * .3F, height,
                    heat * .08F, dustAlpha * .32F));
        }
        if (seconds < 2) puffs.add(new Puff(0, 4 + seconds * 3, 0, 2 + seconds * 7,
                3 + seconds * 3, 1, opacity * (1 - seconds / 2)));
        return puffs;
    }
    public static void draw(VertexConsumer out, Matrix4f matrix, Quaternionf camera, List<Puff> puffs, Vec3 viewer) {
        // Correct alpha ordering, including views from above or from inside the base of the plume.
        puffs.sort(Comparator.comparingDouble((Puff puff) -> viewer.distanceToSqr(puff.x, puff.y, puff.z)).reversed());
        Vector3f right = new Vector3f(1, 0, 0).rotate(camera), up = new Vector3f(0, 1, 0).rotate(camera);
        for (Puff puff : puffs) {
            float glow = Mth.clamp((puff.heat - .12F) / .88F, 0, 1);
            float r = Mth.lerp(glow, .36F, 1), g = Mth.lerp(glow * glow, .34F, .86F), b = Mth.lerp(glow * glow * glow, .32F, .52F);
            point(out, matrix, right, up, puff, -1, -1, 0, 1, r, g, b);
            point(out, matrix, right, up, puff, 1, -1, 1, 1, r, g, b);
            point(out, matrix, right, up, puff, 1, 1, 1, 0, r, g, b);
            point(out, matrix, right, up, puff, -1, 1, 0, 0, r, g, b);
        }
    }
    private static void point(VertexConsumer out, Matrix4f matrix, Vector3f right, Vector3f up, Puff puff,
                              float x, float y, float u, float v, float r, float g, float b) {
        out.addVertex(matrix, puff.x + right.x * puff.width * x + up.x * puff.height * y,
                puff.y + right.y * puff.width * x + up.y * puff.height * y,
                puff.z + right.z * puff.width * x + up.z * puff.height * y)
                .setUv(u, v).setColor(r, g, b, puff.alpha);
    }
}
