package com.jayptucker.heroesevolved.time.intro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Odessa is a memory of one evening, not a place monsters live: none spawn there on their own. Its dimension spawns
 * monsters at light level 0, so without this every sealed dark room in the city (and the space behind each building's
 * rooms) would breed zombies during the Last Day and the vision. Spawn eggs, spawners and story code still work.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class CityMonsters {
    private CityMonsters() {}

    @SubscribeEvent
    public static void spawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof Enemy)) return;
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) return;
        Level level = event.getEntity().level();
        if (level.dimension() == TimeIntroService.DIMENSION) event.setSpawnCancelled(true);
    }
}
