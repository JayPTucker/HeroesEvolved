package com.jayptucker.heroesevolved.gifted;

import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;

/**
 * How strong a gifted mob is, the way Apotheosis grades its bosses: each step adds health, hits harder, casts sooner
 * and knows more of its power. Epic and Mythic mobs are mini-bosses: a boss bar, a phase change at half health
 * ("unbound"), a signature move, and followers who answer the call.
 *
 * @param bonusHealth  added to the mob's maximum health
 * @param damage       multiplies every move's damage
 * @param cooldown     multiplies every cooldown (lower is faster)
 * @param moves        how many of the power's ordinary moves it knows (the signature is extra)
 * @param signature    whether it knows its power's signature move (Epic: once unbound; Mythic: from the start)
 * @param followers    how many raiders come when it is unbound
 * @param experience   experience dropped on death
 * @param knockback    knockback resistance (0 to 1)
 * @param speed        added to movement speed, as a fraction
 * @param armor        added armor points
 */
public enum Rarity {
    UNCOMMON("Uncommon", ChatFormatting.GREEN, BossEvent.BossBarColor.GREEN, 20, 1.0F, 1.0F, 1, false, 0, 30, .3, .15, 0),
    RARE("Rare", ChatFormatting.AQUA, BossEvent.BossBarColor.BLUE, 40, 1.2F, .85F, 2, false, 0, 60, .4, .2, 0),
    EPIC("Epic", ChatFormatting.LIGHT_PURPLE, BossEvent.BossBarColor.PURPLE, 80, 1.4F, .75F, 2, true, 1, 140, .5, .25, 4),
    MYTHIC("Mythic", ChatFormatting.GOLD, BossEvent.BossBarColor.YELLOW, 130, 1.7F, .65F, 2, true, 2, 300, .8, .3, 10);

    public final String title;
    public final ChatFormatting color;
    public final BossEvent.BossBarColor bar;
    public final int bonusHealth, moves, followers, experience, armor;
    public final float damage, cooldown;
    public final boolean signature;
    public final double knockback, speed;

    Rarity(String title, ChatFormatting color, BossEvent.BossBarColor bar, int bonusHealth, float damage, float cooldown, int moves,
           boolean signature, int followers, int experience, double knockback, double speed, int armor) {
        this.title = title; this.color = color; this.bar = bar; this.bonusHealth = bonusHealth; this.damage = damage;
        this.cooldown = cooldown; this.moves = moves; this.signature = signature; this.followers = followers;
        this.experience = experience; this.knockback = knockback; this.speed = speed; this.armor = armor;
    }

    /** A mini-boss: boss bar, phase change, signature move. */
    public boolean boss() { return this == EPIC || this == MYTHIC; }

    /** Rolls a rarity no lower than {@code floor}: 55% Uncommon, 28% Rare, 13% Epic, 4% Mythic before the floor. */
    public static Rarity roll(RandomSource random, Rarity floor) {
        int r = random.nextInt(100);
        Rarity rolled = r < 55 ? UNCOMMON : r < 83 ? RARE : r < 96 ? EPIC : MYTHIC;
        return rolled.ordinal() < floor.ordinal() ? floor : rolled;
    }

    public static Rarity of(int ordinal) { return values()[Math.clamp(ordinal, 0, values().length - 1)]; }

    public static Rarity named(String name) {
        for (Rarity r : values()) if (r.name().equalsIgnoreCase(name) || r.title.equalsIgnoreCase(name)) return r;
        return null;
    }
}
