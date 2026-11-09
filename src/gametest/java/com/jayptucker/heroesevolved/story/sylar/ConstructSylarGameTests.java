package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ConstructSylarGameTests {
    private static SylarEntity hunter(GameTestHelper h) {
        var s = new SylarEntity(ModEntities.SYLAR.get(), h.getLevel());
        s.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 3, 5))));
        s.setNoAi(true); s.setNoGravity(true);
        return s;
    }

    private static SylarPowers.Move move(String id) {
        return SylarPowers.MOVES.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    private static LivingEntity target(GameTestHelper h, Vec3 at) {
        var t = EntityType.COW.create(h.getLevel());
        t.setNoAi(true); t.setNoGravity(true); t.setPos(at);
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); t.setHealth(100);
        h.getLevel().addFreshEntity(t); return t;
    }

    @GameTest(template = "rewind_empty", batch = "sylar.construct_unlocks")
    public static void stolenConstructPowersRequireTheirActualMemories(GameTestHelper h) {
        var base = SylarPowers.available(Set.of());
        h.assertTrue(base.stream().noneMatch(m -> m.power().equals(ModAbilities.TECHNOPATHY_ID)
                || m.power().equals(ModAbilities.MATTER_ASSEMBLY_ID)), "Sylar cannot use gifts he has never stolen");
        var learned = SylarPowers.available(Set.of(ModAbilities.TECHNOPATHY_ID, ModAbilities.MATTER_ASSEMBLY_ID));
        h.assertTrue(learned.size() == base.size() + 2 && learned.stream().filter(m -> !base.contains(m))
                .allMatch(m -> m.windup() >= 24 && m.cooldown() >= 200), "Both stolen gifts must have readable windups and recovery");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "sylar.construct_intercept")
    public static void stolenTechnopathyReturnsMovingArrowsToTheirShooter(GameTestHelper h) {
        var s = hunter(h); var t = target(h, s.position().add(0, 0, 10));
        var shot = new Arrow(EntityType.ARROW, h.getLevel());
        shot.setOwner(t); shot.setPos(s.position().add(0, 1, 4)); shot.setDeltaMovement(0, 0, -1);
        h.getLevel().addFreshEntity(shot);
        try {
            move("stolen_trajectory").execute().run(s, h.getLevel(), t, 1);
            h.assertTrue(shot.getOwner() == s && shot.getDeltaMovement().z > 0,
                    "A moving shot in the visible field must turn toward its original shooter");
            h.succeed();
        } finally { shot.discard(); t.discard(); s.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "sylar.construct_dodge")
    public static void assembledCrossfireCanBeDodgedAndSolidCoverStopsIt(GameTestHelper h) {
        var s = hunter(h); var t = target(h, s.position().add(0, 0, 8));
        var attack = move("assembled_crossfire");
        try {
            attack.telegraph().run(s, h.getLevel(), t, 1);
            t.setPos(t.position().add(5, 0, 0));
            attack.execute().run(s, h.getLevel(), t, 1);
            h.assertTrue(t.getHealth() == 100, "Leaving the fixed mark must avoid the shards");
            t.setPos(s.position().add(0, 0, 8));
            attack.telegraph().run(s, h.getLevel(), t, 1);
            for (int x = -2; x <= 2; x++) for (int y = 0; y <= 4; y++)
                h.getLevel().setBlock(s.blockPosition().offset(x, y, 4), Blocks.STONE.defaultBlockState(), 3);
            attack.execute().run(s, h.getLevel(), t, 1);
            h.assertTrue(t.getHealth() == 100, "A solid wall must stop the stolen construct attack");
            h.succeed();
        } finally { t.discard(); s.discard(); }
    }
}
