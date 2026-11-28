"""
The stamina balance model for every power.

    py -3 tools/balance.py              # write power_balance.json, skill_trees.json and docs/stamina-balance.md
    py -3 tools/balance.py --check      # only report; exit 1 if the files are out of date

It reads every action's own cost and cooldown, as the code computes them, from the dump the game test
BalanceDumpGameTests writes (build/rewind-gametest-world/build/balance/actions.json; run the game tests once to make
it). The table stores both the balanced value and that base, and PowerBalance scales anything else the code computes
(another tier, another mode, a server's config) in proportion.

THE RULES (docs/stamina-balance.md has the reasoning)

Stamina pays for effect:
    2 per point of damage to one target, at the tier the move unlocks
      (a damage-over-time point counts 0.6, a multi-hit flurry 0.85, a telegraphed or dodgeable hit 0.75)
    +40% of that for each extra target the move usually catches (up to four)
    3 per second of hard control (a hold, a root, a lift, a fear), with the same +40% per target
    0.35 per block a move carries you, plus 2
    utility (information, building, portals, shields) priced by judgement, in the same units
    +15 for a tier-5 ultimate
and each key has a band, so a key never costs more or less than its place in the kit:
    slot 1 (basic)   6-16      slot 2   10-26      slot 3   14-34      slot 4   16-40      slot 5   55-95
When the formula leaves the band, the cost is clamped and the move's damage is the thing that changes
(DAMAGE_CHANGES below lists every one).

Cooldown follows cost by role, in seconds per point of stamina:
    basic 0.08 (min 0.5 s)   quick 0.15   skill 0.3   heavy 0.45   ultimate 0.75
Held powers (toggles, flights, beams, a stopped world) have a per-second drain instead of a long cooldown.

Unlock tiers: one new key at each stage, Power Level 1, 3, 6, 10, 15 (tiers 1-5). A kit whose first key is a toggle
(Flight, Superspeed, Fade, Shift...) opens its second key at Level 1 too, so the first level has something to use.

Skill-tree actives keep their designed order and are pulled into the same bands (normalize_skills below). Every class
ability is an Ultimate, with one stamina price and one long cooldown per role (ULTIMATES).
"""

import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build/rewind-gametest-world/build/balance/actions.json"
TABLE = ROOT / "src/main/resources/data/heroesevolved/power_balance.json"
TREES = ROOT / "src/main/resources/data/heroesevolved/skill_trees.json"
LANG = ROOT / "src/main/resources/assets/heroesevolved/lang/en_us.json"
DOC = ROOT / "docs/stamina-balance.md"
PORTAL_TUNING = ROOT / "src/main/resources/data/heroesevolved/end_portal_ritual.json"
END_BATTLE_TUNING = ROOT / "src/main/resources/data/heroesevolved/end_battle_ritual.json"
END_BATTLE = dict(channel_ticks=60, drain_per_second=8)
# A world ritual pays an exact bar, rather than an action discounted by rank or Supercharge.
# The first offering can be remembered while a lone player recovers and moves to its neighbor.
END_PORTAL = dict(minimum_power_level=16, channel_ticks=160, memory_ticks=2400,
                  opening_ticks=40, starting_stamina_fraction=0.95)
# What each core action cost before this model (November 2026), for the doc's "Was" column. Frozen: never regenerated.
BEFORE = json.loads((ROOT / "tools/balance-before.json").read_text(encoding="utf-8"))

BANDS = {1: (6, 16), 2: (10, 26), 3: (14, 34), 4: (16, 40), 5: (55, 95)}
ROLE_CD = {"basic": 0.08, "quick": 0.15, "skill": 0.3, "heavy": 0.45, "ultimate": 0.75}
SLOT = {"PRIMARY": 1, "SECONDARY": 2, "TERTIARY": 3, "QUATERNARY": 4, "QUINARY": 5}


def value(dmg=0.0, n=1, ctrl=0.0, blocks=0.0, util=0.0, ult=False):
    spread = 1 + 0.4 * min(max(n, 1) - 1, 4)
    v = 2.0 * dmg * spread + 3.0 * ctrl * spread + util
    if blocks:
        v += 0.35 * blocks + 2
    if ult:
        v += 15
    return v


# Each core action: unlock tier, role, the effect it pays for (at its unlock tier) and what it does.
# "fixed" sets the stamina outright (a toggle, a resource-spending move, a once-in-a-while power); "cd" sets the
# cooldown in seconds outright; "drain" is stamina per second while held; "per_block" is stamina per block moved.
A = {}


def act(action, power, tier, role, note, fixed=None, cd=None, drain=None, per_block=None, passthrough=False, **effect):
    A[action] = dict(power=power, tier=tier, role=role, note=note, fixed=fixed, cd=cd, drain=drain,
                     per_block=per_block, passthrough=passthrough, effect=effect)


# --- Telepathy
act("telepathy_read_mind", "telepathy", 1, "basic", "held read; builds Focus; 2 a second while held (up to 6 s)", util=8, drain=2)
act("telepathy_suggestion", "telepathy", 2, "heavy", "a 5 s command on one mind", ctrl=5, util=6)
act("telepathy_mind_link", "telepathy", 3, "skill", "links willing minds (no damage)", util=18)
act("telepathy_puppet", "telepathy", 4, "ultimate", "about 10 s in control of a body", ctrl=10, util=10)
act("telepathy_nightmare", "telepathy", 5, "ultimate", "5 s of fear on everyone linked", ctrl=5, n=4, util=15, ult=True)
# --- Superspeed
act("speed_accelerate", "superspeed", 1, "basic", "toggle; 3 a second while running (6 in Redline)", fixed=0, cd=0)
act("speed_blitz", "superspeed", 1, "quick", "four blows of 2.5 after a 22-block rush (a gap-closer: half its distance counts)",
    dmg=10 * .85, blocks=11, cd=2.5)
act("speed_snatch", "superspeed", 2, "skill", "takes items, shots and a weapon", util=16)
act("speed_cyclone", "superspeed", 3, "heavy", "3 s of wind: 1.2 + 0.3/tier every half second, lift, no air; "
    "4/s throughout the owned vortex, including its first ticks and anchored storm; Wind Arms on the same key also drains 4/s",
    dmg=(1.2 + .3 * 3) * 6 * .6, n=3, ctrl=2, drain=4)
act("speed_lightspeed", "superspeed", 5, "ultimate", "8 + tier + Momentum/5 to everything on a 48-block line",
    dmg=15, n=3, blocks=48, ult=True)
# --- Regeneration
act("regeneration_rapid_mending", "regeneration", 1, "heavy", "spends Regrowth on an instant heal (Regrowth limits it)", util=22, cd=15)
act("regeneration_blood_gift", "regeneration", 2, "skill", "heals a friend; held, a stream", util=16)
act("regeneration_indestructible", "regeneration", 3, "heavy", "10 s: no knockback, dulled pain, hard punches", util=32)
act("regeneration_healing_aura", "regeneration", 4, "heavy", "10 s: friends heal with you; you share their wounds", util=34)
act("regeneration_second_life", "regeneration", 5, "ultimate", "a friend heals fully and cheats death once (also 50 Regrowth)",
    fixed=55, cd=90)
# --- Flight
act("flight_toggle", "flight", 1, "basic", "toggle; 4 a second cruising, 9 boosting", fixed=0, cd=0)
act("flight_launch", "flight", 1, "quick", "Skyburst: a dash of up to 20 blocks", blocks=20, util=3)
act("flight_dive_bomb", "flight", 2, "skill", "5 + tier + Momentum on landing, a pressure wave", dmg=7, n=3)
act("flight_skyward_grab", "flight", 3, "skill", "seize and carry, then a throw", ctrl=3, dmg=5, util=2)
act("flight_cyclone", "flight", 5, "ultimate", "Tempest: lifts and bursts everything round its eye; 12/s while the storm runs", dmg=8, n=4, ctrl=3, ult=True, drain=12)
# --- Electrokinesis
act("electro_chain_lightning", "electrokinesis", 1, "basic", "4 + 1.5/tier, chaining at 75%", dmg=5.5, n=2)
act("electro_ball_lightning", "electrokinesis", 2, "skill", "an orb that lashes (3 + tier) and bursts (5 + tier)", dmg=12 * .75, n=2)
act("electro_thunderstrike", "electrokinesis", 3, "skill", "4 + 2/tier where you marked", dmg=10 * .75, n=3)
act("electro_ride_the_lightning", "electrokinesis", 4, "skill", "a bolt's dash; 4 + tier to what you pass", dmg=8, n=2, blocks=16)
act("electro_storm_surge", "electrokinesis", 5, "ultimate", "8 + tier + Charge to everything round you", dmg=13, n=4, ult=True)
# --- Vibe
act("vibe_blast", "vibe", 1, "basic", "held blast: 1.2 + 0.35/tier every 6 ticks while it drains", fixed=4, cd=.5, drain=9)
act("vibe_breach", "vibe", 2, "skill", "two-way portals", util=20)
act("vibe_read", "vibe", 3, "skill", "replays a scene's fights", util=12)
act("vibe_breach_guard", "vibe", 4, "skill", "a moving breach that swallows shots", util=22)
act("vibe_frequency_lock", "vibe", 5, "ultimate", "Rift Fall: everyone in the ring drops out of a sky breach, 6 + 2/tier and a slow", dmg=9 * .75, n=4, ctrl=2, ult=True)
# --- Matter Assembly
act("matter_assembly_barricade", "matter_assembly", 1, "basic", "a 3x2 crystal panel, floor or mirror (6 Matter)", util=8, cd=1.5)
act("matter_assembly_shard_lance", "matter_assembly", 1, "basic", "a shard up to 64 blocks; ricochets twice off your own matter", dmg=6)
act("matter_assembly_causeway", "matter_assembly", 2, "skill", "8 s of walking on air", util=20)
act("matter_assembly_cage", "matter_assembly", 3, "skill", "a Launch Pad for 20 s: throws friends, launches foes (4 Matter)", ctrl=3, util=10)
act("matter_assembly_cathedral", "matter_assembly", 5, "ultimate", "three turrets for 20 s", util=55, ult=True)
# --- Invisibility
act("invisibility_fade", "invisibility", 1, "basic", "toggle; 2 a second, +1 per friend", fixed=6, cd=.5)
act("invisibility_silent_step", "invisibility", 1, "quick", "Lens Shield: 3 s, 70% off frontal harm, stops shots", util=14)
act("invisibility_unseen_strike", "invisibility", 2, "quick", "Force Bridge: a 12-block span", util=14)
act("invisibility_shroud", "invisibility", 3, "heavy", "Bubble: 5 s hold, then a crush (6 + 2/tier)", ctrl=5, dmg=6, util=2)
act("invisibility_ghost_protocol", "invisibility", 5, "ultimate", "Force Nova: heavy damage and a throw in 12 blocks", dmg=14, n=4, ult=True)
# --- Telekinesis
act("telekinesis_push", "telekinesis", 1, "basic", "Shove: a palm along the cone (2 + tier/4)", dmg=2.25, ctrl=1)
act("telekinesis_grip", "telekinesis", 2, "skill", "hold (swing it through foes), then a throw", ctrl=4, dmg=6)
act("telekinesis_bullet_stop", "telekinesis", 3, "heavy", "catches every shot, then turns them", util=22)
act("telekinesis_disarm", "telekinesis", 4, "skill", "plucks a weapon away; a free second press flings it back", util=16)
act("telekinesis_storm", "telekinesis", 5, "ultimate", "lifts and slams everyone round you", dmg=10, n=4, ctrl=3, ult=True)
# --- Hydrokinesis
act("hydro_water_whip", "hydrokinesis", 1, "basic", "4 + tier along your aim", dmg=5)
act("hydro_crashing_wave", "hydrokinesis", 1, "skill", "5 + tier to what it carries", dmg=6, n=3)
act("hydro_octopus_form", "hydrokinesis", 2, "heavy", "10 s: arms lash (6 + tier), shots swallowed, a third off blows", util=30)
act("hydro_wave_rider", "hydrokinesis", 3, "skill", "6 s of surfing", blocks=30, util=8)
act("hydro_maelstrom", "hydrokinesis", 5, "ultimate", "a waterspout that drags and drowns; 12/s while the storm runs", dmg=10, n=4, ctrl=3, ult=True, drain=12)
# --- Power Amplification
act("amp_supercharge", "power_amplification", 1, "skill", "15 s Surge on an ally (+25% harm, -25% cost and cooldowns); held, an Overcharge (50 Charge)",
    util=14)
act("amp_red_lightning", "power_amplification", 1, "basic", "a bolt of 4 + tier (more with Charge), or a beam through a line",
    dmg=5.5)
act("amp_shock_therapy", "power_amplification", 2, "quick", "clears mind control, stuns and slows; stands a downed friend up", util=12)
act("amp_jumpstart", "power_amplification", 3, "quick", "doors, levers, furnaces x4 for 30 s, batteries, a mount x2", util=14)
act("amp_thousand_times", "power_amplification", 5, "ultimate", "every ally within 10 blocks overcharged, no backlash",
    util=45, ult=True)
# --- Shapeshifting
act("shapeshift_morph", "shapeshifting", 1, "basic", "a form pays its own size cost and drain", fixed=0, cd=1)
act("shapeshift_instinct", "shapeshifting", 2, "basic", "each form's move pays its own cost", fixed=0, cd=.3)
act("shapeshift_decoy", "shapeshifting", 3, "skill", "a shell that draws the hunt for 6 s", util=16)
act("shapeshift_reflection", "shapeshifting", 4, "skill", "become your attacker; its kind forget you", util=18, ctrl=1)
act("shapeshift_nightmare", "shapeshifting", 5, "ultimate", "5 s of fear in 12 blocks", ctrl=5, n=4, ult=True)
# --- Pyrokinesis
act("pyro_flare", "pyrokinesis", 1, "basic", "4 + tier and 4 s burning, splashing nearby", dmg=5 + 4 * .6, n=1.5)
act("pyro_flame_palm", "pyrokinesis", 1, "skill", "held cone; 12 a second while breathing", fixed=8, cd=3)
act("pyro_ring_of_fire", "pyrokinesis", 2, "heavy", "an 8 s wall of flame (4 + tier) that draws burning foes in", dmg=6, n=3, ctrl=1)
act("pyro_backdraft", "pyrokinesis", 3, "quick", "Flame Flight toggle; 6-10 a second", fixed=12, cd=4)
act("pyro_inferno", "pyrokinesis", 5, "ultimate", "Sunfall: a charged sun on the marked area", dmg=16 * .75, n=4, ult=True)
# --- Cryokinesis
act("cryo_frost_bolt", "cryokinesis", 1, "basic", "held stream; pulses pay as they go", fixed=8, cd=1)
act("cryo_flash_freeze", "cryokinesis", 2, "skill", "an ice wall or ramp", util=18)
act("cryo_ice_wall", "cryokinesis", 3, "quick", "Glacier Ride toggle; 4-8 a second", fixed=12, cd=4)
act("cryo_glacial_spikes", "cryokinesis", 4, "heavy", "Crystal Prison: a hold, then a shatter (5 + tier)", ctrl=3, dmg=9)
act("cryo_blizzard", "cryokinesis", 5, "ultimate", "Whiteout", dmg=10, n=4, ctrl=3, ult=True)
# --- Empathic Mimicry: an Echo pays the copied move's balanced cost (MimicryService), so the table passes it through.
for i in range(1, 6):
    act(f"mimicry_echo_{i}", "empathic_mimicry", i, "basic", "pays the copied move's own cost", passthrough=True)
# --- Induced Radioactivity
act("radiation_radiant_touch", "radiation", 1, "basic", "a cone of 3 + tier to up to six", dmg=4, n=3)
act("radiation_pulse", "radiation", 2, "skill", "Fission Leap: a blast and a launch; the slam is free", dmg=6, n=2, blocks=8)
act("radiation_emp", "radiation", 3, "heavy", "kills machines for 10 s, shoves, reveals, vents Core", util=20)
act("radiation_meltdown_beam", "radiation", 4, "skill", "a held beam that spends Core; 12 a second while held (up to 5 s)", fixed=18, cd=6, drain=12)
act("radiation_exploding_man", "radiation", 5, "ultimate", "Critical Mass: a 24-block nuclear blast after 25 s", fixed=120, cd=300)
# --- Density (earthbending)
act("phasing_shift", "phasing", 1, "basic", "Horse Stance toggle; holding it drains 3.3 a second (half with Wait and Listen). "
    "Shift: Seismic Stomp", fixed=4, cd=.3, drain=3.3)
act("phasing_reach", "phasing", 1, "quick", "Rock Strike: lift a stone (6 + tier) or hold for a boulder; the punch is free",
    dmg=7 * .85, util=2)
act("phasing_embed", "phasing", 2, "skill", "Earth Shackle: a 3 + tier s hold, 50% more taken; Crush (4 + tier) is free",
    ctrl=5, util=4)
act("phasing_sink", "phasing", 3, "quick", "Pillar (4 + tier and a throw, or a lift for you) or a wall that stops shots and "
    "shoves (5 + tier)", dmg=7 * .75, util=10)
act("phasing_hollow", "phasing", 5, "ultimate", "Rising Mountain: a telegraphed lift and slam, 8 + 2/tier to all on it",
    dmg=18 * .75, n=3, ctrl=3, ult=True)
# --- Space-Time Manipulation
act("time_blink", "time_manipulation", 1, "quick", "Fold Space: 4 to start, 0.3 for every block folded", fixed=4, cd=1.5, per_block=0.3)
act("time_slow", "time_manipulation", 1, "skill", "Between Seconds: held, 5 a second (recovery stops)", fixed=10, cd=2, drain=5)
act("time_stop", "time_manipulation", 3, "heavy", "Still World: held, 12/10/8 a second by tier (recovery stops)", fixed=20, cd=10, drain=12)
act("time_rewind", "time_manipulation", 4, "ultimate", "Rewrite the Moment: most of a tank, a long recovery", fixed=120, cd=240)
act("temporal_snapshot", "time_manipulation", 5, "ultimate", "Walk the Past: travel 35, recording 18, return free", fixed=35, cd=30)
# --- Sonic Resonance
act("sonic_cry", "sonic_resonance", 1, "basic", "tap: 4 + 1.2/tier in a narrow cone; held: 12 a second", dmg=5.2, n=1.5)
act("sonic_note", "sonic_resonance", 2, "skill", "a lure that plays 8 s", util=14)
act("sonic_wall", "sonic_resonance", 3, "heavy", "5 s that turns shots back", util=22)
act("sonic_vault", "sonic_resonance", 4, "quick", "a leap and a safe landing", blocks=12, util=8)
act("sonic_crescendo", "sonic_resonance", 5, "ultimate", "a charged cone, 6 + 10 x charge + tier, after up to 3 s of build", dmg=16 * .75, n=4, ult=True)
# --- Quake
act("quake_shockwave", "quake", 1, "basic", "3 + 1.5/tier, a throw, shots shaken to dust", dmg=4.5, ctrl=.7, util=2)
act("quake_resonate", "quake", 2, "skill", "3 s of shaking (1.5 + tier/2 a pulse), a stagger", dmg=15 * .6, ctrl=2)
act("quake_fault_line", "quake", 3, "skill", "a racing crack: (4 + tier) and a launch", dmg=7, n=3, ctrl=.5)
act("quake_blast", "quake", 4, "basic", "held beam; it drains per second", fixed=10, cd=0)
act("quake_earthquake", "quake", 5, "ultimate", "seconds of shaking, then an aftershock", dmg=12, n=4, ctrl=2, ult=True)

# Fighting styles (empty-handed clicks and double-taps): strike, finisher/kick/clap, dodge, back dodge, dodge cooldown.
STYLE_POWERS = ["pyrokinesis", "electrokinesis", "hydrokinesis", "superspeed", "quake", "cryokinesis", "phasing",
                "regeneration", "invisibility", "telekinesis", "vibe"]
STYLE = {"strike": (2, 2), "finisher": (5, 5), "dodge": (8, 8), "back_dodge": (11, 11)}
STYLE_OVERRIDES = {
    # A speedster's dodge is a step, not a leap; a brawler pays with his body and braces.
    ("superspeed", "dodge"): 6, ("superspeed", "back_dodge"): 9,
    ("quake", "finisher"): 6,
    ("regeneration", "dodge"): 6, ("regeneration", "back_dodge"): 9,
}
STYLE_DODGE_COOLDOWN = 18  # ticks
STYLE_DODGE_COOLDOWN_OVERRIDES = {"superspeed": 14}

# Finishers (combat/Takedowns): one entry a power, shared by both of its finishers; the cooldown is shared by every
# finisher. A kill gives 10 stamina back, so a finished fight costs less than one ended by swinging.
FINISHER_POWERS = ["pyrokinesis", "time_manipulation", "telepathy", "electrokinesis", "hydrokinesis", "cryokinesis", "quake",
                   "sonic_resonance", "radiation", "superspeed", "flight", "telekinesis", "phasing", "invisibility",
                   "regeneration", "vibe", "matter_assembly", "empathic_mimicry", "shapeshifting",
                   "power_amplification"]
FINISHER = (8, 60)  # stamina, cooldown in ticks

# Damage that moved to match its stamina (the code carries each change; this list documents them).
DAMAGE_CHANGES = [
    ("speed_cyclone", "Cyclone's wind: 2 + 0.5/tier every half second -> 1.2 + 0.3/tier (SpeedKit). It hit ultimate numbers "
                      "(about 21 per target at Level 6) from a slot-4 key."),
]

# November 2026 damage audit. These are measurements of the Java implementations, NOT desired damage inferred
# from their names. Damage is the full sequence on one representative target at the unlock tier. `extra` is
# separately weighted damage (burns, sickness, unequal chain hops). Utility has an explicit reserve; pure utility
# never gets damage invented for it. Temporary buffs, prepared statuses, banked incoming damage, spent secondary
# resources and optional upgrades remain conditional rewards, recorded in `note`, rather than assumed free damage.
# Class offense's compulsory 10% is included. Armour/resistance is not: this compares outgoing damage.
DAMAGE_AUDIT = {}


def reviewed(action, source, formula, before=0, after=None, *, n=1, factor=1, extra=0, old_extra=None,
             ctrl=0, util=0, blocks=0, cost=None, note=""):
    if action in DAMAGE_AUDIT:
        raise ValueError(f"Duplicate damage review: {action}")
    DAMAGE_AUDIT[action] = dict(source=source, formula=formula, before=before, after=before if after is None else after,
                                n=n, factor=factor, extra=extra, old_extra=extra if old_extra is None else old_extra,
                                ctrl=ctrl, util=util, blocks=blocks, cost=cost, note=note)


def utility_reviews(source, entries):
    for action, effect in entries:
        reviewed(action, source, "0: " + effect)


# Tree actives and signatures. Tiers below come from each node's `level`, not its row or its current user's level.
reviewed("quake_0_1", "quake/QuakeSkills#land", "7+tier -> 10+tier; airborne x2", 9, 12, factor=.75, ctrl=1)
reviewed("quake_fault_script", "expansion/ExpansionSkills#fault", "6, once per UUID along the entire fault", 6, n=3, factor=.75, ctrl=.25)
reviewed("quake_counterforce", "expansion/ExpansionSkills#release", "min(16,stored+3); cap 24 with Stolen Thunder", 3, n=2, util=8,
         note="Stored incoming damage is earned by bracing; it is not a flat 16 damage cast.")
reviewed("quake_2_1", "quake/QuakeSkills#step", "4+tier/2", 5, n=2, blocks=6, util=3)
utility_reviews("quake/QuakeSkills; expansion/ExpansionSkills", [("quake_1_1", "Vibration Shell deflects shots"), ("quake_seamfinder", "fractures blocks, no creature damage")])
reviewed("flight_2_1", "flight/FlightSkillService#tickThermal", "2 -> 6 on the single updraft; second pulse needs an upgrade", 2, 6, n=2, factor=.75, ctrl=1, util=6)
reviewed("flight_thunderclap", "flight/FlightSkillService#tickVacuum", "4+Momentum*.05 -> 7+Momentum*.05", 4, 7, n=3, factor=.75, ctrl=1, util=5)
utility_reviews("flight/FlightSkillService", [("flight_0_1", "rush to and rescue a friend"), ("flight_sky_catch", "Wind Sanctuary shields friends"),
    ("flight_1_1", "Barrel Roll evades and redirects shots"), ("flight_tailwind", "Jetstream carries friends and shoves foes, no damage")])
reviewed("cryokinesis_0_1", "cryo/CryoCombatSkills#tickForge", "6+tier -> 8+tier; one forged lance", 8, 10, factor=.75, util=6)
reviewed("cryokinesis_1_1", "cryo/CryoCombatSkills#release,impact; cryo/CryoService#coldHit", "six shards of 3+stored/8; equal simultaneous hits respect ordinary invulnerability", 3, n=2, factor=.75, util=20,
         note="Unlike StyleOps and HydroService, coldHit does NOT reset invulnerability: a close fan cannot deal 6*3 to one body. Stored incoming damage is conditional.")
reviewed("cryokinesis_2_1", "cryo/CryoCombatSkills#tick", "2 -> 5 in Cold Snap's single wave", 2, 5, n=3, factor=.75, util=12,
         note="Cold buildup has a utility reserve; a 2/3-second solid freeze requires existing frost.")
reviewed("cryokinesis_brittle_constellation", "cryo/CryoTechniqueService#tickStars", "4 -> 6 per unique hop (strong: 7 -> 9)", 4, 6, n=3, factor=.85, util=8)
utility_reviews("cryo/CryoTechniqueService", [("cryokinesis_rime_loom", "lays rime and chills; no direct hit"), ("cryokinesis_stillheart", "heals and protects in a cocoon")])
reviewed("regeneration_last_one_standing", "regeneration/LastOneStanding#release", "4+tier to everyone within 8 after a 6 s stand; + half of what she banked (cap 16)", 0, 8,
         n=3, ctrl=2, util=10, note="Six seconds of drawing every foe onto herself is soft control; the half-strength brace is the utility. Banked damage is earned by taking hits, so it is conditional.")
reviewed("quake_epicenter", "quake/Epicenter#heave", "6+tier to everyone standing in a 10-block ring after a 24-tick telegraph; dragged in and slowed", 0, 10, n=4, ctrl=1)
reviewed("flight_microburst", "flight/Microburst#slam", "6+tier inside a 6-block ring after a 20-tick telegraph; 2 s pin; outflow shove beyond", 0, 10, n=3, ctrl=1, util=4)
reviewed("telepathy_puppeteer", "telepathy/Puppeteer#cut", "3+tier to each of up to 4 puppets when the strings are cut, after 6 s of fighting each other", 0, 7, n=3, ctrl=3.5,
         note="Foes turned on each other is soft control: their own blows are the payoff, and one with nobody to fight only hangs still.")
reviewed("radiation_fission_star", "radiation/FissionStar#detonate", "8+tier within 5 with line of sight after a 30-tick charge; sickness and a 5 s zone", 0, 12, n=3, util=6)
reviewed("sonic_resonance_standing_wave", "sonic/StandingWave#pulse,shatter", "four pulses of 1+tier/4, then 6+tier on the fifth beat, inside a 6-block column", 0, 18, n=3, ctrl=1, factor=.75,
         note="Only a foe that stays in the column takes every pulse; the pull is soft control.")
reviewed("superspeed_lightning_round", "speed/LightningRound#strike", "4+tier to each of up to 6 foes, one every 3 ticks", 0, 8, n=4, util=6,
         note="Untouchable for the second it lasts, and back where it began.")
reviewed("invisibility_bastion", "invisibility/Bastion#burst", "3+tier to every foe within 9 when the dome bursts", 0, 7, n=3, ctrl=1, util=20,
         note="Six seconds of reflected shots and halved damage for friends under it is the utility; foes are shoved out.")
reviewed("phasing_mountain_armor", "phasing/MountainArmor#blast", "5+tier within 5 when the plates blast off", 0, 9, n=3, util=16,
         note="Eight seconds of +10 armor, +4 toughness and no knockback is the utility; the punch bonus is conditional on landing blows.")
reviewed("empathic_mimicry_chorus", "mimicry/Chorus#sing", "five bolts of 2+tier/2 at the nearest foe, then one 2+tier/4 bolt per voice (3 at least)", 0, 29, n=2, factor=.7,
         note="Each bolt picks the nearest foe in sight at that moment, so the total spreads; voices add small riders.")
reviewed("matter_assembly_ballista", "assembly/Ballista#tick", "six bolts of 2+tier/2, one a second, at the nearest foe within 18", 0, 24, n=1,
         note="Also spends 6 Matter, all returned when it folds.")
reviewed("hydrokinesis_leviathan", "hydro/Leviathan#bite", "three telegraphed bites of 4+tier/2 at the nearest foe within 10 of the pool", 0, 18, n=2, ctrl=1, factor=.9)
reviewed("power_amplification_overdrive", "amplify/Overdrive#discharge", "4+tier per fork, one fork per linked body (3 alone)", 0, 8, n=3, util=20,
         note="Eight seconds of Surge and 4 stamina a second for every linked friend is the utility.")
reviewed("cryokinesis_absolute_zero", "cryo/AbsoluteZero#release", "4+tier cold hit on each drained foe (up to 16) after a 30-tick channel; 3 s solid freeze", 0, 8,
         n=4, ctrl=3, note="Returns 4 stamina per foe drained (capped 24); a frozen foe shatters for more when struck, as Crystal Prison's do.")
reviewed("electrokinesis_0_1", "skills/SkillActionService#activate", "8+tier on primary, two hops of 4", 10, n=1, extra=3.2, util=0,
         note="Two weaker hops count at +40% each: 2*4*.4. Slow is soft control, not a root.")
reviewed("electrokinesis_circuit_thread", "expansion/ExpansionSkills#activate", "5+tier, one hit per UUID across all conductors", 8, n=2)
reviewed("electrokinesis_ground_sink", "expansion/ExpansionSkills#activate", "2+min(16,charge) -> 4+.5*min(16,charge)", 2, 4, n=3, util=20, cost=40,
         note="Plant AND discharge each pay 20; 20 of that buys projectile absorption. Charge is absorbed incoming damage.")
reviewed("electrokinesis_arc_lattice", "expansion/ExpansionSkills#tick", "6 once per UUID (10 with Storm Geometry)", 6, n=2, util=8, cost=28,
         note="At least two paid anchors (14 each) are needed for a damaging wire; adding anchors resets its hit set and pays again.")
reviewed("electrokinesis_blackout", "electro/BlackoutService#release", "5+tier; one release after windup", 9, n=3, factor=.75, util=20,
         note="Machines are shut down and Charge is gathered; darkness/slow are utility, not hard control.")
utility_reviews("skills/SkillActionService#activate", [("electrokinesis_1_1", "Static Screen reflects shots"), ("electrokinesis_2_1", "Overcharge fills Charge and grants speed/haste")])
reviewed("telekinesis_0_1", "telekinesis/TelekinesisSkillService#land", "Vector Lance 10 -> Pin: 6 (+.5 per tier over 2) against the wall, then 3 s held", 10, 6, ctrl=3,
         note="A foe that meets no wall is held 1.5 s and takes nothing; Pinned Together carries two more foes along for the same blow.")
reviewed("telekinesis_kinetic_volley", "telekinesis/TelekinesisSkillService#tickJuggle", "Kinetic Volley 3 shards*4 -> Juggle: 3 pops*2 on each of up to 3 foes", 12, 6,
         factor=.85, n=3, ctrl=1.5, note="Each foe spends about two seconds in the air. Five at Once adds two foes and a pop; Spike is a separate free slam.")
reviewed("telekinesis_skyfall", "telekinesis/TelekinesisSkillService#tickSkyfall", "12+tier to the hauled foe after a 40-tick haul and drop; 6+tier/2 within 5 blocks",
         0, 16, extra=2*8*.4, ctrl=2, note="Two bystanders count at +40% each; the hauled foe is held for two seconds. The ring telegraphs the area.")
utility_reviews("telekinesis/TelekinesisSkillService", [("telekinesis_remote_hand", "Beckon works controls and fetches items"), ("telekinesis_1_1", "Round Up gathers and tosses, no direct damage"),
    ("telekinesis_2_1", "Vector Step moves the caster"), ("telekinesis_force_bubble", "Levitate hovers the caster (and friends with Raise the Party)")])
utility_reviews("telepathy/TelepathySkills", [("telepathy_0_1", "Drop It disarms"), ("telepathy_persuasion", "persuades or frightens one mind"),
    ("telepathy_1_1", "Far Hearing relays voices"), ("telepathy_memory_walk", "replays a memory"), ("telepathy_2_1", "Dream Double draws attention and frightens on contact"), ("telepathy_hive_link", "links willing voices")])
reviewed("regeneration_0_1", "regeneration/RegenSkills#bloodletting", "4+tier to undead; same amount heals bonded friends", 6, n=2, util=6)
reviewed("regeneration_2_1", "regeneration/RegenSkills#damage", "AMBUSH_DAMAGE=6 on the next blow after standing", 6, util=10,
         note="Only the added 6 is bought by Play Dead; the normal blow has its own cost.")
utility_reviews("regeneration/RegenSkills; expansion/AdvancedTechniques", [("regeneration_take_the_wound", "takes a friend's wound"), ("regeneration_1_1", "Adrenal Rush heals and buffs"),
    ("regeneration_adaptation", "learns damage types"), ("regeneration_revive", "revives a downed friend")])
reviewed("time_manipulation_shared_blink", "time/SpaceTimeSkills#edge", "6+tier -> 2+tier, one cut per target", 9, 5, n=3, ctrl=.8, blocks=12,
         note="EDGE_STEP=4; three targets are held for 16 ticks. Thousand Cuts adds a cut and linger after buying its upgrade.")
reviewed("time_manipulation_singularity", "time/SingularityService#pull,collapse", "9*(3+.5*tier) + (8+tier) collapse -> 9*(.6+.1*tier) + (3+.5*tier)",
         45, 9, n=3, factor=.6, extra=5*.75, old_extra=12*.75, ctrl=1.5, util=5,
         note="Ticks 10..90: nine tears; tick 100 collapses. Pull is partial control (1.5 s equivalent), not a 5 s root; core falloff tops at 1.")
utility_reviews("time/SpaceTimeSkills", [("time_manipulation_0_1", "Selective Stasis holds one body"), ("time_manipulation_stasis_seal", "Stasis Seal holds/throws stopped time"),
    ("time_manipulation_1_1", "Spatial Anchor saves a return point"), ("time_manipulation_2_1", "Second Chance rewinds health/position"),
    ("time_manipulation_accelerate", "Fast Forward accelerates existing burns/poison; those were paid by their source, heals friends and ages mobs")])
reviewed("radiation_0_1", "radiation/RadiationSkills#lance", "8+tier -> 4+.5*tier; 7 s stage-I sickness adds 3.5", 10, 5, n=2, extra=3.5*.6,
         note="Venting 15 Core doubles the lance and adds another sickness stage; Core and existing sickness are conditional resources.")
reviewed("radiation_heat_vent", "radiation/RadiationSkills#heatVent", "3+vented/5 -> 1.5+vented/30; 4 s burning", 3, 1.5, n=2, extra=4*.6, util=3,
         note="Core vented is additional payment; Chain Vent detonates a previously dosed foe.")
utility_reviews("radiation/RadiationSkills", [("radiation_1_1", "Lead Skin reduces harm"), ("radiation_reactor_core", "Reactor Core toggles heat gain"),
    ("radiation_2_1", "Decontaminate cleans sickness/ground"), ("radiation_glowing_path", "Glowing Path lays fallout; see its ground tick in NuclearFallout (separate audit below)")])
reviewed("sonic_resonance_0_1", "sonic/SonicSkills#focused", "9+tier, one target; leaves Ringing", 11, util=2)
reviewed("sonic_resonance_1_1", "sonic/SonicSkills#dissonant", "3 (6 on a foe already drawn by a separately paid Note)", 3, n=3, util=12)
reviewed("sonic_resonance_shatter_note", "sonic/SonicSkills#shatter", "4; one burst", 4, n=3, util=2)
utility_reviews("sonic/SonicSkills", [("sonic_resonance_echo_map", "Echo Map locates entities"), ("sonic_resonance_2_1", "Resonant Guard reflects shots"), ("sonic_resonance_quiet_zone", "Quiet Zone silences")])
reviewed("superspeed_1_1", "speed/SpeedService#skill", "6+tier, plus a swept rush up to 18.4 blocks", 8, blocks=18.4, util=1)
reviewed("superspeed_lightning_throw", "expansion/AdvancedTechniques#activate", "6+Momentum/10; requires at least 30 Momentum", 9,
         note="Spends ALL banked Momentum (30..100): 9..16 damage; Charged Throw's extra hops require another upgrade.")
utility_reviews("speed/SpeedService; expansion/AdvancedTechniques", [("superspeed_0_1", "Reflex Focus evades projectiles, each evasion pays again"), ("superspeed_mirage", "Speed Mirage distracts; Time Remnant damage is a separate purchased upgrade"),
    ("superspeed_2_1", "Slipstream Rescue carries a friend"), ("superspeed_perception", "Perception slows the world")])
reviewed("pyrokinesis_0_1", "pyro/PyroService#skill", "6+tier plus 5 s burn; lash movement itself does no second hit", 8, extra=5*.6)
reviewed("pyrokinesis_ember_mine", "pyro/PyroService#detonate", "6+tier plus 5 s burn; exactly 30% high, retained", 9, n=2, factor=.75, extra=5*.6)
reviewed("pyrokinesis_1_1", "pyro/PyroService#tickMantles", "8 pulses of 1.5 -> .1, plus up to 10 s of continually refreshed burning", 12, .8, n=2, factor=.6, extra=10*.6, util=8)
reviewed("pyrokinesis_blue_lance", "pyro/PyroService#blueLance,tickLances", "(8+tier)*1.5 + up to four 2.5 trails -> (3+.5*tier)*1.5 + up to four .5 trails; 6 s burn", 26.5, 8.75, n=2, extra=6*.6,
         note="Blue Lance is ALWAYS blue, including at zero Heat; its 1.5 multiplier must be counted. Thirteen-tick trail pulses on global ticks divisible by four (three or four pulses).")
reviewed("pyrokinesis_phoenix", "pyro/PhoenixService#strike,graze", "7+tier on up to 3 distinct hunted foes, 6 s burn; grazes are 3 + 3 s burn", 11, n=3, factor=.75, extra=6*.6, util=10,
         note="Hunts different UUIDs; cannot repeatedly strike one foe. Blue Heat multiplies direct damage by 1.25; return heals the owner.")
utility_reviews("pyro/PyroService", [("pyrokinesis_2_1", "Cauterizing Flame heals/cleanses"), ("pyrokinesis_fire_shepherd", "Fire Shepherd retrieves existing fire as Heat")])
reviewed("invisibility_2_1", "invisibility/ForceFieldService#pinpoint", "6+tier; launches a foe", 8, ctrl=.5)
reviewed("invisibility_cloak_field", "invisibility/ForceFieldService#kineticShell,finishCrush", "(6+2*tier+1.5*held) -> same*.12 for Shell bubbles only; hold up to 3 s", 16.5, 1.98, n=4, factor=.75, ctrl=3,
         note="Representative crush after 3 s; core Bubble keeps its own damage. Tight Seal and Unseen Force are optional multipliers.")
utility_reviews("invisibility/InvisibilitySkills; invisibility/ForceFieldService; invisibility/InvisibilityService", [("invisibility_0_1", "Vanishing Touch hides another body"), ("invisibility_smoke_and_mirrors", "Vanishing Squad hides friends"),
    ("invisibility_1_1", "Force Barrier builds a shield"), ("invisibility_pickpocket", "Force Disc carries the caster")])
reviewed("phasing_0_1", "phasing/Stones#damage,gauntlet", "4+tier; one gauntlet grabs or pins", 6, ctrl=3)
reviewed("phasing_phase_carry", "phasing/Stones#damage,avalanche", "five*(5+tier) -> five*(.8+.2*tier)", 40, 7, n=3, factor=.85, util=3,
         note="Five boulders have independent hit sets: one body can be hit five times. Root/material are conditional multipliers.")
reviewed("phasing_1_1", "phasing/DensitySkills#rebound", "min(18,4+.6*stored); 4 before receiving blows", 4, n=2, util=8,
         note="Stone Skin's stored damage is paid by receiving blows; its shield gets a utility reserve.")
reviewed("phasing_phase_fall", "phasing/Earthworks#tickSurf", "3+.5*tier once per UUID over the entire surf", 4.5, n=2, blocks=12, util=4)
utility_reviews("phasing/DensitySkills; phasing/DensityService", [("phasing_phase_lock", "Quicksand roots; no direct hit"), ("phasing_2_1", "Tunnel moves underground; surface punch belongs to the same move")])
reviewed("empathic_mimicry_0_1", "mimicry/MimicSkills#borrowedStrike", "6+tier; one Borrowed Strike, plus the channelled power's touch (Chorus.touch: burn, slow, sicken, shove, or half again on one foe nearby) as a conditional rider", 8, util=2)
utility_reviews("mimicry/MimicSkills", [("empathic_mimicry_sense_powers", "detects powers"), ("empathic_mimicry_1_1", "Empathic Surge: Strength for friends within 8"),
    ("empathic_mimicry_overcharge", "strengthens a copied move, which still pays its own stamina"), ("empathic_mimicry_2_1", "Calm lifts harmful effects within 6"), ("empathic_mimicry_absorb_pain", "takes an ally's pain")])
reviewed("matter_assembly_scattershot", "assembly/MatterAssemblyService#activate,firePaid,tickShard", "five projectiles of 5+.5*tier; simultaneous equal hits respect invulnerability", 6, n=3, factor=.75,
         note="tickShard does NOT reset invulnerability: ordinary close overlap deals 6, not 30, to one body. Five rays can hit different bodies. Ricochet and airborne bonuses are conditional rewards for setup.")
utility_reviews("assembly/MatterAssemblyService", [("matter_assembly_faultline", "Kaleidoscope: a mirror ring that holds a foe; its damage is the Shards you fire in, each paid separately"),
    ("matter_assembly_switchback", "builds a route"), ("matter_assembly_drawbridge", "raises a bridge"),
    ("matter_assembly_shelter", "builds shelter"), ("matter_assembly_cradle", "rescues and supports a friend")])
reviewed("vibe_0_1", "vibe/VibeService#release", "7+1.5*tier; travelling narrow wave", 10, factor=.75, util=2)
reviewed("vibe_reverb", "vibe/VibeService#release", "7+1.5*tier; both palms SHARE the UUID hit set", 13, n=2, factor=.75)
reviewed("vibe_prism_lock", "vibe/VibeService#tickLock", "4+tier; one Rift Fall; previously Read adds 4", 8, n=3, factor=.75, ctrl=1,
         note="Prism Lock's delay/slow has a control reserve; Read bonus needs a separate cast.")
reviewed("vibe_rift_beast", "vibe/VibeBeast#summon; vanilla HoglinBase#hurtAndThrowTarget", "45 s / 2 s attack interval; attack attribute 6+tier -> 1.8+.1*tier; random roll f/2+nextInt(int(f))", 22*9.5, 22*1.6, factor=.6, util=10,
         note="Unlock tier 4: 900 ticks, 22 complete 40-tick attack cycles; one continuously reachable foe, no baby bonus. Pet also tanks and commands.")
utility_reviews("vibe/VibeBreaches; vibe/VibeSight", [("vibe_1_1", "Glimpse leaves the body to scout"), ("vibe_flashstep", "Flashstep travels through a breach"), ("vibe_2_1", "Co-Vibe shares a read")])
utility_reviews("shapeshift/ShapeshiftSkills", [("shapeshifting_0_1", "Quick Change bypasses a form swap"), ("shapeshifting_shed", "Shed cleanses and drops a decoy"),
    ("shapeshifting_1_1", "Hybrid borrows form traits; Instinct's attacks pay their own cost"), ("shapeshifting_chimera", "Chimera borrows traits; Instinct pays separately"),
    ("shapeshifting_apex_form", "Apex doubles attack attributes for its duration; base damage comes from the separately paid form/Instinct or weapon"),
    ("shapeshifting_2_1", "Borrowed Face disguises the caster"), ("shapeshifting_doppelganger", "Doppelganger copies a body and hides the caster")])
reviewed("hydro_0_1", "hydro/HydroSkills#burst", "6+tier; one marked Geyser", 8, n=2, factor=.75, ctrl=.75)
reviewed("hydro_pressure_jet", "hydro/HydroSkills#tickJet", "19*(1.5+.25*tier) -> 19*(.65+.1*tier) over 40 ticks, ages 2..38", 47.5, 19.95, n=1, factor=.6,
         note="The 40-tick endpoint exits before hitting. Water depletion can weaken it; full water is the baseline.")
reviewed("hydro_water_spout", "hydro/HydroSkills#tickSpout", "5+tier on the landing; no damage from the rising pillar", 9, n=2, factor=.75, blocks=8, util=2)
utility_reviews("hydro/HydroSkills", [("hydro_1_1", "Tide Wall swallows shots"), ("hydro_2_1", "Healing Water heals/cleanses"), ("hydro_rain_call", "Rain Call refills water and wets the area")])
reviewed("amp_1_1", "amplify/AmpSkills#vanFull", "6+tier+spent/10 -> spent/30; enclosure and Second Coming x2 each", 8, 0, n=2, ctrl=5,
         note="The five-second area stun already uses the 20-stamina budget. Extra damage now needs spent Charge; nonlethal health-1 cap remains.")
reviewed("amp_doorbuster", "amplify/AmpSkills#doorbuster", "3 -> 1.5 around the broken door; three-second stun", 3, 1.5, n=1, ctrl=3, util=6)
utility_reviews("amplify/AmpSkills", [("amp_0_1", "Partner bonds an ally"), ("amp_hold_hands", "shares a separately paid Surge"),
    ("amp_overcharge_enemy", "makes the next enemy power misfire; damage depends on that power"), ("amp_2_1", "Burnout disables a power or projector")])

# Every class is an Ultimate (combat/Ultimates). An Offense Ultimate holds its foe for the whole cinematic (hard
# control for that long) and lands its damage on it; anyone near the finish takes a splash, counted as two bystanders at
# the usual 40%. Offense's compulsory 10% is included. Defense and Support Ultimates are shields, heals and buffs: their
# retaliation and end bursts only land on foes who strike or stay close, so they are recorded as conditional riders.
for action, held, splash, ctrl, formula in [
    ("amp_lightning_cannon", 21.5, 5, 5, "five jolts of 1.5, then 14 as the ball is driven home; 5 to up to four near (AmpUltimates.FeedbackLoop)"),
    ("matter_assembly_forge_cannon", 20, 6, 5.5, "2 as the cage shuts, 18 from the hammer; shards 6 within 6 (AssemblyUltimates.GrandDesign)"),
    ("cryokinesis_hailstorm", 21, 6, 5, "3 as the prism seals, 18 as the glacier shatters it; 6 within the impact (CryoUltimates.GlacierFall)"),
    ("electrokinesis_tesla_spire", 22, 4, 5.5, "five bolts of 2 in the cloud, 12 on the slam; 4 arcs into up to four near (ElectroUltimates.Thunderhead)"),
    ("flight_tempest_drill", 21, 6, 6.5, "2 on the tackle, 19 on the drive down; 6 within 6 of the crater (FlightUltimates.Stratosphere)"),
    ("hydro_tsunami", 25, 9, 5.5, "six tumbles of 1.5, 6 from the geyser, 10 from the slam; the closing barrel 6 and the whirlpool 3 to those near (HydroUltimates.RiptideBarrel)"),
    ("invisibility_shadow_strike", 22, 6, 5, "four slams of 2.5, 12 as the box bursts; shards 6 within 5 (ForceUltimates.CrushBox)"),
    ("empathic_mimicry_prismatic_overflow", 21.5, 4, 5.5, "one 2.5 strike per remembered power (three at least, five at most; three counted), 14 from the braided lance; 4 within 4 (MimicUltimates.EverythingAtOnce)"),
    ("phasing_phase_eruption", 22, 6, 5.5, "8 as the walls clap, 2 from the pillar, 12 from the boulder; 6 within 4 (DensityUltimates.EarthsEmbrace)"),
    ("pyrokinesis_sunfall", 23, 6, 5.5, "2 from the knee, six blows of 1.5, 4 from the wheel, 8 from the axe kick, with burning; 6 within 5 (PyroUltimates.RisingSun)"),
    ("quake_fissure_maw", 23, 6, 5.5, "seven shakes of 1, 12 from the blast, 4 as the fault erupts; 6 along the fault (QuakeUltimates.Heartquake)"),
    ("radiation_ground_zero", 21, 8, 5.5, "five burns of 1, 16 from the blast and a stage-II dose; 8 and a stage-I dose within 8 (RadiationUltimates.PointOfNoReturn)"),
    ("regeneration_vital_burst", 23, 4, 5, "six blows of 1.5, 14 over the knee; 4 within 4; costs her 4 health, healed back as she lands blows (RegenUltimates.CantStopMe)"),
    ("shapeshifting_many_hands", 24, 5, 5, "2 on the pounce, four mauls of 2.5, 12 on the slam; 5 within 5 (ShapeUltimates.ApexPredator)"),
    ("sonic_resonance_resonance_cascade", 25, 6, 5.5, "five notes of 3, 10 from the scream; 6 in the cone (SonicUltimates.FinalMovement)"),
    ("superspeed_lightning_flurry", 25, 6, 6.5, "3 from the jab, 10 from the uppercut, 2 on the way down, 10 on landing; 6 within 5 (SpeedUltimates.AroundTheWorld)"),
    ("telekinesis_implosion", 22, 6, 5.5, "four swings into rock of 2.5, 12 from the spike; 6 within 5 as the ball bursts (KineticUltimates.WreckingHand)"),
    ("telepathy_psychic_storm", 22, 4, 5.5, "three fears of 2, 16 from the spike, then a stun; 4 to minds near, which turn on one another (TelepathyUltimates.Mindbreak)"),
    ("time_manipulation_hour_hand", 22, 6, 5, "eight cuts in stopped time, all landing at once for 22; 6 within 4 (TimeUltimates.TenPastTen)"),
    ("vibe_crosswave", 24.5, 5, 5.5, "five blasts of 2.5 between breaches, 12 from the fall; 5 within 4 (VibeUltimates.DimensionDrop)"),
]:
    reviewed(action, "combat/Ultimates; " + formula[formula.rindex("(") + 1:-1], formula[:formula.rindex(" (")], 0, held,
             factor=1.1, extra=splash * .8, old_extra=0, ctrl=ctrl,
             note="An Offense Ultimate: the foe is held for the whole cinematic, so the hold is its control; the splash is two bystanders at 40%.")
utility_reviews("combat/Ultimates (Defense and Support)", [
    ("amp_lights_back_on", "Red Armour: 12 s supercharged, blows 40% lighter; a fork of 2 back into each striker and 3 into the nearest as it burns out (conditional)"),
    ("amp_got_your_back", "Everyone, Now: 12 s Surge for every friend within 14"),
    ("matter_assembly_bulwark_lattice", "Shell of Twelve: each panel stops shots from its side; halves blows once whole"),
    ("matter_assembly_mending_lattice", "Field Hospital: 14 s of mending, cleansing and a layer of hearts under a roof that stops shots"),
    ("cryokinesis_crystal_bastion", "Winter Citadel: walls stop shots, freeze and repel; blows a third lighter; shards as it ends"),
    ("cryokinesis_aurora_veil", "Hibernation: frost armour, a mending cocoon once, chill on foes"),
    ("electrokinesis_faraday_cage", "Storm Shell: shots struck down, strikers shocked and jammed, banked charge poured into the nearest (conditional)"),
    ("electrokinesis_jumpstart", "Restart: three beats, then health, cleansing, stamina and a 12 s boost for friends within 16"),
    ("flight_gale_wall", "Eye of the Storm: shots flung away, foes thrown out, blows lighter; 4 to those near when it breaks (conditional)"),
    ("flight_formation_flight", "Airlift: friends lifted out of reach, mending, shots halved"),
    ("hydro_tidal_dome", "Undertow Dome: shots sink, foes swept out and soaked, a third less harm inside; 4 to those near as it falls (conditional)"),
    ("hydro_healing_spring", "Monsoon: 15 s of rain that mends and cleanses, two springs, soaks and slows foes"),
    ("invisibility_refraction_cloak", "Aegis: shots turned back at the shooter, blows under half, strikers rippled back"),
    ("invisibility_prism_haze", "Vanishing Act: friends hidden in 10-point bubbles, foes dazzled"),
    ("empathic_mimicry_empathic_ward", "Mirror Soul: blows halved, the other half (up to 8) sent back at the striker (conditional)"),
    ("empathic_mimicry_kindred_light", "Shared Gift: heals and cleanses friends and lends each a remembered power for 15 s"),
    ("phasing_dense_bulwark", "Stone Fortress: half harm, slabs stop shots, close foes shackled; 5 from each flung slab (conditional)"),
    ("phasing_phase_field", "Mountain Hall: a dome that stops shots, pushes foes out, mends and halves harm"),
    ("pyrokinesis_ember_shroud", "Living Flame: fireproof, blows 40% lighter, shots burnt up, strikers ignited and thrown; a ring of fire as it ends (conditional)"),
    ("pyrokinesis_hearthfire", "Bonfire Vigil: 15 s of healing, warmth and a flame cocoon once; foes driven back burning"),
    ("quake_stone_rampart", "Pressure Barrier: shots turned back, strikers blown away for 2, a third less harm; 3 as it pops (conditional)"),
    ("quake_bedrock_stance", "Groundswell: friends can't be knocked down, take less harm, and their blows carry a shockwave"),
    ("radiation_containment_dome", "Lead Heart: shots swallowed, blows 40% lighter and banked as heat; the blast is 2 + banked/3, at most 10 (conditional)"),
    ("radiation_reactor_hearth", "Cherenkov Hearth: 15 s of mending, a layer of hearts, faster stamina and cured sickness; foes close are dosed"),
    ("regeneration_undying", "Unkillable: the last heart can't be taken, blows halved and banked, a heart a second; the banked burst is conditional"),
    ("regeneration_lifebloom", "Lifeblood: half health back to friends within 16 and one caught killing blow each"),
    ("shapeshifting_plate_mail", "Living Fortress: blows under half, no knockback, 2 back to each striker, 4 as the plates burst (conditional)"),
    ("shapeshifting_masquerade", "Many Faces: four copies draw every hunter; friends mend and move quicker"),
    ("sonic_resonance_silence_dome", "Dead Air: shots drop, foes inside hushed, weakened and slowed; 4 to those inside as it breaks (conditional)"),
    ("sonic_resonance_battle_hymn", "Anthem: chords every 2 s that mend, strengthen and harden friends"),
    ("superspeed_phase_blur", "Between Heartbeats: three illusions fight enemies within 32 for 12 s, 2 a blow (about one a second each, three blows to break one); blows on you at half (conditional)"),
    ("superspeed_relay_run", "Rescue Run: every friend within 32 brought to your side, healed 6 + tier, cleansed and quickened"),
    ("telekinesis_debris_halo", "Stop Everything: shots stopped ten blocks out and thrown back, foes held at arm's length"),
    ("telekinesis_gathering_hand", "Cradle: friends gathered under two hands that stop shots, swat foes back for 2 and halve harm"),
    ("telepathy_blind_spot", "Not Here: hunters within 24 lose you and follow three copies"),
    ("telepathy_hive_mind", "One Mind: up to eight friends share wounds, voices, sight of foes, haste, strength and mending"),
    ("time_manipulation_time_loop", "Second Chance: a killing blow sends you back to the marked moment and freezes your attackers"),
    ("time_manipulation_borrowed_time", "Borrowed Hours: friends quickened, 20 stamina back and cooldowns halved; foes slowed"),
    ("vibe_mirror", "Between Worlds: blows pass through you, shots go back out beside the shooter"),
    ("vibe_sanctuary", "Safe Harbor: friends brought through breaches to your side and hidden from hunters while they mend"),
])

# Every fighting move is reviewed separately, even when two hands share a formula or two moves share a price.
# Knockback impulses are not a multi-second root; only sustained lifts/holds get the hard-control rate.
# A dodge's price covers motion/evasion plus its conditional counter; a free loaded strike belongs to that dodge.
STYLE_FILES = {"pyrokinesis":"pyro/PyroCombat", "electrokinesis":"electro/ElectroCombat", "hydrokinesis":"hydro/HydroCombat",
               "superspeed":"speed/SpeedStyle", "quake":"quake/QuakeStyle", "cryokinesis":"cryo/CryoStyle",
               "phasing":"phasing/EarthStyle", "regeneration":"regeneration/RegenStyle", "invisibility":"invisibility/ForceStyle",
               "telekinesis":"telekinesis/KineticStyle", "vibe":"vibe/VibeStyle"}
for power, strike_old, strike_new, fin_old, fin_new, low_old, low_new in [
    ("pyrokinesis",2.4,1,5,.8,3.5,.8), ("electrokinesis",2.4,1,5,1.2,3.5,1.2),
    ("hydrokinesis",2.4,1,10.5,2.55,3.5,1.2), ("superspeed",3.9,1.17,5,1.2,3,1),
    ("quake",2.4,1,5,1.6,3.5,1.6), ("cryokinesis",2.4,1,6,2,2.8,1),
    ("phasing",2.75,.85,6.5,1.2,2.8,1), ("regeneration",3.5,1,6,2,4,1.6),
    ("invisibility",2.4,1,5,1.2,3.5,1.2), ("telekinesis",2.3,.6,7.9,1.8,2.3,1.2),
    ("vibe",2.4,.75,5.5,2.5,3.5,1.2),
]:
    source = STYLE_FILES[power]
    factor = .85 if power == "superspeed" else 1
    # Puppeteer also inflicts .5x on the thrown body and .8x on a second body in a collision.
    extra, old_extra = ((.6*.5 + .4*.6*.8), (1.5 + .4*2.3*.8)) if power == "telekinesis" else \
                       ((.4*.4), (.4*1.3)) if power == "vibe" else (0, 0)
    for hand in ("lead", "rear"):
        reviewed(f"style_{power}_{hand}", source, {
            "pyrokinesis":"2+.4*tier + 2 s ignition -> .8+.2*tier, no normal-jab ignition; loaded ignition remains",
            "hydrokinesis":"2+.4*tier -> .8+.2*tier; full-water baseline",
            "superspeed":"3*(1+.3*tier) -> 3*(.3+.09*tier); all three invulnerability-reset hits land",
            "telekinesis":"2+.3*tier -> .5+.1*tier; collision adds .5x to body and .8x to next foe",
            "vibe":"2+.4*tier -> .6+.15*tier; secondary 1+.3*tier -> .3+.1*tier, counted at 40%",
        }.get(power, f"unlock-tier hit {strike_old:g} -> {strike_new:g}; optional resource/counter multipliers retained"),
                 strike_old, strike_new, factor=factor, extra=extra,
                 old_extra=old_extra + (2*.6 if power == "pyrokinesis" else 0), cost=2)
    fin_cost = STYLE_OVERRIDES.get((power,"finisher"), 5)
    fin_n = 1 if power in ("superspeed","cryokinesis","regeneration","telekinesis","vibe","hydrokinesis") else 3
    low_n = 1 if power == "regeneration" else 3
    reviewed(f"style_{power}_flourish", source, {
        "pyrokinesis":"3+tier + 4 s ignition -> .4+.2*tier + 1 s ignition",
        "hydrokinesis":"5 daggers*(1.5+.3*tier) -> 5*(.35+.08*tier); all five may hit one body",
        "telekinesis":"lift 1.5+.2*tier + slam 4+tier -> .4+.1*tier + .8+.2*tier; hangs 9 ticks",
    }.get(power, f"unlock-tier finisher {fin_old:g} -> {fin_new:g}"), fin_old, fin_new,
             n=fin_n, factor=.85 if power in ("hydrokinesis","telekinesis") else 1,
             ctrl=.45 if power == "telekinesis" else .5 if power == "cryokinesis" else 0,
             util=2.5 if power == "superspeed" else 1 if power in ("electrokinesis","phasing","regeneration") else 0,
             extra=.6 if power == "pyrokinesis" else 0, old_extra=4*.6 if power == "pyrokinesis" else 0, cost=fin_cost)
    reviewed(f"style_{power}_low", source, f"unlock-tier low strike {low_old:g} -> {low_new:g}", low_old, low_new,
             n=low_n, util=1 if power in ("electrokinesis","cryokinesis","phasing","regeneration","vibe","superspeed") else 0,
             extra=.6 if power == "pyrokinesis" else 0, old_extra=3*.6 if power == "pyrokinesis" else 0, cost=fin_cost)
    for move in ("dodge", "back_dodge", "counter"):
        # Counters are contingent on a successful paid evasion/brace. Their loaded strike is not a new free cast.
        reviewed(f"style_{power}_{move}", source, {
            "pyrokinesis":"side step 0; back flip 3+.6*tier + 1 s ignition (was 3 s); counter loads a 2.2x rear fist",
            "electrokinesis":"side step 0; Flashback 3+.6*tier; counter 1.6x spark + three .7x chain hops",
            "hydrokinesis":"Flow Step 0; back Geyser 3+.6*tier; counter 1.6x lash and pull",
            "superspeed":"skids deal 0; conditional Too Slow is 6*(.9+.3*tier)",
            "quake":"dodges deal 0; Rebound (.8+.2*tier)*power plus a loaded 1.8x palm",
            "cryokinesis":"dodges deal 0; counter freezes one attacker and loads a 2.4x shatter jab",
            "phasing":"slides deal 0; Neutral Jing pillar (PILLAR_DAMAGE+tier)*Root, then a 1.5x claw",
            "regeneration":"braces deal 0; Payback adds .8*banked incoming damage",
            "invisibility":"dodges deal 0; counter fires five shards (.8+.2*tier each)",
            "telekinesis":"dodges deal 0; counter holds attacker 30 ticks and loads a 2x swat",
            "vibe":"hop 0; Fold Back 3+.6*tier; counter from behind 1.5+.5*tier",
        }[power], cost=STYLE_OVERRIDES.get((power, "back_dodge" if move == "back_dodge" else "dodge"), 11 if move == "back_dodge" else 8),
                 note="Motion/evasion/brace and a successful counter share this paid budget; counter damage is conditional, not a zero-cost standalone attack.")


def amended_review(action, *args, **kwargs):
    """Replace an opening classification after following its delegated/retaliatory damage path."""
    if action not in DAMAGE_AUDIT:
        raise ValueError(f"Not previously reviewed: {action}")
    del DAMAGE_AUDIT[action]
    reviewed(action, *args, **kwargs)


amended_review("radiation_glowing_path", "radiation/RadiationSkills#tick; radiation/RadiationService#tick; radiation/RadiationSickness#applyEffectTick",
               "8 s laying 5 s patches; sickness capped at amplifier 2 (1.5/s); overlapping patches do not deal separate direct hits", 13*1.5, n=2, factor=.6,
               note="Upper bound 13 s including the last patch's sickness tail; first stages are weaker. Half-Life/Catastrophe are optional upgrades.")
amended_review("radiation_1_1", "radiation/RadiationSkills#onHurt", "repeated doses stacking to amplifier 5 -> stage-I dose only when not already sick; at most 13 s stage I including tail", 36, 6.5,
               factor=.6, util=16, note="Retaliation no longer refreshes its own pulse timer on every melee blow or climbs to stage VI. Lead Skin still protects its owner.")
amended_review("phasing_2_1", "phasing/DensityService#surface", "6+tier -> 3+tier on surfacing; Uproot doubles a previously embedded target", 8, 5, n=2, ctrl=.5, util=4)
DAMAGE_AUDIT["phasing_0_1"]["ctrl"] = 2.5  # A wall pin is 50 ticks; an unobstructed drag is only 14.

for power, old, new, factor, extra, old_extra, ctrl, blocks in [
    ("pyrokinesis", 2.4*2.2, 1*2.2, 1, .6, 5*.6, 0, 4.8),
    ("electrokinesis", 2.4*1.6*1.84, 1*1.5*1.84, 1, 0, 0, 0, 4.5),
    ("hydrokinesis", 2.4*1.6, 1*1.6, 1, 0, 0, .25, 4.6),
    ("superspeed", 6*1.2, 6*.24, .85, 0, 0, 0, 5),
    ("quake", 5+2.4*1.8, 1+1*1.8, 1, 0, 0, 0, 4),
    ("cryokinesis", 2.4*1.6*2, 1*1.2*2, 1, 0, 0, .1, 4),
    ("phasing", 2+2.75*1.4*1.5, .7+.85*1.4*1.5, 1, 0, 0, 0, 3.5),
    ("invisibility", 5*2.4, 5*.5, .85, 0, 0, 0, 4),
    ("telekinesis", 4.6+1.5+.4*4.6*.8, 1.2+.6+.4*1.2*.8, 1, 0, 0, .1, 4),
    ("vibe", 5, 2, 1, 0, 0, 0, 3.6),
]:
    amended_review(f"style_{power}_counter", STYLE_FILES[power], {
        "pyrokinesis":"loaded (.8+.2*tier)*2.2; ignition 5 -> 1 s (normal strike's old base 2+.4*tier)",
        "electrokinesis":"loaded (2+.4*tier)*1.6 -> (.8+.2*tier)*1.5; three .7x hops add .84x at 40% spread",
        "hydrokinesis":"loaded (2+.4*tier)*1.6 -> (.8+.2*tier)*1.6, plus a short pull",
        "superspeed":"6*(.9+.3*tier) -> 6*(.18+.06*tier)",
        "quake":"Rebound 4+tier -> .8+.2*tier PLUS a loaded 1.8x palm",
        "cryokinesis":"loaded (2+.4*tier)*1.6*2 -> (.8+.2*tier)*1.2*2; hitting shatters and ENDS the 40-tick freeze",
        "phasing":"pillar 1.5+.5*tier -> .5+.2*tier PLUS (.7+.15*tier)*1.4 gauntlet*1.5 loaded; Root is conditional",
        "invisibility":"five shards*(2+.4*tier) -> five*(.4+.1*tier), StyleHits resets invulnerability for each",
        "telekinesis":"loaded swat 2x base; body collision 1.5 -> .5x damage and a second foe .8x, weighted .4; swat ENDS the grip",
        "vibe":"4+tier -> 1.5+.5*tier, from a tear behind the attacker",
    }[power], old, new, factor=factor, extra=extra, old_extra=old_extra, ctrl=ctrl, blocks=blocks,
         cost=STYLE_OVERRIDES.get((power,"dodge"),8),
         note="Includes the paid dodge's travel. Full freeze/grip duration and immediate loaded release are alternatives, not additive control. Counter requires a successfully evaded blow.")
for power, old, new, factor, extra, old_extra, ctrl, blocks in [
    ("pyrokinesis",4.2,1.6,1,.6,1.8,0,4.5), ("electrokinesis",4.2,4.2,.75,0,0,0,4.5),
    ("hydrokinesis",4.2,2,1,0,0,.5,4.5), ("vibe",4.2,4.2,.75,0,0,0,4),
]:
    amended_review(f"style_{power}_back_dodge", STYLE_FILES[power], {
        "pyrokinesis":"flip 3+.6*tier -> 1+.3*tier; ignition 3 -> 1 s",
        "electrokinesis":"Flashback 3+.6*tier after a delayed public static burst",
        "hydrokinesis":"Geyser Vault 3+.6*tier -> 1.5+.25*tier",
        "vibe":"Fold Back 3+.6*tier after its 12-tick warning",
    }[power], old, new, n=2, factor=factor, extra=extra, old_extra=old_extra, ctrl=ctrl, blocks=blocks, cost=11,
         note="The immediate special back-dodge attack; a successful evasion may additionally earn the counter reviewed separately.")

DAMAGE_AUDIT["style_phasing_lead"]["formula"] = "2.4+.35*tier -> .7+.15*tier, times Root"
DAMAGE_AUDIT["style_phasing_rear"].update(before=2.75*1.4, after=.85*1.4,
    formula="(2.4+.35*tier)*1.4 -> (.7+.15*tier)*1.4; the rear hand always wears its gauntlet, times Root")


def damage_reviews(trees):
    """Fail closed on missing/obsolete reviews; render all measurements, not just the fixes."""
    nodes = {}
    for tree in trees:
        power = tree["power"]
        for branch, lane in enumerate(tree["branches"]):
            for row, node in enumerate(lane):
                if node.get("active", row == 1):
                    nodes[node.get("id", f"{power}_{branch}_{row}")] = node
        nodes.update({node["id"]: node for node in tree.get("styles", [])})
    expected = set(nodes) | {f"style_{p}_{m}" for p in STYLE_POWERS for m in ("lead","rear","flourish","low","dodge","back_dodge","counter")}
    missing, obsolete = expected - DAMAGE_AUDIT.keys(), DAMAGE_AUDIT.keys() - expected
    if missing or obsolete:
        raise ValueError(f"Damage review coverage: missing {sorted(missing)}, obsolete {sorted(obsolete)}")
    rows = []
    for action, a in DAMAGE_AUDIT.items():
        node = nodes.get(action, {})
        cost = a["cost"] if a["cost"] is not None else node["energy"]
        if a["cost"] is None and node.get("role"):
            cost += ULTIMATE_PREMIUM * node["cooldown"] / 20
        spread = 1 + .4 * min(max(a["n"], 1) - 1, 4)
        reserve = value(n=a["n"], ctrl=a["ctrl"], util=a["util"], blocks=a["blocks"])
        budget = max(0, (cost - reserve) / (2 * spread))
        old = a["before"] * a["factor"] + a["old_extra"]
        new = a["after"] * a["factor"] + a["extra"]
        error = abs(new / budget - 1) if budget > 1e-6 else (0 if new == 0 else math.inf)
        changed = old != new
        if (old or new) and error > .300001:
            raise ValueError(f"{action}: damage {new:g}, implied {budget:g}, error {error:.1%}")
        rows.append(a | dict(action=action, cost=cost, tier=nodes.get(action, {}).get("level", 1 if action.endswith(("_lead","_rear")) else 2),
                             budget=budget, old=old, new=new, reserve=reserve, changed=changed))
    return rows


def cost_of(a, slot):
    if a["fixed"] is not None:
        return a["fixed"]
    lo, hi = BANDS[slot]
    return int(max(lo, min(hi, round(value(**a["effect"])))))


def cooldown_of(a, cost):
    if a["cd"] is not None:
        return a["cd"]
    seconds = max(.5 if a["role"] == "basic" else 1.0, cost * ROLE_CD[a["role"]])
    # Quarter seconds for quick moves, whole seconds for long ones.
    return round(seconds * 4) / 4 if seconds < 10 else float(round(seconds))


def load_dump():
    if not DUMP.exists():
        sys.exit(f"{DUMP} is missing: run the game tests once (BalanceDumpGameTests writes it).")
    return {a["id"]: a for a in json.loads(DUMP.read_text(encoding="utf-8")) if "error" not in a}


def core_table(dump):
    rows = []
    for action, a in A.items():
        d = dump.get(action)
        if d is None:
            sys.exit(f"{action} isn't in the dump")
        tier = a["tier"]
        slot = SLOT[d["kind"].split(":")[1]]
        if a["effect"].get("ult") and slot != 5:
            sys.exit(f"{action}: only a slot-5 key gets the ultimate premium")
        raw = d["raw"][tier - 1]
        row = dict(power=a["power"], action=action, unlock=tier)
        if a["passthrough"]:
            row.update(energy=-1, cooldown=-1)
        else:
            cost = cost_of(a, slot)
            cd = cooldown_of(a, cost)
            row.update(energy=cost, cooldown=int(round(cd * 20)), base_energy=raw["energy"], base_cooldown=raw["cooldown"])
        if a["drain"]:
            row["drain"] = a["drain"]
        if a["per_block"]:
            row["per_block"] = a["per_block"]
        row["role"] = a["role"]
        row["note"] = a["note"]
        rows.append(row)
    for power in STYLE_POWERS:
        for move, (base, cost) in STYLE.items():
            cost = STYLE_OVERRIDES.get((power, move), cost)
            row = dict(power=power, action=f"style_{power}_{move}", energy=cost, base_energy=base)
            if move == "dodge":
                row.update(cooldown=STYLE_DODGE_COOLDOWN_OVERRIDES.get(power, STYLE_DODGE_COOLDOWN), base_cooldown=18)
            row["role"] = "style"
            rows.append(row)
    for power in FINISHER_POWERS:
        energy, cooldown = FINISHER
        rows.append(dict(power=power, action=f"finisher_{power}", energy=energy, base_energy=8, cooldown=cooldown,
                         base_cooldown=60, role="finisher"))
    return rows


# Skill-tree actives: keep each node's place relative to its neighbours, pull it into its band.
SKILL_BANDS = {"early": (12, 26, 20), "late": (14, 32, 24), "signature": (45, 60, 52)}
# Every class ability is an Ultimate: one stamina price and one long cooldown per role (seconds). The long wait is part
# of the price: each second of it is worth ULTIMATE_PREMIUM stamina of effect in the damage review.
ULTIMATES = {"offense": (58, 150), "defense": (50, 120), "support": (52, 135)}
ULTIMATE_PREMIUM = 0.2
LANE_LEVELS = [1, 2, 3, 3, 4, 5]

# Player-facing formulas belong beside the reviewed damage, so regeneration cannot restore old numbers.
DESCRIPTION_EDITS = {
    "quake_0_1": [("7 + level damage", "10 + level damage")],
    "electrokinesis_ground_sink": [("discharge up to 16 stored damage nearby", "discharge nearby for 4 + half the stored damage, using up to 16 stored damage")],
    "time_manipulation_shared_blink": [("6 + level", "2 + level")],
    "radiation_0_1": [("8 + level damage", "4 + 0.5 per level damage")],
    "radiation_1_1": [("anything that hits you in melee meanwhile is dosed", "melee attackers without Radiation Sickness receive a first-stage dose; further hits do not stack or refresh it")],
    "pyrokinesis_blue_lance": [("(8 + level) x 1.5", "(3 + 0.5 per level) x 1.5"), ("2.5 damage every four ticks", "0.5 damage every four ticks")],
    "invisibility_cloak_field": [("One press of Crush closes them all.", "One press of Crush closes them all, each dealing 12% of a single Bubble's crush damage.")],
    "phasing_phase_carry": [("for 5 + level", "for 0.8 + 0.2 per level")],
    "vibe_rift_beast": [("half a minute", "45 seconds")],
    "amp_1_1": [("everyone within five blocks takes 6 + level (more with Charge), double where you're closed in", "everyone within five blocks is stunned for five seconds. It spends up to 30 Charge for damage equal to Charge spent / 30, doubled where you're closed in"), ("foes left at half a heart are stunned for five seconds", "damage leaves at least half a heart")],
}
DESCRIPTION_DETAILS = {
    "flight_2_1": "The updraft deals 6 damage.",
    "flight_thunderclap": "The burst deals 7 + 0.05 per Momentum spent.",
    "cryokinesis_0_1": "The lance deals 8 + tier damage.",
    "cryokinesis_2_1": "The front deals 5 damage before shattering bonuses.",
    "cryokinesis_brittle_constellation": "Each fracture deals 6 damage, or 9 with the stronger chain upgrade.",
    "time_manipulation_singularity": "Core tears deal 0.6 + 0.1 per tier each half-second; collapse deals 3 + 0.5 per tier before falloff.",
    "radiation_heat_vent": "The hit deals 1.5 + Core vented / 30, plus four seconds of burning.",
    "pyrokinesis_1_1": "The aura deals 0.1 damage each second and keeps enemies burning.",
    "phasing_2_1": "The uppercut deals 3 + tier damage.",
    "hydro_pressure_jet": "Each tenth-second contact deals 0.65 + 0.1 per tier damage.",
    "amp_doorbuster": "The blast deals 1.5 damage.",
}
CORE_DESCRIPTION_DETAILS = {
    "speed_cyclone": "Cyclone and Wind Arms drain 4 stamina per second from the start; an anchored tornado keeps charging until it ends. Exhaustion ends the wind.",
    "flight_cyclone": "Tempest drains 12 stamina per second, including its windup, and ends if stamina runs out.",
    "hydro_maelstrom": "Maelstrom drains 12 stamina per second, including its windup, and ends if stamina runs out.",
}


def normalize_descriptions(trees, language):
    nodes = {}
    for tree in trees:
        for b, lane in enumerate(tree["branches"]):
            for row, node in enumerate(lane):
                nodes[node.get("id", f"{tree['power']}_{b}_{row}")] = node
        nodes.update({node["id"]: node for node in tree.get("styles", [])})
    for action, edits in DESCRIPTION_EDITS.items():
        text = nodes[action]["description"]
        for old, new in edits:
            if old in text:
                text = text.replace(old, new)
            elif new not in text:
                raise ValueError(f"Description changed underneath the damage review: {action}: {old}")
        nodes[action]["description"] = text
        language[f"action.heroesevolved.{action}.desc"] = text
    for action, detail in DESCRIPTION_DETAILS.items():
        text = nodes[action]["description"]
        if detail not in text:
            text += " " + detail
        nodes[action]["description"] = text
        language[f"action.heroesevolved.{action}.desc"] = text
    for action, detail in CORE_DESCRIPTION_DETAILS.items():
        key = f"action.heroesevolved.{action}.desc"
        if detail not in language[key]:
            language[key] += " " + detail


def pull(old, lo, hi, centre):
    return int(max(lo, min(hi, round(.7 * old + .3 * centre))))


def normalize_skills(trees):
    changes = []
    for tree in trees:
        power = tree["power"]
        for b, lane in enumerate(tree["branches"]):
            for tier, n in enumerate(lane):
                nid = n.get("id", f"{power}_{b}_{tier}")
                level = 1 if n.get("innate") else 4 if n.get("signature") else LANE_LEVELS[min(tier, 5)]
                n["level"] = level
                active = n.get("active", tier == 1)
                if not active:
                    continue
                band = "signature" if n.get("signature") else "early" if tier <= 1 else "late"
                lo, hi, centre = SKILL_BANDS[band]
                # The designed numbers are kept beside the balanced ones, so a re-run starts from them again.
                n.setdefault("design_energy", n["energy"])
                n.setdefault("design_cooldown", n["cooldown"])
                old_e, old_cd = n["design_energy"], n["design_cooldown"]
                energy = pull(old_e, lo, hi, centre)
                cd_s = old_cd / 20
                cd_s = max(.2 * energy, min(.8 * energy, cd_s)) if band != "signature" else max(25, min(45, cd_s))
                cooldown = int(round(cd_s * 4) / 4 * 20)
                n["energy"], n["cooldown"] = energy, cooldown
                changes.append((power, nid, n["name"], "skill", old_e, energy, old_cd, cooldown, level))
        for s in tree.get("styles", []):
            # An Ultimate is priced by its role alone: designed and balanced numbers are the same.
            energy, seconds = ULTIMATES[s["role"]]
            cooldown = seconds * 20
            old_e, old_cd = energy, cooldown
            s["design_energy"], s["design_cooldown"] = energy, cooldown
            s["energy"], s["cooldown"] = energy, cooldown
            changes.append((power, s["id"], s["name"], "ultimate:" + s["role"], old_e, energy, old_cd, cooldown, s.get("level", 3)))
    return changes


def doc(rows, dump, skill_changes, reviews):
    lines = []
    w = lines.append
    w("# Stamina and balance")
    w("")
    w("Every power spends one stamina bar. This page is the rulebook and the table; `tools/balance.py` holds the same")
    w("rules as code and writes `data/heroesevolved/power_balance.json` and the skill numbers in `skill_trees.json`.")
    w("Change a number there and re-run it, not here.")
    w("")
    w("## The bar")
    w("")
    w("- **Size:** 100 at Power Level 1, 4 more each level: 136 at Level 10, 156 at 15, 176 at 20.")
    w("  Leveling is the permanent max-stamina upgrade path for every power; it does not require a particular item or build.")
    w("- **Practice:** a move costs 4% less for every Power Level since it unlocked, up to 30% off (eight levels")
    w("  later). A Level 1 move is at the cap by Level 9; an ultimate that opened at Level 15 is 16% off at Level 19.")
    w("  The table's numbers below are full price; the HUD and menus show what you actually pay. Rank III's 10% stacks")
    w("  on top. Held drains (Still World's per-second cost, flying) aren't discounted.")
    w("- **Recovery:** 3 a second at Level 1, 0.12 more each level (5.3 at Level 20), and **double** after five seconds")
    w("  without dealing or taking damage. It runs every tick, so the bar fills smoothly.")
    w("- **The pause:** spending stamina on a move stops recovery for 1.5 seconds. A fight is paid for out of the bar; the")
    w("  quiet afterwards refills it.")
    w("- **Held powers** drain a little every tick while they last, and end when the bar can't pay for the next tick. They")
    w("  don't restart the pause, so recovery works against a light drain (flying, Fade, Accelerate). Still World and")
    w("  Between Seconds hold recovery while they run: nothing comes back until time moves again.")
    w("- **Overexertion** is unchanged: hold Shift to use a move you can't afford, and pay in health, Weakness and Nausea.")
    w("- **Server config** (`energy` section): `baseEnergy`, `staminaPerPowerLevel`, `staminaRegenerationPerSecond`,")
    w("  `staminaRegenerationPerPowerLevel`, `staminaCalmRegenerationMultiplier`, `staminaRecoveryDelayTicks`, and")
    w("  `staminaCostScale` / `cooldownScale`, which multiply every cost and cooldown after the table.")
    w("")
    w("## The HUD")
    w("")
    w("The bar sits over the hunger bar in your power's colour, with a notch every 20 stamina so costs can be read off it.")
    w("What a move just took stays as a pale chunk that shrinks away. While stamina recovers a glint runs along the fill")
    w("and the right-hand number shows the rate (`+4.2/s`, green when calm); right after a move a thin line under the bar")
    w("counts down the pause. A held power draws moving stripes at the end of the fill and shows what it takes and how")
    w("long that leaves (`-12/s  9s`). Under a fifth the frame pulses red, and a move refused for lack of stamina flashes")
    w("and shakes the bar. Full and idle, the numbers fade away.")
    w("")
    w("![The stamina bar: full, just spent, recovering, Still World held, low, refused](artwork/stamina-bar.png)")
    w("")
    w("Photographed by `tools/stamina-preview.init.gradle` (`uitest/StaminaHudPreview`, flag `heroes.preview.stamina`).")
    w("")
    w("## Held and distance costs")
    w("")
    w("| Move | Cost |")
    w("| --- | --- |")
    w("| Still World | 20 to start (21 at Level 15 after its practice discount), then 12 a second at Level 6, 10 at")
    w("  Level 10, 8 at Level 15. It lasts until you press the key again or the stamina runs out (recovery stops")
    w("  meanwhile): about 8 s at Level 6, 11 at Level 10, 17 at Level 15 and 19 at Level 20 from a full bar. |")
    w("| Between Seconds | 10 to start, then 5 a second (4 at Level 15), recovery stopped; press again to end. |")
    w("| Fold Space | 4 to start plus 0.3 for every block folded, at full price: 9 for a 16-block fold, 28 for 80, 49")
    w("  for 150. Practice takes up to 30% off (35 for a 150-block fold at Level 15). |")
    w("| Breach to a frequency | The breach's own cost plus 1 for every 40 blocks to the frequency (up to 30 more), or 15")
    w("  more to another world. A breach where you look costs nothing extra. |")
    w("| Free Flight | 4 a second cruising, 9 boosting (recovery still runs). |")
    w("| Accelerate | 3 a second running, 6 in Redline. |")
    w("| Fade | 2 a second, +1 for each friend faded with you. |")
    w("| Glacier Ride | 8 a second moving, 4 standing. |")
    w("| Flame Flight | 10 a second moving, 6 hovering (4 with Pillar of Flame). |")
    w("| Live Current, Dragon's Breath, the held Canary Cry | 12-15 a second. |")
    w("")
    w("## How a cost is worked out")
    w("")
    w("Stamina pays for effect, measured at the tier the move unlocks:")
    w("")
    w("- **2** per point of damage to one target. Damage over time counts 0.6 a point, a flurry 0.85, and a telegraphed or")
    w("  slow, dodgeable hit 0.75.")
    w("- **+40%** of that for each extra target a move usually catches, up to four.")
    w("- **3** per second of hard control (a hold, a root, a lift, a fear), with the same +40% per target.")
    w("- **0.35** per block a move carries you, plus 2.")
    w("- Utility (information, building, portals, shields) priced by judgement in the same units.")
    w("- **+15** for a tier-5 ultimate.")
    w("")
    w("Each key has a band, so no key costs more or less than its place in the kit: slot 1 6-16, slot 2 10-26, slot 3")
    w("14-34, slot 4 16-40, slot 5 55-95. When the formula leaves the band, the cost is clamped and the move's **damage**")
    w("changes instead, so damage always follows stamina.")
    w("")
    w("Cooldown follows cost by role, in seconds per point of stamina: basic 0.08 (at least half a second), quick 0.15,")
    w("skill 0.3, heavy 0.45, ultimate 0.75. Toggles and held powers get a short cooldown and pay by the second.")
    w("")
    w("Damage scales with tier inside the code (4 + tier, 5 + 1.5 x tier...), a move gets cheaper with practice, and")
    w("the bar grows by 76%, so a move gets more for its stamina as you level, while the newest and biggest moves")
    w("stay at full price until you have had them a while.")
    w("")
    w("## Unlock levels")
    w("")
    w("One new key at each stage: Power Level 1, 3, 6, 10 and 15 (tiers 1 to 5). A kit whose first key is a toggle")
    w("(Free Flight, Accelerate, Fade, Shift) opens its second key at Level 1 too. Superspeed's Cyclone moved from")
    w("Level 10 to 6, and Flight's Meteor Drop and Skyhook from 6 and 10 to 3 and 6, so neither kit skips a stage.")
    w("Skill trees open by row: row 1 at Level 1, row 2 at Level 3, rows 3 and 4 at Level 6, row 5 at Level 10 and the")
    w("capstones at Level 15; signature skills at Level 10. Skill points are unchanged: 3 at Level 1, about one more")
    w("each level after (21 at Level 15, 28 Evolved), plus training and story notes, against 33 for a whole tree.")
    w("")
    w("## Damage that changed")
    w("")
    for action, why in DAMAGE_CHANGES:
        w(f"- **{action}**: {why}")
    for r in reviews:
        if r["changed"]:
            w(f"- **{r['action']}**: {r['formula']}. Weighted damage at unlock: {r['old']:.3g} -> {r['new']:.3g}; "
              f"budget {r['budget']:.3g} from {r['cost']:g} stamina. Code: `{r['source']}`.")
    w("")
    w("## Core moves")
    w("")
    by_power = {}
    for r in rows:
        if r.get("role") in ("style", "finisher"):
            continue
        by_power.setdefault(r["power"], []).append(r)
    for power, items in by_power.items():
        w(f"### {power.replace('_', ' ').title()}")
        w("")
        w("| Key | Move | Level | Stamina | Cooldown | Was | Pays for |")
        w("| --- | --- | --- | --- | --- | --- | --- |")
        for r in items:
            d = dump[r["action"]]
            key = SLOT.get(d["kind"].split(":")[1], "?")
            old = BEFORE.get(r["action"])
            was = "" if old is None else f"{old['energy']} / {old['cooldown'] / 20:g}s, Level {[1, 3, 6, 10, 15][old['unlock'] - 1]}"
            level = [1, 3, 6, 10, 15][r["unlock"] - 1]
            if r["energy"] < 0:
                stamina, cd = "copied", "copied x1.5"
            else:
                stamina = str(r["energy"]) + (f" + {r['drain']:g}/s" if r.get("drain") else "") \
                          + (f" + {r['per_block']:g}/block" if r.get("per_block") else "")
                cd = f"{r['cooldown'] / 20:g}s"
            w(f"| {key} | `{r['action']}` | {level} | {stamina} | {cd} | {was} | {r['note']} |")
        w("")
    w("## Fighting styles")
    w("")
    w("Strikes 2, finishers (kicks, claps, flourishes) 5 (Pressure Palm 6), dodges 8 and back dodges 11 (a speedster's")
    w("6 and 9, the Reckless Brawler's brace 6 and 9), and 0.9 s between dodges (0.7 s for a speedster). Every one pauses")
    w("recovery, so a long exchange of blows is paid for.")
    w("")
    w("## Finishers")
    w("")
    w(f"Every finisher costs {FINISHER[0]} stamina and shares one {FINISHER[1] / 20:g} s cooldown across all powers; a finished")
    w("foe gives 10 stamina back. Entries: " + ", ".join(f"`finisher_{p}`" for p in FINISHER_POWERS) + ".")
    w("")
    w("## Skill-tree and class abilities")
    w("")
    w("Actives keep their designed order and are pulled 30% toward the middle of their band, then clamped: early actives")
    w("(row 2) 12-26, later actives 14-32, signatures 45-60. A skill's cooldown is held between 0.2 and 0.8 seconds per")
    w("stamina (25-45 s for a signature).")
    w("")
    off, dfn, sup = ULTIMATES["offense"], ULTIMATES["defense"], ULTIMATES["support"]
    w(f"Every class ability is an Ultimate with one price per role: Offense {off[0]} stamina and {off[1]} s, Defense {dfn[0]}")
    w(f"and {dfn[1]} s, Support {sup[0]} and {sup[1]} s. The long wait is part of the price: in the damage review each second")
    w(f"of an Ultimate's cooldown is worth {ULTIMATE_PREMIUM:g} stamina of effect, and an Offense Ultimate's hold on its foe")
    w("is its control.")
    w("")
    w("| Power | Skill | Kind | Stamina | Cooldown |")
    w("| --- | --- | --- | --- | --- |")
    for power, nid, name, kind, oe, ne, ocd, ncd, level in skill_changes:
        e = f"{ne}" if oe == ne else f"{ne} (was {oe})"
        c = f"{ncd / 20:g}s" if ocd == ncd else f"{ncd / 20:g}s (was {ocd / 20:g}s)"
        w(f"| {power} | {name} | {kind} | {e} | {c} |")
    w("")
    w("## Every damage review")
    w("")
    w(f"Checked {sum(not r['action'].startswith('style_') for r in reviews)} tree actives, signatures and Ultimates, "
      f"and {sum(r['action'].startswith('style_') for r in reviews)} fighting-style moves across all 20 powers.")
    w("Each complete sequence is measured at its unlock tier, before optional upgrades, stored-resource bonuses, armor or resistance.")
    w("Offense Ultimates include their compulsory 10% damage bonus. Typical targets are one for a single hit, two for a narrow")
    w("fan or small patch, and three for a broad area; a four-body Shell uses four. The per-move assumptions are below.")
    w("Damage is weighted before comparison: direct x1, repeated damage over time x0.6, flurries x0.85, telegraphed hits x0.75.")
    w("Burning is counted once across its refreshed duration. Simultaneous equal projectiles which respect ordinary damage immunity")
    w("are not counted as invulnerability-reset flurries. Multi-part paid actions include every paid activation in their stamina.")
    w("The damage budget is (stamina - utility - travel - 3 x control-seconds x spread) / (2 x spread), with spread = 1 + 0.4 x extra targets.")
    w("Only differences strictly greater than 30% are fixed. Pure information, movement, shielding and enhancements of separately paid")
    w("attacks are recorded as utility rather than assigned invented flat damage. A counter shares the paid dodge's budget and requires")
    w("a successful evasion; full holding time and an immediate shattering/releasing blow are alternative uses of the same control.")
    w("")
    w("| Move | Tier | Stamina | Targets | Control (s) | Utility / travel | Weighted damage / budget | Result | Formula and code |")
    w("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for r in reviews:
        damaging = bool(r["old"] or r["new"])
        result = "changed" if r["changed"] else "within 30%" if damaging else "utility / conditional"
        damage = f"{r['new']:.3g} / {r['budget']:.3g}" if damaging else "—"
        travel = f"{r['util']:g} / {r['blocks']:g} blocks"
        detail = (r["formula"] + (". " + r["note"] if r["note"] else "")).replace("|", "/")
        w(f"| `{r['action']}` | {r['tier']} | {r['cost']:g} | {r['n']:g} | {r['ctrl']:g} | {travel} | {damage} | {result} | {detail} (`{r['source']}`) |")
    w("")
    w("## End portal offerings")
    w("")
    w(f"Power Level {END_PORTAL['minimum_power_level']} or higher; begin with at least "
      f"{END_PORTAL['starting_stamina_fraction']:.0%} stamina. One offering drains the entire remaining bar "
      f"over {END_PORTAL['channel_ticks'] / 20:g} seconds, without rank, cost-scale or Supercharge discounts.")
    w(f"Two touching frames need an offering each. A completed offering lasts {END_PORTAL['memory_ticks'] / 20:g} "
      "seconds, so a lone player can recover and charge the neighbor. Held recovery is stopped only while channeling.")
    w("See [the End portal](end-portal.md). These world-ritual values also generate `end_portal_ritual.json`.")
    w("")
    w("## End battle grounding")
    w("")
    w(f"An altar overload takes {END_BATTLE['channel_ticks'] / 20:g} seconds of one person's channeling at "
      f"{END_BATTLE['drain_per_second']:g} stamina per second. Partners share the charge and pay for their own held time. "
      "Recovery pauses while channeling; release, movement, damage or suppression ends the channel. "
      "These values generate `end_battle_ritual.json`; see [The Living Seal](the-living-seal.md).")
    w("")
    return "\n".join(lines)


def main():
    check = "--check" in sys.argv
    dump = load_dump()
    rows = core_table(dump)
    table = json.dumps(rows, indent=2) + "\n"
    trees = json.loads(TREES.read_text(encoding="utf-8"))
    language = json.loads(LANG.read_text(encoding="utf-8"))
    skill_changes = normalize_skills(trees)
    normalize_descriptions(trees, language)
    reviews = damage_reviews(trees)
    trees_text = json.dumps(trees, indent=2, ensure_ascii=False) + "\n"
    language_text = json.dumps(language, indent=2, ensure_ascii=False) + "\n"
    doc_text = doc(rows, dump, skill_changes, reviews) + "\n"
    portal_text = json.dumps(END_PORTAL, indent=2) + "\n"
    end_battle_text = json.dumps(END_BATTLE, indent=2) + "\n"
    stale = [p for p, t in ((TABLE, table), (TREES, trees_text), (LANG, language_text), (DOC, doc_text), (PORTAL_TUNING, portal_text), (END_BATTLE_TUNING, end_battle_text))
             if not p.exists() or p.read_text(encoding="utf-8") != t]
    if check:
        for p in stale:
            print("out of date:", p.relative_to(ROOT))
        sys.exit(1 if stale else 0)
    TABLE.write_text(table, encoding="utf-8")
    TREES.write_text(trees_text, encoding="utf-8")
    LANG.write_text(language_text, encoding="utf-8")
    DOC.write_text(doc_text, encoding="utf-8")
    PORTAL_TUNING.write_text(portal_text, encoding="utf-8")
    END_BATTLE_TUNING.write_text(end_battle_text, encoding="utf-8")
    for r in rows:
        if r.get("role") not in ("style", "finisher"):
            print(f"{r['action']:34} T{r['unlock']}  {r['energy']:>4}  {r.get('cooldown', 0) / 20:>6g}s")
    print(len(rows), "table rows;", len(skill_changes), "skills and class abilities")


if __name__ == "__main__":
    main()
