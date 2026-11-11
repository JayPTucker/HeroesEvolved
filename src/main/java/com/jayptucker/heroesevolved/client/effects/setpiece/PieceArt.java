package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;

/** Which drawing each set piece uses. */
final class PieceArt {
    private PieceArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case GROUND_ZERO -> ElementArt.groundZero(c, a, t);
            case CONTAINMENT_DOME -> ElementArt.containment(c, a, t);
            case REACTOR_HEARTH -> ElementArt.reactor(c, a, t);
            case HOUR_HAND -> MotionArt.hourHand(c, a, t);
            case TIME_LOOP -> MotionArt.timeLoop(c, a, t);
            case BORROWED_TIME -> MotionArt.borrowedTime(c, a, t);
            case SUNFALL -> ElementArt.sunfall(c, a, t);
            case EMBER_SHROUD -> ElementArt.emberShroud(c, a, t);
            case HEARTHFIRE -> ElementArt.hearthfire(c, a, t);
            case HAILSTORM -> ElementArt.hailstorm(c, a, t);
            case CRYSTAL_BASTION -> ElementArt.bastion(c, a, t);
            case AURORA_VEIL -> ElementArt.aurora(c, a, t);
            case TESLA_SPIRE -> ElementArt.tesla(c, a, t);
            case TESLA_ZAP -> ElementArt.teslaZap(c, a, t);
            case FARADAY_CAGE -> ElementArt.faraday(c, a, t);
            case JUMPSTART -> ElementArt.jumpstart(c, a, t);
            case FISSURE_MAW -> ElementArt.fissure(c, a, t);
            case STONE_RAMPART -> ElementArt.rampart(c, a, t);
            case BEDROCK_STANCE -> ElementArt.bedrock(c, a, t);
            case TEMPEST_DRILL -> MotionArt.tempestDrill(c, a, t);
            case GALE_WALL -> MotionArt.galeWall(c, a, t);
            case FORMATION_FLIGHT -> MotionArt.formation(c, a, t);
            case IMPLOSION -> MotionArt.implosion(c, a, t);
            case DEBRIS_HALO -> MotionArt.debrisHalo(c, a, t);
            case GATHERING_HAND -> MotionArt.gathering(c, a, t);
            case PSYCHIC_STORM -> MindArt.psychicStorm(c, a, t);
            case BLIND_SPOT -> MindArt.blindSpot(c, a, t);
            case HIVE_MIND -> MindArt.hiveMind(c, a, t);
            case VITAL_BURST -> MindArt.vitalBurst(c, a, t);
            case UNDYING -> MindArt.undying(c, a, t);
            case LIFEBLOOM -> MindArt.lifebloom(c, a, t);
            case RESONANCE_CASCADE -> ElementArt.cascade(c, a, t);
            case SILENCE_DOME -> ElementArt.silence(c, a, t);
            case BATTLE_HYMN -> ElementArt.hymn(c, a, t);
            case LIGHTNING_FLURRY, RELAY_RUN -> MotionArt.flurry(c, a, t);
            case PHASE_BLUR -> MotionArt.phaseBlur(c, a, t);
            case FORETOLD_STRIKE -> MindArt.foretold(c, a, t);
            case BRANCHING_PATHS -> MindArt.branching(c, a, t);
            case PROPHECY_CANVAS -> MindArt.prophecy(c, a, t);
            case SHADOW_STRIKE -> MotionArt.shadowStrike(c, a, t);
            case REFRACTION_CLOAK -> MotionArt.refraction(c, a, t);
            case PRISM_HAZE -> MotionArt.prismHaze(c, a, t);
            case PHASE_ERUPTION -> MotionArt.eruption(c, a, t);
            case DENSE_BULWARK -> MotionArt.dense(c, a, t);
            case PHASE_FIELD -> MotionArt.phaseField(c, a, t);
            case PRISMATIC_OVERFLOW -> MindArt.overflow(c, a, t);
            case EMPATHIC_WARD -> MindArt.ward(c, a, t);
            case KINDRED_LIGHT -> MindArt.kindred(c, a, t);
            case DRONE_SWARM -> MindArt.drones(c, a, t);
            case DRONE_SHOT -> MindArt.droneShot(c, a, t);
            case FIREWALL -> MindArt.firewall(c, a, t);
            case OVERCLOCK -> MindArt.overclock(c, a, t);
            case FORGE_CANNON -> MindArt.cannon(c, a, t);
            case FORGE_SLUG -> MindArt.slug(c, a, t);
            case BULWARK_LATTICE -> MindArt.lattice(c, a, t);
            case MENDING_LATTICE -> MindArt.mending(c, a, t);
            case EXPLODING_MAN -> MiniBlast.draw(c, a.origin(), a.scale() / 80F, a.scale() * 1.15F, t, a.piece().ticks);
            case SPEED_GUST -> SpeedArt.gust(c, a, t);
            case OVERRUN_DEPART -> SpeedArt.depart(c, a, t);
            case OVERRUN_ARRIVE -> SpeedArt.arrive(c, a, t);
            case MATTER_RISE -> AssemblyArt.rise(c, a, t);
            case SENTINEL -> AssemblyArt.sentinel(c, a, t);
        }
    }
}
