"""Build an original Minecraft-format Sylar skin with a dark coat and watchmaker details.

Pixel placement follows the standard 64x64 player UV layout. No downloaded skin assets.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import random

rng = random.Random(1942)
skin = Image.new('RGBA', (64, 64))
d = ImageDraw.Draw(skin)

def box(x, y, width, depth, height, color):
    # Vanilla cuboid unwrap: top, bottom, then left/front/right/back sides.
    d.rectangle((x+depth,y,x+depth+width-1,y+depth-1), fill=color)
    d.rectangle((x+depth+width,y,x+depth+2*width-1,y+depth-1), fill=color)
    d.rectangle((x,y+depth,x+2*(depth+width)-1,y+depth+height-1), fill=color)

coat=(27,29,33,255); trousers=(32,34,39,255); face=(178,139,113,255); hair=(30,24,23,255)
box(0,0,8,8,8,face)
box(16,16,8,4,12,coat)
box(40,16,4,4,12,coat)
box(32,48,4,4,12,coat)
box(0,16,4,4,12,trousers)
box(16,48,4,4,12,trousers)
# Hair crown, sides and back; the face remains visible on the head's front UV.
d.rectangle((0,0,31,9),fill=hair)
d.rectangle((0,8,7,14),fill=hair); d.rectangle((16,8,31,14),fill=hair)
d.rectangle((8,10,9,10),fill=hair); d.rectangle((14,10,15,11),fill=hair)
# Heavy eyebrows, dark eyes, nose and quiet stubble.
d.line((9,11,10,11),fill=(48,32,27,255)); d.line((13,11,14,11),fill=(48,32,27,255))
d.point((9,12),fill=(193,186,173,255)); d.point((14,12),fill=(193,186,173,255))
d.point((10,12),fill=(37,30,25,255)); d.point((13,12),fill=(37,30,25,255))
d.point((11,13),fill=(151,110,87,255)); d.line((10,15,13,15),fill=(100,73,60,255))
# Grey shirt beneath open coat, lapels, belt and shoes.
d.rectangle((22,20,25,29),fill=(82,80,77,255))
d.line((21,20,22,25),fill=(46,48,52,255)); d.line((26,20,25,25),fill=(46,48,52,255))
d.line((20,30,27,30),fill=(18,18,20,255)); d.point((24,30),fill=(118,113,102,255))
for x,y in [(0,16),(16,48)]:
    d.rectangle((x,y+14,x+15,y+15),fill=(17,17,20,255))
for x,y in [(40,16),(32,48)]:
    d.rectangle((x,y+12,x+15,y+15),fill=face)
    # Cuff around the whole wrist; a small silver watch on the front face.
    d.rectangle((x,y+11,x+15,y+11),fill=(16,17,20,255))
d.rectangle((37,58,38,59),fill=(156,152,138,255)); d.point((37,58),fill=(220,215,188,255))
# Sparse seam and cloth variation, respecting UV transparency outside the base parts.
for x0,y0,x1,y1 in [(20,21,21,29),(26,21,27,29),(44,20,47,26),(36,52,39,57),(4,20,7,27),(20,52,23,59)]:
    for y in range(y0,y1+1):
        for x in range(x0,x1+1):
            r,g,b,a=skin.getpixel((x,y)); shift=rng.randrange(-3,4)
            skin.putpixel((x,y),(max(0,r+shift),max(0,g+shift),max(0,b+shift),a))
target=Path(__file__).resolve().parents[1]/'src/main/resources/assets/heroesevolved/textures/entity/sylar.png'
target.parent.mkdir(parents=True,exist_ok=True); skin.save(target,optimize=True)
print(f'Generated original 64x64 human skin: {target.name}')
