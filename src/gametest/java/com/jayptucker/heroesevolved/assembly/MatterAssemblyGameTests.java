package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.*;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class MatterAssemblyGameTests {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("heroesevolved", path); }
    private static ServerPlayer player(GameTestHelper h, int material) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "matter-test")) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) { return true; }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            p.connection = new ServerGamePacketListenerImpl(p.server, connection, p, CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {}
            };
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(16, 6, 12))));
        p.setNoGravity(true); p.setYRot(0); p.setXRot(0); p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.MATTER_ASSEMBLY_ID).unlock(ModAbilities.MATTER_ASSEMBLY_ID));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.MATTER_ASSEMBLY_ID));
        PlayerProgressionService.setLevel(p, 5); PlayerEnergyService.restore(p, 999);
        for (int slot = 0; material > 0; slot++) { int amount = Math.min(64, material); p.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, amount)); material -= amount; }
        h.getLevel().addNewPlayer(p); return p;
    }
    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.MATTER_ASSEMBLY_ID, 5, false); }
    private static void cleanup(ServerPlayer p) { PowerRules.rewinding(p, false); MatterAssemblyService.forget(p); p.discard(); }
    private static Mob target(GameTestHelper h, ServerPlayer p, double z) {
        Mob mob = EntityType.PILLAGER.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired();
        mob.setPos(p.position().add(0, 0, z)); mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100); h.getLevel().addFreshEntity(mob); return mob;
    }
    @GameTest(template="cryo_rework_arena", batch="matter.no_material")
    public static void anEmptyInventoryStillBuildsAndPaysInStamina(GameTestHelper h) {
        ServerPlayer p = player(h, 0);
        try {
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS, "Matter comes from the ground: no blocks needed");
            h.assertTrue(PlayerEnergyService.getEnergy(p) < energy && CooldownService.isOnCooldown(p, id("matter_assembly_barricade")), "It is paid for in stamina and cooldown");
            h.assertTrue(!MatterAssemblyService.cells(p).isEmpty(), "The shield is real"); h.succeed();
        } finally { cleanup(p); }
    }
    @GameTest(template="cryo_rework_arena", batch="matter.material")
    public static void assemblyNeverTouchesTheInventory(GameTestHelper h) {
        ServerPlayer p = player(h, 0);
        try {
            p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_BLOCK, 64));
            p.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            ItemStack named = new ItemStack(Items.STONE, 64); named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("keep me"));
            p.getInventory().setItem(2, named);
            h.assertTrue(MatterAssemblyService.activate(context(p), "shard_lance") && MatterAssemblyService.activate(context(p), "barricade"), "Shards and shapes are drawn from the world");
            h.assertTrue(p.getInventory().getItem(0).getCount() == 64 && p.getInventory().getItem(1).getCount() == 64 && named.getCount() == 64,
                    "Not one block is taken from the inventory"); h.succeed();
        } finally { cleanup(p); }
    }
    @GameTest(template="cryo_rework_arena", batch="matter.cover")
    public static void wallGrowsWithCollisionFromSixCellsAndNeverReturnsLoot(GameTestHelper h) {
        ServerPlayer p = player(h, 40);
        h.assertTrue(MatterAssemblyService.activate(context(p), "barricade"), "A wall starts");
        h.assertTrue(MatterAssemblyService.cells(p).size() == 4 && MatterAssemblyService.pendingCells(p) > 0, "The initial batch is bounded");
        h.runAfterDelay(4, () -> {
            try {
                var cells = MatterAssemblyService.cells(p); h.assertTrue(cells.size() == 6 && AssemblyMaterials.count(p) == 40, "A lean six-cell shield, and no input blocks spent");
                BlockPos cell = cells.getFirst(); h.assertTrue(!h.getLevel().getBlockState(cell).getCollisionShape(h.getLevel(), cell).isEmpty(), "The wall has real collision");
                int drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(cell).inflate(3)).size();
                h.getLevel().destroyBlock(cell, true, p);
                h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(cell).inflate(3)).size() == drops, "Mining a construct has no loot table");
                MatterAssemblyService.dismantle(p);
                h.assertTrue(cells.stream().allMatch(pos -> h.getLevel().getBlockState(pos).isAir()) && AssemblyMaterials.count(p) == 40, "Dismantling restores air and makes no items"); h.succeed();
            } finally { cleanup(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="matter.protection")
    public static void cancelledPlaceHooksRefundOnlyReservationAndLeaveNoLease(GameTestHelper h) {
        ServerPlayer p = player(h, 10); BlockPos pos = p.blockPosition().offset(3, 0, 0);
        Consumer<BlockEvent.EntityPlaceEvent> deny = event -> { if (event.getPos().equals(pos)) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(deny);
        try {
            h.assertTrue(!AssemblyTerrain.get(h.getLevel()).place(p, pos, 100), "Claims can veto a construction placement");
            h.assertTrue(h.getLevel().getBlockState(pos).isAir() && AssemblyMaterials.count(p) == 10 && !AssemblyTerrain.get(h.getLevel()).valid(h.getLevel(), pos), "Cancelled placement leaves no cell or lease, and the inventory untouched"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(deny); cleanup(p); }
    }
    @GameTest(template="cryo_rework_arena", batch="matter.adventure")
    public static void buildRestrictionsAndExistingContainersAreNeverBypassed(GameTestHelper h) {
        ServerPlayer p = player(h, 20); BlockPos pos = p.blockPosition().offset(3, 0, 0);
        try {
            p.setGameMode(GameType.ADVENTURE);
            h.assertTrue(!MatterAssemblyService.activate(context(p), "barricade") && MatterAssemblyService.pendingCells(p) == 0 && AssemblyMaterials.count(p) == 20, "Adventure mode cannot queue a later unprotected wall");
            p.setGameMode(GameType.SURVIVAL); h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            h.assertTrue(!AssemblyTerrain.get(h.getLevel()).place(p, pos, 20) && h.getLevel().getBlockEntity(pos) != null && AssemblyMaterials.count(p) == 20, "Containers and all non-air terrain remain untouched"); h.succeed();
        } finally { cleanup(p); }
    }
    @GameTest(template="cryo_rework_arena", batch="matter.recovery")
    public static void leasesRoundTripExpireAndRewindResurrectedOrphansSelfDelete(GameTestHelper h) {
        ServerPlayer p = player(h, 5); BlockPos pos = p.blockPosition().offset(3, 0, 0); AssemblyTerrain data = AssemblyTerrain.get(h.getLevel());
        h.assertTrue(data.place(p, pos, 2), "A short lease places");
        AssemblyTerrain loaded = AssemblyTerrain.load(data.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(loaded.owned(h.getLevel(), pos, p.getUUID()) && loaded.count(p.getUUID()) == 1, "Expiry and owner survive serialization");
        h.runAfterDelay(4, () -> {
            h.assertTrue(h.getLevel().getBlockState(pos).isAir(), "Expired state restores air");
            h.getLevel().setBlock(pos, AssemblyBlocks.MATTER.get().defaultBlockState(), 3);
        });
        h.runAfterDelay(18, () -> {
            try { h.assertTrue(h.getLevel().getBlockState(pos).isAir() && AssemblyMaterials.count(p) == 5, "A rewound construct without a live lease self-deletes without resource output"); h.succeed(); }
            finally { cleanup(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="matter.player_edit")
    public static void laterPlayerEditsAreNotErasedByExpiryOrCleanup(GameTestHelper h) {
        ServerPlayer p = player(h, 4); BlockPos pos = p.blockPosition().offset(3, 0, 0);
        try {
            h.assertTrue(AssemblyTerrain.get(h.getLevel()).place(p, pos, 1), "Cell places");
            h.getLevel().setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3); MatterAssemblyService.forget(p);
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.DIAMOND_BLOCK), "Cleanup never overwrites another block state"); h.succeed();
        } finally { cleanup(p); }
    }
    @GameTest(template="cryo_rework_arena", batch="matter.optional")
    public static void purchasedEquippedSwitchbackSpendsEnergyAndBuildsABentPhysicalRoute(GameTestHelper h) {
        ServerPlayer p = player(h, 128); Vec3 start = p.position();
        h.assertTrue(SkillTreeService.purchase(p, "matter_assembly_load_bearing") && SkillTreeService.purchase(p, "matter_assembly_switchback"), "Prerequisite and optional active purchase normally");
        h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, "matter_assembly_switchback"), "Learned route equips from inventory");
        int energy = PlayerEnergyService.getEnergy(p);
        h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS, "The equipped route is playable");
        h.assertTrue(PlayerEnergyService.getEnergy(p) == energy - 20 && CooldownService.isOnCooldown(p, id("matter_assembly_switchback")), "Optional action has a real independent cost and cooldown");
        h.runAfterDelay(5, () -> {
            try {
                var cells = MatterAssemblyService.cells(p);
                h.assertTrue(cells.stream().anyMatch(pos -> Math.abs(pos.getX() - start.x) > 3) && cells.stream().anyMatch(pos -> pos.getZ() > start.z + 3), "The route bends through a right angle");
                h.assertTrue(AssemblyMaterials.count(p) == 128, "The route spends no blocks from the inventory"); h.succeed();
            } finally { cleanup(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="matter.long_shard", timeoutTicks=80)
    public static void paidShardTravelsFortyBlocksAndHitsARealEnemy(GameTestHelper h) {
        ServerPlayer p = player(h, 9); Mob enemy = target(h, p, 40);
        h.runAfterDelay(2, () -> {
            h.assertTrue(MatterAssemblyService.activate(context(p), "shard_lance") && AssemblyMaterials.count(p) == 9, "The lance tears its Shard from the ground, not the inventory");
            h.assertTrue(enemy.getHealth() == 100, "Ranged damage is not instantaneous");
        });
        h.runAfterDelay(24, () -> {
            try { h.assertTrue(enemy.getHealth() < 100 && MatterAssemblyService.shardCount(p) == 0, "Swept shard collision reaches forty blocks and terminates at impact"); h.succeed(); }
            finally { enemy.discard(); cleanup(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="matter.shard_cover")
    public static void hostileCoverStopsShardsAndAlliesAreNotHit(GameTestHelper h) {
        ServerPlayer p = player(h, 9); Mob enemy = target(h, p, 10), friend = target(h, p, 5);
        var board = p.getScoreboard(); var team = board.addPlayerTeam("matter-" + UUID.randomUUID().toString().substring(0, 8));
        board.addPlayerToTeam(p.getScoreboardName(), team); board.addPlayerToTeam(friend.getScoreboardName(), team);
        BlockPos barrier = BlockPos.containing(p.getEyePosition().add(0, 0, 8)); h.getLevel().setBlock(barrier, Blocks.STONE.defaultBlockState(), 3);
        h.runAfterDelay(2, () -> h.assertTrue(MatterAssemblyService.activate(context(p), "shard_lance"), "Lance launches toward cover"));
        h.runAfterDelay(10, () -> {
            try { h.assertTrue(friend.getHealth() == 100 && enemy.getHealth() == 100, "Friends are not colliders and solid cover stops hostile damage"); h.succeed(); }
            finally { board.removePlayerTeam(team); friend.discard(); enemy.discard(); cleanup(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="matter.runtime")
    public static void losingAuthorizationStopsQueuedConstructionAndInFlightShards(GameTestHelper h) {
        ServerPlayer p = player(h, 128);
        h.assertTrue(MatterAssemblyService.activate(context(p), "causeway") && MatterAssemblyService.activate(context(p), "shard_lance"), "Both paid jobs begin");
        int material = AssemblyMaterials.count(p); p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty());
        h.runAfterDelay(3, () -> {
            try { h.assertTrue(MatterAssemblyService.pendingCells(p) == 0 && MatterAssemblyService.shardCount(p) == 0 && AssemblyMaterials.count(p) == material, "Revoked power cannot finish building or keep attacking"); h.succeed(); }
            finally { cleanup(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="matter.service_gates")
    public static void directServiceCannotBypassOptionalPurchasesCoreTiersOrRewindLock(GameTestHelper h) {
        ServerPlayer p = player(h, 64);
        try {
            h.assertTrue(!MatterAssemblyService.activate(context(p), "switchback") && MatterAssemblyService.cells(p).isEmpty(), "Unlearned optional mechanics cannot bypass loadout validation through direct calls");
            h.assertTrue(!MatterAssemblyService.activate(new AbilityUseContext(p, ModAbilities.MATTER_ASSEMBLY_ID, 1, false), "cathedral"), "Low-tier core context cannot access Cathedral");
            PowerRules.rewinding(p, true);
            h.assertTrue(!MatterAssemblyService.activate(context(p), "shard_lance") && AssemblyMaterials.count(p) == 64, "Rewind immobilization blocks projectiles before material payment"); h.succeed();
        } finally { cleanup(p); }
    }
    @GameTest(template="cryo_rework_arena", batch="matter.pvp")
    public static void serverPvpDisabledPreventsCagingAndShardDamage(GameTestHelper h) {
        ServerPlayer p = player(h, 32), other = player(h, 0); other.setPos(p.position().add(0, 0, 10));
        boolean previous = p.server.isPvpAllowed(); p.server.setPvpAllowed(false);
        h.runAfterDelay(2, () -> {
            h.assertTrue(!MatterAssemblyService.activate(context(p), "cage"), "Disabled server PvP excludes player cage targeting");
            h.assertTrue(MatterAssemblyService.activate(context(p), "shard_lance"), "A projectile may still launch at world targets");
        });
        h.runAfterDelay(10, () -> {
            try { h.assertTrue(other.getHealth() == other.getMaxHealth(), "A disabled-PvP player is never damaged by the swept Shard"); h.succeed(); }
            finally { p.server.setPvpAllowed(previous); cleanup(other); cleanup(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="matter.bank_shot")
    public static void learnedBankShotUsesOwnedWallAsARealReflectionSurface(GameTestHelper h) {
        ServerPlayer p = player(h, 32); Mob enemy = target(h, p, -5);
        h.assertTrue(SkillTreeService.purchase(p, "matter_assembly_bank_shot"), "Bank Shot is learned from the tree");
        BlockPos mirror = BlockPos.containing(p.getEyePosition().add(0, 0, 6));
        h.assertTrue(AssemblyTerrain.get(h.getLevel()).place(p, mirror, 200), "A paid wall facet is available as the bank surface");
        h.runAfterDelay(2, () -> h.assertTrue(MatterAssemblyService.activate(context(p), "shard_lance"), "The lance is deliberately fired away from its target"));
        h.runAfterDelay(14, () -> {
            try { h.assertTrue(enemy.getHealth() < 100 && h.getLevel().getBlockState(mirror).is(AssemblyBlocks.MATTER.get()), "An intact owned facet reflects the live projectile into the enemy behind the caster"); h.succeed(); }
            finally { enemy.discard(); cleanup(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="matter.drawbridge")
    public static void learnedDrawbridgeCanRetractOnlyItsPaidCrossing(GameTestHelper h) {
        ServerPlayer p = player(h, 64);
        h.assertTrue(SkillTreeService.purchase(p, "matter_assembly_load_bearing") && SkillTreeService.purchase(p, "matter_assembly_drawbridge"), "Drawbridge alternative branch purchase follows its explicit prerequisite");
        h.assertTrue(MatterAssemblyService.activate(context(p), "drawbridge"), "A twenty-block crossing begins");
        h.runAfterDelay(3, () -> {
            try {
                var cells = MatterAssemblyService.cells(p); h.assertTrue(cells.size() == 20 && AssemblyMaterials.count(p) == 64, "Crossing has twenty cells, drawn from the ground");
                h.assertTrue(MatterAssemblyService.activate(context(p), "drawbridge"), "Second activation retracts its own crossing");
                h.assertTrue(cells.stream().allMatch(pos -> h.getLevel().getBlockState(pos).isAir()) && AssemblyMaterials.count(p) == 64, "Retraction produces no permanent items"); h.succeed();
            } finally { cleanup(p); }
        });
    }
    private static void learnFoundation(GameTestHelper h, ServerPlayer p) {
        for (String skill : List.of("threshold", "shelter", "clean_exit", "cradle", "pressure_valve", "second_foundation"))
            h.assertTrue(SkillTreeService.purchase(p, "matter_assembly_" + skill), "Refuge capstone prerequisite is purchased: " + skill);
    }
    @GameTest(template="cryo_rework_arena", batch="matter.armored_foundation")
    public static void armorSurvivingAnOtherwiseFatalRawHitDoesNotSpendEmergencyMaterials(GameTestHelper h) {
        ServerPlayer p = player(h, 64); Mob enemy = target(h, p, 4); learnFoundation(h, p);
        p.getAttribute(Attributes.ARMOR).setBaseValue(20); p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(12); p.setHealth(10);
        h.runAfterDelay(2, () -> {
            try {
                h.assertTrue(p.hurt(p.damageSources().mobAttack(enemy), 12), "Real armored damage is applied");
                h.assertTrue(p.getHealth() > 1 && p.getHealth() < 10, "Armor makes the raw twelve-point hit survivable");
                h.assertTrue(AssemblyMaterials.count(p) == 64 && MatterAssemblyService.cells(p).isEmpty(), "A survivable post-armor hit spends no capstone materials or cooldown"); h.succeed();
            } finally { enemy.discard(); cleanup(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="matter.fatal_foundation")
    public static void actuallyFatalDamageTriggersAPaidFoundationOnce(GameTestHelper h) {
        ServerPlayer p = player(h, 64); Mob enemy = target(h, p, 4); learnFoundation(h, p); p.setHealth(6);
        h.runAfterDelay(2, () -> {
            try {
                h.assertTrue(p.hurt(p.damageSources().mobAttack(enemy), 12), "A genuinely fatal hit is processed");
                h.assertTrue(p.isAlive() && Math.abs(p.getHealth() - 1) < .001 && MatterAssemblyService.cells(p).size() >= 4, "Emergency geometry leaves exactly one health");
                h.assertTrue(AssemblyMaterials.count(p) == 64, "The rescue spends no blocks from the inventory");
                p.invulnerableTime = 0; p.setHealth(6); p.hurt(p.damageSources().mobAttack(enemy), 13);
                h.assertTrue(p.isDeadOrDying(), "The one-minute capstone cooldown prevents a second rescue: the next fatal hit lands"); h.succeed();
            } finally { enemy.discard(); cleanup(p); }
        });
    }

    @GameTest(template="cryo_rework_arena", batch="matter.skywalk", timeoutTicks=40)
    public static void skywalkCatchesAStepOnAir(GameTestHelper h) {
        ServerPlayer p = player(h, 0);
        try {
            // High over the arena, nothing underfoot.
            p.setPos(p.position().add(0, 12, 0));
            BlockPos below = BlockPos.containing(p.getX(), p.getBoundingBox().minY - 0.2, p.getZ());
            h.assertTrue(h.getLevel().getBlockState(below).isAir(), "There is air underfoot");
            h.assertTrue(MatterAssemblyService.activate(context(p), "causeway"), "Skywalk starts");
            h.assertTrue(h.getLevel().getBlockState(below).is(AssemblyBlocks.MATTER.get()), "Matter catches the step at once");
            h.assertTrue(MatterAssemblyService.cells(p).size() == 3, "A three-wide plate across the path, nothing more: " + MatterAssemblyService.cells(p).size());
            h.assertTrue(!MatterAssemblyService.activate(context(p), "causeway"), "One Skywalk at a time"); h.succeed();
        } finally { cleanup(p); }
    }
    @GameTest(template="cryo_rework_arena", batch="matter.sentinels", timeoutTicks=80)
    public static void sentinelEngineRaisesThreeTurretsThatShootEnemies(GameTestHelper h) {
        ServerPlayer p = player(h, 0); Mob enemy = target(h, p, 10);
        h.runAfterDelay(2, () -> h.assertTrue(MatterAssemblyService.activate(context(p), "cathedral"), "Sentinel Engine starts"));
        h.runAfterDelay(50, () -> {
            try {
                h.assertTrue(MatterAssemblyService.cells(p).size() == 6, "Three two-cell turrets, not a fortress: " + MatterAssemblyService.cells(p).size());
                h.assertTrue(enemy.getHealth() < 100, "The turrets found the enemy and shot it"); h.succeed();
            } finally { enemy.discard(); cleanup(p); }
        });
    }
}
