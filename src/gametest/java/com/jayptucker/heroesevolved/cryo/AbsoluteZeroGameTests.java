package com.jayptucker.heroesevolved.cryo;

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
import net.minecraft.world.effect.MobEffects;
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

/** Cryokinesis's signature: the warmth drawn out for the channel, then every drained foe frozen and the heat returned. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class AbsoluteZeroGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "absolute-zero"));
        try {
            Connection connection = p.connection.getConnection();
            var channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 40, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.CRYOKINESIS_ID).unlock(ModAbilities.CRYOKINESIS_ID));
        PlayerProgressionService.setLevel(p, 4);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.CRYOKINESIS_ID.toString(),
                new SkillTreeData.Build(5, Set.of("cryokinesis_absolute_zero"), Map.of(), 3)));
        return p;
    }

    private static Mob foe(GameTestHelper h, Vec3 at) {
        Mob mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired(); mob.setPos(at);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = "rewind_empty", batch = "cryo.absolute_zero", timeoutTicks = 80)
    public static void theWarmthLeavesThenEveryDrainedFoeFreezesAndTheHeatComesHome(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob near = foe(h, p.position().add(0, 0, 4)), far = foe(h, p.position().add(3, 0, 5));
        h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, "cryokinesis_absolute_zero"), "The signature equips");
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS, "It casts");
        h.assertTrue(AbsoluteZero.drawing(p) && near.getHealth() == 100, "The channel hurts nobody yet");
        float spent = PlayerEnergyService.getEnergy(p);
        h.runAfterDelay(4, () -> {
            try {
                h.assertTrue(near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && far.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "Drained foes slow to a crawl while the warmth leaves");
            } catch (RuntimeException e) { AbsoluteZero.forget(p); near.discard(); far.discard(); throw e; }
        });
        h.runAfterDelay(AbsoluteZero.CHANNEL + 4, () -> {
            try {
                h.assertTrue(!AbsoluteZero.drawing(p), "The channel ends by itself");
                h.assertTrue(CryoService.isFrozen(near) && CryoService.isFrozen(far), "Every drained foe freezes solid");
                h.assertTrue(near.getHealth() < 100 && far.getHealth() < 100, "And takes the cold");
                h.assertTrue(PlayerEnergyService.getEnergy(p) >= spent + 7, "The heat comes home as stamina: " + spent + " -> " + PlayerEnergyService.getEnergy(p));
                h.succeed();
            } finally {
                AbsoluteZero.forget(p);
                CryoService.releaseEntity(near); CryoService.releaseEntity(far);
                near.discard(); far.discard();
            }
        });
    }
}
