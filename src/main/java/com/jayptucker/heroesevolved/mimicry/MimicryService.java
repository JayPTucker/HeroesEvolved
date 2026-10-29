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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Empathic Mimicry, inspired by Peter Petrelli: stand near someone with a power, feel it, and
 * remember it. Echo I, II and III perform the remembered power's first three abilities, one level
 * weaker than its owner's. Copying never transfers or claims the other player's role.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MimicryService {
    /** Remembered powers, oldest first, and which one is being channeled. */
    public record Memory(List<ResourceLocation> powers, int active) {
        public static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("powers").forGetter(Memory::powers),
                Codec.INT.fieldOf("active").forGetter(Memory::active)).apply(i, Memory::new));
        public static Memory empty() { return new Memory(List.of(), 0); }
        public ResourceLocation current() { return powers.isEmpty() ? null : powers.get(Math.floorMod(active, powers.size())); }
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

    private MimicryService() {}

    public static boolean isMimic(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.MIMICRY_ID) && e.getValue().isUnlocked()).isPresent()
                && !EclipseService.arePowersSuppressed(player);
    }

    public static Memory memory(ServerPlayer player) { return player.getData(ModDataAttachments.MIMIC_MEMORY); }

    public static int slots(ServerPlayer player, int level) {
        int base = level >= 5 ? 4 : Math.min(3, Math.max(1, level));
        return base + (SkillTreeService.has(player, "empathic_mimicry_sylars_mirror") ? 1 : 0);
    }

    /** Remembers {@code power}; the oldest memory fades when there is no room. */
    public static boolean remember(ServerPlayer player, ResourceLocation power, int level) {
        if (power == null || power.equals(ModAbilities.MIMICRY_ID) || !AbilityRegistry.ABILITIES.containsKey(power)) return false;
        List<ResourceLocation> powers = new ArrayList<>(memory(player).powers());
        powers.remove(power);
        powers.add(power);
        while (powers.size() > slots(player, level)) powers.removeFirst();
        Memory next = new Memory(List.copyOf(powers), powers.size() - 1);
        memoryChanging(player, next.current());
        player.setData(ModDataAttachments.MIMIC_MEMORY, next);
        player.displayClientMessage(Component.translatable("mimicry.heroesevolved.absorbed", name(power)).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    private static Component name(ResourceLocation power) {
        return Component.translatable("ability." + power.getNamespace() + "." + power.getPath());
    }

    // ------------------------------------------------------------------
    // Core actions
    // ------------------------------------------------------------------

    /** Level 1, Empathize: absorb the power of the person (or Sylar) you are looking at. */
    public static boolean empathize(ServerPlayer player, int level) {
        boolean quick = SkillTreeService.has(player, "empathic_mimicry_quick_study");
        double reach = quick ? 16 : 8;
        LivingEntity target = Targeting.livingEntityInSight(player, reach, t -> t != player
                && (t instanceof ServerPlayer || t instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity));
        if (target == null && quick) target = player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(reach),
                other -> other != player && power(other) != null).stream().findFirst().orElse(null);
        if (target == null) {
            player.displayClientMessage(Component.translatable("mimicry.heroesevolved.no_one"), true);
            return false;
        }
        if (target instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity) {
            var learned = new ArrayList<>(com.jayptucker.heroesevolved.story.sylar.SylarStoryData.get(player.getServer()).learned());
            if (learned.isEmpty()) learned.add(ModAbilities.TELEKINESIS_ID);
            if (SkillTreeService.has(player, "empathic_mimicry_sylars_mirror")) learned.forEach(power -> remember(player, power, level));
            else remember(player, learned.get(player.getRandom().nextInt(learned.size())), level);
        } else {
            ResourceLocation power = power((ServerPlayer) target);
            if (power == null) {
                player.displayClientMessage(Component.translatable("mimicry.heroesevolved.nothing_there", target.getName()), true);
                return false;
            }
            remember(player, power, level);
        }
        AbilityEffects.send(player.serverLevel(), Kind.BLOOD_THREAD, target.getEyePosition(), player.getEyePosition(), 1);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
        return true;
    }

    private static ResourceLocation power(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked()).map(Map.Entry::getKey).orElse(null);
    }

    /** Level 1, Recall: choose which memory to channel. Shift steps backwards. */
    public static boolean recall(ServerPlayer player, int step) {
        Memory memory = memory(player);
        if (memory.powers().isEmpty()) {
            player.displayClientMessage(Component.translatable("mimicry.heroesevolved.empty"), true);
            return false;
        }
        Memory next = new Memory(memory.powers(), Math.floorMod(memory.active() + step, memory.powers().size()));
        memoryChanging(player, next.current());
        player.setData(ModDataAttachments.MIMIC_MEMORY, next);
        player.displayClientMessage(Component.translatable("mimicry.heroesevolved.channeling", name(next.current())).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

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
        Memory memory = memory(player);
        if (!memory.powers().isEmpty()) {
            Memory next = new Memory(memory.powers(), player.getRandom().nextInt(memory.powers().size()));
            memoryChanging(player, next.current());
            player.setData(ModDataAttachments.MIMIC_MEMORY, next);
        }
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120));
    }

    public static void awaken(ServerPlayer player) {
        ServerPlayer nearest = player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(24),
                other -> other != player && power(other) != null).stream().findFirst().orElse(null);
        if (nearest != null) remember(player, power(nearest), 1);
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
                        e -> e != p && (e instanceof ServerPlayer sp && power(sp) != null || e instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity))) {
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
        if (server.overworld().getGameTime() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isMimic(player)) continue;
            boolean near = !player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(8),
                    other -> other != player && power(other) != null).isEmpty();
            if (near) PlayerEnergyService.restore(player, 1);
            // The Empath's Calm capstone: the poisons of borrowed powers cannot take hold.
            if (SkillTreeService.has(player, "empathic_mimicry_empaths_calm")) { player.removeEffect(MobEffects.POISON); player.removeEffect(MobEffects.WITHER); }
        }
    }

    public static void forget(ServerPlayer player) {
        OVERCHARGED.remove(player.getUUID());
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
        OVERCHARGED.clear(); INVOKING.clear();
    }
}
