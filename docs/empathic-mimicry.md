# Empathic Mimicry (Peter Petrelli)

An empath feels the powers of the people around them and borrows them. Copying never takes anything from
anyone, never claims a player's power, and a mimic can't copy Mimicry itself.

## Three phases

| Power Level | Phase | What you carry |
| --- | --- | --- |
| 1 to 5 | **Near** | One power at a time: the power of whoever you were last near. |
| 6 to 14 | **Kept** | The near power, plus **two** you keep by touch. |
| 15 to 20 | **Remembered** | The near power, plus **three** kept (four at Evolved), and every copied kit is complete. |

- **Being near someone.** Stay within 10 blocks of anyone with a power, in sight, for two seconds and gold
  threads draw tight between you; then their power is yours. It becomes your **near** set, replacing the last
  one. This works at every level. You don't attune to someone whose set you already keep.
- **Touching someone.** Hit them, or right-click them with an empty hand (not sneaking: sneak + use is a carry).
  Below Level 6 a touch attunes at once. From Level 6 it **keeps** their set and channels it; when you are full,
  the oldest kept set lets go. A kept set remembers who it came from.
- **Who has a power:** players, **gifted villagers** (below), the radioactive villager of a meltdown, Sylar (you
  get one of the powers he has stolen) and Elle Bishop (Electrokinesis).

## Echoes: your five keys

Your five ability keys are **Echo I to V**: the channeled set's five abilities, on the same keys its owner uses.
They open at your own Power Levels **1, 3, 6, 10 and 15**, so a low-level mimic has two of a copied kit and a
Level 15 mimic has all of it.

- Echoes run one level below yours (the Overcharge skill makes the next one full strength), with the copied
  ability's stamina cost and **1.5×** its cooldown.
- The ability wheel, the key list and the HUD show the copied abilities' real names and icons.
- A held or sustained copy (a borrowed flight, a Fade, a fire channel) ends cleanly when you switch sets, change
  dimension, log out or lose the power.

## The memory wheel

**Hold B** (rebind under Heroes Evolved in Controls) to open the memory wheel. Your near set sits at the top,
your kept sets follow, then the empty places still to fill (or the Level that opens them).

- Each set shows its power, the face of whoever it came from (or a mark: an emerald for a villager, a clock for
  Sylar, a lightning rod for Elle) and a rim in that power's colour. The channeled set's rim breathes.
- **Flick and let go** to channel a set. **Right-click** a kept set to let it go.
- **Tap B** to step to the next set without opening the wheel.

## Gifted villagers

A few villagers (4%, config `giftedVillagerChance`) are born with a quiet power of their own. They never use it,
and it claims nothing. Anyone may notice a stray mote drift off one now and then; a mimic within 24 blocks sees
them glow. Admins can gift the nearest villager with `/heroesevolved mimic gift <power>`.

## Look and sound

The empath's look is a heartbeat made visible, in gold (`E8C27A` thread, `FFF6E0` core, `6B4422` halo), with each
borrowed power shown in its own colour:

- **Attuning:** three gold threads sag between you and them, drawing tighter as it builds, with a bead of light
  running down them in a heartbeat's rhythm and a small ring of their power's colour at their chest. Heartbeats
  quicken.
- **Taking a set:** the thread snaps straight, a pulse rushes into your chest, and their colour blooms out of it
  in rings. A touch also leaves a handprint of light where your palm met them, and your arm lays an open palm
  forward while the other hand rests at your heart.
- **Switching:** a hand drawn to the heart, then opened outward; the gold ring is drawn in, and the new power's
  colour opens out.
- Threads fade where they pass close to the camera, so a front view or your own first-person view isn't flooded.

## Skill tree

| Branch | Active skill | Technique, then upgrade, then capstone |
| --- | --- | --- |
| Echo Hunter | Borrowed Strike | **Sense Powers** (everyone with a power within 48 blocks glows), then Quick Study (feel powers from 20 blocks, through walls), then **Sylar's Mirror** (touching Sylar keeps everything he has stolen that you have room for, and you can keep one more set) |
| Resonance | Empathic Surge | **Overcharge** (next Echo at full level), then Twin Echo (it recovers twice as fast), then **Peter's Burden** (Echoes cost 25% less) |
| Empathy | Calm | **Absorb Pain** (take a friend's harmful effects at half duration and heal them), then Shared Healing, then **Empath's Calm** (immune to poison and wither) |

**Passive:** near anyone with a power you regain stamina. Overexertion scrambles which set answers.

## Older saves

A mimic's old memory (a plain list of powers) loads as kept sets, still channeling the same power. The old
Empathize and Recall keys are gone: being near someone, touching them and the memory wheel replace them.

## Previewing

```bash
./gradlew runClient --init-script tools/mimicry-preview.init.gradle
```

stages gifted villagers, lets an attunement run, touches, switches and opens both wheels, and saves screenshots in
`build/mimicry-preview/screenshots/`. The gameplay checks are in
`src/gametest/java/com/jayptucker/heroesevolved/mimicry/MimicryGameTests.java`.
