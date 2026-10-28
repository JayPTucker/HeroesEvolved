"""Generate the mod's painting canvases (standard library only), 16 pixels per block.

Run: python3 tools/generate-paintings.py
Writes assets/heroesevolved/textures/painting/<name>.png. Each canvas is painted from a few shapes, then
given brush texture: short horizontal strokes of slightly varied color, so they read as oil paint, not pixel art.

  explosion  6x4  Isaac Mendez's painting of the man who exploded over the city.
  sleeper    4x3  Isaac's last canvas: someone waking in a glass pod under a red light.
  bennets    2x2  A family photo from the Bennet house: Noah, Sandra, Claire, Lyle and Mr. Muggles.
  eclipse    3x2  The black sun over the plains, the night the powers woke.
"""
import math
import pathlib
import struct
import zlib

OUT = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/heroesevolved/textures/painting"


def png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in (*p, 255)) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def noise(x, y, seed=0):
    """Deterministic value in [0, 1)."""
    n = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65536


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def gradient(stops, t):
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return mix(c0, c1, (t - t0) / (t1 - t0) if t1 > t0 else 0)
    return stops[-1][1]


def brush(canvas, seed, strength=14, stroke=3):
    """Strokes: each run of `stroke` pixels in a row shares one brightness shift, so the paint has grain."""
    h, w = len(canvas), len(canvas[0])
    out = []
    for y in range(h):
        row = []
        for x in range(w):
            shift = (noise(x // stroke + (y % 2), y, seed) - 0.5) * 2 * strength
            fine = (noise(x, y, seed + 7) - 0.5) * strength * 0.4
            row.append(tuple(int(max(0, min(255, c + shift + fine))) for c in canvas[y][x]))
        out.append(row)
    return out


def frame(canvas, color=(52, 34, 22), light=(92, 64, 40)):
    """A one-pixel wooden frame with a highlight on the top and left."""
    h, w = len(canvas), len(canvas[0])
    for x in range(w):
        canvas[0][x], canvas[h - 1][x] = light, color
    for y in range(h):
        canvas[y][0], canvas[y][w - 1] = light, color
    return canvas


# ------------------------------------------------------------------ the explosion
def explosion():
    W, H = 96, 64
    cx, cy = 50, 27
    sky = [(0, (22, 12, 40)), (0.35, (70, 18, 46)), (0.62, (170, 46, 30)), (0.8, (226, 110, 36)), (1, (240, 150, 60))]
    c = [[gradient(sky, y / (H - 1)) for x in range(W)] for y in range(H)]
    # the blast: rings with ragged edges, brighter toward the core
    for y in range(H):
        for x in range(W):
            dx, dy = x - cx, (y - cy) * 1.15
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            ragged = 3.5 * math.sin(a * 7 + 1.3) + 2.5 * math.sin(a * 13) + 3 * (noise(int(a * 20), 0, 3) - 0.5)
            if r < 30 + ragged:
                t = r / (30 + ragged)
                col = gradient([(0, (255, 255, 236)), (0.18, (255, 240, 150)), (0.4, (255, 190, 60)),
                                (0.7, (238, 100, 30)), (0.92, (160, 40, 24)), (1, (90, 24, 30))], t)
                c[y][x] = mix(c[y][x], col, 1.0 if t < 0.85 else (1 - t) / 0.15 + 0.2)
            # rays of light across the sky
            elif abs(math.sin(a * 9)) > 0.985 and r < 46:
                c[y][x] = mix(c[y][x], (255, 200, 120), 0.45 * (1 - r / 46))
            # smoke ring at the top of the blast
            if 22 < r < 34 and dy < -8 and noise(x // 2, y // 2, 11) > 0.45:
                c[y][x] = mix(c[y][x], (60, 40, 44), 0.55)
    # the man at the center, arms out
    man = ["..#..", "..#..", "#####", ".###.", "..#..", "..#..", ".#.#.", ".#.#.", "#...#"]
    for j, row in enumerate(man):
        for i, ch in enumerate(row):
            if ch == "#":
                c[cy - 5 + j][cx - 2 + i] = (24, 14, 18)
    # the skyline: buildings with lit windows, rim-lit by the blast on their inner edges
    x = 1
    k = 0
    while x < W - 1:
        bw = 5 + int(noise(k, 1, 5) * 7)
        top = 42 + int(noise(k, 2, 5) * 12)
        if 34 < x < 40:
            top = 30          # the tall tower with a spire
        for bx in range(x, min(W - 1, x + bw)):
            for by in range(top, H):
                c[by][bx] = (16, 12, 24)
                near = bx == x and x > cx or bx == x + bw - 1 and x + bw < cx
                if near:
                    c[by][bx] = (190, 80, 40)
                elif by > top + 1 and (bx - x) % 2 == 1 and (by - top) % 3 == 1 and noise(bx, by, 9) > 0.55:
                    c[by][bx] = (240, 200, 110)
        if 34 < x < 40:
            for by in range(22, 30):
                c[by][x + bw // 2] = (16, 12, 24)
        x += bw + (1 if noise(k, 3, 5) > 0.6 else 0)
        k += 1
    # reflection on the river at the very bottom
    for y in range(H - 4, H):
        for x in range(W):
            if noise(x // 3, y, 21) > 0.5:
                c[y][x] = mix(c[y][x], (220, 120, 50), 0.35)
    c = brush(c, 1, strength=10)
    # Isaac's signature: a small white "IM" bottom right
    for (i, j) in ((0, 0), (0, 1), (0, 2), (2, 0), (2, 1), (2, 2), (3, 0), (4, 1), (5, 0), (6, 0), (6, 1), (6, 2)):
        c[H - 6 + j][W - 11 + i] = (236, 228, 210)
    return frame(c)


# ------------------------------------------------------------------ the sleeper
def sleeper():
    W, H = 64, 48
    cx = 32
    c = [[gradient([(0, (10, 22, 30)), (0.7, (18, 40, 48)), (1, (12, 26, 32))], y / (H - 1)) for x in range(W)] for y in range(H)]
    # red alarm glow from the lamp above
    for y in range(H):
        for x in range(W):
            r = math.hypot(x - cx, (y - 6) * 1.3)
            if r < 30:
                c[y][x] = mix(c[y][x], (170, 30, 36), 0.55 * (1 - r / 30) ** 1.4)
    # other pods fading into the dark either side
    for px, scale in ((8, 0.35), (18, 0.55), (46, 0.55), (56, 0.35)):
        for y in range(16, 42):
            for x in range(px - 3, px + 4):
                edge = abs(x - px) == 3
                c[y][x] = mix(c[y][x], (90, 150, 160) if edge else (40, 80, 90), scale)
    # the central pod: metal caps, glass tube, frost, and the sleeper inside
    for y in range(8, 44):
        for x in range(cx - 9, cx + 10):
            if y < 12 or y > 39:
                c[y][x] = mix((80, 92, 100), (40, 46, 54), abs(x - cx) / 10)
            else:
                d = abs(x - cx) / 9
                glass = mix((120, 200, 210), (60, 120, 140), d)
                c[y][x] = mix(c[y][x], glass, 0.5 + 0.4 * d)
                if d > 0.8:
                    c[y][x] = (170, 230, 236)
                if (y > 33 or d > 0.6) and noise(x, y, 4) > 0.55:
                    c[y][x] = mix(c[y][x], (230, 246, 250), 0.6)    # frost
    body = ["..###..", "..###..", "...#...", ".#####.", "#.###.#", "#.###.#", "..###..", "..#.#..", "..#.#..",
            "..#.#..", "..#.#..", ".##.##."]
    for j, row in enumerate(body):
        for i, ch in enumerate(row):
            if ch == "#":
                c[16 + j * 2][cx - 3 + i] = (26, 34, 44)
                c[17 + j * 2][cx - 3 + i] = (26, 34, 44)
    c[17][cx - 1] = c[17][cx + 1] = (255, 240, 200)    # the eyes are open
    # the lamp
    for x in range(cx - 3, cx + 4):
        c[4][x] = c[5][x] = (255, 70, 60)
        c[6][x] = (120, 20, 24)
    c = brush(c, 2, strength=9)
    return frame(c)


# ------------------------------------------------------------------ the Bennets
def bennets():
    W = H = 32
    c = [[gradient([(0, (214, 192, 150)), (1, (170, 140, 100))], (x + y) / 62) for x in range(W)] for y in range(H)]
    for y in range(22, H):
        for x in range(W):
            c[y][x] = (92, 120, 70)    # the lawn
    # (x, head top, height, hair, shirt, legs)
    people = [(3, 5, 22, (40, 30, 24), (40, 44, 56), (30, 32, 40)),         # Noah, dark suit
              (10, 8, 19, (222, 196, 120), (150, 60, 70), (60, 60, 90)),    # Sandra
              (17, 9, 18, (236, 212, 130), (230, 196, 40), (200, 40, 40)),  # Claire, in her cheer uniform
              (24, 10, 17, (110, 70, 40), (60, 100, 160), (70, 60, 50))]    # Lyle
    skin = (232, 190, 160)
    for (px, top, tall, hair, shirt, legs) in people:
        for y in range(top, top + 4):
            for x in range(px + 1, px + 4):
                c[y][x] = skin
        for x in range(px + 1, px + 4):
            c[top][x] = hair
        c[top + 1][px + 1] = c[top + 2][px + 1] = hair
        if hair[0] > 200:                       # longer hair for Sandra and Claire
            c[top + 3][px + 1] = c[top + 4][px] = c[top + 3][px + 3] = hair
        c[top + 2][px + 2] = (40, 30, 30)
        body_end = top + 4 + (tall - 4) * 3 // 5
        for y in range(top + 4, body_end):
            for x in range(px, px + 5):
                c[y][x] = shirt
        c[body_end - 1][px - 1 if px > 1 else px] = skin
        c[body_end - 1][px + 5] = skin
        for y in range(body_end, min(H - 1, top + tall)):
            c[y][px + 1] = c[y][px + 3] = legs
        c[top + 4][px + 2] = mix(shirt, (255, 255, 255), 0.4)    # collar
    c[7][4] = c[7][5] = c[7][6] = (10, 10, 10)    # Noah's horn-rimmed glasses
    for (x, y) in ((29, 25), (30, 25), (29, 24), (28, 26), (29, 26), (30, 26), (28, 27), (30, 27)):
        c[y][x] = (244, 240, 236)                 # Mr. Muggles
    c = brush(c, 3, strength=6, stroke=2)
    return frame(c, color=(236, 232, 222), light=(250, 248, 240))    # a white photo border


# ------------------------------------------------------------------ the eclipse
def eclipse():
    W, H = 48, 32
    cx, cy = 24, 12
    c = [[gradient([(0, (8, 8, 16)), (0.6, (30, 24, 40)), (1, (90, 60, 50))], y / (H - 1)) for x in range(W)] for y in range(H)]
    for y in range(H):
        for x in range(W):
            r = math.hypot(x - cx, y - cy)
            a = math.atan2(y - cy, x - cx)
            corona = 7 + 2.5 * abs(math.sin(a * 5)) + noise(int(a * 12), 0, 8) * 2
            if r < 6:
                c[y][x] = (4, 4, 6)
            elif r < 7:
                c[y][x] = (255, 244, 210)
            elif r < corona + 3:
                c[y][x] = mix(c[y][x], (250, 210, 130), (corona + 3 - r) / 4)
    for x in range(W):
        h = 24 + int(2 * math.sin(x / 5) + noise(x // 3, 0, 9) * 2)
        for y in range(h, H):
            c[y][x] = (22, 30, 20) if y < h + 2 else (14, 20, 14)
    c = brush(c, 4, strength=7)
    return frame(c)


for name, painter in (("explosion", explosion), ("sleeper", sleeper), ("bennets", bennets), ("eclipse", eclipse)):
    png(OUT / f"{name}.png", painter())
    print("wrote", OUT / f"{name}.png")
