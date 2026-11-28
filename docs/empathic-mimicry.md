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
- **Who has a power:** players, **gifted villagers** (below), the radioactive villager of a meltdown, and Sylar (you
  get one of the powers in his collection).

## Echoes: your five keys

Your five ability keys are **Echo I to V**: the channeled set's five abilities, on the same keys its owner uses.
They open at your own Power Levels **1, 3, 6, 10 and 15**, so a low-level mimic has two of a copied kit and a
Level 15 mimic has all of it.

- Echoes run one level below yours (the Overcharge skill makes the next one full strength), with the copied
  ability's stamina cost and **1.5×** its cooldown.
- The ability wheel, the key list and the HUD show the copied abilities' real names and icons.
- A held or sustained copy (a borrowed flight, a Fade, a fire channel) ends cleanly when you switch sets, change
  dimension, log out or lose the power.

## Fighting styles

With an empty hand, Peter fights in the style of the power he is channelling (near or kept): firebending, the lightning style, waterbending or any of the seven in [fighting-styles.md](fighting-styles.md).

## The memory wheel

**Hold B** (rebind under Heroes Evolved in Controls) to open the memory wheel. Your near set sits at the top,
your kept sets follow, then the empty places still to fill (or the Level that opens them).

- Each set shows its power, the face of whoever it came from (or a mark: an emerald for a villager, a clock for
  Sylar) and a rim in that power's colour. The channeled set's rim breathes.
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
- **Your own skills** (Borrowed Strike, Empathic Surge, Calm, Sense Powers, Overcharge, Absorb Pain) speak the same
  language: a thread from your hand or heart, a heartbeat, and a bloom in the colour of the power or sickness involved
  (see "Your own skills" below).

## Skill tree

| Branch | Active skill | Technique, then upgrade, then capstone |
| --- | --- | --- |
| Echo Hunter | Borrowed Strike | **Sense Powers** (everyone with a power within 48 blocks glows), then Quick Study (feel powers from 20 blocks, through walls), then **Sylar's Mirror** (touching Sylar keeps everything he has stolen that you have room for, and you can keep one more set) |
| Resonance | Empathic Surge | **Overcharge** (next Echo at full level), then Twin Echo (it recovers twice as fast), then **Peter's Burden** (Echoes cost 25% less) |
| Empathy | Calm | **Absorb Pain** (take a friend's harmful effects at half duration and heal them), then Shared Healing, then **Empath's Calm** (immune to poison and wither) |

**Passive:** near anyone with a power you regain stamina. Overexertion scrambles which set answers.

### Your own skills

These are the empath's own, not borrowed: each is a gold thread from your hand or your heart, and the colour of whatever
power is involved where it lands. Each has its own gesture, in third and first person.

| Skill | What it does | What you see |
| --- | --- | --- |
| **Borrowed Strike** (active) | Strikes an enemy you aim at, up to 40 blocks away, for 6 + level, with a **touch of the power you are channelling**: fire burns (3 s), cold slows (2 s), lightning jumps to one more foe within 4 blocks for half, radiation sickens, and a far hand, Quake or Flight shoves it away. Other powers (or none) add nothing. | Your rear hand goes to your heart and the lead hand is thrown open at the foe like the crack of a whip. A gold thread whips out of the palm with a wave rolling along it and pulls straight as it lands; the foe blooms in the channelled power's colour (your gold with none), a ring on the plane of the blow, chips thrown back. |
| **Empathic Surge** (active) | You and every friend within 8 blocks (allied players and your pets) get Strength for eight seconds. | Both arms flung wide. Two heartbeats, lub and dub, ring out from your chest and across the ground; a gold thread snaps tight to each friend, a bead of light runs down it on each beat, and their chest blooms when it arrives. |
| **Calm** (active) | Every harmful effect on you and on friends within 6 blocks is lifted. What helps is left. | A hand flat on your heart, the other palm pressed slowly down. A soft gold ring settles outward across the ground; each lifted effect rises off its body as motes in its own colour that arc into your heart, turning gold as they near. |
| **Sense Powers** (technique) | Everyone with a power within 48 blocks (players, gifted villagers, Sylar) glows for you for eight seconds. | A hand on your heart, listening. Two heartbeats race out along the ground; as the ring passes each person it found, a ring in their power's colour flares round their chest and a thin thread of light rises off them. Only you see whom you found. |
| **Overcharge** (technique) | Your next Echo runs at your full level. | The switching gesture held a beat longer: rings in the channelled power's colour close onto both hands and flare. |
| **Absorb Pain** (technique) | Takes a friend's harmful effects onto you at half the time, and heals them for 4 (8 with Shared Healing). | Both hands reach out and draw in. Three gold threads tighten between you; each sickness runs down them into your heart as a bead of its own colour, while gold rises round their chest as they mend. |

### Signature: Chorus

Under Resonance, after Strong Echo Mastery. Let every power you remember speak at once. A **ring of
voices** circles you, one in each remembered power's colour (at least three: the echoes that come easiest fill any gaps),
for six seconds. **Every second the next voice leaps** at the nearest foe within 16 blocks for **2 + tier/2**, with a touch
of its own power: fire burns, cold slows, lightning jumps to a second foe, radiation sickens, a far hand (or Quake, or
Flight) shoves. When it ends, or when you **press again**, they **all speak together**, one bolt each for **2 + tier/4**.

## Older saves

A mimic's old memory (a plain list of powers) loads as kept sets, still channeling the same power. The old
Empathize and Recall keys are gone: being near someone, touching them and the memory wheel replace them.

## Previewing

```bash
./gradlew runClient --init-script tools/mimicry-preview.init.gradle
```

stages gifted villagers, lets an attunement run, touches, switches and opens both wheels, photographs each of your own
skills from the side and in first person, and saves screenshots in `build/mimicry-preview/screenshots/`. The gameplay
checks are in `src/gametest/java/com/jayptucker/heroesevolved/mimicry/` (`MimicryGameTests`, `MimicSkillGameTests`,
`PowerCompatibilityGameTests`).

## Finishers: Take It In, Echo, Borrowed Strength

From Power Level 3, a foe low enough to finish glistens gold. Press **C** near it (within 4 blocks). Shift + C switches finishers off and on; the rules every finisher shares are in [finishers.md](finishers.md).

- **Channeling a power,** you finish with that power's finisher (Dragon Coil, Lights Out and the rest). With nothing channeled, you have your own:
- **What you see.** Your hand goes to your heart and three gold threads sag between the foe and your chest, draw tight while you feel what it felt, and snap straight. A pulse runs along them into your chest and blooms there as the foe goes still.
- **A foe with a power** (a gifted villager, a player or Sylar) is imprinted at once: its power becomes a kept set, no touch needed.

Each press of C plays one of three at random, never the one you used last. Take It In is described above; the two alternates give the same reward.

- **Echo.** A gold outline of the foe rises in front of it, facing it, gold threads running to it from your chest, and hits the foe with its own swing. A foe with a power is imprinted, as with Take It In.
- **Borrowed Strength.** One finisher picked at random from the powers you have kept, played as that power's own, with a gold shimmer over you as it starts. It only comes up once you have kept a power, and gives no power's reward (the stamina back still comes). Channeling a power, you get that power's three instead.
