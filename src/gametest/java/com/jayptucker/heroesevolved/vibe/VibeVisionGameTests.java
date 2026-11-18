package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.FrequencyChoicePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.village.VillageIncidents;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Breach held open as Cisco does it (a channel from the palm that tears open slowly, follows the look, collapses if let
 * go, and goes back to the last frequency), and Vibe's visions: who triggers one, leaving the body for a still double,
 * the hologram and its voice, being torn back by a blow, seeing a village's trouble before it starts, and recovering
 * from a crash mid-vision.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VibeVisionGameTests {
    private static ServerPlayer player(GameTestHelper h, String name, int powerLevel, double x, double z) {
        ServerPlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean teleportTo(ServerLevel world, double x, double y, double z,
                                                Set<RelativeMovement> relative, float yaw, float pitch) {
                if (world != serverLevel()) return false;
                absMoveTo(x, y, z, yaw, pitch);
                return true;
            }
        };
        try {
            Connection connection = player.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        if (powerLevel > 0) {
            player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                    .assignDormant(ModAbilities.VIBE_ID).unlock(ModAbilities.VIBE_ID));
            PlayerProgressionService.setPowerLevel(player, powerLevel);
            PlayerEnergyService.restore(player, 999);
            player.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.VIBE_ID.toString(),
                    new SkillTreeData.Build(15, Set.of(), Map.of(), 0)));
        }
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 2, 0))).add(x, 0, z));
        player.setYRot(0);
        player.setXRot(0);
        player.setOnGround(true);
        player.setGameMode(GameType.SURVIVAL);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static void clean(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            VibeService.forget(player);
            VibeBreaches.close(player);
            if (player.serverLevel().getPlayerByUUID(player.getUUID()) != null)
                player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    private static void guarded(Runnable check, ServerPlayer... players) {
        try {
            check.run();
        } catch (RuntimeException | AssertionError failure) {
            clean(players);
            throw failure;
        }
    }

    private static void done(GameTestHelper h, Runnable check, ServerPlayer... players) {
        try {
            check.run();
            h.succeed();
        } finally {
            clean(players);
        }
    }

    private static int channel(ServerPlayer player) {
        return VibeGeometry.channelTicks(PlayerProgressionService.getLevel(player));
    }

    // --- The held breach ---

    @GameTest(template = "rewind_empty", batch = "vibe.channel", timeoutTicks = 100)
    public static void aHeldBreachIsChannelledOpenSlowlyAndTurnsWithTheLook(GameTestHelper h) {
        ServerPlayer p = player(h, "vibe-channel", 15, 5, 1);
        double stamina = PlayerEnergyService.getStamina(p);
        guarded(() -> {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS
                    && !VibeBreaches.isOpen(p) && PlayerEnergyService.getStamina(p) == stamina,
                    "Pressing Breach does nothing yet, and costs nothing");
        }, p);
        int open = channel(p);
        h.runAfterDelay(VibeBreaches.HOLD_TICKS + 1, () -> guarded(() -> {
            h.assertTrue(VibeBreaches.holding(p) && VibeBreaches.isOpen(p) && VibeBreaches.kind(p) == VibeBreaches.Kind.AIMED,
                    "Held, it starts a breach where the viber looks (no frequency used yet)");
            h.assertTrue(PlayerEnergyService.getStamina(p) < stamina, "which pays Breach's cost");
            float opening = VibeBreaches.opening(p);
            h.assertTrue(opening >= 0 && opening < .3F, "It is only just starting to tear open: " + opening);
            p.setYRot(-35);
            p.setYHeadRot(-35);
        }, p));
        h.runAfterDelay(VibeBreaches.HOLD_TICKS + 4, () -> guarded(() -> {
            Vec3 facing = VibeBreaches.facing(p);
            h.assertTrue(facing != null && facing.dot(Vec3.directionFromRotation(0, -35)) > .99,
                    "While it opens, the tear turns to where the viber now looks: " + facing);
            float opening = VibeBreaches.opening(p);
            h.assertTrue(opening > 0 && opening < 1, "and it is still opening: " + opening);
        }, p));
        h.runAfterDelay(VibeBreaches.HOLD_TICKS + open + 3, () -> done(h, () -> {
            h.assertTrue(VibeBreaches.opening(p) == 1, "After the channel's " + open + " ticks the breach is open");
            VibeBreaches.letGo(p);
            h.assertTrue(VibeBreaches.isOpen(p), "Letting go once it is open leaves it open");
        }, p));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.channel", timeoutTicks = 60)
    public static void lettingGoTooSoonCollapsesTheBreachAndGivesTheStaminaBack(GameTestHelper h) {
        ServerPlayer p = player(h, "vibe-letgo", 15, 5, 1);
        double stamina = PlayerEnergyService.getStamina(p);
        guarded(() -> h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS,
                "The press is taken"), p);
        h.runAfterDelay(VibeBreaches.HOLD_TICKS + 3, () -> done(h, () -> {
            h.assertTrue(VibeBreaches.isOpen(p) && PlayerEnergyService.getStamina(p) < stamina, "The breach is channelling");
            VibeBreaches.letGo(p);
            h.assertFalse(VibeBreaches.isOpen(p), "Let go before it opens, it pinches shut");
            h.assertTrue(Math.abs(PlayerEnergyService.getStamina(p) - stamina) < .01, "and its stamina comes back");
            h.assertFalse(CooldownService.isOnCooldown(p, ResourceLocation.fromNamespaceAndPath("heroesevolved", "vibe_breach")),
                    "and Breach isn't left cooling down");
        }, p));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.channel", timeoutTicks = 60)
    public static void holdingBreachGoesBackToTheLastFrequencyAndShiftAims(GameTestHelper h) {
        ServerPlayer p = player(h, "vibe-last", 15, 5, 2);
        done(h, () -> {
            p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 8))));
            h.assertTrue(VibeFrequencies.mark(p, 0), "A frequency tunes");
            p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 2, 0))).add(5, 0, 2));
            h.assertTrue(VibeFrequencies.last(p) == -1, "Nothing has been used yet");
            VibeFrequencies.choose(p, FrequencyChoicePayload.BREACH, 0, "");
            h.assertTrue(VibeBreaches.kind(p) == VibeBreaches.Kind.FREQUENCY && VibeFrequencies.last(p) == 0,
                    "A breach picked from the wheel is remembered");
            h.assertTrue(VibeBreaches.opening(p) < 1, "A wheel pick is channelled open too");
            VibeBreaches.close(p);
            CooldownService.clearAll(p);
            PlayerEnergyService.restore(p, 999);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS,
                    "The key goes down");
            VibeBreaches.holdNow(p);
            h.assertTrue(VibeBreaches.kind(p) == VibeBreaches.Kind.FREQUENCY, "Held, it goes back to that frequency on its own");
            VibeBreaches.letGo(p);
            CooldownService.clearAll(p);
            PlayerEnergyService.restore(p, 999);
            h.assertTrue(VibeBreaches.shiftHold(p) && VibeBreaches.kind(p) == VibeBreaches.Kind.AIMED,
                    "With Shift held too, it opens where the viber looks instead");
        }, p);
    }

    // --- Visions ---

    /** An ally fifty blocks off, down to two hearts. */
    private static ServerPlayer endangered(GameTestHelper h) {
        ServerPlayer ally = player(h, "vibe-ally", 0, 5, 51);
        ally.setHealth(4);
        return ally;
    }

    @GameTest(template = "rewind_empty", batch = "vibe.vision.ally", timeoutTicks = 100)
    public static void anAllyInDangerPullsTheViberIntoAVisionAndAHologram(GameTestHelper h) {
        ServerPlayer p = player(h, "vibe-seer", 15, 5, 1);
        ServerPlayer ally = endangered(h);
        Vec3 home = p.position();
        guarded(() -> {
            h.assertTrue(VibeVisions.trigger(p) && VibeVisions.kind(p) == VibeVisions.Kind.ALLY,
                    "An ally below three hearts, more than 40 blocks off and within twelve chunks, is what the vision shows");
            h.assertFalse(VibeVisions.inside(p), "First the view only flickers");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) != AbilityUseResult.SUCCESS,
                    "Powers don't answer while a vision takes hold");
        }, p, ally);
        h.runAfterDelay(VibeVisions.FLICKER + 2, () -> guarded(() -> {
            h.assertTrue(VibeVisions.inside(p) && p.isSpectator(), "Then the viber watches from outside their body");
            h.assertTrue(p.position().distanceToSqr(ally.position()) < 49, "beside the ally in danger: " + p.position());
            var body = VibeVisions.body(p);
            h.assertTrue(body != null && body.isAlive() && body.position().distanceToSqr(home) < .01,
                    "A still double of them stands where they were");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS
                    && VibeVisions.projecting(p), "From Power Level 12, a power key projects them as a hologram");
        }, p, ally));
        h.runAfterDelay(VibeVisions.FLICKER + 4, () -> guarded(() -> {
            var voices = VibeVisions.voices(p.getUUID());
            h.assertTrue(voices.stream().anyMatch(v -> v.listener().equals(ally.getUUID())),
                    "The ally near the hologram hears the viber through it");
            var body = VibeVisions.body(p);
            body.hurt(h.getLevel().damageSources().generic(), 2);
            h.assertFalse(VibeVisions.watching(p), "A blow to the double tears the viber back");
            h.assertTrue(!p.isSpectator() && p.position().distanceToSqr(home) < .01, "into their own body, where they stood");
            h.assertFalse(body.isAlive(), "and the double is gone");
            h.assertTrue(VibeVisions.voices(p.getUUID()).isEmpty() || !VibeVisions.projecting(p), "The hologram is gone too");
            h.assertTrue(VibeVisions.spot(p) != null && VibeFrequencies.holdTarget(p) == VibeFrequencies.VISION,
                    "Afterwards, holding Breach opens toward what the vision showed");
        }, p, ally));
        h.runAfterDelay(VibeVisions.FLICKER + 6, () -> done(h, () -> h.assertTrue(VibeVisions.voices(p.getUUID()).isEmpty(),
                "Nobody hears a hologram that has gone"), p, ally));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.vision.young", timeoutTicks = 60)
    public static void aYoungViberOnlyWatchesAndTheVisionEndsOnItsOwn(GameTestHelper h) {
        ServerPlayer p = player(h, "vibe-young", 6, 5, 1);
        ServerPlayer ally = endangered(h);
        Vec3 home = p.position();
        guarded(() -> h.assertTrue(VibeVisions.trigger(p), "The vision comes at any level"), p, ally);
        h.runAfterDelay(VibeVisions.FLICKER + 2, () -> done(h, () -> {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.CANNOT_USE
                    && !VibeVisions.projecting(p), "Below Power Level 12 the viber can only watch");
            VibeVisions.wake(p);
            h.assertTrue(!p.isSpectator() && p.position().distanceToSqr(home) < .01 && !VibeVisions.watching(p),
                    "When it ends they are back in their body");
        }, p, ally));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.vision.calm", timeoutTicks = 40)
    public static void nothingTriggersAVisionWhenEveryoneIsSafeOrClose(GameTestHelper h) {
        VibeVisions.clear();
        ServerPlayer p = player(h, "vibe-calm", 15, 5, 1);
        ServerPlayer near = player(h, "vibe-near", 0, 5, 36);
        ServerPlayer hurt = player(h, "vibe-hurt", 0, 5, 60);
        near.setHealth(2);
        hurt.setHealth(7);
        done(h, () -> {
            h.assertTrue(VibeVisions.find(p) == null,
                    "An ally on one heart within 40 blocks, or one far off on three and a half hearts, brings no vision");
            hurt.setHealth(5);
            h.assertTrue(VibeVisions.find(p) != null, "Below three hearts and far off, they do");
        }, p, near, hurt);
    }

    @GameTest(template = "rewind_empty", batch = "vibe.vision.wait", timeoutTicks = 20)
    public static void theWaitBetweenVisionsIsSavedOnThePlayer(GameTestHelper h) {
        ServerPlayer p = player(h, "vibe-wait", 15, 5, 1);
        done(h, () -> {
            h.assertTrue(VibeVisions.next(p) == Long.MIN_VALUE, "A viber who has never had a vision can have one");
            VibeVisions.rest(p, 123_456L);
            h.assertTrue(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong(VibeVisions.NEXT) == 123_456L
                    && VibeVisions.next(p) == 123_456L, "The wait is kept in the player's saved data, so relogging can't reset it");
        }, p);
    }

    @GameTest(template = "rewind_empty", batch = "vibe.vision.future", timeoutTicks = 60)
    public static void aVillageIncidentCanBeSeenBeforeItHappens(GameTestHelper h) {
        VibeVisions.clear();
        ServerPlayer p = player(h, "vibe-future", 15, 5, 1);
        guarded(() -> {
            VibeVisions.foresee(h.getLevel(), BlockPos.containing(p.position()).offset(0, 0, 60), VillageIncidents.Kind.MELTDOWN);
            h.assertTrue(VibeVisions.trigger(p) && VibeVisions.kind(p) == VibeVisions.Kind.FUTURE,
                    "A village about to have a meltdown shows in a vision before it starts");
        }, p);
        h.runAfterDelay(VibeVisions.FLICKER + 2, () -> done(h, () -> {
            h.assertTrue(VibeVisions.inside(p), "The viber is shown the village");
            h.assertFalse(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS
                    || VibeVisions.projecting(p), "There is nobody yet to appear to");
            VibeVisions.clear();
        }, p));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.vision.crash", timeoutTicks = 20)
    public static void aViberLeftInAVisionByACrashWakesInTheirBody(GameTestHelper h) {
        ServerPlayer p = player(h, "vibe-crash", 15, 5, 1);
        done(h, () -> {
            Vec3 home = p.position();
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", h.getLevel().dimension().location().toString());
            tag.putDouble("x", home.x);
            tag.putDouble("y", home.y);
            tag.putDouble("z", home.z);
            tag.putInt("mode", GameType.SURVIVAL.getId());
            CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            persisted.put(VibeVisions.SAVE, tag);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            p.setGameMode(GameType.SPECTATOR);
            p.absMoveTo(home.x + 30, home.y + 8, home.z, 0, 0);
            VibeVisions.login(p);
            h.assertTrue(!p.isSpectator() && p.position().distanceToSqr(home) < .01,
                    "Logging in still marked as in a vision puts them back in their body, as they were");
            h.assertFalse(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(VibeVisions.SAVE),
                    "and forgets the mark");
        }, p);
    }

    // --- Touch ---

    @GameTest(template = "rewind_empty", batch = "vibe.vision.touch", timeoutTicks = 60)
    public static void touchingAnotherPlayersChestGivesAVisionOfThem(GameTestHelper h) {
        VibeVisions.clear();
        ServerPlayer p = player(h, "vibe-touch", 15, 5, 1);
        ServerPlayer owner = player(h, "vibe-owner", 0, 5, 90);
        BlockPos chest = new BlockPos(5, 2, 3);
        guarded(() -> {
            h.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
            // Eyes 1.62 up and two steps back: looking 30 degrees down puts the hand on the chest's front.
            p.setXRot(30);
            h.assertFalse(VibeVisions.touch(p), "A chest nobody is remembered on is only read as a scene");
            VibeVisions.placed(h.getLevel(), h.absolutePos(chest), owner.getUUID());
            h.assertTrue(owner.getUUID().equals(VibeVisions.placedBy(h.getLevel(), h.absolutePos(chest))),
                    "A chest remembers who placed it");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false) == AbilityUseResult.SUCCESS
                    && VibeVisions.kind(p) == VibeVisions.Kind.TOUCH, "Read the Vibe with a hand on it gives a vision of its owner");
        }, p, owner);
        h.runAfterDelay(VibeVisions.FLICKER + 2, () -> done(h, () -> {
            h.assertTrue(VibeVisions.inside(p) && p.position().distanceToSqr(owner.position()) < 49,
                    "The viber watches beside the owner, however far off they are: " + p.position());
            VibeVisions.wake(p);
            h.assertTrue(VibeVisions.touch(p) && !VibeVisions.watching(p),
                    "Touching it again at once is refused: one vision by touch every thirty seconds");
        }, p, owner));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.vision.pet", timeoutTicks = 40)
    public static void touchingAPlayersPetGivesAVisionOfItsOwner(GameTestHelper h) {
        VibeVisions.clear();
        ServerPlayer p = player(h, "vibe-pet", 15, 5, 1);
        ServerPlayer owner = player(h, "vibe-master", 0, 5, 70);
        var wolf = net.minecraft.world.entity.EntityType.WOLF.create(h.getLevel());
        wolf.setNoAi(true);
        wolf.moveTo(p.position().add(0, 0, 1.6));
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        h.getLevel().addFreshEntity(wolf);
        try {
            done(h, () -> {
                p.setXRot(37);
                h.assertTrue(VibeVisions.touch(p) && !VibeVisions.watching(p),
                        "A pet whose owner can't be seen spends the read but shows nobody");
                wolf.setOwnerUUID(owner.getUUID());
                h.assertTrue(VibeVisions.touch(p) && VibeVisions.kind(p) == VibeVisions.Kind.TOUCH,
                        "A hand on someone's tamed wolf gives a vision of them");
            }, p, owner);
        } finally {
            wolf.discard();
        }
    }
}
