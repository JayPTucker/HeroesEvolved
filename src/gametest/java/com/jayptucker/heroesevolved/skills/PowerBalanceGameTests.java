package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.registry.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PowerBalanceGameTests {
    @GameTest(template="rewind_empty", batch="balance.hud")
    @SuppressWarnings("unchecked")
    public static void actualActivationAndHudUseTheSameAdjustedCost(GameTestHelper h) {
        var p = player(h);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.SONIC_RESONANCE_ID).unlock(ModAbilities.SONIC_RESONANCE_ID));
        com.jayptucker.heroesevolved.progression.PlayerProgressionService.setLevel(p, 5);
        int before = com.jayptucker.heroesevolved.energy.PlayerEnergyService.getEnergy(p);
        var result = com.jayptucker.heroesevolved.ability.service.AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false);
        h.assertTrue(result == AbilityUseResult.SUCCESS && before - com.jayptucker.heroesevolved.energy.PlayerEnergyService.getEnergy(p) == 16, "Resonant Note must charge adjusted stamina");
        try {
            var method = com.jayptucker.heroesevolved.network.PlayerPowerSyncService.class.getDeclaredMethod("toSnapshots", ServerPlayer.class, AbilitySlot.class, AbilityAction.class);
            method.setAccessible(true);
            var snapshots = (java.util.stream.Stream<com.jayptucker.heroesevolved.network.AbilityActionSnapshot>) method.invoke(null, p, AbilitySlot.SECONDARY, ModAbilities.SONIC_RESONANCE.get().actions().get(AbilitySlot.SECONDARY));
            var shown = snapshots.findFirst().orElseThrow();
            h.assertTrue(shown.energyCost() == 16 && shown.cooldownTicks() == 96, "HUD must show the same adjusted cost and cooldown");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "balance-test")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        try { var grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); grace.setAccessible(true); grace.setInt(p, 0); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); return p;
    }
    @GameTest(template="rewind_empty", batch="balance.catalog")
    public static void tuningCoversEveryPowerAndOnlyRealActions(GameTestHelper h) {
        var powers = new HashSet<String>();
        for (var entry : PowerBalance.entries().entrySet()) {
            String name = entry.getKey(); var tuning = entry.getValue(); powers.add(tuning.power());
            var ability = AbilityRegistry.ABILITIES.get(ResourceLocation.fromNamespaceAndPath("heroesevolved", tuning.power()));
            h.assertTrue(ability != null && ability.actions().values().stream().anyMatch(a -> a.definition().id().getPath().equals(name)), "Invalid balance action " + name);
        }
        h.assertTrue(powers.size() == AbilityRegistry.ABILITIES.size(), "Every power must participate in the balance review");
        var stop = ResourceLocation.fromNamespaceAndPath("heroesevolved", "time_stop");
        h.assertTrue(PowerBalance.energy(stop, 150) == 90 && PowerBalance.cooldown(stop, 3600) == 1260, "Time tuning must apply to configured values");
        h.assertTrue(PowerBalance.energy(stop, 0) == 0 && PowerBalance.cooldown(stop, 0) == 0, "Free actions stay free"); h.succeed();
    }
    @GameTest(template="rewind_empty", batch="balance.freeze")
    public static void thawedPlayersCannotBeImmediatelyRefrozen(GameTestHelper h) {
        var p = player(h);
        try {
            h.assertTrue(CryoService.freezeSolid(p, 300, null), "First freeze should work");
            CryoService.releaseEntity(p);
            h.assertTrue(!CryoService.freezeSolid(p, 300, null) && !CryoService.isFrozen(p), "A thawed player needs a recovery window"); h.succeed();
        } finally { CryoService.releaseEntity(p); }
    }
    @GameTest(template="rewind_empty", batch="balance.stored_damage")
    public static void stoppedTimeCannotAccumulateAnUnboundedPlayerKill(GameTestHelper h) {
        var owner = player(h); var target = player(h); target.setPos(owner.position().add(0, 0, 4));
        owner.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.setHealth(100);
        try {
            TimeStopService.start(owner, 5);
            for (int i = 0; i < 30; i++) h.assertTrue(TimeStopService.storeImpact(target, owner.damageSources().playerAttack(owner), 10), "Hit should be stored");
            TimeStopService.stop(owner);
            h.assertTrue(target.getHealth() >= 88 && target.getHealth() < 100, "Release must respect the 12-point PvP cap"); h.succeed();
        } finally { TimeStopService.stop(owner); }
    }
}
