package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Telekinesis's finisher, Cut Strings: you raise a hand with the fingers spread and thin strings run from each fingertip
 * to the foe's limbs and head; you lift it off the ground and make it dance (turns, a jerk, a hop) while your fingers
 * play; you draw a finger across the strings, they snap one by one, and it drops in a heap like a dropped puppet. A mob
 * holding a weapon drops it, and it floats to your hand. Grip is ready again.
 */
public final class CutStrings {
    public static final int LIFT = 6, DANCE = 10, CUT = 20, LAND = 23, TICKS = 30;
    public static final double HEIGHT = 1.1;
    private static final ResourceLocation GRIP = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "telekinesis_grip");

    private CutStrings() {}

    /** How high the puppet hangs and how it is turned at {@code t}, shared with the strings' drawing. */
    public static double lift(float t) {
        if (t < LIFT) return 0;
        if (t < DANCE) return HEIGHT * smooth((t - LIFT) / (DANCE - LIFT));
        if (t < CUT) return HEIGHT + .22 * Math.max(0, Math.sin((t - DANCE) * Math.PI / 3.5));
        if (t < LAND) return HEIGHT * (1 - smooth((t - CUT) / (LAND - CUT)));
        return 0;
    }

    public static float turn(float t) {
        if (t < DANCE || t >= CUT) return 0;
        return 40 * Mth.sin((t - DANCE) * .9F);
    }

    private static double smooth(double k) { k = Mth.clamp(k, 0, 1); return k * k * (3 - 2 * k); }

    private record Puppet(float yaw) {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TELEKINESIS_ID; }
        @Override public String id() { return "cut_strings"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }
        @Override public boolean holds() { return false; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            run.state = new Puppet(foe.getYRot());
            Takedowns.show(run.level(), Piece.FINISH_STRINGS, p.getEyePosition(), run.anchor, Math.max(.6F, foe.getBbHeight()), foe, p.getId());
            ArmPosePayload.show(p, ArmPose.STYLE_LIFT, CUT);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, 1.4F);
            // Disarm: a mob's weapon is torn from its hand and floats to yours.
            if (foe instanceof Mob mob && !mob.getMainHandItem().isEmpty()) {
                ItemStack weapon = mob.getMainHandItem().copy();
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                ItemEntity item = new ItemEntity(run.level(), foe.getX(), foe.getEyeY() - .3, foe.getZ(), weapon);
                Vec3 to = p.getEyePosition().subtract(item.position());
                item.setDeltaMovement(to.scale(.12).add(0, .25, 0));
                item.setPickUpDelay(4);
                run.level().addFreshEntity(item);
            }
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Puppet puppet)) return;
            LivingEntity foe = run.target;
            int age = run.age();
            if (age < LAND) {
                Takedowns.move(foe, run.anchor.add(0, lift(age), 0));
                float yaw = puppet.yaw + turn(age);
                foe.setYRot(yaw);
                foe.setYBodyRot(yaw);
                foe.setYHeadRot(yaw + turn(age) * .5F);
                foe.setXRot(age >= DANCE && age < CUT ? 25 * Mth.sin(age * 1.3F) : 0);
            }
            if (age == LIFT) Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_CHIME, 1F, .8F);
            if (age == 13 || age == 17) Takedowns.flinch(foe);
            if (age == CUT) {
                ArmPosePayload.show(run.player, ArmPose.STYLE_SWAT, 8);
                for (int i = 0; i < 5; i++)
                    Takedowns.sound(run, run.anchor.add(0, 1.5, 0), SoundEvents.TRIPWIRE_DETACH, .7F, 1.3F + i * .1F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .5, .3);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1F, .6F);
        }

        @Override public void reward(Run run) { CooldownService.reset(run.player, GRIP); }
    };
}
