package com.jayptucker.heroesevolved.trace;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.company.CompanyAttention;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.gifted.GiftedMob;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Rarity;
import com.jayptucker.heroesevolved.village.Days;
import com.jayptucker.heroesevolved.village.VillageMemory;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Power traces: what leaves one, how it fades, who can read it, trails, and the Company finding unseen ones. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PowerTraceGameTests {
    private static BlockPos at(GameTestHelper h, int x, int z) { return h.absolutePos(new BlockPos(x, 2, z)); }

    /** A player who remembers what they're told, crouching if asked. */
    private static final class Listener extends FakePlayer {
        final List<String> heard = new ArrayList<>();
        boolean crouching;
        Listener(GameTestHelper h, String name) { this(h.getLevel(), new GameProfile(UUID.randomUUID(), name)); }
        Listener(ServerLevel level, GameProfile profile) { super(level, profile); }
        @Override public void sendSystemMessage(Component c) { heard.add(c.getString()); }
        @Override public boolean isShiftKeyDown() { return crouching; }
    }

    private static Listener player(GameTestHelper h, int x, int z, String name, ResourceLocation power) {
        Listener p = new Listener(h, name);
        p.setPos(Vec3.atBottomCenterOf(at(h, x, z)));
        p.setGameMode(GameType.SURVIVAL);
        var data = com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty();
        if (power != null) data = data.assignDormant(power).unlock(power);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, data);
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void cleanup(GameTestHelper h, Entity... entities) {
        Traces.clear(h.getLevel(), at(h, 6, 6), 48);
        for (Entity e : entities) {
            if (e instanceof ServerPlayer p) { CompanyAttention.forget(p); p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED); }
            else if (e != null) e.discard();
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "trace.left")
    public static void bigMovesLeaveStrongMarksAndSmallOnesNone(GameTestHelper h) {
        Listener p = player(h, 6, 6, "Wren", ModAbilities.PYROKINESIS_ID);
        try {
            h.assertTrue(Traces.left(p, "test", Traces.FAINT - 1) == null, "A small move leaves nothing");
            var faint = Traces.left(p, "test", Traces.FAINT);
            h.assertTrue(faint != null && faint.kind() == Traces.Kind.SCORCH && Math.abs(faint.strength(Days.clock(h.getLevel())) - 0.5F) < 0.01,
                    "A middling one, a faint scorch");
            var strong = Traces.left(p, "test", Traces.STRONG);
            long now = Days.clock(h.getLevel());
            h.assertTrue(strong == faint && Math.abs(strong.strength(now) - 1) < 0.01, "Used again nearby, the same mark made fresh and strong");
            h.assertTrue(Traces.all(h.getLevel()).stream().filter(t -> t.owner().equals(p.getUUID())).count() == 1, "Not a pile of marks");
            h.assertTrue(Math.abs(strong.strength(now + Traces.STRONG_LIFE / 2) - 0.5F) < 0.01, "Half gone half way through its life");
            h.assertTrue(strong.strength(now + Traces.STRONG_LIFE) == 0, "Gone at the end of it");
            h.succeed();
        } finally { cleanup(h, p); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "trace.read")
    public static void anyoneCanTellWhatAMarkIsButOnlyAReaderWhose(GameTestHelper h) {
        Listener maker = player(h, 6, 6, "Wren", ModAbilities.CRYOKINESIS_ID);
        Listener plain = player(h, 7, 6, "Ash", null), reader = player(h, 5, 6, "Rue", ModAbilities.TELEPATHY_ID);
        try {
            Traces.left(maker, "test", Traces.STRONG);
            Traces data = Traces.get(h.getLevel());
            long now = Days.clock(h.getLevel());
            h.assertTrue(Traces.reader(reader) && !Traces.reader(plain), "A telepath reads marks; someone without a reading power doesn't");
            plain.crouching = true;
            reader.crouching = true;
            Traces.read(h.getLevel(), data, plain, false, now);
            Traces.read(h.getLevel(), data, reader, true, now);
            h.assertTrue(plain.heard.stream().anyMatch(s -> s.contains("frost")) && plain.heard.stream().noneMatch(s -> s.contains("Wren")),
                    "Anyone: what it is, not whose: " + plain.heard);
            h.assertTrue(reader.heard.stream().anyMatch(s -> s.contains("Ice was used here") && s.contains("Wren")), "A reader: what and whose: " + reader.heard);
            int before = reader.heard.size();
            Traces.read(h.getLevel(), data, reader, true, now);
            h.assertTrue(reader.heard.size() == before, "Read once, not every tick");
            h.succeed();
        } finally { cleanup(h, maker, plain, reader); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "trace.trail")
    public static void aRenegadeLeavesATrailThatSaysWhichWayItWent(GameTestHelper h) {
        Pillager mob = EntityType.PILLAGER.create(h.getLevel());
        mob.moveTo(Vec3.atBottomCenterOf(at(h, 1, 6)));
        mob.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        mob.setNoAi(true);
        h.getLevel().addFreshEntity(mob);
        GiftedMob g = GiftedMobs.bestow(mob, ModAbilities.ELECTROKINESIS_ID, Rarity.RARE, true, Gift.Origin.WILD, null, "Vesna Stormcaller");
        try {
            Traces.trail(mob, g.gift());
            mob.teleportTo(mob.getX() + 9, mob.getY(), mob.getZ());
            Traces.trail(mob, g.gift());
            var trail = Traces.all(h.getLevel()).stream().filter(t -> t.trail() && t.name().equals("Vesna Stormcaller")).toList();
            h.assertTrue(trail.size() == 2 && trail.getLast().kind() == Traces.Kind.STATIC, "A mark every few blocks: " + trail.size());
            String read = Traces.describe(trail.getLast(), true, Days.clock(h.getLevel()));
            h.assertTrue(read.contains("Vesna Stormcaller passed this way, heading east"), "Which way it went: " + read);
            h.succeed();
        } finally { cleanup(h, mob); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "trace.company")
    public static void theCompanyFindsMarksLeftUnseenNearAVillage(GameTestHelper h) {
        BlockPos bell = at(h, 6, 6);
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        Listener p = player(h, 6, 9, "Wren", ModAbilities.PYROKINESIS_ID);
        try {
            CompanyAttention.raise(p, 10, null, "test");
            var t = Traces.left(p, "test", Traces.STRONG);
            h.assertTrue(t != null && !t.seen(), "Nobody saw it");
            float found = Traces.sweepNow(h.getLevel());
            h.assertTrue(Math.abs(found - Traces.FOUND) < 0.01 && Math.abs(CompanyAttention.of(p) - 10 - Traces.FOUND) < 0.01, "Found: " + found);
            h.assertTrue(Traces.sweepNow(h.getLevel()) == 0, "And only once");
            h.assertTrue(Traces.fresh(h.getLevel(), p.getUUID(), bell, 32, 0.3F), "An investigator asking here would read it");
            h.succeed();
        } finally {
            cleanup(h, p);
            VillageMemory.forget(h.getLevel(), bell);
        }
    }

    private static void readInTwoDimensions(GameTestHelper h, boolean crouching) {
        Listener p = player(h, 6, 6, "DimensionReader", null);
        try {
            Traces.leave(h.getLevel(), Traces.Kind.FROST, p.blockPosition(), UUID.randomUUID(), "The Maker", true,
                    1, Traces.STRONG_LIFE, false);
            Traces data = Traces.get(h.getLevel());
            ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
            // Saved trace ids and calendar timestamps are local to a level and can coincide in two dimensions.
            Traces otherData = Traces.load(data.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess()), nether.registryAccess());
            Listener there = new Listener(nether, p.getGameProfile());
            there.setPos(p.position());
            p.crouching = there.crouching = crouching;
            long now = Days.clock(h.getLevel());
            Traces.read(h.getLevel(), data, p, !crouching, now);
            Traces.read(nether, otherData, there, !crouching, now);
            h.assertTrue(p.heard.size() == 1, "The first dimension's trace was read: " + p.heard);
            h.assertTrue(there.heard.size() == 1, "The other dimension's trace must be readable too: " + there.heard);
            Traces.read(nether, otherData, there, !crouching, now);
            h.assertTrue(there.heard.size() == 1, "The second mark is still reported only once");
            h.succeed();
        } finally { cleanup(h, p); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "trace.dimensionread")
    public static void crouchReadingOneDimensionDoesNotSilenceAnother(GameTestHelper h) { readInTwoDimensions(h, true); }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "trace.dimensionnotice")
    public static void noticingOneDimensionDoesNotSilenceAnother(GameTestHelper h) { readInTwoDimensions(h, false); }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "trace.newreader")
    public static void gainingAReadingPowerLetsYouLearnWhoseMarkItWas(GameTestHelper h) {
        Listener p = player(h, 6, 6, "NewReader", null);
        try {
            Traces.leave(h.getLevel(), Traces.Kind.SCORCH, p.blockPosition(), UUID.randomUUID(), "The Maker", true,
                    1, Traces.STRONG_LIFE, false);
            p.crouching = true;
            long now = Days.clock(h.getLevel());
            Traces.read(h.getLevel(), Traces.get(h.getLevel()), p, false, now);
            h.assertTrue(p.heard.size() == 1 && !p.heard.getFirst().contains("The Maker"), "An ordinary reading cannot identify the maker");
            p.heard.clear();
            p.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty()
                    .assignDormant(ModAbilities.TELEPATHY_ID).unlock(ModAbilities.TELEPATHY_ID));
            h.assertTrue(Traces.reader(p), "The player has gained a reading power");
            Traces.read(h.getLevel(), Traces.get(h.getLevel()), p, Traces.reader(p), now);
            h.assertTrue(p.heard.stream().anyMatch(s -> s.contains("It felt like The Maker")), "A reading power reveals whose mark it was: " + p.heard);
            int before = p.heard.size();
            Traces.read(h.getLevel(), Traces.get(h.getLevel()), p, true, now);
            h.assertTrue(p.heard.size() == before, "The full reading is reported only once too");
            h.succeed();
        } finally { cleanup(h, p); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "trace.faintsweep")
    public static void aFreshFaintTraceDoesNotCountAsStrongCompanyEvidence(GameTestHelper h) {
        BlockPos bell = at(h, 6, 6);
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        Listener p = player(h, 6, 9, "FaintMaker", null);
        try {
            Traces.leave(h.getLevel(), Traces.Kind.FROST, p.blockPosition(), p.getUUID(), p.getName().getString(), true,
                    0.5F, Traces.FAINT_LIFE, false);
            h.assertTrue(Traces.sweepNow(h.getLevel()) == 0, "A faint trace is not strong evidence, even on the tick it was made");
            h.assertTrue(CompanyAttention.of(p) == 0, "Faint marks do not add attention");
            Traces.leave(h.getLevel(), Traces.Kind.SCORCH, p.blockPosition(), p.getUUID(), p.getName().getString(), true,
                    1, Traces.STRONG_LIFE, false);
            h.assertTrue(Math.abs(Traces.sweepNow(h.getLevel()) - Traces.FOUND) < 0.01, "A strong trace is still found");
            h.succeed();
        } finally {
            cleanup(h, p);
            VillageMemory.forget(h.getLevel(), bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "trace.freshwitnesses")
    public static void refreshingAMarkUnseenDoesNotReuseOldWitnesses(GameTestHelper h) {
        BlockPos bell = at(h, 6, 6);
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        Listener p = player(h, 6, 9, "UnseenMaker", null);
        try {
            var old = Traces.leave(h.getLevel(), Traces.Kind.SCORCH, p.blockPosition(), p.getUUID(), p.getName().getString(), true,
                    1, Traces.STRONG_LIFE, true);
            var fresh = Traces.leave(h.getLevel(), Traces.Kind.SCORCH, p.blockPosition(), p.getUUID(), p.getName().getString(), true,
                    1, Traces.STRONG_LIFE, false);
            h.assertTrue(old == fresh, "The existing mark was refreshed");
            h.assertTrue(!fresh.seen(), "Nobody witnessed the latest use");
            h.assertTrue(Math.abs(Traces.sweepNow(h.getLevel()) - Traces.FOUND) < 0.01, "The new unseen evidence is found");
            h.succeed();
        } finally {
            cleanup(h, p);
            VillageMemory.forget(h.getLevel(), bell);
        }
    }
}
