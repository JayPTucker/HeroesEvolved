package com.jayptucker.heroesevolved.ability.service;

import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One authoritative gate shared by packets, sustained powers and rewind transactions. */
public final class PowerRules {
    private static final Set<UUID> REWINDING = new HashSet<>();
    private PowerRules() {}
    public static boolean immobilized(ServerPlayer player) {
        return com.jayptucker.heroesevolved.expansion.ExpansionSkills.preserved(player) || CryoService.isFrozen(player) || TimeStopService.isStopped(player) || REWINDING.contains(player.getUUID());
    }
    public static boolean canAct(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !immobilized(player)
                && !EclipseService.arePowersSuppressed(player) && !PowerIntroService.isActive(player)
                && !TimeIntroService.isVisiting(player) && !TimeIntroService.isCity(player.level());
    }
    public static void rewinding(ServerPlayer player, boolean active) {
        if (active) REWINDING.add(player.getUUID()); else REWINDING.remove(player.getUUID());
    }
    public static void clear() { REWINDING.clear(); }
}
