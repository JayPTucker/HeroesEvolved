package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * A supercharged body wears a field of red energy, the way a charged creeper wears its blue one: the model drawn again a
 * little larger with the creeper's swirling texture, scrolling. A Surge glows at a little over half strength; an Overcharge
 * glows full and swirls twice as fast. It eases in when the boost lands and out as it ends.
 *
 * <p>Players wear it as an inflated copy of their own model (wide or slim arms), so it is a shell like the creeper's. Any
 * other creature that can be supercharged (a pet, a gifted mob) wears its own model drawn again a size larger.
 *
 * <p>The red texture is made on the client from the game's own charged-creeper texture (so a resource pack's swirl is
 * kept): every pixel keeps its brightness and becomes Ando's red. Nothing of Mojang's is shipped in the mod.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SuperchargeField {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModelLayerLocation WIDE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "supercharge_field"), "wide");
    private static final ModelLayerLocation SLIM = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "supercharge_field"), "slim");
    private static final ResourceLocation SOURCE = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "dynamic/supercharge_field");
    /** How far the field stands off a player's skin, in model pixels (the creeper's stands off two). */
    private static final float INFLATE = 1.0F;
    /** Brightness of a Surge's field; an Overcharge's is full. */
    private static final float SURGE = .6F;
    /** Ticks the field takes to ease in, and to ease out before the boost ends. */
    private static final float EASE_IN = 8, EASE_OUT = 12;

    /** Red made from the creeper's swirl: 0 not tried, 1 made, -1 failed (the creeper's own texture is used, tinted). */
    private static int made;
    /** How strongly each body's field is shown right now, eased toward the boost's level frame by frame. */
    private static final Map<Integer, float[]> SHOWN = new HashMap<>();

    private SuperchargeField() {}

    @SubscribeEvent
    public static void definitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WIDE, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(INFLATE), false), 64, 64));
        event.registerLayerDefinition(SLIM, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(INFLATE), true), 64, 64));
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void layers(EntityRenderersEvent.AddLayers event) {
        made = 0; // resources were reloaded: make the red again from whatever swirl is loaded now
        for (PlayerSkin.Model skin : event.getSkins())
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                boolean slim = skin == PlayerSkin.Model.SLIM;
                renderer.addLayer(new PlayerField(renderer, new PlayerModel<>(event.getContext().bakeLayer(slim ? SLIM : WIDE), slim)));
            }
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?> renderer = event.getRenderer((EntityType) type);
            if (renderer instanceof LivingEntityRenderer living) living.addLayer(new CreatureField(living));
        }
    }

    // ------------------------------------------------------------------
    // How bright, how fast
    // ------------------------------------------------------------------

    /** The body whose boost a drawn entity shows: a shapeshifter's stand-in shows its wearer's. */
    private static int owner(LivingEntity entity) {
        int wearer = MorphClient.drawingFor();
        return wearer >= 0 ? wearer : entity.getId();
    }

    /** The vertex colour for a body's field this frame (its brightness, since the swirl adds light), or 0 for none. */
    private static int color(int id, float partial) {
        int level = ClientAmp.level(id);
        var level0 = Minecraft.getInstance().level;
        if (level0 == null) return 0;
        float now = level0.getGameTime() + partial;
        float target = 0;
        ClientAmp.Charged charged = ClientAmp.charged().get(id);
        // An Overcharge always hands back to a Surge, so only a Surge's end fades the field out; an Overcharge's end eases
        // it down from full to a Surge's strength.
        if (level >= 2 && charged != null) target = 1;
        else if (level > 0 && charged != null) target = SURGE * Math.clamp((charged.until() - now) / EASE_OUT, 0, 1);
        if (target <= 0 && !SHOWN.containsKey(id)) return 0;
        float[] shown = SHOWN.computeIfAbsent(id, k -> new float[] {0, now});
        float step = Math.clamp((now - shown[1]) / EASE_IN, 0, 1);
        shown[0] += (target - shown[0]) * (target < shown[0] ? Math.min(1, step * 2) : step);
        shown[1] = now;
        if (target <= 0 && shown[0] < .02F) { SHOWN.remove(id); return 0; }
        int v = Math.round(255 * Math.clamp(shown[0], 0, 1));
        return 0xFF000000 | v << 16 | v << 8 | v;
    }

    /** Forgets every body's eased brightness (a new world: entity ids start over). */
    static void forget() { SHOWN.clear(); }

    private static RenderType swirl(LivingEntity entity, int id, float partial) {
        float f = entity.tickCount + partial;
        float speed = ClientAmp.level(id) >= 2 ? .02F : .01F;
        return RenderType.energySwirl(texture(), f * speed % 1, f * speed % 1);
    }

    private static ResourceLocation texture() {
        if (made == 0) made = make() ? 1 : -1;
        return made > 0 ? TEXTURE : SOURCE;
    }

    /** Ando's red from the charged creeper's swirl: each pixel keeps its brightness and alpha. */
    private static boolean make() {
        var mc = Minecraft.getInstance();
        try (InputStream in = mc.getResourceManager().getResourceOrThrow(SOURCE).open(); NativeImage source = NativeImage.read(in)) {
            NativeImage red = new NativeImage(source.getWidth(), source.getHeight(), true);
            for (int y = 0; y < source.getHeight(); y++)
                for (int x = 0; x < source.getWidth(); x++) {
                    int abgr = source.getPixelRGBA(x, y);
                    int a = abgr >>> 24, b = abgr >> 16 & 255, g = abgr >> 8 & 255, r = abgr & 255;
                    int v = Math.max(r, Math.max(g, b));
                    // A deep, nearly pure red (FF1828), scaled by the swirl's brightness: the swirl adds light, so any green
                    // or blue in it turns pink over a pale skin.
                    red.setPixelRGBA(x, y, a << 24 | (v * 0x28 / 255) << 16 | (v * 0x18 / 255) << 8 | v);
                }
            mc.getTextureManager().register(TEXTURE, new DynamicTexture(red));
            return true;
        } catch (Exception e) {
            LOGGER.warn("Couldn't make the supercharged field's red from {}; using it as it is", SOURCE, e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // The layers
    // ------------------------------------------------------------------

    /** A player's field: their own model inflated by {@link #INFLATE}, posed as they are, outer skin layer left off. */
    private static final class PlayerField extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final PlayerModel<AbstractClientPlayer> shell;

        PlayerField(PlayerRenderer renderer, PlayerModel<AbstractClientPlayer> shell) {
            super(renderer);
            this.shell = shell;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float swing,
                           float amount, float partial, float age, float yaw, float pitch) {
            if (player.isInvisible()) return;
            int id = owner(player);
            int color = color(id, partial);
            if (color == 0) return;
            PlayerModel<AbstractClientPlayer> body = getParentModel();
            body.copyPropertiesTo(shell);
            shell.head.visible = body.head.visible;
            shell.body.visible = body.body.visible;
            shell.rightArm.visible = body.rightArm.visible;
            shell.leftArm.visible = body.leftArm.visible;
            shell.rightLeg.visible = body.rightLeg.visible;
            shell.leftLeg.visible = body.leftLeg.visible;
            // One shell, as the creeper has: the hat, jacket, sleeves and trousers would draw a second one inside it.
            shell.hat.visible = shell.jacket.visible = shell.leftSleeve.visible = shell.rightSleeve.visible
                    = shell.leftPants.visible = shell.rightPants.visible = false;
            shell.renderToBuffer(pose, buffers.getBuffer(swirl(player, id, partial)), light, OverlayTexture.NO_OVERLAY, color);
        }
    }

    /** Any other creature's field: its own model, drawn again a size larger about the middle of its body. */
    private static final class CreatureField<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
        CreatureField(LivingEntityRenderer<T, M> renderer) { super(renderer); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float swing, float amount,
                           float partial, float age, float yaw, float pitch) {
            if (entity instanceof Player || entity.isInvisible()) return;
            int id = owner(entity);
            int color = color(id, partial);
            if (color == 0) return;
            float width = entity.getBbWidth(), height = entity.getBbHeight();
            // About a pixel all round, whatever the size: thin things grow more, big ones less.
            float grow = 1 + Math.clamp(.14F / Math.max(.3F, Math.min(width, height)), .06F, .2F);
            // Model space runs downward from 1.501 above the feet, so the body's middle is this far down.
            float middle = 1.501F - height * .5F;
            pose.pushPose();
            pose.translate(0, middle, 0);
            pose.scale(grow, grow, grow);
            pose.translate(0, -middle, 0);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(swirl(entity, id, partial)), light, OverlayTexture.NO_OVERLAY, color);
            pose.popPose();
        }
    }
}
