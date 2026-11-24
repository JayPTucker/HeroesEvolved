package com.jayptucker.heroesevolved.bugs;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cryo.CryoRideService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.shapeshift.*;
import com.jayptucker.heroesevolved.story.sylar.SylarProjectiles;
import com.jayptucker.heroesevolved.time.*;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Player paths behind the November Trello reports; no shipped test helpers or fixtures. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TrelloRegressionGameTests {
    private static ServerPlayer player(GameTestHelper h, ResourceLocation power) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "trello-test"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(16, 2, 16))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        PlayerProgressionService.setPowerLevel(p, 20);
        PlayerEnergyService.restore(p, 999);
        return p;
    }
    private static void clean(ServerPlayer p) {
        CryoRideService.forget(p); ShapeshiftService.forget(p);
        com.jayptucker.heroesevolved.speed.SpeedService.forget(p);
        PlayerEnergyService.forget(p);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty());
    }
    @GameTest(template="cryo_rework_arena", batch="trello.ride", timeoutTicks=160)
    public static void glacierRideStaysHeldBeyondFourSecondsAndReleaseEndsIt(GameTestHelper h) {
        var p = player(h, ModAbilities.CRYOKINESIS_ID);
        p.setNoGravity(false); p.setOnGround(true);
        try {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false) == AbilityUseResult.SUCCESS,
                    "The actual Glacier Ride key starts the ribbon");
        } catch (RuntimeException e) { clean(p); throw e; }
        h.runAfterDelay(105, () -> {
            try {
                h.assertTrue(CryoRideService.isRiding(p), "A paid hold lasts beyond the reported four-second cutoff");
                h.assertTrue(PlayerEnergyService.getStamina(p) < PlayerEnergyService.getMaximumEnergy(p), "The hold pays upkeep");
                AbilityUseService.releaseAssignedAction(p, AbilitySlot.TERTIARY);
                h.assertTrue(!CryoRideService.isRiding(p) && !p.isNoGravity(), "Releasing the real key ends skating and restores gravity");
                h.succeed();
            } finally { clean(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="trello.cooldown")
    public static void exposureAndFormInstinctHaveTheirRealHudCountdowns(GameTestHelper h) {
        var p = player(h, ModAbilities.SHAPESHIFTING_ID);
        try {
            h.assertTrue(ShapeshiftService.morph(p, EntityType.SKELETON), "Take a ranged form");
            h.assertTrue(ShapeshiftInstinct.use(p), "Fire its actual Instinct");
            var c = new AbilityUseContext(p, ModAbilities.SHAPESHIFTING_ID, 5, false);
            var actions = new com.jayptucker.heroesevolved.ability.types.ShapeshiftingAbility().actions();
            h.assertTrue(actions.get(AbilitySlot.SECONDARY).recovery(c, 20).until() > p.level().getGameTime(),
                    "Instinct's internal per-form cooldown reaches the bound HUD action");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE), "Take a form");
            ShapeshiftSuspicion.raise(p, 100);
            var morph = actions.get(AbilitySlot.PRIMARY);
            h.assertTrue(morph.recovery(c, 20).until() - p.level().getGameTime() == ShapeshiftSuspicion.EXPOSED_LOCK,
                    "The HUD receives the full exposure lock instead of an already finished one-second cooldown");
            h.succeed();
        } finally { clean(p); }
    }
    @GameTest(template="cryo_rework_arena", batch="trello.mirage", timeoutTicks=100)
    public static void miragesAreMovingTargetableCopiesAndBurstsDoNotHurtAnyone(GameTestHelper h) {
        var p = player(h, ModAbilities.SUPERSPEED_ID);
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        zombie.setNoAi(true); zombie.setPos(p.position().add(0, 0, 4)); h.getLevel().addFreshEntity(zombie);
        zombie.setTarget(p);
        h.assertTrue(AdvancedTechniques.activate(new AbilityUseContext(p, ModAbilities.SUPERSPEED_ID, 5, false), "mirage"), "Mirage casts");
        h.runAfterDelay(20, () -> {
            try {
                h.assertTrue(zombie.getTarget() instanceof ShapeDecoy, "An enemy chases a real decoy");
                ShapeDecoy copy = (ShapeDecoy) zombie.getTarget();
                h.assertTrue(copy.speedster() && copy.form().isOf(p) && copy.distanceToSqr(p) > 1, "It runs away wearing the caster's face");
                float health = zombie.getHealth();
                copy.hurt(h.getLevel().damageSources().mobAttack(zombie), 3);
                h.assertTrue(copy.isRemoved() && zombie.getHealth() == health, "A strike dissolves the decoy without damaging its attacker");
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty());
                h.succeed();
            } finally { zombie.discard(); clean(p); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="trello.sylar", timeoutTicks=100)
    public static void sylarCatchesFastTaczRoundsThenReturnsTheSameRound(GameTestHelper h) {
        var shooter = player(h, ModAbilities.TELEKINESIS_ID);
        var boss = ModEntities.SYLAR.get().create(h.getLevel());
        boss.setPos(shooter.position().add(0, 0, 8)); boss.actor(true); h.getLevel().addFreshEntity(boss);
        Projectile shot = (Projectile) PowerWeapons.testBullet(h.getLevel(), 0);
        shot.setPos(shooter.getEyePosition()); shot.setOwner(shooter); shot.setDeltaMovement(0, 0, 20);
        try {
            h.assertTrue(SylarProjectiles.intercept(h.getLevel(), shot, shot.position(), shot.position().add(0,0,20)),
                    "A round crossing his whole catch volume in one tick is caught");
            h.assertTrue(shot.getDeltaMovement().lengthSqr() == 0, "The actual round stops visibly before him");
        } catch (RuntimeException e) { shot.discard(); boss.discard(); clean(shooter); throw e; }
        h.runAfterDelay(SylarProjectiles.HOLD_TICKS + 1, () -> {
            try {
                SylarProjectiles.tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(shot));
                h.assertTrue(shot.getOwner() == boss && shot.getDeltaMovement().z < 0, "After the warning it flies back as Sylar's round");
                h.succeed();
            } finally { shot.discard(); boss.discard(); clean(shooter); }
        });
    }
    @GameTest(template="cryo_rework_arena", batch="trello.revenge")
    public static void avengingAKillersPastReturnsDeathLootOnceAndInvalidatesOriginals(GameTestHelper h) {
        var p = player(h, ModAbilities.TIME_MANIPULATION_ID);
        var killer = EntityType.ZOMBIE.create(h.getLevel());
        killer.setPos(p.position().add(0,0,2));
        var snapshot = new PlayerTemporalSnapshotData(true, true, p.level().dimension().location(),
                p.blockPosition().getX()-16, p.blockPosition().getZ()-16, 0, 0, List.of(),
                List.of(new TemporalEntityEchoData(killer.getUUID(), new net.minecraft.nbt.CompoundTag(), new int[0])));
        var data = TemporalRevenge.get(p.getServer());
        try {
            data.recorded(p, snapshot);
            var drop = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.DIAMOND, 3));
            TemporalRevenge.drops(new LivingDropsEvent(p, h.getLevel().damageSources().mobAttack(killer), List.of(drop), true));
            ItemStack pickedUp = drop.getItem().copy();
            p.getInventory().setItem(0, pickedUp);
            data.entered(p.getUUID());
            data.defeated(p.getServer(), p.getUUID(), killer.getUUID());
            data.deliver(p);
            h.assertTrue(pickedUp.isEmpty(), "The original loot is invalid, even after being picked up");
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 3, "The remembered death loot returns to its owner");
            data.defeated(p.getServer(), p.getUUID(), killer.getUUID()); data.deliver(p);
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 3, "A second kill cannot pay the same death again");
            h.succeed();
        } finally { p.getInventory().clearContent(); killer.discard(); clean(p); }
    }
}
