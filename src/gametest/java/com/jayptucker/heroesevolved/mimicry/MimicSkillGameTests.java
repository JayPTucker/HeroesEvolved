package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Empathic Mimicry's own skills (MimicSkills): Borrowed Strike carries the channelled power's touch, Empathic Surge
 * strengthens friends and not foes, Calm lifts only what harms, Absorb Pain takes a friend's sickness at half and mends
 * them, Sense Powers finds the powered, and Overcharge arms the next Echo.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class MimicSkillGameTests {
    private static ServerPlayer player(GameTestHelper h, ResourceLocation power, int x, String name) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); grace.setAccessible(true);
            grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, 3))));
        p.setYRot(0);
        p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static ServerPlayer mimic(GameTestHelper h, int level) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID, 5, "mimic-skills");
        PlayerProgressionService.setPowerLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.MIMICRY_ID, 5, false); }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(100);
        mob.setHealth(100);
        mob.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180, 0);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static Wolf pet(GameTestHelper h, ServerPlayer owner, double dx, double dz) {
        Wolf wolf = mob(h, EntityType.WOLF, owner, dx, dz);
        wolf.tame(owner);
        return wolf;
    }

    private static void aim(ServerPlayer p, LivingEntity at) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, at.getBoundingBox().getCenter());
    }

    private static void done(ServerPlayer p, Entity... others) {
        for (Entity e : others) e.discard();
        MimicryService.forget(p);
        p.setData(ModDataAttachments.MIMIC_MEMORY, MimicryService.Memory.empty());
        p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.skills")
    public static void borrowedStrikeCarriesTheChannelledPowersTouch(GameTestHelper h) {
        ServerPlayer p = mimic(h, 10);
        LivingEntity plain = mob(h, EntityType.PILLAGER, p, -2, 6), burnt = mob(h, EntityType.PILLAGER, p, 0, 6),
                chilled = mob(h, EntityType.PILLAGER, p, 2, 6);
        try {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition().add(0, 6, -10));
            h.assertFalse(MimicSkills.skill(context(p), 0), "With no foe in sight, Borrowed Strike is refused");
            aim(p, plain);
            h.assertTrue(MimicSkills.skill(context(p), 0), "A foe in sight is struck");
            h.assertTrue(Math.abs(100 - plain.getHealth() - 11) < .01F, "6 + level damage, got " + (100 - plain.getHealth()));
            h.assertTrue(!plain.isOnFire() && !plain.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "With nothing channelled, no touch of any power");
            h.assertTrue(MimicryService.remember(p, ModAbilities.PYROKINESIS_ID, 5), "The empath keeps fire");
            aim(p, burnt);
            h.assertTrue(MimicSkills.skill(context(p), 0) && burnt.isOnFire(), "Channelling fire, the strike burns");
            h.assertTrue(MimicryService.remember(p, ModAbilities.CRYOKINESIS_ID, 5), "The empath keeps cold");
            aim(p, chilled);
            h.assertTrue(MimicSkills.skill(context(p), 0) && chilled.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Channelling cold, the strike slows");
            h.succeed();
        } finally { done(p, plain, burnt, chilled); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.skills")
    public static void empathicSurgeStrengthensFriendsAndNotFoes(GameTestHelper h) {
        ServerPlayer p = mimic(h, 5);
        Wolf wolf = pet(h, p, 3, 0);
        LivingEntity foe = mob(h, EntityType.PILLAGER, p, -3, 0), far = pet(h, p, 0, 12);
        try {
            h.assertTrue(MimicSkills.skill(context(p), 1), "Empathic Surge goes out");
            h.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST) && wolf.hasEffect(MobEffects.DAMAGE_BOOST), "The empath and a pet beside them feel it");
            h.assertFalse(foe.hasEffect(MobEffects.DAMAGE_BOOST), "A foe does not");
            h.assertFalse(far.hasEffect(MobEffects.DAMAGE_BOOST), "Nor does a friend beyond eight blocks");
            h.succeed();
        } finally { done(p, wolf, foe, far); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.skills")
    public static void calmLiftsWhatHarmsAndLeavesWhatHelps(GameTestHelper h) {
        ServerPlayer p = mimic(h, 5);
        Wolf wolf = pet(h, p, 2, 0);
        try {
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
            wolf.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
            h.assertTrue(MimicSkills.skill(context(p), 2), "Calm settles");
            h.assertFalse(p.hasEffect(MobEffects.POISON), "The empath's poison lifts");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "What helps is left");
            h.assertFalse(wolf.hasEffect(MobEffects.WEAKNESS), "A friend's weakness lifts too");
            h.succeed();
        } finally { done(p, wolf); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.skills")
    public static void absorbPainTakesAFriendsSicknessAtHalfAndMendsThem(GameTestHelper h) {
        ServerPlayer p = mimic(h, 5);
        Wolf wolf = pet(h, p, 0, 3);
        try {
            wolf.setHealth(10);
            wolf.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            aim(p, wolf);
            h.assertTrue(MimicSkills.technique(context(p), "absorb_pain"), "Absorb Pain finds the friend in sight");
            h.assertFalse(wolf.hasEffect(MobEffects.POISON), "Their poison is taken");
            MobEffectInstance taken = p.getEffect(MobEffects.POISON);
            h.assertTrue(taken != null && taken.getDuration() <= 100, "The empath carries it at half the time, " + taken);
            h.assertTrue(wolf.getHealth() >= 14 - .01F, "And the friend mends for 4, has " + wolf.getHealth());
            h.succeed();
        } finally { done(p, wolf); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.skills")
    public static void sensePowersFindsThePoweredAndOverchargeArmsTheNextEcho(GameTestHelper h) {
        ServerPlayer p = mimic(h, 5);
        ServerPlayer pyro = player(h, ModAbilities.PYROKINESIS_ID, 9, "sensed-pyro");
        try {
            h.assertTrue(MimicryService.imprintOf(p, pyro) != null, "A pyrokinetic has a power to feel");
            h.assertTrue(MimicSkills.technique(context(p), "sense_powers"), "Sense Powers listens");
            h.assertFalse(MimicryService.overcharged(p), "Nothing is overcharged yet");
            h.assertTrue(MimicSkills.technique(context(p), "overcharge") && MimicryService.overcharged(p), "Overcharge arms the next Echo");
            h.assertFalse(MimicSkills.technique(context(p), "not_a_technique"), "An unknown technique is refused");
            h.succeed();
        } finally {
            pyro.serverLevel().removePlayerImmediately(pyro, Entity.RemovalReason.DISCARDED);
            done(p);
        }
    }
}
