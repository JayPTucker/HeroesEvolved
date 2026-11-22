package com.jayptucker.heroesevolved.compat.mca;

import net.conczin.mca.server.SpawnQueue;
import net.minecraft.world.entity.npc.Villager;

/** MCA's own classes, for tests; only ever reached when MCA is loaded. */
final class McaTestCalls {
    private McaTestCalls() {}

    /** What MCA's {@code /mca-admin convertVanillaVillagers} does to a villager already in the world. */
    static void convert(Villager villager) {
        SpawnQueue.getInstance().convert(villager);
    }
}
