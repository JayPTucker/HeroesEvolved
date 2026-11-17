package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.setpiece.HazardArt;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A player burning up (BodyBurn on the server): their skin flakes away pixel by pixel, the edges glowing then charring,
 * and shows a charred, ember-lit body underneath. The skin is a copy of their own, read back from the GPU once and
 * repainted in {@link #STEPS} steps as the burn deepens ({@code mixin/BurnSkinMixin} hands it to the player renderer);
 * the body under it is {@link EmberLayer}, the player's own model drawn a little thinner so the skin covers it wherever
 * the skin is still there. Bodies that radiation burns away entirely are not drawn while their cubes fly
 * ({@code HazardArt}).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class BurnClient {
    /** How many distinct stages the peeling skin is painted in. */
    static final int STEPS = 24;
    /** How fast the drawn burn follows the server's: quick to burn, a little slower to heal. */
    static final float RISE = 0.06F, FALL = 0.03F;

    private static final class Burn { float target, shown, last; long fell = Long.MIN_VALUE / 2; }
    private static final Map<Integer, Burn> BURNS = new HashMap<>();

    private static final class Peel {
        ResourceLocation source, id;
        NativeImage original;
        DynamicTexture texture;
        int step = -1;
        boolean failed;
    }
    private static final Map<UUID, Peel> PEELS = new HashMap<>();

    private BurnClient() {}

    public static void set(int entity, float level) {
        Burn b = BURNS.computeIfAbsent(entity, id -> new Burn());
        float next = Mth.clamp(level, 0, 1);
        var world = Minecraft.getInstance().level;
        if (next < b.target && world != null) b.fell = world.getGameTime();
        b.target = next;
    }

    /** How burnt this body is drawn, 0 to 1. */
    public static float level(Entity entity, float partial) {
        Burn b = BURNS.get(entity.getId());
        return b == null ? 0 : Mth.lerp(partial, b.last, b.shown);
    }

    /** Is this body's burnt skin growing back right now? */
    public static boolean regrowing(Entity entity) {
        Burn b = BURNS.get(entity.getId());
        var world = Minecraft.getInstance().level;
        // The server heals it a little at a time, so it is "growing back" for a moment after each step down.
        return b != null && (b.target < b.shown - 0.001F || world != null && world.getGameTime() - b.fell < 15);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        for (Iterator<Burn> it = BURNS.values().iterator(); it.hasNext(); ) {
            Burn b = it.next();
            b.last = b.shown;
            b.shown += Mth.clamp(b.target - b.shown, -FALL, RISE);
            if (b.target <= 0 && b.shown <= 0.001F && b.last <= 0.001F) it.remove();
        }
    }

    // ------------------------------------------------------------------
    // The peeling skin
    // ------------------------------------------------------------------

    /** The skin to draw {@code player} with: their own, or a burnt copy of it. */
    public static ResourceLocation skin(AbstractClientPlayer player, ResourceLocation skin) {
        Burn b = BURNS.get(player.getId());
        if (b == null || b.shown < 0.02F || skin == null) return skin;
        if (!RenderSystem.isOnRenderThread()) return skin;
        int step = Mth.clamp(Math.round(b.shown * STEPS), 1, STEPS);
        Peel peel = PEELS.get(player.getUUID());
        if (peel == null || !skin.equals(peel.source)) {
            if (peel != null) release(peel);
            peel = capture(player.getUUID(), skin);
            PEELS.put(player.getUUID(), peel);
        }
        if (peel.failed) return skin;
        if (peel.step != step) {
            paint(peel, step / (float) STEPS);
            peel.step = step;
        }
        return peel.id;
    }

    /** Reads the skin back from the GPU into a copy we can repaint. */
    private static Peel capture(UUID who, ResourceLocation skin) {
        Peel peel = new Peel();
        peel.source = skin;
        try {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skin);
            GlStateManager._bindTexture(texture.getId());
            int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w < 16 || h < 16 || w > 1024 || h > 1024) { peel.failed = true; return peel; }
            NativeImage original = new NativeImage(w, h, false);
            original.downloadTexture(0, false);
            peel.original = original;
            peel.texture = new DynamicTexture(new NativeImage(w, h, true));
            peel.id = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "burn/" + who);
            Minecraft.getInstance().getTextureManager().register(peel.id, peel.texture);
        } catch (RuntimeException e) {
            HeroesEvolved.LOGGER.debug("Couldn't copy a skin to burn it", e);
            peel.failed = true;
        }
        return peel;
    }

    /** Ember, then char, then the skin itself, by how far each pixel is from burning through. */
    private static final int EMBER = 0xFF1E7AFF; // ABGR: a hot orange

    private static void paint(Peel peel, float burnt) {
        NativeImage from = peel.original, to = peel.texture.getPixels();
        if (to == null) return;
        int w = from.getWidth(), h = from.getHeight();
        float scale = w / 64F;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = from.getPixelRGBA(x, y);
                if ((c >>> 24) == 0) { to.setPixelRGBA(x, y, 0); continue; }
                float n = noise((int) (x / scale), (int) (y / scale));
                float edge = n - burnt;
                if (edge < 0) to.setPixelRGBA(x, y, 0);
                else if (edge < 0.04F) to.setPixelRGBA(x, y, EMBER);
                else if (edge < 0.12F) to.setPixelRGBA(x, y, darken(c, 0.25F + 0.75F * (edge - 0.04F) / 0.08F * 0.6F));
                else to.setPixelRGBA(x, y, c);
            }
        peel.texture.upload();
    }

    private static int darken(int abgr, float k) {
        int a = abgr >>> 24, b = (int) (((abgr >> 16) & 255) * k), g = (int) (((abgr >> 8) & 255) * k), r = (int) ((abgr & 255) * k);
        return a << 24 | b << 16 | g << 8 | r;
    }

    /** Blotchy noise over the skin's pixels, 0 to 1: patches burn through first, then spread. */
    static float noise(int x, int y) {
        float fx = x / 5F, fy = y / 5F;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float tx = smooth(fx - x0), ty = smooth(fy - y0);
        float v = Mth.lerp(ty, Mth.lerp(tx, hash(x0, y0), hash(x0 + 1, y0)), Mth.lerp(tx, hash(x0, y0 + 1), hash(x0 + 1, y0 + 1)));
        return Mth.clamp(0.62F * v + 0.38F * hash(x * 7 + 3, y * 13 + 5), 0, 0.999F);
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static void release(Peel peel) {
        if (peel.id != null) Minecraft.getInstance().getTextureManager().release(peel.id);
        if (peel.original != null) peel.original.close();
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BURNS.clear();
        for (Peel peel : PEELS.values()) release(peel);
        PEELS.clear();
    }

    // ------------------------------------------------------------------
    // Burnt-away bodies
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void hide(RenderLivingEvent.Pre<?, ?> event) {
        if (HazardArt.disintegrating(event.getEntity())) event.setCanceled(true);
    }

    // ------------------------------------------------------------------
    // The body under the skin
    // ------------------------------------------------------------------

    /** The charred body a burning player's skin peels away from: their own model, a little thinner, in embers. */
    public static final class EmberLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/burn/ember_body.png");
        private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/burn/ember_body_glow.png");

        public EmberLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partial, float age, float yaw, float pitch) {
            float burnt = level(player, partial);
            if (burnt < 0.02F || player.isInvisible()) return;
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
            draw(parts, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BODY)), light, overlay, -1);
            float pulse = 0.75F + 0.25F * Mth.sin(age * 0.3F);
            int k = (int) (255 * Mth.clamp(burnt * 1.4F, 0, 1) * pulse);
            draw(parts, pose, buffers.getBuffer(RenderType.eyes(GLOW)), light, overlay, 0xFF000000 | k << 16 | k << 8 | k);
        }

        private static void draw(ModelPart[] parts, PoseStack pose, VertexConsumer out, int light, int overlay, int color) {
            for (ModelPart part : parts) {
                if (!part.visible) continue;
                float xs = part.xScale, ys = part.yScale, zs = part.zScale;
                part.xScale = xs * 0.9F;
                part.yScale = ys * 0.97F;
                part.zScale = zs * 0.9F;
                part.render(pose, out, light, overlay, color);
                part.xScale = xs;
                part.yScale = ys;
                part.zScale = zs;
            }
        }
    }

    @EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : event.getSkins())
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new EmberLayer(renderer));
        }
    }
}
