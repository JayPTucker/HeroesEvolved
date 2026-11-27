package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.amplify.AmpService;
import com.jayptucker.heroesevolved.amplify.Overdrive;
import com.jayptucker.heroesevolved.assembly.Ballista;
import com.jayptucker.heroesevolved.assembly.Matter;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.flight.Microburst;
import com.jayptucker.heroesevolved.hydro.Leviathan;
import com.jayptucker.heroesevolved.invisibility.Bastion;
import com.jayptucker.heroesevolved.mimicry.Chorus;
import com.jayptucker.heroesevolved.phasing.MountainArmor;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.quake.Epicenter;
import com.jayptucker.heroesevolved.radiation.FissionStar;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.sonic.StandingWave;
import com.jayptucker.heroesevolved.speed.LightningRound;
import com.jayptucker.heroesevolved.telepathy.Puppeteer;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The twelve signatures run by {@link Signatures}: each one cast through its key (so the tree node, the routing and the
 * cost all work), then checked at the ticks its own class names.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SignatureSetGameTests {
    /** A stone floor under (3..13, 39, 3..13), so ground-aimed signatures have somewhere to land. */
    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 15; x++) for (int z = 0; z <= 15; z++) h.setBlock(new BlockPos(x, 39, z), Blocks.STONE);
    }

    private static ServerPlayer caster(GameTestHelper h, ResourceLocation power, String node, float pitch) {
        floor(h);
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "sig-" + node.substring(0, Math.min(10, node.length()))));
        try {
            Connection connection = p.connection.getConnection();
            var channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 40, 5)));
        p.moveTo(at.x, at.y, at.z, 0, pitch);
        p.setOnGround(true);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, 4);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(power.toString(),
                new SkillTreeData.Build(5, Set.of(node), Map.of(), 3)));
        h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, node), node + " equips");
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static boolean cast(ServerPlayer p) {
        return AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS;
    }

    private static Mob foe(GameTestHelper h, List<Mob> keep, Vec3 at) {
        Mob mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired(); mob.setPos(at);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        keep.add(mob);
        return mob;
    }

    /** Runs {@code check} at {@code delay}; tidies the caster and the foes whatever happens, and succeeds if asked. */
    private static void at(GameTestHelper h, int delay, ServerPlayer p, List<Mob> foes, boolean last, Runnable check) {
        h.runAfterDelay(delay, () -> {
            boolean ok = false;
            try { check.run(); ok = true; }
            finally {
                if (!ok || last) { Signatures.forget(p); foes.forEach(Mob::discard); }
            }
            if (last) h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.epicenter", timeoutTicks = 80)
    public static void epicenterWarnsThenHeavesTheRing(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.QUAKE_ID, "quake_epicenter", 0);
        List<Mob> foes = new ArrayList<>();
        Mob near = foe(h, foes, p.position().add(4, 0, 0)), far = foe(h, foes, p.position().add(0, 0, -1).add(0, 0, 13));
        h.assertTrue(cast(p), "It casts");
        h.assertTrue(near.getHealth() == 100, "The faults only warn at first");
        at(h, Epicenter.HEAVE + 2, p, foes, false, () -> h.assertTrue(near.getHealth() < 100, "The heave throws whoever stands in the ring"));
        at(h, Epicenter.DROP + 2, p, foes, true, () -> {
            h.assertTrue(near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The drop staggers them");
            h.assertTrue(far.getHealth() == 100, "Outside the ring is safe");
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.microburst", timeoutTicks = 80)
    public static void microburstPinsTheRingAndBlowsPastIt(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.FLIGHT_ID, "flight_microburst", 90);
        List<Mob> foes = new ArrayList<>();
        Mob inside = foe(h, foes, p.position().add(3, 0, 0)), beyond = foe(h, foes, p.position().add(0, 0, 8));
        h.assertTrue(cast(p), "It casts");
        at(h, Microburst.SLAM + 2, p, foes, false, () -> {
            h.assertTrue(inside.getHealth() < 100 && inside.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The column hurts and pins the ring");
            h.assertTrue(beyond.getHealth() == 100, "Past the ring it doesn't hurt");
        });
        at(h, Microburst.OUTFLOW, p, foes, true, () -> h.assertTrue(beyond.getDeltaMovement().horizontalDistanceSqr() > .01, "The outflow throws whoever stands past it"));
    }

    @GameTest(template = "rewind_empty", batch = "signatures.puppeteer", timeoutTicks = 60)
    public static void puppetsTurnOnEachOtherUntilTheStringsAreCut(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.TELEPATHY_ID, "telepathy_puppeteer", 0);
        List<Mob> foes = new ArrayList<>();
        Mob a = foe(h, foes, p.position().add(-2, 0, 5)), b = foe(h, foes, p.position().add(2, 0, 5));
        h.assertTrue(cast(p), "It casts");
        h.assertTrue(Puppeteer.puppets(p).size() == 2, "Both are strung");
        h.assertTrue(a.getTarget() == b && b.getTarget() == a, "Each turns on the other");
        at(h, 5, p, foes, true, () -> {
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(cast(p), "A second press cuts the strings");
            h.assertTrue(PlayerEnergyService.getEnergy(p) == energy && Puppeteer.puppets(p).isEmpty(), "For free, and it ends");
            h.assertTrue(a.getHealth() < 100 && b.getHealth() < 100 && a.hasEffect(MobEffects.WEAKNESS), "Each puppet is hurt and left reeling");
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.fission_star", timeoutTicks = 80)
    public static void theStarGrowsIsThrownAndGoesOffOnAFoe(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.RADIATION_ID, "radiation_fission_star", 0);
        List<Mob> foes = new ArrayList<>();
        Mob target = foe(h, foes, p.position().add(0, 0, 6));
        h.assertTrue(cast(p), "It casts");
        at(h, FissionStar.CHARGE - 2, p, foes, false, () -> h.assertTrue(FissionStar.core(p) == null && target.getHealth() == 100, "Still charging"));
        at(h, FissionStar.CHARGE + 14, p, foes, true, () -> {
            h.assertTrue(FissionStar.core(p) == null, "It has gone off");
            h.assertTrue(target.getHealth() < 100, "On the foe in its path");
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.standing_wave", timeoutTicks = 60)
    public static void theWavePulsesThenBreaksOnASecondPress(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.SONIC_RESONANCE_ID, "sonic_resonance_standing_wave", 90);
        List<Mob> foes = new ArrayList<>();
        Mob in = foe(h, foes, p.position().add(3, 0, 0));
        h.assertTrue(cast(p), "It casts");
        float[] afterPulse = new float[1];
        at(h, StandingWave.PERIOD + 1, p, foes, false, () -> {
            afterPulse[0] = in.getHealth();
            h.assertTrue(StandingWave.beat(p) == 1 && afterPulse[0] < 100, "The first beat hurts a little");
        });
        at(h, StandingWave.PERIOD + 4, p, foes, true, () -> {
            h.assertTrue(cast(p), "A second press breaks the note");
            h.assertTrue(StandingWave.beat(p) == -1, "And ends it");
            h.assertTrue(in.getHealth() < afterPulse[0] - 5, "The break hurts far more than a beat: " + in.getHealth());
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.lightning_round", timeoutTicks = 60)
    public static void theRoundVisitsEveryFoeAndComesHome(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.SUPERSPEED_ID, "superspeed_lightning_round", 0);
        Vec3 start = p.position();
        List<Mob> foes = new ArrayList<>();
        Mob a = foe(h, foes, start.add(0, 0, 5)), b = foe(h, foes, start.add(5, 0, 5)), c = foe(h, foes, start.add(-4, 0, 3));
        h.assertTrue(cast(p), "It casts");
        at(h, 3 * LightningRound.HOP + 4, p, foes, true, () -> {
            h.assertTrue(a.getHealth() < 100 && b.getHealth() < 100 && c.getHealth() < 100, "Every foe is struck");
            h.assertTrue(p.position().distanceTo(start) < .2, "And the speedster is back where they started: " + p.position().distanceTo(start));
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.bastion", timeoutTicks = 60)
    public static void theDomeTurnsShotsShovesFoesAndBursts(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.INVISIBILITY_ID, "invisibility_bastion", 0);
        List<Mob> foes = new ArrayList<>();
        Mob archer = foe(h, foes, p.position().add(0, 0, 12)), inside = foe(h, foes, p.position().add(2, 0, 0));
        h.assertTrue(cast(p), "It casts");
        h.assertTrue(Bastion.shelters(p), "Its caster is sheltered");
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setOwner(archer);
        arrow.setNoGravity(true);
        arrow.setPos(p.position().add(0, 1.5, 7.5));
        arrow.setDeltaMovement(0, 0, -1.2);
        h.getLevel().addFreshEntity(arrow);
        at(h, 6, p, foes, false, () -> {
            h.assertTrue(Bastion.turned(p) >= 1 && arrow.getDeltaMovement().z > 0, "The shot glances off and flies back");
            h.assertTrue(inside.getDeltaMovement().x > 0, "The foe inside is shoved out through the wall");
            arrow.discard();
        });
        at(h, 8, p, foes, true, () -> {
            h.assertTrue(cast(p), "A second press bursts it");
            h.assertTrue(inside.getHealth() < 100 && !Bastion.shelters(p), "The burst hurts, and the dome is gone");
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.mountain_armor", timeoutTicks = 60)
    public static void theMountainClosesThenBlastsOff(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.PHASING_ID, "phasing_mountain_armor", 0);
        List<Mob> foes = new ArrayList<>();
        Mob near = foe(h, foes, p.position().add(0, 0, 3));
        double armor = p.getAttributeValue(Attributes.ARMOR);
        h.assertTrue(cast(p), "It casts");
        h.assertTrue(!MountainArmor.wearing(p), "Not until the plates have closed");
        at(h, MountainArmor.ASSEMBLE + 1, p, foes, false, () -> {
            h.assertTrue(MountainArmor.wearing(p) && p.getAttributeValue(Attributes.ARMOR) >= armor + 10, "Closed: +10 armor");
            h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1, "And no knockback");
        });
        at(h, MountainArmor.ASSEMBLE + 3, p, foes, true, () -> {
            h.assertTrue(cast(p), "A second press blasts the plates off");
            h.assertTrue(near.getHealth() < 100, "Into the foe beside her");
            h.assertTrue(p.getAttributeValue(Attributes.ARMOR) == armor, "And the armor is gone");
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.chorus", timeoutTicks = 60)
    public static void theVoicesSpeakOneByOneThenAllTogether(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.MIMICRY_ID, "empathic_mimicry_chorus", 0);
        List<Mob> foes = new ArrayList<>();
        Mob target = foe(h, foes, p.position().add(0, 0, 6));
        h.assertTrue(Chorus.voicesOf(p).size() >= 3, "At least three voices sing");
        h.assertTrue(cast(p), "It casts");
        at(h, Chorus.FIRE + 1, p, foes, false, () -> h.assertTrue(Chorus.fired(p) == 1 && target.getHealth() < 100, "A voice leaps at the foe"));
        at(h, Chorus.FIRE + 3, p, foes, true, () -> {
            int before = Chorus.fired(p);
            h.assertTrue(cast(p), "A second press makes them all speak");
            h.assertTrue(Chorus.fired(p) == -1 && before == 1, "And ends it");
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.ballista", timeoutTicks = 80)
    public static void theBallistaFoldsTogetherShootsAndGivesItsMatterBack(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.MATTER_ASSEMBLY_ID, "matter_assembly_ballista", 90);
        Matter.fill(p);
        int full = Matter.of(p);
        List<Mob> foes = new ArrayList<>();
        Mob target = foe(h, foes, p.position().add(0, 0, 6));
        h.assertTrue(cast(p), "It casts");
        h.assertTrue(Matter.of(p) == full - Ballista.COST, "It spends its Matter");
        at(h, Ballista.BUILD - 2, p, foes, false, () -> h.assertTrue(target.getHealth() == 100, "Nothing until the drawing has finished it"));
        at(h, Ballista.BUILD + Ballista.flight(6) + 3, p, foes, true, () -> {
            h.assertTrue(Ballista.hits(p) >= 1 && target.getHealth() < 100, "Its first bolt lands");
            h.assertTrue(cast(p), "A second press folds it");
            h.assertTrue(Matter.of(p) == full, "And the Matter comes home");
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.leviathan", timeoutTicks = 80)
    public static void theSerpentRearsThenBitesTheMarkedSpot(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.HYDROKINESIS_ID, "hydrokinesis_leviathan", 90);
        List<Mob> foes = new ArrayList<>();
        Mob target = foe(h, foes, p.position().add(3, 0, 0));
        h.assertTrue(cast(p), "It casts");
        int bite = Leviathan.strikeAt(0) + Leviathan.WINDUP + Leviathan.LUNGE;
        at(h, bite - 2, p, foes, false, () -> h.assertTrue(target.getHealth() == 100, "It rears first: the spot only warns"));
        at(h, bite + 2, p, foes, true, () -> {
            h.assertTrue(Leviathan.bitten(p) >= 1 && target.getHealth() < 100, "Then lunges onto it");
            h.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "And slows what it bit");
        });
    }

    @GameTest(template = "rewind_empty", batch = "signatures.overdrive", timeoutTicks = 60)
    public static void overdriveFillsTheChargeAndDischargesAlone(GameTestHelper h) {
        ServerPlayer p = caster(h, ModAbilities.AMPLIFICATION_ID, "power_amplification_overdrive", 0);
        List<Mob> foes = new ArrayList<>();
        Mob target = foe(h, foes, p.position().add(0, 0, 5));
        h.assertTrue(cast(p), "It casts");
        h.assertTrue(Overdrive.linkedOf(p).size() == 1 && AmpService.charge(p) == AmpService.MAX_CHARGE, "Alone, it links him and fills his Charge");
        at(h, 4, p, foes, true, () -> {
            h.assertTrue(cast(p), "A second press discharges it");
            h.assertTrue(target.getHealth() < 100, "The forks find the foe");
            h.assertTrue(Overdrive.linkedOf(p).isEmpty(), "And it ends");
        });
    }
}
