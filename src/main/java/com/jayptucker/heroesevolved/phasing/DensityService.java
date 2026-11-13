package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.DensityPayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
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
 * Density, after D.L. Hawkins: a body whose density is a dial.
 * <ul>
 *   <li><b>Ghost</b> (the left of the dial): walls you walk into let you through, shots and blows pass
 *   through you, and at Full Ghost your own blows don't land either. You fall like a leaf.</li>
 *   <li><b>Dense</b> (the right): heavy as stone. No knockback, harder punches that crack blocks, armour of
 *   rock, and a fall that ends in a shockwave instead of a broken leg.</li>
 *   <li><b>Breath</b>: while your body is inside solid ground it drains; run out and you are thrown out, hurt.</li>
 * </ul>
 * Every passage ends in a checked, open landing (see {@link Passage}). Bedrock, barriers and protected
 * ground stop you. You cannot suffocate in a wall.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class DensityService {
    /** The dial's notches: Full Ghost, Ghost, Neutral, Dense, Full Dense. */
    public static final int FULL_GHOST = -2, GHOST = -1, NEUTRAL = 0, DENSE = 1, FULL_DENSE = 2;
    public static final int BREATH = 100;
    /** The attribute modifiers the dial applies, and the marker that tells both sides a body is under the ground. */
    private static final ResourceLocation DIAL = id("density_dial");
    public static final ResourceLocation SUNK = id("density_sunk");
    private static final ResourceLocation SUNK_SLOW = id("density_sunk_slow");
    private static final ResourceLocation PINNED = id("density_pinned");

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    private static final class State {
        final ServerPlayer player;
        int notch, applied = Integer.MIN_VALUE, sentDensity = Integer.MIN_VALUE, sentBreath = Integer.MIN_VALUE;
        long freeUntil, passReady, lastMoved;
        Vec3 last;
        boolean sunk;
        float breath = BREATH;
        Vec3 sunkSafe;
        double solidBank;
        State(ServerPlayer player) { this.player = player; last = player.position(); }
    }

    /** An enemy phased halfway into the ground and made solid again. */
    private static final class Embedded {
        final LivingEntity target;
        final Entity owner;
        final Vec3 at;
        final double depth;
        long until;
        final boolean entombed;
        /** Ticks Set in Stone has added to the hold. */
        int extended;
        Embedded(LivingEntity target, Entity owner, Vec3 at, double depth, long until, boolean entombed) {
            this.target = target; this.owner = owner; this.at = at; this.depth = depth; this.until = until; this.entombed = entombed;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Embedded> EMBEDDED = new HashMap<>();
    /** A body a hand has passed through: it cannot heal until this tick. */
    private static final Map<UUID, Long> SEALED = new HashMap<>();

    private DensityService() {}

    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static long now(Entity e) { return now(e.getServer()); }

    public static boolean isOwner(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.PHASING_ID) && e.getValue().isUnlocked()).isPresent()
                && !EclipseService.arePowersSuppressed(player);
    }

    private static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State(p)); }

    /** The dial's notch for a body: -2 Full Ghost to +2 Full Dense. Anything without the power is neutral. */
    public static int notch(Entity e) { State s = STATES.get(e.getUUID()); return s == null ? NEUTRAL : s.notch; }
    public static boolean ghost(Entity e) { return notch(e) < 0; }
    public static boolean fullGhost(Entity e) { return notch(e) == FULL_GHOST; }
    public static boolean dense(Entity e) { return notch(e) > 0; }
    /** Kept for older callers: Full Ghost is the old intangibility. */
    public static boolean isIntangible(Entity e) { return fullGhost(e); }

    /** True on both sides while a body travels under the ground (a synced, zero-strength attribute marker). */
    public static boolean sunk(Player player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(SUNK);
    }

    public static boolean embedded(Entity e) { return EMBEDDED.containsKey(e.getUUID()); }
    /** Where the ground's surface is round a body held in it (its feet if it isn't held). */
    public static Vec3 surfaceOf(Entity e) {
        Embedded held = EMBEDDED.get(e.getUUID());
        return held == null ? e.position() : held.at.add(0, held.depth, 0);
    }

    /** Whoever put a body in the ground, or null. */
    public static Entity holder(Entity e) { Embedded held = EMBEDDED.get(e.getUUID()); return held == null ? null : held.owner; }
    /** True while the ground has closed right over a body (Entombed). */
    public static boolean buried(Entity e) { Embedded held = EMBEDDED.get(e.getUUID()); return held != null && held.depth >= e.getBbHeight(); }

    /** Holds an embedded body {@code ticks} longer, never past {@code max} added in all (Set in Stone). */
    public static boolean extend(Entity e, int ticks, int max) {
        Embedded held = EMBEDDED.get(e.getUUID());
        if (held == null || held.extended >= max) return false;
        int add = Math.min(ticks, max - held.extended);
        held.extended += add; held.until += add;
        return true;
    }

    /** Lets a body up out of the ground at once. */
    public static void release(Entity e) { Embedded held = EMBEDDED.get(e.getUUID()); if (held != null) free(held); }

    /**
     * Entombed: a body already held in the ground sinks right under it for {@code ticks}, taking damage every second.
     * False (and it stays as it was) if there is no room under it.
     */
    public static boolean entomb(LivingEntity target, Entity owner, int ticks) {
        Embedded held = EMBEDDED.remove(target.getUUID());
        if (held == null) return false;
        hold(target, held.at.add(0, held.depth, 0));
        if (embed(target, owner, ticks, true, 1.05, 2.3)) return true;
        EMBEDDED.put(target.getUUID(), held);
        hold(target, held.at);
        return false;
    }
    public static float breath(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s == null ? BREATH : s.breath; }

    private static int tier(ServerPlayer p) { return Math.max(1, PlayerProgressionService.getLevel(p)); }

    // ------------------------------------------------------------------
    // The dial
    // ------------------------------------------------------------------

    /** Level 1, Shift: one notch toward Ghost, or with Shift held one notch toward Dense. */
    public static boolean shift(ServerPlayer p, boolean towardDense) {
        State s = state(p);
        if (s.sunk) return false;
        int next = Math.clamp(s.notch + (towardDense ? 1 : -1), FULL_GHOST, FULL_DENSE);
        if (next == s.notch) return false;
        set(p, s, next);
        return true;
    }

    /** Snaps to {@code notch}, upkeep-free for {@code freeTicks}. */
    public static void setNotch(ServerPlayer p, int notch, int freeTicks) {
        State s = state(p);
        if (s.sunk) return;
        set(p, s, Math.clamp(notch, FULL_GHOST, FULL_DENSE));
        if (freeTicks > 0) s.freeUntil = Math.max(s.freeUntil, now(p) + freeTicks);
    }

    private static void set(ServerPlayer p, State s, int notch) {
        int before = s.notch;
        s.notch = notch;
        apply(p, s);
        ServerLevel level = p.serverLevel();
        Vec3 feet = p.position();
        AbilityEffects.send(level, Kind.DENSITY_SHIFT, feet, feet.add(0, notch < 0 ? -1 : notch > 0 ? 1 : 0.01, 0), 1 + Math.abs(notch) * 0.5);
        if (notch < before) level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F + (2 + notch) * 0.2F);
        else level.playSound(null, p.blockPosition(), SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 1, 0.5F + (2 - notch) * 0.15F);
        p.displayClientMessage(Component.translatable("density.heroesevolved.notch." + (notch + 2)).withStyle(notch < 0 ? ChatFormatting.AQUA
                : notch > 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE), true);
        sync(p, s);
    }

    private static void modifier(LivingEntity e, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = e.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /** What each notch does to the body, as native, synced attributes so the client moves the same way. */
    private static void apply(ServerPlayer p, State s) {
        if (s.applied == s.notch) return;
        s.applied = s.notch;
        var plus = AttributeModifier.Operation.ADD_VALUE;
        var times = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        int n = s.notch;
        modifier(p, Attributes.GRAVITY, DIAL, n == FULL_GHOST ? -0.55 : n == GHOST ? -0.25 : n == FULL_DENSE ? 0.35 : 0, times);
        modifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, DIAL, n == FULL_GHOST ? -1 : n == GHOST ? -0.5 : n == FULL_DENSE ? -1 : 0, times);
        modifier(p, Attributes.ATTACK_DAMAGE, DIAL, n == GHOST ? -0.5 : 0, times);
        modifier(p, Attributes.ATTACK_DAMAGE, id("density_fist"), n == DENSE ? 3 : n == FULL_DENSE ? 6 : 0, plus);
        modifier(p, Attributes.ARMOR, DIAL, n == DENSE ? 4 : n == FULL_DENSE ? 8 : 0, plus);
        modifier(p, Attributes.ARMOR_TOUGHNESS, DIAL, n == FULL_DENSE ? 4 : 0, plus);
        modifier(p, Attributes.KNOCKBACK_RESISTANCE, DIAL, n == DENSE ? 0.6 : n == FULL_DENSE ? 1 : 0, plus);
        modifier(p, Attributes.MOVEMENT_SPEED, DIAL, n == DENSE ? -0.1 : n == FULL_DENSE ? -0.25 : 0, times);
        modifier(p, Attributes.BLOCK_BREAK_SPEED, DIAL, n == DENSE ? 1 : n == FULL_DENSE ? 2 : 0, times);
        modifier(p, Attributes.JUMP_STRENGTH, DIAL, n == FULL_DENSE ? -0.2 : 0, times);
    }

    private static void clearModifiers(ServerPlayer p) {
        for (var attribute : List.of(Attributes.GRAVITY, Attributes.FALL_DAMAGE_MULTIPLIER, Attributes.ATTACK_DAMAGE, Attributes.ARMOR,
                Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED, Attributes.BLOCK_BREAK_SPEED,
                Attributes.JUMP_STRENGTH)) {
            AttributeInstance instance = p.getAttribute(attribute);
            if (instance != null) { instance.removeModifier(DIAL); instance.removeModifier(SUNK); instance.removeModifier(SUNK_SLOW); }
        }
        AttributeInstance damage = p.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.removeModifier(id("density_fist"));
    }

    private static void sync(ServerPlayer p, State s) {
        int density = s.notch * 50, breath = s.sunk ? Math.round(s.breath) : -1;
        if (density == s.sentDensity && breath == s.sentBreath) return;
        s.sentDensity = density; s.sentBreath = breath;
        DensityPayload.send(p, density, breath);
    }

    // ------------------------------------------------------------------
    // Walking through walls
    // ------------------------------------------------------------------

    /** The thickest wall the body can walk through at its notch. */
    public static double wallLimit(ServerPlayer p, int notch) {
        if (notch >= NEUTRAL) return 0;
        return notch == GHOST ? 1 : 2 + tier(p) / 2;
    }

    /** Ghost: walk into a wall and you come out the other side. Crouch to lean on it instead. */
    private static void tickPassage(ServerPlayer p, State s, long now) {
        if (p.position().subtract(s.last).horizontalDistanceSqr() > 0.0025) s.lastMoved = now;
        s.last = p.position();
        if (s.notch >= NEUTRAL || p.isShiftKeyDown() || p.isPassenger() || now - s.lastMoved > 4 || now < s.passReady) return;
        Vec3 heading = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (heading.lengthSqr() < 1.0E-4) return;
        heading = heading.normalize();
        if (!SpeedService.wallAhead(p, heading)) return;
        s.passReady = now + 10;
        Vec3 from = p.position(), to = Passage.through(p, heading, wallLimit(p, s.notch) + 1.5);
        if (to == null) return;
        pass(p, from, to);
    }

    /** Moves a phaser to {@code to}, rippling both faces of what it passed. Whatever you carry comes too. */
    public static void pass(ServerPlayer p, Vec3 from, Vec3 to) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = to.subtract(from);
        Vec3 normal = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : dir.normalize();
        double solid = Passage.solidBetween(p, from, to);
        p.teleportTo(level, to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        AbilityEffects.send(level, Kind.DENSITY_RIPPLE, from.add(normal.scale(0.5)).add(0, 1, 0), from.add(normal.scale(1.5)).add(0, 1, 0), 1.1);
        AbilityEffects.send(level, Kind.DENSITY_RIPPLE, to.subtract(normal.scale(0.5)).add(0, 1, 0), to.add(normal.scale(0.5)).add(0, 1, 0), 1.1);
        level.playSound(null, BlockPos.containing(from), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 0.5F);
        level.playSound(null, p.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.4F, 1.5F);
        bankSolid(p, solid);
        DensitySkills.passed(p, from, to);
    }

    /** Distance through solid matter counts toward the Evolved trial. */
    private static void bankSolid(ServerPlayer p, double blocks) {
        State s = state(p);
        s.solidBank += blocks;
        if (s.solidBank >= 1) {
            int whole = (int) s.solidBank;
            s.solidBank -= whole;
            EvolutionTrials.advance(p, ModAbilities.PHASING_ID, whole);
        }
    }

    /** Friends within {@code radius} come along, each needing its own open landing beside you. */
    static List<LivingEntity> carryFriends(ServerPlayer player, Vec3 offset, double radius, int limit) {
        List<LivingEntity> carried = new ArrayList<>();
        for (LivingEntity friend : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                other -> other != player && ExpansionSkills.friendly(player, other) && !other.isPassenger() && !EntityCarryService.isBeingCarried(other))) {
            if (carried.size() >= limit) break;
            Vec3 to = friend.position().add(offset);
            if (!SpeedService.safe(friend, to)) continue;
            friend.teleportTo(to.x, to.y, to.z);
            friend.resetFallDistance();
            carried.add(friend);
        }
        return carried;
    }

    // ------------------------------------------------------------------
    // Reach Through
    // ------------------------------------------------------------------

    /** Level 1, Reach Through: your arm sinks into a body, or through a wall into whatever holds items behind it. */
    public static boolean reach(ServerPlayer p, int tier) {
        if (sinking(p) && SkillTreeService.has(p, DensitySkills.GRASP)) return DensitySkills.grasp(p, tier);
        LivingEntity target = Targeting.livingEntityInSight(p, 4 + tier * 0.5, t -> ExpansionSkills.hostile(p, t));
        ArmPosePayload.show(p, ArmPose.REACH, 12);
        if (target != null) { reachInside(p, target, tier, 0); return true; }
        if (DensitySkills.throughWall(p, tier)) return true;
        if (reachContainer(p)) return true;
        p.displayClientMessage(Component.translatable("density.heroesevolved.nothing_to_reach"), true);
        return false;
    }

    /** D.L.'s hand inside a body: a blow armour cannot stop, and for a few seconds that body cannot heal. */
    public static void reachInside(ServerPlayer p, LivingEntity target, int tier) { reachInside(p, target, tier, 0); }

    /** Reach Through with {@code bonus} more damage (a hand through a wall, or an Ambush). */
    public static void reachInside(ServerPlayer p, LivingEntity target, int tier, float bonus) {
        ServerLevel level = p.serverLevel();
        target.invulnerableTime = 0;
        float damage = 5 + tier + (dense(p) ? 2 : 0) + bonus;
        target.hurt(p.damageSources().indirectMagic(p, p), damage);
        DensitySkills.touched(p, target, 100);
        ItemStack taken = pullGear(target);
        if (!taken.isEmpty()) {
            ItemEntity drop = new ItemEntity(level, target.getX(), target.getY() + 0.3, target.getZ(), taken);
            drop.setPickUpDelay(40);
            level.addFreshEntity(drop);
        }
        Vec3 hand = hand(p), at = target.getBoundingBox().getCenter();
        AbilityEffects.send(level, Kind.DENSITY_REACH, hand, at, 1);
        level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1, 1.3F);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.4F);
    }

    /** What a hand inside armour pulls loose: the off-hand first, then a piece of armour. */
    private static ItemStack pullGear(LivingEntity target) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.OFFHAND, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = target.getItemBySlot(slot);
            if (worn.isEmpty()) continue;
            target.setItemSlot(slot, ItemStack.EMPTY);
            if (target instanceof Mob mob) mob.setDropChance(slot, 0);
            return worn;
        }
        return ItemStack.EMPTY;
    }

    /** No healing for {@code ticks}: not regeneration, not food, not a healer's power. */
    public static void seal(LivingEntity target, int ticks) {
        if (target.getServer() == null) return;
        SEALED.put(target.getUUID(), now(target) + ticks);
        target.removeEffect(MobEffects.REGENERATION);
    }

    public static boolean sealed(Entity e) { return e.getServer() != null && SEALED.getOrDefault(e.getUUID(), 0L) > now(e); }

    /** Through up to two blocks of wall into a chest, barrel or furnace: the first stack in it comes out in your hand. */
    static boolean reachContainer(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 surface = null;
        int solid = 0;
        BlockPos last = null;
        for (double d = 0.2; d <= 5.5; d += 0.2) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
            if (pos.equals(last)) continue;
            last = pos;
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (!Passage.passable(p, pos)) return false;
            if (surface == null) surface = eye.add(look.scale(d));
            if (level.getBlockEntity(pos) instanceof Container container) {
                if (container instanceof BaseContainerBlockEntity locked && !locked.canOpen(p)) return false;
                if (container instanceof RandomizableContainerBlockEntity loot) loot.unpackLootTable(p);
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (stack.isEmpty()) continue;
                    ItemStack taken = container.removeItemNoUpdate(slot);
                    container.setChanged();
                    if (!p.getInventory().add(taken)) p.drop(taken, false);
                    AbilityEffects.send(level, Kind.DENSITY_RIPPLE, surface, surface.subtract(look), 0.8);
                    AbilityEffects.send(level, Kind.DENSITY_REACH, hand(p), Vec3.atCenterOf(pos), 0.8);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.6F);
                    level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 0.5F);
                    return true;
                }
                p.displayClientMessage(Component.translatable("density.heroesevolved.empty"), true);
                return false;
            }
            if (++solid > 2 + 1) return false;
        }
        return false;
    }

    static Vec3 hand(Entity p) {
        Vec3 flat = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
        return p.getEyePosition().add(flat.scale(0.5)).subtract(0, 0.45, 0);
    }

    // ------------------------------------------------------------------
    // Embed
    // ------------------------------------------------------------------

    /** Level 2, Embed: phase the enemy you look at halfway into the floor, then make them solid again. */
    public static boolean embed(ServerPlayer p, int tier, double range) {
        LivingEntity target = Targeting.livingEntityInSight(p, range, t -> ExpansionSkills.hostile(p, t) && !embedded(t));
        if (target == null) return false;
        ArmPosePayload.show(p, ArmPose.GROUND, 10);
        return embed(target, p, 60 + 20 * tier, false, 0.45);
    }

    /**
     * Sinks {@code target} into the ground beneath it by {@code fraction} of its height and holds it
     * there. It can still fight back, and it can struggle free. False if there is no ground to sink into.
     */
    public static boolean embed(LivingEntity target, Entity owner, int ticks, boolean entombed, double fraction) {
        return embed(target, owner, ticks, entombed, fraction, 1.3);
    }

    /** As above, sinking no deeper than {@code maxDepth} blocks (Entombed buries a body right under). */
    public static boolean embed(LivingEntity target, Entity owner, int ticks, boolean entombed, double fraction, double maxDepth) {
        if (!(target.level() instanceof ServerLevel level) || embedded(target) || target.getType().is(Tags.EntityTypes.BOSSES)
                || target.isPassenger() || target.getBbHeight() > 4) return false;
        double depth = Math.min(maxDepth, target.getBbHeight() * fraction);
        Vec3 at = target.position().subtract(0, depth, 0);
        BlockPos below = BlockPos.containing(target.position().subtract(0, 0.2, 0));
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
        for (double y = 0.1; y < depth + 0.1; y += 0.5) {
            BlockPos pos = BlockPos.containing(at.add(0, y - 0.1, 0));
            var state = level.getBlockState(pos);
            if (state.getDestroySpeed(level, pos) < 0) return false;
            if (owner instanceof ServerPlayer p && !Passage.passable(p, pos)) return false;
        }
        EMBEDDED.put(target.getUUID(), new Embedded(target, owner, at, depth, now(target) + ticks, entombed));
        if (target instanceof ServerPlayer pinned) {
            modifier(pinned, Attributes.MOVEMENT_SPEED, PINNED, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            modifier(pinned, Attributes.JUMP_STRENGTH, PINNED, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            pinned.displayClientMessage(Component.translatable("density.heroesevolved.embedded").withStyle(ChatFormatting.RED), true);
        }
        hold(target, at);
        AbilityEffects.send(level, Kind.DENSITY_RIPPLE, target.position().add(0, depth + 0.05, 0), target.position().add(0, depth + 1, 0), target.getBbWidth() + 0.6);
        AbilityEffects.send(level, Kind.DENSITY_EMBED, target.position().add(0, depth, 0), target.position().add(0, depth + 1, 0), target.getBbWidth());
        level.playSound(null, target.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1, 0.5F);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 0.5F);
        return true;
    }

    private static void hold(LivingEntity target, Vec3 at) {
        if (target instanceof ServerPlayer player) {
            if (player.position().distanceToSqr(at) > 0.09) player.teleportTo(player.serverLevel(), at.x, at.y, at.z, player.getYRot(), player.getXRot());
        } else target.teleportTo(at.x, at.y, at.z);
        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
    }

    private static void tickEmbedded(long now) {
        for (Embedded e : List.copyOf(EMBEDDED.values())) {
            LivingEntity t = e.target;
            if (!t.isAlive() || t.isRemoved() || now >= e.until || t.isPassenger()) { free(e); continue; }
            // Struggling: a player fighting the ground (or any mob, now and then) works loose sooner.
            boolean set = e.owner instanceof ServerPlayer holder && SkillTreeService.has(holder, DensitySkills.SET_IN_STONE);
            if (set) { /* Set in Stone: there is no working loose. */ }
            else if (t instanceof ServerPlayer player && player.position().distanceToSqr(e.at) > 0.09) e.until -= 3;
            else if (!(t instanceof ServerPlayer) && now % 20 == 0 && t.getRandom().nextInt(4) == 0) e.until -= 10;
            hold(t, e.at);
            if (e.entombed && now % 20 == 0 && t.level() instanceof ServerLevel)
                t.hurt(e.owner instanceof LivingEntity owner ? t.damageSources().indirectMagic(owner, owner) : t.damageSources().magic(), 2);
            if (now % 10 == 0 && t.level() instanceof ServerLevel level && e.depth < t.getBbHeight()) {
                Vec3 ground = e.at.add(0, e.depth, 0);
                BlockPos below = BlockPos.containing(ground.subtract(0, 0.5, 0));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), ground.x, ground.y + 0.1, ground.z,
                        6, t.getBbWidth() * 0.4, 0.05, t.getBbWidth() * 0.4, 0.05);
                AbilityEffects.send(level, Kind.DENSITY_EMBED, ground, ground.add(0, 1, 0), t.getBbWidth());
            }
        }
    }

    /** Pops an embedded body back up out of the ground. */
    private static void free(Embedded e) {
        EMBEDDED.remove(e.target.getUUID());
        LivingEntity t = e.target;
        if (t instanceof ServerPlayer player) {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED), jump = player.getAttribute(Attributes.JUMP_STRENGTH);
            if (speed != null) speed.removeModifier(PINNED);
            if (jump != null) jump.removeModifier(PINNED);
        }
        if (!t.isAlive() || t.isRemoved() || !(t.level() instanceof ServerLevel level)) return;
        Vec3 up = e.at.add(0, e.depth + 0.05, 0);
        if (!SpeedService.safe(t, up)) up = openNear(t, up);
        if (up != null) {
            if (t instanceof ServerPlayer player) player.teleportTo(level, up.x, up.y, up.z, player.getYRot(), player.getXRot());
            else t.teleportTo(up.x, up.y, up.z);
        }
        t.setDeltaMovement(0, e.depth >= t.getBbHeight() ? 0.6 : 0.25, 0); t.hurtMarked = true;
        if (t instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        AbilityEffects.send(level, Kind.DENSITY_RIPPLE, e.at.add(0, e.depth + 0.05, 0), e.at.add(0, e.depth + 1, 0), t.getBbWidth() + 0.4);
    }

    /** The nearest open space for a body, searching up and around. */
    private static Vec3 openNear(Entity body, Vec3 from) {
        for (int r = 0; r <= 4; r++)
            for (int dy = 0; dy <= r + 1; dy++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        Vec3 point = new Vec3(Math.floor(from.x) + 0.5 + dx, Math.floor(from.y) + dy, Math.floor(from.z) + 0.5 + dz);
                        if (SpeedService.safe(body, point)) return point;
                    }
        return null;
    }

    // ------------------------------------------------------------------
    // Sink and Surface
    // ------------------------------------------------------------------

    /** Level 3, Sink: drop into the ground and move through it. Press again to surface, under an enemy if you can. */
    public static boolean sinkOrSurface(ServerPlayer p, int tier) {
        State s = state(p);
        return s.sunk ? surface(p, true) : sink(p, tier);
    }

    public static boolean sinking(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s != null && s.sunk; }

    static boolean sink(ServerPlayer p, int tier) {
        State s = state(p);
        if (s.sunk || p.isPassenger() || EntityCarryService.isCarrying(p)) return false;
        if (!p.onGround()) { p.displayClientMessage(Component.translatable("density.heroesevolved.need_ground"), true); return false; }
        Vec3 to = p.position().subtract(0, 2.4, 0);
        for (double y = -2.4; y < 0; y += 0.5)
            for (BlockPos pos : column(p, p.position().add(0, y, 0)))
                if (!Passage.passable(p, pos)) { p.displayClientMessage(Component.translatable("phasing.heroesevolved.no_passage"), true); return false; }
        if (SpeedService.safe(p, to)) { p.displayClientMessage(Component.translatable("density.heroesevolved.need_ground"), true); return false; }
        Vec3 from = p.position();
        s.sunk = true;
        s.breath = BREATH;
        s.sunkSafe = from;
        // Neutral underground: the dial's own modifiers would fight the zero gravity that keeps you level.
        s.notch = NEUTRAL; s.applied = Integer.MIN_VALUE; apply(p, s);
        modifier(p, Attributes.GRAVITY, SUNK, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(p, Attributes.MOVEMENT_SPEED, SUNK_SLOW, -0.3 + tier * 0.04, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        p.teleportTo(p.serverLevel(), to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO); p.hurtMarked = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        ArmPosePayload.show(p, ArmPose.SINK, 14);
        AbilityEffects.send(p.serverLevel(), Kind.DENSITY_RIPPLE, from.add(0, 0.05, 0), from.add(0, 1, 0), 1.6);
        AbilityEffects.send(p.serverLevel(), Kind.DENSITY_SURFACE, from, from.subtract(0, 1, 0), 1);
        p.serverLevel().playSound(null, BlockPos.containing(from), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, 0.4F);
        p.serverLevel().playSound(null, BlockPos.containing(from), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1, 0.5F);
        sync(p, s);
        return true;
    }

    private static List<BlockPos> column(Entity body, Vec3 feet) {
        var box = body.getBoundingBox().move(feet.subtract(body.position())).deflate(0.01);
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                .forEach(pos -> blocks.add(pos.immutable()));
        return blocks;
    }

    /** Up through the ground to the open space above. Chosen (not forced), an enemy right above takes an uppercut. */
    public static boolean surface(ServerPlayer p, boolean chosen) {
        State s = STATES.get(p.getUUID());
        if (s == null || !s.sunk) return false;
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position(), to = null;
        for (int up = 1; up <= 8 && to == null; up++) {
            Vec3 point = new Vec3(from.x, Math.floor(from.y) + up, from.z);
            if (SpeedService.safe(p, point)) to = point;
        }
        if (to == null) to = openNear(p, from.add(0, 1, 0));
        if (to == null) to = s.sunkSafe;
        endSink(p, s);
        p.teleportTo(level, to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        AbilityEffects.send(level, Kind.DENSITY_SURFACE, to, to.add(0, 1, 0), chosen ? 1.4 : 0.8);
        AbilityEffects.send(level, Kind.DENSITY_RIPPLE, to.add(0, 0.05, 0), to.add(0, 1, 0), 1.6);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, 0.8F);
        if (chosen) {
            final Vec3 landing = to;
            int tier = tier(p);
            boolean uproot = SkillTreeService.has(p, DensitySkills.UPROOT);
            for (LivingEntity above : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(2.5, 2.5, 2.5),
                    t -> ExpansionSkills.hostile(p, t) && t.position().distanceToSqr(landing) < 2.5 * 2.5)) {
                // Uproot: a body you hold in the ground is torn out of it, twice as hard and twice as high.
                boolean torn = uproot && embedded(above);
                if (torn) release(above);
                above.invulnerableTime = 0;
                above.hurt(p.damageSources().playerAttack(p), (6 + tier) * (torn ? 2 : 1));
                above.setDeltaMovement(above.getDeltaMovement().add(0, torn ? 2.0 : 1.0, 0)); above.hurtMarked = true;
                if (uproot) DensitySkills.uprooted(p, above, torn);
                if (above instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
                AbilityEffects.send(level, Kind.DENSITY_REACH, landing.add(0, 0.5, 0), above.getBoundingBox().getCenter(), 1.2);
                level.playSound(null, above.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1, 0.6F);
            }
            p.setDeltaMovement(0, 0.5, 0); p.hurtMarked = true;
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
        return true;
    }

    private static void endSink(ServerPlayer p, State s) {
        s.sunk = false;
        for (var attribute : List.of(Attributes.GRAVITY, Attributes.MOVEMENT_SPEED)) {
            AttributeInstance instance = p.getAttribute(attribute);
            if (instance != null) { instance.removeModifier(SUNK); instance.removeModifier(SUNK_SLOW); }
        }
        sync(p, s);
    }

    private static void tickSunk(ServerPlayer p, State s, long now) {
        ServerLevel level = p.serverLevel();
        // Out into open air (a cave, a cliff face): you simply come out.
        if (SpeedService.safe(p, p.position())) { endSink(p, s); p.resetFallDistance(); return; }
        // Bedrock, barriers and protected ground stay solid, even to you.
        for (BlockPos pos : column(p, p.position()))
            if (!Passage.passable(p, pos)) {
                Vec3 back = s.sunkSafe;
                p.teleportTo(level, back.x, back.y, back.z, p.getYRot(), p.getXRot());
                break;
            }
        double moved = p.position().distanceTo(s.last);
        if (moved < 4) bankSolid(p, moved);
        if (!SpeedService.safe(p, p.position())) s.sunkSafe = p.position();
        int tier = tier(p);
        s.breath -= 0.5F / (1 + (tier - 1) * 0.15F);
        if (s.breath <= 0) {
            surface(p, false);
            p.hurt(p.damageSources().generic(), 4);
            p.displayClientMessage(Component.translatable("density.heroesevolved.out_of_breath").withStyle(ChatFormatting.RED), true);
            return;
        }
        // Tremor sense: the footsteps of everything walking on the ground above, felt through the rock.
        if (now % 10 == 0)
            for (LivingEntity walker : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(14, 8, 14),
                    t -> t != p && t.onGround() && t.getY() > p.getEyeY() - 0.5))
                PacketDistributor.sendToPlayer(p, new AbilityEffectPayload(Kind.DENSITY_FOOTSTEP, walker.position(),
                        walker.position().add(0, 1, 0), ExpansionSkills.hostile(p, walker) ? 1.2F : 0.8F));
    }

    // ------------------------------------------------------------------
    // Hollow Ground and Bedrock Slam
    // ------------------------------------------------------------------

    /** Level 5: at Dense, slam down and harden; otherwise hollow the ground and bury every enemy around you. */
    public static boolean ultimate(ServerPlayer p, int tier) {
        return dense(p) ? slam(p, tier) : hollow(p, tier);
    }

    static boolean hollow(ServerPlayer p, int tier) {
        ServerLevel level = p.serverLevel();
        double radius = 6 + tier;
        ArmPosePayload.show(p, ArmPose.GROUND, 20);
        AbilityEffects.send(level, Kind.DENSITY_HOLLOW, p.position(), p.position().add(0, 1, 0), radius);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2, 0.3F);
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1, 0.8F);
        int buried = 0;
        for (LivingEntity t : ExpansionSkills.enemies(p, p.position(), radius))
            if (embed(t, p, 100, true, 0.6)) buried++;
        if (buried == 0) p.displayClientMessage(Component.translatable("density.heroesevolved.hollow_none"), true);
        return true;
    }

    static boolean slam(ServerPlayer p, int tier) {
        ServerLevel level = p.serverLevel();
        double radius = 4 + tier * 0.5;
        ArmPosePayload.show(p, ArmPose.GROUND, 14);
        shockwave(p, p.position(), radius, 8 + tier);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 2));
        AbilityEffects.send(level, Kind.DENSITY_SLAM, p.position(), p.position().add(0, 1, 0), radius);
        level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1, 0.4F);
        level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 0.5F);
        return true;
    }

    /** Ground struck by something very heavy: everything nearby is hurt and thrown back (or, with Crater Landing, rooted). */
    public static int shockwave(ServerPlayer p, Vec3 at, double radius, float damage) {
        ServerLevel level = p.serverLevel();
        int struck = 0;
        for (LivingEntity t : ExpansionSkills.enemies(p, at, radius)) {
            double falloff = 1 - Math.sqrt(t.position().distanceToSqr(at)) / (radius + 1);
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), (float) (damage * Math.max(0.4, falloff)));
            struck++;
            if (DensitySkills.crater(p, t)) continue;
            Vec3 away = new Vec3(t.getX() - at.x, 0, t.getZ() - at.z);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
            t.setDeltaMovement(away.scale(0.9 * falloff + 0.3).add(0, 0.45, 0)); t.hurtMarked = true;
            if (t instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(level, at, radius + 6, 0.7F, 8);
        BlockPos below = BlockPos.containing(at.subtract(0, 0.5, 0));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), at.x, at.y + 0.1, at.z,
                40, radius * 0.4, 0.1, radius * 0.4, 0.15);
        return struck;
    }

    // ------------------------------------------------------------------
    // Skill tree
    // ------------------------------------------------------------------

    /** The three branch actives and techniques live in {@link DensitySkills}. */
    public static boolean skill(AbilityUseContext c, int branch) { return DensitySkills.skill(c, branch); }

    public static boolean technique(AbilityUseContext c, String mechanic) { return DensitySkills.technique(c, mechanic); }

    // ------------------------------------------------------------------
    // Awakening, tick and events
    // ------------------------------------------------------------------

    public static void awaken(ServerPlayer player) {
        setNotch(player, FULL_GHOST, 100);
        player.displayClientMessage(Component.translatable("phasing.heroesevolved.awakened").withStyle(ChatFormatting.GRAY), false);
    }

    public static void tick(MinecraftServer server) {
        long now = now(server);
        for (State s : List.copyOf(STATES.values())) {
            ServerPlayer p = s.player;
            if (p.isRemoved() || !p.isAlive() || !isOwner(p)) { forget(p); continue; }
            if (s.sunk) tickSunk(p, s, now);
            else tickPassage(p, s, now);
            s.last = p.position();
            if (s.notch != NEUTRAL && now >= s.freeUntil) {
                int every = (Math.abs(s.notch) == 2 ? 6 : 12) * (SkillTreeService.has(p, DensitySkills.UNTOUCHABLE) ? 2 : 1);
                if (now % every == 0 && !PlayerEnergyService.tryConsume(p, 1)) {
                    set(p, s, s.notch + (s.notch > 0 ? -1 : 1));
                    p.displayClientMessage(Component.translatable("phasing.heroesevolved.exhausted"), true);
                }
            }
            if (s.notch < 0 && now % 4 == 0)
                AbilityEffects.send(p.serverLevel(), Kind.DENSITY_GHOST, p.position(), p.position().add(p.getDeltaMovement()), s.notch == FULL_GHOST ? 1 : 0.5);
            if (s.notch > 0 && now % 6 == 0 && p.onGround())
                AbilityEffects.send(p.serverLevel(), Kind.DENSITY_DENSE, p.position(), p.position().add(0, 1, 0), s.notch == FULL_DENSE ? 1 : 0.5);
            sync(p, s);
            if (!s.sunk && s.notch == NEUTRAL && now >= s.freeUntil && s.applied == NEUTRAL) STATES.remove(p.getUUID());
        }
        tickEmbedded(now);
        SEALED.values().removeIf(until -> until <= now);
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity hurt = event.getEntity();
        var source = event.getSource();
        // A body held in the ground isn't smothered by it: the hold is the damage.
        if (source.is(DamageTypes.IN_WALL) && embedded(hurt)) { event.setCanceled(true); return; }
        if (hurt instanceof ServerPlayer player) {
            // A phaser cannot suffocate in walls.
            if (source.is(DamageTypes.IN_WALL) && isOwner(player)) { event.setCanceled(true); return; }
            int notch = notch(player);
            boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
            if (notch == FULL_GHOST && !bypass && (source.getDirectEntity() != null || source.is(DamageTypeTags.IS_FALL)
                    || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.FALLING_BLOCK))) { event.setCanceled(true); return; }
            if (notch == GHOST && source.getDirectEntity() instanceof LivingEntity) event.setAmount(event.getAmount() * 0.5F);
        }
        // An enemy stuck in the ground cannot roll with a blow.
        if (embedded(hurt) && source.getEntity() != null) event.setAmount(event.getAmount() * 1.5F);
    }

    /** Shots fly straight through a Ghost. */
    @SubscribeEvent public static void projectile(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && (hit.getEntity() instanceof ServerPlayer player && ghost(player)
                || DensitySkills.loosened(hit.getEntity())))
            event.setCanceled(true);
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        // Inside Knowledge: a body your hand has been inside is still in reach of a Full Ghost.
        if (event.getEntity() instanceof ServerPlayer player && fullGhost(player) && !DensitySkills.knows(player, event.getTarget()))
            event.setCanceled(true);
    }

    @SubscribeEvent public static void heal(LivingHealEvent event) {
        if (sealed(event.getEntity())) event.setCanceled(true);
    }

    /** Full Dense: a fall ends in a shockwave instead of a broken leg. */
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || notch(p) != FULL_DENSE || event.getDistance() < 3) return;
        event.setDamageMultiplier(0);
        impact(p, event.getDistance());
    }

    /** A Full Dense body landing from {@code distance} blocks: a shockwave instead of a broken leg. */
    public static void impact(ServerPlayer p, float distance) {
        double radius = Math.min(6, 2 + distance / 4);
        shockwave(p, p.position(), radius, Math.min(12, distance * 0.8F));
        AbilityEffects.send(p.serverLevel(), Kind.DENSITY_SLAM, p.position(), p.position().add(0, 1, 0), radius);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.5F);
    }

    public static void forget(ServerPlayer player) {
        State s = STATES.remove(player.getUUID());
        if (s != null && s.sunk) {
            s.sunk = false;
            Vec3 out = openNear(player, player.position());
            if (out != null) player.teleportTo(player.serverLevel(), out.x, out.y, out.z, player.getYRot(), player.getXRot());
        }
        clearModifiers(player);
        if (s != null) DensityPayload.send(player, 0, -1);
        Embedded held = EMBEDDED.get(player.getUUID());
        if (held != null) free(held);
        DensitySkills.drop(player);
    }

    /** Logging in inside a wall (a crash while underground): step out to the nearest open space. */
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isOwner(player) && !SpeedService.safe(player, player.position())) {
            Vec3 out = openNear(player, player.position());
            if (out != null) player.teleportTo(player.serverLevel(), out.x, out.y, out.z, player.getYRot(), player.getXRot());
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
        Embedded held = EMBEDDED.get(event.getEntity().getUUID());
        if (held != null) free(held);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (State s : List.copyOf(STATES.values())) forget(s.player);
        for (Embedded e : List.copyOf(EMBEDDED.values())) free(e);
        STATES.clear(); EMBEDDED.clear(); SEALED.clear();
        DensitySkills.clear();
    }
}
