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
| `trailer/Stage` | The place: the ground, actors (`puppet`), the shot frame (`at`, `place`, `aim`), `mark`, `clear` |
| `trailer/FilmCamera` | Keyed moves (`Move.of`, `Move.orbit`), following, ground clearance, line of sight, `shake`, `widen` |
| `trailer/FilmRecorder` | Captures every frame, blends 24 fps with a half-frame shutter, raw RGB to `build/trailer-preview/film/<shot>/` |
| `trailer/Scout` | Finds a dry, open hilltop facing the sunset from the world generator alone |
| `tools/trailer-preview.init.gradle` | Runs it: `-Pshots=epicenter,ballista`, `-Pseed`, `-Prate` |
| `tools/trailer/cut.py` | Blender: grades frames, lays out an edit file, writes the cue sheet and score, renders the MP4 |
| `tools/trailer/score.py` | The original score: drone, pulse, risers, braams, hits, a title hit, from a cue sheet |
| `tools/trailer/stills.py` | Frames of a rendered MP4 as PNGs, to look at the cut itself |
| `tools/trailer/contact.py` | A contact sheet: chosen raw frames of many shots tiled into one PNG (Blender's Python) |
| `tools/trailer/*.json` | Edit files: `test-shot.json` (one shot), `teaser.json` (nine shots), `powers.json` (all twenty powers: its clip ranges are the frames worth using) |

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
`{"kind": "braam", "shift": 6}` to land on the frame the impact shows. Add `"look": {"exposure": 1.05}` to adjust the
grade (also `balance`, `glow`, `contrast`, `saturation`) and `"regrade": true` after changing it. A `{"title": ...,
"sub": ..., "small": ..., "seconds": 4.5}` item gets the biggest hit. `"music": "<file>"` uses a track instead of the
generated score, such as the mod's own `src/main/resources/assets/heroesevolved/sounds/music/main_menu.ogg`. Cutting
grades only new frames: about two minutes for twenty seconds.

**Music is ours.** The generated score is synthesized from nothing. The user's reference library
([docs/agents/audio.md](../../../docs/agents/audio.md)) may guide the mood, never the sound itself: no copied melody
and no sample of it in a video. A licensed track the user supplies is fine.

## Machine notes

- Blender 5.0 is the only working Blender here (the 4.2 folder has no executable). Its video editor API is
  `sequence_editor.strips` and `new_effect(..., length=)`. **Adding a strip modifier from a script crashes it**, which
  is why the grade is numpy in `cut.py`.
- `score.py` runs on its own with Blender's Python (numpy is bundled):
  `"C:/Program Files/Blender Foundation/Blender 5.0/5.0/python/bin/python.exe" tools/trailer/score.py cues.json out.wav`.
  Check a score's shape with numbers (loudness every quarter second); an agent can't listen to it, so the user must.
- Frames are 6 MB each (raw RGB at 1920x1080): a shot is 1 to 2 GB under `build/trailer-preview/film/`, and graded
  PNGs are kept in `build/trailer/work/`. Delete old takes when the disk fills.
- The window is 1920x1080, so the primary display must be at least that.
