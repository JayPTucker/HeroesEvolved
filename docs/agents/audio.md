# Audio: notes for agents

Power sounds, making OGG files, and where to look for long or live audio. Voice chat is in [powers/telepathy.md](powers/telepathy.md); boss music is in the bosses skill ([SKILL.md](../../.claude/skills/minecraft-bosses/SKILL.md)). What the player hears: [power-sounds.md](../power-sounds.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

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
