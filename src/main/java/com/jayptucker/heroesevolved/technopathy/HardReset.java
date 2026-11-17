package com.jayptucker.heroesevolved.technopathy;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Technopathy's finisher, Hard Reset: a palm on the nearest metal, and a ripple runs through every enrolled control
 * within 16 blocks toward the foe (lamps flicker, doors and trapdoors bang, levers flip: real block states, put back two
 * seconds later), the last node fires a line taut through the foe, and it goes down. With no circuit the line runs short
 * and plain from your own hand. The finisher is as big as the circuit you built. Your enrolment is renewed.
 */
public final class HardReset {
    public static final int RIPPLE = 6, LINE = 18, LAND = 22, TICKS = 28, NODES = 12, RESTORE = 40;
    public static final double RANGE = 16;
    /** The scale of a spark running node to node (the final line through the foe uses the foe's height). */
    public static final float SPARK = .2F;

    private HardReset() {}

    private record Circuit(List<BlockPos> nodes) {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TECHNOPATHY_ID; }
        @Override public String id() { return "hard_reset"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Vec3 at = p.position();
            List<BlockPos> nodes = new ArrayList<>(TechnopathyService.nodes(p).stream()
                    .filter(pos -> Vec3.atCenterOf(pos).distanceToSqr(at) <= RANGE * RANGE).toList());
            // Out from you toward the foe.
            nodes.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(at)));
            run.state = new Circuit(nodes.size() > NODES ? nodes.subList(0, NODES) : nodes);
            ArmPosePayload.show(p, ArmPose.ELECTRO_TOUCH, LINE);
            Takedowns.sound(run, p.position(), SoundEvents.COPPER_BULB_TURN_ON, 1F, .8F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Circuit circuit)) return;
            ServerPlayer p = run.player;
            ServerLevel level = run.level();
            int age = run.age();
            int i = age - RIPPLE;
            if (i >= 0 && i < circuit.nodes.size()) {
                BlockPos pos = circuit.nodes.get(i);
                Vec3 from = i == 0 ? p.getEyePosition().add(0, -.5, 0) : Vec3.atCenterOf(circuit.nodes.get(i - 1));
                Takedowns.show(level, Piece.FINISH_RESET, from, Vec3.atCenterOf(pos), SPARK, null, p.getId());
                BlockState before = level.getBlockState(pos);
                if (TechnopathyService.toggle(p, pos)) {
                    BlockState after = level.getBlockState(pos);
                    Takedowns.later(p.getServer(), RESTORE, () -> restore(p, level, pos, before, after));
                }
                Takedowns.sound(run, Vec3.atCenterOf(pos), SoundEvents.COPPER_BULB_TURN_OFF, .7F, 1.2F + i * .05F);
            }
            if (age == LINE) {
                Vec3 from = circuit.nodes.isEmpty() ? p.getEyePosition().add(0, -.5, 0) : Vec3.atCenterOf(circuit.nodes.getLast());
                Takedowns.show(level, Piece.FINISH_RESET, from, run.target.getBoundingBox().getCenter(),
                        Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
                ArmPosePayload.show(p, ArmPose.ELECTRO_POINT, 8);
                Takedowns.sound(run, run.anchor, SoundEvents.TRIPWIRE_ATTACH, 1F, .6F);
            }
            if (age == LINE + 2) Takedowns.flinch(run.target);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.COPPER_BULB_TURN_ON, 1.3F, .5F);
            Takedowns.sound(run, run.anchor, SoundEvents.PISTON_EXTEND, 1F, .7F);
        }

        @Override public void reward(Run run) { TechnopathyService.renew(run.player); }
    };

    /**
     * Puts a flipped control back, if nobody has touched it since: through the circuit's own rules while it lives, or
     * straight back to how it was (a door by its own hinge, so both halves move) once the circuit has lapsed.
     */
    static void restore(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (!level.hasChunkAt(pos) || level.getBlockState(pos) != after) return;
        if (TechnopathyService.toggle(p, pos)) return;
        if (after.getBlock() instanceof DoorBlock door && before.hasProperty(BlockStateProperties.OPEN))
            door.setOpen(null, level, after, pos, before.getValue(BlockStateProperties.OPEN));
        else level.setBlock(pos, before, 3);
    }
}
