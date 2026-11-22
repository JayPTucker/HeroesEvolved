package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** While a mind is linked to another body, its own power menus don't answer. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PossessionLockGameTests {
    @GameTest(template = "rewind_empty", batch = "possession.lock")
    public static void aLinkedMindCannotChangeItsMimicrySets(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "linked-mind"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.MIMICRY_ID).unlock(ModAbilities.MIMICRY_ID));
        PlayerProgressionService.setLevel(p, 15);
        var body = EntityType.PILLAGER.create(h.getLevel());
        body.setNoAi(true);
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 6)));
        body.moveTo(at.x, at.y, at.z, 180, 0);
        h.getLevel().addFreshEntity(body);
        try {
            h.assertTrue(MimicryService.remember(p, ModAbilities.PYROKINESIS_ID, 3), "A first set is kept");
            h.assertTrue(MimicryService.remember(p, ModAbilities.CRYOKINESIS_ID, 3), "A second set is kept");
            h.assertTrue(PossessionService.start(p, body, 3, true), "The mind links to the body");
            h.assertTrue(PossessionService.locked(p), "Linked, the mind is locked");
            h.assertTrue(PossessionService.actionActor(p) == null, "So a skid or any other input from it does nothing");
            h.assertTrue(!MimicryService.choose(p, 0) && !MimicryService.choose(p, 1), "The wheel doesn't change sets");
            h.assertTrue(!MimicryService.forgetSlot(p, 0), "And can't forget one");
            PossessionService.end(p, "test over");
            h.assertTrue(MimicryService.choose(p, 0) || MimicryService.choose(p, 1), "Back in its own mind, the wheel works");
            h.assertTrue(MimicryService.forgetSlot(p, 0), "And so does forgetting");
            h.succeed();
        } finally {
            PossessionService.forget(p);
            body.discard();
        }
    }
}
