package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.shapeshift.ShapeshiftForm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import org.jetbrains.annotations.Nullable;

/**
 * Instinct: each form's own attack (docs/shapeshifting.md, "Instinct for each form"). Its ordinal rides in the
 * {@code SHIFT_INSTINCT} piece's scale, so append new moves at the end. Costs are stamina; cooldowns are ticks, kept per
 * move so switching forms doesn't wait on another form's attack.
 */
public enum InstinctMove {
    /** Your own body: the arm lengthens into a reaching strike. */
    LUNGE(10, 16),
    /** Any form without a move of its own: a strike at the form's own attack damage. */
    STRIKE(6, 16),
    GRAB(14, 80), BONE(6, 14), WEB(10, 60), DETONATE(25, 200), BLINK(14, 30), FIREBALLS(14, 50), POUNCE(10, 30),
    BUCK(10, 40), FLING(14, 50), CHARGE(18, 80), HEX(12, 60), FANGS(18, 70), SONIC(30, 120), GAZE(20, 100),
    SKULLS(16, 40), FAMILIAR(20, 400),
    /** Apex Form's ground slam: everything within five blocks is thrown. */
    SLAM(20, 60);

    public final int cost, cooldown;

    InstinctMove(int cost, int cooldown) { this.cost = cost; this.cooldown = cooldown; }

    private static final InstinctMove[] VALUES = values();

    public static @Nullable InstinctMove byId(int id) { return id >= 0 && id < VALUES.length ? VALUES[id] : null; }

    /** The move a form gives: {@code example} is a living example of a creature's form, null for a person or your own body. */
    public static InstinctMove of(@Nullable ShapeshiftForm form, @Nullable LivingEntity example) {
        if (form == null) return LUNGE;
        if (form.person()) return FAMILIAR;
        if (example == null) return STRIKE;
        if (example instanceof Zombie) return GRAB;
        if (example instanceof AbstractSkeleton) return BONE;
        if (example instanceof Spider) return WEB;
        if (example instanceof Creeper) return DETONATE;
        if (example instanceof EnderMan) return BLINK;
        if (example instanceof Blaze || example instanceof Ghast) return FIREBALLS;
        if (example instanceof Wolf) return POUNCE;
        if (example instanceof AbstractHorse) return BUCK;
        if (example instanceof IronGolem) return FLING;
        if (example instanceof Ravager) return CHARGE;
        if (example instanceof Witch) return HEX;
        if (example instanceof Evoker) return FANGS;
        if (example instanceof Warden) return SONIC;
        if (example instanceof Guardian) return GAZE;
        if (example instanceof WitherBoss) return SKULLS;
        return STRIKE;
    }

    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
}
