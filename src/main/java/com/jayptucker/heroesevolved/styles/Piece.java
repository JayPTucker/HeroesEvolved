package com.jayptucker.heroesevolved.styles;

/**
 * Every set piece: a showpiece animation drawn on the client as real geometry, as a function of its age in
 * ticks (like the vision's explosion). The server sends where it happens and when; both sides know how long it
 * lasts. Pieces marked "per ally" are sent once for each player they touch, bound to that player.
 */
public enum Piece {
    // Radiation
    GROUND_ZERO(230), CONTAINMENT_DOME(160), REACTOR_HEARTH(200),
    // Space-Time
    HOUR_HAND(34), TIME_LOOP(120), BORROWED_TIME(160),
    // Pyrokinesis
    SUNFALL(90), EMBER_SHROUD(160), HEARTHFIRE(240),
    // Cryokinesis
    HAILSTORM(96), CRYSTAL_BASTION(160), AURORA_VEIL(200),
    // Electrokinesis
    TESLA_SPIRE(120), TESLA_ZAP(7), FARADAY_CAGE(120), JUMPSTART(16),
    // Quake
    FISSURE_MAW(56), STONE_RAMPART(200), BEDROCK_STANCE(160),
    // Flight
    TEMPEST_DRILL(30), GALE_WALL(160), FORMATION_FLIGHT(200),
    // Telekinesis
    IMPLOSION(44), DEBRIS_HALO(160), GATHERING_HAND(34),
    // Telepathy
    PSYCHIC_STORM(120), BLIND_SPOT(34), HIVE_MIND(200),
    // Regeneration
    VITAL_BURST(28), UNDYING(120), LIFEBLOOM(240),
    // Sonic Resonance
    RESONANCE_CASCADE(32), SILENCE_DOME(160), BATTLE_HYMN(200),
    // Superspeed
    LIGHTNING_FLURRY(18), PHASE_BLUR(120), RELAY_RUN(18),
    // Precognition
    FORETOLD_STRIKE(60), BRANCHING_PATHS(120), PROPHECY_CANVAS(90),
    // Invisibility
    SHADOW_STRIKE(18), REFRACTION_CLOAK(120), PRISM_HAZE(200),
    // Density
    PHASE_ERUPTION(34), DENSE_BULWARK(160), PHASE_FIELD(160),
    // Empathic Mimicry
    PRISMATIC_OVERFLOW(34), EMPATHIC_WARD(160), KINDRED_LIGHT(44),
    // Technopathy
    DRONE_SWARM(160), DRONE_SHOT(6), FIREWALL(200), OVERCLOCK(200),
    // Matter Assembly
    FORGE_CANNON(44), FORGE_SLUG(8), BULWARK_LATTICE(160), MENDING_LATTICE(64),
    // Induced Radioactivity's Critical Mass, drawn as the vision's blast
    EXPLODING_MAN(600),
    // Superspeed's own: the wind a sudden stop throws ahead, and outrunning the last minute (leaving, arriving)
    SPEED_GUST(34), OVERRUN_DEPART(44), OVERRUN_ARRIVE(110),
    // Matter Assembly: matter torn up out of the ground into a construct; a sentinel turret's crystal head
    MATTER_RISE(30), SENTINEL(400),
    // Wind Arms: a funnel of wind off each circling hand, re-sent while held
    SPEED_ARMS(12),
    FLIGHT_RELEASE(24), FLIGHT_IMPACT(26), FLIGHT_RESCUE(24), FLIGHT_SHELTER(10),
    FLIGHT_CURRENT(10), FLIGHT_THERMAL_MARK(8), FLIGHT_THERMAL_RISE(19), FLIGHT_INTERCEPT(13),
    FLIGHT_SKYBURST(30),
    // Pyrokinesis uses the same tongues of flame for every cast and its blue-hot variant.
    PYRO_FORGE(7), PYRO_SPEAR(14), PYRO_BREATH(8), PYRO_FIRESTORM_PRIME(12), PYRO_FIRESTORM(14),
    PYRO_COLLAPSE(8), PYRO_BURST(24), PYRO_SUN_CHARGE(7), PYRO_SUN_DROP(12), PYRO_LASH(6),
    PYRO_MANTLE(8), PYRO_CAUTERIZE(26), PYRO_MINE_ARM(24), PYRO_MINE_WARN(8), PYRO_LANCE(9),
    PYRO_SHEPHERD(16), PYRO_TAKEOFF(20), PYRO_PHOENIX(3), PYRO_WAKE(12),
    // Induced Radioactivity: the heat cone, Fission Leap's launch and slam, contaminated ground, the EMP, the beam
    // (re-sent while held) and Critical Mass building
    RAD_HEAT(14), RAD_LEAP(26), RAD_SLAM(34), RAD_ZONE(12), RAD_EMP(22), RAD_BEAM(4), RAD_CHARGE(64),
    ELECTRO_DRAW(9), ELECTRO_ARC(13), ELECTRO_CURRENT(6), ELECTRO_ORB(5), ELECTRO_PRIME(8),
    ELECTRO_WARN(16), ELECTRO_STRIKE(23), ELECTRO_PULSE(26), ELECTRO_RIDE(15), ELECTRO_SCAR(60),
    ELECTRO_COIL(8), ELECTRO_SIPHON(9), ELECTRO_BLACKOUT(32), ELECTRO_AURA(10), ELECTRO_FIELD(30), ELECTRO_SCREEN(120),
    // Induced Radioactivity's Gamma Lance: a bolt of gamma light down its line
    RAD_LANCE(12),
    // Invisibility's force fields: lens shells with a bright rim and hexagonal facets. Constructs, bubbles, the shield and
    // the disc are re-sent every ten ticks while they last.
    FORCE_SHIELD(12), FORCE_PUSH(14), FORCE_FORM(10), FORCE_BRIDGE(12), FORCE_WALL(12), FORCE_BUBBLE(12), FORCE_CRUSH(10),
    FORCE_BEAD(9), FORCE_NOVA_CHARGE(22), FORCE_NOVA(30), FORCE_DISC(12), FORCE_VANISH(18), FORCE_NET(16),
    FORCE_SHATTER(18), FORCE_RIPPLE(8);

    /** How long it is drawn, in ticks. */
    public final int ticks;

    Piece(int ticks) { this.ticks = ticks; }

    private static final Piece[] VALUES = values();

    public static Piece byId(int id) { return id >= 0 && id < VALUES.length ? VALUES[id] : null; }
}
