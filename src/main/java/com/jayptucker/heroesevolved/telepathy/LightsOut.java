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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Telepathy's finisher, Lights Out, from up to six blocks and without a touch: two fingers to the temple and a stare. A
 * thread reaches the foe's mind, it freezes and its head bows as a halo closes round it, and when the halo shuts the
 * lights go out. Everyone nearby hears a whisper ("...quiet now."), and every hostile within eight blocks loses its
 * target for a moment. The overkill spills onto foes beside it as the same quiet blow.
 *
 * <p>It works the same alone or in a crowd. {@code TelepathyArt}'s {@code FINISH_LIGHTS} draws the thread, the iris
 * and the closing halo from the same ticks.</p>
 */
public final class LightsOut {
    /** The thread reaches the mind by GRIP, the halo closes from CLOSE, the whisper at WHISPER, the lights go at {@link #LAND}. */
    public static final int GRIP = 5, CLOSE = 8, SHAKE = 12, WHISPER = 18, LAND = 22, TICKS = 30;
    /** How far the stunned silence reaches: hostiles this close forget whom they were fighting. */
    public static final double HUSH_RANGE = 8;
    /** Who hears the whisper. */
    public static final double WHISPER_RANGE = 16;

    private LightsOut() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TELEPATHY_ID; }
        @Override public String id() { return "lights_out"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }
        @Override public double reach() { return Takedowns.RANGED_REACH; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.show(run.level(), Piece.FINISH_LIGHTS, p.getEyePosition(), foe.getEyePosition(), foe.getBbHeight(), foe, p.getId());
            ArmPosePayload.show(p, ArmPose.TELE_READ, TICKS - 4);
            sound(p, foe.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1F, .6F);
            sound(p, foe.position(), SoundEvents.SOUL_ESCAPE.value(), .8F, .7F);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            int age = run.age();
            // The head bows as the halo closes.
            if (age >= CLOSE && age < LAND && foe.isAlive()) {
                float bow = Math.min(1, (age - CLOSE) / (float) (LAND - CLOSE));
                foe.setXRot(50 * bow);
                foe.xRotO = foe.getXRot();
            }
            if (age == GRIP) {
                Takedowns.flinch(foe);
                sound(p, foe.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1F, .5F);
            }
            if (age == SHAKE) {
                Takedowns.flinch(foe);
                hush(run);
            }
            if (age == WHISPER) {
                Component whisper = Component.translatable("finisher.heroesevolved.lights_out.whisper")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
                for (ServerPlayer listener : p.serverLevel().players())
                    if (listener.distanceToSqr(foe) <= WHISPER_RANGE * WHISPER_RANGE) listener.sendSystemMessage(whisper);
                sound(p, foe.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, .8F, .55F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(foe, p.damageSources().indirectMagic(p, p), damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(other, p.damageSources().indirectMagic(p, p), amount));
            sound(p, foe.position(), SoundEvents.SOUL_ESCAPE.value(), 1.2F, .5F);
            sound(p, foe.position(), SoundEvents.BEACON_DEACTIVATE, .9F, .8F);
        }
    };

    /** Every hostile mob near the foe forgets whom it was fighting, for a moment. */
    static void hush(Run run) {
        Vec3 at = run.anchor;
        for (Mob mob : run.level().getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(HUSH_RANGE),
                m -> m != run.target && m.isAlive() && StyleHits.hostile(run.player, m))) {
            mob.setTarget(null);
            if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getNavigation().stop();
        }
    }

    private static void sound(ServerPlayer player, Vec3 at, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
