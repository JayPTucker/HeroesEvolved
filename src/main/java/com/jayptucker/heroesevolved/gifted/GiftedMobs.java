package com.jayptucker.heroesevolved.gifted;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gifted mobs: any villager, illager or humanoid monster carrying a power and using it. Every loaded one is ticked here
 * ({@link GiftedMob}); its gift is saved on it ({@link Gift}).
 * <p>
 * Where they come from (each rolled once per mob, then remembered with {@link #ROLLED}):
 * <ul>
 *   <li>a villager's dormant gift ({@link GiftedVillagers}) waking: half turn good and guard their village, half turn
 *   evil and become renegades ({@link Renegades});</li>
 *   <li>a raid's wave leader made its champion, always in the last wave ({@link GiftedRaids});</li>
 *   <li>the leader of a pillager patrol crossing the land;</li>
 *   <li>now and then a zombie, skeleton, witch or piglin that spawns with one.</li>
 * </ul>
 * Each is graded by {@link Rarity}, named ({@link GiftNames}), tougher than its kind, and drops something worth having.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class GiftedMobs {
    /** Set once a mob has had its roll, gifted or not. */
    public static final String ROLLED = HeroesEvolved.MOD_ID + "_gifted_rolled";
    /** A raider summoned to an unbound mob's side: never gifted itself. */
    public static final String FOLLOWER = HeroesEvolved.MOD_ID + "_gifted_follower";
    /** Gifted mobs with a fresh gift, announced on the next tick (not while their chunk is loading). */
    private static final List<Mob> FRESH = new ArrayList<>();
    private static final Map<UUID, GiftedMob> ACTIVE = new HashMap<>();
    private static final ResourceLocation HEALTH = id("gifted_health"), ARMOR = id("gifted_armor"),
            KNOCKBACK = id("gifted_knockback"), SPEED = id("gifted_speed");

    private GiftedMobs() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    // ------------------------------------------------------------------
    // Asking about them
    // ------------------------------------------------------------------

    /** The loaded gifted mob, or null. */
    public static GiftedMob of(Entity entity) { return entity == null ? null : ACTIVE.get(entity.getUUID()); }

    public static boolean isGifted(Entity entity) { return of(entity) != null; }
    public static boolean isEvil(Entity entity) { GiftedMob g = of(entity); return g != null && g.evil(); }
    public static boolean isGood(Entity entity) { GiftedMob g = of(entity); return g != null && !g.evil(); }
    public static List<GiftedMob> all() { return List.copyOf(ACTIVE.values()); }

    /**
     * The power someone carries, as a gifted mob or a mimic sees it: a player's awakened power, a gifted mob's, or a
     * villager's dormant gift. Null for no one.
     */
    public static ResourceLocation powerOf(LivingEntity entity) {
        if (entity instanceof ServerPlayer player)
            return PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked()).map(Map.Entry::getKey).orElse(null);
        GiftedMob g = of(entity);
        if (g != null) return g.movePower();
        Gift gift = Gift.read(entity);
        if (gift != null) return gift.power();
        return GiftedVillagers.gift(entity);
    }

    /** Every power a mob may be given: all that can be rolled, Empathic Mimicry included. */
    public static List<ResourceLocation> powers() {
        return AbilityRegistry.ABILITIES.keySet().stream().filter(ModAbilities::rollable)
                .filter(p -> p.equals(ModAbilities.MIMICRY_ID) || !MobMoves.of(p).isEmpty()).sorted().toList();
    }

    public static ResourceLocation randomPower(RandomSource random) {
        List<ResourceLocation> powers = powers();
        return powers.get(random.nextInt(powers.size()));
    }

    // ------------------------------------------------------------------
    // Giving one
    // ------------------------------------------------------------------

    /**
     * Gives {@code mob} a power now: saved on it, its body toughened for its rarity, named, and set to using it.
     * {@code name} null rolls one.
     */
    public static GiftedMob bestow(Mob mob, ResourceLocation power, Rarity rarity, boolean evil, Gift.Origin origin, BlockPos home, String name) {
        Gift gift = new Gift(power, rarity, evil, name != null ? name : GiftNames.roll(power, mob.getRandom()), origin, home);
        gift.write(mob);
        mob.getPersistentData().putBoolean(ROLLED, true);
        empower(mob, gift);
        GiftedMob g = ACTIVE.get(mob.getUUID());
        if (g == null) {
            g = new GiftedMob(mob, gift);
            ACTIVE.put(mob.getUUID(), g);
        } else g.gift = gift;
        g.sync();
        return g;
    }

    /** Takes a gift away (admin). */
    public static void cleanse(Mob mob) {
        GiftedMob g = ACTIVE.remove(mob.getUUID());
        if (g != null) g.forget();
        Gift.clear(mob);
        for (ResourceLocation id : List.of(HEALTH, ARMOR, KNOCKBACK, SPEED)) {
            for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED)) {
                AttributeInstance a = mob.getAttribute(attribute);
                if (a != null) a.removeModifier(id);
            }
        }
        mob.setHealth(Math.min(mob.getHealth(), mob.getMaxHealth()));
    }

    /** A gifted mob's body: health, armor, footing and speed for its rarity, its name, and it stays in the world. */
    static void empower(Mob mob, Gift gift) {
        Rarity r = gift.rarity();
        modifier(mob, Attributes.MAX_HEALTH, HEALTH, r.bonusHealth, AttributeModifier.Operation.ADD_VALUE);
        modifier(mob, Attributes.ARMOR, ARMOR, r.armor, AttributeModifier.Operation.ADD_VALUE);
        modifier(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, r.knockback, AttributeModifier.Operation.ADD_VALUE);
        // A villager ambles; a guardian who could outrun its own village would leave it behind.
        if (!(mob instanceof AbstractVillager)) modifier(mob, Attributes.MOVEMENT_SPEED, SPEED, r.speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.literal(gift.name()).withStyle(r.color));
        // Hostile gifts wear their name like Apotheosis's bosses; a guardian's shows when you look at them.
        mob.setCustomNameVisible(gift.evil());
        mob.setPersistenceRequired();
        // Its weapon drops on death as the named, enchanted prize (see reward), not as an ordinary drop.
        mob.setDropChance(EquipmentSlot.MAINHAND, 0);
    }

    private static void modifier(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                 ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance a = mob.getAttribute(attribute);
        if (a == null || amount == 0) return;
        a.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
    }

    // ------------------------------------------------------------------
    // Rolling, as mobs appear
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        Gift gift = Gift.read(mob);
        if (gift != null) {
            ACTIVE.computeIfAbsent(mob.getUUID(), id -> new GiftedMob(mob, gift));
            return;
        }
        var data = mob.getPersistentData();
        if (data.getBoolean(ROLLED) || data.getBoolean(FOLLOWER)) return;
        data.putBoolean(ROLLED, true);
        if (mob instanceof AbstractVillager) return; // villagers roll a dormant gift instead (GiftedVillagers)
        RandomSource random = mob.getRandom();
        var config = HeroesEvolvedConfig.COMMON;
        if (mob instanceof Raider raider && raider.getCurrentRaid() != null) {
            if (GiftedRaids.champion(raider)) FRESH.add(mob);
            return;
        }
        if (mob instanceof PatrollingMonster leader && leader.isPatrolLeader() && mob.getSpawnType() == MobSpawnType.PATROL) {
            if (random.nextDouble() < config.giftedPatrolLeaderChance.get()) {
                bestow(mob, randomPower(random), Rarity.roll(random, Rarity.RARE), true, Gift.Origin.PATROL, null, null);
                FRESH.add(mob);
            }
            return;
        }
        if (humanoid(mob) && (mob.getSpawnType() == MobSpawnType.NATURAL || mob.getSpawnType() == MobSpawnType.PATROL)
                && random.nextDouble() < config.giftedWildChance.get()) {
            bestow(mob, randomPower(random), Rarity.roll(random, Rarity.UNCOMMON), true, Gift.Origin.WILD, null, null);
            FRESH.add(mob);
        }
    }

    /** Human-shaped monsters: the ones that can carry a power. */
    static boolean humanoid(Mob mob) {
        return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof Witch || mob instanceof AbstractPiglin
                || mob instanceof Raider;
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        GiftedMob g = ACTIVE.get(event.getEntity().getUUID());
        if (g != null && g.mob == event.getEntity()) {
            ACTIVE.remove(event.getEntity().getUUID());
            g.forget();
        }
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        GiftedMob g = of(event.getTarget());
        if (g != null && event.getEntity() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, g.payload());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Mob mob : List.copyOf(FRESH)) if (mob.isAlive() && !mob.isRemoved()) announce(mob);
        FRESH.clear();
        for (GiftedMob g : List.copyOf(ACTIVE.values())) {
            Mob mob = g.mob;
            if (mob.isRemoved() || !mob.isAlive() || mob.getServer() != server) continue;
            if (!(mob.level() instanceof ServerLevel level) || !level.isPositionEntityTicking(mob.blockPosition())) continue;
            try {
                g.tick();
            } catch (RuntimeException e) {
                HeroesEvolved.LOGGER.warn("Gifted mob {} failed to tick", g.gift.name(), e);
            }
        }
    }

    /**
     * A gifted mob just appeared: those nearby hear it ("has awoken nearby", Apotheosis's line), and a hostile one
     * glows for a minute so it can be found.
     */
    static void announce(Mob mob) {
        GiftedMob g = of(mob);
        if (g == null || !(mob.level() instanceof ServerLevel level)) return;
        Gift gift = g.gift;
        Vec3 at = mob.position();
        SetPiecePayload.send(level, Piece.GIFT_AWAKEN, at, at.add(0, 1, 0), MimicColors.index(gift.power().getPath()) + (gift.evil() ? .75F : .25F), mob);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.2F, .7F);
        if (gift.evil()) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60, 0, false, false));
        String what = switch (gift.origin()) {
            case PATROL -> " leads a patrol nearby.";
            case RAID -> " leads this wave of the raid!";
            case WILD -> " has awoken nearby!";
            default -> " has awoken.";
        };
        if (gift.origin() == Gift.Origin.WILD && !gift.rarity().boss() && gift.rarity() != Rarity.RARE) return;
        Component line = Component.literal(gift.name()).withStyle(gift.rarity().color, ChatFormatting.BOLD)
                .append(Component.literal(" (" + gift.rarity().title + " " + gift.powerName() + ")").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(what).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : g.near(140)) p.sendSystemMessage(line);
    }

    // ------------------------------------------------------------------
    // Fighting them
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        // A renegade between kills: its blows don't land on its village's people.
        if (source.getEntity() instanceof Mob attacker && victim instanceof AbstractVillager && !Renegades.mayHurt(attacker, victim)) {
            event.setCanceled(true);
            return;
        }
        GiftedMob g = of(victim);
        if (g == null) return;
        float amount = event.getAmount();
        if (g.spentNow()) amount *= 1.5F;
        else if (g.staggered()) amount *= 1.25F;
        // Only people can really hurt a mini-boss or a renegade (Cataclysm's rule): a golem, a wolf pack or a dripstone
        // trap chips at it, so the fight waits for whoever answers the call.
        // Lava pits and fall traps count as chipping too; /kill still kills.
        if ((g.gift.rarity().boss() || g.gift.origin() == Gift.Origin.RENEGADE) && g.evil() && !byPlayer(source)
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
            amount = Math.min(amount, 3);
        event.setAmount(amount);
    }

    /** A player's blow, a player's shot or a player's pet. */
    static boolean byPlayer(DamageSource source) {
        Entity e = source.getEntity();
        return e instanceof Player || e instanceof OwnableEntity pet && pet.getOwner() instanceof Player;
    }

    @SubscribeEvent
    public static void struck(LivingDamageEvent.Post event) {
        GiftedMob g = of(event.getEntity());
        if (g != null && event.getNewDamage() > 0) g.struck(event.getNewDamage(), event.getSource().getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void died(LivingDeathEvent event) {
        GiftedMob g = of(event.getEntity());
        if (g == null) return;
        Mob mob = g.mob;
        // Regeneration's second life: a mini-boss gets up once.
        if (g.gift.power().equals(ModAbilities.REGENERATION_ID) && g.gift.rarity().boss() && !Gift.spentSecondLife(mob)
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            Gift.spendSecondLife(mob);
            mob.setHealth(mob.getMaxHealth() / 2);
            mob.removeAllEffects();
            AbilityEffects.send((ServerLevel) mob.level(), Kind.REGEN_SECOND_LIFE, mob.position(), mob.getEyePosition(), 1.6);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1, .8F);
            for (ServerPlayer p : g.near(32))
                p.displayClientMessage(Component.literal(g.gift.name() + " gets back up!").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        ACTIVE.remove(mob.getUUID());
        g.forget();
        if (g.grownUntil > 0) MobMoves.shrink(mob);
        reward(g, event.getSource());
        Renegades.died(mob, event.getSource());
    }

    /**
     * The prize, the way Apotheosis's bosses give one: experience and emeralds by rarity, and its own weapon, named for
     * it and enchanted. The killer gains Mastery. Guardians are mourned, not looted.
     */
    static void reward(GiftedMob g, DamageSource source) {
        if (!g.evil() || !(g.mob.level() instanceof ServerLevel level)) return;
        Mob mob = g.mob;
        Rarity r = g.gift.rarity();
        ExperienceOrb.award(level, mob.position(), r.experience);
        mob.spawnAtLocation(new ItemStack(Items.EMERALD, 2 + r.ordinal() * 3 + mob.getRandom().nextInt(3)));
        ItemStack weapon = mob.getMainHandItem().copy();
        if (!weapon.isEmpty()) {
            weapon = net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(mob.getRandom(), weapon, 10 + r.ordinal() * 8,
                    level.registryAccess(), java.util.Optional.empty());
            String owner = g.gift.name().split(" ")[0];
            weapon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(owner + "'s " + weapon.getHoverName().getString())
                    .withStyle(r.color).withStyle(s -> s.withItalic(false)));
            mob.spawnAtLocation(weapon);
        }
        if (source.getEntity() instanceof ServerPlayer killer) {
            com.jayptucker.heroesevolved.progression.MasteryService.awardRescue(killer, 10L << r.ordinal());
            killer.sendSystemMessage(Component.literal("You defeated ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(g.gift.name()).withStyle(r.color))
                    .append(Component.literal(" (" + r.title + " " + g.gift.powerName() + ").").withStyle(ChatFormatting.GRAY)));
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { clear(); }

    public static void clear() {
        for (GiftedMob g : ACTIVE.values()) if (g.bar != null) g.bar.removeAllPlayers();
        ACTIVE.clear();
        FRESH.clear();
    }
}
