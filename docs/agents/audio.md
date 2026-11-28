# Audio: notes for agents

Power sounds, making OGG files, and where to look for long or live audio. Voice chat is in [powers/telepathy.md](powers/telepathy.md); boss music is in the bosses skill ([SKILL.md](../../.claude/skills/minecraft-bosses/SKILL.md)). What the player hears: [power-sounds.md](../power-sounds.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Reference audio (the user's library)

The user keeps the audio to work from in **`C:\Users\jaypa\OneDrive\Desktop\Gemini Heroes Reborn Audio`** (outside the
repo, about 1.9 GB). It is the soundtrack and ability sounds extracted from the game *Gemini: Heroes Reborn* (score by
Wendy Melvoin and Lisa Coleman). `Track List.txt` describes it and `Extraction Inventory.csv` maps every file to its
source.

**It is a reference, never an asset.** It belongs to its owners: never copy, trim, resample, layer or encode any of it
into the mod, its tools' output or its tests. Listen to it and measure it, then make our own sound that answers the same
need: the same job, mood, length, tempo and rough spectrum, with our own synthesis and our own melody.

| Folder | Use it as the reference for |
| --- | --- |
| `Music/Gameplay Score` | a theme's states: each score bank has Exploration, Combat and sometimes Stealth versions of one piece, the model for a boss's phase loops and for music that changes with play |
| `Music/Individual Layers and Alternate Encodings` | how those states are built from stems that share a tempo and key, so one layer can come in or drop out at a phase change |
| `Music/Stingers and Transitions` | phase changes and arrivals (4 to 11 seconds) |
| `Music/Short Music Cues` | defeats, victories, story beats (3 to 9 seconds) |
| `Ability Sound Effects/Telekinesis` | Telekinesis: charge, hold loop, push, throw, impact, disperse, and catching bullets |
| `Ability Sound Effects/Time Slow`, `Time Travel` | Space-Time: a slowed-time loop and its release, travel, and the "time view" loop and release |
| `Ability Sound Effects/Tornado` | the shared tornado's loop and release (Superspeed, Flight's Tempest, Maelstrom) |
| `Ability Sound Effects/Additional Ability Events` | vortex pulls and pillars, fire slams, thrown projectiles and their explosions, shoulder impacts, a loud time travel |
| `Ability Sound Effects/Ability Feedback` | the "can't use that" cue (a refused press) |
| `Ability Sound Effects/Individual Source Sounds` | single unmixed layers, for picking apart how a mixed effect is built |

- **Measure, don't guess.** Read a file with soundfile from `build/audio-tools` (it reads FLAC and WAV) in a script run
  with `py -3 -I`, and write down its length, loudness, tempo, the bands that carry it and how it starts and decays, with
  a spectrogram next to one of ours. Design to those numbers.
- **Loops come in pairs.** The library's held sounds are a loop plus a release (Time Slow, Time View, Tornado, the
  Telekinesis hold); make held powers the same way, with the release playing when the drain stops.
- **Record provenance.** Note in the generator which reference a new sound answers and that nothing was taken from it.
  `tools/verify-skill-content.py` already checks the custom sounds' provenance.

## Reusable patterns from the story cues

(The Last Day's own music is in the next section.)

The Last Day's pods and the bunker's waking have four short music cues (`tools/generate-story-cues.py`, `ModSounds.STORY_*`,
played once by `IntroDirector.cue` on the music channel when a `TimeIntroPayload` stage arrives): `story_sealed`
(PROLOGUE_POD), `story_years` (PROLOGUE_YEARS, with the *103 years later* title), `story_ward` (PROLOGUE_WARD, sealed in the Helix Ward), and the waking's `story_wake_music`, a seamless 128-beat loop that `client/sound/BunkerMusic` fades in once the
player is out of the pod and the alarm is over (BUNKER_MUSIC) and fades away when `BunkerService.music` sees them leave
the bunker (BUNKER_EXIT, with the *What happened...?* advancement). The wake music is designed to the shape of the reference track the user picked (`Individual Layers and Alternate Encodings`,
Music_03: D minor, about 92 bpm, roots D-F-D-C, a hush at 35 to 55 s and its peak near 60 s) with our own chords and motif, and two swells inside it follow the user's Score 01 stingers: a rise to the peak at 1.5 to 2.5 s,
sung by a synthesized choir (`sung`: additive voices shaped by vowel formants, ~5.5 Hz vibrato) round C5 to E5, where
the references' voices sit (round vowels, most energy near 300 Hz).
- **A loop has no seam when its tail wraps round.** Render one loop plus a tail, add the tail onto the head (the last
  chord, the pulse and the reverb ring on into the start), make the loudness arc end where it begins, and skip the end
  fade (`finish(..., loop=True)`). Its length is a whole number of chords and beats (16 chords, 128 beats).
- **Music never starts under an alarm.** It waits for the alarm to end and fades in (`BunkerMusic`, 4 s), and fades out
  (5 s) rather than cutting.

- **Design to the library's numbers.** The library's stingers and short cues measure 3 to 10 seconds, -18 to -23 dBFS RMS,
  a peak one to three seconds in, a two to three second decay, mid-heavy (centroid 0.7 to 2 kHz), roots on D, A and E.
  The first renders came out 3 to 5 dB loud: normalising only the band above 150 Hz ignores a cue's low end, so the
  targets there sit about 3 dB under the full-band goal. Measure ours with the same script before shipping.
- **A thump's click can own the envelope.** `story_sealed` first peaked at 0.1 s with a 0.5 s decay and a 2.5 kHz centroid:
  the knock under its thump was louder than the pad. Keep the knock just audible and let the pad carry the peak.
- **Write long OGGs in blocks.** libsndfile's Vorbis encoder overflowed the stack (exit 0xC00000FD, no message) when
  handed the 80-second track in one `sf.write`; `finish` now writes 4096 frames at a time.
- **Music steps aside for a cue.** `IntroDirector.cue` stops the vanilla background track first, so a beat never plays
  under a song.

## Reusable patterns from the Last Day's music

The Last Day has two stems of one loop (`tools/generate-lastday-music.py`: `lastday_evening`, `lastday_run`), the crash
of every window bursting (`lastday_shatter`) and the ringing in your ears after it (`lastday_ringing`), played by
`client/sound/LastDayMusic` and `client/DazeView`. The user picked two layers of the library's Music_06 as the reference.

- **Stems that join at any moment are started together.** Both loops are exactly 96 beats (24 bars at 75 bpm, 76.8 s)
  on one chord chart; `LastDayMusic.start` plays both in the same tick, the fight's layer silent (`canStartSilent`, or a
  zero-volume sound never starts), and only its volume moves when the fight begins. Starting the second stem later would
  land it anywhere in the bar.
- **Measure ours with the reference's own script.** The first action layer measured a 206 Hz centroid against the
  reference's 442 (all drums and bass, almost nothing above 1 kHz); louder, brighter hats and ostinato and stronger brass
  brought it to 364 Hz with a similar band split. The bed matched at the first try (130 against 164 Hz, -21.7 against
  -21.8 dBFS). `finish` normalises the band above 150 Hz, so a bass-heavy bed reads 2 to 3 dB louder full-band than its
  target: aim the target that much lower.
- **Reverb with filters, not Python loops.** `fast_reverb` is the story cues' Schroeder room built from `lfilter` combs and
  allpasses: two 80-second stems render in about 30 seconds.
- **Muffling the world without touching settings.** See `DazeView` in [story-and-scenes.md](story-and-scenes.md): the
  ringing plays on the master channel, which the duck leaves alone, and so do the wave's rumble (`WaveRumble`) and the
  screams heard while out cold (`PanicSounds.dazed`).
- **Distortion from our own recordings, made live.** `PanicSounds.Dazed` reads the same CC0 crowd and scream clips as the
  panic, slowed to about two thirds with a wandering rate, low-passed at a drifting 600 to 900 Hz, through a big reverb,
  swelling with a 70 bpm double-thump pulse. `WaveRumble` is pure synthesis: a sub-bass sine rising from 32 to 48 Hz,
  low-passed noise for the ground, band-passed noise for the roar, and sparse rattles, all building to the moment of
  impact. Nothing from the reference library is in either.
- **A dimension change stops every sound** (`Minecraft.setLevel` calls `soundManager.stop()`). A cue that must finish
  plays before a teleport across dimensions, not across it.

## Reusable patterns from Sylar's theme

Sylar's boss theme (`tools/generate-sylar-music.py`: three phase loops, a stinger, a last chord and the record in
`sounds/music/sylar_*.ogg`, played by `client/sound/SylarMusic` and, for the record, a jukebox) is modelled on the
library's gameplay score states (Score 04's Exploration, Combat and Stealth, and the Combat states of Scores 06
and 08). What the player hears is in [sylar.md](../sylar.md#music).

- **Import the synthesis, don't copy it.** The generator loads `generate-story-cues.py` and `generate-lastday-music.py` as
  modules (both guard their `__main__`) for their voices, choir, drums, ostinato, brass, reverb and `finish`, and points
  `finish` at another folder by setting `cues.OUT`.
- **Phase loops are one chart.** All three loops are the same 24 bars (D minor, 68 bpm, 84.7 s) in three 8-bar
  sections, each adding layers to the last, so any can follow any; the music manager swaps them when the phase changes
  and the stinger covers the cut. The record joins eight bars of each, the stinger at the turns and the last chord,
  each part levelled first (`level_to`) so the joins match.
- **Measure more than loudness and brightness.** Beside length, tempo, key, RMS and centroid, compare a five-band
  split (sub, low, low-mid, 1 to 4 kHz, top), the percussive share (median-filter HPSS on the spectrogram), onsets a
  second under 200 Hz and over 3 kHz, crest factor and the 4-second loudness arc. The first theme matched the
  reference's tempo and key but had a third of its 1 to 4 kHz energy and half its high onsets; a kit (taiko-style drums
  with a bright slap, toms, claps, shaker, cymbal swells and crashes at the section turns, clockwork anvils) and
  brighter strings brought the action loops to 621 and 708 Hz centroids and 6.8 high onsets a second, inside the
  reference Combat states' range. The scripts are scratch work, not kept: rebuild them from this list.
- **A drum's long body reads as tone, not percussion.** Pushing a slow-decaying sine kick up lowered the percussive
  share; a shorter body with more skin and slap raised it. Pitched layers (strings, bass, choir) always count against it.
- **Check a spectrogram sheet** of ours stacked over a reference, with a peak lane marking samples over 0.75: it shows
  section crashes, gaps, clicks and clipping at a glance.
- **The game's music channel, not a sound.** `SelectMusicEvent` returns the phase's `Music` (min and max delay 0, replace
  current) while the local player is in the hall, and null (silence) before he wakes and after his last chord.

## Reusable patterns from the power sounds

Every power got a palette of original synthesized cues in November 2026 (`sounds/PowerSounds`, `sounds/PowerCue`, made by
`tools/generate-power-audio.py`). What the player hears is in [docs/power-sounds.md](../../docs/power-sounds.md).

- **One hook gives every action a sound.** `AbilityUseService` calls `PowerSounds.used` after each press, follow-up,
  dismissal and release, so core moves and skill-tree actives all sound without per-move code (a class's Ultimate plays its own cue as it opens). Pick a
  different cue in the tables at the top of `PowerSounds`, not at the call site.
- **Did the action already make a sound?** `mixin/ServerSoundCountMixin` counts every `ServerLevel.playSeededSound`;
  take `PowerSounds.mark()` before something runs and compare after. Space-Time, Sonic Resonance and Vibe (hand-made
  sounds) only get a cue for silence, and a release only plays when it did something.
- **Impacts from one event.** A `LivingDamageEvent.Post` hook plays the attacker's power's impact where the blow lands.
  Plain swings are told apart by `AttackEntityEvent` in the same tick (style strikes go through `StyleHits.hurt`, which
  fires none), vanilla projectiles by the direct entity's namespace, and damage over time by a 1.5 floor.
- **Making OGGs here.** `build/audio-tools` holds numpy, scipy and soundfile (`py -3 -m pip install --target
  build/audio-tools --no-deps scipy`), and soundfile writes Vorbis. Synthesize in Python, normalise loudness on the
  band above 150 Hz (sub-bass alone is silent on laptop speakers, so give thumps a mid-range knock), keep peaks under
  0.8 because Vorbis overshoots, and check the files with numbers and a spectrogram sheet before wiring them in.
- **Private cues.** A stealthy move's cue goes to its user alone as a `ClientboundSoundPacket` (`PowerSounds.play(...,
  alone)`).
- **A power with no palette is silent, and nothing used to say so.** Power Amplification was missing from
  `generate-power-audio.py` and `PowerCue.POWERS` until November 2026, so every Supercharge played no power cue.
  `PowerSoundsGameTests` now fails for any rollable power without a palette. A new power needs a recipe, its subtitles,
  an entry in `PowerCue.POWERS` (and `STYLED` if it fights hand to hand), and its hold-to-charge moves in
  `PowerSounds.CHARGES`. Run the generator for that power alone (`py -3 tools/generate-power-audio.py <power>`); it
  re-serialises `sounds.json` and moves the subtitle block, so check the change is only the new keys.

## Reusable patterns from the Wither's theme

`tools/generate-wither-music.py` ("The Hungry Cage": three phase loops, stinger, last chord, record) imports Sylar's kit
and orchestra (`generate-sylar-music.py`, guarded `__main__`) and adds bone rattles, chain clanks, a growling staccato
bass and a rising sub. What the player hears is in [wither.md](../wither.md#music-the-hungry-cage).

- **The first score failed on numbers anyone could have measured.** It measured a 137 Hz centroid, 78% of its energy at
  60 to 250 Hz, under 1% above 1 kHz and 25% percussion, and its three phases measured alike: a drone with thumps, which
  the user heard as "so bad". The library's Combat states measure a 310 to 860 Hz centroid, 5 to 10% at 1 to 4 kHz and
  34 to 44% percussion. The rebuild measures 406, 649 and 767 Hz for its three phases with 2 to 8% at 1 to 4 kHz.
  Measure every loop against a reference before wiring it in, and make each phase measurably different.
- **`finish` squashes anything that peaks over 0.78** with a tanh, so a drum-heavy loop normalised on the band above
  150 Hz came out 3 dB louder and flattened. Pick targets that leave every loop unlimited (peak under 0.75), stepping up
  a little per phase; the Wither's land at -21.0, -20.8 and -20.0 dBFS.
- **Hold the last chord for the moment it belongs to.** `WitherMusic` silences the fight when the death scene starts and
  plays the last chord at the detonation, so it rises out of the `DazeView` ringing instead of under the death throes.
- **Rebuilt to the user's own picks** (November 25, 2026): two `Music_06` layers for the fight (183300580, 432375702),
  a third for after it dies (1048779095) and Score 01's Stinger 03; the measurements are in the generator's docstring.
  The score moved from E at 100 bpm to A minor with a Phrygian B-flat at 75 bpm driven in sixteenths (the references'
  key and pulse, still the Wither's half-step hunger), 24 bars (76.8 s), and every loop takes the first layer's shape: a
  hush, an impact at bar 4, a build, full stops on the last beat of bars 15 and 19 (`stops()` gates the dry mix, so the
  reverb rings into the hole) and a rise back round. The second layer (one impact, then a throbbing drone) became the
  section-turn `impact` plus `drone_pulse`. Threefold Hunger measures 440 Hz centroid against the reference's 444, 62%
  against 60% at 60-250 Hz, 21% against 22% percussion; the stinger 541 Hz against 478, 67% against 74% at 250 Hz-1 kHz.
  `WitherMusic`'s bar is now 64 ticks.
- **A new aftermath loop** (`wither_aftermath`) plays from 130 ticks after the detonation (as the last chord fades) for as
  long as the player is in the Ossuary, fading in over five seconds, and gives way if a new fight starts. A sound's own
  `rise` in `WitherMusic.Stem` sets how fast it fades in.
- **A low pulse fools the band-limited levelling.** `finish` measures above 150 Hz; with a sixteenth-note bass under
  everything, the full-band loudness came out about 6 dB above the target and hit the limiter. Set the target from the
  first measured render (the Wither's are now -25.8, -23.9 and -23.4 for -22.3, -21.6 and -21.4 dBFS).
- **Slow low chords read as percussion and onsets** to the median-filter split and the flux counter: equal-tempered
  thirds and fifths beat a few times a second in the low band. The aftermath measures 22% percussion against the
  reference's 11% though it has no drums to speak of; judge it by the spectrogram's blocks, not that number.

