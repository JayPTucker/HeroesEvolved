# Writing trailer music: what the score follows

Researched November 2026 for the trailer's score (`tools/trailer/compose.py`). The rules come from working trailer
composers and theory guides, cited below. Each comes with what it means in `compose.py`. The sources are practitioner
advice, not law: where they disagree (mixing frequencies especially), the numbers are starting points.

## Structure and pacing

- **Three acts that build: setup, escalation, climax.** The setup is about the first 20 to 30 seconds. Several
  climaxes are fine, but **the last must be clearly the biggest**. If everything is big, nothing is.
  *Here:* the montage (`powers`) keeps the choir, the three-octave motif and the full low brass back for the `finale`.
- **Build by adding instruments, not by turning up.** Pass a repeating rhythm round the orchestra and add a layer every
  few bars. *Here:* the montage has three tiers: strings over a D pedal; then horns, a moving bass and the bass drum;
  then low brass, the tenor drum's fills and the trumpet motif.
- **Transitions get the most care:** layered crescendos (string tremolo, a timpani roll, a noise riser), pitch rises
  and filter sweeps. *Here:* `swell()` before each bigger section.
- **The lead-in to a hit has almost no bass,** so the hit lands bigger. **The biggest hits get a fraction of a second
  of silence** first. **Nothing after the last big hit:** let the tail fade. *Here:* a beat with no low strings, low
  brass or drums before every braam, a full cut before the drop and the title, and a ringing title chord with nothing
  added.
- **The hit lands on the frame of the hero shot or the card.** *Here:* `cut.py` puts each shot's first cue on a beat
  (96 bpm is 15 frames a beat), and the cue sheet puts the hit on that frame.

## Harmony

- **Minor-key epic progressions:** i–VI–III–VII (Dm Bb F C) drives the powers. The Phrygian ♭II (Eb in D minor) darkens
  the hunt.
- **From tension to resolution at the climax:** suspended or dissonant chords resolving to the tonic, or lifting to
  **major**. A **chromatic mediant** gives the heroic lift: two chords whose roots are a third apart and share one tone,
  out of key, like Bb major to D major. *Here:* the finale's last pass is Bb, C, Bb, **D major**, and the title chord is
  D major with an added ninth. The stinger falls back to D minor, which leaves it ominous.
- **Close voicings for urgency, open voicings for size.** *Here:* the montage's chords are packed in the middle, and the
  finale and the title spread from the bass's D2 to the violins' high octave.

## Melody and orchestration

- **One motif, our own** (D A F E D C D), introduced quietly (piano in the prologue), stated by the **horns** (they
  carry trailer melodies), and finally in **three octaves**: trumpets, horns an octave below, trombones two below. A
  second statement moves to the relative major (F), which sounds like hope, before the title.
- **Short strings drive the rhythm** between big hits: spiccato ostinatos, accented on the downbeats.
- **A solo sample is not a section:** the library's horn, trombone, tuba and trumpet are single players, so a section
  part is played two or three times, a few milliseconds and cents apart and spread a little in the stereo field.

## Percussion

- **Taiko (here the bass drum) anchors the phrase:** its biggest hits are the phrase's downbeat and its half-point.
- **Accent by place in the bar** (the downbeat strongest, beat three next, offbeats lighter), not at random.
- **Offset stacked percussion by 10 to 25 ms** so the layers don't flatten into one click.
- **The faster the figure, the higher the drum:** 16ths on the tenor drum, 8ths on the timpani, quarters and the
  3-3-2 pulse on the bass drum.

## Mixing

- **Mud lives at 200 to 500 Hz** in a big ensemble. Cut it gently on the whole bus, and filter the low end out of the
  reverb returns, rather than high-passing every section, which thins the mix.
- **A reverb per family,** with a short pre-delay so string attacks speak before the hall. Use enough to give size,
  but no more than lets a hit's tail die before the next.
- **Pan the supporting parts wide** (high strings, pulses) and keep the centre for the bass and the melody.
- **A conservative limiter and gentle glue,** to YouTube's -14 LUFS with a -1 dB ceiling.

## Sources

- [UJAM, The Secret of Every Trailer Soundtrack](https://www.ujam.com/tutorials/secrets-of-trailer-soundtracks/):
  building by layers, transitions, silence and no bass before the hit, a bigger last climax.
- [Epicomposer, trailer music production tips](https://epicomposer.com/trailer-music-production-tips/): voicings, horns
  on the melody, percussion offsets, panning, glue and limiting.
- [Tonal Chaos, What Makes Great Trailer Music](https://tonalchaos.pages.dev/blog/what-makes-great-trailer-music/):
  the three-act arc, tension to resolution, landing the hit on the frame.
- [Hooktheory, cinematic chord progressions](https://www.hooktheory.com/blog/cinematic-chord-progressions/) and
  [LANDR, 8 cinematic chord progressions](https://blog.landr.com/cinematic-chord-progressions): borrowed chords, minor
  to major lifts.
- [Music Theory for Guitar, chromatic mediants](https://www.musictheoryforguitar.com/Chromatic-Mediant-Progressions-Guitar.html):
  roots a third apart sharing one tone.
- [MusicRadar, programming a cinematic drum part](https://www.musicradar.com/how-to/how-to-program-a-pounding-cinematic-drum-part):
  accenting the downbeats, varying velocities.
- [Soundfly, mixing orchestral music](https://flypaper.soundfly.com/produce/top-five-considerations-when-mixing-orchestral-music/)
  and [Bradford College, mixing orchestral instruments](https://digitalspace.bradfordcollege.ac.uk/10626277/?p=10011):
  the low-mid build-up, filtered reverb.
