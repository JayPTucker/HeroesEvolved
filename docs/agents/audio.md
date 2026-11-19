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

## Reusable patterns from the power sounds

Every power got a palette of original synthesized cues in November 2026 (`sounds/PowerSounds`, `sounds/PowerCue`, made by
`tools/generate-power-audio.py`). What the player hears is in [docs/power-sounds.md](../../docs/power-sounds.md).

- **One hook gives every action a sound.** `AbilityUseService` calls `PowerSounds.used` after each press, follow-up,
  dismissal and release, so core moves, skill-tree actives and class abilities all sound without per-move code. Pick a
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
