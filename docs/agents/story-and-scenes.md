# Story scenes, Odessa and the bunker: notes for agents

The Last Day prologue, story actors, the Odessa city build and the bunker rework. Read the building skill ([SKILL.md](../../.claude/skills/minecraft-building/SKILL.md)) before changing any scenery. What the player sees: [the-last-day.md](../the-last-day.md), [the-past.md](../the-past.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Reusable patterns from the Last Day (the playable prologue)

Every new player now lives the evening Odessa ended before waking in the bunker (`story/prologue/PrologueService`,
the light's timing in `PrologueTimeline`, Noah, Claire, Peter and Ted in `story/actor/StoryActorEntity`, the
apartments in `time/intro/city/Apartments`). The Space-Time vision reuses the actors to show Peter and Ted. What the
player sees is in [docs/the-last-day.md](../../docs/the-last-day.md).

- **One entity for every scripted character.** `StoryActorEntity` is an unsaved, invulnerable `PathfinderMob` with
  no goals. A synced `Role` picks the skin (`StoryActorRenderer`, Ted adds a glow layer) and a synced `Pose` picks
  the arms. The script drives it with `walkTo`, `face`, `place` and `say` (chat as `<Name>` plus a speech bubble).
  Spawn with a scene tag and remove by it. Add a role and a skin in `tools/GenerateStoryActorTextures.java` for the
  next character; don't write another entity.
- **A scene that hands off to the real game.** The prologue ends by calling `BunkerService.handOff`, which runs the
  normal wake-up in the pod the player was sealed in. The prologue pod index *is* `BunkerData.pod(uuid, …)`, so the
  number matches 103 years later. Pod 14 (Claire's) is excluded everywhere.
- **Two scenes, one city.** The prologue and the Space-Time vision share `time_fracture`. `TimeIntroService` only
  starts its next scene while `PrologueService.running()` is false, and `TimeGather` skips players waiting for a
  prologue. The city's protections (no damage, powers, edits or items) check `isMember` and `isWaiting` as well as
  the vision's visitors.
- **Ready-up without a screen.** A clickable chat line runs `/lastday ready`, and using your own door counts too.
  The run starts when everyone present is ready or a timer after the last arrival. Late arrivals wait on a held black
  tint until the run ends.
- **Tension without the payoff.** The light grows on one shared curve (`PrologueTimeline`) read by the server (white
  flashes and tremors on each surge) and the client (the glow's size), but it never detonates on screen. Save the
  real blast for the vision.
- **Stragglers are carried, not killed.** At the deadline an agent darts anyone outside (`Tranquilizer` trail and
  go-under blur), and they skip to their pod. Nobody holds the group up.
- **The bunker as it was.** `story/bunker/PastBunker.build(level, origin)` builds the stasis bunker from the same
  blueprints with every light on and nothing grown in (`past(layer, state)`). Night Three and the prologue both use
  it. Build it once below anything the city rebuilds, and reset only its pods and doors each run.
- **A way in for a crowd under a deadline is a stair, not a ladder.** The safe house is reached by a 3-wide switchback
  (blueprint `lastday_stairwell`); the city draws only its top step and a railed slot in the lobby floor
  (`Landmarks.safeHouse`), so a city rebuild never touches the part underground. A game test walks the route cell by
  cell (`PrologueGameTests.walkable`).
- **A door that shuts behind a group mustn't shut anyone out.** The vault (`PrologueService.vault`, block swaps with a
  grindstone for its wheel) closes only once every sheltered member is past it, or after ten seconds; anyone still on
  the stair side is moved through first. "Safe" starts at the turn of the stair, and anyone who climbs back up is put
  back at the foot.
- **One pod that looks different.** Pod 14's clawed glass is a `cracked` block state on the Cryochamber with four
  `_cracked` models that inherit the normal ones and swap only the glass texture. Use this for any one-off variant of a
  modelled block; don't copy the models.
- **Check where the cast stands, in a test.** Guards and actors are spawned at hand-written offsets inside rooms built
  from blueprints; two of them once stood inside a desk and a lectern. `PrologueGameTests.standing` asserts every
  scene entity is clear of blocks and on a floor once the scene is set. Do the same for any scripted scene.
- **Story text goes to chat, once.** `TimeIntroCaption` writes captions (with their subtitle in grey) and instructions
  (`»`) to chat, skipping repeats, instead of drawing over the scene; story actors speak in chat only. Don't add
  action-bar nags or on-screen captions to a scene: one chat line per beat. Hide corner labels while a black hold tint
  is up (`ClientScreenTintState.blackedOut`).
- **Pace before the payoff.** The evening crowd runs in `VisionCrowd.calm()` (a conversation every thirteen seconds, pauses
  between lines); the light gets fifteen quiet seconds of staring (`PrologueTimeline.SIRENS`) before the sirens, the
  agents' calls and the objective; the surges sit at fractions of the run, so they quicken toward the end.
- **Props on a face are items, not skin pixels.** Glasses painted into an 8-pixel face read as a mask. Noah's are an
  item (`HornRimmedGlassesItem`, `Equipable` in the head slot) whose 3D model the head item layer draws on the face; the
  model is sized for that layer (a full block worn on the head is 1.25 times the head, so the head spans 1.6 to 14.4 in
  model units and the face is the north side), and `neoforge:separate_transforms` gives it a flat icon elsewhere.
  `StoryActorEntity.role` puts the pair on Noah. Use the same for hats, masks or headsets.
- **A blast felt underground.** `PrologueService.quake` collects the hall's lights when the blow lands, kills them,
  flickers them back (a third stay dark) and drops `FallingBlockEntity` rubble down the aisle, all undone by the next
  run's room rebuild. `PrologueGameTests` checks rubble and dark lights after the blast.
- **One path, two ways of seeing it.** The sky fight (`time/intro/SkyFight`) is pure maths: positions and moves at any
  tick for a `Script` (start, pass length, passes). The vision moves real actors along it (`VisionFight`, with the powers'
  own pieces: `FLIGHT_SKYBURST`, `ELLE_BOLT`, `WATCH_HOLD`, `FLIGHT_IMPACT`, `TIME_SNAP`); the Last Day draws it on each
  client from the light's start time (`client/effects/intro/SkyFightArt`, hooked into `SetPieceClient`'s live drawing),
  because a dedicated server only sends entities within its view distance and the fight is 190 blocks away. Use this
  for anything watched from very far off.
- **Probe the city before choreographing over it.** The towers' real faces and heights decided the fight's centre, its
  reach, and where the throw lands; `PrologueGameTests.theSkyFightHasRoomAndAWallToHit` walks every tick of the path for
  open air and checks the raycast finds the wall the far view draws. A real crater is put back with
  `FractureCity.rebuild` when the fireball covers it.
- **Flying actors.** `StoryActorEntity.glide(at, look)` sets position and facing each tick (no gravity, no physics), and
  `StoryActorRenderer.setupRotations` lays a `FLY` actor along its line of flight (as an elytra flyer) or spins a
  `TUMBLE` one. `shouldRenderAtSqrDistance` is raised so actors read from across the city; their update interval is 1.
- **A player's double in a scene.** `Role.TRAVELER` with `StoryActorEntity.doubleOf(uuid, name)` syncs whose double it is;
  `StoryActorRenderer` draws that player's own skin as this client knows it (on the slim model if their arms are slim)
  and shows their name over the head, and the actor speaks in chat under that name. The vision's twist uses it: the
  time traveler whose power the vision is comes back for Peter (`VisionFight.travelerArrives`).
- **Vanilla does the frost and the title.** The pods' creeping frost is `setTicksFrozen` (the powder-snow overlay,
  kept below the damage threshold), and *103 years later...* is a vanilla title packet over a held black tint
  (`ScreenTintPayload.Style.HOLD`). No new overlay was needed.
- **Recovery from a crash.** A player sealed in their pod gets the `SLEPT` tag; logging in with it skips straight to
  the wake-up. Anyone who logged out earlier starts the day over.
- **Returning a group together.** The vision's `together(...)` lands everyone in a ring of open standing spots round
  the traveler's return point, falling back to each player's own ticket if the traveler's is gone. Reuse it for any
  scene that should end with the party side by side.

## Reusable patterns from the Odessa rebuild

Odessa (`time/intro/city/`) was redrawn in November 2026 so nothing floats or dead-ends, and gained outskirts so the city
no longer ends in fog over grey concrete. What the player sees is in [docs/the-last-day.md](../../docs/the-last-day.md).

- **Audit a build, don't eyeball it.** `CityAudit.run` walks a box and reports floating blocks (anything not joined face
  to face to the ground), blocks whose `canSurvive` fails (plants on concrete, wall signs on nothing, ladders on walls),
  lone glass panes, and doors that open into a wall or onto a drop. The city preview prints `CITY_AUDIT`, the bunker
  preview `BUNKER_AUDIT`, and `CityAuditGameTests` fails on any fault. Iron bars may stand alone on purpose (poles).
- **Draw from the wall out.** `Building.Face` addresses a cell by face, position along it and depth (0 the wall plane,
  1 the glass, negative over the sidewalk). Awnings, cornices, canopies, stoops and fire escapes all hang from depth 0,
  and only on faces with a street in front, so nothing reaches into the building next door.
- **A window is held at both ends.** Panes sit a block back between piers two blocks deep; a pane beside a door or a
  corner goes in the wall plane instead, where the corner holds it. A room three blocks deep with a partition behind it
  shows through a lit window, not a hollow shell.
- **Build what players see first.** `FractureCity` builds the city's chunks before the outskirts (`ready()` vs
  `complete()`) and saves how far it got, so the Last Day never waits on the prairie.
- **Paving clears what grew there.** Scrub is scattered by the ground pass; any feature that repaves calls
  `Outskirts.floor`, which clears the block above, or grass is left standing on a parking lot.
- **Dark floor, and a city without monsters.** `CityAudit.run(..., true)` also finds dark floor where monsters would
  spawn; run it after the light settles and only over the build (caves round it are dark too). The vision dimension
  spawns monsters at light level 0, so `CityMonsters` cancels every natural monster spawn there: every sealed dark room
  and the space behind each building's rooms would otherwise breed zombies during the Last Day.

## Reusable patterns from the bunker rework

The stasis bunker (`story/bunker/`) was reworked in November 2026: the lab and infirmary grew, the plain rooms gained
structure, and the way out became the Last Day's own vault and stair, a hundred years on. What the player sees is in
[docs/the-past.md](../../docs/the-past.md).

- **The way out is drawn in code.** `BunkerStair` builds the vault, the switchback and the ruined lobby at spawn, because
  its depth depends on the terrain (about 31 blocks on a normal world). Two lanes (the south one beside the vault, the
  north one behind a divider) climb along x 3..12 and turn on landings at both ends; the number and length of flights
  are worked out from the depth, so the last step is always the lobby's floor. The top flight stays open to the sky as
  a railed slot, as Primatech's lobby has it. A world too shallow for it (`BunkerStair.fits`, under 12) keeps the ladder.
- **One look, two times.** The vault, the hazard stripe, the rails and the SUBLEVEL 4 sign copy the Last Day's
  (`lastday_stairwell`, `PrologueService.vault`, `Landmarks.safeHouse`), so a player who lived the Last Day recognises
  the door they came through. The present's version is stuck open off its hinge, cracked, and grown over.
- **Vines need a face or a vine above.** A vine hung across a doorway is held by the vine above it, back to one on the
  lintel; one placed against air, glass or iron bars can't stay. Lichen, cave vines and hanging roots need the block
  above or behind them too. The audit finds the ones that don't.
- **Build into free rock, keep the pins.** The lab grew east and up and the infirmary west, keeping every cell that code,
  tests and `BunkerLayoutUpgrade` read (chests, lecterns, cots, survivor spots, lights). Moving the anchor with the
  growth keeps old cells at their world positions. Bunkers are built once, so all of this reaches new worlds only;
  existing worlds keep their rooms, ladder and hatch.
- **Audit the bunker too.** The bunker preview prints `BUNKER_AUDIT` for the rooms, the stairwell and the lobby. It
  photographs the vault, the stairwell, the slot and the lobby (`bunker-18` to `bunker-21`).
