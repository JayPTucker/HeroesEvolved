package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.FormLibraryPayload;
import com.jayptucker.heroesevolved.network.MorphStatePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shapeshifting, phase 1: study a creature, keep its shape in a library, wear its body, and walk among its kind.
 * The server owns every form; clients only learn which form to draw ({@link MorphStatePayload}) and size the body
 * the same way through {@link MorphBodies}. See docs/shapeshifting.md.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ShapeshiftService {
    /** Ticks a creature must stay in view while it is studied. */
    public static final int STUDY_TICKS = 20;
    public static final double STUDY_REACH = 6, STUDY_KEEP = 8;
    /** A mob that was already fighting you loses you after this long out of its sight. */
    public static final int LOSE_SIGHT_TICKS = 40;
    public static final double HUNTER_SCAN = 32, WITNESS_RANGE = 16;
    static final String LIBRARY = "heroesevolved_shapeshift_forms";

    private static final ResourceLocation HEALTH = id("shapeshift_health"), ATTACK = id("shapeshift_attack"),
            ARMOR = id("shapeshift_armor"), TOUGHNESS = id("shapeshift_toughness"), KNOCKBACK = id("shapeshift_knockback"),
            SPEED = id("shapeshift_speed"), STEP = id("shapeshift_step"), JUMP = id("shapeshift_jump");
    private static final List<ResourceLocation> MODIFIERS = List.of(HEALTH, ATTACK, ARMOR, TOUGHNESS, KNOCKBACK, SPEED, STEP, JUMP);

    /** A worn form. Hunters are the mobs of its kind that saw through it, with how long each has lost sight of you. */
    static final class Morph {
        final ServerPlayer player;
        final ShapeshiftForm form;
        final EntityType<?> type;
        final String kin;
        final ShapeshiftForms.Size size;
        final Set<ShapeshiftTraits.Trait> traits;
        final boolean hands;
        final Map<UUID, Integer> hunters = new HashMap<>();
        /** How close the disguise is to being seen through, 0 to 100 (see {@link ShapeshiftSuspicion}). */
        float suspicion;

        Morph(ServerPlayer player, ShapeshiftForm form, String kin, ShapeshiftForms.Size size, Set<ShapeshiftTraits.Trait> traits, boolean hands) {
            this.player = player; this.form = form; this.type = form.type(); this.kin = kin; this.size = size;
            this.traits = traits; this.hands = hands;
        }
    }

    private record Study(ServerPlayer player, UUID target, long until) {}

    /** The last time each player picked from the form wheel, so a client can't flicker between forms. */
    private static final Map<UUID, Long> LAST_CHOICE = new HashMap<>();
    static final int CHOICE_GAP = 10;

    private static final Map<UUID, Morph> MORPHS = new HashMap<>();
    /** The shapes each player wore before the current one, newest first (null entries are their own body). */
    private static final Map<UUID, java.util.LinkedList<java.util.Optional<ShapeshiftForm>>> HISTORY = new HashMap<>();
    /** When each player last changed shape (for Ambush Shift), or absent once that change has been spent. */
    private static final Map<UUID, Long> CHANGED = new HashMap<>();
    private static final Map<UUID, Study> STUDIES = new HashMap<>();

    private ShapeshiftService() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    // ---- Questions other code asks ----

    public static boolean owns(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.SHAPESHIFTING_ID) && e.getValue().isUnlocked()).isPresent();
    }

    public static @Nullable EntityType<?> form(ServerPlayer player) {
        Morph m = MORPHS.get(player.getUUID());
        return m == null ? null : m.type;
    }

    /** The whole form worn (with a person's profile), or null in your own body. */
    public static @Nullable ShapeshiftForm worn(ServerPlayer player) {
        Morph m = MORPHS.get(player.getUUID());
        return m == null ? null : m.form;
    }

    /** The person whose shape {@code player} wears, or null. */
    public static @Nullable GameProfile posingAs(ServerPlayer player) {
        ShapeshiftForm form = worn(player);
        return form == null ? null : form.personOrNull();
    }

    public static boolean morphed(ServerPlayer player) { return MORPHS.containsKey(player.getUUID()); }

    /** The traits of the form worn (none in your own body or a person's). */
    public static Set<ShapeshiftTraits.Trait> traits(ServerPlayer player) {
        Morph m = MORPHS.get(player.getUUID());
        return m == null ? ShapeshiftSkills.hybridTraits(player) : m.traits;
    }

    /** Whether the form worn keeps hands (your own body always does). */
    public static boolean hands(ServerPlayer player) {
        Morph m = MORPHS.get(player.getUUID());
        return m == null || m.hands;
    }

    /** A living example of the form worn, for anything that reads a form's body (null for a person or your own body). */
    public static @Nullable LivingEntity example(ServerPlayer player) {
        ShapeshiftForm form = worn(player);
        return form == null || form.person() ? null : ShapeshiftForms.example(player.serverLevel(), form.type());
    }

    public static boolean studying(ServerPlayer player) { return STUDIES.containsKey(player.getUUID()); }

    /** The shape worn before the current one (null: their own body, or none at all). */
    static @Nullable ShapeshiftForm previous(ServerPlayer player) {
        var history = HISTORY.get(player.getUUID());
        return history == null || history.isEmpty() ? null : history.getFirst().orElse(null);
    }

    /** Every shape worn before the current one, newest first (own body left out). */
    static List<ShapeshiftForm> history(ServerPlayer player) {
        var history = HISTORY.get(player.getUUID());
        return history == null ? List.of() : history.stream().flatMap(java.util.Optional::stream).toList();
    }

    /** The creature's shape worn most recently, now or before; else the newest creature in the library. */
    static @Nullable ShapeshiftForm lastCreature(ServerPlayer player) {
        ShapeshiftForm worn = worn(player);
        if (worn != null && !worn.person()) return worn;
        for (ShapeshiftForm f : history(player)) if (!f.person()) return f;
        List<ShapeshiftForm> forms = forms(player);
        for (int i = forms.size() - 1; i >= 0; i--) if (!forms.get(i).person()) return forms.get(i);
        return null;
    }

    static long lastChange(ServerPlayer player) { return CHANGED.getOrDefault(player.getUUID(), -1L); }

    static void spendChange(ServerPlayer player) { CHANGED.remove(player.getUUID()); }

    private static void remember(ServerPlayer player, @Nullable ShapeshiftForm left) {
        var history = HISTORY.computeIfAbsent(player.getUUID(), id -> new java.util.LinkedList<>());
        history.addFirst(java.util.Optional.ofNullable(left));
        while (history.size() > 8) history.removeLast();
        CHANGED.put(player.getUUID(), player.serverLevel().getGameTime());
    }

    /** The worn form's state, for the other classes of this power. */
    static @Nullable Morph wearing(ServerPlayer player) { return MORPHS.get(player.getUUID()); }

    /** The family that takes the wearer for one of its own (a person's form: "player:" and their id), or null. */
    public static @Nullable String kin(ServerPlayer player) {
        Morph m = MORPHS.get(player.getUUID());
        return m == null ? null : m.kin;
    }

    /** Does {@code mob} count {@code player} as one of its own right now (and not as someone who gave themselves away)? */
    public static boolean passesAsKin(ServerPlayer player, Entity mob) {
        Morph m = MORPHS.get(player.getUUID());
        return m != null && m.kin.equals(ShapeshiftForms.kin(mob)) && !m.hunters.containsKey(mob.getUUID());
    }

    /** Why {@code player} can't learn this creature's shape (a message key), or null if they can. */
    static @Nullable String refusal(ServerPlayer player, LivingEntity target) {
        if (target instanceof Player person && operator(player.getServer(), person.getGameProfile())) return "operator";
        int needed = ShapeshiftForms.requiredLevel(target);
        if (needed == ShapeshiftForms.NEVER) return "impossible";
        return PlayerProgressionService.getPowerLevel(player) < needed ? "too_big" : null;
    }

    /**
     * Whether a profile belongs to an operator (permission level 2 or more). Nobody may wear an operator's face, so no one
     * can fake an admin's announcement.
     */
    public static boolean operator(@Nullable MinecraftServer server, GameProfile profile) {
        return server != null && server.getProfilePermissions(profile) >= 2;
    }

    /** The form a creature or a person gives. */
    public static ShapeshiftForm formOf(LivingEntity target) {
        return target instanceof Player person ? ShapeshiftForm.of(person.getGameProfile()) : ShapeshiftForm.of(target.getType());
    }

    // ---- The library ----

    /** Learned forms, oldest first. Kept through death in the persisted tag. */
    public static List<ShapeshiftForm> forms(ServerPlayer player) {
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        List<ShapeshiftForm> forms = new ArrayList<>();
        // Phase 1 saved bare type ids; later phases save a compound per form (with a person's profile).
        ListTag compounds = persisted.getList(LIBRARY, Tag.TAG_COMPOUND);
        ListTag strings = compounds.isEmpty() ? persisted.getList(LIBRARY, Tag.TAG_STRING) : new ListTag();
        for (int i = 0; i < compounds.size(); i++) add(forms, ShapeshiftForm.load(compounds.getCompound(i)));
        for (int i = 0; i < strings.size(); i++) {
            EntityType<?> type = ShapeshiftForms.type(strings.getString(i));
            if (type != null && type != EntityType.PLAYER) add(forms, ShapeshiftForm.of(type));
        }
        return forms;
    }

    private static void add(List<ShapeshiftForm> forms, @Nullable ShapeshiftForm form) {
        if (form != null && forms.stream().noneMatch(f -> f.key().equals(form.key()))) forms.add(form);
    }

    /** The types of the learned forms, oldest first (a person's form is {@link EntityType#PLAYER}). */
    public static List<EntityType<?>> library(ServerPlayer player) {
        return forms(player).stream().<EntityType<?>>map(ShapeshiftForm::type).toList();
    }

    private static void save(ServerPlayer player, List<ShapeshiftForm> forms) {
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag list = new ListTag();
        for (ShapeshiftForm form : forms) list.add(form.save());
        persisted.put(LIBRARY, list);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static boolean learn(ServerPlayer player, EntityType<?> type) { return learn(player, ShapeshiftForm.of(type)); }

    /** Adds a form to the library, forgetting the oldest if it is full. True if the form is new. */
    public static boolean learn(ServerPlayer player, ShapeshiftForm form) {
        List<ShapeshiftForm> forms = new ArrayList<>(forms(player));
        if (forms.stream().anyMatch(f -> f.key().equals(form.key()))) return false;
        int capacity = ShapeshiftForms.capacity(PlayerProgressionService.getLevel(player), PlayerProgressionService.evolved(player));
        while (forms.size() >= capacity && !forms.isEmpty()) {
            ShapeshiftForm forgotten = forms.remove(0);
            tell(player, forgotten.person() ? "forgot_person" : "forgot", forgotten.description());
        }
        forms.add(form);
        save(player, forms);
        return true;
    }

    private static void learned(ServerPlayer player, ShapeshiftForm form) {
        tell(player, form.person() ? "learned_person" : "learned", form.description());
    }

    // ---- The Morph key ----

    /** A press with a creature in sight: study it for a second, then take its shape. */
    public static boolean study(ServerPlayer player) {
        if (ShapeshiftSuspicion.locked(player)) { ShapeshiftSuspicion.tellLocked(player); return false; }
        double people = ShapeshiftSkills.personReach(player);
        LivingEntity target = Targeting.livingEntityInSight(player, Math.max(STUDY_REACH, people), e -> e != player && e.isAlive()
                && e.distanceTo(player) <= (e instanceof Player ? people : STUDY_REACH) + e.getBbWidth());
        if (target == null) { tell(player, "look"); return false; }
        String refused = refusal(player, target);
        if (refused != null) {
            if (refused.equals("too_big")) tell(player, refused, ShapeshiftForms.requiredLevel(target));
            else tell(player, refused);
            return false;
        }
        // Ambush Shift: in mid-air, a look is enough.
        if (ShapeshiftSkills.instantStudy(player)) return morph(player, formOf(target));
        STUDIES.put(player.getUUID(), new Study(player, target.getUUID(), player.serverLevel().getGameTime() + STUDY_TICKS));
        ArmPosePayload.show(player, ArmPose.SHIFT_STUDY, STUDY_TICKS);
        // Plates trace the creature's outline; only the studier and the studied see them.
        ShapeshiftGeometry.showTo(player, Piece.SHIFT_STUDY, player.getEyePosition(), target.position(), 1, target);
        if (target instanceof ServerPlayer studied)
            ShapeshiftGeometry.showTo(studied, Piece.SHIFT_STUDY, player.getEyePosition(), target.position(), 1, target);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR,
                SoundSource.PLAYERS, 0.5F, 1.4F);
        return true;
    }

    /** A Shift press: open the form wheel with every remembered form. */
    public static boolean openWheel(ServerPlayer player) {
        List<ShapeshiftForm> forms = forms(player);
        if (forms.isEmpty()) { tell(player, "empty"); return false; }
        ShapeshiftForm current = worn(player);
        PacketDistributor.sendToPlayer(player, new FormLibraryPayload(forms, current == null ? "" : current.key(),
                ShapeshiftForms.capacity(PlayerProgressionService.getLevel(player), PlayerProgressionService.evolved(player)),
                PlayerProgressionService.getPowerLevel(player)));
        return true;
    }

    /**
     * A pick from the form wheel: a remembered form's key, or "" for your own body. Refused when the power is switched
     * off, the form isn't remembered, or a pick came less than half a second ago.
     */
    public static boolean choose(ServerPlayer player, String id) {
        if (!owns(player) || !com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(player)) return false;
        long now = player.serverLevel().getGameTime();
        Long last = LAST_CHOICE.get(player.getUUID());
        if (last != null && now - last < CHOICE_GAP && now >= last) return false;
        LAST_CHOICE.put(player.getUUID(), now);
        if (id.isEmpty()) return revert(player);
        ShapeshiftForm form = forms(player).stream().filter(f -> f.key().equals(id)).findFirst().orElse(null);
        if (form == null) return false;
        STUDIES.remove(player.getUUID());
        return morph(player, form);
    }

    /** Wear the next remembered form after the current one (the wheel's step, without opening it). */
    public static boolean cycle(ServerPlayer player) {
        List<ShapeshiftForm> forms = forms(player);
        if (forms.isEmpty()) { tell(player, "empty"); return false; }
        ShapeshiftForm current = worn(player);
        int index = -1;
        for (int i = 0; current != null && i < forms.size(); i++) if (forms.get(i).key().equals(current.key())) index = i;
        int start = current == null ? forms.size() - 1 : index + 1;
        ShapeshiftForm next = forms.get(Math.floorMod(start, forms.size()));
        if (current != null && next.key().equals(current.key())) { tell(player, "same"); return false; }
        return morph(player, next);
    }

    /**
     * A press while wearing a form and looking at nothing studiable: go back to your own body. Free, like every
     * follow-up. Returns false (so the press studies instead) when a creature is in sight.
     */
    public static boolean revertOnPress(ServerPlayer player) {
        if (!morphed(player)) return false;
        if (Targeting.livingEntityInSight(player, STUDY_REACH, e -> e != player && e.isAlive()) != null) return false;
        revert(player);
        return true;
    }

    public static boolean morph(ServerPlayer player, EntityType<?> type) { return morph(player, ShapeshiftForm.of(type)); }

    /** Takes a form now. False, with a message, if there is no room or no stamina, or the face is one nobody may wear. */
    public static boolean morph(ServerPlayer player, ShapeshiftForm form) { return morph(player, form, true); }

    /**
     * Takes a form now; {@code pay} false when the move that asked (Reflection) has paid already. Refused for a while
     * after the wearer was seen through ({@link ShapeshiftSuspicion#locked}).
     */
    static boolean morph(ServerPlayer player, ShapeshiftForm form, boolean pay) {
        if (ShapeshiftSuspicion.locked(player)) { ShapeshiftSuspicion.tellLocked(player); return false; }
        EntityType<?> type = form.type();
        ShapeshiftForm current = worn(player);
        if (current != null && current.key().equals(form.key())) { tell(player, "same"); return false; }
        ShapeshiftForms.Body body;
        ShapeshiftForms.Size size;
        String kin;
        Set<ShapeshiftTraits.Trait> traits = java.util.EnumSet.noneOf(ShapeshiftTraits.Trait.class);
        boolean hands = true;
        if (form.person()) {
            // A person's shape: their face and build, nothing more. Never your own, and never an operator's.
            if (form.isOf(player)) { tell(player, "self"); return false; }
            if (operator(player.getServer(), form.personOrNull())) { tell(player, "operator"); return false; }
            body = ShapeshiftForms.PERSON;
            size = ShapeshiftForms.Size.SMALL;
            kin = "player:" + form.personId();
        } else {
            LivingEntity example = ShapeshiftForms.example(player.serverLevel(), type);
            if (example == null) return false;
            body = ShapeshiftForms.body(example);
            size = ShapeshiftForms.size(example);
            kin = ShapeshiftForms.kin(example);
            traits.addAll(ShapeshiftTraits.of(example));
            // A bat's or a parrot's flutter is real flight, so it waits for Level 6.
            if (PlayerProgressionService.getPowerLevel(player) < ShapeshiftForms.ODD) traits.remove(ShapeshiftTraits.Trait.FLUTTER);
            hands = ShapeshiftGeometry.handed(example);
        }
        if (!fits(player, MorphBodies.dimensions(type, Pose.STANDING))) { tell(player, "no_room"); return false; }
        if (pay && !PlayerEnergyService.tryConsume(player, size.cost)) { tell(player, "no_stamina", size.cost); return false; }

        float fraction = health(player);
        int tier = PlayerProgressionService.getLevel(player);
        apply(player, body, ShapeshiftForms.healthCap(tier, PlayerProgressionService.evolved(player)) + ShapeshiftSkills.healthBonus(player));
        restore(player, fraction);

        Morph previous = MORPHS.get(player.getUUID());
        if (previous != null) { ShapeshiftTraitService.end(player, previous.traits); ShapeshiftSkills.endApex(player); }
        remember(player, previous == null ? null : previous.form);
        Morph morph = new Morph(player, form, kin, size, traits, hands);
        MORPHS.put(player.getUUID(), morph);
        ShapeshiftTraitService.begin(player, traits);
        MorphBodies.set(player, type);
        sync(player);
        ShapeshiftSuspicion.reset(player);
        ArmPosePayload.show(player, ArmPose.SHIFT_SWEEP, 14);
        if (learn(player, form)) learned(player, form);
        settle(morph);
        // Social Engineering: a person's pets come running the moment you take their face.
        if (form.person() && ShapeshiftSkills.social(player)) ShapeshiftInstinct.familiar(player);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 0.8F, 0.9F);
        return true;
    }

    /** Goes back to your own body if there is room for it (standing or crouched). */
    public static boolean revert(ServerPlayer player) {
        if (!morphed(player)) return false;
        if (!fits(player, Player.STANDING_DIMENSIONS) && !fits(player, EntityDimensions.scalable(0.6F, 1.5F))) {
            tell(player, "no_room_back");
            return false;
        }
        end(player, null);
        return true;
    }

    /** A creeper's blast throws its wearer back into their own body, room or not. */
    static void detonated(ServerPlayer player) { end(player, "detonated"); }

    /** Sends the move's message (shared with the other classes of this power). */
    static void say(ServerPlayer player, String key, Object... args) { tell(player, key, args); }

    /** Drops every form and pending study, as when the power is taken away. */
    public static void forget(ServerPlayer player) {
        STUDIES.remove(player.getUUID());
        ShapeshiftInstinct.forget(player);
        ShapeshiftTricks.forget(player);
        ShapeshiftSkills.forget(player);
        if (morphed(player)) end(player, null);
        HISTORY.remove(player.getUUID());
        CHANGED.remove(player.getUUID());
    }

    private static void end(ServerPlayer player, @Nullable String reason) { end(player, reason, true); }

    /**
     * Breaks a form at once: the plates burst off ({@code SHIFT_EXPOSED}) instead of rolling down, and the body under
     * them is simply there. Used when someone sees through the disguise or a blow is too heavy for it to hold.
     */
    static void breakForm(ServerPlayer player, String reason) {
        if (!morphed(player)) return;
        com.jayptucker.heroesevolved.network.SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_EXPOSED,
                player.position(), player.position().add(0, player.getBbHeight(), 0), player.getBbWidth(), player);
        ArmPosePayload.show(player, ArmPose.SHIFT_SHED, 14);
        end(player, reason, false);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(),
                SoundSource.PLAYERS, 1, .6F);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BREAK,
                SoundSource.PLAYERS, .8F, .7F);
    }

    /** Every mob of the worn form's kind near you forgets you, even one that saw through you (Reflection). */
    static void forgetKin(ServerPlayer player, double radius) {
        Morph morph = MORPHS.get(player.getUUID());
        if (morph == null) return;
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                mob -> morph.kin.equals(ShapeshiftForms.kin(mob)))) {
            morph.hunters.remove(mob.getUUID());
            if (targets(mob, player)) lose(mob, player);
        }
    }

    private static void end(ServerPlayer player, @Nullable String reason, boolean animate) {
        Morph ended = MORPHS.get(player.getUUID());
        if (ended == null) return;
        ShapeshiftSkills.endApex(player);
        MORPHS.remove(player.getUUID());
        remember(player, ended.form);
        ShapeshiftTraitService.end(player, ended.traits);
        float fraction = health(player);
        for (Holder<Attribute> attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR,
                Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED, Attributes.STEP_HEIGHT,
                Attributes.JUMP_STRENGTH)) {
            var instance = player.getAttribute(attribute);
            if (instance != null) for (ResourceLocation id : MODIFIERS) instance.removeModifier(id);
        }
        restore(player, fraction);
        MorphBodies.set(player, null);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload(player, animate && player.isAlive()));
        ShapeshiftSuspicion.reset(player);
        if (animate && player.isAlive())
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                    SoundSource.PLAYERS, 0.7F, 1.2F);
        if (reason != null) tell(player, reason);
    }

    // ---- The body ----

    private static boolean fits(ServerPlayer player, EntityDimensions dimensions) {
        AABB box = dimensions.makeBoundingBox(player.position()).deflate(1.0E-4);
        return player.level().noCollision(player, box);
    }

    private static float health(ServerPlayer player) { return player.getMaxHealth() <= 0 ? 1 : player.getHealth() / player.getMaxHealth(); }

    /** Keeps the same share of health across a change, so nobody heals or dies by changing shape. */
    private static void restore(ServerPlayer player, float fraction) {
        if (!player.isAlive()) return;
        player.setHealth(Math.max(0.5F, fraction * player.getMaxHealth()));
    }

    private static void apply(ServerPlayer player, ShapeshiftForms.Body body, float healthCap) {
        set(player, Attributes.MAX_HEALTH, HEALTH, Math.min(body.health(), healthCap) - base(player, Attributes.MAX_HEALTH), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_DAMAGE, ATTACK, body.attack() - base(player, Attributes.ATTACK_DAMAGE), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR, ARMOR, body.armor(), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS, body.toughness(), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, body.knockback(), AttributeModifier.Operation.ADD_VALUE);
        double speed = base(player, Attributes.MOVEMENT_SPEED);
        set(player, Attributes.MOVEMENT_SPEED, SPEED, speed <= 0 ? 0 : body.speed() / speed - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.STEP_HEIGHT, STEP, Math.max(0, body.step() - base(player, Attributes.STEP_HEIGHT)), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.JUMP_STRENGTH, JUMP, body.jump() - base(player, Attributes.JUMP_STRENGTH), AttributeModifier.Operation.ADD_VALUE);
    }

    private static double base(ServerPlayer player, Holder<Attribute> attribute) {
        var instance = player.getAttribute(attribute);
        return instance == null ? 0 : instance.getBaseValue();
    }

    private static void set(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (Math.abs(amount) > 1.0E-6) instance.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    // ---- Kin ----

    /** On a change, kin who can see you keep fighting (they watched it happen); the rest lose you. */
    private static void settle(Morph morph) {
        ServerPlayer player = morph.player;
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HUNTER_SCAN),
                mob -> targets(mob, player) && morph.kin.equals(ShapeshiftForms.kin(mob)))) {
            if (mob.hasLineOfSight(player)) morph.hunters.put(mob.getUUID(), 0);
            else lose(mob, player);
        }
    }

    static boolean targets(Mob mob, ServerPlayer player) {
        if (mob.getTarget() == player) return true;
        var brain = mob.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).filter(t -> t == player).isPresent();
    }

    static void lose(Mob mob, ServerPlayer player) {
        if (mob.getTarget() == player) mob.setTarget(null);
        var brain = mob.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).filter(t -> t == player).isPresent())
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)
                && brain.getMemory(MemoryModuleType.ANGRY_AT).filter(id -> id.equals(player.getUUID())).isPresent())
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
    }

    // ---- Ticking ----

    static void tick(MinecraftServer server) {
        for (Study study : List.copyOf(STUDIES.values())) tickStudy(study);
        for (Morph morph : List.copyOf(MORPHS.values())) tickMorph(morph);
    }

    private static void tickStudy(Study study) {
        ServerPlayer player = study.player;
        if (player.isRemoved() || !player.isAlive()) { STUDIES.remove(player.getUUID()); return; }
        Entity found = player.serverLevel().getEntity(study.target);
        if (!(found instanceof LivingEntity target) || !target.isAlive() || player.distanceToSqr(target) > STUDY_KEEP * STUDY_KEEP
                || !player.hasLineOfSight(target)) {
            STUDIES.remove(player.getUUID());
            tell(player, "lost");
            return;
        }
        if (player.serverLevel().getGameTime() < study.until) return;
        STUDIES.remove(player.getUUID());
        morph(player, formOf(target));
    }

    private static void tickMorph(Morph morph) {
        ServerPlayer player = morph.player;
        if (player.isRemoved()) {
            MORPHS.remove(player.getUUID());
            MorphBodies.set(false, player.getId(), null);
            return;
        }
        if (blocked(player)) { end(player, "slipped"); return; }
        long now = player.serverLevel().getGameTime();
        ShapeshiftTraitService.tick(player, morph.traits, now);
        if (!MORPHS.containsKey(player.getUUID())) return;
        ShapeshiftSuspicion.tick(player, morph, now);
        if (!MORPHS.containsKey(player.getUUID())) return;
        if (now % ShapeshiftSkills.drainPeriod(player) == 0 && morph.size.drainPerSecond > 0 && !PlayerEnergyService.tryConsume(player, morph.size.drainPerSecond)) {
            end(player, "tired");
            return;
        }
        if (now % 20 == 0 && !morph.form.person() && owns(player) && !player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(8), mob -> mob.isAlive() && morph.kin.equals(ShapeshiftForms.kin(mob))).isEmpty())
            com.jayptucker.heroesevolved.progression.EvolutionTrials.advance(player, ModAbilities.SHAPESHIFTING_ID);
        if (now % 5 != 0) return;
        var hunters = morph.hunters.entrySet().iterator();
        while (hunters.hasNext()) {
            var entry = hunters.next();
            Entity found = player.serverLevel().getEntity(entry.getKey());
            if (!(found instanceof Mob mob) || !mob.isAlive()) { hunters.remove(); continue; }
            if (mob.hasLineOfSight(player)) { entry.setValue(0); continue; }
            int unseen = entry.getValue() + 5;
            if (unseen >= ShapeshiftSkills.loseSight(player)) { hunters.remove(); lose(mob, player); }
            else entry.setValue(unseen);
        }
    }

    /** Anything that switches powers off also drops a worn form. */
    private static boolean blocked(ServerPlayer player) {
        return com.jayptucker.heroesevolved.events.EclipseService.arePowersSuppressed(player)
                || com.jayptucker.heroesevolved.company.SuppressorField.suppresses(player)
                || com.jayptucker.heroesevolved.dampener.Dampening.dampened(player)
                || com.jayptucker.heroesevolved.story.sylar.WatchmakerArena.stolen(player);
    }

    // ---- Talking to clients and players ----

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload(player, player.isAlive()));
    }

    private static MorphStatePayload payload(ServerPlayer player, boolean animate) {
        return new MorphStatePayload(player.getId(), java.util.Optional.ofNullable(worn(player)), animate,
                ShapeshiftSkills.perfectCopy(player) ? MorphStatePayload.PERFECT_COPY : 0);
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("shapeshift.heroesevolved." + key, args), true);
    }

    // ---- Events ----

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    /** Kin don't pick a fight with one of their own. */
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && passesAsKin(player, event.getEntity()))
            event.setNewAboutToBeSetTarget(null);
    }

    /** Hurting one of your own kind gives you away to it and to every one of them who saw. */
    @SubscribeEvent public static void provoke(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        Morph morph = MORPHS.get(attacker.getUUID());
        LivingEntity victim = event.getEntity();
        if (morph == null || !morph.kin.equals(ShapeshiftForms.kin(victim))) return;
        if (victim instanceof Mob) morph.hunters.put(victim.getUUID(), 0);
        for (Mob witness : attacker.serverLevel().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(WITNESS_RANGE),
                mob -> mob != victim && morph.kin.equals(ShapeshiftForms.kin(mob))
                        && (mob.hasLineOfSight(victim) || mob.hasLineOfSight(attacker))))
            morph.hunters.put(witness.getUUID(), 0);
    }

    /** Touching a creature with an empty hand learns its shape at once. The touch still does whatever it normally does. */
    @SubscribeEvent public static void touch(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().isEmpty() || !owns(player) || !(event.getTarget() instanceof LivingEntity target)) return;
        if (refusal(player, target) != null) return;
        ShapeshiftForm form = formOf(target);
        if (!learn(player, form)) return;
        learned(player, form);
        ArmPosePayload.show(player, ArmPose.SHIFT_TOUCH, 10);
        Vec3 touch = target.getBoundingBox().clip(player.getEyePosition(), target.getBoundingBox().getCenter())
                .orElse(target.getBoundingBox().getCenter());
        ShapeshiftGeometry.showTo(player, Piece.SHIFT_TOUCH, touch, target.position(), 1, target);
        if (target instanceof ServerPlayer touched) ShapeshiftGeometry.showTo(touched, Piece.SHIFT_TOUCH, touch, target.position(), 1, target);
        player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 0.7F);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { forget(player); LAST_CHOICE.remove(player.getUUID()); ShapeshiftSuspicion.leave(player); }
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target && morphed(target))
            PacketDistributor.sendToPlayer(viewer, payload(target, false));
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        MORPHS.clear();
        STUDIES.clear();
        LAST_CHOICE.clear();
        MorphBodies.clear(false);
        ShapeshiftForms.clear();
        ShapeshiftHands.clear();
        ShapeshiftSuspicion.clear();
        ShapeshiftTricks.clear();
        ShapeshiftSkills.clear();
        HISTORY.clear();
        CHANGED.clear();
    }
}
