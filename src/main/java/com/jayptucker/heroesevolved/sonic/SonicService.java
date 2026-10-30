package com.jayptucker.heroesevolved.sonic;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.ResonancePayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.MasteryService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sonic Resonance, after Emma Coolidge and Black Canary.
 *
 * <p>Emma sees sound as colour (Sound Sight, drawn on the client), and her music calls people to it: a
 * Resonant Note lures enemies in and bursts. Canary's voice is force: the Cry (tap) and the sustained
 * scream (hold), a Wall of Sound that turns shots back, and a Sonic Vault that lands on a shockwave.
 * Crescendo pours everything into one wave.</p>
 *
 * <p><b>Resonance</b> (0 to 100) builds from sonic hits that land and loud explosions nearby, fades
 * when it is quiet, makes a Note's burst harmonic, and feeds Crescendo.</p>
 */
public final class SonicService {
    public static final int MAX_RESONANCE = 100, HARMONIC_COST = 30, CRESCENDO_TICKS = 60, NOTE_TICKS = 160;
    private static final int HOLD_TICKS = 6, LURE_RADIUS = 16;
    /** How hard a cry pushes its singer back: a tap, then the held scream every three ticks. */
    public static final double CRY_RECOIL = 0.75, SCREAM_RECOIL = 0.25;
    private static final java.util.Set<UUID> BRACE_HINTED = new java.util.HashSet<>();
    private static final int[] MELODY = {0, 4, 7, 12, 7, 4, 2, 5};

    private record Hold(ServerPlayer player, int tier, long start) {}
    private record Channel(ServerPlayer player, int level, long until, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private record Note(ServerPlayer owner, ServerLevel level, Vec3 pos, long placed, long until, int tier) {}
    private record Wall(ServerPlayer owner, ServerLevel level, Vec3 center, Vec3 normal, long until, int tier) {}
    private record Crescendo(ServerPlayer player, int tier, long start) {}
    private static final class Meter { int value; long lastGain; int sent = -1; }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static final Map<UUID, Long> CUSHION = new HashMap<>();
    private static final Map<UUID, Note> NOTES = new HashMap<>();
    /** Mobs drawn to a Note, by mob, with the Note's owner. */
    private static final Map<UUID, UUID> LURED = new HashMap<>();
    private static final Map<UUID, Wall> WALLS = new HashMap<>();
    private static final Map<UUID, Crescendo> CRESCENDOS = new HashMap<>();
    private static final Map<UUID, Meter> METERS = new HashMap<>();

    private SonicService() {}

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }

    private static boolean canHit(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isSpectator() && !player.isAlliedTo(target)
                && !(target instanceof com.jayptucker.heroesevolved.entity.TemporalEchoEntity)
                && (!(target instanceof ServerPlayer other) || player.getServer().isPvpAllowed() && player.canHarmPlayer(other));
    }

    public static boolean singer(ServerPlayer player) {
        return PowerRules.canAct(player) && PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getValue().isUnlocked() && e.getKey().equals(ModAbilities.SONIC_RESONANCE_ID)).isPresent();
    }

    private static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ------------------------------------------------------------------
    // Resonance
    // ------------------------------------------------------------------

    public static int resonance(ServerPlayer player) { Meter m = METERS.get(player.getUUID()); return m == null ? 0 : m.value; }

    public static void addResonance(ServerPlayer player, int amount) {
        Meter m = METERS.computeIfAbsent(player.getUUID(), ignored -> new Meter());
        m.value = Math.clamp(m.value + amount, 0, MAX_RESONANCE);
        if (amount > 0) m.lastGain = now(player);
        syncMeter(player);
    }

    private static void syncMeter(ServerPlayer player) {
        Meter m = METERS.get(player.getUUID());
        int value = m == null ? 0 : m.value;
        if (m != null && m.sent == value) return;
        if (m != null) m.sent = value;
        PacketDistributor.sendToPlayer(player, new ResonancePayload(value));
    }

    /** A loud sound nearby (an explosion) feeds every singer who hears it. */
    public static void loudSound(ServerLevel level, Vec3 at, int amount) {
        for (ServerPlayer player : level.players())
            if (player.position().distanceToSqr(at) < 16 * 16 && singer(player)) addResonance(player, amount);
    }

    // ------------------------------------------------------------------
    // Canary Cry: tap for the cry, hold for the scream
    // ------------------------------------------------------------------

    public static boolean screaming(ServerPlayer player) { return CHANNELS.containsKey(player.getUUID()); }

    public static boolean holdingCry(ServerPlayer player) { return HOLDS.containsKey(player.getUUID()) || screaming(player); }

    public static void press(ServerPlayer player, int tier) {
        HOLDS.put(player.getUUID(), new Hold(player, tier, now(player)));
    }

    public static void release(ServerPlayer player) {
        Hold hold = HOLDS.remove(player.getUUID());
        if (screaming(player)) { stop(player); return; }
        if (hold != null) cry(player, hold.tier, false);
    }

    /** A focused cone of voice: damage and shove, and glass in its path shatters. */
    public static void cry(ServerPlayer player, int powerLevel, boolean sustained) {
        var level = player.serverLevel(); Vec3 origin = player.getEyePosition(), aim = player.getLookAngle().normalize();
        double range = sustained ? 40 : 24 + powerLevel * 4;
        var hit = level.clip(new ClipContext(origin, origin.add(aim.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double length = hit.getType() == HitResult.Type.BLOCK ? origin.distanceTo(hit.getLocation()) : range;
        if (hit.getType() == HitResult.Type.BLOCK) {
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
                com.jayptucker.heroesevolved.expansion.PowerReactions.resonate(level, hit.getBlockPos().offset(x, y, 0), player.getServer().overworld().getGameTime() + 160);
        }
        int affected = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                target -> canHit(player, target))) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
            double along = delta.dot(aim);
            if (along < 0 || along > length + .5 || delta.length() > range
                    || delta.normalize().dot(aim) < .90 || !player.hasLineOfSight(target)) continue;
            if (++affected > 64) break;
            target.hurt(player.damageSources().playerAttack(player), sustained ? 3.5F : 4 + powerLevel * 1.2F);
            push(target, target.getDeltaMovement().add(aim.scale(sustained ? .35 : .9)).add(0, .12, 0));
        }
        if (affected > 0) addResonance(player, sustained ? Math.min(3, affected) : Math.min(12, affected * 4));
        AbilityEffects.send(level, Kind.SONIC_WAVE, origin, origin.add(aim.scale(length)), sustained ? .8 : 1.2);
        if (!sustained) {
            voice(player, false);
            recoil(player, CRY_RECOIL);
        }
        // Glass resonance has a small fixed work bound, rather than scanning the whole cone.
        int checks = Math.min(96, (int) Math.ceil(length * 2));
        for (int i = 1; i <= checks; i++) shatter(player, level, BlockPos.containing(origin.add(aim.scale(i * .5))), false);
    }

    /**
     * Your own cry shoves you backward unless you crouch to brace against it. In the air there is
     * nothing to brace on, so it always pushes.
     */
    public static boolean recoil(ServerPlayer player, double strength) {
        if (player.onGround() && (player.isShiftKeyDown() || player.isCrouching())) return false;
        Vec3 back = player.getLookAngle().multiply(1, 0, 1);
        if (back.lengthSqr() < 1.0E-4) return false;
        back = back.normalize().scale(-strength);
        push(player, player.getDeltaMovement().multiply(0.5, 1, 0.5).add(back.x, player.onGround() ? 0.05 : 0, back.z));
        if (BRACE_HINTED.add(player.getUUID()))
            player.displayClientMessage(Component.literal("Your own cry throws you back. Crouch to brace against it.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /** Glass always gives to the right note; ice and leaves too when {@code soft}. Containers and protected ground never. */
    private static boolean shatter(ServerPlayer player, ServerLevel level, BlockPos pos, boolean soft) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) return false;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        boolean breaks = path.contains("glass") || soft && (path.contains("ice") || path.contains("leaves"));
        if (!breaks || !WorldMutationPolicy.canEdit(player, level, pos)) return false;
        return level.destroyBlock(pos, true, player);
    }

    /** An outward scream (the awakening, and Sonic Vault's landing): pushes everything back and turns shots around. */
    public static void burst(ServerPlayer player, int powerLevel, boolean awakening) {
        double radius = awakening ? 14 : 5 + powerLevel * .5;
        var level = player.serverLevel();
        int affected = 0;
        for (var target : level.getEntities(player, player.getBoundingBox().inflate(radius), target -> target.isAlive() && !target.isSpectator())) {
            if (target.distanceToSqr(player) > radius * radius || !player.hasLineOfSight(target)) continue;
            if (target instanceof LivingEntity living && !canHit(player, living)) continue;
            if (!(target instanceof LivingEntity) && !(target instanceof Projectile)) continue;
            Vec3 away = target.position().subtract(player.position()).normalize();
            if (target instanceof Projectile projectile) {
                if (projectile.getOwner() == player || projectile.getDeltaMovement().dot(away) >= 0) continue;
                projectile.setOwner(player);
            }
            if (++affected > 64) break;
            if (target instanceof LivingEntity living && !awakening) living.hurt(player.damageSources().playerAttack(player), 3 + powerLevel);
            push(target, away.scale(awakening ? 1.1 : .85).add(0, .25, 0));
        }
        AbilityEffects.send(level, Kind.SONIC_BURST, player.position(), player.getEyePosition(), radius);
        voice(player, true);
    }

    // ------------------------------------------------------------------
    // Resonant Note
    // ------------------------------------------------------------------

    public static boolean hasNote(ServerPlayer player) { return NOTES.containsKey(player.getUUID()); }

    /** Sets a humming note where you aim. Enemies nearby forget you and walk to it. */
    public static boolean placeNote(ServerPlayer player, int tier) {
        if (hasNote(player)) return false;
        Vec3 eye = player.getEyePosition(), reach = eye.add(player.getLookAngle().scale(20));
        var hit = player.level().clip(new ClipContext(eye, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 pos = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(player.getLookAngle().scale(0.6)) : reach;
        if (hit.getType() == HitResult.Type.BLOCK) pos = pos.add(0, 0.8, 0);
        placeNoteAt(player, pos, tier);
        return true;
    }

    public static void placeNoteAt(ServerPlayer player, Vec3 pos, int tier) {
        long now = now(player);
        NOTES.put(player.getUUID(), new Note(player, player.serverLevel(), pos, now, now + NOTE_TICKS, tier));
        AbilityEffects.send(player.serverLevel(), Kind.SONIC_NOTE, pos, pos, 1.4F);
        sound(player.serverLevel(), pos, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.7F);
        ArmPosePayload.show(player, ArmPose.ONE_HAND, 10);
        player.displayClientMessage(Component.literal("The note hums. Hunters come to it. Press again to break it.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** True if {@code mob} is drawn to a Resonant Note right now. */
    public static boolean lured(Entity mob) { return LURED.containsKey(mob.getUUID()); }

    /** Breaks the note: everything gathered around it is hit, harder and wider if you have the Resonance for a harmonic. */
    public static boolean detonate(ServerPlayer player) {
        Note note = NOTES.remove(player.getUUID());
        if (note == null) return false;
        burstNote(note);
        return true;
    }

    private static void burstNote(Note note) {
        ServerPlayer p = note.owner;
        LURED.values().removeIf(owner -> owner.equals(p.getUUID()));
        boolean harmonic = resonance(p) >= HARMONIC_COST;
        if (harmonic) addResonance(p, -HARMONIC_COST);
        double radius = 4 + note.tier * 0.5 + (harmonic ? 3 : 0);
        float damage = (5 + note.tier * 1.5F) * (harmonic ? 1.5F : 1.0F);
        int caught = 0;
        for (LivingEntity target : note.level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(note.pos, note.pos).inflate(radius),
                t -> canHit(p, t) && t.getBoundingBox().getCenter().distanceToSqr(note.pos) <= radius * radius)) {
            if (++caught > 32) break;
            target.hurt(p.damageSources().playerAttack(p), damage);
            Vec3 away = target.position().subtract(note.pos).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : away.normalize();
            push(target, away.scale(0.8).add(0, 0.35, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            if (caught <= 8) EvolutionTrials.advance(p, ModAbilities.SONIC_RESONANCE_ID);
        }
        BlockPos center = BlockPos.containing(note.pos);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) shatter(p, note.level, pos.immutable(), true);
        AbilityEffects.send(note.level, Kind.SONIC_NOTE_BURST, note.pos, note.pos, (float) radius);
        sound(note.level, note.pos, SoundEvents.BELL_RESONATE, 1.2F, harmonic ? 1.4F : 1.0F);
        sound(note.level, note.pos, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.8F);
        sound(note.level, note.pos, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.6F);
        if (harmonic) p.displayClientMessage(Component.literal("Harmonic!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
    }

    private static void tickNotes(long now) {
        for (var entry : List.copyOf(NOTES.entrySet())) {
            Note note = entry.getValue();
            if (!singer(note.owner) || note.owner.level() != note.level) { NOTES.remove(entry.getKey()); LURED.values().removeIf(o -> o.equals(entry.getKey())); continue; }
            if (now >= note.until) { NOTES.remove(entry.getKey()); burstNote(note); continue; }
            long age = now - note.placed;
            if (age % 4 == 0) AbilityEffects.send(note.level, Kind.SONIC_NOTE, note.pos, note.pos, 1 + (age % 40) / 40F);
            // A slow phrase, over and over, the way Emma's cello carried.
            if (age % 10 == 0) {
                int step = MELODY[(int) (age / 10) % MELODY.length];
                sound(note.level, note.pos, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, (float) Math.pow(2, (step - 12) / 12.0));
                sound(note.level, note.pos, SoundEvents.NOTE_BLOCK_HARP.value(), 0.5F, (float) Math.pow(2, step / 12.0));
            }
            if (age % 5 != 0) continue;
            int drawn = 0;
            for (Mob mob : note.level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(note.pos, note.pos).inflate(LURE_RADIUS),
                    m -> m instanceof Enemy && canHit(note.owner, m) && m.getMaxHealth() <= 100
                            && !(m instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity))) {
                if (++drawn > 24) break;
                LURED.put(mob.getUUID(), note.owner.getUUID());
                mob.setTarget(null);
                if (mob.distanceToSqr(note.pos) > 2.5 * 2.5) mob.getNavigation().moveTo(note.pos.x, note.pos.y - 0.8, note.pos.z, 1.15);
                else mob.getNavigation().stop();
            }
        }
    }

    // ------------------------------------------------------------------
    // Wall of Sound
    // ------------------------------------------------------------------

    public static boolean hasWall(ServerPlayer player) { return WALLS.containsKey(player.getUUID()); }

    /** A standing wall of sound a couple of blocks ahead: shots turn back, enemies are pushed away. */
    public static boolean wall(ServerPlayer player, int tier) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 center = player.position().add(0, 1.2, 0).add(look.scale(2.2));
        WALLS.put(player.getUUID(), new Wall(player, player.serverLevel(), center, look, now(player) + 100 + 10L * tier, tier));
        AbilityEffects.send(player.serverLevel(), Kind.SONIC_WALL, center, center.add(look), 2.6F);
        voice(player, true);
        ArmPosePayload.show(player, ArmPose.TWO_HANDS, 12);
        return true;
    }

    public static boolean dropWall(ServerPlayer player) { return WALLS.remove(player.getUUID()) != null; }

    private static void tickWalls(long now) {
        for (var entry : List.copyOf(WALLS.entrySet())) {
            Wall wall = entry.getValue();
            if (now >= wall.until || !singer(wall.owner) || wall.owner.level() != wall.level) { WALLS.remove(entry.getKey()); continue; }
            Vec3 side = new Vec3(-wall.normal.z, 0, wall.normal.x);
            var area = new net.minecraft.world.phys.AABB(wall.center, wall.center).inflate(4);
            for (Projectile shot : wall.level.getEntitiesOfClass(Projectile.class, area)) {
                if (shot.getOwner() == wall.owner) continue;
                Vec3 rel = shot.position().subtract(wall.center), v = shot.getDeltaMovement();
                double toward = v.dot(wall.normal);
                if (toward >= 0 || Math.abs(rel.dot(side)) > 2.6 || Math.abs(rel.y) > 1.9) continue;
                double d = rel.dot(wall.normal);
                if (d < -0.4 || d > 0.8 - toward) continue;
                // Turned back the way it came, and it is yours now.
                push(shot, v.subtract(wall.normal.scale(2 * toward)).scale(1.1));
                shot.setOwner(wall.owner);
                AbilityEffects.send(wall.level, Kind.SONIC_NOTE, shot.position(), shot.position(), 0.5F);
                sound(wall.level, shot.position(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.6F);
                addResonance(wall.owner, 4);
            }
            if (now % 4 == 0) AbilityEffects.send(wall.level, Kind.SONIC_WALL, wall.center, wall.center.add(wall.normal), 2.6F);
            if (now % 20 == 0) sound(wall.level, wall.center, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.7F);
            if (now % 10 != 0) continue;
            for (LivingEntity target : wall.level.getEntitiesOfClass(LivingEntity.class, area, t -> canHit(wall.owner, t))) {
                Vec3 rel = target.getBoundingBox().getCenter().subtract(wall.center);
                double d = rel.dot(wall.normal);
                if (d < -1.0 || d > 1.4 || Math.abs(rel.dot(side)) > 2.8 || Math.abs(rel.y) > 2.2) continue;
                target.hurt(wall.owner.damageSources().playerAttack(wall.owner), 2 + wall.tier * 0.5F);
                push(target, wall.normal.scale(0.9).add(0, 0.2, 0));
            }
        }
    }

    // ------------------------------------------------------------------
    // Sonic Vault
    // ------------------------------------------------------------------

    /** A cry at the ground behind you throws you the other way; your next landing comes down on a shockwave. */
    public static void vault(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement().add(player.getLookAngle().reverse().scale(1.25)).add(0, .45, 0);
        player.setDeltaMovement(velocity); player.connection.send(new ClientboundSetEntityMotionPacket(player));
        CUSHION.put(player.getUUID(), player.level().getGameTime() + 200); player.resetFallDistance();
        AbilityEffects.send(player.serverLevel(), Kind.SONIC_BURST, player.position(), player.getEyePosition(), 3);
        voice(player, true);
    }

    /** Called on landing: true cancels fall damage. A real fall also lands on a shockwave. */
    public static boolean cushion(ServerPlayer player, float distance) {
        Long until = CUSHION.remove(player.getUUID());
        if (until == null || player.level().getGameTime() > until) return false;
        if (distance > 2.5F) {
            var level = player.serverLevel();
            int tier = com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(player);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4), t -> canHit(player, t))) {
                target.hurt(player.damageSources().playerAttack(player), 3 + tier);
                Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
                push(target, (away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize()).scale(0.9).add(0, 0.35, 0));
            }
            AbilityEffects.send(level, Kind.SONIC_LANDING, player.position(), player.position(), 4);
            sound(level, player.position(), SoundEvents.MACE_SMASH_GROUND, 0.9F, 1.4F);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Crescendo
    // ------------------------------------------------------------------

    public static boolean building(ServerPlayer player) { return CRESCENDOS.containsKey(player.getUUID()); }

    public static void startCrescendo(ServerPlayer player, int tier) {
        CRESCENDOS.put(player.getUUID(), new Crescendo(player, tier, now(player)));
        ArmPosePayload.show(player, ArmPose.ARMS_WIDE, CRESCENDO_TICKS + 10);
    }

    /** The key came up: release everything that was built. */
    public static void releaseCrescendo(ServerPlayer player) {
        Crescendo c = CRESCENDOS.remove(player.getUUID());
        if (c != null) fireCrescendo(player, c.tier, Math.min(1F, (now(player) - c.start) / (float) CRESCENDO_TICKS));
    }

    public static boolean cancelCrescendo(ServerPlayer player) {
        if (CRESCENDOS.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clear(player);
        return true;
    }

    /**
     * One wave of everything: a wide cone, longer and harder the longer it was built, and harder still
     * for every point of Resonance (all of it is spent).
     */
    public static int fireCrescendo(ServerPlayer player, int tier, float built) {
        built = Math.max(0.25F, built);
        int resonance = resonance(player);
        if (resonance > 0) addResonance(player, -resonance);
        var level = player.serverLevel();
        Vec3 origin = player.getEyePosition(), aim = player.getLookAngle().normalize();
        double range = 16 + 16 * built;
        float damage = 6 + 10 * built + resonance / 10F + tier;
        int hit = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), t -> canHit(player, t))) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
            if (delta.length() > range || delta.normalize().dot(aim) < 0.8 || !player.hasLineOfSight(target)) continue;
            if (++hit > 48) break;
            target.hurt(player.damageSources().playerAttack(player), damage);
            push(target, aim.scale(1.2 + 1.2 * built).add(0, 0.35, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        }
        int checks = Math.min(160, (int) Math.ceil(range * 2));
        for (int i = 2; i <= checks; i++) {
            BlockPos center = BlockPos.containing(origin.add(aim.scale(i * 0.5)));
            for (BlockPos pos : new BlockPos[] {center, center.above(), center.below(), center.east(), center.west(), center.north(), center.south()})
                shatter(player, level, pos, true);
        }
        AbilityEffects.send(level, Kind.SONIC_CRESCENDO, origin, origin.add(aim.scale(range)), 1 + 2 * built);
        sound(level, origin, com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_CRY.get(), 1.4F, 0.85F);
        sound(level, origin, SoundEvents.BELL_RESONATE, 1.5F, 0.6F);
        sound(level, origin, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.3F);
        ArmPosePayload.show(player, ArmPose.TWO_HANDS_STRAIN, 12);
        com.jayptucker.heroesevolved.network.ScreenTintPayload.send(player, 0x40C080FF, 12, com.jayptucker.heroesevolved.network.ScreenTintPayload.Style.FLASH);
        return hit;
    }

    private static void tickCrescendos(long now) {
        for (var entry : List.copyOf(CRESCENDOS.entrySet())) {
            Crescendo c = entry.getValue();
            if (!singer(c.player) || c.player.isRemoved()) { CRESCENDOS.remove(entry.getKey()); continue; }
            long held = now - c.start;
            c.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false, false));
            float built = Math.min(1F, held / (float) CRESCENDO_TICKS);
            if (held % 3 == 0) AbilityEffects.send(c.player.serverLevel(), Kind.SONIC_CRESCENDO_CHARGE, c.player.getEyePosition(),
                    c.player.getEyePosition().add(c.player.getLookAngle()), 0.4F + built);
            if (held % 5 == 0) sound(c.player.serverLevel(), c.player.getEyePosition(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.5F + built * 1.5F);
            if (held >= CRESCENDO_TICKS) { CRESCENDOS.remove(entry.getKey()); fireCrescendo(c.player, c.tier, 1F); }
        }
    }

    // ------------------------------------------------------------------
    // The sustained scream and the voice
    // ------------------------------------------------------------------

    public static void start(ServerPlayer player, int level) {
        CHANNELS.put(player.getUUID(), new Channel(player, level, player.getServer().overworld().getGameTime() + 100, player.level().dimension()));
        voiceState(player, true);
    }

    public static void voice(ServerPlayer player, boolean burst) {
        player.serverLevel().playSound(null, player.blockPosition(), burst
                ? com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_BURST.get()
                : com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_CRY.get(), SoundSource.PLAYERS, .85F, 1);
    }

    private static void voiceState(ServerPlayer player, boolean active) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new com.jayptucker.heroesevolved.network.SonicVoicePayload(player.getId(), player.getUUID(), active));
    }

    public static void stop(ServerPlayer player) {
        if (CHANNELS.remove(player.getUUID()) != null) voiceState(player, false);
    }

    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        stop(player);
        HOLDS.remove(id); CUSHION.remove(id); NOTES.remove(id); WALLS.remove(id); CRESCENDOS.remove(id);
        LURED.values().removeIf(owner -> owner.equals(id));
        if (METERS.remove(id) != null) PacketDistributor.sendToPlayer(player, new ResonancePayload(0));
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        // A held Cry becomes the sustained scream.
        for (Hold hold : List.copyOf(HOLDS.values())) {
            if (!singer(hold.player) || hold.player.isRemoved()) { HOLDS.remove(hold.player.getUUID()); continue; }
            if (!screaming(hold.player) && now - hold.start >= HOLD_TICKS) start(hold.player, hold.tier);
        }
        for (Channel channel : List.copyOf(CHANNELS.values())) {
            if (!channel.player.isAlive() || channel.player.isRemoved() || now >= channel.until
                    || !channel.dimension.equals(channel.player.level().dimension()) || !PowerRules.canAct(channel.player)) {
                HOLDS.remove(channel.player.getUUID());
                stop(channel.player);
            } else {
                if (now % 20 == 0) voiceState(channel.player, true);
                // The scream keeps pushing while it lasts: steadily on the ground, in gusts in the air.
                if (channel.player.onGround() ? now % 3 == 0 : now % 10 == 0)
                    recoil(channel.player, channel.player.onGround() ? SCREAM_RECOIL : SCREAM_RECOIL * 1.6);
            }
        }
        if (now % 10 == 0) for (Channel channel : List.copyOf(CHANNELS.values())) {
            if (!PlayerEnergyService.tryConsume(channel.player, 6)) { stop(channel.player); continue; }
            MasteryService.awardEnergySpent(channel.player, 6); cry(channel.player, channel.level, true);
        }
        CUSHION.values().removeIf(until -> until < now);
        tickNotes(now);
        tickWalls(now);
        tickCrescendos(now);
        LURED.entrySet().removeIf(e -> !NOTES.containsKey(e.getValue()));
        for (var entry : List.copyOf(METERS.entrySet())) {
            Meter m = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            if (!singer(player)) { forget(player); continue; }
            if (m.value > 0 && now - m.lastGain > 200 && now % 20 == 0) { m.value--; syncMeter(player); }
        }
    }

    public static void clear() {
        for (var channel : List.copyOf(CHANNELS.values())) stop(channel.player);
        BRACE_HINTED.clear(); HOLDS.clear(); CUSHION.clear(); NOTES.clear(); LURED.clear(); WALLS.clear(); CRESCENDOS.clear(); METERS.clear();
    }
}
