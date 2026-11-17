package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
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
import net.minecraft.world.entity.LivingEntity;

/**
 * Telepathy's alternate finishers, picked at random beside {@link LightsOut}. Both reach six blocks, like Lights Out.
 *
 * <ul>
 *   <li><b>Pressure</b>: two fingers to your temple, and three halos close round the foe's head, pressing in with each
 *   pulse; it clutches at its head and bows lower with every one, then folds.</li>
 *   <li><b>Last Thought</b>: you draw a glowing thread out of the foe's head; its last thought runs down the thread to
 *   you as a bead of light and you hear it (in chat, to you alone); you snip the thread and the foe goes still.</li>
 * </ul>
 *
 * <p>{@code TelepathyArt} draws both; each piece is bound to the foe with the telepath's entity id in the seed.</p>
 */
public final class TelepathyFinishers {
    /** Pressure: the halos pulse in on these ticks; the foe folds at LAND. */
    public static final int[] PRESSURE_PULSES = {5, 9, 12, 15, 17};
    public static final int PRESSURE_LAND = 20, PRESSURE_TICKS = 30;
    /** Last Thought: the thread is drawn out by DRAWN, the thought reaches you at HEARD, the snip at LAND. */
    public static final int THOUGHT_DRAWN = 8, THOUGHT_HEARD = 16, THOUGHT_LAND = 22, THOUGHT_TICKS = 32;
    /** How many last thoughts there are in the lang file ({@code finisher.heroesevolved.last_thought.N}). */
    public static final int THOUGHTS = 8;

    private TelepathyFinishers() {}

    public static final Takedowns.Finisher PRESSURE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TELEPATHY_ID; }
        @Override public String id() { return "pressure"; }
        @Override public int ticks() { return PRESSURE_TICKS; }
        @Override public int landAt() { return PRESSURE_LAND; }
        @Override public double reach() { return Takedowns.RANGED_REACH; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.show(run.level(), Piece.FINISH_PRESSURE, p.getEyePosition(), foe.getEyePosition(), foe.getBbHeight(), foe, p.getId());
            ArmPosePayload.show(p, ArmPose.TEMPLE, PRESSURE_TICKS - 6);
            Takedowns.sound(run, foe.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1F, .5F);
        }

        @Override public void tick(Run run) {
            LivingEntity foe = run.target;
            int age = run.age();
            for (int i = 0; i < PRESSURE_PULSES.length; i++) if (age == PRESSURE_PULSES[i]) {
                Takedowns.flinch(foe);
                Takedowns.sound(run, foe.position(), SoundEvents.WARDEN_HEARTBEAT, .7F + i * .1F, 1.3F + i * .1F);
            }
            // The head is forced down, a little more with every pulse.
            if (foe.isAlive() && age < PRESSURE_LAND) {
                int pulses = 0;
                for (int pulse : PRESSURE_PULSES) if (age >= pulse) pulses++;
                foe.setXRot(Math.min(70, pulses * 14));
                foe.xRotO = foe.getXRot();
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(foe, p.damageSources().indirectMagic(p, p), damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(other, p.damageSources().indirectMagic(p, p), amount));
            Takedowns.sound(run, foe.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1F, .5F);
            Takedowns.sound(run, foe.position(), SoundEvents.BEACON_DEACTIVATE, .8F, 1.2F);
        }
    };

    public static final Takedowns.Finisher LAST_THOUGHT = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TELEPATHY_ID; }
        @Override public String id() { return "last_thought"; }
        @Override public int ticks() { return THOUGHT_TICKS; }
        @Override public int landAt() { return THOUGHT_LAND; }
        @Override public double reach() { return Takedowns.RANGED_REACH; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.show(run.level(), Piece.FINISH_THOUGHT, p.getEyePosition(), foe.getEyePosition(), foe.getBbHeight(), foe, p.getId());
            ArmPosePayload.show(p, ArmPose.TELE_MEMORY, THOUGHT_LAND - 2);
            Takedowns.sound(run, foe.position(), SoundEvents.SOUL_ESCAPE.value(), .9F, 1.2F);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            int age = run.age();
            if (age == THOUGHT_DRAWN) {
                Takedowns.flinch(foe);
                Takedowns.sound(run, foe.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1F, 1.6F);
            }
            if (age == THOUGHT_HEARD) {
                // What it was thinking, heard by you alone.
                int line = Math.floorMod(foe.getUUID().hashCode() + foe.getType().hashCode(), THOUGHTS);
                p.sendSystemMessage(Component.translatable("finisher.heroesevolved.last_thought.heard",
                        Component.translatable("finisher.heroesevolved.last_thought." + line))
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
                Takedowns.sound(run, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .6F, 1.8F);
            }
            if (age == THOUGHT_LAND - 2) {
                ArmPosePayload.show(p, ArmPose.TELE_COMMAND, 8);
                Takedowns.sound(run, foe.position(), SoundEvents.SHEEP_SHEAR, 1F, 1.4F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(foe, p.damageSources().indirectMagic(p, p), damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(other, p.damageSources().indirectMagic(p, p), amount));
            Takedowns.sound(run, foe.position(), SoundEvents.SOUL_ESCAPE.value(), 1.2F, .5F);
        }
    };
}
