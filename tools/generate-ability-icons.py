"""Bake the hand-specified ability glyphs into one GUI texture and its ID index.

Run with Python and Pillow: python tools/generate-ability-icons.py [--check]
"""
import argparse
import json
from pathlib import Path

from PIL import Image, ImageColor, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
DATA = ROOT / "src/main/resources/data/heroesevolved"
ASSETS = ROOT / "src/main/resources/assets/heroesevolved"
TEXTURE = ASSETS / "textures/gui/ability_icons.png"
INDEX = ASSETS / "ability_icons.json"
SIZE = 32
COLUMNS = 16

# Glyphs are drawn at Minecraft scale. Coordinates below refer to a 24-pixel square.
# Each command is a filled polygon, stroke, disc, or elliptical arc.
GLYPHS = {
    "arc": ["a 2 2 21 21 205 470 3", "a 6 6 17 17 225 455 2"],
    "arrow": ["p 2 10 12 10 12 4 22 12 12 20 12 14 2 14"],
    "anchor": ["o 9 2 14 7", "l 11 6 11 21 3", "l 6 10 17 10 2", "a 3 10 20 21 0 180 3",
               "p 1 16 3 11 7 16", "p 17 16 20 11 23 16"],
    "armor": ["p 2 7 7 3 10 6 14 6 17 3 22 7 20 12 17 11 17 21 7 21 7 11 4 12",
              "l 9 12 14 12 1"],
    "beast": ["p 2 7 5 2 9 6 14 6 18 2 22 7 21 16 16 20 12 22 7 20 2 16",
              "e 5 10 9 12", "e 15 10 19 12", "e 10 16 14 18"],
    "beam": ["p 1 16 17 4 21 7 5 21", "l 1 10 11 3 2", "l 11 21 22 12 2"],
    "bird": ["p 1 3 5 7 10 10 10 7 13 4 17 4 22 7 17 8 16 12 19 9 22 7 20 15 15 18 12 23 8 20 7 16 4 13",
             "e 14 6 15 7"],
    "bolt": ["p 13 1 3 14 10 14 7 23 21 9 14 9 18 1"],
    "bomb": ["o 3 9 17 22", "l 11 10 13 6 17 4 2", "l 18 1 18 4 2", "l 20 5 23 5 2"],
    "book": ["p 1 5 7 4 11 7 16 4 22 5 22 19 16 18 11 21 7 18 1 19", "e 10 8 12 18",
             "e 3 8 7 9", "e 15 8 19 9", "e 3 12 7 13", "e 15 12 19 13"],
    "bow": ["a 4 3 20 20 270 450 3", "l 12 3 12 20 1", "l 3 12 21 12 2", "p 18 9 23 12 18 15"],
    "bridge": ["p 1 14 22 14 22 17 1 17", "a 3 4 20 24 180 360 2", "l 5 11 5 17 2",
               "l 9 6 9 17 2", "l 14 6 14 17 2", "l 18 11 18 17 2"],
    "bubble": ["a 2 2 21 21 0 360 3", "a 6 5 17 17 188 275 2", "o 14 15 17 18"],
    "burst": ["p 12 0 14 7 20 3 18 9 23 12 17 14 21 21 14 18 11 23 9 17 2 20 6 14 0 11 7 9 4 3 10 7"],
    "chain": ["a 2 9 14 21 0 360 3", "a 9 2 21 14 0 360 3", "l 8 15 15 8 2"],
    "chip": ["p 5 5 18 5 18 18 5 18", "e 8 8 15 15", "o 10 10 13 13", "l 8 2 8 5 2",
             "l 15 2 15 5 2", "l 2 8 5 8 2", "l 2 15 5 15 2", "l 18 8 21 8 2",
             "l 18 15 21 15 2", "l 8 18 8 21 2", "l 15 18 15 21 2"],
    "circle": ["o 4 4 19 19", "e 7 7 16 16", "o 9 9 14 14"],
    "claw": ["p 2 14 8 7 14 4 10 9 5 21", "p 8 16 14 9 20 6 16 11 11 22",
             "p 15 17 21 11 23 9 21 16 17 22"],
    "clock": ["a 2 2 21 21 0 360 3", "l 11 5 11 12 16 13 2", "o 10 11 12 13"],
    "cloud": ["o 2 10 12 19", "o 7 5 18 18", "o 13 9 22 19", "p 6 14 19 14 19 19 6 19"],
    "compass": ["a 2 2 21 21 0 360 2", "p 16 6 14 14 6 17 9 9", "e 10 11 12 12"],
    "crack": ["p 3 1 14 6 10 9 20 12 13 16 23 21 10 21 9 17 4 17 11 12 3 10 8 6"],
    "crystal": ["p 12 1 20 9 17 19 10 22 4 16 4 8", "e 10 5 11 16", "e 13 15 17 16"],
    "cube": ["p 12 2 21 7 21 17 12 22 3 17 3 7", "e 5 8 18 9", "e 11 10 12 19"],
    "dash": ["p 2 14 13 8 10 4 22 6 17 18 15 12 2 18", "l 1 8 7 5 2", "l 7 21 11 19 2"],
    "door": ["l 4 21 4 3 19 3 19 21 3", "p 6 5 14 7 14 18 6 21", "o 10 12 12 13"],
    "down": ["p 9 2 14 2 14 12 20 12 11 22 3 12 9 12"],
    "drone": ["p 1 8 8 8 10 5 13 5 15 8 22 8 21 12 15 12 13 16 10 16 8 12 2 12",
              "l 8 19 15 19 2", "o 10 10 13 12"],
    "drop": ["p 12 1 18 10 20 15 17 20 12 22 7 20 4 15 6 10", "e 8 13 9 17"],
    "echo": ["p 3 6 11 2 17 5 17 8 11 5 3 9", "p 3 11 11 7 19 11 19 14 11 10 3 14",
             "p 3 16 11 12 21 16 21 19 11 15 3 19"],
    "eye": ["p 1 12 5 8 9 5 14 5 19 8 22 12 19 16 14 19 9 19 5 16", "e 7 8 16 16",
            "o 10 10 13 14"],
    "face": ["p 3 3 20 3 20 14 16 19 12 22 7 20 3 14", "e 6 8 9 11", "e 14 8 17 11",
             "e 9 15 14 16"],
    "feather": ["p 21 2 21 10 16 16 11 19 5 19 2 22 1 20 5 16 6 12 11 6 17 2",
                "e 8 14 10 15", "e 12 10 15 11", "e 16 6 19 7"],
    "fist": ["p 1 9 5 9 5 6 9 6 9 4 12 4 12 3 16 3 16 5 20 5 22 8 22 13 16 21 8 21 4 16",
             "e 7 10 16 11", "e 17 7 18 9"],
    "flame": ["p 14 1 16 8 19 11 20 7 22 14 21 19 17 22 7 22 3 19 2 14 7 6 8 12 11 8",
              "e 10 17 13 20"],
    "flask": ["p 8 2 15 2 15 5 14 5 14 10 21 19 20 22 3 22 2 19 9 10 9 5 8 5",
              "e 7 16 16 16", "e 10 13 13 13"],
    "flower": ["o 8 2 15 10", "o 2 7 10 14", "o 13 7 21 14", "o 5 12 12 19",
               "o 11 12 18 19", "e 9 9 14 14", "o 10 10 13 13", "l 11 17 11 22 2"],
    "foot": ["p 3 3 10 3 10 8 13 12 16 15 20 15 22 17 22 20 2 20 2 15 4 11", "e 3 17 20 17"],
    "gate": ["a 3 1 20 20 177 543 3", "l 3 12 3 22 3", "l 20 12 20 22 3",
             "p 5 19 11 16 18 19 11 22", "o 10 7 13 10"],
    "gear": ["p 9 2 14 2 15 5 17 6 20 5 22 9 20 11 20 13 22 15 20 19 17 18 15 19 14 22 9 22 8 19 6 18 3 19 1 15 3 13 3 11 1 9 3 5 6 6 8 5",
             "e 7 7 16 16", "o 9 9 14 14"],
    "ghost": ["o 3 2 20 18", "p 3 10 20 10 20 22 16 18 12 22 8 18 3 22", "e 6 9 9 12", "e 14 9 17 12"],
    "globe": ["a 2 2 21 21 0 360 2", "a 7 2 16 21 0 360 2", "l 2 11 21 11 2"],
    "hammer": ["p 9 4 15 2 22 8 17 13", "l 14 9 4 20 4", "l 1 14 6 14 2", "l 13 19 17 20 2"],
    "hand": ["p 2 10 5 9 8 13 8 3 10 2 11 3 11 9 12 9 12 2 14 1 15 2 15 9 16 9 16 4 18 3 19 4 19 11 21 7 23 8 21 17 17 22 9 22 7 17",
             "e 10 15 14 15"],
    "heart": ["p 2 6 6 3 10 4 12 7 14 4 18 3 21 6 22 10 20 14 12 22 3 14 1 10",
              "e 5 8 7 9"],
    "hex": ["p 12 1 21 6 21 17 12 22 2 17 2 6", "e 6 8 17 15", "p 12 8 16 11 12 15 7 11"],
    "hourglass": ["l 5 3 18 3 3", "l 5 20 18 20 3", "p 6 3 17 3 16 9 12 12 16 14 17 20 6 20 7 14 11 12 7 9",
                  "e 8 5 15 6", "e 9 17 14 18"],
    "hook": ["l 13 2 13 14 3", "a 4 8 15 21 0 210 3", "p 2 15 4 8 8 14"],
    "ice": ["l 12 1 12 22 3", "l 2 6 21 17 3", "l 2 17 21 6 3", "l 8 3 12 7 16 3 2",
            "l 8 20 12 16 16 20 2"],
    "impact": ["p 12 0 16 2 12 12 6 15 7 9", "l 1 18 6 19 10 16 14 19 18 17 22 18 2",
               "l 1 22 21 22 2"],
    "key": ["a 2 2 14 14 0 360 3", "l 11 11 21 21 4", "l 16 16 19 13 3", "l 19 19 22 16 2"],
    "knot": ["a 2 3 17 18 180 495 3", "a 6 6 21 21 0 315 3", "l 8 16 15 7 2"],
    "lance": ["p 21 1 22 10 15 11 4 22 1 19 12 8 14 3", "l 4 10 13 19 2"],
    "leaf": ["p 22 2 22 12 18 18 11 21 4 20 1 23 0 21 4 17 3 10 7 5 14 2", "e 9 11 13 13"],
    "link": ["l 4 17 11 5 18 16 4", "o 0 14 7 21", "o 7 1 14 8", "o 16 13 23 20"],
    "lock": ["a 6 2 17 15 178 362 3", "p 4 9 19 9 19 21 4 21", "e 10 13 13 17"],
    "loop": ["l 11 11 8 6 4 6 1 9 1 14 4 17 8 17 16 6 20 6 22 9 22 14 20 17 16 17 11 11 3",
             "p 17 4 23 5 18 9"],
    "mask": ["p 1 5 7 7 12 8 17 7 22 5 21 15 17 19 12 16 7 19 2 15",
             "e 5 11 9 13", "e 14 11 18 13"],
    "mine": ["p 3 12 7 9 17 9 21 13 21 20 3 20", "o 9 3 14 8", "l 2 22 22 22 2", "e 6 14 17 16"],
    "moon": ["a 1 1 21 21 45 270 4", "a 6 0 21 16 60 246 3", "o 17 3 20 6"],
    "net": ["p 1 3 22 3 20 6 3 6", "l 3 5 9 20 13 20 20 5 2", "l 5 10 17 10 2", "l 7 15 15 15 2",
            "l 8 5 11 19 1", "l 15 5 12 19 1"],
    "network": ["l 4 5 18 4 19 19 5 18 4 5 11 11 18 4 2", "l 5 18 11 11 19 19 2",
                "o 1 2 7 8", "o 15 1 21 7", "o 16 16 22 22", "o 2 15 8 21", "o 8 8 14 14"],
    "note": ["o 3 14 13 21", "l 12 16 12 3 3", "p 13 3 20 7 21 12 13 8"],
    "orbit": ["a 1 6 22 17 0 360 2", "o 7 7 15 15", "o 17 7 22 12", "o 2 13 5 16"],
    "pause": ["p 5 4 10 4 10 20 5 20", "p 14 4 19 4 19 20 14 20"],
    "peak": ["p 0 21 8 9 11 13 15 4 23 21", "e 13 12 17 13", "e 7 16 10 17"],
    "people": ["o 8 2 15 9", "o 0 5 6 11", "o 17 5 23 11", "p 8 12 15 12 18 21 5 21",
               "p 1 12 6 13 4 19 0 19", "p 17 13 22 12 23 19 19 19"],
    "pillar": ["p 7 2 16 2 16 19 20 19 20 22 3 22 3 19 7 19", "e 10 5 11 17", "e 13 9 14 18"],
    "pin": ["o 4 2 19 17", "p 5 11 18 11 12 23", "e 9 6 14 11"],
    "portal": ["a 3 1 19 22 0 360 3", "a 7 5 15 18 0 360 2", "l 1 15 7 15 2",
               "l 17 8 23 8 2"],
    "prism": ["l 2 18 11 3 20 18 2 18 3", "l 1 9 7 12 2", "l 16 10 22 6 2", "l 18 14 23 13 2"],
    "pulse": ["l 1 12 6 12 9 5 12 20 15 9 18 12 22 12 3"],
    "ray": ["p 2 12 17 8 21 3 23 9 21 21 17 16 2 13", "l 4 7 11 5 2", "l 4 18 11 20 2"],
    "rewind": ["p 2 12 10 5 10 19", "p 11 12 20 5 20 19"],
    "ripple": ["a 1 6 22 21 0 360 2", "a 4 8 19 18 0 360 2", "a 8 10 15 15 0 360 2"],
    "rock": ["p 2 15 4 7 9 3 17 4 21 10 22 17 17 21 5 21", "e 7 7 11 8", "e 14 11 17 12"],
    "roots": ["l 11 2 11 10 6 14 6 20 3", "l 11 10 16 14 17 21 3", "l 6 14 2 16 2", "l 17 16 22 19 2",
              "l 11 11 11 22 2", "p 4 5 11 2 14 7"],
    "shards": ["p 2 4 10 7 5 15", "p 13 1 21 5 16 12", "p 12 13 21 14 17 22", "p 2 18 7 17 9 22"],
    "shield": ["p 11 2 21 6 20 14 16 20 11 23 6 20 2 14 1 6", "e 10 7 12 17", "e 6 11 17 13"],
    "skin": ["p 3 2 19 3 20 10 17 12 15 20 7 22 4 17 1 14", "l 8 7 8 13 12 14 15 9 2",
             "e 6 8 8 10", "e 14 7 16 9"],
    "skull": ["o 2 2 21 19", "p 7 13 16 13 16 22 7 22", "e 5 8 9 12", "e 14 8 18 12", "e 10 14 13 16",
              "e 9 19 10 22", "e 13 19 14 22"],
    "spark": ["p 12 1 14 9 22 12 14 14 12 22 9 14 1 12 9 9"],
    "spear": ["p 21 2 22 12 16 10 4 22 1 19 13 7 11 4", "l 2 12 7 7 2"],
    "spiral": ["l 2 4 7 2 15 2 21 7 21 15 16 21 9 21 4 17 4 10 8 6 14 6 17 9 17 14 14 17 10 17 8 14 8 11 11 9 13 11 13 13 2"],
    "star": ["p 12 1 15 8 23 9 17 14 19 22 12 18 4 22 6 14 0 9 8 8"],
    "sun": ["o 5 5 18 18", "l 11 0 11 3 2", "l 21 11 23 11 2", "l 11 20 11 23 2", "l 0 11 3 11 2",
            "l 3 3 5 5 2", "l 18 5 20 3 2", "l 18 18 20 20 2", "l 3 20 5 18 2"],
    "sword": ["p 19 2 22 2 22 6 12 16 8 12", "l 5 11 13 19 3", "l 4 20 9 15 3", "o 1 19 5 23"],
    "target": ["a 4 4 19 19 0 360 2", "o 10 10 13 13", "l 11 1 11 7 2", "l 11 16 11 22 2",
               "l 1 11 7 11 2", "l 16 11 22 11 2"],
    "tentacles": ["o 7 3 16 13", "l 9 8 4 11 4 17 1 20 3", "l 11 9 9 15 10 22 3", "l 13 9 15 19 19 22 3",
                  "l 15 9 20 12 20 16 23 17 3", "e 10 7 11 8", "e 13 7 14 8"],
    "tornado": ["p 1 4 22 4 19 7 4 7", "p 3 9 21 9 16 12 5 12", "p 6 14 17 14 12 17 8 17",
                "p 9 19 15 19 13 22 10 22"],
    "trail": ["l 1 18 6 15 11 15 16 9 22 9 3", "l 1 12 7 9 12 9 17 3 2"],
    "up": ["p 9 21 14 21 14 11 20 11 11 1 3 11 9 11"],
    "valve": ["l 1 15 22 15 4", "p 8 11 15 11 15 19 8 19", "l 11 11 11 5 2", "l 5 5 18 5 3",
              "l 1 11 1 19 2", "l 22 11 22 19 2"],
    "wall": ["p 2 5 21 5 21 20 2 20", "e 2 9 21 10", "e 2 14 21 15", "e 8 5 9 9", "e 14 10 15 14",
             "e 8 15 9 20"],
    "wave": ["p 1 16 6 15 9 10 13 5 17 4 22 8 17 8 15 11 18 14 22 14 22 19 1 19",
             "e 12 12 13 14"],
    "waves": ["l 1 7 5 5 9 5 14 7 18 7 22 5 2", "l 1 12 5 10 9 10 14 12 18 12 22 10 2",
              "l 1 17 5 15 9 15 14 17 18 17 22 15 2"],
    "wing": ["p 1 4 7 9 12 13 16 8 22 3 21 11 19 16 15 20 12 22 6 19 3 15", "e 5 12 6 14", "e 17 12 18 14"],
}


def catalog():
    trees = json.loads((DATA / "skill_trees.json").read_text(encoding="utf-8"))
    entries = {}
    for tree in trees:
        power = tree["power"]
        for branch, nodes in enumerate(tree["branches"]):
            for tier, node in enumerate(nodes):
                entries[node.get("id", f"{power}_{branch}_{tier}")] = power
        for node in tree["styles"]:
            entries[node["id"]] = power
    balance = json.loads((DATA / "power_balance.json").read_text(encoding="utf-8"))
    for action in balance:
        if not action["action"].startswith("style_"):
            entries[action["action"]] = action["power"]
    return entries


def mask(name):
    image = Image.new("L", (24, 24))
    draw = ImageDraw.Draw(image)
    for command in GLYPHS[name]:
        operation, *coordinates = command.split()
        values = list(map(int, coordinates))
        match operation:
            case "p":
                draw.polygon(list(zip(values[::2], values[1::2])), fill=255)
            case "l":
                draw.line(list(zip(values[:-1:2], values[1:-1:2])), fill=255, width=values[-1], joint="curve")
            case "o":
                draw.ellipse(tuple(values), fill=255)
            case "e":
                draw.rectangle(tuple(values), fill=0)
            case "a":
                draw.arc(tuple(values[:4]), values[4], values[5], fill=255, width=values[6])
    return image


def icon(art, palette, masks):
    symbol, _, accent = art.partition("/")
    color = ImageColor.getrgb(palette)
    highlight = tuple(round(channel * 0.44 + 255 * 0.56) for channel in color)
    dark = tuple(round(channel * 0.18) for channel in color)
    image = Image.new("RGBA", (SIZE, SIZE))
    body = Image.new("L", image.size)
    body.paste(masks[symbol], (3, 3) if accent else (4, 4))
    image.paste((*dark, 255), mask=body.filter(ImageFilter.MaxFilter(3)))
    lit = Image.new("RGBA", image.size)
    lit_draw = ImageDraw.Draw(lit)
    for y in range(SIZE):
        mix = min(1, max(0, (19 - y) / 14))
        shade = tuple(round(lo + (hi - lo) * mix) for lo, hi in zip(color, highlight))
        lit_draw.line((0, y, SIZE, y), fill=(*shade, 255))
    image.paste(lit, mask=body)
    if accent:
        badge = ImageDraw.Draw(image)
        badge.rounded_rectangle((18, 18, 31, 31), radius=3, fill=(*dark, 255), outline=(*color, 255))
        detail = Image.new("L", image.size)
        detail.paste(masks[accent].resize((10, 10), Image.Resampling.BOX), (20, 20))
        image.paste((*highlight, 255), mask=detail)
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify the checked-in texture and index")
    options = parser.parse_args()
    definitions = json.loads((TOOLS / "ability-icon-designs.json").read_text(encoding="utf-8"))
    designs = {
        name: (power, design.get("variants", {}).get(name, design["color"]), art)
        for power, design in definitions.items() for name, art in design["icons"].items()
    }
    assert {name: power for name, (power, _, _) in designs.items()} == catalog(), "Icon designs must cover the full catalog"
    masks = {name: mask(name) for name in GLYPHS}
    ids = list(designs)
    rows = (len(ids) + COLUMNS - 1) // COLUMNS
    atlas = Image.new("RGBA", (SIZE * COLUMNS, SIZE * rows))
    seen = {}
    for index, name in enumerate(ids):
        _, color, art = designs[name]
        picture = icon(art, color, masks)
        pixels = picture.tobytes()
        assert pixels not in seen, f"Identical icons for {name} and {seen.get(pixels)}"
        seen[pixels] = name
        atlas.paste(picture, (index % COLUMNS * SIZE, index // COLUMNS * SIZE))
    index_text = json.dumps(ids, indent=2) + "\n"
    if options.check:
        assert INDEX.read_text(encoding="utf-8") == index_text, "Icon index needs regeneration"
        with Image.open(TEXTURE) as existing:
            assert existing.size == atlas.size and existing.tobytes() == atlas.tobytes(), "Icon texture needs regeneration"
    else:
        TEXTURE.parent.mkdir(parents=True, exist_ok=True)
        atlas.save(TEXTURE, optimize=True)
        INDEX.write_text(index_text, encoding="utf-8")
    print(f"Verified {len(ids)} unique ability, skill and class icons")


if __name__ == "__main__":
    main()
