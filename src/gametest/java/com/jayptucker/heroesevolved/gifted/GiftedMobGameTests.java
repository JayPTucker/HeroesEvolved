package com.jayptucker.heroesevolved.gifted;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gifted mobs: every power has moves a mob can use, a villager's gift turns them good or evil, a renegade's siege waits
 * its turn between victims, raid champions, mini-boss rules (heavy blows break casts, unbound at half health, only
 * players really hurt them), Regeneration's second life and the prize.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class GiftedMobGameTests {
    private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double z) {
        T mob = type.create(helper.getLevel());
        if (mob == null) throw new AssertionError("No " + type);
        mob.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 2, 0))).add(x, 0, z));
        mob.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        if (mob instanceof Villager v) GiftedVillagers.give(v, null);
        mob.setNoAi(true);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static ServerPlayer player(GameTestHelper helper, Vec3 at) {
        ServerPlayer p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "gifted-test"));
        p.setPos(at);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static void cleanup(Entity... entities) {
        for (Entity e : entities) {
            if (e == null) continue;
            Renegades.cancel(e);
            if (e instanceof Mob m && GiftedMobs.isGifted(m)) GiftedMobs.cleanse(m);
            e.discard();
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "gifted.moves")
    public static void everyPowerHasMovesAMobCanUse(GameTestHelper helper) {
        for (ResourceLocation power : GiftedMobs.powers()) {
            if (power.equals(ModAbilities.MIMICRY_ID)) {
                helper.assertTrue(MobMoves.of(power).isEmpty(), "A mimic borrows; it has no moves of its own");
                continue;
            }
            List<MobMoves.Move> moves = MobMoves.of(power);
            long signatures = moves.stream().filter(MobMoves.Move::signature).count();
            helper.assertTrue(moves.size() - signatures >= 2, power + " has two ordinary moves");
            helper.assertTrue(signatures == 1, power + " has one signature");
            helper.assertTrue(MobMoves.known(power, Rarity.UNCOMMON, false).size() == 1, "An Uncommon mob knows one move");
            helper.assertTrue(MobMoves.known(power, Rarity.RARE, false).size() == 2, "A Rare mob knows two");
            helper.assertTrue(MobMoves.known(power, Rarity.EPIC, false).stream().noneMatch(MobMoves.Move::signature),
                    "An Epic mob holds its signature back until unbound");
            helper.assertTrue(MobMoves.known(power, Rarity.EPIC, true).stream().anyMatch(MobMoves.Move::signature), "and uses it once unbound");
            helper.assertTrue(MobMoves.known(power, Rarity.MYTHIC, false).stream().anyMatch(MobMoves.Move::signature), "A Mythic mob always has it");
        }
        helper.assertTrue(GiftedMobs.powers().size() >= 20, "Every power can be given to a mob: " + GiftedMobs.powers().size());
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "gifted.everymove")
    public static void everyMoveLandsWithoutBreaking(GameTestHelper helper) {
        Pillager caster = mob(helper, EntityType.PILLAGER, 3, 6);
        List<Entity> made = new ArrayList<>(List.of(caster));
        try {
            List<String> failed = new ArrayList<>();
            int hurt = 0;
            for (MobMoves.Move move : MobMoves.ALL) {
                IronGolem target = mob(helper, EntityType.IRON_GOLEM, 8, 6);
                made.add(target);
                GiftedMob g = GiftedMobs.bestow(caster, move.power(), Rarity.MYTHIC, true, Gift.Origin.ADMIN, null, "Test Caster");
                caster.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 2, 0))).add(3, 0, 6));
                float before = target.getHealth();
                try {
                    g.land(move, target);
                } catch (RuntimeException e) {
                    failed.add(move.id() + ": " + e);
                }
                if (target.getHealth() < before) hurt++;
                target.discard();
            }
            helper.assertTrue(failed.isEmpty(), "Every move lands without throwing: " + failed);
            helper.assertTrue(hurt >= MobMoves.ALL.size() / 2, "Most moves hurt their target at once: " + hurt + " of " + MobMoves.ALL.size());
        } catch (RuntimeException e) { cleanup(made.toArray(Entity[]::new)); throw e; }
        // Delayed steps (strikes, slams) run on later ticks; let them, then tidy up.
        helper.runAfterDelay(60, () -> { cleanup(made.toArray(Entity[]::new)); helper.succeed(); });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "gifted.sides")
    public static void guardiansFightMonstersAndRenegadesFightPeople(GameTestHelper helper) {
        Villager guardian = mob(helper, EntityType.VILLAGER, 2, 2);
        Pillager raider = mob(helper, EntityType.PILLAGER, 9, 9);
        Zombie zombie = mob(helper, EntityType.ZOMBIE, 5, 2);
        Villager neighbour = mob(helper, EntityType.VILLAGER, 2, 5);
        IronGolem golem = mob(helper, EntityType.IRON_GOLEM, 9, 3);
        ServerPlayer player = player(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 6))));
        try {
            GiftedMob good = GiftedMobs.bestow(guardian, ModAbilities.PYROKINESIS_ID, Rarity.RARE, false, Gift.Origin.GUARDIAN, null, null);
            GiftedMob evil = GiftedMobs.bestow(raider, ModAbilities.CRYOKINESIS_ID, Rarity.RARE, true, Gift.Origin.PATROL, null, null);
            helper.assertTrue(MobMoves.foe(good, zombie) && MobMoves.foe(good, evil.mob()), "A guardian fights monsters and evil gifts");
            helper.assertTrue(!MobMoves.foe(good, player) && !MobMoves.foe(good, neighbour) && !MobMoves.foe(good, golem),
                    "but never people, villagers or golems");
            helper.assertTrue(MobMoves.foe(evil, player) && MobMoves.foe(evil, neighbour) && MobMoves.foe(evil, golem) && MobMoves.foe(evil, guardian),
                    "An evil gift fights players, villagers, golems and guardians");
            helper.assertTrue(!MobMoves.foe(evil, zombie), "but not its own kind");
            helper.assertTrue(guardian.hasCustomName() && !guardian.isCustomNameVisible() && raider.isCustomNameVisible(),
                    "Both are named; only the hostile one wears it overhead");
            helper.assertTrue(raider.getMaxHealth() > 24, "A gifted raider is tougher than its kind: " + raider.getMaxHealth());
            helper.succeed();
        } finally { cleanup(guardian, raider, zombie, neighbour, golem); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "gifted.turn")
    public static void anEvilGiftTurnsAVillagerIntoAnIllagerWhoKeepsIt(GameTestHelper helper) {
        Villager villager = mob(helper, EntityType.VILLAGER, 4, 4);
        Villager neighbour = mob(helper, EntityType.VILLAGER, 6, 4);
        GiftedVillagers.give(villager, ModAbilities.PYROKINESIS_ID);
        Mob renegade = null;
        try {
            renegade = Renegades.awaken(villager, true);
            helper.assertTrue(renegade instanceof Raider, "They become an illager: " + renegade);
            helper.assertTrue(villager.isRemoved(), "The villager is gone");
            Gift gift = Gift.read(renegade);
            helper.assertTrue(gift != null && gift.evil() && gift.power().equals(ModAbilities.PYROKINESIS_ID) && gift.origin() == Gift.Origin.RENEGADE,
                    "They keep their power, turned: " + gift);
            helper.assertTrue(gift.rarity().ordinal() >= Rarity.RARE.ordinal(), "A renegade is at least Rare");
            helper.assertTrue(renegade.hasCustomName() && renegade.isCustomNameVisible(), "and wears its name");
            Renegades.Terror terror = Renegades.of(renegade);
            helper.assertTrue(terror != null && terror.villageSize() == 1, "It lays siege to its village (the neighbour): " + (terror == null ? 0 : terror.villageSize()));
            helper.assertTrue(EmergencyBand.live(terror.call()) && terror.call().trouble() == EmergencyBand.Trouble.RENEGADE, "The village calls for help");
            helper.assertTrue(renegade.getPersistentData().contains(Renegades.TAG), "The siege is saved on the renegade");
            helper.succeed();
        } finally { cleanup(renegade, neighbour, villager); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "gifted.siege")
    public static void aRenegadesBlowsOnItsVillageWaitTheirTurn(GameTestHelper helper) {
        Villager villager = mob(helper, EntityType.VILLAGER, 4, 4);
        Villager neighbour = mob(helper, EntityType.VILLAGER, 6, 4);
        GiftedVillagers.give(villager, ModAbilities.TELEKINESIS_ID);
        Mob renegade = null;
        try {
            renegade = Renegades.awaken(villager, true);
            helper.assertTrue(renegade != null, "They turned");
            float before = neighbour.getHealth();
            neighbour.hurt(renegade.damageSources().mobAttack(renegade), 4);
            helper.assertTrue(neighbour.getHealth() == before, "Between turns its blows on its village don't land");
            helper.assertTrue(!MobMoves.foe(GiftedMobs.of(renegade), neighbour), "and its powers leave them be");
            Renegades.hurry(renegade);
            neighbour.invulnerableTime = 0;
            neighbour.hurt(renegade.damageSources().mobAttack(renegade), 4);
            helper.assertTrue(neighbour.getHealth() < before, "When its turn comes, they do");
            helper.succeed();
        } finally { cleanup(renegade, neighbour, villager); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "gifted.guardian")
    public static void aGoodGiftMakesAGuardianAndRadiationMeltsDown(GameTestHelper helper) {
        Villager kind = mob(helper, EntityType.VILLAGER, 3, 3), hot = mob(helper, EntityType.VILLAGER, 8, 8);
        GiftedVillagers.give(kind, ModAbilities.HYDROKINESIS_ID);
        GiftedVillagers.give(hot, ModAbilities.RADIATION_ID);
        try {
            helper.assertTrue(Renegades.awaken(kind, false) == kind && GiftedMobs.isGood(kind), "A good gift: a guardian, still a villager");
            helper.assertTrue(Renegades.awaken(hot, false) == hot && com.jayptucker.heroesevolved.village.VillagerMeltdown.critical(hot),
                    "Radiation can't be held by anyone good: a meltdown");
            helper.succeed();
        } finally {
            com.jayptucker.heroesevolved.village.VillagerMeltdown.cancel(hot);
            cleanup(kind, hot);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "gifted.interrupt")
    public static void aHeavyBlowBreaksAWindup(GameTestHelper helper) {
        Pillager caster = mob(helper, EntityType.PILLAGER, 3, 3);
        IronGolem target = mob(helper, EntityType.IRON_GOLEM, 8, 8);
        try {
            GiftedMob g = GiftedMobs.bestow(caster, ModAbilities.ELECTROKINESIS_ID, Rarity.RARE, true, Gift.Origin.ADMIN, null, null);
            g.start(MobMoves.byId("thunderstrike"), target);
            helper.assertTrue(g.casting(), "It winds up");
            g.struck(3, null);
            helper.assertTrue(g.casting(), "A light blow doesn't break it");
            g.struck(12, null);
            helper.assertTrue(!g.casting() && g.staggered(), "A heavy one breaks the cast and staggers it");
            helper.succeed();
        } finally { cleanup(caster, target); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "gifted.unbound")
    public static void anEpicMobTurnsUnboundAtHalfHealthAndCallsFollowers(GameTestHelper helper) {
        Pillager boss = mob(helper, EntityType.PILLAGER, 5, 5);
        try {
            GiftedMob g = GiftedMobs.bestow(boss, ModAbilities.QUAKE_ID, Rarity.EPIC, true, Gift.Origin.ADMIN, null, null);
            g.tick();
            helper.assertTrue(!g.unbound(), "Not yet");
            boss.setHealth(boss.getMaxHealth() * .4F);
            g.tick();
            helper.assertTrue(g.unbound(), "Below half health it is unbound");
            helper.assertTrue(MobMoves.known(g.movePower(), Rarity.EPIC, true).stream().anyMatch(MobMoves.Move::signature), "with its signature");
            List<Mob> followers = helper.getLevel().getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(6),
                    m -> m.getPersistentData().getBoolean(GiftedMobs.FOLLOWER));
            helper.assertTrue(followers.size() == Rarity.EPIC.followers, "Its followers answer: " + followers.size());
            followers.forEach(Entity::discard);
            helper.succeed();
        } finally { cleanup(boss); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "gifted.cap")
    public static void onlyPlayersReallyHurtAMiniBoss(GameTestHelper helper) {
        Pillager boss = mob(helper, EntityType.PILLAGER, 5, 5);
        Zombie zombie = mob(helper, EntityType.ZOMBIE, 7, 5);
        try {
            GiftedMobs.bestow(boss, ModAbilities.SONIC_RESONANCE_ID, Rarity.EPIC, true, Gift.Origin.ADMIN, null, null);
            float before = boss.getHealth();
            boss.hurt(zombie.damageSources().mobAttack(zombie), 15);
            helper.assertTrue(before - boss.getHealth() <= 3.01F, "A monster's blow only chips it: " + (before - boss.getHealth()));
            helper.succeed();
        } finally { cleanup(boss, zombie); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "gifted.secondlife")
    public static void aRegeneratingMiniBossGetsUpOnce(GameTestHelper helper) {
        Pillager boss = mob(helper, EntityType.PILLAGER, 5, 5);
        ServerPlayer hero = player(helper, boss.position().add(2, 0, 0));
        try {
            GiftedMobs.bestow(boss, ModAbilities.REGENERATION_ID, Rarity.MYTHIC, true, Gift.Origin.ADMIN, null, null);
            boss.hurt(boss.damageSources().playerAttack(hero), 10_000);
            helper.assertTrue(boss.isAlive() && boss.getHealth() >= boss.getMaxHealth() / 2 - 1, "It gets back up: " + boss.getHealth());
            boss.invulnerableTime = 0;
            boss.hurt(boss.damageSources().playerAttack(hero), 10_000);
            helper.assertTrue(!boss.isAlive(), "but only once");
            helper.succeed();
        } finally { cleanup(boss); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "gifted.prize")
    public static void aDefeatedGiftDropsItsNamedWeapon(GameTestHelper helper) {
        Pillager boss = mob(helper, EntityType.PILLAGER, 5, 5);
        boss.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.COMMAND, null);
        GiftedMobs.bestow(boss, ModAbilities.FLIGHT_ID, Rarity.EPIC, true, Gift.Origin.ADMIN, null, "Lark Skystrider");
        // A generic blow is only "chip" damage to a mini-boss; a player's lands in full.
        float before = boss.getHealth();
        boss.hurt(boss.damageSources().generic(), 50);
        helper.assertTrue(before - boss.getHealth() <= 3.01F, "A trap only chips a mini-boss");
        boss.invulnerableTime = 0;
        boss.hurt(boss.damageSources().playerAttack(player(helper, boss.position().add(2, 0, 0))), 10_000);
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(boss.blockPosition()).inflate(4));
            try {
                helper.assertTrue(drops.stream().anyMatch(d -> d.getItem().is(Items.CROSSBOW) && d.getItem().getHoverName().getString().startsWith("Lark's")
                        && d.getItem().isEnchanted()), "Its crossbow, named and enchanted");
                helper.assertTrue(drops.stream().anyMatch(d -> d.getItem().is(Items.EMERALD)), "and emeralds");
                helper.succeed();
            } finally { drops.forEach(Entity::discard); cleanup(boss); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "gifted.raid")
    public static void theLastWavesLeaderIsAnEpicChampionAndTheVillageCalls(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(5, 2, 5));
        Raid raid = new Raid(9_999, helper.getLevel(), center);
        int last = raid.getNumGroups(helper.getLevel().getDifficulty());
        Pillager leader = EntityType.PILLAGER.create(helper.getLevel());
        try {
            leader.setPatrolLeader(true);
            raid.setLeader(last, leader);
            raid.joinRaid(last, leader, center, false);
            Gift gift = Gift.read(leader);
            helper.assertTrue(gift != null && gift.origin() == Gift.Origin.RAID && gift.rarity().ordinal() >= Rarity.EPIC.ordinal(),
                    "The last wave's leader is an Epic champion: " + gift);
            helper.assertTrue(GiftedRaids.calling() >= 1, "The raided village calls for help");
            helper.succeed();
        } finally {
            raid.stop();
            cleanup(leader);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "gifted.distress")
    public static void aDistressCallCarriesHundredsOfBlocks(GameTestHelper helper) {
        BlockPos bell = helper.absolutePos(new BlockPos(5, 2, 5));
        ServerPlayer near = player(helper, Vec3.atCenterOf(bell).add(3, 0, 0));
        helper.getLevel().addNewPlayer(near);
        EmergencyBand.Call call = EmergencyBand.call(helper.getLevel(), bell, EmergencyBand.Trouble.RENEGADE,
                net.minecraft.network.chat.Component.literal("Help!"), 100);
        try {
            helper.assertTrue(EmergencyBand.told(call, near), "Everyone in reach is told a village is calling");
            helper.assertTrue(EmergencyBand.reaches(call, Vec3.atCenterOf(bell).add(500, 0, 300)), "The call carries hundreds of blocks");
            helper.assertTrue(!EmergencyBand.reaches(call, Vec3.atCenterOf(bell).add(0, 0, 2000)), "but not for ever");
            helper.succeed();
        } finally {
            EmergencyBand.end(call);
            helper.getLevel().removePlayerImmediately(near, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "gifted.crater")
    public static void aCraterIsCarvedAllAtOnce(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(5, 3, 5));
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 2, 4)))
            helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        int broken = com.jayptucker.heroesevolved.radiation.NuclearFallout.carve(helper.getLevel(), center, 3, null);
        boolean inside = true, outside = true;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 2, 4))) {
            double d2 = pos.distSqr(center);
            boolean air = helper.getLevel().getBlockState(pos).isAir();
            if (d2 <= 9 && !air) inside = false;
            if (d2 > 9 && air) outside = false;
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 2, 4)))
            helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(broken > 0 && inside, "Everything in the sphere goes in one call");
        helper.assertTrue(outside, "and nothing outside it");
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "gifted.mimic")
    public static void aMimicCopiesThePowerOfWhoeverItFights(GameTestHelper helper) {
        Pillager mimic = mob(helper, EntityType.PILLAGER, 3, 3);
        Villager guardian = mob(helper, EntityType.VILLAGER, 6, 6);
        try {
            GiftedMob g = GiftedMobs.bestow(mimic, ModAbilities.MIMICRY_ID, Rarity.RARE, true, Gift.Origin.ADMIN, null, null);
            GiftedMobs.bestow(guardian, ModAbilities.CRYOKINESIS_ID, Rarity.RARE, false, Gift.Origin.GUARDIAN, null, null);
            helper.assertTrue(g.movePower().equals(ModAbilities.TELEKINESIS_ID), "Before it has anyone to copy, it moves things");
            mimic.setNoAi(false);
            g.tick();
            helper.assertTrue(g.movePower().equals(ModAbilities.CRYOKINESIS_ID), "Fighting a cryokinetic guardian, it copies the cold: " + g.movePower());
            helper.succeed();
        } finally { cleanup(mimic, guardian); }
    }
}
