# Animating abilities

A guide for anyone (Claude, ChatGPT/Codex, or a person) making the visuals and animations for a power in
Heroes Evolved. It is drawn from the three sets that look best so far: **Quake**, **Superspeed** and
**Space-Time Manipulation**. Read it before writing any effect, pose or set piece, and check your work
against the list at the end before showing it.

The player judges a power by watching it. They don't read the description or the code. If it doesn't look good
on screen it isn't finished, however correct it is.

## 1. The bar: geometry, not particle spam

Powers are drawn as **shapes made of light, wind and matter**: ribbons, rings, discs, shells, funnels, cloud
billows, shards and boxes, built in 3D around the body and the world. They are **not** clouds of vanilla
particles.

- What worked: the time-travel intro's blast (lumpy cloud meshes, shells and ribbons), Quake's ring tubes,
  the speedster tornado, Rewrite the Moment's violet trail with ghosts.
- What failed: the first rewind sigil used `END_ROD` particles and looked like a snowstorm. A pile of
  particles reads as clutter; one well-shaped form reads as a power.

Vanilla particles are fine as **seasoning** (a little sand hanging in slowed time, a few reverse-portal motes,
a puff of smoke when a speedster puts out their fire). They are never the main event.

## 2. What the three reference sets do well

### Quake: one idea, everywhere

Daisy's power has a single visual idea: **clear, shimmering rings of pressure**, the way the air warps
round her hands on screen. Every move is a variation on it (`client/effects/QuakeEffects.java`):

- **Shockwave** is a tube of rings opening out from the palm. Each ring appears a moment after the one
  before (`delay = d / 3`), so you *see* the wave travel. Rings widen with distance
  (`0.18 + 0.55 * sqrt(t)`), two thin wavering strands run along the tube, and a ring pops where it lands.
- **Resonate** is one trembling line to the target, then rings shivering up and down its body.
- **Fault Line** is a dark jagged crack across the ground with dust flicked up along it.
- **The cost shows on the body.** Past 80 Strain her forearms shake; at the limit, red and white cracks run
  up both arms with a red flash.

Lesson: **pick one strong visual idea for the power and make every ability a variation on it.** A player
should know which power it is from a single frame.

### Superspeed: the body is the effect

The Flash's lightning and Daphne's blur (`client/effects/SpeedEffects.java`,
`client/effects/setpiece/SpeedArt.java`, `SpeedCyclone.java`, `SpeedAfterimages.java`):

- **Running** leaves a gold ribbon at waist height with a jagged white-gold bolt through it. Lightning
  crawls over the body, more of it with more Momentum.
- **Afterimages** are the player's real skin and pose, frozen in the world and fading. They aren't sprites.
- **Cyclone** hides the real body at the eye and runs a stand-in with the player's skin, armour and cape round
  the ring, leaning into the turn, with six fading ghosts behind it. Wind bands are dragged round after the
  runner.
- **The tornado** is one shape (`TornadoShape`) shared by the server's physics and the drawing, so what is
  caught rides the wall you see. It pinches to a neck, flares into a lid of storm cloud, turns faster near the
  ground, and comes down by lifting off and thinning away from the bottom up.
- **The stop gust** is skid dust billowing forward, streaks of air rushing on past, a bow wave of pressed air
  and two scorched skid marks.
- **Wind Arms** points both arms along the aim, with the hands circling too fast to see. A blurred disc sits
  at each hand and a funnel of wind spirals off each palm.

Lesson: **put the power in the body and the motion, not just around it.** Use the player's own skin and
pose (afterimages, stand-ins, arm poses), and let the shapes come out of the hands and feet.

### Space-Time: a visual grammar

Time reads as **clockwork made of light** (`client/effects/TimeEffects.java`, `setpiece/RewindArt.java`,
`TimeGhosts.java`). It is a small grammar that every ability speaks:

- **Clock faces** on the ground: two rings, twelve hour marks (every third one longer), and hands.
- **Slowed time** is warm amber and drifts: hands crawl round, and sand hangs in the air, barely falling.
- **Stopped time** is cold violet and holds perfectly still: hands stopped at ten past ten, motes frozen.
- **Rewound time runs backwards.** Trails draw from now to then, the hands spin the wrong way, and ghosts appear
  along the path *newest first*.
- **Fold Space** stages it in steps (`ElectroEffects.later(step, ...)`): the tear behind you pinches shut, a
  seam of light runs to where you land, and a tear opens there.

Lesson: **give the power rules, and let them carry meaning.** Colour, speed and direction tell the player
what is happening (slow, stopped, backwards) without any text.

## 3. Principles

1. **One idea per power, one palette per power.** Name three to six colours, each with a role. Hard-code them
   as constants at the top of the art class.

   | Power | Palette (role) |
   | --- | --- |
   | Quake | `CFE6FF` wave, `FFFFFF` core, `7FA8D8` deep strand, `A8946E` dust, `2A2622` crack, `FF5A5A` strain/heart |
   | Superspeed | `FFC640` gold, `FFF4C8` hot core, `FF8C1E` amber, pale `DBE6F5` wind, warm dust, soot |
   | Space-Time | `FFD27A` gold, `E8A43C` amber (slowed), `9A7CFF` violet, `5B3FD1` deep, `F4EBFF` pale core, `C9C2FF` ice (stopped) |
   | Flight | `535A5A` shadow, `D2D4CE` vapor, `DCDDD5` condensation, `F6F4EC` core (all in `FlightArt`; Tempest's storm uses `DBDBD2` cloud and `EEEDE3` wind) |

   Flight is also a good model of a whole power built from one shape language: every Flight set piece is drawn
   by `FlightArt` from three shared primitives (a translucent `arc` sheet with a turning lip, a broken tapered
   `crest`, and a `spiral`). See the Flight section of [AGENTS.md](../AGENTS.md).

2. **Layer every glowing line in three.** Draw a wide, faint halo, then the coloured body, then a thin bright
   core. Rewind's path is exactly this: deep violet at 0.22 wide and 0.4 alpha, violet at 0.07 and 0.85, and a
   pale core at 0.025 and 1.0. One flat line looks cheap.
3. **Make motion tell the story.** Each move should have a wind-up, a release and an aftermath. Stagger
   things in time so they travel (delays, `ElectroEffects.later`, or a set piece's age `t`). A wave should be
   seen moving outward, not appear all at once.
4. **Nothing pops.** Fade every element in and out over a few ticks. Big things leave in a way that suits
   them: a tornado lifts and thins, a breach slams shut in a flash, a clock winds down to rest.
5. **Start the effect from the body.** Effects come out of the hand, the feet or the chest, and the arm pose
   must point where the effect goes. Get the anchor points right (section 5).
6. **Make it readable in daylight, from every side.** Glows use additive blending so they show at noon. Check
   from the side, from behind, from the front and in first person.
7. **Dark needs its own pass.** Black adds nothing when blended additively, so cracks, soot, scorch marks and
   shadows must use normal blending. `LightningRenderer` does this automatically for dark colours; on a
   Canvas, draw them in the SOFT or SOLID pass, not GLOW.
8. **Respect the world.** Rings lie on their physical plane (the ground, or across the blast), not facing the
   camera. Things follow the terrain (the Tempest follows the ground's height). Face the way a body is really
   travelling, not where it looks: `FlightVisualGeometry.travel(player)` works for other players too. A body
   moving several blocks a tick needs its culling widened along its travel (`FlightRenderBoundsMixin`), or it
   vanishes at the frame's edge. Fade a trail where it passes
   the camera, so a player's own view isn't striped by it (`RewindArt`'s `clear`).
9. **Be deterministic.** A set piece is a pure function of its age and seed. Use `Canvas.hash(seed, i)`, never
   `Random`, so every viewer (and a player arriving late) sees the same thing.
10. **Show restraint.** Prefer fewer, larger, better-shaped forms. Thin out decoration when the player's
    particle setting is reduced, and keep only the main cue on Minimal. Stay inside the budgets: 1200
    lightning strokes, 96 live set pieces, and 768 custom particles.
11. **Let gameplay and drawing share a shape.** When the server has a physical shape (a tornado wall, a cone,
    a radius), put its maths in a common class both sides use, as `TornadoShape` does. Then the picture never
    lies about what will hit you.

## 4. The toolbox: which layer to use

| Need | Use | Examples |
| --- | --- | --- |
| A quick strike, ring, bolt or crack (under about a second) | `AbilityEffects.send(level, Kind, origin, target, scale)` → a case in the power's `*Effects.play` → `LightningRenderer.stroke` / `star` | Quake's rings, time's clock, speed trail |
| A staged animation with a fixed length (a few seconds) | **Set piece**: `Piece` + `SetPiecePayload.send` → `PieceArt` → a method in a `*Art` class drawing on `Canvas` | stop gust, Overrun depart/arrive, Matter rise, Starfall |
| Something with no fixed length that follows state | A client state mirror (fed by its own payload) drawn every frame from `drawLive(Canvas)` | Cyclone and tornado, Overrun wake, rewind trail, Flight's wind round charging and diving flyers (`FlightArt.drawLive`) |
| An animation timed by a state change | Bit flags in the power's visual-state payload; the client stamps the time a flag changes and animations read the phase's age | Flight's `FlightVisualStatePayload` flags and `ClientFlightVisualState.phaseAge` |
| The player's body | `ArmPose` + `PowerArmPoses` (third person) + `FirstPersonArmGeometry` (first person), sent with `ArmPosePayload.show` | Wind Arms, Flight's coil, brace, roll, clap and cast, Quake strain, cryo lance |
| The player seen elsewhere | A stand-in or afterimage rendering the real player model | Cyclone runner, afterimages, `TimeGhosts` |
| The view | A camera mixin (`TornadoCameraMixin` + `TempestCamera`) | tornado's pulled-back camera |

### LightningRenderer (short strokes)

- `stroke(points, startWidth, endWidth, color, alpha, life, delay)`: one tapering camera-facing ribbon along a
  polyline. Width is the half-width in blocks; `life` and `delay` are in ticks. Pass `soft = false` for a
  hard thin core.
- `star(center, size, color, alpha, life, delay)`: a four-pointed glint where something lands or leaves.
- `ElectroEffects.channel(a, b, random, jitter, depth)`: a jagged lightning or crack path.
- `ElectroEffects.later(ticks, runnable)`: stage a step of the animation later.

### Set pieces and Canvas (staged 3D geometry)

1. Add the piece to `styles/Piece.java` with its length in ticks: `MY_PIECE(40)`. **Append it to the end of
   the list** (ordinals go over the network), and bump the protocol version in `network/ModPayloads.java`
   whenever an enum that goes over the network changes.
2. Send it from the server: `SetPiecePayload.send(level, Piece.MY_PIECE, origin, target, scale, boundEntityOrNull)`.
   With a bound entity, the piece can follow it (`a.at(c)`, `a.bound(c)`). For something held, re-send a
   short piece every ten ticks, fading in and out over two, so the copies hand over without a seam (Wind Arms).
3. Draw it with a case in `PieceArt` → `static void myPiece(Canvas c, Active a, float t)`, where `t` is its age
   in ticks (with the partial tick).
4. Draw by pass, because each pass has its own blending:
   - `c.solid()`: lit, opaque things (debris boxes, shards, stone). Use `c.lit(...)` for sun shading.
   - `c.soft()`: smoke, dust, wind, cloud billows (`c.cloud(..., Shades.smoke(c, DUST, 0))`), and dark strokes.
   - `c.glow()`: additive light (lightning, hot cores, energy rings, `c.glow(at, radius, col, alpha)` halos).
5. Primitives: `ribbon` (along points, with per-point width and alpha), `line`, `bolt`, `helix`, `ring` (on any
   axis, with a partial arc), `disc`, `spoke`, `shell`/`sphere`, `tube`, `revolve` (a lathe profile),
   `shard`, `box`, `cloud` (lumpy billows), `tri`. Helpers: `Canvas.hash`, `mix`, `rgb`, `hue`, `smooth`.
6. Time inside a piece: use `t` for its own story and `c.level.getGameTime() + c.partial` for anything that
   must stay continuous across re-sent copies (a spin).

### Sound

Every set-piece moment wants a sound. Use vanilla sounds re-pitched (the gust is the breeze wind-charge burst
at 1.35 pitch plus a sweep at 0.7). For something bespoke, synthesise it (`SynthSound`, PCM streams): there's
no OGG encoder on this machine.

## 5. Anchoring to the body

These numbers come from the player model. Getting them wrong is the most common reason an animation looks off.

- **Shoulder joint:** 1.375 blocks above the feet standing, about 1.17 crouching (`e.isCrouching()`), and
  0.3125 to each side of the body's centre.
- **Arm length:** about 0.72 blocks. A hand pointed along `dir` is at `shoulder + dir * 0.72`.
- **The body's yaw is not the head's.** Arms hang off the body, which turns with
  `Mth.rotLerp(partial, yBodyRotO, yBodyRot)`. Aim the effect with the look vector, but place the shoulders
  from the body's yaw.
- **Sides:** with yaw in radians, forward is `(-sin yaw, 0, cos yaw)` and the player's right is
  `(-cos yaw, 0, -sin yaw)`.
- **Third-person arm angles** (`PowerArmPoses`): `aimX = -PI/2 + head.xRot` points an arm straight along the
  look, and `aimY = head.yRot`. Keep motion small and centred on the aim (Wind Arms circles ±0.3 rad round it).
  **Never spin an arm through a full turn**: the first Wind Arms windmilled and the arms passed through the
  body and head.
- **First person** (`FirstPersonArmGeometry`) is a separate pose; write one for every new `ArmPose`, or it
  falls back to a generic hold. Keep the hands in view (about ±0.36 to the sides, −0.36 down, −0.3 forward).
- New `ArmPose` values go **at the end** of the enum (sent by ordinal).

## 6. Recipe for a new ability's animation

1. **Write the beat sheet first**, in a few lines: what the player sees during the wind-up, the release and
   the aftermath, which body part it comes from, and which of the power's colours each part uses. Check it
   fits the power's one idea (section 2).
2. **Pick the layer** (section 4). Most new abilities are a set piece plus an arm pose.
3. **Build the server side to match**: the same origin, direction and size as the gameplay, and the same
   shared shape if there is one.
4. **Draw it** following the principles: three-layer glows, staggered timing, fades, dark strokes in the
   right pass, a deterministic seed.
5. **Pose the arms** (third and first person) so the hands are where the effect starts.
6. **Add the sound.**
7. **Photograph it** (section 7), fix what looks wrong, and photograph it again.
8. **Document it** in the power's doc under its look-and-feel section, in plain words: what the player sees.

## 7. Checking your work

Game tests check gameplay; they can't show an animation. Every visual must be seen in a real client:

- **Preview scripts** live in `tools/*-preview.init.gradle`, with drivers in `src/uitest/java/.../uitest/`
  (copy `WindArmsPreview` or `ReworkPreview`). They make a fresh flat world, give the power, stage the scene,
  and save screenshots to `build/<name>-preview/screenshots/`.
  Run them with `./gradlew runClient --init-script tools/<name>-preview.init.gradle`.
- **Stage at least 50 blocks from spawn.** The bunker stands at spawn and its walls trap the player.
- **Shoot several angles and moments**: from the side (to judge the pose), from behind (what the player
  sees), from the front, and at the wind-up, the peak and the aftermath. Crop and enlarge to judge the hands.
- **Don't film a moving player through another entity.** `mc.setCameraEntity(otherEntity)` stops the local
  player sending its position, so the server never sees them move (the first Wind Arms lift preview climbed
  forever because of this). Film movement from the player's own third-person camera.
- **Hide the HUD** (`mc.options.hideGui = true`) once the world has loaded, so the shots are clean.
- Log numbers that matter (heights, orbit speeds) from both client and server while previewing.
- Show the user the screenshots, not a description.

## 8. Before you call it done

- [ ] It belongs to the power's one visual idea and uses only the power's palette.
- [ ] It is made of shapes, with particles only as seasoning.
- [ ] Glowing lines have a halo, a body and a core.
- [ ] It has a wind-up, a release and an aftermath; things travel instead of appearing at once.
- [ ] Every element fades in and out; nothing pops.
- [ ] The effect starts from the hand or body part the pose points with, in third and first person.
- [ ] Dark parts (cracks, soot) are drawn with normal blending.
- [ ] It reads in daylight and at night, from the side, from behind, from the front and in first person.
- [ ] It is deterministic (seeded hashes), so every viewer sees the same.
- [ ] It thins out on reduced particle settings and stays inside the budgets.
- [ ] Network enums were appended at the end and the protocol version was bumped.
- [ ] It has a sound.
- [ ] It was photographed in a preview at least 50 blocks from spawn, and the screenshots looked right.
- [ ] The power's doc describes what the player sees.
