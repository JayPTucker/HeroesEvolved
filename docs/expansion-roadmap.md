# Expansion proposal: A Future Unwritten

Repository review and original design proposal, October 26, 2026. This document recommends future work; it does not describe newly implemented gameplay.

## What the repository already supports

The mod has eleven unique power sets, five ability slots, 99 skill nodes, 33 optional active skills, persistent ownership, awakening sequences, energy-driven mastery, and configurable balance. The more distinctive systems include Radiation stress and loss of control, Superspeed momentum, temporal records and visits, real freezing, telepathic story fragments, and a persistent Sylar director.

The current trees are mechanically uniform: two of each branch's three nodes grant the same stamina, damage, or cooldown bonuses across every power. That makes 66 of the 99 nodes statistical upgrades. Several optional attacks and defenses also share the same targeted-hit, resistance-buff, or projectile-screen structure. The largest growth opportunity is giving powers more ways to interact with people, places, and one another.

Story infrastructure exists, but its pieces are separate. `FutureVisions` reveals an ordered sequence of memories; `TemporalStoryService` stores five personal milestones; `SylarStoryData` stores attention, chapters, copied powers, and encounter timing. These are useful foundations for a shared campaign with branching outcomes. They do not yet constitute that campaign.

Ownership remains claimed while a player is offline. Eclipse assignment explicitly skips players when no unclaimed abilities remain. More skill nodes do not increase the number of distinct powered roles the server can support.

## Design principles

1. Each ability set should solve at least one problem through investigation, traversal, combat, and helping someone else.
2. Most upgrades should change a rule: direction, timing, material interaction, target selection, or a consequence. Statistical upgrades can remain as connective tissue.
3. Every major cooperative challenge needs alternative solutions. Missing or offline power owners must not stall the campaign.
4. Strong powers should create decisions. Noise, strain, heat, exposure, and commitment are more interesting than enormous cooldowns alone.
5. Give important actions visible consequences: survivors, changed paintings, rebuilt streets, altered enemy knowledge, and new dialogue.
6. Preserve five main ability keys. Deeper builds should use clear stances and a small number of consistent tap/hold interactions, with an in-game explanation.

## Skill trees: 18–24 meaningful nodes per power

Replace the repeated Control/Force/Survival template with three named specializations unique to each power. Each contains new actions, modifications to existing actions, a utility technique, and a capstone that changes the build. Permit investment in two branches, with one major capstone equipped at a time. Players should be able to experiment through a clear respecialization option outside combat.

For example, a Cryokinetic could choose between preserving frozen targets as rescue cocoons or making them dangerously brittle for chain reactions. Those should lead to different decisions and visuals. A larger damage percentage does not create the same distinction.

Keep ordinary mastery for initial growth. Award advanced training through accomplishments such as rescuing a person from a live hazard, using a controlled shockwave to open a route without injuring hostages, or grounding an electrical overload. Award each discovery once, then reward varied challenges rather than repeated ability activation. Continuing after level five should open more options without multiplying every damage value.

Suggested specialization identities:

| Existing power | Three directions | Distinctive additions |
| --- | --- | --- |
| Quake | Faultwright / Counterforce / Seismic Hunter | Draw a short fault along aimed ground and trigger it later; brace to absorb one telegraphed impact and redirect its force; distinguish footsteps, digging, and heavy machinery through connected ground. Precision fracture opens a doorway while sparing nearby blocks. |
| Flight | Interceptor / Slipstream / Pressure Front | Intercept a falling person and convert the catch into a shared glide; leave a temporary current that allies can ride; bank speed through turns and spend it on an air brake or controlled pressure strike. Give different maneuver silhouettes, not just longer trails. |
| Cryokinesis | Glacier Architect / Whiteout / Preservation | Grow an aimed ice ramp along a bounded path; create a shelter with clear interior visibility and a foggy exterior; preserve an injured ally in a breakable cocoon that pauses specified hazards but prevents their actions. Thermal fractures make deliberate shatter routes. |
| Electrokinesis | Conductor / Capacitor / Storm Rider | Route current through a bounded network of tagged conductive surfaces; ground an overload into rods; store charge for devices or attacks; choose whether an arc follows wet targets or metal anchors. Power a settlement while risking an overload if the circuit is poor. |
| Telekinesis | Fine Manipulation / Kinetic Arsenal / Bastion | Assemble a few nearby loose blocks into a temporary shield with tracked originals; catch a projectile and orbit it until release; brace a failing mission doorway; remotely operate a visible lever or retrieve a dropped quest object. Existing grip and projectile stopping become the base of a manipulation system. |
| Telepathy | Whisper / Dreamweaver / Mind Network | Implant an NPC distraction at a chosen location; reconstruct a witness memory as a short scene; share an investigation clue with linked teammates; hide a group's presence from a limited number of minds until someone attacks. Investigation and deception should matter alongside control effects. |
| Regeneration | Martyr / Adaptation / Recovery | Absorb part of an ally's injury into a temporary wound burden; earn short, capped adaptation to a specific damage source through surviving it; rescue someone from a defined incapacitated state; prepare a limited blood treatment. Build on existing self-resurrection rather than adding a second copy of it. |
| Time Manipulation | Stillness / Wayfinder / Historian | Place a small stasis seal that releases a captured projectile on command; establish a paired travel route with an agreed companion; revisit a deliberately recorded mission scene to inspect evidence; choose a bounded local rewind or a precise personal rescue. Keep what was actually recorded clear. |
| Radiation | Containment / Reactor / Catastrophe | Move dangerous heat into a visible vent cone; transfer a limited charge into a grounded containment device; detect contaminated paths; choose a narrow pressure release or a costly detonation. Calm, controlled play should have a satisfying identity alongside losing control. |
| Sonic Resonance | Resonant Breaker / Echo Cartographer / Harmonist | Sweep a surface for cavities and movement; match a structure's resonant frequency before opening a precise weak point; establish a directional quiet corridor; redirect an enemy shockwave with a timed counter-cry. Voice, material, and geometry should affect the result. |
| Superspeed | Courier / Momentum Fighter / Perception | Build a short rescue route and carry people along it one at a time; bank momentum in a turn for a slingshot strike; spend charge on a brief perception window that reveals incoming attack paths; force a choice between a subtle low-light sprint and a conspicuous high-output run. |

These are additions beyond current mechanics. Current water running, wall assistance, freeze/shatter, contact rescue, blink carrying, telekinetic grip, resurrection, and other implemented features should remain recognizable foundations.

## A small material-interaction system

Introduce named, bounded states such as Chilled, Frozen, Brittle, Conductive, Charged, Resonating, Stressed, and Contaminated. A handful of explicit reactions can create many tactics:

- Quake fractures a frozen, brittle target into a directional fragment burst. A frozen ally in Preservation mode is excluded from that offensive reaction.
- Cryokinesis creates an icy barrier; Electrokinesis charges installed conductive anchors on it, producing a trap with visible entry points.
- Telekinesis holds a loose iron object; Electrokinesis charges it; the telekinetic chooses when and where to throw it.
- Sonic Resonance maps a weak seam; Quake opens that seam with less collateral damage.
- Flight establishes a safe current; Superspeed uses its exit to extend a rescue route.
- A telepath links a Radiation user and helps stabilize a voluntary containment process while a teammate operates the heat sink. They cannot erase every involuntary meltdown with a button.

Each combination needs a visual cue, a short lifetime, an owner, and a counter. Cap reactions per target and disallow reactions from recursively triggering themselves. Effects should arise from positions and states rather than a special co-op key. Allies should be able to coordinate after seeing what the first player did.

## New powers worth adding

### Precognition: Oracle / Painter / Fatekeeper

Make this the next major power. A trance creates a physical painting showing a planned possible event in a recognizable location, including clues to a target, route, or cause. Paintings belong on walls and in an investigation journal. A small scene renderer with a painted treatment can produce images from authored or selected event compositions once per vision; this does not require recording or rendering the entire world continuously.

Combat foresight reads existing attack intent and projectile paths. Story foresight uses events the director has actually selected. A vision should identify its uncertainty: preventing one cause may change the painting or reveal a second possible outcome. Players' arbitrary future decisions cannot be known in advance.

The awakening could produce a painting of the group with Sylar watching from a rooftop and one person's face obscured. Completing the related investigation reveals why that person was missing. The existing cartographer memory is a natural introduction.

### Technopathy: Signal / Infiltration / Machine Bond

Sense active circuits, trace a guarded facility's alarm network, temporarily reroute a door signal, disable a camera, or send an existing machine a command. Tie it to physical connections, line of sight, and purpose-built mission devices. Electrical users provide energy; technopaths provide instructions. Maintaining a controlled machine occupies attention, and cutting its connection breaks control.

### Density Manipulation: Anchor / Ghost Step / Impact

Become dense to resist displacement, sink, brace a passage, and crack suitable floors under a committed landing. Become light to cross fragile terrain, ride an ally's current, and slip through specifically supported narrow passages. A short phase technique needs an explicit exit preview and sufficient clearance; it must never leave a player embedded or bypass arbitrary containers. Mass changes should strongly alter momentum and handling.

### Shapeshifting: Infiltrator / Mimic / Identity

Copy an observed outward appearance and use it to enter an NPC-controlled location, impersonate a known contact, or distract a hunter. Disguises do not copy powers or ownership. Suspicion rises when behavior contradicts the identity. Telepathy senses the underlying mind, a precognitive painting can show the concealed identity, and speaking or attacking can expose an imperfect disguise. Player-facing copies need an inspectable clue so a disguise creates a mystery players can solve.

### Matter Assembly: Sculptor / Salvager / Fabricator

Reshape a limited volume of ordinary material into a ramp, brace, cover, or repair patch. Pull the actual inputs from reachable blocks or supplied material; track what was used. Smoothly moving a few visual proxies is preferable to dozens of full simulated block entities. Reshaping never creates free valuable resources. It provides a rebuilding and infiltration role that Quake's destruction currently lacks.

Add these in batches of one or two, each with meaningful missions and interactions. Precognition and Technopathy supply the strongest next pair because they expand investigation and facilities as well as combat.

## The flagship campaign: A Future Unwritten

Build on the twelve-chunk future city and present it first as a warning. Later the group discovers the same avenue under construction in the present. Its people and buildings become familiar before the threatened disaster.

1. **The painting.** A vision shows a recognizable landmark, a stopped clock, and one unfamiliar person. Telepaths learn who has been dreaming about it. A map or witness route provides an alternative investigation path.
2. **The missing people.** A hidden facility is studying awakened individuals. The group can infiltrate through conversation or disguise, disable systems, open a precise physical route, or trigger a diversion. Captives have identities and memories that survive the mission.
3. **The false rescue.** A survivor carries a fragment of information Sylar wants. Players choose whether to move them, conceal them, or confront their pursuer. Visible evidence distinguishes a genuine warning from a trap.
4. **The fracture.** Investigators discover how the future disaster was prepared. A recorded mission version preserves the evidence for a time traveller. The temporal investigation supplies information; present-day action changes the outcome.
5. **The convergence.** Power failure, pursuit, evacuation, and a developing containment emergency happen in different parts of the district. The group allocates people and resources. Avoid a single long boss fight in which everyone simply deals damage.
6. **The aftermath.** Rescued people return, damaged streets are repaired, witnesses remember the methods used, and the painting changes. Possible outcomes include a city saved intact, a partial evacuation with a scarred district, or a contained disaster followed by recovery missions.

Plan operations around available players and known power owners. Offer NPC equipment, longer physical routes, or alternate objectives when a specialist is absent. A very large server may need several independent incidents at once; the global Sylar singleton should remain deliberate rather than becoming a universal mission bottleneck.

## Sylar, settlements, and long-term play

Extend his existing attention system into intelligible investigation. He should develop counters only after witnessing relevant behavior, and players should be able to discover that knowledge. A repeated rescue route can become an ambush site. Hiding a captive somewhere new, concealing emissions, or leaving a false trail should change the encounter. Avoid arbitrary immunity to a player's favorite power.

Add two original competing organizations: one offers containment, registration, and equipment; another offers secrecy, refuge, and risky freedom. Their objectives occasionally conflict. Village trust can change according to rescues and collateral harm. Make reactions local to witnesses and settlements instead of a single global morality meter.

Unpowered players need consequential roles: operate containment rigs, manage evacuation routes, investigate physical evidence, prepare treatment, and coordinate communications. This supports server growth without violating the one-owner-per-power rule. Add new powers to expand powered slots; any future ownership succession should be an explicit server setting and documented story event, never an automatic offline timeout.

## Implementation order and repository changes needed

1. **Flexible skills and shared reactions.** Replace the strict three-by-three assumptions in `SkillCatalog` and `SkillTreeScreen`, positional node IDs, and branch-number dispatch in `SkillActionService`. Use stable named IDs, explicit prerequisites and effect identifiers, scrolling/panning, and a migration that preserves existing purchases or refunds replaced nodes. Keep server-side purchase/loadout validation.
2. **A small vertical slice.** Rework Quake, Cryokinesis, and Electrokinesis around distinct branches and three tested material interactions. Ship one rescue/infiltration mission demonstrating precision, teamwork, and alternate routes. Playtest this before expanding every tree.
3. **Persistent story planning and Precognition.** Introduce shared incident records, planned causes, evidence, participants, outcomes, and recovery after restart. Connect the existing future memories, time milestones, journal, and Sylar knowledge. Predictions read incident records; they do not invent unrelated warnings.
4. **Technopathy and the facility campaign.** Add the first actual mission devices, infiltration spaces, survivors, and a branching route to the city finale. Add the remaining powers after their supporting interactions exist.
5. **Expand the full roster.** Apply lessons from the first trees, then add settlement reactions, follow-up incidents, and new mission variations.

Preserve the existing particle budgets, world anchoring, and background terrain work. Share a server-wide budget for new terrain and incident jobs, use spatially bounded state queries, and keep distant cosmetic simulations on clients. Do not turn every affected block into a persistent ticking object. Use `WorldMutationPolicy` for edits and track temporary changes through restart-safe recovery.

Preserve temporal boundaries: recorded history and scene snapshots are local and explicit. New mission state, item provenance, ownership, and economic transactions need deliberate rewind rules so a rescue or repair cannot duplicate rewards. Campaign state and scheduled events should have stable identifiers and idempotent completion.

Expand existing gameplay, geometry, resource, and visual fixtures around actual interactions. Add restart/rejoin tests for incidents and migration tests for skill saves. Performance acceptance should use a multiplayer scene with several simultaneous powers, an active incident, and a large terrain effect. Existing automated coverage is valuable, but this review did not rerun it or measure new performance.

## Delivered first release

The first playable slice is implemented in [A Future Unwritten](a-future-unwritten.md):
three eighteen-node trees, three shared reactions, twelve-power ownership with
Precognition, physical changing paintings, and the repeatable annex rescue.
The rest of this document remains a proposal for later releases.

## Original first-release target

Deliver **three deeper trees, three meaningful reactions, one replayable facility mission, and the first prophetic painting**. That demonstrates the expansion's identity in an achievable slice: players discover a future, learn how it happens, and use their particular powers to change it.
