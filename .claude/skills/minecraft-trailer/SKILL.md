---
name: minecraft-trailer
description: Film and cut trailers, teasers and cinematic videos of the Heroes Evolved mod - shots filmed in the real game with moving cameras, slow motion and motion blur, graded and cut in Blender with title cards and an original score, rendered to MP4. Use this whenever the task involves a trailer, teaser, showcase or promo video, a cinematic, b-roll, filming a power, boss or place in motion, a camera move or fly-through, slow motion, a colour grade, title cards, or music for a video, even if the user just says "make a video of X", "add a shot of Y" or "film the new boss".
---

# Filming trailers for Heroes Evolved

A trailer here is **filmed in the game, never generated**: a driver stages each shot in a real world, a moving camera
films it while the game runs slowed down, a recorder blends the frames into 24 fps video with motion blur, and Blender
grades and cuts them with cards, a title and an original score. The plan is the storyboard,
[docs/trailer/storyboard.md](../../../docs/trailer/storyboard.md). The lessons behind every rule below, with the logs
that proved them, are in [docs/agents/previews.md](../../../docs/agents/previews.md), "Filming the trailer": read that
section before changing the camera, the recorder or the runner.

## The pieces

| File | What it does |
| --- | --- |
| `src/uitest/.../trailer/TrailerPreview` | The runner: a normal world, the scouted stage, each chosen shot in turn (reset, power, clocks in step, cast, roll, cut) |
| `trailer/Shots` | The catalog, in storyboard order. **A new shot is one entry here** |
| `trailer/Shot` | The base: `cast`, `camera`, `speed`, `act` (server ticks), `watch` (client ticks), `follow`, `near`; `slow` and `arc` helpers |
| `trailer/SignatureShot` | A power's signature set piece, staged as `uitest/SignaturesPreview` stages it |
| `trailer/UltimateShot` | A power's Offense Ultimate, staged as `uitest/UltimatePreview` stages it, filmed through the Ultimate's own camera |
| `trailer/FirebendingShot` | A hand-made fight: actors that walk in, a fighter who turns to them, strikes on beats |
| `trailer/StoryShots` | The story's shots in their own places: the Last Day, the bunker, Odessa, a real village, Level 5, the Collection, the hilltop (Wither, Still World, an awakening), the End |
| `src/uitest/.../endgame/TrailerEnd` | The End for the finale (the Seal's moves and the Inheritance are package-private, so it lives in that package) |
| `trailer/Stage` | The place: the ground, actors (`puppet`), the shot frame (`at`, `place`, `aim`), `mark`, `clear` |
| `trailer/FilmCamera` | Keyed moves (`Move.of`, `Move.orbit`), following, ground clearance, line of sight, `shake`, `widen` |
| `trailer/FilmRecorder` | Captures every frame, blends 24 fps with a half-frame shutter, raw RGB to `build/trailer-preview/film/<shot>/` |
| `trailer/Scout` | Finds a dry, open hilltop facing the sunset from the world generator alone |
| `tools/trailer-preview.init.gradle` | Runs it: `-Pshots=epicenter,ballista`, `-Pseed`, `-Prate` |
| `tools/trailer/cut.py` | Blender: grades frames, lays out an edit file, writes the cue sheet and score, renders the MP4 |
| `tools/trailer/compose.py` | The trailer's score, composed: an arranger per part on a beat grid, cues, the mod's sounds over the cuts, ducking, a hall per family, mastering to -14 LUFS (`"score": "orchestra"`) |
| `tools/trailer/orchestra.py` | A sampler over VSCO 2 CE (CC0) in `build/vsco2`: strings, brass, harp, glock, piano, timpani, bass and tenor drums |
| `tools/trailer/score.py` | Our own synthesis: braams, booms, risers, ticks, filters; and the older all-synth score from a cue sheet (the default when an edit has no `"score"`) |
| `references/trailer-music.md` | **Read before writing music:** researched trailer practice (structure, harmony, orchestration, percussion, mixing) and where each rule lives in `compose.py` |
| `tools/trailer/analyze.py` | Measures a music track: tempo and every beat, bars, hits, quiet spans, key, each bar's level per stem; writes `<track>.json` for the cut to snap to |
| `tools/trailer/stills.py` | Frames of a rendered MP4 as PNGs, to look at the cut itself |
| `tools/trailer/contact.py` | A contact sheet: chosen raw frames of many shots tiled into one PNG (Blender's Python) |
| `tools/trailer/*.json` | Edit files: `trailer.json` (the whole trailer), `trailer-suno.json` (the same cut to a Suno track), `powers.json` (all twenty powers), `teaser.json` (nine shots), `test-shot.json` (one shot) |

## Adding a shot

1. **Find how the scene is already staged.** Every power, boss and place has a preview driver (the table at the end of
   previews.md). Copy its staging (the power, the skill node, the call that starts the move, where foes stand, the tick
   of each beat), never its camera: previews hold still, a trailer moves.
2. **Write it in the shot frame.** `Stage.at(dx, dy, dz)` is the fighter's frame: they stand at the origin facing +Z,
   as every preview stages scenes, and the stage turns that toward the sunset. A signature is one `SignatureShot` entry
   with the preview's numbers unchanged. A class Ultimate is one `UltimateShot` entry (power and mechanic): it returns
   no camera move, so the Ultimate's keyed cinematic camera (`client/UltimateCamera`) directs, and its own letterbox
   sits inside the grade's. Anything else is a small `Shot` subclass like `FirebendingShot`.
3. **Give every beat a marker.** `Stage.mark("impact")` on the tick the music should hit; the edit turns markers into
   cues. Put strikes and presses in `act` (server ticks) and wrap each in `PreviewQuiet.check`, so a refused move fails
   the run instead of filming a fighter doing nothing.
4. **Film only that shot:** `./gradlew runClient --init-script tools/trailer-preview.init.gradle -Pshots=<name>`
   (about four minutes, most of it loading; the scout's pick is kept per seed). Run it in the background.
5. **Read the log before the pictures:** `TRAILER_STEADY` before each roll (under a second behind), `TRAILER PASS
   <shot>` with 0 repeated, no `FAIL`, and every `TRAILER_MARK` on the frame you planned.
6. **Look at the frames**: a contact sheet of the build-up and the impact of each shot
   (`contact.py build/trailer/contact.png 2 13-epicenter:40 13-epicenter:58 ...`, frame numbers from `shot.txt`), or
   the stills, one a second, in `build/trailer-preview/screenshots/<shot>-*.png`. Is the fighter in frame, is the
   action visible, is a foe standing in front of it? Fix and film again. Then cut it (below) and look at the MP4's
   stills too.
7. **Update the storyboard's Progress** with what is filmed and which frames to use.

## Camera

- **Moves:** `Move.orbit` (an arc round a point, radius, height and field of view easing), `Shot.arc` (a slow swing
  through a preview's camera spot), `Move.of` with keys for anything else. Ease `BOTH` unless it should start or stop
  moving on a cut.
- **Follow what moves:** `followRate` 0.12 keeps an operator's lag; the aim keeps up three times faster; `near` stops
  the camera closing on the subject. A fixed move (`followRate` 0) still keeps its subject's feet in sight.
- **Framing that worked:** a fighter at 4.7 to 3.5 blocks, field of view 44 to 38, low (1.3 to 1.6 up); a signature at
  1.15 to 0.95 of its preview's camera distance, field of view 58 to 50, swinging 20 to 26 degrees.
- **The letterbox keeps 74% of the height.** At 38 degrees only about 1.8 blocks fit: `FilmCamera.widen` for a jump or
  a flip, and leave headroom for anything that rises.
- **To see a face, end on the foes' side**: a fighter faces their foes, so a camera behind them only ever sees a back.
- `FilmCamera.shake` on impacts, small (0.06 to 0.08).

## A shot in its own place

A story shot (a `StoryShots.Place`, or any `Shot` overriding these) goes somewhere other than the hilltop:

1. `stage(p)` goes there and starts what builds or opens it (`BunkerService.ensureBuilt`, `OdessaRuins.get`,
   `LevelFive.descend`, `CollectionArena.open`, `PrologueService.join`, `TrailerEnd.stage`, `Village.go`). It runs at
   20 ticks a second.
2. `ready(p)` says when it is there: a dimension reached, a city `complete()`, an arena open, a story phase's `age()`
   reached. Work out camera spots here or in `cast` into the place's `a`, `b`, `c`, `d`, which the client reads at the
   roll. `settle()` is the least wait. Set `impossible` if the place cannot exist, and the shot is skipped.
3. `cast(p)` sets the scene just before the roll; `act` times its beats; `leave(p)` cleans up after the cut (end an
   arena, `ascend`, `EndBattle.reset`, cancel a storm).
4. Use a fixed `dolly` camera and return null from `follow`. The ground lift reads the surface heightmap and would put a
   camera underground or indoors through the ceiling.

What each place needed is in previews.md under "What each place needed": structures on for villages, the bunker's
power started, Creative in the End, the Last Day's phases. Read it before staging a new one.

## Time, light and speed

- **The game films at 1.5 ticks a second**, the same from the first shot to the last; never change it mid-shot.
- **Slow motion is the playback speed**, `speed(tick)` with `Shot.slow(tick, from, to, 0.3 to 0.5)` round the impact;
  the camera slows with it. Keep real-time stretches short: a trailer shot is two or three seconds on screen.
- **Time of day per shot** (`Shots.SUNSET`, `Shots.DUSK`): sunset puts the sun behind the action when the camera looks
  west; dusk makes glowing powers read. Night is too dark for vanilla lighting.

## Cutting

```
"C:/Program Files/Blender Foundation/Blender 5.0/blender.exe" -b --factory-startup -P tools/trailer/cut.py -- tools/trailer/teaser.json
"C:/Program Files/Blender Foundation/Blender 5.0/blender.exe" -b --factory-startup -P tools/trailer/stills.py -- build/trailer/teaser.mp4 build/trailer/stills 40 120 200
```

An edit file lists the timeline in order. `{"card": "TEXT", "seconds": 2}` is a line on black.
`{"shot": "<name>", "from": 40, "to": 95, "fade_in": 6, "fade_out": 6, "cues": {"impact": "braam"}}` is a stretch of a
shot. Its frame numbers are the shot's own (its `shot.txt` lists the markers). A cue is `braam` (the big one), `hit` or
`{"kind": "braam", "shift": 6}` to land on the frame the impact shows (also `drop`, the hit as stopped time ends; a card may carry `"cue": "freeze"` to cut the music into stopped time). Add `"look": {"exposure": 1.05}` to adjust the
grade (also `balance`, `glow`, `contrast`, `saturation`) and `"regrade": true` after changing it. A `{"title": ...,
"sub": ..., "small": ..., "seconds": 4.5}` item gets the biggest hit. `"music": "<file>"` uses a track instead of the
generated score, such as the mod's own `src/main/resources/assets/heroesevolved/sounds/music/main_menu.ogg`. Cutting
grades only new frames: about two minutes for twenty seconds.

**Music is ours.** The score is written here and played on public-domain samples (VSCO 2 CE, CC0) and our own
synthesis. The user's reference library ([docs/agents/audio.md](../../../docs/agents/audio.md)) may guide the mood,
never the sound itself: no copied melody and no sample of it in a video. A licensed track the user supplies (theirs,
or one they made with a music service they subscribe to) is fine.

## Scoring

`trailer.json` has `"bpm": 96` and `"score": "orchestra"`. At 96 bpm a beat is exactly 15 frames at 24 fps.

- **The edit is cut to the beat.** `cut.py` stretches every card and title to whole beats and slides each shot's `from`
  so its first cue lands on a beat (its length snaps to whole beats too). Shots without cues just snap their length.
  A stretch grows by whole beats until every cue has 8 frames after it, because rounding down once cut an impact off
  and played its hit over the next shot. Leave a cue at least half a second from the end of `to` anyway.
- **Parts say what the music does from that item on:** `"part": "powers"` names an arranger in `compose.py`
  (prologue, light, years, bunker, odessa, awaken, powers, noticed, hunt, collects, finale, title, stinger).
  `"part": "finale@resume"` starts it at the shot's `resume` marker instead of its first frame.
- **Sound effects:** `"sfx": [{"at": "impact", "shift": 3, "sound": "power/quake/heavy_1", "gain": 0.8}]` lays one
  of the mod's own sounds (a path under `assets/heroesevolved/sounds/`, no `.ogg`) at a marker or a frame number of
  the shot (or seconds into a card). Every power shot carries its `heavy_1` on its impact; the music ducks under them.
- **Compose without rendering:** add `--score-only` after the edit file. It writes `build/trailer/work/score.wav` in
  about 80 seconds. Then check its shape by section: RMS per section in dB. The arc that works: the cold open about
  -35, the first climax -14, a dip to -24, a montage that grows over its three tiers, a darker hunt at -25, stopped
  time about -40, and the finale and title on top at -13. An agent can't hear it, so the user must listen.
- **Changing the music:** follow `references/trailer-music.md`. A new section is a new arranger method plus a
  `"part"`. Build by adding layers rather than turning up. Keep the choir, the three-octave motif and the full low
  brass for the finale. Leave the beat before a braam without bass. Velocity picks the sample's dynamic layer, so a
  quiet part needs higher velocities than you'd guess: anything under about 0.4 is nearly inaudible after mastering.
- **The samples:** `build/vsco2` is a sparse clone of `https://github.com/sgossner/VSCO-2-CE` (about 1 GB, CC0,
  never shipped or committed). Fetch it once with the user's permission: `git clone --filter=blob:none --no-checkout`
  it, then `git sparse-checkout set --no-cone` with the folders `orchestra.py`'s `KIT` reads (each as `/<path>/`,
  plus `/Keys/Upright Piano/` and `/LICENSE`), then `git checkout`. In Git Bash, set `MSYS_NO_PATHCONV=1` first, or
  it turns `/Strings/...` into a Windows path and nothing matches.
- **The library numbers octaves one lower** for most instruments: `orchestra.py` measures each instrument's pitch and
  shifts it. A noisy style (the cello tremolo) can't be measured cleanly and is given `{"shift": 12}`. After adding an
  instrument, play a few notes and check them against their frequencies; every instrument is within 10 cents.

### A finished track instead (Suno or any other)

`tools/trailer/trailer-suno.json` cuts the trailer to a Suno track; copy it for another track.
[docs/trailer/suno-prompt.md](../../../docs/trailer/suno-prompt.md) is the prompt that made it. The user's two mixes
and their measured beats are committed in `tools/trailer/music/` (the repository is public, and the user chose to
publish them). Their stems stay in `build/trailer/music/` on the user's PC, because the zips are over GitHub's 100 MB
file limit. Without the stems folder, the cut plays the full mix, quieter wherever the edit asks for stems.

1. **Ask for WAV, and the stems and MIDI with tempo changes followed.** Unzip each stems archive into its own new
   folder.
2. **Measure it:** `python -I tools/trailer/analyze.py <track.wav> <stems-folder>` (Blender's Python). It prints the
   tempo, the hits, the quiet spans and a bar-by-bar table of the mix and every stem, and writes `<track>.json` with
   every beat. Read the table for the shape: which bars the drums, bass and choir come in, where the breaks are, where
   it is loudest.
3. **Map the trailer onto it.** A generated track runs two or three times the trailer's length, so the edit plays
   pieces of it. An item's `"track": {"from": <track seconds>}` plays from there at the item's start (a cut to black
   hides the join). `"at": "<marker>"` starts the piece at that marker instead, so a hit hides the join.
   `"exact": true` keeps the time as given rather than moving it to the nearest beat. Use it for a hit pushed half a
   beat ahead of its bar: check each join's real attack (`analyze.onset` round it), because Suno's big hits often come
   0.3 s early. If the track already reaches an exact hit within half a beat, the shot slides to it instead of
   jumping, so the attack never plays twice.
4. **Stems for moments, the full mix for the rest.** Suno's stems don't add back up to its mix (the difference is only
   6 dB down; the mix is mastered on its own), so the mix plays everywhere except where an item says
   `"stems": {"Strings": 0.8, "*": 0.1}`, until `"stems": "mix"`. `"freeze_stems"` thins stopped time (freeze to drop),
   and the beat before the title is always silent. `"music_db"` sets the track's level from an item on.
5. **The cut follows the track's measured beats,** not a fixed tempo: Suno's v2 drifted from 98.4 to 100 bpm, half a
   second off any grid. Every `TRAILER_TRACK` line prints a piece; check each join against where the piece before it
   had got to (a join should be a deliberate skip, or within a few hundredths of continuous).
6. **Measure the sections** as for the score, then render and check stills at every join and hit. Expect the montage
   to measure loud from its sixteen impacts whatever the music does.

Without `<track>.json`, `"bpm"` gives the grid and `"music_offset"` (seconds) starts the track further in.

## Machine notes

- Blender 5.0 is the only working Blender here (the 4.2 folder has no executable). Its video editor API is
  `sequence_editor.strips` and `new_effect(..., length=)`. **Adding a strip modifier from a script crashes it**, which
  is why the grade is numpy in `cut.py`.
- `score.py` runs on its own with Blender's Python (numpy is bundled):
  `"C:/Program Files/Blender Foundation/Blender 5.0/5.0/python/bin/python.exe" tools/trailer/score.py cues.json out.wav`.
  Check a score's shape with numbers (loudness every quarter second); an agent can't listen to it, so the user must.
- `compose.py` needs Blender to place the sound effects (it decodes OGG with Blender's `aud`); without it the effects
  are left out with a warning. The story-cue toolkit's soundfile and scipy are not in Blender's Python, so everything
  in `compose.py` is numpy alone: FFT convolution reverb, FFT EQ and the BS.1770 loudness meter.
- Frames are 6 MB each (raw RGB at 1920x1080): a shot is 1 to 2 GB under `build/trailer-preview/film/`, and graded
  PNGs are kept in `build/trailer/work/`. Delete old takes when the disk fills.
- The window is 1920x1080, so the primary display must be at least that.
