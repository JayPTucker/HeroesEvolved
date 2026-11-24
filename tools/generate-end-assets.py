"""Original finale icons, registration data, dialogue, journal and rewards. No external art or dialogue."""
from pathlib import Path
import sys,json
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'build/title-asset-tools'))
from PIL import Image,ImageDraw
A=ROOT/'src/main/resources/assets/heroesevolved';D=ROOT/'src/main/resources/data/heroesevolved'
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
for name in ['inheritance_core','anchor_of_return','worldline_sigil','music_disc_inheritance']:
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    if name=='inheritance_core':
        d.polygon([(8,0),(13,5),(14,10),(8,15),(2,10),(3,5)],fill='#3C225E',outline='#E6C987')
        d.polygon([(8,3),(11,7),(8,12),(5,7)],fill='#B18AFF');d.line([(8,3),(7,7),(10,9),(8,12)],fill='#F6EEFF')
        d.line([(1,4),(3,6),(1,9)],fill='#A7E4E8');d.line([(14,3),(12,6),(15,10)],fill='#A7E4E8')
    elif name=='anchor_of_return':
        d.ellipse((4,0,11,7),fill='#302B43',outline='#F0D18A',width=2);d.line((8,6,8,13),fill='#E7D7B1',width=2)
        d.line([(2,8),(3,12),(8,15),(13,12),(14,8)],fill='#9ADEF0',width=2);d.point((8,3),fill='#D5F8FF')
    elif name=='worldline_sigil':
        d.polygon([(8,0),(15,7),(8,15),(0,7)],fill='#25213D',outline='#D5B878')
        d.line([(4,4),(11,4),(11,11),(4,11),(4,4)],fill='#A7D9E9');d.line([(8,2),(8,13)],fill='#B48BFA');d.point((8,7),fill='#FBF1CE')
    else:
        d.ellipse((1,1,14,14),fill='#222132',outline='#5C496E');d.ellipse((4,4,11,11),fill='#A380C6');d.line((5,9,10,6),fill='#E9CD8B',width=2);d.point((8,8),fill='#201C2B')
    im.save(A/f'textures/item/{name}.png');write(A/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'heroesevolved:item/{name}'}})
# Transparent emissive layers follow the real PlayerModel UVs, including the back and both arms.
faces=[(8,8,8,8),(20,20,8,12),(32,20,8,12),(44,20,4,12),(52,20,4,12),(36,52,4,12),(44,52,4,12),(4,20,4,12),(12,20,4,12),(20,52,4,12),(28,52,4,12)]
faces.extend([(40,8,8,8),(20,36,8,12),(32,36,8,12),(44,36,4,12),(52,36,4,12),(52,52,4,12),(60,52,4,12),(4,36,4,12),(12,36,4,12),(4,52,4,12),(12,52,4,12)])
for stage in range(4):
    im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
    for i,(x,y,width,height) in enumerate(faces):
        points=[(x+width//2,y),(x+width//2-1,y+height//3),(x+width//2+1,y+height*2//3),(x+width//2,y+height-1)]
        d.line(points,fill=(170,130,255,220),width=1+stage)
        d.line(points,fill=(255,253,245,255),width=max(1,stage))
        if stage>=1:d.line([(x+width//2,y+height//3),(x,y+height//2)],fill=(218,239,255,255),width=1)
        if stage>=2:d.line([(x+width//2,y+height*2//3),(x+width-1,y+height//2)],fill=(255,244,202,255),width=1)
    im.save(A/f'textures/entity/sylar_rupture_{stage}.png')
lang=json.loads((A/'lang/en_us.json').read_text(encoding='utf-8'))
texts={
 'dragon.1':'The Living Seal — Tethered Sky','dragon.2':'The Living Seal — Riftstorm','dragon.3':'The Living Seal — Dying Star',
 'sylar.1':'Sylar — Inheritance','sylar.2':'Sylar — Collector of Worlds','sylar.3':'Sylar — One More Future',
 'absorption':'The light is moving…','release':'Sylar — The Light Breaks','complete':'The End, at last',
 'rupture':'There is too much of it.',
 'arrive':'The towers answer your presence. Face a lit altar and hold an ability key to ground the Dragon. Gold orbits shelter you from its largest discharges.',
 'grounded':'The feeding filament breaks. The Dragon is grounded — strike while the star is exposed.',
 'phase.dragon.2':'The broken seal reaches into the sky. The Dragon commits its weight to every charge.',
 'phase.dragon.3':'The star beneath its ribs opens. Find a golden orbit, the lit sector, or solid cover.',
 'phase.sylar.2':'You brought whole worlds into this fight. Let me show you what they look like together.',
 'phase.sylar.3':'I can see every ending. I only need one of them to belong to me.',
 'false_victory':'The Dragon falls. Its light stops short of the ground, then bends toward someone behind you.',
 'arrival.new':'You broke something that was keeping all of this in place. I felt it from the other side.',
 'arrival.remembered':'I remember dying in your world. There are other versions of a moment.',
 'inheritance':'That was a remarkable creature. Now, show me what you can do.',
 'interference':'His interference catches your gift for two seconds. Keep moving; the pattern has already been fixed.',
 'level':'The End answers awakened players at Power Level %s or above.',
 'channel.unavailable':'The focus does not answer yet. Follow the feeding filament or wait for its light to return.',
 'channel.stamina':'You need enough stamina to hold a complete grounding channel.',
 'prism':'Echo Prism: %s','anchor_saved':'The anchor breaks. The refuge catches you above the void.',
 'won':'The borrowed light unravels. This ending belongs to you.',
}
read={
 'quake':'The ground was never still. You simply learned to hear where it wanted to break.',
 'flight':'You think in open sky. Let us see how much room that leaves you.',
 'electrokinesis':'Every bright path has another end. I can see yours.',
 'telekinesis':'You reach without touching. So do I.',
 'telepathy':'There is a shape to every thought. Yours is very loud.',
 'cryokinesis':'Stillness, held just before it shatters. An elegant habit.',
 'radiation':'You carry a small disaster and call it restraint.',
 'sonic_resonance':'The world is a listening body. I wonder which note makes it answer.',
 'superspeed':'You are quick. The place you are going has already been chosen.',
 'regeneration':'You have learned to outlast pain. I am interested in what comes after.',
 'pyrokinesis':'You give fire a direction. Let us try giving it three.',
 'invisibility':'Empty space has edges. I can borrow those too.',
 'phasing':'You ask stone to remember a different shape. It seems willing.',
 'empathic_mimicry':'You carry the people you have met. I carry what they could have been.',
 'technopathy':'Every system believes its own instructions. That is a useful weakness.',
 'matter_assembly':'Pieces become a prison so easily. Watch the corners.',
 'time_manipulation':'You move between moments. I have learned to leave something in each one.',
 'vibe':'A world can split along a very fine line. Here is mine.',
 'shapeshifting':'So many faces, all choosing the same direction.',
 'hydrokinesis':'Water remembers the path around an obstacle. I will give it several.',
 'power_amplification':'You make everything more. Let us discover where more ends.',
}
texts.update({'read.'+k:v for k,v in read.items()})
for key,value in texts.items():lang['endgame.heroesevolved.'+key]=value
for key,value in {'inheritance_core':'Inheritance Core','anchor_of_return':'Anchor of Return','worldline_sigil':'Worldline Sigil','music_disc_inheritance':'Music Disc'}.items():lang['item.heroesevolved.'+key]=value
lang['item.heroesevolved.music_disc_inheritance.desc']='Heroes Evolved — One More Future'
lang['entity.heroesevolved.end_bolt']='Falling Constellation'
infos={
 'inheritance_core':'Won by completing The Living Seal and Sylar: Inheritance in the End. Each participant receives a core. Craft it with four End Crystals into a Worldline Sigil to challenge the entire encounter again.',
 'anchor_of_return':'Your first Inheritance victory awards this anchor. Hold it in either hand while exploring the End. A fatal fall into the void consumes it and recalls you to the refuge. It does not grant general immortality.',
 'worldline_sigil':'After an Inheritance victory, use beside the central End fountain with an awakened gift at Power Level 16 or above. It places the four crystals and starts the native Dragon respawn. The usual four-crystal ritual also works.',
 'inheritance_record':'A rare reward from repeat Inheritance victories. An original six-part arrangement of the encounter score. Play it in a jukebox or through a compatible radio.',
}
for k,v in infos.items():lang['jei.heroesevolved.info.'+k]=v
for key,title,desc,icon in [
 ('false_victory','There Is Always Someone Else','Defeat the Living Seal and witness what answers its falling light.','minecraft:dragon_breath'),
 ('inheritance','One More Future','Defeat Sylar carrying the Dragon’s inheritance.','heroesevolved:inheritance_core')]:
    lang['advancement.heroesevolved.'+key]=title;lang['advancement.heroesevolved.'+key+'.desc']=desc
    write(D/f'advancement/{key}.json',{'parent':'minecraft:end/kill_dragon' if key=='false_victory' else 'heroesevolved:false_victory','display':{'icon':{'id':icon},'title':{'translate':'advancement.heroesevolved.'+key},'description':{'translate':'advancement.heroesevolved.'+key+'.desc'},'frame':'challenge','show_toast':True,'announce_to_chat':True,'hidden':False},'criteria':{'observed':{'trigger':'minecraft:impossible'}}})
write(A/'lang/en_us.json',lang)
write(D/'recipe/worldline_sigil.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':[' C ','CIC',' C '],'key':{'C':{'item':'minecraft:end_crystal'},'I':{'item':'heroesevolved:inheritance_core'}},'result':{'id':'heroesevolved:worldline_sigil','count':1}})
sounds=json.loads((A/'sounds.json').read_text(encoding='utf-8'))
for name in ['tethered','riftstorm','dying_star','inheritance','collector','one_more_future','false_victory','phase_break','last_light']:
    sounds['end_'+name]={'sounds':[{'name':'heroesevolved:music/end_'+name,'stream':True}]}
sounds['music_disc.inheritance']={'sounds':[{'name':'heroesevolved:music/end_record','stream':True}]}
write(A/'sounds.json',sounds)
write(D/'jukebox_song/inheritance.json',{'sound_event':'heroesevolved:music_disc.inheritance','description':{'translate':'item.heroesevolved.music_disc_inheritance.desc'},'length_in_seconds':115.2,'comparator_output':15})
print('Generated four icons, rewards, recipe, score entries, advances and all 21 gift lines.')
