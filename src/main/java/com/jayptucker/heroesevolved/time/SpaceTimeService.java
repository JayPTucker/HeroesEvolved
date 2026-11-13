package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bounded personal history and selective temporal effects. Never changes the server clock. */
public final class SpaceTimeService {
    public record Anchor(boolean set, String dimension, double x, double y, double z, float yaw, float pitch) {
        public static final Codec<Anchor> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("set").forGetter(Anchor::set), Codec.STRING.fieldOf("dimension").forGetter(Anchor::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(Anchor::x), Codec.DOUBLE.fieldOf("y").forGetter(Anchor::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Anchor::z), Codec.FLOAT.fieldOf("yaw").forGetter(Anchor::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(Anchor::pitch)).apply(i, Anchor::new));
        public static Anchor empty() { return new Anchor(false, "", 0, 0, 0, 0, 0); }
        public Vec3 position() { return new Vec3(x, y, z); }
        public static Anchor capture(ServerPlayer p) { return new Anchor(true, p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()); }
    }
    private record Moment(long tick, Anchor place, float health, int fire) {}
    private record Stasis(ServerPlayer owner, LivingEntity target, Vec3 position, long until) {}
    private static final Map<UUID, Deque<Moment>> HISTORY = new HashMap<>();
    private static final Map<UUID, Stasis> STASIS = new HashMap<>();
    private SpaceTimeService() {}

    public static boolean awakened(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(e -> e.getKey().equals(ModAbilities.TIME_MANIPULATION_ID) && e.getValue().isUnlocked()).isPresent();
    }
    private static LivingEntity aimed(ServerPlayer p) {
        return Targeting.livingEntityInSight(p, 20, t -> !p.isAlliedTo(t)
                && !(t instanceof com.jayptucker.heroesevolved.entity.TemporalEchoEntity)
                && !TemporalEntityEchoService.isReplaying(t)
                && (!(t instanceof ServerPlayer other) || p.getServer().isPvpAllowed() && p.canHarmPlayer(other)));
    }
    public static boolean canUse(AbilityUseContext c, int branch) {
        if (c.player().isPassenger() || TemporalSnapshotService.isTemporalDimension(c.player().serverLevel())) return false;
        return switch (branch) {
            case 0 -> aimed(c.player()) != null;
            case 1 -> c.modifierHeld() || c.player().getData(ModDataAttachments.SPACE_TIME_ANCHOR).set();
            case 2 -> personalTarget(c.player()) != null;
            default -> false;
        };
    }
    public static boolean activate(AbilityUseContext c, int branch) {
        boolean done = switch (branch) {
            case 0 -> suspend(c.player(), aimed(c.player()), 60 + c.abilityLevel() * 10);
            case 1 -> c.modifierHeld() ? remember(c.player()) : recall(c.player());
            case 2 -> secondChance(c.player());
            default -> false;
        };
        if (done) com.jayptucker.heroesevolved.progression.MasteryService.markPowerUse(c.player(), ModAbilities.TIME_MANIPULATION_ID);
        return done;
    }
    public static boolean suspend(ServerPlayer p, LivingEntity target, int ticks) {
        if (target == null || !target.isAlive() || target.level() != p.level()) return false;
        // One target per traveller; replacing it immediately releases the previous target, and its stored blows land.
        Stasis old = STASIS.put(p.getUUID(), new Stasis(p, target, target.position(), now(p) + Math.clamp(ticks, 1, 120)));
        if (old != null && old.target != target) TimeStopService.release(p.getServer(), p.getUUID(), old.target);
        // A cold violet clock closes round them with its hands stopped; the palm that stopped it closes into a fist.
        TimeGeometry.show(p.serverLevel(), com.jayptucker.heroesevolved.styles.Piece.TIME_STASIS, target.position(), target.getEyePosition(), target.getBbHeight(), target);
        com.jayptucker.heroesevolved.network.ArmPosePayload.show(p, com.jayptucker.heroesevolved.ability.ArmPose.TIME_HALT, 14);
        p.serverLevel().playSound(null, target.blockPosition(), ModSounds.TIME_SLOW_STOP.get(), SoundSource.PLAYERS, .6F, 1.5F);
        cue(p);
        return true;
    }
    private static boolean valid(Stasis s) {
        return s.target.level() == s.owner.level() && s.until > now(s.owner) && s.owner.isAlive() && awakened(s.owner);
    }
    /** Who holds this creature in stopped time (Selective Stasis, a Stasis Seal, Moment's Edge), or null. */
    public static java.util.UUID holder(Entity entity) {
        if (!STASIS.isEmpty()) for (Stasis s : STASIS.values()) if (s.target == entity && valid(s)) return s.owner.getUUID();
        return SpaceTimeSkills.holder(entity);
    }
    public static boolean isSuspended(Entity entity) {
        return holder(entity) != null;
    }
    /** Selective Stasis let go early with a snap of the fingers: its stored blows land at once. Free. */
    public static boolean releaseStasis(ServerPlayer p) {
        Stasis s = STASIS.remove(p.getUUID());
        if (s == null) return false;
        TimeStopService.release(p.getServer(), p.getUUID(), s.target);
        com.jayptucker.heroesevolved.network.ArmPosePayload.show(p, com.jayptucker.heroesevolved.ability.ArmPose.TIME_SNAP, 10);
        p.serverLevel().playSound(null, s.target.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, .8F, 1.3F);
        return true;
    }
    /** Lets this one creature out of your Selective Stasis, if it is the one you hold. */
    public static boolean releaseTarget(ServerPlayer p, LivingEntity target) {
        Stasis s = STASIS.get(p.getUUID());
        if (s == null || s.target != target) return false;
        STASIS.remove(p.getUUID());
        TimeStopService.release(p.getServer(), p.getUUID(), target);
        return true;
    }
    public static boolean remember(ServerPlayer p) {
        if (!safe(p, p.position()) || !p.onGround()) return message(p, "anchor_ground");
        p.setData(ModDataAttachments.SPACE_TIME_ANCHOR, Anchor.capture(p));
        // A palm pressed to the ground plants a gold clock face there, its hands set to the hour.
        plant(p, p.position());
        com.jayptucker.heroesevolved.network.ArmPosePayload.show(p, com.jayptucker.heroesevolved.ability.ArmPose.TIME_PLANT, 16);
        cue(p); message(p, "anchor_set");
        TemporalStoryService.reach(p, TemporalStoryService.ANCHOR);
        return true;
    }
    public static boolean recall(ServerPlayer p) {
        Anchor a = p.getData(ModDataAttachments.SPACE_TIME_ANCHOR);
        if (!a.set() || !a.dimension.equals(p.level().dimension().location().toString())) return message(p, "anchor_dimension");
        // Fixed budget: no chunk generation and no tickets retained by travel.
        if (p.position().distanceToSqr(a.position()) > 512 * 512 || !safe(p, a.position())) return message(p, "anchor_blocked");
        // Friends beside you (teammates, or players crouching to be taken) come back with you.
        Vec3 from = p.position();
        List<LivingEntity> friends = p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(TimeGeometry.ANCHOR_CARRY),
                t -> t != p && t.isAlive() && !t.isSpectator() && !t.isPassenger() && t.distanceToSqr(p) <= TimeGeometry.ANCHOR_CARRY * TimeGeometry.ANCHOR_CARRY
                        && !(t instanceof com.jayptucker.heroesevolved.entity.TemporalEchoEntity)
                        && (t instanceof ServerPlayer other ? p.isAlliedTo(other) || other.isShiftKeyDown()
                        : t instanceof net.minecraft.world.entity.OwnableEntity pet && p.getUUID().equals(pet.getOwnerUUID())))
                .stream().limit(3).toList();
        com.jayptucker.heroesevolved.network.ArmPosePayload.show(p, com.jayptucker.heroesevolved.ability.ArmPose.TIME_RECALL, 16);
        travel(p, a); TemporalStoryService.reach(p, TemporalStoryService.ANCHOR);
        int brought = 0;
        for (LivingEntity friend : friends) {
            Vec3 to = a.position().add(friend.position().subtract(from).multiply(1, 0, 1));
            if (!safe(friend, to)) to = a.position().add(Math.cos(brought * 2.1) * 1.2, 0, Math.sin(brought * 2.1) * 1.2);
            if (!safe(friend, to)) continue;
            AbilityEffects.send(p.serverLevel(), Kind.TIME_FOLD, friend.position(), to, .8);
            friend.teleportTo(to.x, to.y, to.z);
            friend.setDeltaMovement(Vec3.ZERO); friend.resetFallDistance(); friend.hurtMarked = true;
            if (friend instanceof ServerPlayer other) other.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(other));
            brought++;
        }
        if (brought > 0) TemporalStoryService.reach(p, TemporalStoryService.RESCUE);
        plant(p, a.position());
        return true;
    }
    /** The anchor's clock face, its hands showing the hour of the day (carried in the piece's scale). */
    private static void plant(ServerPlayer p, Vec3 at) {
        float hour = (p.serverLevel().getDayTime() + 6000) % 24000 / 1000F;
        TimeGeometry.show(p.serverLevel(), com.jayptucker.heroesevolved.styles.Piece.TIME_ANCHOR, at, at.add(0, 1, 0), 1 + hour, null);
    }
    public static boolean safe(Entity entity, Vec3 position) {
        if (!(entity.level() instanceof ServerLevel level) || !Double.isFinite(position.lengthSqr())) return false;
        var box = entity.getBoundingBox().move(position.subtract(entity.position()));
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty() || level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) return false;
        }
        return level.noCollision(entity, box);
    }
    public static void travel(ServerPlayer p, Anchor a) {
        Vec3 from = p.position();
        p.teleportTo(p.serverLevel(), a.x, a.y, a.z, a.yaw, a.pitch);
        p.setDeltaMovement(Vec3.ZERO); p.resetFallDistance();
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
        AbilityEffects.send(p.serverLevel(), Kind.TEMPORAL_BURST, from, from.add(0, 1, 0), 2);
        AbilityEffects.at(p, Kind.TEMPORAL_BURST, 2); cue(p);
    }
    /** Every player's last five seconds are kept (25 samples at most): Shared Hour can take a friend back through theirs. */
    public static void record(ServerPlayer p, long tick) {
        if (!p.isAlive() || p.isSpectator() || TemporalSnapshotService.isTemporalDimension(p.serverLevel())
                || com.jayptucker.heroesevolved.time.intro.TimeIntroService.isVisiting(p)) { HISTORY.remove(p.getUUID()); return; }
        var history = HISTORY.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        history.addLast(new Moment(tick, Anchor.capture(p), p.getHealth(), p.getRemainingFireTicks()));
        while (history.size() > 25 || !history.isEmpty() && history.getFirst().tick < tick - 120) history.removeFirst();
    }
    private static Moment personalTarget(ServerPlayer p) {
        var history = HISTORY.get(p.getUUID()); if (history == null || history.isEmpty()) return null;
        long cutoff = now(p) - 100;
        if (history.getLast().tick < now(p) - 5) return null;
        return history.stream().filter(m -> m.tick <= cutoff && m.tick >= cutoff - 5
                && m.place.dimension.equals(p.level().dimension().location().toString())).reduce((a, b) -> b).orElse(null);
    }
    public static boolean secondChance(ServerPlayer p) {
        Moment m = personalTarget(p);
        if (m == null) return message(p, "history");
        if (!safe(p, m.place.position())) return message(p, "anchor_blocked");
        // A hand at the chest turns a crown backward; violet trails run back to then, where rings close in on you.
        com.jayptucker.heroesevolved.network.ArmPosePayload.show(p, com.jayptucker.heroesevolved.ability.ArmPose.TIME_WIND, 14);
        SpaceTimeSkills.remember(p, TimeGeometry.SECOND_TICKS);
        SpaceTimeSkills.sharedHour(p);
        Vec3 from = p.position(); travel(p, m.place);
        p.setHealth(Math.min(p.getMaxHealth(), Math.max(p.getHealth(), m.health)));
        p.setRemainingFireTicks(Math.min(p.getRemainingFireTicks(), m.fire));
        AbilityEffects.send(p.serverLevel(), Kind.TEMPORAL_REWIND, from, m.place.position(), 5);
        TimeGeometry.show(p.serverLevel(), com.jayptucker.heroesevolved.styles.Piece.TIME_SECOND, m.place.position(), from, 1, p);
        HISTORY.remove(p.getUUID());
        return true;
    }
    /** Shared Hour: one friend back through their own five seconds (where they stood, lost health, fire). */
    static boolean rewindPersonal(ServerPlayer friend) {
        Moment m = personalTarget(friend);
        if (m == null || !safe(friend, m.place.position())) return false;
        Vec3 from = friend.position();
        travel(friend, m.place);
        friend.setHealth(Math.min(friend.getMaxHealth(), Math.max(friend.getHealth(), m.health)));
        friend.setRemainingFireTicks(Math.min(friend.getRemainingFireTicks(), m.fire));
        AbilityEffects.send(friend.serverLevel(), Kind.TEMPORAL_REWIND, from, m.place.position(), 3);
        TimeGeometry.show(friend.serverLevel(), com.jayptucker.heroesevolved.styles.Piece.TIME_SECOND, m.place.position(), from, 1, friend);
        HISTORY.remove(friend.getUUID());
        return true;
    }
    public static void tick(MinecraftServer server) {
        long tick = server.overworld().getGameTime();
        if (tick % 5 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) record(p, tick);
        List<Stasis> ended = new ArrayList<>();
        for (var it = STASIS.values().iterator(); it.hasNext(); ) {
            Stasis s = it.next();
            if (tick >= s.until || !s.target.isAlive() || s.target.isRemoved()
                    || s.target.level() != s.owner.level() || !awakened(s.owner) || !PowerRules.canAct(s.owner)) {
                it.remove();
                ended.add(s);
            }
        }
        // Time resumes for each, and every blow it was dealt while stopped lands together (after the loop: a blow can kill).
        for (Stasis s : ended) TimeStopService.release(server, s.owner.getUUID(), s.target);
        if (tick % 10 == 0) for (Stasis s : STASIS.values())
            TimeGeometry.show(s.owner.serverLevel(), com.jayptucker.heroesevolved.styles.Piece.TIME_STASIS, s.target.position(),
                    s.target.getEyePosition(), s.target.getBbHeight(), s.target);
    }
    public static void forget(ServerPlayer p) {
        HISTORY.remove(p.getUUID());
        Stasis own = STASIS.remove(p.getUUID());
        if (own != null && p.getServer() != null) TimeStopService.release(p.getServer(), p.getUUID(), own.target);
        STASIS.values().removeIf(s -> s.target == p);
        SpaceTimeSkills.drop(p);
    }
    public static void clear() { HISTORY.clear(); STASIS.clear(); SpaceTimeSkills.clear(); }
    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    private static void cue(ServerPlayer p) { p.serverLevel().playSound(null, p.blockPosition(), ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, .7F, 1); }
    private static boolean message(ServerPlayer p, String key) { p.displayClientMessage(Component.translatable("time.heroesevolved." + key), true); return false; }
}
