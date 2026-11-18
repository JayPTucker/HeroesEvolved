# Fighting styles: notes for agents

The shared melee engine (`combat/FightingStyles`, `combat/ComboStyle`, `combat/StyleHits`, `render/FightingStylePoseRenderer`) and the three generations of styles built on it. Pyrokinesis is the bar for movement. What the player sees: [fighting-styles.md](../fighting-styles.md), [pyrokinesis.md](../pyrokinesis.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Reusable patterns from firebending (Pyrokinesis melee)

Pyrokinesis gained a fighting style in November 2026 (`pyro/PyroCombat`, numbers in `pyro/PyroGeometry`, art in
`PyroArt`). Its input, dodges and evasion became the shared fighting-style engine (`combat/FightingStyles`,
`combat/StyleMotion`, `client/FightingStyleClient`, `network/FightingStylePayload`, whole-body turns in
`render/FightingStylePoseRenderer`), which Electrokinesis and Hydrokinesis also use. What the player sees is in
[docs/pyrokinesis.md](../../docs/pyrokinesis.md). Reuse these for any power that should fight hand to hand.

- **A new style is one class.** Implement `FightingStyles.Style` (`owns`, `strike`, `dodge`, `counter`, `tick`,
  `forget`, `clear`), add it to `FightingStyles.STYLES`, and add the power to `FightingStyleClient.STYLED`. Input,
  payload, client settings, evasion, fall cushions and cleanup on death, logout and dimension change come for free.
  Copy `PyroCombat`, `electro/ElectroCombat` or `hydro/HydroCombat` for the shape of one.
- **Moves without keys.** An empty-handed left click (`InputEvent.InteractionKeyMappingTriggered`, cancelled when the
  crosshair isn't on a block) and a double-tapped A, D or S (edges read in `ClientTickEvent`) send
  `FightingStylePayload`. The server routes it through `PossessionService.actionActor` to the actor's style, which picks
  the move, pays for it and times it. Both can be switched off in `ClientVisualSettings` (`fightingStyleStrikes`,
  `fightingStyleDodges`).
- **A click that can't strike still punches.** The payload carries the crosshair's entity; when a style's `strike`
  returns -1 (no stamina, power switched off), `FightingStyles.strikeInput` does a plain `player.attack`. A cancelled
  click must never leave a player swinging at nothing.
- **A combo is three numbers.** The next step, the last strike's time and when the next is allowed (`Fighter`). A click
  within `COMBO_WINDOW` carries on; the client also throttles so mashing doesn't flood the server.
- **Dodges with evasion.** `StyleMotion.evade(player, ticks)` opens an evasion window; `FightingStyles` cancels blows
  with an entity source in `LivingIncomingDamageEvent` (HIGH priority, before the power's immunities) and calls the
  style's `counter(player, source)` for the first one, the cross-move link (Counterflame, Ground Out, Turn the Tide).
  Never evade `BYPASSES_INVULNERABILITY`.
- **Ground dashes are re-sent each tick.** One velocity packet on the ground is eaten by friction within two ticks;
  `StyleMotion.dash` re-sends the push for its push ticks, working out the hop's fall the client's way
  (`(vy - .08) * .98`), and stops on `horizontalCollision`. A flip is an `impulse` (pushed once, then flies). During
  Flame Flight, `PyroFlightService.tick` skips steering while `PyroCombat.isDodging`, or cruise overwrites the dash.
- **Turning the whole body.** `render/FightingStylePoseRenderer` rotates the model in `RenderPlayerEvent.Pre` about the
  hip, in the body's yaw frame (as `CryoRidePoseRenderer` does), keyed off the arm pose and its age: a backflip, a spin, a
  twist into a kick, a surfer's sideways stance. The camera never turns. Legs are posed in `PowerArmPoses` (`firebend`,
  `lightningStyle`, `waterbend`); copy the pants afterward.
- **Keep the owner, not their id.** Anything a style leaves behind (Flashback's static) holds the `ServerPlayer`, not a
  UUID looked up in the player list: game-test players (and other mods' fake players) aren't in the list.
- **A kicked crescent.** `PyroArt.crescent` lays flame tongues along an arc between two angles in any plane, unrolled
  over time, trailing back the way the foot came, with a three-layer lip. The roundhouse (horizontal), flip kick
  (vertical) and sweep kick (a full turn) are one primitive. The server's `PyroGeometry.inCrescent` uses the same reach
  and angle.
- **Strikes never place blocks.** Fire thrown many times a second would grief; only the kit's casts light real fire.

## Reusable patterns from the lightning style (Electrokinesis melee)

Electrokinesis gained a fighting style in November 2026 on the shared engine (`electro/ElectroCombat`, numbers in
`ElectroGeometry`, art in `ElectroArt`). What the player sees is in [docs/electrokinesis.md](../../docs/electrokinesis.md).

- **Give each style its own character, not a recolour.** Firebending kicks; lightning boxes. Sparks seek the nearest
  body within `SPARK_SEEK` of the aim (lightning finds a conductor), the finisher is a clap rather than a kick, dodges
  are flashes (pushed hard and nearly level for three ticks) rather than hops, and the back dodge leaves a delayed
  static burst behind instead of kicking. Same engine, same controls, a different fight.
- **A counter that points at the attacker.** `Style.counter` gets the `DamageSource`, so Ground Out remembers who threw
  the evaded blow and the next spark goes through them and chains on (`ElectroCombat.chain`).
- **Electrical facades have volume.** `client/effects/ElectricalAfterimages` draws a full-sized player mesh wrapped
  in the charged creeper's scrolling energy field for Arc Step, Flashback and Short Circuit. The departure position,
  body facing and pose stay frozen while the field crawls over the surface. Use substantial body models for ghosts,
  never a spine-and-limbs line skeleton. Minimal keeps one shell; the normal setting adds a pale outer sheen.
- **Hands per side.** `ElectroGeometry.fist(player, partial, main)` and `ElectroArt.fist(c, owner, main, reach)` place
  the lead or rear fist in third person and in first person, so a jab leaves the hand that threw it.
- **Cross-power rules live where the damage is dealt.** `ElectroService.shock` reads `HydroService.soaked`, and
  `PyroService.burn` turns a soaked target's burn into steam: one line each, at the one place each power hurts things.

## Reusable patterns from the shared fighting styles

Seven more powers gained fighting styles in November 2026 on the same engine: Blur Boxing (`speed/SpeedStyle`), Pressure
Palm (`quake/QuakeStyle`), Ice Skater (`cryo/CryoStyle`), Ghost Fist (`phasing/DensityStyle`, since replaced by Tiger and Crane, `phasing/EarthStyle`), Reckless Brawler
(`regeneration/RegenStyle`), Force Palm (`invisibility/ForceStyle`) and Puppeteer (`telekinesis/KineticStyle`). What the
player sees is in [docs/fighting-styles.md](../../docs/fighting-styles.md).

- **A style is only its moves.** `combat/ComboStyle` holds the combo (lead, rear, a finisher from level 2, crouch for the
  low move), the dodges (back is the special one from level 2), the counter that loads the next strike and marks the
  attacker, costs, cooldowns and the tired message. A subclass writes `throwMove`, `dodgeMove` and `onCounter`, and can
  override `tickFighter` (lingering things), `evadeTicks` (0 for a style that doesn't dodge; Tiger and Crane evades only on its back dodge, underground) and
  `canFight` (Claire can't swing a broken arm). The fighter keeps a one-second `trail` (Rewind Step), a `stack` (a combo that
  grows heavier) and `banked` damage (Payback).
- **One helper class for hitting.** `combat/StyleHits` aims (`aimed`, `firstInTube`, `foes`), hurts (`hurt` resets
  `invulnerableTime` so a three-blow jab lands three times, and guards against re-entry), shoves with knockback
  resistance respected (`shove`, `away`), and places hands and chests (`hand`, `chest`). `blink` teleports with
  `absMoveTo` too, so FakePlayers move in tests.
- **Speak in the power's own strokes.** Most styles need no new art: Blur Boxing uses Superspeed's afterimages and
  lanes, Pressure Palm Quake's rings and fissures. Tiger and Crane draws with `EarthArt`'s chunks and cracks. New pieces live in
  `client/effects/setpiece/StyleArt` and borrow the power's primitives (Force Palm draws with `ForceArt.lens`/`pane`).
  Telekinesis's strokes were too thin to read at fighting range, so the Puppeteer has a visible hand: `STYLE_TK_PALM`
  (a palm print on each swat) and `STYLE_TK_GRASP` (fingers closing over a lifted or gripped body, bound to it, with
  the hold's length in the scale).
- **A brace instead of a dodge.** `StyleMotion.brace(player, ticks)` and the style's `braced(player, source, amount)`
  reduce a blow rather than cancel it; `FightingStyles.incoming` checks the brace before evasion. Reckless Brawler banks
  every braced blow and calls `counter` on the first.
- **Skids, not blinks, for a speedster in a fight.** Players disliked Blur Boxing's dodges and Blitz teleporting
  (striking, then appearing behind the mob). `StyleMotion.skid(player, push, ticks)` throws the real body and eases the
  push off as a cube, so most of the ground goes in the first ticks and the rest is a short slide; `skidReach(ticks)`
  turns a distance into a push (the Blitz stops `BLITZ_STOP` short of the foe). Each skid has its own pose
  (`SPEED_SKID_BACK` kneels side-on, `SPEED_SKID_LEFT`/`RIGHT` lean away from the slide over a braking hand) and ends in
  Superspeed's `SPEED_GUST` piece for the skid marks and dust.
- **A run's stop is a client-owned skid.** `client/SpeedSkid` sees Forward released at speed and keeps an easing slide
  on the runner's own client (a server push would stop and restart them), then sends `SpeedSkidPayload`;
  `SpeedService.skidStop` checks the run and shows everyone `SPEED_SKID_STOP`. A runner at full pace is off the ground
  half the time, so "running" is remembered for a few ticks, and a release in mid-stride waits to land. Photograph it
  with `tools/speedrun-preview.init.gradle -Pstop`.
- **Cross-power rules where the blow lands.** A Pressure Palm on someone frozen solid shatters them
  (`CryoService.shatterHit`); a Frost Jab on someone Soaked freezes them solid. One line each.
- **Mimicry fights too.** `FightingStyles.of(player)` falls back to the style of the power Peter is channelling, and
  `FightingStyles.fights(player, style)` is the ownership check every style uses, so a borrowed style works with no
  per-style code. The client's `FightingStyleClient` does the same with `ClientMimicMemory`.
- **A helper name can shadow the base class.** Claire's health cost was first called `pay(p, 1)`, which resolved to
  `ComboStyle.pay(player, int)` (stamina) and silently cost stamina instead. Name a style's own costs differently.
- **Don't change a set while you walk it.** `PrologueService.holdWaiting` removed players from `WAITING` while
  iterating it and crashed the server when a preview player joined the Last Day; it now walks `List.copyOf(WAITING)`.
- **Photographs:** `tools/fighting-preview.init.gradle` (flag `heroes.preview.fighting`; `-Ponly=telekinesis,quake` to
  shoot some) runs every style through the same scene into `build/fighting-preview/screenshots/`. Add `-Pinput` for the
  input check: it presses the real attack key three times at a pillager and double-taps right and back for all ten
  styles, and prints `INPUT <power> ... PASS/FAIL` and `INPUT_RESULT`. Game tests call `strike`/`dodge` directly, so only
  this catches a broken click or double-tap on the client. Each preview driver
  needs its own flag: `heroes.preview.styles` already belonged to `uitest/StylePreview`, and two drivers on one flag
  started two worlds and photographed the dark. The preview's actors have no AI, so shoves don't move them; check
  knockback in game tests.
- **A brace on a dodge key, and a counter that answers.** Tiger and Crane (`phasing/EarthStyle`) has no evasion on its
  side dodges (`evadeTicks` returns 0 unless special): an Earth Slide is a `StyleMotion.skid` plus a `StyleMotion.brace`,
  and `braced` halves the blow and calls `counter` for the first one (tracked in the fighter's `banked`, reset on each
  slide). Its `onCounter` raises a pillar under the attacker at once (Neutral Jing) before the loaded claw. Use this for
  any style whose defence is standing firm.
