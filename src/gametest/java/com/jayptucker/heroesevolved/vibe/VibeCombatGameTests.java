package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Floor breaches, the Rift Beast and Vibe's fighting style (Breach Boxing). */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VibeCombatGameTests {
    /** A viber at (5, 2, 2) facing +Z, in the level's player list, who can really be teleported. */
    private static ServerPlayer viber(GameTestHelper h) {
        ServerPlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "vibe-combat")) {
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
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.VIBE_ID).unlock(ModAbilities.VIBE_ID));
        PlayerProgressionService.setLevel(player, 5);
        PlayerEnergyService.restore(player, 999);
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 2))));
        player.setYRot(0);
        player.setXRot(0);
        player.setOnGround(true);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static Pillager target(GameTestHelper h, Vec3 feet) {
        Pillager mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.setPos(feet);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void clean(List<Entity> cleanup) {
        for (Entity entity : cleanup) {
            if (entity instanceof ServerPlayer player) {
                VibeService.forget(player);
                VibeBreaches.close(player);
                FightingStyles.forget(player);
                if (player.serverLevel().getPlayerByUUID(player.getUUID()) != null)
                    player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            } else {
                entity.discard();
            }
        }
    }

    private static void guarded(List<Entity> cleanup, Runnable check) {
        try {
            check.run();
        } catch (RuntimeException | AssertionError failure) {
            clean(cleanup);
            throw failure;
        }
    }

    private static void done(GameTestHelper h, List<Entity> cleanup, Runnable check) {
        try {
            check.run();
            h.succeed();
        } finally {
            clean(cleanup);
        }
    }

    // ------------------------------------------------------------------ floor breaches

    @GameTest(template = "rewind_empty", batch = "vibe.floor", skyAccess = true, timeoutTicks = 210)
    public static void shiftBreachTracksAnUpwardAimAndCanBeCrossedInTheAir(GameTestHelper h) {
        ServerPlayer p = viber(h);
        List<Entity> cleanup = List.of(p);
        p.setYRot(22);
        p.setXRot(-56);
        p.setNoGravity(true);
        Vec3 start = p.position(), eye = p.getEyePosition(), aim = p.getLookAngle();
        guarded(cleanup, () -> h.assertTrue(VibeBreaches.shiftHold(p), "Shift + Breach opens along an upward aim"));
        Vec3 near = VibeBreaches.mouthAt(p, 0), far = VibeBreaches.mouthAt(p, 1);
        guarded(cleanup, () -> {
            h.assertTrue(near.distanceToSqr(eye.add(aim.scale(2))) < 1e-5, "The near mouth is on the aim ray");
            h.assertTrue(far.y > near.y + 3 && far.subtract(eye).normalize().dot(aim) > .999,
                    "The exit stays on the upward ray in open air");
        });
        Vec3 bodyOffset = p.getBoundingBox().getCenter().subtract(p.position());
        h.runAfterDelay(200, () -> clean(cleanup));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(VibeBreaches.view(p, 0) != null, "The aimed exit finishes loading"))
                .thenExecute(() -> p.setPos(near.subtract(aim.scale(.5)).subtract(bodyOffset)))
                .thenExecuteAfter(2, () -> p.setPos(near.add(aim.scale(.5)).subtract(bodyOffset)))
                .thenExecuteAfter(2, () -> {
                    try {
                        h.assertTrue(p.getBoundingBox().getCenter().distanceToSqr(far.add(aim.scale(1.14))) < .1,
                                "Crossing a tilted mouth takes the traveler out the far one in midair: " + p.position());
                        VibeBreaches.close(p);
                        p.setPos(start);
                        p.setYRot(0);
                        p.setXRot(45);
                        p.setOnGround(true);
                        h.assertTrue(VibeBreaches.open(p, false) && !VibeBreaches.flat(p) && VibeBreaches.mouthAt(p, 1).z > start.z + 5,
                                "Aiming diagonally down at nearby ground opens a tilted breach with room to cross");
                    } finally {
                        clean(cleanup);
                    }
                }).thenSucceed();
    }

    @GameTest(template = "rewind_empty", batch = "vibe.floor")
    public static void lookingDownOpensABreachUnderfootThatDropsYouOutOfTheAirAhead(GameTestHelper h) {
        ServerPlayer p = viber(h);
        List<Entity> cleanup = new ArrayList<>(List.of(p));
        // The aimed drop reaches twelve blocks. Aim diagonally so the landing stays inside the 12-by-12 arena.
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        p.setYRot(-45);
        p.setXRot(80);
        p.setOnGround(true);
        Vec3 start = p.position();
        Vec3[] ceiling = new Vec3[1];
        guarded(cleanup, () -> {
            h.assertTrue(VibeBreaches.shiftHold(p),
                    "Shift + Breach looking down opens a breach");
            h.assertTrue(VibeBreaches.flat(p), "and its near mouth lies flat in the floor");
            ceiling[0] = VibeBreaches.mouthAt(p, 1);
            h.assertTrue(VibeBreaches.mouthAt(p, 0).distanceToSqr(start) < .01, "The hole is right under the viber's feet");
            h.assertTrue(Math.abs(ceiling[0].y - (start.y + VibeBreaches.DROP)) < .01 && ceiling[0].z - start.z > 4,
                    "The far mouth opens in the air, three blocks above the ground ahead: " + ceiling[0]);
        });
        h.runAfterDelay(12, () -> guarded(cleanup, () -> {
            h.assertTrue(VibeBreaches.view(p, 0) != null, "Looking down the hole shows the ground under the far mouth");
            h.assertTrue(p.position().distanceToSqr(ceiling[0].subtract(0, 2.05, 0)) < .05,
                    "Standing on the hole drops the viber out of the far mouth: " + p.position());
        }));
        h.runAfterDelay(14, () -> guarded(cleanup, () -> {
            h.assertTrue(p.position().distanceToSqr(start) > 9, "and they aren't dropped straight back");
            VibeBreaches.close(p);
            p.setPos(start);
            p.setOnGround(true);
            h.assertTrue(VibeBreaches.open(p, false), "Another floor breach can open at the same spot");
            ceiling[0] = VibeBreaches.mouthAt(p, 1);
            p.setPos(start.add(2, 0, 0));
        }));
        h.runAfterDelay(24, () -> guarded(cleanup, () -> {
            h.assertTrue(VibeBreaches.view(p, 0) != null, "The second breach is open");
            h.getLevel().setBlockAndUpdate(BlockPos.containing(ceiling[0].subtract(0, 1, 0)), Blocks.STONE.defaultBlockState());
            p.setPos(start);
        }));
        h.runAfterDelay(26, () -> done(h, cleanup, () -> h.assertTrue(!VibeBreaches.isOpen(p)
                && p.position().distanceToSqr(start) < .05, "A newly obstructed ceiling closes before it can drop anyone")));
    }

    // ------------------------------------------------------------------ the Rift Beast

    @GameTest(template = "rewind_empty", batch = "vibe.beast")
    public static void theRiftBeastFightsWhatItsOwnerFightsAndNeverItsOwner(GameTestHelper h) {
        ServerPlayer p = viber(h);
        Pillager hit = target(h, p.position().add(-3, 0, 7));
        Pillager pointed = target(h, p.position().add(0, 0, 6));
        List<Entity> cleanup = new ArrayList<>(List.of(p, hit, pointed));
        done(h, cleanup, () -> {
            h.assertTrue(VibeBeast.summon(new AbilityUseContext(p, ModAbilities.VIBE_ID, 5, false)), "A beast comes through a breach");
            Hoglin body = VibeBeast.body(p);
            cleanup.add(body);
            h.assertTrue(body != null && body.isAlive() && VibeBeast.isBeast(body), "It is a tagged Nether beast");
            h.assertTrue(body.saveWithoutId(new CompoundTag()).getBoolean("IsImmuneToZombification"),
                    "that never zombifies in the overworld");
            h.assertFalse(VibeBeast.summon(new AbilityUseContext(p, ModAbilities.VIBE_ID, 5, false)), "One beast at a time");
            h.assertTrue(VibeBeast.refuses(body, p), "Its own brain may never set its sights on its owner");
            h.assertFalse(VibeBeast.refuses(body, hit), "but may on a foe");
            p.setLastHurtMob(hit);
            VibeBeast.step(p);
            h.assertTrue(VibeBeast.fighting(p) == hit, "Like a wolf, it goes for what its owner just hit");
            p.setYRot(0);
            p.setXRot(0);
            h.assertTrue(VibeBeast.command(new AbilityUseContext(p, ModAbilities.VIBE_ID, 5, false)), "A second press commands it");
            VibeBeast.step(p);
            h.assertTrue(VibeBeast.fighting(p) == pointed, "and sends it at what the viber looks at");
            h.assertFalse(VibeBeast.command(new AbilityUseContext(p, ModAbilities.VIBE_ID, 5, true)),
                    "Shift never commands (it falls through to send the beast home)");
            h.assertTrue(VibeBeast.dismiss(p) && body.isRemoved() && !VibeBeast.alive(p), "Shift sends it home through a breach");
            Hoglin stray = EntityType.HOGLIN.create(h.getLevel());
            stray.getPersistentData().putUUID(VibeBeast.TAG, p.getUUID());
            h.assertTrue(VibeBeast.orphan(stray), "A tagged body this server didn't put out is turned away when it loads");
            stray.discard();
        });
    }

    // ------------------------------------------------------------------ Breach Boxing

    @GameTest(template = "rewind_empty", batch = "vibe.style")
    public static void breachBoxingRingsThroughKicksFromBehindAndSweeps(GameTestHelper h) {
        ServerPlayer p = viber(h);
        Pillager first = target(h, p.position().add(0, 0, 2.5));
        Pillager second = target(h, p.position().add(0, 0, 4.2));
        Pillager far = target(h, p.position().add(0, 0, 6));
        List<Entity> cleanup = new ArrayList<>(List.of(p, first, second, far));
        done(h, cleanup, () -> {
            h.assertTrue(FightingStyles.of(p) == VibeStyle.STYLE, "Vibe fights with Breach Boxing");
            VibeStyle.STYLE.jab(p, 5, false);
            h.assertTrue(first.getHealth() < 24 && second.getHealth() < 24, "A jab rings through the first body into the one behind");
            h.assertTrue(far.getHealth() == 24, "but no further");
            first.discard();
            second.discard();
            float before = far.getHealth();
            VibeStyle.STYLE.kick(p, 5);
            h.assertTrue(far.getHealth() < before, "The Breach Kick reaches a foe six blocks off");
            h.assertTrue(far.getDeltaMovement().z < 0, "and kicks them toward the kicker, from behind: " + far.getDeltaMovement());
            Pillager low = target(h, p.position().add(.3, 0, 3));
            cleanup.add(low);
            VibeStyle.STYLE.seam(p, 5);
            h.assertTrue(low.getHealth() < 24 && low.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Seam Sweep trips and slows a foe on its line");
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.style")
    public static void breachHopAndFoldBackGoThroughBreaches(GameTestHelper h) {
        ServerPlayer p = viber(h);
        List<Entity> cleanup = new ArrayList<>(List.of(p));
        Vec3 start = p.position();
        guarded(cleanup, () -> {
            h.assertTrue(VibeStyle.STYLE.dodge(p, FightingStyles.Dodge.LEFT), "A double-tapped side dodges");
            h.assertTrue(VibeStyle.hopping(p) && p.position().distanceTo(start) < 2,
                    "Breach Hop: a tear opens at the side and the viber dives into it");
        });
        h.runAfterDelay(VibeStyle.HOP_DIVE + 4, () -> done(h, cleanup, () -> {
            h.assertTrue(!VibeStyle.hopping(p) && p.position().distanceTo(start) > 3.5,
                    "and comes out of the far tear, several blocks along: " + p.position());
            VibeStyle.STYLE.fighter(p).dodgeReady = -1000;
            p.setPos(start.add(0, 0, 4));
            Vec3 spot = p.position();
            Pillager lurker = target(h, spot.add(0, 0, 1.2));
            cleanup.add(lurker);
            h.assertTrue(VibeStyle.STYLE.dodge(p, FightingStyles.Dodge.BACK), "Back dodges from level 2");
            h.assertTrue(p.position().distanceTo(spot) > 3, "Fold Back steps back through a breach: " + p.position());
            h.assertTrue(VibeStyle.primed(p), "and leaves the spot it left primed");
            VibeStyle.burstAll(p);
            h.assertTrue(lurker.getHealth() < 24 && !VibeStyle.primed(p), "The primed spot bursts on a foe beside it");
        }));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.style")
    public static void aDodgedBlowIsAnsweredFromBehind(GameTestHelper h) {
        ServerPlayer p = viber(h);
        Pillager attacker = target(h, p.position().add(0, 0, 4));
        List<Entity> cleanup = new ArrayList<>(List.of(p, attacker));
        done(h, cleanup, () -> {
            VibeStyle.STYLE.counter(p, p.damageSources().mobAttack(attacker));
            h.assertTrue(VibeStyle.STYLE.loaded(p), "A dodged blow loads the next strike");
            h.assertTrue(VibeStyle.STYLE.strike(p) == 1, "The loaded strike is thrown");
            h.assertTrue(attacker.getHealth() <= 24 - 9 + .01, "It lands from behind for 4 + level: " + attacker.getHealth());
            h.assertTrue(attacker.getDeltaMovement().z < 0, "and throws the attacker toward the viber");
        });
    }
}
