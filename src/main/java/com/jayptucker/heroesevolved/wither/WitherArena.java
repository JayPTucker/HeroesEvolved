package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.DazePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.radiation.NuclearShields;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.story.bunker.BunkerLogs;
import com.jayptucker.heroesevolved.structure.Blueprint;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/**
 * A persistent breach where the Wither was built, an Ossuary rebuilt for each attempt, a way out torn open where it
 * dies, and a safe way home for every participant.
 */
public final class WitherArena {
    private static RiftWitherEntity intro, boss;
    private static boolean pending, ready, built, sourceForced;
    private static ServerLevel forcedSource;
    private static ChunkPos sourceChunk;
    private static int emptyTicks, closeIn = -1;
    private static int generation;
    /** After a victory: where the exit tears open, the cage tick it opens on, and whether it is open yet. */
    private static Vec3 exitAt;
    private static long exitOpensAt = -1;
    /** The encounter's own clock, a tick per {@link #tick}: the exit and every knock-down are timed on it. */
    private static long clock;
    private static boolean exitOpen;
    private static final Map<UUID, ServerPlayer> MEMBERS = new LinkedHashMap<>();
    private static final Set<UUID> MOVING = new HashSet<>(), LEAVE_FIRST = new HashSet<>();
    private static final Map<ItemEntity, UUID> PRIZES = new HashMap<>();
    /** Knocked flat by the detonation: the cage tick each gets up on. */
    private static final Map<UUID, Long> DOWN = new HashMap<>();
    private static final Map<UUID, List<ItemStack>> PENDING_PRIZES = new LinkedHashMap<>();
    private static Map<String, List<BlockPos>> markers = Map.of();
    private static final Set<Long> CAGE_HELD = new HashSet<>();
    /** Chunks round the summoning site this encounter keeps loaded (the Wither is dragged across them). */
    private static final List<ChunkPos> SOURCE_HELD = new ArrayList<>();
    private static long introSince = -1;
    private WitherArena() {}

    public static boolean inside(Entity e) { return e.level().dimension().equals(WitherGeometry.DIMENSION); }
    public static boolean moving(ServerPlayer p) { return MOVING.contains(p.getUUID()); }
    public static RiftWitherEntity boss() { return boss; }
    public static boolean ready() { return ready; }
    /** Where the way out stands after a victory (its center), or null. */
    public static Vec3 exitPortal() { return exitOpen ? exitAt : null; }
    public static boolean down(ServerPlayer p) { return DOWN.containsKey(p.getUUID()); }

    public static String status(MinecraftServer server) {
        if (!busy(server)) return "No Wither breach. Open one near the selected player or use the ordinary skull construction.";
        if (closeIn >= 0) return "Contained. " + (exitOpen ? "The exit stands where it died; " : "The exit is tearing open; ")
                + MEMBERS.values().stream().filter(p -> p.isAlive() && inside(p)).count() + " fighter(s) still inside. The Ossuary closes in "
                + closeIn / 20 + " s.";
        if (boss == null) return ready ? "Breach ready. Entry requires an awakened ability and Power Level 16." : "The Wither is forming and being dragged into its breach.";
        long marked = boss.fighters().stream().filter(p -> p.hasEffect(com.jayptucker.heroesevolved.registry.ModItems.HUNGERING)).count();
        return "Phase " + boss.phase() + "; " + Math.round(boss.getHealth()) + " / " + Math.round(boss.getMaxHealth()) + " health; " + boss.fighters().size() + " fighter(s). "
                + (boss.ward ? "Star Ward up: " + boss.wardHearts() + " / 3 hearts feed it (gate at " + Math.round(WitherGeometry.WARD_GATE * 100) + "%). "
                : boss.phase() == 1 ? "Ward broken. " : "Hearts: " + (3 - Integer.bitCount(boss.burnt())) + " / 3 unspent" + (boss.anchor() >= 0 ? ", heart " + boss.anchor() + " feeding" : "") + ". ")
                + (marked > 0 ? marked + " marked. " : "") + (boss.minions.isEmpty() ? "" : boss.minions.size() + " brood. ")
                + (boss.dying() ? "Death scene: the star detonates." : boss.arriving() ? "Arriving through the tear." : boss.action() == null ? "Pursuing / recovering."
                : boss.action().name() + "; cracked stance " + boss.stance() + " / " + WitherGeometry.STANCE_HITS + ".")
                + " Cancellation returns every fighter and clears the source breach.";
    }
    public static String operatorStart(ServerPlayer target, int variant) {
        if (variant == 0) return start(target.serverLevel(), target.position().add(target.getLookAngle().multiply(1, 0, 1).normalize().scale(7)), target.getYRot())
                ? "Forming a Wither breach near the selected player." : "A Wither breach is already open, or this dimension cannot host its source.";
        if (variant == 1) return enter(target) ? "Entered the Wither fight." : "Entry refused: wait for the breach, leave any current attempt, and check awakened Power Level 16.";
        if (variant == 2 && boss != null && boss.fighting() && boss.phase() < 3) { boss.operatorPhase(); return "Advanced the live Wither to phase " + boss.phase() + "."; }
        if (variant == 3 && boss != null && boss.fighting()) { boss.operatorDefeat(); return "The Wither's star is collapsing: death scene, detonation and exit."; }
        if (variant == 4) return boss != null && boss.operatorBreakWard() ? "The Star Ward shatters: the Wither is thrown down, and its hearts stay dark this phase."
                : "No live Wither with its ward standing.";
        return variant == 3 ? "No live Wither to defeat." : "No live Wither can advance to another phase.";
    }
    public static boolean busy(MinecraftServer server) { return WitherData.get(server).source != null; }
    public static boolean sheltered(Entity p) {
        return inside(p) && p.getZ() < WitherGeometry.ORIGIN.getZ() - 34 && p.getZ() > WitherGeometry.ORIGIN.getZ() - 42
                && Math.abs(p.getX() - WitherGeometry.ORIGIN.getX()) < 4 && p.getY() < WitherGeometry.floor().y + 5;
    }

    // ---------------------------------------------------------------- the summoning site

    /**
     * The Wither forms at {@code at}, turned to whoever is watching; its breach tears open behind it (the side it is
     * dragged through) on clear ground, and stays there as the way in.
     */
    public static boolean start(ServerLevel source, Vec3 at, float yaw) {
        MinecraftServer server = source.getServer();
        if (busy(server) || server.getLevel(WitherGeometry.DIMENSION) == null || insideDimension(source)) return false;
        var watcher = source.getNearestPlayer(at.x, at.y, at.z, 32, false);
        float facing = yaw;
        if (watcher != null && watcher.position().distanceToSqr(at) > 1) {
            Vec3 d = watcher.position().subtract(at);
            facing = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        }
        Vec3 back = WitherGeometry.forward(facing).scale(-1), gateGround = at.add(back.scale(4.5));
        for (double d : new double[]{4.5, 3.5, 2.5}) {
            Vec3 ground = at.add(back.scale(d));
            AABB door = new AABB(ground.x - .6, ground.y + .2, ground.z - .6, ground.x + .6, ground.y + 3.6, ground.z + .6);
            if (source.noCollision(door)) { gateGround = ground; break; }
        }
        WitherData.get(server).source(new WitherData.Home(source.dimension(), gateGround, facing, 0));
        pending = true; ready = false;
        int run = ++generation;
        Vec3 gate = gateGround.add(0, WitherGeometry.GATE_LIFT, 0);
        float face = facing;
        server.execute(() -> {
            if (run != generation || WitherData.get(server).source == null) return;
            var actor = ModEntities.RIFT_WITHER.get().create(source);
            pending = false;
            if (actor == null) return;
            actor.moveTo(at.x, at.y, at.z, face, 0); actor.yBodyRot = actor.yBodyRotO = face; actor.setYHeadRot(face);
            actor.beginIntro(gate, face);
            intro = actor; source.addFreshEntity(actor);
        });
        return true;
    }
    private static boolean insideDimension(ServerLevel level) { return level.dimension().equals(WitherGeometry.DIMENSION); }
    public static void swallowed(RiftWitherEntity actor) {
        if (actor != intro || WitherData.get(actor.level().getServer()).source == null) { actor.discard(); return; }
        ready = true; intro = null;
        ServerLevel level = (ServerLevel) actor.level();
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(actor) < 32 * 32) award(p, "wither_anomaly");
        actor.discard();
    }
    private static void award(ServerPlayer p, String id) {
        var advancement = p.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, id));
        if (advancement != null) p.getAdvancements().award(advancement, "observed");
    }

    // ---------------------------------------------------------------- in and out

    public static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && PlayerProgressionService.getPowerLevel(p) >= WitherGeometry.MINIMUM_LEVEL
                && PlayerAbilityService.getData(p).assignedPower().filter(e -> e.getValue().isUnlocked()).isPresent();
    }
    public static boolean enter(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        var data = WitherData.get(server);
        if (!ready || data.source == null || closeIn >= 0 || inside(p) || boss != null && boss.dying()) return false;
        if (!eligible(p)) {
            if (!LEAVE_FIRST.contains(p.getUUID())) p.sendSystemMessage(Component.translatable("wither.heroesevolved.level", WitherGeometry.MINIMUM_LEVEL));
            LEAVE_FIRST.add(p.getUUID());
            return false;
        }
        ServerLevel cage = server.getLevel(WitherGeometry.DIMENSION);
        if (cage == null) return false;
        if (!built) {
            holdCage(cage, true);
            markers = Blueprint.load("wither_cage").place(cage, WitherGeometry.ORIGIN, Rotation.NONE);
            built = true;
        }
        data.home(p.getUUID(), new WitherData.Home(p.level().dimension(), p.position(), p.getYRot(), p.getXRot()));
        MEMBERS.put(p.getUUID(), p);
        move(p, cage, WitherGeometry.entrance(), 0, -8);
        if (boss == null || !boss.isAlive()) {
            boss = ModEntities.RIFT_WITHER.get().create(cage);
            if (boss == null) { sendHome(p); return false; }
            boss.moveTo(WitherGeometry.floor().add(0, .35, 0));
            boss.beginFight();
            cage.addFreshEntity(boss);
        }
        boss.join(p);
        emptyTicks = 0;
        p.sendSystemMessage(Component.translatable("wither.heroesevolved.enter").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return true;
    }
    private static void holdCage(ServerLevel level, boolean hold) {
        if (hold) for (int x = -3; x <= 2; x++) for (int z = -3; z <= 2; z++) {
            long key = ChunkPos.asLong(x, z);
            if (!level.getForcedChunks().contains(key)) { level.setChunkForced(x, z, true); CAGE_HELD.add(key); }
        }
        else { for (long key : CAGE_HELD) { ChunkPos at = new ChunkPos(key); level.setChunkForced(at.x, at.z, false); } CAGE_HELD.clear(); }
    }
    private static void move(ServerPlayer p, ServerLevel level, Vec3 at, float yaw, float pitch) {
        MOVING.add(p.getUUID());
        try { p.teleportTo(level, at.x, at.y, at.z, yaw, pitch); p.absMoveTo(at.x, at.y, at.z, yaw, pitch); }
        finally { MOVING.remove(p.getUUID()); }
        p.setDeltaMovement(Vec3.ZERO); p.fallDistance = 0;
    }
    public static void sendHome(ServerPlayer p) {
        getUp(p, false);
        var data = WitherData.get(p.getServer());
        var home = data.home(p.getUUID());
        ServerLevel level = home == null ? null : p.getServer().getLevel(home.dimension());
        if (level == null || insideDimension(level)) { level = p.getServer().overworld(); home = null; }
        Vec3 at = home == null ? Vec3.atBottomCenterOf(level.getSharedSpawnPos()) : home.at();
        level.getChunk(BlockPos.containing(at));
        if (!level.noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) {
            Vec3 raised = at.add(0, 2, 0);
            at = level.noCollision(p, p.getBoundingBox().move(raised.subtract(p.position()))) ? raised : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        }
        move(p, level, at, home == null ? p.getYRot() : home.yaw(), home == null ? 0 : home.pitch());
        data.clearHome(p.getUUID()); LEAVE_FIRST.add(p.getUUID()); deliver(p);
    }
    public static void deliver(ServerPlayer p) {
        for (ItemStack stack : WitherData.get(p.getServer()).take(p.getUUID(), p.registryAccess()))
            if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    // ---------------------------------------------------------------- the detonation

    /**
     * The star's blast reaches {@code p}. A raised shield facing it, or a power that throws up a shield (the same powers
     * that outlast a nuclear blast), holds it off: pushed back, still standing. Anyone else is thrown flat, ears ringing,
     * out cold for a moment and then blinking awake on the ground, held down until they come to.
     */
    public static void blast(ServerPlayer p, Vec3 core) {
        Vec3 away = p.position().subtract(core).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
        if (shielded(p, core)) {
            p.setDeltaMovement(away.scale(.7).add(0, .15, 0));
            p.hurtMarked = true;
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
            PacketDistributor.sendToPlayer(p, new com.jayptucker.heroesevolved.network.CameraShakePayload(.6F, 24));
            ScreenTintPayload.send(p, 0x90E8FFF4, 10, ScreenTintPayload.Style.FLASH);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 2F, .5F);
            p.sendSystemMessage(Component.translatable("wither.heroesevolved.braced").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
            return;
        }
        if (com.jayptucker.heroesevolved.entity.EntityCarryService.isCarrying(p)) com.jayptucker.heroesevolved.entity.EntityCarryService.release(p);
        p.stopUsingItem();
        p.setDeltaMovement(away.scale(1.1).add(0, .45, 0));
        p.hurtMarked = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        DOWN.put(p.getUUID(), clock + WitherGeometry.DAZE);
        p.setForcedPose(Pose.SLEEPING);
        PacketDistributor.sendToPlayer(p, new DazePayload(WitherGeometry.DAZE, true, WitherGeometry.BLACKOUT));
        PacketDistributor.sendToPlayer(p, new com.jayptucker.heroesevolved.network.CameraShakePayload(1F, 34));
        ScreenTintPayload.send(p, 0xF0EAFFF6, 20, ScreenTintPayload.Style.FLASH);
        p.sendSystemMessage(Component.translatable("wither.heroesevolved.blast").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
    /** A shield raised toward the blast, or a shield-making power that can hold one. */
    public static boolean shielded(ServerPlayer p, Vec3 core) {
        if (p.isBlocking()) {
            Vec3 from = core.vectorTo(p.position());
            from = new Vec3(from.x, 0, from.z).normalize();
            if (from.dot(p.getViewVector(1)) < 0) return true;
        }
        if (NuclearShields.canBrace(p)) { NuclearShields.brace(p, core, 0); return true; }
        return false;
    }
    private static void holdDown() {
        long now = clock;
        for (ServerPlayer p : List.copyOf(MEMBERS.values())) {
            Long until = DOWN.get(p.getUUID());
            if (until == null) continue;
            if (!p.isAlive() || !inside(p)) { getUp(p, false); continue; }
            if (now < until) {
                p.setForcedPose(Pose.SLEEPING);
                if (now > until - WitherGeometry.DAZE + 8) p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
                p.fallDistance = 0;
            } else getUp(p, true);
        }
    }
    private static void getUp(ServerPlayer p, boolean tell) {
        if (DOWN.remove(p.getUUID()) == null) return;
        if (p.getForcedPose() == Pose.SLEEPING) p.setForcedPose(null);
        if (tell) p.sendSystemMessage(Component.translatable("wither.heroesevolved.come_to").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    // ---------------------------------------------------------------- victory and the way out

    public static void victory(RiftWitherEntity defeated) {
        if (defeated != boss || closeIn >= 0) return;
        ServerLevel level = (ServerLevel) defeated.level();
        var data = WitherData.get(level.getServer());
        // The way out tears open where it died, on the floor, within reach of the floor's edge.
        Vec3 spot = new Vec3(defeated.getX(), WitherGeometry.floor().y, defeated.getZ()), flat = spot.subtract(WitherGeometry.floor());
        if (flat.horizontalDistance() > 22) spot = WitherGeometry.floor().add(flat.multiply(1, 0, 1).normalize().scale(22));
        exitAt = spot.add(0, WitherGeometry.GATE_LIFT, 0);
        exitOpensAt = clock + WitherGeometry.EXIT_OPENS;
        exitOpen = false;
        UUID starOwner = defeated.killer();
        if (starOwner == null || !MEMBERS.containsKey(starOwner)) starOwner = MEMBERS.keySet().stream().findFirst().orElse(null);
        if (starOwner != null) PENDING_PRIZES.computeIfAbsent(starOwner, k -> new ArrayList<>()).add(new ItemStack(Items.NETHER_STAR));
        for (var entry : MEMBERS.entrySet()) {
            UUID id = entry.getKey();
            List<ItemStack> prizes = PENDING_PRIZES.computeIfAbsent(id, k -> new ArrayList<>());
            prizes.add(new ItemStack(ModItems.FRACTURED_STAR.get()));
            if (data.win(id)) prizes.add(BunkerLogs.book("Containment Failure", "Unknown researcher",
                    "Three heads. One cage.\n\nThe star was never a heart. It was something the bones were keeping in.",
                    "The twelve eyes of the stronghold are another cage. Eyes alone cannot open it.\n\nTwo touching frames. Two offerings from gifts beyond Power Level 15. A frame remembers long enough for one traveler to make both."));
            else if (level.random.nextFloat() < .1F) prizes.add(new ItemStack(ModItems.MUSIC_DISC_CONTAINMENT.get()));
            award(entry.getValue(), "wither_contained");
        }
        closeIn = WitherGeometry.EXIT_OPENS + WitherGeometry.EXIT_LINGER;
    }
    /** The exit tears open: the prizes are thrown out of it toward whoever earned them. */
    private static void openExit(ServerLevel cage) {
        exitOpen = true;
        SetPiecePayload.send(cage, Piece.WITHER_OPEN, exitAt, exitAt.add(0, 0, -1), 1, null);
        cage.playSound(null, exitAt.x, exitAt.y, exitAt.z, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 2.5F, .6F);
        cage.playSound(null, exitAt.x, exitAt.y, exitAt.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2.5F, .5F);
        Vec3 mouth = exitAt.add(0, -.6, 0);
        for (var entry : PENDING_PRIZES.entrySet()) {
            ServerPlayer owner = MEMBERS.get(entry.getKey());
            for (ItemStack stack : entry.getValue()) {
                Vec3 toward = owner != null && inside(owner) ? owner.position().subtract(mouth).multiply(1, 0, 1) : new Vec3(0, 0, -1);
                toward = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : toward.normalize();
                prize(cage, mouth, stack, entry.getKey(), toward.scale(.22).add(0, .3, 0));
            }
        }
        PENDING_PRIZES.clear();
    }
    private static void prize(ServerLevel level, Vec3 at, ItemStack stack, UUID owner, Vec3 velocity) {
        var entity = new ItemEntity(level, at.x, at.y, at.z, stack);
        entity.setTarget(owner); entity.setPickUpDelay(10); entity.setDeltaMovement(velocity);
        level.addFreshEntity(entity); PRIZES.put(entity, owner);
    }
    /** Leaving by the exit: anything of theirs still lying on the floor goes with them. */
    private static void leave(ServerPlayer p) {
        var data = WitherData.get(p.getServer());
        for (var entry : List.copyOf(PRIZES.entrySet()))
            if (entry.getValue().equals(p.getUUID()) && !entry.getKey().isRemoved() && !entry.getKey().getItem().isEmpty()) {
                data.stash(p.getUUID(), entry.getKey().getItem(), p.registryAccess());
                entry.getKey().discard();
                PRIZES.remove(entry.getKey());
            }
        sendHome(p);
    }
    private static boolean touching(ServerPlayer p, Vec3 center, Vec3 normal) {
        Vec3 d = p.position().add(0, .9, 0).subtract(center), side = normal.cross(new Vec3(0, 1, 0));
        return Math.abs(d.dot(normal)) < .75 && Math.abs(d.dot(side)) < 1.3 && Math.abs(d.y) < 2;
    }

    // ---------------------------------------------------------------- every tick

    public static void tick(MinecraftServer server) {
        clock++;
        var data = WitherData.get(server);
        var source = data.source;
        if (source == null) return;
        ServerLevel world = server.getLevel(source.dimension());
        if (world == null) return;
        if (forcedSource == null) {
            sourceChunk = new ChunkPos(BlockPos.containing(source.at())); forcedSource = world;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                ChunkPos at = new ChunkPos(sourceChunk.x + dx, sourceChunk.z + dz);
                if (!world.getForcedChunks().contains(at.toLong())) { world.setChunkForced(at.x, at.z, true); SOURCE_HELD.add(at); }
            }
            sourceForced = !SOURCE_HELD.isEmpty();
        }
        if (!pending && (intro == null || intro.isRemoved()) && !ready) ready = true; // A saved entrance resumes after a restart.
        // A watchdog: an intro that stops advancing (a stalled chunk, a frozen tick) still leaves the breach usable.
        if (intro != null && !ready) {
            if (introSince < 0) introSince = clock;
            else if (clock - introSince > WitherGeometry.INTRO_TICKS + 60) swallowed(intro);
        } else introSince = -1;
        Vec3 gate = source.at().add(0, WitherGeometry.GATE_LIFT, 0), normal = WitherGeometry.forward(source.yaw());
        if (ready && world.getGameTime() % 10 == 0) SetPiecePayload.send(world, Piece.WITHER_BREACH, gate, gate.add(normal), 1, null);
        if (ready && closeIn < 0) for (ServerPlayer p : List.copyOf(world.players())) {
            if (p.position().distanceToSqr(gate) > 16) LEAVE_FIRST.remove(p.getUUID());
            if (!LEAVE_FIRST.contains(p.getUUID()) && touching(p, gate, normal)) enter(p);
        }
        ServerLevel cage = server.getLevel(WitherGeometry.DIMENSION);
        if (!built || cage == null) return;
        if (cage.getGameTime() % 10 == 0) SetPiecePayload.send(cage, Piece.WITHER_BREACH, WitherGeometry.exit(), WitherGeometry.exit().add(0, 0, 1), 1, null);
        holdDown();
        if (exitAt != null && !exitOpen && exitOpensAt >= 0 && clock >= exitOpensAt) openExit(cage);
        boolean exitSteady = exitOpen && clock >= exitOpensAt + WitherGeometry.EXIT_OPEN_TICKS - 12;
        if (exitSteady && clock % 10 == 0) SetPiecePayload.send(cage, Piece.WITHER_BREACH, exitAt, exitAt.add(0, 0, -1), 1, null);
        for (ServerPlayer p : List.copyOf(MEMBERS.values())) if (p.isAlive() && inside(p)) {
            if (touching(p, WitherGeometry.exit(), new Vec3(0, 0, 1))) { leave(p); continue; }
            if (exitSteady && !down(p) && touching(p, exitAt, new Vec3(0, 0, -1))) { leave(p); continue; }
            if (p.getY() < WitherGeometry.ORIGIN.getY() - 5 || p.getY() > WitherGeometry.floor().y + WitherGeometry.FLIGHT_HEIGHT
                    || p.position().multiply(1, 0, 1).distanceTo(WitherGeometry.floor().multiply(1, 0, 1)) > WitherGeometry.LEASH + 7)
                move(p, cage, WitherGeometry.entrance(), 0, 0);
        }
        if (closeIn > 0) {
            boolean anyoneLeft = MEMBERS.values().stream().anyMatch(p -> p.isAlive() && inside(p));
            if (exitSteady && !anyoneLeft) closeIn = Math.min(closeIn, 20);
            if (--closeIn == 0) { reset(server, false); return; }
        }
        if (closeIn < 0 && boss != null) {
            if (boss.fighters().isEmpty()) emptyTicks++; else emptyTicks = 0;
            if (emptyTicks >= 400) resetAttempt(server);
        }
    }
    private static void collect(MinecraftServer server) {
        var data = WitherData.get(server);
        for (var entry : PRIZES.entrySet()) if (!entry.getKey().isRemoved() && !entry.getKey().getItem().isEmpty()) {
            data.stash(entry.getValue(), entry.getKey().getItem(), server.registryAccess()); entry.getKey().discard();
        }
        PRIZES.clear();
        for (var entry : PENDING_PRIZES.entrySet()) for (ItemStack stack : entry.getValue()) data.stash(entry.getKey(), stack, server.registryAccess());
        PENDING_PRIZES.clear();
    }
    private static void resetAttempt(MinecraftServer server) {
        for (ServerPlayer p : List.copyOf(MEMBERS.values())) if (p.isAlive() && inside(p)) sendHome(p);
        collect(server);
        if (boss != null) boss.discard();
        boss = null;
        ServerLevel cage = server.getLevel(WitherGeometry.DIMENSION);
        if (cage != null) holdCage(cage, false);
        built = false; emptyTicks = 0; MEMBERS.clear(); markers = Map.of();
        exitAt = null; exitOpensAt = -1; exitOpen = false;
        for (UUID id : List.copyOf(DOWN.keySet())) DOWN.remove(id);
    }
    public static void phase(int phase) {
        if (boss == null || !(boss.level() instanceof ServerLevel level)) return;
        var light = (phase == 2 ? net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN : net.minecraft.world.level.block.Blocks.SHROOMLIGHT).defaultBlockState();
        for (BlockPos at : markers.getOrDefault("wither:channel", List.of())) if (level.hasChunkAt(at)) level.setBlock(at, light, 3);
    }
    /** Server stops preserve the entrance. Victory and operator/test cleanup clear it. */
    public static void reset(MinecraftServer server, boolean preserveEntrance) {
        generation++;
        collect(server);
        for (ServerPlayer p : List.copyOf(MEMBERS.values())) { if (p.isAlive() && inside(p)) sendHome(p); else if (p.isAlive()) deliver(p); }
        resetAttempt(server);
        if (intro != null) intro.discard();
        intro = null; pending = false; ready = false; closeIn = -1;
        if (!preserveEntrance) {
            var source = WitherData.get(server).source;
            ServerLevel world = source == null ? null : server.getLevel(source.dimension());
            if (world != null) {
                // The breach at the summoning site slams shut.
                Vec3 gate = source.at().add(0, WitherGeometry.GATE_LIFT, 0);
                SetPiecePayload.sendSeeded(world, Piece.WITHER_OPEN, gate, gate.add(WitherGeometry.forward(source.yaw())), 1, null, 0, 1);
                world.playSound(null, gate.x, gate.y, gate.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 2F, .5F);
            }
        }
        if (forcedSource != null) for (ChunkPos at : SOURCE_HELD) forcedSource.setChunkForced(at.x, at.z, false);
        SOURCE_HELD.clear(); introSince = -1;
        forcedSource = null; sourceChunk = null; sourceForced = false;
        if (!preserveEntrance) WitherData.get(server).source(null);
        MOVING.clear(); LEAVE_FIRST.clear();
    }
}
