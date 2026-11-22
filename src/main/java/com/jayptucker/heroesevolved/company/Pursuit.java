package com.jayptucker.heroesevolved.company;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Company's hunt for refugees on the road ({@code travel/Refugees}): two agents come up behind the family and take
 * them one at a time. They go for anyone within {@link #GUARDING} of the family; anyone within {@link #CAUGHT_WITH} when
 * one is taken was caught with them. Put both agents down and the family goes on. What the player sees:
 * {@code docs/travellers.md}.
 */
public final class Pursuit extends CompanyOps.Op {
    /** Agents go for anyone this near the family, take a refugee from this close, and catch anyone this near with them. */
    public static final double GUARDING = 10, TAKE = 2.0, CAUGHT_WITH = 16;
    public static final float CAUGHT = 10, FOUGHT = 8;
    public static final int AGENTS = 2;
    /** An agent who has taken one needs this long before he can take another. */
    public static final int TAKE_EVERY = 60;

    final List<Villager> family;
    final Set<UUID> fought = new HashSet<>(), caughtWith = new HashSet<>();
    final java.util.Map<CompanyAgentEntity, Integer> nextTake = new java.util.HashMap<>();
    int taken;

    private Pursuit(ServerLevel level, ServerPlayer subject, List<Villager> family) {
        super(level, subject);
        this.family = family;
    }

    @Override public CompanyOps.Kind kind() { return CompanyOps.Kind.PURSUIT; }
    public int taken() { return taken; }

    /** Two agents after {@code family}, coming from {@code from}; null if {@code subject} already has an operation. */
    public static Pursuit start(ServerLevel level, ServerPlayer subject, List<Villager> family, Vec3 from) {
        if (CompanyOps.current(subject) != null || family.isEmpty()) return null;
        Pursuit op = new Pursuit(level, subject, family);
        Vec3 near = family.getFirst().position();
        if (!CompanyOps.ready(level, BlockPos.containing(from))) from = CompanyOps.ground(level, near.x + 12, near.z, near.y);
        op.keep(BlockPos.containing(near), "road");
        for (int i = 0; i < AGENTS; i++) {
            Vec3 at = CompanyOps.ground(level, from.x + (i * 2 - 1) * 1.5, from.z, from.y);
            op.spawn(CompanyAgentEntity.Role.GUARD, at, CompanyOps.yaw(at, near));
        }
        if (op.agents.isEmpty()) return null;
        CompanyOps.add(op);
        op.tell(near, 48, "Two men in dark suits are coming up the road behind them.");
        return op;
    }

    private List<Villager> left() { return family.stream().filter(v -> v.isAlive() && !v.isRemoved()).toList(); }

    @Override boolean lostTrack() {
        return super.lostTrack() || family.stream().anyMatch(CompanyOps::lost);
    }

    @Override void tick() {
        List<Villager> left = left();
        if (left.isEmpty()) {
            for (CompanyAgentEntity a : agents) CompanyOps.leave(a);
            finish(CompanyOps.AFTER_INVESTIGATION);
            return;
        }
        List<CompanyAgentEntity> standing = standing();
        if (standing.isEmpty()) { beaten(); return; }
        if (age % 20 == 0) keep(left.getFirst().blockPosition(), "road");
        for (CompanyAgentEntity a : standing) {
            Villager nearest = null;
            for (Villager v : left) {
                // An earlier agent in this same tick may already have taken someone from the snapshot.
                if (!v.isAlive() || v.isRemoved()) continue;
                if (nearest == null || v.distanceToSqr(a) < nearest.distanceToSqr(a)) nearest = v;
            }
            if (nearest == null) continue;
            if (a.distanceTo(nearest) <= TAKE && age >= nextTake.getOrDefault(a, 0)) { take(a, nearest); continue; }
            if (a.getTarget() == null && age % 10 == 0) a.walkTo(nearest.position(), 1.05);
        }
    }

    /** {@code a} reaches {@code v}: taken; anyone close by was caught with them. */
    private void take(CompanyAgentEntity a, Villager v) {
        taken++;
        nextTake.put(a, age + TAKE_EVERY);
        speak(a, "Got one.", 32);
        // Caught with them once a pursuit, however many are taken in front of you.
        for (ServerPlayer p : level.players())
            if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(v) <= CAUGHT_WITH * CAUGHT_WITH && caughtWith.add(p.getUUID())) {
                CompanyAttention.raise(p, CAUGHT, null, "caught with refugees");
                p.sendSystemMessage(Component.literal("The agent looks straight at you as he takes them. He'll remember your face.")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
            }
        com.jayptucker.heroesevolved.travel.Travellers.release(v, true);
    }

    /** Both agents down: the family goes on, and whoever fought them is noticed for it. */
    private void beaten() {
        for (ServerPlayer p : level.players())
            if (fought.contains(p.getUUID())) CompanyAttention.raise(p, FOUGHT, null, "fought the Company for refugees");
        Villager any = left().isEmpty() ? null : left().getFirst();
        tell(any != null ? any.position() : Vec3.atCenterOf(subject.blockPosition()), 48, "The agents are down. The family can go on.");
        finish(CompanyOps.AFTER_INVESTIGATION);
    }

    /** The family reached their village: the agents fall back. */
    public void reached() {
        if (over) return;
        for (CompanyAgentEntity a : standing()) speak(a, "They've reached the village. Fall back.", 32);
        for (CompanyAgentEntity a : agents) CompanyOps.leave(a);
        finish(CompanyOps.AFTER_INVESTIGATION);
    }

    /** The travelling party left or was cleared: nobody remains to pursue. */
    public void cancel() { callOff(); }

    @Override boolean stops(CompanyAgentEntity agent, ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return false;
        for (Villager v : left()) if (p.distanceToSqr(v) <= GUARDING * GUARDING) return true;
        return fought.contains(p.getUUID());
    }

    /** One of them was hurt by {@code p}. */
    void hurt(ServerPlayer p) { fought.add(p.getUUID()); }

    @Override void down(CompanyAgentEntity agent, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer p) fought.add(p.getUUID());
    }
}
