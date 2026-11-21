package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Company comes for someone it hunts: at night, at home. Thirty seconds of warning (a radio in the dark), then a
 * Suppressor and three armed agents (one more for each other player there) come out of the dark {@link #DISTANCE} blocks
 * away and close in. The Suppressor makes for the subject, and his quiet goes with him. It ends when every agent is
 * down, the subject goes down, the sun comes up, or the subject leaves.
 */
public final class Raid extends CompanyOps.Op {
    public enum Stage { WARNING, COMING, WITHDRAWING, BEATEN }
    public static final int WARN_TICKS = 600, WITHDRAW_TICKS = 200, GUARDS = 3, MOST_GUARDS = 5;
    /** They come from this far; they stop anyone this near home; the subject this far from home calls it off. */
    public static final double DISTANCE = 36, REACH = 48, GONE = 160;

    final BlockPos home;
    Stage stage = Stage.WARNING;
    int stageAge;
    Vec3 from, lastSuppressor;
    CompanyAgentEntity suppressor;
    final List<CompanyAgentEntity> guards = new ArrayList<>();
    /** A raid that started at night ends at dawn; one started by day (admin, tests) doesn't. */
    final boolean byNight;

    private Raid(ServerLevel level, ServerPlayer subject, BlockPos home, Vec3 from) {
        super(level, subject);
        this.home = home.immutable();
        this.from = from;
        this.byNight = CompanyOps.night(level);
    }

    @Override public CompanyOps.Kind kind() { return CompanyOps.Kind.RAID; }
    public Stage stage() { return stage; }
    public CompanyAgentEntity suppressor() { return suppressor; }
    public List<CompanyAgentEntity> guards() { return List.copyOf(guards); }
    public BlockPos home() { return home; }

    /** A raid on {@code subject} at {@code home}, coming from {@code from} (null: {@link #DISTANCE} out, wherever there's ground). */
    public static Raid start(ServerLevel level, ServerPlayer subject, BlockPos home, Vec3 from) {
        if (CompanyOps.current(subject) != null || !CompanyOps.prepare(level, home, subject)) return null;
        Raid op = new Raid(level, subject, home, from);
        op.keep(home, "home");
        CompanyOps.add(op);
        op.tell(Vec3.atCenterOf(home), 64, "Out in the dark, a radio crackles. A calm voice: \"All teams, the house is lit. Suppressor goes in first.\"");
        level.playSound(null, home, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE, 0.6F, 0.5F);
        return op;
    }

    private void to(Stage next) {
        stage = next;
        stageAge = 0;
    }

    /** A raid under way within {@code r} of {@code at}, or null. */
    public static Raid near(ServerLevel level, BlockPos at, double r) {
        for (CompanyOps.Op op : CompanyOps.all())
            if (op instanceof Raid raid && !raid.over() && raid.level == level && raid.home.distSqr(at) <= r * r) return raid;
        return null;
    }

    /**
     * Its subject's attention is left at {@code value}; with {@code everyone}, so is that of anyone else at home who fought
     * it (a shared base's raid counts for all who were there), never raised.
     */
    private void settle(float value, boolean everyone) {
        CompanyAttention.set(subject, value);
        if (!everyone) return;
        for (ServerPlayer p : level.players())
            if (p != subject && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(Vec3.atCenterOf(home)) <= REACH * REACH
                    && CompanyAttention.of(p) > value) CompanyAttention.set(p, value);
    }

    /** Skips the warning (tests, previews). */
    public void now() { if (stage == Stage.WARNING) stageAge = WARN_TICKS; }

    @Override void tick() {
        stageAge++;
        switch (stage) {
            case WARNING -> { if (stageAge >= WARN_TICKS) arrive(); }
            case COMING -> coming();
            case WITHDRAWING -> {
                for (CompanyAgentEntity a : standing()) {
                    a.setTarget(null);
                    a.walkTo(from, 1.0);
                }
                if (stageAge >= WITHDRAW_TICKS) {
                    for (CompanyAgentEntity a : agents) CompanyOps.leave(a);
                    finish(CompanyOps.AFTER_OP);
                }
            }
            case BEATEN -> { }
        }
    }

    private void arrive() {
        Vec3 h = Vec3.atBottomCenterOf(home);
        if (from == null) {
            double a0 = level.random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < 8 && from == null; i++) {
                Vec3 at = CompanyOps.out(level, h, a0 + i * Math.PI / 4, DISTANCE);
                if (level.hasChunkAt(BlockPos.containing(at))) from = at;
            }
            if (from == null) from = CompanyOps.out(level, h, a0, 16);
        }
        int extra = 0;
        for (ServerPlayer p : level.players())
            if (p != subject && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(h) <= REACH * REACH) extra++;
        int count = Math.min(MOST_GUARDS, GUARDS + extra);
        Vec3 toward = h.subtract(from).multiply(1, 0, 1).normalize(), side = new Vec3(-toward.z, 0, toward.x);
        // The Suppressor a step ahead, the rest in a line abreast.
        Vec3 point = CompanyOps.ground(level, from.x + toward.x * 2, from.z + toward.z * 2, from.y);
        suppressor = spawn(CompanyAgentEntity.Role.SUPPRESSOR, point, CompanyOps.yaw(point, h));
        for (int i = 0; i < count; i++) {
            double across = (i - (count - 1) / 2.0) * 3;
            Vec3 at = CompanyOps.ground(level, from.x + side.x * across, from.z + side.z * across, from.y);
            CompanyAgentEntity g = spawn(CompanyAgentEntity.Role.GUARD, at, CompanyOps.yaw(at, h));
            if (g != null) guards.add(g);
        }
        if (suppressor == null) { callOff(); return; }
        lastSuppressor = suppressor.position();
        to(Stage.COMING);
    }

    private void coming() {
        if (!CompanyOps.downed(suppressor)) lastSuppressor = suppressor.position();
        List<CompanyAgentEntity> standing = standing();
        if (standing.isEmpty()) { beaten(); return; }
        if (subject.isDeadOrDying()) {
            withdraw(CompanyAttention.RAID_WON, false, "They check you're down, then pull back into the dark.");
            return;
        }
        if (byNight && !CompanyOps.night(level)) {
            withdraw(CompanyAttention.RAID_DAWN, true, "Dawn comes up grey. The agents pull back into the trees and are gone.");
            return;
        }
        if (subject.level() != level || subject.distanceToSqr(Vec3.atCenterOf(home)) > GONE * GONE) {
            withdraw(-1, false, "Out in the dark, someone says \"Not tonight.\" The agents fade back the way they came.");
            return;
        }
        if (stageAge % 10 != 0) return;
        Vec3 h = Vec3.atBottomCenterOf(home);
        for (CompanyAgentEntity a : standing) {
            if (a.getTarget() != null) continue;
            // The Suppressor makes for the subject; the rest for the house, then for whoever is in it.
            Vec3 to = a == suppressor || a.distanceToSqr(h) < 64 ? subject.position() : h;
            a.walkTo(to, 1.0);
        }
    }

    @Override boolean stops(CompanyAgentEntity agent, ServerPlayer p) {
        return stage == Stage.COMING && !p.isCreative() && !p.isSpectator()
                && (p.distanceToSqr(Vec3.atCenterOf(home)) <= REACH * REACH || p.distanceToSqr(agent) <= 24 * 24);
    }

    private void beaten() {
        to(Stage.BEATEN);
        settle(CompanyAttention.RAID_BEATEN, true);
        List<ItemStack> spoils = new ArrayList<>();
        spoils.add(PowerWeapons.gun(PowerWeapons.DAMPENER_GUN));
        spoils.add(PowerWeapons.ammo(PowerWeapons.DAMPENER_ROUND, 6));
        spoils.add(PowerWeapons.ammo(PowerWeapons.TASER_CARTRIDGE, 4));
        CompanyOps.drop(level, lastSuppressor != null ? lastSuppressor : Vec3.atCenterOf(home), spoils);
        tell(Vec3.atCenterOf(home), 96, "The last of them goes down. Out in the dark, an engine starts and drives away.");
        com.jayptucker.heroesevolved.radio.BandChatter.news(level, home, "Gunfire out at " + subject.getName().getString()
                + "'s place last night. They say the men in suits came off worst.", 48_000);
        finish(CompanyOps.AFTER_OP);
    }

    /** They pull back the way they came; attention is left at {@code value} (or as it is, if negative). */
    private void withdraw(float value, boolean everyone, String line) {
        if (value >= 0) settle(value, everyone);
        tell(Vec3.atCenterOf(home), 96, line);
        com.jayptucker.heroesevolved.radio.BandChatter.news(level, home, "Lights and gunfire out at " + subject.getName().getString()
                + "'s place last night. Nobody's saying what it was.", 48_000);
        to(Stage.WITHDRAWING);
    }
}
