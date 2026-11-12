# Power rework status

Audited against the working tree on November 3, 2026. The roster contains **18 powers**;
Sylar is a separate encounter. [Technopathy](technopathy.md) and [Matter Assembly](matter-assembly.md)
are new complete kits, each with five core actions and an eighteen-node interaction tree.

**Empathic Mimicry is the remaining original core kit. Cryokinesis now connects Frost,
solid ice construction, Glacier Ride, Crystal Prison and Whiteout. Invisibility is now
a force-field kit: Fade for you and your friends, Lens Shield and Force Push, Force Bridge, Bubble and Crush, and Force Nova.** A working power, a bug fix, or an expanded tree
is not by itself a full redesign.

The other fifteen original powers have a substantially redesigned core. That does **not** mean all
their optional skills have received the same treatment. Flight, Telekinesis, Cryokinesis, Invisibility and Induced Radioactivity have complete,
interaction-based eighteen-node redesigns; several other trees still mix
new techniques with older attacks, buffs and generic stat nodes.

## Original kits still pending

| Power | Evidence in the current implementation | Remaining work |
| --- | --- | --- |
| **Empathic Mimicry** | [Base actions](../src/main/java/com/jayptucker/heroesevolved/ability/types/MimicryAbility.java) are still Empathize, Recall and three Echo slots. [Borrowed-action handling](../src/main/java/com/jayptucker/heroesevolved/mimicry/MimicryService.java) now preserves held releases, costs and cleanup, including updated fire attacks, but the [workshop redesign](power-workshop.md) remains a proposal. | Redesign the act of learning/recalling powers and its support/combination options; current compatibility work should be preserved. |

## Redesigned cores and optional-skill follow-up

| Power | Implemented signature redesign | Optional-skill status |
| --- | --- | --- |
| **Flight** | [Momentum, tap/hold Skyburst, staged Meteor Drop, Skyhook and Tempest](flight.md). | All eighteen nodes were redesigned into interaction-based branches. |
| **Cryokinesis** | [Held Rime Stream, solid sculpted walls/ramps, physical Glacier Ride, prepared Crystal Prison/shatter and growing Whiteout](cryokinesis.md). | All six active sub-abilities now have distinct mechanics: forged physical lances, finite armor/countershots, a travelling cold front, guided growing paths, pressure-bearing cocoons and staged Frost-sharing constellations. The eighteen saved nodes form Sculptor, Glacier and Winterbinder branches; purchase IDs remain compatible. |
| **Telekinesis** | [Physical fragments, gathered projectiles, staged lift/slam, remote manipulation and fields](telekinesis.md). | All eighteen nodes were redesigned around those interactions. |
| **Electrokinesis** | [Charge, held Live Current, guided orb, telegraphed sky strike and lightning movement](electrokinesis.md). | Circuit Thread, Ground Sink and Overcharge support it; older generic stat nodes and some simple optional actions remain. |
| **Quake** | [Strain, Resonance, directed blasts, fault lines and staged terrain effects](quake.md). | Fault Script, Counterforce and Surgical Fracture add choices; several older targeted attacks/buffs and stat nodes remain. |
| **Regeneration** | [Regrowth, downed bodies, staged limb regrowth, blood bonds and Second Life](regeneration.md). | Older Cauterize/Adrenal Rush/Transfusion and expanded techniques remain alongside the redesigned core. |
| **Space-Time Manipulation** | [Fold Space, shared time, local/entity/death rewind and recorded past travel](space-time-manipulation.md). | Selective Stasis, Spatial Anchor and Second Chance have real mechanics; the tree still retains generic passives and older techniques. |
| **Radiation** | [Core heat states, stacking Radiation Sickness, Fission Leap, EMP venting, the held Meltdown Beam and Critical Mass](induced-radioactivity.md). | All eighteen nodes were redesigned around the new kit (Meltdown, Reactor and Fallout branches); no generic stat nodes remain. |
| **Sonic Resonance** | [Sound Sight, Resonance, tap/held Cry, lure Note, reflecting Wall, Vault and Crescendo](sonic-resonance.md). | Its guide explicitly says the optional tree is unchanged: Shatter Note, Echo Map, Quiet Zone and the older optional attacks/guards still need a matching pass. |
| **Superspeed** | [Momentum, speed controls, physical rush/carry, lightning and Vibrate](superspeed.md). | Reflex Focus, Momentum Strike and Slipstream Rescue were updated; older Mirage/Lightning Throw/Perception paths and generic passives remain. |
| **Precognition** | [Danger Sense, Clarity, foreseen ghosts, painted outcomes and Fate Thread](precognition.md). | Object Reading, Red Margin and Omen Relay are updated; older Foresight/Find the Path/Shared Omen paths and generic stat nodes remain. |
| **Density** (`phasing`) | [Five density notches, Reach Through, Embed, underground Sink and Hollow Ground](density.md). | Optional actions were adapted to the new density states; its generic stat roots/masteries remain. The old Phasing kit is **already replaced**. |
| **Telepathy** | [Mind Threads, command choices, player/mob possession with resistance and private illusions](telepathy.md). | Implanted Command, Shared Sight and Dream Double were updated. Persuasion, Memory Walk and Hive Link retain older mechanics/upgrades. |
| **Pyrokinesis** | [Cinder Spear, Dragon's Breath, Firestorm, Flight-style propulsion, Meteor Dive and Sunfall](pyrokinesis.md). | Core and Flight-style movement reworks are implemented. All six active sub-abilities now have a targeted mechanical and animation refresh; original stat roots/masteries remain, so this is not a complete eighteen-node interaction redesign. |
| **Invisibility** | [Fade shared with nearby friends, Lens Shield → Force Push, walkable Force Bridges, Bubble → Crush (or a shelter for friends) and the telegraphed Force Nova](invisibility.md), reworked in November 2026 around invisibility for yourself, allies and objects plus strong force fields. | All eighteen nodes form Veil, Aegis and Pressure branches: hiding friends, pets and objects, unseen constructs, barriers, a riding disc, a safety net, rebounding projectiles, Pinpoint, multi-target bubbles, chain bursts and an imploding Nova. No generic stat nodes remain. |

The optional-skill findings come from the actual
[eighteen-node definitions](../src/main/resources/data/heroesevolved/skill_trees.json),
[active-skill dispatch](../src/main/java/com/jayptucker/heroesevolved/skills/SkillActionService.java)
and their power services. Generic stat nodes are not broken or missing; they are opportunities
for more distinctive progression, rather than evidence that an entire power was never reworked.

## History cross-check

- `2e4c5aa` (**Power Reworks**) rebuilt Flight, Telekinesis, Regeneration and Radiation, expanded
  Time, and repaired borrowed-power compatibility. Its Mimicry changes do not implement the
  workshop's new play loop.
- `a74d4e9` (**Lightning Rework**) implements the Electrokinesis overhaul.
- `30c321d` includes the Sonic/Precognition redesigns and speedster changes; `3b84a05` follows
  up Superspeed. `2821684` replaces Phasing with Density; `ae8e76e` implements the Quake overhaul.
- The working tree adds the Telepathy, Pyrokinesis, Cryokinesis and Invisibility redesigns discussed above. Those current
  implementations matter even while uncommitted.

The remaining original core to redesign is **Empathic Mimicry**. The older optional trees
still merit focused passes alongside the already redesigned cores.
