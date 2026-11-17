package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rift Beast (Vibe's signature): a breach opens beside the viber and a beast from a hostile world comes through to fight
 * for them for half a minute. It behaves the way a tamed wolf does: it follows its owner, attacks whatever the owner hits
 * and whatever hits the owner (or is hunting them), never turns on the owner or their friends, and when it falls too far
 * behind it breaches back to their side. A second press sends it at whatever the viber looks at; Shift sends it home.
 *
 * <p>The body is a Nether hoglin that can't zombify. Its brain still runs (charging, tossing), and this class steers it
 * through its memories every tick: {@code ATTACK_TARGET} for whom to fight and {@code WALK_TARGET} for heeling. It is
 * tagged in its persistent data, drops nothing, and a tagged body that loads without an owner (after a restart) is
 * turned away.</p>
 */
public final class VibeBeast {
    static final String TAG = "heroesevolved_rift_beast";
    static final int LIFETIME = 500, PER_TIER = 100, MEMORY = 100, COMMAND = 200, PIECE_EVERY = 10;
    static final double HEEL = 7, LEASH = 22, REACH = 32, COMMAND_REACH = 24;

    private static final class Beast {
        final ServerPlayer owner;
        final Hoglin body;
        final long until;
        @Nullable UUID commanded;
        long commandedUntil;

        Beast(ServerPlayer owner, Hoglin body, long until) {
            this.owner = owner;
            this.body = body;
            this.until = until;
        }
    }

    private static final Map<UUID, Beast> BEASTS = new HashMap<>();
    /** Bodies this server put in the world; a tagged body not in here is an orphan from before a restart. */
    private static final Set<UUID> LIVE = new HashSet<>();

    private VibeBeast() {}

    private static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.jayptucker.heroesevolved.HeroesEvolved.MOD_ID, path);
    }

    public static boolean alive(ServerPlayer owner) {
        Beast beast = BEASTS.get(owner.getUUID());
        return beast != null && beast.body.isAlive();
    }

    static @Nullable Hoglin body(ServerPlayer owner) {
        Beast beast = BEASTS.get(owner.getUUID());
        return beast == null ? null : beast.body;
    }

    public static boolean summon(AbilityUseContext context) {
        ServerPlayer owner = context.player();
        if (!VibeService.ready(owner) || alive(owner)) return false;
        if (!VibeBreaches.travelWorld(owner.serverLevel())) return VibeBreaches.fail(owner, "restricted");
        ServerLevel level = owner.serverLevel();
        Vec3 forward = StyleHits.level(owner), right = StyleHits.right(forward);
        Vec3 spot = null;
        for (Vec3 offset : List.of(forward.scale(2.2).add(right.scale(1.4)), forward.scale(2.2).subtract(right.scale(1.4)),
                forward.scale(2.6), right.scale(2), right.scale(-2))) {
            Vec3 ground = StyleOps.ground(level, owner.position().add(offset).add(0, 1, 0));
            if (Math.abs(ground.y - owner.getY()) > 2.5) continue;
            AABB box = EntityType.HOGLIN.getDimensions().makeBoundingBox(ground);
            if (level.noCollision(box) && level.getBlockState(net.minecraft.core.BlockPos.containing(ground.subtract(0, .05, 0)))
                    .isFaceSturdy(level, net.minecraft.core.BlockPos.containing(ground.subtract(0, .05, 0)), net.minecraft.core.Direction.UP)) {
                spot = ground;
                break;
            }
        }
        if (spot == null) return VibeBreaches.fail(owner, "no_room_beast");
        Hoglin body = EntityType.HOGLIN.create(level);
        if (body == null) return false;
        int tier = Math.max(1, context.abilityLevel());
        body.setImmuneToZombification(true);
        body.setPersistenceRequired();
        body.moveTo(spot.x, spot.y, spot.z, VibeGeometry.yaw(forward), 0);
        body.setYHeadRot(VibeGeometry.yaw(forward));
        body.getPersistentData().putUUID(TAG, owner.getUUID());
        body.setCustomName(Component.translatable("entity.heroesevolved.rift_beast"));
        var health = body.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.addPermanentModifier(new AttributeModifier(id("rift_beast_health"), 6 * tier, AttributeModifier.Operation.ADD_VALUE));
        var attack = body.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.addPermanentModifier(new AttributeModifier(id("rift_beast_attack"), tier, AttributeModifier.Operation.ADD_VALUE));
        body.setHealth(body.getMaxHealth());
        LIVE.add(body.getUUID());
        if (!level.addFreshEntity(body)) {
            LIVE.remove(body.getUUID());
            return false;
        }
        long now = level.getGameTime();
        BEASTS.put(owner.getUUID(), new Beast(owner, body, now + LIFETIME + (long) PER_TIER * tier));
        // The breach it steps out of stands just behind it.
        Vec3 mouth = spot.subtract(forward.scale(1.1)).add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        SetPiecePayload.send(level, Piece.VIBE_BREACH_ARRIVE, mouth, mouth.add(forward), 1, null);
        SetPiecePayload.send(level, Piece.VIBE_BEAST, body.position(), body.position().add(forward), 1, body);
        ArmPosePayload.show(owner, ArmPose.VIBE_OPEN, 18);
        StyleOps.sound(level, mouth, ModSounds.VIBE_BREACH.get(), .9F, .8F);
        StyleOps.sound(level, spot, SoundEvents.HOGLIN_ANGRY, 1, .7F);
        return true;
    }

    /** The second press: send the beast at what the viber looks at, or back to heel if nothing. Never Shift. */
    public static boolean command(AbilityUseContext context) {
        ServerPlayer owner = context.player();
        if (context.modifierHeld() || !alive(owner)) return false;
        Beast beast = BEASTS.get(owner.getUUID());
        LivingEntity target = StyleHits.aimed(owner, COMMAND_REACH, .7);
        ArmPosePayload.show(owner, ArmPose.VIBE_LANCE, 12);
        if (target == null || !valid(beast, target)) {
            beast.commanded = null;
            beast.body.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            owner.displayClientMessage(Component.translatable("vibe.heroesevolved.beast_heel"), true);
            return true;
        }
        beast.commanded = target.getUUID();
        beast.commandedUntil = owner.serverLevel().getGameTime() + COMMAND;
        Vec3 at = target.getBoundingBox().getCenter();
        SetPiecePayload.send(owner.serverLevel(), Piece.VIBE_HIT, at, at.add(0, 1, 0), .8F, target);
        StyleOps.sound(owner.serverLevel(), beast.body.position(), SoundEvents.HOGLIN_ANGRY, 1, .9F);
        return true;
    }

    /** Shift-press, the end of its time, or its owner gone: it goes back through a breach. */
    public static boolean dismiss(ServerPlayer owner) {
        Beast beast = BEASTS.remove(owner.getUUID());
        if (beast == null) return false;
        send(beast);
        return true;
    }

    private static void send(Beast beast) {
        Hoglin body = beast.body;
        LIVE.remove(body.getUUID());
        if (body.isRemoved()) return;
        if (body.level() instanceof ServerLevel level && body.isAlive()) {
            Vec3 facing = Vec3.directionFromRotation(0, body.getYRot());
            Vec3 mouth = body.position().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
            SetPiecePayload.send(level, Piece.VIBE_BREACH_CLOSE, mouth, mouth.add(facing), 1, null);
            StyleOps.sound(level, mouth, ModSounds.VIBE_SHUT.get(), .8F, .9F);
        }
        body.discard();
    }

    /** May the beast fight this creature? Never its owner, their friends, itself or another rift beast. */
    static boolean valid(Beast beast, @Nullable LivingEntity target) {
        return target != null && target.isAlive() && target != beast.owner && target != beast.body
                && target.level() == beast.body.level() && !isBeast(target)
                && !StyleOps.isFriend(beast.owner, target) && VibeService.hostile(beast.owner, target)
                && target.distanceToSqr(beast.body) < REACH * REACH;
    }

    public static boolean isBeast(Entity entity) {
        return entity.getPersistentData().hasUUID(TAG);
    }

    /** Whom the beast should fight now, in a wolf's order: a command, the owner's victim, the owner's attacker, a hunter. */
    static @Nullable LivingEntity choose(Beast beast, long now) {
        ServerPlayer owner = beast.owner;
        if (beast.commanded != null && now < beast.commandedUntil
                && owner.serverLevel().getEntity(beast.commanded) instanceof LivingEntity commanded && valid(beast, commanded))
            return commanded;
        LivingEntity hit = owner.getLastHurtMob();
        if (owner.tickCount - owner.getLastHurtMobTimestamp() <= MEMORY && valid(beast, hit)) return hit;
        LivingEntity hurt = owner.getLastHurtByMob();
        if (owner.tickCount - owner.getLastHurtByMobTimestamp() <= MEMORY && valid(beast, hurt)) return hurt;
        for (Mob hunter : owner.serverLevel().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12),
                mob -> mob.getTarget() == owner))
            if (valid(beast, hunter)) return hunter;
        return null;
    }

    static void tick(MinecraftServer server) {
        for (Beast beast : List.copyOf(BEASTS.values())) {
            ServerPlayer owner = beast.owner;
            Hoglin body = beast.body;
            if (owner.server != server) continue;
            long now = body.level().getGameTime();
            if (!body.isAlive() || body.isRemoved() || owner.isRemoved() || !VibeService.active(owner)
                    || owner.level() != body.level() || now >= beast.until) {
                BEASTS.remove(owner.getUUID());
                send(beast);
                continue;
            }
            steer(beast, now);
            if (now % PIECE_EVERY == 0)
                SetPiecePayload.send((ServerLevel) body.level(), Piece.VIBE_BEAST, body.position(),
                        body.position().add(Vec3.directionFromRotation(0, body.getYRot())), 1, body);
        }
    }

    /** One tick of a wolf's mind, written into the hoglin's brain. Package-visible so tests can step it. */
    static void steer(Beast beast, long now) {
        Hoglin body = beast.body;
        ServerPlayer owner = beast.owner;
        var brain = body.getBrain();
        LivingEntity target = choose(beast, now);
        if (target != null) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
            brain.eraseMemory(MemoryModuleType.AVOID_TARGET);
            return;
        }
        LivingEntity current = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (current != null && !valid(beast, current)) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        double distance = body.distanceTo(owner);
        if (distance > LEASH) heel(beast);
        else if (distance > HEEL && !brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET))
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(owner, 1.1F, 3));
    }

    /** Left too far behind: it breaches back to its owner's side, as a wolf teleports to its master. */
    private static void heel(Beast beast) {
        Hoglin body = beast.body;
        ServerPlayer owner = beast.owner;
        ServerLevel level = owner.serverLevel();
        Vec3 behind = owner.position().subtract(StyleHits.level(owner).scale(2.5));
        Vec3 ground = StyleOps.ground(level, behind.add(0, 1, 0));
        if (!level.noCollision(body, body.getDimensions(body.getPose()).makeBoundingBox(ground))) return;
        Vec3 from = body.position().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        SetPiecePayload.send(level, Piece.VIBE_BREACH_CLOSE, from, from.add(Vec3.directionFromRotation(0, body.getYRot())), 1, null);
        body.teleportTo(ground.x, ground.y, ground.z);
        body.getNavigation().stop();
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        Vec3 to = ground.add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        SetPiecePayload.send(level, Piece.VIBE_BREACH_ARRIVE, to, to.add(StyleHits.level(owner)), 1, null);
        StyleOps.sound(level, to, ModSounds.VIBE_SHUT.get(), .6F, 1.2F);
    }

    /** Never let a rift beast set its sights on its owner or their friends (its own brain wants the nearest player). */
    public static boolean refuses(Entity beast, @Nullable LivingEntity target) {
        if (target == null || !isBeast(beast)) return false;
        for (Beast b : BEASTS.values())
            if (b.body == beast) return !valid(b, target);
        return true;
    }

    /** A tagged body loading without a living owner here (a restart, a reload) is turned away. */
    public static boolean orphan(Entity entity) {
        return isBeast(entity) && !LIVE.contains(entity.getUUID());
    }

    /** Package-visible for tests: the beast's time left and the target its brain holds. */
    static @Nullable LivingEntity fighting(ServerPlayer owner) {
        Hoglin body = body(owner);
        return body == null ? null : body.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    static void step(ServerPlayer owner) {
        Beast beast = BEASTS.get(owner.getUUID());
        if (beast != null) steer(beast, beast.body.level().getGameTime());
    }

    static void forget(ServerPlayer owner) { dismiss(owner); }

    static void clear() {
        for (Beast beast : List.copyOf(BEASTS.values())) send(beast);
        BEASTS.clear();
        LIVE.clear();
    }
}
