"""Original Containment Failure score; no samples or melodies copied from Gemini: Heroes Reborn.

Reference: the user's Music/Gameplay Score/14 - Score 06 - Combat.flac (80.00 s, -16.42 dBFS,
72.2% below 250 Hz), 19 - Score 08 - Combat.flac (80.64 s, -22.35 dBFS, 61.0% below 250 Hz),
and Score 08 - Transition 01.flac (5.76 s, -18.95 dBFS). Measured 2026-11-23 with soundfile/scipy.
Their low-register weight and variations on shared stems inform our three phases and transition, never their notes.
Every oscillator, drum, chord, motif and envelope here is newly synthesized. Libraries reuse our own story synthesis.
"""
from pathlib import Path
import importlib.util
import sys, json
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'build/audio-tools'))
import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt

ROOT = Path(__file__).resolve().parents[1]
def load(name, file):
    spec=importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m
c=load('wither_cues','generate-story-cues.py')
s=load('wither_instruments','generate-sylar-music.py')
c.OUT=ROOT/'src/main/resources/assets/heroesevolved/sounds/music'
SR=c.RATE; BPM=96; BEAT=60/BPM; BAR=4*BEAT; BARS=24; LENGTH=BARS*BAR; TAIL=5
RNG=np.random.default_rng(3616)
CHORDS=[['F#2','A2','C#3'],['F#2','C#3','G3'],['D2','A2','F#3'],['G2','B2','D3'],
        ['F#2','A2','E3'],['D2','A2','C#3'],['G2','D3','A3'],['C#2','G#2','C#3']]
MOTIF=[(0,'F#4',.5),(.75,'G4',.25),(1.5,'C#5',.5),(2.75,'A4',.25),(3.25,'F#4',.75)]

def filter_low(x, hz): return sosfilt(butter(2,hz,fs=SR,output='sos'),x)
def score(phase):
    t=np.arange(round((LENGTH+TAIL)*SR))/SR; x=np.zeros_like(t)
    for b in range(BARS):
        at=b*BAR; chord=CHORDS[b%8]
        local=np.arange(round((BAR+2)*SR))/SR
        shape=np.clip(local/.18,0,1)*np.clip((BAR+2-local)/2,0,1)
        s.add(x,at,.24*c.pad(local,chord,detune=7,cutoff=650+phase*220)*shape)
        for beat in [0,1.25,2.5]:
            s.add(x,at+beat*BEAT,s.taiko(44,level=.55+.22*phase))
        if phase>=2:
            for beat in [1,3]: s.add(x,at+beat*BEAT,s.tom(94,level=.32))
            for k in range(8):
                note=chord[k%3]; length=.22
                tt=np.arange(round(length*SR))/SR
                tone=c.voice(tt,c.hz(note)*2,harmonics=5)*np.exp(-tt*16)
                s.add(x,at+k*BEAT/2,.12*tone)
        if phase==3:
            for k in range(16): s.add(x,at+k*BEAT/4,.055*s.hat())
            for beat,note,duration in MOTIF:
                local=np.arange(round((duration*BEAT+.6)*SR))/SR
                s.add(x,at+beat*BEAT,.16*c.sung(local,c.hz(note),0,duration*BEAT+.4,voices=3))
        elif b%4 in [0,2]:
            for beat,note,duration in MOTIF:
                local=np.arange(round(3*SR))/SR
                s.add(x,at+beat*BEAT,.075*c.bell(local,c.hz(note),0,decay=duration*BEAT+.3))
        # The chest: three narrow inhalations, with a space where the fourth beat should be.
        for beat in [0,.75,1.5]:
            at2=at+beat*BEAT
            breath=RNG.normal(0,1,round(.35*SR)); breath=filter_low(breath,900)
            breath*=np.sin(np.linspace(0,np.pi,len(breath)))**2
            s.add(x,at2,.045*breath)
    n=round(LENGTH*SR); out=x[:n].copy(); out[:len(x)-n]+=x[n:]
    return out

def write(name,x,target=-21,loop=False):
    if loop:
        n=len(x);tail=round(TAIL*SR)
        wet=s.fast_reverb(np.concatenate([x,np.zeros(tail)]),.2)
        x=wet[:n].copy();x[:tail]+=wet[n:]
        edge=round(.008*SR)
        x[:edge]*=np.sin(np.linspace(0,np.pi/2,edge));x[-edge:]*=np.cos(np.linspace(0,np.pi/2,edge))
    else:
        x=s.fast_reverb(x,.2)
        edge=min(round(.025*SR),len(x)//2)
        x[:edge]*=np.linspace(0,1,edge);x[-edge:]*=np.linspace(1,0,edge)
    # Normalize the complete periodic signal, leaving headroom after Ogg encoding.
    rms=np.sqrt(np.mean(x*x))+1e-12
    x*=10**(target/20)/rms
    x*=min(1,.76/(np.max(np.abs(x))+1e-12))
    path=c.OUT/f'{name}.ogg'; path.parent.mkdir(parents=True,exist_ok=True)
    with sf.SoundFile(path,'w',samplerate=SR,channels=1,format='OGG',subtype='VORBIS') as f:
        for i in range(0,len(x),4096): f.write(x[i:i+4096].astype('float32'))
    print(name,round(len(x)/SR,3), 's', 'peak',round(float(np.max(np.abs(x))),3))
    return x

if __name__=='__main__':
    phases=[write('wither_'+n,score(i),-23+i,loop=True) for i,n in enumerate(['hunger','vessel','critical'],1)]
    t=np.arange(round(5.8*SR))/SR
    stinger=.4*c.pad(t,['F#2','G2','C#3'],cutoff=1200)*c.env(t,.15,.65,5.8)
    stinger+=.4*c.thump(t,0,f=43,knock=280)
    stinger+=.18*c.sung(t,c.hz('C#5'),.2,4.6,voices=4)
    write('wither_stinger',stinger,-22)
    t=np.arange(round(6*SR))/SR
    ending=.45*c.pad(t,['F#2','C#3','F#3'],cutoff=850)*c.env(t,.03,.4,6)
    ending+=.2*c.bell(t,c.hz('F#5'),.25,2)
    write('wither_last_chord',ending,-24)
    write('wither_record',np.concatenate([p[:round(20*SR)] for p in phases]),-21)
