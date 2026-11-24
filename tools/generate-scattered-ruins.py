"""The four century-old surface ruins. Regenerate blueprints, then export each with blueprint.py to-nbt."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from blueprint_draft import StateGrid, painting_anchor

ROOT = Path(__file__).resolve().parents[1]

def tree(g, x, z):
    g.put(x, 0, z, 'rooted_dirt')
    for y in range(1, 7): g.put(x, y, z, 'oak_log')
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for y in range(4, 8):
                if dx*dx+dz*dz+(y-5.5)**2 <= 7 and g.get(x+dx,y,z+dz) in ('_', '.'):
                    g.put(x+dx,y,z+dz,'oak_leaves[persistent=true]')

def build(kind):
    g = StateGrid(21, 13, 21)
    studio = kind == 'isaac_studio'
    x1, z1 = (17,16) if studio else (16,15)
    # Irregular soil at the edges, with no rectangular paved platform outside the old walls.
    for x in range(2,19):
        for z in range(2,19):
            if 3 <= x <= x1 and 3 <= z <= z1:
                g.put(x,0,z,'spruce_planks' if (x+z)%7 else 'moss_block')
            elif (x-10)**2+(z-10)**2 < 75 and (x*13+z*7)%5 < 2:
                g.put(x,0,z,'coarse_dirt')
    g.box(4,1,4,x1-1,10,z1-1,'.')
    # Surviving masonry: front sheared low, taller rear and west walls. A real broken silhouette.
    for x in range(3,x1+1):
        for z in range(3,z1+1):
            if x not in (3,x1) and z not in (3,z1): continue
            if studio: height = 7+(z//3)%3 if x in (3,x1) else 4+(x//3)%4
            elif kind == 'burnt_house': height = 3+(x//3+z//3)%3
            elif kind == 'collapsed_house': height = 7-(z//3)%3 if x==3 else 2+(x//3+z//3)%4
            else: height = 9 if x<=10 and (x==3 or z in (3,z1)) else 2+(x+z)//4%3
            g.put(x,0,z,'mossy_stone_bricks')
            for y in range(1,height+1):
                wall = 'bricks' if kind != 'burnt_house' else 'mud_bricks'
                if y==1: wall='mossy_stone_bricks'
                elif y%4==1: wall='smooth_sandstone'
                g.put(x,y,z,wall)
            g.put(x,height+1,z,'moss_carpet')
    # Three-wide front threshold, generous headroom, framing and remnants of the porch.
    g.box(8,1,3,10,3,3,'.')
    for x in range(7,12):
        g.put(x,0,2,'mossy_cobblestone')
    for x in (7,11): g.box(x,1,2,x,3,2,'dark_oak_fence')
    g.box(7,4,2,11,4,3,'dark_oak_slab[type=bottom]')
    # Broad window openings keep solid lintels and piers; no isolated pane posts.
    for z in (5,10):
        if not studio: g.box(3,2,z,3,3,z+1,'.')
    for x in (5,12): g.box(x,2,z1,x+1,3,z1,'.')
    for z in (4,9,14):
        g.box(2,0,z,2,4,z,'mossy_stone_bricks')
        g.put(2,5,z,'stone_brick_slab[type=bottom]')
    # The north-east corner has fallen out completely, exposing the room through a ragged break.
    for x in range(12,x1+1):
        for z in range(3,7):
            if x in (3,x1) or z==3:
                g.box(x,2+(x+z)%2,z,x,10,z,'.')
    # Fallen roof lies against the eastern wall, away from the entrance and the central walk.
    for x in range(12,x1):
        for z in range(7,z1):
            height = 1+(x+z)//4%2
            g.box(x,1,z,x,height,z,'mossy_cobblestone' if z%3 else 'cracked_stone_bricks')
            g.put(x,height+1,z,'deepslate_tile_slab[type=bottom]')
    # Fireplace, hearth and broken flue: the highest surviving piece of each domestic ruin.
    if not studio:
        g.box(4,1,z1-2,6,2,z1-1,'cobblestone')
        g.put(5,1,z1-2,'campfire[lit=false]')
        g.box(5,3,z1-1,5,8,z1-1,'mossy_cobblestone')
        g.put(5,9,z1-1,'cobblestone_wall')
    # Furniture stays in usable corners rather than filling the path.
    g.box(5,1,5,6,1,5,'dark_oak_slab[type=top]')
    g.put(5,1,6,'spruce_stairs[facing=south]')
    loot = 'ruins_house'
    chest = (5,1,8)
    if kind == 'bennet_house':
        loot='bennet_house'
        g.box(4,5,4,10,5,z1-1,'spruce_planks')
        g.box(4,5,11,7,5,12,'.')
        for i in range(4):
            for z in (11,12):
                g.box(4+i,1,z,4+i,1+i,z,'mossy_cobblestone')
                g.put(4+i,1+i,z,'spruce_stairs[facing=east]')
        g.box(10,6,4,10,9,9,'bricks')
        g.box(5,7,3,6,8,3,'glass_pane')
        g.box(4,10,4,10,10,7,'deepslate_tile_slab[type=bottom]')
        g.put(5,6,5,'white_bed[facing=east,part=foot]')
        g.put(6,6,5,'white_bed[facing=east,part=head]')
        chest=(6,6,8)
        tree(g,15,12)
        # A car reduced to its burnt chassis; no recoverable metal blocks.
        g.box(14,0,2,17,0,3,'polished_blackstone')
        g.put(15,1,2,'moss_carpet')
    elif studio:
        loot='isaac_studio'; chest=(6,1,14)
        # The two original paintings retain complete backing walls.
        for face,left in [('east',(4,12)),('west',(16,7))]:
            variant='explosion' if face=='east' else 'eclipse'
            width,height=(6,4) if variant=='explosion' else (3,2)
            pos=painting_anchor(face,left,2,width,height)
            g.put(*pos,f'air @painting:{variant}:{face}')
        g.box(5,1,4,9,1,4,'dark_oak_slab[type=top]')
        for x in (5,7,9): g.put(x,2,4,'flower_pot')
        g.box(5,1,15,10,1,15,'dark_oak_planks')
        g.put(8,2,15,'white_carpet')
        g.box(4,7,5,7,7,13,'spruce_planks')
        for z in (5,13): g.box(7,1,z,7,6,z,'stripped_spruce_log')
        g.box(4,1,14,4,7,14,'ladder[facing=east]')
        tree(g,14,13)
    elif kind=='burnt_house':
        # Char follows the former kitchen; timber ribs survive on the cool western side.
        for z in (4,8,12): g.box(4,1,z,4,6,z,'dark_oak_log')
        g.box(4,6,4,4,6,12,'dark_oak_log')
        for x in range(10,16):
            for z in range(4,8): g.put(x,0,z,'polished_blackstone' if x>12 else 'coarse_dirt')
        tree(g,14,12)
    else: tree(g,14,12)
    g.put(*chest,f'chest[facing=south] @loot:{loot}')
    # Moss follows cracks and damp rear corners; vines have solid backing, not window bars.
    for z in (7,12):
        for y in range(4,0,-1):
            if g.get(3,y,z) in ('bricks','mud_bricks','smooth_sandstone','mossy_stone_bricks'):
                g.put(2,y,z,'vine[east=true]')
    for x in (4,6,12):
        for y in range(5,0,-1):
            if g.get(x,y,3) in ('bricks','mud_bricks','smooth_sandstone','mossy_stone_bricks'):
                g.put(x,y,2,'vine[south=true]')
    for x,z in ((6,10),(8,13),(11,5)):
        if g.get(x,1,z)=='.':
            g.put(x,0,z,'moss_block'); g.put(x,1,z,'fern')
    name='ruins_'+kind
    for y in range(1,g.h):
        for z in range(g.d):
            for x in range(g.w):
                if g.get(x,y,z)=='moss_carpet' and g.get(x,y-1,z) in ('_', '.'):
                    g.put(x,y,z,'.')
    g.write(ROOT/'src/main/resources/data/heroesevolved/blueprints'/f'{name}.blueprint',name,
            notes=['Generated by tools/generate-scattered-ruins.py; edit the generator.',
                   'A century of collapse, buried foundations and rooted woodland. Export again after regeneration.'])

if __name__ == '__main__':
    for kind in ('burnt_house','collapsed_house','bennet_house','isaac_studio'): build(kind)
