package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pyrokinesis's three Ultimates; {@code PyroArt} draws them from these beats. */
public final class PyroUltimates {
    private PyroUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(RisingSun::new, 8);
    public static final Ultimate DEFENSE = Ultimate.defense(LivingFlame::new);
    public static final Ultimate SUPPORT = Ultimate.support(BonfireVigil::new);

    private static final String POWER = "pyrokinesis";

    // ------------------------------------------------------------------
    // Offense: Rising Sun
    // ------------------------------------------------------------------

    /**
     * A jet-step in and a rising knee lift the foe off its feet; the firebender hangs beside it on their boot jets and
     * works it over with six fire-wrapped blows, then jets up above it, flips, and brings a blue-white axe kick down on
     * it. Where it lands, a dome of fire blooms and a sun rises out of the crater.
     */
    public static final class RisingSun extends Run {
        public static final int KNEE = 6, FLURRY = 12, BLOWS = 6, EVERY = 4, RISE = 38, WHEEL = 50, AXE = 62, IMPACT = 66,
                LANDED = 82, TICKS = 120;
        public static final double LIFT = 3.2, HANG = 2.4, ABOVE = 7.5;

        public RisingSun(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "sunfall", foe); }

        @Override protected int length() { return TICKS; }

        private Vec3 front() { return around(base, -1.3, 0, 0); }

        /** Where the firebender hangs beside the foe during the flurry, and where they hover above it for the kick. */
        public static Vec3 hang(Vec3 base, Vec3 facing) { return base.subtract(facing.scale(1.25)).add(0, HANG, 0); }

        public static Vec3 above(Vec3 base, Vec3 facing) { return base.subtract(facing.scale(.6)).add(0, ABOVE, 0); }

        /** The foe's height over its start by tick. */
        public static double height(float t) {
            if (t < KNEE) return 0;
            if (t < AXE) return LIFT * Math.min(1, (t - KNEE) / 6F);
            return LIFT * Math.max(0, 1 - (t - AXE) / (float) (IMPACT - AXE));
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.FIRE_STEP, 6);
            piece(Piece.PYRO_ULT_SUN, origin, base, 1);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.2, 2.6, 1.8, d * .6, 0, 1.2)
                    .key(KNEE + 2, d - 3.4, 3.6, .6, d, 0, 2)
                    .watch(FLURRY + 10, d - 3.2, 4.6, 2.6, foe)
                    .watch(RISE - 2, d - 4, -4.6, 3.2, foe)
                    .key(WHEEL, d - 6, -5, 1.2, d, 0, 6.5)
                    .key(AXE + 1, d - 7.5, 4.5, 6.5, d, 0, 1.5)
                    .key(IMPACT + 10, d - 9, 6, 3, d, 0, 2.2)
                    .key(TICKS - 14, d - 11, 7, 3.6, d, 0, 2.8)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age < KNEE) glide(origin.lerp(front(), (age - 1) / (KNEE - 1F)), origin.lerp(front(), age / (KNEE - 1F)));
            if (age == KNEE) {
                still();
                pose(ArmPose.FIRE_KICK, 8);
                PyroService.burn(player, foe, 2, 2);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, .8F);
                sound(foeCentre(), SoundEvents.BLAZE_SHOOT, 1, .7F);
            }
            if (age >= KNEE) lift(height(age));
            if (age > KNEE && age <= FLURRY) {
                if (age == KNEE + 1) hover(true);
                float k0 = (age - 1 - KNEE) / (float) (FLURRY - KNEE), k1 = (age - KNEE) / (float) (FLURRY - KNEE);
                glide(front().lerp(hang(base, facing), k0), front().lerp(hang(base, facing), k1));
            }
            if (age >= FLURRY && age < FLURRY + BLOWS * EVERY && (age - FLURRY) % EVERY == 0) {
                int i = (age - FLURRY) / EVERY;
                still();
                face(foeCentre());
                pose(i % 2 == 0 ? ArmPose.FIRE_JAB : ArmPose.FIRE_CROSS, 4);
                PyroService.burn(player, foe, 1.5F, 1);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.FIRECHARGE_USE, .9F, 1.1F + i * .08F);
            }
            if (age > RISE && age <= WHEEL) {
                float k0 = (age - 1 - RISE) / (float) (WHEEL - RISE), k1 = (age - RISE) / (float) (WHEEL - RISE);
                glide(hang(base, facing).lerp(above(base, facing), k0 * k0 * (3 - 2 * k0)), hang(base, facing).lerp(above(base, facing), k1 * k1 * (3 - 2 * k1)));
                if (age == RISE + 1) {
                    pose(ArmPose.FIRE_DIVE, 12);
                    sound(player.position(), SoundEvents.FIRECHARGE_USE, 1.2F, .5F);
                }
            }
            if (age == WHEEL) {
                still();
                face(foeCentre());
                pose(ArmPose.FIRE_FLIP, 12);
                sound(player.position(), SoundEvents.BLAZE_AMBIENT, 1, .6F);
            }
            if (age == AXE) {
                pose(ArmPose.FIRE_KICK, 6);
                PyroService.burn(player, foe, 4, 3);
                sound(foeCentre(), SoundEvents.PLAYER_ATTACK_STRONG, 1.5F, .6F);
            }
            if (age == IMPACT) {
                PyroService.burn(player, foe, 8, 5);
                Vec3 at = base;
                for (LivingEntity t : others(at, 5)) {
                    PyroService.burn(player, t, 6, 4);
                    shove(t, at, 1.1, .5);
                }
                sound(at, SoundEvents.GENERIC_EXPLODE.value(), 1.6F, .8F);
                sound(at, SoundEvents.BLAZE_SHOOT, 1.4F, .5F);
                shake(at, 24, .55F, 12);
                release();
            }
            if (age > IMPACT && age <= LANDED) {
                float k0 = (age - 1 - IMPACT) / (float) (LANDED - IMPACT), k1 = (age - IMPACT) / (float) (LANDED - IMPACT);
                Vec3 down = front().subtract(facing.scale(.8));
                glide(above(base, facing).lerp(down, k0), above(base, facing).lerp(down, k1));
            }
            if (age == LANDED) { hover(false); still(); }
        }

        @Override protected void tidy(boolean broken) { hover(false); }
    }

    // ------------------------------------------------------------------
    // Defense: Living Flame
    // ------------------------------------------------------------------

    /**
     * For twelve seconds the firebender is flame: fire can't touch them, every blow is two-fifths lighter, shots burn
     * up before they arrive, and anyone who strikes them catches fire and is thrown back by a jet. When it ends, the
     * heat they took in goes out of them in a ring of fire.
     */
    public static final class LivingFlame extends Run {
        public static final int TICKS = 240, INTRO = 22;
        private int banked;
        private final Map<UUID, Integer> kicked = new HashMap<>();

        public LivingFlame(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "ember_shroud", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.FIRE_SUN, 24);
            heroShot(INTRO, .5);
            piece(Piece.PYRO_ULT_FLAME, player.position(), player.position(), 1, player, 0);
            StyleOps.effect(player, MobEffects.FIRE_RESISTANCE, TICKS + 20, 0);
            sound(player.position(), SoundEvents.BLAZE_SHOOT, 1.4F, .5F);
            sound(player.position(), SoundEvents.FIRECHARGE_USE, 1.2F, .4F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.PYRO_ULT_FLAME, player.position(), player.position(), 1, player, age / 20);
            if (age % 20 == 10) sound(player.position(), SoundEvents.FIRE_AMBIENT, 1, .8F);
            player.clearFire();
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, 2.6)) {
                piece(Piece.PYRO_ULT_FLARE, body, shot.position(), .4F, player, 1);
                shot.discard();
                banked++;
                sound(shot.position(), SoundEvents.FIRE_EXTINGUISH, .6F, 1.6F);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            if (event.getSource().is(DamageTypeTags.IS_FIRE)) { event.setCanceled(true); return; }
            event.setAmount(event.getAmount() * .6F);
            banked++;
            if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player && StyleOps.isFoe(player, attacker)
                    && attacker.distanceToSqr(player) < 25) {
                int last = kicked.getOrDefault(attacker.getUUID(), -100);
                if (age - last < 20) return;
                kicked.put(attacker.getUUID(), age);
                attacker.igniteForSeconds(4);
                PyroService.burn(player, attacker, 2, 4);
                shove(attacker, player.position(), 1.3, .45);
                pose(ArmPose.FIRE_KICK, 6);
                piece(Piece.PYRO_ULT_FLARE, player.getBoundingBox().getCenter(), attacker.getBoundingBox().getCenter(), 1, player, 0);
                sound(attacker.position(), SoundEvents.BLAZE_SHOOT, 1, 1.2F);
            }
        }

        @Override protected void finish() {
            Vec3 at = player.position();
            float damage = Math.min(10, 3 + banked * .5F);
            for (LivingEntity t : StyleOps.foes(player, at, 5)) {
                PyroService.burn(player, t, damage, 4);
                shove(t, at, 1, .4);
            }
            piece(Piece.PYRO_ULT_NOVA, at, at, 5, null, banked);
            sound(at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.1F);
            shake(at, 16, .35F, 8);
        }

        /** Blows and shots taken in, for tests. */
        public int banked() { return banked; }
    }

    // ------------------------------------------------------------------
    // Support: Bonfire Vigil
    // ------------------------------------------------------------------

    /**
     * A great bonfire roars up at the firebender's feet for fifteen seconds. Friends within ten blocks of it heal, are
     * thawed and cleansed, can't burn, and take an extra layer of warmth the first time; a friend who falls low is
     * wrapped in a cocoon of flame that heals them once. Foes who come close to the fire are driven back, burning.
     */
    public static final class BonfireVigil extends Run {
        public static final int TICKS = 300, INTRO = 24;
        public static final double RADIUS = 10, HEAT = 4;
        private Vec3 fire;
        private final Set<UUID> warmed = new HashSet<>(), cocooned = new HashSet<>();

        public BonfireVigil(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "hearthfire", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            fire = StyleOps.ground(level, player.position().add(facing.scale(1.5)).add(0, .5, 0));
            cue();
            pose(ArmPose.FIRE_FORGE, 12);
            heroShot(INTRO, 2);
            piece(Piece.PYRO_ULT_BONFIRE, fire, fire, (float) RADIUS, null, 0);
            sound(fire, SoundEvents.BLAZE_SHOOT, 1.4F, .4F);
            sound(fire, SoundEvents.FIRECHARGE_USE, 1.2F, .6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.PYRO_ULT_BONFIRE, fire, fire, (float) RADIUS, null, age / 20);
                sound(fire, SoundEvents.CAMPFIRE_CRACKLE, 1.6F, .7F);
            }
            if (age % 10 != 0) return;
            for (LivingEntity friend : StyleOps.friends(player, fire, RADIUS)) {
                StyleOps.heal(friend, .9F);
                friend.clearFire();
                friend.setTicksFrozen(0);
                StyleOps.effect(friend, MobEffects.FIRE_RESISTANCE, 60, 0);
                if (warmed.add(friend.getUUID())) {
                    StyleOps.cleanse(friend);
                    StyleOps.effect(friend, MobEffects.ABSORPTION, TICKS, 1);
                }
                if (friend.getHealth() < friend.getMaxHealth() * .3F && cocooned.add(friend.getUUID())) {
                    StyleOps.heal(friend, 8);
                    StyleOps.cleanse(friend);
                    piece(Piece.PYRO_ULT_COCOON, friend.position(), friend.position(), friend.getBbHeight(), friend, 0);
                    sound(friend.position(), SoundEvents.BLAZE_SHOOT, 1, 1.3F);
                }
            }
            if (age % 20 == 0) for (LivingEntity t : StyleOps.foes(player, fire, HEAT)) {
                PyroService.burn(player, t, 1, 2);
                shove(t, fire, .9, .3);
            }
        }

        public Vec3 fire() { return fire; }
    }
}
