package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private static final Map<UUID, Boolean> LAST_OVERCHARGED = new HashMap<>();

    private MimicryService() {}

    public static boolean isMimic(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.MIMICRY_ID) && e.getValue().isUnlocked()).isPresent()
                && !EclipseService.arePowersSuppressed(player);
    }

    public static Memory memory(ServerPlayer player) { return player.getData(ModDataAttachments.MIMIC_MEMORY); }

    public static int slots(ServerPlayer player, int level) {
        int base = level >= 5 ? 4 : Math.min(3, Math.max(1, level));
        return base + (SkillTreeService.has(player, "mimicry_sylars_mirror") ? 1 : 0);
    }

    /** Remembers {@code power}; the oldest memory fades when there is no room. */
    public static boolean remember(ServerPlayer player, ResourceLocation power, int level) {
        if (power == null || power.equals(ModAbilities.MIMICRY_ID) || !AbilityRegistry.ABILITIES.containsKey(power)) return false;
        List<ResourceLocation> powers = new ArrayList<>(memory(player).powers());
        powers.remove(power);
        powers.add(power);
        while (powers.size() > slots(player, level)) powers.removeFirst();
        player.setData(ModDataAttachments.MIMIC_MEMORY, new Memory(List.copyOf(powers), powers.size() - 1));
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
        boolean quick = SkillTreeService.has(player, "mimicry_quick_study");
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
            if (SkillTreeService.has(player, "mimicry_sylars_mirror")) learned.forEach(power -> remember(player, power, level));
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
        player.setData(ModDataAttachments.MIMIC_MEMORY, next);
        player.displayClientMessage(Component.translatable("mimicry.heroesevolved.channeling", name(next.current())).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Echo I/II/III: the remembered power's primary, secondary and tertiary abilities. */
    public static AbilityAction echo(int index, AbilitySlot copied, int unlock) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "mimicry_echo_" + index);
        AbilityActionDefinition definition = new AbilityActionDefinition(id, "action.heroesevolved.mimicry_echo_" + index, unlock, 0, 0);
        return new AbilityAction() {
            private AbilityAction borrowed(AbilityUseContext c) {
                ResourceLocation power = memory(c.player()).current();
                Ability ability = power == null ? null : AbilityRegistry.ABILITIES.get(power);
                return ability == null ? null : ability.actions().get(copied);
            }
            private AbilityUseContext as(AbilityUseContext c, boolean full) {
                ResourceLocation power = memory(c.player()).current();
                return new AbilityUseContext(c.player(), power, full ? c.abilityLevel() : Math.max(1, c.abilityLevel() - 1), c.modifierHeld());
            }
            @Override public AbilityActionDefinition definition() { return definition; }
            @Override public boolean canUse(AbilityUseContext c) {
                AbilityAction action = borrowed(c);
                if (action == null) {
                    c.player().displayClientMessage(Component.translatable("mimicry.heroesevolved.empty"), true);
                    return false;
                }
                return action.canUse(as(c, false));
            }
            @Override public int energyCost(AbilityUseContext c) {
                AbilityAction action = borrowed(c);
                if (action == null) return 0;
                int cost = action.energyCost(as(c, false));
                return SkillTreeService.has(c.player(), "mimicry_peters_burden") ? (int) Math.ceil(cost * 0.75) : cost;
            }
            @Override public int cooldownTicks(AbilityUseContext c) {
                AbilityAction action = borrowed(c);
                if (action == null) return 20;
                int ticks = (int) Math.ceil(action.cooldownTicks(as(c, false)) * 1.5);
                boolean twin = LAST_OVERCHARGED.remove(c.player().getUUID()) != null && SkillTreeService.has(c.player(), "mimicry_twin_echo");
                return twin ? ticks / 2 : ticks;
            }
            @Override public AbilityActivationResult activate(AbilityUseContext c) {
                AbilityAction action = borrowed(c);
                if (action == null) return AbilityActivationResult.REJECTED;
                boolean full = OVERCHARGED.remove(c.player().getUUID());
                if (full) LAST_OVERCHARGED.put(c.player().getUUID(), true);
                return action.activate(as(c, full));
            }
            @Override public void release(AbilityUseContext c) {
                AbilityAction action = borrowed(c);
                if (action != null) action.release(as(c, false));
            }
            @Override public boolean deactivate(AbilityUseContext c) {
                AbilityAction action = borrowed(c);
                return action != null && action.deactivate(as(c, false));
            }
        };
    }

    /** Overexertion scrambles which memory answers. */
    public static void overload(ServerPlayer player) {
        Memory memory = memory(player);
        if (!memory.powers().isEmpty())
            player.setData(ModDataAttachments.MIMIC_MEMORY, new Memory(memory.powers(), player.getRandom().nextInt(memory.powers().size())));
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
                friend.heal(SkillTreeService.has(p, "mimicry_shared_healing") ? 8 : 4);
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
        if (server.overworld().getGameTime() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isMimic(player)) continue;
            boolean near = !player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(8),
                    other -> other != player && power(other) != null).isEmpty();
            if (near) PlayerEnergyService.restore(player, 1);
            // The Empath's Calm capstone: the poisons of borrowed powers cannot take hold.
            if (SkillTreeService.has(player, "mimicry_empaths_calm")) { player.removeEffect(MobEffects.POISON); player.removeEffect(MobEffects.WITHER); }
        }
    }

    public static void forget(ServerPlayer player) {
        OVERCHARGED.remove(player.getUUID());
        LAST_OVERCHARGED.remove(player.getUUID());
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { OVERCHARGED.clear(); LAST_OVERCHARGED.clear(); }
}
