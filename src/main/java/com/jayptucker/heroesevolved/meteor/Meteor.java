package com.jayptucker.heroesevolved.meteor;

import com.jayptucker.heroesevolved.radio.EmergencyBand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One meteor in the sky: a falling star, or a great meteor coming down on a village. Run by {@link Meteors}. */
public final class Meteor {
    /** What someone is doing to it: pushing it aside (Telekinesis, or Flight's wind), holding it, cracking it, bracing. */
    public enum Help { PUSH, WIND, HOLD, CRACK, BRACE }

    /** A player working on it this tick, and how. */
    public record Helper(ServerPlayer player, Help help) {}

    final int id;
    final ServerLevel level;
    final boolean great;
    final Vec3 start;
    Vec3 land;
    final int fall;
    int elapsed;
    /** How far it has been pushed, how long it has been cracked and braced; whether something holds it this tick. */
    double pushed;
    int crack, brace;
    boolean held;
    final List<Helper> helpers = new ArrayList<>();
    /** The last player to work on it (pushing, holding or cracking it): who a village thanks. */
    ServerPlayer lastHelper;
    /** What a great meteor's village had standing where it lands, to rebuild if it survives. */
    com.jayptucker.heroesevolved.village.Rebuild.Site site;
    /** A great meteor's village: its bell, who belongs, who died. */
    final BlockPos bell;
    final Set<UUID> village = new LinkedHashSet<>();
    final Set<UUID> dead = new HashSet<>();
    EmergencyBand.Call call;
    final ServerBossEvent bar;
    long nextShout = -1000;
    int shouts;

    Meteor(int id, ServerLevel level, boolean great, Vec3 start, Vec3 land, int fall, BlockPos bell) {
        this.id = id; this.level = level; this.great = great; this.start = start; this.land = land; this.fall = Math.max(1, fall);
        this.bell = bell;
        bar = new ServerBossEvent(Component.literal("Great meteor"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setDarkenScreen(false);
    }

    public int id() { return id; }
    public boolean great() { return great; }
    public Vec3 land() { return land; }
    public Vec3 start() { return start; }
    public BlockPos bell() { return bell; }
    public int fall() { return fall; }
    public int elapsed() { return elapsed; }
    public int left() { return Math.max(0, fall - elapsed); }
    public double pushed() { return pushed; }
    public EmergencyBand.Call call() { return call; }
    public List<Helper> helpers() { return List.copyOf(helpers); }
    public boolean inVillage(Entity e) { return village.contains(e.getUUID()); }
    public int villageSize() { return village.size(); }

    /** How much of its fall it has come. */
    public float fallen() { return elapsed / (float) fall; }

    /** Where it is in the sky now ({@link MeteorGeometry#position}, as drawn). */
    public Vec3 sky() { return MeteorGeometry.position(start, land, fallen()); }

    /** How far through cracking it is (a falling star cracks four times as fast). */
    public float cracked() { return Math.min(1, crack / (float) (great ? MeteorGeometry.SHATTER_TICKS : MeteorGeometry.SHATTER_TICKS / 4)); }

    public float braced() { return Math.min(1, brace / (float) MeteorGeometry.BRACE_TICKS); }

    /** Members of its village not seen dying. */
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
