package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Regeneration's signature: foes drawn onto her, every blow banked at half strength, then let go as a heartbeat. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class LastOneStandingGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "last-one-standing"));
        try {
            Connection connection = p.connection.getConnection();
            var channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 40, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.REGENERATION_ID).unlock(ModAbilities.REGENERATION_ID));
        PlayerProgressionService.setLevel(p, 4);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.REGENERATION_ID.toString(),
                new SkillTreeData.Build(5, Set.of("regeneration_last_one_standing"), Map.of(), 3)));
        return p;
    }

    private static Mob foe(GameTestHelper h, Vec3 at) {
        Mob mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired(); mob.setPos(at);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = "rewind_empty", batch = "regen.last_one_standing")
    public static void foesTurnOnHerBlowsAreBankedAndASecondPressLetsItGo(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob near = foe(h, p.position().add(0, 0, 3)), far = foe(h, p.position().add(0, 0, 20));
        try {
            h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, "regeneration_last_one_standing"), "The signature equips");
            PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS, "It casts");
            h.assertTrue(LastOneStanding.standing(p) && near.getTarget() == p, "A foe in reach turns on her");
            h.assertTrue(far.getTarget() != p, "One out of reach doesn't");
            h.assertTrue(Math.abs(LastOneStanding.braced(p, 10) - 5) < 1e-4 && Math.abs(LastOneStanding.banked(p) - 10) < 1e-4,
                    "A blow lands at half strength and is banked in full");
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                    "A second press lets it go early");
            h.assertTrue(!LastOneStanding.standing(p) && PlayerEnergyService.getEnergy(p) == energy, "For free, and it ends the stand");
            float expected = 4 + 4 + Math.min(LastOneStanding.BANKED_CAP, 10 * LastOneStanding.BANKED_SHARE);
            h.assertTrue(Math.abs(near.getHealth() - (100 - expected)) < .01, "The near foe takes 4 + tier and half the bank: " + near.getHealth());
            h.assertTrue(far.getHealth() == 100, "The release reaches 8 blocks, no farther");
            h.assertTrue(RegenerationService.regrowth(p) >= 10, "What she banked became Regrowth");
            h.succeed();
        } finally {
            LastOneStanding.forget(p);
            near.discard(); far.discard();
        }
    }
}
