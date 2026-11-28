package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every class Ultimate, cast for real by a test player at tier 5, one batch each (their blasts, searches and slams
 * reach past the arena next door).
 * <ul>
 *     <li>Offense: takes the foe in front, holds it, lands its damage, gives the foe its mind back, and leaves the caster
 *     out of any wall, near where they began.</li>
 *     <li>Defense: does what it promises against the first blow, shot, killing blow or hunter that comes.</li>
 *     <li>Support: leaves the hurt caster better off (healed, shielded or strengthened).</li>
 * </ul>
 * Each ends cleanly when stopped early, the way death, logout or a dampener would end it.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class UltimateGameTests {
    private static final List<String> POWERS = List.of("superspeed", "pyrokinesis", "electrokinesis", "hydrokinesis", "cryokinesis",
            "flight", "quake", "sonic_resonance", "telepathy", "telekinesis", "time_manipulation", "regeneration", "invisibility",
            "empathic_mimicry", "matter_assembly", "vibe", "power_amplification", "shapeshifting", "phasing", "radiation");

    /** Defenses that answer a killing blow, not every blow. */
    private static final Set<String> LETHAL = Set.of("undying", "time_loop");
    /** Defenses that answer shots: they stop, slow or drop them. */
    private static final Set<String> SHOTS = Set.of("silence_dome", "debris_halo");
    /** Defenses that make hunters lose the caster (Between Heartbeats: they turn on its illusions). */
    private static final Set<String> LOST = Set.of("blind_spot", "phase_blur");

    private static final float FOE_HEALTH = 200;

    @GameTestGenerator
    public static List<TestFunction> everyUltimate() {
        List<TestFunction> tests = new ArrayList<>();
        for (String path : POWERS) {
            ResourceLocation power = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path);
            for (SkillCatalog.Node node : SkillCatalog.styles(path)) {
                String mechanic = node.mechanic();
                tests.add(new TestFunction("ult." + mechanic, "ultimate." + mechanic, "heroesevolved:rewind_empty", Rotation.NONE,
                        400, 0, true, false, 1, 1, false, h -> run(h, power, node.role(), mechanic)));
            }
        }
        return tests;
    }

    private static void run(GameTestHelper h, ResourceLocation power, String role, String mechanic) {
        switch (role) {
            case "offense" -> offense(h, power, mechanic);
            case "defense" -> defense(h, power, mechanic);
            default -> support(h, power, mechanic);
        }
    }

    // ------------------------------------------------------------------
    // Offense
    // ------------------------------------------------------------------

    private static void offense(GameTestHelper h, ResourceLocation power, String mechanic) {
        ServerPlayer p = caster(h, power);
        Pillager foe = foe(h, p, 3.5, false);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, foe.getBoundingBox().getCenter());
        Vec3 start = p.position();
        AbilityUseContext c = new AbilityUseContext(p, power, 5, false);
        guarded(p, () -> {
            h.assertTrue(Ultimates.canUse(c, mechanic), mechanic + " can be used with a foe in front");
            h.assertTrue(Ultimates.cast(c, mechanic), mechanic + " starts");
            Ultimates.Run run = Ultimates.run(p);
            h.assertTrue(run != null && run.foe == foe, mechanic + " takes the foe in front");
            h.assertTrue(foe.isNoAi(), mechanic + " holds the foe while it plays");
            h.assertFalse(Ultimates.canUse(c, mechanic), mechanic + " cannot be cast again while it plays");
            h.runAfterDelay(run.ticks() + 4, () -> guarded(p, () -> {
                h.assertFalse(Ultimates.running(p), mechanic + " ends on time");
                float dealt = FOE_HEALTH - (foe.isAlive() ? foe.getHealth() : 0);
                h.assertTrue(dealt >= 10, mechanic + " lands at least 10 damage (dealt " + dealt + ")");
                h.assertFalse(foe.isNoAi(), mechanic + " gives the foe its mind back");
                h.assertFalse(foe.getPersistentData().getBoolean("heroesevolved_ultimate_held"), mechanic + " clears its hold mark");
                h.assertTrue(clear(h, p), mechanic + " leaves the caster out of any wall, at " + p.blockPosition());
                h.assertTrue(p.position().distanceTo(start) < 24, mechanic + " brings the caster back near where they began ("
                        + Math.round(p.position().distanceTo(start)) + " blocks away)");
                foe.discard();
                h.succeed();
            }));
        });
    }

    // ------------------------------------------------------------------
    // Defense
    // ------------------------------------------------------------------

    private static void defense(GameTestHelper h, ResourceLocation power, String mechanic) {
        ServerPlayer p = caster(h, power);
        Pillager attacker = foe(h, p, 2, true);
        AbilityUseContext c = new AbilityUseContext(p, power, 5, false);
        guarded(p, () -> {
            h.assertTrue(Ultimates.canUse(c, mechanic), mechanic + " can be used");
            h.assertTrue(Ultimates.cast(c, mechanic), mechanic + " starts");
            Ultimates.Run run = Ultimates.run(p);
            h.assertTrue(run != null && run.foe == null, mechanic + " runs without a held foe");
        });
        if (LETHAL.contains(mechanic)) {
            h.runAfterDelay(30, () -> guarded(p, () -> {
                p.setHealth(4);
                p.invulnerableTime = 0;
                p.hurt(p.damageSources().mobAttack(attacker), 60);
                h.assertTrue(p.isAlive() && p.getHealth() > 0, mechanic + " survives a killing blow (" + p.getHealth() + " health)");
                end(h, p, mechanic, 4);
            }));
        } else if (SHOTS.contains(mechanic)) {
            Arrow[] shot = new Arrow[1];
            h.runAfterDelay(30, () -> guarded(p, () -> {
                Arrow arrow = EntityType.ARROW.create(h.getLevel());
                arrow.setOwner(attacker);
                arrow.setNoGravity(true);
                // From the side, so its line never crosses the attacker standing in front.
                arrow.setPos(p.getX() + 4, p.getY() + 1.2, p.getZ());
                arrow.setDeltaMovement(-1.2, 0, 0);
                h.getLevel().addFreshEntity(arrow);
                shot[0] = arrow;
            }));
            h.runAfterDelay(38, () -> guarded(p, () -> {
                Arrow arrow = shot[0];
                double speed = arrow.getDeltaMovement().horizontalDistance();
                h.assertTrue(arrow.isRemoved() || speed < .4, mechanic + " stops or drops a shot at the caster (speed " + speed + ")");
                arrow.discard();
                end(h, p, mechanic, 4);
            }));
        } else if (LOST.contains(mechanic)) {
            Zombie hunter = EntityType.ZOMBIE.create(h.getLevel());
            hunter.setPersistenceRequired();
            hunter.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0, 0);
            h.getLevel().addFreshEntity(hunter);
            hunter.setTarget(p);
            h.runAfterDelay(30, () -> guarded(p, () -> {
                h.assertTrue(hunter.getTarget() != p, mechanic + " makes a hunter lose the caster");
                hunter.discard();
                end(h, p, mechanic, 4);
            }));
        } else {
            // Late enough for a shell built panel by panel (Shell of Twelve is whole at 42 ticks).
            h.runAfterDelay(50, () -> guarded(p, () -> {
                float before = p.getHealth();
                p.invulnerableTime = 0;
                p.hurt(p.damageSources().mobAttack(attacker), 6);
                float taken = before - p.getHealth();
                h.assertTrue(taken < 6, mechanic + " softens or turns a 6-point blow (took " + taken + ")");
                end(h, p, mechanic, 4);
            }));
        }
    }

    // ------------------------------------------------------------------
    // Support
    // ------------------------------------------------------------------

    private static void support(GameTestHelper h, ResourceLocation power, String mechanic) {
        ServerPlayer p = caster(h, power);
        p.setHealth(8);
        AbilityUseContext c = new AbilityUseContext(p, power, 5, false);
        boolean[] helped = new boolean[1];
        guarded(p, () -> {
            h.assertTrue(Ultimates.canUse(c, mechanic), mechanic + " can be used");
            h.assertTrue(Ultimates.cast(c, mechanic), mechanic + " starts");
            h.assertTrue(Ultimates.run(p) != null, mechanic + " is running");
        });
        h.onEachTick(() -> {
            if (p.getHealth() > 8 || p.getAbsorptionAmount() > 0
                    || p.getActiveEffects().stream().anyMatch(e -> e.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL))
                helped[0] = true;
        });
        h.runAfterDelay(80, () -> guarded(p, () -> {
            h.assertTrue(helped[0], mechanic + " leaves a hurt caster better off (healed, shielded or strengthened)");
            end(h, p, mechanic, 4);
        }));
    }

    // ------------------------------------------------------------------
    // Shared
    // ------------------------------------------------------------------

    /** Stops the Ultimate early, the way death, logout or a dampener would, and checks it is over. */
    private static void end(GameTestHelper h, ServerPlayer p, String mechanic, int after) {
        Ultimates.stop(p);
        h.assertFalse(Ultimates.running(p), mechanic + " stops when told to");
        h.runAfterDelay(after, () -> {
            h.assertTrue(clear(h, p), mechanic + " leaves the caster out of any wall");
            h.succeed();
        });
    }

    private static boolean clear(GameTestHelper h, ServerPlayer p) {
        return h.getLevel().noCollision(p, p.getBoundingBox().deflate(.05));
    }

    private static void guarded(ServerPlayer p, Runnable step) {
        try { step.run(); }
        catch (RuntimeException | AssertionError e) { Ultimates.stop(p); throw e; }
    }

    private static ServerPlayer caster(GameTestHelper h, ResourceLocation power) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ultimate-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true);
            grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 3))));
        p.setYRot(0);
        p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    /** A sturdy pillager {@code ahead} blocks in front (with its mind, unless {@code still}). */
    private static Pillager foe(GameTestHelper h, ServerPlayer p, double ahead, boolean still) {
        Pillager foe = EntityType.PILLAGER.create(h.getLevel());
        foe.setNoAi(still);
        foe.setPersistenceRequired();
        var health = foe.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(FOE_HEALTH);
        foe.setHealth(FOE_HEALTH);
        foe.moveTo(p.getX(), p.getY(), p.getZ() + ahead, 180, 0);
        h.getLevel().addFreshEntity(foe);
        return foe;
    }
}
