package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.story.bunker.BunkerLogs;
import com.jayptucker.heroesevolved.structure.Blueprint;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.jayptucker.heroesevolved.story.sylar.SylarGeometry.*;

/**
 * The Collection: Sylar's hall in a pocket dimension ({@link SylarGeometry#DIMENSION}), rebuilt from the
 * {@code the_collection} blueprint before every fight, so whatever the last fight tore up, froze or broke is whole again.
 *
 * <p>The story opens it the first time ({@link SylarStory}: the prisoner is out, and his hand is on your wrist), and a
 * Repair Ticket ({@link RepairTicketItem}) every time after: {@link #open} pulls its holder and everyone within
 * {@link SylarGeometry#PARTY} blocks into the hall, remembers where each of them stood (in {@link SylarData}, so a restart
 * still sends them back), and starts a fresh Sylar. When the fight ends, {@link #close} sends everyone home, and any
 * power he took comes back at once. One fight at a time, server-wide.</p>
 */
public final class CollectionArena {
    public enum Outcome { BEATEN, EVERYONE_DOWN, TIME_UP, ABANDONED }

    private static final Set<UUID> STOLEN = new HashSet<>();
    private static final Set<UUID> MOVING = new HashSet<>();
    private static final Map<UUID, List<ItemStack>> GIFTS = new HashMap<>();
    private static final Map<ItemEntity, UUID> PRIZES = new HashMap<>();
    private static Map<String, List<BlockPos>> marks = Map.of();
    private static boolean open, byStory;
    private static int closeIn = -1, coverAge = -1;
    private static UUID opener;
    private static SylarEntity sylar;

    private CollectionArena() {}

    public static ServerLevel level(MinecraftServer server) { return server.getLevel(DIMENSION); }
    public static boolean inCollection(Entity entity) { return entity.level().dimension().equals(DIMENSION); }
    /** A fight is on (or has just ended and its people are about to go home). */
    public static boolean open() { return open; }
    /** The Sylar of the fight that is on, if any. */
    public static SylarEntity sylar() { return sylar != null && sylar.isAlive() ? sylar : null; }
    /** He holds this player's power in a case: they have it back when the case breaks or the fight ends. */
    public static boolean stolen(Player player) { return STOLEN.contains(player.getUUID()); }
    static void steal(ServerPlayer player) { STOLEN.add(player.getUUID()); }
    static void unsteal(UUID player) { STOLEN.remove(player); }
    /** This player is being moved into or out of the hall by the fight itself. */
    public static boolean moving(Player player) { return MOVING.contains(player.getUUID()); }
    static List<BlockPos> marked(String marker) { return marks.getOrDefault(marker, List.of()); }
    /** How long a fight may run before he closes up. */
    static int fightTicks() { return HeroesEvolvedConfig.COMMON.sylarFightMinutes.get() * 1200; }

    // ------------------------------------------------------------------
    // Building and entering
    // ------------------------------------------------------------------

    /** Builds (or rebuilds) the hall. Its chunks stay loaded from then on, so the next fight's Sylar ticks at once. */
    public static void build(ServerLevel level) {
        BlockPos min = cell(0, 0, 0), max = cell(SIZE - 1, HEIGHT - 1, SIZE - 1);
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                level.getChunk(cx, cz);
                level.setChunkForced(cx, cz, true);
            }
        AABB box = new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1, 1, 1)).inflate(4);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) e.discard();
        marks = Blueprint.load("the_collection").place(level, ORIGIN, Rotation.NONE);
        coverAge = -1;
    }

    /** Why a ticket can't be used here and now, or null if it can. */
    public static Component refusal(ServerPlayer player) {
        if (!HeroesEvolvedConfig.COMMON.sylarEnabled.get()) return Component.translatable("story.heroesevolved.sylar.ticket.disabled");
        if (level(player.getServer()) == null) return Component.translatable("story.heroesevolved.sylar.ticket.disabled");
        if (open) return Component.translatable("story.heroesevolved.sylar.ticket.busy");
        if (inCollection(player)) return Component.translatable("story.heroesevolved.sylar.ticket.busy");
        return null;
    }

    /** Pulls {@code holder} and everyone near them into the hall, and starts the fight. Returns the Sylar, or null. */
    public static SylarEntity open(ServerPlayer holder) { return open(holder, false); }

    /**
     * As {@link #open(ServerPlayer)}; {@code story} is the story's own way in ({@link SylarStory}: his hand on your wrist,
     * not a ticket in it), which only changes what everyone is told.
     */
    public static SylarEntity open(ServerPlayer holder, boolean story) {
        if (refusal(holder) != null) return null;
        MinecraftServer server = holder.getServer();
        ServerLevel hall = level(server);
        build(hall);
        List<ServerPlayer> party = new ArrayList<>();
        party.add(holder);
        for (ServerPlayer near : holder.serverLevel().players())
            if (near != holder && !near.isSpectator() && near.distanceToSqr(holder) < PARTY * PARTY) party.add(near);
        SylarEntity boss = ModEntities.SYLAR.get().create(hall);
        if (boss == null) return null;
        Vec3 at = center();
        boss.moveTo(at.x, at.y, at.z, 0, 0);
        boss.setYHeadRot(0);
        if (!hall.addFreshEntity(boss)) return null;
        open = true; closeIn = -1; opener = holder.getUUID(); sylar = boss; byStory = story;
        SylarData data = SylarData.get(server);
        boolean refight = data.wins(holder.getUUID()) > 0;
        for (int i = 0; i < party.size(); i++) {
            ServerPlayer player = party.get(i);
            data.home(player.getUUID(), new SylarData.Home(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
            SetPiecePayload.send(player.serverLevel(), Piece.SYLAR_ARRIVE, player.position(), player.getEyePosition(), 1, player);
            Vec3 to = arrival(i);
            move(player, hall, to, 180, 0);
            SetPiecePayload.send(hall, Piece.SYLAR_ARRIVE, player.position(), player.getEyePosition(), 2, player);
        }
        boss.begin(party, refight);
        hall.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.6F, 0.5F);
        CameraShakePayload.sendAround(hall, at, 48, 0.3F, 10);
        for (ServerPlayer player : party) {
            SylarVoice.narrate(player, refight ? "story.heroesevolved.sylar.narrate.again"
                    : story ? "story.heroesevolved.sylar.narrate.taken" : "story.heroesevolved.sylar.narrate.arrive");
            SylarVoice.say(player, refight ? "story.heroesevolved.sylar.say.again" : "story.heroesevolved.sylar.say.meet");
            String yours = SylarMoves.power(player) == null ? "story.heroesevolved.sylar.say.powerless"
                    : "story.heroesevolved.sylar.say.want." + SylarMoves.power(player).getPath();
            if (!refight) SylarVoice.say(player, yours);
        }
        return boss;
    }

    private static void move(ServerPlayer player, ServerLevel level, Vec3 to, float yaw, float pitch) {
        MOVING.add(player.getUUID());
        try {
            player.teleportTo(level, to.x, to.y, to.z, yaw, pitch);
            player.absMoveTo(to.x, to.y, to.z);
        } finally { MOVING.remove(player.getUUID()); }
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
    }

    // ------------------------------------------------------------------
    // What the fight does to the hall
    // ------------------------------------------------------------------

    /** The hall's copper bulbs go out (the second phase) or come back on (the end). */
    static void lights(ServerLevel level, boolean on) {
        for (BlockPos pos : marked("lamp")) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(CopperBulbBlock.LIT)) level.setBlock(pos, state.setValue(CopperBulbBlock.LIT, on), 2);
        }
        Vec3 c = center();
        level.playSound(null, c.x, c.y + 10, c.z, on ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, 2.0F, 0.6F);
    }

    /** The last phase: four pillars rise from the floor on the diagonals (after their outlines warn whoever stands there). */
    static void raiseCover(ServerLevel level) {
        coverAge = 0;
        for (int i = 0; i < 4; i++) {
            Vec3 at = Vec3.atBottomCenterOf(cover(i));
            SetPiecePayload.send(level, Piece.SYLAR_COVER, at, at.add(0, COVER_HEIGHT, 0), COVER_HEIGHT, null);
        }
    }

    /** The chance a re-fight's win leaves each fighter the record of his theme. */
    public static final float RECORD_CHANCE = 0.10F;

    /** How long the pillars' outlines show before stone moves, and the ticks between each course they rise. */
    public static final int COVER_WARN = 12, COVER_STEP = 4;

    private static void tickCover(ServerLevel level) {
        if (coverAge < 0) return;
        int age = coverAge++;
        if (age < COVER_WARN || (age - COVER_WARN) % COVER_STEP != 0) return;
        int course = (age - COVER_WARN) / COVER_STEP;
        if (course >= COVER_HEIGHT) { coverAge = -1; return; }
        for (int i = 0; i < 4; i++) {
            BlockPos base = cover(i);
            Vec3 middle = Vec3.atBottomCenterOf(base);
            // Whoever stands on its footprint is pushed off it first.
            for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(base).inflate(1.6, COVER_HEIGHT, 1.6), e -> !(e instanceof SylarEntity))) {
                Vec3 out = SylarEntity.flat(e.position().subtract(middle));
                Vec3 to = middle.add(out.scale(2.6)).add(0, e.getY() - middle.y, 0);
                SylarMoves.place(e, to);
            }
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = base.offset(dx, course, dz);
                BlockState state = course == COVER_HEIGHT - 1
                        ? dx == 0 && dz == 0 ? Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(CopperBulbBlock.LIT, true) : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                        : course == 2 || course == 5 ? Blocks.WAXED_CUT_COPPER.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                level.setBlock(p, state, 3);
            }
            level.playSound(null, middle.x, middle.y + course, middle.z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 0.5F);
        }
        CameraShakePayload.sendAround(level, center(), 40, 0.2F, 4);
    }

    // ------------------------------------------------------------------
    // Ending
    // ------------------------------------------------------------------

    /** The fight is over without his death (everyone down, time up): a line, then everyone goes home. */
    public static void end(MinecraftServer server, Outcome outcome) {
        if (!open || closeIn > 0) return;
        SylarEntity boss = sylar;
        if (boss != null) {
            for (ServerPlayer p : boss.bar().getPlayers())
                SylarVoice.say(p, outcome == Outcome.TIME_UP ? "story.heroesevolved.sylar.say.closing" : "story.heroesevolved.sylar.say.everyone_down");
            boss.bar().removeAllPlayers();
            boss.discard();
        }
        close(server, outcome == Outcome.TIME_UP ? 60 : 20);
    }

    /**
     * He is dead. Each fighter beating him for the first time gets his watch and his ledger; everyone gets a brass
     * movement (what a re-fight ticket is made from). The prizes land where he fell; whatever is still lying there when
     * the hall closes goes to its owner's pocket. The ticket that opened the fight is torn.
     */
    static void defeated(MinecraftServer server, SylarEntity boss, UUID killer) {
        if (!open) return;
        ServerLevel hall = level(server);
        SylarData data = SylarData.get(server);
        data.defeated();
        Vec3 at = boss.position().add(0, 0.5, 0);
        for (ServerPlayer player : boss.party()) {
            if (player.isRemoved() || player.level() != hall) continue;
            boolean first = data.wins(player.getUUID()) == 0;
            List<ItemStack> prizes = new ArrayList<>();
            if (first) {
                prizes.add(new ItemStack(ModItems.SYLARS_WATCH.get()));
                prizes.add(ledger(server));
            }
            prizes.add(new ItemStack(ModItems.BRASS_MOVEMENT.get()));
            // Beaten again: now and then he leaves the record of his theme.
            if (!first && hall.random.nextFloat() < RECORD_CHANCE) prizes.add(new ItemStack(ModItems.MUSIC_DISC_THE_COLLECTION.get()));
            for (ItemStack stack : prizes) {
                ItemEntity item = new ItemEntity(hall, at.x, at.y, at.z, stack);
                item.setTarget(player.getUUID());
                item.setPickUpDelay(10);
                item.setDeltaMovement((hall.random.nextDouble() - 0.5) * 0.2, 0.3, (hall.random.nextDouble() - 0.5) * 0.2);
                if (hall.addFreshEntity(item)) PRIZES.put(item, player.getUUID());
            }
            data.won(player.getUUID());
            SylarVoice.narrate(player, first ? "story.heroesevolved.sylar.narrate.first_win" : "story.heroesevolved.sylar.narrate.win");
        }
        ServerPlayer holder = server.getPlayerList().getPlayer(opener);
        if (holder == null) for (ServerPlayer p : boss.party()) if (p.getUUID().equals(opener)) holder = p;
        if (holder != null && !byStory) RepairTicketItem.tear(holder);
        SylarStory.beaten(server);
        close(server, 140);
    }

    /** Gray's Ledger: the catalogue of his collection, as the story left it. */
    static ItemStack ledger(MinecraftServer server) {
        boolean claire = SylarData.holds(server, com.jayptucker.heroesevolved.ability.registry.ModAbilities.REGENERATION_ID);
        boolean light = SylarData.holds(server, com.jayptucker.heroesevolved.ability.registry.ModAbilities.RADIATION_ID);
        return BunkerLogs.book("Gray's Ledger", "Gabriel Gray",
                "GRAY & SONS\nRepairs and Acquisitions\n\nItems held for collection. Every one of them came apart under my hands, and every one went back together better.",
                "1 o'clock. Current. A Company woman's. Fast and bright and impatient; it wanted out.\n\n2 o'clock. Cold. A boy in the lockdown. Slow, patient, very clean work.",
                "4 o'clock. Fire. 5 o'clock. Pressure, the kind that moves floors. 8 o'clock. A voice that breaks glass. 9 o'clock. Speed. 10 o'clock. Time, which was the hardest to hold still.",
                claire ? "12 o'clock. Claire Bennet, Pod 14, the third night. She never woke. It didn't matter."
                        : "12 o'clock. Reserved: Claire Bennet, Pod 14.\nNot collected. Someone shut the pod and stood in front of it.",
                light ? "6 o'clock. Ted Sprague, 6:14 PM. I stood at the edge of the light and understood it. It took a hundred years to understand what I had understood."
                        : "6 o'clock. Reserved: Ted Sprague.\nNot collected. Someone kept me from the light. I never saw how it worked.",
                "3, 7 and 11 o'clock. Kept empty, for the ones who come to collect me.\n\nThe last line is in a different hand, newer ink:\n\n\"The watch is still ticking. So is the next one.\"");
    }

    /** Powers come back now; in {@code delay} ticks everyone goes home. */
    static void close(MinecraftServer server, int delay) {
        if (!open) return;
        STOLEN.clear();
        closeIn = Math.max(1, delay);
    }

    public static void tick(MinecraftServer server) {
        ServerLevel hall = level(server);
        if (hall == null) return;
        tickCover(hall);
        if (closeIn > 0 && --closeIn == 0) {
            for (var entry : PRIZES.entrySet()) {
                ItemEntity prize = entry.getKey();
                if (prize.isRemoved() || prize.getItem().isEmpty()) continue;
                ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue());
                if (owner == null) for (ServerPlayer p : hall.players()) if (p.getUUID().equals(entry.getValue())) owner = p;
                if (owner != null) gift(owner, prize.getItem().copy());
                prize.discard();
            }
            PRIZES.clear();
            for (ServerPlayer player : List.copyOf(hall.players())) sendHome(player);
            if (sylar != null && !sylar.isRemoved()) sylar.discard();
            open = false; closeIn = -1; sylar = null; opener = null;
            return;
        }
        if (!open) {
            for (ServerPlayer player : List.copyOf(hall.players())) if (!player.isSpectator() || !player.hasPermissions(2)) sendHome(player);
            return;
        }
        for (ServerPlayer player : List.copyOf(hall.players())) {
            // Nobody falls out of the world: the hall floats in the dark.
            if (player.getY() < ORIGIN.getY() - 8) {
                move(player, hall, center().add(0, 0, 11), player.getYRot(), 0);
            }
        }
    }

    /** Back where they stood when the ticket took them; world spawn when that's gone. */
    public static void sendHome(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        SylarData data = SylarData.get(server);
        SylarData.Home home = data.home(player.getUUID());
        ServerLevel level = home == null ? null : server.getLevel(home.dimension());
        if (level == null || level.dimension().equals(DIMENSION)) { level = server.overworld(); home = null; }
        Vec3 to = home == null ? Vec3.atBottomCenterOf(level.getSharedSpawnPos()) : home.at();
        level.getChunk(BlockPos.containing(to));
        if (!level.noCollision(player, player.getBoundingBox().move(to.subtract(player.position()))))
            to = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        move(player, level, to, home == null ? player.getYRot() : home.yaw(), home == null ? 0 : home.pitch());
        data.clearHome(player.getUUID());
        SetPiecePayload.send(level, Piece.SYLAR_ARRIVE, player.position(), player.getEyePosition(), 3, player);
        SylarVoice.narrate(player, "story.heroesevolved.sylar.narrate.home");
        List<ItemStack> gifts = GIFTS.remove(player.getUUID());
        if (gifts != null) for (ItemStack stack : gifts) if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** Gives {@code stack} now, or when they get home if their pockets are full (nothing is left lying in the hall). */
    static void gift(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().add(stack) && stack.isEmpty()) return;
        if (inCollection(player) && open) GIFTS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(stack.copy());
        else player.drop(stack.copy(), false);
    }

    /** Someone left the hall by dying or logging out: they no longer need taking home, and any power comes back. */
    public static void forget(ServerPlayer player) {
        STOLEN.remove(player.getUUID());
        SylarData.get(player.getServer()).clearHome(player.getUUID());
    }

    /** For tests and server stops: no fight, nobody owed anything. */
    public static void reset(MinecraftServer server) {
        STOLEN.clear(); MOVING.clear(); GIFTS.clear(); PRIZES.clear();
        if (sylar != null && !sylar.isRemoved()) sylar.discard();
        open = false; closeIn = -1; coverAge = -1; sylar = null; opener = null;
    }

}
