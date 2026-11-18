# Quake: notes for agents

Follow-ups, wave deflection and the interaction tree (`quake/`, `QuakeArt`, `QuakeSkills`). What the player sees: [quake.md](../../quake.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Quake rework

Quake (`quake/`, new art in `client/effects/setpiece/QuakeArt`) was reworked in November 2026: free follow-ups on four
moves, blasts that shake bullets to dust and set off explosives, an eighteen-node interaction tree (`quake/QuakeSkills`)
and 23 poses. What the player sees is in [docs/quake.md](../../../docs/quake.md).

- **One place decides what a wave does to things.** `QuakeKinetics.disrupt(owner, entity, push)` answers for every
  blast, the beam and the Vibration Shell: an incoming TaCZ bullet turns to dust, an explosive goes off where it is
  (credited to whoever fired it), a creeper's fuse is lit, any other shot turns back and becomes hers. Give the next
  deflecting power (a shield, a force field) the same single entry point instead of copying the cases.
- **Fast things need a forward-looking check.** Bullets cross several blocks a tick, so `QuakeKinetics.crossing` tests the
  shot's position *and* where it will be next tick against the blast's line, with a wider reach for bullets
  (`QuakeGeometry.BULLET_CATCH`). Held effects (the beam, the shell) screen shots every tick, even when they only hit
  bodies every few.
- **Reading another mod's private state.** TaCZ keeps a round's explosion settings private; `TaczLink.explosive` reads
  them by reflection inside its try/catch and treats any failure as an ordinary round. `PowerWeapons.testBullet` makes
  a bare bullet for tests (`EntityKineticBullet.TYPE`).
- **A follow-up for each signature move.** Shockwave → Double Tap (a time window kept in `DOUBLE_TAPS`), Resonate →
  Shatter (collapse the rest of a running effect into one blow), Fault Line → Upheaval (finish a travelling effect at
  once), Earthquake → an early aftershock (`followUp` that calls the existing `deactivate`). All are
  `followUp`s, so they are free and ignore the cooldown.
- **Follow-ups change what other tests see.** A second Shockwave press is now Double Tap, so a test that expected the
  second press to hit the cooldown now presses three times (`PowerFeedbackGameTests`).
- **Passives that link two moves, by checking the other's state.** Live Fault (a crack under her own resonance collapses
  it), Pressure Skin (a shot is cancelled while braced, blasting or shelled), Read the Pulse (a felt heartbeat is marked
  and the next hit on it, in `LivingIncomingDamageEvent`, lands harder), Kinetic Sink (each shot broken calls
  `QuakeKit.relieve`). Each is a few lines where the two moves already meet.
- **A telegraph a beat before an aimed hit.** Seismic Lance marks the target's feet and strikes `LANCE_DELAY` ticks later,
  queued in `LANCES`. A test that checks damage at once must wait for it (the all-skills test exempts it).
- **Moving stat-bonus tests again.** Quake joined the no-stat-bonus list, so the discount and attack-bonus tests in
  `SkillTreeGameTests` moved to Empathic Mimicry and Electrokinesis. These are the powers that still have the generic
  nodes.
- **Photographs:** `tools/quake-kit-preview.init.gradle` (`uitest/QuakeKitPreview`) shoots every move, follow-up, skill and
  style into `build/quake-kit-preview/screenshots/`.
