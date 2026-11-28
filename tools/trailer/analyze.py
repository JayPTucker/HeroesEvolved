"""Measures a music track for the trailer: tempo and every beat, the bar lines, each bar's loudness (and each stem's, if
a folder of stems is given), the big hits, the silences and the key. Writes <track>.json beside the report so the edit
can snap cuts to the track's real beats.

    "C:/Program Files/Blender Foundation/Blender 5.0/5.0/python/bin/python.exe" -I tools/trailer/analyze.py track.wav [stems-folder]

Reads 16- or 24-bit WAV with numpy alone. An agent can't listen, so this is how it sees the shape of a track:
- Beats: an onset envelope (spectral flux on a log spectrogram, 100 frames a second), the tempo from its
  autocorrelation (weighted toward 70-130 bpm), then dynamic-programming beat tracking (Ellis 2007), so a track that
  drifts a little keeps its beats.
- Bars: of the four phases of the beats, the one whose beats carry the most low-end attack (kick, timpani, braams).
- Hits: where the level jumps 8 dB or more over the second and a half before it, loud against the whole track.
- Key: a chromagram against the Krumhansl-Kessler profiles.
"""
import json
import os
import sys
import wave

import numpy as np

HOP = 480  # 10 ms at 48 kHz; recomputed for other rates
FPS_ENV = 100


def read(path):
    with wave.open(path) as w:
        rate, width, ch, n = w.getframerate(), w.getsampwidth(), w.getnchannels(), w.getnframes()
        raw = w.readframes(n)
    if width == 2:
        x = np.frombuffer(raw, "<i2").astype(np.float32) / 32768
    elif width == 3:
        b = np.frombuffer(raw, np.uint8).reshape(-1, 3)
        v = (b[:, 0].astype(np.int32) | (b[:, 1].astype(np.int32) << 8) | (b[:, 2].astype(np.int32) << 16))
        v = np.where(v >= 1 << 23, v - (1 << 24), v)
        x = v.astype(np.float32) / (1 << 23)
    else:
        raise ValueError("unsupported sample width %d" % width)
    return x.reshape(-1, ch).mean(axis=1), rate


def spectrogram(x, rate, n_fft=2048):
    hop = int(rate / FPS_ENV)
    frames = 1 + (len(x) - n_fft) // hop
    win = np.hanning(n_fft).astype(np.float32)
    out = np.empty((frames, n_fft // 2 + 1), np.float32)
    for i in range(0, frames, 2048):
        idx = np.arange(i, min(frames, i + 2048))[:, None] * hop + np.arange(n_fft)
        out[i:i + len(idx)] = np.abs(np.fft.rfft(x[idx] * win, axis=1))
    return out, np.fft.rfftfreq(n_fft, 1 / rate)


def onset(spec, freqs, lo=30, hi=16000):
    band = (freqs >= lo) & (freqs <= hi)
    s = np.log1p(100 * spec[:, band])
    flux = np.maximum(0, np.diff(s, axis=0)).sum(axis=1)
    flux = np.concatenate([[0], flux])
    # Take away the local mean so a loud section isn't all onset.
    k = FPS_ENV
    c = np.cumsum(np.concatenate([[0], flux]))
    idx = np.arange(len(flux))
    lo_i, hi_i = np.clip(idx - k // 2, 0, len(flux)), np.clip(idx + k // 2, 0, len(flux))
    flux = np.maximum(0, flux - (c[hi_i] - c[lo_i]) / np.maximum(1, hi_i - lo_i))
    return flux / (flux.std() + 1e-9)


def tempo(env):
    env = env - env.mean()
    ac = np.correlate(env, env, "full")[len(env) - 1:]
    lags = np.arange(len(ac))
    bpm = 60 * FPS_ENV / np.maximum(lags, 1)
    ok = (bpm >= 55) & (bpm <= 200)
    weight = np.exp(-.5 * (np.log2(np.maximum(bpm, 1) / 100) / .9) ** 2)
    score = np.where(ok, ac * weight, -np.inf)
    best = int(np.argmax(score))
    # Parabolic refinement of the peak.
    if 1 <= best < len(ac) - 1:
        a, b, c = ac[best - 1], ac[best], ac[best + 1]
        shift = .5 * (a - c) / (a - 2 * b + c) if (a - 2 * b + c) != 0 else 0
    else:
        shift = 0
    period = best + shift
    cands = sorted(((score[l], 60 * FPS_ENV / l) for l in range(2, len(ac)) if ok[l] and
                    score[l] >= score[l - 1] and score[l] >= score[l + 1]), reverse=True)[:4]
    return 60 * FPS_ENV / period, [round(c[1], 1) for c in cands]


def beats(env, bpm, tightness=120):
    """Ellis's dynamic-programming beat tracker: each beat is an onset whose distance from the one before is close to
    the period."""
    period = 60 * FPS_ENV / bpm
    n = len(env)
    score = env.astype(np.float64).copy()
    back = np.full(n, -1)
    lo, hi = int(round(period / 2)), int(round(period * 2))
    for t in range(lo, n):
        p = np.arange(max(0, t - hi), t - lo + 1)
        if not len(p):
            continue
        cost = -tightness * np.log((t - p) / period) ** 2
        cand = score[p] + cost
        j = int(np.argmax(cand))
        score[t] = env[t] + cand[j]
        back[t] = p[j]
    # Start from the best score in the last period, follow it back.
    t = int(np.argmax(score[n - int(period):])) + n - int(period)
    out = []
    while t >= 0:
        out.append(t)
        t = back[t]
    return np.array(out[::-1]) / FPS_ENV


def key_of(spec, freqs):
    band = (freqs > 55) & (freqs < 2000)
    pc = np.round(12 * np.log2(freqs[band] / 440) + 69).astype(int) % 12
    energy = spec[:, band].mean(axis=0) ** 2
    chroma = np.bincount(pc, weights=energy, minlength=12)
    major = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
    minor = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17])
    names = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
    best = []
    for r in range(12):
        for prof, mode in ((major, "major"), (minor, "minor")):
            best.append((np.corrcoef(chroma, np.roll(prof, r))[0, 1], names[r] + " " + mode))
    best.sort(reverse=True)
    return best[0][1], best[1][1], round(best[0][0], 2)


def level(x, rate, step=.05):
    n = int(rate * step)
    m = len(x) // n * n
    r = np.sqrt((x[:m].reshape(-1, n) ** 2).mean(axis=1))
    return 20 * np.log10(r + 1e-9)


def bar_levels(x, rate, bars, end):
    out = []
    edges = list(bars) + [end]
    for a, b in zip(edges[:-1], edges[1:]):
        seg = x[int(a * rate):int(b * rate)]
        out.append(float(20 * np.log10(np.sqrt((seg ** 2).mean()) + 1e-9)) if len(seg) else -120.0)
    return out


def main():
    track = sys.argv[1]
    stems = sys.argv[2] if len(sys.argv) > 2 else None
    x, rate = read(track)
    length = len(x) / rate
    spec, freqs = spectrogram(x, rate)
    env = onset(spec, freqs)
    bpm, cands = tempo(env)
    bt = beats(env, bpm)
    real_bpm = 60 / np.median(np.diff(bt))
    # Downbeats: the phase whose beats carry the most low attack.
    low = onset(spec, freqs, 30, 160)
    near = lambda t: low[max(0, int(t * FPS_ENV) - 2): int(t * FPS_ENV) + 3].max(initial=0)
    phase = int(np.argmax([sum(near(t) for t in bt[k::4]) for k in range(4)]))
    bars = bt[phase::4]
    if bars[0] > 60 / real_bpm * 2:  # bars before the first full one, counted back from it
        lead = np.arange(bars[0], 0, -4 * 60 / real_bpm)[1:][::-1]
        bars = np.concatenate([lead, bars])
    key = key_of(spec, freqs)

    lv = level(x, rate)
    top = np.percentile(lv, 99)
    hits = []
    w = int(1.5 / .05)
    for i in range(w, len(lv)):
        before = np.median(lv[i - w:i - 1])
        rise = lv[i] - before
        if rise >= 8 and lv[i] > top - 14 and lv[i] >= lv[max(0, i - 2):i + 3].max():
            t = i * .05
            if hits and t - hits[-1]["at"] < 1.0:
                if lv[i] > hits[-1]["db"]:
                    hits[-1] = {"at": t, "db": float(lv[i]), "rise": float(rise)}
                continue
            hits.append({"at": t, "db": float(lv[i]), "rise": float(rise)})
    for h in hits:
        h["beat"] = float(bt[np.argmin(np.abs(bt - h["at"]))])
        h["bar"] = int(np.argmin(np.abs(bars - h["at"]))) + 1
        h["on_bar"] = bool(np.min(np.abs(bars - h["at"])) < .12)
    quiet = []
    run = None
    for i, v in enumerate(lv):
        if v < top - 32:
            run = i if run is None else run
        elif run is not None:
            if (i - run) * .05 >= .25:
                quiet.append((run * .05, i * .05))
            run = None

    table = {"mix": bar_levels(x, rate, bars, length)}
    if stems:
        for f in sorted(os.listdir(stems)):
            if f.lower().endswith(".wav"):
                s, r = read(os.path.join(stems, f))
                table[os.path.splitext(f)[0].split(" ", 1)[-1]] = bar_levels(s, r, bars, length)
                del s

    print("TRACK %s: %.1f s, %d Hz" % (os.path.basename(track), length, rate))
    print("TEMPO %.2f bpm (beats' median), autocorrelation %.2f, candidates %s; %d beats, first at %.2f s"
          % (real_bpm, bpm, cands, len(bt), bt[0]))
    print("BARS %d, first downbeat %.2f s, bar = %.2f s" % (len(bars), bars[0], 4 * 60 / real_bpm))
    print("KEY %s (next %s, r=%.2f)" % key)
    print("HITS (time, level dB, rise dB, bar, on the bar line):")
    for h in hits:
        print("  %6.2f  %6.1f  +%4.1f  bar %3d %s" % (h["at"], h["db"], h["rise"], h["bar"], "*" if h["on_bar"] else ""))
    print("QUIET (32 dB under the loudest):", ", ".join("%.2f-%.2f" % q for q in quiet) or "none")
    names = list(table)
    short = [n[:6] for n in names]
    print("BAR   TIME  " + " ".join("%6s" % s for s in short))
    for i, t in enumerate(bars):
        row = " ".join("%6.0f" % max(-99, table[n][i]) for n in names)
        print("%3d %6.2f  %s" % (i + 1, t, row))
    out = os.path.splitext(track)[0] + ".json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"length": length, "bpm": real_bpm, "beats": [round(float(b), 3) for b in bt],
                   "bars": [round(float(b), 3) for b in bars], "key": key[0], "hits": hits,
                   "quiet": quiet, "levels": table}, f, indent=1)
    print("WROTE", out)


if __name__ == "__main__":
    main()
