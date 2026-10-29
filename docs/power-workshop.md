# Power workshop

This page records directions for future power redesigns. **The Phasing, Sonic Resonance,
Telepathy and Empathic Mimicry interactions proposed below are planned experiments, not
shipped abilities or completed prototypes.** Their current abilities remain described in
[new powers](new-powers.md) and the skill tree.

Telekinesis is the implemented reference in the current build: visible orbiting fragments,
physical launches with swept collision, a staged lift and slam, caught projectile formations,
remote manipulation, force fields, and distinct gather/hold/release gestures. Its debris is
transient display geometry; it does not mine blocks or create collectible stone. That existing
implementation is separate from the proposals on this page.

The aim for each future redesign is a complete kit: five base actions and eighteen purchased
nodes with different interactions. A branch should change what a player can do, what they
prepare, or how they cooperate. Greater damage, longer duration and shorter cooldowns should
support those choices rather than stand in for them.

## Phasing: choose when to become solid

**Current foundation:** checked wall/floor traversal, temporary intangibility, armor-piercing
contact, and group phase moves. The proposed redesign would connect these actions through
visible transitions between solid and intangible states.

1. **Seam Swim.** Hold against a wall and steer through a short, continuous passage. A moving
   cross-section of the player's silhouette reveals progress and the checked exit. Thick or
   forbidden material makes the contour tighten and stops further travel. Releasing early
   returns to the last safe space. This would replace selected instant traversal moves;
   unrestricted noclip is outside this proposal.
2. **Density Snap.** Come out of a phase with the momentum of the entry: exit a falling phase
   into a compact landing pulse, or leave a sideward phase with a shoulder strike. The ghost
   outline contracts into the solid body before the impact. A deliberately braced enemy can
   interrupt the exit wind-up; the same stored movement cannot produce repeated impacts.
3. **Slip the Hit.** A short, timed partial phase lets a telegraphed strike pass through the
   torso. The missed blow leaves a thin displaced outline, and the player can spend that
   opening on a quick solid counter. Mistimed use provides the ordinary brief phase rather
   than a guaranteed counter. The window should read visually without forcing camera motion.
4. **Phase Thread.** An ally accepts a hand link before the phaser leads them through one
   checked seam. Both silhouettes cross the surface together, with a thread joining entry
   and exit. The route ends after one crossing; losing the link returns each person to a
   safe position. This would reshape existing group teleport techniques into a shared
   traversal sequence with an obvious start, commitment and arrival.

Prototype first: one ordinary wall, a turn while inside the seam, a blocked exit, and a
consenting second player. Judge the body transition and control feel before adding wider
routes or group upgrades.

## Sonic Resonance: set a frequency, then make it answer

**Current foundation:** directed cries, a held positional scream, projectile reflection,
recoil movement, sensing, material breaking and quiet fields. Preserve the recorded voice
and its smooth release; give preparation, attack and returning information different shapes.

1. **Frequency Sweep.** Send an expanding sound front across exposed surfaces. Returning arcs
   distinguish a cavity, moving creature or resonant material through shape and timing as
   well as pitch. The player receives a bounded set of useful echoes as the front returns,
   rather than an instant cloud of generic outlines.
2. **Resonant Mark.** Short notes leave a small vibrating mark on a target or surface. Holding
   the voice narrows the note and strengthens that mark; a deliberate release fractures it
   into a directed pulse. A marked object shows alternating pressure bands before release.
   Breaking line of sight or waiting out the vibration gives opponents a clear escape.
3. **Counter-Cry.** Meet an incoming projectile group or eligible shockwave with a timed
   directional note. The wavefront visibly folds around the contact and exits along the
   chosen angle. A late response only pushes locally; the player cannot continuously reflect
   every attack by holding the key. The timing cue must remain legible with sound muted.
4. **Quiet Corridor.** Place a short directional lane that helps companions pass nearby
   creatures. Sound rings flatten at its two boundaries and footfall ripples vanish inside.
   Attacking or crossing an enemy's close detection range breaks the concealment. A support
   branch could turn a carefully maintained corridor into a limited recovery space, replacing
   the current stationary silence field rather than adding a duplicate buff zone.

Prototype first: a note hitting glass, a moving target and a stone wall; then a visible
outbound/returning echo. Keep voices recognizable and avoid layering multiple full-volume
screams whenever several techniques overlap.

## Telepathy: manipulate attention, share understanding

**Current foundation:** reading minds, suggestions, mind links, temporary puppets and
nightmares. The proposed kit would make what a mind believes as useful as direct control.

1. **Borrowed Attention.** Place a distraction at a visible point. A selected mob looks,
   investigates, then doubts it if no evidence appears. A faint thought trail lets the
   telepath see where its attention went. Human opponents may receive a brief directional
   illusion, but retain control of their camera and movement.
2. **Memory Theatre.** Read an authored witness memory or a short scene actually recorded by
   the server. Translucent figures reconstruct a clue at its location while the real world
   remains visible. Contradictory fragments invite investigation; the power does not invent
   a player's unrecorded past or claim to reveal a future decision.
3. **Dream Double.** A chosen mind perceives a temporary false combatant. The telepath can
   send it across a doorway or use it to bait a committed attack. The double has an imperfect
   pulse and dissolves when tested, taking a limited amount of attention to maintain. Other
   players see the illusion only when their own minds are affected.
4. **Shared Intent.** Consenting linked teammates share a marked threat, destination or
   rescue request. A narrow thought thread briefly points toward the shared intention; a
   later upgrade could share a limited defensive burden at an explicit cost to the link.
   Links end clearly, cannot recursively redistribute the same wound, and do not expose
   unrelated players' private information.

Prototype first: one mob choosing between its real target and an implanted distraction,
followed by two players receiving different illusion views. Information and deception should
be understandable before stronger control effects are added.

## Empathic Mimicry: borrow a way of playing

**Current foundation:** remembered powers, Recall, three copied action slots, voluntary
copying without taking ownership, and empathic support. Compatibility with each redesigned
power is part of this work; copied movement and sustained actions need a real borrowed-use
context and the same cleanup as their original versions.

1. **Emotional Imprint.** Witness a nearby powered ally perform a meaningful action to form
   an imprint. A short visual echo follows the observed hand or movement gesture, making
   the memory's origin recognizable. The ally retains their power. Repeating a harmless
   action should not become a mandatory relationship grind.
2. **Recall as Rehearsal.** Selecting a memory previews the available borrowed gestures and
   their actual costs, then lets the player rehearse one in a brief fading silhouette.
   The preview distinguishes an unavailable action from a ready one before stamina is spent.
   A stable selection replaces cycling blindly through memories during a fight.
3. **Crossfade.** Complete one borrowed action and switch into a second remembered power for
   a short combination window. A split-color echo passes between the two gestures. Repeated
   switching creates visible instability and ends the chain; simultaneous full kits and
   copied ultimates remain outside this direction. Existing cooldowns continue to matter.
4. **Empathic Rescue.** Bind a borrowed movement or protection action to one consenting
   companion: share a short lift, protect an evacuation, or carry a friend through a checked
   escape. The source power determines the physical behavior, while Mimicry pays its own
   borrowing cost. A broken link ends the shared action safely instead of leaving a copied
   flight, shield or carry session running.

Prototype first: one copied instant attack, one sustained effect and one movement action.
Each must work after switching memories, removing the source power, changing dimensions and
ending the link. Only then build cross-power chains around them.

## Turning a workshop into a build

Choose one signature interaction, make its complete control/animation/counter loop playable,
and review it in first person, third person and multiplayer. Record the chosen behavior and
its limits before filling the other base actions and all eighteen nodes. Move an idea out of
this workshop only when its mechanics, visible feedback, cancellation and cleanup exist in
the build; keep unused experiments explicitly marked as proposals.
