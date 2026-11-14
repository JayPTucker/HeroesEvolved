package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * What a form is: which Power Level may learn it, which kin ignore its wearer, how big it is and what body it gives.
 * Every rule here reads a living example of the form, so a mob from another mod is sorted by the same tests
 * (its health, size, hostility and class) as a vanilla one. See docs/shapeshifting.md.
 */
public final class ShapeshiftForms {
    /** The Power Level at which each band of forms opens. People open with the odd bodies, at Level 6. */
    public static final int ANIMALS = 1, COMMON = 3, ODD = 6, LARGE = 10, MINIBOSS = 15, BOSS = 20, NEVER = Integer.MAX_VALUE;

    /** How much a form costs to take and to hold. */
    public enum Size {
        SMALL(8, 0), LARGE(16, 1), BOSS(30, 3);
        public final int cost, drainPerSecond;
        Size(int cost, int drainPerSecond) { this.cost = cost; this.drainPerSecond = drainPerSecond; }
    }

    /**
     * The body a form gives: the values its wearer's attributes become. {@code speed} is already mapped onto a
     * player's range; {@code health} is the form's own, before the tier cap.
     */
    public record Body(float health, double attack, double armor, double toughness, double knockback,
                       double speed, double step, double jump) {}

    /** A person's body: a plain player's, whatever powers or gear the person has. */
    public static final Body PERSON = new Body(20, 1, 0, 0, 0, 0.1, 0.6, 0.42);

    /** One studied example per type, made once per server and kept: forms are sorted by an instance, not a type. */
    private static final Map<EntityType<?>, LivingEntity> EXAMPLES = new HashMap<>();

    private ShapeshiftForms() {}

    public static ResourceLocation id(EntityType<?> type) { return BuiltInRegistries.ENTITY_TYPE.getKey(type); }

    public static @Nullable EntityType<?> type(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
    }

    /** A fresh, unspawned example of {@code type} with its default attributes, or null if it isn't a creature. */
    public static @Nullable LivingEntity example(ServerLevel level, EntityType<?> type) {
        LivingEntity cached = EXAMPLES.get(type);
        if (cached != null) return cached;
        Entity made;
        try { made = type.create(level); } catch (RuntimeException e) { return null; }
        if (!(made instanceof LivingEntity living)) return null;
        EXAMPLES.put(type, living);
        return living;
    }

    public static void clear() { EXAMPLES.clear(); }

    /** The Power Level needed to learn this creature's form, or {@link #NEVER}. */
    public static int requiredLevel(LivingEntity e) {
        if (e instanceof Player) return ODD;
        if (!(e instanceof Mob)) return NEVER;
        if (e instanceof EnderDragon) return NEVER;
        ResourceLocation id = id(e.getType());
        if (HeroesEvolved.MOD_ID.equals(id.getNamespace()))
            return id.getPath().equals("sylar") || id.getPath().equals("elle_bishop") ? BOSS : MINIBOSS;
        // Named creatures first, so a tall enderman or a 500-health warden lands in its own band, not a generic one.
        if (e instanceof Warden || e instanceof ElderGuardian) return MINIBOSS;
        if (e instanceof WitherBoss || e.getMaxHealth() > 100) return BOSS;
        if (e instanceof IronGolem || e instanceof Ravager || e instanceof Evoker || e instanceof Witch
                || e instanceof Hoglin || e instanceof Zoglin || e instanceof PiglinBrute || e instanceof Ghast) return LARGE;
        if (e instanceof EnderMan || e instanceof AbstractPiglin || e instanceof Blaze || e instanceof Guardian
                || e instanceof Phantom || e instanceof AbstractIllager || e instanceof Shulker || e instanceof Vex
                || e instanceof AbstractGolem) return ODD;
        // Anything else, from any mod, is sorted by its body.
        if (e.getMaxHealth() > 40 || e.getBbWidth() > 1.4F || e.getBbHeight() > 2.6F) return LARGE;
        if (e instanceof Enemy || e instanceof AbstractVillager || e instanceof AbstractHorse || e instanceof Bee
                || e instanceof Goat || e instanceof PolarBear) return COMMON;
        return ANIMALS;
    }

    public static Size size(LivingEntity e) {
        int level = requiredLevel(e);
        if (level >= BOSS && (e.getBbHeight() > 2.2F || e.getBbWidth() > 1.2F || e.getMaxHealth() > 100)) return Size.BOSS;
        if (e instanceof Warden || e instanceof ElderGuardian) return Size.BOSS;
        if (level == LARGE) return Size.LARGE;
        return Size.SMALL;
    }

    /**
     * The family that treats a wearer of this form as one of its own. Every zombie variant is kin to every other, and so
     * are skeletons, raiders (illagers, witches and ravagers), piglins, spiders, slimes and guardians. Anything else is
     * kin only to its own type.
     */
    public static String kin(Entity e) {
        if (e instanceof Zombie || e instanceof Zoglin) return "zombie";
        if (e instanceof AbstractSkeleton) return "skeleton";
        if (e instanceof Raider) return "raider";
        if (e instanceof AbstractPiglin) return "piglin";
        if (e instanceof Spider) return "spider";
        if (e instanceof Slime) return "slime";
        if (e instanceof Guardian) return "guardian";
        return id(e.getType()).toString();
    }

    /** The body this creature gives whoever takes its form. */
    public static Body body(LivingEntity e) {
        double speed = value(e, Attributes.MOVEMENT_SPEED, 0.25);
        double mapped;
        if (e instanceof AbstractVillager) mapped = 0.1;
        else if (e instanceof WaterAnimal) mapped = 0.07;
        else mapped = Mth.clamp(0.1 * speed / 0.25, 0.07, 0.13);
        return new Body(e.getMaxHealth(), value(e, Attributes.ATTACK_DAMAGE, 1), value(e, Attributes.ARMOR, 0),
                value(e, Attributes.ARMOR_TOUGHNESS, 0), value(e, Attributes.KNOCKBACK_RESISTANCE, 0), mapped,
                value(e, Attributes.STEP_HEIGHT, 0.6), Mth.clamp(value(e, Attributes.JUMP_STRENGTH, 0.42), 0.3, 0.9));
    }

    /** The highest max health a shapeshifter of this tier may wear: 20 + 10 per tier, 100 once Evolved. */
    public static float healthCap(int tier, boolean evolved) { return evolved ? 100 : 20 + 10 * tier; }

    /** How many forms the library holds: 3 + 2 per tier, 15 once Evolved. */
    public static int capacity(int tier, boolean evolved) { return evolved ? 15 : 3 + 2 * tier; }

    private static double value(LivingEntity e, Holder<Attribute> attribute, double fallback) {
        return e.getAttributes().hasAttribute(attribute) ? e.getAttributeValue(attribute) : fallback;
    }
}
