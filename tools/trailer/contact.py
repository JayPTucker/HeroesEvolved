"""A contact sheet of filmed frames: chosen frames of chosen shots, shrunk and tiled into one PNG, to look over many
shots at once before cutting them. Reads the recorder's raw frames directly (no Blender needed).

    "C:/Program Files/Blender Foundation/Blender 5.0/5.0/python/bin/python.exe" tools/trailer/contact.py build/trailer/contact.png 3 10-ballista:64 13-epicenter:54

The number after the output is how many frames go across; each frame is shown at a quarter size (480x270). A thin
black line separates the tiles. Frames are numbered from 1, as in shot.txt.
"""
import os
import struct
import sys
import zlib

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FILM = os.path.join(ROOT, "build", "trailer-preview", "film")


def frame(shot, n):
    folder = os.path.join(FILM, shot)
    w, h = 1920, 1080
    with open(os.path.join(folder, "shot.txt"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("size "):
                w, h = (int(v) for v in line.split()[1:3])
    raw = np.fromfile(os.path.join(folder, "%05d.rgb" % n), dtype=np.uint8).reshape(h, w, 3)
    return raw[: h - h % 4, : w - w % 4].reshape(h // 4, 4, w // 4, 4, 3).mean(axis=(1, 3)).astype(np.uint8)


def png(path, image):
    h, w, _ = image.shape
    rows = b"".join(b"\x00" + image[y].tobytes() for y in range(h))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(rows, 6)) + chunk(b"IEND", b""))


def main():
    out, across, items = sys.argv[1], int(sys.argv[2]), sys.argv[3:]
    tiles = [frame(*(lambda s, n: (s, int(n)))(*item.rsplit(":", 1))) for item in items]
    th, tw, _ = tiles[0].shape
    down = (len(tiles) + across - 1) // across
    sheet = np.zeros((down * (th + 2), across * (tw + 2), 3), np.uint8)
    for i, tile in enumerate(tiles):
        y, x = i // across * (th + 2), i % across * (tw + 2)
        sheet[y: y + th, x: x + tw] = tile
    png(out, sheet)
    print("CONTACT", len(tiles), "frames to", out)


main()
