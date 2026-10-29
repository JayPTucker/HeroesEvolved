package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.mojang.authlib.GameProfile;
import com.mojang.math.Transformation;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TelekinesisSafetyGameTests {
    private static ServerPlayer player(GameTestHelper h, String... upgrades) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "kinetic-safety"));
        try {
            Connection connection = p.connection.getConnection();
            var field = Connection.class.getDeclaredField("channel"); field.setAccessible(true);
            if (connection.channel() == null) field.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 40, 3))));
        p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.TELEKINESIS_ID).unlock(ModAbilities.TELEKINESIS_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        var learned = new HashSet<>(Set.of("telekinesis_0_1", "telekinesis_1_1", "telekinesis_2_1",
                "telekinesis_remote_hand", "telekinesis_kinetic_volley", "telekinesis_force_bubble"));
        for (String suffix : upgrades) learned.add("telekinesis_" + suffix);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TELEKINESIS_ID.toString(),
                new SkillTreeData.Build(5, learned, Map.of(), 6)));
        return p;
    }

    private static LivingEntity enemy(GameTestHelper h, Vec3 at) {
        var target = EntityType.PILLAGER.create(h.getLevel());
        target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired(); target.setPos(at);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.setHealth(100);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    private static void aim(ServerPlayer p, Vec3 at) {
        Vec3 delta = at.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }

    private static void tick(ServerPlayer p, int count) {
        for (int i = 0; i < count; i++) {
            TelekineticArsenalService.tick(p.getServer());
            TelekinesisSkillService.tick(p.getServer());
        }
    }

    private static List<Display.BlockDisplay> displays(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Display.BlockDisplay.class, p.getBoundingBox().inflate(12));
    }

    private static void cleanup(ServerPlayer p, Entity... targets) {
        TelekinesisService.stopAll(p);
        for (Entity target : targets) target.discard();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.safety_display")
    public static void debrisUsesItsEffectiveSmallCenteredTransformAndCannotBeSaved(GameTestHelper h) {
        var p = player(h);
        try {
            TelekineticArsenalService.gather(p, 1);
            h.assertTrue(TelekineticArsenalService.lance(p, p.getEyePosition().add(0, 0, 12)), "Lance must create the larger fragment");
            var debris = displays(p);
            h.assertTrue(debris.size() == 2, "Gather and lance must each create a visible display");
            boolean small = false, large = false;
            for (Display.BlockDisplay display : debris) {
                // Re-encoding the live entity reads its synchronized effective transform, not the constructor input.
                CompoundTag saved = display.saveWithoutId(new CompoundTag());
                Transformation transform = Transformation.EXTENDED_CODEC.parse(NbtOps.INSTANCE,
                        saved.get("transformation")).getOrThrow();
                Vector3f scale = transform.getScale();
                small |= Math.abs(scale.x - .28F) < .0001;
                large |= Math.abs(scale.x - .42F) < .0001;
                h.assertTrue(Math.abs(scale.x - scale.y) < .0001 && Math.abs(scale.x - scale.z) < .0001,
                        "Debris must retain uniform scale after Display's NBT codec applies it");
                h.assertTrue(transform.getMatrix().transformPosition(new Vector3f(.5F, .5F, .5F)).length() < .0001,
                        "The actual rendered cube must be centered on the swept collision position");
                // PersistentEntitySectionManager filters shouldBeSaved before calling low-level NBT serialization.
                h.assertTrue(!display.shouldBeSaved(), "Temporary debris must be excluded from chunk saving");
            }
            h.assertTrue(small && large, "The codec must apply both .28 and .42 scales instead of silently retaining identity");
            var ordinary = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, h.getLevel());
            var candidates = new ArrayList<Entity>(debris);
            candidates.add(ordinary);
            h.assertTrue(candidates.stream().filter(Entity::shouldBeSaved).toList().equals(List.of(ordinary)),
                    "The chunk persistence gate must omit temporary debris while retaining ordinary block displays");
            ordinary.discard();
            TelekinesisService.stopAll(p);
            h.assertTrue(debris.stream().allMatch(Entity::isRemoved), "Session cleanup must discard every display");
        } finally { cleanup(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.safety_pet")
    public static void ownedPetsIgnoreDebrisFieldsAndProjectileReflection(GameTestHelper h) {
        var p = player(h);
        var pet = EntityType.WOLF.create(h.getLevel());
        pet.setTame(true, true); pet.setOwnerUUID(p.getUUID());
        pet.setNoAi(true); pet.setNoGravity(true); pet.setPersistenceRequired();
        pet.setPos(p.position().add(0, 0, 6)); h.getLevel().addFreshEntity(pet);
        float health = pet.getHealth();
        var hostile = enemy(h, p.position().add(3, 0, 4));
        Arrow friendlyShot = new Arrow(EntityType.ARROW, h.getLevel());
        friendlyShot.setOwner(pet); friendlyShot.setPos(p.position().add(0, 1, 3));
        friendlyShot.setDeltaMovement(0, 0, -.8); h.getLevel().addFreshEntity(friendlyShot);
        try {
            h.assertTrue(!TelekinesisSkillService.hostile(p, pet), "An owned pet must never be classified as hostile");
            TelekineticArsenalService.lance(p, pet.getBoundingBox().getCenter()); tick(p, 8);
            h.assertTrue(pet.getHealth() == health, "A physical fragment crossing its owner's pet must not hurt it");
            pet.setPos(p.position().add(1.5, 0, 0)); pet.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(TelekinesisSkillService.activate(p, "telekinesis_force_bubble", false), "Bubble must cast");
            TelekinesisSkillService.tick(p.getServer());
            h.assertTrue(pet.getDeltaMovement().lengthSqr() == 0 && friendlyShot.getOwner() == pet
                            && friendlyShot.getDeltaMovement().z < 0,
                    "The bubble must leave both the owned pet and its outgoing/incoming shots alone");
            pet.setPos(hostile.position().add(1.5, 0, 0)); pet.setDeltaMovement(Vec3.ZERO);
            aim(p, hostile.getBoundingBox().getCenter());
            h.assertTrue(TelekinesisSkillService.activate(p, "telekinesis_1_1", false), "Well must cast beside the pet");
            TelekinesisSkillService.tick(p.getServer());
            h.assertTrue(pet.getDeltaMovement().lengthSqr() == 0 && pet.getHealth() == health,
                    "A nearby hostile target must not make the well pull an owned pet");
        } finally { cleanup(p, pet, hostile, friendlyShot); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.safety_respec")
    public static void respecDiscardsFlyingAndQueuedFragmentsBeforeAnyDelayedDamage(GameTestHelper h) {
        var p = player(h, "five_fragments");
        var target = enemy(h, p.position().add(0, 0, 18)); aim(p, target.getBoundingBox().getCenter());
        try {
            h.assertTrue(TelekinesisSkillService.activate(p, "telekinesis_kinetic_volley", false), "Volley must gather");
            tick(p, 8);
            h.assertTrue(TelekineticArsenalService.flyingCount(p) == 1 && TelekineticArsenalService.orbitCount(p) == 4,
                    "Fixture must contain both a flying fragment and pending launches");
            var debris = displays(p);
            h.assertTrue(SkillTreeService.respecialize(p), "An undamaged player may respecialize");
            h.assertTrue(TelekineticArsenalService.flyingCount(p) == 0 && TelekineticArsenalService.orbitCount(p) == 0
                            && debris.stream().allMatch(Entity::isRemoved),
                    "Respecializing must discard queued and flying displays even when Volley was never equipped");
            tick(p, 100);
            h.assertTrue(target.getHealth() == 100, "Removed queued shots must never cause delayed post-respec damage");
        } finally { cleanup(p, target); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.safety_sphere")
    public static void bubbleReflectsSphereCrossingsAndLeavesCubeCornerNearMissesAlone(GameTestHelper h) {
        var p = player(h);
        var owner = enemy(h, p.position().add(0, 0, 8));
        Vec3 center = p.position().add(0, 1, 0);
        Arrow corner = new Arrow(EntityType.ARROW, h.getLevel()), crossing = new Arrow(EntityType.ARROW, h.getLevel());
        corner.setOwner(owner); corner.setPos(center.add(2.9, 0, 2.9)); corner.setDeltaMovement(0, 0, -.5);
        crossing.setOwner(owner); crossing.setPos(center.add(0, 0, 3.2)); crossing.setDeltaMovement(0, 0, -.8);
        h.getLevel().addFreshEntity(corner); h.getLevel().addFreshEntity(crossing);
        try {
            h.assertTrue(TelekinesisSkillService.activate(p, "telekinesis_force_bubble", false), "Bubble must cast");
            TelekinesisSkillService.tick(p.getServer());
            h.assertTrue(corner.getOwner() == owner && corner.getDeltaMovement().z == -.5,
                    "A segment through the enclosing cube's corner, outside the sphere, must not reflect");
            h.assertTrue(crossing.getOwner() == p && crossing.getDeltaMovement().z > 0,
                    "A true sphere crossing must still reflect during the same tick");
        } finally { cleanup(p, owner, corner, crossing); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.safety_iron_rain")
    public static void ironRainHitsTheLowCeilingInsteadOfAnEnemyBeyondIt(GameTestHelper h) {
        var p = player(h, "five_fragments", "iron_rain");
        var target = enemy(h, p.position().add(0, 0, 14)); aim(p, target.getBoundingBox().getCenter());
        BlockPos ceiling = p.blockPosition().above(3);
        try {
            for (int x = -4; x <= 4; x++) for (int z = 0; z <= 12; z++)
                h.getLevel().setBlock(ceiling.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(TelekinesisSkillService.activate(p, "telekinesis_kinetic_volley", true),
                    "Modifier volley with Iron Rain must queue its arcing barrage");
            tick(p, 110);
            h.assertTrue(target.getHealth() == 100, "The rising arc must strike the ceiling before reaching the distant enemy");
            h.assertTrue(TelekineticArsenalService.flyingCount(p) == 0 && TelekineticArsenalService.orbitCount(p) == 0,
                    "Ceiling impacts must consume every launched fragment");
            for (int x = -4; x <= 4; x++) for (int z = 0; z <= 12; z++)
                h.assertTrue(h.getLevel().getBlockState(ceiling.offset(x, 0, z)).is(Blocks.STONE),
                        "Iron Rain must not mutate the ceiling it hits");
        } finally {
            for (int x = -4; x <= 4; x++) for (int z = 0; z <= 12; z++)
                h.getLevel().setBlock(ceiling.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
            cleanup(p, target);
        }
        h.succeed();
    }
}
