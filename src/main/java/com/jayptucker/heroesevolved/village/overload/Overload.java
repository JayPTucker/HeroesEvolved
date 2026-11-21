package com.jayptucker.heroesevolved.village.overload;

import com.jayptucker.heroesevolved.radio.EmergencyBand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One overload under way: a villager at the centre of their own storm. Run by {@link Overloads}. */
public final class Overload {
    /** Growing toward the break; the break's warning; burning itself out after it; dying away once calmed (or dampened). */
    public enum Stage { GROWING, BREAKING, BURNING, DYING }

    final Villager villager;
    final ServerLevel level;
    final Storm storm;
    final BlockPos bell;
    final int total;
    int fuse;
    float calm;
    Stage stage = Stage.GROWING;
    long stageAt;
    /** Ticks since it began, for the drawing's growth (the funnel rising). */
    int age;
    /** Dampened, not calmed: dying away with the gift gone. */
    boolean dampened;
    /** Who calmed them, for the message and the village's memory. */
    String calmedBy;
    net.minecraft.server.level.ServerPlayer calmer;
    final Set<UUID> village = new LinkedHashSet<>();
    final Set<UUID> dead = new HashSet<>();
    /** Who is calming them this tick (drawn as threads of light). */
    final List<ServerPlayer> helpers = new ArrayList<>();
    EmergencyBand.Call call;
    final ServerBossEvent bar;
    long heldAt = -1000, hitAt = -1000, nextShout = -1000;
    int shouts;
    /** The weather's own state (embers, the funnel's track). */
    Object weather;

    Overload(Villager villager, ServerLevel level, Storm storm, BlockPos bell, int total, int fuse) {
        this.villager = villager; this.level = level; this.storm = storm; this.bell = bell; this.total = total; this.fuse = fuse;
        bar = new ServerBossEvent(Component.literal(storm.title), storm.color, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setDarkenScreen(false);
    }

    public Villager villager() { return villager; }
    public ServerLevel level() { return level; }
    public Storm storm() { return storm; }
    public BlockPos bell() { return bell; }
    public int fuse() { return fuse; }
    public int total() { return total; }
    public float calm() { return calm; }
    public Stage stage() { return stage; }
    public int villageSize() { return village.size(); }
    public boolean inVillage(Entity e) { return village.contains(e.getUUID()); }
    public EmergencyBand.Call call() { return call; }
    public List<ServerPlayer> helpers() { return List.copyOf(helpers); }

    /** How much of the countdown has run. */
    public float progress() { return stage == Stage.GROWING ? 1 - fuse / (float) total : 1; }

    /** Ticks into the current stage. */
    public int stageAge() { return (int) (level.getGameTime() - stageAt); }

    /**
     * How hard it is blowing now: it grows with the countdown, eases as they calm, swells through the break's warning,
     * and falls away to nothing as it burns out or dies away.
     */
    public float power() {
        float base = OverloadGeometry.power(progress(), calm);
        return switch (stage) {
            case GROWING -> base;
            case BREAKING -> base + (1 - base) * Math.min(1, stageAge() / (float) OverloadGeometry.BREAK_WARNING);
            case BURNING -> 1 - Math.min(1, stageAge() / (float) OverloadGeometry.BURNOUT);
            case DYING -> base * (1 - Math.min(1, stageAge() / (float) OverloadGeometry.DIE_AWAY));
        };
    }

    /** Whether it can still hurt anyone (not while it dies away). */
    public boolean harms() { return stage != Stage.DYING; }

    /** Where they stand: the eye of the storm. */
    public Vec3 eye() { return villager.position(); }

    /** Members of the village not seen dying. */
    public int alive() {
        int n = 0;
        for (UUID id : village) {
            if (dead.contains(id)) continue;
            Entity e = level.getEntity(id);
            if (e == null || e.isAlive()) n++;
        }
        return n;
    }
}
