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

Skill-tree actives and class abilities keep their designed order and are pulled into the same bands
(normalize_skills below).
"""

import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "build/rewind-gametest-world/build/balance/actions.json"
TABLE = ROOT / "src/main/resources/data/heroesevolved/power_balance.json"
TREES = ROOT / "src/main/resources/data/heroesevolved/skill_trees.json"
DOC = ROOT / "docs/stamina-balance.md"
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
act("telepathy_read_mind", "telepathy", 1, "basic", "held read; builds Focus", util=8)
act("telepathy_suggestion", "telepathy", 2, "heavy", "a 5 s command on one mind", ctrl=5, util=6)
act("telepathy_mind_link", "telepathy", 3, "skill", "links willing minds (no damage)", util=18)
act("telepathy_puppet", "telepathy", 4, "ultimate", "about 10 s in control of a body", ctrl=10, util=10)
act("telepathy_nightmare", "telepathy", 5, "ultimate", "5 s of fear on everyone linked", ctrl=5, n=4, util=15, ult=True)
# --- Superspeed
act("speed_accelerate", "superspeed", 1, "basic", "toggle; 3 a second while running (6 in Redline)", fixed=0, cd=0)
act("speed_blitz", "superspeed", 1, "quick", "four blows of 2.5 after a 22-block rush (a gap-closer: half its distance counts)",
    dmg=10 * .85, blocks=11, cd=2.5)
act("speed_snatch", "superspeed", 2, "skill", "takes items, shots and a weapon", util=16)
act("speed_cyclone", "superspeed", 3, "heavy", "3 s of wind: 1.2 + 0.3/tier every half second, lift, no air",
    dmg=(1.2 + .3 * 3) * 6 * .6, n=3, ctrl=2)
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
act("flight_cyclone", "flight", 5, "ultimate", "Tempest: lifts and bursts everything round its eye", dmg=8, n=4, ctrl=3, ult=True)
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
act("matter_assembly_barricade", "matter_assembly", 1, "skill", "a 3x2 wall (also costs material)", util=12)
act("matter_assembly_shard_lance", "matter_assembly", 1, "basic", "a shard up to 64 blocks", dmg=6)
act("matter_assembly_causeway", "matter_assembly", 2, "skill", "8 s of walking on air", util=20)
act("matter_assembly_cage", "matter_assembly", 3, "heavy", "a cell round one enemy", ctrl=4, util=10)
act("matter_assembly_cathedral", "matter_assembly", 5, "ultimate", "three turrets for 20 s", util=55, ult=True)
# --- Invisibility
act("invisibility_fade", "invisibility", 1, "basic", "toggle; 2 a second, +1 per friend", fixed=6, cd=.5)
act("invisibility_silent_step", "invisibility", 1, "quick", "Lens Shield: 3 s, 70% off frontal harm, stops shots", util=14)
act("invisibility_unseen_strike", "invisibility", 2, "quick", "Force Bridge: a 12-block span", util=14)
act("invisibility_shroud", "invisibility", 3, "heavy", "Bubble: 5 s hold, then a crush (6 + 2/tier)", ctrl=5, dmg=6, util=2)
act("invisibility_ghost_protocol", "invisibility", 5, "ultimate", "Force Nova: heavy damage and a throw in 12 blocks", dmg=14, n=4, ult=True)
# --- Telekinesis
act("telekinesis_push", "telekinesis", 1, "basic", "a shove (2 + tier/4)", dmg=2.25, ctrl=1)
act("telekinesis_grip", "telekinesis", 2, "skill", "hold, then a throw", ctrl=4, dmg=6)
act("telekinesis_bullet_stop", "telekinesis", 3, "heavy", "catches every shot, then turns them", util=22)
act("telekinesis_disarm", "telekinesis", 4, "skill", "tears a weapon away", util=16)
act("telekinesis_storm", "telekinesis", 5, "ultimate", "lifts and slams everyone round you", dmg=10, n=4, ctrl=3, ult=True)
# --- Hydrokinesis
act("hydro_water_whip", "hydrokinesis", 1, "basic", "4 + tier along your aim", dmg=5)
act("hydro_crashing_wave", "hydrokinesis", 1, "skill", "5 + tier to what it carries", dmg=6, n=3)
act("hydro_octopus_form", "hydrokinesis", 2, "heavy", "10 s: arms lash (6 + tier), shots swallowed, a third off blows", util=30)
act("hydro_wave_rider", "hydrokinesis", 3, "skill", "6 s of surfing", blocks=30, util=8)
act("hydro_maelstrom", "hydrokinesis", 5, "ultimate", "a waterspout that drags and drowns", dmg=10, n=4, ctrl=3, ult=True)
# --- Technopathy
act("technopathy_handshake", "technopathy", 1, "basic", "enrols a network", util=8)
act("technopathy_remote_hand", "technopathy", 1, "basic", "works a linked device", fixed=5, cd=.6)
act("technopathy_tripwire", "technopathy", 2, "skill", "a trap: 6 + tier and a stumble", dmg=8 * .75, util=4)
act("technopathy_intercept", "technopathy", 3, "heavy", "10 s of arrow diversion", util=24)
act("technopathy_machine_city", "technopathy", 5, "ultimate", "12 s of a defended district", util=60, ult=True)
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
act("radiation_meltdown_beam", "radiation", 4, "skill", "a held beam that spends Core", fixed=18, cd=6)
act("radiation_exploding_man", "radiation", 5, "ultimate", "Critical Mass: a 24-block nuclear blast after 25 s", fixed=120, cd=300)
# --- Density
act("phasing_shift", "phasing", 1, "basic", "a notch of density; holding it drains 1.7-3.3 a second", fixed=4, cd=.3)
act("phasing_reach", "phasing", 1, "quick", "5 + tier that ignores armour, and no healing for 5 s", dmg=6 * 1.2, util=2)
act("phasing_embed", "phasing", 2, "skill", "half into the floor for 5 s, taking 50% more", ctrl=5, util=6)
act("phasing_sink", "phasing", 3, "quick", "move through the ground", util=16)
act("phasing_hollow", "phasing", 5, "ultimate", "everyone in 6 + tier blocks sinks for 5 s", ctrl=5, n=4, dmg=4, ult=True)
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
                   "regeneration", "vibe", "technopathy", "matter_assembly", "empathic_mimicry", "shapeshifting"]
FINISHER = (8, 60)  # stamina, cooldown in ticks

# Damage that moved to match its stamina (the code carries each change; this list documents them).
DAMAGE_CHANGES = [
    ("speed_cyclone", "Cyclone's wind: 2 + 0.5/tier every half second -> 1.2 + 0.3/tier (SpeedKit). It hit ultimate numbers "
                      "(about 21 per target at Level 6) from a slot-4 key."),
]


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


# Skill-tree actives and class abilities: keep each node's place relative to its neighbours, pull it into its band.
SKILL_BANDS = {"early": (12, 26, 20), "late": (14, 32, 24), "signature": (45, 60, 52)}
CLASS_BANDS = {"offense": (28, 40, 34), "defense": (22, 34, 28), "support": (22, 34, 28)}
LANE_LEVELS = [1, 2, 3, 3, 4, 5]


def pull(old, lo, hi, centre):
    return int(max(lo, min(hi, round(.7 * old + .3 * centre))))


def normalize_skills(trees):
    changes = []
    for tree in trees:
        power = tree["power"]
        for b, lane in enumerate(tree["branches"]):
            for tier, n in enumerate(lane):
                nid = n.get("id", f"{power}_{b}_{tier}")
                level = 4 if n.get("signature") else LANE_LEVELS[min(tier, 5)]
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
            lo, hi, centre = CLASS_BANDS[s["role"]]
            s.setdefault("design_energy", s["energy"])
            s.setdefault("design_cooldown", s["cooldown"])
            old_e, old_cd = s["design_energy"], s["design_cooldown"]
            energy = pull(old_e, lo, hi, centre)
            cd_s = max(.45 * energy, min(.75 * energy, old_cd / 20))
            cooldown = int(round(cd_s * 4) / 4 * 20)
            s["energy"], s["cooldown"] = energy, cooldown
            changes.append((power, s["id"], s["name"], "class:" + s["role"], old_e, energy, old_cd, cooldown, s.get("level", 3)))
    return changes


def doc(rows, dump, skill_changes):
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
    w("(row 2) 12-26, later actives 14-32, signatures 45-60, Offense classes 28-40, Defense and Support 22-34. A skill's")
    w("cooldown is held between 0.2 and 0.8 seconds per stamina (25-45 s for a signature), a class ability's between 0.45")
    w("and 0.75.")
    w("")
    w("| Power | Skill | Kind | Stamina | Cooldown |")
    w("| --- | --- | --- | --- | --- |")
    for power, nid, name, kind, oe, ne, ocd, ncd, level in skill_changes:
        e = f"{ne}" if oe == ne else f"{ne} (was {oe})"
        c = f"{ncd / 20:g}s" if ocd == ncd else f"{ncd / 20:g}s (was {ocd / 20:g}s)"
        w(f"| {power} | {name} | {kind} | {e} | {c} |")
    w("")
    return "\n".join(lines)


def main():
    check = "--check" in sys.argv
    dump = load_dump()
    rows = core_table(dump)
    table = json.dumps(rows, indent=2) + "\n"
    trees = json.loads(TREES.read_text(encoding="utf-8"))
    skill_changes = normalize_skills(trees)
    trees_text = json.dumps(trees, indent=2, ensure_ascii=False) + "\n"
    doc_text = doc(rows, dump, skill_changes) + "\n"
    stale = [p for p, t in ((TABLE, table), (TREES, trees_text), (DOC, doc_text))
             if not p.exists() or p.read_text(encoding="utf-8") != t]
    if check:
        for p in stale:
            print("out of date:", p.relative_to(ROOT))
        sys.exit(1 if stale else 0)
    TABLE.write_text(table, encoding="utf-8")
    TREES.write_text(trees_text, encoding="utf-8")
    DOC.write_text(doc_text, encoding="utf-8")
    for r in rows:
        if r.get("role") not in ("style", "finisher"):
            print(f"{r['action']:34} T{r['unlock']}  {r['energy']:>4}  {r.get('cooldown', 0) / 20:>6g}s")
    print(len(rows), "table rows;", len(skill_changes), "skills and class abilities")


if __name__ == "__main__":
    main()
