# Hydrokinesis: notes for agents

The reservoir, five core moves, waterbending, the tree and three classes (`hydro/`, `HydroArt`). What the player sees: [hydrokinesis.md](../../hydrokinesis.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from Hydrokinesis (water)

Hydrokinesis (`hydro/`, art in `client/effects/setpiece/HydroArt`) was added in November 2026: the reservoir, five core
moves, waterbending, an eighteen-node tree and three classes. What the player sees is in
[docs/hydrokinesis.md](../../../docs/hydrokinesis.md).

- **A resource you can see on the body.** The reservoir has no HUD bar: `HydroStatePayload` (water, capacity and state
  flags, sent to the bender and everyone tracking them, on change and on `StartTracking`) feeds `ClientHydroState`, and
  `HydroArt.drawLive` draws the water flowing round the waist, longer and thicker the fuller. Ease the drawn value toward
  the server's each client tick so it never jumps.
- **Where an element can't live.** `ability/service/Elements` is the one rule: Hydrokinesis is snuffed in an ultra-warm
  dimension (the Nether), Pyrokinesis under rain (`doused`: rain at the feet or head). `AbilityUseService` refuses a
  snuffed press before follow-ups, cost and cooldown (an Echo counts as the borrowed power), `FightingStyles` turns a
  strike into a plain punch and refuses dodges, `Takedowns` refuses and interrupts finishers, and the powers' own held
  gates (`HydroService.canBend`, `PyroService.canSustainFire`, the Phoenix and Flame Flight) end what is running.
  `Elements.fizzle` is the shared puff and hiss; `Elements.check` (every five ticks) empties water or Heat with a big
  fizzle as the element goes out. A new environmental weakness (cold for fire, a vacuum for sound) is one more case.
- **Never let a resource lock the kit.** `HydroService.draw` spends water if there is enough and otherwise pulls it
  "thin from the air": the move still works at 70% and costs a little stamina, and its piece gets `HydroGeometry.THIN`
  added to the scale so the art draws it paler and thinner (the same scale-flag trick as Pyrokinesis's `BLUE_SCALE`).
- **A status other powers read.** Soaked is a real `MobEffect` (`hydro/Soaked`, `ModItems.SOAKED`) so it syncs and shows an
  icon. Lightning hits it harder, fire turns to steam on it, Ice Daggers freeze it. Make cross-power hooks statuses, not
  private maps.
- **A travelling shape both sides follow over the terrain.** A wave is one packet: the server and `HydroArt.wave` both
  ask `HydroGeometry.waveFront` how far it has rolled and `HydroGeometry.waveFloor` (a block-by-block ground walk over a
  `BlockGetter`, so the client can run it on its own level) how high the ground is there. The drawn wave climbs the same
  steps the real one does.
- **Held moves tick on the server tick, not the player tick.** Octopus Form, Wave Rider and Maelstrom keep a reference to
  their `ServerPlayer` and tick from `HydroService.tick`, so they run the same for game-test players, which the level
  never ticks.
- **The shared tornado, again.** Maelstrom is the Superspeed/Tempest tornado with a new style (`SpeedCyclonePayload.HYDRO`):
  `SpeedArt.tornado` swaps the funnel to water and spray, drops the debris and the lightning, and calls
  `HydroArt.waterspout` for the whirlpool, foam bands and spray crown. The physics is `TornadoWind.blowTempest` with
  suffocation on, so a waterspout drowns. A whirlpool, sandstorm or gravity well is another style, not a second tornado.
- **Water is drawn as water.** `HydroArt.tube` and `sheet` draw lit, translucent surfaces in the SOFT pass (sea-blue
  bodies, teal where thin, more opaque at the silhouette where you look through more water), with white `foam` lips and
  falling `spray`; only thin glints go in the GLOW pass. Additive water reads as light, not water.
- **A surfer stands sideways.** `WATER_SURF` turns the model 58° in `FightingStylePoseRenderer` and rolls it against the
  turn rate (`yRot - yRotO`), while the arms hold a balance pose. The camera stays forward.
- **Continuous poses must not have a recovery.** A re-sent pose keeps its age (`ClientArmPoseState`'s continuous list), so a
  pose whose arms lower after a few ticks (`WATER_RAISE`) will droop halfway through a long hold. Held moves use poses that
  hold (`WATER_CALL`, `WATER_OCTOPUS`, `WATER_SURF`, `WATER_PUSH`).
- **Photographs:** `tools/hydro-preview.init.gradle` (`uitest/HydroPreview`, flag `heroes.preview.hydro`) shoots every
  move, the style, the tree and the classes into `build/hydro-preview/screenshots/`; `tools/lightning-preview.init.gradle`
  (the same driver, flag `heroes.preview.lightning`) shoots Electrokinesis's style into `build/lightning-preview/`.
  - A fixed side camera (`TempestCamera.fix`) must be queued behind the scene's reset on the server thread
    (`HydroPreview.side`): the reset clears the camera, and with any server lag a clear sent in the same tick, or a few
    ticks later, lands after the fix.
  - Don't stand an actor straight ahead of the player for a front shot: the front camera ends up inside it.
- **Show nothing for someone who no longer has the power.** `HydroService.sync` sends no water and no flags unless the
  player is a hydrokinetic (or borrowing it, `MimicryService.isBorrowingHydrokinesis`), so a respec or a revoked power
  doesn't leave water drawn round them.
