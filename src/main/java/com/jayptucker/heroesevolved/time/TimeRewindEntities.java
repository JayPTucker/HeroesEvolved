package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Physical entity/inventory history. Power ownership, Mastery and cooldowns are never rolled back. */
public final class TimeRewindEntities {
    public static final int MAX_ENTITIES = 2048;
    private record State(UUID id, CompoundTag tag, Vec3 position, UUID vehicle) {}
    public record Frame(Map<UUID, State> states, Vec3 center, boolean complete) {}
    private static final Map<UUID, Map<UUID, Long>> BIRTHS = new HashMap<>();
    private static final Map<UUID, Frame> PREVIOUS = new HashMap<>();
    private static boolean restoring;
    private TimeRewindEntities() {}

    public static Frame capture(ServerPlayer caster) {
        double radius = TimeRewindBlocks.RECORDING_RADIUS + 24;
        var entities = new ArrayList<>(caster.serverLevel().getEntities((Entity) null, new AABB(caster.position(), caster.position()).inflate(radius),
                entity -> entity.isAlive() && !entity.isSpectator()));
        // The caster might not be in entity lookup during login or a controlled server test.
        if (!entities.contains(caster)) entities.add(caster);
        if (entities.size() > MAX_ENTITIES) return new Frame(Map.of(), caster.position(), false);
        Map<UUID, State> states = new HashMap<>();
        Frame previous = PREVIOUS.get(caster.getUUID());
        for (Entity entity : entities) {
            CompoundTag tag = entity.saveWithoutId(new CompoundTag());
            tag.putString("id", EntityType.getKey(entity.getType()).toString());
            tag.remove("Passengers");
            if (entity instanceof ServerPlayer player)
                tag.putInt("HeroesRadiationStress", player.getData(ModDataAttachments.PLAYER_RADIATION_STRESS));
            State last = previous == null ? null : previous.states.get(entity.getUUID());
            if (last != null && last.tag.equals(tag)) tag = last.tag; // Stationary entities share immutable NBT between frames.
            states.put(entity.getUUID(), new State(entity.getUUID(), tag, entity.position(),
                    entity.getVehicle() == null ? null : entity.getVehicle().getUUID()));
        }
        Frame frame = new Frame(Map.copyOf(states), caster.position(), true);
        PREVIOUS.put(caster.getUUID(), frame);
        BIRTHS.computeIfAbsent(caster.getUUID(), ignored -> new HashMap<>());
        return frame;
    }
    public static void born(Entity entity, boolean loadedFromDisk) {
        if (restoring || loadedFromDisk || entity instanceof ServerPlayer || !(entity.level() instanceof ServerLevel level)) return;
        long now = level.getServer().overworld().getGameTime();
        for (ServerPlayer caster : level.players()) {
            Map<UUID, Long> births = BIRTHS.get(caster.getUUID());
            if (births != null && entity.distanceToSqr(caster) <= TimeRewindBlocks.RECORDING_RADIUS * TimeRewindBlocks.RECORDING_RADIUS)
                births.put(entity.getUUID(), now);
        }
    }
    private static Entity find(ServerLevel level, UUID id) {
        for (ServerLevel world : level.getServer().getAllLevels()) {
            Entity entity = world.getEntity(id); if (entity != null) return entity;
        }
        return level.getServer().getPlayerList().getPlayer(id);
    }
    public static void restore(ServerPlayer caster, Frame frame, long cutoff) {
        if (!frame.complete) throw new IllegalStateException("Entity history is incomplete");
        ServerLevel level = caster.serverLevel();
        double radius = TimeRewindBlocks.RECORDING_RADIUS, radiusSqr = radius * radius;
        restoring = true;
        try {
            for (var birth : BIRTHS.getOrDefault(caster.getUUID(), Map.of()).entrySet()) {
                if (birth.getValue() > cutoff && !frame.states.containsKey(birth.getKey())) {
                    Entity entity = find(level, birth.getKey());
                    if (entity != null && !(entity instanceof ServerPlayer)) entity.discard();
                }
            }
            Map<UUID, Entity> restored = new HashMap<>();
            for (State state : frame.states.values()) {
                Entity current = state.id.equals(caster.getUUID()) ? caster : find(level, state.id);
                boolean inside = state.position.distanceToSqr(frame.center) <= radiusSqr
                        || current != null && current.level() == level && current.position().distanceToSqr(caster.position()) <= radiusSqr;
                if (!inside) continue;
                if (current instanceof ServerPlayer player) {
                    if (!player.isAlive()) player = level.getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
                    restorePlayer(player, level, state.tag);
                    restored.put(state.id, player); continue;
                }
                // Offline players cannot be recreated as NPCs or have their account duplicated.
                if (state.tag.getString("id").equals("minecraft:player")) {
                    PendingPlayerRewinds.get(level.getServer()).put(state.id, level.dimension().location(), state.tag);
                    continue;
                }
                if (current != null && (current.level() != level || current instanceof LivingEntity && !current.isAlive())) {
                    current.discard(); current = null;
                }
                if (current == null || current.isRemoved()) {
                    Entity entity = EntityType.loadEntityRecursive(state.tag.copy(), level, value -> value);
                    if (entity != null && level.addFreshEntity(entity)) current = entity;
                } else {
                    com.jayptucker.heroesevolved.cryo.CryoService.releaseEntity(current);
                    current.stopRiding(); current.load(state.tag.copy());
                }
                if (current != null) {
                    current.teleportTo(state.position.x, state.position.y, state.position.z);
                    current.hurtMarked = true; restored.put(state.id, current);
                }
            }
            for (State state : frame.states.values()) {
                Entity rider = restored.get(state.id), vehicle = state.vehicle == null ? null : restored.get(state.vehicle);
                if (rider != null && vehicle != null) rider.startRiding(vehicle, true);
            }
        } finally { restoring = false; }
    }
    public static void restorePlayer(ServerPlayer player, ServerLevel level, CompoundTag tag) {
        com.jayptucker.heroesevolved.cryo.CryoService.releaseEntity(player);
        player.stopRiding();
        var pos = tag.getList("Pos", 6); var rotation = tag.getList("Rotation", 5); var motion = tag.getList("Motion", 6);
        player.teleportTo(level, pos.getDouble(0), pos.getDouble(1), pos.getDouble(2), rotation.getFloat(0), rotation.getFloat(1));
        player.setHealth(Math.clamp(tag.getFloat("Health"), 1, player.getMaxHealth()));
        player.setAbsorptionAmount(Math.max(0, tag.getFloat("AbsorptionAmount")));
        player.getInventory().load(tag.getList("Inventory", 10));
        player.getFoodData().readAdditionalSaveData(tag);
        player.experienceLevel = tag.getInt("XpLevel"); player.totalExperience = tag.getInt("XpTotal"); player.experienceProgress = tag.getFloat("XpP");
        player.removeAllEffects();
        ListTag effects = tag.getList("active_effects", 10);
        for (int i = 0; i < effects.size(); i++) {
            var effect = net.minecraft.world.effect.MobEffectInstance.load(effects.getCompound(i));
            if (effect != null) player.addEffect(effect);
        }
        player.setRemainingFireTicks(tag.getShort("Fire")); player.resetFallDistance();
        player.setDeltaMovement(new Vec3(motion.getDouble(0), motion.getDouble(1), motion.getDouble(2)));
        player.setData(ModDataAttachments.PLAYER_RADIATION_STRESS, Math.clamp(tag.getInt("HeroesRadiationStress"), 0, 100));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        player.inventoryMenu.broadcastFullState();
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
    }
    public static void prune(long now) {
        for (var births : BIRTHS.values()) births.values().removeIf(time -> now - time > 340);
    }
    public static void forget(UUID id) { BIRTHS.remove(id); PREVIOUS.remove(id); }
    public static void clear() { BIRTHS.clear(); PREVIOUS.clear(); restoring = false; }
}
