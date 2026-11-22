package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.entity.BlockDebris;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.phasing.EarthGeometry.Material;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.jayptucker.heroesevolved.phasing.EarthGeometry.*;

/**
 * Rock that leaves the ground: Rock Strike's stone (held to a boulder), Stacked Stones' discs, a pebble pulled from no
 * ground, Shatterpoint's shrapnel, Avalanche's rolling boulders and the Earth Gauntlet. Each is a real block flying about
 * ({@link BlockDebris}), made of the ground it came from, moved here every server tick and swept against bodies and walls.
 * <p>
 * Hung Gar's tiger claw is the shape of it: the stomp tears the rock up, the punch drives it on.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Stones {
    public enum Kind { STONE, DISC, PEBBLE, SHRAPNEL, AVALANCHE, GAUNTLET }

    static final class Rock {
        final ServerPlayer owner;
        final ServerLevel level;
        final BlockDebris display;
        final BlockState block;
        final Material material;
        final Kind kind;
        final Set<UUID> hit = new HashSet<>();
        Vec3 pos, vel = Vec3.ZERO, rise;
        float size, share = 1, growth;
        int age, flightAge, slot, tier;
        boolean flying, growing, harmless, molten, rolling;
        double travelled, range = ROCK_RANGE;

        Rock(ServerPlayer owner, BlockState block, Kind kind, Vec3 at, float size, int tier) {
            this.owner = owner; this.level = owner.serverLevel(); this.block = block; this.kind = kind;
            this.material = Material.of(block); this.pos = at; this.size = size; this.tier = tier;
            // Bolin's discs are flat; everything else is a lump as tall as it is wide.
            display = new BlockDebris(level, EarthGeometry.construct(block), size, kind == Kind.DISC ? .35F : 1);
            display.setPos(at);
            // Named, so a kill reads "pelted by a rock", not by a block display.
            display.setCustomName(net.minecraft.network.chat.Component.translatable("entity.heroesevolved.earth_rock"));
            level.addFreshEntity(display);
        }
    }

    /** An enemy an Earth Gauntlet holds: dragged to its owner, or pinned to the wall behind it. */
    private record Grip(ServerPlayer owner, LivingEntity target, boolean pin, long until) {}

    /** Rocks lifted and waiting for the punch, per owner, in the order they'll be thrown. */
    private static final Map<UUID, List<Rock>> HELD = new HashMap<>();
    private static final List<Rock> FLYING = new ArrayList<>();
    private static final Map<UUID, Grip> GRIPS = new HashMap<>();

    private Stones() {}

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }

    public static boolean holding(ServerPlayer p) { List<Rock> held = HELD.get(p.getUUID()); return held != null && !held.isEmpty(); }

    /** How many rocks {@code p} has lifted and not yet thrown. */
    public static int held(ServerPlayer p) { List<Rock> held = HELD.get(p.getUUID()); return held == null ? 0 : held.size(); }

    /** The first rock {@code p} has lifted and not thrown, or null (for tests and the preview). */
    static Rock firstHeld(ServerPlayer p) { List<Rock> held = HELD.get(p.getUUID()); return held == null || held.isEmpty() ? null : held.get(0); }

    /** All the rocks in the air (for tests and the preview). */
    public static List<Rock> flying() { return List.copyOf(FLYING); }
    public static float size(Rock rock) { return rock.size; }
    public static Kind kind(Rock rock) { return rock.kind; }

    // ------------------------------------------------------------------
    // Rock Strike
    // ------------------------------------------------------------------

    /**
     * H, Rock Strike: the stomp tears a stone up out of the ground in front of you, and it hovers at your chest. Hold the
     * key and it grows to a boulder. With Stacked Stones three discs come up instead. With no earth underfoot, a pebble.
     */
    public static boolean lift(ServerPlayer p, int tier) {
        if (holding(p)) return false;
        DensityService.track(p);
        BlockState ground = DensityService.footing(p);
        boolean pebble = ground == null;
        if (pebble) {
            // No earth to pull from: a pebble out of the dust, weaker, and it costs a little more.
            PlayerEnergyService.consumeUpTo(p, PEBBLE_COST);
            ground = Blocks.COBBLESTONE.defaultBlockState();
            p.displayClientMessage(Component.translatable("density.heroesevolved.pebble"), true);
        }
        if (p.level().dimensionType().ultraWarm() && !pebble && !ground.is(Blocks.MAGMA_BLOCK) && Material.of(ground) != Material.METAL)
            ground = Blocks.MAGMA_BLOCK.defaultBlockState();
        List<Rock> held = new ArrayList<>();
        Vec3 hover = hover(p, 1);
        BlockPos under = EarthGeometry.ground(p.level(), hover.subtract(0, ROCK_HEIGHT, 0));
        Vec3 from = under == null ? hover.subtract(0, ROCK_HEIGHT, 0) : Vec3.atCenterOf(under).add(0, .2, 0);
        if (!pebble && SkillTreeService.has(p, DensitySkills.STACKED)) {
            for (int i = 0; i < 3; i++) {
                Rock disc = new Rock(p, ground, Kind.DISC, from.add(sideways(p, i - 1, .55)), DISC_SIZE, tier);
                disc.slot = i - 1; disc.share = DISC_SHARE; disc.rise = from;
                held.add(disc);
            }
        } else {
            Rock rock = new Rock(p, ground, pebble ? Kind.PEBBLE : Kind.STONE, from, pebble ? PEBBLE_SIZE : ROCK_SIZE, tier);
            rock.share = pebble ? PEBBLE_SHARE : 1;
            rock.growing = !pebble;
            rock.rise = from;
            held.add(rock);
        }
        for (Rock rock : held) rock.molten = ground.is(Blocks.MAGMA_BLOCK);
        HELD.put(p.getUUID(), held);
        ArmPosePayload.show(p, ArmPose.EARTH_LIFT, 12);
        EarthGeometry.show(p.serverLevel(), Piece.EARTH_LIFT, from, from.add(0, 1, 0), held.size() > 1 ? 1.4F : 1, null, ground);
        p.serverLevel().playSound(null, BlockPos.containing(from), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1, .55F);
        p.serverLevel().playSound(null, BlockPos.containing(from), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, .8F, .5F);
        dust(p.serverLevel(), ground, from, 8, .4);
        return true;
    }

    /** The key came up: a stone that was growing stays the size it reached. */
    public static void release(ServerPlayer p) {
        List<Rock> held = HELD.get(p.getUUID());
        if (held != null) for (Rock rock : held) rock.growing = false;
    }

    /** Rock Strike's second press, free: the punch drives the next lifted rock at what you look at. */
    public static boolean strike(AbilityUseContext c) {
        ServerPlayer p = c.player();
        List<Rock> held = HELD.get(p.getUUID());
        if (held == null || held.isEmpty()) return false;
        Rock rock = held.remove(0);
        if (held.isEmpty()) HELD.remove(p.getUUID());
        rock.growing = false;
        launch(p, rock, null);
        return true;
    }

    /** Throws {@code rock} from where it hovers at what {@code p} looks at (or along {@code aim}, if given). */
    static void launch(ServerPlayer p, Rock rock, Vec3 aim) {
        // Punched before it finished rising: it meets the fist at the chest, not inside the ground it came from.
        if (rock.age < ROCK_RISE) { rock.pos = hoverAt(p, rock, p.serverLevel().getGameTime()); rock.display.setPos(rock.pos); }
        boolean know = SkillTreeService.has(p, DensitySkills.KNOW_STONE);
        double speed = Mth.lerp(rock.growth, ROCK_SPEED, BOULDER_SPEED) * (know && rock.material == Material.HEAVY ? .8 : 1);
        Vec3 dir = aim != null ? aim : aimAt(p, rock.pos, speed);
        boolean rear = rock.slot > 0 || (rock.kind == Kind.STONE && rock.growth > .5F);
        ArmPosePayload.show(p, rock.growth > .5F ? ArmPose.EARTH_SHOVE : rear ? ArmPose.STYLE_CROSS : ArmPose.EARTH_STRIKE, 10);
        // Know Your Stone: gravel bursts apart off the fist into three pellets.
        if (know && rock.material == Material.GRAVEL && rock.kind != Kind.SHRAPNEL) {
            for (int i = -1; i <= 1; i++) {
                Rock pellet = new Rock(p, rock.block, Kind.SHRAPNEL, rock.pos, rock.size * .5F, rock.tier);
                pellet.share = rock.share * .45F;
                pellet.range = 16;
                fly(pellet, rotate(dir, i * 7), speed * 1.1);
            }
            rock.display.discard();
        } else {
            fly(rock, dir, speed);
        }
        ServerLevel level = p.serverLevel();
        level.playSound(null, BlockPos.containing(rock.pos), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, .8F, .6F);
        level.playSound(null, BlockPos.containing(rock.pos), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1, .6F - rock.growth * .2F);
        EarthGeometry.show(level, Piece.EARTH_THROW, rock.pos, rock.pos.add(dir), rock.size, p, rock.block);
    }

    private static void fly(Rock rock, Vec3 dir, double speed) {
        rock.flying = true;
        rock.vel = dir.normalize().scale(speed);
        rock.flightAge = 0;
        FLYING.add(rock);
    }

    /** The throw's direction: at the enemy in sight (allowing for the drop), or along the look. */
    static Vec3 aimAt(ServerPlayer p, Vec3 from, double speed) {
        LivingEntity target = Targeting.livingEntityInSight(p, ROCK_RANGE, t -> ExpansionSkills.hostile(p, t));
        if (target == null) {
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            HitResult hit = p.level().clip(new ClipContext(eye, eye.add(look.scale(ROCK_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 point = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(ROCK_RANGE)) : hit.getLocation();
            return point.subtract(from).normalize();
        }
        Vec3 aim = target.getBoundingBox().getCenter();
        double ticks = aim.distanceTo(from) / speed;
        return aim.add(0, ROCK_GRAVITY * ticks * ticks * .5, 0).subtract(from).normalize();
    }

    private static Vec3 rotate(Vec3 dir, double degrees) {
        double r = Math.toRadians(degrees), c = Math.cos(r), s = Math.sin(r);
        return new Vec3(dir.x * c - dir.z * s, dir.y, dir.x * s + dir.z * c);
    }

    private static Vec3 sideways(ServerPlayer p, int slot, double gap) {
        Vec3 fwd = forward(p.getYRot());
        return new Vec3(-fwd.z, 0, fwd.x).scale(slot * gap);
    }

    // ------------------------------------------------------------------
    // Hitting
    // ------------------------------------------------------------------

    /** The damage a rock lands: 6 + tier for a stone at the unlock tier, scaled by its share, its size and the Root. */
    static float damage(Rock rock) {
        float base = (ROCK_DAMAGE + rock.tier) * rock.share * (1 + .8F * rock.growth);
        if (rock.kind == Kind.AVALANCHE) base = 5 + rock.tier;
        if (rock.kind == Kind.GAUNTLET) base = 4 + rock.tier;
        boolean know = SkillTreeService.has(rock.owner, DensitySkills.KNOW_STONE);
        if (know && rock.material == Material.HEAVY) base *= 1.4F;
        if (rock.material == Material.METAL) base *= 1.25F;
        return base * DensityService.rooted(rock.owner);
    }

    private static void strikeBody(Rock rock, LivingEntity t) {
        ServerPlayer p = rock.owner;
        rock.hit.add(t.getUUID());
        float damage = damage(rock);
        // Metalbending: armour is a handle, and a heavier blow.
        int armour = 0;
        if (SkillTreeService.has(p, DensitySkills.METAL))
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                if (!t.getItemBySlot(slot).isEmpty()) armour++;
        damage += armour;
        StyleHits.hurt(t, p.damageSources().thrown(rock.display, p), damage);
        Vec3 along = rock.vel.lengthSqr() < 1.0E-4 ? t.position().subtract(p.position()).normalize() : rock.vel.normalize();
        double push = rock.kind == Kind.AVALANCHE ? .9 : .45 + .6 * rock.growth + (rock.material == Material.HEAVY ? .25 : 0);
        if (armour >= 2 && rock.kind == Kind.GAUNTLET) push = 0;
        if (push > 0) StyleHits.shove(t, along.multiply(1, 0, 1).scale(push).add(0, rock.kind == Kind.AVALANCHE ? .5 : .22 + .2 * rock.growth, 0));
        effects(rock, t);
        CameraShakePayload.sendAround(rock.level, t.position(), 12, .12F + .25F * rock.growth, 4);
    }

    /** Know Your Stone, mud and molten rock: what a rock does besides hit. */
    private static void effects(Rock rock, LivingEntity t) {
        ServerPlayer p = rock.owner;
        boolean know = SkillTreeService.has(p, DensitySkills.KNOW_STONE);
        if (com.jayptucker.heroesevolved.hydro.HydroService.soaked(t) || know && rock.material == Material.MUD)
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        if (know && rock.material == Material.SAND) t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
        if (rock.molten) t.igniteForSeconds(4);
    }

    /** A rock that meets a wall (or runs out of range) breaks; with Shatterpoint, it bursts into shrapnel. */
    private static void shatter(Rock rock, Vec3 at, Vec3 normal, boolean wall) {
        ServerLevel level = rock.level;
        EarthGeometry.show(level, Piece.EARTH_SHATTER, at, at.add(normal), rock.size, null, rock.block);
        dust(level, rock.block, at, 10 + Math.round(rock.size * 8), rock.size * .5);
        level.playSound(null, BlockPos.containing(at), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1, .7F - rock.growth * .2F);
        if (wall && rock.kind != Kind.SHRAPNEL && !rock.harmless && SkillTreeService.has(rock.owner, DensitySkills.SHATTERPOINT)) {
            Vec3 in = rock.vel.lengthSqr() < 1.0E-4 ? normal : rock.vel.normalize();
            Vec3 out = in.subtract(normal.scale(2 * in.dot(normal))).normalize();
            int n = 5;
            for (int i = 0; i < n; i++) {
                double yaw = (i - (n - 1) / 2.0) * 14, lift = (hash(i) - .5) * .3;
                Rock shard = new Rock(rock.owner, rock.block, Kind.SHRAPNEL, at.add(normal.scale(.3)), .25F, rock.tier);
                shard.share = .35F * rock.share * (1 + rock.growth);
                shard.range = 7;
                fly(shard, rotate(out, yaw).add(0, lift, 0), 1.3);
            }
            level.playSound(null, BlockPos.containing(at), SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.PLAYERS, 1, .9F);
        }
        rock.display.discard();
    }

    /** A cheap deterministic hash, so shrapnel spreads the same way every time. */
    private static double hash(int i) { long h = i * 0x9E3779B97F4A7C15L; h ^= h >>> 31; return (h & 0xFFFF) / 65535.0; }

    static void dust(ServerLevel level, BlockState block, Vec3 at, int count, double spread) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block), at.x, at.y, at.z, count, spread, spread * .5, spread, .08);
    }

    // ------------------------------------------------------------------
    // Earth Gauntlet and Avalanche (the Boulder lane's actives)
    // ------------------------------------------------------------------

    /** Earth Gauntlet: a stone glove flies out at the enemy you look at, grabs them, and drags them to you or pins them. */
    public static boolean gauntlet(ServerPlayer p, int tier) {
        LivingEntity target = Targeting.livingEntityInSight(p, 18, t -> ExpansionSkills.hostile(p, t));
        if (target == null) return false;
        BlockState ground = DensityService.footing(p);
        if (ground == null) ground = Blocks.COBBLESTONE.defaultBlockState();
        Vec3 from = EarthGeometry.palm(p, p.getLookAngle(), 1, true);
        Rock glove = new Rock(p, ground, Kind.GAUNTLET, from, .55F, tier);
        glove.range = 20;
        fly(glove, target.getBoundingBox().getCenter().subtract(from), 1.6);
        ArmPosePayload.show(p, ArmPose.EARTH_GAUNTLET, 12);
        EarthGeometry.show(p.serverLevel(), Piece.EARTH_THROW, from, from.add(glove.vel), .55F, p, ground);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, 1, .6F);
        return true;
    }

    private static void grab(Rock glove, LivingEntity t) {
        // A boss takes the blow, and the glove breaks on it.
        if (DensityService.immovable(t)) {
            shatter(glove, t.getBoundingBox().getCenter(), glove.vel.normalize().reverse(), false);
            return;
        }
        ServerPlayer p = glove.owner;
        Vec3 along = glove.vel.multiply(1, 0, 1).normalize();
        // A wall within four blocks behind them: pinned to it. Otherwise dragged back to the bender.
        HitResult behind = t.level().clip(new ClipContext(t.getBoundingBox().getCenter(), t.getBoundingBox().getCenter().add(along.scale(4)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t));
        boolean pin = behind.getType() == HitResult.Type.BLOCK;
        if (pin) {
            Vec3 wall = behind.getLocation().subtract(along.scale(t.getBbWidth() * .5 + .05));
            Vec3 to = new Vec3(wall.x, t.getY(), wall.z);
            if (SpeedService.safe(t, to)) com.jayptucker.heroesevolved.combat.Takedowns.move(t, to);
        }
        GRIPS.put(t.getUUID(), new Grip(p, t, pin, now(p) + (pin ? 50 : 14)));
        EarthGeometry.show(glove.level, Piece.EARTH_GAUNTLET, t.getBoundingBox().getCenter(), p.position(), t.getBbWidth() + (pin ? 10 : 0), t, glove.block);
        glove.level.playSound(null, t.blockPosition(), SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 1.1F, .6F);
    }

    private static void tickGrips(long now) {
        for (Grip g : List.copyOf(GRIPS.values())) {
            LivingEntity t = g.target;
            if (now >= g.until || !t.isAlive() || t.isRemoved() || g.owner.isRemoved() || t.level() != g.owner.level()) { GRIPS.remove(t.getUUID()); continue; }
            t.setDeltaMovement(Vec3.ZERO);
            t.resetFallDistance();
            if (g.pin) { if (t instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop(); continue; }
            Vec3 to = g.owner.position().add(forward(g.owner.getYRot()).scale(1.6 + t.getBbWidth() * .5));
            Vec3 step = to.subtract(t.position()).multiply(1, 0, 1);
            if (step.length() < .3) { GRIPS.remove(t.getUUID()); continue; }
            Vec3 next = SpeedService.swept(t, step.normalize().scale(Math.min(step.length(), 1.1)));
            com.jayptucker.heroesevolved.combat.Takedowns.move(t, next);
        }
    }

    /** True while an Earth Gauntlet pins {@code t} to a wall. */
    public static boolean pinned(LivingEntity t) { Grip g = GRIPS.get(t.getUUID()); return g != null && g.pin; }

    /** Avalanche: five boulders torn up in a line ahead of you, one after another, roll on over the ground. */
    public static boolean avalanche(ServerPlayer p, int tier) {
        BlockState ground = DensityService.footing(p);
        if (ground == null) { p.displayClientMessage(Component.translatable("density.heroesevolved.need_ground"), true); return false; }
        Vec3 fwd = forward(p.getYRot());
        for (int i = 0; i < 5; i++) {
            Vec3 spot = p.position().add(fwd.scale(2 + i * 1.6)).add(new Vec3(-fwd.z, 0, fwd.x).scale((i % 2 == 0 ? .6 : -.6)));
            BlockPos under = EarthGeometry.ground(p.level(), spot.add(0, 1.5, 0));
            if (under == null) continue;
            Vec3 at = Vec3.atBottomCenterOf(under.above()).add(0, -.8, 0);
            Rock boulder = new Rock(p, p.level().getBlockState(under).isAir() ? ground : p.level().getBlockState(under), Kind.AVALANCHE, at, 1.25F, tier);
            boulder.rolling = true;
            boulder.range = 16;
            boulder.age = -i * 3;
            boulder.vel = fwd.scale(.75);
            boulder.flying = true;
            FLYING.add(boulder);
        }
        ArmPosePayload.show(p, ArmPose.EARTH_SHOVE, 14);
        EarthGeometry.show(p.serverLevel(), Piece.EARTH_AVALANCHE, p.position(), p.position().add(fwd.scale(10)), 1, null, ground);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.2F, .8F);
        CameraShakePayload.sendAround(p.serverLevel(), p.position(), 16, .35F, 12);
        return true;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (var it = HELD.entrySet().iterator(); it.hasNext();) {
            List<Rock> held = it.next().getValue();
            if (held.isEmpty()) { it.remove(); continue; }
            ServerPlayer p = held.get(0).owner;
            if (p.isRemoved() || !p.isAlive() || held.get(0).level != p.level()) { held.forEach(r -> r.display.discard()); it.remove(); continue; }
            for (Rock rock : List.copyOf(held)) {
                rock.age++;
                if (rock.growing && rock.kind == Kind.STONE) {
                    rock.growth = Math.min(1, rock.age / (float) BOULDER_TICKS);
                    rock.size = Mth.lerp(rock.growth, ROCK_SIZE, BOULDER_SIZE);
                    rock.display.resize(rock.size, 2);
                    if (rock.age == BOULDER_TICKS) {
                        rock.level.playSound(null, BlockPos.containing(rock.pos), SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 1.2F, .4F);
                        ArmPosePayload.show(p, ArmPose.EARTH_HEAVE, 10);
                    }
                    if (rock.age % 6 == 0) dust(rock.level, rock.block, rock.pos.subtract(0, rock.size * .5, 0), 3, rock.size * .4);
                }
                if (rock.age >= ROCK_HOVER + (rock.growing ? BOULDER_TICKS : 0)) {
                    // Nobody threw it: it drops where it hangs and breaks harmlessly.
                    held.remove(rock);
                    rock.harmless = true;
                    rock.growing = false;
                    fly(rock, new Vec3(0, -1, 0), .05);
                    continue;
                }
                float k = Mth.clamp(rock.age / (float) ROCK_RISE, 0, 1);
                k = 1 - (1 - k) * (1 - k) * (1 - k);
                Vec3 at = rock.rise.lerp(hoverAt(p, rock, now), k);
                rock.pos = at;
                rock.display.setPos(at);
                rock.display.setYRot(p.getYRot() + rock.slot * 12);
            }
            if (held.isEmpty()) it.remove();
        }
        for (Rock rock : List.copyOf(FLYING)) tickFlying(rock);
        tickGrips(now);
    }

    /** Where a lifted rock hangs: ahead of the chest (discs fanned out), bobbing a little in time with the bender's breath. */
    private static Vec3 hoverAt(ServerPlayer p, Rock rock, long now) {
        return hover(p, 1).add(sideways(p, rock.slot, .7))
                .add(0, (rock.kind == Kind.DISC ? -.15 * Math.abs(rock.slot) : rock.size * .2) + Math.sin((now + rock.slot * 7) * .18) * .04, 0);
    }

    private static void tickFlying(Rock rock) {
        rock.age++;
        if (rock.age < 0) return;
        if (rock.display.isRemoved() || rock.owner.isRemoved() || rock.level != rock.owner.level()) { end(rock); return; }
        rock.flightAge++;
        if (rock.rolling) { roll(rock); return; }
        rock.vel = rock.vel.add(0, -ROCK_GRAVITY, 0);
        Vec3 from = rock.pos, to = from.add(rock.vel);
        double r = rock.size * .5 + .25;
        if (!rock.harmless) {
            LivingEntity hit = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity t : rock.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(r),
                    t -> t != rock.owner && !rock.hit.contains(t.getUUID()) && ExpansionSkills.hostile(rock.owner, t))) {
                var clip = t.getBoundingBox().inflate(r).clip(from, to);
                if (clip.isPresent() && clip.get().distanceToSqr(from) < best) { best = clip.get().distanceToSqr(from); hit = t; }
            }
            if (hit != null) {
                if (rock.kind == Kind.GAUNTLET) { strikeBody(rock, hit); grab(rock, hit); end(rock); return; }
                strikeBody(rock, hit);
                // A boulder rolls on through the first body; everything else breaks on it.
                if (rock.growth < .9F) { shatter(rock, hit.getBoundingBox().getCenter(), rock.vel.normalize().reverse(), false); end(rock); return; }
            }
        }
        BlockHitResult wall = rock.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rock.display));
        if (wall.getType() == HitResult.Type.BLOCK) {
            Vec3 n = Vec3.atLowerCornerOf(wall.getDirection().getNormal());
            shatter(rock, wall.getLocation(), n, true);
            Earthworks.struck(rock.owner, wall.getBlockPos(), rock.growth);
            end(rock);
            return;
        }
        rock.pos = to;
        rock.travelled += rock.vel.length();
        rock.display.setPos(to);
        rock.display.setYRot(rock.flightAge * 17);
        rock.display.setXRot(rock.flightAge * 11);
        if (!rock.harmless) DensityService.bankEarth(rock.owner, rock.vel.length() * .25);
        if (rock.travelled > rock.range || rock.flightAge > 200) { shatter(rock, to, new Vec3(0, 1, 0), false); end(rock); }
    }

    /** An Avalanche boulder: it climbs out of the ground, then rolls on along the terrain, flattening what it meets. */
    private static void roll(Rock rock) {
        if (rock.flightAge <= 6) {
            rock.pos = rock.pos.add(0, (rock.size * .5 + .85) / 6, 0);
            rock.display.setPos(rock.pos);
            if (rock.flightAge == 1) {
                dust(rock.level, rock.block, rock.pos.add(0, .8, 0), 12, .6);
                rock.level.playSound(null, BlockPos.containing(rock.pos), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1, .5F);
            }
            return;
        }
        Vec3 next = rock.pos.add(rock.vel);
        // Look for the ground from a step above the boulder's foot to well below it, so it climbs and drops with the land.
        BlockPos ground = EarthGeometry.ground(rock.level, next.add(0, 1.2 - rock.size * .5, 0), 3.5);
        if (ground == null) { shatter(rock, rock.pos, new Vec3(0, 1, 0), false); end(rock); return; }
        double floor = ground.getY() + 1 + rock.size * .5;
        if (floor - (rock.pos.y) > 1.2) { shatter(rock, rock.pos, rock.vel.normalize().reverse(), true); end(rock); return; }
        next = new Vec3(next.x, Mth.lerp(.5, rock.pos.y, floor), next.z);
        double r = rock.size * .5 + .2;
        for (LivingEntity t : rock.level.getEntitiesOfClass(LivingEntity.class, new AABB(rock.pos, next).inflate(r),
                t -> t != rock.owner && !rock.hit.contains(t.getUUID()) && ExpansionSkills.hostile(rock.owner, t)))
            strikeBody(rock, t);
        rock.pos = next;
        rock.travelled += rock.vel.length();
        rock.display.setPos(next);
        rock.display.setYRot((float) Math.toDegrees(Math.atan2(-rock.vel.x, rock.vel.z)));
        rock.display.setXRot(rock.flightAge * 24 % 360);
        if (rock.flightAge % 3 == 0) dust(rock.level, rock.block, next.subtract(0, rock.size * .5, 0), 3, .4);
        if (rock.flightAge % 5 == 0)
            rock.level.playSound(null, BlockPos.containing(next), SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, 1, .5F);
        DensityService.bankEarth(rock.owner, rock.vel.length() * .25);
        if (rock.travelled > rock.range) { shatter(rock, next, new Vec3(0, 1, 0), false); end(rock); }
    }

    private static void end(Rock rock) {
        FLYING.remove(rock);
        if (!rock.display.isRemoved()) rock.display.discard();
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    static void forget(ServerPlayer p) {
        List<Rock> held = HELD.remove(p.getUUID());
        if (held != null) held.forEach(r -> r.display.discard());
        for (Rock rock : List.copyOf(FLYING)) if (rock.owner == p) end(rock);
        GRIPS.values().removeIf(g -> g.owner == p);
    }

    static void clear() {
        HELD.values().forEach(list -> list.forEach(r -> r.display.discard()));
        FLYING.forEach(r -> r.display.discard());
        HELD.clear(); FLYING.clear(); GRIPS.clear();
    }
}
