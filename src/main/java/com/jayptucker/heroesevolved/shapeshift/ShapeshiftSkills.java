package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
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
import net.minecraft.world.entity.player.Player;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
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

    public static final int HYBRID_TICKS = 400, CHIMERA_TICKS = 400, APEX_TICKS = 240, PLATE_TICKS = 160, MASQUERADE_TICKS = 400,
            BORROW_TICKS = 160, AMBUSH_WINDOW = 40, MANY_HANDS_GAP = 3;
    public static final double APEX_SCALE = 2.5, PACK_RANGE = 12, FACELESS_CROWD = 8, FACELESS_REACH = 4;
    private static final ResourceLocation APEX_SIZE = id("shapeshift_apex_size"), APEX_HEALTH = id("shapeshift_apex_health"),
            APEX_ATTACK = id("shapeshift_apex_attack");
    /** Gifts a Hybrid can borrow: those the server keeps; never a weakness, and nothing only a client can draw. */
    private static final Set<ShapeshiftTraits.Trait> GIFTS = EnumSet.of(ShapeshiftTraits.Trait.NIGHT_EYES, ShapeshiftTraits.Trait.SLOW_FALL,
            ShapeshiftTraits.Trait.FLUTTER, ShapeshiftTraits.Trait.FLIGHT, ShapeshiftTraits.Trait.SWIM, ShapeshiftTraits.Trait.DEEP_BREATH,
            ShapeshiftTraits.Trait.GILLS, ShapeshiftTraits.Trait.FIRE_PROOF, ShapeshiftTraits.Trait.PROJECTILE_PROOF,
            ShapeshiftTraits.Trait.CREEPER_REPELLENT);

    private record Timed<T>(T value, long until) {}
    private record Strike(long at, ServerPlayer player, LivingEntity target, int index) {}

    private static final Map<UUID, Timed<Set<ShapeshiftTraits.Trait>>> HYBRIDS = new HashMap<>();
    private static final Map<UUID, Timed<InstinctMove>> CHIMERAS = new HashMap<>();
    private static final Map<UUID, Long> APEXES = new HashMap<>(), PLATES = new HashMap<>(), PLATE_SHED = new HashMap<>();
    private static final Map<UUID, Timed<ServerPlayer>> MASKED = new HashMap<>();
    private static final List<Strike> STRIKES = new ArrayList<>();

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

    /** The class abilities, cast through {@code StyleCasts}. */
    public static boolean cast(AbilityUseContext c, String mechanic) {
        return switch (mechanic) {
            case MANY_HANDS -> manyHands(c.player());
            case PLATE_MAIL -> plateMail(c.player());
            case MASQUERADE -> masquerade(c.player());
            default -> false;
        };
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

    // ---- Class abilities ----

    /** Plated arms lash out at up to five foes, one after another. */
    static boolean manyHands(ServerPlayer player) {
        List<LivingEntity> foes = new ArrayList<>(player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8),
                e -> ShapeshiftInstinct.hostile(player, e) && !(e instanceof ShapeDecoy) && e.distanceToSqr(player) <= 64 && player.hasLineOfSight(e)));
        if (foes.isEmpty()) { ShapeshiftService.say(player, "many_hands_none"); return false; }
        foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        long now = now(player);
        for (int i = 0; i < Math.min(5, foes.size()); i++) STRIKES.add(new Strike(now + (long) i * MANY_HANDS_GAP, player, foes.get(i), i));
        return true;
    }

    private static void strike(Strike s) {
        ServerPlayer player = s.player();
        LivingEntity target = s.target();
        if (!player.isAlive() || !target.isAlive() || target.level() != player.level()) return;
        Vec3 shoulder = ShapeshiftGeometry.shoulder(player, 1);
        Vec3 chest = target.position().add(0, target.getBbHeight() * .6, 0);
        target.hurt(player.damageSources().mobAttack(player), 5 + PlayerProgressionService.getLevel(player));
        ShapeshiftInstinct.knock(target, chest.subtract(shoulder).normalize().scale(.35).add(0, .15, 0));
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_LUNGE, shoulder, chest, 1, player);
        ArmPosePayload.show(player, ArmPose.SHIFT_LUNGE, 6);
        sound(player, s.index() % 2 == 0 ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_STRONG, .9F, 1.2F + s.index() * .08F);
    }

    /** Eight seconds of plates: shots skid off, 30% less from everything else, and the first blow sheds a Decoy. */
    static boolean plateMail(ServerPlayer player) {
        PLATES.put(player.getUUID(), now(player) + PLATE_TICKS);
        PLATE_SHED.remove(player.getUUID());
        showPlates(player);
        ArmPosePayload.show(player, ArmPose.SHIFT_GUARD, 16);
        sound(player, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1, .8F);
        return true;
    }

    private static void showPlates(ServerPlayer player) {
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_PLATES, player.position(), player.position().add(0, player.getBbHeight(), 0),
                player.getBbWidth(), player);
    }

    public static boolean plated(ServerPlayer player) {
        Long until = PLATES.get(player.getUUID());
        return until != null && now(player) < until;
    }

    /**
     * You and up to four friends take one creature's shape for twenty seconds: the one you wear, else the newest you
     * remember, else the nearest creature you can see.
     */
    static boolean masquerade(ServerPlayer player) {
        ShapeshiftForm worn = ShapeshiftService.worn(player);
        ShapeshiftForm form = worn != null && !worn.person() ? worn : ShapeshiftService.lastCreature(player);
        if (form == null) {
            LivingEntity seen = null;
            for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
                    m -> m.isAlive() && !(m instanceof ShapeDecoy) && player.hasLineOfSight(m) && ShapeshiftForms.requiredLevel(m) != ShapeshiftForms.NEVER))
                if (seen == null || mob.distanceToSqr(player) < seen.distanceToSqr(player)) seen = mob;
            if (seen != null) form = ShapeshiftService.formOf(seen);
        }
        if (form == null) { ShapeshiftService.say(player, "masquerade_none"); return false; }
        List<ServerPlayer> party = new ArrayList<>();
        for (LivingEntity e : StyleOps.friends(player, player.position(), 10))
            if (e instanceof ServerPlayer friend && (friend == player || !ShapeshiftService.owns(friend)) && party.size() < 5) party.add(friend);
        int given = 0;
        for (ServerPlayer friend : party) {
            ShapeshiftForm theirs = ShapeshiftService.worn(friend);
            if (theirs != null && theirs.key().equals(form.key())) { given++; continue; }
            if (!ShapeshiftService.morph(friend, form, false)) continue;
            MASKED.put(friend.getUUID(), new Timed<>(friend, now(player) + MASQUERADE_TICKS));
            if (friend == player) { given++; continue; }
            SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_INSTINCT, player.position().add(0, player.getBbHeight() * .6, 0),
                    friend.position().add(0, friend.getBbHeight() * .6, 0), InstinctMove.FAMILIAR.ordinal(), player);
            given++;
        }
        if (given == 0) return false;
        ArmPosePayload.show(player, ArmPose.SHIFT_GIFT, 16);
        sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1, .6F);
        return true;
    }

    /** Whether a player wears a shape a Masquerade gave them. */
    public static boolean masked(ServerPlayer player) { return MASKED.containsKey(player.getUUID()); }

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
        PLATES.remove(player.getUUID());
        PLATE_SHED.remove(player.getUUID());
        STRIKES.removeIf(s -> s.player() == player);
    }

    static void clear() {
        HYBRIDS.clear(); CHIMERAS.clear(); APEXES.clear(); PLATES.clear(); PLATE_SHED.clear(); MASKED.clear(); STRIKES.clear();
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ---- Events ----

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            if (s.player().isRemoved()) { it.remove(); continue; }
            if (now(s.player()) < s.at()) continue;
            it.remove();
            strike(s);
        }
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
        for (var it = PLATES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || now(p) >= e.getValue()) { it.remove(); continue; }
            if ((now(p) - (e.getValue() - PLATE_TICKS)) % 10 == 0) showPlates(p);
        }
        for (var it = MASKED.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = e.getValue().value();
            if (p.isRemoved() || !ShapeshiftService.morphed(p)) { it.remove(); continue; }
            if (now(p) < e.getValue().until()) continue;
            it.remove();
            if (!ShapeshiftService.revert(p)) ShapeshiftService.forget(p);
        }
    }

    /** Plate Mail: shots skid off, other blows land 30% lighter, the first sheds a Decoy. Faceless: far shots miss. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void guard(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var source = event.getSource();
        boolean shot = source.getDirectEntity() instanceof Projectile;
        if (shot && crowded(player) && source.getEntity() != null && source.getEntity().distanceTo(player) > FACELESS_REACH) {
            event.setCanceled(true);
            return;
        }
        if (!plated(player)) return;
        if (shot) {
            event.setCanceled(true);
            sound(player, SoundEvents.SHIELD_BLOCK, .8F, 1.3F);
            return;
        }
        event.setAmount(event.getAmount() * .7F);
        if (source.getEntity() != null && PLATE_SHED.putIfAbsent(player.getUUID(), now(player)) == null) ShapeshiftTricks.decoy(player);
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
