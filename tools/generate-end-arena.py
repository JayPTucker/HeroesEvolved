"""Original temporary End grounding ruins and refuge. Rebuild from this generator, not by editing their blueprints."""
from pathlib import Path
from blueprint_draft import StateGrid
R=Path(__file__).resolve().parents[1]/'src/main/resources/data/heroesevolved/blueprints'

def altar():
    g=StateGrid(11,12,11,fill='minecraft:air')
    def put(x,y,z,s):g.put(x+5,y,z+5,s if s.startswith('minecraft:') else 'minecraft:'+s)
    for x in range(-5,6):
        for z in range(-5,6):
            if abs(x)+abs(z)>8:continue
            for y in range(2):put(x,y,z,'end_stone_bricks' if (x+z)%5 else 'end_stone')
            put(x,2,z,'purpur_block' if abs(x)==4 or abs(z)==4 else 'smooth_sandstone' if abs(x)<=1 or abs(z)<=1 else 'end_stone_bricks')
    for x,z in [(-4,0),(4,0),(0,-4),(0,4)]:put(x,2,z,'sea_lantern')
    for x,z,height in [(-3,-3,8),(3,-3,10),(-3,3,6),(3,3,7)]:
        for dx in [-1,0,1]:
            for dz in [-1,0,1]:put(x+dx,3,z+dz,'purpur_block')
        for y in range(4,height):put(x,y,z,'purpur_pillar[axis=y]')
        put(x,height,z,'end_stone_bricks')
        put(x,height+1,z,'sea_lantern')
    for y in range(3,5):put(0,y,0,'chiseled_sandstone')
    put(0,5,0,'sea_lantern');put(0,6,0,'purple_stained_glass @focus')
    for x,z in [(-1,0),(1,0),(0,-1),(0,1)]:put(x,5,z,'purpur_block')
    put(0,3,3,'air @plate');put(0,3,-3,'air @plate')
    g.write(R/'end_grounding_altar.blueprint',name='end_grounding_altar',anchor=(5,3,5),notes=['Temporary, snapshot-restored ruin. Focus is anchor+(0,3,0). Clear approach from both sides.'])

def refuge():
    g=StateGrid(25,13,21,fill='minecraft:air')
    def put(x,y,z,s):g.put(x+12,y,z+10,'minecraft:'+s)
    for x in range(-12,13):
        for z in range(-10,11):
            if abs(x)>10 and abs(z)>8:continue
            for y in range(3):put(x,y,z,'end_stone_bricks' if (x+z+y)%7 else 'end_stone')
            put(x,3,z,'smooth_sandstone' if abs(z)<=1 or abs(x)<=1 else 'purpur_block' if abs(x)==10 or abs(z)==8 else 'end_stone_bricks')
    for x in [-9,-4,0,4,9]:
        for z in [-7,-2,3,8]:put(x,3,z,'sea_lantern')
    for x in [-9,9]:
        for z in [-7,0,7]:
            for y in range(4,10):
                for dx in [-1,0,1]:put(x+dx,y,z,'end_stone_bricks' if dx else 'purpur_pillar[axis=y]')
            for dx in [-1,0,1]:
                for dz in [-1,0,1]:put(x+dx,10,z+dz,'purpur_block')
            put(x,11,z,'sea_lantern')
    for x in range(-8,9):
        for y in [4,5,6,7,8]:
            if abs(x)>3:put(x,y,-7,'end_stone_bricks')
    for x in range(-4,5):put(x,9,-7,'purpur_block')
    for x in [-6,6]:
        put(x,4,4,'chiseled_sandstone');put(x,5,4,'sea_lantern')
    put(0,4,0,'air @return');put(0,4,5,'air @rally')
    g.write(R/'end_refuge.blueprint',name='end_refuge',anchor=(12,4,10),notes=['Temporary refuge; rooted podium, broken colonnade and three-wide exit arch. Snapshot every cell; never replace player blocks.'])

if __name__=='__main__':altar();refuge()
