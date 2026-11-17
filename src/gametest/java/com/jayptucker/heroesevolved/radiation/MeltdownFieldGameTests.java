package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.hazard.HazardDamage;
import com.jayptucker.heroesevolved.hazard.SuitFabricatorMenu;
import com.jayptucker.heroesevolved.hazard.SuitItems;
import com.jayptucker.heroesevolved.hazard.Suits;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * What pours off someone melting down, who it hurts, and the suits that keep it off. Bodies are staged high over the
 * test's own chunk where nothing else is.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class MeltdownFieldGameTests {
    private static Vec3 high(GameTestHelper helper, int height, double dx) {
        return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 0, 5))).add(dx, 0, 0).with(net.minecraft.core.Direction.Axis.Y, height);
    }

    private static <T extends LivingEntity> T mob(GameTestHelper helper, EntityType<T> type, Vec3 at) {
        T e = type.create(helper.getLevel());
        if (e == null) throw new AssertionError("No " + type);
        e.setPos(at);
        e.setNoGravity(true);
        helper.getLevel().addFreshEntity(e);
        return e;
    }

    public static ServerPlayer player(GameTestHelper helper, Vec3 at, ResourceLocation power) {
        ServerPlayer p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "hazard-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(at);
        p.setNoGravity(true);
        p.setGameMode(GameType.SURVIVAL);
        if (power != null) p.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty()
                .assignDormant(power).unlock(power));
        helper.getLevel().addNewPlayer(p);
        return p;
    }

    public static void suit(LivingEntity e, boolean velocity) {
        e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(velocity ? SuitItems.VELOCITY_COWL.get() : SuitItems.RADIATION_HOOD.get()));
        e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(velocity ? SuitItems.VELOCITY_SUIT.get() : SuitItems.RADIATION_COAT.get()));
        e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(velocity ? SuitItems.VELOCITY_LEGGINGS.get() : SuitItems.RADIATION_TROUSERS.get()));
        e.setItemSlot(EquipmentSlot.FEET, new ItemStack(velocity ? SuitItems.VELOCITY_BOOTS.get() : SuitItems.RADIATION_BOOTS.get()));
    }

    private static void cleanup(Entity... entities) {
        for (Entity e : entities) {
            if (e == null) continue;
            if (e instanceof LivingEntity l) MeltdownField.end(l);
            if (e instanceof ServerPlayer p) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else e.discard();
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "hazard.expose")
    public static void theNearFieldBurnsTheUnprotectedOnly(GameTestHelper helper) {
        Zombie core = mob(helper, EntityType.ZOMBIE, high(helper, 410, 0));
        Pillager bare = mob(helper, EntityType.PILLAGER, high(helper, 410, 2));
        Pillager halfSuited = mob(helper, EntityType.PILLAGER, high(helper, 410, -2));
        ServerPlayer sealed = player(helper, high(helper, 410, 3), null);
        ServerPlayer reactor = player(helper, high(helper, 410, -3), ModAbilities.RADIATION_ID);
        try {
            suit(sealed, false);
            halfSuited.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SuitItems.RADIATION_COAT.get()));
            halfSuited.setItemSlot(EquipmentSlot.LEGS, new ItemStack(SuitItems.RADIATION_TROUSERS.get()));
            float bareBefore = bare.getHealth(), halfBefore = halfSuited.getHealth(), sealedBefore = sealed.getHealth(), reactorBefore = reactor.getHealth();
            int coatBefore = sealed.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
            for (LivingEntity e : new LivingEntity[] {bare, halfSuited, sealed, reactor}) {
                e.invulnerableTime = 0;
                MeltdownField.expose(core, helper.getLevel(), e, 8, 1, 0, true);
            }
            helper.assertTrue(bare.getHealth() <= bareBefore - 7.5F, "Nothing between them and the heat: it all lands (" + bare.getHealth() + ")");
            helper.assertTrue(halfSuited.getHealth() < halfBefore && halfSuited.getHealth() > bareBefore - 7.5F,
                    "Half a suit keeps half of it off (" + halfSuited.getHealth() + ")");
            helper.assertTrue(bare.hasEffect(ModItems.RADIATION_SICKNESS), "And the unprotected take a dose");
            helper.assertTrue(sealed.getHealth() == sealedBefore && !sealed.hasEffect(ModItems.RADIATION_SICKNESS), "A full suit seals it all out");
            helper.assertTrue(Suits.sealed(sealed) && RadiationSickness.immune(sealed), "Sealed is immune to the sickness too");
            helper.assertTrue(sealed.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() >= coatBefore, "The suit wears as it works");
            helper.assertTrue(reactor.getHealth() == reactorBefore, "A radioactive player is immune to it: it's their own power");
            helper.succeed();
        } finally { cleanup(core, bare, halfSuited, sealed, reactor); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "hazard.regrow")
    public static void regenerationBurnsButCannotDieOfIt(GameTestHelper helper) {
        Zombie core = mob(helper, EntityType.ZOMBIE, high(helper, 425, 0));
        ServerPlayer claire = player(helper, high(helper, 425, 2), ModAbilities.REGENERATION_ID);
        ServerPlayer other = player(helper, high(helper, 425, -2), ModAbilities.SUPERSPEED_ID);
        try {
            claire.setHealth(4);
            other.setHealth(4);
            claire.invulnerableTime = 0;
            other.invulnerableTime = 0;
            MeltdownField.expose(core, helper.getLevel(), claire, 30, 1, 0, true);
            MeltdownField.expose(core, helper.getLevel(), other, 30, 1, 0, true);
            helper.assertTrue(claire.isAlive() && claire.getHealth() >= 1 && claire.getHealth() < 4,
                    "Regeneration burns down to half a heart and no further (" + claire.getHealth() + ")");
            helper.assertTrue(!other.isAlive() || other.getHealth() <= 0, "Anyone else without a suit dies of it");
            helper.succeed();
        } finally { cleanup(core, claire, other); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "hazard.waves")
    public static void shockWavesRollOutAndGrowAsTimeRunsOut(GameTestHelper helper) {
        helper.assertTrue(MeltdownField.waveRadius(1) > MeltdownField.waveRadius(0) && MeltdownField.waveStrength(1) > MeltdownField.waveStrength(0) * 4
                && MeltdownField.waveEvery(1) < MeltdownField.waveEvery(0) / 4, "The less time left, the bigger, harder and closer together the waves");
        helper.assertTrue(MeltdownField.nearRadius(1) >= 12, "At the end the lethal heat reaches past a cryokinetic's Ice Blast");
        Zombie core = mob(helper, EntityType.ZOMBIE, high(helper, 440, 0));
        Pillager near = mob(helper, EntityType.PILLAGER, high(helper, 440, 6));
        float before = near.getHealth();
        helper.onEachTick(() -> { if (core.isAlive()) MeltdownField.tick(core, 0.95F, false); });
        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(near.getHealth() < before || !near.isAlive(), "A wave reached them and burned them (" + near.getHealth() + ")");
                helper.assertTrue(MeltdownField.active(core), "The field is live while it's ticked");
                helper.succeed();
            } finally { cleanup(core, near); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "hazard.deaths")
    public static void radiationDeathsAreToldApart(GameTestHelper helper) {
        Zombie sick = mob(helper, EntityType.ZOMBIE, high(helper, 455, 0));
        Zombie clean = mob(helper, EntityType.ZOMBIE, high(helper, 455, 2));
        try {
            var level = helper.getLevel();
            helper.assertTrue(RadiationDeaths.byRadiation(clean, HazardDamage.source(level, HazardDamage.MELTDOWN)), "A meltdown's heat is radiation");
            helper.assertTrue(RadiationDeaths.byRadiation(clean, HazardDamage.source(level, HazardDamage.FRICTION)), "So is burning up from friction");
            helper.assertTrue(!RadiationDeaths.byRadiation(clean, level.damageSources().generic()), "A plain blow is not");
            sick.addEffect(new MobEffectInstance(ModItems.RADIATION_SICKNESS, 100, 1));
            helper.assertTrue(RadiationDeaths.byRadiation(sick, level.damageSources().generic()), "Anyone sick with it who dies burns away");
            RadiationDeaths.mark(clean);
            helper.assertTrue(RadiationDeaths.byRadiation(clean, level.damageSources().generic()), "As does anyone radiation just hurt");
            helper.succeed();
        } finally { cleanup(sick, clean); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "hazard.fabricator")
    public static void theFabricatorMakesASuitPieceFromItsMaterials(GameTestHelper helper) {
        ServerPlayer tailor = player(helper, high(helper, 470, 0), null);
        try {
            SuitFabricatorMenu menu = new SuitFabricatorMenu(0, tailor.getInventory(), ContainerLevelAccess.NULL);
            menu.input().setItem(0, new ItemStack(Items.LEATHER_CHESTPLATE));
            menu.input().setItem(1, new ItemStack(SuitItems.LEAD_INGOT.get(), 5));
            menu.input().setItem(2, new ItemStack(Items.SLIME_BALL, 2));
            menu.clickMenuButton(tailor, 1);
            helper.assertTrue(menu.result().is(SuitItems.RADIATION_COAT.get()), "Coat pattern with its materials in: a radiation coat (" + menu.result() + ")");
            menu.clickMenuButton(tailor, 5);
            helper.assertTrue(menu.result().isEmpty(), "The velocity suit's pattern doesn't match those materials");
            menu.clickMenuButton(tailor, 1);
            menu.slots.get(SuitFabricatorMenu.RESULT).onTake(tailor, menu.result());
            helper.assertTrue(menu.input().getItem(1).getCount() == 1 && menu.input().getItem(0).isEmpty() && menu.input().getItem(2).isEmpty(),
                    "Taking it uses up exactly its materials: " + menu.input().getItem(1));
            helper.succeed();
        } finally { cleanup(tailor); }
    }
}
