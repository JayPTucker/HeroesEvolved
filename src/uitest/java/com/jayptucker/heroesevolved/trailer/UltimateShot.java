package com.jayptucker.heroesevolved.trailer;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.combat.Ultimates;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * A power's Offense Ultimate (its class cinematic, {@code combat/Ultimates}), staged as {@code uitest/UltimatePreview}
 * stages it: a pillager in front to be held, a vindicator beside it. **The Ultimate directs:** it flies its own keyed
 * camera ({@code client/UltimateCamera}), made for the move, so this shot plays no camera move of its own. Its
 * letterbox bars (a tenth of the screen each) sit inside the grade's 2.39:1 bars and never show.
 */
final class UltimateShot extends Shot {
    /** Ticks of the shot before the cast: the fighter facing the foe, the camera behind them. */
    static final int LEAD = 8;
    /** The longest Offense Ultimate ends inside 140 ticks. */
    static final int RUN = 146;

    private final ResourceLocation power;
    private final String mechanic;
    private double slowFrom = -1, slowTo = -1, low = 1;

    UltimateShot(String name, ResourceLocation power, String mechanic, long time) {
        super(name, LEAD + RUN, time);
        this.power = power;
        this.mechanic = mechanic;
    }

    /** Slow motion from {@code from} to {@code to} ticks after the cast. */
    UltimateShot slowly(double from, double to, double low) {
        this.slowFrom = from;
        this.slowTo = to;
        this.low = low;
        return this;
    }

    @Override void cast(ServerPlayer p) {
        String node = SkillCatalog.styles(power.getPath()).stream().filter(n -> mechanic.equals(n.mechanic()))
                .map(SkillCatalog.Node::id).findFirst().orElse(null);
        if (node == null) PreviewQuiet.report("TRAILER FAIL no class node casts " + mechanic);
        TrailerPreview.grant(p, power, node, -1);
        Stage.puppet(p, EntityType.PILLAGER, 0, 4);
        Stage.puppet(p, EntityType.VINDICATOR, 2.5, 6);
        Stage.aim(p, Stage.at(0, 1.2, 4));
    }

    /** None: the Ultimate's own camera. */
    @Override FilmCamera.Move camera() { return null; }

    @Override Supplier<Vec3> follow(Minecraft mc) { return null; }

    @Override double speed(double tick) {
        return slowFrom < 0 ? 1 : slow(tick, LEAD + slowFrom, LEAD + slowTo, low);
    }

    @Override void act(ServerPlayer p, int tick) {
        if (tick != LEAD) return;
        var context = new AbilityUseContext(p, power, 5, false);
        PreviewQuiet.check("TRAILER_ULTIMATE", Ultimates.canUse(context, mechanic) && Ultimates.cast(context, mechanic), name + " casts");
        Stage.mark("cast");
    }
}
