"""Draws the 16x16 item icons for Sylar's Collection:

- repair_ticket.png: a buff paper tag from Gray & Sons, a string through its punched hole, a brass-brown stamp, two
  lines of a watchmaker's handwriting and a perforated tear-off edge.
- music_disc_the_collection.png: the record of his theme, a black disc with a brass label and a clock hand.
- brass_movement.png: the works of a pocket watch, out of its case: a brass plate, two gears, a balance wheel and three
  ruby jewels.

Uses only the standard library (no Pillow).  Run from anywhere:  py -3 tools/generate-sylar-items.py
"""
import math
import struct
import zlib
from pathlib import Path

ITEMS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/heroesevolved/textures/item"

CLEAR = (0, 0, 0, 0)
PAPER, PAPER_SHADE, PAPER_EDGE = (226, 210, 168, 255), (196, 178, 132, 255), (120, 98, 62, 255)
INK, STAMP, STAMP_DARK = (46, 38, 40, 255), (150, 82, 46, 255), (104, 52, 30, 255)
STRING, STRING_DARK = (196, 74, 56, 255), (128, 40, 32, 255)
DARK, BRASS, LIGHT, SHINE = (74, 52, 22, 255), (176, 132, 52, 255), (224, 186, 98, 255), (252, 236, 176, 255)
STEEL, RUBY = (150, 158, 170, 255), (206, 40, 64, 255)


class Icon:
    """A 16x16 RGBA canvas that writes itself as a PNG."""

    def __init__(self):
        self.px = [[CLEAR] * 16 for _ in range(16)]

    def __setitem__(self, xy, colour):
        x, y = xy
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = colour

    def save(self, path):
        raw = b"".join(bytes([0]) + bytes(c for p in row for c in p) for row in self.px)

        def chunk(tag, data):
            return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        signature = bytes([137, 80, 78, 71, 13, 10, 26, 10])
        png = signature + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        Path(path).write_bytes(png)


def ticket():
    px = Icon()
    # The tag: a card with a clipped top-left corner.
    for y in range(3, 15):
        for x in range(2, 15):
            if x + y < 8 or x - y > 9:
                continue
            edge = y in (3, 14) or x in (2, 14) or x + y == 8 or x - y == 9
            px[x, y] = PAPER_EDGE if edge else (PAPER_SHADE if (x + y) % 7 == 0 or y > 11 else PAPER)
    # Perforation along the bottom: a dotted line above the tear-off stub.
    for x in range(3, 14, 2):
        px[x, 12] = PAPER_EDGE
    # The punched hole, and the string through it, trailing up and away.
    px[5, 5] = CLEAR
    for x, y, c in ((4, 4, STRING), (3, 3, STRING_DARK), (2, 2, STRING), (1, 1, STRING_DARK), (5, 4, STRING_DARK), (6, 3, STRING),
                    (7, 2, STRING_DARK), (7, 1, STRING)):
        px[x, y] = c
    # Two lines of handwriting.
    for x in range(7, 13):
        if x != 10:
            px[x, 6] = INK
    for x in range(5, 12):
        if x not in (7, 9):
            px[x, 8] = INK
    # The shop's stamp.
    for x, y in ((10, 9), (11, 9), (12, 9), (10, 10), (12, 10), (10, 11), (11, 11), (12, 11)):
        px[x, y] = STAMP
    px[11, 10] = STAMP_DARK
    # The tear-off stub under the perforation.
    for x in range(4, 13):
        px[x, 13] = PAPER_SHADE
    return px


def gear(px, cx, cy, r, teeth, colour, edge):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            tooth = math.cos(math.atan2(y - cy, x - cx) * teeth) > 0.3
            if d <= r - 0.6 or (d <= r + 0.6 and tooth):
                px[x, y] = edge if d > r - 1.2 else colour


def movement():
    px = Icon()
    cx, cy = 7.5, 7.5
    # The plate, lit from the top left.
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 7.0:
                px[x, y] = DARK if d > 6.2 else (LIGHT if (x - cx) + (y - cy) < -4 else BRASS)
    # A large gear lower left, a small one upper right.
    gear(px, 5.5, 9.5, 3.4, 8, LIGHT, DARK)
    gear(px, 10.5, 5.0, 2.2, 6, SHINE, DARK)
    px[5, 9] = DARK
    px[10, 5] = DARK
    # The balance wheel, steel, lower right.
    for y in range(16):
        for x in range(16):
            if 1.4 <= math.hypot(x - 11, y - 11) <= 2.3:
                px[x, y] = STEEL
    # Ruby jewels, and a glint.
    for x, y in ((11, 11), (8, 3), (3, 6)):
        px[x, y] = RUBY
    px[12, 3] = SHINE
    return px


def disc():
    """The record of his theme: a black disc with grooves, a brass label, and a clock hand on the label."""
    px = Icon()
    cx, cy = 7.5, 7.5
    groove, black, shine = (38, 34, 40, 255), (20, 18, 24, 255), (88, 84, 96, 255)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d > 7.2:
                continue
            if d <= 2.6:
                px[x, y] = LIGHT if (x - cx) + (y - cy) < -1 else BRASS
            elif d <= 3.1:
                px[x, y] = DARK
            else:
                px[x, y] = groove if int(d * 2) % 2 == 0 else black
    for x, y in ((4, 3), (3, 4), (11, 12), (12, 11)):
        px[x, y] = shine
    px[7, 7] = black
    px[8, 8] = black
    for x, y in ((8, 6), (9, 5)):
        px[x, y] = DARK
    return px


ticket().save(ITEMS / "repair_ticket.png")
disc().save(ITEMS / "music_disc_the_collection.png")
movement().save(ITEMS / "brass_movement.png")
print("wrote repair_ticket.png, music_disc_the_collection.png and brass_movement.png")
