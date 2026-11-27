package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Chorus (Empathic Mimicry, Resonance signature): Peter lets every power he has ever touched speak at once. A ring of
 * voices, one in each remembered power's colour (at least three: the echoes that come easiest fill any gaps), circles
 * him for six seconds, and every {@link #FIRE} ticks the next one leaps at the nearest foe within 16 blocks for 2 + tier/2,
 * with a touch of its own power (fire burns, cold slows, lightning jumps, radiation sickens, a far hand shoves). When it
 * ends, or on a second press, they all speak together, one bolt from each (2 + tier/4). {@code MimicArt.chorus} draws the ring (the
 * voices packed into the seed, five bits each) and {@code MimicArt.voice} each bolt (the power's index in the scale).
 */
public final class Chorus {
    public static final String ID = "chorus";
    public static final int HOLD = 120, FIRE = 20, RESEND = 20, MAX_VOICES = 5;
    public static final double RANGE = 16;
    private static final List<String> EASIEST = List.of("flight", "telekinesis", "radiation", "electrokinesis", "pyrokinesis", "cryokinesis");

    private Chorus() {}

    /** The powers that sing: every one remembered (not his own), filled up to three from the easiest echoes. */
    static List<String> voices(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (ResourceLocation power : MimicryService.memory(p).powers()) {
            String path = power.getPath();
            if (!path.equals("empathic_mimicry") && !out.contains(path) && out.size() < MAX_VOICES) out.add(path);
        }
        for (String easy : EASIEST) if (out.size() < 3 && !out.contains(easy)) out.add(easy);
        return out;
    }

    /** The voices packed for the art: five bits each, the count in the top three. */
    static int pack(List<String> voices) {
        int n = Math.min(voices.size(), MAX_VOICES), packed = n << 27;
        for (int i = 0; i < n; i++) packed |= (MimicColors.index(voices.get(i)) & 31) << (i * 5);
        return packed;
    }

    public static boolean canUse(ServerPlayer p) { return true; }

    public static boolean activate(ServerPlayer p, int tier) {
        Run run = new Run(p, tier, voices(p));
        if (!Signatures.start(ID, run)) return false;
        run.show();
        ArmPosePayload.show(p, ArmPose.ARMS_WIDE, 20);
        run.level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4F, 1.2F);
        run.level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1, 1.6F);
        p.displayClientMessage(Component.literal("Everyone at once.").withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    static final class Run extends Signatures.Run {
        final List<String> voices;
        int next, fired;

        Run(ServerPlayer player, int tier, List<String> voices) { super(player, tier); this.voices = voices; }

        void show() {
            SetPiecePayload.sendSeeded(level, Piece.MIMIC_CHORUS, player.position(), player.position().add(0, 1, 0), voices.size(), player, 0, pack(voices));
        }

        @Override protected boolean tick() {
            if (age % RESEND == 0 && age < HOLD) show();
            if (age % FIRE == 0 && age < HOLD) {
                List<LivingEntity> foes = Signatures.foes(player, player.getEyePosition(), RANGE, true);
                if (!foes.isEmpty()) {
                    sing(voices.get(next % voices.size()), foes.getFirst(), 2 + tier / 2F);
                    next++;
                }
            }
            if (age >= HOLD) { finale(); return false; }
            return true;
        }

        @Override protected boolean press() {
            finale();
            Signatures.end(ID, player);
            return true;
        }

        /** Every voice at once: one bolt each at the nearest foes. */
        void finale() {
            List<LivingEntity> foes = Signatures.foes(player, player.getEyePosition(), RANGE, true);
            if (!foes.isEmpty()) for (int i = 0; i < voices.size(); i++) sing(voices.get(i), foes.get(i % foes.size()), 2 + tier / 4F);
            level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2F, 1.4F);
            ArmPosePayload.show(player, ArmPose.TWO_HANDS, 12);
        }

        /** One voice speaks: a bolt in its colour, and a touch of its power. */
        void sing(String power, LivingEntity foe, float damage) {
            Vec3 chest = player.position().add(0, 1.3, 0);
            SetPiecePayload.sendSeeded(level, Piece.MIMIC_VOICE, chest, foe.getBoundingBox().getCenter(), MimicColors.index(power), player, 0, fired);
            Signatures.hit(foe, player.damageSources().indirectMagic(player, player), damage);
            switch (power) {
                case "pyrokinesis" -> foe.igniteForSeconds(3);
                case "cryokinesis" -> foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), player);
                case "radiation" -> com.jayptucker.heroesevolved.radiation.RadiationService.irradiate(foe, player, 1, 80);
                case "telekinesis", "quake", "flight" -> {
                    Vec3 out = foe.position().subtract(player.position()).multiply(1, 0, 1);
                    if (out.lengthSqr() > 1e-4) Signatures.fling(foe, out.normalize().scale(.8).add(0, .3, 0));
                }
                case "electrokinesis" -> {
                    for (LivingEntity near : Signatures.foes(player, foe.getBoundingBox().getCenter(), 4, true)) {
                        if (near == foe) continue;
                        SetPiecePayload.sendSeeded(level, Piece.MIMIC_VOICE, foe.getBoundingBox().getCenter(), near.getBoundingBox().getCenter(),
                                MimicColors.index(power), null, 0, fired + 100);
                        Signatures.hit(near, player.damageSources().indirectMagic(player, player), damage * .5F);
                        break;
                    }
                }
                default -> { }
            }
            level.playSound(null, foe.blockPosition(), SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 1.2F, .8F + fired * .07F % .8F);
            fired++;
        }
    }

    /** For tests: how many bolts this caster's Chorus has sung (-1 when none is running). */
    public static int fired(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? -1 : run.fired;
    }

    /** For tests: the voices a Chorus would sing. */
    public static List<String> voicesOf(ServerPlayer p) { return voices(p); }
}
