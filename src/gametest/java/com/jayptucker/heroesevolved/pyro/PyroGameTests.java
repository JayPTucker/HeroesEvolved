package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PyroGameTests {
    private static ServerPlayer player(GameTestHelper h, int level) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pyro-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.PYROKINESIS_ID).unlock(ModAbilities.PYROKINESIS_ID));
        PlayerProgressionService.setLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static LivingEntity target(GameTestHelper h, ServerPlayer p, double distance) {
        var target = EntityType.PILLAGER.create(h.getLevel());
        target.setNoAi(true); target.setPersistenceRequired();
        target.moveTo(p.getX(), p.getY(), p.getZ() + distance, 180, 0);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    @GameTest(template = "rewind_empty", batch = "pyro.immunity")
    public static void fireNeverHurtsAndLavaStopsHurtingAtLevelThree(GameTestHelper h) {
        ServerPlayer novice = player(h, 1);
        float health = novice.getHealth();
        novice.hurt(novice.damageSources().inFire(), 4);
        novice.hurt(novice.damageSources().onFire(), 4);
        h.assertTrue(novice.getHealth() == health, "Fire must never hurt a pyrokinetic");
        novice.invulnerableTime = 0;
        novice.hurt(novice.damageSources().lava(), 4);
        h.assertTrue(novice.getHealth() < health, "Lava still hurts below level three");
        ServerPlayer veteran = player(h, 3);
        float before = veteran.getHealth();
        veteran.hurt(veteran.damageSources().lava(), 4);
        h.assertTrue(veteran.getHealth() == before, "Lava must not hurt a level three pyrokinetic");
        PyroService.forget(novice); PyroService.forget(veteran);
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "pyro.flare")
    public static void flareLightsRealFireUnlessCrouching(GameTestHelper h) {
        ServerPlayer p = player(h, 1);
        p.setXRot(50);
        try {
            h.assertTrue(PyroService.flare(p, 1), "Flare must fire");
            BlockPos origin = h.absolutePos(new BlockPos(3, 2, 3));
            boolean lit = BlockPos.betweenClosedStream(origin.offset(-1, -1, 0), origin.offset(1, 1, 5))
                    .anyMatch(pos -> h.getLevel().getBlockState(pos).is(BlockTags.FIRE));
            h.assertTrue(lit, "Flare must light a real fire block where it strikes");
            BlockPos.betweenClosed(origin.offset(-1, -1, 0), origin.offset(1, 1, 5)).forEach(pos -> {
                if (h.getLevel().getBlockState(pos).is(BlockTags.FIRE)) h.getLevel().removeBlock(pos, false);
            });
            p.setShiftKeyDown(true);
            PyroService.flare(p, 1);
            boolean contained = BlockPos.betweenClosedStream(origin.offset(-1, -1, 0), origin.offset(1, 1, 5))
                    .noneMatch(pos -> h.getLevel().getBlockState(pos).is(BlockTags.FIRE));
            h.assertTrue(contained, "Crouching keeps the flames contained");
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.heat")
    public static void heatTurnsFlamesBlueAndWaterQuenchesIt(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        try {
            var victim = target(h, p, 4);
            PyroService.burn(p, victim, 2, 0);
            float orange = victim.getMaxHealth() - victim.getHealth();
            h.assertTrue(!PyroService.isBlue(p), "Heat starts cold");
            PyroService.addHeat(p, 80);
            h.assertTrue(PyroService.isBlue(p), "Eighty heat turns the flames blue");
            PyroService.quench(p);
            h.assertTrue(PyroService.heat(p) == 0 && !PyroService.isBlue(p), "Quenching resets Heat");
            h.assertTrue(orange > 0 && victim.getRemainingFireTicks() <= 0, "A zero-second burn deals damage without ignition");
            victim.discard();
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.ring")
    public static void ringOfFireBurnsUpIncomingArrows(GameTestHelper h) {
        ServerPlayer p = player(h, 2);
        PyroService.ringOfFire(p, 2);
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setPos(p.position().add(3, 1, 0));
        arrow.setDeltaMovement(-0.01, 0, 0);
        arrow.setNoGravity(true);
        h.getLevel().addFreshEntity(arrow);
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(arrow.isRemoved(), "Projectiles inside the ring must burn up");
                h.succeed();
            } finally { PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.thermal")
    public static void fireOnAFrozenEnemyCausesThermalShock(GameTestHelper h) {
        ServerPlayer p = player(h, 3);
        try {
            var frozen = target(h, p, 3);
            var control = target(h, p, -3);
            h.assertTrue(CryoService.freezeSolid(frozen, 100, p), "The setup must actually freeze the target");
            PyroService.burn(p, frozen, 2, 0);
            PyroService.burn(p, control, 2, 0);
            h.assertTrue(!CryoService.isFrozen(frozen), "Thermal Shock consumes the freeze");
            h.assertTrue(frozen.getHealth() < control.getHealth(), "Thermal Shock adds a steam explosion on top of the burn");
            frozen.discard(); control.discard();
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.inferno")
    public static void infernoChargesAndShiftCancelsIt(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        try {
            h.assertTrue(PyroService.startInferno(p, 5) && PyroService.isCharging(p), "Inferno must start charging");
            h.assertTrue(PyroService.cancelInferno(p) && !PyroService.isCharging(p), "Shift cancels a charging Inferno");
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.skills")
    public static void techniquesHaveRealConsequences(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        try {
            var context = new AbilityUseContext(p, ModAbilities.PYROKINESIS_ID, 5, false);
            BlockPos fire = h.absolutePos(new BlockPos(5, 2, 3));
            h.getLevel().setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
            h.assertTrue(h.getLevel().getBlockState(fire).is(BlockTags.FIRE), "Setup must place a fire");
            PlayerEnergyService.consumeUpTo(p, 40);
            int before = PlayerEnergyService.getEnergy(p);
            h.assertTrue(PyroService.technique(context, "fire_shepherd"), "Fire Shepherd must activate");
            h.assertTrue(h.getLevel().getBlockState(fire).isAir(), "Fire Shepherd must put out nearby fire");
            h.assertTrue(PlayerEnergyService.getEnergy(p) > before, "Drawing in fire restores stamina");
            var victim = target(h, p, 6);
            float health = victim.getHealth();
            h.assertTrue(PyroService.technique(context, "blue_lance"), "Blue Lance must fire");
            h.assertTrue(victim.getHealth() < health, "Blue Lance must hit what it pierces");
            victim.discard();
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "helixite")
    public static void helixiteOreDropsShardsAndGeneratesUnderground(GameTestHelper h) {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        for (Block ore : new Block[] {ModBlocks.HELIXITE_ORE.get(), ModBlocks.DEEPSLATE_HELIXITE_ORE.get()}) {
            var drops = Block.getDrops(ore.defaultBlockState(), h.getLevel(), pos, null, null, tool);
            h.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.HELIXITE_SHARD.get()),
                    "Mining Helixite with a pickaxe must drop one shard: " + drops);
            h.assertTrue(ore.defaultBlockState().getLightEmission() > 0, "Helixite glows faintly");
        }
        var placed = h.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        h.assertTrue(placed.containsKey(ResourceLocation.fromNamespaceAndPath("heroesevolved", "helixite_ore")),
                "The Helixite ore feature must be registered for world generation");
        h.succeed();
    }
}
