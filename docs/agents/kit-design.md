# Designing or reworking a power: notes for agents

Every power's rework solved problems the next one will meet. This page is the way into them: a checklist for wiring a
new power, then tables of "I need to..." with the pattern that already does it and the file that explains it. Read the
linked section before writing anything new; reuse beats a second copy.

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Before you start

- Read the power's player doc (`docs/<power>.md`) and its agent notes (`docs/agents/powers/<power>.md`).
- Read [animation-guide.md](../animation-guide.md) before any effect, pose, set piece or camera.
- **Should it move like Pyrokinesis?** If the power is physical, start from firebending and the shared fighting-style
  engine ([fighting-styles.md](fighting-styles.md)). A telepath or a healer has no business doing backflips.
- Design at least one interaction between the kit's own moves and one with another power. A tree is eighteen
  interactions, not stat bonuses ([powers/radiation.md](powers/radiation.md), "A skill tree of interactions").

## Checklist: wiring a new power

| Step | Where | Notes |
| --- | --- | --- |
| Keep it out of random rolls while it is built | `ModAbilities.IN_DEVELOPMENT` | [powers/shapeshifting.md](powers/shapeshifting.md), "A power still being built" |
| A balance entry for every action | `tools/balance.py`, then `py -3 tools/balance.py` | [AGENTS.md](../../AGENTS.md#stamina-and-balance); `PowerBalanceGameTests` fails without it |
| An awakening and the dangers it answers | intro `Spec` in `PowerIntroService`, `Danger`'s sets, a case in `intro/PerilRelease` | [awakenings.md](awakenings.md) |
| A colour and an icon | `mimicry/MimicColors`, `PowerIcons` | Mimicry's threads and the wheel use the colour |
| Echoes of it work | ownership gates ask `MimicryService.isBorrowing...` | [powers/mimicry.md](powers/mimicry.md) |
| The tree and three classes | `skill_trees.json`, `SkillCatalog.Node.action`, `StyleCasts`, `SkillTreeService.bonus`'s no-stat list, `EvolutionTrials` | [powers/shapeshifting.md](powers/shapeshifting.md), "Wiring a new power's tree and classes"; [powers/radiation.md](powers/radiation.md) |
| Names that match | `en_us.json`; `python tools/verify-skill-content.py` | keep node and action IDs when reworking ([powers/radiation.md](powers/radiation.md)) |
| Three finishers | `Takedowns.FINISHERS`, `FINISHER_POWERS` in `tools/balance.py`, a lang line each | [finishers.md](finishers.md) |
| Sounds | the tables at the top of `sounds/PowerSounds` | [audio.md](audio.md) |
| A fighting style, if physical | `FightingStyles.STYLES`, `FightingStyleClient.STYLED` | [fighting-styles.md](fighting-styles.md) |
| Its own art class and palette | `client/effects/setpiece/<Power>Art` | "Drawing it" below |
| Leaving development | remove from `IN_DEVELOPMENT`; add to `SkillTreeGameTests.POWERS` and `verify-skill-content.py`'s expected set | [powers/shapeshifting.md](powers/shapeshifting.md), "Leaving the in-development list" |
| Docs and photographs | `docs/<power>.md` (what the player sees), `docs/agents/powers/<power>.md`, a row in AGENTS.md, a preview script | [previews.md](previews.md) |

## Moves and keys

| I need... | Pattern | Read |
| --- | --- | --- |
| A second press that is free and ignores the cooldown | `SimpleAction.builder(...).followUp(...)` | [powers/radiation.md](powers/radiation.md) (Fission Leap), [powers/quake.md](powers/quake.md) |
| Two different follow-ups on one key | the follow-up reads `c.modifierHeld()` | [powers/sonic.md](powers/sonic.md) |
| Tap for one thing, hold for another | record the press, let the server tick decide | [powers/regeneration.md](powers/regeneration.md) (Blood Gift), [powers/vibe.md](powers/vibe.md) (Breach) |
| A move held for as long as it is paid for | `StaminaDrain.start`, `drain` | [AGENTS.md](../../AGENTS.md#stamina-and-balance), [powers/vibe.md](powers/vibe.md) (held Vibe Blast) |
| A cost that depends on distance | `AbilityAction.travelCost` | [AGENTS.md](../../AGENTS.md#stamina-and-balance) |
| A big ultimate others can stop | a long, visible charge that damage breaks | [powers/radiation.md](powers/radiation.md) (Critical Mass) |
| A channel that collapses if released early | re-aimed each tick, refund on early release | [powers/vibe.md](powers/vibe.md) |
| A follow-up that spends what the first part banked | a running total on the move | [powers/regeneration.md](powers/regeneration.md) (Blood Bond) |
| A core move made free for a while | override `energyCost`, `cooldownTicks` and `cooldownId` | [powers/time.md](powers/time.md) (Still Point) |
| A wheel of choices | a small screen and one payload each way | [powers/mimicry.md](powers/mimicry.md), [powers/vibe.md](powers/vibe.md) (frequencies) |
| Saved places that grow with level | slots in persisted data | [powers/vibe.md](powers/vibe.md) |
| A key that means two things by context | try the special case first | [power-interactions.md](power-interactions.md) |
| Many moves on one key | the move comes from state; each keeps its own cost and cooldown | [powers/shapeshifting.md](powers/shapeshifting.md) (Instinct) |
| A power to act through a player for a while | `intro/Surge` | [awakenings.md](awakenings.md) |

## Rules, statuses and other powers

| I need... | Pattern | Read |
| --- | --- | --- |
| One friend/foe rule for a whole power | `isFriend` / `hostile` | [powers/flight.md](powers/flight.md); "hostile is not not-a-friend" in [powers/regeneration.md](powers/regeneration.md) |
| One place every hit of the power goes through | a single damage helper | [powers/sonic.md](powers/sonic.md) (`SonicService.hit`) |
| A rule between two powers | one line where the damage is dealt | [fighting-styles.md](fighting-styles.md) (lightning style, shared styles) |
| A status other powers can read | a real `MobEffect` | [powers/hydrokinesis.md](powers/hydrokinesis.md) (Soaked), [weapons-and-dampener.md](weapons-and-dampener.md) (Dampened) |
| A resource the player can see on the body | a small state payload and live drawing | [powers/hydrokinesis.md](powers/hydrokinesis.md) |
| A resource that never locks the kit | a weaker move instead of a refusal | [powers/hydrokinesis.md](powers/hydrokinesis.md) ("thin from the air") |
| A place an element can't work | `ability/service/Elements` | [powers/hydrokinesis.md](powers/hydrokinesis.md) |
| One power acting on another | a small API on the other power | [power-interactions.md](power-interactions.md) |
| A defence another power provides | a map of power to shield | [power-interactions.md](power-interactions.md) (`NuclearShields`) |
| What a power does to a boss's lock (a ward, a heart, a curse, a blast) | one table of keys, `combat/BossLocks` | the bosses skill's [built-bosses.md](../../.claude/skills/minecraft-bosses/references/built-bosses.md) |
| To stop a gun firing for a while | a real `MobEffect`, TaCZ events cancelled on both sides | [powers/electrokinesis.md](powers/electrokinesis.md) (`Jamming`) |
| Take a power out of the mod | one successor table every loader asks | [powers/electrokinesis.md](powers/electrokinesis.md) (`RetiredPowers`) |
| To switch another power off for a while | a refusal in `AbilityUseService` | [powers/vibe.md](powers/vibe.md) (desync), [weapons-and-dampener.md](weapons-and-dampener.md) (`Dampening.dampened`) |
| Every kind of stop to store blows the same way | `SpaceTimeService.holder` | [powers/time.md](powers/time.md) |
| A cost that can kill, and a special death | stamina, then health; a custom damage type | [power-interactions.md](power-interactions.md), [hazards-and-suits.md](hazards-and-suits.md) |
| Respect cover | raycast from the effect | [powers/flight.md](powers/flight.md) (`TornadoWind.exposed`) |
| Deflect or catch shots | one entry point for every case | [powers/quake.md](powers/quake.md) (`QuakeKinetics.disrupt`), [powers/invisibility.md](powers/invisibility.md), [powers/superspeed.md](powers/superspeed.md) (Wind Arms), [powers/electrokinesis.md](powers/electrokinesis.md) (`ElectroMachines.screenCatch`) |
| Catch bullets that cross a block a tick | sample the step, three hooks | [powers/quake.md](powers/quake.md), [powers/vibe.md](powers/vibe.md) |
| Temporary blocks that survive a crash | `cryo/TemporaryBlocks` | [powers/invisibility.md](powers/invisibility.md), [powers/cryokinesis.md](powers/cryokinesis.md) |
| To hold a creature | re-anchor it every tick | [powers/invisibility.md](powers/invisibility.md) (bubble), [powers/density.md](powers/density.md) |
| A history of where things were | a bounded deque per creature | [powers/time.md](powers/time.md), [powers/telepathy.md](powers/telepathy.md) (`MindMemory`), [powers/vibe.md](powers/vibe.md) (`VibeSight`) |
| A change to the player's size or body | `MorphBodies`, `Attributes.SCALE` | [powers/shapeshifting.md](powers/shapeshifting.md) |
| Voice chat or chat as someone | `MindVoice`, `ServerChatEvent` | [powers/telepathy.md](powers/telepathy.md), [powers/shapeshifting.md](powers/shapeshifting.md) |
| An out-of-body view | a spectator on a leash, with a double | [powers/vibe.md](powers/vibe.md) (visions) |
| State that must not outlive the power | clear it after the change, not before | [awakenings.md](awakenings.md) (`HydroService.powerChanged`) |
| A static list that holds a `ServerLevel` | clear it on `ServerStoppingEvent`, not only on logout. A singleplayer player comes back with the same id, and the next world's server thread would tick the entry and wait forever on the closed world's chunks | `QuakeEvents`, `TemporalSnapshotEvents` |
| A finisher | `Takedowns.Finisher` | [finishers.md](finishers.md) |
| One power to change every other power's numbers | each power asks `Amplification.surged` where it decides its own number; shared bonuses in shared paths (`AbilityRanks`, `StaminaDrain`) | [powers/amplification.md](powers/amplification.md) |
| A status every client can draw on someone else | its own payload, sent on change and on `StartTracking` (vanilla syncs only your own effects) | [powers/amplification.md](powers/amplification.md) (`SuperchargedPayload`) |

## Moving bodies

| I need... | Pattern | Read |
| --- | --- | --- |
| A dash over 3.9 blocks a tick | `SkyburstMotionPayload` each tick | [powers/flight.md](powers/flight.md) |
| Any fast move that can't pass through walls | `SpeedService.swept` (returns a position) | [powers/flight.md](powers/flight.md) |
| A short hop | `setDeltaMovement` plus the vanilla motion packet | [powers/radiation.md](powers/radiation.md) |
| A landing that always comes, and no fall damage after | a timeout, a ten-tick cushion | [powers/radiation.md](powers/radiation.md) |
| A ground dash, a flip, a skid | `StyleMotion.dash`, `impulse`, `skid` | [fighting-styles.md](fighting-styles.md) |
| To nudge a player without killing their momentum | do it on their client | [powers/flight.md](powers/flight.md), [powers/superspeed.md](powers/superspeed.md) (run stop) |
| A fast orbit | an ideal point runs round the circle | [powers/flight.md](powers/flight.md) (Tempest) |
| To turn a player without moving them | `connection.teleport` with absolute coordinates | [awakenings.md](awakenings.md) |
| A smooth scripted path | `Takedowns.glide` | [finishers.md](finishers.md) |
| A route through caves | bounded breadth-first search | [awakenings.md](awakenings.md) |
| Carrying | `CarryAnchorEntity` | [powers/flight.md](powers/flight.md) |
| A chain of teleports behind targets | a ticked sequence | [powers/time.md](powers/time.md) (Moment's Edge) |
| A vortex, whirlpool or storm | a new style of the shared tornado | [powers/flight.md](powers/flight.md), [powers/hydrokinesis.md](powers/hydrokinesis.md) (Maelstrom) |

## Reusable patterns from stamina bookkeeping

- **A never-started pause still goes into a packet.** `PlayerEnergyService.State` once used `Long.MIN_VALUE` for
  `pausedUntil`: subtracting the current time overflowed, sending a 2,147,483,647-tick recovery countdown on a fresh
  sync (and 1,000 on a refusal). Use a safe negative timestamp for both it and `sentAt`.
- **Spending up to a cost includes the final fraction.** `PlayerEnergyService.consumeUpTo` once floored stamina before
  paying, so overexertion kept 0.75 stamina and never paused recovery. Subtract the exact payment and return whole
  points only for callers that use that count. `energy/StaminaAccountingGameTests` checks both packets and payments.
- **Anything that runs while its key is held pays while it runs.** Every action that overrides `release` (or a
  `SimpleAction` built with `.release`) and keeps going between press and release needs a per-second cost: a
  `StaminaDrain.start` with `PowerBalance.drain(id, fallback)`, stopped on every path that ends it (release, Shift-stop,
  `stopAll`/`forget`, `clear`, and the drain's own `onEmpty`), or a `PlayerEnergyService.drain` on its pulse. Cyclone once
  skipped its first three seconds and its anchored storm, Wind Arms, Tempest and Maelstrom paid only to start, and the
  Meltdown Beam (`RadiationService.startEmission`) and Read Mind (`TelepathyFocus.start`) were free for as long as they
  were held. Put the rate in `tools/balance.py` as `drain=`, and test a paid second plus an empty bar ending it
  (`energy/HeldWindGameTests`, `meltdownBeamPaysStaminaEverySecondItIsHeld`). Hold-to-charge moves that fire once on
  release (spears, Inferno, Crescendo, Rock Strike) are one cast and don't drain.
- **Damage answers to its stamina.** `tools/balance.py` keeps a `DAMAGE_AUDIT` entry for every tree active, signature,
  class ability and fighting-style move, measured from the Java (the full sequence on one target at the unlock tier,
  pulses and hops counted, invulnerability resets checked), and fails if one is missing or more than 30% off what its
  cost buys. A new or reworked move adds its `reviewed(...)` line in the same change; fix a miss by changing the
  damage in code, not by inventing a utility reserve.

## Drawing it

Read [animation-guide.md](../animation-guide.md) first; these are the code patterns behind it.

| I need... | Pattern | Read |
| --- | --- | --- |
| A construct the player watches assemble | blueprint, fragments from the hand, snap, sheen; the server builds on the drawing's schedule (`Job.paced`, `AssemblyGeometry.panelArrival`) | [powers/matter-assembly.md](powers/matter-assembly.md) |
| A spendable resource orbiting the body that flies home when returned | a pool + state payload, eased client mirror, `drawLive` orbit, a return piece | [powers/matter-assembly.md](powers/matter-assembly.md) (`Matter`) |
| A projectile that reads at speed | one short piece per server step (crystal crosses the step in a tick, tail fades), a flash piece per ricochet | [powers/matter-assembly.md](powers/matter-assembly.md) (`MATTER_SHARD`) |
| A coherent look for a whole kit | one art class, a few primitives, one palette | [powers/flight.md](powers/flight.md) (`FlightArt`), [powers/electrokinesis.md](powers/electrokinesis.md), [powers/sonic.md](powers/sonic.md), [powers/invisibility.md](powers/invisibility.md) |
| A variant without a new piece | a flag added to the piece's `scale` (clamped 0.05 to 64 on the client) | [powers/pyrokinesis.md](powers/pyrokinesis.md) (`BLUE_SCALE`), [powers/mimicry.md](powers/mimicry.md), [powers/shapeshifting.md](powers/shapeshifting.md) |
| A held effect | short pieces re-sent with fades | [powers/radiation.md](powers/radiation.md), [powers/electrokinesis.md](powers/electrokinesis.md) |
| A held piece that stops the moment it ends | `SetPieceClient.superseded` | [powers/invisibility.md](powers/invisibility.md) |
| An effect that lasts as long as a state | live drawing, no packet per frame | [powers/flight.md](powers/flight.md), [powers/pyrokinesis.md](powers/pyrokinesis.md) |
| Effects from the visible hand | geometry shared by server and art | [powers/pyrokinesis.md](powers/pyrokinesis.md) (`PyroGeometry`), [powers/electrokinesis.md](powers/electrokinesis.md) |
| First person that doesn't fill the screen | gestures at the view's edge; fade near the camera | [powers/superspeed.md](powers/superspeed.md), [powers/mimicry.md](powers/mimicry.md), [powers/shapeshifting.md](powers/shapeshifting.md) |
| A pose per maneuver | phase flags and `phaseAge` | [powers/flight.md](powers/flight.md) |
| A held pose that doesn't droop | the continuous list in `ClientArmPoseState` | [powers/hydrokinesis.md](powers/hydrokinesis.md), [powers/sonic.md](powers/sonic.md) |
| Skills to keep their own pose | return before the generic skill pose | [powers/electrokinesis.md](powers/electrokinesis.md) |
| The whole body to turn | `FightingStylePoseRenderer` | [fighting-styles.md](fighting-styles.md) |
| Water, blood or colour that reads at noon | the coloured body in SOFT, rims in GLOW | [powers/hydrokinesis.md](powers/hydrokinesis.md), [powers/regeneration.md](powers/regeneration.md) |
| An aura that follows the body | a render layer that draws the model again a size larger (`client/SuperchargeField`) | [powers/amplification.md](powers/amplification.md) |
| Afterimages, ghosts and copies of bodies | `SpeedAfterimages`, `ElectricalAfterimages`, `VibeEchoes`, `BodyCopies` | [powers/superspeed.md](powers/superspeed.md), [fighting-styles.md](fighting-styles.md), [powers/vibe.md](powers/vibe.md), [finishers.md](finishers.md) |
| A player drawn as something else | a client-only stand-in | [powers/shapeshifting.md](powers/shapeshifting.md) |
| A changed skin | repaint a copy of the skin | [hazards-and-suits.md](hazards-and-suits.md) (`BurnClient`) |
| A screen effect | a post chain | [powers/vibe.md](powers/vibe.md) |
| A camera that watches a big effect | `TempestCamera`; never `setCameraEntity` | [powers/flight.md](powers/flight.md) |
| Something seen from very far off | live drawing, scaled near the camera | [village-events.md](village-events.md) (flares), [story-and-scenes.md](story-and-scenes.md) (the sky fight) |
| A fast mover that doesn't vanish | widen culling along its travel | [powers/flight.md](powers/flight.md) |
| A readout on the HUD | a ring gauge in `HudGauges` | [hazards-and-suits.md](hazards-and-suits.md) |
| Quieter effects for players who ask | `minimal()` / `detail()` | [powers/flight.md](powers/flight.md) |

## Sound and feel

| I need... | Pattern | Read |
| --- | --- | --- |
| A sound for every action | `PowerSounds` tables, one hook | [audio.md](audio.md) |
| A sound heard far away | a fixed-range event matched in `sounds.json` | [powers/flight.md](powers/flight.md) |
| A tremor | `CameraShakePayload.sendAround` | [powers/flight.md](powers/flight.md) |
| A cue only the user hears | a sound packet to one player | [audio.md](audio.md) |

- **An action with its own recovery exposes it through `AbilityAction.recovery`.** Generic cooldown snapshots
  alone hid Morph's exposure lock and Instinct's per-form waits. `PlayerPowerSyncService` uses the returned end and
  duration for keyed and wheel loadouts; keep the same question when another action owns an internal recovery.
