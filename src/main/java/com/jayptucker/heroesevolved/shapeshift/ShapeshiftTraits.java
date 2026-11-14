package com.jayptucker.heroesevolved.shapeshift;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a form brings besides its stats: its gifts and its weaknesses (docs/shapeshifting.md, "Traits"). Read from a
 * living example of the form, so both sides agree and a modded creature gets the traits of the vanilla class it extends.
 * The weaknesses matter: they keep a strong form from always being the best one.
 */
public final class ShapeshiftTraits {
    public enum Trait {
        /** Climbs walls by walking into them (a spider). */
        CLIMB,
        /** Sees in the dark. */
        NIGHT_EYES,
        /** Falls gently and takes no fall damage (a chicken, a bat). */
        SLOW_FALL,
        /** Slow, fluttering flight (a bat, a parrot, a bee). */
        FLUTTER,
        /** Real flight (a wither, a ghast, a phantom). */
        FLIGHT,
        /** Swims at full speed. */
        SWIM,
        /** Holds its breath far longer. */
        DEEP_BREATH,
        /** Breathes water. */
        GILLS,
        /** ...and runs out of air on land (a fish, a guardian). */
        DRIES_OUT,
        /** Instant damage heals and healing hurts; Smite cuts deeper. */
        UNDEAD,
        /** Burns in sunlight. */
        SUN_BURN,
        /** Can't be set alight or burned. */
        FIRE_PROOF,
        /** Water and rain hurt. */
        WATER_HURTS,
        /** Snowballs hurt. */
        SNOWBALL_HURTS,
        /** Arrows and other shots pass harmlessly (an enderman). */
        PROJECTILE_PROOF,
        /** Creepers keep their distance (a cat). */
        CREEPER_REPELLENT,
        /** Hurt creatures nearby glow (a wolf's nose). */
        SCENT,
        /** Moving creatures nearby glow, even through walls (a warden). */
        VIBRATIONS,
        /** Almost blind (a warden). */
        BLIND
    }

    private ShapeshiftTraits() {}

    /** The traits of a form, read from a living example of it; none for a person or your own body. */
    public static Set<Trait> of(@Nullable LivingEntity e) {
        EnumSet<Trait> t = EnumSet.noneOf(Trait.class);
        if (e == null || e.getType() == EntityType.PLAYER) return t;
        if (e instanceof Spider) { t.add(Trait.CLIMB); t.add(Trait.NIGHT_EYES); }
        if (e instanceof Chicken || e instanceof Bat || e instanceof Parrot) t.add(Trait.SLOW_FALL);
        if (e instanceof Bat || e instanceof Parrot || e instanceof Bee || e instanceof Allay || e instanceof Vex) t.add(Trait.FLUTTER);
        if (e instanceof WitherBoss || e instanceof Ghast || e instanceof Phantom) t.add(Trait.FLIGHT);
        if (e instanceof AbstractFish || e instanceof Guardian || e instanceof Squid || e instanceof Tadpole) {
            t.add(Trait.SWIM); t.add(Trait.GILLS); t.add(Trait.DRIES_OUT);
        }
        if (e instanceof Dolphin || e instanceof Turtle || e instanceof Axolotl) { t.add(Trait.SWIM); t.add(Trait.DEEP_BREATH); }
        if (e instanceof Drowned) { t.add(Trait.SWIM); t.add(Trait.GILLS); }
        if (e.getType().is(EntityTypeTags.INVERTED_HEALING_AND_HARM)) t.add(Trait.UNDEAD);
        if ((e instanceof Zombie && !(e instanceof net.minecraft.world.entity.monster.Husk) && !(e instanceof net.minecraft.world.entity.monster.ZombifiedPiglin))
                || (e instanceof AbstractSkeleton && !(e instanceof WitherSkeleton)) || e instanceof Phantom) t.add(Trait.SUN_BURN);
        if (e.fireImmune()) t.add(Trait.FIRE_PROOF);
        if (e instanceof Blaze || e instanceof Strider || e instanceof MagmaCube || e instanceof EnderMan) t.add(Trait.WATER_HURTS);
        if (e instanceof Blaze) t.add(Trait.SNOWBALL_HURTS);
        if (e instanceof EnderMan) t.add(Trait.PROJECTILE_PROOF);
        if (e instanceof Cat || e instanceof Ocelot) t.add(Trait.CREEPER_REPELLENT);
        if (e instanceof Wolf) t.add(Trait.SCENT);
        if (e instanceof Warden) { t.add(Trait.VIBRATIONS); t.add(Trait.BLIND); }
        return t;
    }

    /** Whether a form keeps knockback off entirely (a golem, a ravager): their attributes already say so. */
    public static boolean immovable(LivingEntity e) { return e instanceof IronGolem || e instanceof Ravager; }
}
