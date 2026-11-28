# A Suno track for the trailer

The trailer's own score is composed by `tools/trailer/compose.py`. This page is for trying an alternative made with
Suno (the user's subscription; the trailer is not for public release). The prompt follows the trailer's real structure:
96.9 seconds at 96 bpm, so a bar is 2.5 seconds. Suno doesn't keep exact timings, so make several versions and pick the
one whose sections fall closest to the times below.

## In Suno (Custom mode, Instrumental on)

**Style** (paste as is):

```
Epic cinematic trailer score, hybrid orchestral, 96 BPM, D minor, instrumental. Quiet piano and harp opening, rising
string tremolos, deep brass braams, spiccato string ostinato, taiko and timpani, French horn theme, dark choir, risers
and impacts. Builds in layers to a huge final climax that lifts into D major. Silence before the biggest hits.
```

**Exclude styles:** `vocals, lyrics, EDM, dubstep, electric guitar, pop drum kit, lo-fi`

**Lyrics** (the section tags; Suno reads these as structure, and Instrumental keeps them unsung):

```
[Intro: soft piano and harp, low strings, D minor, very quiet]
[Build: string tremolo rising, timpani roll]
[Hit: massive brass braam, then silence]
[Interlude: one low sustained note, slow strings, sparse]
[Theme: French horns state the melody, open strings, harp arpeggios]
[Build: cellos and basses start a pulsing eighth-note ostinato]
[Hit: brass braam]
[Main section: driving spiccato strings, impacts every bar, adding horns then taiko then low brass]
[Hit: biggest braam so far]
[Break: one deep hit, then silence]
[Dark section: menacing ostinato, low brass, choir, war drums, tension]
[Hit]
[Break: near silence, a thin high held tone, time standing still]
[Drop: full orchestra and choir, the horn melody in three octaves]
[Climax: lifts to D major, everything at once]
[Silence]
[Final hit: enormous chord, ringing out]
[Outro: lone piano, quiet and ominous]
[End]
```

## Where each section should fall

| Time | Section | On screen |
| --- | --- | --- |
| 0:00 | Intro | ODESSA card, the Last Day's street |
| 0:05 | Build | the light growing |
| 0:08.8 | Hit | the clash |
| 0:10.6 | Interlude | the blast, 103 YEARS LATER, the pods |
| 0:16.2 | Theme | Odessa from the air |
| 0:20.6 | Build | SOME OF US WOKE UP DIFFERENT. |
| 0:23.8 | Hit | the awakening |
| 0:26.2 | Main section | every power, a hit on each impact |
| 0:50.6 | Hit | Fission Star |
| 0:51.2 | Break | THE WORLD NOTICED. |
| 0:53.1 | Dark section | the convoy, wildfire, Starfall, Level 5, Sylar's Collection |
| 1:11.2 | Hit | the Wither breach |
| 1:13.1 | Break | stopped time |
| 1:18.8 | Drop | time resumes |
| 1:18.8 to 1:28 | Climax | the End finale |
| 1:28.2 | Silence | |
| 1:28.8 | Final hit | HEROES EVOLVED |
| 1:33.8 | Outro | the stinger |

## Bringing it in

Download each version as WAV, with its stems (and MIDI with tempo changes followed). The mixes go in
`tools/trailer/music/` (committed); the stems go in `build/trailer/music/`, unzipped into a folder per version, and stay
off GitHub (over its 100 MB file limit). The trailer skill's "A finished track instead" measures a track and cuts the
trailer to it; the mod's power sounds still play on every impact, with the music ducked under them.

**What came back (November 2026):** two versions of "The Final Braam", 218 s and 208 s. Neither kept the times above:
Suno wrote a 3.5-minute piece. v2 fits best: an intro with soft hits, a breakdown at 1:43 where the bass and drums
drop out, drums building from nothing, a second break at 2:07, its loudest bars at 3:12 and a final chord at 3:24.
`tools/trailer/trailer-suno.json` plays nine pieces of it. v1 has one break near its end, right before its final hit,
which can't serve as both stopped time and the title.
