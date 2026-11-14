package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.shapeshift.MorphBodies;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftForm;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftGeometry;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a shapeshifter as their form. The player model is skipped and a client-only stand-in of the form, never added
 * to the level, is drawn in its place with the player's pose copied on: facing, head, walk cycle, swing, crouch and
 * hurt flash. A person's form is a {@link Double}: a player with the copied person's skin and name tag, wearing the
 * shapeshifter's own gear (a clue, until Perfect Copy).
 *
 * <p>A change is a {@link Transition}: {@code ShapeArt} closes plates over the old body, the body underneath is swapped
 * while the shell is shut, and the plates fall away from the new one (the timeline is in {@link ShapeshiftGeometry}).
 * In first person a form with hands shows its own arm, and a form without them shows none.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class MorphClient {
    /**
     * One change of shape on one player: from a form (null for their own body) to another, starting at a game time.
     * The sizes are the bodies' hitboxes, which the plates hug.
     */
    public record Transition(int entity, @Nullable ShapeshiftForm from, @Nullable ShapeshiftForm to, long start, int ticks,
                             float fromWidth, float fromHeight, float toWidth, float toHeight) {
        public float progress(long now, float partial) { return Mth.clamp((now - start + partial) / ticks, 0, 1); }
        public boolean done(long now) { return now - start > ticks; }
        public boolean down() { return to == null; }
    }

    /**
     * A person's double: a player with the copied person's profile, drawn in the shapeshifter's place. It carries the
     * shapeshifter's entity id, so arm poses and other per-player drawing follow it, and asks for the copied person's
     * skin: the one their client already has if they are online, otherwise the one in the profile.
     */
    public static final class Double extends RemotePlayer {
        private final GameProfile copied;
        private final int owner;

        Double(ClientLevel level, GameProfile copied, int owner) {
            super(level, copied);
            this.copied = copied;
            this.owner = owner;
            if (owner >= 0) setId(owner);
            getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        }

        public int owner() { return owner; }

        @Override public PlayerSkin getSkin() {
            var connection = Minecraft.getInstance().getConnection();
            var info = connection == null ? null : connection.getPlayerInfo(copied.getId());
            return info != null ? info.getSkin() : Minecraft.getInstance().getSkinManager().getInsecureSkin(copied);
        }
    }

    /** A double of a person for drawing anywhere else (the form wheel): owned by no one. */
    public static Double person(ClientLevel level, GameProfile profile) { return new Double(level, profile, -1); }

    /** Each player's stand-ins by form key: during a change both the old and the new are kept. */
    private static final Map<Integer, Map<String, LivingEntity>> STAND_INS = new HashMap<>();
    /** The whole form (with a person's profile) each player wears, on this client. */
    private static final Map<Integer, ShapeshiftForm> FORMS = new HashMap<>();
    private static final Map<Integer, Transition> TRANSITIONS = new HashMap<>();
    /** How far each transition had got last tick, so its sounds play once as it crosses each stage. */
    private static final Map<Integer, Float> HEARD = new HashMap<>();

    private MorphClient() {}

    /** Each entity's morph flags ({@code MorphStatePayload.PERFECT_COPY}). */
    private static final Map<Integer, Integer> FLAGS = new HashMap<>();

    public static void update(int entity, @Nullable ShapeshiftForm form, boolean animate) { update(entity, form, animate, 0); }

    public static void update(int entity, @Nullable ShapeshiftForm form, boolean animate, int flags) {
        var level = Minecraft.getInstance().level;
        Entity found = level == null ? null : level.getEntity(entity);
        if (flags == 0) FLAGS.remove(entity); else FLAGS.put(entity, flags);
        if (!(found instanceof Player) && found != null) {
            // A creature wearing a shapeshifter's face (Borrowed Face): drawn as that face, never resized.
            if (form == null) FORMS.remove(entity); else FORMS.put(entity, form);
            STAND_INS.remove(entity);
            return;
        }
        ShapeshiftForm previous = FORMS.get(entity);
        Transition old = TRANSITIONS.remove(entity);
        if (animate && found instanceof Player player && !same(previous, form) && level != null) {
            // A change that interrupts another starts from whatever the viewer can see now.
            ShapeshiftForm from = old != null && old.progress(level.getGameTime(), 0) < ShapeshiftGeometry.SWAP ? old.from : previous;
            EntityDimensions a = dimensions(from, player), b = dimensions(form, player);
            TRANSITIONS.put(entity, new Transition(entity, from, form, level.getGameTime(),
                    ShapeshiftGeometry.morphTicks(a, b), a.width(), a.height(), b.width(), b.height()));
            HEARD.put(entity, 0F);
        }
        if (form == null) FORMS.remove(entity); else FORMS.put(entity, form);
        MorphBodies.set(true, entity, form == null ? null : form.type());
        if (found != null) found.refreshDimensions();
    }

    private static boolean same(@Nullable ShapeshiftForm a, @Nullable ShapeshiftForm b) {
        return a == null ? b == null : b != null && a.key().equals(b.key());
    }

    private static EntityDimensions dimensions(@Nullable ShapeshiftForm form, Player player) {
        return form == null ? Player.STANDING_DIMENSIONS : MorphBodies.dimensions(form.type(), player.getPose());
    }

    /** Every change of shape now playing, for {@code ShapeArt}. */
    public static Iterable<Transition> transitions() { return TRANSITIONS.values(); }

    public static @Nullable Transition transition(int entity) { return TRANSITIONS.get(entity); }

    /** The form this client thinks {@code entity} wears, or null. */
    public static @Nullable ShapeshiftForm form(int entity) { return FORMS.get(entity); }

    /** The body to draw for this player right now: their form, or the old one before the shell closes. Null means their own. */
    private static @Nullable ShapeshiftForm shown(Player player, @Nullable Transition t, float partial) {
        if (t == null) return FORMS.get(player.getId());
        float p = t.progress(player.level().getGameTime(), partial);
        return p < ShapeshiftGeometry.SWAP ? t.from : t.to;
    }

    /** The form drawn for this player now (null for their own body), counting a change in progress. */
    public static @Nullable ShapeshiftForm shownForm(Player player, float partial) {
        return shown(player, TRANSITIONS.get(player.getId()), partial);
    }

    /** Whether the plates are shut round this player, so no body at all is drawn. */
    private static boolean sealed(@Nullable Transition t, Player player, float partial) {
        if (t == null) return false;
        float p = t.progress(player.level().getGameTime(), partial);
        return p > ShapeshiftGeometry.COVERED + .02F && p < ShapeshiftGeometry.SHED - .01F;
    }

    /** A stand-in of {@code form} for this player, made on first use and kept while it is drawn; null if it can't be. */
    private static @Nullable LivingEntity standIn(Player player, ShapeshiftForm form) {
        Map<String, LivingEntity> mine = STAND_INS.computeIfAbsent(player.getId(), id -> new HashMap<>());
        LivingEntity existing = mine.get(form.key());
        if (existing != null) return existing;
        LivingEntity living;
        if (form.person()) {
            if (!(player.level() instanceof ClientLevel level)) return null;
            living = new Double(level, form.personOrNull(), player.getId());
        } else {
            Entity made;
            try { made = form.type().create(player.level()); } catch (RuntimeException e) { made = null; }
            if (!(made instanceof LivingEntity created)) return null;
            if (created instanceof Mob mob) mob.setNoAi(true);
            living = created;
        }
        living.setSilent(true);
        copyPosition(living, player);
        mine.put(form.key(), living);
        return living;
    }

    private static void copyPosition(LivingEntity standIn, Player player) {
        standIn.setPosRaw(player.getX(), player.getY(), player.getZ());
        standIn.xo = player.xo; standIn.yo = player.yo; standIn.zo = player.zo;
        standIn.xOld = player.xOld; standIn.yOld = player.yOld; standIn.zOld = player.zOld;
    }

    private static void copyPose(LivingEntity standIn, Player player) {
        copyPosition(standIn, player);
        standIn.setYRot(player.getYRot()); standIn.yRotO = player.yRotO;
        standIn.setXRot(player.getXRot()); standIn.xRotO = player.xRotO;
        standIn.yBodyRot = player.yBodyRot; standIn.yBodyRotO = player.yBodyRotO;
        standIn.yHeadRot = player.yHeadRot; standIn.yHeadRotO = player.yHeadRotO;
        standIn.setOnGround(player.onGround());
        standIn.setShiftKeyDown(player.isShiftKeyDown());
        standIn.setSprinting(player.isSprinting());
        standIn.setInvisible(player.isInvisible());
        standIn.swinging = player.swinging; standIn.swingTime = player.swingTime;
        standIn.swingingArm = player.swingingArm;
        standIn.attackAnim = player.attackAnim; standIn.oAttackAnim = player.oAttackAnim;
        standIn.hurtTime = player.hurtTime; standIn.hurtDuration = player.hurtDuration;
        standIn.deathTime = player.deathTime;
        if (standIn instanceof Double copy) {
            // A person's shape still wears your own gear and holds what you hold: one of the clues. Perfect Copy hides it.
            boolean perfect = (FLAGS.getOrDefault(player.getId(), 0) & com.jayptucker.heroesevolved.network.MorphStatePayload.PERFECT_COPY) != 0;
            copy.setPose(player.getPose());
            copy.setMainArm(player.getMainArm());
            for (EquipmentSlot slot : EquipmentSlot.values())
                if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR)
                    copy.setItemSlot(slot, perfect ? net.minecraft.world.item.ItemStack.EMPTY : player.getItemBySlot(slot));
        }
    }

    /** The warden each player is seen as during a Nightmare (only on a foe's client, which alone gets the piece). */
    private static final Map<Integer, LivingEntity> NIGHTMARES = new HashMap<>();

    /**
     * Nightmare, as a foe sees it: a warden rears up where the shapeshifter stands, roaring, a little larger than life,
     * and shrinks away again. Only a foe's client has the piece that asks for it, so nobody else sees anything but plates.
     */
    private static boolean nightmare(RenderPlayerEvent.Pre event, Player player, float partial) {
        var piece = com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.boundTo(
                com.jayptucker.heroesevolved.styles.Piece.SHIFT_NIGHTMARE, player.getId());
        if (piece == null || piece.scale() < 1.5F) { NIGHTMARES.remove(player.getId()); return false; }
        float age = player.level().getGameTime() - piece.start() + partial;
        float grow = Mth.clamp(age / 5, 0, 1), shrink = Mth.clamp((age - 26) / 8, 0, 1);
        float size = (1 - (1 - grow) * (1 - grow) * (1 - grow)) * (1 - shrink * shrink) * 1.12F;
        if (size <= .02F) return true;
        LivingEntity warden = NIGHTMARES.computeIfAbsent(player.getId(), id -> {
            var made = EntityType.WARDEN.create(player.level());
            if (made == null) return null;
            made.setNoAi(true);
            made.setSilent(true);
            made.roarAnimationState.start(0);
            return made;
        });
        if (warden == null) return false;
        copyPose(warden, player);
        warden.tickCount = (int) age;
        if (warden instanceof net.minecraft.world.entity.monster.warden.Warden w && !w.roarAnimationState.isStarted())
            w.roarAnimationState.start(0);
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.scale(size, size, size);
        float yaw = Mth.lerp(partial, player.yRotO, player.getYRot());
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(warden)
                .render(warden, yaw, partial, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
        return true;
    }

    @SubscribeEvent public static void render(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player instanceof Double || UnseenClient.hidden(player.getId())) return;
        float partial = event.getPartialTick();
        if (nightmare(event, player, partial)) { event.setCanceled(true); return; }
        Transition t = TRANSITIONS.get(player.getId());
        if (sealed(t, player, partial)) { event.setCanceled(true); return; }
        ShapeshiftForm form = shown(player, t, partial);
        if (form == null) return;
        LivingEntity standIn = standIn(player, form);
        if (standIn == null) return;
        event.setCanceled(true);
        copyPose(standIn, player);
        float yaw = Mth.lerp(partial, player.yRotO, player.getYRot());
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(standIn);
        // Apex Form: the player's scale grows the shape drawn too.
        float size = player.getScale();
        var pose = event.getPoseStack();
        pose.pushPose();
        if (Math.abs(size - 1) > 1.0E-3) pose.scale(size, size, size);
        renderer.render(standIn, yaw, partial, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    /** A creature wearing a borrowed face is drawn as that face (a person's double, or another creature). */
    @SubscribeEvent public static void renderCreature(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity who = event.getEntity();
        if (who instanceof Player || who instanceof Double) return;
        ShapeshiftForm face = FORMS.get(who.getId());
        if (face == null) return;
        Map<String, LivingEntity> mine = STAND_INS.computeIfAbsent(who.getId(), id -> new HashMap<>());
        LivingEntity standIn = mine.get(face.key());
        if (standIn == null) {
            if (face.person() && who.level() instanceof ClientLevel level) standIn = person(level, face.personOrNull());
            else {
                Entity made;
                try { made = face.type().create(who.level()); } catch (RuntimeException e) { made = null; }
                if (made instanceof LivingEntity living) { if (living instanceof Mob mob) mob.setNoAi(true); standIn = living; }
            }
            if (standIn == null) return;
            standIn.setSilent(true);
            mine.put(face.key(), standIn);
        }
        event.setCanceled(true);
        standIn.setPosRaw(who.getX(), who.getY(), who.getZ());
        standIn.xo = who.xo; standIn.yo = who.yo; standIn.zo = who.zo;
        standIn.setYRot(who.getYRot()); standIn.yRotO = who.yRotO;
        standIn.setXRot(who.getXRot()); standIn.xRotO = who.xRotO;
        standIn.yBodyRot = who.yBodyRot; standIn.yBodyRotO = who.yBodyRotO;
        standIn.yHeadRot = who.yHeadRot; standIn.yHeadRotO = who.yHeadRotO;
        standIn.setOnGround(who.onGround());
        standIn.tickCount = who.tickCount;
        standIn.hurtTime = who.hurtTime;
        standIn.walkAnimation.setSpeed(who.walkAnimation.speed());
        float partial = event.getPartialTick();
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(standIn).render(standIn,
                Mth.lerp(partial, who.yRotO, who.getYRot()), partial, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    /** Your own double's name tag stays hidden from you, as your own name tag is. */
    @SubscribeEvent public static void nameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Double copy && copy.owner() >= 0 && copy.owner() == idOf(Minecraft.getInstance().getCameraEntity()))
            event.setCanRender(TriState.FALSE);
    }

    private static int idOf(@Nullable Entity entity) { return entity == null ? Integer.MIN_VALUE : entity.getId(); }

    // ---- First person ----

    /** The arm a humanoid form draws in place of yours, with its sleeve (a person's form) and texture. */
    public record FormArm(ModelPart arm, @Nullable ModelPart sleeve, ResourceLocation texture) {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable FormArm formArm(Player player, HumanoidArm side) {
        ShapeshiftForm form = shownForm(player, 0);
        if (form == null) return null;
        LivingEntity standIn = standIn(player, form);
        if (standIn == null) return null;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(standIn);
        if (!(renderer instanceof LivingEntityRenderer living) || !(living.getModel() instanceof HumanoidModel<?> model)) return null;
        ModelPart sleeve = model instanceof PlayerModel<?> person ? side == HumanoidArm.RIGHT ? person.rightSleeve : person.leftSleeve : null;
        return new FormArm(side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm, sleeve, living.getTextureLocation(standIn));
    }

    /** Whether the local player wears a form, and whether that form keeps its hands. */
    private static @Nullable Boolean handed(Player player) {
        ShapeshiftForm form = shownForm(player, 0);
        if (form == null) return null;
        LivingEntity standIn = standIn(player, form);
        return standIn != null && ShapeshiftGeometry.handed(standIn);
    }

    /** A form without hands shows nothing in first person: no arm and nothing held. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && Boolean.FALSE.equals(handed(player))) event.setCanceled(true);
    }

    /** A form with hands shows its own bare arm (a zombie's, a skeleton's, the copied person's) where yours would be. */
    @SubscribeEvent public static void arm(RenderArmEvent event) {
        Player player = event.getPlayer();
        if (shownForm(player, 0) == null) return;
        event.setCanceled(true);
        FormArm form = formArm(player, event.getArm());
        if (form == null) return;
        for (ModelPart part : new ModelPart[] {form.arm(), form.sleeve()}) {
            if (part == null) continue;
            var saved = part.storePose();
            try {
                part.resetPose();
                part.setRotation(0, 0, 0);
                part.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(form.texture())),
                        event.getPackedLight(), OverlayTexture.NO_OVERLAY);
            } finally {
                part.loadPose(saved);
            }
        }
    }

    // ---- Ticking ----

    /** Walk cycles and idle animations advance once a tick, from the player's own movement; finished changes are let go. */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        for (var entry : MorphBodies.client().entrySet()) {
            Entity found = level.getEntity(entry.getKey());
            if (!(found instanceof Player player)) continue;
            // A body that arrived before its form (or the reverse) is resized here.
            var wanted = MorphBodies.dimensions(entry.getValue(), player.getPose()).scale(player.getScale());
            if (Math.abs(player.getBbWidth() - wanted.width()) > 1.0E-3 || Math.abs(player.getBbHeight() - wanted.height()) > 1.0E-3)
                player.refreshDimensions();
        }
        for (Transition t : TRANSITIONS.values()) sounds(level, t, now);
        TRANSITIONS.values().removeIf(t -> t.done(now) || level.getEntity(t.entity()) == null);
        HEARD.keySet().retainAll(TRANSITIONS.keySet());
        var players = STAND_INS.entrySet().iterator();
        while (players.hasNext()) {
            var entry = players.next();
            int id = entry.getKey();
            Transition t = TRANSITIONS.get(id);
            ShapeshiftForm worn = FORMS.get(id);
            // Keep only the bodies that can still be drawn: the form worn, and both ends of a change in progress.
            entry.getValue().keySet().removeIf(key -> !matches(worn, key) && (t == null || !matches(t.from(), key) && !matches(t.to(), key)));
            if (entry.getValue().isEmpty() || !(level.getEntity(id) instanceof Player player)) { players.remove(); continue; }
            double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
            for (LivingEntity standIn : entry.getValue().values()) {
                standIn.tickCount = player.tickCount;
                standIn.walkAnimation.update(Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0F, 1.0F), 0.4F);
            }
        }
        FORMS.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    private static boolean matches(@Nullable ShapeshiftForm form, String key) { return form != null && form.key().equals(key); }

    /** The shell clicks shut, then the plates rattle loose. */
    private static void sounds(ClientLevel level, Transition t, long now) {
        Entity e = level.getEntity(t.entity());
        if (e == null) return;
        float p = t.progress(now, 0), before = HEARD.getOrDefault(t.entity(), 0F);
        if (before < ShapeshiftGeometry.COVERED && p >= ShapeshiftGeometry.COVERED)
            level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, .7F, .6F, false);
        if (before < ShapeshiftGeometry.SHED && p >= ShapeshiftGeometry.SHED)
            level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, .9F, 1.5F, false);
        HEARD.put(t.entity(), p);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STAND_INS.clear();
        FORMS.clear();
        TRANSITIONS.clear();
        HEARD.clear();
        MorphBodies.clear(true);
    }
}
