package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.dampener.AbilityDampenerBlockEntity;
import com.jayptucker.heroesevolved.dampener.DampenerGeometry;
import com.jayptucker.heroesevolved.dampener.Dampening;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.grid.TestMachines;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.skills.SkillActionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.weapons.Jamming;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Electrokinesis's machine nodes, taken over from the retired Technopathy ({@link ElectroMachines}): Remote Hand works
 * real devices through every protection a hand passes and never closes a door on a body; Kill Switch kills a machine and a
 * Dampener's field; Dead Trigger jams a gun so TaCZ won't fire it; Return to Sender turns a bullet back at its shooter;
 * Counterweight hauls you to metal with no fall damage; Winchline reels a caught body until cover snaps the line.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ElectroMachinesGameTests {
    private static long lastTick;

    private static ServerPlayer electro(GameTestHelper h, String... nodes) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "machines-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            // Poses and pieces go out as packets; a fake player needs somewhere to send them. No spawn grace either.
            Connection connection = p.connection.getConnection();
            java.lang.reflect.Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            java.lang.reflect.Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.ELECTROKINESIS_ID).unlock(ModAbilities.ELECTROKINESIS_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.ELECTROKINESIS_ID.toString(),
                new SkillTreeData.Build(15, Set.of(nodes), Map.of())));
        return p;
    }

    private static void done(ServerPlayer p) {
        ElectroMachines.forget(p); ElectroService.forget(p); SkillActionService.forget(p);
        p.discard();
    }

    private static BlockHitResult at(GameTestHelper h, BlockPos relative) {
        BlockPos pos = h.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static void door(GameTestHelper h, BlockPos lower) {
        BlockState state = Blocks.OAK_DOOR.defaultBlockState();
        h.setBlock(lower, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(lower.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    @GameTest(template = "rewind_empty", batch = "electro.machines.hand")
    public static void remoteHandWorksRealDevicesAndNeverClosesADoorOnABody(GameTestHelper h) {
        ServerPlayer p = electro(h, ElectroMachines.REMOTE_HAND), plain = electro(h);
        BlockPos lever = new BlockPos(3, 2, 8), doorway = new BlockPos(6, 2, 6);
        h.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        door(h, doorway);
        try {
            h.assertFalse(ElectroMachines.struck(plain, at(h, lever), false) || h.getBlockState(lever).getValue(LeverBlock.POWERED),
                    "Without Remote Hand a bolt only charges what it hits");
            h.assertTrue(ElectroMachines.struck(p, at(h, lever), false) && h.getBlockState(lever).getValue(LeverBlock.POWERED),
                    "A tap at a lever throws it");
            h.assertTrue(ElectroMachines.struck(p, at(h, lever), true) && !h.getBlockState(lever).getValue(LeverBlock.POWERED),
                    "Shift + tap throws it back");
            h.assertTrue(ElectroMachines.struck(p, at(h, doorway.above()), false) && h.getBlockState(doorway).getValue(DoorBlock.OPEN),
                    "A tap at either half of a door opens it");
            Pig pig = h.spawn(EntityType.PIG, doorway);
            h.assertFalse(ElectroMachines.struck(p, at(h, doorway), true), "It never closes a door on anyone standing in it");
            h.assertTrue(h.getBlockState(doorway).getValue(DoorBlock.OPEN), "The door stays open over the pig");
            pig.discard();
            h.assertTrue(ElectroMachines.struck(p, at(h, doorway), true) && !h.getBlockState(doorway).getValue(DoorBlock.OPEN),
                    "With the doorway clear, Shift + tap closes it");
        } finally { done(p); done(plain); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "electro.machines.network")
    public static void remoteHandThroughAConductorWorksEveryControlItTouches(GameTestHelper h) {
        ServerPlayer p = electro(h, ElectroMachines.REMOTE_HAND);
        // A copper line along the floor with a door at its far end and a lever beside it.
        for (int z = 4; z <= 8; z++) h.setBlock(new BlockPos(5, 1, z), Blocks.COPPER_BLOCK);
        BlockPos doorway = new BlockPos(5, 2, 8), lever = new BlockPos(6, 1, 6);
        door(h, doorway);
        h.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.EAST));
        try {
            h.assertTrue(ElectroMachines.struck(p, at(h, new BlockPos(5, 1, 4)), false), "Current runs through the copper");
            h.assertTrue(h.getBlockState(doorway).getValue(DoorBlock.OPEN) && h.getBlockState(lever).getValue(LeverBlock.POWERED),
                    "Every control the copper touches comes on: the door opens, the lever is thrown");
            h.assertTrue(ElectroMachines.struck(p, at(h, new BlockPos(5, 1, 4)), true)
                    && !h.getBlockState(doorway).getValue(DoorBlock.OPEN) && !h.getBlockState(lever).getValue(LeverBlock.POWERED),
                    "Shift + tap sends OFF / CLOSED down the same line");
        } finally { done(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "electro.machines.kill", timeoutTicks = 200)
    public static void killSwitchShutsAMachineDownAndItWakesAsItWas(GameTestHelper h) {
        ServerPlayer p = electro(h, ElectroMachines.KILL_SWITCH);
        BlockPos at = new BlockPos(6, 2, 3);
        h.setBlock(at, Blocks.BARREL);
        TestMachines.consumer(h.getBlockEntity(at), 10_000, 0);
        BlockPos abs = h.absolutePos(at);
        h.assertFalse(GridDisruption.stalled(h.getLevel(), abs), "(it runs)");
        h.assertTrue(ElectroMachines.struck(p, at(h, at), true) && GridDisruption.stalled(h.getLevel(), abs),
                "Shift + tap at a powered machine kills it");
        h.runAfterDelay(ElectroMachines.KILL_TICKS + 4, () -> {
            h.assertFalse(GridDisruption.stalled(h.getLevel(), abs), "Five seconds later it wakes on its own");
            done(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.machines.dampener", timeoutTicks = 200)
    public static void killSwitchDropsADampenersField(GameTestHelper h) {
        BlockPos pylon = new BlockPos(1, 2, 6);
        h.setBlock(pylon, ModBlocks.ABILITY_DAMPENER.get());
        AbilityDampenerBlockEntity field = (AbilityDampenerBlockEntity) h.getBlockEntity(pylon);
        field.fill(DampenerGeometry.CAPACITY);
        // The electrokinetic stands outside the field; a bystander stands inside it.
        ServerPlayer p = electro(h, ElectroMachines.KILL_SWITCH);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(pylon.offset(field.radius() + 4, 0, 0))));
        ServerPlayer inside = electro(h);
        inside.setPos(Vec3.atBottomCenterOf(h.absolutePos(pylon.offset(1, 0, 0))));
        h.runAfterDelay(DampenerGeometry.SPIN_UP + 3, () -> {
            h.assertTrue(field.live() && Dampening.dampened(inside), "(the field is up)");
            h.assertTrue(ElectroMachines.struck(p, at(h, pylon), true), "Kill Switch reaches the projector from outside its field");
            h.runAfterDelay(3, () -> {
                h.assertFalse(field.live(), "A dead projector holds no field");
                h.assertFalse(Dampening.dampened(inside), "Powers answer inside it again");
                done(p); done(inside);
                h.succeed();
            });
        });
    }

    /** TaCZ times shots in real milliseconds; hold each tick to 50 ms, as in play. */
    private static void realTime(GameTestHelper h) {
        h.onEachTick(() -> {
            long wait = 50 - (System.currentTimeMillis() - lastTick);
            if (wait > 0 && wait <= 50) try { Thread.sleep(wait); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            lastTick = System.currentTimeMillis();
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.machines.jam", timeoutTicks = 240)
    public static void deadTriggerJamsAGunAndAJammedGunWillNotFire(GameTestHelper h) {
        ServerPlayer p = electro(h, ElectroMachines.DEAD_TRIGGER), plain = electro(h);
        // A shock on a hostile holding a gun jams it; one without the node doesn't.
        Zombie armed = h.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5)), other = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 5));
        armed.setItemSlot(EquipmentSlot.MAINHAND, PowerWeapons.gun(PowerWeapons.AGENT_PISTOL));
        other.setItemSlot(EquipmentSlot.MAINHAND, PowerWeapons.gun(PowerWeapons.AGENT_PISTOL));
        ElectroService.shock(p, armed, 1);
        ElectroService.shock(plain, other, 1);
        h.assertTrue(Jamming.jammed(armed), "Dead Trigger: the shock fries the gun in its hand");
        h.assertFalse(Jamming.jammed(other), "Without the node a shock leaves the gun alone");
        Zombie unarmed = h.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 1));
        ElectroService.shock(p, unarmed, 1);
        h.assertFalse(Jamming.jammed(unarmed), "No gun, nothing to jam");
        done(p); done(plain);
        // A jammed shooter's TaCZ gun won't go off; once the jam wears off, it does.
        realTime(h);
        ArmorStand stand = h.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 2, 2));
        stand.setItemSlot(EquipmentSlot.MAINHAND, PowerWeapons.gun(PowerWeapons.AGENT_PISTOL));
        PowerWeapons.draw(stand);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(2, 2, 7));
        Jamming.jam(stand, 1000, null);
        java.util.Map<String, Integer> results = new java.util.TreeMap<>();
        boolean[] cleared = {false};
        h.onEachTick(() -> {
            if (stand.tickCount <= 12 || stand.tickCount % 5 != 0) return;
            if (PowerWeapons.rounds(stand.getMainHandItem()) <= 1) PowerWeapons.reload(stand.getMainHandItem());
            results.merge(PowerWeapons.fire(stand, pig.getBoundingBox().getCenter()), 1, Integer::sum);
        });
        h.runAfterDelay(70, () -> {
            h.assertTrue(pig.getHealth() == pig.getMaxHealth() && !results.containsKey("SUCCESS"),
                    "While jammed, not one shot goes off " + results);
            stand.removeEffect(ModItems.JAMMED);
            cleared[0] = true;
        });
        h.succeedWhen(() -> h.assertTrue(cleared[0] && pig.getHealth() < pig.getMaxHealth(), "Unjammed, it fires again " + results));
    }

    @GameTest(template = "rewind_empty", batch = "electro.machines.return", timeoutTicks = 60)
    public static void returnToSenderTurnsABulletBackAtItsShooter(GameTestHelper h) {
        // The arena is twelve blocks deep (with a barrier round it): she stands at one end, the shooter at the other.
        ServerPlayer p = electro(h, ElectroMachines.RETURN_TO_SENDER);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 2, 1))));
        Zombie shooter = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 11));
        shooter.setNoAi(true);
        try {
            h.assertTrue(SkillActionService.activate(new AbilityUseContext(p, ModAbilities.ELECTROKINESIS_ID, 5, false), 1),
                    "Static Screen goes up");
            Entity bullet = PowerWeapons.testBullet(h.getLevel(), 0);
            h.assertTrue(bullet instanceof Projectile, "TaCZ can make a bullet");
            Projectile shot = (Projectile) bullet;
            // A round crossing four blocks a tick, from outside the screen to well inside it, in one step.
            Vec3 start = p.getEyePosition().add(0, 0, 9.5), end = start.add(0, 0, -4);
            shot.setOwner(p);
            shot.setPos(start); shot.setDeltaMovement(0, 0, -4);
            h.assertFalse(ElectroMachines.screenCatch(h.getLevel(), shot, start, end), "Her own shots pass through her screen");
            shot.setOwner(shooter);
            h.assertFalse(ElectroMachines.screenCatch(h.getLevel(), shot, end, start), "A shot going away is left alone");
            h.assertTrue(ElectroMachines.screenCatch(h.getLevel(), shot, start, end), "The screen catches the round on its swept step");
            h.assertTrue(shot.getOwner() == p, "The returned round is hers");
            h.assertTrue(shot.getDeltaMovement().z > 3 && shot.position().distanceTo(p.getEyePosition()) < 7.6,
                    "It goes back from the screen's edge, at full speed, toward the one who fired it");
            h.assertFalse(ElectroMachines.screenCatch(h.getLevel(), shot, shot.position(), shot.position().add(0, 0, -4)),
                    "A round is turned once, not caught again");
        } finally { done(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "electro.machines.haul", timeoutTicks = 60)
    public static void counterweightHaulsYouToMetalAndYouLandUnhurt(GameTestHelper h) {
        ServerPlayer p = electro(h, ElectroMachines.COUNTERWEIGHT), plain = electro(h);
        BlockPos iron = new BlockPos(3, 6, 12), stone = new BlockPos(1, 2, 12);
        h.setBlock(iron, Blocks.IRON_BLOCK);
        h.setBlock(stone, Blocks.STONE);
        try {
            look(p, Vec3.atCenterOf(h.absolutePos(stone)));
            h.assertFalse(ElectroMachines.counterweight(p), "Stone isn't magnetic: Shift + Ride blinks as usual");
            look(plain, Vec3.atCenterOf(h.absolutePos(iron)));
            h.assertFalse(ElectroMachines.counterweight(plain), "Without the node, it blinks as usual");
            look(p, Vec3.atCenterOf(h.absolutePos(iron)));
            h.assertTrue(ElectroMachines.counterweight(p) && ElectroMachines.hauling(p), "Aimed at iron, the line takes hold");
            ElectroMachines.tick(h.getLevel().getServer());
            Vec3 toward = Vec3.atCenterOf(h.absolutePos(iron)).subtract(p.getBoundingBox().getCenter()).normalize();
            h.assertTrue(p.getDeltaMovement().normalize().dot(toward) > .95 && p.getDeltaMovement().length() > 1,
                    "It hauls her straight at the metal, fast");
            float health = p.getHealth(), plainHealth = plain.getHealth();
            p.causeFallDamage(20, 1, p.damageSources().fall());
            plain.causeFallDamage(20, 1, plain.damageSources().fall());
            h.assertTrue(plainHealth - plain.getHealth() > 5, "(the same drop hurts anyone else)");
            h.assertTrue(p.getHealth() == health, "She lands without fall damage");
        } finally { done(p); done(plain); }
        h.succeed();
    }

    private static void look(ServerPlayer p, Vec3 point) {
        Vec3 d = point.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setXRot((float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z))));
        p.setYHeadRot(p.getYRot());
    }

    @GameTest(template = "rewind_empty", batch = "electro.machines.reel", timeoutTicks = 80)
    public static void winchlineReelsACaughtBodyUntilCoverSnapsTheLine(GameTestHelper h) {
        ServerPlayer p = electro(h, ElectroMachines.WINCHLINE);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 10));
        Vec3 anchor = Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 2, 3))).add(0, .2, 0);
        double before = zombie.position().distanceTo(anchor);
        ElectroMachines.caught(p, zombie, List.of(anchor));
        h.assertTrue(ElectroMachines.reeling(zombie), "The wire that caught it reels it in");
        h.runAfterDelay(8, () -> {
            h.assertTrue(zombie.position().distanceTo(anchor) < before - 1, "It's dragged toward the anchor");
            // A wall between them snaps the line.
            Zombie second = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 12));
            for (int y = 2; y <= 4; y++) for (int x = 4; x <= 8; x++) h.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
            ElectroMachines.caught(p, second, List.of(Vec3.atCenterOf(h.absolutePos(new BlockPos(6, 2, 4)))));
            ElectroMachines.tick(h.getLevel().getServer());
            h.assertFalse(ElectroMachines.reeling(second), "Cover between the anchor and the body snaps the line");
            done(p);
            h.succeed();
        });
    }
}
