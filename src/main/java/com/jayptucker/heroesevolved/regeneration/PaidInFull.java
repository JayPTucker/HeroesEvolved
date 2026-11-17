package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Regeneration's finisher, Paid in Full: you open your arms and take the foe's last blow plainly, look down at the wound
 * as it closes from the bone out, then grab the foe and land one punch while a red ring of every wound you healed in the
 * last ten seconds runs down your arm into it. The punch adds a quarter of the damage you took in those ten seconds (up
 * to 20) to the overkill. The scripted blow costs a little health and the move heals more. Gives 30 Regrowth.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PaidInFull {
    public static final int TAKE = 3, CLOSE = 12, PUNCH = 15, LAND = 18, TICKS = 26;
    public static final float BLOW = 3, HEAL = 5, BONUS_SHARE = .25F, BONUS_CAP = 20;
    public static final int REGROWTH = 30;
    private static final long WINDOW = 200;

    private record Wound(long at, float amount) {}
    private static final Map<UUID, ArrayDeque<Wound>> WOUNDS = new HashMap<>();

    private PaidInFull() {}

    /** Damage this player took in the last ten seconds. */
    public static float suffered(ServerPlayer p) {
        ArrayDeque<Wound> wounds = WOUNDS.get(p.getUUID());
        if (wounds == null) return 0;
        long now = p.serverLevel().getGameTime();
        float total = 0;
        for (Wound w : wounds) if (now - w.at <= WINDOW) total += w.amount;
        return total;
    }

    @SubscribeEvent
    public static void wounded(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getNewDamage() <= 0
                || !com.jayptucker.heroesevolved.combat.FightingStyles.hasPower(p, ModAbilities.REGENERATION_ID)) return;
        ArrayDeque<Wound> wounds = WOUNDS.computeIfAbsent(p.getUUID(), id -> new ArrayDeque<>());
        long now = p.serverLevel().getGameTime();
        wounds.addLast(new Wound(now, event.getNewDamage()));
        while (!wounds.isEmpty() && (now - wounds.peekFirst().at > WINDOW || wounds.size() > 64)) wounds.pollFirst();
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { WOUNDS.remove(event.getEntity().getUUID()); }

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.REGENERATION_ID; }
        @Override public String id() { return "paid_in_full"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            run.state = Math.min(BONUS_CAP, suffered(p) * BONUS_SHARE);
            Takedowns.show(run.level(), Piece.FINISH_PAID, p.position(), run.anchor, Math.max(.6F, run.target.getBbHeight()), p, run.target.getId());
            ArmPosePayload.show(p, ArmPose.REGEN_DEFY, PUNCH);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            int age = run.age();
            if (age == TAKE) {
                // The foe's last blow, taken plainly (a generic blow, so the hold doesn't cancel it).
                p.invulnerableTime = 0;
                p.hurt(p.damageSources().generic(), BLOW);
                Takedowns.sound(run, p.position(), SoundEvents.PLAYER_HURT, 1F, .9F);
            }
            if (age == CLOSE) {
                p.heal(HEAL);
                Takedowns.sound(run, p.position(), SoundEvents.HONEY_BLOCK_SLIDE, 1F, .6F);
            }
            if (age == PUNCH) ArmPosePayload.show(p, ArmPose.REGEN_PAYBACK, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            float bonus = run.state instanceof Float f ? f : 0;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            float spilled = run.overkill() + bonus;
            for (var other : Takedowns.others(run, Takedowns.SPILL_RADIUS, Takedowns.SPILL_COUNT)) {
                if (spilled <= .05F) break;
                StyleHits.hurt(p, other, Math.max(2, spilled));
                StyleHits.away(other, run.anchor, StyleHits.level(p), .6, .25);
            }
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3F, .6F);
            Takedowns.sound(run, run.anchor, SoundEvents.SLIME_SQUISH, 1F, .6F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .18F, 5);
        }

        @Override public void reward(Run run) { RegenerationService.addRegrowth(run.player, REGROWTH); }
    };
}
