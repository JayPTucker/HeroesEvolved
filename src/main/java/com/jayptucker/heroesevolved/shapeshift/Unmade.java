package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FinisherBeats;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Shapeshifting's finisher, Unmade: you touch the foe and plates ripple out from the touch, flipping slate to brass, until
 * its whole shape is shut in them; then the plates come loose one by one and fall, tumbling, with nothing underneath,
 * and the last one sinks into the ground. You learn the foe's shape without studying it.
 *
 * <p>{@code ShapeArt}'s {@code FINISH_UNMADE} draws the plates; {@code BodyCopies} hides the body once they have closed.</p>
 */
public final class Unmade {
    private Unmade() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.SHAPESHIFTING_ID; }
        @Override public String id() { return "unmade"; }
        @Override public int ticks() { return FinisherBeats.UNMADE_TICKS; }
        @Override public int landAt() { return FinisherBeats.UNMADE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_UNMADE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()),
                    run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.SHIFT_TOUCH, FinisherBeats.UNMADE_GONE);
            Takedowns.sound(run, run.anchor, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1F, .7F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == 5 || age == 10) Takedowns.flinch(run.target);
            if (age > 0 && age < FinisherBeats.UNMADE_GONE && age % 3 == 0)
                Takedowns.sound(run, run.anchor, SoundEvents.ARMOR_EQUIP_IRON.value(), .6F, 1.2F + age * .03F);
            if (age == FinisherBeats.UNMADE_GONE) {
                ArmPosePayload.show(run.player, ArmPose.SHIFT_SHED, 10);
                Takedowns.sound(run, run.anchor, SoundEvents.CHAIN_BREAK, 1F, .7F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.ANVIL_LAND, .5F, 1.6F);
        }

        @Override public void reward(Run run) {
            ServerPlayer p = run.player;
            if (ShapeshiftService.learn(p, run.target.getType()))
                p.displayClientMessage(Component.translatable("finisher.heroesevolved.unmade.learned", run.target.getType().getDescription())
                        .withStyle(ChatFormatting.GOLD), true);
        }
    };
}
