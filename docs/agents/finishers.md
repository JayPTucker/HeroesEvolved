# Finishers: notes for agents

The takedown engine (`combat/Takedowns`) and three finishers per power. What the player sees: [finishers.md](../finishers.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Reusable patterns from finishers

Finishers were added in November 2026. They are Destiny-style: a foe near death glistens gold, and C (Shift + C
switches them off) ends it with a short move that belongs to the power. The shared engine is `combat/Takedowns`, and
every power has one finisher, a class in its own package (`pyro/DragonCoil`, `time/ClosedLoop`,
`telepathy/LightsOut`, `electro/ShortCircuit` and the rest; the full list is in docs/finishers.md). The design for
every power (Sets A and B) and what the player sees are in [docs/finishers.md](../../docs/finishers.md) and each power's
doc.

- **A finisher must work alone.** Telepathy's first finisher, Last Order, turned the foe on another hostile. With
  nobody to turn it on, it only knelt, so it was dropped for Lights Out. Don't build a finisher whose payoff needs
  something the player can't count on (another mob, a construct, a resource at full); make that a bonus on top of a
  move that always works.

- **The name.**
  - The fighting styles already call their third combo strike a "finisher", so the code says *takedown*
    (`Takedowns`, `Takedowns.Finisher`, `Takedowns.Run`).
  - The key, the lang lines and the docs say **Finisher**.
- **A new finisher is one object.**
  - Implement `Takedowns.Finisher`: `power`, `id`, `ticks`, `landAt` and `reach`; `holds` (pin the foe, or move it
    yourself); `start`, `tick`, `land`, `reward` and `end`.
  - Add it to `Takedowns.FINISHERS`, its power to `FINISHER_POWERS` in `tools/balance.py`, and a
    `finisher.heroesevolved.<id>` lang line.
  - The engine already does the rest: the threshold, the request's checks and cost, the hold, half damage and the
    break, the refund, the shared cooldown, the camera, and cleanup on death, logout and dimension change.
- **Who can be finished is answered twice.**
  - `Takedowns.looksFinishable(viewer, target, pvp)` uses only what every client knows: health, max health, the
    `heroesevolved:unfinishable` tag, `Enemy`, and vanilla's synced `isAggressive`. The client's glisten and the
    server's rule therefore agree.
  - `finishable` adds the server's friend rule (`StyleHits.hostile`) and "hunting this player".
  - Keep any new rule in `looksFinishable` when the client can see it.
- **The killing blow goes through the power's own damage.**
  - `Takedowns.strike(run, hit)` calls the power's helper (`PyroService.burn`, `StyleHits.hurt` with a playerAttack or
    indirectMagic source), so statuses and cross-power rules still apply.
  - If the foe lives through it (fire immunity, armor), a plain blow finishes the job.
  - A foe held in someone else's stopped time is released first. A blow stored in your own stopped time still
    counts as a finish.
  - `spill(run, hit)` spreads the overkill; `others(run, radius, n)` finds the nearest other foes.
- **The foe's mind is switched off, safely.**
  - The engine sets `NoAI` on a held mob and marks it with `heroesevolved_finisher_held`, and gives the AI back
    before the blow lands.
  - `EntityJoinLevelEvent` clears a marked mob that was saved mid-move, so a crash can't leave a frozen zombie.
  - No-AI mobs ignore velocity. A finisher that moves the foe (set `holds` false) must step it with `teleportTo`:
    up a one-block step, down off a ledge half a block a tick, never into a wall. Last Order's walk did this; it is in
    git history if a later finisher needs it.
- **Root the player on the client's input, never with a speed attribute.**
  - The first version used a −100% `MOVEMENT_SPEED` modifier. Vanilla zooms the field of view in as walk speed
    drops, so the camera seemed to rush into the fire.
  - `FinisherClient.hold` now zeroes the movement input (`MovementInputUpdateEvent`, as Glacier Ride does) while
    your finisher plays. It gives up 10 ticks after the move should have ended, even if the end packet is lost.
  - A finisher can still move the body itself (Dragon Coil's hop, Closed Loop's blink).
- **Two entities in one set piece.** A piece can be bound to only one entity. Lights Out binds its piece to the foe
  and passes the telepath's entity id as the piece's seed (`Takedowns.show(..., seed)`), so `TelepathyArt` draws the
  thread between both live bodies.
- **Hushing a crowd.** `LightsOut.hush` clears a mob's target, its brain's `ATTACK_TARGET` (checked with
  `hasMemoryValue` first) and its path. Use it for any "they forget you for a moment" effect.
- **Helpers every finisher uses.**
  - `Takedowns.sound(run, at, sound, volume, pitch)` plays a sound.
  - `Takedowns.move(body, at)` moves the player or the foe at once; it calls `absMoveTo` too, so test players move.
  - `Takedowns.later(server, ticks, job)` runs a job after the move has ended: Hard Reset puts its switches back
    with it, and Short Circuit's trap checks each tick. Jobs still waiting run at once when the server stops, so
    nothing is left flipped.
  - A finisher that moves a body passes each step through `SpeedService.swept`, which returns a position.
- **Shared timings in one class.** Monument, Off Frequency, Hall of Mirrors and Unmade keep their beats (and Hall of
  Mirrors' pane layout, `pane(foe, from, i, n)`) in `combat/FinisherBeats`, because both the server and
  `BodyCopies` read them.
- **Drawing a body differently: `BodyCopies`.**
  - It works off the set pieces bound to a body. It can draw that body in stone (Monument), as tinted copies at
    offsets turned to face a point (Off Frequency, Hall of Mirrors' reflections of the player), or hide it (Unmade,
    under its plates).
  - It re-renders the real model inside `RenderLivingEvent.Pre` (a `drawing` flag stops it recursing), through a
    vertex wrapper that multiplies colours. Stone swaps the texture for `textures/block/stone.png` and drops the hurt
    overlay, or the statue flashes plain red when hit.
  - A copy moves and breathes with the real body. Add a case to `BodyCopies.plan` for the next piece that needs one.
- **One-offs in a piece's scale.** A finisher's own extra pieces mark themselves with a small scale while the main
  piece's scale is the foe's height, at least 0.6. Examples: Short Circuit's standing trap (0.3) and its burst (0.1),
  Chain Reaction's spare motes and Off Frequency's filaments (0.1), Hard Reset's node-to-node sparks (0.2).
- **Every `finisher_*` balance entry needs a built finisher.** `PowerBalanceGameTests` checks this, because the
  table's other entries must be real actions.
- **A double of the player is a story actor.** Closed Loop spawns `StoryActorEntity` as `Role.TRAVELER` with
  `doubleOf(uuid, name)`. It never saves, and it gained a `WATCH` pose. Use it for any "another you" moment.
- **Flinches before the blow.** `Takedowns.flinch` sends `ClientboundHurtAnimationPacket` through
  `getChunkSource().broadcastAndSend`, with a hit sound. Each kick or cut reads as landing without dealing damage
  that could end the move early.
- **The camera.**
  - `FinisherClient` frames a still side view at the start of your own finisher. It tries both sides and keeps the
    one with more room, raycast in front of walls.
  - `TempestCamera.shot` asks it after a preview's `fix` and before the tornado.
  - `ClientVisualSettings.finisherCamera` turns it off, and `finisherGlisten` turns off the outline and glints (the
    prompt stays).
- **A coloured outline for one viewer.** `FinisherGlowMixin` answers `Minecraft.shouldEntityAppearGlowing`, and
  `FinisherOutlineMixin` answers `Entity.getTeamColor` with pale gold, on the client only.
- **Tests.**
  - `TakedownGameTests` uses generic damage to test half damage and breaks, because a mob's own attack is scaled
    by the test server's difficulty.
  - `PlayerProgressionService.getLevel` is the power's **tier** (1–5), not its Power Level. The unlock check uses
    `getPowerLevel`, and the tests use `setPowerLevel`.
- **Three per power, picked at random.** Each power has its signature and two alternates (`pyro/PyroFinishers` and
  the like, one class per power), all in `Takedowns.FINISHERS`, signature first.
  - `Takedowns.choose` picks among `variants(power)` that are `available` and reach the foe, never the one in `LAST`.
    `of(player)` is still the signature (the HUD's power and the tests read it).
  - An alternate gives its signature's reward by calling it (`DragonCoil.FINISHER.reward(run)`), so a power's payoff
    stays one thing. They share the power's balance entry.
  - `Finisher.resolve(player)` lets one stand in for another as it starts: Borrowed Strength hands over a kept power's
    finisher wrapped by `MimicFinishers.borrowed` (same beats, a gold shimmer, no power's reward).
  - Tests and previews that check one finisher force it: `Takedowns.request(player, id, finisher)`.
- **Keep the player near their spot, and move them smoothly.** Players found finishers that carried them five blocks
  away jarring. Don't move the player more than about two blocks, and end where they began where you can.
  - Move the player along a path with `Takedowns.glide(player, now, next)` every tick: it hands the step to the
    player's own client as a full-precision velocity (`SkyburstMotionPayload`, as Skyburst does), with a gentle pull
    toward the path, swept against walls. `Takedowns.move` teleports, which jumps once a tick; keep it for blinks.
    Call `Takedowns.still(player)` when the path ends, or the client coasts on; the engine calls it for you when a
    gliding run is broken. Hold a flying player up with `Takedowns.hover(player, true/false)`, never a bare
    `setNoGravity`: NoGravity is saved with the player, and hover's mark lets login clear it after a crash.
  - When something should fly, move the foe instead: `Takedowns.lift(run, height)` each tick (with `holds()` false)
    holds it up, lower under a ceiling. A mob with its AI off hangs where it is put.
- **Two bodies in one plan.** `BodyCopies` plans with `FinisherBeats.HIDE_MARK`: a piece bound to the player with
  that scale only hides them, while a second piece bound to the foe draws the foe's model where the player stands (Its
  Own Face). Copies are never drawn round the camera, so a shape worn by the local player doesn't fill a first-person
  view. Other plans added: afterimages (Unwound), greening to ash (Half-Life), sinking through a breach or into a
  crater (Breach Drop, Heavy Hand), and a gold copy facing the foe (Echo, which swings when the foe does).
- **A temporary beast is a `ShapeDecoy`.** Apex Bite sets one up with `setup(form, owner, life, null, false)`, moves
  it with `setPos` each tick and bursts it at the end (`ShapeshiftTricks.burst`), while the player is hidden.
- **A flinch with no sound:** `Takedowns.shudder` (Silence).
- **Photographs:** `tools/finisher-preview.init.gradle` (`uitest/FinisherPreview`, flag `heroes.preview.finisher`,
  `-Ponly=telepathy` for a power or `-Ponly=hearth,phoenix_kick` for finisher ids) shoots the glisten and prompt, then
  each finisher's beats from its own camera and from behind. All sixty run about thirteen minutes.
