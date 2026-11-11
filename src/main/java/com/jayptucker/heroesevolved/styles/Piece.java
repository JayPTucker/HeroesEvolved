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
    // Ted Sprague's own detonation (Exploding Man), drawn as the vision's blast
    EXPLODING_MAN(600),
    // Superspeed's own: the wind a sudden stop throws ahead, and outrunning the last minute (leaving, arriving)
    SPEED_GUST(34), OVERRUN_DEPART(44), OVERRUN_ARRIVE(110),
    // Matter Assembly: matter torn up out of the ground into a construct; a sentinel turret's crystal head
    MATTER_RISE(30), SENTINEL(400);

    /** How long it is drawn, in ticks. */
    public final int ticks;

    Piece(int ticks) { this.ticks = ticks; }

    private static final Piece[] VALUES = values();

    public static Piece byId(int id) { return id >= 0 && id < VALUES.length ? VALUES[id] : null; }
}
