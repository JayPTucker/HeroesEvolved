package com.jayptucker.heroesevolved.compat.jade;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.dampener.AbilityDampenerBlockEntity;
import com.jayptucker.heroesevolved.formula.CentrifugeBlockEntity;
import com.jayptucker.heroesevolved.formula.SynthesisStationBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Heroes Evolved in Jade, the "what am I looking at" overlay. Jade is optional: it finds this class by its annotation,
 * and only this package imports Jade, so the mod loads the same without it (see docs/agents/compat.md).
 *
 * <p>Both sides: the Ability Dampener's field (radius, upkeep, on or off) and the lab machines' progress. The client
 * part ({@link JadeClient}) hides technical entities and keeps disguises: whatever Jade names is what the eye sees.
 * Every step is guarded, so a change in Jade's API turns a feature off with one log line instead of a crash.
 */
@WailaPlugin(HeroesEvolved.MOD_ID)
public final class HeroesJadePlugin implements IWailaPlugin {
    static final Logger LOG = LogUtils.getLogger();
    private static boolean broken;

    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    @Override public void register(IWailaCommonRegistration registration) {
        guard("dampener data", () -> registration.registerBlockDataProvider(JadeProviders.Dampener.INSTANCE, AbilityDampenerBlockEntity.class));
        guard("lab progress", () -> {
            registration.registerProgress(JadeProviders.LabProgress.INSTANCE, CentrifugeBlockEntity.class);
            registration.registerProgress(JadeProviders.LabProgress.INSTANCE, SynthesisStationBlockEntity.class);
        });
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        guard("client", () -> JadeClient.register(registration));
    }

    /** Runs one registration step; a throw (an API Jade changed) is logged once and that step is skipped. */
    static void guard(String step, Runnable run) {
        try {
            run.run();
        } catch (Throwable t) {
            fail(step, t);
        }
    }

    /** Logs a failure the first time; afterwards Jade simply shows its own tooltip. */
    static void fail(String what, Throwable t) {
        if (!broken) LOG.warn("Heroes Evolved's Jade support stopped at {}: {}", what, t.toString());
        broken = true;
    }
}
