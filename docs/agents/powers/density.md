# Density: notes for agents

Phasing, embedding and the interaction tree (`phasing/`, `DensityArt`, `DensitySkills`). What the player sees: [density.md](../../density.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Density rework

Density's tree (`phasing/DensitySkills`, numbers in `phasing/DensityGeometry`, art in `client/effects/setpiece/DensityArt`)
was rebuilt in November 2026: Infiltrator, Density and Escape Artist, eighteen interaction nodes, ten poses and fifteen
pieces. What the player sees is in [docs/density.md](../../../docs/density.md).

- **The core asks the tree where the moves meet.** `DensityService.pass` calls `DensitySkills.passed` (Ambush primes the
  hand, Ghost Network opens the wall), Reach Through calls `touched` (the seal, and Inside Knowledge) and
  `throughWall`, every shockwave asks `crater` before it throws anyone, and the surfacing uppercut calls `uprooted`.
  The core keeps its own logic; the passives live in the tree's class.
- **Follow-ups on a core key.** Ambush rides Reach Through's key and Entombed rides Embed's, as `.followUp(...)` on the
  core `SimpleAction`. Each returns false when it doesn't apply (no primed hand, no held body in sight), so the normal
  press still runs and pays its cost. A follow-up that succeeds is free and ignores the cooldown, which is the point.
- **"Your blow" means your fist.** `indirectMagic(p, p)` has the player as its *direct* entity too, so a check of
  `getDirectEntity() == p` alone counts Reach Through and the ground's grip on an Entombed body as punches. Set in
  Stone once extended a burial forever from its own damage ticks. Melee-only passives check
  `source.is(DamageTypes.PLAYER_ATTACK)` as well.
- **Re-embedding deeper.** `DensityService.entomb` takes the existing hold out of the map, stands the body back on the
  ground, embeds it again at full depth, and puts the old hold back if there is no room. Embedded bodies also ignore
  `IN_WALL` damage, so a burial's damage is exactly the skill's.
- **A passage friends can use.** A Ghost Network door is two mouths and a normal, re-sent as a 12-tick piece every ten
  ticks. A player goes through when facing into the wall; a mob when its owner (or the caster) is on the far side.
  Each traveller then waits two seconds, or they bounce straight back. Use this for any portal that follows its maker.
- **A primed state shown only to its owner.** The Ambush hand is a 60-tick private piece (`DensityGeometry.showTo`)
  bound to the player; the strike piece, bound to the same player, ends it at once through `superseded`.
- **Draw at the ground, not the body.** Once a body is embedded, `position()` is under the floor, and a collar or slab
  drawn there is buried. Ask `DensityService.surfaceOf(entity)` for the surface it is held in.
- **Separate aiming from the effect.** `sinkhole` picks the ground and `sinkholeAt` opens the hole, so a game test opens
  one at a known point without pointing a FakePlayer's head at the floor.
- **Stone in a set piece.** `DensityArt.slab` draws `c.box` only in the SOLID pass and shrinks it instead of fading
  (solids have no alpha). Harden's plates hide in your own first-person view, where they would sit inside the camera.
- **Photographs:** `tools/density-skills-preview.init.gradle` (`uitest/DensitySkillsPreview`, flag
  `heroes.preview.densityskills`) casts every skill into `build/density-skills-preview/screenshots/`.
