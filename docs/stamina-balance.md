# Stamina and balance

Every power spends one stamina bar. This page is the rulebook and the table; `tools/balance.py` holds the same
rules as code and writes `data/heroesevolved/power_balance.json` and the skill numbers in `skill_trees.json`.
Change a number there and re-run it, not here.

## The bar

- **Size:** 100 at Power Level 1, 4 more each level: 136 at Level 10, 156 at 15, 176 at 20.
- **Practice:** a move costs 4% less for every Power Level since it unlocked, up to 30% off (eight levels
  later). A Level 1 move is at the cap by Level 9; an ultimate that opened at Level 15 is 16% off at Level 19.
  The table's numbers below are full price; the HUD and menus show what you actually pay. Rank III's 10% stacks
  on top. Held drains (Still World's per-second cost, flying) aren't discounted.
- **Recovery:** 3 a second at Level 1, 0.12 more each level (5.3 at Level 20), and **double** after five seconds
  without dealing or taking damage. It runs every tick, so the bar fills smoothly.
- **The pause:** spending stamina on a move stops recovery for 1.5 seconds. A fight is paid for out of the bar; the
  quiet afterwards refills it.
- **Held powers** drain a little every tick while they last, and end when the bar can't pay for the next tick. They
  don't restart the pause, so recovery works against a light drain (flying, Fade, Accelerate). Still World and
  Between Seconds hold recovery while they run: nothing comes back until time moves again.
- **Overexertion** is unchanged: hold Shift to use a move you can't afford, and pay in health, Weakness and Nausea.
- **Server config** (`energy` section): `baseEnergy`, `staminaPerPowerLevel`, `staminaRegenerationPerSecond`,
  `staminaRegenerationPerPowerLevel`, `staminaCalmRegenerationMultiplier`, `staminaRecoveryDelayTicks`, and
  `staminaCostScale` / `cooldownScale`, which multiply every cost and cooldown after the table.

## The HUD

The bar sits over the hunger bar in your power's colour, with a notch every 20 stamina so costs can be read off it.
What a move just took stays as a pale chunk that shrinks away. While stamina recovers a glint runs along the fill
and the right-hand number shows the rate (`+4.2/s`, green when calm); right after a move a thin line under the bar
counts down the pause. A held power draws moving stripes at the end of the fill and shows what it takes and how
long that leaves (`-12/s  9s`). Under a fifth the frame pulses red, and a move refused for lack of stamina flashes
and shakes the bar. Full and idle, the numbers fade away.

![The stamina bar: full, just spent, recovering, Still World held, low, refused](artwork/stamina-bar.png)

Photographed by `tools/stamina-preview.init.gradle` (`uitest/StaminaHudPreview`, flag `heroes.preview.stamina`).

## Held and distance costs

| Move | Cost |
| --- | --- |
| Still World | 20 to start (21 at Level 15 after its practice discount), then 12 a second at Level 6, 10 at
  Level 10, 8 at Level 15. It lasts until you press the key again or the stamina runs out (recovery stops
  meanwhile): about 8 s at Level 6, 11 at Level 10, 17 at Level 15 and 19 at Level 20 from a full bar. |
| Between Seconds | 10 to start, then 5 a second (4 at Level 15), recovery stopped; press again to end. |
| Fold Space | 4 to start plus 0.3 for every block folded, at full price: 9 for a 16-block fold, 28 for 80, 49
  for 150. Practice takes up to 30% off (35 for a 150-block fold at Level 15). |
| Free Flight | 4 a second cruising, 9 boosting (recovery still runs). |
| Accelerate | 3 a second running, 6 in Redline. |
| Fade | 2 a second, +1 for each friend faded with you. |
| Glacier Ride | 8 a second moving, 4 standing. |
| Flame Flight | 10 a second moving, 6 hovering (4 with Pillar of Flame). |
| Live Current, Dragon's Breath, the held Canary Cry | 12-15 a second. |

## How a cost is worked out

Stamina pays for effect, measured at the tier the move unlocks:

- **2** per point of damage to one target. Damage over time counts 0.6 a point, a flurry 0.85, and a telegraphed or
  slow, dodgeable hit 0.75.
- **+40%** of that for each extra target a move usually catches, up to four.
- **3** per second of hard control (a hold, a root, a lift, a fear), with the same +40% per target.
- **0.35** per block a move carries you, plus 2.
- Utility (information, building, portals, shields) priced by judgement in the same units.
- **+15** for a tier-5 ultimate.

Each key has a band, so no key costs more or less than its place in the kit: slot 1 6-16, slot 2 10-26, slot 3
14-34, slot 4 16-40, slot 5 55-95. When the formula leaves the band, the cost is clamped and the move's **damage**
changes instead, so damage always follows stamina.

Cooldown follows cost by role, in seconds per point of stamina: basic 0.08 (at least half a second), quick 0.15,
skill 0.3, heavy 0.45, ultimate 0.75. Toggles and held powers get a short cooldown and pay by the second.

Damage scales with tier inside the code (4 + tier, 5 + 1.5 x tier...), a move gets cheaper with practice, and
the bar grows by 76%, so a move gets more for its stamina as you level, while the newest and biggest moves
stay at full price until you have had them a while.

## Unlock levels

One new key at each stage: Power Level 1, 3, 6, 10 and 15 (tiers 1 to 5). A kit whose first key is a toggle
(Free Flight, Accelerate, Fade, Shift) opens its second key at Level 1 too. Superspeed's Cyclone moved from
Level 10 to 6, and Flight's Meteor Drop and Skyhook from 6 and 10 to 3 and 6, so neither kit skips a stage.
Skill trees open by row: row 1 at Level 1, row 2 at Level 3, rows 3 and 4 at Level 6, row 5 at Level 10 and the
capstones at Level 15; signature skills at Level 10. Skill points are unchanged: 3 at Level 1, about one more
each level after (21 at Level 15, 28 Evolved), plus training and story notes, against 33 for a whole tree.

## Damage that changed

- **speed_cyclone**: Cyclone's wind: 2 + 0.5/tier every half second -> 1.2 + 0.3/tier (SpeedKit). It hit ultimate numbers (about 21 per target at Level 6) from a slot-4 key.

## Core moves

### Telepathy

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `telepathy_read_mind` | 1 | 8 | 0.75s | 7 / 0.7s, Level 1 | held read; builds Focus |
| 2 | `telepathy_suggestion` | 3 | 21 | 9.5s | 25 / 12s, Level 3 | a 5 s command on one mind |
| 3 | `telepathy_mind_link` | 6 | 18 | 5.5s | 27 / 0.6s, Level 6 | links willing minds (no damage) |
| 4 | `telepathy_puppet` | 10 | 40 | 30s | 50 / 51.75s, Level 10 | about 10 s in control of a body |
| 5 | `telepathy_nightmare` | 15 | 63 | 47s | 72 / 63s, Level 15 | 5 s of fear on everyone linked |

### Superspeed

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `speed_accelerate` | 1 | 0 | 0s | 0 / 0s, Level 1 | toggle; 3 a second while running (6 in Redline) |
| 2 | `speed_blitz` | 1 | 23 | 2.5s | 14 / 2s, Level 1 | four blows of 2.5 after a 22-block rush (a gap-closer: half its distance counts) |
| 3 | `speed_snatch` | 3 | 16 | 4.75s | 20 / 10s, Level 3 | takes items, shots and a weapon |
| 4 | `speed_cyclone` | 6 | 38 | 17s | 30 / 12s, Level 10 | 3 s of wind: 1.2 + 0.3/tier every half second, lift, no air |
| 5 | `speed_lightspeed` | 15 | 88 | 66s | 50 / 45s, Level 15 | 8 + tier + Momentum/5 to everything on a 48-block line |

### Regeneration

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `regeneration_rapid_mending` | 1 | 16 | 15s | 25 / 25s, Level 1 | spends Regrowth on an instant heal (Regrowth limits it) |
| 2 | `regeneration_blood_gift` | 3 | 16 | 4.75s | 21 / 22.5s, Level 3 | heals a friend; held, a stream |
| 3 | `regeneration_indestructible` | 6 | 32 | 14s | 40 / 60s, Level 6 | 10 s: no knockback, dulled pain, hard punches |
| 4 | `regeneration_healing_aura` | 10 | 34 | 15s | 36 / 36s, Level 10 | 10 s: friends heal with you; you share their wounds |
| 5 | `regeneration_second_life` | 15 | 55 | 90s | 40 / 90s, Level 15 | a friend heals fully and cheats death once (also 50 Regrowth) |

### Flight

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `flight_toggle` | 1 | 0 | 0s | 0 / 0s, Level 1 | toggle; 4 a second cruising, 9 boosting |
| 2 | `flight_launch` | 1 | 12 | 1.75s | 18 / 3s, Level 1 | Skyburst: a dash of up to 20 blocks |
| 3 | `flight_dive_bomb` | 3 | 25 | 7.5s | 28 / 6s, Level 6 | 5 + tier + Momentum on landing, a pressure wave |
| 4 | `flight_skyward_grab` | 6 | 21 | 6.25s | 30 / 5s, Level 10 | seize and carry, then a throw |
| 5 | `flight_cyclone` | 15 | 70 | 52s | 47 / 22.5s, Level 15 | Tempest: lifts and bursts everything round its eye |

### Electrokinesis

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `electro_chain_lightning` | 1 | 15 | 1.25s | 18 / 1.5s, Level 1 | 4 + 1.5/tier, chaining at 75% |
| 2 | `electro_ball_lightning` | 3 | 25 | 7.5s | 25 / 3s, Level 3 | an orb that lashes (3 + tier) and bursts (5 + tier) |
| 3 | `electro_thunderstrike` | 6 | 27 | 8s | 39 / 8s, Level 6 | 4 + 2/tier where you marked |
| 4 | `electro_ride_the_lightning` | 10 | 30 | 9s | 30 / 5s, Level 10 | a bolt's dash; 4 + tier to what you pass |
| 5 | `electro_storm_surge` | 15 | 72 | 54s | 94 / 72s, Level 15 | 8 + tier + Charge to everything round you |

### Vibe

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `vibe_blast` | 1 | 14 | 1s | 9 / 1.1s, Level 1 | a wave of 4 + tier (palm) to 7 + 1.5/tier |
| 2 | `vibe_breach` | 3 | 20 | 6s | 20 / 6.5s, Level 3 | two-way portals |
| 3 | `vibe_read` | 6 | 14 | 4.25s | 12 / 5s, Level 6 | replays a scene's fights |
| 4 | `vibe_breach_guard` | 10 | 22 | 6.5s | 21 / 8.5s, Level 10 | a moving breach that swallows shots |
| 5 | `vibe_frequency_lock` | 15 | 58 | 44s | 42 / 17s, Level 15 | 4 + tier (+4 if read) and a slow, after a telegraph |

### Matter Assembly

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `matter_assembly_barricade` | 1 | 12 | 3.5s | 12 / 3s, Level 1 | a 3x2 wall (also costs material) |
| 2 | `matter_assembly_shard_lance` | 1 | 12 | 1s | 14 / 1.5s, Level 1 | a shard up to 64 blocks |
| 3 | `matter_assembly_causeway` | 3 | 20 | 6s | 22 / 8s, Level 3 | 8 s of walking on air |
| 4 | `matter_assembly_cage` | 6 | 22 | 10s | 30 / 11s, Level 6 | a cell round one enemy |
| 5 | `matter_assembly_cathedral` | 15 | 70 | 52s | 70 / 50s, Level 15 | three turrets for 20 s |

### Invisibility

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `invisibility_fade` | 1 | 6 | 0.5s | 6 / 0.5s, Level 1 | toggle; 2 a second, +1 per friend |
| 2 | `invisibility_silent_step` | 1 | 14 | 2s | 14 / 4s, Level 1 | Lens Shield: 3 s, 70% off frontal harm, stops shots |
| 3 | `invisibility_unseen_strike` | 3 | 14 | 2s | 16 / 4s, Level 3 | Force Bridge: a 12-block span |
| 4 | `invisibility_shroud` | 6 | 29 | 13s | 25 / 10s, Level 6 | Bubble: 5 s hold, then a crush (6 + 2/tier) |
| 5 | `invisibility_ghost_protocol` | 15 | 77 | 58s | 90 / 90s, Level 15 | Force Nova: heavy damage and a throw in 12 blocks |

### Telekinesis

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `telekinesis_push` | 1 | 8 | 0.75s | 12 / 1.5s, Level 1 | a shove (2 + tier/4) |
| 2 | `telekinesis_grip` | 3 | 24 | 7.25s | 30 / 10s, Level 3 | hold, then a throw |
| 3 | `telekinesis_bullet_stop` | 6 | 22 | 10s | 32 / 21s, Level 6 | catches every shot, then turns them |
| 4 | `telekinesis_disarm` | 10 | 16 | 4.75s | 20 / 8s, Level 10 | tears a weapon away |
| 5 | `telekinesis_storm` | 15 | 79 | 59s | 94 / 72s, Level 15 | lifts and slams everyone round you |

### Hydrokinesis

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `hydro_water_whip` | 1 | 10 | 0.75s | 10 / 0.7s, Level 1 | 4 + tier along your aim |
| 2 | `hydro_crashing_wave` | 1 | 22 | 6.5s | 26 / 6s, Level 1 | 5 + tier to what it carries |
| 3 | `hydro_octopus_form` | 3 | 30 | 14s | 30 / 14s, Level 3 | 10 s: arms lash (6 + tier), shots swallowed, a third off blows |
| 4 | `hydro_wave_rider` | 6 | 20 | 6s | 22 / 8s, Level 6 | 6 s of surfing |
| 5 | `hydro_maelstrom` | 15 | 79 | 59s | 95 / 75s, Level 15 | a waterspout that drags and drowns |

### Technopathy

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `technopathy_handshake` | 1 | 8 | 0.75s | 8 / 1s, Level 1 | enrols a network |
| 2 | `technopathy_remote_hand` | 1 | 5 | 0.6s | 5 / 0.6s, Level 1 | works a linked device |
| 3 | `technopathy_tripwire` | 3 | 16 | 4.75s | 20 / 3.5s, Level 3 | a trap: 6 + tier and a stumble |
| 4 | `technopathy_intercept` | 6 | 24 | 11s | 30 / 12s, Level 6 | 10 s of arrow diversion |
| 5 | `technopathy_machine_city` | 15 | 75 | 56s | 85 / 60s, Level 15 | 12 s of a defended district |

### Shapeshifting

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `shapeshift_morph` | 1 | 0 | 1s | 0 / 1s, Level 1 | a form pays its own size cost and drain |
| 2 | `shapeshift_instinct` | 3 | 0 | 0.3s | 0 / 0.3s, Level 3 | each form's move pays its own cost |
| 3 | `shapeshift_decoy` | 6 | 16 | 4.75s | 16 / 10s, Level 6 | a shell that draws the hunt for 6 s |
| 4 | `shapeshift_reflection` | 10 | 21 | 6.25s | 18 / 12s, Level 10 | become your attacker; its kind forget you |
| 5 | `shapeshift_nightmare` | 15 | 55 | 41s | 40 / 30s, Level 15 | 5 s of fear in 12 blocks |

### Pyrokinesis

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `pyro_flare` | 1 | 16 | 1.25s | 14 / 1.25s, Level 1 | 4 + tier and 4 s burning, splashing nearby |
| 2 | `pyro_flame_palm` | 1 | 8 | 3s | 12 / 4s, Level 1 | held cone; 12 a second while breathing |
| 3 | `pyro_ring_of_fire` | 3 | 27 | 12s | 35 / 15s, Level 3 | an 8 s wall of flame (4 + tier) that draws burning foes in |
| 4 | `pyro_backdraft` | 6 | 12 | 4s | 25 / 5s, Level 6 | Flame Flight toggle; 6-10 a second |
| 5 | `pyro_inferno` | 15 | 68 | 51s | 76 / 48s, Level 15 | Sunfall: a charged sun on the marked area |

### Cryokinesis

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `cryo_frost_bolt` | 1 | 8 | 1s | 14 / 1s, Level 1 | held stream; pulses pay as they go |
| 2 | `cryo_flash_freeze` | 3 | 18 | 5.5s | 35 / 15s, Level 3 | an ice wall or ramp |
| 3 | `cryo_ice_wall` | 6 | 12 | 4s | 20 / 4s, Level 6 | Glacier Ride toggle; 4-8 a second |
| 4 | `cryo_glacial_spikes` | 10 | 27 | 12s | 30 / 10s, Level 10 | Crystal Prison: a hold, then a shatter (5 + tier) |
| 5 | `cryo_blizzard` | 15 | 79 | 59s | 88 / 72s, Level 15 | Whiteout |

### Empathic Mimicry

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `mimicry_echo_1` | 1 | copied | copied x1.5 | 0 / 1s, Level 1 | pays the copied move's own cost |
| 2 | `mimicry_echo_2` | 3 | copied | copied x1.5 | 0 / 1s, Level 3 | pays the copied move's own cost |
| 3 | `mimicry_echo_3` | 6 | copied | copied x1.5 | 0 / 1s, Level 6 | pays the copied move's own cost |
| 4 | `mimicry_echo_4` | 10 | copied | copied x1.5 | 0 / 1s, Level 10 | pays the copied move's own cost |
| 5 | `mimicry_echo_5` | 15 | copied | copied x1.5 | 0 / 1s, Level 15 | pays the copied move's own cost |

### Radiation

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `radiation_radiant_touch` | 1 | 14 | 1s | 15 / 1s, Level 1 | a cone of 3 + tier to up to six |
| 2 | `radiation_pulse` | 3 | 22 | 6.5s | 26 / 6.4s, Level 3 | Fission Leap: a blast and a launch; the slam is free |
| 3 | `radiation_emp` | 6 | 20 | 9s | 24 / 14s, Level 6 | kills machines for 10 s, shoves, reveals, vents Core |
| 4 | `radiation_meltdown_beam` | 10 | 18 | 6s | 25 / 6s, Level 10 | a held beam that spends Core |
| 5 | `radiation_exploding_man` | 15 | 120 | 300s | 140 / 450s, Level 15 | Critical Mass: a 24-block nuclear blast after 25 s |

### Phasing

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `phasing_shift` | 1 | 4 | 0.3s | 4 / 0.3s, Level 1 | a notch of density; holding it drains 1.7-3.3 a second |
| 2 | `phasing_reach` | 1 | 16 | 2.5s | 16 / 3s, Level 1 | 5 + tier that ignores armour, and no healing for 5 s |
| 3 | `phasing_embed` | 3 | 21 | 6.25s | 24 / 10s, Level 3 | half into the floor for 5 s, taking 50% more |
| 4 | `phasing_sink` | 6 | 16 | 2.5s | 20 / 2s, Level 6 | move through the ground |
| 5 | `phasing_hollow` | 15 | 66 | 50s | 60 / 45s, Level 15 | everyone in 6 + tier blocks sinks for 5 s |

### Time Manipulation

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `time_blink` | 1 | 4 + 0.3/block | 1.5s | 25 / 6s, Level 1 | Fold Space: 4 to start, 0.3 for every block folded |
| 2 | `time_slow` | 1 | 10 + 5/s | 2s | 56 / 27s, Level 1 | Between Seconds: held, 5 a second (recovery stops) |
| 3 | `time_stop` | 6 | 20 + 12/s | 10s | 60 / 31.5s, Level 6 | Still World: held, 12/10/8 a second by tier (recovery stops) |
| 4 | `time_rewind` | 10 | 120 | 240s | 130 / 240s, Level 10 | Rewrite the Moment: most of a tank, a long recovery |
| 5 | `temporal_snapshot` | 15 | 35 | 30s | 40 / 30s, Level 15 | Walk the Past: travel 35, recording 18, return free |

### Sonic Resonance

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `sonic_cry` | 1 | 12 | 1s | 18 / 1.25s, Level 1 | tap: 4 + 1.2/tier in a narrow cone; held: 12 a second |
| 2 | `sonic_note` | 3 | 14 | 4.25s | 16 / 4.8s, Level 3 | a lure that plays 8 s |
| 3 | `sonic_wall` | 6 | 22 | 10s | 23 / 11.25s, Level 6 | 5 s that turns shots back |
| 4 | `sonic_vault` | 10 | 16 | 2.5s | 25 / 4s, Level 10 | a leap and a safe landing |
| 5 | `sonic_crescendo` | 15 | 68 | 51s | 48 / 48s, Level 15 | a charged cone, 6 + 10 x charge + tier, after up to 3 s of build |

### Quake

| Key | Move | Level | Stamina | Cooldown | Was | Pays for |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `quake_shockwave` | 1 | 13 | 1s | 15 / 1.5s, Level 1 | 3 + 1.5/tier, a throw, shots shaken to dust |
| 2 | `quake_resonate` | 3 | 24 | 7.25s | 18 / 8s, Level 3 | 3 s of shaking (1.5 + tier/2 a pulse), a stagger |
| 3 | `quake_fault_line` | 6 | 28 | 8.5s | 30 / 4.8s, Level 6 | a racing crack: (4 + tier) and a launch |
| 4 | `quake_blast` | 10 | 10 | 0s | 10 / 0s, Level 10 | held beam; it drains per second |
| 5 | `quake_earthquake` | 15 | 81 | 61s | 102 / 78s, Level 15 | seconds of shaking, then an aftershock |

## Fighting styles

Strikes 2, finishers (kicks, claps, flourishes) 5 (Pressure Palm 6), dodges 8 and back dodges 11 (a speedster's
6 and 9, the Reckless Brawler's brace 6 and 9), and 0.9 s between dodges (0.7 s for a speedster). Every one pauses
recovery, so a long exchange of blows is paid for.

## Finishers

Every finisher costs 8 stamina and shares one 3 s cooldown across all powers; a finished
foe gives 10 stamina back. Entries: `finisher_pyrokinesis`, `finisher_time_manipulation`, `finisher_telepathy`, `finisher_electrokinesis`, `finisher_hydrokinesis`, `finisher_cryokinesis`, `finisher_quake`, `finisher_sonic_resonance`, `finisher_radiation`, `finisher_superspeed`, `finisher_flight`, `finisher_telekinesis`, `finisher_phasing`, `finisher_invisibility`, `finisher_regeneration`, `finisher_vibe`, `finisher_technopathy`, `finisher_matter_assembly`, `finisher_empathic_mimicry`, `finisher_shapeshifting`.

## Skill-tree and class abilities

Actives keep their designed order and are pulled 30% toward the middle of their band, then clamped: early actives
(row 2) 12-26, later actives 14-32, signatures 45-60, Offense classes 28-40, Defense and Support 22-34. A skill's
cooldown is held between 0.2 and 0.8 seconds per stamina (25-45 s for a signature), a class ability's between 0.45
and 0.75.

| Power | Skill | Kind | Stamina | Cooldown |
| --- | --- | --- | --- | --- |
| quake | Seismic Lance | skill | 23 (was 24) | 4.5s |
| quake | Fault Script | skill | 20 (was 18) | 4s |
| quake | Vibration Shell | skill | 26 (was 30) | 12s |
| quake | Counterforce | skill | 17 (was 14) | 3.5s (was 1s) |
| quake | Pressure Step | skill | 21 (was 22) | 5s |
| quake | Surgical Fracture | skill | 21 (was 20) | 6s |
| quake | Fissure Maw | class:offense | 35 | 18s |
| quake | Stone Rampart | class:defense | 26 (was 25) | 19.5s (was 22s) |
| quake | Bedrock Stance | class:support | 26 (was 25) | 19.5s (was 24s) |
| flight | Rescue Rush | skill | 20 | 6s |
| flight | Wind Sanctuary | skill | 27 (was 28) | 12s |
| flight | Barrel Roll | skill | 19 (was 18) | 5s |
| flight | Jetstream | skill | 24 | 10s |
| flight | Thermal Lance | skill | 23 (was 24) | 8s |
| flight | Vacuum Clap | skill | 28 (was 30) | 9s |
| flight | Tempest Drill | class:offense | 31 (was 30) | 15s |
| flight | Gale Wall | class:defense | 26 (was 25) | 19.5s (was 22s) |
| flight | Formation Flight | class:support | 26 (was 25) | 19.5s (was 24s) |
| cryokinesis | Crystal Lance | skill | 24 (was 26) | 5s |
| cryokinesis | Rime Loom | skill | 24 | 7s |
| cryokinesis | Frost Armor | skill | 26 (was 30) | 13s |
| cryokinesis | Stillheart Cocoon | skill | 25 (was 26) | 12s |
| cryokinesis | Cold Snap | skill | 26 (was 32) | 9s |
| cryokinesis | Brittle Constellation | skill | 27 (was 28) | 9s |
| cryokinesis | Hailstorm | class:offense | 35 | 20s |
| cryokinesis | Crystal Bastion | class:defense | 29 (was 30) | 21.75s (was 24s) |
| cryokinesis | Aurora Veil | class:support | 26 (was 25) | 19.5s (was 26s) |
| electrokinesis | Arc Lance | skill | 26 (was 28) | 5.25s (was 5s) |
| electrokinesis | Circuit Thread | skill | 23 (was 22) | 5s |
| electrokinesis | Static Screen | skill | 26 (was 28) | 11s |
| electrokinesis | Ground Sink | skill | 20 (was 18) | 4s (was 2s) |
| electrokinesis | Blackout | skill | 47 (was 45) | 30s |
| electrokinesis | Overcharge | skill | 24 (was 25) | 11s |
| electrokinesis | Arc Lattice | skill | 14 (was 10) | 2.75s (was 1s) |
| electrokinesis | Tesla Spire | class:offense | 35 | 20s |
| electrokinesis | Faraday Cage | class:defense | 26 (was 25) | 19.5s (was 22s) |
| electrokinesis | Jumpstart | class:support | 22 (was 20) | 16.5s (was 18s) |
| telekinesis | Vector Lance | skill | 23 (was 24) | 5s |
| telekinesis | Remote Hand | skill | 14 (was 8) | 2.75s (was 2s) |
| telekinesis | Gravity Well | skill | 26 (was 35) | 12s |
| telekinesis | Kinetic Volley | skill | 24 | 6s |
| telekinesis | Vector Step | skill | 21 (was 22) | 8s |
| telekinesis | Force Bubble | skill | 28 (was 30) | 15s |
| telekinesis | Implosion | class:offense | 35 | 20s |
| telekinesis | Debris Halo | class:defense | 26 (was 25) | 19.5s (was 22s) |
| telekinesis | Gathering Hand | class:support | 26 (was 25) | 19.5s (was 20s) |
| telepathy | Drop It | skill | 21 (was 22) | 8s |
| telepathy | Persuasion | skill | 32 (was 40) | 20s |
| telepathy | Far Hearing | skill | 19 (was 18) | 15.25s (was 20s) |
| telepathy | Memory Walk | skill | 20 (was 18) | 10s |
| telepathy | Dream Double | skill | 26 (was 32) | 9s |
| telepathy | Hive Link | skill | 25 (was 26) | 20s (was 30s) |
| telepathy | Psychic Storm | class:offense | 35 | 22s |
| telepathy | Blind Spot | class:defense | 22 (was 20) | 16.5s (was 20s) |
| telepathy | Hive Mind | class:support | 26 (was 25) | 19.5s (was 26s) |
| regeneration | Bloodletting | skill | 23 (was 24) | 8s |
| regeneration | Take the Wound | skill | 18 (was 16) | 8s |
| regeneration | Adrenal Rush | skill | 26 (was 30) | 12s |
| regeneration | Adaptation | skill | 21 (was 20) | 16.75s (was 30s) |
| regeneration | Play Dead | skill | 23 (was 24) | 15s |
| regeneration | Revive | skill | 28 (was 30) | 20s |
| regeneration | Vital Burst | class:offense | 28 (was 25) | 15s |
| regeneration | Undying | class:defense | 29 (was 30) | 21.75s (was 45s) |
| regeneration | Lifebloom | class:support | 26 (was 25) | 19.5s (was 26s) |
| time_manipulation | Selective Stasis | skill | 21 (was 22) | 8s |
| time_manipulation | Stasis Seal | skill | 24 | 10s |
| time_manipulation | Spatial Anchor | skill | 16 (was 15) | 3.25s (was 3s) |
| time_manipulation | Moment's Edge | skill | 27 (was 28) | 12s |
| time_manipulation | Singularity | skill | 51 (was 50) | 35s |
| time_manipulation | Second Chance | skill | 26 (was 30) | 20s |
| time_manipulation | Fast Forward | skill | 21 (was 20) | 12s |
| time_manipulation | Hour Hand | class:offense | 31 (was 30) | 15s |
| time_manipulation | Time Loop | class:defense | 29 (was 30) | 21.75s (was 30s) |
| time_manipulation | Borrowed Time | class:support | 26 (was 25) | 19.5s (was 24s) |
| radiation | Gamma Lance | skill | 19 (was 18) | 5s |
| radiation | Heat Vent | skill | 14 (was 10) | 10s |
| radiation | Lead Skin | skill | 23 (was 24) | 15s |
| radiation | Reactor Core | skill | 14 (was 6) | 11.25s (was 15s) |
| radiation | Decontaminate | skill | 20 | 12s |
| radiation | Glowing Path | skill | 27 (was 28) | 20s |
| radiation | Ground Zero | class:offense | 38 (was 40) | 22s |
| radiation | Containment Dome | class:defense | 29 (was 30) | 21.75s (was 24s) |
| radiation | Reactor Hearth | class:support | 29 (was 30) | 21.75s (was 26s) |
| sonic_resonance | Focused Shriek | skill | 24 (was 26) | 5s |
| sonic_resonance | Shatter Note | skill | 21 (was 20) | 6s |
| sonic_resonance | Dissonant Wave | skill | 26 (was 30) | 9s |
| sonic_resonance | Echo Map | skill | 18 (was 16) | 14.5s (was 20s) |
| sonic_resonance | Resonant Guard | skill | 26 (was 30) | 11s |
| sonic_resonance | Quiet Zone | skill | 25 (was 26) | 20s |
| sonic_resonance | Resonance Cascade | class:offense | 35 | 18s |
| sonic_resonance | Silence Dome | class:defense | 26 (was 25) | 19.5s (was 22s) |
| sonic_resonance | Battle Hymn | class:support | 26 (was 25) | 19.5s (was 24s) |
| superspeed | Reflex Focus | skill | 20 | 8s |
| superspeed | Speed Mirage | skill | 21 (was 20) | 12s |
| superspeed | Momentum Strike | skill | 26 (was 28) | 9s |
| superspeed | Lightning Throw | skill | 20 (was 18) | 5s |
| superspeed | Slipstream Rescue | skill | 23 (was 24) | 6s |
| superspeed | Perception | skill | 23 (was 22) | 15s |
| superspeed | Lightning Flurry | class:offense | 31 (was 30) | 15s |
| superspeed | Phase Blur | class:defense | 26 (was 25) | 19.5s (was 24s) |
| superspeed | Relay Run | class:support | 26 (was 25) | 19.5s (was 20s) |
| pyrokinesis | Ember Lash | skill | 21 (was 22) | 4.25s (was 4s) |
| pyrokinesis | Ember Mine | skill | 21 (was 20) | 5s |
| pyrokinesis | Heat Mantle | skill | 26 (was 28) | 12s |
| pyrokinesis | Blue Lance | skill | 28 (was 30) | 8s |
| pyrokinesis | Cauterizing Flame | skill | 23 (was 24) | 15s |
| pyrokinesis | Fire Shepherd | skill | 14 (was 10) | 6s |
| pyrokinesis | Phoenix | skill | 54 (was 55) | 30s |
| pyrokinesis | Starfall | class:offense | 35 | 18s |
| pyrokinesis | Ember Shroud | class:defense | 26 (was 25) | 19.5s (was 24s) |
| pyrokinesis | Hearthfire | class:support | 26 (was 25) | 19.5s (was 26s) |
| invisibility | Vanishing Touch | skill | 19 (was 18) | 10s |
| invisibility | Vanishing Squad | skill | 28 (was 30) | 22.5s (was 30s) |
| invisibility | Force Barrier | skill | 23 (was 24) | 15s |
| invisibility | Force Disc | skill | 21 (was 20) | 15s |
| invisibility | Pinpoint | skill | 16 (was 14) | 6s |
| invisibility | Kinetic Shell | skill | 28 (was 30) | 20s |
| invisibility | Shadow Strike | class:offense | 31 (was 30) | 16s |
| invisibility | Refraction Cloak | class:defense | 26 (was 25) | 19.5s (was 22s) |
| invisibility | Prism Haze | class:support | 26 (was 25) | 19.5s (was 26s) |
| phasing | Pass Through | skill | 21 (was 22) | 8s |
| phasing | Phase Carry | skill | 25 (was 26) | 10s |
| phasing | Harden | skill | 20 | 15s |
| phasing | Sinkhole | skill | 27 (was 28) | 15s |
| phasing | Fade Through | skill | 19 (was 18) | 12s |
| phasing | Phase Fall | skill | 23 (was 22) | 10s |
| phasing | Phase Eruption | class:offense | 31 (was 30) | 16s |
| phasing | Dense Bulwark | class:defense | 26 (was 25) | 19.5s (was 24s) |
| phasing | Phase Field | class:support | 26 (was 25) | 19.5s (was 24s) |
| empathic_mimicry | Borrowed Strike | skill | 21 (was 22) | 5s |
| empathic_mimicry | Sense Powers | skill | 18 (was 16) | 14.5s (was 20s) |
| empathic_mimicry | Empathic Surge | skill | 24 (was 26) | 15s |
| empathic_mimicry | Overcharge | skill | 17 (was 14) | 10s |
| empathic_mimicry | Calm | skill | 19 (was 18) | 12s |
| empathic_mimicry | Absorb Pain | skill | 21 (was 20) | 12s |
| empathic_mimicry | Prismatic Overflow | class:offense | 35 | 18s |
| empathic_mimicry | Empathic Ward | class:defense | 26 (was 25) | 19.5s (was 24s) |
| empathic_mimicry | Kindred Light | class:support | 26 (was 25) | 19.5s (was 20s) |
| technopathy | Batch Instruction | skill | 17 (was 16) | 4s |
| technopathy | Delayed Instruction | skill | 18 (was 16) | 5s |
| technopathy | Sentry Seal | skill | 19 (was 18) | 5s |
| technopathy | Kill Switch | skill | 21 (was 20) | 7s |
| technopathy | Winchline | skill | 20 | 5s |
| technopathy | Domino Engine | skill | 30 (was 32) | 9s |
| technopathy | Drone Swarm | class:offense | 31 (was 30) | 23.25s (was 24s) |
| technopathy | Firewall | class:defense | 26 (was 25) | 19.5s (was 22s) |
| technopathy | Overclock | class:support | 26 (was 25) | 19.5s (was 24s) |
| matter_assembly | Switchback | skill | 20 | 6s |
| matter_assembly | Drawbridge | skill | 20 (was 18) | 4s (was 3s) |
| matter_assembly | Scattershot | skill | 21 (was 22) | 5s |
| matter_assembly | Faultline | skill | 27 (was 28) | 9s |
| matter_assembly | Folded Shelter | skill | 21 (was 22) | 8s |
| matter_assembly | Rescue Cradle | skill | 20 (was 18) | 6s |
| matter_assembly | Forge Cannon | class:offense | 31 (was 30) | 18s |
| matter_assembly | Bulwark Lattice | class:defense | 26 (was 25) | 19.5s (was 24s) |
| matter_assembly | Mending Lattice | class:support | 26 (was 25) | 19.5s (was 22s) |
| vibe | Vibe Lance | skill | 19 (was 18) | 4.25s |
| vibe | Reverb | skill | 27 (was 29) | 7.25s |
| vibe | Attune Frequency | skill | 12 (was 8) | 2.5s |
| vibe | Flashstep | skill | 16 (was 13) | 4.25s |
| vibe | Co-Vibe | skill | 16 (was 14) | 5.25s |
| vibe | Prism Lock | skill | 31 (was 34) | 13s |
| vibe | Crosswave | class:offense | 34 | 15.25s (was 11s) |
| vibe | Mirror Breach | class:defense | 27 (was 26) | 12.5s |
| vibe | Sanctuary Breach | class:support | 28 | 13s |
| shapeshifting | Quick Change | skill | 13 (was 10) | 3s |
| shapeshifting | Shed | skill | 17 (was 14) | 8s |
| shapeshifting | Hybrid | skill | 17 (was 16) | 13.5s (was 20s) |
| shapeshifting | Chimera | skill | 20 (was 18) | 16s (was 20s) |
| shapeshifting | Apex Form | skill | 51 (was 50) | 45s |
| shapeshifting | Borrowed Face | skill | 19 (was 18) | 15s |
| shapeshifting | Doppelganger | skill | 21 (was 20) | 15s |
| shapeshifting | Many Hands | class:offense | 34 | 15.25s (was 12s) |
| shapeshifting | Plate Mail | class:defense | 27 (was 26) | 15s |
| shapeshifting | Masquerade | class:support | 31 (was 32) | 20s |
| hydrokinesis | Geyser | skill | 20 | 4.5s |
| hydrokinesis | Pressure Jet | skill | 25 (was 26) | 7s |
| hydrokinesis | Tide Wall | skill | 19 (was 18) | 6s |
| hydrokinesis | Water Spout | skill | 24 | 8s |
| hydrokinesis | Healing Water | skill | 17 (was 16) | 5s |
| hydrokinesis | Rain Call | skill | 28 (was 30) | 20s |
| hydrokinesis | Tsunami | class:offense | 38 (was 40) | 17s (was 13s) |
| hydrokinesis | Tidal Dome | class:defense | 28 | 13s |
| hydrokinesis | Healing Spring | class:support | 27 (was 26) | 12.25s (was 12s) |

