package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shapeshifting's tree (Infiltrator, Beast, Identity), its signature Apex Form and its three class abilities
 * (docs/shapeshifting.md, "Skills"). Every passive changes a core move where it already decides something: the core
 * asks the questions at the bottom of this class.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ShapeshiftSkills {
    public static final String BLEND_IN = "shapeshifting_0_0", QUICK_CHANGE = "shapeshifting_0_1", SECOND_SKIN = "shapeshifting_0_2",
            HOLLOW_CROWD = "shapeshifting_hollow_crowd", SHED = "shapeshifting_shed", FACELESS = "shapeshifting_faceless",
            NATURAL_WEAPONS = "shapeshifting_1_0", HYBRID = "shapeshifting_1_1", APEX = "shapeshifting_1_2",
            AMBUSH = "shapeshifting_ambush", CHIMERA = "shapeshifting_chimera", PACK_LEADER = "shapeshifting_pack_leader",
            APEX_FORM = "shapeshifting_apex_form",
            PERFECT_COPY = "shapeshifting_2_0", BORROWED_FACE = "shapeshifting_2_1", PRACTISED = "shapeshifting_2_2",
            LONG_CON = "shapeshifting_long_con", DOPPELGANGER = "shapeshifting_doppelganger", SOCIAL = "shapeshifting_social_engineering",
            MANY_HANDS = "shapeshifting_many_hands", PLATE_MAIL = "shapeshifting_plate_mail", MASQUERADE = "shapeshifting_masquerade";

    public static final int HYBRID_TICKS = 400, CHIMERA_TICKS = 400, APEX_TICKS = 240, BORROW_TICKS = 160, AMBUSH_WINDOW = 40;
    public static final double APEX_SCALE = 2.5, PACK_RANGE = 12, FACELESS_CROWD = 8, FACELESS_REACH = 4;
    private static final ResourceLocation APEX_SIZE = id("shapeshift_apex_size"), APEX_HEALTH = id("shapeshift_apex_health"),
            APEX_ATTACK = id("shapeshift_apex_attack");
    /** Gifts a Hybrid can borrow: those the server keeps; never a weakness, and nothing only a client can draw. */
    private static final Set<ShapeshiftTraits.Trait> GIFTS = EnumSet.of(ShapeshiftTraits.Trait.NIGHT_EYES, ShapeshiftTraits.Trait.SLOW_FALL,
            ShapeshiftTraits.Trait.FLUTTER, ShapeshiftTraits.Trait.FLIGHT, ShapeshiftTraits.Trait.SWIM, ShapeshiftTraits.Trait.DEEP_BREATH,
            ShapeshiftTraits.Trait.GILLS, ShapeshiftTraits.Trait.FIRE_PROOF, ShapeshiftTraits.Trait.PROJECTILE_PROOF,
            ShapeshiftTraits.Trait.CREEPER_REPELLENT);

    private record Timed<T>(T value, long until) {}

    private static final Map<UUID, Timed<Set<ShapeshiftTraits.Trait>>> HYBRIDS = new HashMap<>();
    private static final Map<UUID, Timed<InstinctMove>> CHIMERAS = new HashMap<>();
    private static final Map<UUID, Long> APEXES = new HashMap<>();

    private ShapeshiftSkills() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    static boolean has(ServerPlayer player, String node) { return SkillTreeService.has(player, node); }

    private static long now(ServerPlayer player) { return player.serverLevel().getGameTime(); }

    // ---- Routing ----

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .activate(c -> switch (node.id()) {
                    case QUICK_CHANGE -> quickChange(c.player());
                    case SHED -> shed(c.player());
                    case HYBRID -> hybrid(c.player());
                    case CHIMERA -> chimera(c.player());
                    case APEX_FORM -> apexForm(c.player());
                    case BORROWED_FACE -> ShapeshiftTricks.borrowFace(c.player());
                    case DOPPELGANGER -> ShapeshiftTricks.doppelganger(c.player());
                    default -> false;
                }).build();
    }

    // ---- Infiltrator ----

    /** Back to the shape worn before this one (or your own body) at once, and every hunter loses you. */
    static boolean quickChange(ServerPlayer player) {
        ShapeshiftForm previous = ShapeshiftService.previous(player);
        boolean morphed = ShapeshiftService.morphed(player);
        if (previous == null && !morphed) { ShapeshiftService.say(player, "no_previous"); return false; }
        List<Mob> hunters = player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ShapeshiftService.HUNTER_SCAN),
                mob -> mob.isAlive() && ShapeshiftService.targets(mob, player));
        boolean done = previous == null ? ShapeshiftService.revert(player) : ShapeshiftService.morph(player, previous, false);
        if (!done) return false;
        ShapeshiftService.Morph morph = ShapeshiftService.wearing(player);
        for (Mob mob : hunters) {
            if (morph != null) morph.hunters.remove(mob.getUUID());
            ShapeshiftService.lose(mob, player);
        }
        return true;
    }

    /** Out of a grab, a web or a slowing hold: a Decoy where you stood and three blocks back, free. */
    static boolean shed(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ShapeshiftTricks.decoy(player);
        if (player.isPassenger()) player.stopRiding();
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        ShapeshiftInstinct.release(player);
        BlockPos.betweenClosedStream(player.getBoundingBox().inflate(.3)).forEach(pos -> {
            if (level.getBlockState(pos).is(Blocks.COBWEB)) level.destroyBlock(pos, false);
        });
        Vec3 back = player.getLookAngle().multiply(1, 0, 1);
        back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, -1) : back.normalize().scale(-3);
        Vec3 to = com.jayptucker.heroesevolved.speed.SpeedService.swept(player, back, null);
        player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        player.absMoveTo(to.x, to.y, to.z);
        player.fallDistance = 0;
        ShapeshiftService.say(player, "shed_free");
        return true;
    }

    /** Among three or more of the worn shape's kind: no Suspicion rises, and far shots miss. */
    public static boolean crowded(ServerPlayer player) {
        if (!has(player, FACELESS)) return false;
        String kin = ShapeshiftService.kin(player);
        if (kin == null || kin.startsWith("player:")) return false;
        return player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FACELESS_CROWD),
                mob -> mob.isAlive() && kin.equals(ShapeshiftForms.kin(mob))).size() >= 3;
    }

    // ---- Beast ----

    /** Your own body with the last creature's gifts for twenty seconds. */
    static boolean hybrid(ServerPlayer player) {
        if (ShapeshiftService.morphed(player)) { ShapeshiftService.say(player, "hybrid_shaped"); return false; }
        ShapeshiftForm source = ShapeshiftService.lastCreature(player);
        LivingEntity example = source == null ? null : ShapeshiftForms.example(player.serverLevel(), source.type());
        if (example == null) { ShapeshiftService.say(player, "hybrid_none"); return false; }
        Set<ShapeshiftTraits.Trait> gifts = EnumSet.noneOf(ShapeshiftTraits.Trait.class);
        for (var trait : ShapeshiftTraits.of(example)) if (GIFTS.contains(trait)) gifts.add(trait);
        endHybrid(player);
        HYBRIDS.put(player.getUUID(), new Timed<>(gifts, now(player) + HYBRID_TICKS));
        ShapeshiftTraitService.begin(player, gifts);
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_TOUCH, player.position().add(0, player.getBbHeight() * .6, 0),
                player.position(), 1, player);
        ArmPosePayload.show(player, ArmPose.SHIFT_SWEEP, 12);
        sound(player, SoundEvents.ARMOR_EQUIP_TURTLE.value(), 1, .9F);
        return true;
    }

    private static void endHybrid(ServerPlayer player) {
        Timed<Set<ShapeshiftTraits.Trait>> old = HYBRIDS.remove(player.getUUID());
        if (old != null) ShapeshiftTraitService.end(player, old.value());
    }

    /** The gifts a Hybrid is wearing, if any. */
    static Set<ShapeshiftTraits.Trait> hybridTraits(ServerPlayer player) {
        Timed<Set<ShapeshiftTraits.Trait>> h = HYBRIDS.get(player.getUUID());
        return h == null ? Set.of() : h.value();
    }

    /** For twenty seconds Instinct is the previous creature's attack. */
    static boolean chimera(ServerPlayer player) {
        ShapeshiftForm current = ShapeshiftService.worn(player), source = null;
        for (ShapeshiftForm f : ShapeshiftService.history(player))
            if (!f.person() && (current == null || !f.key().equals(current.key()))) { source = f; break; }
        LivingEntity example = source == null ? null : ShapeshiftForms.example(player.serverLevel(), source.type());
        if (example == null) { ShapeshiftService.say(player, "chimera_none"); return false; }
        CHIMERAS.put(player.getUUID(), new Timed<>(InstinctMove.of(source, example), now(player) + CHIMERA_TICKS));
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_STUDY, player.getEyePosition(), player.position(), 1, player);
        ArmPosePayload.show(player, ArmPose.SHIFT_STUDY, 12);
        sound(player, SoundEvents.ILLUSIONER_PREPARE_MIRROR, .9F, .7F);
        return true;
    }

    /** The move Chimera lends Instinct now, or null. */
    static @Nullable InstinctMove chimeraMove(ServerPlayer player) {
        Timed<InstinctMove> c = CHIMERAS.get(player.getUUID());
        return c == null || now(player) >= c.until() ? null : c.value();
    }

    /** Signature: two and a half times the size for twelve seconds, a boss's health, twice the strength. */
    static boolean apexForm(ServerPlayer player) {
        ShapeshiftForm worn = ShapeshiftService.worn(player);
        if (worn == null || worn.person()) { ShapeshiftService.say(player, "apex_needs_shape"); return false; }
        var big = MorphBodies.dimensions(worn.type(), net.minecraft.world.entity.Pose.STANDING).scale((float) APEX_SCALE);
        if (!player.level().noCollision(player, big.makeBoundingBox(player.position()).deflate(1.0E-4))) {
            ShapeshiftService.say(player, "apex_room");
            return false;
        }
        float fraction = player.getHealth() / player.getMaxHealth();
        modifier(player, Attributes.SCALE, APEX_SIZE, APEX_SCALE - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        float target = Math.min(ShapeshiftForms.healthCap(5, true), player.getMaxHealth() * (float) APEX_SCALE);
        modifier(player, Attributes.MAX_HEALTH, APEX_HEALTH, target - player.getMaxHealth(), AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.ATTACK_DAMAGE, APEX_ATTACK, 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        player.setHealth(Math.max(1, fraction * player.getMaxHealth()));
        player.refreshDimensions();
        APEXES.put(player.getUUID(), now(player) + APEX_TICKS);
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_DECOY_BURST, player.position(), player.position().add(0, player.getBbHeight(), 0), 1, null);
        ArmPosePayload.show(player, ArmPose.SHIFT_NIGHTMARE, 20);
        sound(player, SoundEvents.RAVAGER_ROAR, 1.2F, .6F);
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(player.serverLevel(), player.position(), 24, .6F, 12);
        return true;
    }

    public static boolean apex(ServerPlayer player) { return APEXES.containsKey(player.getUUID()); }

    /** Ends Apex Form (when it runs out, or the shape it grew is left). */
    static void endApex(ServerPlayer player) {
        if (APEXES.remove(player.getUUID()) == null) return;
        float fraction = player.getMaxHealth() <= 0 ? 1 : player.getHealth() / player.getMaxHealth();
        for (var a : List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE)) {
            var instance = player.getAttribute(a);
            if (instance != null) for (ResourceLocation id : List.of(APEX_SIZE, APEX_HEALTH, APEX_ATTACK)) instance.removeModifier(id);
        }
        if (player.isAlive()) player.setHealth(Math.max(.5F, fraction * player.getMaxHealth()));
        player.refreshDimensions();
    }

    private static void modifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                 ResourceLocation id, double amount, AttributeModifier.Operation op) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    // ---- Questions the core asks ----

    /** Suspicion gained, after Blend In, Practised Manner and Faceless. */
    static float suspicionGain(ServerPlayer player, float amount, boolean person) {
        if (crowded(player)) return 0;
        if (has(player, BLEND_IN)) amount *= .7F;
        if (person && has(player, PRACTISED)) amount *= .5F;
        return amount;
    }

    /** How fast Suspicion fades (Practised Manner doubles it in a person's shape). */
    static float decay(ServerPlayer player, boolean person) { return person && has(player, PRACTISED) ? 2 : 1; }

    /** How long a hunter must lose sight of you (Blend In halves it). */
    static int loseSight(ServerPlayer player) { return has(player, BLEND_IN) ? ShapeshiftService.LOSE_SIGHT_TICKS / 2 : ShapeshiftService.LOSE_SIGHT_TICKS; }

    /** The share of the form's health a single blow must take to break it (Second Skin: half). */
    static float heavyBlow(ServerPlayer player) { return has(player, SECOND_SKIN) ? .5F : ShapeshiftSuspicion.HEAVY_BLOW; }

    static int decoyLife(ServerPlayer player) { return has(player, HOLLOW_CROWD) ? ShapeDecoy.LIFE * 2 : ShapeDecoy.LIFE; }

    static int instinctCost(ServerPlayer player, int cost) { return has(player, NATURAL_WEAPONS) ? Math.max(1, Math.round(cost * .7F)) : cost; }

    static double lungeReach(ServerPlayer player) { return ShapeshiftGeometry.LUNGE_REACH + (has(player, NATURAL_WEAPONS) ? 2 : 0); }

    static float healthBonus(ServerPlayer player) { return has(player, APEX) ? 20 : 0; }

    /** Seconds per stamina drained while a large or boss shape is held (Apex: half as often). */
    static int drainPeriod(ServerPlayer player) { return has(player, APEX) ? 40 : 20; }

    /** Instinct's damage factor: 1.5 for the first one within two seconds of a change, with Ambush Shift. */
    static float ambush(ServerPlayer player) {
        if (!has(player, AMBUSH)) return 1;
        long changed = ShapeshiftService.lastChange(player);
        if (changed < 0 || now(player) - changed > AMBUSH_WINDOW) return 1;
        ShapeshiftService.spendChange(player);
        return 1.5F;
    }

    static boolean instantStudy(ServerPlayer player) { return has(player, AMBUSH) && !player.onGround(); }

    static double personReach(ServerPlayer player) { return has(player, LONG_CON) ? 12 : ShapeshiftService.STUDY_REACH; }

    static int familiarTicks(ServerPlayer player) { return has(player, SOCIAL) ? 600 : 200; }

    static boolean social(ServerPlayer player) { return has(player, SOCIAL); }

    public static boolean perfectCopy(ServerPlayer player) { return has(player, PERFECT_COPY); }

    static boolean secondSkin(ServerPlayer player) { return has(player, SECOND_SKIN); }

    static boolean hollowCrowd(ServerPlayer player) { return has(player, HOLLOW_CROWD); }

    /** Ends everything this class holds on a player (death, logout, respec, losing the power). */
    public static void forget(ServerPlayer player) {
        endHybrid(player);
        CHIMERAS.remove(player.getUUID());
        endApex(player);
    }

    static void clear() {
        HYBRIDS.clear(); CHIMERAS.clear(); APEXES.clear();
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ---- Events ----

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        for (var it = HYBRIDS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null) { it.remove(); continue; }
            long now = now(p);
            if (now >= e.getValue().until()) { it.remove(); ShapeshiftTraitService.end(p, e.getValue().value()); continue; }
            ShapeshiftTraitService.tick(p, e.getValue().value(), now);
        }
        for (var it = APEXES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null) { it.remove(); continue; }
            if (now(p) >= e.getValue()) endApex(p);
        }
    }

    /** Faceless: far shots miss. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void guard(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var source = event.getSource();
        boolean shot = source.getDirectEntity() instanceof Projectile;
        if (shot && crowded(player) && source.getEntity() != null && source.getEntity().distanceTo(player) > FACELESS_REACH)
            event.setCanceled(true);
    }

    /** Pack Leader: wolves (and, as a raider, raiders) join in on whatever you strike. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void pack(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !has(player, PACK_LEADER)) return;
        ShapeshiftForm worn = ShapeshiftService.worn(player);
        LivingEntity victim = event.getEntity();
        if (worn == null || worn.person() || victim == player) return;
        boolean raider = "raider".equals(ShapeshiftService.kin(player));
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(PACK_RANGE),
                m -> m != victim && m.isAlive() && (m instanceof Wolf w && !w.isOwnedBy(victim) || raider && m instanceof Raider)))
            mob.setTarget(victim);
    }
}
