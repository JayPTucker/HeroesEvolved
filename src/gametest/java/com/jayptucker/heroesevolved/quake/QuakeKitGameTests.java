package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Strain and the fracture, Resonate, Fault Line, rest, and Quake's Evolved trial. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class QuakeKitGameTests {
    private static ServerPlayer daisy(GameTestHelper h, int tier) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "quake-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 2)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true); mob.setPersistenceRequired();
        Vec3 feet = Vec3.atBottomCenterOf(h.absolutePos(at));
        mob.moveTo(feet.x, feet.y, feet.z, 180, 0);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
    }

    @GameTest(template = "rewind_empty", batch = "quakekit.strain")
    public static void strainMakesHerHitHarderUntilHerArmsCrack(GameTestHelper h) {
        floor(h);
        var p = daisy(h, 3);
        // Aimed at the floor, so the blast stays inside this test's space.
        p.setXRot(90);
        try {
            h.assertTrue(QuakeKit.strain(p) == 0 && QuakeKit.power(p) == 1, "Rested: no Strain, normal force");
            QuakeService.shockwave(p, 3);
            h.assertTrue(QuakeKit.strain(p) == QuakeKit.SHOCKWAVE_STRAIN, "A Shockwave costs 8 Strain");
            QuakeKit.setStrain(p, 80);
            h.assertTrue(QuakeKit.power(p) == 1.4F, "At 80 Strain she hits 40% harder");
            QuakeKit.setStrain(p, 95);
            p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 2))));
            QuakeService.shockwave(p, 3);
            h.assertTrue(QuakeKit.fractured(p), "Past 100 the bones in her arms crack");
            h.assertTrue(QuakeKit.strain(p) == QuakeKit.AFTER_FRACTURE && p.hasEffect(MobEffects.WEAKNESS), "Left weak, Strain down to 40");
            var shockwave = ModAbilities.QUAKE.get().actions().get(AbilitySlot.PRIMARY);
            h.assertTrue(!shockwave.canUse(new AbilityUseContext(p, ModAbilities.QUAKE_ID, 3, false)), "And her powers are locked while they set");
            h.succeed();
        } finally { QuakeService.stopAll(p); QuakeKit.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "quakekit.rest", timeoutTicks = 140)
    public static void restBringsStrainBackDown(GameTestHelper h) {
        var p = daisy(h, 3);
        QuakeKit.setStrain(p, 50);
        h.runAfterDelay(110, () -> {
            try {
                h.assertTrue(QuakeKit.strain(p) < 50, "Strain falls once she stops using her power: " + QuakeKit.strain(p));
                h.succeed();
            } finally { QuakeKit.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "quakekit.resonate", timeoutTicks = 60)
    public static void resonateShakesABodyApartFromTheInside(GameTestHelper h) {
        floor(h);
        var p = daisy(h, 3);
        var pillager = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 6));
        pillager.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        h.assertTrue(QuakeKit.resonate(p, 3) && QuakeKit.resonating(pillager), "She finds its frequency");
        h.assertTrue(QuakeKit.strain(p) == QuakeKit.RESONATE_STRAIN, "Resonate costs 12 Strain");
        h.runAfterDelay(30, () -> {
            try {
                h.assertTrue(pillager.getHealth() < 100, "Its bones ache through the armour: " + pillager.getHealth());
                h.assertTrue(pillager.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() > 0, "Its armour rattles itself apart");
                h.assertTrue(pillager.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "And it can barely stand");
                h.succeed();
            } finally { pillager.discard(); QuakeKit.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "quakekit.fault", timeoutTicks = 60)
    public static void aFaultLineRacesAheadAndEruptsUnderEveryEnemy(GameTestHelper h) {
        floor(h);
        // A wall at the far side keeps the crack inside this test's space, and shows that walls stop it.
        for (int y = 2; y <= 3; y++) for (int z = 0; z < 12; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(11, y, z)), Blocks.STONE.defaultBlockState(), 3);
        var p = daisy(h, 1);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 2, 5)))); p.setYRot(-90);
        p.setOnGround(true);
        var near = mob(h, EntityType.PILLAGER, new BlockPos(4, 2, 5));
        var far = mob(h, EntityType.PILLAGER, new BlockPos(8, 2, 5));
        var aside = mob(h, EntityType.PILLAGER, new BlockPos(4, 2, 10));
        h.assertTrue(QuakeKit.faultLine(p, 1) && QuakeKit.faulting(), "The crack opens");
        h.runAfterDelay(20, () -> {
            try {
                h.assertTrue(near.getHealth() < 100 && far.getHealth() < 100, "Both enemies on the line are hit: " + near.getHealth() + " " + far.getHealth());
                h.assertTrue(aside.getHealth() == 100, "One off to the side is not");
                h.assertTrue(!QuakeKit.faulting(), "The wall stopped it");
                h.assertTrue(QuakeKit.strain(p) == QuakeKit.FAULT_STRAIN, "Fault Line costs 16 Strain");
                h.succeed();
            } finally { near.discard(); far.discard(); aside.discard(); QuakeKit.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "quakekit.trial")
    public static void quakeEvolvesByPushingThroughIt(GameTestHelper h) {
        var p = daisy(h, 3);
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        try {
            h.assertTrue(EvolutionTrials.special("quake") && EvolutionTrials.trial("quake").goal() == 40, "Quake's own trial");
            QuakeKit.landed(p, zombie);
            h.assertTrue(EvolutionTrials.progress(p, "quake") == 0, "A hit at rest does not count");
            QuakeKit.setStrain(p, 85);
            QuakeKit.landed(p, zombie);
            h.assertTrue(EvolutionTrials.progress(p, "quake") == 1, "A hit at the edge of breaking does");
            h.succeed();
        } finally { QuakeKit.forget(p); }
    }
}
