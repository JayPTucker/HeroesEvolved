package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillActionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.styles.Piece;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The reworked Telepathy tree: commands, persuasion, voices carried by telepathy, walked memories and links. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TelepathyReworkGameTests {
    private record Actor(ServerPlayer player, List<Packet<?>> packets) {}

    private static Actor telepath(GameTestHelper h, int z, String name) {
        List<Packet<?>> packets = new ArrayList<>();
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) { return !isAlliedTo(other); }
        };
        try {
            Connection connection = p.connection.getConnection();
            var f = Connection.class.getDeclaredField("channel");
            f.setAccessible(true);
            if (connection.channel() == null) f.set(connection, new EmbeddedChannel());
            p.connection = new ServerGamePacketListenerImpl(p.server, connection, p, CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) { packets.add(packet); }
                @Override public void send(Packet<?> packet, net.minecraft.network.PacketSendListener listener) { packets.add(packet); }
            };
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, z))));
        p.setNoGravity(true);
        p.setYRot(0);
        p.setXRot(0);
        p.setYHeadRot(0);
        p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.TELEPATHY_ID).unlock(ModAbilities.TELEPATHY_ID));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.TELEPATHY_ID));
        PlayerProgressionService.setLevel(p, 15);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        h.getLevel().addNewPlayer(p);
        // In the player list, so everything that looks players up by id (links, routes) finds them.
        PlayerList list = p.getServer().getPlayerList();
        try {
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
            @SuppressWarnings("unchecked") var all = (List<ServerPlayer>) players.get(list);
            @SuppressWarnings("unchecked") var index = (Map<UUID, ServerPlayer>) byId.get(list);
            all.add(p); index.put(p.getUUID(), p);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        return new Actor(p, packets);
    }

    /**
     * Voice tests pretend voice chat reaches only {@link #NEAR} blocks, so "far apart" is twenty blocks. Sending test
     * players hundreds of blocks away makes the server generate chunks round them and slows every other test.
     */
    private static final double NEAR = 5;

    private static double nearVoices() {
        double was = MindVoiceRoutes.voiceDistance();
        MindVoiceRoutes.voiceDistance(NEAR);
        return was;
    }

    private static void learn(ServerPlayer p, String... ids) {
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TELEPATHY_ID.toString(),
                new SkillTreeData.Build(15, Set.of(ids), Map.of())));
    }

    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.TELEPATHY_ID, 5, false); }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer near, double z) {
        T mob = type.create(h.getLevel());
        mob.setPos(near.position().add(0, 0, z));
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPersistenceRequired();
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static List<SetPiecePayload> pieces(Actor a, Piece piece) {
        return a.packets.stream().filter(p -> p instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof SetPiecePayload s
                && s.piece() == piece.ordinal()).map(p -> (SetPiecePayload) ((ClientboundCustomPayloadPacket) p).payload()).toList();
    }

    private static void clean(Actor... actors) {
        for (Actor a : actors) {
            TelepathyService.stopAll(a.player);
            TelepathySkills.leave(a.player);
            PlayerList list = a.player.getServer().getPlayerList();
            try {
                Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
                Field byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
                ((List<?>) players.get(list)).remove(a.player);
                ((Map<?, ?>) byId.get(list)).remove(a.player.getUUID());
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            // Removed from the level properly, so its chunk tickets go with it.
            a.player.serverLevel().removePlayerImmediately(a.player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        // The next tick's routes would still name them; nobody should hear a player who has gone.
        MindVoiceRoutes.publish(Map.of(), true, false);
    }

    // ------------------------------------------------------------------
    // Puppet Master
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "telepathy.drop_it")
    public static void dropItDisarmsAMobAndAThreadedOneLetsGoOfItsWeapon(GameTestHelper h) {
        Actor a = telepath(h, 2, "drop-it");
        var pillager = mob(h, EntityType.PILLAGER, a.player, 3);
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        try {
            learn(a.player, TelepathySkills.DROP_IT);
            TelepathyFocus.deepen(a.player, pillager, 60, 100);
            pillager.setTarget(a.player);
            var c = context(a.player);
            h.assertTrue(SkillActionService.canUse(c, 0) && SkillActionService.activate(c, 0), "Drop It finds the aimed mob");
            h.assertTrue(TelepathySkills.disarmed(pillager) && pillager.getTarget() == null, "The mob forgets its target and can't fight");
            pillager.setTarget(a.player);
            h.assertTrue(pillager.getTarget() == null, "A disarmed mob can't pick a target again");
            h.assertTrue(pillager.getMainHandItem().isEmpty(), "A mob you hold a deep thread to drops its weapon");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, pillager.getBoundingBox().inflate(3),
                    i -> i.getItem().is(Items.CROSSBOW)).isEmpty(), "The weapon lands on the ground, not into nothing");
            h.assertTrue(!pieces(a, Piece.TELE_COMMAND).isEmpty(), "Everyone nearby sees the word land");
            h.succeed();
        } finally {
            h.getLevel().getEntitiesOfClass(ItemEntity.class, pillager.getBoundingBox().inflate(4)).forEach(ItemEntity::discard);
            pillager.discard(); clean(a);
        }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.persuasion", timeoutTicks = 120)
    public static void persuasionTalksAMobRoundOverTwoSeconds(GameTestHelper h) {
        Actor a = telepath(h, 2, "persuader");
        var zombie = mob(h, EntityType.ZOMBIE, a.player, 4);
        try {
            learn(a.player, TelepathySkills.PERSUASION);
            h.assertTrue(ExpansionSkills.activate(context(a.player), "persuasion"), "Persuasion starts on the aimed mind");
            h.assertTrue(TelepathySkills.persuading(a.player) && !TelepathySkills.persuaded(zombie), "It takes time: the mind isn't won yet");
            h.assertTrue(!pieces(a, Piece.TELE_PERSUADE).isEmpty(), "The talk-down is visible, so it can be answered");
        } catch (RuntimeException e) { zombie.discard(); clean(a); throw e; }
        h.runAfterDelay(TelepathyGeometry.PERSUADE_TICKS + 4, () -> {
            try {
                h.assertTrue(TelepathySkills.persuaded(zombie), "Kept in sight for the whole talk-down, the mind is won");
                zombie.setTarget(a.player);
                h.assertTrue(zombie.getTarget() == null, "A persuaded mob won't turn on its persuader");
                h.succeed();
            } finally { zombie.discard(); clean(a); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.persuasion_cover", timeoutTicks = 120)
    public static void breakingLineOfSightShakesPersuasionOff(GameTestHelper h) {
        Actor a = telepath(h, 2, "persuader-cover");
        var zombie = mob(h, EntityType.ZOMBIE, a.player, 4);
        BlockPos wall = a.player.blockPosition().offset(0, 0, 2);
        try {
            learn(a.player, TelepathySkills.PERSUASION);
            h.assertTrue(ExpansionSkills.activate(context(a.player), "persuasion"), "Persuasion starts");
            h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            h.getLevel().setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        } catch (RuntimeException e) { zombie.discard(); clean(a); throw e; }
        h.runAfterDelay(TelepathyGeometry.PERSUADE_TICKS + 4, () -> {
            try {
                h.assertTrue(!TelepathySkills.persuaded(zombie) && !TelepathySkills.persuading(a.player), "Out of sight, the mind slips away");
                h.succeed();
            } finally {
                h.getLevel().setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
                h.getLevel().setBlock(wall.above(), Blocks.AIR.defaultBlockState(), 3);
                zombie.discard(); clean(a);
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.edens_voice")
    public static void aDeeplyReadMindTurnsAtOnceAndEdensVoiceReachesEveryThread(GameTestHelper h) {
        Actor a = telepath(h, 2, "eden");
        var aimed = mob(h, EntityType.ZOMBIE, a.player, 4);
        var aside = mob(h, EntityType.ZOMBIE, a.player, 3);
        aside.setPos(a.player.position().add(3, 0, 3));
        try {
            learn(a.player, TelepathySkills.PERSUASION, TelepathySkills.SILVER_TONGUE, TelepathySkills.EDENS_VOICE);
            TelepathyFocus.deepen(a.player, aimed, 60, 100);
            TelepathyFocus.deepen(a.player, aside, 60, 100);
            h.assertTrue(ExpansionSkills.activate(context(a.player), "persuasion"), "Persuasion speaks");
            h.assertTrue(TelepathySkills.persuaded(aimed), "A mind you hold a 60+ thread to turns at once");
            h.assertTrue(TelepathySkills.persuaded(aside), "Eden's Voice reaches every threaded mind, not just the aimed one");
            h.succeed();
        } finally { aimed.discard(); aside.discard(); clean(a); }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.strings")
    public static void stringsAttachedAndWhisperedOrdersKeepMindsOnYourSide(GameTestHelper h) {
        Actor a = telepath(h, 2, "strings");
        var zombie = mob(h, EntityType.ZOMBIE, a.player, 4);
        try {
            TelepathySkills.released(a.player, zombie);
            h.assertTrue(!TelepathySkills.persuaded(zombie), "Without Strings Attached a released body is free");
            learn(a.player, TelepathySkills.STRINGS_ATTACHED, TelepathySkills.WHISPERED_ORDERS);
            TelepathySkills.released(a.player, zombie);
            h.assertTrue(TelepathySkills.persuadedBy(zombie) == a.player, "With it, the mob you let go keeps fighting for you");
            h.assertTrue(TelepathySkills.suggestionTicks(a.player, zombie, 200) == 200, "Whispered Orders needs a thread first");
            TelepathyFocus.deepen(a.player, zombie, 40, 100);
            h.assertTrue(TelepathySkills.suggestionTicks(a.player, zombie, 200) == 400, "A threaded mind keeps a Suggestion twice as long");
            h.assertTrue(TelepathyFocus.focus(a.player, zombie) == 60, "Giving the order deepens the thread by 20");
            h.succeed();
        } finally { zombie.discard(); clean(a); }
    }

    // ------------------------------------------------------------------
    // Mind Reader
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "telepathy.far_hearing")
    public static void farHearingCarriesADistantVoiceFromItsDirection(GameTestHelper h) {
        Actor a = telepath(h, 2, "listener"), b = telepath(h, 6, "speaker");
        try {
            b.player.setPos(a.player.position().add(8, 0, 14));
            var far = List.of(new MindVoiceRoutes.Listener(a.player, 30));
            var everyone = List.of(a.player, b.player);
            var routes = MindVoiceRoutes.compute(far, List.of(), Map.of(), everyone, NEAR).get(b.player.getUUID());
            h.assertTrue(routes != null && routes.size() == 1, "A voice beyond normal range reaches the listener");
            var route = routes.getFirst();
            Vec3 at = new Vec3(route.x(), route.y(), route.z()), eye = a.player.getEyePosition();
            Vec3 toward = b.player.getEyePosition().subtract(eye).normalize();
            h.assertTrue(!route.inHead() && route.kind() == MindVoiceRoutes.Kind.FAR, "Far Hearing is heard from somewhere, not inside the head");
            h.assertTrue(at.subtract(eye).normalize().dot(toward) > .999, "The voice comes from the speaker's direction");
            h.assertTrue(at.distanceTo(eye) > 6 && at.distanceTo(eye) < 13, "A voice you don't face is faint");
            a.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, b.player.getEyePosition());
            var faced = MindVoiceRoutes.compute(far, List.of(), Map.of(), everyone, NEAR).get(b.player.getUUID()).getFirst();
            h.assertTrue(new Vec3(faced.x(), faced.y(), faced.z()).distanceTo(a.player.getEyePosition()) < 3.5, "Facing a voice brings it close and clear");
            b.player.setPos(a.player.position().add(0, 0, 3));
            h.assertTrue(MindVoiceRoutes.compute(far, List.of(), Map.of(), everyone, NEAR).get(b.player.getUUID()) == null,
                    "A voice already in range isn't heard twice");
            b.player.setPos(a.player.position().add(0, 0, 40));
            h.assertTrue(MindVoiceRoutes.compute(far, List.of(), Map.of(), everyone, NEAR).get(b.player.getUUID()) == null,
                    "Beyond Far Hearing's reach, nothing");
            h.succeed();
        } finally { clean(a, b); }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.far_hearing_skill")
    public static void farHearingListensMarksVoicesAndStopsOnASecondPress(GameTestHelper h) {
        Actor a = telepath(h, 2, "ear"), b = telepath(h, 6, "talker");
        double was = nearVoices();
        try {
            learn(a.player, TelepathySkills.FAR_HEARING, TelepathySkills.EAVESDROPPER);
            var c = context(a.player);
            h.assertTrue(SkillActionService.activate(c, 1) && TelepathySkills.listening(a.player), "Far Hearing starts listening");
            h.assertTrue(TelepathySkills.hearingRange(a.player) == TelepathyGeometry.HEARING_LONG, "Eavesdropper hears out to 320 blocks");
            b.player.setPos(a.player.position().add(0, 0, 20));
            TelepathySkills.tick(h.getLevel().getServer());
            var routes = MindVoiceRoutes.current(b.player.getUUID());
            h.assertTrue(routes.stream().anyMatch(r -> r.listener().equals(a.player.getUUID())), "A voice out of normal range is heard");
            // The voice chat thread reports the voice as heard; the next half second marks it and threads the speaker.
            MindVoiceRoutes.testHeard(a.player.getUUID(), b.player.getUUID());
            h.runAfterDelay(11, () -> {
                try {
                    h.assertTrue(!pieces(a, Piece.TELE_VOICE).isEmpty(), "A heard voice is marked for the listener alone");
                    h.assertTrue(pieces(b, Piece.TELE_VOICE).isEmpty(), "The speaker doesn't see the mark");
                    h.assertTrue(TelepathyFocus.focus(a.player, b.player) > 0, "A faced voice is threaded");
                    h.assertTrue(TelepathySkills.followUp(c, 1) && !TelepathySkills.listening(a.player), "A second press stops listening");
                    h.succeed();
                } finally { MindVoiceRoutes.voiceDistance(was); clean(a, b); }
            });
        } catch (RuntimeException e) { MindVoiceRoutes.voiceDistance(was); clean(a, b); throw e; }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.memory_walk")
    public static void memoryWalkShowsWhereAMindWentAndWhoHurtIt(GameTestHelper h) {
        Actor a = telepath(h, 2, "walker");
        var zombie = mob(h, EntityType.ZOMBIE, a.player, 4);
        try {
            learn(a.player, TelepathySkills.MEMORY_WALK, TelepathySkills.DEEP_READ);
            long now = h.getLevel().getGameTime();
            Vec3 here = zombie.position();
            MindMemory.remember(zombie, now - 400, here.add(6, 0, 0), null);
            MindMemory.remember(zombie, now - 200, here.add(3, 0, 1), "Steve");
            MindMemory.remember(zombie, now - 40, here.add(1, 0, 0), null);
            h.assertTrue(ExpansionSkills.activate(context(a.player), "memory_walk"), "Memory Walk reads the aimed mind");
            var trail = pieces(a, Piece.TELE_MEMORY);
            h.assertTrue(trail.size() >= 3, "The path shows as trail pieces");
            h.assertTrue(trail.stream().anyMatch(s -> s.scale() >= TelepathyGeometry.FLAG_HURT), "Where it was hurt is marked");
            h.assertTrue(!pieces(a, Piece.TELE_ECHO).isEmpty(), "An echo stands where the memory began");
            h.assertTrue(TelepathyFocus.focus(a.player, zombie) >= 40, "Deep Read leaves a 40-Focus thread");
            h.succeed();
        } finally { zombie.discard(); MindMemory.forget(zombie.getUUID()); clean(a); }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.memory_place")
    public static void memoryWalkOnAPlaceShowsWhoPassedThroughIt(GameTestHelper h) {
        Actor a = telepath(h, 2, "place-walker");
        var passer = mob(h, EntityType.PIG, a.player, 1);
        BlockPos block = a.player.blockPosition().offset(0, -1, 5);
        try {
            learn(a.player, TelepathySkills.MEMORY_WALK);
            a.player.setXRot(35);
            passer.setPos(a.player.position().add(4, 0, -3));
            long now = h.getLevel().getGameTime();
            MindMemory.remember(passer, now - 100, Vec3.atBottomCenterOf(block.above()), null);
            MindMemory.remember(passer, now - 60, Vec3.atBottomCenterOf(block.above()).add(3, 0, 0), null);
            h.assertTrue(ExpansionSkills.activate(context(a.player), "memory_walk"), "Memory Walk reads a place when no mind is aimed at");
            h.assertTrue(!pieces(a, Piece.TELE_MEMORY).isEmpty(), "Whoever passed through it leaves a trail");
            h.succeed();
        } finally { passer.discard(); MindMemory.forget(passer.getUUID()); clean(a); }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.lost")
    public static void lostInThoughtWalksAMobBackAlongItsMemory(GameTestHelper h) {
        Actor a = telepath(h, 2, "lost");
        var zombie = mob(h, EntityType.ZOMBIE, a.player, 4);
        try {
            learn(a.player, TelepathySkills.MEMORY_WALK, TelepathySkills.DEEP_READ, TelepathySkills.LOST_IN_THOUGHT);
            MindMemory.remember(zombie, h.getLevel().getGameTime() - 100, zombie.position().add(4, 0, 0), null);
            h.assertTrue(ExpansionSkills.activate(context(a.player), "memory_walk"), "Memory Walk reads the aimed mind");
            h.assertTrue(TelepathySkills.lost(zombie), "An enemy relives what was walked");
            zombie.setTarget(a.player);
            h.assertTrue(zombie.getTarget() == null, "A mob lost in thought can't pick a target");
            h.succeed();
        } finally { zombie.discard(); MindMemory.forget(zombie.getUUID()); clean(a); }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.open_book")
    public static void openBookHitsAReadMindHarder(GameTestHelper h) {
        Actor a = telepath(h, 2, "reader");
        var pillager = mob(h, EntityType.PILLAGER, a.player, 3);
        try {
            float before = pillager.getHealth();
            pillager.hurt(a.player.damageSources().playerAttack(a.player), 5);
            float plain = before - pillager.getHealth();
            pillager.setHealth(pillager.getMaxHealth());
            pillager.invulnerableTime = 0;
            learn(a.player, TelepathySkills.OPEN_BOOK);
            TelepathyFocus.deepen(a.player, pillager, 60, 100);
            pillager.hurt(a.player.damageSources().playerAttack(a.player), 5);
            float read = pillager.getMaxHealth() - pillager.getHealth();
            h.assertTrue(Math.abs(read - plain * 1.4F) < .05F, "A deeply read mind takes 40% more (" + plain + " → " + read + ")");
            h.succeed();
        } finally { pillager.discard(); clean(a); }
    }

    // ------------------------------------------------------------------
    // Dreamweaver
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "telepathy.hive_link")
    public static void hiveLinkCarriesVoicesAnywhereButNeverTwice(GameTestHelper h) {
        Actor a = telepath(h, 2, "hive"), b = telepath(h, 5, "volunteer"), stranger = telepath(h, 8, "stranger");
        double was = nearVoices();
        try {
            learn(a.player, TelepathySkills.HIVE_LINK);
            b.player.setShiftKeyDown(true);
            h.assertTrue(ExpansionSkills.activate(context(a.player), "hive_link"), "Hive Link takes a crouching volunteer");
            h.assertTrue(TelepathySkills.inHive(b.player) && !TelepathySkills.inHive(stranger.player), "Only willing minds are linked");
            TelepathySkills.tick(h.getLevel().getServer());
            h.assertTrue(MindVoiceRoutes.current(b.player.getUUID()).isEmpty(), "Friends side by side already hear each other");
            b.player.setPos(a.player.position().add(0, 0, 20));
            stranger.player.setPos(a.player.position().add(0, 0, 20));
            TelepathySkills.tick(h.getLevel().getServer());
            var routes = MindVoiceRoutes.current(b.player.getUUID());
            h.assertTrue(routes.size() == 1 && routes.getFirst().inHead() && routes.getFirst().listener().equals(a.player.getUUID()),
                    "Far apart, a linked friend's voice goes straight into the other's head");
            h.assertTrue(MindVoiceRoutes.current(stranger.player.getUUID()).isEmpty(), "Nobody outside the link is carried");
            h.succeed();
        } finally { MindVoiceRoutes.voiceDistance(was); clean(a, b, stranger); }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.mind_link_voice")
    public static void mindLinkToOnePlayerCarriesTheirVoicesAcrossTheWorld(GameTestHelper h) {
        Actor a = telepath(h, 2, "linker"), b = telepath(h, 6, "partner"), c = telepath(h, 9, "bystander");
        double was = nearVoices();
        try {
            b.player.setShiftKeyDown(true);
            c.player.setShiftKeyDown(true);
            h.assertTrue(TelepathyService.mindLink(a.player, 3), "Mind Link takes the willing player you aim at");
            h.assertTrue(TelepathyService.linked(b.player) && !TelepathyService.linked(c.player),
                    "Aimed at one mind, the link is just the two of you, even with another volunteer nearby");
            TelepathySkills.tick(h.getLevel().getServer());
            h.assertTrue(MindVoiceRoutes.current(b.player.getUUID()).isEmpty(), "Close together, they already hear each other");
            b.player.setPos(a.player.position().add(0, 0, 20));
            TelepathySkills.tick(h.getLevel().getServer());
            h.assertTrue(MindVoiceRoutes.current(b.player.getUUID()).stream().anyMatch(r -> r.inHead() && r.listener().equals(a.player.getUUID())),
                    "Far apart, the partner's voice is inside the telepath's head");
            h.assertTrue(MindVoiceRoutes.current(a.player.getUUID()).stream().anyMatch(r -> r.inHead() && r.listener().equals(b.player.getUUID())),
                    "And the telepath's inside theirs");
            h.assertTrue(MindVoiceRoutes.current(c.player.getUUID()).isEmpty(), "Nobody outside the link is carried");
            h.succeed();
        } finally { MindVoiceRoutes.voiceDistance(was); clean(a, b, c); }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.inner_voice")
    public static void theInnerVoiceGoesOnlyIntoLinkedMinds(GameTestHelper h) {
        Actor a = telepath(h, 2, "thinker"), b = telepath(h, 4, "friend"), ear = telepath(h, 9, "eavesdropper"), alone = telepath(h, 10, "alone");
        double was = nearVoices();
        try {
            h.assertTrue(!TelepathySkills.toggleInnerVoice(alone.player), "With nobody to reach, the inner voice doesn't start");
            b.player.setShiftKeyDown(true);
            h.assertTrue(TelepathyService.mindLink(a.player, 3), "The telepath links a friend");
            ear.player.setPos(a.player.position().add(0, 0, 25));
            learn(ear.player, TelepathySkills.FAR_HEARING);
            h.assertTrue(TelepathySkills.farHearing(ear.player), "Someone out of earshot listens with Far Hearing");
            TelepathySkills.tick(h.getLevel().getServer());
            h.assertTrue(MindVoiceRoutes.current(a.player.getUUID()).stream().anyMatch(r -> r.listener().equals(ear.player.getUUID())),
                    "Spoken aloud, the telepath can be overheard from afar");
            h.assertTrue(MindVoiceRoutes.current(a.player.getUUID()).stream().noneMatch(r -> r.listener().equals(b.player.getUUID())),
                    "and the friend beside them hears them the ordinary way");
            h.assertTrue(TelepathySkills.toggleInnerVoice(a.player) && TelepathySkills.innerVoice(a.player), "Shift + Mind Link: the inner voice");
            TelepathySkills.tick(h.getLevel().getServer());
            h.assertTrue(MindVoiceRoutes.inner(a.player.getUUID()), "The voice chat is told not to broadcast them");
            var routes = MindVoiceRoutes.current(a.player.getUUID());
            h.assertTrue(routes.stream().anyMatch(r -> r.inHead() && r.listener().equals(b.player.getUUID())),
                    "The linked friend hears it inside their head, however close they stand");
            h.assertTrue(routes.stream().noneMatch(r -> r.listener().equals(ear.player.getUUID())), "Nobody can overhear a thought");
            h.assertTrue(TelepathySkills.toggleInnerVoice(a.player) && !TelepathySkills.innerVoice(a.player), "A second Shift + Mind Link speaks aloud again");
            h.succeed();
        } finally { MindVoiceRoutes.voiceDistance(was); clean(a, b, ear, alone); }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.psychic_voice")
    public static void aVoiceInsideTheHeadIsDistortedButNotDestroyed(GameTestHelper h) {
        PsychicVoice voice = new PsychicVoice();
        double inEnergy = 0, outEnergy = 0, difference = 0;
        for (int frame = 0; frame < 10; frame++) {
            short[] pcm = new short[960], original = new short[960];
            for (int i = 0; i < 960; i++) original[i] = pcm[i] = (short) (9000 * Math.sin(2 * Math.PI * 440 * (frame * 960 + i) / 48000.0));
            voice.process(pcm, frame * 20_000_000L);
            for (int i = 0; i < 960; i++) {
                inEnergy += original[i] * (double) original[i];
                outEnergy += pcm[i] * (double) pcm[i];
                difference += Math.abs(pcm[i] - original[i]);
            }
        }
        double ratio = Math.sqrt(outEnergy / inEnergy);
        h.assertTrue(difference / 9600 > 500, "The voice is audibly changed");
        h.assertTrue(ratio > .3 && ratio < 3, "but stays about as loud (" + ratio + ")");
        short[] silence = new short[960];
        voice.process(silence, 2_000_000_000L);
        boolean quiet = true;
        for (short sample : silence) quiet &= Math.abs(sample) < 2;
        h.assertTrue(quiet, "After a pause, the last sentence's echo doesn't bleed into silence");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.mind_network")
    public static void mindNetworkSpeaksIntoEveryDeeplyThreadedMind(GameTestHelper h) {
        Actor a = telepath(h, 2, "network"), b = telepath(h, 6, "threaded");
        double was = nearVoices();
        try {
            learn(a.player, TelepathySkills.MIND_NETWORK);
            b.player.setPos(a.player.position().add(0, 0, 20));
            TelepathySkills.tick(h.getLevel().getServer());
            h.assertTrue(MindVoiceRoutes.current(a.player.getUUID()).isEmpty(), "An unread mind can't be reached");
            TelepathyFocus.deepen(a.player, b.player, TelepathyGeometry.PROJECT_FOCUS, 100);
            TelepathySkills.tick(h.getLevel().getServer());
            h.assertTrue(MindVoiceRoutes.current(a.player.getUUID()).stream().anyMatch(r -> r.inHead() && r.listener().equals(b.player.getUUID())),
                    "The telepath's voice reaches inside the threaded mind");
            h.assertTrue(MindVoiceRoutes.current(b.player.getUUID()).stream().anyMatch(r -> r.listener().equals(a.player.getUUID())),
                    "And the mind's answer comes back");
            h.succeed();
        } finally { MindVoiceRoutes.voiceDistance(was); clean(a, b); }
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.burden", timeoutTicks = 120)
    public static void sharedBurdenEasesStrainWhileLinked(GameTestHelper h) {
        Actor a = telepath(h, 2, "burden"), alone = telepath(h, 9, "alone"), friend = telepath(h, 4, "friend");
        try {
            learn(a.player, TelepathySkills.SHARED_BURDEN, TelepathySkills.HIVE_LINK);
            learn(alone.player, TelepathySkills.SHARED_BURDEN);
            friend.player.setShiftKeyDown(true);
            alone.player.setPos(a.player.position().add(0, 0, 40));
            h.assertTrue(ExpansionSkills.activate(context(a.player), "hive_link"), "The telepath links with a friend");
            h.assertTrue(TelepathySkills.sharesBurden(a.player) && !TelepathySkills.sharesBurden(alone.player), "Only a linked telepath shares the burden");
            TelepathyFocus.strain(a.player, 60);
            TelepathyFocus.strain(alone.player, 60);
        } catch (RuntimeException e) { clean(a, alone, friend); throw e; }
        h.runAfterDelay(61, () -> {
            try {
                h.assertTrue(TelepathyFocus.strain(a.player) < TelepathyFocus.strain(alone.player) - 6,
                        "Strain falls faster while linked (" + TelepathyFocus.strain(a.player) + " vs " + TelepathyFocus.strain(alone.player) + ")");
                h.succeed();
            } finally { clean(a, alone, friend); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "telepathy.fright")
    public static void wakingFrightThrowsAMindBackAndShakesIt(GameTestHelper h) {
        Actor a = telepath(h, 2, "weaver"), b = telepath(h, 6, "dreamer");
        try {
            TelepathySkills.fright(a.player, b.player, b.player.position().add(0, 0, 1));
            h.assertTrue(!TelepathySkills.shaken(b.player), "Without Waking Fright, touching an illusion only dissolves it");
            learn(a.player, TelepathySkills.WAKING_FRIGHT);
            b.player.setDeltaMovement(Vec3.ZERO);
            TelepathySkills.fright(a.player, b.player, b.player.position().add(0, 0, 1));
            h.assertTrue(TelepathySkills.shaken(b.player), "The mind is Shaken");
            h.assertTrue(b.player.getDeltaMovement().z < 0, "And thrown back from the illusion");
            h.succeed();
        } finally { clean(a, b); }
    }
}
