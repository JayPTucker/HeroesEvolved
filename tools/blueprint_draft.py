"""Helpers for drafting large or symmetric blueprints in Python instead of typing every row.

Write a small script that builds a Grid and calls .write(); commit the resulting .blueprint
(the readable file is the source of truth, the script is just how you drafted it).

    import sys; sys.path.insert(0, "tools")
    from blueprint_draft import Grid
    g = Grid(9, 5, 7)                      # width (x), height (y), depth (z)
    g.room(wall="D", floor="F", ceiling="B")
    g.box(1, 1, 1, 7, 3, 5, ".")           # carve
    g.put(4, 1, 0, ".").put(4, 2, 0, ".")  # door
    g.write("src/main/resources/data/heroesevolved/blueprints/my_room.blueprint",
            name="my_room", anchor=(4, 0, 0),
            palette={"D": "deepslate_tiles", "F": "polished_deepslate", "B": "deepslate_bricks"},
            notes=["One-line description of what this is and where it is placed."],
            layer_notes={0: "floor", 4: "ceiling"})

Coordinates: x runs west to east (columns), y bottom to top (layers), z north to south (rows).
"""


class Grid:
    def __init__(self, width, height, depth, fill="_"):
        self.w, self.h, self.d = width, height, depth
        self.cells = [[[fill] * width for _ in range(depth)] for _ in range(height)]

    def put(self, x, y, z, ch):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d:
            self.cells[y][z][x] = ch
        return self

    def get(self, x, y, z):
        return self.cells[y][z][x]

    def box(self, x0, y0, z0, x1, y1, z1, ch):
        """Fills an inclusive box."""
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    self.put(x, y, z, ch)
        return self

    def room(self, wall="D", floor="F", ceiling="B", inside="."):
        """A closed shell the size of the grid: floor at y=0, ceiling at the top, air inside."""
        self.box(0, 0, 0, self.w - 1, self.h - 1, self.d - 1, wall)
        self.box(0, 0, 0, self.w - 1, 0, self.d - 1, floor)
        self.box(0, self.h - 1, 0, self.w - 1, self.h - 1, self.d - 1, ceiling)
        self.box(1, 1, 1, self.w - 2, self.h - 2, self.d - 2, inside)
        return self

    def scatter(self, ch, positions):
        for x, y, z in positions:
            self.put(x, y, z, ch)
        return self

    def weather(self, find, replace, every=9, seed=0):
        """Swaps a deterministic sprinkling of one character for another (cracked or mossy variants)."""
        for y in range(self.h):
            for z in range(self.d):
                for x in range(self.w):
                    if self.cells[y][z][x] == find and (x * 7 + y * 5 + z * 13 + seed) % every == 0:
                        self.cells[y][z][x] = replace
        return self

    def mirror_x(self):
        """Copies the west half onto the east half, for symmetric builds."""
        for y in range(self.h):
            for z in range(self.d):
                row = self.cells[y][z]
                for x in range(self.w // 2):
                    row[self.w - 1 - x] = row[x]
        return self

    def write(self, path, name, palette, anchor=(0, 0, 0), notes=(), layer_notes=None, signs=None):
        """signs maps an id to (text with | between lines, color, glow) for @sign:<id> markers."""
        lines = [f"# {n}" for n in notes] + [f"name: {name}", "anchor: " + " ".join(map(str, anchor)), "palette:"]
        lines += [f"  {ch} = {state}" for ch, state in palette.items()]
        if signs:
            lines += ["signs:"] + [f"  {sid} {color}{' glow' if glow else ''} = {text}" for sid, (text, color, glow) in signs.items()]
        layer_notes = layer_notes or {}
        y = 0
        while y < self.h:
            top = y
            while top + 1 < self.h and self.cells[top + 1] == self.cells[y] and (top + 1) not in layer_notes:
                top += 1
            note = layer_notes.get(y)
            lines += [""] + ([f"# {note}"] if note else []) + [f"layer {y}:" if top == y else f"layer {y}-{top}:"]
            lines += ["".join(row) for row in self.cells[y]]
            y = top + 1
        with open(path, "w", encoding="utf-8") as out:
            out.write("\n".join(lines) + "\n")


class StateGrid(Grid):
    """A Grid whose cells hold block states directly ("polished_deepslate", "chest[facing=east] @loot:lab"),
    with '.' for air and '_' to keep the existing block. Palette letters are assigned on write, so two
    blocks can never collide on one letter. Pass prefer={"state": "X"} to pin a readable letter."""

    POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!$%&*+=?^~<>/|;,{}()-αβγδεζηθικλμνξπρστυφχψωΓΔΘΛΞΠΣΦΨΩ"

    def write(self, path, name, palette=None, anchor=(0, 0, 0), notes=(), layer_notes=None, signs=None, prefer=None):
        prefer = dict(prefer or {})
        states = []
        for layer in self.cells:
            for row in layer:
                for cell in row:
                    if cell not in (".", "_") and cell not in states:
                        states.append(cell)
        letters, used = {}, set(prefer.values())
        free = (ch for ch in self.POOL if ch not in used)
        for state in states:
            letters[state] = prefer.get(state) or next(free)
        chars = Grid(self.w, self.h, self.d)
        chars.cells = [[[letters.get(cell, cell) for cell in row] for row in layer] for layer in self.cells]
        chars.write(path, name, {letters[s]: s for s in states}, anchor, notes, layer_notes, signs)


def painting_anchor(facing, left, bottom, width, height):
    """The anchor cell for a painting, given the cell at its bottom-left as seen from the front.
    Paintings grow from the anchor toward the facing's counter-clockwise side and upward; even sizes
    put the anchor just left of / below the middle. left is (x, z); returns (x, y, z)."""
    right = {"east": (0, -1), "north": (-1, 0), "west": (0, 1), "south": (1, 0)}[facing]   # the counter-clockwise side
    k_along = (width - 1) // 2 if width % 2 else width // 2 - 1
    k_up = (height - 1) // 2 if height % 2 else height // 2 - 1
    # bottom-left as seen from the front is the clockwise end (k = 1 - w/2 for even, -(w-1)/2 for odd)
    low = -(width // 2 - 1) if width % 2 == 0 else -((width - 1) // 2)
    x, z = left[0] - low * right[0], left[1] - low * right[1]
    y = bottom + (k_up if height % 2 else height // 2 - 1)
    return x, y, z
