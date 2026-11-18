package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Exercise the same target-id input used when the client replaces a vanilla left click with a style. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class CombatTargetGameTests {
    private static final List<ResourceLocation> POWERS = List.of(ModAbilities.PYROKINESIS_ID,
            ModAbilities.HYDROKINESIS_ID, ModAbilities.SUPERSPEED_ID, ModAbilities.QUAKE_ID,
            ModAbilities.CRYOKINESIS_ID, ModAbilities.PHASING_ID, ModAbilities.REGENERATION_ID,
            ModAbilities.INVISIBILITY_ID, ModAbilities.TELEKINESIS_ID, ModAbilities.VIBE_ID);

    private static ServerPlayer fighter(GameTestHelper h, ResourceLocation power) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "target-test"));
        try {
            var channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (p.connection.getConnection().channel() == null)
                channel.set(p.connection.getConnection(), new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, 2);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static <T extends Mob> T target(GameTestHelper h, ServerPlayer p, EntityType<T> type) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.moveTo(p.getX(), p.getY(), p.getZ() + 2, 180, 0);
        h.getLevel().addFreshEntity(mob);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, mob.getBoundingBox().getCenter());
        return mob;
    }

    private static void punch(GameTestHelper h, ServerPlayer p, Mob target) {
        float health = target.getHealth();
        double stamina = PlayerEnergyService.getStamina(p);
        FightingStyles.strikeInput(p, target.getId());
        h.assertTrue(target.getHealth() < health, FightingStyles.of(p).power() + ": a direct click must hurt " + target.getType());
        h.assertTrue(PlayerEnergyService.getStamina(p) == stamina, "A plain punch spends no style stamina");
        target.setHealth(.1F);
        target.invulnerableTime = 0;
        FightingStyles.strikeInput(p, target.getId());
        h.assertTrue(!target.isAlive(), "The same input must be able to kill the mob");
    }

    private static void villagers(GameTestHelper h, EntityType<? extends Mob> type) {
        for (ResourceLocation power : POWERS) {
            ServerPlayer p = fighter(h, power);
            Mob mob = target(h, p, type);
            try { punch(h, p, mob); }
            finally { FightingStyles.forget(p); mob.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "combat_targets.villagers")
    public static void aDirectClickCanHurtAndKillVillagersWithEveryStyle(GameTestHelper h) {
        villagers(h, EntityType.VILLAGER);
    }

    @GameTest(template = "rewind_empty", batch = "combat_targets.traders")
    public static void aDirectClickCanHurtAndKillWanderingTraders(GameTestHelper h) {
        villagers(h, EntityType.WANDERING_TRADER);
    }

    @GameTest(template = "rewind_empty", batch = "combat_targets.pets")
    public static void aDirectClickStillPunchesAnOwnedPet(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.QUAKE_ID);
        var wolf = target(h, p, EntityType.WOLF);
        wolf.setOwnerUUID(p.getUUID());
        try { punch(h, p, wolf); h.succeed(); }
        finally { FightingStyles.forget(p); wolf.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "combat_targets.fire_immune")
    public static void aFirebenderCanPunchAFireImmuneMob(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.PYROKINESIS_ID);
        var blaze = target(h, p, EntityType.BLAZE);
        try { punch(h, p, blaze); h.succeed(); }
        finally { FightingStyles.forget(p); blaze.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "combat_targets.fire_resistance")
    public static void aFirebenderCanPunchAMobWithFireResistance(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.PYROKINESIS_ID);
        var zombie = target(h, p, EntityType.ZOMBIE);
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        try { punch(h, p, zombie); h.succeed(); }
        finally { FightingStyles.forget(p); zombie.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "combat_targets.reach")
    public static void thePunchFallbackStillRequiresMeleeReach(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.QUAKE_ID);
        var villager = target(h, p, EntityType.VILLAGER);
        villager.setPos(p.position().add(0, 0, 12));
        try {
            float health = villager.getHealth();
            double stamina = PlayerEnergyService.getStamina(p);
            FightingStyles.strikeInput(p, villager.getId());
            h.assertTrue(villager.getHealth() == health, "An excluded target outside melee reach is not hit");
            h.assertTrue(PlayerEnergyService.getStamina(p) == stamina, "An invalid punch does not spend stamina");
            h.succeed();
        } finally { FightingStyles.forget(p); villager.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "combat_targets.combo")
    public static void eligibleTargetsStillUseTheComboAndItsRecovery(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.QUAKE_ID);
        var pillager = target(h, p, EntityType.PILLAGER);
        var villager = target(h, p, EntityType.VILLAGER);
        villager.setPos(p.position().add(.8, 0, 2));
        try {
            float health = pillager.getHealth(), villagerHealth = villager.getHealth();
            double stamina = PlayerEnergyService.getStamina(p);
            FightingStyles.strikeInput(p, pillager.getId());
            h.assertTrue(pillager.getHealth() < health, "An eligible target takes the powered strike");
            h.assertTrue(villager.getHealth() == villagerHealth, "An area strike still spares nearby villagers");
            h.assertTrue(PlayerEnergyService.getStamina(p) < stamina, "The powered strike still costs stamina");
            health = pillager.getHealth();
            stamina = PlayerEnergyService.getStamina(p);
            pillager.invulnerableTime = 0;
            FightingStyles.strikeInput(p, pillager.getId());
            h.assertTrue(pillager.getHealth() == health, "Combo recovery does not turn into an extra vanilla punch");
            h.assertTrue(PlayerEnergyService.getStamina(p) == stamina, "A click during recovery pays nothing extra");
            h.succeed();
        } finally { FightingStyles.forget(p); pillager.discard(); villager.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "combat_targets.boats")
    public static void aFighterCanBreakABoat(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.QUAKE_ID);
        p.setGameMode(GameType.CREATIVE);
        var boat = EntityType.BOAT.create(h.getLevel());
        boat.moveTo(p.getX(), p.getY(), p.getZ() + 2, 0, 0);
        h.getLevel().addFreshEntity(boat);
        try {
            FightingStyles.strikeInput(p, boat.getId());
            h.assertTrue(boat.isRemoved(), "Creative left click still breaks a boat while a fighting style is active");
            h.succeed();
        } finally { FightingStyles.forget(p); boat.discard(); }
    }
}
