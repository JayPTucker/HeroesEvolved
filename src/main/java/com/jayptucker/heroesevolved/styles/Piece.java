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
    FORCE_SHATTER(18), FORCE_RIPPLE(8),
    // A speedster burning away on a containment run
    SPEED_DISINTEGRATE(104),
    // A shield-maker's shield holding against a nuclear blast, then keeping the fallout off
    SHIELD_BRACE(200),
    CRYO_BLAST(6), CRYO_QUENCH(17), CRYO_CAST(14),
    // A villager in meltdown (re-sent every ten ticks; scale is how far gone, +2 while held) and his village's safe point
    VILLAGE_MELTDOWN(12), VILLAGE_SAFE(12),
    // Sylar's shop, the Watchmaker's arena: its clocks (re-sent every second; scale is the phase, +10 while he runs), the
    // glint of his exposed watch and its cracking, his telegraphs (each a dial whose hand reaches twelve as it lands) and
    // what they do, his grab, the rings of stopped time, the doors locking, the pull into and out of the shop, the drop
    WATCH_ARENA(24), WATCH_EXPOSED(12), WATCH_CRACK(40), WATCH_SLAM_MARK(30), WATCH_SLAM(30), WATCH_ICE_MARK(30),
    WATCH_ICE_WALL(24), WATCH_RAIL_MARK(24), WATCH_RAIL_ARC(16), WATCH_GRAB_MARK(16), WATCH_HOLD(12), WATCH_TAKE(36),
    WATCH_RINGS_MARK(36), WATCH_RINGS(32), WATCH_DOORS(30), WATCH_PULL(30), WATCH_DROP(40),
    // Elle Bishop on Primatech's test floor: the room's live grid (re-sent every second, bound to her), her charge and
    // shield (re-sent every ten ticks), each telegraph and what it does, a bank shorting, a siphon and its backfire,
    // the conduits, her reboot, the storm, the phase changes, her arrival in the dark, the omens, the drop
    ELLE_ARENA(24), ELLE_AURA(12), ELLE_AIM(16), ELLE_BOLT(10), ELLE_RING_MARK(24), ELLE_RING(14), ELLE_SIPHON(60),
    ELLE_BACKFIRE(36), ELLE_CONDUIT_MARK(24), ELLE_CONDUIT(12), ELLE_REBOOT(60), ELLE_STRIKE_MARK(30), ELLE_STRIKE(14),
    ELLE_ORB(48), ELLE_SHORT(20), ELLE_PHASE(30), ELLE_ARRIVE(60), ELLE_OMEN(16), ELLE_DROP(40),
    // The power-themed TaCZ guns: the heat gun's flame, the cold gun's frost streak (scale 0) and burst (scale 1), and a
    // taser's barbs and current (bound to whoever they bit)
    GUN_HEAT(8), GUN_FROST(12), GUN_TASER(16),
    // Electrokinesis's skills: Arc Lance's straight shot, Circuit Thread crawling block to block, Overcharge climbing
    // the body, Ground Sink's rod (re-sent while it stands) and its discharge, and Arc Lattice's anchors and wires
    // (both re-sent while the lattice stands)
    ELECTRO_LANCE(16), ELECTRO_THREAD(16), ELECTRO_OVERCHARGE(18), ELECTRO_SINK(12), ELECTRO_SINK_BURST(24),
    ELECTRO_ANCHOR(12), ELECTRO_WIRE(12),
    // Empathic Mimicry: the thread of an attunement (re-sent as it builds), the moment a set is taken, and a switch
    MIMIC_ATTUNE(12), MIMIC_IMPRINT(28), MIMIC_SHIFT(18),
    // Sonic Resonance, all drawn by SonicArt: the cry and the held scream (re-sent while it lasts), a Note playing
    // (re-sent; scale grows as it is fed) and breaking (+32 when harmonic), the Wall (re-sent) and its push, the
    // vault, the dive (re-sent) and the landing, Crescendo gathering (re-sent; scale is how built) and going out,
    // the skills (Focused Shriek, a Ringing mark re-sent on its target, Shatter Note, Dissonant Wave, Echo Map's sweep,
    // Resonant Guard and Quiet Zone, both re-sent)
    SONIC_CRY(14), SONIC_SCREAM(5), SONIC_NOTE(12), SONIC_NOTE_BURST(26), SONIC_WALL(12), SONIC_WALL_PUSH(18),
    SONIC_VAULT(16), SONIC_DIVE(5), SONIC_LANDING(28), SONIC_GATHER(8), SONIC_CRESCENDO(30), SONIC_SHRIEK(14),
    SONIC_RING(12), SONIC_SHATTER(20), SONIC_DISSONANCE(22), SONIC_ECHO(40), SONIC_GUARD(12), SONIC_QUIET(12),
    // Quake, drawn by QuakeArt: a bullet shaken to dust, a Resonate collapsing, Seismic Lance's column, the Vibration Shell
    // (re-sent; scale is its radius), Counterforce's brace (re-sent, bound; scale is how much is stored), Pressure Step,
    // Surgical Fracture's cracks on a wall, and one step of a written fault (scale is its fuse in ticks)
    QUAKE_DUST(14), QUAKE_SHATTER(16), QUAKE_LANCE(24), QUAKE_SHELL(12), QUAKE_BRACE(12), QUAKE_DASH(14), QUAKE_SEAM(30),
    QUAKE_TRACE(40),
    // Regeneration, drawn by RegenArt: Blood Stream's flow (re-sent while held, bound to Claire), Bloodletting's fan, a
    // Freefall landing, Pay It Back's burst, Play Dead's slowing heartbeat (re-sent, bound) and Revive's compressions
    REGEN_STREAM(5), REGEN_FAN(16), REGEN_IMPACT(24), REGEN_PAYBACK(24), REGEN_FEIGN(12), REGEN_CPR(20),
    // Telepathy, drawn by TelepathyArt: Drop It's word, Far Hearing's listening (re-sent, bound) and a voice heard (sent to
    // the listener; scale 1 + 10 × how far), Dream Double's weave, Persuasion's talk-down (scale is its length in ticks)
    // and a mind won over (re-sent on a persuaded mob), Memory Walk's cast, a trail segment (private; scale 1 + 10 × how
    // long ago, +32 where it was hurt) and the echo where the memory began, Lost in Thought (re-sent), Waking Fright,
    // Hive Link's threads and a linked friend's halo (private, re-sent; +16 speaking, +32 in danger), and a voice
    // inside your head (Mind Network)
    TELE_COMMAND(14), TELE_LISTEN(12), TELE_VOICE(12), TELE_WEAVE(22), TELE_PERSUADE(40), TELE_WON(12), TELE_RECALL(18),
    TELE_MEMORY(200), TELE_ECHO(200), TELE_LOST(12), TELE_FRIGHT(18), TELE_HIVE_LINK(26), TELE_HALO(12), TELE_PROJECT(12),
    // Vibe: split blue wave fronts, two-sided breaches, combat echoes and collapsing interference bands.
    VIBE_GATHER(9), VIBE_WAVE(18), VIBE_CROSSWAVE(18), VIBE_HIT(14), VIBE_BREACH_SEED(14),
    VIBE_BREACH_OPEN(12), VIBE_BREACH_ARRIVE(16), VIBE_BREACH_CLOSE(10), VIBE_TRANSIT(18),
    VIBE_TOUCH(24), VIBE_ECHO(29), VIBE_GUARD(10), VIBE_GUARD_CLOSE(10), VIBE_LOCK_WARN(16),
    VIBE_LOCK(33), VIBE_AWAKEN(28),
    // Space-Time's skill tree, drawn by TimeArt (clockwork made of light): Selective Stasis's cage (re-sent, bound to the
    // target) and the release when its stored blows land (scale is how many), a Spatial Anchor planted (target holds the
    // hands' time of day), Second Chance's arrival, Stasis Seal's sphere (re-sent; scale is its radius) and its throw, one
    // cut of Moment's Edge or a Behind You strike (scale 1, +10 for a replayed blow), the hole Fold Space leaves snapping
    // shut (Snap Shut; scale is its reach), Foreknowledge's private mark (re-sent, bound) and Fast Forward (bound; scale 1
    // on an enemy, 33 on a friend, 16 + radius for growth)
    TIME_STASIS(12), TIME_RELEASE(18), TIME_ANCHOR(30), TIME_SECOND(20), TIME_SEAL(12), TIME_SEAL_BREAK(16), TIME_CUT(12),
    TIME_SNAP(14), TIME_MARK(12), TIME_FORWARD(24),
    // Density's skill tree, drawn by DensityArt (surfaces that ripple like water, and stone): Pass Through's wake through
    // a body (scale is the body's height), an Ambush-primed hand (private, bound) and its strike, a wall left open by
    // Ghost Network (re-sent; +10 when Phase Carry opens it), Harden's stone skin (re-sent, bound; scale 1 + weight / 8)
    // and Rebound (scale is its radius), a Pile Driver or Crater Landing root, Sinkhole's warning and its soft ground
    // (re-sent; scale is its radius), the ground closing over an Entombed body, Uproot, Fade Through (bound), Grasp from
    // Below, Phase Fall (origin the floor, target the landing) and Untouchable's slip (bound)
    DENSITY_PASS(12), DENSITY_PRIMED(60), DENSITY_AMBUSH(12), DENSITY_DOOR(12), DENSITY_HARDEN(12), DENSITY_REBOUND(16),
    DENSITY_PILE(12), DENSITY_SINK_MARK(10), DENSITY_SINKHOLE(12), DENSITY_ENTOMB(20), DENSITY_UPROOT(14), DENSITY_FADE(60),
    DENSITY_GRASP(14), DENSITY_DROP(14), DENSITY_SLIP(14);

    /** How long it is drawn, in ticks. */
    public final int ticks;

    Piece(int ticks) { this.ticks = ticks; }

    private static final Piece[] VALUES = values();

    public static Piece byId(int id) { return id >= 0 && id < VALUES.length ? VALUES[id] : null; }
}
