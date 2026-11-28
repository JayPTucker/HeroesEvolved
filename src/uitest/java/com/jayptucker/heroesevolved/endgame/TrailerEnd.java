package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.mixin.EndFightAccess;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The End for the trailer's finale ({@code trailer/StoryShots}), staged as {@link EndgamePreview} stages it. It lives
 * in this package, as that preview does, because the encounter's parts (the Seal's moves, the Inheritance) are
 * package-private. Never shipped.
 */
public final class TrailerEnd {
    private TrailerEnd() {}

    /** Into the End as a flyer at Power Level 20, a fresh Dragon over the island held still, standing on the island. */
    public static void stage(ServerPlayer p) {
        EndBattle.reset(p.getServer(), false);
        // Creative, as the End preview films: the Unmaking killed a survival fighter, and the death screen then covered
        // the next shot.
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        p.removeAllEffects();
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.FLIGHT_ID));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.FLIGHT_ID));
        PlayerProgressionService.setPowerLevel(p, 20);
        PlayerPowerSyncService.sync(p);
        ServerLevel l = p.getServer().getLevel(Level.END);
        EndScenery.hold(l);
        p.teleportTo(l, 17, 72, 21, 0, 0);
        var f = l.getDragonFight();
        var a = (EndFightAccess) f;
        a.heroes$needsScan(false);
        a.heroes$dragonKilled(false);
        for (Entity entity : java.util.stream.StreamSupport.stream(l.getAllEntities().spliterator(), false).toList())
            if (entity instanceof EnderDragon) entity.discard();
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(l);
        dragon.setPos(.5, 89, .5);
        dragon.setDragonFight(f);
        a.heroes$dragonUUID(dragon.getUUID());
        l.addFreshEntity(dragon);
        var e = EndBattle.begin(dragon, List.of(p));
        e.still(true);
        e.holdOff(10000);
        p.teleportTo(l, 17, e.seal.ground(17, 21), 21, 0, 0);
    }

    public static boolean ready(ServerPlayer p) {
        var e = EndBattle.current();
        return e != null && e.dragon != null && e.dragon.isAlive() && p.level().dimension() == Level.END;
    }

    /**
     * The last phase, grounded: the Unmaking is its finale, and cast in the first phase it barely showed (the preview
     * casts it after {@code phase(3)} with the Dragon grounded).
     */
    public static void finale() {
        var e = EndBattle.current();
        e.boss().setHealth(e.boss().getMaxHealth() * .25F);
        e.phaseChange();
        e.grounded = 400;
    }

    /** The Dragon set down where it is, turned toward the player, so a ground move starts from a settled body. */
    public static void land(ServerPlayer p) {
        var e = EndBattle.current();
        Vec3 at = e.dragon.position();
        float yaw = SealGeometry.yaw(p.position().subtract(at));
        e.seal.landAt(new Vec3(at.x, e.seal.ground(at.x, at.z), at.z), yaw);
        e.transition = 0;
    }

    /** A move of the Living Seal's: an air move from high over the island, a ground move where it stands. */
    public static void move(SealGeometry.Move move) {
        var e = EndBattle.current();
        if (move.air) e.seal.liftTo(new Vec3(.5, 89, .5));
        e.transition = 0;
        e.seal.crash = 0;
        e.seal.stagger = 0;
        e.seal.slump = 0;
        e.seal.chain.clear();
        e.seal.start(move);
    }

    /** The false victory: the Dragon's light drawn out of it into Sylar's brass, the Inheritance beginning. */
    public static void inherit() {
        var e = EndBattle.current();
        e.startAbsorption(e.dragon.position());
        e.dragon.discard();
    }

    /** Where the Dragon's chest is now (for a camera), or the island's middle if it is gone. */
    public static Vec3 chest() {
        var e = EndBattle.current();
        return e == null || e.dragon == null || !e.dragon.isAlive() ? new Vec3(.5, 80, .5) : SealGeometry.chest(e.dragon);
    }

    public static void leave(ServerPlayer p) {
        EndBattle.reset(p.getServer(), false);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    }
}
