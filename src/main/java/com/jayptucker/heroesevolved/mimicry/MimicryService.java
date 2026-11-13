package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightDynamicsService;
import com.jayptucker.heroesevolved.flight.DiveBombService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.progression.PowerLevels;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Empathic Mimicry, inspired by Peter Petrelli: be near someone with a power, feel it, and borrow it. Copying never
 * transfers or claims the other player's role.
 *
 * <p>Three phases, by Power Level:
 * <ul>
 *   <li><b>1 to 5, Near.</b> One set at a time: the power of whoever you were last near (a player, a gifted villager,
 *   the meltdown villager, Sylar or Elle). Two seconds within ten blocks attunes you; a touch does it at once.</li>
 *   <li><b>6 to 14, Kept.</b> Touch someone (a hit, or an empty-hand right-click) to keep their set: two kept sets
 *   besides the one you are near.</li>
 *   <li><b>15 to 20, Remembered.</b> Three kept sets (four at Evolved), and every copied kit is complete.</li>
 * </ul>
 * Echo I to V perform the channeled set's five abilities, one level weaker than its owner's, unlocking at the mimic's
 * own levels 1, 3, 6, 10 and 15. The memory wheel switches between sets.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MimicryService {
    /** Power Levels where touch starts keeping sets, and where whole kits and a third set arrive. */
    public static final int KEEP_LEVEL = 6, REMEMBER_LEVEL = 15;
    /** Ticks of being near someone before their power is yours. */
    public static final int ATTUNE_TICKS = 40;
    public static final double ATTUNE_RANGE = 10, QUICK_STUDY_RANGE = 20;

    /**
     * One borrowed set: the power, who it came from (their name, and their id when there is one) and what they were
     * ({@code player}, {@code villager}, {@code sylar}, {@code elle} or {@code memory} for one carried over from an
     * older save).
     */
    public record Imprint(ResourceLocation power, String name, Optional<UUID> origin, String kind) {
        public static final Codec<Imprint> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("power").forGetter(Imprint::power),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Imprint::name),
                net.minecraft.core.UUIDUtil.CODEC.optionalFieldOf("origin").forGetter(Imprint::origin),
                Codec.STRING.optionalFieldOf("kind", "memory").forGetter(Imprint::kind)).apply(i, Imprint::new));
        boolean same(Imprint other) { return other != null && power.equals(other.power) && origin.equals(other.origin); }
    }

    /**
     * What the mimic carries: the set they are near (or last were), the sets they have kept by touch, oldest first,
     * and which one is channeled ({@code -1} for the near set). Older saves (a plain list of powers) load as kept sets.
     */
    public record Memory(Optional<Imprint> near, List<Imprint> kept, int channel) {
        public static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
                Imprint.CODEC.optionalFieldOf("near").forGetter(Memory::near),
                Imprint.CODEC.listOf().optionalFieldOf("kept", List.of()).forGetter(Memory::kept),
                Codec.INT.optionalFieldOf("channel", -1).forGetter(Memory::channel),
                ResourceLocation.CODEC.listOf().optionalFieldOf("powers", List.of()).forGetter(m -> List.of()),
                Codec.INT.optionalFieldOf("active", 0).forGetter(m -> 0)).apply(i, Memory::load));

        private static Memory load(Optional<Imprint> near, List<Imprint> kept, int channel,
                                   List<ResourceLocation> legacy, int active) {
            if (near.isPresent() || !kept.isEmpty() || legacy.isEmpty()) return new Memory(near, kept, channel);
            List<Imprint> carried = legacy.stream().map(p -> new Imprint(p, "", Optional.empty(), "memory")).toList();
            return new Memory(Optional.empty(), carried, Math.floorMod(active, carried.size()));
        }

        public static Memory empty() { return new Memory(Optional.empty(), List.of(), -1); }

        /** The channeled set, or null when there is nothing to channel. */
        public Imprint channeled() {
            if (channel >= 0 && channel < kept.size()) return kept.get(channel);
            if (near.isPresent()) return near.get();
            return kept.isEmpty() ? null : kept.getLast();
        }

        public ResourceLocation current() { Imprint imprint = channeled(); return imprint == null ? null : imprint.power(); }

        /** Every set carried, the near one first: the order the memory wheel shows them in. */
        public List<Imprint> slots() {
            List<Imprint> slots = new ArrayList<>();
            near.ifPresent(slots::add);
            slots.addAll(kept);
            return slots;
        }

        /** The powers carried (the near set's first). */
        public List<ResourceLocation> powers() { return slots().stream().map(Imprint::power).toList(); }

        /** The wheel slot that is channeled. */
        public int selectedSlot() {
            Imprint channeled = channeled();
            List<Imprint> slots = slots();
            for (int i = 0; i < slots.size(); i++) if (slots.get(i) == channeled) return i;
            return -1;
        }
    }

    private static final Set<UUID> OVERCHARGED = new HashSet<>();
    private record EchoCast(AbilityAction action, AbilityUseContext context, boolean full) {}
    private static final class Borrowing {
        final ServerPlayer player;
        final ServerLevel level;
        final Map<ResourceLocation, EchoCast> pressed = new HashMap<>();
        final Map<ResourceLocation, AbilityUseContext> used = new HashMap<>();
        boolean flight;
        Borrowing(ServerPlayer player) { this.player = player; level = player.serverLevel(); }
    }
    private static final Map<UUID, Borrowing> BORROWING = new HashMap<>();
    // Authorization exists only within an actual Echo call, or for the Flight session it started.
    private static final Map<UUID, ResourceLocation> INVOKING = new HashMap<>();
    /** Which of the source power's slots each Echo performs. */
    private static final Map<ResourceLocation, AbilitySlot> ECHO_SLOTS = new HashMap<>();
    private static final class Attune { UUID source; int ticks; }
    private static final Map<UUID, Attune> ATTUNES = new HashMap<>();
    /** Whom each mimic last touched and when, so one swing landing twice on the same person counts once. */
    private record Touch(UUID target, long at) {}
    private static final Map<UUID, Touch> LAST_TOUCH = new HashMap<>();
    /** When each mimic last switched sets, so a held key can't flicker between them. */
    private static final Map<UUID, Long> LAST_SWITCH = new HashMap<>();

    private MimicryService() {}

    public static boolean isMimic(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.MIMICRY_ID) && e.getValue().isUnlocked()).isPresent()
                && !EclipseService.arePowersSuppressed(player);
    }

    public static Memory memory(ServerPlayer player) { return player.getData(ModDataAttachments.MIMIC_MEMORY); }

    private static void store(ServerPlayer player, Memory next) {
        memoryChanging(player, next.current());
        player.setData(ModDataAttachments.MIMIC_MEMORY, next);
        sync(player);
    }

    /** How many sets a mimic can keep by touch at this Power Level (Sylar's Mirror adds one). */
    public static int keepCapacity(ServerPlayer player, int powerLevel) {
        int base = powerLevel >= PowerLevels.EVOLVED ? 4 : powerLevel >= REMEMBER_LEVEL ? 3 : powerLevel >= KEEP_LEVEL ? 2 : 0;
        return base == 0 ? 0 : base + (SkillTreeService.has(player, "empathic_mimicry_sylars_mirror") ? 1 : 0);
    }

    public static int keepCapacity(ServerPlayer player) {
        return keepCapacity(player, PlayerProgressionService.getPowerLevel(player));
    }

    /** Every set the mimic can hold at once: the near one plus the kept ones. */
    public static int slots(ServerPlayer player, int powerLevel) { return 1 + keepCapacity(player, powerLevel); }

    /**
     * Keeps {@code power} as if its owner had been touched, and channels it. For tests, commands and older callers:
     * {@code tier} is a tier (1 to 5), and the mimic keeps sets as if they were at least that tier's level.
     */
    public static boolean remember(ServerPlayer player, ResourceLocation power, int tier) {
        if (!copyable(power)) return false;
        Imprint imprint = new Imprint(power, "", Optional.empty(), "memory");
        int level = Math.max(PlayerProgressionService.getPowerLevel(player), PowerLevels.levelForTier(Math.clamp(tier, 1, 5)));
        int capacity = keepCapacity(player, level);
        if (capacity == 0) setNear(player, imprint, true);
        else keep(player, imprint, capacity);
        return true;
    }

    private static boolean copyable(ResourceLocation power) {
        return power != null && !power.equals(ModAbilities.MIMICRY_ID) && AbilityRegistry.ABILITIES.containsKey(power);
    }

    private static Component name(ResourceLocation power) {
        return Component.translatable("ability." + power.getNamespace() + "." + power.getPath());
    }

    // ------------------------------------------------------------------
    // Who has a power
    // ------------------------------------------------------------------

    /** What {@code mimic} could borrow from {@code source}, or null if they have nothing to give. */
    public static Imprint imprintOf(ServerPlayer mimic, LivingEntity source) {
        if (source == mimic || !source.isAlive() || source.isSpectator()) return null;
        if (source instanceof ServerPlayer other) {
            ResourceLocation power = power(other);
            return copyable(power) ? new Imprint(power, other.getGameProfile().getName(), Optional.of(other.getUUID()), "player") : null;
        }
        if (source instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity) {
            List<ResourceLocation> learned = sylarLearned(mimic);
            // The same mimic always feels the same one of his stolen powers, so being near him doesn't flicker.
            ResourceLocation power = learned.get(Math.floorMod(mimic.getUUID().hashCode(), learned.size()));
            return new Imprint(power, "Sylar", Optional.of(source.getUUID()), "sylar");
        }
        if (source instanceof com.jayptucker.heroesevolved.story.elle.ElleEntity)
            return new Imprint(ModAbilities.ELECTROKINESIS_ID, "Elle Bishop", Optional.of(source.getUUID()), "elle");
        if (source instanceof net.minecraft.world.entity.npc.Villager villager) {
            ResourceLocation power = com.jayptucker.heroesevolved.village.VillagerMeltdown.critical(villager)
                    ? ModAbilities.RADIATION_ID : GiftedVillagers.gift(villager);
            if (!copyable(power)) return null;
            String name = villager.hasCustomName() ? villager.getCustomName().getString() : "a villager";
            return new Imprint(power, name, Optional.of(villager.getUUID()), "villager");
        }
        return null;
    }

    private static List<ResourceLocation> sylarLearned(ServerPlayer mimic) {
        List<ResourceLocation> learned = new ArrayList<>(com.jayptucker.heroesevolved.story.sylar.SylarStoryData
                .get(mimic.getServer()).learned().stream().filter(MimicryService::copyable).toList());
        if (learned.isEmpty()) learned.add(ModAbilities.TELEKINESIS_ID);
        learned.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        return learned;
    }

    private static ResourceLocation power(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked()).map(Map.Entry::getKey).orElse(null);
    }

    // ------------------------------------------------------------------
    // Near: attuning to whoever you are close to
    // ------------------------------------------------------------------

    /** The set the mimic is near replaces the last one. Channeling follows it only if it was on the near set. */
    private static void setNear(ServerPlayer player, Imprint imprint, boolean channel) {
        Memory memory = memory(player);
        boolean follow = channel || memory.channel() < 0 || memory.channeled() == null;
        store(player, new Memory(Optional.of(imprint), memory.kept(), follow ? -1 : memory.channel()));
        player.displayClientMessage(Component.translatable("mimicry.heroesevolved.attuned", name(imprint.power()),
                source(imprint)).withStyle(ChatFormatting.GOLD), true);
    }

    private static Component source(Imprint imprint) {
        return imprint.name().isEmpty() ? Component.translatable("mimicry.heroesevolved.someone") : Component.literal(imprint.name());
    }

    /**
     * One step (five ticks) of attunement. Two seconds near someone with a power makes it yours: gold threads draw
     * tight between you as it builds. Public for tests.
     */
    public static void attune(ServerPlayer player) {
        if (!PowerRules.canAct(player)) { ATTUNES.remove(player.getUUID()); return; }
        boolean quick = SkillTreeService.has(player, "empathic_mimicry_quick_study");
        double range = quick ? QUICK_STUDY_RANGE : ATTUNE_RANGE;
        LivingEntity nearest = null;
        Imprint found = null;
        double best = range * range;
        for (LivingEntity other : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                e -> e != player && e.distanceToSqr(player) <= range * range && (quick || player.hasLineOfSight(e)))) {
            double distance = other.distanceToSqr(player);
            if (distance >= best) continue;
            Imprint imprint = imprintOf(player, other);
            if (imprint == null) continue;
            best = distance;
            nearest = other;
            found = imprint;
        }
        Memory memory = memory(player);
        Imprint candidate = found;
        // Nothing to feel: no one near, the one you're already near, or someone whose set you already keep.
        if (candidate == null || candidate.same(memory.near().orElse(null)) || memory.kept().stream().anyMatch(candidate::same)) {
            ATTUNES.remove(player.getUUID());
            return;
        }
        Attune attune = ATTUNES.computeIfAbsent(player.getUUID(), id -> new Attune());
        if (!nearest.getUUID().equals(attune.source)) { attune.source = nearest.getUUID(); attune.ticks = 0; }
        attune.ticks += 5;
        float progress = Math.min(1, attune.ticks / (float) ATTUNE_TICKS);
        if (attune.ticks % 10 == 5) MimicVisuals.attuning(player, nearest, found.power(), progress);
        if (attune.ticks < ATTUNE_TICKS) return;
        ATTUNES.remove(player.getUUID());
        setNear(player, found, false);
        MimicVisuals.imprint(player, nearest, found.power(), false);
    }

    // ------------------------------------------------------------------
    // Kept: touch someone to keep their set
    // ------------------------------------------------------------------

    /**
     * A hit or an empty-hand touch: below Level 6 it attunes at once; from 6 it keeps the set, and channels it. The
     * oldest kept set makes room when the memory is full.
     */
    public static boolean touch(ServerPlayer player, LivingEntity target) {
        if (!isMimic(player) || !PowerRules.canAct(player)) return false;
        Imprint imprint = imprintOf(player, target);
        if (imprint == null) return false;
        long now = player.serverLevel().getGameTime();
        Touch last = LAST_TOUCH.get(player.getUUID());
        if (last != null && last.target().equals(target.getUUID()) && now - last.at() < 10) return true;
        LAST_TOUCH.put(player.getUUID(), new Touch(target.getUUID(), now));
        int capacity = keepCapacity(player);
        if (capacity == 0) setNear(player, imprint, true);
        else if (target instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity
                && SkillTreeService.has(player, "empathic_mimicry_sylars_mirror")) {
            // Sylar's Mirror: touching him keeps everything he has stolen that there is room for.
            for (ResourceLocation power : sylarLearned(player))
                keep(player, new Imprint(power, "Sylar", Optional.of(target.getUUID()), "sylar"), capacity);
        } else keep(player, imprint, capacity);
        MimicVisuals.imprint(player, target, imprint.power(), true);
        return true;
    }

    private static void keep(ServerPlayer player, Imprint imprint, int capacity) {
        Memory memory = memory(player);
        List<Imprint> kept = new ArrayList<>(memory.kept());
        kept.removeIf(imprint::same);
        kept.add(imprint);
        while (kept.size() > capacity) kept.removeFirst();
        store(player, new Memory(memory.near(), List.copyOf(kept), kept.size() - 1));
        player.displayClientMessage(Component.translatable("mimicry.heroesevolved.kept", name(imprint.power()), source(imprint))
                .withStyle(ChatFormatting.GOLD), true);
    }

    @SubscribeEvent public static void attack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity target)
            touch(player, target);
    }

    @SubscribeEvent public static void interact(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        // An empty hand, not sneaking (sneak + use is a carry).
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !player.getMainHandItem().isEmpty() || player.isShiftKeyDown()
                || !(event.getTarget() instanceof LivingEntity target)) return;
        if (touch(player, target)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    // ------------------------------------------------------------------
    // The memory wheel
    // ------------------------------------------------------------------

    /** Channels the set in wheel slot {@code slot} (the near set first, then the kept ones). */
    public static boolean choose(ServerPlayer player, int slot) {
        if (!isMimic(player)) return false;
        Memory memory = memory(player);
        List<Imprint> slots = memory.slots();
        if (slot < 0 || slot >= slots.size() || slot == memory.selectedSlot()) return false;
        long now = player.serverLevel().getGameTime();
        Long last = LAST_SWITCH.get(player.getUUID());
        if (last != null && now - last < 6) return false;
        LAST_SWITCH.put(player.getUUID(), now);
        int channel = memory.near().isPresent() ? slot - 1 : slot;
        store(player, new Memory(memory.near(), memory.kept(), channel));
        Imprint chosen = slots.get(slot);
        MimicVisuals.shift(player, chosen.power());
        player.displayClientMessage(Component.translatable("mimicry.heroesevolved.channeling", name(chosen.power()), source(chosen))
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Lets a kept set go (the near set can't be forgotten: it is whoever you are next to). */
    public static boolean forgetSlot(ServerPlayer player, int slot) {
        Memory memory = memory(player);
        int index = memory.near().isPresent() ? slot - 1 : slot;
        if (index < 0 || index >= memory.kept().size()) return false;
        List<Imprint> kept = new ArrayList<>(memory.kept());
        Imprint gone = kept.remove(index);
        int channel = memory.channel() == index ? -1 : memory.channel() > index ? memory.channel() - 1 : memory.channel();
        store(player, new Memory(memory.near(), List.copyOf(kept), channel));
        player.displayClientMessage(Component.translatable("mimicry.heroesevolved.forgot", name(gone.power())), true);
        return true;
    }

    /** Steps the channel through the sets carried (for older callers and overexertion). */
    public static boolean recall(ServerPlayer player, int step) {
        Memory memory = memory(player);
        List<Imprint> slots = memory.slots();
        if (slots.isEmpty()) {
            player.displayClientMessage(Component.translatable("mimicry.heroesevolved.empty"), true);
            return false;
        }
        int slot = Math.floorMod(Math.max(0, memory.selectedSlot()) + step, slots.size());
        int channel = memory.near().isPresent() ? slot - 1 : slot;
        store(player, new Memory(memory.near(), memory.kept(), channel));
        player.displayClientMessage(Component.translatable("mimicry.heroesevolved.channeling", name(slots.get(slot).power()),
                source(slots.get(slot))).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Sends the memory to its owner's wheel, and refreshes their ability names (Echoes show the copied abilities). */
    public static void sync(ServerPlayer player) {
        if (player.connection == null) return;
        Memory memory = memory(player);
        List<com.jayptucker.heroesevolved.network.MimicMemoryPayload.Slot> slots = new ArrayList<>();
        memory.near().ifPresent(n -> slots.add(slot(n, false)));
        for (Imprint kept : memory.kept()) slots.add(slot(kept, true));
        PacketDistributor.sendToPlayer(player, new com.jayptucker.heroesevolved.network.MimicMemoryPayload(List.copyOf(slots),
                memory.selectedSlot(), keepCapacity(player), PlayerProgressionService.getPowerLevel(player)));
        com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(player);
    }

    private static com.jayptucker.heroesevolved.network.MimicMemoryPayload.Slot slot(Imprint imprint, boolean kept) {
        return new com.jayptucker.heroesevolved.network.MimicMemoryPayload.Slot(imprint.power().getPath(), imprint.name(),
                imprint.kind(), kept, imprint.origin().map(UUID::toString).orElse(""));
    }

    /** The copied ability an Echo shows in menus (its name and icon), or null when nothing is channeled. */
    public static AbilityActionDefinition shown(ServerPlayer player, AbilityAction echo) {
        AbilitySlot slot = ECHO_SLOTS.get(echo.definition().id());
        ResourceLocation power = slot == null ? null : memory(player).current();
        Ability ability = power == null ? null : AbilityRegistry.ABILITIES.get(power);
        AbilityAction copied = ability == null ? null : ability.actions().get(slot);
        return copied == null ? null : copied.definition();
    }

    // ------------------------------------------------------------------
    // Echoes: performing the channeled set's abilities
    // ------------------------------------------------------------------

    /** Flight's native ownership gates also accept this narrowly scoped, transient borrowed session. */
    public static boolean isBorrowingFlight(ServerPlayer player) {
        if (player.isRemoved() || !isMimic(player) || !PowerRules.canAct(player)
                || !ModAbilities.FLIGHT_ID.equals(memory(player).current())) return false;
        if (ModAbilities.FLIGHT_ID.equals(INVOKING.get(player.getUUID()))) return true;
        Borrowing state = BORROWING.get(player.getUUID());
        return state != null && state.player == player && state.level == player.serverLevel() && state.flight
                && (FlightService.hasActiveSession(player) || DiveBombService.isDiving(player)
                    || FlightDynamicsService.isCharging(player));
    }

    /** Authorizes fire channels only during an Echo call or its still-current borrowed session. */
    public static boolean isBorrowingPyrokinesis(ServerPlayer player) {
        if (player.isRemoved() || !isMimic(player) || !PowerRules.canAct(player)
                || !ModAbilities.PYROKINESIS_ID.equals(memory(player).current())) return false;
        if (ModAbilities.PYROKINESIS_ID.equals(INVOKING.get(player.getUUID()))) return true;
        Borrowing state = BORROWING.get(player.getUUID());
        return state != null && state.player == player && state.level == player.serverLevel()
                && state.used.containsKey(ModAbilities.PYROKINESIS_ID);
    }

    /** Fade can be sustained by the real Echo session without claiming Invisibility ownership. */
    public static boolean isBorrowingInvisibility(ServerPlayer player) {
        if (player.isRemoved() || !isMimic(player) || !PowerRules.canAct(player)
                || !ModAbilities.INVISIBILITY_ID.equals(memory(player).current())) return false;
        if (ModAbilities.INVISIBILITY_ID.equals(INVOKING.get(player.getUUID()))) return true;
        Borrowing state = BORROWING.get(player.getUUID());
        return state != null && state.player == player && state.level == player.serverLevel()
                && state.used.containsKey(ModAbilities.INVISIBILITY_ID);
    }

    /** Authorizes a Cryokinesis channel or ride only within its real borrowed Echo session. */
    public static boolean isBorrowingCryokinesis(ServerPlayer player) {
        if (player.isRemoved() || !isMimic(player) || !PowerRules.canAct(player)
                || !ModAbilities.CRYOKINESIS_ID.equals(memory(player).current())) return false;
        if (ModAbilities.CRYOKINESIS_ID.equals(INVOKING.get(player.getUUID()))) return true;
        Borrowing state = BORROWING.get(player.getUUID());
        return state != null && state.player == player && state.level == player.serverLevel()
                && state.used.containsKey(ModAbilities.CRYOKINESIS_ID);
    }

    public static boolean isBorrowingTechnopathy(ServerPlayer player) {
        return isBorrowingConstructPower(player, ModAbilities.TECHNOPATHY_ID);
    }

    public static boolean isBorrowingVibe(ServerPlayer player) {
        return isBorrowingConstructPower(player, ModAbilities.VIBE_ID);
    }

    public static boolean isBorrowingMatterAssembly(ServerPlayer player) {
        return isBorrowingConstructPower(player, ModAbilities.MATTER_ASSEMBLY_ID);
    }

    /** Only a real Echo invocation or its still-selected session authorizes these constructs. */
    private static boolean isBorrowingConstructPower(ServerPlayer player, ResourceLocation power) {
        if (player.isRemoved() || !isMimic(player) || !PowerRules.canAct(player)
                || !power.equals(memory(player).current())) return false;
        if (power.equals(INVOKING.get(player.getUUID()))) return true;
        Borrowing state = BORROWING.get(player.getUUID());
        return state != null && state.player == player && state.level == player.serverLevel()
                && state.used.containsKey(power);
    }

    private static <T> T invoke(EchoCast cast, Supplier<T> operation) {
        UUID id = cast.context.player().getUUID();
        ResourceLocation previous = INVOKING.put(id, cast.context.abilityId());
        try { return operation.get(); }
        finally { if (previous == null) INVOKING.remove(id); else INVOKING.put(id, previous); }
    }

    private static EchoCast selected(AbilityUseContext c, AbilitySlot slot) {
        if (c.player().isRemoved() || !isMimic(c.player()) || !PowerRules.canAct(c.player())) return null;
        ResourceLocation power = memory(c.player()).current();
        Ability ability = power == null || power.equals(ModAbilities.MIMICRY_ID) ? null : AbilityRegistry.ABILITIES.get(power);
        AbilityAction action = ability == null ? null : ability.actions().get(slot);
        if (action == null) return null;
        boolean full = OVERCHARGED.contains(c.player().getUUID());
        return new EchoCast(action, new AbilityUseContext(c.player(), power,
                full ? c.abilityLevel() : Math.max(1, c.abilityLevel() - 1), c.modifierHeld()), full);
    }

    /** Echo releases retain their original action, power and level even if Recall changes the memory. */
    public static AbilityAction echo(int index, AbilitySlot copied, int unlock) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "mimicry_echo_" + index);
        ECHO_SLOTS.put(id, copied);
        AbilityActionDefinition definition = new AbilityActionDefinition(id, "action.heroesevolved.mimicry_echo_" + index, unlock, 0, 0);
        return new AbilityAction() {
            private EchoCast pressed(ServerPlayer player) {
                Borrowing state = BORROWING.get(player.getUUID());
                if (state != null && state.player != player) {
                    forget(state.player);
                    return null;
                }
                return state == null ? null : state.pressed.get(id);
            }
            @Override public AbilityActionDefinition definition() { return definition; }
            @Override public boolean canUse(AbilityUseContext c) {
                EchoCast cast = selected(c, copied);
                if (cast == null) {
                    c.player().displayClientMessage(Component.translatable("mimicry.heroesevolved.empty"), true);
                    return false;
                }
                return pressed(c.player()) == null && invoke(cast, () -> cast.action.canUse(cast.context));
            }
            @Override public int energyCost(AbilityUseContext c) {
                EchoCast cast = selected(c, copied);
                if (cast == null) return 0;
                int cost = cast.action.energyCost(cast.context);
                return SkillTreeService.has(c.player(), "empathic_mimicry_peters_burden") ? (int) Math.ceil(cost * .75) : cost;
            }
            @Override public int cooldownTicks(AbilityUseContext c) {
                EchoCast cast = pressed(c.player());
                if (cast == null) cast = selected(c, copied);
                if (cast == null) return 20;
                int ticks = (int) Math.ceil(cast.action.cooldownTicks(cast.context) * 1.5);
                return cast.full && SkillTreeService.has(c.player(), "empathic_mimicry_twin_echo") ? ticks / 2 : ticks;
            }
            @Override public AbilityActivationResult activate(AbilityUseContext c) {
                EchoCast cast = selected(c, copied);
                if (cast == null || pressed(c.player()) != null
                        || !invoke(cast, () -> cast.action.canUse(cast.context))) return AbilityActivationResult.REJECTED;
                AbilityActivationResult result = invoke(cast, () -> cast.action.activate(cast.context));
                if (result != AbilityActivationResult.SUCCESS) return result;
                if (cast.full) OVERCHARGED.remove(c.player().getUUID());
                Borrowing state = BORROWING.computeIfAbsent(c.player().getUUID(), ignored -> new Borrowing(c.player()));
                state.pressed.put(id, cast);
                state.used.put(cast.context.abilityId(), cast.context);
                if (cast.context.abilityId().equals(ModAbilities.FLIGHT_ID)) state.flight = true;
                return result;
            }
            @Override public void release(AbilityUseContext c) {
                Borrowing state = BORROWING.get(c.player().getUUID());
                EchoCast cast = state == null ? null : state.pressed.remove(id);
                if (cast != null) invoke(cast, () -> { cast.action.release(cast.context); return true; });
            }
            @Override public boolean followUp(AbilityUseContext c) {
                EchoCast cast = selected(c, copied);
                return cast != null && invoke(cast, () -> cast.action.followUp(cast.context));
            }
            @Override public boolean deactivate(AbilityUseContext c) {
                EchoCast cast = pressed(c.player());
                if (cast == null) cast = selected(c, copied);
                if (cast == null) return false;
                EchoCast original = cast;
                AbilityUseContext ending = new AbilityUseContext(c.player(), cast.context.abilityId(), cast.context.abilityLevel(), true);
                boolean stopped = invoke(original, () -> original.action.deactivate(ending));
                if (stopped) {
                    Borrowing state = BORROWING.get(c.player().getUUID());
                    if (state != null) state.pressed.remove(id);
                }
                return stopped;
            }
        };
    }

    private static void memoryChanging(ServerPlayer player, ResourceLocation next) {
        Borrowing state = BORROWING.get(player.getUUID());
        if (state == null || !state.flight || ModAbilities.FLIGHT_ID.equals(next)) return;
        // A borrowed toggle cannot be stockpiled while channeling a different remembered power.
        state.flight = false;
        FlightService.stopForPowerRemoval(player);
        DiveBombService.forget(player);
        com.jayptucker.heroesevolved.network.FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    /** Also handles attachment changes by commands or other mods, and does not depend on being in the player list. */
    public static void tickBorrowed(ServerPlayer player) {
        Borrowing state = BORROWING.get(player.getUUID());
        if (state == null) return;
        if (state.player != player || player.isRemoved() || state.level != player.serverLevel()
                || !isMimic(player) || !PowerRules.canAct(player)) {
            forget(state.player);
            return;
        }
        memoryChanging(player, memory(player).current());
        if (!FlightService.hasActiveSession(player) && !DiveBombService.isDiving(player)
                && !FlightDynamicsService.isCharging(player)) state.flight = false;
    }

    /** Overexertion scrambles which memory answers. */
    public static void overload(ServerPlayer player) {
        List<Imprint> slots = memory(player).slots();
        if (!slots.isEmpty()) recall(player, 1 + player.getRandom().nextInt(slots.size()));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120));
    }

    /** The awakening attunes to whoever is nearest with a power. */
    public static void awaken(ServerPlayer player) {
        LivingEntity nearest = null;
        Imprint found = null;
        for (LivingEntity other : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24),
                e -> e != player)) {
            Imprint imprint = imprintOf(player, other);
            if (imprint != null && (nearest == null || other.distanceToSqr(player) < nearest.distanceToSqr(player))) {
                nearest = other;
                found = imprint;
            }
        }
        if (found != null) {
            setNear(player, found, true);
            MimicVisuals.imprint(player, nearest, found.power(), false);
        }
        player.displayClientMessage(Component.translatable("mimicry.heroesevolved.awakened").withStyle(ChatFormatting.GOLD), false);
    }

    // ------------------------------------------------------------------
    // Skill tree
    // ------------------------------------------------------------------

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        switch (branch) {
            case 0 -> {
                LivingEntity target = Targeting.livingEntityInSight(p, 40, t -> ExpansionSkills.hostile(p, t));
                if (target == null) return false;
                target.hurt(p.damageSources().playerAttack(p), 6 + c.abilityLevel());
                AbilityEffects.send(p.serverLevel(), Kind.BLOOD_THREAD, p.getEyePosition(), target.getEyePosition(), 1);
            }
            case 1 -> {
                for (ServerPlayer friend : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(8),
                        other -> other == p || p.isAlliedTo(other))) friend.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0));
            }
            case 2 -> {
                for (ServerPlayer friend : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(6),
                        other -> other == p || p.isAlliedTo(other)))
                    for (MobEffectInstance effect : List.copyOf(friend.getActiveEffects()))
                        if (!effect.getEffect().value().isBeneficial()) friend.removeEffect(effect.getEffect());
            }
            default -> { return false; }
        }
        return true;
    }

    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        switch (mechanic) {
            case "sense_powers" -> {
                int found = 0;
                for (LivingEntity other : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48),
                        e -> e != p && imprintOf(p, e) != null)) {
                    PrivateGlow.glow(p, other, 160);
                    found++;
                }
                p.displayClientMessage(Component.translatable("mimicry.heroesevolved.sensed", found), true);
                return true;
            }
            case "overcharge" -> {
                OVERCHARGED.add(p.getUUID());
                p.displayClientMessage(Component.translatable("mimicry.heroesevolved.overcharged").withStyle(ChatFormatting.GOLD), true);
                return true;
            }
            case "absorb_pain" -> {
                LivingEntity friend = Targeting.livingEntityInSight(p, 8, t -> t != p && ExpansionSkills.friendly(p, t));
                if (friend == null) return false;
                for (MobEffectInstance effect : List.copyOf(friend.getActiveEffects())) {
                    if (effect.getEffect().value().isBeneficial()) continue;
                    friend.removeEffect(effect.getEffect());
                    p.addEffect(new MobEffectInstance(effect.getEffect(), Math.max(20, effect.getDuration() / 2), effect.getAmplifier()));
                }
                friend.heal(SkillTreeService.has(p, "empathic_mimicry_shared_healing") ? 8 : 4);
                AbilityEffects.send(p.serverLevel(), Kind.BLOOD_THREAD, friend.getEyePosition(), p.getEyePosition(), 1);
                return true;
            }
            default -> { return false; }
        }
    }

    // ------------------------------------------------------------------
    // Passive: empathy
    // ------------------------------------------------------------------

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Borrowing state : List.copyOf(BORROWING.values())) tickBorrowed(state.player);
        long now = server.overworld().getGameTime();
        if (now % 5 == 0) for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isMimic(player) && !player.isSpectator()) attune(player);
            else ATTUNES.remove(player.getUUID());
        }
        if (now % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isMimic(player)) continue;
            boolean near = !player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8),
                    other -> other != player && imprintOf(player, other) != null).isEmpty();
            if (near) PlayerEnergyService.restore(player, 1);
            // The Empath's Calm capstone: the poisons of borrowed powers cannot take hold.
            if (SkillTreeService.has(player, "empathic_mimicry_empaths_calm")) { player.removeEffect(MobEffects.POISON); player.removeEffect(MobEffects.WITHER); }
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    public static void forget(ServerPlayer player) {
        OVERCHARGED.remove(player.getUUID());
        ATTUNES.remove(player.getUUID());
        Borrowing state = BORROWING.remove(player.getUUID());
        if (state == null) return;
        for (EchoCast cast : state.pressed.values()) {
            AbilityUseContext ending = new AbilityUseContext(player, cast.context.abilityId(), cast.context.abilityLevel(), true);
            invoke(cast, () -> {
                if (!cast.action.deactivate(ending)) cast.action.release(cast.context);
                return true;
            });
        }
        for (var entry : state.used.entrySet()) {
            Ability borrowed = AbilityRegistry.ABILITIES.get(entry.getKey());
            if (borrowed != null) borrowed.onRevoked(entry.getValue());
        }
        INVOKING.remove(player.getUUID());
        if (state.used.containsKey(ModAbilities.FLIGHT_ID))
            com.jayptucker.heroesevolved.network.FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Borrowing state : List.copyOf(BORROWING.values())) forget(state.player);
        OVERCHARGED.clear(); INVOKING.clear(); ATTUNES.clear(); LAST_TOUCH.clear(); LAST_SWITCH.clear();
    }
}
