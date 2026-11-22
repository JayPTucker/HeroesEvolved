package com.jayptucker.heroesevolved.travel;

import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.village.VillageMemory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A party on the road: who is in it, where it is going, and how far along. It moves as one point along a straight line
 * from where it set out to its village, at a walking pace, waiting for its leader; each member walks to its place round
 * the point. Arrived, it stays a while, then goes on its way. Nobody near for {@link #UNSEEN} ticks and it is gone (it
 * reached its village unseen). The kinds ({@link Pedlar}, {@link Healer}, {@link Caravan}, {@link Refugees}) say who it
 * is and what it does. What the player sees: {@code docs/travellers.md}.
 */
public abstract class Party {
    public enum Kind { PEDLAR, HEALER, CARAVAN, REFUGEES }
    public enum Stage { ROAD, STAYING, LEAVING, OVER }
    /** Its pace (blocks a tick), how near its bell it has arrived, how long it stays, how far it is told of. */
    public static final double PACE = 0.05, ARRIVED = 12, TOLD = 24, SEEN = 160;
    public static final int UNSEEN = 2400, STAY = 6000, LEAVE = 400;

    final ServerLevel level;
    final BlockPos bell;
    final String village;
    final Vec3 start, heading;
    final double length;
    final List<Mob> members = new ArrayList<>();
    final Set<UUID> told = new HashSet<>();
    double along;
    Vec3 center;
    Stage stage = Stage.ROAD;
    int age, stageAge, unseenFor;

    Party(ServerLevel level, Vec3 start, BlockPos bell) {
        this.level = level;
        this.bell = bell.immutable();
        this.village = VillageMemory.of(level, bell).name();
        this.start = start;
        Vec3 to = Vec3.atBottomCenterOf(bell).subtract(start).multiply(1, 0, 1);
        this.length = Math.max(0, to.length() - ARRIVED);
        this.heading = to.lengthSqr() < 1 ? new Vec3(1, 0, 0) : to.normalize();
        this.center = start;
    }

    public abstract Kind kind();
    public Stage stage() { return stage; }
    public List<Mob> members() { return List.copyOf(members); }
    public BlockPos bell() { return bell; }
    public Vec3 center() { return center; }
    public double along() { return along; }

    /** What a player is told the first time they come near. */
    abstract String intro();

    /** Its own business each tick (healing, an ambush, a pursuit). */
    void business() {}

    /** It has reached its village. */
    void arrive() {}

    /** Everyone that matters is gone before it got there. */
    void fell() {}

    /** Whether everyone that matters is gone (dead or taken), and it is over. */
    boolean lost() {
        for (Mob m : members) if (m.isAlive() && !m.isRemoved()) return false;
        return true;
    }

    /** The member everyone else waits for. */
    Mob lead() {
        for (Mob m : members) if (m.isAlive() && !m.isRemoved()) return m;
        return null;
    }

    final void tick() {
        age++;
        stageAge++;
        // An unloaded member is still alive in its saved chunk. End without a loss or delivery, and let the leftover
        // check turn them away if they load again: continuing would leave the party holding a stale entity reference.
        if (members.stream().anyMatch(CompanyOps::lost)) { gone(); return; }
        if (lost()) { fell(); over(); return; }
        tell();
        boolean anyone = false;
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.position().distanceToSqr(center) <= SEEN * SEEN) anyone = true;
        unseenFor = anyone ? 0 : unseenFor + 1;
        if (unseenFor >= UNSEEN) { gone(); return; }
        business();
        if (stage == Stage.OVER) return;
        switch (stage) {
            case ROAD -> {
                Mob lead = lead();
                if (lead != null && lead.position().distanceToSqr(slot(lead, 0)) <= 16) along = Math.min(length, along + PACE);
                Vec3 flat = start.add(heading.scale(along));
                if (age % 5 == 0) center = CompanyOps.ground(level, flat.x, flat.z, center.y);
                if (age % 5 == 0) walk();
                if (along >= length) { stage(Stage.STAYING); arrive(); }
            }
            case STAYING -> {
                if (age % 100 == 0) walkNearBell();
                if (stageAge >= stay()) leave();
            }
            case LEAVING -> {
                if (stageAge >= LEAVE) gone();
            }
            case OVER -> { }
        }
    }

    /** How long it stays at its village. */
    int stay() { return STAY; }

    void stage(Stage s) {
        stage = s;
        stageAge = 0;
    }

    /** Where member {@code i} of the party walks, round its point. */
    Vec3 slot(Mob m, int i) {
        Vec3 right = new Vec3(-heading.z, 0, heading.x);
        int index = members.indexOf(m);
        Vec3 at = center.subtract(heading.scale(index * 1.6)).add(right.scale(index % 2 == 0 ? 0.6 : -0.6));
        return CompanyOps.ground(level, at.x, at.z, center.y);
    }

    /** Each member to its place. */
    void walk() {
        for (Mob m : members) if (m.isAlive() && !m.isRemoved()) goTo(m, slot(m, 0), 0.6F);
    }

    void walkNearBell() {
        for (Mob m : members) {
            if (!m.isAlive() || m.isRemoved()) continue;
            Vec3 near = Vec3.atBottomCenterOf(bell).add(level.random.nextInt(17) - 8, 0, level.random.nextInt(17) - 8);
            goTo(m, CompanyOps.ground(level, near.x, near.z, bell.getY()), 0.45F);
        }
    }

    /** Walks {@code m} to {@code to}: a villager through its brain, anything else by its navigation. */
    static void goTo(Mob m, Vec3 to, float speed) {
        if (m instanceof Villager v) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, speed, 1));
        else if (!m.isLeashed()) m.getNavigation().moveTo(to.x, to.y, to.z, speed * 1.6);
    }

    /** On its way out of the village, gone shortly. */
    void leave() {
        stage(Stage.LEAVING);
        Vec3 away = Vec3.atBottomCenterOf(bell).add(heading.scale(40));
        for (Mob m : members) if (m.isAlive() && !m.isRemoved()) goTo(m, CompanyOps.ground(level, away.x, away.z, bell.getY()), 0.6F);
    }

    /** Gone on its way: its people leave the world (they are somewhere else now). */
    void gone() {
        for (Mob m : members) Travellers.release(m, true);
        over();
    }

    void over() {
        for (Mob m : members) Travellers.release(m, false);
        stage = Stage.OVER;
    }

    /** Players coming near for the first time hear who it is. */
    void tell() {
        if (age % 20 != 0 || stage == Stage.LEAVING) return;
        for (ServerPlayer p : level.players())
            if (!p.isSpectator() && p.position().distanceToSqr(center) <= TOLD * TOLD && told.add(p.getUUID())) {
                p.sendSystemMessage(Component.literal(intro()).withStyle(ChatFormatting.YELLOW));
                met(p);
            }
    }

    /** A player has come near for the first time. */
    void met(ServerPlayer p) {}

    /** Players within {@code r} of its point. */
    List<ServerPlayer> near(double r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.position().distanceToSqr(center) <= r * r) out.add(p);
        return out;
    }

    /** Moves it {@code blocks} along at once (tests, previews). */
    public void hurry(double blocks) {
        along = Math.min(length, along + blocks);
        Vec3 flat = start.add(heading.scale(along));
        center = CompanyOps.ground(level, flat.x, flat.z, center.y);
    }
}
