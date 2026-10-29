package com.jayptucker.heroesevolved.expansion;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class UnwrittenGameTests {
    private static ServerPlayer player(GameTestHelper h, String power) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "unwritten-test")) {
            @Override public boolean isInvulnerableTo(DamageSource s) { return false; }
            // FakePlayer skips vanilla death hooks; exercise the same hook ServerPlayer uses.
            @Override public void die(DamageSource source) { net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, source); }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                setServerLevel(level); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        var id = ResourceLocation.fromNamespaceAndPath("heroesevolved", power);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id).unlock(id));
        PlayerProgressionService.setLevel(p, 5);
        try { var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); field.setAccessible(true); field.setInt(p, 0); }
        catch (Exception ex) { throw new AssertionError(ex); }
        return p;
    }
    private static LivingEntity enemy(GameTestHelper h, Vec3 pos) {
        var t = EntityType.PILLAGER.create(h.getLevel()); t.setNoAi(true); t.setPos(pos);
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); t.setHealth(100); h.getLevel().addFreshEntity(t); return t;
    }
    private static AbilityUseContext context(ServerPlayer p, String power) { return new AbilityUseContext(p, ResourceLocation.fromNamespaceAndPath("heroesevolved", power), 5, false); }
    private static void clear(ServerPlayer p) { ExpansionSkills.forget(p); PrecognitionService.forget(p); com.jayptucker.heroesevolved.cryo.CryoService.releaseEntity(p); }
    @GameTest(template="rewind_empty", batch="unwritten.migration")
    public static void legacySavesRetainPurchasesAndNewTrainingDefaultsToZero(GameTestHelper h) {
        var json = JsonParser.parseString("{\"heroesevolved:quake\":{\"highest_level\":5,\"learned\":[\"quake_0_0\",\"quake_0_1\"],\"slots\":{\"PRIMARY\":\"quake_0_1\"}}}");
        var data = SkillTreeData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(); var b = data.build("heroesevolved:quake");
        h.assertTrue(b.training() == 0 && b.highestLevel() == 15 && b.earned() == com.jayptucker.heroesevolved.progression.PowerLevels.points(15)
                && SkillCatalog.spent("quake", b) == 3, "A legacy Level 5 is Power Level 15: purchases kept, the new points added");
        h.assertTrue(SkillCatalog.find("quake", b.slots().get("PRIMARY")).orElseThrow().active(), "Legacy equipped ID still resolves");
        h.assertTrue(SkillCatalog.nodes("quake").size() == 18 && SkillCatalog.rows("quake") == 6, "Expanded tree has two pages and stable old IDs"); h.succeed();
    }
    @GameTest(template="rewind_empty", batch="unwritten.capstones")
    public static void namedPrerequisitesCapstonesAndRespecializationAreEnforced(GameTestHelper h) {
        var p = player(h, "quake"); var power = ResourceLocation.fromNamespaceAndPath("heroesevolved", "quake");
        try {
            h.assertTrue(!SkillTreeService.purchase(p, "quake_fault_script"), "Named action needs its explicit parent");
            for (String id : List.of("quake_0_0", "quake_fault_script", "quake_short_fuse", "quake_undertow")) h.assertTrue(SkillTreeService.purchase(p, id), "Advanced path purchase " + id);
            SkillTreeService.awardTraining(p); SkillTreeService.awardTraining(p); SkillTreeService.awardTraining(p);
            for (String id : List.of("quake_1_0", "quake_counterforce", "quake_counter_balance")) h.assertTrue(SkillTreeService.purchase(p, id), "Second branch purchase");
            h.assertTrue(!SkillTreeService.purchase(p, "quake_stolen_thunder"), "Only one capstone can be learned");
            int earned = SkillTreeService.build(p, power).earned();
            h.assertTrue(SkillTreeService.respecialize(p) && SkillTreeService.build(p, power).earned() == earned && SkillTreeService.build(p, power).learned().isEmpty(), "Respecialization refunds purchases without minting points");
            p.setData(ModDataAttachments.PLAYER_COMBAT, new com.jayptucker.heroesevolved.combat.PlayerCombatData(p.level().getGameTime()));
            h.assertTrue(!SkillTreeService.respecialize(p), "Cannot respecialize immediately after damage"); h.succeed();
        } finally { clear(p); }
    }
    @GameTest(template="rewind_empty", batch="unwritten.circuit")
    public static void circuitsFollowMaterialsAndHaveAFixedWorkBound(GameTestHelper h) {
        BlockPos start = h.absolutePos(new BlockPos(1, 2, 1));
        for (int i = 0; i < 6; i++) h.getLevel().setBlock(start.east(i), Blocks.COPPER_BLOCK.defaultBlockState(), 2);
        h.getLevel().setBlock(start.east(7), Blocks.COPPER_BLOCK.defaultBlockState(), 2);
        h.assertTrue(ExpansionSkills.conductorNetwork(h.getLevel(), start).size() == 6, "Ordinary circuit cannot cross an air gap");
        h.assertTrue(ExpansionSkills.conductorNetwork(h.getLevel(), start.below()).isEmpty(), "Non-conductive ground is not a circuit");
        for (int x = 0; x < 20; x++) for (int z = 0; z < 10; z++) h.getLevel().setBlock(start.offset(x, 0, z), Blocks.COPPER_BLOCK.defaultBlockState(), 2);
        h.assertTrue(ExpansionSkills.conductorNetwork(h.getLevel(), start).size() == 48, "A large connected network stops at its 48-conductor budget"); h.succeed();
    }
    @GameTest(template="rewind_empty", batch="unwritten.reactions")
    public static void frozenReactionsConsumeTheSetupAndCannotRepeat(GameTestHelper h) {
        var p = player(h, "quake"); var first = enemy(h, p.position().add(0, 0, 3)); var second = enemy(h, first.position().add(0, 0, 2));
        try {
            com.jayptucker.heroesevolved.cryo.CryoService.freezeSolid(first, 100, null);
            h.assertTrue(PowerReactions.quake(p, first) && second.getHealth() < 100, "Brittle fan damages a real enemy behind its frozen origin");
            h.assertTrue(!PowerReactions.quake(p, first), "Consuming the frozen setup prevents recursive or repeated reactions");
            var third = enemy(h, p.position().add(2, 0, 3));
            try { com.jayptucker.heroesevolved.cryo.CryoService.freezeSolid(third, 100, null); second.invulnerableTime = 0; float before = second.getHealth();
                h.assertTrue(PowerReactions.electric(p, third) && second.getHealth() < before, "Frozen conductors release a bounded electrical arc");
            } finally { third.discard(); }
            h.succeed();
        } finally { first.discard(); second.discard(); clear(p); }
    }
    @GameTest(template="rewind_empty", batch="unwritten.fault", timeoutTicks=75)
    public static void faultHasARealDelayAndDamagesAnEnemyOnce(GameTestHelper h) {
        var p = player(h, "quake"); p.setYRot(0); var t = enemy(h, p.position().add(0, -1, 4));
        h.assertTrue(ExpansionSkills.activate(context(p, "quake"), "fault_script"), "Fault should find nearby supported ground");
        h.runAfterDelay(20, () -> h.assertTrue(t.getHealth() == 100, "Fault is visibly armed before damage"));
        h.runAfterDelay(60, () -> { try { h.assertTrue(t.getHealth() == 94, "One wave hits the same enemy once"); h.succeed(); } finally { t.discard(); clear(p); } });
    }
    @GameTest(template="rewind_empty", batch="unwritten.cocoon", timeoutTicks=100)
    public static void preservationRestrictsActionsReducesDamageAndReleasesNormally(GameTestHelper h) {
        var p = player(h, "cryokinesis"); p.setShiftKeyDown(true); p.setHealth(10);
        h.assertTrue(ExpansionSkills.activate(context(p, "cryokinesis"), "stillheart"), "Self preservation should activate even though the caster resists ordinary freezing");
        h.assertTrue(!PowerRules.canAct(p) && ExpansionSkills.preserved(p), "Preserved players cannot use abilities");
        p.hurt(p.damageSources().generic(), 10);
        h.assertTrue(p.getHealth() >= 7.9F && p.getHealth() < 10, "Cocoon reduces rather than cancels ordinary damage");
        h.runAfterDelay(70, () -> { try { h.assertTrue(!ExpansionSkills.preserved(p) && PowerRules.canAct(p) && p.getHealth() >= 9.9, "Cocoon opens, heals and restores actions"); h.succeed(); } finally { clear(p); } });
    }
    @GameTest(template="rewind_empty", batch="unwritten.painting")
    public static void paintingIsALockedShareableMapAndReflectsTheIncident(GameTestHelper h) {
        var p = player(h, "precognition");
        var stack = PropheticPainting.canvas(p); var map = net.minecraft.world.item.MapItem.getSavedData(stack, p.getServer().overworld());
        h.assertTrue(PropheticPainting.isCanvas(stack) && map != null && map.locked && map.colors.length == 16384, "Canvas is a real locked map with persistent pixel data");
        byte[] before = PropheticPainting.pixels(FacilityData.Phase.PLANNED, false, 0), after = PropheticPainting.pixels(FacilityData.Phase.SAVED, true, 3);
        h.assertTrue(!Arrays.equals(before, after) && PropheticPainting.isCanvas(stack.copy()), "Saved future changes the painting and copied canvases preserve identity"); h.succeed();
    }
    @GameTest(template="rewind_empty", batch="unwritten.incident_save")
    public static void incidentPersistenceKeepsReturnTicketsAndIdempotentDiscoveries(GameTestHelper h) {
        var d = new FacilityData(); UUID id = UUID.randomUUID();
        d.visitors.put(id, new FacilityData.ReturnPoint(ResourceLocation.parse("minecraft:overworld"), 12, 70, -40, 15, 20)); d.phase = FacilityData.Phase.ACTIVE; d.elapsed = 400; d.gate = true;
        h.assertTrue(d.discover(id, "rime_circuit") && !d.discover(id, "rime_circuit"), "A discovery can be rewarded once only");
        var copy = FacilityData.load(d.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(copy.visitors.equals(d.visitors) && copy.elapsed == 400 && copy.phase == d.phase && copy.gate && copy.discovered(id, "rime_circuit"), "Restart preserves mission and safe return information");
        copy.reset(); h.assertTrue(copy.visitors.containsKey(id), "An offline visitor retains their return ticket across a replay"); h.succeed();
    }
    @GameTest(template="rewind_empty", batch="unwritten.facility", timeoutTicks=180)
    public static void facilityBuildsInBatchesAndManualRouteCanSaveBothCaptives(GameTestHelper h) {
        var p = player(h, "precognition"); var d = FacilityData.get(p.getServer());
        d.visitors.clear(); d.reset();
        FacilityService.tick(p.getServer());
        h.assertTrue(d.cursor == 256 && d.phase == FacilityData.Phase.BUILDING, "Construction respects its fixed per-tick budget");
        h.runAfterDelay(35, () -> {
            h.assertTrue(d.phase == FacilityData.Phase.READY, "Bounded construction completes");
            FacilityService.plan(p); Vec3 origin = p.position();
            h.assertTrue(FacilityService.enter(p) && FacilityService.atSite(p), "Discovered painting grants entry and a return ticket");
            var site = p.serverLevel();
            for (int x = 0; x <= 1; x++) for (int z = 0; z <= 2; z++) site.setChunkForced(x, z, true);
            if (!site.players().contains(p)) site.players().add(p);
            FacilityService.tick(p.getServer());
            p.setPos(10.5, 65, 7.5); FacilityService.interact(p, new BlockPos(10, 65, 7));
            p.setPos(18.5, 65, 7.5); FacilityService.interact(p, new BlockPos(18, 65, 7));
            p.setPos(11.5, 65, 15.5); FacilityService.interact(p, FacilityService.CORE.west(3));
            h.assertTrue(d.gate && d.stable, "Manual controls open the partition and cool the relay without a specialist ability");
            h.succeedWhen(() -> {
            h.assertTrue(d.first != null && d.second != null, "Wait for loaded captive chunks");
            for (UUID id : new UUID[]{d.first, d.second}) { var captive = p.serverLevel().getEntity(id); h.assertTrue(captive != null, "Real captive spawned: first=" + d.first + ", second=" + d.second + ", present=" + site.players().size() + ", elapsed=" + d.elapsed + ", phase=" + d.phase); captive.setPos(14.5, 65, 2.5); }
            d.elapsed = 19; FacilityService.tick(p.getServer());
            h.assertTrue(d.phase == FacilityData.Phase.SAVED && d.rescued == 3, "Escorting both captives yields a saved outcome");
            h.assertTrue(FacilityService.leave(p) && p.position().distanceToSqr(origin) < .01, "Return uses the saved original coordinates");
            site.players().remove(p);
            for (int x = 0; x <= 1; x++) for (int z = 0; z <= 2; z++) site.setChunkForced(x, z, false);
            clear(p);
            });
        });
    }
    @GameTest(template="rewind_empty", batch="unwritten.sever")
    public static void severedIntentCancelsOneCommittedHitButNotTheNext(GameTestHelper h) {
        var p = player(h, "precognition"); var t = enemy(h, p.position().add(0, 0, 3)); p.setYRot(0); p.setXRot(0);
        try {
            h.assertTrue(PrecognitionService.activate(context(p, "precognition"), 2), "Visible enemy intention is marked");
            float before = p.getHealth(); p.hurt(p.damageSources().mobAttack(t), 4);
            h.assertTrue(p.getHealth() == before, "Marked attack is crossed out");
            p.invulnerableTime = 0; p.hurt(p.damageSources().mobAttack(t), 4);
            h.assertTrue(p.getHealth() < before, "The consumed intention cannot cancel a second attack"); h.succeed();
        } finally { t.discard(); clear(p); }
    }
    @GameTest(template="rewind_empty", batch="unwritten.fate")
    public static void fateThreadCanSaveItsOwnCasterAndCannotImmediatelyRepeat(GameTestHelper h) {
        var p = player(h, "precognition"); p.setShiftKeyDown(true); p.setHealth(3);
        try {
            h.assertTrue(PrecognitionService.activate(context(p, "precognition"), 3), "Self Fate Thread activates");
            p.hurt(p.damageSources().generic(), 100);
            h.assertTrue(p.isAlive() && p.getHealth() == 2, "A lethal hit crosses out the caster's own death and leaves one heart");
            h.assertTrue(!PrecognitionService.activate(context(p, "precognition"), 3), "The rescued player has a thirty-second repeat guard");
            h.succeed();
        } finally { clear(p); }
    }
    @GameTest(template="rewind_empty", batch="unwritten.convergence", timeoutTicks=30)
    public static void convergenceBendsHostileProjectilesAndSparesFriendlyShots(GameTestHelper h) {
        var p = player(h, "precognition"); p.setYRot(0); p.setXRot(0);
        var hostile = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, h.getLevel());
        var friendly = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, h.getLevel());
        hostile.setPos(p.position().add(1, 1, 5)); hostile.setDeltaMovement(-.5, 0, 0);
        friendly.setPos(p.position().add(-1, 1, 5)); friendly.setOwner(p); friendly.setDeltaMovement(-.5, 0, 0);
        h.getLevel().addFreshEntity(hostile); h.getLevel().addFreshEntity(friendly);
        h.assertTrue(PrecognitionService.activate(context(p, "precognition"), 4), "Convergence establishes a predicted impact point");
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(hostile.isRemoved() || hostile.getDeltaMovement().z > 0, "Hostile arrow is redirected toward the foretold point or consumed there");
                h.assertTrue(!friendly.isRemoved() && friendly.getOwner() == p && friendly.getDeltaMovement().x < 0, "A friendly arrow retains its direction and ownership");
                h.succeed();
            } finally { hostile.discard(); friendly.discard(); clear(p); }
        });
    }
}
