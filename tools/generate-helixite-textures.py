"""Generate the original 16x16 Helixite ore and shard textures (standard library only).

Run: python tools/generate-helixite-textures.py
Output is deterministic, so regenerating does not change committed files.
"""
import pathlib
import random
import struct
import zlib

root = pathlib.Path(__file__).resolve().parents[1]
textures = root / "src/main/resources/assets/heroesevolved/textures"

# Helixite: a teal crystal with violet depth, matching the Formula's glow.
CRYSTAL = [(34, 92, 104), (52, 156, 160), (96, 222, 208), (184, 255, 240)]
VIOLET = (120, 84, 200)


def png(path, pixels):
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def host(seed, base, spread, streaks):
    rng = random.Random(seed)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            shade = base + rng.randint(-spread, spread)
            if streaks and (y % 5 == 0 or (y + x // 6) % 7 == 0):
                shade -= 9
            row.append([shade, shade, shade + 2, 255])
        rows.append(row)
    return rows


def crystals(pixels, seed):
    # Three small clusters, each a diagonal prism with a bright tip.
    rng = random.Random(seed)
    for cx, cy in ((4, 4), (11, 7), (6, 12)):
        for step in range(4):
            x, y = cx + step - 1, cy - step + 1
            for dx, dy, tone in ((0, 0, 2), (1, 0, 1), (0, 1, 0), (-1, 0, 1)):
                px, py = x + dx, y + dy
                if 0 <= px < 16 and 0 <= py < 16:
                    color = CRYSTAL[min(3, tone + (step == 3))]
                    if rng.random() < 0.18:
                        color = VIOLET
                    pixels[py][px] = [*color, 255]
        pixels[cy - 2][cx + 2] = [*CRYSTAL[3], 255]


def shard():
    pixels = [[[0, 0, 0, 0] for _ in range(16)] for _ in range(16)]
    # A long faceted shard from the lower left to the upper right, with a violet core.
    for i in range(11):
        x, y = 3 + i, 13 - i
        width = 1 if i in (0, 10) else 2 if i in (1, 9) else 3
        for w in range(-width // 2, width - width // 2):
            px, py = x + w, y + w
            if 0 <= px < 16 and 0 <= py < 16:
                edge = w == -width // 2
                core = w == 0 and 3 <= i <= 7
                color = CRYSTAL[3] if edge else VIOLET if core else CRYSTAL[2 if i > 5 else 1]
                pixels[py][px] = [*color, 255]
    for x, y in ((3, 13), (2, 14)):
        pixels[y][x] = [*CRYSTAL[0], 255]
    pixels[3][13] = [255, 255, 255, 255]
    return pixels


stone = host(11, 124, 10, False)
crystals(stone, 5)
png(textures / "block/helixite_ore.png", stone)

deepslate = host(29, 74, 8, True)
crystals(deepslate, 17)
png(textures / "block/deepslate_helixite_ore.png", deepslate)

png(textures / "item/helixite_shard.png", shard())
print("PASS: wrote helixite_ore, deepslate_helixite_ore and helixite_shard textures")
