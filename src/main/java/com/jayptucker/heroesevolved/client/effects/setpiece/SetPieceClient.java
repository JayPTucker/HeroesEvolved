package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.ClientProjectileTime;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The set pieces playing now, and drawing them: solid parts after the world's entities, then the
 * see-through and glowing parts after particles. Every piece is a pure function of its age, so it looks
 * the same to everyone watching and needs nothing from the server once it has started.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SetPieceClient {
    /** One piece playing: where, toward what, how big, what it follows, its seed, and the game tick it began. */
    public record Active(Piece piece, Vec3 origin, Vec3 target, float scale, int entity, int seed, long start) {
        /** Where it is now: on the entity it follows (at its feet), or where it was cast. */
        public Vec3 at(Canvas canvas) {
            if (entity >= 0 && canvas.level != null) {
                Entity e = canvas.level.getEntity(entity);
                if (e != null) return e.getPosition(canvas.partial);
            }
            return origin;
        }
        public Entity bound(Canvas canvas) { return entity >= 0 && canvas.level != null ? canvas.level.getEntity(entity) : null; }
        /** The way it faces: from where it was cast toward its target, level. */
        public Vec3 facing() {
            Vec3 d = target.subtract(origin).multiply(1, 0, 1);
            return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static ClientLevel world;
    private static final Canvas CANVAS = new Canvas();

    private SetPieceClient() {}

    public static void add(SetPiecePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        Piece piece = Piece.byId(payload.piece());
        if (level == null || piece == null || !Double.isFinite(payload.origin().lengthSqr()) || !Double.isFinite(payload.target().lengthSqr())
                || !Float.isFinite(payload.scale())) return;
        if (world != level) { ACTIVE.clear(); world = level; }
        if (ACTIVE.size() >= 96) ACTIVE.removeFirst();
        ACTIVE.add(new Active(piece, payload.origin(), payload.target(), Math.clamp(payload.scale(), 0.05F, 64), payload.entity(), payload.seed(),
                level.getGameTime() + Math.min(payload.delay(), 200)));
    }

    /** Starts a piece that began at game tick {@code start} (in the past, for someone arriving late). */
    public static void startedAt(Piece piece, Vec3 origin, Vec3 target, float scale, int entity, int seed, long start) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (world != level) { ACTIVE.clear(); world = level; }
        if (ACTIVE.size() >= 96) ACTIVE.removeFirst();
        ACTIVE.add(new Active(piece, origin, target, scale, entity, seed, start));
    }

    /** Plays a piece here only, for previews and for pieces the client starts itself. */
    public static void play(Piece piece, Vec3 origin, Vec3 target, float scale, int entity, int seed) {
        add(new SetPiecePayload(piece.ordinal(), origin, target, scale, entity, seed, 0));
    }

    /**
     * Whether a later piece of one of {@code by} has started on the same entity (or, unbound, at the same place) and
     * so replaces {@code a}: a held field that was crushed, popped or thrown stops drawing at once. A newer copy of the
     * same piece only replaces it once it has faded in.
     */
    public static boolean superseded(Active a, java.util.Set<Piece> by, long now) {
        for (Active o : ACTIVE)
            if (o != a && o.start >= a.start && by.contains(o.piece) && (o.piece != a.piece || now - o.start >= 2)
                    && (a.entity >= 0 ? o.entity == a.entity : o.origin.equals(a.origin) && o.target.equals(a.target))) return true;
        return false;
    }

    /** Stops everything playing, for previews. */
    public static void clear() { ACTIVE.clear(); }

    /** The newest forming-ice spray also steers the caster's arms. */
    public static Vec3 cryoCastTarget(int entityId) {
        if (world == null || Minecraft.getInstance().level != world) return null;
        long now = world.getGameTime();
        for (Active a : ACTIVE.reversed())
            if (a.piece == Piece.CRYO_CAST && a.entity == entityId && now >= a.start && now - a.start <= 8)
                return a.target;
        return null;
    }

    /** The newest running copy of {@code piece} bound to {@code entity}, or null. */
    public static Active boundTo(Piece piece, int entity) {
        if (world == null || Minecraft.getInstance().level != world) return null;
        long now = world.getGameTime();
        Active best = null;
        for (Active a : ACTIVE)
            if (a.piece == piece && a.entity == entity && now >= a.start && now - a.start <= piece.ticks && (best == null || a.start > best.start))
                best = a;
        return best;
    }

    /** Where each running copy of {@code piece} is now: on its entity if it rides one, else where it was cast. */
    public static List<Vec3> where(Piece piece) {
        List<Vec3> out = new ArrayList<>();
        if (world == null || Minecraft.getInstance().level != world) return out;
        long now = world.getGameTime();
        for (Active a : ACTIVE) {
            if (a.piece != piece || now < a.start) continue;
            Entity e = a.entity >= 0 ? world.getEntity(a.entity) : null;
            out.add(e != null ? e.position() : a.origin);
        }
        return out;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ACTIVE.clear(); world = null; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (world != level) { ACTIVE.clear(); world = level; }
        if (level != null) ACTIVE.removeIf(a -> expired(a, level, level.getGameTime() - a.start));
    }

    private static boolean expired(Active a, ClientLevel level, float age) {
        if (age <= a.piece.ticks) return false;
        return a.piece != Piece.RAD_CHARGE || RadiationArt.reactorCharge(a, level) < 0;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        boolean solid = event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (!solid && event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty() && !SpeedArt.live() && !RewindArt.live() && !FlightArt.live()
                && !PyroArt.live() && !ShapeArt.live() && !CryoConstructArt.live(mc.level) && !ClientProjectileTime.hasControlledShots()
                && !com.jayptucker.heroesevolved.client.effects.intro.SkyFightArt.live()) return;
        if (world != mc.level) { ACTIVE.clear(); world = mc.level; }
        Vec3 camera = event.getCamera().getPosition();
        Canvas c = CANVAS;
        c.cx = camera.x; c.cy = camera.y; c.cz = camera.z;
        c.partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        c.level = mc.level;
        c.daylight = net.minecraft.util.Mth.clamp(mc.level.getSkyDarken(c.partial), 0.2F, 1);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            if (solid) {
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
                pass(c, Canvas.Pass.SOLID);
            } else {
                RenderSystem.depthMask(false);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                pass(c, Canvas.Pass.SOFT);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                pass(c, Canvas.Pass.GLOW);
            }
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
        }
    }

    private static void pass(Canvas c, Canvas.Pass pass) {
        c.pass = pass;
        c.out = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        long now = c.level.getGameTime();
        for (Active a : ACTIVE) {
            float age = now - a.start + c.partial;
            if (age < 0 || expired(a, c.level, age)) continue;
            if (a.origin.distanceToSqr(c.cx, c.cy, c.cz) > 224 * 224 && a.target.distanceToSqr(c.cx, c.cy, c.cz) > 224 * 224) continue;
            try {
                PieceArt.draw(c, a, age);
            } catch (RuntimeException broken) {
                // One broken piece must never take the frame with it.
                HeroesEvolved.LOGGER.warn("Set piece {} failed to draw", a.piece, broken);
                a = null;
            }
        }
        // Then what has no fixed length: a speedster's cyclone or wake.
        try {
            SpeedArt.drawLive(c);
            RewindArt.drawLive(c);
            FlightArt.drawLive(c);
            PyroArt.drawLive(c);
            ShapeArt.drawLive(c);
            com.jayptucker.heroesevolved.client.effects.intro.SkyFightArt.drawLive(c);
            CryoConstructArt.draw(c);
            TemporalProjectileArt.draw(c);
        } catch (RuntimeException broken) {
            HeroesEvolved.LOGGER.warn("Live power effects failed to draw", broken);
        }
        MeshData mesh = c.out.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        c.out = null;
    }
}
