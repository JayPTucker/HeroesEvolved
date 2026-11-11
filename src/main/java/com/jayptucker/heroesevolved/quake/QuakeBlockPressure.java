package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.expansion.FacilityService;
import com.jayptucker.heroesevolved.expansion.PowerReactions;
import com.jayptucker.heroesevolved.story.night.NightThreeService;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Held vibration grinding blocks down. While Quake Blast is held, or an Earthquake runs, the blocks it
 * works on crack with vanilla's mining overlay, the harder ones more slowly, until they break and drop.
 * A crack nothing works on any more holds for a moment, then fades back out, so letting go never breaks
 * anything.
 *
 * <p>Everything is bounded: blocks worked per tick, cracks kept per player, and breaks per tick. A block
 * is only broken through {@link WorldMutationPolicy#canEdit}, at the moment it gives, so claim mods and
 * spawn protection still decide; the break event is never posted just to ask, because temporal echoes
 * record every one. Breaks go through {@code Level.destroyBlock}, so time rewinds journal them.
 */
public final class QuakeBlockPressure {
    /** Blocks one player's powers can work on in a single tick. */
    static final int MAX_WORKED_PER_TICK = 48;
    /** Blocks around the beam's impact it works at once. */
    static final int BEAM_BLOCKS = 9;
    /** Cracks one player can have open at once. */
    static final int MAX_CRACKS = 96;
    static final int MAX_BREAKS_PER_PLAYER = 3;
    static final int MAX_BREAKS_PER_TICK = 12;
    /** Ticks a crack holds after it was last worked before it starts to fade. */
    static final int GRACE_TICKS = 8;
    /** A fully cracked block fades out in two seconds. */
    static final float FADE_PER_TICK = 1.0F / 40.0F;
    /** Vanilla forgets a crack overlay it hasn't heard about for 400 ticks. */
    private static final int REFRESH_TICKS = 200;

    private static final class Crack {
        private final BlockPos pos;
        private final Block block;
        private final int animation;
        /** Progress per tick of full-strength work. */
        private final float rate;
        private float progress;
        private int stage = -1;
        private long shownAt;
        private long workedAt = Long.MIN_VALUE;
        private long fadeAt;
        /** The world's rules refused the break: the crack stays closed while it is still worked. */
        private boolean refused;

        private Crack(BlockPos pos, Block block, int animation, float rate) {
            this.pos = pos;
            this.block = block;
            this.animation = animation;
            this.rate = rate;
        }
    }

    private static final class Field {
        private final ServerPlayer owner;
        private final ServerLevel level;
        private final LinkedHashMap<BlockPos, Crack> cracks = new LinkedHashMap<>();
        private int worked;

        private Field(ServerPlayer owner, ServerLevel level) {
            this.owner = owner;
            this.level = level;
        }
    }

    private static final Map<UUID, Field> FIELDS = new HashMap<>();
    /** Counts this class's own ticks, so cracks age the same however they are driven. */
    private static long clock;
    // Negative, so the overlay never collides with an entity id (vanilla's breaker ids).
    private static int nextAnimation = -1_000;

    private QuakeBlockPressure() {
    }

    /** Ticks of full-strength work a block of this hardness takes: stone 4 seconds, dirt 2, obsidian over 100. */
    static float ticksToBreak(float hardness) {
        return Math.clamp(20.0F + hardness * 40.0F, 10.0F, 6_000.0F);
    }

    /**
     * One tick of the held beam landing at {@code impact} on {@code hit}. The struck block takes the full
     * {@code strength}; its neighbours take less the farther they sit from the impact, so a held beam bores
     * in and slowly widens the hole.
     */
    public static void beam(ServerPlayer player, Vec3 impact, BlockPos hit, float strength) {
        ServerLevel level = player.serverLevel();
        if (!enabled(level)) {
            return;
        }
        FacilityService.technique(player, hit, "fracture");

        record Target(BlockPos pos, double weight) {
        }
        List<Target> targets = new ArrayList<>(27);
        for (BlockPos pos : BlockPos.betweenClosed(hit.offset(-1, -1, -1), hit.offset(1, 1, 1))) {
            double weight = pos.equals(hit) ? 1.0D : falloff(Vec3.atCenterOf(pos).distanceTo(impact));
            if (weight >= 0.1D) {
                targets.add(new Target(pos.immutable(), weight));
            }
        }
        targets.sort(Comparator.comparingDouble(Target::weight).reversed());
        int worked = 0;
        for (Target target : targets) {
            if (worked >= BEAM_BLOCKS) {
                break;
            }
            if (work(player, target.pos(), (float) (strength * target.weight()))) {
                worked++;
            }
        }
    }

    /** Full strength within three quarters of a block of the impact, none past 1.9 blocks. */
    static double falloff(double distance) {
        if (distance <= 0.75D) {
            return 1.0D;
        }
        double weight = (1.9D - distance) / 1.15D;
        return weight <= 0.0D ? 0.0D : weight * weight;
    }

    /**
     * Works one block for a tick: {@code strength} 1 is one tick of full-strength work. Returns whether
     * the block took the work (false for air, fluids, unbreakable blocks, protected ground, or past a cap).
     */
    public static boolean work(ServerPlayer owner, BlockPos pos, float strength) {
        ServerLevel level = owner.serverLevel();
        if (!enabled(level) || !level.hasChunkAt(pos)) {
            return false;
        }
        Field field = FIELDS.get(owner.getUUID());
        if (field != null && (field.owner != owner || field.level != level)) {
            hideAll(field);
            FIELDS.remove(owner.getUUID());
            field = null;
        }

        Crack crack = field == null ? null : field.cracks.get(pos);
        if (crack == null) {
            BlockState state = level.getBlockState(pos);
            if (!breakable(level, pos, state) || !mayCrack(owner, level, pos)) {
                return false;
            }
            if (field == null) {
                field = new Field(owner, level);
                FIELDS.put(owner.getUUID(), field);
            }
            if (field.cracks.size() >= MAX_CRACKS || field.worked >= MAX_WORKED_PER_TICK) {
                return false;
            }
            crack = new Crack(pos.immutable(), state.getBlock(), nextAnimation--,
                    1.0F / ticksToBreak(state.getDestroySpeed(level, pos)));
            field.cracks.put(crack.pos, crack);
        }

        if (crack.workedAt != clock) {
            if (field.worked >= MAX_WORKED_PER_TICK) {
                return false;
            }
            field.worked++;
            crack.workedAt = clock;
        }
        crack.fadeAt = clock + GRACE_TICKS;
        if (crack.refused) {
            return false;
        }

        float rate = crack.rate;
        // A seam already found by Seamfinder sings back: it gives three times as fast.
        if (PowerReactions.resonating(level, crack.pos)) {
            rate *= 3.0F;
            FacilityService.discovery(owner, "seam_chorus");
        }
        crack.progress = Math.min(1.0F, crack.progress + Math.max(0.0F, strength) * rate);
        show(level, crack);
        return true;
    }

    /** The key was let go: nothing more is being worked, so every crack starts to fade now. */
    public static void release(ServerPlayer player) {
        Field field = FIELDS.get(player.getUUID());
        if (field != null) {
            for (Crack crack : field.cracks.values()) {
                crack.fadeAt = Math.min(crack.fadeAt, clock);
            }
        }
    }

    /** Removes every crack at once (death, logout, a change of dimension, or the power taken away). */
    public static void clear(ServerPlayer player) {
        Field field = FIELDS.remove(player.getUUID());
        if (field != null) {
            hideAll(field);
        }
    }

    public static void clearAll() {
        FIELDS.values().forEach(QuakeBlockPressure::hideAll);
        FIELDS.clear();
    }

    /** Breaks blocks that gave way, fades cracks nothing works any more, and drops stale ones. */
    public static void tick() {
        int breaks = 0;
        Iterator<Field> fields = FIELDS.values().iterator();
        while (fields.hasNext()) {
            Field field = fields.next();
            ServerLevel level = field.level;
            if (field.owner.isRemoved() || field.owner.level() != level) {
                hideAll(field);
                fields.remove();
                continue;
            }

            int ownBreaks = 0;
            Iterator<Crack> cracks = field.cracks.values().iterator();
            while (cracks.hasNext()) {
                Crack crack = cracks.next();
                // Mined, replaced, or unloaded meanwhile: that crack is gone.
                if (!level.hasChunkAt(crack.pos) || !level.getBlockState(crack.pos).is(crack.block)) {
                    hide(level, crack);
                    cracks.remove();
                    continue;
                }
                if (crack.refused) {
                    if (clock >= crack.fadeAt) {
                        cracks.remove();
                    }
                    continue;
                }
                if (crack.progress >= 1.0F) {
                    if (ownBreaks >= MAX_BREAKS_PER_PLAYER || breaks >= MAX_BREAKS_PER_TICK) {
                        continue;
                    }
                    hide(level, crack);
                    if (WorldMutationPolicy.canEdit(field.owner, level, crack.pos)
                            && level.destroyBlock(crack.pos, true, field.owner)) {
                        ownBreaks++;
                        breaks++;
                        cracks.remove();
                    } else {
                        crack.refused = true;
                        crack.progress = 0.0F;
                    }
                    continue;
                }
                if (clock >= crack.fadeAt) {
                    crack.progress -= FADE_PER_TICK;
                    if (crack.progress <= 0.0F) {
                        hide(level, crack);
                        cracks.remove();
                        continue;
                    }
                    show(level, crack);
                }
            }
            field.worked = 0;
            if (field.cracks.isEmpty()) {
                fields.remove();
            }
        }
        clock++;
    }

    /** How far this player's powers have cracked a block, 0 to 1, or -1 when it isn't cracking. */
    public static float progress(ServerPlayer player, BlockPos pos) {
        Field field = FIELDS.get(player.getUUID());
        Crack crack = field == null ? null : field.cracks.get(pos);
        return crack == null || crack.refused ? -1.0F : crack.progress;
    }

    /** The crack overlay stage shown on a block, 0 to 9, or -1 for none. */
    public static int stage(ServerPlayer player, BlockPos pos) {
        Field field = FIELDS.get(player.getUUID());
        Crack crack = field == null ? null : field.cracks.get(pos);
        return crack == null ? -1 : crack.stage;
    }

    /** Blocks this player's powers are cracking right now. */
    public static int cracking(ServerPlayer player) {
        return cracked(player).size();
    }

    /** Where this player's powers are cracking blocks right now. */
    public static List<BlockPos> cracked(ServerPlayer player) {
        Field field = FIELDS.get(player.getUUID());
        return field == null ? List.of()
                : field.cracks.values().stream().filter(crack -> !crack.refused).map(crack -> crack.pos).toList();
    }

    private static boolean enabled(ServerLevel level) {
        // Snapshots mirror block changes into the Present, and the story's set pieces are kept whole.
        return HeroesEvolvedConfig.COMMON.quakeBreaksBlocks.get()
                && !TemporalSnapshotService.isTemporalDimension(level)
                && !TimeIntroService.isCity(level)
                && !NightThreeService.inNight(level);
    }

    /** Never air, fluids, or anything unbreakable: bedrock, barriers, end portal frames, reinforced deepslate. */
    static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.is(BlockTags.WITHER_IMMUNE)
                && !com.jayptucker.heroesevolved.story.LastSignalService.protectedBlock(level, pos);
    }

    /** The side-effect-free rules: build rights, spawn protection, the border and build height. */
    private static boolean mayCrack(ServerPlayer owner, ServerLevel level, BlockPos pos) {
        return owner.mayBuild() && !level.isOutsideBuildHeight(pos)
                && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(owner, pos);
    }

    private static void show(ServerLevel level, Crack crack) {
        int stage = Math.min(9, (int) (crack.progress * 10.0F));
        if (stage != crack.stage || clock - crack.shownAt >= REFRESH_TICKS) {
            level.destroyBlockProgress(crack.animation, crack.pos, stage);
            crack.stage = stage;
            crack.shownAt = clock;
        }
    }

    private static void hide(ServerLevel level, Crack crack) {
        if (crack.stage >= 0) {
            level.destroyBlockProgress(crack.animation, crack.pos, -1);
            crack.stage = -1;
        }
    }

    private static void hideAll(Field field) {
        field.cracks.values().forEach(crack -> hide(field.level, crack));
    }
}
