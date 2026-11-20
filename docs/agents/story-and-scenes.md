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
- **Ready-up without a screen.** A clickable chat line runs `/lastday ready` in the lobby (see the rework below for the
  rules). Late arrivals wait on a held black tint until the run ends.
- **Tension without the payoff.** The light grows on one shared curve (`PrologueTimeline`) read by the server (white
  flashes and tremors on each surge) and the client (the glow's size), but it never detonates on screen. Save the
  real blast for the vision.
- **Stragglers are carried, not killed.** At the deadline an agent darts anyone outside (`Tranquilizer` trail and
  go-under blur), and they skip to their pod. Nobody holds the group up.
- **The bunker as it was.** `story/bunker/PastBunker.rooms(level, origin)` builds the stasis bunker from the
  `stasis_*_past` blueprints: new copper, every light on, nothing grown in. Night Three and the prologue both use it.
  Build its rock once below anything the city rebuilds, and reset only its rooms, pods and doors each run.
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
  own pieces: `FLIGHT_SKYBURST`, `ELLE_BOLT`, `WATCH_HOLD`, `FLIGHT_IMPACT`, `TIME_SNAP`); the Last Day's own fight
  (`LastDayFight`, see the rework below) uses its plaza passes and draws everything on each client from the light's start,
  because a dedicated server only sends entities within its view distance and the plaza is 130 blocks from the market.
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

## Reusable patterns from the Last Day rework (ready-up, the fight, the rescue, the wave)

The Last Day was reworked in November 2026: an explicit ready-up in the lobby, a thread of light to every goal, holstered
agents, music in two stems, Peter and Sylar's fight brought down the avenue to the market, the people its rubble pins,
and Ted's wave knocking everyone flat. What the player sees is in [docs/the-last-day.md](../the-last-day.md).

- **A scripted fight is one class of maths, drawn on the client.** `story/prologue/LastDayFight` gives Peter's and
  Sylar's positions, poses and beats at any tick since the light; `client/effects/intro/LastDayArt` draws them as
  client-only `StoryActorEntity` stand-ins (never added to the level, rendered through the entity dispatcher with their
  real skins and poses) and plays each beat's set pieces locally (`SetPieceClient.play`) with the sound delayed by
  distance; the server does only what touches the world (the torn facades, the rubble, the wave) on the same ticks. Real
  actors can't be seen from the far end of the avenue on a server with a short view distance; stand-ins can.
- **Long flights go by the open air.** The first entrance flew straight from the outskirts to the plaza, through a tower;
  the test that walks every tick of the path for open air (`theSkyFightHasRoomAndAWallToHit`) caught it. Route any long
  flight over the avenue (the road is open to the sky its whole length) and into a checked region (`along` with the
  `PLAZA_GATE` waypoints).
- **Find the walls when the scene starts, then share them.** The chunks Sylar throws come from and land on facades found
  by raycast when the light appears (`LastDayFight.find`, with a fallback guess the test checks), and the four points
  ride to every client in the light's cue, so a client never raycasts a wall the server has already broken.
- **A cue that carries its own age.** The light's cue sends how far into the light the day already is, and every jump
  (`skipTo`) re-sends it: a client's whole drawing runs from that start, so previews and tests that jump stay in step.
  Beats on the server fire on `>=` with a done flag, never `==`, or a jump skips them.
- **Everything the day breaks is logged and put back.** `LastDayDamage.set` remembers each block's first state (rubble,
  facade holes, every burst window) and `restore` puts them back when the day ends; only the box is saved, and a day that
  never ended (a crash) has that box rebuilt from the city's plan next time. Writes skip neighbour and shape updates, so
  a burst pane leaves its neighbours' frames as they were.
- **Rubble that reads as diggable.** The first pile was infested stone (soft, no tool needed) and the user didn't think to
  dig it: it looked like stone. It's gravel now, burying the legs with more heaped on top and against the building side,
  so what's above slides into the hole and the dig takes a few goes. Nothing broken in the city drops anything
  (`BlockDropsEvent`, and stray gravel and flint items are turned away). Gravel falls, so nothing is ever put above the
  body's own cell, only beside and over the legs; the road side stays open to reach them. The city still cancels every
  break except `PrologueService.mayBreak` positions.
- **Someone lying down is hard to lift.** A body in the sleeping pose is a 0.2-block box at floor level: the crosshair
  rarely finds it, the carry's line-of-sight check from the eyes runs into the rubble beside it, and its height makes the
  carry hold it against the chest like a cat. Lifting is `PrologueService.lift`: sneak and right-click them or the
  ground within 2.5 blocks, they're stood up first (so the hold is the two-arm one), and `EntityCarryService.pickUpBeside`
  skips the sight check. The city's interact rule runs at `HIGH` priority, so the ordinary carry never grabs them flat.
- **A city that comes apart, then comes back.** `LastDayCrumble` tears roof edges and exposed facade blocks loose as the
  wave passes (within 64 blocks of a player), strews slabs and gravel on the street, and keeps a facade section falling
  near someone every second or two. Each piece is a `FallingBlockEntity` made from its saved form (`DropItem` and
  `CancelDrop` set, so it neither writes the world where it starts, as the vanilla factory does with neighbour updates
  that would drop torches and signs unlogged, nor places itself where it lands); the class lands it as logged rubble.
  Primatech, the trapped and anyone standing are kept clear, and the thread's maps are redrawn every two seconds while the
  street changes, not every tick.
- **The street's level is not the road's.** Odessa's road surface is at `STREET_Y - 1` and its sidewalks are bottom slabs
  at `STREET_Y`. The first crumble put street rubble at `STREET_Y + 1`, over air or a slab's half-height top, and almost
  none of it landed (28 pieces out of thousands of tries), and pieces that came down on a sidewalk were refused for the
  same reason. `LastDayCrumble.strew` now puts rubble at `STREET_Y` and lets it take a sidewalk slab's place (logged, so
  the slab comes back). Before placing anything on the street, read `Streets.ground` for what is really there.
- **Leave remains.** The user didn't want every window gone: the wave bursts about half the glass, chosen per block by a
  hash (`LastDayWave.BURSTS`), so frames keep jagged pieces; near players a few shards lie on the ground below each
  burst pane, as flat `BlockDisplay` glass (no collision, scene-tagged, gone with the day). Use a display for any
  decoration a scene leaves lying about that mustn't be a block.
- **Dust in the air is fog.** After the wave passes the camera, `BlastRenderer.atmosphere` pulls the city's fog in to about
  50 blocks and tints it the colour of dust, thinning over half a minute but never clearing; the wave's front throws
  tumbling grit boxes (`LastDayArt.flung`).
- **The thread of light is a flooded distance map.** `LastDayGuide` floods walking distance out from each goal through
  its box once (about 12 ms for the streets), caches it until the street changes, and sends each player the corners of
  the walk downhill from where they stand; one new map per tick at most. The client smooths the corners (keeping climbs
  crisp) and draws a three-layer ribbon with pulses running toward the goal (`GuideArt`).
- **Instructions the server decides.** The client can't tell who is trapped or freed, so the line (dig, lift, carry, go)
  is a cue the server sends when a member's own situation changes (`Member.hint`).
- **One knocked-down view.** `client/DazeView` (flat on your back, blinking awake, the head wandering) is shared by the
  radiation awakening and the wave; the wave adds ringing ears (`lastday_ringing` on the master channel) and muffles
  every other sound by scaling `SoundEngine.calculateVolume` (`mixin/SoundEngineDuckMixin`), re-applied to playing sounds
  through `updateSourceVolume` while it changes. Never let the duck reach zero: a channel whose volume is set to zero stops.
- **Wait for the effective config, not the default.** A test world keeps its old config file, so `prologueRunSeconds`
  read 120 after the default became 150 and a test's jump to "the end of the run" landed short. Tests and previews read
  the day's own numbers (`PrologueService.runTicks()`), and a run never drops below what its script needs
  (`PrologueTimeline.run`).
- **Doors work by hand, through the day's log.** The city cancels every right-click on a block, so no door would open;
  the user wanted to walk into the market and work doors themselves. `TimeIntroEvents.rightBlock` now hands wooden doors
  and fence gates to `PrologueService.useDoor`, which flips both halves of a door itself through `LastDayDamage` (it writes
  without neighbour updates, so one half never follows the other) and plays the door's own sound; the client answers
  SUCCESS for the same blocks (`openableByHand`) so the hand swings. The Arms' street doors stay with `useBlock` (shut until
  everyone is ready). `LastDayGuide.height` counts a shut wooden door as open, so the thread never routes round one.
- **A checkpoint is a place, not a radius in the road.** The errand's checkpoint is `Market.checkout()`, on the shop floor
  in front of the counter (`Market.arrived` is inside the shop and within four blocks of it); the thread then leads back
  out to `Market.beacon()`, the avenue in front, where the fight, the rubble and the wave are staged.
- **A slow scene reads; a fast one is skipped.** Noah's scene was a line every two or three seconds and the user found it
  rushed. Its beats are named constants (`PrologueService.NOAH_PLEA` ... `NOAH_ORDER`), about a hundred ticks apart, and
  tests and previews skip by name, so retiming the scene never breaks them.
- **Someone who shoots you walks up first.** The stun guns (`ModItems.STUN_GUN`, a plain item with a box model, so it
  reads as a taser and not a TaCZ gun) are fired through `PrologueService.Tase`: each shooter walks to an arm's length
  (`TASE_REACH`), following their target, raises it (`Pose.AIM` on an actor, `CompanyAgentEntity.aiming` on a guard),
  holds it for `TASE_AIM`, and fires; one target at a time per shooter. Alone, Noah does it; with a group he orders the
  guards in the bunker to (`order`). The hall is closed while it happens: anyone outside it is put back at
  `Scene.GATHER`. The test checks `PrologueService.firedFrom()`, how far the farthest shot was taken from.
- **A mob's walk goes with the square of its speed.** `MoveControl` sets both the mob's speed and its forward input to
  `modifier * MOVEMENT_SPEED`, and the two multiply, so an actor told to walk at 0.5 covers about a block a second: Noah
  fired from eight blocks off before `STRIDE` (0.95) gave him a normal pace. Time a walk in a test before writing a
  scene around it. A walk also ends a block or so short of its target (the path's accuracy), and a walk shorter than
  that never starts, so "close enough" needs that slack: Noah aimed to stop 2.6 short, ended 4.4 off, and stood there
  until the give-up timer. Now he aims 2.0 short and anything within 3.5 counts; the debug log says how far he was.
- **A ready-up nobody can be stuck in.** Ready counts only in the lobby (leaving it takes it back), typing *ready* counts
  (`ServerChatEvent`, the message still goes through), and nothing moves on a timer; after `prologueReadySeconds` the
  ready ones are offered `/lastday go`.
- **An actor is drawn a player's size, and stands on a pod's floor.** A mob renderer draws the player model two blocks
  tall; the player renderer scales it by 15/16. `StoryActorRenderer.scale` does the same now, after Claire's head came
  out through the lid of Pod 14 (a pod is under 1.9 blocks inside: a 2-pixel base plate and a 2-pixel lid). Someone
  sealed in a pod is placed at `Vec3.atBottomCenterOf(base)`, feet in the base plate, with gravity off, not at
  `CryoChamberBlock.standing` (that is for players, who see it from inside). Arms held straight out go through the
  glass; `Pose.SEALED` keeps the palms on it at waist height. Villagers in pods also take `Attributes.SCALE` 0.88
  (`PodSleepers.SCALE`): a villager is taller than a player.
- **A dimension change stops every sound.** `Minecraft.setLevel` calls `soundManager.stop()`, so "103 years later"
  was cut off 5 seconds into its 9.5 when the waking teleported the player to the overworld. Music that has to finish
  plays before the teleport: the pods phase now waits for it (`PrologueService.PODS_WAKE` is `PODS_YEARS` plus the cue's
  length) under a black hold that lasts until then.
- **A crowd hands people on; it doesn't delete them.** `VisionCrowd.shelter(door, stair, below)` gives each runner who
  reaches the head of Primatech's stair to `LastDayShelter.take`, and the crowd lets go of them (`Person.below`). The
  shelter leads them down the way players go, on the thread of light's own maps (`LastDayGuide.route` with the same
  `vaultGoal` and `hallGoal`, so nothing is flooded twice), setting a villager's `WALK_TARGET` to the first corner at
  least three blocks ahead (a villager plans only about forty blocks, so never the far end). `odessasPeopleWalkDownIntoTheHall`
  checks one walks from the stair head into the hall.
- **A beat that needs people guarantees them.** The vault shutting "too soon" only works with someone outside, so
  `LastDayShelter.shut` makes sure at least `SHUT_OUT` are: whoever is still on the stair side, plus people put on the
  landing behind the shut door (nobody can see them get there). They are heard, not seen: fists on the steel in bursts
  (`ZOMBIE_ATTACK_IRON_DOOR` at the vault door, so it fades as you go down the corridor) and a line through it every five
  seconds in chat to whoever is within 40 blocks of the door. The guards' "move down the hall" is two chat lines at the
  seal and one more 7 seconds later if a player is still out of the hall.
- **Bodies in pods are villagers with no AI, kept in step with the claims.** `PodSleepers` is the one place for them, in
  the past and the present: `NoAI` (so they don't fall, wander or get pushed), invulnerable, silent, scaled, tagged, and
  never traded with (`PlayerInteractEvent.EntityInteract` cancelled). The present's bodies are put in every hall pod with
  no claim in `BunkerData.pods` and taken out of any pod that gets one (`PodSleepers.present`, every second, and
  `clear` when a waking begins). It acts only where `ServerLevel.areEntitiesLoaded` holds for the pod's chunk: a chunk's
  blocks load before its entities, and a body checked for too early would be put in twice. The same nameplate name per
  pod (`PodSleepers.name`) on the Last Day and in the present.
- **Something noticed is noticed on approach.** Claire's note waited for a right-click on Pod 14 that nobody thought to
  give. `CryoChamberService.noticeClaire` runs from the bunker's tick every five ticks for players within three blocks;
  the right-click is an ordinary pod again.
- **Turning a player's head for them.** The client sets the player's own rotation in `RenderFrameEvent.Pre` (which fires
  after the mouse has turned the player that frame) and zeroes the mouse with `CalculatePlayerTurnEvent`
  (`setMouseSensitivity(-1 / 3.0)` makes the turn factor zero). Per tick is not enough: a `LocalPlayer`'s yaw isn't
  interpolated between ticks, so it steps. `WaveApproach` turns the head to the wave for its last 30 ticks from where
  `PrologueTimeline.hits` says it reaches this player, which is the server's own knock-down test, so the two agree.
- **Out cold, then the blinks.** `DazeView` takes a `blackout` (the wave's is `PrologueTimeline.BLACKOUT`, 60 ticks of
  `DAZE`'s 160): eyes shut but for two flutters, and `age()` held at 0 so the radiation awakening's tuned blinks play
  over what is left. What is heard while out cold must be on the master channel (`PanicSounds.dazed`), or
  `SoundEngineDuckMixin` muffles it with everything else.
- **Tests that read lights must load them first.** `BunkerPower.cut` only reaches loaded fixtures (in play the bunker is
  round the sleeper), and `getBlockState` on an unloaded chunk loads it from disk as an earlier test left it.
  `aNewBunkerWakesDarkAndThePowerComesBack` failed with a lamp lit on the far wall right after the bunker test let go
  of that chunk; it now forces every light's chunk and waits for them before cutting the power.

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

## Reusable patterns from the Sublevel 4 rebuild

The bunker was rebuilt from scratch in November 2026 with a new layout (airlock, commons, stasis hall, records,
generator, infirmary, containment, maintenance, and the sealed Helix Ward with one private pod). What the player sees is in
[docs/the-past.md](../the-past.md#sublevel-4).

- **One geometry class, two designs.** `story/bunker/BunkerLayout` holds every room, pod, nameplate, receiver, survivor
  spot, the generator and Claire's chest, and `BunkerLayout.Scene` the Last Day's and Night Three's spots in the hall.
  `BunkerData.design` says which design a world's bunker was built with (`LEGACY`, the original rooms, for saves without
  the field), and everything that touches the present bunker asks `data.layout()`; the past is always `STASIS`. Old worlds'
  sleepers kept their pods because nothing hard-codes a coordinate any more. Add a spot to the layout, never to a caller.
- **Draw the past and the present from one script.** The rooms are drawn by `tools/generate-bunker.py`: the bunker as
  built, then a copy aged by 103 years, written as `stasis_<room>_past` and `stasis_<room>`. A block-by-block remap of one
  blueprint (`PastBunker.past`, the old way) can't move a prop, cut a hole or drop rubble, and an overlay of `_` cells
  escapes `BlueprintPlacementGameTests` (everything beside a keep cell is only a note). Two full blueprints are each
  validated and placed in full. Marker cells are protected from aging, and a game test checks both twins keep them.
- **Slice rooms by ownership.** The script draws every room into one grid in bunker coordinates; each cell belongs to the
  room that drew it last, and a room's blueprint holds only its own cells (`_` elsewhere), so shared walls never
  disagree and no marker is placed twice. The room boxes come out of the script (it prints the `Room` lines for
  `BunkerLayout`).
- **Every change the years made has a cause.** Copper oxidises by zone (most near the cave's water, least in the sealed,
  dry ward); cracks gather where the blast came through the ceiling and round the breach; moss thins with distance from
  the cave; the planter's azalea became the tree. Random noise on its own reads as noise.
- **Check the dark before the game does.** `py -3 tools/generate-bunker.py --light` propagates block light through the
  present and the past (slabs and closed trapdoors block it vertically, stairs and full blocks entirely) and lists every
  floor cell a monster could spawn on, by room. The in-game `BUNKER_DARK` only prints the first few cells of a long list.
  It found containment's cells, the ward's corner under the mezzanine and the floor under the far balcony.
- **Cobwebs hang three clear blocks up.** The first pass put webs in the ward's two-high escape vent and at head height on
  the mezzanine; `cobwebs()` now wants three air cells beneath.
- **The bunker stays 13 high.** The Last Day's stairwell is a fixed blueprint that meets the vault 18 below Odessa's
  street, so `LastDayData.origin()` is pinned there and the rooms must keep their roof under the street. Under Odessa the
  rock poured round the past bunker stops at the rooms' ceilings (`PastBunker.rock`): above them start the city's cellars
  and subway steps.
- **One sleeper apart.** `HelixWard.draw` picks from the Last Day's group when the light appears (never a lone player,
  never when `BunkerData.labOpen()` is false, which includes legacy bunkers); `seal` claims the ward (`claimLab`, pod index
  `LAB_POD`) and doses them (`HelixWard.dose`: a random rollable power, dormant). `BunkerService.begin` doses again if a
  crash skipped it, and sends the ward's own captions and music cue.
- **Logs go in chests, not on lecterns.** `BunkerBuilder.finish` puts a `@log:<id>` book in a chest's middle slot,
  `BunkerBuilder.log` reads it back from a chest or a lectern, and opening a chest with a story note in it counts as
  reading it (`StoryDiscoveryEvents`).
- **A new block needs fresh facts.** `tools/blueprint.py validate` and its support rules read `tools/block-facts.json.gz`;
  after adding or removing a block, run `tools/structure-gametest.init.gradle` and copy `build/block-facts.json.gz` over it.
- **Long edit scripts go in files.** On this machine a long heredoc passed to the shell failed with `ENAMETOOLONG` and
  another with a quoting error; write the script with the file tool and run it.

## Reusable patterns from the bunker rework (the original rooms)

These are about the original rooms (`BunkerLayout.LEGACY`), which worlds built before the rebuild still have.

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
