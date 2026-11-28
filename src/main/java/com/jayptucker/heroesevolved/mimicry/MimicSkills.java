package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import com.jayptucker.heroesevolved.ability.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Empathic Mimicry's own skills: the tree's three actives (Borrowed Strike, Empathic Surge, Calm) and its techniques
 * (Sense Powers, Overcharge, Absorb Pain). Each is a heartbeat made visible ({@code MimicArt}): gold threads from the
 * empath's hand or heart, and the colour of whatever power is involved (the one channelled, a friend's sickness, a
 * stranger's gift) where it lands. Each has the empath's own gesture: a lash from the heart, arms flung wide, a hand on
 * the heart pressing the world calm, both hands drawing pain in, a hand on the heart listening.
 *
 * <p>Pieces: a skill's main piece has seed 0 (or a target's id); a <b>link</b> to someone else has {@link #LINK} set in
 * its seed, with a colour or a power's index in the low bits.</p>
 */
public final class MimicSkills {
    /** How far each reaches. */
    public static final double STRIKE_RANGE = 40, SURGE_RANGE = 8, CALM_RANGE = 6, ABSORB_RANGE = 8, SENSE_RANGE = 48;
    /** How long Empathic Surge's Strength lasts, and the most threads one skill draws. */
    public static final int SURGE_TICKS = 160, MAX_LINKS = 8, MAX_SENSED = 12;
    /** Marks a piece's seed as a link to someone else (the low 24 bits a colour or a power's index). */
    public static final int LINK = 1 << 24;

    private MimicSkills() {}

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        return switch (branch) {
            case 0 -> borrowedStrike(p, c.abilityLevel());
            case 1 -> surge(p);
            case 2 -> calm(p);
            default -> false;
        };
    }

    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        return switch (mechanic) {
            case "sense_powers" -> sense(p);
            case "overcharge" -> overcharge(p);
            case "absorb_pain" -> absorb(p);
            default -> false;
        };
    }

    /** The channelled power's path, or null with nothing channelled. */
    static String channelled(ServerPlayer p) {
        ResourceLocation power = MimicryService.memory(p).current();
        return power == null ? null : power.getPath();
    }

    /** A power's colour index for a piece's scale (the empath's own gold for none). */
    static int index(String power) { return MimicColors.index(power == null ? "empathic_mimicry" : power); }

    // ------------------------------------------------------------------
    // Borrowed Strike
    // ------------------------------------------------------------------

    /**
     * The rear hand at the heart, the lead hand thrown open at a foe up to 40 blocks off: a gold thread whips out of the
     * palm and blooms on the foe in the channelled power's colour, carrying a touch of that power ({@link Chorus#touch}:
     * fire burns, cold slows, lightning jumps, radiation sickens, a far hand shoves). 6 + level damage.
     */
    static boolean borrowedStrike(ServerPlayer p, int abilityLevel) {
        LivingEntity target = Targeting.livingEntityInSight(p, STRIKE_RANGE, t -> ExpansionSkills.hostile(p, t));
        if (target == null) return false;
        String power = channelled(p);
        float damage = 6 + abilityLevel;
        ArmPosePayload.show(p, ArmPose.MIMIC_LASH, 12);
        SetPiecePayload.sendSeeded(p.serverLevel(), Piece.MIMIC_LASH, MimicGeometry.hand(p, 1, true, 1),
                target.getBoundingBox().getCenter(), index(power), p, 0, target.getId());
        target.hurt(p.damageSources().playerAttack(p), damage);
        Chorus.touch(p, power, target, damage, 0);
        sound(p, target.position(), SoundEvents.AMETHYST_CLUSTER_HIT, 1.1F, 1.3F);
        sound(p, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, .7F, 1.4F);
        return true;
    }

    // ------------------------------------------------------------------
    // Empathic Surge
    // ------------------------------------------------------------------

    /**
     * Arms flung wide: two heartbeats ring out from the empath's chest and a gold thread snaps tight to every friend within
     * 8 blocks (players, pets), each bloom at their chest in time with the beat. Everyone linked feels each other's
     * strength: Strength for eight seconds.
     */
    static boolean surge(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 heart = MimicGeometry.heart(p, 1);
        ArmPosePayload.show(p, ArmPose.MIMIC_SURGE, 18);
        SetPiecePayload.sendSeeded(level, Piece.MIMIC_SURGE, heart, heart, (float) SURGE_RANGE, p, 0, 0);
        int linked = 0;
        for (LivingEntity friend : StyleOps.friends(p, p.position(), SURGE_RANGE)) {
            friend.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, SURGE_TICKS, 0), p);
            if (friend == p || linked >= MAX_LINKS) continue;
            SetPiecePayload.sendSeeded(level, Piece.MIMIC_SURGE, heart, MimicGeometry.chest(friend, 1), 1 + linked, friend, 0, LINK | linked);
            linked++;
        }
        sound(p, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.1F);
        sound(p, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.2F);
        return true;
    }

    // ------------------------------------------------------------------
    // Calm
    // ------------------------------------------------------------------

    /**
     * A hand on the heart, the other pressed slowly down: a soft gold ring settles out across the ground to 6 blocks, and
     * every harmful effect on the empath and their friends lifts off as motes in its own colour that drift into the
     * empath's heart and turn gold.
     */
    static boolean calm(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 heart = MimicGeometry.heart(p, 1);
        ArmPosePayload.show(p, ArmPose.MIMIC_CALM, 26);
        SetPiecePayload.sendSeeded(level, Piece.MIMIC_CALM, heart, p.position(), (float) CALM_RANGE, p, 0, 0);
        int links = 0;
        for (LivingEntity friend : StyleOps.friends(p, p.position(), CALM_RANGE)) {
            for (MobEffectInstance effect : List.copyOf(friend.getActiveEffects())) {
                if (effect.getEffect().value().isBeneficial()) continue;
                friend.removeEffect(effect.getEffect());
                if (links >= MAX_LINKS) continue;
                // The empath's own sickness rises off their body; a friend's drifts across.
                Vec3 from = friend == p ? p.position().add(0, .4, 0) : MimicGeometry.chest(friend, 1);
                SetPiecePayload.sendSeeded(level, Piece.MIMIC_CALM, from, heart, 1 + links, p, 0,
                        LINK | (effect.getEffect().value().getColor() & 0xFFFFFF));
                links++;
            }
        }
        sound(p, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1, .7F);
        sound(p, p.position(), SoundEvents.WARDEN_HEARTBEAT, .8F, .7F);
        return true;
    }

    // ------------------------------------------------------------------
    // Absorb Pain
    // ------------------------------------------------------------------

    /**
     * Both hands reach out and draw in: three gold threads tighten between a friend and the empath's heart, and each
     * harmful effect on the friend runs down them as a bead of its own colour into the empath (who carries it at half
     * the time), while gold flows the other way and mends the friend for 4 (8 with Shared Healing).
     */
    static boolean absorb(ServerPlayer p) {
        LivingEntity friend = Targeting.livingEntityInSight(p, ABSORB_RANGE, t -> t != p && ExpansionSkills.friendly(p, t));
        if (friend == null) return false;
        List<Integer> colours = new ArrayList<>();
        for (MobEffectInstance effect : List.copyOf(friend.getActiveEffects())) {
            if (effect.getEffect().value().isBeneficial()) continue;
            friend.removeEffect(effect.getEffect());
            p.addEffect(new MobEffectInstance(effect.getEffect(), Math.max(20, effect.getDuration() / 2), effect.getAmplifier()));
            colours.add(effect.getEffect().value().getColor() & 0xFFFFFF);
        }
        friend.heal(SkillTreeService.has(p, "empathic_mimicry_shared_healing") ? 8 : 4);
        ArmPosePayload.show(p, ArmPose.MIMIC_ABSORB, 18);
        ServerLevel level = p.serverLevel();
        Vec3 from = MimicGeometry.chest(friend, 1), heart = MimicGeometry.heart(p, 1);
        if (colours.isEmpty()) SetPiecePayload.sendSeeded(level, Piece.MIMIC_ABSORB, from, heart, 1, p, 0, -1);
        else for (int i = 0; i < Math.min(4, colours.size()); i++)
            SetPiecePayload.sendSeeded(level, Piece.MIMIC_ABSORB, from, heart, 1 + i, p, 0, colours.get(i));
        sound(p, friend.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .9F, 1.4F);
        sound(p, p.position(), SoundEvents.WARDEN_HEARTBEAT, .7F, .9F);
        return true;
    }

    // ------------------------------------------------------------------
    // Sense Powers
    // ------------------------------------------------------------------

    /**
     * A hand on the heart, listening: two heartbeats race out along the ground to 48 blocks, and as the ring passes
     * each powered person (players, gifted villagers, Sylar) a ring in their power's colour flares round their chest and
     * a thin thread of light rises off them. Only the empath sees whom they found (the glow and the rings); anyone near
     * sees the heartbeat go out. Everyone found glows for the empath for eight seconds.
     */
    static boolean sense(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 heart = MimicGeometry.heart(p, 1);
        ArmPosePayload.show(p, ArmPose.MIMIC_SENSE, 30);
        SetPiecePayload.sendSeeded(level, Piece.MIMIC_SENSE, heart, p.position(), (float) SENSE_RANGE, p, 0, 0);
        int found = 0;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SENSE_RANGE),
                e -> e != p && MimicryService.imprintOf(p, e) != null)) {
            PrivateGlow.glow(p, other, 160);
            if (found < MAX_SENSED) {
                MimicryService.Imprint imprint = MimicryService.imprintOf(p, other);
                privately(p, Piece.MIMIC_SENSE, p.position(), MimicGeometry.chest(other, 1), 1, other,
                        LINK | index(imprint == null ? null : imprint.power().getPath()));
            }
            found++;
        }
        sound(p, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1, .8F);
        sound(p, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, 1.6F);
        p.displayClientMessage(Component.translatable("mimicry.heroesevolved.sensed", found), true);
        return true;
    }

    // ------------------------------------------------------------------
    // Overcharge
    // ------------------------------------------------------------------

    /**
     * The switching gesture held a beat longer: rings in the channelled power's colour close onto both hands and flare,
     * and the next Echo runs at the empath's full level.
     */
    static boolean overcharge(ServerPlayer p) {
        MimicryService.overcharge(p);
        ArmPosePayload.show(p, ArmPose.MIMIC_SHIFT, 12);
        Vec3 heart = MimicGeometry.heart(p, 1);
        SetPiecePayload.sendSeeded(p.serverLevel(), Piece.MIMIC_OVERCHARGE, heart, heart, index(channelled(p)), p, 0, 0);
        sound(p, p.position(), SoundEvents.BEACON_POWER_SELECT, .8F, 1.6F);
        p.displayClientMessage(Component.translatable("mimicry.heroesevolved.overcharged").withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    // ------------------------------------------------------------------

    /** A piece only the empath is sent (what they sense is theirs alone). */
    private static void privately(ServerPlayer p, Piece piece, Vec3 origin, Vec3 target, float scale, LivingEntity bound, int seed) {
        if (p.connection == null) return;
        PacketDistributor.sendToPlayer(p, new SetPiecePayload(piece.ordinal(), origin, target, scale, bound.getId(), seed, 0));
    }

    private static void sound(ServerPlayer p, Vec3 at, SoundEvent sound, float volume, float pitch) {
        p.serverLevel().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
