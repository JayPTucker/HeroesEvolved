# Vibe: notes for agents

Cisco Ramon's power: blasts, two-way breaches you can see through, visions, holograms and Rift Fall (`vibe/`, `VibeArt`, `BreachViewClient`). What the player sees: [vibe.md](../../vibe.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from Vibe

Vibe (`vibe/`, art in `client/effects/setpiece/VibeArt`) is Cisco Ramon's power, added in November 2026: Vibe Blast,
two-way Breaches, Read the Vibe, Breach Guard → Return Fire and Rift Fall (the slot that was Frequency Lock). What the player sees is in
[docs/vibe.md](../../../docs/vibe.md).

- **One crossing test for every opening.** `VibeGeometry.crossing(from, to, center, normal, width, height)` checks a
  segment against the same ellipse the viewer sees. Breaches use it on each traveler's last two body centres; the guard
  uses it on each shot's step. Reuse it for any doorway, rift or window.
- **A portal keeps your direction of travel.** A traveler comes out on the side of the far mouth they were heading for
  (`Mouth.arrival(side)`), turned by the angle between the two mouths (`Mouth.yaw()`). Sending everyone out of the front
  of the far mouth bounced anyone walking back through it between the two mouths.
- **A link to another dimension.** `VibeBreaches` keeps both mouths' chunks ticking while the link is open, moves
  players with `changeDimension(new DimensionTransition(...))`, and adds them to `CROSSING` while they move, so the
  `PlayerChangedDimensionEvent` handler that tidies up a power (`VibeService.forget`) doesn't close the link they're
  using. A saved destination lives in `Player.PERSISTED_NBT_TAG`, so it survives death.
- **A short combat memory.** `VibeSight.record` keeps every hit (`LivingDamageEvent.Post`) in a bounded deque for 25
  seconds; a read replays the matching ones privately (`VibeGeometry.privatePiece`) and marks the attackers. Any
  "what happened here" power can read the same trace.
- **Catching fast shots needs three hooks.** The guard screens each projectile's step in `EntityTickEvent.Pre`
  (lowest priority), again in `ProjectileImpactEvent` (a shot whose whole step fell between ticks), and TaCZ rounds in
  `TaczLink`'s `EntityHurtByGunEvent.Pre`.
- **Switching another power off for a while.** Rift Fall's landing (and Prism Lock) puts a caught speedster in `VibeService.DESYNCED`, and
  `AbilityUseService` refuses Superspeed while `VibeService.desynced(player)` is true. `desync` is package-visible so
  `VibeKitGameTests` can test the rule directly (the test server never allows PvP).
- **`SpeedService.swept` returns a position, not a displacement.** Subtract the entity's position before measuring how
  far it got. The awakening's rescue once treated the result as a displacement and never moved anyone.
- **A wave through your own portal.** Through the Seam asks `VibeBreaches.seam(owner, from, to)` for the first open
  mouth a wave crosses; it returns the crossing, the matching point on the other mouth and the turned direction
  (`VibeGeometry.rotation`). The first wave stops at the mouth, and a second one leaves the far mouth when the front
  arrives, sharing one strike list. Any projectile that should pass through a portal can use the same lookup.
- **Every passive ties two moves together.** Slingshot (cross a breach → next blast), Return Address (guard → mark),
  Overflow (Return Fire → a small lock queued in `OVERFLOWS`), Residual Sense (damage taken → mark). Marks never shorten
  (`VibeSight.mark(player, target, ticks)` keeps the longer one), so several sources can feed them safely.
- **Waypoints that grow with level.** Breaches go to frequencies (`VibeFrequencies`): a fixed array of slots in the
  player's persisted data (so they survive death), `slots(powerLevel, worlds)` deciding how many are usable (one, then
  one every four Power Levels, two more from World Frequency). An old single-value save (`heroesevolved_vibe_anchor`)
  is read into slot 1 on first use. Copy this for any power with saved places.
- **A wheel pick that pays through the normal press.** The Breach key's `followUp` opens the frequency wheel for free.
  The wheel's click (`FrequencyChoicePayload`) records a pick for this tick and calls
  `AbilityUseService.activateAction` with Breach's built-in action key, so it works after a key rebind. `followUp` sees
  the pick and steps aside, so cooldown, stamina and sounds apply, and `SimpleAction.Builder.travel` adds the distance
  cost from the same pick. The server checks the player's current slot count on each wheel choice.
- **Seeing through a portal.** When the destination chunks are loaded, the server captures a `BreachSnapshot` round each mouth (blocks and
  packed light inside a 30-block sphere, plus that biome's sky, grass, foliage and water colours, run-length packed; a test keeps
  a flat arena's under 64 KB) and sends it in `BreachViewPayload` to players within 112 blocks of the other mouth, and
  again to anyone who arrives later or changes world. `client/effects/BreachViewClient` tessellates it off the render
  thread with the vanilla block and liquid renderers over a `BlockAndTintGetter` backed by the snapshot, into the chunk
  layers, with their relative yaw and pitch baked in (`VibeGeometry.rotation`, reused for actors, travelers and shots).
  Each frame after entities it marks the opening in the stencil buffer,
  pushes its depth to the far plane with `glDepthRange(1, 1)` and fills it with the far sky, draws the far terrain with
  the terrain shaders (`CHUNK_OFFSET` set to the near mouth, fog set to end where the snapshot ends) and writes the
  opening's own depth back.
  - The main target gets its stencil from NeoForge's `RenderTarget.enableStencil()` in a client tick. Fabulous targets
    that copy the main depth buffer use the same depth/stencil format. Far-side translucency draws into the main target.
  - Only the half of the far side ahead of the viewer is drawn, so a wall behind the far mouth never blocks the view;
    blocks in the mouth's plane go in both halves.
  - The liquid renderer writes positions within the block's chunk section and takes no pose; wrap its consumer to add
    the section's corner and the turn (`BreachViewClient.Moved`).
  - A set piece that frames a window must leave its middle clear: `VibeArt` asks `BreachViewClient.showing(origin)` and
    keeps only the dark rim and the lip.
- **Live entities in the window.** `BreachActorsPayload` sends bounded updates using spawn and appearance data from
  the other mouth. `BreachViewActors` draws detached copies with the vanilla renderers after opaque terrain and before
  translucent terrain. Keep the copies outside the client world's entity list and the rendering inside the stencil.
- **A shot through a breach.** `BreachProjectiles` tests the shot's whole tick segment against both mouths and lets
  cover or a target in front of the opening take the normal hit. Map the crossing point and rotate the full velocity
  between the mouths. The shot resumes its own collision logic from the far mouth on the next tick. `TaczLink`
  maps the round's damage falloff origin and carries TaCZ's runtime-only round fields if it changes dimension.
- **Photographs:** `tools/vibe-preview.init.gradle` (`uitest/VibePreview`) shoots every core move, active skill, class
  ability, Through the Seam, Overflow, the awakening, the frequency wheel and the view through a breach into a built
  garden into `build/vibe-preview/screenshots/`. It skips the Last Day and stages every scene on one strip of forced
  overworld chunks; breaches are sealed in the mod's own dimensions, so a preview left in the city casts nothing.
  Run with `-Pbreaches` for the focused Fabulous preview of terrain, entities and tilted/floor openings.
- **Tap or hold on a key with a free follow-up.** Breach's `followUp` only records the press (`VibeBreaches.PRESSES`,
  holding the `ServerPlayer`, since test players aren't in the player list) and returns true, so the press is free. The
  `release` callback turns a quick let-go into the wheel; the server tick turns a press still down after `HOLD_TICKS`
  into the normal paid press (`VibeFrequencies.press`, the same path a wheel pick takes, so stamina, cooldown and the
  distance cost apply). A press while a breach is open closes it and marks itself handled, so its release does nothing.
- **A channelled opening.** A breach begun from the key (`keyed`) takes `VibeGeometry.channelTicks(tier)` to open, its
  near mouth re-aimed from the look every tick (`follow`), and collapses if the key comes up first (`breachRelease`
  gives the stamina back and resets the cooldown). The `VIBE_BREACH_CHANNEL` piece is re-sent every two ticks, bound to
  the viber so it leaves the visible palm, with the progress in its scale and the channel's length in its seed (built
  as a `SetPiecePayload` directly), so the client grows the tear smoothly and the newest copy supersedes the last. A
  mouth that moves needs its chunk tickets remembered (`Gate.tickets`), or close releases the wrong ones. Tests that
  aren't about the channel call `VibeBreaches.hurry` / `shiftHold` to keep the old eight-tick opening.
- **A vision is a spectator on a leash, with a double left behind.** `vibe/VibeVisions` saves the way home in the
  player's persisted data before anything changes, spawns a `StoryActorEntity` double (`Role.TRAVELER`, `doubleOf`),
  sets spectator mode and teleports; each tick it resets the camera (no riding a mob's eyes) and pulls the watcher back
  within `LEASH`. Logout, death, a power change, a gamemode change by an operator and server stop all end it through
  `VibeService.forget`/`clear`; `login` restores anyone whose save survived a crash, and `orphan` refuses a double that
  loads without its vision. `StoryActorEntity.hurt` swallows every blow before `LivingIncomingDamageEvent`, so the
  double's "tear them back" hook lives in `hurt` itself. Use this for any out-of-body view (astral projection, a
  remote camera).
- **Who triggers a vision.** An ally (`Danger.ally`) more than `ALLY_NEAREST` (40) blocks off and under
  `DANGER_HEALTH` (three hearts) always does; village trouble rolls `CHANCE`. The wait between visions is saved in the
  player's persisted data (`VibeVisions.next`/`rest`), so relogging can't reset it. `trigger` skips the wait and the
  roll, for tests and previews.
- **Things that remember who placed them.** Vanilla doesn't record a block's placer, so `BlockEvent.EntityPlaceEvent`
  stamps the player's UUID into the block entity's persistent data (`VibeVisions.placed`, `PLACED_BY`) for personal
  blocks only (beds, both halves; chests, barrels, ender chests, furnaces, smokers, shulker boxes). It saves with the
  block entity for free. Pets answer the same question through `OwnableEntity.getOwnerUUID`. A touch vision is Read the
  Vibe with the hand on one (`VibeVisions.touch`, checked first in `VibeSight.read`), within `TOUCH_REACH`, rested
  `TOUCH_REST` apart. Any power that should find a player through their things can ask `placedBy`.
- **Seeing the future is a hook where the event is prepared.** `VillageIncidents.requestAt` calls
  `VibeVisions.foresee`, so a village loading for an incident counts as trouble before it starts. A new kind of
  incident is foreseen for free.
- **A hologram for people who can't see the spectator.** Spectators aren't sent to other players, so
  `VibeHologramPayload` carries the position and look every other tick to players within 64 blocks, and
  `client/effects/VibeHologramClient` draws the player model with the skin from the client's player list: translucent
  and blue-tinted, a charged-creeper `energySwirl` sheen over it, and an offset ghost copy. Simple Voice Chat won't let
  ordinary players hear a spectator, so `MindVoice.hologram` relays their microphone as a locational sound from the
  hologram (`VibeVisions.voices`, published from the server tick), with its own "Vibe Hologram" category.
- **A screen effect as a post chain.** `assets/heroesevolved/shaders/post/vibe_vision.json` chains vanilla's `box_blur`
  and `color_convolve` programs; `client/effects/VibeVisionClient` loads it with `GameRenderer.loadEffect`, animates the
  blur with `PostChain.setUniform("Radius", ...)` and shuts it down only if it is still the current effect (spectating
  a creeper replaces it). Washes, edges, scan lines and stutters are drawn over the HUD in `RenderGuiEvent.Post`, so a
  preview that wants them must leave the GUI on.
- **Ghosts of real creatures, not stick figures.** Read the Vibe's echoes (`client/effects/VibeEchoes`) draw each body
  with its own model: the `VIBE_ECHO` piece carries the entity type's registry id in its seed (and the living entity's
  id, for a player's skin), the client makes a stand-in per type (`type.create`, or a `RemotePlayer`), poses it and
  renders the model alone (no layers) with `entityTranslucent` plus an `eyes` glow, through
  `SetPieceClient.running(piece)`. Don't mark things with `PrivateGlow` outlines in Vibe: a white silhouette seen
  through bodies reads as a stick figure; a `VIBE_HIT` ripple bound to the body says the same.
- **A held beam that pays as it goes.** The held Vibe Blast (`VibeService.stream`) starts a `StaminaDrain` with the
  table's `drain` and ends on the key's `release`, a second press, or an empty bar. Its hits are timed by a due tick
  (`nextHit`), not by its age, so a first tick that lands late never skips a hit, and it clears the target's
  `invulnerableTime` before each hit, or a mob ignores every other hit 6 ticks apart.
- **A mass drop driven by hand.** Rift Fall (`vibe/RiftFall`) moves every swallowed body itself along a fixed fall line
  (`teleportTo` plus a `ClientboundTeleportEntityPacket` each tick, because a mob's position otherwise reaches clients
  every few ticks), so it lands the same for NoAI test mobs and players, adds no vanilla fall damage, and puts anyone
  still falling down where they'd land if the caster leaves (`forget`). Anything a breach can't take (`fits`) is
  slammed in place.
- **A breach window must write its depth with the depth test on.** Chunk layers' `clearRenderState` turns depth testing
  off, and OpenGL writes no depth while it is off, so `BreachViewClient` re-enables it before putting the opening's
  depth back. Without that, block entities (beds, chests) and translucent blocks behind a breach showed through it.
  Put it back a hair behind the plane (`DEPTH_BACK`, toward the far side), not in it: the iris's rim is drawn in the
  plane, and two surfaces at one depth z-fight, which flickered as jagged shards round the edge.
- **Photographs:** `tools/vibe-vision-preview.init.gradle` (`uitest/VibeVisionPreview`, flag
  `heroes.preview.vibevision`). A player just released from the bunker has a darkness effect for a few seconds: clear
  effects before the first shot. Tests: `tools/vibe-gametest.init.gradle` runs only the Vibe (and grid) tests.
