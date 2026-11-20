package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.client.effects.setpiece.Canvas;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.client.effects.setpiece.Shades;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity.Role;
import com.jayptucker.heroesevolved.story.prologue.LastDayFight;
import com.jayptucker.heroesevolved.story.prologue.LastDayFight.Beat;
import com.jayptucker.heroesevolved.story.prologue.PrologueTimeline;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.SkyFight;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The Last Day's light, fight and wave as this client sees them, all from the shared script ({@link LastDayFight},
 * {@link PrologueTimeline}) and the light's start, so they read from the far end of the avenue as well as from under them:
 * <ul>
 *   <li>Ted Sprague on his knees in the middle of the avenue at ground zero, burning, with shock waves rolling off him in
 *   Induced Radioactivity's colours, further and harder each time: a hot ring racing over the street, a sickly one behind
 *   it, a wall of heat haze and dust. Then the big one, which crosses the whole city.</li>
 *   <li>Peter and Sylar as themselves (their own skins and poses), Peter trailing condensation and Sylar dark smoke, their
 *   clashes drawn with the powers' own pieces: Flight's pressure fronts, Elle's lightning, the Watchmaker's grip.</li>
 *   <li>The chunks Sylar tears out of the facades, tumbling through the air, and the stone and glass that come down on the
 *   sidewalk where they hit.</li>
 * </ul>
 * Every boom reaches you as late as sound would.
 */
public final class LastDayArt {
    private static final float[] VAPOR = Canvas.rgb(0xDCDDD5), SMOKE = Canvas.rgb(0x2A1416), EMBER = Canvas.rgb(0xFF4A0C),
            HEAT = Canvas.rgb(0xFF9330), WHITE_HOT = Canvas.rgb(0xFFF2D2), TOXIC = Canvas.rgb(0xC4F23C), HAZE = Canvas.rgb(0xFFD7A8),
            DUST = Canvas.rgb(0x9A968E), CORE = Canvas.rgb(0xF6F4EC);

    private static StoryActorEntity peter, sylar, ted;
    private static ClientLevel actorsOf;
    /** The facades' blocks, read when the light came, for the chunks Sylar tears out of them. */
    private static BlockState[] chunkStone = new BlockState[2];
    /** Beats already played for this light (rejoins and jumps don't replay a backlog). */
    private static final Set<Beat> played = EnumSet.noneOf(Beat.class);
    private static int pulsesSeen = -1;
    private static long playedFor = Long.MIN_VALUE;
    /** Sounds on their way to this ear: when each lands. */
    private record Heard(float at, Vec3 where, SoundEvent sound, float volume, float pitch) {}
    private static final List<Heard> HEARING = new ArrayList<>();

    private LastDayArt() {}

    /** While the Last Day's light is up. */
    public static boolean live() {
        return IntroScene.prologue && IntroScene.light >= 0 && Minecraft.getInstance().level != null;
    }

    private static float since(float partial) { return IntroScene.sinceLight(partial); }

    /** The light came (or a jump moved its start): the facades' stone is read now, before anything breaks. */
    static void lightCame() {
        ClientLevel level = Minecraft.getInstance().level;
        LastDayFight.Facades f = IntroScene.facades;
        for (int i = 0; i < 2; i++) {
            Vec3 at = i == 0 ? f.grabWest() : f.grabEast();
            BlockState s = level == null ? null : level.getBlockState(BlockPos.containing(at.x + (i == 0 ? -0.6 : 0.6), at.y, at.z));
            chunkStone[i] = s == null || s.isAir() ? Blocks.STONE_BRICKS.defaultBlockState() : s;
        }
        played.clear();
        HEARING.clear();
        pulsesSeen = -1;
        playedFor = IntroScene.light;
    }

    static void reset() {
        peter = sylar = ted = null;
        actorsOf = null;
        played.clear();
        HEARING.clear();
        pulsesSeen = -1;
    }

    // ------------------------------------------------------------------
    // Every tick: the beats, and the sounds arriving
    // ------------------------------------------------------------------

    static void tick() {
        if (!live()) return;
        Minecraft mc = Minecraft.getInstance();
        if (playedFor != IntroScene.light) lightCame();
        float t = since(0);
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        LastDayFight.Facades f = IntroScene.facades;
        for (Beat beat : Beat.values()) {
            if (played.contains(beat) || t < beat.tick()) continue;
            played.add(beat);
            // Long past (a jump, a rejoin): nothing to see of it now.
            if (t - beat.tick() > 20) continue;
            play(beat, f, ear);
        }
        // Ted's small waves: each one's thud, as it leaves him.
        for (int i = 0; i < PrologueTimeline.PULSES.length; i++) {
            if (i <= pulsesSeen || t < PrologueTimeline.PULSES[i]) continue;
            pulsesSeen = i;
            if (t - PrologueTimeline.PULSES[i] > 20) continue;
            float power = PrologueTimeline.pulsePower(i);
            hear(LastDayFight.TED, SoundEvents.GENERIC_EXPLODE.value(), 0.6F + power, 0.45F + 0.2F * (1 - power), ear, t);
            if (power > 0.5F) hear(LastDayFight.TED, SoundEvents.WARDEN_SONIC_BOOM, 0.5F * power, 0.5F, ear, t);
        }
        // Sounds that have come the distance.
        HEARING.removeIf(h -> {
            if (t < h.at) return false;
            mc.level.playLocalSound(h.where.x, h.where.y, h.where.z, h.sound, SoundSource.HOSTILE, h.volume, h.pitch, false);
            return true;
        });
    }

    /** A sound made at {@code where} now, heard here when it has crossed the distance (about 17 blocks a tick). */
    private static void hear(Vec3 where, SoundEvent sound, float volume, float pitch, Vec3 ear, float t) {
        double d = ear.distanceTo(where);
        // Heard from far off, loud enough to reach you: what plays is at your end of the street, in its direction.
        Vec3 near = d > 24 ? ear.add(where.subtract(ear).normalize().scale(20)) : where;
        float v = d > 24 ? (float) (volume * Mth.clamp(1.6 - d / 260, 0.25, 1.2)) : volume;
        HEARING.add(new Heard(t + (float) (d / 17), near, sound, v, pitch));
    }

    private static void play(Beat beat, LastDayFight.Facades f, Vec3 ear) {
        float t = beat.tick();
        Vec3 at = beat.where(f);
        Vec3 p = LastDayFight.peter(t, f), s = LastDayFight.sylar(t, f);
        switch (beat) {
            case ENTRY_BOOM -> {
                Vec3 ahead = LastDayFight.peter(t + 1, f).subtract(p);
                SetPieceClient.play(Piece.FLIGHT_SKYBURST, p.add(0, 1, 0), p.add(0, 1, 0).add(ahead.normalize()), 4, -1, 11);
                hear(p, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 1.3F, ear, t);
                hear(p, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.6F, ear, t);
            }
            case SYLAR_SHOWS -> hear(at, SoundEvents.EVOKER_PREPARE_ATTACK, 1.2F, 0.6F, ear, t);
            case MEET, PASS_0, PASS_1, PASS_2, PASS_3 -> {
                if (s == null) return;
                Vec3 a = p.add(0, 1.1, 0), b = s.add(0, 1.1, 0);
                if (beat.peterWins()) {
                    SetPieceClient.play(Piece.FLIGHT_SKYBURST, at.add(0, 1, 0), at.add(0, 1, 0).add(b.subtract(a).normalize()), 4, -1, beat.ordinal());
                    SetPieceClient.play(Piece.ELLE_BOLT, a, b, 1.2F, -1, beat.ordinal());
                    hear(at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4F, 1.4F, ear, t);
                } else {
                    SetPieceClient.play(Piece.WATCH_HOLD, a, b, 0.3F, -1, beat.ordinal());
                    hear(at, SoundEvents.EVOKER_CAST_SPELL, 1.4F, 0.6F, ear, t);
                }
                hear(at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.3F, ear, t);
            }
            case GRIPPED -> {
                if (s != null) SetPieceClient.play(Piece.WATCH_HOLD, s.add(0, 1.2, 0), p.add(0, 1, 0), 0.4F, -1, 3);
                hear(at, SoundEvents.EVOKER_CAST_SPELL, 1.5F, 0.5F, ear, t);
            }
            case HURLED_DOWN -> {
                Vec3 toward = LastDayFight.peter(t + 4, f).subtract(p).normalize();
                SetPieceClient.play(Piece.FLIGHT_SKYBURST, p.add(0, 1, 0), p.add(0, 1, 0).add(toward), 5, -1, 5);
                hear(at, SoundEvents.WIND_CHARGE_BURST.value(), 1.6F, 0.5F, ear, t);
                hear(at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.9F, ear, t);
            }
            case ARRIVES -> hear(at, SoundEvents.ELYTRA_FLYING, 0.8F, 0.7F, ear, t);
            case BOLT -> {
                if (s == null) return;
                Vec3 hand = p.add(0, 1.3, 0), chest = s.add(0, 1.1, 0);
                SetPieceClient.play(Piece.ELLE_BOLT, hand, chest, 1.4F, -1, 17);
                SetPieceClient.play(Piece.FLIGHT_SKYBURST, at.add(0, 1, 0), at.add(0, 1, 0).add(chest.subtract(hand).normalize()), 4.5F, -1, 17);
                hear(at, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9F, 1.6F, ear, t);
                hear(at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6F, 1.2F, ear, t);
            }
            case TORN_1, TORN_2 -> {
                int which = beat == Beat.TORN_1 ? 0 : 1;
                if (s != null) SetPieceClient.play(Piece.WATCH_HOLD, s.add(0, 1.2, 0), at, 0.5F, -1, 21 + which);
                hear(at, SoundEvents.EVOKER_CAST_SPELL, 1.6F, 0.55F, ear, t);
            }
            case FLUNG_1, FLUNG_2 -> {
                int which = beat == Beat.FLUNG_1 ? 0 : 1;
                Vec3 next = LastDayFight.chunk(which, t + 1, f);
                if (next != null) SetPieceClient.play(Piece.FLIGHT_SKYBURST, at, at.add(next.subtract(at).normalize()), 2.5F, -1, 31 + which);
                hear(at, SoundEvents.WIND_CHARGE_BURST.value(), 1.4F, 0.7F, ear, t);
            }
            case HIT_1, HIT_2 -> {
                Vec3 out = beat == Beat.HIT_1 ? new Vec3(-1, 0, 0) : new Vec3(1, 0, 0);
                SetPieceClient.play(Piece.FLIGHT_IMPACT, at, at.add(out), 3.2F, -1, 41);
            }
            case TACKLED -> {
                SetPieceClient.play(Piece.FLIGHT_SKYBURST, at.add(0, 1, 0), at.add(0, 2, -1), 3, -1, 51);
                hear(at, SoundEvents.PLAYER_ATTACK_STRONG, 1.6F, 0.7F, ear, t);
            }
            case THROWN_NORTH -> {
                Vec3 toward = LastDayFight.PLAZA.impactGuess().subtract(at).normalize();
                SetPieceClient.play(Piece.FLIGHT_SKYBURST, at.add(0, 1, 0), at.add(0, 1, 0).add(toward), 5, -1, 61);
                hear(at, SoundEvents.WIND_CHARGE_BURST.value(), 2, 0.55F, ear, t);
                hear(at, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 1.1F, ear, t);
            }
            case INTO_TOWER -> {
                SetPieceClient.play(Piece.FLIGHT_IMPACT, at, at.add(1, 0, 0.4), 3.2F, -1, 71);
                hear(at, SoundEvents.GENERIC_EXPLODE.value(), 2, 0.55F, ear, t);
                hear(at, SoundEvents.GLASS_BREAK, 1.5F, 0.7F, ear, t);
            }
            case LANDS -> {
                SetPieceClient.play(Piece.FLIGHT_IMPACT, SkyFight.LANDING, SkyFight.LANDING.add(0, 1, 0), 1.8F, -1, 81);
                hear(at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.5F, ear, t);
            }
        }
    }

    // ------------------------------------------------------------------
    // The people and the stone, as models
    // ------------------------------------------------------------------

    private static StoryActorEntity actor(ClientLevel level, Role role) {
        StoryActorEntity a = new StoryActorEntity(ModEntities.STORY_ACTOR.get(), level);
        a.role(role);
        return a;
    }

    /** Draws Peter, Sylar and Ted where the script has them, the chunks in flight, and the stone coming down. */
    static void render(Vec3 cam, float partial, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        if (!live()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (actorsOf != level) {
            actorsOf = level;
            peter = actor(level, Role.PETER);
            sylar = actor(level, Role.SYLAR);
            ted = actor(level, Role.TED);
        }
        float t = since(partial);
        LastDayFight.Facades f = IntroScene.facades;
        // Ted, on his knees in the light, shaking with it.
        place(ted, LastDayFight.TED, LastDayFight.TED.add(0, 1, 5), StoryActorEntity.Pose.GLOW, t);
        draw(ted, cam, partial, pose, buffers, level);
        tedGlow = true;
        Vec3 p = LastDayFight.peter(t, f);
        if (p != null) {
            Vec3 before = LastDayFight.peter(t - 1, f);
            place(peter, p, flying(p, before) ? null : LastDayFight.peterLook(t, f), LastDayFight.peterPose(t), t, before);
            draw(peter, cam, partial, pose, buffers, level);
        }
        Vec3 s = LastDayFight.sylar(t, f);
        if (s != null) {
            Vec3 before = LastDayFight.sylar(t - 1, f);
            if (before == null) before = s;
            Vec3 look = p != null ? p.add(0, 1, 0) : LastDayFight.TED;
            place(sylar, s, flying(s, before) ? null : look, LastDayFight.sylarPose(t), t, before);
            draw(sylar, cam, partial, pose, buffers, level);
        }
        for (int which = 0; which < 2; which++) {
            Vec3 c = LastDayFight.chunk(which, t, f);
            if (c != null) chunk(c, which, t, cam, pose, buffers, level);
            int hit = which == 0 ? LastDayFight.RUBBLE_EAST : LastDayFight.RUBBLE_WEST;
            float since = t - hit;
            if (since >= 0 && since < LastDayFight.FALL + 2) falling(LastDayFight.impact(which, f), which, since, cam, pose, buffers, level);
        }
        buffers.endBatch();
    }

    private static boolean flying(Vec3 now, Vec3 before) { return before != null && now.distanceToSqr(before) > 0.04; }

    private static void place(StoryActorEntity a, Vec3 at, Vec3 look, StoryActorEntity.Pose pose, float t) { place(a, at, look, pose, t, at); }

    /** Puts a stand-in where the script says, facing along its flight (lying into it) or toward {@code look}. */
    private static void place(StoryActorEntity a, Vec3 at, Vec3 look, StoryActorEntity.Pose pose, float t, Vec3 before) {
        Vec3 aim = look == null ? at.subtract(before) : look.subtract(at);
        float yaw = a.getYRot(), pitch = 0;
        if (aim.lengthSqr() > 1e-6) {
            yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
            if (look == null) pitch = (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance()));
        }
        a.setPos(at.x, at.y, at.z);
        a.xo = a.xOld = at.x; a.yo = a.yOld = at.y; a.zo = a.zOld = at.z;
        a.setYRot(yaw); a.yRotO = yaw;
        a.setYBodyRot(yaw); a.yBodyRotO = yaw;
        a.setYHeadRot(yaw); a.yHeadRotO = yaw;
        a.setXRot(pitch); a.xRotO = pitch;
        a.tickCount = (int) t;
        if (a.pose() != pose) a.pose(pose);
    }

    private static void draw(StoryActorEntity a, Vec3 cam, float partial, PoseStack pose, MultiBufferSource buffers, ClientLevel level) {
        if (a.position().distanceToSqr(cam) > 240 * 240) return;
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(a.position().add(0, 1, 0)));
        Minecraft.getInstance().getEntityRenderDispatcher().render(a, a.getX() - cam.x, a.getY() - cam.y, a.getZ() - cam.z, a.getYRot(),
                partial, pose, buffers, light);
    }

    /** A chunk of facade, a few blocks of it held together, turning as it flies. */
    private static void chunk(Vec3 at, int which, float t, Vec3 cam, PoseStack pose, MultiBufferSource buffers, ClientLevel level) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(at));
        int tear = which == 0 ? LastDayFight.TEAR_1 : LastDayFight.TEAR_2;
        float spin = (t - tear) * (which == 0 ? 9 : -11);
        int[][] parts = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 0}, {0, -1, 0}, {-1, 0, 1}, {1, 0, 1}, {0, 1, 1}};
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.scale(1.25F, 1.25F, 1.25F);
        pose.mulPose(Axis.YP.rotationDegrees(spin * 0.7F));
        pose.mulPose(Axis.XP.rotationDegrees(spin));
        for (int[] q : parts) {
            pose.pushPose();
            pose.translate(q[0] * 0.95 - 0.5, q[1] * 0.95 - 0.5, q[2] * 0.95 - 0.5);
            blocks.renderSingleBlock(q[1] == 1 && which == 0 ? Blocks.GLASS.defaultBlockState() : chunkStone[which], pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Stone and glass coming down off the facade onto the sidewalk below, in the moments before it lands. */
    private static void falling(Vec3 from, int which, float since, Vec3 cam, PoseStack pose, MultiBufferSource buffers, ClientLevel level) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        double sidewalk = which == 0 ? 9.5 : -1.5, floor = CityGrid.STREET_Y + 1;
        for (int i = 0; i < 14; i++) {
            float h1 = Canvas.hash(97 + which, i), h2 = Canvas.hash(131 + which, i), h3 = Canvas.hash(173 + which, i);
            double x0 = from.x + (which == 0 ? -0.4 : 0.4), y0 = from.y + (h1 - 0.5) * 2, z0 = from.z + (h2 - 0.5) * 3;
            double x1 = sidewalk + (h3 - 0.5) * 2.4, z1 = from.z + (h2 - 0.5) * 7;
            float k = Mth.clamp(since / LastDayFight.FALL, 0, 1);
            double y = Math.max(floor, y0 + (floor - y0) * k * k);
            Vec3 at = new Vec3(Mth.lerp(k, x0, x1), y, Mth.lerp(k, z0, z1));
            float size = i % 3 == 0 ? 0.55F : 0.35F;
            BlockState state = i % 4 == 1 ? Blocks.GLASS.defaultBlockState() : chunkStone[which == 0 ? 1 : 0];
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(Axis.of(new org.joml.Vector3f(h1 - 0.5F, h2 - 0.5F, h3 - 0.5F).normalize()).rotation(since * (0.4F + h1)));
            pose.scale(size, size, size);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(state, pose, buffers, LevelRenderer.getLightColor(level, BlockPos.containing(at)), OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------
    // Drawn on the canvas: trails, Ted's waves, the crash in the tower
    // ------------------------------------------------------------------

    /** Whether Ted has been drawn (his own halo then hugs him, so his shape reads inside the light from far off). */
    private static boolean tedGlow;

    public static void drawLive(Canvas c) {
        if (!live()) return;
        float t = since(c.partial());
        LastDayFight.Facades f = IntroScene.facades;
        // Ted burns where he kneels: a white-hot halo close round him, pulsing, so you can see him from the market.
        if (tedGlow && c.glow()) {
            float pulse = 0.85F + 0.15F * Mth.sin(t * 0.4F);
            Vec3 chest = LastDayFight.TED.add(0, 0.8, 0);
            c.glow(chest, 1.4F * pulse, WHITE_HOT, 0.85F);
            c.glow(chest, 3.2F * pulse, HEAT, 0.45F);
            c.ring(LastDayFight.TED.add(0, 0.05, 0), Canvas.UP, 1.6F + 0.3F * pulse, 0.5F, 0, Mth.TWO_PI, HEAT, 0.6F);
        }
        // The chunks in Sylar's grip: dust streaming off them, his hold glowing round them.
        for (int which = 0; which < 2; which++) {
            Vec3 at = LastDayFight.chunk(which, t, f), before = LastDayFight.chunk(which, t - 2, f);
            if (at == null) continue;
            if (c.glow()) c.glow(at, 2.2F, Canvas.rgb(0x9A7CFF), 0.35F);
            if (c.soft() && before != null && at.distanceToSqr(before) > 0.5)
                for (int i = 0; i < 4; i++) {
                    Vec3 puff = before.lerp(at, i / 4.0);
                    c.cloud(puff, 0.9F + i * 0.2F, 0.7F, 0.9F + i * 0.2F, 90 + i, t * 0.05F, 0.4F, Shades.smoke(c, DUST, 0));
                }
        }
        // The way each has come: pale condensation behind Peter, dark smoke behind Sylar.
        trail(c, t, f, true);
        trail(c, t, f, false);
        // Sylar shows himself: dark smoke unrolling round him.
        float shows = t - LastDayFight.SYLAR_IN;
        if (shows >= 0 && shows < 40 && c.soft()) {
            Vec3 s = SkyFight.sylarStart(LastDayFight.PLAZA, 0).add(0, 0.9, 0);
            float fade = 1 - shows / 40;
            for (int i = 0; i < 5; i++) {
                float r = 0.8F + shows * 0.05F + i * 0.2F;
                c.cloud(s.add((Canvas.hash(5, i) - 0.5) * 2, (Canvas.hash(7, i) - 0.5) * 2, (Canvas.hash(9, i) - 0.5) * 2), r, r * 0.8F, r, i,
                        shows * 0.02F, 0.6F * fade, Shades.smoke(c, SMOKE, 0));
            }
        }
        // Sylar into the tower: a flash, then dust rolling out of the hole.
        float crash = t - LastDayFight.TOWER;
        if (crash >= 0 && crash < 100) tower(c, LastDayFight.PLAZA.impactGuess(), crash);
        // Peter in his dive into the light.
        if (t > LastDayFight.DIVE && t < LastDayFight.LAND + 6 && c.glow()) {
            Vec3 p = LastDayFight.peter(t, f);
            if (p != null) c.glow(p.add(0, 0.9, 0), 1.8F, CORE, 0.5F);
        }
        // Ted's waves.
        for (int i = 0; i < PrologueTimeline.PULSES.length; i++) {
            float age = t - PrologueTimeline.PULSES[i];
            if (age >= 0 && age < PrologueTimeline.PULSE_TICKS + 14) pulse(c, age, PrologueTimeline.pulseReach(i), PrologueTimeline.pulsePower(i), i);
        }
        float wave = t - PrologueTimeline.WAVE;
        if (wave >= 0 && wave < 200) bigWave(c, wave);
    }

    private static void trail(Canvas c, float t, LastDayFight.Facades f, boolean isPeter) {
        if (isPeter ? !c.glow() : !c.soft()) return;
        int n = 12;
        Vec3[] points = new Vec3[n];
        float[] width = new float[n], alpha = new float[n];
        for (int i = 0; i < n; i++) {
            Vec3 at = isPeter ? LastDayFight.peter(t - i * 0.8F, f) : LastDayFight.sylar(t - i * 0.8F, f);
            if (at == null) return;
            points[i] = at.add(0, 0.9, 0);
            float k = 1 - i / (float) n;
            width[i] = 0.18F + 0.45F * k;
            alpha[i] = (isPeter ? 0.5F : 0.55F) * k;
        }
        // Only while they're really moving: no smear while they hover.
        if (points[0].distanceTo(points[n - 1]) < 2) return;
        c.ribbon(points, width, isPeter ? VAPOR : SMOKE, alpha);
    }

    private static void tower(Canvas c, Vec3 impact, float since) {
        if (c.glow() && since < 12) c.glow(impact, 4 + since, CORE, 1 - since / 12);
        if (c.soft()) {
            float fade = Mth.clamp(1 - since / 100, 0, 1), grow = Mth.clamp(since / 30, 0, 1);
            for (int i = 0; i < 5; i++) {
                Vec3 at = impact.add(1.5 + i * 0.9, -since * 0.03 - i * 0.6, (i - 2) * 0.8);
                float r = 1.2F + 2.6F * grow + i * 0.3F;
                c.cloud(at, r, r * 0.8F, r, i, since * 0.01F, 0.55F * fade, Shades.smoke(c, DUST, 0));
            }
        }
    }

    private static float out(float x) { x = Mth.clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); }

    /** One of Ted's small waves: a hot ring racing out over the avenue, a sickly one behind it, a dome of haze over it. */
    private static void pulse(Canvas c, float age, float reach, float power, int seed) {
        Vec3 o = new Vec3(IntroBlast.CENTER.x, CityGrid.STREET_Y + 1.06, IntroBlast.CENTER.z);
        float front = reach * out(age / PrologueTimeline.PULSE_TICKS);
        float fade = 1 - Canvas.smooth(PrologueTimeline.PULSE_TICKS * 0.55F, PrologueTimeline.PULSE_TICKS + 14, age);
        if (c.glow()) {
            if (age < 4) c.glow(o.add(0, 1.2, 0), 2 + 5 * power, WHITE_HOT, 0.9F * (1 - age / 4) * power);
            c.ring(o, Canvas.UP, front, 0.25F + 0.6F * power, 0, Mth.TWO_PI, WHITE_HOT, 0.85F * fade * (0.4F + 0.6F * power));
            c.ring(o, Canvas.UP, front * 1.03F, 0.9F + 1.6F * power, 0, Mth.TWO_PI, HEAT, 0.4F * fade);
            if (age > 2) c.ring(o.add(0, 0.02, 0), Canvas.UP, front * 0.86F, 0.4F, 0, Mth.TWO_PI, TOXIC, 0.5F * fade);
            for (int i = 0; i < 6; i++) {
                float ang = (i + Canvas.hash(seed, i)) * Mth.TWO_PI / 6, len = front * (0.35F + 0.4F * Canvas.hash(seed, i + 9));
                c.bolt(o, o.add(Mth.cos(ang) * len, 0, Mth.sin(ang) * len), seed * 7 + i, 0.4F, 0.05F, EMBER, 0.55F * fade * power);
            }
        }
        if (c.soft()) {
            float dome = 1 - Canvas.smooth(PrologueTimeline.PULSE_TICKS * 0.4F, PrologueTimeline.PULSE_TICKS, age);
            c.shell(o, front, front * (0.25F + 0.25F * power), front, Mth.HALF_PI, HAZE, 0.25F * power * dome, 0.05F, Canvas.PLAIN, 0);
            wall(c, o, front, 8 + 26 * power, HAZE, 0.26F * power * fade);
            wall(c, o, front * 0.97F, 3 + 4 * power, DUST, 0.35F * power * fade);
        }
        if (c.glow()) wall(c, o, front, 5 + 14 * power, HEAT, 0.12F * power * fade);
    }

    /**
     * The big wave: the light whites out, then a wall of pressure and dust crosses the whole city, a burning ring under
     * it, bright where it is, faint far round the other side of it.
     */
    private static void bigWave(Canvas c, float age) {
        Vec3 o = new Vec3(IntroBlast.CENTER.x, CityGrid.STREET_Y + 1.08, IntroBlast.CENTER.z);
        float front = PrologueTimeline.front(age);
        float fade = 1 - Canvas.smooth(120, 200, age);
        if (c.glow()) {
            if (age < 10) c.glow(o.add(0, 6, 0), 20 + age * 8, WHITE_HOT, 1 - age / 10);
            c.ring(o, Canvas.UP, front, 1.8F, 0, Mth.TWO_PI, WHITE_HOT, 0.95F * fade);
            c.ring(o, Canvas.UP, front * 1.02F, 6F, 0, Mth.TWO_PI, HEAT, 0.55F * fade);
            c.ring(o.add(0, 0.03, 0), Canvas.UP, front * 0.9F, 2.2F, 0, Mth.TWO_PI, TOXIC, 0.45F * fade);
            // The pressure front itself: a bright sheet of compressed air, then the heat behind it.
            wall(c, o, front + 1.5F, 60, WHITE_HOT, 0.35F * fade);
            wall(c, o, front * 0.985F, 44, HEAT, 0.3F * fade);
        }
        if (c.solid()) flung(c, o, front, age);
        if (c.soft()) {
            // A wall of dust and debris as high as the towers, thickest at the street.
            wall(c, o, front * 0.99F, 70, HAZE, 0.42F * fade);
            wall(c, o, front * 0.975F, 26, DUST, 0.75F * fade);
            Vec3 cam = c.camera();
            double toCam = Math.atan2(cam.z - o.z, cam.x - o.x);
            int n = 40;
            for (int i = -n / 2; i <= n / 2; i++) {
                double ang = toCam + i * 0.035 * Math.max(0.3, 120 / Math.max(front, 20));
                for (int layer = 0; layer < 3; layer++) {
                    Vec3 at = o.add(Math.cos(ang) * (front - layer * 2.5), 1.5 + layer * 5.5, Math.sin(ang) * (front - layer * 2.5));
                    if (at.distanceToSqr(cam) > 160 * 160) continue;
                    float size = 3.2F + 2.4F * Canvas.hash(13 + layer, i + 20) + age * 0.03F;
                    c.cloud(at, size * 1.7F, size * 1.2F, size * 1.7F, 60 + i + layer * 50, (float) ang + age * 0.02F,
                            0.7F * fade * (1 - layer * 0.2F), Shades.smoke(c, DUST, 0));
                }
            }
        }
    }

    private static final float[][] GRIT = {Canvas.rgb(0x6E6A64), Canvas.rgb(0x8C8378), Canvas.rgb(0x4A4642), Canvas.rgb(0xA89A86), Canvas.rgb(0x5C3A26)};

    /**
     * Debris the front throws ahead of it near you: stone, brick and splinters tumbling out of the dust and skipping along
     * the street, carried a few blocks past where you stand.
     */
    private static void flung(Canvas c, Vec3 o, float front, float age) {
        Vec3 cam = c.camera();
        if (Math.abs(IntroBlast.distance(cam.x, cam.z) - front) > 70) return;
        double toCam = Math.atan2(cam.z - o.z, cam.x - o.x);
        for (int i = 0; i < 90; i++) {
            float h1 = Canvas.hash(301, i), h2 = Canvas.hash(307, i), h3 = Canvas.hash(311, i), h4 = Canvas.hash(313, i);
            double ang = toCam + (h1 - 0.5) * Math.min(1.2, 60 / Math.max(front, 20));
            // Each piece rides a little behind or ahead of the front, rising and falling as it's carried.
            float r = front + 3 - h2 * 12;
            float life = (age * 0.6F + h3 * 10) % 10;
            float y = 0.3F + h4 * h4 * 16 * Mth.sin(life / 10 * Mth.PI);
            Vec3 at = o.add(Math.cos(ang) * r, y, Math.sin(ang) * r);
            if (at.distanceToSqr(cam) > 70 * 70) continue;
            float size = 0.12F + 0.42F * h3 * h3;
            float spin = (age + h1 * 20) * (0.3F + h2);
            c.box(at, size, size * (0.5F + h4), size * (0.6F + h1 * 0.6F), spin, spin * 0.7F, spin * 0.4F, GRIT[i % GRIT.length], 1);
        }
    }

    /** An upright band of haze round ground zero at {@code radius}, only the arc facing the camera, fading upward. */
    private static void wall(Canvas c, Vec3 o, float radius, float height, float[] col, float alpha) {
        if (alpha <= 0.004F || radius < 1) return;
        Vec3 cam = c.camera();
        double toCam = Math.atan2(cam.z - o.z, cam.x - o.x);
        int n = 48;
        double span = Math.min(Math.PI, 160 / Math.max(radius, 1) + 0.3);
        for (int i = 0; i < n; i++) {
            double a0 = toCam - span + 2 * span * i / n, a1 = toCam - span + 2 * span * (i + 1) / n;
            Vec3 p0 = o.add(Math.cos(a0) * radius, -0.5, Math.sin(a0) * radius), p1 = o.add(Math.cos(a1) * radius, -0.5, Math.sin(a1) * radius);
            Vec3 q0 = p0.add(0, height, 0), q1 = p1.add(0, height, 0);
            float edge0 = (float) (1 - Math.abs(i - n / 2F) / (n / 2F)), edge1 = (float) (1 - Math.abs(i + 1 - n / 2F) / (n / 2F));
            float a = alpha * Mth.sqrt(edge0), b = alpha * Mth.sqrt(edge1);
            c.tri(p0, p1, q1, col, a, b, 0);
            c.tri(p0, q1, q0, col, a, 0, 0);
        }
    }
}
