package com.jayptucker.heroesevolved.story.elle;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.structure.Blueprint;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Primatech's test floor, Elle Bishop's arena. It stands in a pocket dimension of its own and is rebuilt from the
 * {@code primatech_substation} blueprint before every fight, so banks a fight blew are whole again.
 *
 * <p>Six capacitor banks feed her. Each has a breaker lever: pull it and the bank is <b>cut off</b> (her shield thins
 * and its conduit stops surging); pull it while she is <b>siphoning</b> from that bank and the current comes back up
 * the arc into her (the backfire, in {@link ElleEntity}), which <b>blows</b> the bank for the rest of the fight. A
 * pulse from a player's power (Electrokinesis's Blackout, Radiation's EMP) over a bank shorts it too, for as long as
 * the field lasts: {@link GridDisruption#jammed} is read every tick.</p>
 */
public final class PrimatechArena {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "primatech_substation"));
    /** The floor block in the middle of the room. */
    public static final BlockPos ORIGIN = new BlockPos(256, 64, 256);
    /** Allies within this many blocks of the subject come too. */
    public static final double PARTY = 16;
    /** A fight that runs this long ends: she has her data. */
    public static final int TIME_LIMIT = 20 * 600;

    public enum Bank { LIVE, CUT, BLOWN }

    record ReturnPoint(ResourceKey<Level> dimension, Vec3 at, float yaw, float pitch) {}

    private static final Map<UUID, ReturnPoint> RETURNS = new HashMap<>();
    private static final Set<UUID> MOVING = new HashSet<>();
    private static final Map<UUID, List<ItemStack>> GIFTS = new HashMap<>();
    private static Map<String, List<BlockPos>> marks = Map.of();
    private static final Bank[] BANKS = new Bank[PrimatechGeometry.BANK_COUNT];
    private static int liveMask = (1 << PrimatechGeometry.BANK_COUNT) - 1;
    private static boolean open, dark;
    private static int closeIn = -1;

    static { Arrays.fill(BANKS, Bank.LIVE); }

    private PrimatechArena() {}

    public static ServerLevel level(MinecraftServer server) { return server.getLevel(DIMENSION); }
    public static boolean available(MinecraftServer server) { return level(server) != null; }
    public static boolean inArena(Entity entity) { return entity.level().dimension().equals(DIMENSION); }
    public static boolean open() { return open; }
    public static boolean dark() { return dark; }
    public static boolean moving(Player player) { return MOVING.contains(player.getUUID()); }

    /** The middle floor block's bottom centre: what {@link PrimatechGeometry} measures from. */
    public static Vec3 floor() { return Vec3.atBottomCenterOf(ORIGIN); }
    /** The middle of the room at standing height. */
    public static Vec3 center() { return floor().add(0, 1, 0); }
    /** A cell relative to the middle floor block. */
    public static BlockPos cell(int x, int y, int z) { return ORIGIN.offset(x, y, z); }
    public static BlockPos breaker(int i) { int[] b = PrimatechGeometry.breaker(i); return cell(b[0], b[1], b[2]); }
    public static List<BlockPos> marked(String marker) { return marks.getOrDefault(marker, List.of()); }

    // ------------------------------------------------------------------
    // Building and entering
    // ------------------------------------------------------------------

    /** Builds (or rebuilds) the room. Its chunks stay loaded from then on, so the next Elle is ticking the moment she arrives. */
    public static void build(ServerLevel level) {
        BlockPos min = cell(-13, 0, -17), max = cell(13, 11, 13);
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                level.getChunk(cx, cz);
                level.setChunkForced(cx, cz, true);
            }
        AABB box = new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1, 1, 1)).inflate(4);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) entity.discard();
        marks = Blueprint.load("primatech_substation").place(level, ORIGIN, Rotation.NONE);
        Arrays.fill(BANKS, Bank.LIVE);
        liveMask = (1 << PrimatechGeometry.BANK_COUNT) - 1;
        dark = false;
    }

    /**
     * Brings the subject (and the people beside them) onto the test floor, and Elle with them. Returns the Elle there, or
     * null when the room can't be used.
     */
    static ElleEntity enter(ServerPlayer subject) {
        MinecraftServer server = subject.getServer();
        ServerLevel room = level(server);
        if (room == null || open) return null;
        build(room);
        List<ServerPlayer> party = new ArrayList<>();
        party.add(subject);
        for (ServerPlayer near : subject.serverLevel().players())
            if (near != subject && near.distanceToSqr(subject) < PARTY * PARTY && ElleStoryService.eligible(near)) party.add(near);
        ElleEntity elle = ModEntities.ELLE.get().create(room);
        if (elle == null) return null;
        Vec3 at = marked("spawn:elle").stream().findFirst().map(Vec3::atBottomCenterOf).orElse(center().add(0, 0, -6));
        elle.moveTo(at.x, at.y, at.z, 0, 0);
        elle.subject(subject);
        if (!room.addFreshEntity(elle)) return null;
        open = true; closeIn = -1;
        Vec3 start = marked("spawn:player").stream().findFirst().map(Vec3::atBottomCenterOf).orElse(center().add(0, 0, 8));
        for (int i = 0; i < party.size(); i++) {
            ServerPlayer player = party.get(i);
            RETURNS.put(player.getUUID(), new ReturnPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
            double side = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 1.4;
            Vec3 to = start.add(side, 0, 0);
            MOVING.add(player.getUUID());
            try { player.teleportTo(room, to.x, to.y, to.z, 180, 0); }
            finally { MOVING.remove(player.getUUID()); }
            player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
        }
        room.playSound(null, center().x, center().y, center().z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.4F, 0.6F);
        return elle;
    }

    // ------------------------------------------------------------------
    // The banks
    // ------------------------------------------------------------------

    public static Bank bank(int i) { return BANKS[i]; }
    /** Which banks feed her right now, as a bit mask. */
    public static int liveMask() { return liveMask; }
    public static boolean live(int i) { return PrimatechGeometry.live(liveMask, i); }
    public static int liveCount() { return Integer.bitCount(liveMask); }

    /** The breaker lever is closed (up). A missing lever counts as open. */
    public static boolean closed(ServerLevel level, int i) {
        BlockState state = level.getBlockState(breaker(i));
        return state.getBlock() instanceof LeverBlock && state.getValue(LeverBlock.POWERED);
    }

    /** Throws bank {@code i}'s breaker (as a player pulling it would). For tests and the reboot. */
    public static void setBreaker(ServerLevel level, int i, boolean closed) {
        BlockPos pos = breaker(i);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LeverBlock)) {
            state = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(LeverBlock.FACING, PrimatechGeometry.facing(i));
        }
        level.setBlock(pos, state.setValue(LeverBlock.POWERED, closed), Block.UPDATE_ALL);
    }

    /**
     * Reads every breaker and every pulse. A bank whose lever is open, or that sits inside a player's pulse, is cut off;
     * a blown bank stays dead. Returns the banks that went dead this tick (as a mask).
     */
    static int pollBanks(ServerLevel level) {
        int mask = 0;
        for (int i = 0; i < PrimatechGeometry.BANK_COUNT; i++) {
            if (BANKS[i] == Bank.BLOWN) continue;
            boolean on = closed(level, i) && !GridDisruption.jammed(level, breaker(i));
            BANKS[i] = on ? Bank.LIVE : Bank.CUT;
            if (on) mask |= PrimatechGeometry.bit(i);
        }
        int died = liveMask & ~mask, woke = mask & ~liveMask;
        liveMask = mask;
        for (int i = 0; i < PrimatechGeometry.BANK_COUNT; i++) {
            if (PrimatechGeometry.live(died, i)) shorted(level, i, false);
            if (PrimatechGeometry.live(woke, i)) bulbs(level, i, true);
        }
        return died;
    }

    /** A bank goes dead: sparks and smoke at its face, its lights die. */
    static void shorted(ServerLevel level, int i, boolean blown) {
        bulbs(level, i, false);
        Vec3 face = PrimatechGeometry.bank(floor(), i);
        SetPiecePayload.send(level, Piece.ELLE_SHORT, face, PrimatechGeometry.rod(floor(), i), blown ? 2 : 1, null);
        level.playSound(null, face.x, face.y, face.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, blown ? 0.6F : 1.4F);
        level.playSound(null, face.x, face.y, face.z, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    /** The backfire wrecks bank {@code i}: it stays dead for the rest of the fight, whatever its lever says. */
    static void blow(ServerLevel level, int i) {
        BANKS[i] = Bank.BLOWN;
        liveMask &= ~PrimatechGeometry.bit(i);
        shorted(level, i, true);
        for (BlockPos rod : marked("rod:" + i)) level.setBlock(rod, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos bulb : marked("bulb:" + i)) level.setBlock(bulb, Blocks.WAXED_OXIDIZED_COPPER_BULB.defaultBlockState(), Block.UPDATE_CLIENTS);
        Vec3 face = PrimatechGeometry.bank(floor(), i);
        CameraShakePayload.sendAround(level, face, 32, 0.45F, 8);
    }

    /** Elle forces a cut bank's breaker shut: it feeds her again. */
    static void reboot(ServerLevel level, int i) {
        if (BANKS[i] == Bank.BLOWN) return;
        setBreaker(level, i, true);
        BANKS[i] = Bank.LIVE;
        if (!GridDisruption.jammed(level, breaker(i))) liveMask |= PrimatechGeometry.bit(i);
        bulbs(level, i, true);
    }

    private static void bulbs(ServerLevel level, int i, boolean lit) {
        for (BlockPos pos : marked("bulb:" + i)) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit)
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    /** The third phase: the ceiling lights go out (the four red emergency lights stay). */
    static void lightsOut(ServerLevel level) {
        dark = true;
        for (BlockPos pos : marked("light")) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.LIT)) level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), Block.UPDATE_CLIENTS);
        }
    }

    /** Standing on rubber: the floor's current can't reach you. */
    public static boolean onMat(Entity entity) {
        BlockPos below = BlockPos.containing(entity.getX(), entity.getY() - 0.2, entity.getZ());
        return entity.level().getBlockState(below).is(Blocks.BLACK_WOOL);
    }

    /** On (or just over) the test floor's surface, where floor current reaches. */
    public static boolean onFloor(Entity entity) {
        return entity.onGround() && Math.abs(entity.getY() - (ORIGIN.getY() + 1)) < 0.7;
    }

    /** Inside the room's walls. */
    public static boolean inside(Vec3 at) {
        Vec3 c = floor();
        return Math.abs(at.x - c.x) < PrimatechGeometry.HALF && Math.abs(at.z - c.z) < PrimatechGeometry.HALF;
    }

    // ------------------------------------------------------------------
    // Ending
    // ------------------------------------------------------------------

    /** The fight is over: in {@code delay} ticks everyone goes home. */
    static void close(int delay) {
        if (!open) return;
        closeIn = Math.max(1, delay);
    }

    public static void tick(MinecraftServer server) {
        ServerLevel room = level(server);
        if (room == null) return;
        if (closeIn > 0 && --closeIn == 0) {
            for (ServerPlayer player : List.copyOf(room.players())) sendHome(player);
            open = false; closeIn = -1;
            return;
        }
        if (!open) {
            for (ServerPlayer player : List.copyOf(room.players())) if (!player.isSpectator()) sendHome(player);
            return;
        }
        pollBanks(room);
        for (ServerPlayer player : List.copyOf(room.players())) {
            if (player.getY() < ORIGIN.getY() - 12) {
                Vec3 to = center();
                player.teleportTo(to.x, to.y, to.z); player.fallDistance = 0; player.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /** Back where they stood when she took them; world spawn when that's gone. */
    public static void sendHome(ServerPlayer player) {
        ReturnPoint point = RETURNS.remove(player.getUUID());
        MinecraftServer server = player.getServer();
        ServerLevel level = point == null ? null : server.getLevel(point.dimension());
        if (level == null || level.dimension().equals(DIMENSION)) { level = server.overworld(); point = null; }
        Vec3 to = point == null ? Vec3.atBottomCenterOf(level.getSharedSpawnPos()) : point.at();
        level.getChunk(BlockPos.containing(to));
        if (!level.noCollision(player, player.getBoundingBox().move(to.subtract(player.position()))))
            to = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        MOVING.add(player.getUUID());
        try { player.teleportTo(level, to.x, to.y, to.z, point == null ? player.getYRot() : point.yaw(), point == null ? 0 : point.pitch()); }
        finally { MOVING.remove(player.getUUID()); }
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
        ElleVoice.narrate(player, "story.heroesevolved.elle.narrate.home");
        List<ItemStack> gifts = GIFTS.remove(player.getUUID());
        if (gifts != null) for (ItemStack stack : gifts) if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** Someone left by dying or logging out: they no longer need a ticket home. */
    public static void forget(ServerPlayer player) { RETURNS.remove(player.getUUID()); }

    /** For tests and server stops: no fight, nobody owed anything. */
    public static void reset() {
        RETURNS.clear(); MOVING.clear(); GIFTS.clear();
        open = false; dark = false; closeIn = -1;
        Arrays.fill(BANKS, Bank.LIVE);
        liveMask = (1 << PrimatechGeometry.BANK_COUNT) - 1;
    }

    /** Gives {@code stack} now, or when they get home if their pockets are full. */
    static void gift(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().add(stack) && stack.isEmpty()) return;
        if (inArena(player) && open) GIFTS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(stack.copy());
        else player.drop(stack.copy(), false);
    }
}
