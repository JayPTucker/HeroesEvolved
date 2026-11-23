package com.jayptucker.heroesevolved.travel;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** People on the road are real, ticking villagers whichever body they have: with Minecraft Comes Alive, its humans. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TravellerBodiesGameTests {
    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "traveller.bodies")
    public static void aHealerOnTheRoadIsInTheWorldWithTheirGift(GameTestHelper h) {
        BlockPos bell = h.absolutePos(new BlockPos(6, 2, 6));
        Healer party = Healer.start(h.getLevel(), Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))), bell);
        h.assertTrue(party != null, "The healer sets out");
        Villager healer = party.healer();
        h.runAfterDelay(10, () -> {
            h.assertTrue(h.getLevel().getEntity(healer.getUUID()) == healer && !healer.isRemoved(), "The party holds the body in the world");
            h.assertTrue(GiftedMobs.isGifted(healer), "Their gift is on them");
            h.assertTrue(ModAbilities.REGENERATION_ID.equals(GiftedMobs.of(healer).gift().power()), "It is the healing gift");
            boolean human = "mca".equals(EntityType.getKey(healer.getType()).getNamespace());
            h.assertTrue(human == ModList.get().isLoaded("mca"), "They are MCA's human exactly when MCA is here");
            Travellers.release(healer, true);
            h.succeed();
        });
    }
}
