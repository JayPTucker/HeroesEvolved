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
        return switch (branch) {
            case 0 -> suspend(c.player(), aimed(c.player()), 60 + c.abilityLevel() * 10);
            case 1 -> c.modifierHeld() ? remember(c.player()) : recall(c.player());
            case 2 -> secondChance(c.player());
            default -> false;
        };
    }
    public static boolean suspend(ServerPlayer p, LivingEntity target, int ticks) {
        if (target == null || !target.isAlive() || target.level() != p.level()) return false;
        // One target per traveller; replacing it immediately releases the previous target.
        STASIS.put(p.getUUID(), new Stasis(p, target, target.position(), now(p) + Math.clamp(ticks, 1, 120)));
        AbilityEffects.send(p.serverLevel(), Kind.TEMPORAL_FIELD, target.position(), target.getEyePosition(), 1.5);
        cue(p);
        return true;
    }
    public static boolean isSuspended(Entity entity) {
        return STASIS.values().stream().anyMatch(s -> s.target == entity && s.target.level() == s.owner.level()
                && s.until > now(s.owner) && s.owner.isAlive() && awakened(s.owner));
    }
    public static boolean remember(ServerPlayer p) {
        if (!safe(p, p.position()) || !p.onGround()) return message(p, "anchor_ground");
        p.setData(ModDataAttachments.SPACE_TIME_ANCHOR, Anchor.capture(p));
        AbilityEffects.at(p, Kind.TEMPORAL_FIELD, 2);
        cue(p); message(p, "anchor_set");
        TemporalStoryService.reach(p, TemporalStoryService.ANCHOR);
        return true;
    }
    public static boolean recall(ServerPlayer p) {
        Anchor a = p.getData(ModDataAttachments.SPACE_TIME_ANCHOR);
        if (!a.set() || !a.dimension.equals(p.level().dimension().location().toString())) return message(p, "anchor_dimension");
        // Fixed budget: no chunk generation and no tickets retained by travel.
        if (p.position().distanceToSqr(a.position()) > 512 * 512 || !safe(p, a.position())) return message(p, "anchor_blocked");
        travel(p, a); TemporalStoryService.reach(p, TemporalStoryService.ANCHOR);
        return true;
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
    public static void record(ServerPlayer p, long tick) {
        if (!awakened(p) || !p.isAlive() || TemporalSnapshotService.isTemporalDimension(p.serverLevel())
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
        Vec3 from = p.position(); travel(p, m.place);
        p.setHealth(Math.min(p.getMaxHealth(), Math.max(p.getHealth(), m.health)));
        p.setRemainingFireTicks(Math.min(p.getRemainingFireTicks(), m.fire));
        AbilityEffects.send(p.serverLevel(), Kind.TEMPORAL_REWIND, from, m.place.position(), 5);
        HISTORY.remove(p.getUUID());
        return true;
    }
    public static void tick(MinecraftServer server) {
        long tick = server.overworld().getGameTime();
        if (tick % 5 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) record(p, tick);
        STASIS.values().removeIf(s -> tick >= s.until || !s.target.isAlive() || s.target.isRemoved()
                || s.target.level() != s.owner.level() || !awakened(s.owner) || !PowerRules.canAct(s.owner));
        if (tick % 20 == 0) for (Stasis s : STASIS.values())
            AbilityEffects.send(s.owner.serverLevel(), Kind.TEMPORAL_FIELD, s.position, s.position.add(0, 1, 0), 1.5);
    }
    public static void forget(ServerPlayer p) { HISTORY.remove(p.getUUID()); STASIS.values().removeIf(s -> s.owner == p || s.target == p); }
    public static void clear() { HISTORY.clear(); STASIS.clear(); }
    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    private static void cue(ServerPlayer p) { p.serverLevel().playSound(null, p.blockPosition(), ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, .7F, 1); }
    private static boolean message(ServerPlayer p, String key) { p.displayClientMessage(Component.translatable("time.heroesevolved." + key), true); return false; }
}
