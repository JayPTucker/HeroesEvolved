package com.jayptucker.heroesevolved.eclipse;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.EclipseCalendar;
import com.jayptucker.heroesevolved.data.EclipseTimeline;
import com.jayptucker.heroesevolved.data.EclipseTimeline.Phase;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.events.AwakeningService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class EclipseGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "eclipse-test")) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) { absMoveTo(x, y, z, yaw, pitch); }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 6))));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static Zombie attacker(GameTestHelper helper, ServerPlayer player) {
        // A husk: it does not burn in the test structure's daylight.
        Zombie zombie = EntityType.HUSK.create(helper.getLevel());
        zombie.setNoAi(true);
        zombie.moveTo(player.getX() + 2, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(zombie);
        return zombie;
    }

    private static void remove(ServerPlayer player) {
        PowerOwnershipTestSupport.release(player);
        PowerIntroService.forget(player);
        AwakeningService.forget(player);
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "rewind_empty", batch = "eclipse.calendar")
    public static void eclipseFallsEveryTenthDayAtANewMoon(GameTestHelper helper) {
        int previous = EclipseCalendar.intervalDays();
        EclipseCalendar.setIntervalDays(10);
        try {
            helper.assertTrue(EclipseCalendar.isEclipseDay(9) && EclipseCalendar.isEclipseDay(19)
                    && !EclipseCalendar.isEclipseDay(0) && !EclipseCalendar.isEclipseDay(10), "Eclipses fall on the tenth day of each cycle");
            helper.assertTrue(EclipseCalendar.nextEclipseDay(0) == 9 && EclipseCalendar.nextEclipseDay(9) == 9
                    && EclipseCalendar.nextEclipseDay(10) == 19, "The next Eclipse day is found from any day");
            long eclipseNoon = 9 * EclipseCalendar.TICKS_PER_DAY + EclipseCalendar.PEAK_TIME_OF_DAY;
            helper.assertTrue(EclipseCalendar.moonPhase(eclipseNoon) == EclipseCalendar.NEW_MOON, "Eclipse day is a new moon");
            helper.assertTrue(EclipseCalendar.moonPhase(14 * EclipseCalendar.TICKS_PER_DAY) == 0, "The moon is full halfway between Eclipses");
            helper.assertTrue(EclipseCalendar.moonPhase(10 * EclipseCalendar.TICKS_PER_DAY) == 5, "The moon waxes after an Eclipse");
            helper.assertTrue(helper.getLevel().dimensionType().moonPhase(eclipseNoon) == EclipseCalendar.NEW_MOON,
                    "The Overworld's real moon follows the Eclipse calendar");
            helper.assertTrue(EclipseCalendar.firstContactTimeOfDay(3600, 4200) == 300, "Mid-totality lands at noon");
            helper.succeed();
        } finally { EclipseCalendar.setIntervalDays(previous); }
    }

    @GameTest(template = "rewind_empty", batch = "eclipse.timeline")
    public static void moonApproachesCoversTheSunThenLeaves(GameTestHelper helper) {
        int approach = HeroesEvolvedConfig.COMMON.eclipseApproachTicks.get();
        int totality = HeroesEvolvedConfig.COMMON.eclipseTotalityTicks.get();
        helper.assertTrue(totality >= 20 * 60 * 3 && totality <= 20 * 60 * 4, "Totality lasts three to four minutes");
        EclipseTimeline timeline = new EclipseTimeline(1000, approach, totality, 3600);
        helper.assertTrue(timeline.phaseAt(999) == Phase.NONE && timeline.phaseAt(1000) == Phase.APPROACH, "First contact starts the Eclipse");
        helper.assertTrue(timeline.coverage(1000) < 0.01F && timeline.coverage(1000 + approach / 2.0) > 0.4F
                && timeline.coverage(1000 + approach / 2.0) < 0.6F, "The moon slides steadily onto the sun");
        helper.assertTrue(timeline.darkness(1000 + approach / 2.0) < 0.1F, "Half-covered, daylight barely changes");
        helper.assertTrue(timeline.phaseAt(timeline.totalityStart()) == Phase.TOTALITY
                && timeline.coverage(timeline.totalityStart()) == 1, "Totality covers the sun completely");
        helper.assertTrue(timeline.darkness(timeline.totalityStart() + totality / 2.0) == 1, "The shadow is deepest at the peak");
        helper.assertTrue(timeline.diamondRing(timeline.totalityStart() + 1) > 0.9F
                && timeline.diamondRing(timeline.totalityStart() + totality / 2.0) == 0, "The diamond ring only flares at the edges of totality");
        helper.assertTrue(timeline.phaseAt(timeline.totalityEnd()) == Phase.DEPARTURE
                && timeline.phaseAt(timeline.endGameTime()) == Phase.NONE, "The moon slides off and the Eclipse ends");
        helper.assertTrue(timeline.moonTravel(1000) < -0.99F && timeline.moonTravel(timeline.endGameTime() - 1) > 0.99F,
                "The moon crosses from one side of the sun to the other");
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "eclipse.totality")
    public static void onlyTotalitySuppressesAndHandsOutPowers(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        double chance = HeroesEvolvedConfig.COMMON.eclipseDormantAbilityChance.get();
        HeroesEvolvedConfig.COMMON.eclipseDormantAbilityChance.set(1.0D);
        try {
            helper.assertTrue(EclipseService.start(player.getServer()), "An Eclipse can be started");
            helper.assertTrue(EclipseService.phase(player.getServer()) == Phase.APPROACH, "A started Eclipse begins at first contact");
            helper.assertTrue(!EclipseService.arePowersSuppressed(player), "The partial phase leaves powers alone");
            helper.assertTrue(!EclipseService.tryAssignDormantAbility(player), "Nobody is chosen before totality");
            helper.assertTrue(EclipseService.beginTotality(player.getServer()), "Totality can be reached");
            helper.assertTrue(EclipseService.arePowersSuppressed(player), "Totality suppresses powers");
            helper.assertTrue(EclipseService.tryAssignDormantAbility(player), "Totality chooses a powerless player");
            var assignment = PlayerAbilityService.getData(player).assignedPower();
            helper.assertTrue(assignment.isPresent() && !assignment.get().getValue().isUnlocked(), "The chosen power waits, dormant");
            helper.assertTrue(player.getData(ModDataAttachments.PLAYER_LAST_ECLIPSE_ROLL.get())
                    == com.jayptucker.heroesevolved.data.EclipseSavedData.get(player.getServer().overworld()).eclipseId(),
                    "The roll is recorded against this Eclipse");
            helper.assertTrue(!EclipseService.beginTotality(player.getServer()), "Totality is not restarted while it runs");
            helper.succeed();
        } finally {
            HeroesEvolvedConfig.COMMON.eclipseDormantAbilityChance.set(chance);
            EclipseService.end(player.getServer());
            remove(player);
        }
    }

    @GameTest(template = "rewind_empty", batch = "eclipse.first_power")
    public static void firstEclipsePicksSomeoneOnlineAndGivesThemSpaceTime(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CompoundTag owners = PowerOwnershipSavedData.get(server).save(new CompoundTag(), server.registryAccess());
        List<ServerPlayer> online = List.of(player(helper), player(helper), player(helper));
        ServerPlayer offline = player(helper);
        double chance = HeroesEvolvedConfig.COMMON.eclipseDormantAbilityChance.get();
        Set<ServerPlayer> chosen = new HashSet<>();
        try {
            // With the usual roll switched off, only the guaranteed first power can appear.
            HeroesEvolvedConfig.COMMON.eclipseDormantAbilityChance.set(0.0D);
            helper.assertTrue(EclipseService.beginTotality(server), "The first Eclipse reaches totality");

            for (int world = 0; world < 12; world++) {
                // A fresh world: nobody has ever had a power.
                PowerOwnershipTestSupport.replace(server, new PowerOwnershipSavedData());
                for (ServerPlayer player : online) forgetPowers(player);
                forgetPowers(offline);
                helper.assertTrue(EclipseService.firstPowerPending(server), "A world without powers is waiting for its first");

                EclipseService.handOutPowers(server, online);

                List<ServerPlayer> spaceTime = online.stream().filter(player -> PlayerAbilityService.getData(player)
                        .assignedPower().filter(power -> power.getKey().equals(ModAbilities.TIME_MANIPULATION_ID)).isPresent()).toList();
                helper.assertTrue(spaceTime.size() == 1, "Exactly one player online receives Space-Time Manipulation");
                helper.assertTrue(!PlayerAbilityService.getData(spaceTime.getFirst()).assignedPower().orElseThrow().getValue().isUnlocked(),
                        "Space-Time waits dormant, like every Eclipse power");
                helper.assertTrue(online.stream().filter(player -> player != spaceTime.getFirst())
                        .noneMatch(player -> PlayerAbilityService.getData(player).hasAssignedPower()), "Everyone else rolls as usual");
                helper.assertTrue(!PlayerAbilityService.getData(offline).hasAssignedPower(), "Nobody offline is chosen");
                helper.assertTrue(!EclipseService.firstPowerPending(server), "Only the first power is guaranteed");
                chosen.add(spaceTime.getFirst());
            }
            helper.assertTrue(chosen.size() > 1, "The first power goes to a random player, not the first in line");

            // A later Eclipse hands out powers at random, and Space-Time keeps its one owner.
            HeroesEvolvedConfig.COMMON.eclipseDormantAbilityChance.set(1.0D);
            EclipseService.end(server);
            helper.assertTrue(EclipseService.beginTotality(server), "A second Eclipse reaches totality");
            EclipseService.handOutPowers(server, online);
            Set<ResourceLocation> powers = new HashSet<>();
            for (ServerPlayer player : online) {
                var power = PlayerAbilityService.getData(player).assignedPower();
                helper.assertTrue(power.isPresent(), "A later Eclipse still hands out powers");
                helper.assertTrue(powers.add(power.get().getKey()), "Every power still has one owner");
            }
            helper.assertTrue(powers.contains(ModAbilities.TIME_MANIPULATION_ID), "Space-Time stays with its first owner");
            helper.succeed();
        } finally {
            HeroesEvolvedConfig.COMMON.eclipseDormantAbilityChance.set(chance);
            EclipseService.end(server);
            for (ServerPlayer player : online) remove(player);
            remove(offline);
            PowerOwnershipTestSupport.replace(server, PowerOwnershipSavedData.load(owners, server.registryAccess()));
        }
    }

    private static void forgetPowers(ServerPlayer player) {
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty());
        player.setData(ModDataAttachments.PLAYER_LAST_ECLIPSE_ROLL.get(), -1L);
    }

    @GameTest(template = "rewind_empty", batch = "eclipse.adrenaline_calm", timeoutTicks = 60)
    public static void lowHealthAloneDoesNotAwakenAPower(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(player, ModAbilities.REGENERATION_ID), "Dormant power assigned");
            player.setHealth(2);
            AwakeningService.tick(player);
            helper.assertTrue(AwakeningService.hasDormantPower(player), "Low health outside a fight leaves the power asleep");
            helper.assertTrue(AwakeningService.adrenaline(player) == 0, "No fight, no adrenaline");
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty", batch = "eclipse.adrenaline_fight", timeoutTicks = 160)
    public static void aFightAtLowHealthAwakensThePowerThatSavesYou(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Zombie zombie = attacker(helper, player);
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(player, ModAbilities.REGENERATION_ID), "Dormant power assigned");
            AwakeningService.recordCombat(player, player.damageSources().mobAttack(zombie));
            helper.assertTrue(AwakeningService.inCombat(player), "Being hit by a monster is a fight");
            player.setHealth(10);
            AwakeningService.tick(player);
            helper.assertTrue(AwakeningService.hasDormantPower(player), "A fight above the threshold is not enough");
            player.setHealth(3);
            helper.assertTrue(AwakeningService.adrenaline(player) == 1, "Adrenaline peaks below the threshold");
            AwakeningService.tick(player);
            helper.assertTrue(!AwakeningService.hasDormantPower(player), "Low health in a fight awakens the power");
            helper.assertTrue(PowerIntroService.isActive(player), "The awakening plays its intro");
        } catch (RuntimeException failure) { zombie.discard(); remove(player); throw failure; }
        helper.runAfterDelay(110, () -> {
            try {
                helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Regeneration's release saves its owner");
                helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Every release grants a second wind");
                helper.succeed();
            } finally { zombie.discard(); remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "eclipse.adrenaline_lethal", timeoutTicks = 60)
    public static void aKillingBlowInAFightWakesThePowerInstead(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Zombie zombie = attacker(helper, player);
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(player, ModAbilities.FLIGHT_ID), "Dormant power assigned");
            player.setHealth(6);
            DamageSource blow = player.damageSources().mobAttack(zombie);
            float landed = AwakeningService.interceptDamage(player, blow, 20);
            helper.assertTrue(landed < 6 && player.getHealth() - landed >= 1, "The blow can hurt but cannot kill");
            helper.assertTrue(!AwakeningService.hasDormantPower(player), "The blow wakes the power first");
            helper.assertTrue(AwakeningService.inGrace(player) && PowerIntroService.isActive(player), "The awakened owner is shielded");
            helper.assertTrue(AwakeningService.interceptDamage(player, blow, 2) == 2, "Only a dormant power intercepts");
            helper.succeed();
        } finally { zombie.discard(); remove(player); }
    }

    @GameTest(template = "rewind_empty", batch = "eclipse.adrenaline_totality", timeoutTicks = 60)
    public static void nothingAwakensUnderTheBlackSun(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Zombie zombie = attacker(helper, player);
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(player, ModAbilities.REGENERATION_ID), "Dormant power assigned");
            EclipseService.beginTotality(player.getServer());
            AwakeningService.recordCombat(player, player.damageSources().mobAttack(zombie));
            player.setHealth(2);
            helper.assertTrue(!AwakeningService.tryAwakenFromAdrenaline(player), "Totality silences awakenings too");
            helper.assertTrue(AwakeningService.interceptDamage(player, player.damageSources().mobAttack(zombie), 5) == 5,
                    "Totality offers no rescue");
            helper.assertTrue(AwakeningService.hasDormantPower(player), "The power still waits");
            helper.succeed();
        } finally { EclipseService.end(player.getServer()); zombie.discard(); remove(player); }
    }

    @GameTest(template = "rewind_empty", batch = "eclipse.precog_intro", timeoutTicks = 160)
    public static void precognitionHasAnAnimatedIntroThatStopsTheFight(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Zombie zombie = attacker(helper, player);
        PlayerAbilityService.grantAbility(player, ModAbilities.PRECOGNITION_ID);
        helper.assertTrue(PowerIntroService.isActive(player), "Precognition plays a full intro");
        helper.runAfterDelay(110, () -> {
            try {
                helper.assertTrue(!PowerIntroService.isActive(player), "The intro finishes");
                helper.assertTrue(player.getData(ModDataAttachments.POWER_INTRO_HISTORY).seen().contains(ModAbilities.PRECOGNITION_ID),
                        "The intro is recorded");
                helper.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && zombie.hasEffect(MobEffects.WEAKNESS),
                        "The release leaves attackers stumbling");
                helper.succeed();
            } finally { zombie.discard(); remove(player); }
        });
    }
}
