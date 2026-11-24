"""The Living Seal / Inheritance. Six original 100 BPM arrangements, all oscillators newly synthesized.

References are listening/production references only: Gemini Heroes Reborn Audio's Gameplay Score
14 - Score 06 - Combat and 19 - Score 08 - Combat, and Score 08 - Transition 01. Their measured
low-register emphasis and shared-stem escalation inform the mix. No reference samples or notes ship.
Reuses this mod's own percussion, choir and reverb instruments, never the Wither composition.
"""
from pathlib import Path
import importlib.util, sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/audio-tools'))
import numpy as np
import soundfile as sf
def module(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file))
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
w=module('end_mix','generate-wither-music.py');c=w.c;s=w.s
SR=c.RATE;BPM=100;BEAT=.6;BAR=2.4;BARS=24;LENGTH=BARS*BAR;TAIL=5
OUT=ROOT/'src/main/resources/assets/heroesevolved/sounds/music'
CHORDS=[['D2','A2','F3'],['D#2','A#2','G3'],['D2','A2','E3'],['A#1','F2','D3'],
        ['G1','D2','A#2'],['D#2','A#2','F3'],['A1','E2','A#2'],['D2','A2','D3']]
# An unresolved six-note question; the inheritance rearranges and finally resolves it.
MOTIF=[(0,'D4',.7),(.75,'D#4',.25),(1.5,'A4',.5),(2.25,'F4',.5),(3,'C5',.3),(3.5,'A#4',.5)]
NAMES=['end_tethered','end_riftstorm','end_dying_star','end_inheritance','end_collector','end_one_more_future']
def score(phase):
    x=np.zeros(round((LENGTH+TAIL)*SR));severity=(phase-1)%3+1;inherited=phase>3
    for b in range(BARS):
        at=b*BAR;chord=CHORDS[b%8];tt=np.arange(round((BAR+2)*SR))/SR
        env=np.clip(tt/.25,0,1)*np.clip((BAR+2-tt)/2,0,1)
        s.add(x,at,.20*c.pad(tt,chord,detune=9 if inherited else 5,cutoff=700+severity*250)*env)
        for beat in [0,1.5,2.75]:s.add(x,at+beat*BEAT,s.taiko(38 if inherited else 49,level=.6+.15*severity))
        if severity>1 or inherited:
            for k in range(8):
                t=np.arange(round(.3*SR))/SR
                s.add(x,at+k*BEAT/2,.14*c.voice(t,c.hz(chord[(k+int(inherited))%3])*(2 if inherited else 1),harmonics=6)*np.exp(-t*14))
            for beat in [1,3]:s.add(x,at+beat*BEAT,s.tom(112 if inherited else 80,level=.28))
        if severity==3:
            for k in range(16):s.add(x,at+k*BEAT/4,.055*s.hat())
        if b%2==0 or severity==3:
            for beat,note,duration in MOTIF:
                tt=np.arange(round((duration*BEAT+1.8)*SR))/SR
                hz=c.hz(note)*(.5 if inherited and severity==1 else 1)
                tone=c.sung(tt,hz,0,duration*BEAT+1,voices=3) if severity==3 else c.bell(tt,hz,0,decay=.8)
                s.add(x,at+((3.5-beat) if inherited else beat)*BEAT,(.13 if severity==3 else .09)*tone)
        if inherited:
            tt=np.arange(round(1.4*SR))/SR
            s.add(x,at+2*BEAT,.07*c.sung(tt,c.hz(chord[-1])*4,0,1.1,voices=2))
    n=round(LENGTH*SR);out=x[:n].copy();out[:len(x)-n]+=x[n:];return out
def write(name,x,loop=False,target=-22):
    tail=round(TAIL*SR);n=len(x)
    if loop:
        y=s.fast_reverb(np.concatenate([x,np.zeros(tail)]),.22);x=y[:n].copy();x[:tail]+=y[n:]
    else:x=s.fast_reverb(x,.22)
    edge=round((.008 if loop else .04)*SR);x[:edge]*=np.linspace(0,1,edge);x[-edge:]*=np.linspace(1,0,edge)
    x*=10**(target/20)/(np.sqrt(np.mean(x*x))+1e-12);x*=min(1,.74/(np.max(np.abs(x))+1e-12))
    OUT.mkdir(parents=True,exist_ok=True)
    with sf.SoundFile(OUT/f'{name}.ogg','w',samplerate=SR,channels=1,format='OGG',subtype='VORBIS') as f:
        for i in range(0,len(x),4096):f.write(x[i:i+4096].astype('float32'))
    print(name,round(len(x)/SR,3),flush=True);return x
if __name__=='__main__':
    phases=[write(name,score(i+1),True,-24+(i%3)) for i,name in enumerate(NAMES)]
    t=np.arange(round(9*SR))/SR
    # An apparent resolution, a missing downbeat, then a rising intake answers from behind the player.
    false=.35*c.pad(t,['D2','A2','D3'],cutoff=700)*c.env(t,.03,.45,3.8)
    false+=.13*c.bell(t,c.hz('A5'),.3,1.3)
    for at,note in [(3.4,'D#3'),(4.5,'A3'),(5.5,'A#3'),(6.5,'D#4')]:false+=.19*c.sung(t,c.hz(note),at,2.3,voices=3)
    write('end_false_victory',false,target=-23)
    t=np.arange(round(4.8*SR))/SR
    stinger=.3*c.pad(t,['D2','D#2','A3'],cutoff=1450)*c.env(t,.03,.7,4.8)+.35*c.thump(t,0,f=37,knock=260)
    write('end_phase_break',stinger,target=-23)
    t=np.arange(round(9*SR))/SR
    from scipy.signal import butter,sosfilt
    final=.24*c.pad(t,['D2','D#3','A3'],cutoff=1500)*c.env(t,.1,.8,3.6)
    for at,note in [(0,'D4'),(.8,'D#4'),(1.6,'A4'),(2.4,'D#5')]:final+=.1*c.sung(t,c.hz(note),at,3.6-at,voices=3)
    final+=.5*c.thump(t,3.6,f=29,knock=180)
    noise=np.random.default_rng(72160).normal(0,1,len(t));noise=sosfilt(butter(2,[80,3400],btype='bandpass',fs=SR,output='sos'),noise)
    final+=.38*noise*c.env(t-3.6,.015,.4,2.6)
    final+=.3*c.pad(t-3.6,['D2','A2','D3','F3'],cutoff=1500)*c.env(t-3.6,.02,.5,5.4)
    for i in range(21):final+=.028*c.bell(t,c.hz(['D4','F4','A4','C5','D5','A5','F5'][i%7]),3.6+i*.025,1.4)
    write('end_last_light',final,target=-24)
    write('end_record',np.concatenate([x[:round(8*BAR*SR)] for x in phases]),target=-22)
