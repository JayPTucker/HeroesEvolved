package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;

/** Which drawing each set piece uses. */
final class PieceArt {
    private PieceArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case FLIGHT_RELEASE, FLIGHT_SKYBURST, FLIGHT_IMPACT, FLIGHT_RESCUE, FLIGHT_SHELTER,
                    FLIGHT_CURRENT, FLIGHT_THERMAL_MARK, FLIGHT_THERMAL_RISE, FLIGHT_INTERCEPT,
                    TEMPEST_DRILL, GALE_WALL, FORMATION_FLIGHT -> FlightArt.draw(c, a, t);
            case PYRO_FORGE, PYRO_SPEAR, PYRO_BREATH, PYRO_FIRESTORM_PRIME, PYRO_FIRESTORM,
                    PYRO_COLLAPSE, PYRO_BURST, PYRO_SUN_CHARGE, PYRO_SUN_DROP, PYRO_LASH, PYRO_MANTLE,
                    PYRO_CAUTERIZE, PYRO_MINE_ARM, PYRO_MINE_WARN, PYRO_LANCE, PYRO_SHEPHERD, PYRO_TAKEOFF, PYRO_PHOENIX, PYRO_WAKE,
                    SUNFALL, EMBER_SHROUD, HEARTHFIRE -> PyroArt.draw(c, a, t);
            case ELECTRO_DRAW, ELECTRO_ARC, ELECTRO_CURRENT, ELECTRO_ORB, ELECTRO_PRIME, ELECTRO_WARN,
                    ELECTRO_STRIKE, ELECTRO_PULSE, ELECTRO_RIDE, ELECTRO_SCAR, ELECTRO_COIL, ELECTRO_SIPHON,
                    ELECTRO_BLACKOUT, ELECTRO_AURA, ELECTRO_FIELD, ELECTRO_SCREEN,
                    TESLA_SPIRE, TESLA_ZAP, FARADAY_CAGE, JUMPSTART -> ElectroArt.draw(c, a, t);
            case GROUND_ZERO -> ElementArt.groundZero(c, a, t);
            case CONTAINMENT_DOME -> ElementArt.containment(c, a, t);
            case REACTOR_HEARTH -> ElementArt.reactor(c, a, t);
            case HOUR_HAND -> MotionArt.hourHand(c, a, t);
            case TIME_LOOP -> MotionArt.timeLoop(c, a, t);
            case BORROWED_TIME -> MotionArt.borrowedTime(c, a, t);
            case HAILSTORM -> ElementArt.hailstorm(c, a, t);
            case CRYSTAL_BASTION -> ElementArt.bastion(c, a, t);
            case AURORA_VEIL -> ElementArt.aurora(c, a, t);
            case FISSURE_MAW -> ElementArt.fissure(c, a, t);
            case STONE_RAMPART -> ElementArt.rampart(c, a, t);
            case BEDROCK_STANCE -> ElementArt.bedrock(c, a, t);
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
            case SPEED_ARMS -> SpeedArt.arms(c, a, t);
            case RAD_HEAT, RAD_LEAP, RAD_SLAM, RAD_ZONE, RAD_EMP, RAD_BEAM, RAD_CHARGE, RAD_LANCE -> RadiationArt.draw(c, a, t);
            case FORCE_SHIELD, FORCE_PUSH, FORCE_FORM, FORCE_BRIDGE, FORCE_WALL, FORCE_BUBBLE, FORCE_CRUSH, FORCE_BEAD,
                    FORCE_NOVA_CHARGE, FORCE_NOVA, FORCE_DISC, FORCE_VANISH, FORCE_NET, FORCE_SHATTER, FORCE_RIPPLE -> ForceArt.draw(c, a, t);
            case OVERRUN_DEPART -> SpeedArt.depart(c, a, t);
            case OVERRUN_ARRIVE -> SpeedArt.arrive(c, a, t);
            case MATTER_RISE -> AssemblyArt.rise(c, a, t);
            case SENTINEL -> AssemblyArt.sentinel(c, a, t);
        }
    }
}
