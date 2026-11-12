package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.structure.Blueprint;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Gray &amp; Sons, Sylar's clock shop: the Watchmaker's arena. It stands in a pocket dimension of its own and is built
 * fresh from the {@code sylar_shop} blueprint before every fight, so whatever the last fight broke (the floor his slam
 * ripped up, the glass cases, the doors) is whole again.
 *
 * <p>When a hunt turns into a fight, {@link #enter} pulls the quarry and anyone fighting beside them into the shop and
 * remembers where each of them stood. While the front door is open (the first phase), stepping out into the street is
 * a way home: it counts as getting away. In the second phase the doors lock. When the fight ends, {@link #close} sends
 * everyone back where they came from, and any power he took comes back with them.</p>
 */
public final class WatchmakerArena {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "watchmaker_shop"));
    /** Where the clock face's middle lands: the floor block under the arena's centre. Well away from x 0. */
    public static final BlockPos ORIGIN = new BlockPos(256, 64, 0);
    /** Half the shop floor's width, from the clock face's middle to the inside of a wall. */
    public static final double HALF = 13;
    /** The clock face's radius on the floor, which the time-stop rings close across. */
    public static final double FACE = 9.5;
    /** Allies within this many blocks of the quarry come into the shop with them. */
    public static final double PARTY = 16;

    record ReturnPoint(ResourceKey<Level> dimension, Vec3 at, float yaw, float pitch) {}

    private static final Map<UUID, ReturnPoint> RETURNS = new HashMap<>();
    private static final Set<UUID> STOLEN = new HashSet<>();
    private static final Set<UUID> MOVING = new HashSet<>();
    private static Map<String, List<BlockPos>> marks = Map.of();
    private static boolean open, locked, backOpen;
    private static int closeIn = -1;

    private WatchmakerArena() {}

    public static ServerLevel level(MinecraftServer server) { return server.getLevel(DIMENSION); }
    public static boolean available(MinecraftServer server) { return level(server) != null; }
    public static boolean inShop(Entity entity) { return entity.level().dimension().equals(DIMENSION); }
    /** A fight is on in the shop (or just ended and its people are about to go home). */
    public static boolean open() { return open; }
    public static boolean locked() { return locked; }
    public static boolean backOpen() { return backOpen; }
    /** Sylar took this player's power in the fight that is on: they have it back when it ends. */
    public static boolean stolen(Player player) { return STOLEN.contains(player.getUUID()); }
    static void steal(ServerPlayer player) { STOLEN.add(player.getUUID()); }
    /** This player is being moved into or out of the shop by the fight itself, not leaving it. */
    public static boolean moving(Player player) { return MOVING.contains(player.getUUID()); }

    /** The middle of the clock face, at standing height. */
    public static Vec3 center() { return Vec3.atBottomCenterOf(ORIGIN.above()); }
    /** A blueprint cell's world position (x and z as drawn, y as a layer). */
    public static BlockPos cell(int x, int y, int z) { return ORIGIN.offset(x - 14, y - 1, z - 54); }

    static List<BlockPos> marked(String marker) { return marks.getOrDefault(marker, List.of()); }

    // ------------------------------------------------------------------
    // Building and entering
    // ------------------------------------------------------------------

    /**
     * Builds (or rebuilds) the shop. Its few chunks stay loaded from then on (the game saves that), so the next fight's
     * Sylar is ticking the moment he arrives instead of waiting on the chunk's entities to load.
     */
    public static void build(ServerLevel level) {
        // The shop and its alley: blueprint x 0..28, z 0..72 around the anchor (14, 54).
        BlockPos min = cell(0, 0, 0), max = cell(28, 12, 72);
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                level.getChunk(cx, cz);
                level.setChunkForced(cx, cz, true);
            }
        AABB box = new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1, 1, 1)).inflate(4);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) entity.discard();
        marks = Blueprint.load("sylar_shop").place(level, ORIGIN, Rotation.NONE);
        // A clock on the wall over every case, and over the bench.
        for (var entry : marks.entrySet()) {
            if (!entry.getKey().startsWith("frame:")) continue;
            Direction facing = Direction.byName(entry.getKey().substring(6));
            for (BlockPos pos : entry.getValue()) {
                ItemFrame frame = new ItemFrame(level, pos, facing == null ? Direction.NORTH : facing);
                frame.setItem(new ItemStack(Items.CLOCK), false);
                frame.setInvulnerable(true);
                if (frame.survives()) level.addFreshEntity(frame);
            }
        }
        locked = false; backOpen = false;
    }

    /**
     * Pulls {@code quarry} (and the people fighting beside them) into the shop, and a new Sylar with them. Returns the
     * Sylar in the shop, or null when the shop can't be used (then the fight carries on where it is).
     */
    static SylarEntity enter(SylarEntity hunter, ServerPlayer quarry) {
        MinecraftServer server = quarry.getServer();
        ServerLevel shop = level(server);
        if (shop == null || open) return null;
        build(shop);
        List<ServerPlayer> party = new ArrayList<>();
        party.add(quarry);
        for (ServerPlayer near : quarry.serverLevel().players())
            if (near != quarry && near.distanceToSqr(quarry) < PARTY * PARTY && SylarStoryService.eligible(near)) party.add(near);
        SylarEntity sylar = ModEntities.SYLAR.get().create(shop);
        if (sylar == null) return null;
        BlockPos sylarAt = marked("spawn:sylar").stream().findFirst().orElse(cell(14, 2, 47));
        sylar.moveTo(Vec3.atBottomCenterOf(sylarAt).x, sylarAt.getY(), Vec3.atBottomCenterOf(sylarAt).z, 0, 0);
        sylar.watch(quarry);
        sylar.arena(true);
        if (!shop.addFreshEntity(sylar)) return null;
        open = true; closeIn = -1;
        BlockPos start = marked("spawn:quarry").stream().findFirst().orElse(cell(14, 2, 61));
        for (int i = 0; i < party.size(); i++) {
            ServerPlayer player = party.get(i);
            RETURNS.put(player.getUUID(), new ReturnPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
            // His grip, then the world folds: a clock face opens behind them and they are gone.
            SetPiecePayload.send(player.serverLevel(), Piece.WATCH_PULL, player.position(), hunter.getEyePosition(), 1, player);
            double side = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 1.6;
            Vec3 to = Vec3.atBottomCenterOf(start).add(side, 0, 0);
            MOVING.add(player.getUUID());
            try { player.teleportTo(shop, to.x, to.y, to.z, 0, 0); }
            finally { MOVING.remove(player.getUUID()); }
            player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
            SetPiecePayload.send(shop, Piece.WATCH_PULL, player.position(), sylar.getEyePosition(), 2, player);
        }
        shop.playSound(null, center().x, center().y, center().z, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.4F, 0.6F);
        hunter.discard();
        return sylar;
    }

    // ------------------------------------------------------------------
    // What the fight does to the shop
    // ------------------------------------------------------------------

    /** The Hunger: the front door is barred and the clocks run faster. */
    static void lock(ServerLevel level) {
        if (locked) return;
        locked = true;
        List<BlockPos> door = marked("door:front");
        for (BlockPos pos : door) level.setBlock(pos, Blocks.IRON_BARS.defaultBlockState(), 3);
        Vec3 at = door.isEmpty() ? center() : Vec3.atCenterOf(door.get(door.size() / 2));
        SetPiecePayload.send(level, Piece.WATCH_DOORS, at, center(), 1, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 1.6F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 0.6F);
        CameraShakePayload.sendAround(level, at, 40, 0.5F, 10);
        // Anyone standing in the doorway is put back inside.
        for (ServerPlayer player : level.players())
            if (!door.isEmpty() && player.position().z > Vec3.atCenterOf(door.getFirst()).z - 1.5)
                player.teleportTo(player.getX(), player.getY(), Vec3.atCenterOf(door.getFirst()).z - 2);
    }

    /** He runs: the boarded back door bursts outward into the alley. */
    static void burstBack(ServerLevel level) {
        if (backOpen) return;
        backOpen = true;
        for (BlockPos pos : marked("door:back")) level.destroyBlock(pos, false);
        Vec3 at = Vec3.atCenterOf(cell(4, 3, 40));
        SetPiecePayload.send(level, Piece.WATCH_SLAM, at.add(0, -1.5, 0), at.add(0, 0, -3), 2.0F, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.4F, 0.7F);
    }

    /** The waypoints of his run: through the back door, then down the alley to where he gets away. */
    static List<Vec3> chaseRoute() {
        return List.of(Vec3.atBottomCenterOf(cell(4, 2, 43)), Vec3.atBottomCenterOf(cell(4, 2, 38)),
                Vec3.atBottomCenterOf(cell(4, 2, 20)), alleyEnd());
    }

    static Vec3 alleyEnd() {
        return marked("alley:end").stream().findFirst().map(Vec3::atBottomCenterOf).orElse(Vec3.atBottomCenterOf(cell(4, 2, 3)));
    }

    /** Rips up the floor around {@code at}: the top boards are thrown and the sub-floor shows. The gear rails hold. */
    static void ripFloor(ServerLevel level, Vec3 at, double radius) {
        BlockPos middle = BlockPos.containing(at.x, ORIGIN.getY(), at.z);
        int r = (int) Math.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-r, 0, -r), middle.offset(r, 0, r))) {
            double d = Math.hypot(pos.getX() + .5 - at.x, pos.getZ() + .5 - at.z);
            if (d > radius || Math.abs(pos.getX() - ORIGIN.getX()) > HALF || pos.getZ() - ORIGIN.getZ() > HALF
                    || ORIGIN.getZ() - pos.getZ() > HALF) continue;
            var state = level.getBlockState(pos);
            if (state.is(Blocks.WAXED_CUT_COPPER) || state.isAir()) continue;
            // The middle tears out; the edge cracks and lifts.
            boolean torn = d < radius * 0.65 || Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, 3) == 0;
            level.setBlock(pos, torn ? Blocks.AIR.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 3);
        }
    }

    /** Glass cases near {@code at} shatter. */
    static void shatter(ServerLevel level, Vec3 at, double radius) {
        for (BlockPos pos : marked("case")) {
            if (Vec3.atCenterOf(pos).distanceToSqr(at) > radius * radius || !level.getBlockState(pos).is(Blocks.GLASS)) continue;
            level.destroyBlock(pos, false);
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.9F, 0.8F);
        }
    }

    /** The two gear rails, each from one geared post to the other, at standing height. */
    static List<Vec3[]> rails() {
        List<Vec3[]> rails = new ArrayList<>();
        for (String key : List.of("rail:n", "rail:s")) {
            List<BlockPos> posts = marked(key);
            if (posts.size() >= 2) rails.add(new Vec3[] {Vec3.atBottomCenterOf(posts.get(0)), Vec3.atBottomCenterOf(posts.get(1))});
        }
        if (rails.isEmpty()) {
            rails.add(new Vec3[] {Vec3.atBottomCenterOf(cell(2, 2, 44)), Vec3.atBottomCenterOf(cell(26, 2, 44))});
            rails.add(new Vec3[] {Vec3.atBottomCenterOf(cell(2, 2, 64)), Vec3.atBottomCenterOf(cell(26, 2, 64))});
        }
        return rails;
    }

    /** Inside the shop's walls (not the alley or the street). */
    static boolean inside(Vec3 at) {
        Vec3 c = center();
        return Math.abs(at.x - c.x) < HALF + .5 && Math.abs(at.z - c.z) < HALF + .5;
    }

    // ------------------------------------------------------------------
    // Ending
    // ------------------------------------------------------------------

    /** The fight is over: powers come back now, and in {@code delay} ticks everyone goes home. */
    static void close(MinecraftServer server, int delay) {
        if (!open) return;
        STOLEN.clear();
        closeIn = Math.max(1, delay);
    }

    public static void tick(MinecraftServer server) {
        ServerLevel shop = level(server);
        if (shop == null) return;
        if (closeIn > 0 && --closeIn == 0) {
            for (ServerPlayer player : List.copyOf(shop.players())) sendHome(player);
            open = false; closeIn = -1;
            return;
        }
        if (!open && !shop.players().isEmpty())
            for (ServerPlayer player : List.copyOf(shop.players())) if (!player.isSpectator()) sendHome(player);
        if (!open) return;
        for (ServerPlayer player : List.copyOf(shop.players())) {
            // Nobody falls out of the world: the shop floats in the dark.
            if (player.getY() < ORIGIN.getY() - 12) {
                Vec3 to = center();
                player.teleportTo(to.x, to.y, to.z); player.fallDistance = 0; player.setDeltaMovement(Vec3.ZERO);
            }
            // While the door stands open, the street is a way out.
            if (!locked && closeIn < 0 && player.getZ() > cell(14, 2, 71).getZ() && !player.isSpectator())
                SylarStoryService.leftShop(player);
        }
    }

    /** Back where they stood when he took them; world spawn when that's gone. */
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
        SetPiecePayload.send(level, Piece.WATCH_PULL, player.position(), player.getEyePosition(), 3, player);
        SylarVoice.narrate(player, "story.heroesevolved.sylar.narrate.home");
        List<ItemStack> gifts = GIFTS.remove(player.getUUID());
        if (gifts != null) for (ItemStack stack : gifts) if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** Someone left the shop by dying or logging out: they no longer need a ticket home. */
    public static void forget(ServerPlayer player) { RETURNS.remove(player.getUUID()); STOLEN.remove(player.getUUID()); }

    /** For tests and server stops: no fight, nobody owed anything. */
    public static void reset(MinecraftServer server) {
        RETURNS.clear(); STOLEN.clear(); MOVING.clear();
        open = false; locked = false; backOpen = false; closeIn = -1;
    }

    private static final Map<UUID, List<ItemStack>> GIFTS = new HashMap<>();

    /** Gives {@code stack} now, or when they get home if their pockets are full (nothing is left lying in the shop). */
    static void gift(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().add(stack) && stack.isEmpty()) return;
        if (inShop(player) && open) GIFTS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(stack.copy());
        else player.drop(stack.copy(), false);
    }
}
