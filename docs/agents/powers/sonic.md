# Sonic Resonance: notes for agents

The kit, follow-ups and the interaction tree (`sonic/`, `SonicArt`, `SonicSkills`). What the player sees: [sonic-resonance.md](../../sonic-resonance.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Sonic Resonance rework

Sonic Resonance (`sonic/`, art in `client/effects/setpiece/SonicArt`) was reworked in November 2026: every move,
skill and style has its own pose and set piece, three moves gained free follow-ups, and the tree became eighteen
interactions (`sonic/SonicSkills`). What the player sees is in [docs/sonic-resonance.md](../../../docs/sonic-resonance.md).

- **One damage helper makes a status cheap.** Every sonic hit goes through `SonicService.hit(owner, target, damage)`.
  Ringing (`SonicService.ring`) is left by four sources (Focused Shriek, Shatter Note, a braced Cry, Deep Echo), and
  `hit` cashes it in for extra damage and Resonance. Route a power's damage through one helper, and a new mark or
  bonus is one change.
- **Moves that pass through your own constructs.** A Cry or Crescendo checks its line against the singer's Note
  (`onLine`: distance from a segment) and Wall (`throughWall`: a ray crossing the wall's plane inside its box). A tap
  through the Note breaks it harmonic for free; the held scream feeds it; Feedback Loop amplifies through the Wall.
  Cheap geometry against your own things gives combos without new state.
- **One key, several follow-ups.** Note (press: break; Shift: carry it once), Wall (press: shove; Shift: drop),
  Vault (press in the air: dive). `SimpleAction.followUp` runs before cooldown and stamina and sees
  `c.modifierHeld()`, so one predicate picks between two follow-ups. A follow-up that returns false falls through
  to the normal Shift-deactivate (the Wall's drop).
- **A moving construct drawn from one packet.** A shoved Wall moves a fixed `SonicGeometry.WALL_PUSH_SPEED` a tick;
  the server and `SonicArt` both call `SonicGeometry.pushed(start, normal, age)`, so a single `SONIC_WALL_PUSH`
  piece draws the whole roll. The standing wall's re-sent piece is bound to the owner, so `superseded` stops it the
  moment the push starts.
- **An opposite is the same shapes inverted.** Silence (Quiet Zone, Silence Dome) reuses `SonicArt.front` in slate
  colours with rings that close inward. Four primitives (`front`, `trace`, `staff`, `glyph`) and one palette
  (`tone(k)` blends rose, amber, mint, azure and violet) draw all twenty-one pieces.
- **Re-sent poses must be continuous.** The scream, Crescendo's gather, the dive, Resonant Guard's hum and the Quiet
  Zone's hush are refreshed while they last, so they are on `ClientArmPoseState`'s continuous list; otherwise each
  refresh restarts the gesture from frame one.
- **Delegate the whole branch.** `SkillActionService` returns `SonicSkills.skill(c, branch)` directly, which also
  skips the generic skill pose (see [electrokinesis.md](electrokinesis.md)).
- **Taking a power off the stat-bonus list breaks tests that used its stat nodes.** A stamina-discount test bought
  `sonic_resonance_0_0`; it now uses Empathic Mimicry (see [quake.md](quake.md)). Search the tests for a power's `_0_0`/`_1_0`/`_2_0` nodes when you add it
  to `SkillTreeService.bonus`'s list.
- **Photographs:** `tools/sonic-kit-preview.init.gradle` (`uitest/SonicKitPreview`) shoots every move, skill and
  style into `build/sonic-kit-preview/screenshots/`.
