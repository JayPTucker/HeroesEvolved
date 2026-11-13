package com.jayptucker.heroesevolved.ability;

/** How a player's arms are held while using a power. */
public enum ArmPose {
    /** The main arm thrust out toward where the player is looking. */
    ONE_HAND,
    /** Both arms out, palms forward, toward where the player is looking. */
    TWO_HANDS,
    /** Both arms out and trembling with the strain of a sustained power. */
    TWO_HANDS_STRAIN,
    /** Both arms raised overhead. */
    ARMS_RAISED,
    /** Both arms spread out to the sides, palms down. */
    ARMS_WIDE,
    /** The main hand pressed to the temple in concentration. */
    TEMPLE,
    /** Both arms driven down toward the ground. */
    GROUND,
    /** Alternating hands draw loose matter into a telekinetic orbit. */
    KINETIC_GATHER,
    /** One palm steadies a held object while the other braces its weight. */
    KINETIC_HOLD,
    /** A sharp open-handed release of stored force. */
    KINETIC_RELEASE,
    /** The main arm pushed straight out and low, the hand sinking into whatever it touches. */
    REACH,
    /** Arms held out and down at the sides as the body sinks into the ground. */
    SINK,
    /** A gathered elbow, snapping wrist and drawn-back recovery. */
    FIRE_LASH,
    /** Cupped palms slowly feeding a fire core. */
    FIRE_FORGE,
    /** A sharp two-handed thrust and recoil. */
    FIRE_RELEASE,
    /** Alternating pressure against a sustained cone of flame. */
    FIRE_BREATH,
    /** Draw loose fire inward with alternating open palms. */
    FIRE_SHEPHERD,
    /** Close hands to the chest, then open like rising wings. */
    FIRE_CAUTERIZE,
    /** Gather low, then lift a growing sun overhead. */
    FIRE_SUN,
    /** Sweep a low ember outward into a rising field. */
    FIRE_SWEEP,
    /** Separate a refracted double from between the palms. */
    OPTICAL_SPLIT,
    /** Brush light outward to settle a shared veil. */
    OPTICAL_VEIL,
    /** Cross the hands briefly, then scatter several optical echoes. */
    OPTICAL_DISPERSE,
    /** Alternating palms sustain a fine stream of rime. */
    CRYO_WEAVE,
    /** A low sweep raises sculpted ice. */
    CRYO_SCULPT,
    /** Draw both hands inward around a crystal prison. */
    CRYO_PRISON,
    /** Snap a formed lattice apart. */
    CRYO_SHATTER,
    /** Spread a gathering winter storm out from the body. */
    CRYO_WHITEOUT,
    /** A crouched skate, with a guiding hand and a balancing arm. */
    CRYO_SURF,
    /** Draw a forming crystal lance back, then release it from the leading hand. */
    CRYO_LANCE,
    /** Fold the palms inward to assemble orbiting armor plates. */
    CRYO_GUARD,
    /** Snap both palms toward the ground to send out a ring of cold. */
    CRYO_SNAP,
    /** Alternating hands stitch a growing icy route. */
    CRYO_LOOM,
    /** Cradle a protective shell between open palms. */
    CRYO_SHELTER,
    /** Spread and turn the hands to set a constellation of linked crystals. */
    CRYO_STARS,
    /** Both arms windmilling too fast to see, out of step, into two funnels of wind. */
    WIND_ARMS,
    FLIGHT_COIL, FLIGHT_BRACE, FLIGHT_ROLL, FLIGHT_CLAP, FLIGHT_CAST,
    FIRE_DIVE,
    ELECTRO_DRAW, ELECTRO_CAST, ELECTRO_STREAM, ELECTRO_CALL, ELECTRO_COIL, ELECTRO_RELEASE,
    /** Invisibility's force fields: palms up behind a lens, a two-handed thrust, a low sweep that lays a span,
     *  an open hand sealing a bubble, a closing fist, and arms gathering then flung wide for Force Nova. */
    FORCE_SHIELD, FORCE_PUSH, FORCE_BUILD, FORCE_BUBBLE, FORCE_CRUSH, FORCE_NOVA,
    /** Electrokinesis: arms flung back as the body becomes the bolt (Ride the Lightning); palms cupped round a
     *  forming orb, then pushed out (Ball Lightning); a braced, straight-armed lance (Arc Lance); a palm laid on
     *  metal (Circuit Thread); fists clenched at the sides as current floods the body (Overcharge); a palm driven
     *  down into the ground (Ground Sink); a two-finger point that plants an anchor (Arc Lattice). */
    ELECTRO_RIDE, ELECTRO_CUP, ELECTRO_LANCE, ELECTRO_TOUCH, ELECTRO_OVERCHARGE, ELECTRO_GROUND, ELECTRO_POINT,
    /** Superspeed's skills: a boxer's guard (Reflex Focus), arms flung low and flickering (Speed Mirage), a fist
     *  driven through (Momentum Strike), a javelin throw (Lightning Throw), an arm round someone's shoulders
     *  (Slipstream Rescue) and open hands held perfectly still (Perception). */
    SPEED_GUARD, SPEED_SPLIT, SPEED_LUNGE, SPEED_THROW, SPEED_ESCORT, SPEED_STILL,
    /** Empathic Mimicry: an open palm laid on someone to keep their power; a hand drawn to the heart, then opened,
     *  to switch which remembered power answers. */
    MIMIC_TOUCH, MIMIC_SHIFT,
    /** Sonic Resonance. The core: arms swept back as the voice goes out (Cry) and braced wide and shaking while it is
     *  held (the scream); a plucked string (setting a Note) and a closing fist (breaking it); palms raised flat (Wall of
     *  Sound) and a two-handed shove that sends it rolling; arms thrown up as the cry at the ground launches you
     *  (Vault) and pinned back in the dive; arms gathering in to the chest (Crescendo building) and flung wide and back
     *  (its release). The skills: a hand cupped at the mouth (Focused Shriek), a raised finger-snap (Shatter Note),
     *  a low, wide howl (Dissonant Wave), a hand to the ear (Echo Map), arms crossed over the chest (Resonant Guard), a
     *  finger to the lips (Quiet Zone), a conductor's beat (Resonance Cascade), palms pressed down (Silence Dome) and
     *  arms opened in song (Battle Hymn). */
    SONIC_CRY, SONIC_SCREAM, SONIC_PLUCK, SONIC_CLENCH, SONIC_WALL, SONIC_SHOVE, SONIC_VAULT, SONIC_DIVE, SONIC_GATHER,
    SONIC_RELEASE, SONIC_CUP, SONIC_SNAP, SONIC_HOWL, SONIC_LISTEN, SONIC_HUM, SONIC_HUSH, SONIC_CONDUCT, SONIC_DOME,
    SONIC_SING,
    /** Quake. The core: a palm thrust out (Shockwave) and the other palm after it (Double Tap); a hand held out with the
     *  fingers spread and trembling (Resonate) closing into a fist (Shatter); a fist driven into the ground (Fault Line,
     *  a grounded Slam), both fists brought down from overhead (the airborne Slam) and both palms swept up (Upheaval);
     *  both palms out and shaking (Quake Blast) or thrust down and back (Quake Blast flight); arms low and wide, palms
     *  down (Earthquake) and both fists slammed down (its aftershock); a palm laid on the ground (Tremor Sense) and arms
     *  clutched to the chest (a fracture). The skills: an uppercut (Seismic Lance), a hand trailed along the ground
     *  (Fault Script), arms crossed then flung out (Vibration Shell), forearms crossed (Counterforce's brace) and a
     *  double palm push (its release), palms flung back (Pressure Step) and a palm on the wall (Surgical Fracture). The
     *  styles: fists driven down and pulled apart (Fissure Maw), palms swept up (Stone Rampart) and a wide, rooted stance
     *  (Bedrock Stance). */
    QUAKE_PALM, QUAKE_PALM_OFF, QUAKE_TUNE, QUAKE_CLENCH, QUAKE_PUNCH_DOWN, QUAKE_PLUNGE, QUAKE_UPHEAVAL, QUAKE_BEAM,
    QUAKE_FLY, QUAKE_RUMBLE, QUAKE_AFTERSHOCK, QUAKE_FEEL, QUAKE_FRACTURED, QUAKE_UPPERCUT, QUAKE_TRACE, QUAKE_SHELL,
    QUAKE_BRACE, QUAKE_RELEASE, QUAKE_DASH, QUAKE_TOUCH, QUAKE_SPLIT, QUAKE_RAMPART, QUAKE_ROOT,
    /** Regeneration. The core: hands pressed to the wound, then flexed out (Rapid Mending); a palm offered up (Blood
     *  Gift) and held out with the other hand gripping the forearm (Blood Stream); chest out, arms spread low, daring it
     *  (Indestructible), arms flung up (Freefall) and fists driven down on landing; arms opened to draw friends in (Blood
     *  Bond) and hands pulled to the chest then thrown open (Pay It Back); a hand pressing into a friend (Second Life).
     *  The skills: a cut palm flung across (Bloodletting), a palm drawing a wound in (Take the Wound), fists clenched and
     *  flexed (Adrenal Rush), fists tensed at the sides (Adaptation), an arm pushing up off the ground (getting up from
     *  Play Dead) and both hands pumping down (Revive's chest compressions). The styles: a fist beating the chest (Vital
     *  Burst), arms out, palms open, daring it (Undying) and palms raised (Lifebloom). */
    REGEN_MEND, REGEN_OFFER, REGEN_STREAM, REGEN_BRACE, REGEN_LEAP, REGEN_LAND, REGEN_EMBRACE, REGEN_PAYBACK, REGEN_INJECT,
    REGEN_FLING, REGEN_DRAW, REGEN_PUMP, REGEN_HARDEN, REGEN_RISE, REGEN_COMPRESS, REGEN_HEARTBEAT, REGEN_DEFY, REGEN_BLOOM,
    /** Telepathy. The core: two fingers at the temple, the other hand reaching toward the mind (Read Mind); a palm
     *  lowered, easing (Suggestion); both hands to the temples, then opened outward (Mind Link); a raised hand with the
     *  fingers hooked down as if on strings, twitching (Puppet); a hand drawn down over the face and flung out
     *  (Nightmare). The skills: a sharp two-fingered point (Drop It); fingers at both temples, head bowed (Far Hearing);
     *  hands weaving circles before the chest (Dream Double); an open palm held out, the other hand on the heart
     *  (Persuasion); a palm passed slowly before the eyes (Memory Walk); palms raised, then pressed out toward friends
     *  (Hive Link). The styles: hands at the temples thrown up at the point (Psychic Storm), a hand swept across the
     *  face like drawing a veil (Blind Spot) and hands clasped, then spread (Hive Mind). */
    TELE_READ, TELE_SUGGEST, TELE_LINK, TELE_PUPPET, TELE_NIGHTMARE, TELE_COMMAND, TELE_LISTEN, TELE_WEAVE, TELE_PERSUADE,
    TELE_MEMORY, TELE_HIVE, TELE_STORM, TELE_VEIL, TELE_GATHER,
    VIBE_PALM, VIBE_OPEN, VIBE_READ, VIBE_GUARD, VIBE_LOCK, VIBE_LANCE, VIBE_CROSS, VIBE_STEP,
    /** Space-Time's skills. A palm thrust out, fingers spread, that closes into a fist as the clock locks (Selective
     *  Stasis); a raised hand that snaps its fingers (the stasis released); a palm pressed down toward the ground (an
     *  anchor planted); a hand drawn to the chest and flicked open like a pocket watch (recall); a hand at the chest
     *  turning a crown backward (Second Chance); both hands framing a point ahead (Stasis Seal); one arm swept forward
     *  (the seal thrown); a blade drawn from the opposite hip and slashed across (Moment's Edge); a hand that twists a
     *  crown forward and flicks it at the target (Fast Forward). */
    TIME_HALT, TIME_SNAP, TIME_PLANT, TIME_RECALL, TIME_WIND, TIME_FRAME, TIME_THROW, TIME_DRAW, TIME_TURN,
    /** Density's skill gestures: a shoulder-first lunge through a body, arms trailing (Pass Through); one arm leading
     *  into a wall while the other waves friends on (Phase Carry); fists clenched low at the sides, braced (Harden);
     *  both arms flung out and down as the weight is let go (Rebound); a palm pressed down at a distant point
     *  (Sinkhole); both hands pressed down and closed together (Entombed); arms loose and open, drifting (Fade Through);
     *  arms raised overhead as you drop (Phase Fall); a hand thrust straight up to grab (Grasp from Below); a twist
     *  away with one arm across the body (Untouchable). */
    DENSITY_LUNGE, DENSITY_USHER, DENSITY_BRACE, DENSITY_BURST, DENSITY_PRESS, DENSITY_CLOSE, DENSITY_FADE, DENSITY_DROP,
    DENSITY_GRASP, DENSITY_SLIP
}
