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
                    TESLA_SPIRE, TESLA_ZAP, FARADAY_CAGE, JUMPSTART, ELECTRO_LANCE, ELECTRO_THREAD,
                    ELECTRO_OVERCHARGE, ELECTRO_SINK, ELECTRO_SINK_BURST, ELECTRO_ANCHOR, ELECTRO_WIRE -> ElectroArt.draw(c, a, t);
            case MIMIC_ATTUNE, MIMIC_IMPRINT, MIMIC_SHIFT -> MimicArt.draw(c, a, t);
            case SONIC_CRY, SONIC_SCREAM, SONIC_NOTE, SONIC_NOTE_BURST, SONIC_WALL, SONIC_WALL_PUSH, SONIC_VAULT,
                    SONIC_DIVE, SONIC_LANDING, SONIC_GATHER, SONIC_CRESCENDO, SONIC_SHRIEK, SONIC_RING, SONIC_SHATTER,
                    SONIC_DISSONANCE, SONIC_ECHO, SONIC_GUARD, SONIC_QUIET,
                    RESONANCE_CASCADE, SILENCE_DOME, BATTLE_HYMN -> SonicArt.draw(c, a, t);
            case QUAKE_DUST, QUAKE_SHATTER, QUAKE_LANCE, QUAKE_SHELL, QUAKE_BRACE, QUAKE_DASH, QUAKE_SEAM, QUAKE_TRACE ->
                    QuakeArt.draw(c, a, t);
            case REGEN_STREAM, REGEN_FAN, REGEN_IMPACT, REGEN_PAYBACK, REGEN_FEIGN, REGEN_CPR -> RegenArt.draw(c, a, t);
            case TELE_COMMAND, TELE_LISTEN, TELE_VOICE, TELE_WEAVE, TELE_PERSUADE, TELE_WON, TELE_RECALL, TELE_MEMORY,
                    TELE_ECHO, TELE_LOST, TELE_FRIGHT, TELE_HIVE_LINK, TELE_HALO, TELE_PROJECT,
                    PSYCHIC_STORM, BLIND_SPOT, HIVE_MIND -> TelepathyArt.draw(c, a, t);
            case VIBE_GATHER, VIBE_WAVE, VIBE_CROSSWAVE, VIBE_HIT, VIBE_BREACH_SEED, VIBE_BREACH_OPEN,
                    VIBE_BREACH_ARRIVE, VIBE_BREACH_CLOSE, VIBE_TRANSIT, VIBE_TOUCH, VIBE_ECHO, VIBE_GUARD,
                    VIBE_GUARD_CLOSE, VIBE_LOCK_WARN, VIBE_LOCK, VIBE_AWAKEN -> VibeArt.draw(c, a, t);
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
            case VITAL_BURST -> MindArt.vitalBurst(c, a, t);
            case UNDYING -> MindArt.undying(c, a, t);
            case LIFEBLOOM -> MindArt.lifebloom(c, a, t);
            case LIGHTNING_FLURRY -> MotionArt.flurry(c, a, t);
            case RELAY_RUN -> MotionArt.relay(c, a, t);
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
            case SPEED_DISINTEGRATE -> SpeedArt.disintegrate(c, a, t);
            case SHIELD_BRACE -> ShieldArt.draw(c, a, t);
            case VILLAGE_MELTDOWN, VILLAGE_SAFE -> VillageArt.draw(c, a, t);
            case CRYO_BLAST, CRYO_QUENCH, CRYO_CAST -> CryoBlastArt.draw(c, a, t);
            case RAD_HEAT, RAD_LEAP, RAD_SLAM, RAD_ZONE, RAD_EMP, RAD_BEAM, RAD_CHARGE, RAD_LANCE -> RadiationArt.draw(c, a, t);
            case FORCE_SHIELD, FORCE_PUSH, FORCE_FORM, FORCE_BRIDGE, FORCE_WALL, FORCE_BUBBLE, FORCE_CRUSH, FORCE_BEAD,
                    FORCE_NOVA_CHARGE, FORCE_NOVA, FORCE_DISC, FORCE_VANISH, FORCE_NET, FORCE_SHATTER, FORCE_RIPPLE -> ForceArt.draw(c, a, t);
            case OVERRUN_DEPART -> SpeedArt.depart(c, a, t);
            case OVERRUN_ARRIVE -> SpeedArt.arrive(c, a, t);
            case MATTER_RISE -> AssemblyArt.rise(c, a, t);
            case SENTINEL -> AssemblyArt.sentinel(c, a, t);
            case WATCH_ARENA, WATCH_EXPOSED, WATCH_CRACK, WATCH_SLAM_MARK, WATCH_SLAM, WATCH_ICE_MARK, WATCH_ICE_WALL,
                    WATCH_RAIL_MARK, WATCH_RAIL_ARC, WATCH_GRAB_MARK, WATCH_HOLD, WATCH_TAKE, WATCH_RINGS_MARK, WATCH_RINGS,
                    WATCH_DOORS, WATCH_PULL, WATCH_DROP -> WatchmakerArt.draw(c, a, t);
            case ELLE_ARENA, ELLE_AURA, ELLE_AIM, ELLE_BOLT, ELLE_RING_MARK, ELLE_RING, ELLE_SIPHON, ELLE_BACKFIRE,
                    ELLE_CONDUIT_MARK, ELLE_CONDUIT, ELLE_REBOOT, ELLE_STRIKE_MARK, ELLE_STRIKE, ELLE_ORB, ELLE_SHORT,
                    ELLE_PHASE, ELLE_ARRIVE, ELLE_OMEN, ELLE_DROP -> ElleArt.draw(c, a, t);
            case GUN_HEAT, GUN_FROST, GUN_TASER -> WeaponArt.draw(c, a, t);
        }
    }
}
