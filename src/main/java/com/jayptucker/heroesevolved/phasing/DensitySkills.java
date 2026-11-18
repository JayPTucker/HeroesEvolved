package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

import static com.jayptucker.heroesevolved.phasing.DensityGeometry.*;

/**
 * Density's earthbending tree, built from interactions with the kit rather than stat bonuses. Three lanes:
 * <ul>
 *   <li><b>Boulder</b> (Hung Gar's tiger, and Bolin): Stacked Stones, Earth Gauntlet, Know Your Stone, Avalanche →
 *   Shatterpoint → Metalbending.</li>
 *   <li><b>Mountain</b> (the root): Sei Ping Ma, Stone Skin → Rebound, Pile Driver, Quicksand → Set in Stone →
 *   Entombed.</li>
 *   <li><b>Listener</b> (Toph): Seismic Sense, Tunnel, Grasp from Below, Earth Surf → Uproot → Wait and Listen.</li>
 * </ul>
 * Node ids are kept from the older tree (each slot now means its earthbending node), so purchases and prerequisites
 * carry over and the tree's shape is unchanged.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class DensitySkills {
    public static final String STACKED = "phasing_0_0", GAUNTLET = "phasing_0_1", KNOW_STONE = "phasing_0_2",
            AVALANCHE = "phasing_phase_carry", SHATTERPOINT = "phasing_thicker_walls", METAL = "phasing_ghost_network",
            SEI_PING_MA = "phasing_1_0", HARDEN = "phasing_1_1", PILE_DRIVER = "phasing_1_2",
            SINKHOLE = "phasing_phase_lock", SET_IN_STONE = "phasing_long_lock", ENTOMBED = "phasing_entombed",
            SEISMIC = "phasing_2_0", TUNNEL = "phasing_2_1", GRASP = "phasing_2_2",
            SURF = "phasing_phase_fall", UPROOT = "phasing_deep_fall", LISTEN = "phasing_untouchable";

    /** Seismic Stomp's sight lasts this long; Wait and Listen needs this long standing still; and rests this long after. */
    public static final int STOMP_TICKS = 80, LISTEN_STILL = 30, LISTEN_REST = 200;
    public static final double STOMP_RADIUS = 24, SENSE_RADIUS = 20;
    /** How much harder the blow after a heard one lands (Wait and Listen). */
    public static final float LISTEN_BONUS = 1.5F;

    private static final class Hardened {
        final ServerPlayer player; final long until; float stored;
        Hardened(ServerPlayer player, long until) { this.player = player; this.until = until; }
    }
    private record Sinkhole(ServerPlayer owner, ServerLevel level, Vec3 center, long opens, long until) {}
    /** A bender standing still in stance: since when, and whether they are listening yet. */
    private static final class Stillness { ServerPlayer player; long since; Vec3 at; boolean listening; }

    private static final Map<UUID, Long> PILED = new HashMap<>(), STOMPS = new HashMap<>(), LISTEN_READY = new HashMap<>();
    private static final Set<UUID> PRIMED = new HashSet<>();
    private static final Map<UUID, Hardened> HARDENS = new HashMap<>();
    private static final List<Sinkhole> SINKHOLES = new ArrayList<>();
    private static final Map<UUID, Stillness> STILL = new HashMap<>();
    private static final Map<UUID, ServerPlayer> STOMPERS = new HashMap<>();

    private DensitySkills() {}

    private static long now(Entity e) { return e.getServer() == null ? 0 : e.getServer().overworld().getGameTime(); }
    private static boolean has(ServerPlayer p, String node) { return SkillTreeService.has(p, node); }

    // ------------------------------------------------------------------
    // Routing
    // ------------------------------------------------------------------

    public static boolean canUse(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        if (DensityService.sinking(p)) return branch == 2;
        return branch != 0 || Targeting.livingEntityInSight(p, 18, t -> ExpansionSkills.hostile(p, t)) != null;
    }

    /** The three branch actives: Earth Gauntlet, Stone Skin, Tunnel. Each poses itself. */
    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        return switch (branch) {
            case 0 -> Stones.gauntlet(p, c.abilityLevel());
            case 1 -> harden(p);
            case 2 -> DensityService.sinkOrSurface(p, c.abilityLevel());
            default -> false;
        };
    }

    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        return switch (mechanic) {
            case "phase_carry" -> Stones.avalanche(p, c.abilityLevel());
            case "phase_lock" -> sinkhole(p);
            case "phase_fall" -> Earthworks.surf(p, c.abilityLevel());
            default -> false;
        };
    }

    /** The second press of a branch active (free, ignoring the cooldown): Stone Skin's Rebound, or up out of a Tunnel. */
    public static boolean followUp(AbilityUseContext c, int branch, String mechanic) {
        if (!mechanic.isEmpty()) return false;
        if (branch == 2 && DensityService.sinking(c.player())) return DensityService.surface(c.player(), true);
        return branch == 1 && rebound(c.player());
    }

    // ------------------------------------------------------------------
    // Seismic Stomp, Seismic Sense and Wait and Listen
    // ------------------------------------------------------------------

    /**
     * Shift + Horse Stance, Seismic Stomp: one stomp, and for four seconds every footstep within 24 blocks comes up
     * through the ground to you, through walls. What flies or swims makes no footsteps.
     */
    static boolean stomp(ServerPlayer p) {
        if (DensityService.footing(p) == null || !p.onGround()) {
            p.displayClientMessage(Component.translatable("density.heroesevolved.need_ground"), true);
            return false;
        }
        STOMPS.put(p.getUUID(), now(p) + STOMP_TICKS);
        STOMPERS.put(p.getUUID(), p);
        ArmPosePayload.show(p, ArmPose.EARTH_STOMP, 12);
        DensityGeometry.show(p.serverLevel(), Piece.EARTH_STOMP, p.position(), p.position(), (float) STOMP_RADIUS, p);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1, .6F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, 1.2F, .5F);
        DensityService.feel(p, STOMP_RADIUS, 6, p.getY() - 8);
        return true;
    }

    /** True while a stomp's sight lasts. */
    public static boolean sensing(ServerPlayer p) { return STOMPS.getOrDefault(p.getUUID(), 0L) > now(p); }

    /** True while a bender stands listening (Wait and Listen). */
    public static boolean listening(ServerPlayer p) { Stillness s = STILL.get(p.getUUID()); return s != null && s.listening; }

    private static void tickStillness(ServerPlayer p, long now) {
        if (!DensityService.stance(p) || !p.onGround()) { STILL.remove(p.getUUID()); return; }
        Stillness s = STILL.computeIfAbsent(p.getUUID(), id -> { Stillness n = new Stillness(); n.player = p; n.since = now; n.at = p.position(); return n; });
        if (p.position().distanceToSqr(s.at) > .01) { s.at = p.position(); s.since = now; s.listening = false; return; }
        // Seismic Sense: standing still in stance, the ground tells you who walks on it.
        if (has(p, SEISMIC) && (now - s.since) % 20 == 10) DensityService.feel(p, SENSE_RADIUS, 6, p.getY() - 8);
        if (has(p, LISTEN) && !s.listening && now - s.since >= LISTEN_STILL && LISTEN_READY.getOrDefault(p.getUUID(), 0L) <= now) {
            s.listening = true;
            showTo(p, Piece.EARTH_LISTEN, p.position(), p.position(), 1, p);
            ArmPosePayload.show(p, ArmPose.EARTH_LISTEN, 20);
            p.displayClientMessage(Component.translatable("density.heroesevolved.listening").withStyle(ChatFormatting.GOLD), true);
        }
        if (s.listening && now % 10 == 0) {
            ArmPosePayload.show(p, ArmPose.EARTH_LISTEN, 14);
            showTo(p, Piece.EARTH_LISTEN, p.position(), p.position(), 1, p);
        }
    }

    /** Wait and Listen: the blow you heard coming never lands; a pillar answers it, and your next blow is heavier. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void heard(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !listening(p)) return;
        var source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == p) return;
        event.setCanceled(true);
        STILL.remove(p.getUUID());
        LISTEN_READY.put(p.getUUID(), now(p) + LISTEN_REST);
        PRIMED.add(p.getUUID());
        DensityService.fillRoot(p);
        BlockPos ground = EarthGeometry.ground(attacker.level(), attacker.position().add(0, .5, 0));
        if (ground != null) {
            BlockState state = attacker.level().getBlockState(ground);
            Earthworks.pillarAt(p, ground.above(), Direction.UP, 3, state, (EarthGeometry.PILLAR_DAMAGE + DensityService.tier(p)) * DensityService.rooted(p));
        }
        ArmPosePayload.show(p, ArmPose.EARTH_RAISE, 10);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1, 1.2F);
        p.displayClientMessage(Component.translatable("density.heroesevolved.heard").withStyle(ChatFormatting.GOLD), true);
    }

    /** A finisher's reward: full Root at once. */
    public static void prime(ServerPlayer p) { DensityService.fillRoot(p); }

    // ------------------------------------------------------------------
    // Mountain
    // ------------------------------------------------------------------

    /** Stone Skin: plates of ground climb onto you and hold Horse Stance for free, banking every blow. Press again: Rebound. */
    static boolean harden(ServerPlayer p) {
        int hardenTicks = HARDEN_TICKS * (com.jayptucker.heroesevolved.amplify.Amplification.surged(p) ? 2 : 1);
        DensityService.setNotch(p, DensityService.FULL_DENSE, hardenTicks);
        if (DensityService.notch(p) != DensityService.FULL_DENSE) return false;
        HARDENS.put(p.getUUID(), new Hardened(p, now(p) + hardenTicks));
        ArmPosePayload.show(p, ArmPose.DENSITY_BRACE, 12);
        show(p.serverLevel(), Piece.DENSITY_HARDEN, p.position(), p.position(), 1, p);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.PLAYERS, 1, .5F);
        return true;
    }

    public static float hardened(ServerPlayer p) { Hardened h = HARDENS.get(p.getUUID()); return h == null ? -1 : h.stored; }

    /** Rebound: every blow Stone Skin banked comes back out of the ground as one shockwave. */
    static boolean rebound(ServerPlayer p) {
        Hardened h = HARDENS.get(p.getUUID());
        if (h == null || h.until <= now(p)) return false;
        HARDENS.remove(p.getUUID());
        double radius = REBOUND_RADIUS + Math.min(3, h.stored / 8);
        float damage = Math.min(18, 4 + h.stored * .6F);
        ArmPosePayload.show(p, ArmPose.DENSITY_BURST, 10);
        DensityService.shockwave(p, p.position(), radius, damage);
        show(p.serverLevel(), Piece.DENSITY_REBOUND, p.position(), p.position(), (float) radius, null);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1, .35F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, .7F, .6F);
        return true;
    }

    /** Pile Driver: an enemy one of your shockwaves hits is rooted knee-deep instead of thrown. False if it wasn't. */
    static boolean crater(ServerPlayer p, LivingEntity t) {
        if (!has(p, PILE_DRIVER)) return false;
        if (DensityService.embedded(t)) return true;
        if (!DensityService.embed(t, p, CRATER_TICKS, false, .3)) return false;
        Vec3 ground = DensityService.surfaceOf(t);
        show(p.serverLevel(), Piece.DENSITY_PILE, ground, ground, t.getBbWidth(), null);
        return true;
    }

    /** Quicksand: after a short warning, the ground at a point you look at turns to quicksand, and enemies in it sink waist-deep. */
    static boolean sinkhole(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        // Under a foe you look at; otherwise the ground you aim at.
        LivingEntity foe = Targeting.livingEntityInSight(p, SINKHOLE_RANGE, t -> ExpansionSkills.hostile(p, t));
        BlockPos ground = foe == null ? null : EarthGeometry.ground(level, foe.position().add(0, .5, 0));
        if (ground == null) {
            HitResult hit = p.pick(SINKHOLE_RANGE, 1, false);
            if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
                p.displayClientMessage(Component.translatable("density.heroesevolved.no_ground_there"), true);
                return false;
            }
            ground = block.getBlockPos();
        }
        if (!level.getBlockState(ground.above()).getCollisionShape(level, ground.above()).isEmpty() || !Passage.passable(p, ground)
                || !EarthGeometry.earth(level.getBlockState(ground), has(p, METAL))) {
            p.displayClientMessage(Component.translatable("density.heroesevolved.no_earth_there"), true);
            return false;
        }
        sinkholeAt(p, Vec3.atBottomCenterOf(ground.above()));
        return true;
    }

    /** Opens Quicksand on the ground at {@code center} (its warning first). */
    static void sinkholeAt(ServerPlayer p, Vec3 center) {
        ServerLevel level = p.serverLevel();
        long now = now(p);
        SINKHOLES.add(new Sinkhole(p, level, center, now + SINKHOLE_WARN, now + SINKHOLE_WARN + SINKHOLE_TICKS));
        ArmPosePayload.show(p, ArmPose.DENSITY_PRESS, 14);
        show(level, Piece.DENSITY_SINK_MARK, center, center, (float) SINKHOLE_RADIUS, null);
        level.playSound(null, BlockPos.containing(center), SoundEvents.MUD_BREAK, SoundSource.PLAYERS, 1, .4F);
    }

    /** Entombed (Earth Shackle's press on a body already crushed): the ground closes right over it. */
    static boolean entomb(ServerPlayer p, LivingEntity t) {
        if (!has(p, ENTOMBED) || DensityService.buried(t)) return false;
        if (!DensityService.entomb(t, p, ENTOMB_TICKS)) return false;
        Vec3 ground = DensityService.surfaceOf(t);
        ArmPosePayload.show(p, ArmPose.DENSITY_CLOSE, 14);
        show(p.serverLevel(), Piece.DENSITY_ENTOMB, ground, ground, t.getBbWidth() + .4F, null);
        p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.2F, .4F);
        p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.GRAVEL_PLACE, SoundSource.PLAYERS, 1, .5F);
        return true;
    }

    // ------------------------------------------------------------------
    // Listener
    // ------------------------------------------------------------------

    /** Uproot: a body you surface under can't heal for a moment (one you held in the ground is torn out of it). */
    static void uprooted(ServerPlayer p, LivingEntity above, boolean torn) {
        DensityService.seal(above, UPROOT_SEAL);
        show(p.serverLevel(), Piece.DENSITY_UPROOT, above.position(), above.position(), torn ? 2 : 1, null);
    }

    /** Grasp from Below (Rock Strike while tunnelling): the nearest enemy above you is pulled waist-deep by the ankles. */
    public static boolean grasp(ServerPlayer p, int tier) {
        if (!has(p, GRASP)) { p.displayClientMessage(Component.translatable("density.heroesevolved.underground"), true); return false; }
        ServerLevel level = p.serverLevel();
        List<LivingEntity> above = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(GRASP_REACH, 8, GRASP_REACH),
                t -> ExpansionSkills.hostile(p, t) && t.onGround() && t.getY() > p.getEyeY() - .5 && !DensityService.embedded(t));
        above.sort(Comparator.comparingDouble(p::distanceToSqr));
        ArmPosePayload.show(p, ArmPose.DENSITY_GRASP, 12);
        for (LivingEntity t : above) {
            if (!DensityService.embed(t, p, GRASP_TICKS + tier * 4, false, .5)) continue;
            DensityService.seal(t, GRASP_TICKS);
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().indirectMagic(p, p), 2 + tier * .5F);
            Vec3 ground = DensityService.surfaceOf(t);
            show(level, Piece.DENSITY_GRASP, ground, ground, t.getBbWidth(), null);
            level.playSound(null, t.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1, .5F);
            return true;
        }
        p.displayClientMessage(Component.translatable("density.heroesevolved.nothing_to_reach"), true);
        return false;
    }

    // ------------------------------------------------------------------
    // Metalbending: iron doors open to a metalbender's hand
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void bend(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getMainHandItem().isEmpty() || !has(p, METAL) || !DensityService.isOwner(p)) return;
        ServerLevel level = p.serverLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(EarthGeometry.METAL)) return;
        if (state.getBlock() instanceof DoorBlock door && !state.getValue(BlockStateProperties.OPEN)) {
            door.setOpen(p, level, state, pos, true);
        } else if (state.getBlock() instanceof TrapDoorBlock && state.hasProperty(BlockStateProperties.OPEN)) {
            level.setBlock(pos, state.cycle(BlockStateProperties.OPEN), 3);
        } else return;
        ArmPosePayload.show(p, ArmPose.EARTH_CRUSH, 10);
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, .6F, 1.6F);
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------
    // Damage
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity hurt = event.getEntity();
        var source = event.getSource();
        if (hurt instanceof ServerPlayer h) {
            Hardened hard = HARDENS.get(h.getUUID());
            if (hard != null && hard.until > now(h)) hard.stored = Math.min(HARDEN_CAP, hard.stored + event.getAmount());
        }
        if (!(source.getEntity() instanceof ServerPlayer p) || hurt == p) return;
        // Wait and Listen: the blow after a heard one is heavier, whatever throws it.
        if (PRIMED.remove(p.getUUID())) event.setAmount(event.getAmount() * LISTEN_BONUS);
        // Only your own fists from here on: not a thrown rock, not the ground's grip on an Entombed body.
        if (source.getDirectEntity() != p || !source.is(DamageTypes.PLAYER_ATTACK)) return;
        boolean stance = DensityService.stance(p);
        // Set in Stone: each blow in stance on a body you hold sets it a second longer.
        if (stance && has(p, SET_IN_STONE) && DensityService.holder(hurt) == p) DensityService.extend(hurt, SET_STEP, SET_MAX);
        // Pile Driver: a punch in Horse Stance drives a standing enemy into the ground.
        if (stance && has(p, PILE_DRIVER) && hurt.onGround() && !DensityService.embedded(hurt)
                && ExpansionSkills.hostile(p, hurt) && PILED.getOrDefault(hurt.getUUID(), 0L) <= now(p)
                && DensityService.embed(hurt, p, PILE_TICKS, false, .3)) {
            PILED.put(hurt.getUUID(), now(p) + PILE_COOLDOWN);
            Vec3 ground = DensityService.surfaceOf(hurt);
            show(p.serverLevel(), Piece.DENSITY_PILE, ground, ground, hurt.getBbWidth(), null);
            p.serverLevel().playSound(null, hurt.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .6F, .5F);
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        tickHarden(now);
        tickSinkholes(now);
        for (ServerPlayer p : List.copyOf(STOMPERS.values())) {
            if (p.isRemoved() || !sensing(p)) { STOMPERS.remove(p.getUUID()); continue; }
            if ((STOMPS.get(p.getUUID()) - now) % 10 == 0) DensityService.feel(p, STOMP_RADIUS, 6, p.getY() - 8);
        }
        for (ServerPlayer p : DensityService.benders())
            if (DensityService.stance(p) && (has(p, SEISMIC) || has(p, LISTEN))) tickStillness(p, now);
        STILL.values().removeIf(s -> s.player.isRemoved() || !DensityService.stance(s.player));
        if (now % 100 == 0) {
            PILED.values().removeIf(until -> until <= now);
            STOMPS.values().removeIf(until -> until <= now);
            LISTEN_READY.values().removeIf(until -> until <= now);
        }
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    private static void tickHarden(long now) {
        for (var it = HARDENS.entrySet().iterator(); it.hasNext();) {
            Hardened h = it.next().getValue();
            ServerPlayer p = h.player;
            if (gone(p) || now >= h.until || DensityService.notch(p) != DensityService.FULL_DENSE) { it.remove(); continue; }
            if (now % 10 == 0) {
                show(p.serverLevel(), Piece.DENSITY_HARDEN, p.position(), p.position(), 1 + h.stored / 8, p);
                ArmPosePayload.show(p, ArmPose.DENSITY_BRACE, 12);
            }
        }
    }

    private static void tickSinkholes(long now) {
        for (Sinkhole s : List.copyOf(SINKHOLES)) {
            if (now >= s.until || s.owner.isRemoved() || s.owner.level() != s.level) { SINKHOLES.remove(s); continue; }
            if (now < s.opens) continue;
            if (now == s.opens) s.level.playSound(null, BlockPos.containing(s.center), SoundEvents.MUD_BREAK, SoundSource.PLAYERS, 1.2F, .6F);
            if ((now - s.opens) % 10 == 0) show(s.level, Piece.DENSITY_SINKHOLE, s.center, s.center, (float) SINKHOLE_RADIUS, null);
            if ((now - s.opens) % 5 != 0) continue;
            for (LivingEntity t : s.level.getEntitiesOfClass(LivingEntity.class, new AABB(s.center, s.center).inflate(SINKHOLE_RADIUS, 2, SINKHOLE_RADIUS),
                    t -> ExpansionSkills.hostile(s.owner, t) && !DensityService.embedded(t)
                            && t.position().subtract(s.center).horizontalDistance() <= SINKHOLE_RADIUS && Math.abs(t.getY() - s.center.y) < 1.5))
                DensityService.embed(t, s.owner, SINKHOLE_HOLD, false, .5);
        }
    }

    private static boolean gone(ServerPlayer p) { return p.isRemoved() || !p.isAlive(); }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    static void drop(ServerPlayer p) {
        UUID id = p.getUUID();
        HARDENS.remove(id); STOMPS.remove(id); STOMPERS.remove(id); STILL.remove(id); PRIMED.remove(id);
        SINKHOLES.removeIf(s -> s.owner == p);
    }

    static void clear() {
        PILED.clear(); STOMPS.clear(); STOMPERS.clear(); LISTEN_READY.clear(); PRIMED.clear();
        HARDENS.clear(); SINKHOLES.clear(); STILL.clear();
    }
}
