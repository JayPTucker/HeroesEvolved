package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Empathic Mimicry's own finisher (with nothing channeled; channeling a power, you finish with that power's), Take It In:
 * your hand goes to your heart and gold threads sag between the foe and your chest, draw tight while you feel what it
 * felt, snap straight, and a pulse runs into your chest as it goes still. A foe with a power (a gifted villager, a player,
 * Sylar or Elle) is imprinted at once: its power becomes a kept set, no touch needed.
 */
public final class TakeItIn {
    public static final int TIGHT = 8, SNAP = 16, LAND = 20, TICKS = 26;
    /** Added to the piece's scale when the foe has a power to take (its colour is in the seed's high bits). */
    public static final float POWERED = 32;

    private TakeItIn() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.MIMICRY_ID; }
        @Override public String id() { return "take_it_in"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            MimicryService.Imprint imprint = MimicryService.imprintOf(p, run.target);
            run.state = imprint;
            Takedowns.show(run.level(), Piece.FINISH_TAKE, run.anchor, p.position(),
                    Math.max(.6F, run.target.getBbHeight()) + (imprint != null ? POWERED : 0), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.MIMIC_SHIFT, TICKS - 4);
            Takedowns.sound(run, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, 1.2F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == TIGHT) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.WARDEN_HEARTBEAT, .7F, 1.2F);
            }
            if (age == SNAP) Takedowns.sound(run, run.player.position(), SoundEvents.BEACON_POWER_SELECT, 1F, 1.4F);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(run.target, p.damageSources().indirectMagic(p, p), damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(other, p.damageSources().indirectMagic(p, p), amount));
        }

        @Override public void reward(Run run) {
            ServerPlayer p = run.player;
            if (!(run.state instanceof MimicryService.Imprint imprint)) return;
            if (MimicryService.remember(p, imprint.power(), PlayerProgressionService.getLevel(p)))
                p.displayClientMessage(Component.translatable("finisher.heroesevolved.take_it_in.kept",
                        Component.translatable("ability.heroesevolved." + imprint.power().getPath())).withStyle(ChatFormatting.GOLD), true);
        }
    };
}
