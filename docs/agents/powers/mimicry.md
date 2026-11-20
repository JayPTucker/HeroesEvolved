# Empathic Mimicry: notes for agents

Borrowing other powers, kept sets, Echo keys and the memory wheel (`mimicry/`, `MimicArt`, `MemoryWheelScreen`). Read this before adding any power with an ownership gate: Echoes need `MimicryService.isBorrowing...` for it. What the player sees: [empathic-mimicry.md](../../empathic-mimicry.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Empathic Mimicry rework

Empathic Mimicry (`mimicry/`, art in `client/effects/setpiece/MimicArt`, the wheel in `gui/screen/MemoryWheelScreen`)
became a three-phase kit in November 2026: the power of whoever you are near (Levels 1 to 5), sets kept by touch
(6 to 14), whole kits remembered (15 to 20), five Echo keys and a memory wheel. What the player sees is in
[docs/empathic-mimicry.md](../../../docs/empathic-mimicry.md).

- **Borrow a power by running its real actions.** An Echo (`MimicryService.echo`) wraps the copied power's own
  `AbilityAction` for the same slot and calls it inside `invoke(...)`, which marks the call as borrowed. Powers whose
  native gates check ownership ask `MimicryService.isBorrowingX(player)` (Flight, Pyrokinesis, Invisibility,
  Cryokinesis, Technopathy, Matter Assembly). A new power with an ownership gate needs one of these, or Echoes of it
  fail. Switching sets, logging out or changing dimension runs `forget`, which ends every held borrowed action.
- **One question: who has a power.** `MimicryService.imprintOf(mimic, entity)` answers for players, gifted villagers,
  the meltdown villager and Sylar (whatever stands in his cases). Attunement, touch, Sense Powers and the stamina passive all use it, so a new
  powered NPC is one more branch there.
- **Keep the old API when the model changes.** The memory became near + kept + channel, but `current()`, `powers()`,
  `remember(player, power, tier)` and `recall(player, step)` still work, so a dozen callers and tests didn't change.
  The codec reads the old `powers`/`active` fields and loads them as kept sets.
- **Menus can show a proxy's target.** `PlayerPowerSyncService` asks `MimicryService.shown(player, action)` and uses
  the copied action's name and path, so the wheel, key list and HUD say "Skyburst", not "Echo II". Resync the loadout
  whenever the channel changes (`MimicryService.sync` does).
- **A second wheel is a small screen and one payload each way.** `MimicMemoryPayload` (server to client, on every
  change and at login) feeds `ClientMimicMemory`; `MimicChoicePayload` (client to server) channels or forgets a slot.
  The key handling copies the ability wheel's hold-to-open, tap-to-act pattern (`ClientKeyEvents.handleMemoryKey`).
- **Two numbers in a piece's scale.** An attunement sends `colourIndex + progress` (whole part and fraction); a touch
  adds 32. Each power's colour lives in `mimicry/MimicColors`, shared by the server and the art.
- **Fade a thread per point near the camera.** `MimicArt.thread` dims each point by its distance to the camera
  (`ribbon` with per-point alphas), so a thread ending at your own chest or passing a front-view camera doesn't fill
  the screen, while the rest of it stays bright. Prefer this to dimming the whole stroke.
- **Throttle touches per target.** One swing can land twice on the same person; two quick touches on different
  people should both count (`LAST_TOUCH` stores the target as well as the time).
- **A saved roll on an entity.** Gifted villagers roll once in `EntityJoinLevelEvent` and save `heroesevolved_gift`
  and `heroesevolved_gift_rolled` in the persistent data, so a reload never re-rolls.
