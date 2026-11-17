package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Electrokinesis's finisher, Short Circuit: a two-fingered tap on the foe's forehead, sparks crawl over it and trace its
 * body in lightning, you blow on your fingertips, and the foe's own lightning sketch tears loose and earths into the
 * ground. At 100 Charge the sketch stays standing as a trap for three seconds and bursts on the first foe that comes
 * close. Gives 25 Charge.
 */
public final class ShortCircuit {
    public static final int TAP = 2, CRAWL = 4, BLOW = 13, LAND = 20, TICKS = 26;
    /** Added to the piece's scale when the sketch stays as a trap. */
    public static final float TRAP = 32;
    public static final int TRAP_TICKS = 56, CHARGE_REWARD = 25;
    /** Scales that mark the trap's own pieces: the sketch left standing, and its burst. */
    public static final float STANDING = .3F, BURST = .1F;

    private ShortCircuit() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.ELECTROKINESIS_ID; }
        @Override public String id() { return "short_circuit"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            boolean trap = ElectroService.charge(p) >= 100;
            run.state = trap;
            Takedowns.show(run.level(), Piece.FINISH_SHORT, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()) + (trap ? TRAP : 0),
                    run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.ELECTRO_POINT, BLOW);
            Takedowns.sound(run, run.anchor, SoundEvents.FIRECHARGE_USE, .5F, 2F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == TAP || age == 8 || age == 11) Takedowns.flinch(run.target);
            if (age == CRAWL) Takedowns.sound(run, run.anchor, SoundEvents.BEACON_ACTIVATE, .8F, 1.9F);
            if (age == BLOW) {
                ArmPosePayload.show(run.player, ArmPose.ELECTRO_CUP, 8);
                Takedowns.sound(run, run.player.position(), SoundEvents.BREEZE_WHIRL, .6F, 1.6F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> ElectroService.shock(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> ElectroService.shock(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.LIGHTNING_BOLT_IMPACT, .7F, 1.4F);
            if (run.state instanceof Boolean trap && trap) {
                Vec3 at = run.anchor;
                float damage = Math.max(4, run.damage() * .6F);
                // The sketch left standing: two copies of the piece back to back cover the trap's three seconds.
                Takedowns.show(run.level(), Piece.FINISH_SHORT, at, p.position(), STANDING, null, p.getId());
                Takedowns.later(p.getServer(), Piece.FINISH_SHORT.ticks, () -> { if (!SPRUNG.contains(at))
                        Takedowns.show(p.serverLevel(), Piece.FINISH_SHORT, at, p.position(), STANDING, null, p.getId()); });
                for (int i = 2; i <= TRAP_TICKS; i += 2) {
                    int age = i;
                    Takedowns.later(p.getServer(), i, () -> spring(p, at, damage, age));
                }
            }
        }

        @Override public void reward(Run run) { ElectroService.addCharge(run.player, CHARGE_REWARD); }
    };

    /** The standing sketch bursts on the first foe within reach of it (once). */
    private static final java.util.Set<Vec3> SPRUNG = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private static void spring(ServerPlayer p, Vec3 at, float damage, int age) {
        if (SPRUNG.contains(at) || !p.isAlive()) return;
        for (LivingEntity foe : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.4, 2, 1.4),
                e -> StyleHits.hostile(p, e))) {
            SPRUNG.add(at);
            ElectroService.shock(p, foe, damage);
            Takedowns.show(p.serverLevel(), Piece.FINISH_SHORT, at, foe.position(), BURST, null, p.getId());
            p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, net.minecraft.sounds.SoundSource.PLAYERS, .8F, 1.7F);
            return;
        }
    }
}
