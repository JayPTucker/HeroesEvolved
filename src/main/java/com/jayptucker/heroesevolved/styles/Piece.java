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
    // Invisibility
    SHADOW_STRIKE(18), REFRACTION_CLOAK(120), PRISM_HAZE(200),
    // Density
    PHASE_ERUPTION(34), DENSE_BULWARK(160), PHASE_FIELD(160),
    // Empathic Mimicry
    PRISMATIC_OVERFLOW(34), EMPATHIC_WARD(160), KINDRED_LIGHT(44),
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
    FORCE_SHIELD(12), FORCE_PUSH(18), FORCE_FORM(10), FORCE_BRIDGE(12), FORCE_WALL(12), FORCE_BUBBLE(12), FORCE_CRUSH(10),
    FORCE_BEAD(9), FORCE_NOVA_CHARGE(22), FORCE_NOVA(30), FORCE_DISC(12), FORCE_VANISH(18), FORCE_NET(16),
    FORCE_SHATTER(18), FORCE_RIPPLE(8),
    // A speedster burning away on a containment run
    SPEED_DISINTEGRATE(104),
    // A shield-maker's shield holding against a nuclear blast, then keeping the fallout off
    SHIELD_BRACE(200),
    CRYO_BLAST(6), CRYO_QUENCH(17), CRYO_CAST(14),
    // A villager in meltdown (re-sent every ten ticks; scale is how far gone, +2 while held) and his village's safe point
    VILLAGE_MELTDOWN(12), VILLAGE_SAFE(12),
    // The story's scripted fights (the Last Day's sky fight, the Space-Time vision), drawn by StoryArt: a forked lightning
    // bolt (origin the hand, target where it lands, scale its thickness) and a telekinetic grip (origin the held body,
    // target the hand, scale how hard it holds, bound to whoever is held)
    STORY_BOLT(10), STORY_GRIP(12),
    // Sylar in the Collection (story/sylar), drawn by SylarArt. Every telegraph is a brass schematic that assembles as the
    // move lands, so each _MARK lasts exactly its move's windup; seeded pieces carry a MimicColors index in their seed.
    // The room (re-sent every second, bound to him; scale the phase): specimens in their cases, the thread to the case he
    // draws on, his loupe, the lights and the great movement overhead. A locked case mending itself. Then each move's
    // telegraph and what it does (see SylarMoves for what origin, target and scale carry), Appraisal's hold (bound to the
    // held player; scale the progress, seed colour * 100 + how hard they have fought) and the theft into a case, a
    // stolen power going home, a backfire, his broken concentration, a phase change (scale the new phase), the pillars
    // rising, the shove, the fold into and out of the hall (bound to each player; scale 1 leaving, 2 arriving, 3 home),
    // and his death (scale the cases still standing, as a bit mask)
    SYLAR_ROOM(24), SYLAR_LOCK(16), SYLAR_HURL_MARK(24), SYLAR_IMPACT(26), SYLAR_REEL_MARK(12), SYLAR_REEL(10),
    SYLAR_LATTICE_MARK(24), SYLAR_LATTICE(14), SYLAR_ICE_MARK(28), SYLAR_ICE(34), SYLAR_FAULT_MARK(20), SYLAR_FAULT(24),
    SYLAR_RING_MARK(20), SYLAR_RING(46), SYLAR_HAND_MARK(30), SYLAR_HAND(40), SYLAR_BLUR_MARK(24), SYLAR_BLUR(10),
    SYLAR_HOURS_MARK(60), SYLAR_HOURS(36), SYLAR_LIGHT_MARK(60), SYLAR_LIGHT(40), SYLAR_FIZZLE(30), SYLAR_APPRAISE_MARK(16),
    SYLAR_HOLD(12), SYLAR_TAKE(36), SYLAR_RELEASE(30), SYLAR_YOURS_MARK(16), SYLAR_YOURS(12), SYLAR_BACKFIRE(36),
    SYLAR_STAGGER(30), SYLAR_PHASE(40), SYLAR_COVER(40), SYLAR_SHOVE(10), SYLAR_ARRIVE(30), SYLAR_DEATH(60),
    // The power-themed TaCZ guns: the heat gun's flame, the cold gun's frost streak (scale 0) and burst (scale 1), and a
    // taser's barbs and current (bound to whoever they bit)
    GUN_HEAT(8), GUN_FROST(12), GUN_TASER(16),
    // The Ability Dampener, drawn by DampenerArt (a slate lattice and rings that close inward, as if a wave were being
    // cancelled): the projector's field (re-sent; scale is its radius, target.x - origin.x how far it has spun up), the
    // Nullifier's shot, a round landing (bound to whoever it hit; scale is their height), a dampened body's collar
    // (re-sent, bound) and the gun drinking from a machine (bound to the player; scale is the rounds it took)
    DAMPENER_FIELD(12), DAMPENER_SHOT(10), DAMPENER_HIT(16), DAMPENER_MARK(12), DAMPENER_CHARGE(16),
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
    VIBE_GATHER(9), VIBE_WAVE(34), VIBE_CROSSWAVE(34), VIBE_HIT(14), VIBE_BREACH_SEED(14),
    VIBE_BREACH_OPEN(12), VIBE_BREACH_ARRIVE(16), VIBE_BREACH_CLOSE(10), VIBE_TRANSIT(18),
    VIBE_TOUCH(24), VIBE_ECHO(29), VIBE_GUARD(10), VIBE_GUARD_CLOSE(10), VIBE_LOCK_WARN(16),
    VIBE_LOCK(33), VIBE_AWAKEN(28),
    // Vibe's Rift Beast (re-sent, bound to it), and the style's Breach Kick (origin the foot's mouth, target the far mouth
    // behind the foe) and Seam Sweep (a crack along the ground; scale is its length)
    VIBE_BEAST(12), VIBE_KICK(14), VIBE_SEAM(18),
    // A breach channelled from the palm (re-sent every two ticks, bound to the viber; scale is 1 + how far it has opened,
    // the seed the channel's whole length in ticks), and a vision's hologram arriving or leaving (bound to nothing;
    // scale 1 arriving, 0.5 leaving)
    VIBE_BREACH_CHANNEL(5), VIBE_PROJECT(16),
    // Breach Boxing's Breach Hop: a tear at the hopper's side (origin) and one farther along (target), the second
    // opening as they dive into the first
    VIBE_HOP(10),
    // A held Vibe Blast, re-sent every two ticks: bound to the viber it runs from their palm to the origin; unbound (the
    // leg out of a breach) from origin to target. Scale above 1 is Slingshot's harder blast.
    VIBE_STREAM(5),
    // Rift Fall: the zone's warning ring (scale its radius), the breach in the sky (origin, facing down at the zone's
    // centre; scale its radius; bound to the caster, whose palms feed it at first; seed how long it stays), and one
    // body's plunge out of it (bound to the body; origin where it comes out, target where it lands; scale its height)
    VIBE_RIFT_ZONE(20), VIBE_RIFT_SKY(70), VIBE_RIFT_FALL(13),
    // Space-Time's skill tree, drawn by TimeArt (clockwork made of light): Selective Stasis's cage (re-sent, bound to the
    // target) and the release when its stored blows land (scale is how many), a Spatial Anchor planted (target holds the
    // hands' time of day), Second Chance's arrival, Stasis Seal's sphere (re-sent; scale is its radius) and its throw, one
    // cut of Moment's Edge or a Behind You strike (scale 1, +10 for a replayed blow), the hole Fold Space leaves snapping
    // shut (Snap Shut; scale is its reach), Foreknowledge's private mark (re-sent, bound) and Fast Forward (bound; scale 1
    // on an enemy, 33 on a friend, 16 + radius for growth)
    TIME_STASIS(12), TIME_RELEASE(18), TIME_ANCHOR(30), TIME_SECOND(20), TIME_SEAL(12), TIME_SEAL_BREAK(16), TIME_CUT(12),
    TIME_SNAP(14), TIME_MARK(12), TIME_FORWARD(24),
    // Density's older (phasing) skill tree, drawn by DensityArt. Since the earthbending rework only Harden (Stone Skin),
    // Rebound, the Pile Driver root, Sinkhole (Quicksand), Entombed, Uproot and Grasp from Below are still sent; the rest
    // keep their places because pieces go over the network by ordinal. Pass Through's wake through
    // a body (scale is the body's height), an Ambush-primed hand (private, bound) and its strike, a wall left open by
    // Ghost Network (re-sent; +10 when Phase Carry opens it), Harden's stone skin (re-sent, bound; scale 1 + weight / 8)
    // and Rebound (scale is its radius), a Pile Driver or Crater Landing root, Sinkhole's warning and its soft ground
    // (re-sent; scale is its radius), the ground closing over an Entombed body, Uproot, Fade Through (bound), Grasp from
    // Below, Phase Fall (origin the floor, target the landing) and Untouchable's slip (bound)
    DENSITY_PASS(12), DENSITY_PRIMED(60), DENSITY_AMBUSH(12), DENSITY_DOOR(12), DENSITY_HARDEN(12), DENSITY_REBOUND(16),
    DENSITY_PILE(12), DENSITY_SINK_MARK(10), DENSITY_SINKHOLE(12), DENSITY_ENTOMB(20), DENSITY_UPROOT(14), DENSITY_FADE(60),
    DENSITY_GRASP(14), DENSITY_DROP(14), DENSITY_SLIP(14),
    // A shot caught in a speedster's Wind Arms and blown back (drawn by SpeedArt): origin where it turned, target one
    // block along its new path; scale 1, or 0.7 for a bullet
    SPEED_DEFLECT(10),
    // Firebending, drawn by PyroArt (all bound to the fighter; BLUE_SCALE added for blue fire): a fist of fire from hand to
    // end (scale 1, or 1 + BLAZING for a Counterflame fireball), a roundhouse's crescent (origin the hip, target the
    // crescent's far middle), a spinning sweep kick's ring (scale is its radius), a jet step (origin and target the feet
    // before and after), a backflip's rising arc (origin the hip, target its far middle), and a dodged blow (Counterflame)
    PYRO_JAB(9), PYRO_KICK(12), PYRO_SWEEP_KICK(14), PYRO_JET_STEP(14), PYRO_FLIP(14), PYRO_COUNTER(12),
    // Shapeshifting, drawn by ShapeArt (both bound to the creature, shown only to the shapeshifter and to it): plates
    // tracing a studied creature's outline (origin the studier's eye), and plates rippling out from a touching hand
    // (origin the touch). The morph itself is drawn live from MorphStatePayload, not as a piece.
    SHIFT_STUDY(20), SHIFT_TOUCH(14),
    // Shapeshifting's Instinct (ShapeArt): Lunge's lengthening arm of plates (origin the shoulder, target the reach; scale
    // 1 on a hit), any other form's move (bound to the shapeshifter; scale is the InstinctMove's ordinal), a zombie's grab
    // (bound to the held creature), the warden's charge and boom (origin the chest, target the far end), and the
    // guardian's gaze (bound to its target)
    SHIFT_LUNGE(10), SHIFT_INSTINCT(16), SHIFT_HOLD(40), SHIFT_SONIC(26), SHIFT_GAZE(22),
    // Shapeshifting's tricks (ShapeArt): the seam down a decoy's back while it stands (bound to the decoy; scale its
    // height) and its burst (scale 1 when struck), Reflection's mirror between you and your attacker (bound to you;
    // origin your chest, target theirs), Nightmare's flare (sent to each viewer alone, bound to the shapeshifter; scale 2
    // for a foe, who sees a warden in your place, 1 for anyone else), a disguise bursting off (bound to the wearer;
    // scale its width), and the plates circling a confused creature's head (bound to it; scale its length in ticks)
    SHIFT_DECOY(240), SHIFT_DECOY_BURST(22), SHIFT_REFLECT(20), SHIFT_NIGHTMARE(36), SHIFT_EXPOSED(24), SHIFT_DAZE(40),
    // Plate Mail: plates locked flat over the body, re-sent every ten ticks while it lasts (bound to the wearer; scale its width)
    SHIFT_PLATES(12),
    // Electrokinesis's fighting style, drawn by ElectroArt (bound to the fighter unless said): a spark from fist to end
    // (scale 1 on a hit, .7 on a miss, + CHAINED when Ground Out loaded it), a Thunderclap's fan (origin the palms, target
    // its far middle; scale its reach), a Grounding Strike's ring (origin the feet, target a block ahead; scale its
    // radius), an Arc Step (origin and target the feet before and after; scale 2 for a Flashback), a Flashback's static
    // (unbound; scale its radius; it bursts FLASHBACK_FUSE ticks in), a blow grounded out, and the loaded rear fist (re-sent)
    ELECTRO_SPARK(9), ELECTRO_CLAP(14), ELECTRO_GROUND_ARC(16), ELECTRO_ARC_STEP(12), ELECTRO_DECOY(24),
    ELECTRO_GROUND_OUT(14), ELECTRO_LOADED(7),
    // Hydrokinesis, drawn by HydroArt (living water: glassy sea-blue bodies with foam lips and white spray; THIN added to
    // the scale when the water was pulled from the air). The kit: a Water Whip from hand to end (bound to the bender;
    // scale 1 on a hit, .6 a miss, + 4 for a snare), the coil a snare throws round its catch (bound to it; origin the
    // hand; scale its height), a Crashing Wave and a Tsunami (origin the floor it rises from, target its reach along the
    // ground; scale half its width), an Octopus Form arm lashing out (bound; origin the arm's root, target the foe), every
    // arm thrown out at once (bound; scale the burst's radius), a splash anywhere (scale its radius), Maelstrom's opening
    // whirlpool (scale its ring) and the Deluge it comes down as (scale its reach).
    HYDRO_WHIP(12), HYDRO_SNARE(14), HYDRO_WAVE(28), HYDRO_TSUNAMI(40), HYDRO_TENDRIL(9), HYDRO_BURST(16), HYDRO_SPLASH(16),
    HYDRO_WHIRLPOOL(36), HYDRO_DELUGE(26),
    // Waterbending (bound to the bender): a lash (scale 1 / .6, + 4 when Turn the Tide loaded it), one ice dagger (origin
    // the hands), a Water Wheel (scale its radius), a Flow Step (origin and target the feet before and after), a Geyser
    // Vault's column (origin where it bursts, target a block ahead; scale its radius), a blow turned (Turn the Tide), and
    // the loaded rear hand (re-sent)
    HYDRO_LASH(10), HYDRO_DAGGER(10), HYDRO_WHEEL(14), HYDRO_FLOW(16), HYDRO_VAULT(18), HYDRO_UNDERTOW(12), HYDRO_LOADED(7),
    // Hydrokinesis's tree and classes: a Geyser's mark and its burst (scale the radius), the Pressure Jet (bound, re-sent
    // every two ticks from the palms to its end), a Tide Wall (re-sent; origin its foot, target a block along its facing;
    // scale half its width), Healing Water wrapping its target (bound to it), a Rain Call cloud (bound to the caller,
    // re-sent; scale its radius), a Tidal Dome and a Healing Spring (re-sent; scale the radius), and the awakening (bound;
    // target the ward)
    HYDRO_GEYSER_MARK(16), HYDRO_GEYSER(24), HYDRO_JET(4), HYDRO_WALL(12), HYDRO_HEAL(20), HYDRO_RAIN(12), HYDRO_DOME(12),
    HYDRO_SPRING(12), HYDRO_AWAKEN(30),
    // Fighting styles, drawn by StyleArt. Force Palm (in the force fields' lenses and panes, bound to the fighter): a jab
    // through a lens on the knuckles (origin the fist, target the end; scale 1 on a hit, .6 a miss), one shard of a
    // released Pressure (origin the fist), a Force Burst's fan of lenses (origin the chest, target its far middle; scale
    // its reach), a Field Slam's dome (scale its radius), a Shield Slide's pane (origin and target the feet before and
    // after) and a Bounce's trampoline pane (origin the feet, target a block back). Ice Skater: a skate's ice strip,
    // lingering (origin and target its ends).
    STYLE_LENS_JAB(9), STYLE_SHARDS(10), STYLE_FORCE_BURST(14), STYLE_FIELD_SLAM(16), STYLE_LENS_SLIDE(12), STYLE_BOUNCE(14),
    STYLE_ICE_STRIP(60), STYLE_TK_PALM(11), STYLE_TK_GRASP(36),
    // A meltdown (MeltdownField): a shock wave rolling out (scale its radius; the target's height above the origin its
    // strength), the near-field's lethal edge on the ground (re-sent, bound to whoever is melting down; scale its
    // radius), cubes peeling off a burning body (bound to it; scale how badly), and a body burning away to cubes when
    // radiation kills it (bound to it, so its corpse is hidden; scale its height, the target along its facing as far as
    // it is wide)
    RAD_SHOCK(16), RAD_FIELD(12), RAD_FLAKE(24), RAD_DISINTEGRATE(90),
    // Rime accumulating on one face, replaced as it grows. Scale 1 clears the face.
    CRYO_TERRAIN_FROST(80),
    // A slow Cyclone's vacuum (re-sent every ten ticks while it holds; scale its radius): the air dragged out of the ring
    SPEED_VACUUM(12),
    // Gifted mobs (gifted/GiftedMob): a gift waking (scale the power's colour index plus .75 if it woke evil, .25 if good),
    // a villager turning into an illager (scale the colour index), and a mini-boss going unbound at half health (bound to
    // it; scale the colour index plus .5). All drawn by GiftArt.
    GIFT_AWAKEN(30), GIFT_TURN(44), GIFT_UNBOUND(36),
    // Shared speedster Perception: gold lightning flowing round the guest's waist, re-sent every eight ticks.
    SPEED_SHARED_TIME(12),
    // Finishers (combat/Takedowns), each drawn in its power's own art. Dragon Coil (PyroArt): origin the foe's feet, target the
    // finisher's feet, scale the foe's height (+ PyroGeometry.BLUE_SCALE for blue fire). Closed Loop (TimeArt): origin the foe's
    // feet, target where you fold in; scale the foe's height (+ 32 when three of you strike). Lights Out (TelepathyArt): bound to
    // the foe, its seed the telepath's entity id; origin the telepath's eyes; scale the foe's height.
    FINISH_DRAGON(38), FINISH_LOOP(34), FINISH_LIGHTS(34),
    // The other finishers, each in its power's art (see each finisher class for what origin, target, scale and seed carry).
    FINISH_SHORT(28), FINISH_BREAKER(34), FINISH_WARMTH(30), FINISH_THUNDER(30), FINISH_DOWNBURST(34), FINISH_BEAT(30),
    FINISH_ENCORE(34), FINISH_STRINGS(32), FINISH_SIXFEET(34), FINISH_MIRRORS(32), FINISH_PAID(30), FINISH_CHAIN(30),
    FINISH_FREQUENCY(30), FINISH_MONUMENT(36), FINISH_TAKE(30), FINISH_UNMADE(34),
    // The alternate finishers, two per power, each in its power's art; each finisher class says what origin, target,
    // scale, binding and seed carry.
    FINISH_PHOENIX(30), FINISH_HEARTH(32), FINISH_STOPPED(34), FINISH_UNWOUND(32), FINISH_PRESSURE(30), FINISH_THOUGHT(32),
    FINISH_ROD(30), FINISH_OVERLOAD(30), FINISH_BUBBLE(32), FINISH_RIPTIDE(30), FINISH_COFFIN(30), FINISH_ICICLES(30),
    FINISH_FISTS(28), FINISH_FLICK(28), FINISH_CLAP(26), FINISH_UPDRAFT(36), FINISH_FAULT(30), FINISH_SHATTER(28),
    FINISH_NOTE(30), FINISH_SILENCE(32), FINISH_CRUSH(28), FINISH_TOSS(34), FINISH_PHASE(28), FINISH_HEAVY(30),
    FINISH_BUBBLE_CRUSH(30), FINISH_LENS(26), FINISH_NO_PAIN(32), FINISH_GRIP(28), FINISH_HALF_LIFE(34),
    FINISH_POCKET_SUN(34), FINISH_BREACH(30), FINISH_POINT_BLANK(26),
    FINISH_HAMMER(30), FINISH_ORBIT(30), FINISH_ECHO(28), FINISH_BORROW(16), FINISH_APEX(30), FINISH_FACE(30),
    // Power Amplification, drawn by AmpArt (red lightning: crimson bodies in the soft pass, hot rims and pale cores in
    // glow). A zap from the hand to an ally (bound to the amplifier; seed the ally's id); the crackle climbing an ally as a
    // Surge takes hold (bound to the ally); an Overcharge's ring of sparks tightening round the ally (bound to the ally,
    // its whole length); a bolt and a beam (origin the hand, target the end; bound to the caster; scale the strength);
    // Van Full's burst (scale the radius); Shock Therapy's jolt (bound to whoever is jolted); Jumpstart into a block or a
    // machine (origin the hand, target the block); A Thousand Times' gathering (bound) and its wave (scale the radius);
    // the Partner thread (bound to the amplifier, seed the partner's id); Burnout and a misfire (bound to the victim);
    // Doorbuster's blast; the Invisibility overcharge's dome (scale the radius); a storm bolt from the sky; the
    // awakening; a backlash (bound).
    AMP_ZAP(10), AMP_SURGE(24), AMP_OVERCHARGE(120), AMP_BOLT(8), AMP_BEAM(10), AMP_BURST(16), AMP_SHOCK(14),
    AMP_JUMP(16), AMP_GATHER(40), AMP_WAVE(18), AMP_THREAD(12), AMP_BURNOUT(24), AMP_WILD(14), AMP_DOOR(14),
    AMP_DOME(12), AMP_STORM(10), AMP_AWAKEN(30), AMP_BACKLASH(16),
    FINISH_JUICE(30), FINISH_RED_WAVE(28), FINISH_REVERSE(26),
    // Density's earthbending (phasing/, art in EarthArt). Earth pieces made of a block carry its id in the seed.
    EARTH_STANCE(16), EARTH_LIFT(14), EARTH_THROW(8), EARTH_SHATTER(16), EARTH_SHACKLE(20), EARTH_CRUSH(14), EARTH_TUNNEL(16),
    EARTH_PILLAR(14), EARTH_WALL(14), EARTH_SHOVE(10), EARTH_MOUNTAIN_MARK(15), EARTH_MOUNTAIN(12), EARTH_MOUNTAIN_SLAM(30),
    EARTH_GAUNTLET(16), EARTH_AVALANCHE(20), EARTH_TENT(20), EARTH_SURF(12), EARTH_STOMP(24), EARTH_LISTEN(12), EARTH_CLAW(8),
    EARTH_SLAB(12), EARTH_SPIKES(16), EARTH_SLIDE(14),
    // Overloads (village/overload), drawn by OverloadArt: a wildfire's ember (origin above the eye, target where it lands;
    // its ring glows OverloadGeometry.EMBER_WARNING ticks, then it burns EMBER_BURN; scale its radius), the Firestorm's wall
    // rushing out (scale its reach), and the Deep Freeze's white wave (scale its reach).
    OVERLOAD_EMBER(130), OVERLOAD_FIRESTORM(40), OVERLOAD_DEEP_FREEZE(40),
    // Starfall (meteor/), drawn by MeteorArt: a meteor landing (origin where it landed; scale its blast) and one cracked
    // apart in the sky by a sonic (origin where it broke; scale its rock's radius).
    METEOR_IMPACT(44), METEOR_SHATTER(30),
    OVEREXERTION(6000),
    // End portal offerings: origin the center's surface, target the eye, bound to a channeling player.
    // Seed: power colour in bits 0..4, paid channel ticks above it. OPEN packs the pair's colours in five bits each.
    END_PORTAL_DORMANT(24), END_PORTAL_CHANNEL(14), END_PORTAL_IMPRINT(14), END_PORTAL_OPEN(54), END_PORTAL_CANCEL(18),
    WITHER_FORM(158), WITHER_BREACH(14), WITHER_AURA(14), WITHER_WARNING(80), WITHER_PULSE(90),
    WITHER_IMPACT(24), WITHER_NOVA(24), WITHER_BACKFIRE(36), WITHER_PHASE(50), WITHER_DEATH(116), WITHER_CANCEL(84),
    END_BATTLE_FIELD(14), END_BATTLE_WARNING(130), END_BATTLE_CHANNEL(14), END_BATTLE_CANCEL(150),
    END_BATTLE_GROUNDED(50), END_BATTLE_PHASE(60), END_BATTLE_ABSORB(200), END_BATTLE_RELEASE(180),
    END_BATTLE_REFUGE(14), END_BATTLE_BACKFIRE(40), END_BATTLE_STRIKE(20), END_BATTLE_NOVA(30),
    END_BATTLE_IMPACT(24), END_BATTLE_WAVE(180), END_BATTLE_DASH(16), END_BATTLE_BOLT(10),
    // A breath pool on the ground: origin its centre, scale its radius, seed its lifetime in ticks.
    END_BATTLE_POOL(180),
    WITHER_FIRE(16), WITHER_WAKE(18), WITHER_ARC(20), WITHER_HEAD_WARN(48), WITHER_MUZZLE(12),
    FUTURE_OMEN(26), FUTURE_BREACH(14), FUTURE_SHUT(26),
    // The Wither, continued: a stance crack across its chest (seed: cracks so far), one Bone Spires crack (origin the
    // creature, target the mark; its spires burst in order), the death detonation (origin the star, scale its radius),
    // the tear it drops out of over the dais, and the exit tearing open where it died (origin center, target + normal).
    WITHER_CRACK(16), WITHER_SPIRES(80), WITHER_DETONATE(150), WITHER_TEAR(70), WITHER_OPEN(60),
    // A skeleton of its brood thrown from its mouth (origin) onto its mark (target), seed the phase.
    WITHER_SPEW(30),
    // Its locks (combat/BossLocks). An aimed power answering a heart (origin the hand, target the heart, seed the role
    // and bit 5 strong); a heart shattering or re-forming (origin the heart, target the star; seed its state, bit 3 a
    // re-forming one); the Star Ward shattering off its ribs; the ward turning a blow at its gate; the Hunger Mark's
    // thread (origin the marked fighter, bound to them; seed 1 when it snaps, 2 while unseen); a brood skeleton carrying
    // bone back (bound to it; seed bit 4 when it climbs in); a fighter a power carried out of the discharge (seed the role).
    WITHER_KEY(18), WITHER_HEART_BREAK(34), WITHER_WARD_BREAK(46), WITHER_WARD_HIT(12), WITHER_MARK(14), WITHER_MEND(14),
    WITHER_SPARED(22),
    // Its other ways to fight: a Breach Step's tear (origin its center, target + its normal; seed 1 the one it leaves by),
    // the Bone Cage (origin the center on the floor, scale its radius; seed 0 holding someone, 2 empty, 1 shattered by a
    // power, 3 sinking), and a Starving Well (origin the star, scale its blast radius, seed its age; re-sent as it drifts).
    WITHER_STEP(20), WITHER_CAGE(52), WITHER_WELL(3),
    // Electrokinesis's machine nodes (electro/ElectroMachines): a device the current reached (scale 1 worked, 2 killed);
    // a jammed gun spitting sparks (bound to its holder, re-sent while it lasts); Counterweight's taut line from the hand
    // to the anchor (bound to the hauled player) and Winchline's from the anchor to the reeled body (bound to them).
    ELECTRO_DEVICE(16), ELECTRO_JAM(12), ELECTRO_HAUL(12), ELECTRO_REEL(12),
    // Matter Assembly, the engineer (assembly/MatterAssemblyService, drawn by AssemblyArt; shapes in AssemblyGeometry):
    // a panel snapping together cell by cell (origin its base cell, target base + facing, scale its shape flags, bound
    // to the builder); a Skywalk plate; a Launch Pad held (re-sent) and placed; a throw off a pad (bound to who flew);
    // a Kaleidoscope's mirrors; one step of a Shard in flight (scale its brightness); a ricochet (target the face's
    // normal); Matter flying home (scale how many, bound to the owner).
    MATTER_PANEL(22), MATTER_PLATE(14), MATTER_PAD(12), MATTER_PAD_PLACE(18), MATTER_LAUNCH(16), MATTER_KALEIDO(140),
    MATTER_SHARD(4), MATTER_BOUNCE(10), MATTER_RETURN(24);

    /** How long it is drawn, in ticks. */
    public final int ticks;

    Piece(int ticks) { this.ticks = ticks; }

    private static final Piece[] VALUES = values();

    public static Piece byId(int id) { return id >= 0 && id < VALUES.length ? VALUES[id] : null; }
}
