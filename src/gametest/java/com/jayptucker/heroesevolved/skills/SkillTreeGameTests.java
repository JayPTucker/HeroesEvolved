package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.*;
import com.jayptucker.heroesevolved.ability.service.*;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.sonic.SonicService;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SkillTreeGameTests {
    private static final List<String> POWERS = List.of("quake", "flight", "cryokinesis", "electrokinesis", "telekinesis",
            "telepathy", "regeneration", "time_manipulation", "radiation", "sonic_resonance", "superspeed", "precognition", "pyrokinesis",
            "invisibility", "phasing", "empathic_mimicry");
    private static ResourceLocation id(String power) { return ResourceLocation.fromNamespaceAndPath("heroesevolved", power); }
    private static ServerPlayer player(GameTestHelper h, String power, int level) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "skill-test"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id(power)).unlock(id(power)));
        PlayerProgressionService.setLevel(p, level); return p;
    }
    private static void cleanup(ServerPlayer p) {
        SkillActionService.forget(p);
        com.jayptucker.heroesevolved.expansion.PrecognitionService.forget(p); SonicService.stop(p);
        com.jayptucker.heroesevolved.speed.SpeedService.forget(p);
        com.jayptucker.heroesevolved.time.SpaceTimeService.forget(p);
        com.jayptucker.heroesevolved.flight.FlightService.stopForPowerRemoval(p);
        com.jayptucker.heroesevolved.flight.FlightSkillService.forget(p);
    }
    private static LivingEntity target(GameTestHelper h, ServerPlayer p) {
        var target = EntityType.PILLAGER.create(h.getLevel());
        target.setNoAi(true); target.setPersistenceRequired();
        target.moveTo(p.getX(), p.getY(), p.getZ() + 5, 0, 0);
        h.getLevel().addFreshEntity(target); return target;
    }
    @GameTest(template="rewind_empty", batch="skills.catalog")
    public static void everyRegisteredPowerHasThreeCompleteBranches(GameTestHelper h) {
        h.assertTrue(AbilityRegistry.ABILITIES.size() == POWERS.size(), "Every registered power must have a skill tree");
        for (String power : POWERS) {
            h.assertTrue(AbilityRegistry.ABILITIES.containsKey(id(power)), "Unregistered power in skill catalog");
            var nodes = SkillCatalog.nodes(power);
            h.assertTrue(nodes.size() == 18 && nodes.stream().filter(SkillCatalog.Node::active).count() == 6, "Missing nodes or sub-abilities: " + power);
            h.assertTrue(nodes.stream().map(SkillCatalog.Node::cost).mapToInt(Integer::intValue).sum() >= 15,
                    "Completing all branches must cost more than the eleven level-five points");
        }
        h.succeed();
    }
    @GameTest(template="rewind_empty", batch="skills.purchase")
    public static void pointsLevelsPrerequisitesAndDuplicateRequestsAreValidated(GameTestHelper h) {
        var p = player(h, "sonic_resonance", 1);
        try {
            h.assertTrue(SkillCatalog.available("sonic_resonance", SkillTreeService.build(p, id("sonic_resonance"))) == 3, "Awakening grants three points");
            h.assertTrue(!SkillTreeService.purchase(p, "sonic_resonance_0_1"), "Cannot skip parent or level gate");
            h.assertTrue(!SkillTreeService.purchase(p, "quake_0_0"), "Cannot buy another player's power tree");
            h.assertTrue(SkillTreeService.purchase(p, "sonic_resonance_0_0"), "First Control node should be purchasable");
            h.assertTrue(!SkillTreeService.purchase(p, "sonic_resonance_0_0"), "Repeated packet cannot spend or mint points");
            h.assertTrue(!SkillTreeService.purchase(p, "sonic_resonance_0_1"), "Level one cannot buy an active tier");
            PlayerProgressionService.setLevel(p, 2);
            h.assertTrue(SkillTreeService.purchase(p, "sonic_resonance_0_1"), "Level two can learn the child");
            var build = SkillTreeService.build(p, id("sonic_resonance"));
            h.assertTrue(build.earned() == 5 && SkillCatalog.available("sonic_resonance", build) == 2, "Points must be spent exactly once");
            PlayerProgressionService.setLevel(p, 1); PlayerProgressionService.setLevel(p, 2);
            h.assertTrue(SkillTreeService.build(p, id("sonic_resonance")).earned() == 5, "Level changes cannot re-award points");
            h.succeed();
        } finally { cleanup(p); }
    }
    @GameTest(template="rewind_empty", batch="skills.budget")
    public static void spendingCannotExceedEarnedPointsAndDormantPlayersCannotBuy(GameTestHelper h) {
        var p = player(h, "quake", 1);
        for (int branch = 0; branch < 3; branch++) h.assertTrue(SkillTreeService.purchase(p, "quake_" + branch + "_0"), "Each opening branch should cost one point");
        h.assertTrue(SkillCatalog.available("quake", SkillTreeService.build(p, id("quake"))) == 0, "No points should remain");
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id("quake")));
        h.assertTrue(!SkillTreeService.purchase(p, "quake_0_1") && !SkillTreeService.equip(p, AbilitySlot.PRIMARY, ""), "Dormant powers cannot change builds");
        h.succeed(); cleanup(p);
    }
    @GameTest(template="rewind_empty", batch="skills.persistence")
    public static void purchasesLoadoutsAndPointHistorySurviveSerialization(GameTestHelper h) {
        var p = player(h, "sonic_resonance", 5);
        SkillTreeService.purchase(p, "sonic_resonance_0_0"); SkillTreeService.purchase(p, "sonic_resonance_0_1");
        h.assertTrue(SkillTreeService.equip(p, AbilitySlot.QUATERNARY, "sonic_resonance_0_1"), "Learned active should equip");
        var before = p.getData(ModDataAttachments.SKILL_TREES);
        var encoded = SkillTreeData.CODEC.encodeStart(JsonOps.INSTANCE, before).getOrThrow();
        var after = SkillTreeData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        h.assertTrue(before.equals(after), "Save/load must preserve purchases, remaining points and key assignments");
        var copy = player(h, "sonic_resonance", 5); copy.setData(ModDataAttachments.SKILL_TREES, after);
        h.assertTrue(SkillTreeService.actions(copy, id("sonic_resonance")).get(AbilitySlot.QUATERNARY).definition().id().equals(id("sonic_resonance_0_1")), "Reconnected player must resolve saved action");
        h.assertTrue(!SkillTreeService.purchase(copy, "sonic_resonance_0_1"), "Relog cannot repurchase a node");
        h.succeed(); cleanup(p); cleanup(copy);
    }
    @GameTest(template="rewind_empty", batch="skills.loadout")
    public static void loadoutRejectsForgedNodesMovesOnceAndRestoresDefaults(GameTestHelper h) {
        var p = player(h, "quake", 5);
        h.assertTrue(!SkillTreeService.equip(p, AbilitySlot.PRIMARY, "quake_0_1"), "Unlearned skill cannot equip");
        SkillTreeService.purchase(p, "quake_0_0"); SkillTreeService.purchase(p, "quake_0_1");
        h.assertTrue(!SkillTreeService.equip(p, AbilitySlot.PRIMARY, "quake_0_0"), "Passive nodes cannot occupy active slots");
        h.assertTrue(!SkillTreeService.equip(p, AbilitySlot.PRIMARY, "time_rewind"), "Cannot equip arbitrary action IDs");
        SkillTreeService.equip(p, AbilitySlot.PRIMARY, "quake_0_1"); SkillTreeService.equip(p, AbilitySlot.SECONDARY, "quake_0_1");
        var actions = SkillTreeService.actions(p, id("quake"));
        h.assertTrue(actions.get(AbilitySlot.PRIMARY).definition().id().equals(ModAbilities.QUAKE.get().actions().get(AbilitySlot.PRIMARY).definition().id()), "Moving a skill restores its previous key");
        h.assertTrue(actions.get(AbilitySlot.SECONDARY).definition().id().equals(id("quake_0_1")), "Moved skill must occupy chosen key");
        SkillTreeService.equip(p, AbilitySlot.SECONDARY, "");
        h.assertTrue(SkillTreeService.actions(p, id("quake")).equals(ModAbilities.QUAKE.get().actions()), "Default restore must recover all original skills");
        h.succeed(); cleanup(p);
    }
    @GameTest(template="rewind_empty", batch="skills.activation")
    public static void equippedSkillUsesRealDamageDiscountedEnergyAndPersistentCooldown(GameTestHelper h) {
        var p = player(h, "sonic_resonance", 5); var victim = target(h, p);
        try {
            SkillTreeService.purchase(p, "sonic_resonance_0_0"); SkillTreeService.purchase(p, "sonic_resonance_0_1");
            SkillTreeService.equip(p, AbilitySlot.PRIMARY, "sonic_resonance_0_1");
            int energy = PlayerEnergyService.getEnergy(p); float health = victim.getHealth();
            var result = AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false);
            h.assertTrue(result == AbilityUseResult.SUCCESS && victim.getHealth() < health, "New loadout skill must damage a real aimed mob");
            h.assertTrue(PlayerEnergyService.getEnergy(p) == energy - 24, "26 base stamina with 10% reduction rounds up to 24");
            h.assertTrue(CooldownService.isOnCooldown(p, id("sonic_resonance_0_1")), "Sub-ability must use its own cooldown");
            SkillTreeService.equip(p, AbilitySlot.SECONDARY, "sonic_resonance_0_1");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.ON_COOLDOWN, "Moving a skill cannot reset its cooldown");
            h.succeed();
        } finally { victim.discard(); cleanup(p); }
    }
    @GameTest(template="rewind_empty", batch="skills.bonuses")
    public static void skillBonusesApplyToCostsCooldownsAndAttacks(GameTestHelper h) {
        var p = player(h, "quake", 5); var victim = target(h, p);
        try {
            for (int branch : new int[]{0,1}) for (int tier = 0; tier < 3; tier++) h.assertTrue(SkillTreeService.purchase(p, "quake_" + branch + "_" + tier), "Build must fit ten of eleven points");
            h.assertTrue(SkillTreeService.activationCost(p, id("quake"), 40) == 30, "Control mastery should reduce activation stamina by 25%");
            float before = victim.getHealth(); victim.hurt(p.damageSources().playerAttack(p), 4);
            h.assertTrue(before - victim.getHealth() >= 4.9F, "Force mastery should increase outgoing attack damage by 25%");
            h.assertTrue(SkillCatalog.available("quake", SkillTreeService.build(p, id("quake"))) == 1, "Specializing in two branches leaves one point");
            h.succeed();
        } finally { victim.discard(); cleanup(p); }
    }
    @GameTest(template="rewind_empty", batch="skills.actions")
    public static void allThirtyOptionalSkillsHaveGameplayConsequences(GameTestHelper h) {
        for (String power : POWERS) {
            // These physical/stateful kits have dedicated travel, field and lifecycle tests.
            if (power.equals("flight") || power.equals("telekinesis")) continue;
            var p = player(h, power, 5);
            try {
                for (int branch = 0; branch < 3; branch++) {
                    var victim = target(h, p); p.setHealth(10); p.removeAllEffects(); p.setDeltaMovement(Vec3.ZERO);
                    if (power.equals("regeneration") && branch == 2) victim.setHealth(5);
                    LivingEntity rescued = null;
                    if (power.equals("superspeed") && branch == 2) {
                        rescued = EntityType.VILLAGER.create(h.getLevel()); rescued.setPos(p.position().add(.9, 0, 0)); h.getLevel().addFreshEntity(rescued);
                    }
                    float before = victim.getHealth();
                    boolean settingAnchor = power.equals("time_manipulation") && branch == 1;
                    if (settingAnchor) p.setOnGround(true);
                    if (power.equals("time_manipulation") && branch == 2) {
                        long now = p.getServer().overworld().getGameTime();
                        com.jayptucker.heroesevolved.time.SpaceTimeService.record(p, now - 100);
                        com.jayptucker.heroesevolved.time.SpaceTimeService.record(p, now);
                    }
                    var context = new AbilityUseContext(p, id(power), 5, settingAnchor);
                    h.assertTrue(SkillActionService.canUse(context, branch) && SkillActionService.activate(context, branch), "Optional skill must activate: " + power + "/" + branch);
                    if (branch == 0 && !power.equals("flight") && !power.equals("regeneration") && !power.equals("telepathy") && !power.equals("time_manipulation") && !power.equals("superspeed") && !power.equals("precognition"))
                        h.assertTrue(victim.getHealth() < before, "Aimed attack must damage its target: " + power);
                    if (branch == 0 && power.equals("telepathy")) h.assertTrue(victim.hasEffect(MobEffects.WEAKNESS), "Psychic Silence must weaken its target");
                    if (branch == 0 && power.equals("time_manipulation")) h.assertTrue(com.jayptucker.heroesevolved.time.SpaceTimeService.isSuspended(victim), "Selective Stasis must pause its target");
                    if (power.equals("regeneration") && branch < 2) h.assertTrue(p.getHealth() > 10, "Regeneration branch must actually heal");
                    if (power.equals("regeneration") && branch == 2) h.assertTrue(victim.getHealth() > before && victim.hasEffect(MobEffects.ABSORPTION), "Transfusion must heal and shield its recipient");
                    if (power.equals("cryokinesis") && branch != 1) h.assertTrue(CryoService.isFrozen(victim), "Optional cold attacks must freeze mobs");
                    if (power.equals("flight") && branch != 1) h.assertTrue(p.getDeltaMovement().lengthSqr() > .5, "Flight mobility must move the player");
                    if (branch == 1 && Set.of("cryokinesis", "radiation", "telepathy").contains(power)) h.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Defensive skill must grant Resistance: " + power);
                    if (branch == 2 && power.equals("electrokinesis")) h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "Overcharge must improve movement");
                    if (rescued != null) rescued.discard();
                    CryoService.releaseEntity(victim); victim.discard();
                }
            } finally { cleanup(p); }
        }
        h.succeed();
    }
    @GameTest(template="rewind_empty", batch="skills.projectiles")
    public static void defensiveScreensReflectIncomingProjectiles(GameTestHelper h) {
        var p = player(h, "sonic_resonance", 5);
        var shot = new Arrow(EntityType.ARROW, h.getLevel()); shot.setPos(p.position().add(0, 1, 4)); shot.setDeltaMovement(0, 0, -1);
        h.getLevel().addFreshEntity(shot);
        SkillActionService.activate(new AbilityUseContext(p, id("sonic_resonance"), 5, false), 2);
        h.assertTrue(shot.getDeltaMovement().z > 0 && shot.getOwner() == p, "Sonic screen must turn hostile shots outward and transfer attribution");
        shot.discard(); cleanup(p); h.succeed();
    }
    @GameTest(template="rewind_empty", batch="sonic.channel")
    public static void canaryScreamStopsOnReleaseAndIncapacitation(GameTestHelper h) {
        var p = player(h, "sonic_resonance", 5);
        try {
            var scream = ModAbilities.SONIC_RESONANCE.get().actions().get(AbilitySlot.QUINARY);
            var context = new AbilityUseContext(p, id("sonic_resonance"), 5, false);
            scream.activate(context); h.assertTrue(SonicService.screaming(p), "Canary Scream must create a held channel");
            scream.release(context); h.assertTrue(!SonicService.screaming(p), "Releasing must stop voice and channel immediately");
            scream.activate(context); CryoService.freezeSolid(p, 60, null); SonicService.tick(h.getLevel().getServer());
            h.assertTrue(!SonicService.screaming(p), "Incapacitated players must not continue screaming");
            CryoService.releaseEntity(p); h.succeed();
        } finally { cleanup(p); }
    }
    @GameTest(template="rewind_empty", batch="sonic.cover")
    public static void sonicCryRespectsCoverAndShattersGlassBeforeTheNextCry(GameTestHelper h) {
        var p = player(h, "sonic_resonance", 5); var victim = target(h, p);
        var cover = p.blockPosition().above().offset(0, 0, 3);
        try {
            float health = victim.getHealth();
            h.getLevel().setBlock(cover, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            SonicService.cry(p, 5, false);
            h.assertTrue(victim.getHealth() == health, "Screams must not damage targets through solid cover");
            h.getLevel().setBlock(cover, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
            SonicService.cry(p, 5, false);
            h.assertTrue(h.getLevel().getBlockState(cover).isAir(), "A voice attack must shatter unprotected glass");
            SonicService.cry(p, 5, false);
            h.assertTrue(victim.getHealth() < health, "Once cover shatters, the next scream must reach the target");
            h.succeed();
        } finally { victim.discard(); cleanup(p); }
    }
    @GameTest(template="rewind_empty", batch="sonic.vault")
    public static void sonicVaultRecoilCushionsExactlyOneLanding(GameTestHelper h) {
        var p = player(h, "sonic_resonance", 5);
        SonicService.vault(p);
        h.assertTrue(p.getDeltaMovement().z < -1 && p.getDeltaMovement().y > 0, "Voice propulsion must recoil opposite the player's aim");
        h.assertTrue(SonicService.cushion(p) && !SonicService.cushion(p), "Landing protection must be consumed once");
        SonicService.vault(p); SonicService.forget(p);
        h.assertTrue(!SonicService.cushion(p), "Logout and power removal must clear unspent protection");
        h.succeed(); cleanup(p);
    }
    @GameTest(template="rewind_empty", batch="skills.gravity")
    public static void gravityWellPullsMobsAndExpiresWithItsOwner(GameTestHelper h) {
        var p = player(h, "telekinesis", 5); var victim = target(h, p);
        victim.setPos(p.position().add(0, 0, 3));
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(id("telekinesis").toString(),
                new SkillTreeData.Build(5, Set.of("telekinesis_1_1"), Map.of(), 0)));
        // Anchor at an actual surface beside the caster, rather than implicitly at their feet.
        BlockPos anchor = p.blockPosition().below();
        var previous = h.getLevel().getBlockState(anchor);
        h.getLevel().setBlock(anchor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        p.setXRot(90);
        SkillActionService.activate(new AbilityUseContext(p, id("telekinesis"), 5, false), 1);
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(victim.getDeltaMovement().z < 0, "The gravity well must pull a mob toward its anchor over time");
                SkillActionService.forget(p);
        com.jayptucker.heroesevolved.expansion.PrecognitionService.forget(p); victim.setDeltaMovement(Vec3.ZERO);
                SkillActionService.tick(h.getLevel().getServer());
                h.assertTrue(victim.getDeltaMovement().lengthSqr() == 0, "Removed field must stop altering its targets");
                h.succeed();
            } finally { h.getLevel().setBlock(anchor, previous, 3); victim.discard(); cleanup(p); }
        });
    }
}
