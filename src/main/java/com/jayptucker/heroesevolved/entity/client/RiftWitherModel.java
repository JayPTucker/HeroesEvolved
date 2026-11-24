package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.wither.RiftWitherEntity;
import com.jayptucker.heroesevolved.wither.WitherGeometry;
import com.jayptucker.heroesevolved.wither.WitherGeometry.Move;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Wither inside its cage: a skeletal sovereign about ten blocks tall. A bone yoke carries three heads on stretching
 * necks and two long clawed arms; a hinged ribcage holds the star; armoured pauldrons shed at the second phase; a tail
 * drags behind with follow-through. Every move reads in the body before it lands: the center head coils before it snaps,
 * an arm cocks across the chest before it rakes, both fists climb overhead before the slam, the claws dig in before the
 * spires, and the whole cage opens before the star discharges.
 *
 * <p>Texture layout (shared with {@code tools/generate-wither-assets.py}): 256 x 256; pale bone v 0..63, charred bone
 * v 64..127, pauldron plate u 0..127 v 128..191, the craniums at (128,128), (128,150) and (176,150), the star at
 * (0,192), ivory (claws, teeth) at (64,192), horn at (128,192).
 */
public final class RiftWitherModel extends HierarchicalModel<RiftWitherEntity> {
    private static final int BONE = 0, CHAR = 64;
    private static final float NECK_REST = 6;
    private final ModelPart root, hips, waist, chest, yoke, core;
    private final ModelPart[] tail = new ModelPart[4];
    private final ModelPart[] heads = new ModelPart[3], jaws = new ModelPart[3], neckA = new ModelPart[3], neckB = new ModelPart[3];
    private final ModelPart[][] ribs = new ModelPart[2][4], ribB = new ModelPart[2][4], ribC = new ModelPart[2][4];
    private final ModelPart[] shoulders = new ModelPart[2], forearms = new ModelPart[2], hands = new ModelPart[2], pauldrons = new ModelPart[2];
    private final ModelPart[][] claws = new ModelPart[2][4], clawTips = new ModelPart[2][4];

    public RiftWitherModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        // Hips: the cracked pelvis it hangs from, and the tail.
        PartDefinition hip = r.addOrReplaceChild("hips", CubeListBuilder.create()
                .texOffs(0, CHAR).addBox(-7, -3, -4, 14, 5, 9)
                .texOffs(46, CHAR).addBox(-5, -5, -2, 10, 2, 6)
                .texOffs(128, 192).addBox(-10, -2, -2, 3, 3, 4).addBox(7, -2, -2, 3, 3, 4),
                PartPose.offset(0, -WitherGeometry.HIPS[1], -WitherGeometry.HIPS[2]));
        PartDefinition t = hip.addOrReplaceChild("tail0", CubeListBuilder.create().texOffs(80, CHAR).addBox(-2.5F, 0, -2.5F, 5, 8, 5),
                PartPose.offsetAndRotation(0, 1, 5, 1.05F, 0, 0));
        t = t.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(100, CHAR).addBox(-2, 0, -2, 4, 8, 4)
                .texOffs(136, 192).addBox(-.5F, 1, 2, 1, 4, 2), PartPose.offsetAndRotation(0, 8, 0, .2F, 0, 0));
        t = t.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(116, CHAR).addBox(-1.5F, 0, -1.5F, 3, 7, 3)
                .texOffs(142, 192).addBox(-.5F, 1, 1.5F, 1, 3, 2), PartPose.offsetAndRotation(0, 8, 0, .22F, 0, 0));
        t.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(128, CHAR).addBox(-1, 0, -1, 2, 6, 2)
                .texOffs(150, 192).addBox(-3, 3, -.5F, 6, 5, 1), PartPose.offsetAndRotation(0, 7, 0, .28F, 0, 0));
        // The lumbar spine bends for every lean, rear and slump.
        PartDefinition w = hip.addOrReplaceChild("waist", CubeListBuilder.create()
                .texOffs(140, CHAR).addBox(-3, -4, -2.5F, 6, 4, 5).addBox(-2, -5, -2, 4, 1, 4)
                .texOffs(162, CHAR).addBox(-3, -9, -2.5F, 6, 4, 5).addBox(-2, -10, -2, 4, 1, 4)
                .texOffs(184, CHAR).addBox(-3.5F, -14, -2.5F, 7, 4, 5)
                .texOffs(160, 192).addBox(-1, -3, 2.5F, 2, 2, 4).addBox(-1, -8, 2.5F, 2, 2, 4).addBox(-1, -13, 2.5F, 2, 2, 4),
                PartPose.offset(0, -2, 0));
        PartDefinition c = w.addOrReplaceChild("chest", CubeListBuilder.create()
                .texOffs(0, CHAR + 20).addBox(-3.5F, -22, 0, 7, 22, 6)
                .texOffs(176, 192).addBox(-1, -6, 6, 2, 3, 4).addBox(-1, -12, 6, 2, 3, 4).addBox(-1, -18, 6, 2, 3, 4),
                PartPose.offset(0, -14, 0));
        c.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 192).addBox(-2.5F, -2.5F, -2.5F, 5, 5, 5)
                        .texOffs(24, 192).addBox(-.5F, -.5F, 2.5F, 1, 1, 6).addBox(-.5F, -8, -.5F, 1, 5, 1).addBox(-.5F, 3, -.5F, 1, 5, 1),
                PartPose.offset(0, -(WitherGeometry.CORE[1] - 32), -WitherGeometry.CORE[2] - 2));
        int[][] lengths = {{5, 8, 7}, {6, 9, 8}, {6, 9, 8}, {5, 8, 6}};
        for (int side = 0; side < 2; side++) {
            float sign = side == 0 ? -1 : 1;
            for (int i = 0; i < 4; i++) {
                int a = lengths[i][0], b = lengths[i][1], cc = lengths[i][2], u = 96 + i * 36 + side * 18;
                PartDefinition ra = c.addOrReplaceChild("rib" + side + i, ribBox(u, 4, a, sign), PartPose.offset(3.5F * sign, -3 - 5 * i, 1));
                PartDefinition rb = ra.addOrReplaceChild("b", ribBox(u, 10, b, sign), PartPose.offsetAndRotation(a * sign, 0, 0, 0, 1.3F * sign, 0));
                rb.addOrReplaceChild("c", ribBox(u, 16, cc, sign), PartPose.offsetAndRotation(b * sign, 0, 0, 0, 1.45F * sign, 0));
            }
        }
        // The yoke: a clavicle bar, shoulder knobs and the armoured pauldrons it sheds at the second phase.
        PartDefinition y = c.addOrReplaceChild("yoke", CubeListBuilder.create()
                .texOffs(0, BONE).addBox(-19, -3, -3, 38, 5, 6)
                .texOffs(30, CHAR + 20).addBox(-23, -6, -4, 7, 8, 8).addBox(16, -6, -4, 7, 8, 8)
                .texOffs(186, 192).addBox(-1, -7, -1, 2, 4, 3),
                PartPose.offset(0, -22, 0));
        for (int side = 0; side < 2; side++) {
            float sign = side == 0 ? -1 : 1;
            float x0 = sign < 0 ? -6 : -4;
            PartDefinition pd = y.addOrReplaceChild("pauldron" + side, CubeListBuilder.create()
                    .texOffs(0, 128).addBox(x0, -2, -6, 10, 3, 12)
                    .texOffs(48, 128).addBox(x0 + 1, -5, -5, 8, 3, 10)
                    .texOffs(0, 150).addBox(x0 + 2, -7, -3, 6, 2, 6)
                    .texOffs(196, 192).addBox(sign < 0 ? -4 : 2, -12, -1, 2, 6, 2).addBox(-1, -13, 1, 2, 7, 2),
                    PartPose.offsetAndRotation(19 * sign, -5, 0, 0, 0, .18F * sign));
            // Arms hang beneath the knobs: upper arm, forearm, a broad hand and four claws.
            PartDefinition up = y.addOrReplaceChild("arm" + side, CubeListBuilder.create()
                    .texOffs(side * 22, 24).addBox(-2.5F, -1, -2.5F, 5, 18, 5)
                    .texOffs(56 + side * 26, CHAR + 50).addBox(-3, 14, -3, 6, 5, 6),
                    PartPose.offsetAndRotation(WitherGeometry.SHOULDER[side][0], -1, 0, 0, 0, -.1F * sign));
            PartDefinition fore = up.addOrReplaceChild("fore", CubeListBuilder.create()
                    .texOffs(44 + side * 18, 24).addBox(-2, 0, -2, 4, 17, 4)
                    .texOffs(204, 192).addBox(-.5F, 3, 2, 1, 10, 2),
                    PartPose.offsetAndRotation(0, 18, 0, -.4F, 0, 0));
            PartDefinition hand = fore.addOrReplaceChild("hand", CubeListBuilder.create()
                    .texOffs(110 + side * 28, CHAR + 50).addBox(-3.5F, 0, -3, 7, 3, 6)
                    .texOffs(166, CHAR + 50).addBox(-3.5F, 2, -3.5F, 7, 2, 2),
                    PartPose.offsetAndRotation(0, 17, 0, -.2F, 0, 0));
            float[][] at = {{-2.5F, 4, -2.5F}, {0, 4, -3}, {2.5F, 4, -2.5F}, {-3.2F * sign * -1, 3, 1.5F}};
            for (int k = 0; k < 4; k++) {
                PartDefinition claw = hand.addOrReplaceChild("claw" + k, CubeListBuilder.create().texOffs(64 + k * 8, 192).addBox(-.75F, 0, -.75F, 1.5F, 6, 1.5F),
                        PartPose.offsetAndRotation(at[k][0], at[k][1] - 1, at[k][2], k == 3 ? .5F : -.25F, 0, 0));
                claw.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(96 + k * 6, 192).addBox(-.5F, 0, -.5F, 1, 5, 1),
                        PartPose.offsetAndRotation(0, 6, 0, k == 3 ? .4F : -.55F, 0, 0));
            }
        }
        // Necks are two stretching segments each, re-aimed every frame between the yoke and their head.
        for (int i = 0; i < 3; i++) {
            float wide = i == 0 ? 5 : 4;
            y.addOrReplaceChild("neckA" + i, CubeListBuilder.create().texOffs(200 + i * 18, CHAR).addBox(-wide / 2, -NECK_REST, -wide / 2, wide, NECK_REST, wide)
                    .texOffs(214, 192).addBox(-.5F, -5, wide / 2, 1, 3, 2), PartPose.ZERO);
            y.addOrReplaceChild("neckB" + i, CubeListBuilder.create().texOffs(200 + i * 18, CHAR + 14).addBox(-wide / 2 + .5F, -NECK_REST, -wide / 2 + .5F, wide - 1, NECK_REST, wide - 1),
                    PartPose.ZERO);
            PartDefinition head;
            if (i == 0) {
                head = y.addOrReplaceChild("head0", CubeListBuilder.create()
                        .texOffs(128, 128).addBox(-6, -10, -8, 12, 9, 10)
                        .texOffs(0, CHAR + 34).addBox(-6.5F, -9, -9.5F, 13, 2, 2)
                        .texOffs(40, 40).addBox(-3.5F, -5, -10.5F, 7, 4, 3)
                        .texOffs(64, 40).addBox(-7.5F, -5, -7, 2, 3, 5).addBox(5.5F, -5, -7, 2, 3, 5)
                        .texOffs(112, 192).addBox(-3, -1.5F, -10.3F, 6, 1, 2)
                        .texOffs(220, 192).addBox(-1, -14, -3, 2, 5, 6),
                        PartPose.ZERO);
                for (int s = 0; s < 2; s++) {
                    float sign = s == 0 ? -1 : 1;
                    PartDefinition horn = head.addOrReplaceChild("horn" + s, CubeListBuilder.create().texOffs(128 + s * 8, 202).addBox(-1, -5, -1, 2, 5, 2),
                            PartPose.offsetAndRotation(4.5F * sign, -9, -2, -.6F, 0, .5F * sign));
                    horn.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(144 + s * 6, 202).addBox(-.5F, -5, -.5F, 1, 5, 1),
                            PartPose.offsetAndRotation(0, -5, 0, -.5F, 0, .15F * sign));
                }
                head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(80, 40).addBox(-4.5F, 0, -8, 9, 3, 8)
                        .texOffs(112, 198).addBox(-3.5F, -1, -8.3F, 7, 1, 1), PartPose.offset(0, -1, -1));
            } else {
                float sign = i == 1 ? -1 : 1;
                head = y.addOrReplaceChild("head" + i, CubeListBuilder.create()
                        .texOffs(i == 1 ? 128 : 176, 150).addBox(-5, -8, -7, 10, 8, 9)
                        .texOffs(30, CHAR + 34).addBox(-5.5F, -7.5F, -8, 11, 2, 2)
                        .texOffs(40, 50).addBox(-3, -4, -9, 6, 3, 3)
                        .texOffs(112, 204).addBox(-2.5F, -1.2F, -9, 5, 1, 2),
                        PartPose.ZERO);
                PartDefinition horn = head.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(160, 202).addBox(-1, -5, -1, 2, 5, 2),
                        PartPose.offsetAndRotation(3.5F * sign, -7, 0, .75F, 0, .35F * sign));
                horn.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(168, 202).addBox(-.5F, -5, -.5F, 1, 5, 1),
                        PartPose.offsetAndRotation(0, -5, 0, .45F, 0, 0));
                head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(80, 52).addBox(-4, 0, -7, 8, 2, 7)
                        .texOffs(112, 210).addBox(-3, -1, -7.3F, 6, 1, 1), PartPose.offset(0, 0, -1));
            }
        }
        root = LayerDefinition.create(mesh, 256, 256).bakeRoot().getChild("root");
        hips = root.getChild("hips");
        for (int i = 0; i < 4; i++) tail[i] = i == 0 ? hips.getChild("tail0") : tail[i - 1].getChild("tail" + i);
        waist = hips.getChild("waist");
        chest = waist.getChild("chest");
        core = chest.getChild("core");
        yoke = chest.getChild("yoke");
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                ribs[side][i] = chest.getChild("rib" + side + i);
                ribB[side][i] = ribs[side][i].getChild("b");
                ribC[side][i] = ribB[side][i].getChild("c");
            }
            pauldrons[side] = yoke.getChild("pauldron" + side);
            shoulders[side] = yoke.getChild("arm" + side);
            forearms[side] = shoulders[side].getChild("fore");
            hands[side] = forearms[side].getChild("hand");
            for (int k = 0; k < 4; k++) {
                claws[side][k] = hands[side].getChild("claw" + k);
                clawTips[side][k] = claws[side][k].getChild("tip");
            }
        }
        for (int i = 0; i < 3; i++) {
            heads[i] = yoke.getChild("head" + i);
            jaws[i] = heads[i].getChild("jaw");
            neckA[i] = yoke.getChild("neckA" + i);
            neckB[i] = yoke.getChild("neckB" + i);
        }
    }

    private static CubeListBuilder ribBox(int u, int v, int length, float sign) {
        return CubeListBuilder.create().texOffs(u, v).addBox(sign < 0 ? -length : 0, -1, -1, length, 2, 2);
    }

    @Override public ModelPart root() { return root; }

    /** A smooth curve through (tick, value) keys, held flat before the first and after the last. */
    static float k(float t, float... keys) {
        if (t <= keys[0]) return keys[1];
        for (int i = 2; i < keys.length; i += 2) {
            if (t <= keys[i]) {
                float p = (t - keys[i - 2]) / Math.max(1e-4F, keys[i] - keys[i - 2]);
                p = p * p * (3 - 2 * p);
                return keys[i - 1] + (keys[i + 1] - keys[i - 1]) * p;
            }
        }
        return keys[keys.length - 1];
    }
    private static float ease(float a, float b, float x) { float t = Mth.clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }

    /** The pose for one frame. Everything here is a pure function of the entity's synced state and the frame's time. */
    @Override public void setupAnim(RiftWitherEntity e, float walk, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float partial = age - e.tickCount;
        Pose p = new Pose();
        p.open = e.phase() == 1 ? .08F : e.phase() == 2 ? .35F : .7F;
        // Breathing, hovering and a restless tail.
        p.bob = Mth.sin(age * .075F) * 1.3F;
        p.open += Mth.sin(age * .11F) * .05F;
        float lean = e.leanForward(partial), bank = e.leanSide(partial);
        p.chestX += lean * .35F;
        p.waistZ += bank * .25F;
        for (int i = 0; i < 4; i++) {
            tail[i].yRot += Mth.sin(age * .07F - i * .7F) * (.1F + i * .04F) - bank * .3F;
            tail[i].xRot += Mth.sin(age * .05F - i * .5F) * .05F - lean * .12F * (i + 1);
        }
        for (int s = 0; s < 2; s++) {
            p.shoulderX[s] += Mth.sin(age * .06F + s * 2) * .05F - lean * .35F;
            p.curl[s] = .1F + Mth.sin(age * .09F + s) * .08F;
        }
        // Idle heads: the center one watches the nearest player; the sides sway on their own.
        Vec3 watch = e.watchPoint(partial);
        for (int i = 0; i < 3; i++) {
            p.headLook[i] = watch;
            p.headSway[i] = Mth.sin(age * .045F + i * 2.1F) * (i == 0 ? .08F : .22F);
            p.jaw[i] = .06F + Mth.sin(age * .07F + i) * .04F;
        }
        if (e.mode() == 1) intro(e, p, e.age() + partial);
        else if (e.dying()) death(e, p, e.deathAge(partial));
        else {
            int state = e.state();
            float stateAge = e.stateAge(partial);
            Move action = e.action();
            if (action != null && state == RiftWitherEntity.STATE_NONE) move(e, p, action, e.actionAge(partial), e.actionVariant());
            switch (state) {
                case RiftWitherEntity.STATE_ARRIVE -> arrive(p, age, e.age() + partial);
                case RiftWitherEntity.STATE_SPENT -> spent(p, age, stateAge);
                case RiftWitherEntity.STATE_STAGGER -> stagger(p, age, stateAge);
                case RiftWitherEntity.STATE_CRASH -> crash(p, age, stateAge);
                case RiftWitherEntity.STATE_ROAR -> roar(p, age, stateAge);
                default -> {}
            }
            int side = e.sideHead();
            if (side > 0 && action != Move.HORIZON) sideShot(p, side, e.sideAge(partial), e.sideAim());
        }
        apply(e, p, age, partial);
    }

    /** Everything a frame's animation decides, as offsets from the rest pose. */
    private static final class Pose {
        float bob, hipsY, hipsX, chestX, chestY, waistZ, waistX, yokeZ, open, coreScale = 1;
        float[] shoulderX = new float[2], shoulderY = new float[2], shoulderZ = new float[2], elbow = new float[2], wrist = new float[2], curl = new float[2];
        float[] headUp = new float[3], headFwd = new float[3], headSide = new float[3], headPitch = new float[3], headSway = new float[3], jaw = new float[3];
        float[] headShrink = new float[3], neckBend = {3, 2, 2};
        Vec3[] headLook = new Vec3[3];
        float[] aimWeight = new float[3];
        float ribBurst, scaleAll = 1;
        /** The pauldrons stay on until the second phase's roar throws them off ten ticks in. */
        boolean plates, shed;
    }

    private void move(RiftWitherEntity e, Pose p, Move move, float t, int variant) {
        float w = e.actionWindup();
        Vec3 aim = e.aim();
        switch (move) {
            case BITE -> {
                p.headFwd[0] += k(t, 0, 0, w - 3, -5, w + 1, 11, w + 5, 10, w + 15, 0);
                p.headUp[0] += k(t, 0, 0, w - 3, 4, w + 1, -7, w + 5, -6, w + 15, 0);
                p.jaw[0] = k(t, 0, .1F, w - 3, 1.0F, w, 1.1F, w + 1.5F, -.05F, w + 6, .1F);
                p.chestX += k(t, 0, 0, w - 3, -.12F, w + 1, .32F, w + 15, 0);
                p.aimWeight[0] = 1;
                for (int s = 0; s < 2; s++) { p.shoulderX[s] += k(t, 0, 0, w - 3, .3F, w + 1, -.35F, w + 15, 0); p.curl[s] += k(t, 0, 0, w, .5F, w + 15, 0); }
                p.neckBend[0] = k(t, 0, 3, w - 3, 7, w + 1, 0, w + 15, 3);
            }
            case RAKE -> {
                int s = variant & 1;
                float sign = s == 0 ? -1 : 1;
                p.shoulderY[s] += sign * k(t, 0, 0, w - 2, -1.1F, w + 1, 1.25F, w + 6, 1.35F, w + 18, 0);
                p.shoulderZ[s] += k(t, 0, 0, w - 2, .4F, w + 2, .1F, w + 18, 0);
                p.shoulderX[s] += k(t, 0, 0, w - 2, -.85F, w + 2, -.95F, w + 18, 0);
                p.elbow[s] += k(t, 0, 0, w - 2, -1.1F, w + 1, .3F, w + 18, 0);
                p.curl[s] += k(t, 0, 0, w - 2, -.4F, w + 1, .7F, w + 18, 0);
                p.chestY += sign * k(t, 0, 0, w - 2, -.38F, w + 2, .45F, w + 18, 0);
                p.yokeZ += sign * k(t, 0, 0, w - 2, .12F, w + 2, -.1F, w + 18, 0);
                p.chestX += k(t, 0, 0, w - 2, -.08F, w + 2, .2F, w + 18, 0);
                for (int i = 0; i < 3; i++) p.aimWeight[i] = .7F;
            }
            case VERDICT -> {
                float tremble = t < w ? Mth.sin(t * 2.3F) * .03F * t / w : 0;
                float fire = WitherGeometry.VERDICT_FIRE;
                p.chestX += k(t, 0, 0, w - 4, -.28F, w + 1, .1F, w + fire, .05F, w + fire + 12, 0) + tremble;
                p.open += k(t, 0, 0, w, .35F, w + fire, .3F, w + fire + 12, 0);
                for (int i = 0; i < 3; i++) {
                    p.aimWeight[i] = 1;
                    p.headUp[i] += k(t, 0, 0, w - 4, 4, w + 1, 1, w + fire + 12, 0);
                    p.headFwd[i] += k(t, 0, 0, w - 4, -3, w + 1, 3, w + fire, 2.5F, w + fire + 12, 0);
                    p.jaw[i] = k(t, 0, .1F, w - 6, .9F, w + fire, 1.0F, w + fire + 8, .1F) + (t > w && t < w + fire ? Mth.sin(t * 3 + i) * .05F : 0);
                }
                for (int s = 0; s < 2; s++) {
                    float sign = s == 0 ? -1 : 1;
                    p.shoulderZ[s] += k(t, 0, 0, w - 4, .55F, w + fire, .5F, w + fire + 12, 0);
                    p.shoulderX[s] += k(t, 0, 0, w - 4, .25F, w + fire + 12, 0);
                    p.curl[s] += k(t, 0, 0, w - 4, -.5F, w + fire + 12, 0);
                }
            }
            case RAIN -> {
                for (int i = 1; i < 3; i++) {
                    float off = i == 1 ? 0 : 3;
                    p.headPitch[i] += k(t, 0, 0, 6, -1.05F, w + 8, -1.0F, w + 18, 0);
                    p.headUp[i] += k(t, 0, 0, 6, 3, w + 18, 0);
                    float spit = (t + off) % 7;
                    p.jaw[i] = t > 4 && t < w + 4 ? .35F + .7F * (spit < 3 ? spit / 3 : 1 - (spit - 3) / 4) : p.jaw[i];
                }
                p.aimWeight[0] = 1;
                p.jaw[0] = k(t, 0, .1F, w - 4, .5F, w + 6, .1F);
                p.chestX += k(t, 0, 0, 6, -.2F, w + 14, 0);
                for (int s = 0; s < 2; s++) p.shoulderZ[s] += k(t, 0, 0, 6, .3F, w + 14, 0);
            }
            case TETHER -> {
                p.aimWeight[0] = 1;
                p.headFwd[0] += k(t, 0, 0, w - 4, -3, w + 1, 5, w + 3, 5, w + 9, -4, w + 18, 0);
                p.jaw[0] = k(t, 0, .1F, w - 4, 1.1F, w + 2, .9F, w + 9, .3F, w + 16, .1F);
                p.chestX += k(t, 0, 0, w - 4, .1F, w + 3, .2F, w + 9, -.25F, w + 18, 0);
                p.shoulderX[0] += k(t, 0, 0, w - 4, -1.5F, w + 3, -1.6F, w + 9, -.6F, w + 18, 0);
                p.elbow[0] += k(t, 0, 0, w - 4, .2F, w + 9, -1.2F, w + 18, 0);
                p.curl[0] += k(t, 0, 0, w - 4, -.5F, w + 3, .9F, w + 18, 0);
            }
            case PULSE -> {
                float shake = t < w ? Mth.sin(t * 2.7F) * .04F * t / w : 0;
                p.chestX += k(t, 0, 0, w - 2, .32F, w + 1, -.3F, w + 16, 0) + shake;
                p.open += k(t, 0, 0, w - 2, -.25F, w + 1, 1.25F, w + 6, 1.0F, w + 20, 0);
                p.coreScale = 1 + k(t, 0, 0, w - 2, .5F, w + 1, 1.2F, w + 12, 0);
                p.hipsY += k(t, 0, 0, w - 2, -2, w + 1, 2, w + 16, 0);
                for (int s = 0; s < 2; s++) {
                    float sign = s == 0 ? -1 : 1;
                    p.shoulderZ[s] += k(t, 0, 0, w - 2, -.25F, w + 1, 1.05F, w + 16, 0);
                    p.shoulderX[s] += k(t, 0, 0, w - 2, -.6F, w + 1, .2F, w + 16, 0);
                    p.elbow[s] += k(t, 0, 0, w - 2, -1.0F, w + 1, 0, w + 16, 0);
                    p.curl[s] += k(t, 0, 0, w - 2, .9F, w + 1, -.6F, w + 16, 0);
                }
                for (int i = 0; i < 3; i++) {
                    p.headPitch[i] += k(t, 0, 0, w - 2, .3F, w + 1, -.6F, w + 16, 0);
                    p.jaw[i] = k(t, 0, .1F, w - 2, .2F, w + 1, 1.1F, w + 12, .1F);
                }
            }
            case SLAM -> {
                float land = w + WitherGeometry.SLAM_TICKS;
                p.chestX += k(t, 0, 0, w - 4, -.45F, w, -.5F, land, .5F, land + 18, .1F, land + 28, 0);
                p.open += k(t, 0, 0, w - 4, .4F, land, -.1F, land + 28, 0);
                for (int s = 0; s < 2; s++) {
                    float sign = s == 0 ? -1 : 1;
                    p.shoulderX[s] += k(t, 0, 0, w - 4, -2.7F, w, -2.8F, land - 1, -.55F, land + 18, -.5F, land + 28, 0);
                    p.shoulderZ[s] += k(t, 0, 0, w - 4, -.15F, land, .25F, land + 28, 0);
                    p.elbow[s] += k(t, 0, 0, w - 4, -.9F, w, -1.0F, land - 1, .25F, land + 28, 0);
                    p.curl[s] += k(t, 0, 0, w - 4, .9F, land - 1, 1.2F, land + 2, .2F, land + 28, 0);
                }
                for (int i = 0; i < 3; i++) {
                    p.headPitch[i] += k(t, 0, 0, w - 4, -.5F, w, -.55F, land, .45F, land + 28, 0);
                    p.jaw[i] = k(t, 0, .1F, w - 4, .8F, land, 1.0F, land + 10, .2F);
                }
            }
            case LUNGE -> {
                float end = w + WitherGeometry.LUNGE_TICKS;
                p.chestX += k(t, 0, 0, w - 4, .55F, end, .6F, end + 14, 0);
                p.open += k(t, 0, 0, w - 4, -.35F, end, -.3F, end + 14, 0);
                for (int i = 0; i < 3; i++) {
                    p.headUp[i] += k(t, 0, 0, w - 4, -5, end, -6, end + 14, 0);
                    p.headFwd[i] += k(t, 0, 0, w - 4, 5, end, 6, end + 14, 0);
                    p.headPitch[i] += k(t, 0, 0, w - 4, .35F, end + 14, 0);
                    p.jaw[i] = k(t, 0, .1F, w - 4, .7F, end, .9F, end + 10, .1F);
                }
                for (int s = 0; s < 2; s++) {
                    float sign = s == 0 ? -1 : 1;
                    p.shoulderX[s] += k(t, 0, 0, w - 4, .95F, end, 1.05F, end + 14, 0);
                    p.shoulderZ[s] += k(t, 0, 0, w - 4, .35F, end + 14, 0);
                    p.elbow[s] += k(t, 0, 0, w - 4, -.6F, end + 14, 0);
                }
                p.hipsY += k(t, 0, 0, w - 4, -1.5F, end + 14, 0);
            }
            case SPIRES -> {
                float hold = w + 22;
                p.chestX += k(t, 0, 0, w - 8, -.25F, w - 3, .55F, hold, .5F, hold + 14, 0);
                for (int s = 0; s < 2; s++) {
                    float sign = s == 0 ? -1 : 1;
                    p.shoulderX[s] += k(t, 0, 0, w - 8, -2.2F, w - 3, -.95F, hold, -.9F, hold + 14, 0);
                    p.shoulderZ[s] += k(t, 0, 0, w - 8, .2F, w - 3, -.15F, hold + 14, 0);
                    p.elbow[s] += k(t, 0, 0, w - 8, -1.1F, w - 3, .25F, hold + 14, 0);
                    p.curl[s] += k(t, 0, 0, w - 8, -.4F, w - 3, 1.3F, hold, 1.25F, hold + 14, 0);
                }
                p.hipsY += k(t, 0, 0, w - 3, -3, hold, -3, hold + 14, 0);
                for (int i = 0; i < 3; i++) {
                    p.headPitch[i] += k(t, 0, 0, w - 3, -.4F, hold, -.35F, hold + 14, 0);
                    p.jaw[i] = k(t, 0, .1F, w - 3, 1.05F, hold, .9F, hold + 10, .1F);
                }
            }
            case BROOD -> {
                // It grips its own ribcage and heaves; the center head rears, then retches the brood out.
                float heave = t < w ? Mth.sin(t * 1.3F) * .12F * t / w : 0;
                p.chestX += k(t, 0, 0, w - 6, -.35F, w, .45F, w + 6, .35F, w + 18, 0) + heave;
                p.open += k(t, 0, 0, w - 8, 1.1F, w, 1.5F, w + 10, 1.2F, w + 20, 0) + heave * 2;
                p.coreScale += k(t, 0, 0, w, .6F, w + 14, 0);
                for (int s = 0; s < 2; s++) {
                    p.shoulderX[s] += k(t, 0, 0, w - 8, -.55F, w + 14, 0);
                    p.shoulderZ[s] += k(t, 0, 0, w - 8, -.2F, w + 14, 0);
                    p.elbow[s] += k(t, 0, 0, w - 8, -1.35F, w + 14, 0);
                    p.curl[s] += k(t, 0, 0, w - 8, 1.25F, w + 14, 0);
                }
                p.aimWeight[0] = 1;
                p.headUp[0] += k(t, 0, 0, w - 6, 4, w, -2, w + 8, -1, w + 18, 0);
                p.headFwd[0] += k(t, 0, 0, w - 6, -3, w, 7, w + 8, 6, w + 18, 0);
                p.headPitch[0] += k(t, 0, 0, w - 6, -.7F, w, .45F, w + 18, 0);
                p.jaw[0] = k(t, 0, .1F, w - 6, .5F, w - 1, 1.25F, w + 10, 1.2F, w + 16, .1F);
                for (int i = 1; i < 3; i++) { p.jaw[i] = k(t, 0, .1F, w - 4, .7F, w + 10, .1F); p.headPitch[i] += k(t, 0, 0, w - 4, -.4F, w + 14, 0); }
            }
            case HORIZON -> {
                float tremble = t < w ? Mth.sin(t * 3.1F) * .05F * Math.min(1, t / w) : 0;
                p.hipsY += k(t, 0, 0, w - 10, 5, w + 2, 3, w + 22, 0);
                p.chestX += k(t, 0, 0, w - 10, -.4F, w + 1, .35F, w + 22, 0) + tremble;
                p.open += k(t, 0, 0, w - 20, 1.6F, w, 1.7F, w + 2, 2.0F, w + 22, 0);
                p.coreScale = 1 + k(t, 0, 0, w, 1.8F, w + 3, 2.4F, w + 18, 0);
                for (int s = 0; s < 2; s++) {
                    float sign = s == 0 ? -1 : 1;
                    p.shoulderZ[s] += k(t, 0, 0, w - 10, 1.3F, w + 2, 1.45F, w + 22, 0);
                    p.shoulderX[s] += k(t, 0, 0, w - 10, .5F, w + 22, 0);
                    p.curl[s] += k(t, 0, 0, w - 10, -.6F, w + 22, 0);
                }
                for (int i = 0; i < 3; i++) {
                    p.headPitch[i] += k(t, 0, 0, w - 10, -1.0F, w + 1, -.6F, w + 22, 0) + tremble * 2;
                    p.headUp[i] += k(t, 0, 0, w - 10, 3, w + 22, 0);
                    p.jaw[i] = k(t, 0, .1F, w - 12, 1.15F, w + 10, 1.0F, w + 22, .1F);
                }
            }
        }
        if (aim != null) for (int i = 0; i < 3; i++) if (p.aimWeight[i] > 0) p.headLook[i] = aim;
    }

    private void sideShot(Pose p, int head, float t, Vec3 aim) {
        float w = WitherGeometry.HEAD_WINDUP;
        p.headLook[head] = aim;
        p.aimWeight[head] = 1;
        p.headFwd[head] += k(t, 0, 0, w - 3, -3, w + 1, 2, w + 12, 0);
        p.headUp[head] += k(t, 0, 0, w - 3, 2, w + 12, 0);
        p.jaw[head] = k(t, 0, .1F, w - 3, 1.0F, w + 1, .4F, w + 10, .1F);
    }

    private void spent(Pose p, float age, float t) {
        float in = ease(0, 10, t);
        p.hipsY -= 5 * in;
        p.chestX += .38F * in + Mth.sin(age * .12F) * .03F;
        p.open += .65F * in + Mth.sin(age * .15F) * .08F;
        p.coreScale += .25F * in + Mth.sin(age * .3F) * .08F;
        for (int s = 0; s < 2; s++) { p.shoulderX[s] += -.25F * in; p.elbow[s] += .35F * in; p.curl[s] += .4F * in; }
        for (int i = 0; i < 3; i++) { p.headPitch[i] += .55F * in; p.headUp[i] -= 3 * in; p.jaw[i] = .35F * in + p.jaw[i] * (1 - in); }
    }
    private void stagger(Pose p, float age, float t) {
        float in = ease(0, 4, t), sway = Mth.sin(age * .55F);
        p.chestX += (-.3F + sway * .08F) * in;
        p.waistZ += sway * .12F * in;
        p.open += .9F * in;
        for (int s = 0; s < 2; s++) { p.shoulderZ[s] += (.6F + Mth.sin(age * .7F + s) * .25F) * in; p.shoulderX[s] += .4F * in; }
        for (int i = 0; i < 3; i++) { p.headSway[i] += Mth.sin(age * .9F + i * 1.7F) * .45F * in; p.headPitch[i] -= .3F * in; p.jaw[i] = .8F * in; }
    }
    private void crash(Pose p, float age, float t) {
        float hit = k(t, 0, 0, 3, 1, 12, .75F);
        float sway = Mth.sin(age * .2F);
        p.hipsY -= 6 * hit;
        p.chestX += (-.45F + .25F * ease(3, 16, t)) * hit;
        p.waistZ += (.3F + sway * .1F) * hit;
        p.open += 1.0F * hit;
        for (int s = 0; s < 2; s++) { p.shoulderZ[s] += .8F * hit; p.shoulderX[s] += .5F * hit; p.elbow[s] += .5F * hit; }
        for (int i = 0; i < 3; i++) { p.headPitch[i] += (.7F + Mth.sin(age * .25F + i) * .15F) * hit; p.headUp[i] -= 4 * hit; p.headSway[i] += sway * .3F * hit; p.jaw[i] = .55F * hit; }
    }
    private void roar(Pose p, float age, float t) {
        float in = k(t, 0, 0, 6, 1, 32, 1, 40, 0), tremble = Mth.sin(age * 2.2F) * .03F * in;
        p.chestX += -.5F * in + tremble;
        p.open += 1.4F * in;
        p.hipsY += 3 * in;
        p.plates = t <= 10;
        for (int s = 0; s < 2; s++) { p.shoulderZ[s] += 1.2F * in; p.shoulderX[s] += -.5F * in; p.curl[s] -= .6F * in; }
        for (int i = 0; i < 3; i++) { p.headPitch[i] += -.9F * in + tremble * 3; p.jaw[i] = 1.2F * in + .1F; p.headUp[i] += 2 * in; }
    }
    /**
     * The summoning site: it knits together hunched, rears and roars at whoever built it, twists to look at the seam
     * splitting behind it, digs its claws in against the pull with its tail already drawn into the tear, and is yanked
     * backwards through, arms flung out toward the people it is being torn away from.
     */
    private void intro(RiftWitherEntity e, Pose p, float t) {
        float form = ease(0, WitherGeometry.FORM_END, t);
        float rear = ease(WitherGeometry.FORM_END - 2, WitherGeometry.FORM_END + 10, t) * (1 - ease(WitherGeometry.SEAM + 2, WitherGeometry.SEAM + 14, t));
        float look = ease(WitherGeometry.SEAM, WitherGeometry.SPLIT + 4, t) * (1 - ease(WitherGeometry.PULL - 2, WitherGeometry.PULL + 6, t));
        float resist = ease(WitherGeometry.PULL - 4, WitherGeometry.PULL + 8, t) * (1 - ease(WitherGeometry.YANK, WitherGeometry.YANK + 3, t));
        float yank = ease(WitherGeometry.YANK, WitherGeometry.YANK + 5, t);
        float tremble = Mth.sin(t * 2.1F) * .04F * (rear + resist);
        // Knitting together: hunched over, ribs drawn in, arms curled to the chest.
        p.chestX += .6F * (1 - form) + .55F * resist + .35F * yank - .55F * rear + tremble;
        p.hipsY += -3 * (1 - form) - 1.5F * resist;
        p.open = Mth.lerp(form, -.3F, p.open) + 1.2F * rear + .5F * resist + .9F * yank;
        p.coreScale = .4F + .6F * form + .6F * rear + .3F * resist;
        for (int s = 0; s < 2; s++) {
            p.shoulderX[s] += -.4F * (1 - form) - .5F * rear - 1.15F * resist - 1.9F * yank;
            p.shoulderZ[s] += -.2F * (1 - form) + 1.25F * rear + .25F * resist + .55F * yank;
            p.elbow[s] += -1.2F * (1 - form) - .45F * resist - .2F * yank;
            p.curl[s] += .8F * (1 - form) - .5F * rear + 1.25F * resist - .6F * yank;
        }
        Vec3 gate = e.gate();
        for (int i = 0; i < 3; i++) {
            p.headPitch[i] += .5F * (1 - form) - 1.0F * rear + .25F * resist + tremble * 3;
            p.headUp[i] += 2 * rear - 2 * (1 - form);
            p.headFwd[i] += 2.5F * resist + 4 * yank;
            p.jaw[i] = .1F + 1.15F * rear + .95F * resist + 1.1F * yank * (1 - ease(WitherGeometry.YANK + 10, WitherGeometry.THROUGH, t));
            if (look > .3F && gate != null) { p.headLook[i] = gate; p.aimWeight[i] = 1; }
        }
        // Its tail goes first: drawn straight back into the tear.
        float drawn = Math.max(resist, yank);
        for (int i = 0; i < 4; i++) {
            tail[i].xRot -= (i == 0 ? .9F : .25F) * drawn;
            tail[i].yRot *= 1 - drawn;
        }
    }
    /** Out of the arrival tear: falling with arms thrown up, a crouching landing, then a roar at the room. */
    private void arrive(Pose p, float age, float t) {
        float fall = ease(WitherGeometry.ARRIVAL_OPEN - 2, WitherGeometry.ARRIVAL_OPEN + 4, t) * (1 - ease(WitherGeometry.ARRIVAL_LAND - 2, WitherGeometry.ARRIVAL_LAND, t));
        float land = k(t, 0, 0, WitherGeometry.ARRIVAL_LAND - 1, 0, WitherGeometry.ARRIVAL_LAND + 1, 1, WitherGeometry.ARRIVAL_LAND + 6, 1, WitherGeometry.ARRIVAL_LAND + 10, 0);
        float roar = ease(WitherGeometry.ARRIVAL_LAND + 6, WitherGeometry.ARRIVAL_LAND + 12, t) * (1 - ease(WitherGeometry.ARRIVAL_TICKS - 8, WitherGeometry.ARRIVAL_TICKS, t));
        float tremble = Mth.sin(age * 2.2F) * .035F * roar;
        p.chestX += -.2F * fall + .55F * land - .55F * roar + tremble;
        p.hipsY += -4 * land + 2 * roar;
        p.open += .6F * fall + .3F * land + 1.35F * roar;
        p.coreScale += .4F * roar;
        for (int s = 0; s < 2; s++) {
            p.shoulderX[s] += -1.3F * fall - .7F * land - .45F * roar;
            p.shoulderZ[s] += 1.2F * fall + .3F * land + 1.25F * roar;
            p.elbow[s] += -.3F * fall + .45F * land;
            p.curl[s] += -.6F * fall + 1.1F * land - .6F * roar;
        }
        for (int i = 0; i < 3; i++) {
            p.headPitch[i] += .55F * fall + .3F * land - 1.0F * roar + tremble * 3;
            p.headUp[i] += 2 * roar - 2 * land;
            p.jaw[i] = .1F + .5F * fall + 1.2F * roar;
        }
        for (int i = 0; i < 4; i++) tail[i].xRot -= .7F * fall * (i == 0 ? 1 : .4F);
    }
    /**
     * Dying: it thrashes; its ribs burst outward; it rises with its arms thrown wide and its heads screaming at the vault
     * while the star swells; then it curls round the star as everything is drawn back in, and is gone in the blast.
     */
    private void death(RiftWitherEntity e, Pose p, float t) {
        float thrash = k(t, 0, 0, 4, 1, 26, 1, 32, 0);
        float swell = ease(WitherGeometry.RIBS_BURST, WitherGeometry.RIBS_BURST + 16, t) * (1 - ease(WitherGeometry.IMPLODE - 2, WitherGeometry.IMPLODE + 6, t));
        float curl = ease(WitherGeometry.IMPLODE - 2, WitherGeometry.DETONATE - 4, t);
        float tremble = Mth.sin(t * 2.6F) * (.03F + .05F * swell + .06F * curl);
        p.chestX += -.5F * thrash - .45F * swell + .55F * curl + tremble;
        p.open += .6F * thrash + 2.2F * swell - .5F * curl;
        p.ribBurst = ease(WitherGeometry.RIBS_BURST, WitherGeometry.RIBS_BURST + 8, t) * (1 - .8F * curl);
        p.coreScale = 1 + .5F * thrash + 2.4F * ease(WitherGeometry.RIBS_BURST, WitherGeometry.IMPLODE, t) * (1 - curl) + .2F * curl;
        p.hipsY += 1.5F * swell;
        p.shed = true;
        for (int s = 0; s < 2; s++) {
            p.shoulderZ[s] += (.8F + Mth.sin(t * .9F + s * 2) * .6F) * thrash + 1.45F * swell - .5F * curl;
            p.shoulderX[s] += Mth.sin(t * .7F + s) * .7F * thrash - .4F * swell - 1.0F * curl;
            p.elbow[s] += -1.5F * curl;
            p.curl[s] += -.7F * swell + 1.4F * curl;
        }
        for (int i = 0; i < 3; i++) {
            p.headSway[i] += Mth.sin(t * 1.1F + i * 2.2F) * .7F * thrash;
            p.headPitch[i] += -.8F * thrash - 1.05F * swell + 1.0F * curl + tremble * 4;
            p.headUp[i] += 2 * swell - 4 * curl;
            p.jaw[i] = 1.2F * thrash + 1.25F * swell + .3F * curl;
        }
    }

    private void apply(RiftWitherEntity e, Pose p, float age, float partial) {
        hips.y += -p.bob - p.hipsY;
        hips.xRot += p.hipsX;
        waist.xRot += p.waistX + p.chestX * .35F;
        waist.zRot += p.waistZ;
        chest.xRot += p.chestX * .65F;
        chest.yRot += p.chestY;
        yoke.zRot += p.yokeZ;
        yoke.yRot += p.chestY * .4F;
        root.xScale = root.yScale = root.zScale = p.scaleAll;
        core.xScale = core.yScale = core.zScale = Math.max(.05F, p.coreScale);
        core.xRot = age * .05F;
        core.yRot = age * .07F;
        core.zRot = age * .03F;
        float open = Math.max(-.35F, p.open);
        for (int side = 0; side < 2; side++) {
            float sign = side == 0 ? -1 : 1;
            for (int i = 0; i < 4; i++) {
                float rib = open * (.75F + i * .12F);
                ribs[side][i].yRot += -sign * rib * .9F;
                ribs[side][i].zRot += sign * rib * .12F;
                ribB[side][i].yRot += -sign * rib * .35F;
                ribC[side][i].yRot += -sign * rib * .45F;
                if (p.ribBurst > 0) {
                    ribs[side][i].x += sign * p.ribBurst * (6 + i * 2);
                    ribs[side][i].z -= p.ribBurst * 3;
                    ribs[side][i].xRot += p.ribBurst * (i - 1.5F) * .3F;
                }
            }
            pauldrons[side].visible = (e.phase() < 2 || p.plates && e.phase() == 2) && !p.shed;
            shoulders[side].xRot += p.shoulderX[side];
            shoulders[side].yRot += p.shoulderY[side];
            shoulders[side].zRot += (side == 0 ? 1 : -1) * p.shoulderZ[side];
            forearms[side].xRot += p.elbow[side];
            hands[side].xRot += p.wrist[side];
            for (int c = 0; c < 4; c++) {
                float curl = p.curl[side] * (c == 3 ? -.6F : 1);
                claws[side][c].xRot -= curl * .55F;
                clawTips[side][c].xRot -= curl * .7F;
            }
        }
        // Heads: place each pivot, aim the skull, then stretch its neck between the yoke and the head.
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        Vec3 feet = e.getPosition(partial);
        float scale = e.modelScale();
        for (int i = 0; i < 3; i++) {
            ModelPart head = heads[i];
            float[] rest = WitherGeometry.HEAD[i], base = WitherGeometry.NECK_BASE[i];
            float sideSign = i == 0 ? 0 : i == 1 ? -1 : 1;
            float hx = rest[0] - WitherGeometry.YOKE[0] + sideSign * p.headSide[i];
            float hy = -(rest[1] - WitherGeometry.YOKE[1]) - p.headUp[i];
            float hz = -(rest[2] - WitherGeometry.YOKE[2]) - p.headFwd[i];
            float shrink = p.headShrink[i];
            if (shrink > 0) {
                float cx = 0, cy = -(WitherGeometry.CORE[1] - WitherGeometry.YOKE[1]), cz = -(WitherGeometry.CORE[2] - WitherGeometry.YOKE[2]);
                hx = Mth.lerp(shrink, hx, cx); hy = Mth.lerp(shrink, hy, cy); hz = Mth.lerp(shrink, hz, cz);
                head.xScale = head.yScale = head.zScale = Math.max(.02F, 1 - shrink);
            }
            head.x = hx; head.y = hy; head.z = hz;
            float turn = 0, tilt = 0;
            if (p.headLook[i] != null) {
                Vec3 pivot = WitherGeometry.point(feet, bodyYaw, scale, rest[0], rest[1], rest[2]);
                Vec3 to = p.headLook[i].subtract(pivot);
                if (to.lengthSqr() > .05) {
                    float limit = p.aimWeight[i] > 0 ? 1.3F : .75F;
                    turn = Mth.clamp(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-to.x, to.z)) - bodyYaw) * Mth.DEG_TO_RAD, -limit, limit);
                    tilt = Mth.clamp((float) -Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)), -1.1F, .9F);
                    if (p.aimWeight[i] <= 0 && i > 0) { turn *= .5F; tilt *= .5F; }
                }
            }
            head.yRot = turn + p.headSway[i] - p.chestY * .4F;
            head.xRot = tilt + p.headPitch[i];
            jaws[i].xRot = Math.max(-.05F, p.jaw[i]);
            stretch(neckA[i], neckB[i], base[0] - WitherGeometry.YOKE[0], -(base[1] - WitherGeometry.YOKE[1]), -(base[2] - WitherGeometry.YOKE[2]), hx, hy + 2, hz + 1.5F, p.neckBend[i], i);
        }
    }

    /** Lays two neck segments from (bx,by,bz) to (hx,hy,hz), bowed up and back by {@code bend} pixels. */
    private static void stretch(ModelPart a, ModelPart b, float bx, float by, float bz, float hx, float hy, float hz, float bend, int index) {
        float mx = (bx + hx) / 2, my = (by + hy) / 2 - bend, mz = (bz + hz) / 2 + bend * .6F;
        if (index > 0) mx += (index == 1 ? -1 : 1) * bend * .4F;
        aim(a, bx, by, bz, mx, my, mz);
        aim(b, mx, my, mz, hx, hy, hz);
    }
    private static void aim(ModelPart part, float x0, float y0, float z0, float x1, float y1, float z1) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        part.x = x0; part.y = y0; part.z = z0;
        if (length < 1e-3) return;
        dx /= length; dy /= length; dz /= length;
        part.xRot = (float) Math.asin(Mth.clamp(-dz, -1, 1));
        part.zRot = (float) Math.atan2(dx, -dy);
        part.yScale = length / NECK_REST;
    }
}
