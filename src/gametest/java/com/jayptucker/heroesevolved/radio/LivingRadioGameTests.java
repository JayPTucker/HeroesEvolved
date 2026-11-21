package com.jayptucker.heroesevolved.radio;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.BunkerRadios;
import com.jayptucker.heroesevolved.story.bunker.BunkerService;
import com.jayptucker.heroesevolved.village.VillageMemory;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The living radio: the bunker's radios (one for every two sleepers) and the villages' chatter on 101.10 between calls
 * (what they talk about, who hears it, how it sounds).
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class LivingRadioGameTests {
    private static BlockPos at(GameTestHelper h, int x, int z) { return h.absolutePos(new BlockPos(x, 2, z)); }

    private static BlockPos bell(GameTestHelper h, int x, int z) {
        BlockPos bell = at(h, x, z);
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    private static ServerPlayer player(GameTestHelper h, Vec3 at, String name) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            final List<Component> heard = new ArrayList<>();
            @Override public void sendSystemMessage(Component c) { heard.add(c); }
            @Override public String toString() { return String.join("\n", heard.stream().map(Component::getString).toList()); }
        };
        p.setPos(at);
        p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty());
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void cleanup(GameTestHelper h, BlockPos bell, Entity... entities) {
        for (Entity e : entities) {
            if (e instanceof ServerPlayer p) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else if (e != null) e.discard();
        }
        if (bell != null) VillageMemory.forget(h.getLevel(), bell);
    }

    private static boolean radio(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("simpleradio:walkie_talkie");
    }

    // ------------------------------------------------------------------
    // The bunker's radios
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "radio.owed")
    public static void theBunkerOwesOneRadioForEveryTwoSleepers(GameTestHelper h) {
        h.assertTrue(BunkerRadios.owed(0) == 0 && BunkerRadios.owed(1) == 1 && BunkerRadios.owed(2) == 1, "One for the first two (and singleplayer)");
        h.assertTrue(BunkerRadios.owed(3) == 2 && BunkerRadios.owed(4) == 2 && BunkerRadios.owed(5) == 3, "Another for each two more");
        ItemStack r = EmergencyBand.radio();
        h.assertTrue(radio(r), "A walkie-talkie: " + r);
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "radio.bunker")
    public static void sleepersFindRadiosInTheRecordsChest(GameTestHelper h) {
        var server = h.getLevel().getServer();
        BunkerData data = BunkerService.ensureBuilt(server);
        BlockPos chest = BunkerRadios.chest(data);
        h.assertTrue(chest != null, "The bunker has a Records supply chest");
        var level = server.overworld();
        level.getChunkAt(chest);
        BunkerRadios.stock(level, data);
        int before = data.radios, players = BunkerRadios.players(data);
        h.assertTrue(before == BunkerRadios.owed(players), "Already stocked for everyone so far: " + before + " for " + players);
        List<UUID> added = new ArrayList<>();
        try {
            int inChest = count(level.getBlockEntity(chest));
            // Two more sleepers: odd count goes up by one radio at the first of an odd number.
            for (int i = 0; i < 2; i++) {
                UUID id = UUID.randomUUID();
                added.add(id);
                BunkerRadios.joined(level, data, id);
                h.assertTrue(data.radios == BunkerRadios.owed(BunkerRadios.players(data)),
                        "One for every two: " + data.radios + " for " + BunkerRadios.players(data));
            }
            h.assertTrue(data.radios == before + 1, "Two more sleepers, one more radio: " + before + " -> " + data.radios);
            h.assertTrue(count(level.getBlockEntity(chest)) == inChest + 1, "It is in the chest");
            h.succeed();
        } finally {
            data.counted.removeAll(added);
        }
    }

    private static int count(Object box) {
        if (!(box instanceof Container c)) return -1;
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (radio(c.getItem(i))) n += c.getItem(i).getCount();
        return n;
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "radio.floor")
    public static void aRadioWithNoChestIsLeftOnTheFloor(GameTestHelper h) {
        BlockPos spot = at(h, 5, 5);
        BunkerRadios.put(h.getLevel(), spot, EmergencyBand.radio());
        var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(1.5), i -> radio(i.getItem()));
        h.assertTrue(items.size() == 1, "On the floor where the chest stood: " + items.size());
        items.forEach(Entity::discard);
        h.succeed();
    }

    // ------------------------------------------------------------------
    // The chatter
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "radio.chatter")
    public static void aVillageNearAListenerSaysSomethingInItsOwnName(GameTestHelper h) {
        BlockPos bell = bell(h, 6, 6);
        ServerPlayer p = player(h, Vec3.atBottomCenterOf(at(h, 9, 9)), "Wren");
        try {
            var said = BandChatter.beat(h.getLevel(), List.of(p));
            // Whichever village in reach speaks (other tests' villages may be remembered nearby), it speaks as itself.
            String name = said.isEmpty() ? null : VillageMemory.of(h.getLevel(), said.getFirst().bell()).name();
            h.assertTrue(said.size() == 1 && said.getFirst().place().equals(name) && !said.getFirst().line().isBlank(),
                    "A village speaks in its own name: " + said);
            h.assertTrue(BandChatter.villages(h.getLevel(), p).contains(VillageMemory.of(h.getLevel(), bell).bell()), "Ours is among those in reach");
            h.assertTrue(p.toString().contains("101.10 FM  " + name + ": " + said.getFirst().line()), "In chat, to the listener: " + p);
            h.assertTrue(BandChatter.beat(h.getLevel(), List.of()).isEmpty(), "Nobody with a radio: nothing said");
            h.succeed();
        } finally { cleanup(h, bell, p); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "radio.news")
    public static void newsAndFreshHistoryAreWhatTheyTalkAboutMost(GameTestHelper h) {
        BlockPos bell = bell(h, 6, 6);
        ServerPlayer p = player(h, Vec3.atBottomCenterOf(at(h, 9, 9)), "Wren");
        try {
            VillageMemory.record(h.getLevel(), bell, "the wildfire", VillageMemory.Outcome.SAVED, p, List.of(p), 3, 3);
            BandChatter.news(h.getLevel(), at(h, 4, 4), "A test of the news.", 200);
            var options = BandChatter.options(h.getLevel(), List.of(bell));
            int news = 0, memory = 0, small = 0;
            for (var o : options) {
                if (o.line().equals("A test of the news.")) news = o.weight();
                if (o.line().contains("the wildfire") && o.line().contains("Wren")) memory = o.weight();
                if (o.weight() == 1) small++;
            }
            h.assertTrue(news > memory && memory > 1 && small > 0, "News, then their own history, then small talk: " + news + " " + memory + " " + small);
            h.succeed();
        } finally { cleanup(h, bell, p); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "radio.distress")
    public static void whileOneVillageCallsForHelpTheOthersTalkAboutIt(GameTestHelper h) {
        BlockPos ours = bell(h, 2, 2), theirs = h.absolutePos(new BlockPos(200, 2, 2));
        String name = VillageMemory.of(h.getLevel(), theirs).name();
        var call = EmergencyBand.call(h.getLevel(), ours, Component.literal("test"), 200);
        try {
            // The calling village is the bell at "ours"; the one talking about it is a different village.
            var other = BandChatter.options(h.getLevel(), List.of(theirs));
            String caller = call.place();
            boolean reacts = other.stream().anyMatch(o -> o.place().equals(name) && (o.line().contains("Anyone near " + caller) || o.line().contains(caller + "'s flare")));
            h.assertTrue(reacts, "Another village asks for someone to help " + caller);
            h.succeed();
        } finally {
            EmergencyBand.end(call);
            VillageMemory.forget(h.getLevel(), theirs);
            cleanup(h, ours);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "radio.guardians")
    public static void theyTalkAboutTheirGiftedNeighboursAndTheirFavours(GameTestHelper h) {
        BlockPos bell = bell(h, 6, 6);
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(Vec3.atBottomCenterOf(at(h, 3, 3)));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        v.setNoAi(true);
        h.getLevel().addFreshEntity(v);
        try {
            com.jayptucker.heroesevolved.gifted.Renegades.guard(v, com.jayptucker.heroesevolved.ability.registry.ModAbilities.PYROKINESIS_ID);
            var f = com.jayptucker.heroesevolved.village.Favours.ask(v, com.jayptucker.heroesevolved.village.GiftedWork.Job.SMITH);
            var options = BandChatter.options(h.getLevel(), List.of(bell));
            String who = v.getName().getString();
            h.assertTrue(options.stream().anyMatch(o -> o.line().contains(who) && o.line().contains("forge")), "Their smith's work is talked about");
            h.assertTrue(options.stream().anyMatch(o -> o.line().contains(f.describe())), "And the favour they want: " + f.describe());
            h.succeed();
        } finally { cleanup(h, bell, v); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "radio.voice")
    public static void theVoiceIsAMurmurInATelephoneBand(GameTestHelper h) {
        var voice = new BandChatter.Voice(UUID.randomUUID(), null, 40, new java.util.Random(7));
        short[] start = voice.next(EmergencyBand.SAMPLES_PER_TICK * 2), middle = voice.next(EmergencyBand.SAMPLES_PER_TICK * 20);
        double loud = 0, peak = 0;
        for (short s : middle) { loud += s * (double) s; peak = Math.max(peak, Math.abs(s)); }
        loud = Math.sqrt(loud / middle.length);
        h.assertTrue(loud > 300 && peak < 32767, "Heard, and never clipping: rms " + loud + ", peak " + peak);
        double squelch = 0;
        for (int i = 0; i < 2000; i++) squelch += Math.abs(start[i]);
        h.assertTrue(squelch / 2000 > 50, "A squelch click opens it");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "radio.starland")
    public static void aFallingStarKeepsWhereItLandsLoaded(GameTestHelper h) {
        var m = com.jayptucker.heroesevolved.meteor.Meteors.drop(h.getLevel(), Vec3.atBottomCenterOf(at(h, 5, 5)));
        try {
            h.assertTrue(com.jayptucker.heroesevolved.village.VillageTickets.held("meteor:" + m.id()), "Its landing is held loaded while it falls");
        } finally { com.jayptucker.heroesevolved.meteor.Meteors.cancel(m); }
        h.assertTrue(!com.jayptucker.heroesevolved.village.VillageTickets.held("meteor:" + m.id()), "And let go when it's gone");
        h.succeed();
    }
}
