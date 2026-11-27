package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Empathic Mimicry's three Ultimates, each sung in the powers the empath remembers (the same voices as Chorus: every
 * one remembered, filled up to three from the easiest echoes); {@code MimicArt} draws them from these beats.
 */
public final class MimicUltimates {
    private MimicUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(EverythingAtOnce::new, 10);
    public static final Ultimate DEFENSE = Ultimate.defense(MirrorSoul::new);
    public static final Ultimate SUPPORT = Ultimate.support(SharedGift::new);

    private static final String POWER = "empathic_mimicry";

    /** What a power leaves on a foe when it is borrowed against it. */
    private static void rider(ServerPlayer p, LivingEntity t, String power) {
        switch (power) {
            case "pyrokinesis" -> t.igniteForSeconds(3);
            case "cryokinesis" -> com.jayptucker.heroesevolved.cryo.CryoService.chill(t, 60, p);
            case "electrokinesis" -> com.jayptucker.heroesevolved.weapons.Jamming.jam(t, 60, p);
            case "hydrokinesis" -> com.jayptucker.heroesevolved.hydro.HydroService.soak(t, 120);
            case "quake", "telekinesis", "phasing" -> StyleOps.push(t, new Vec3(0, .6, 0));
            case "sonic_resonance" -> StyleOps.effect(t, MobEffects.WEAKNESS, 80, 0);
            case "telepathy" -> StyleOps.effect(t, MobEffects.CONFUSION, 80, 0);
            case "time_manipulation", "superspeed" -> StyleOps.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
            default -> StyleOps.effect(t, MobEffects.GLOWING, 120, 0);
        }
    }

    /** What a power gives a friend when it is shared. */
    private static Holder<MobEffect> boon(String power) {
        return switch (power) {
            case "pyrokinesis", "radiation" -> MobEffects.FIRE_RESISTANCE;
            case "superspeed", "shapeshifting" -> MobEffects.MOVEMENT_SPEED;
            case "flight", "telekinesis" -> MobEffects.JUMP;
            case "hydrokinesis" -> MobEffects.DOLPHINS_GRACE;
            case "regeneration" -> MobEffects.REGENERATION;
            case "electrokinesis", "time_manipulation" -> MobEffects.DIG_SPEED;
            case "quake", "power_amplification", "sonic_resonance" -> MobEffects.DAMAGE_BOOST;
            case "telepathy", "vibe" -> MobEffects.NIGHT_VISION;
            case "matter_assembly", "invisibility" -> MobEffects.ABSORPTION;
            default -> MobEffects.DAMAGE_RESISTANCE;
        };
    }

    // ------------------------------------------------------------------
    // Offense: Everything at Once
    // ------------------------------------------------------------------

    /**
     * Gold threads take hold of the foe and lift it. The empath runs through every power he remembers, one strike each,
     * each in its own colour and with its own touch (a burn, a chill, a jam, a soaking...). Then all the colours braid
     * into one rope of light and he drives it through the foe.
     */
    public static final class EverythingAtOnce extends Run {
        public static final int HOLD = 10, FIRST = 14, EVERY = 12, BRAID = 76, LANCE = 92, TICKS = 120;
        private final List<String> voices;

        public EverythingAtOnce(ServerPlayer p, int tier, LivingEntity foe) {
            super(p, tier, POWER, "prismatic_overflow", foe);
            voices = Chorus.voices(p);
        }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.MIMIC_TOUCH, HOLD + 2);
            piece(Piece.MIMIC_ULT_ALL, origin, base, foe.getBbHeight(), player, foeId());
            piece(Piece.MIMIC_ULT_VOICES, origin, base, foe.getBbHeight(), player, Chorus.pack(voices));
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.4, 2.4, 1.8, d, 0, 1.4)
                    .key(FIRST + EVERY, d * .5, 4, 2.2, d * .5, 0, 1.6)
                    .key(BRAID - 4, d * .5, -4.2, 2.4, d * .5, 0, 1.6)
                    .key(LANCE + 2, -2.6, 1.8, 2, d, 0, 1.6)
                    .key(TICKS - 14, d - 5, 5, 3, d, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age <= LANCE) lift(1.1 * Math.min(1, age / (float) HOLD));
            int n = Math.min(5, voices.size());
            if (age >= FIRST && age < FIRST + n * EVERY && (age - FIRST) % EVERY == 0) {
                int i = (age - FIRST) / EVERY;
                pose(i % 2 == 0 ? ArmPose.MIMIC_SHIFT : ArmPose.MIMIC_TOUCH, 6);
                zap(foe, 2.5F);
                rider(player, foe, voices.get(i));
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.AMETHYST_CLUSTER_HIT, 1.2F, .8F + i * .15F);
                sound(foeCentre(), SoundEvents.ILLUSIONER_CAST_SPELL, .6F, 1.2F + i * .1F);
            }
            if (age == BRAID) { pose(ArmPose.ARMS_WIDE, LANCE - BRAID); sound(player.position(), SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.4F); }
            if (age == LANCE) {
                pose(ArmPose.MIMIC_SHIFT, 10);
                zap(foe, 14);
                for (String v : voices) rider(player, foe, v);
                for (LivingEntity t : others(foeAt(), 4)) { zap(t, 4); shove(t, foeAt(), .9, .3); }
                sound(foeCentre(), SoundEvents.BEACON_DEACTIVATE, 1.4F, 1.6F);
                sound(foeCentre(), SoundEvents.GENERIC_EXPLODE.value(), .8F, 1.5F);
                shake(foeAt(), 18, .4F, 8);
                release();
            }
        }

        public List<String> voices() { return voices; }
    }

    // ------------------------------------------------------------------
    // Defense: Mirror Soul
    // ------------------------------------------------------------------

    /**
     * For twelve seconds the empath's remembered powers circle him as rings of their own colour. He feels every blow
     * only half, and the other half goes straight back at whoever struck, a bolt in the colour of the power he is
     * channeling.
     */
    public static final class MirrorSoul extends Run {
        public static final int TICKS = 240, INTRO = 22;
        private final List<String> voices;
        private final Map<UUID, Integer> answered = new HashMap<>();

        public MirrorSoul(ServerPlayer p, int tier, LivingEntity unused) {
            super(p, tier, POWER, "empathic_ward", null);
            voices = Chorus.voices(p);
        }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.ARMS_WIDE, 22);
            heroShot(INTRO, .5);
            piece(Piece.MIMIC_CHORUS, player.position(), player.position(), 1, player, Chorus.pack(voices));
            sound(player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, .7F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.MIMIC_CHORUS, player.position(), player.position(), 1, player, Chorus.pack(voices));
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            float half = event.getAmount() * .5F;
            event.setAmount(half);
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player || !StyleOps.isFoe(player, attacker)) return;
            int last = answered.getOrDefault(attacker.getUUID(), -100);
            if (age - last < 20) return;
            answered.put(attacker.getUUID(), age);
            zap(attacker, Math.min(8, half));
            String voice = voices.get((age / 20) % voices.size());
            piece(Piece.MIMIC_VOICE, player.getBoundingBox().getCenter(), attacker.getBoundingBox().getCenter(), MimicColors.index(voice), null, age);
        }
    }

    // ------------------------------------------------------------------
    // Support: Shared Gift
    // ------------------------------------------------------------------

    /**
     * Gold threads run from the empath to every friend within fourteen blocks, each carrying one of the powers he
     * remembers. They heal (more for every power he holds), lose their harmful effects, and for fifteen seconds wear a
     * little of that power: a firebender's warmth, a speedster's legs, a healer's blood.
     */
    public static final class SharedGift extends Run {
        public static final int TICKS = 300, INTRO = 22;
        public static final double RANGE = 14;
        private final List<String> voices;

        public SharedGift(ServerPlayer p, int tier, LivingEntity unused) {
            super(p, tier, POWER, "kindred_light", null);
            voices = Chorus.voices(p);
        }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.ARMS_WIDE, 24);
            heroShot(INTRO, 1);
            List<LivingEntity> friends = friends(RANGE);
            for (int i = 0; i < friends.size(); i++) {
                LivingEntity f = friends.get(i);
                String voice = voices.get(i % voices.size());
                StyleOps.heal(f, 8 + voices.size());
                StyleOps.cleanse(f);
                StyleOps.effect(f, boon(voice), TICKS, 0);
                piece(Piece.MIMIC_ULT_GIFT, player.position(), f.position(), MimicColors.index(voice), f, player.getId());
            }
            sound(player.position(), SoundEvents.PLAYER_LEVELUP, .7F, 1.4F);
        }

        @Override protected void beat(int age) {}
    }
}
