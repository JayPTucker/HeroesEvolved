"""Generate the original soft, billowing smoke sprite used by the detonation renderer."""
from pathlib import Path
import random
from PIL import Image, ImageFilter
import math

SIZE = 128
rng = random.Random(73419)
noise_layers = []
for resolution, weight in [(5, .55), (11, .30), (23, .15)]:
    field = Image.new('L', (resolution, resolution))
    field.putdata([rng.randrange(256) for _ in range(resolution ** 2)])
    noise_layers.append((list(field.resize((SIZE, SIZE), Image.Resampling.BICUBIC).getdata()), weight))
sprite = Image.new('RGBA', (SIZE, SIZE))
pixels = []
for y in range(SIZE):
    for x in range(SIZE):
        nx, ny = (x + .5) / SIZE * 2 - 1, (y + .5) / SIZE * 2 - 1
        angle = math.atan2(ny, nx)
        radius = math.hypot(nx, ny)
        noise = sum(layer[y * SIZE + x] / 255 * weight for layer, weight in noise_layers)
        edge = .83 + .065 * math.sin(angle * 5 + .3) + .04 * math.sin(angle * 9)
        envelope = max(0, min(1, (edge - radius) / .18))
        envelope = envelope * envelope * (3 - 2 * envelope)
        alpha = round(255 * envelope * (.50 + noise * .45))
        shade = round(255 * max(.22, min(1, .40 + noise * .65 - ny * .16 - nx * .06)))
        pixels.append((shade, shade, shade, alpha))
sprite.putdata(pixels)
sprite = sprite.filter(ImageFilter.GaussianBlur(.6))
target = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/heroesevolved/textures/effects/nuclear_smoke.png'
target.parent.mkdir(parents=True, exist_ok=True)
sprite.save(target, optimize=True)
assert all(sprite.getpixel((x, y))[3] == 0 for x, y in [(0, 0), (127, 0), (0, 127), (127, 127)])
print(f'Generated {target.name}: {SIZE}x{SIZE} RGBA, soft transparent edges')
