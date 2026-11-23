"""Original Wither cage skin, Fractured Star and Breach Seed icons. No external artwork."""
from pathlib import Path
import sys, json, random
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'build/title-asset-tools'))
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]; assets=ROOT/'src/main/resources/assets/heroesevolved'
rand=random.Random(3616)
im=Image.new('RGBA',(128,128)); px=im.load()
for y in range(128):
    for x in range(128):
        g=rand.randrange(60,87); px[x,y]=(g-4,g,g+1,255)
draw=ImageDraw.Draw(im)
for y in range(5,125,16):
    draw.line([(2,y),(10,y+2),(18,y-1),(27,y+3)],fill=(78,113,108),width=1)
# Actual model UVs: chipped pale ribs, two hollow eye sockets and a cracked jaw, rather than featureless cubes.
for y in range(40):
    for x in range(50,88):
        g=rand.randrange(112,153);px[x,y]=(g,g+3,g-1,255)
for y in range(49,59):
    for x in range(9,19):
        g=rand.randrange(92,125);px[x,y]=(g-4,g,g,255)
draw.rectangle((10,52,12,53),fill='#111C22');draw.rectangle((16,52,18,53),fill='#111C22')
draw.line((14,54,14,56),fill='#17242A');draw.line((11,57,17,57),fill='#17242A')
for x in [12,14,16]:draw.point((x,58),fill='#D6DED1')
draw.rectangle((24,65,36,70),fill='#91E6D0')
draw.rectangle((90,0,108,12),fill='#86D7C4');draw.line((94,0,96,9),fill='#EBFFF2',width=2)
im.save(assets/'textures/entity/rift_wither.png')
for name in ['fractured_star','breach_seed','music_disc_containment']:
    icon=Image.new('RGBA',(16,16)); d=ImageDraw.Draw(icon)
    if name=='fractured_star':
        d.polygon([(8,1),(10,5),(14,6),(11,9),(12,14),(8,11),(3,14),(4,9),(1,6),(6,5)],fill='#B9F9E7',outline='#315A63')
        d.line([(8,2),(7,7),(10,9),(7,13)],fill='#152535',width=1)
    elif name=='breach_seed':
        d.ellipse((2,1,13,14),fill='#172033',outline='#8FE8DE',width=2)
        d.ellipse((5,4,10,11),fill='#9C80C5'); d.line((8,4,7,12),fill='#E9FFF6')
    else:
        d.ellipse((1,1,14,14),fill='#1D242B',outline='#3C4C52'); d.ellipse((5,5,10,10),fill='#8AE3D7'); d.point((8,8),fill='#172033')
    icon.save(assets/f'textures/item/{name}.png')
    (assets/f'models/item/{name}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'heroesevolved:item/{name}'}},indent=2)+'\n')
