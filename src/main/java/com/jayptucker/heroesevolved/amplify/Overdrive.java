package com.jayptucker.heroesevolved.amplify;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Overdrive (Power Amplification, Red Lightning signature): Ando throws red lightning into everyone on his side within
 * {@link #LINK} blocks and holds them all open at once for eight seconds. Each one is Surged (their powers harder,
 * cheaper and quicker) and gets back {@link #STAMINA} stamina a second; his own Charge runs to the top. When it ends, or
 * on a second press, the overflow comes out: a fork of red lightning leaps from each linked body to the nearest foe within
 * {@link #ARC} blocks, 4 + tier each (alone, he throws three forks himself). {@code AmpArt.overdrive} draws the web
 * (re-sent each second) and {@code AmpArt.discharge} each fork.
 */
public final class Overdrive {
    public static final String ID = "overdrive";
    public static final int HOLD = 160, RESEND = 20;
    public static final double LINK = 12, ARC = 8;
    public static final float STAMINA = 4;

    private Overdrive() {}

    static List<LivingEntity> linked(ServerPlayer p) {
        List<LivingEntity> out = new ArrayList<>();
        out.add(p);
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(LINK),
                e -> e != p && e.isAlive() && e.distanceToSqr(p) <= LINK * LINK && AmpService.ally(p, e)))
            if (out.size() < 8) out.add(e);
        return out;
    }

    public static boolean canUse(ServerPlayer p) { return true; }

    public static boolean activate(ServerPlayer p, int tier) {
        Run run = new Run(p, tier, linked(p));
        if (!Signatures.start(ID, run)) return false;
        for (LivingEntity e : run.linked) if (e != p) Amplification.surge(p, e, HOLD);
        AmpService.addCharge(p, AmpService.MAX_CHARGE);
        run.show();
        ArmPosePayload.show(p, ArmPose.AMP_RAISE, 20);
        run.level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, .7F, 1.6F);
        run.level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.5F);
        return true;
    }

    static final class Run extends Signatures.Run {
        final List<LivingEntity> linked;
        int forks;

        Run(ServerPlayer player, int tier, List<LivingEntity> linked) { super(player, tier); this.linked = new ArrayList<>(linked); }

        void show() {
            for (LivingEntity e : linked)
                if (e.isAlive()) SetPiecePayload.sendSeeded(level, Piece.AMP_OVERDRIVE, e.position(), player.position(), e.getBbHeight(), e, 0, e == player ? -1 : player.getId());
        }

        @Override protected boolean tick() {
            linked.removeIf(e -> !e.isAlive() || e.level() != level);
            if (age % RESEND == 0 && age < HOLD) {
                show();
                for (LivingEntity e : linked) if (e instanceof ServerPlayer friend) PlayerEnergyService.restore(friend, (double) STAMINA);
                AmpService.addCharge(player, 10);
                if (age % 40 == 0) ArmPosePayload.show(player, ArmPose.AMP_RAISE, 24);
            }
            if (age >= HOLD) { discharge(); return false; }
            return true;
        }

        @Override protected boolean press() {
            discharge();
            Signatures.end(ID, player);
            return true;
        }

        /** The overflow: a fork from each linked body to the nearest foe. */
        void discharge() {
            List<LivingEntity> sources = new ArrayList<>(linked);
            if (sources.size() == 1) { sources.add(player); sources.add(player); }
            float damage = 4 + tier;
            int n = 0;
            for (LivingEntity from : sources) {
                List<LivingEntity> foes = Signatures.foes(player, from.getBoundingBox().getCenter(), ARC, true);
                if (foes.isEmpty()) continue;
                LivingEntity foe = foes.get(Math.min(foes.size() - 1, from == player ? n : 0));
                SetPiecePayload.sendSeeded(level, Piece.AMP_DISCHARGE, from.getBoundingBox().getCenter(), foe.getBoundingBox().getCenter(), 1, null, 0, n * 17 + 5);
                Signatures.hit(foe, player.damageSources().indirectMagic(player, player), damage);
                AmpService.stun(foe, 10);
                forks++;
                n++;
            }
            level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 1.3F);
            CameraShakePayload.sendAround(level, player.position(), ARC * 2, .7F, 8);
            ArmPosePayload.show(player, ArmPose.AMP_THROW, 12);
        }
    }

    /** For tests: who this caster's Overdrive links (empty when none is running). */
    public static List<LivingEntity> linkedOf(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? List.of() : List.copyOf(run.linked);
    }

    /** For tests: how many forks the last discharge threw (-1 when none is running). */
    public static int forks(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? -1 : run.forks;
    }
}
