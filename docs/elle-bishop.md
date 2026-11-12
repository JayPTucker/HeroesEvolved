# Elle Bishop: the Company comes calling

Character source: [Elle Bishop](https://heroes.fandom.com/wiki/Elle_Bishop). This adapts the Company's electric
field agent: cheerful, cruel, and sent to test what you can do. Her lines, look, arena, numbers and moves are
original game adaptations. The [Company's agents](company-agents.md) are the same organisation.

She is a boss in the style of a dungeon boss: her own arena, a telegraph for every attack, phases at set health, a
rule for beating her, a boss bar, and a drop of her own.

## The day before: omens

The Company notices a player who has had a power for a few days (three Minecraft days after the relay rescue, by
default). For the **whole day before she comes**, the grid around that player goes wrong. It starts rarely, about
once every half a minute, and by evening it happens every few seconds:

| Omen | What you notice |
| --- | --- |
| **Lights flicker** | Redstone lamps and copper bulbs near you stutter off and on (off, on, off for longer) and settle back. A tiny crackle of current shows at each one. |
| **Redstone misfires** | A circuit near you dies for about a second (doors and pistons drop, lamps blink out, contraptions stop), then wakes. A note block plays on its own; a door or trapdoor swings open and shuts again. |
| **Machines switch themselves on and off** | Forge Energy machines (Mekanism, Thermal and the rest) and Create machines stop for a few seconds, then run again. Cold furnaces flare up and lit ones go dark, then go back. |
| *(nothing wired nearby)* | The air hums like a power line. |

The first time each kind happens, a line of narration says so ("The lights flicker. Nobody touched them."). Late in
the day **Elle speaks on the emergency band** ([101.10 FM](emergency-band.md)): "Testing, testing... is this thing
on? Don't go far. I'll be there tomorrow." Without a radio you still get her line.

Omens never break, place, drain or use up anything. Everything goes back exactly as it was, and they only touch
blocks you could use yourself (spawn protection and the world border are respected).

## Her arrival

When the day runs out, wherever you are, **every light for two dozen blocks goes out at once**. In the dark, Elle
steps out of a ring of current a few blocks in front of you, crackling from her feet up. "There you are." A
second later a dart takes you in the neck, and you wake on **Primatech's test floor**. Anyone within 16 blocks comes
with you.

## The test floor

A pocket dimension of its own (`heroesevolved:primatech_substation`), rebuilt from the `primatech_substation`
blueprint before every fight, so anything a fight broke is whole again.

- **A square hall**, 25 blocks across and 9 high: deepslate walls with pilasters, a measured grid in the floor,
  copper pipes along the walls and chains hanging from the beams.
- **Six capacitor banks** stand against the walls (two north, two south, one east, one west). Each is iron with a
  copper top, two copper bulbs that show it is live, a lightning rod, and a **breaker lever** at chest height.
- **Copper conduits** run in the floor from under each breaker to the **pylon** in the middle: an iron column under a
  copper coil, on a copper pad.
- **Four black rubber mats** (3 by 3) on the floor. They are the only floor the current can't reach.
- **An observation booth** behind tinted glass over the north wall. Someone is watching. The blast door in the
  south wall is sealed.

A **boss bar** shows her health (200) to everyone within 48 blocks.

## The counterplay: the banks

Every live bank feeds her:

- **Her shield.** Each live bank takes **10%** off every hit on her. With all six live, a hit lands at 40%. You see
  it as one upright arc of current per live bank, turning round her.
- **Pull a breaker** and that bank is **cut off**: its lights die with a burst of sparks and smoke, her shield
  thins, and its conduit stops surging. You can close it again.
- **Her siphon is your chance.** Sometimes she reaches out and **drinks a bank**: a copper arc runs from the bank
  into her chest and a ring round its breaker fills over three seconds. "She's drinking bank N. Pull its breaker!"
  - **Pull that breaker before the ring closes** and the current comes back up the arc into her: a **backfire**.
    She takes 16% of her health straight through her shield, is **stunned for three seconds** (every hit does a
    quarter more), and the bank is **blown** for the rest of the fight, whatever its lever says.
  - Let it finish and she **heals 6%** and her next two attacks are **overcharged** (30% harder).
- **Powers short banks too.** A pulse from your own power over a bank (Electrokinesis's Blackout, Radiation's EMP)
  cuts it off for as long as the field lasts, and counts as pulling its breaker if she is drinking from it.

So the choice is yours: take banks out early to thin her shield and quiet the floor, or leave them live and wait
for a siphon you can turn against her.

## Three phases

| Phase | Health | What changes |
| --- | --- | --- |
| **Field Test** (blue bar) | 100–65% | Arc bolts, ground current and siphons. The pylon bites anyone touching its pad while any bank is live. |
| **Overcharged** (yellow) | 65–30% | **The conduits surge** one after another. She **rolls ball lightning** and **forces cut banks back on**. She rests less between moves. |
| **Blackout** (white) | 30–0% | **The lights go out**: the ceiling bulbs flare and die one by one, leaving only the red emergency lights. **The storm comes down** from the ceiling, and current **leaks from her** into anyone within a few blocks. |

Each phase change throws everyone near her back with a burst of current across the whole floor and up to the ceiling.

## Every attack crawls before it bites

Her one idea: **current crawls before it bites**. Every telegraph is a thin, jittering filament or a creeping glow
that tightens and brightens until it lands, and what lands is the same shape, thick and white. **Pale blue is her
own current; copper is the room's.**

| Move | Phase | Telegraph | What it does | How to answer it |
| --- | --- | --- | --- | --- |
| **Arc bolt** | all | 0.8 s: she points; a thin filament runs from her hand to where you stood, wandering less as it tightens, a small ring closing where it will land | A forked bolt down that exact line: 6 damage and slowed | Step off the line |
| **Ground current** | all | 1.2 s: palms to the floor; rings race out from her feet to 6.5 blocks, the edge brightening | The floor discharges inside the ring: 7 damage and slowed | Jump, stand on a rubber mat, or get out of the ring |
| **Siphon** | all | 3 s: both hands drawing in; a copper arc from a bank to her chest, a ring filling round its breaker | Heals her 6%, overcharges her next two attacks | **Pull that bank's breaker** (the backfire) |
| **Conduit surge** | Overcharged on | 1.2 s: copper light creeps down a conduit toward the pylon, its two edges showing how far it reaches | Everyone on that strip (within 1.35 blocks of it): 5 damage | Get off the strip, or stand on rubber. A cut bank never surges |
| **Reboot** | Overcharged on | 3 s: she points at a cut bank; a ring fills round its breaker and a dashed copper circle on the floor marks it | The breaker slams shut: the bank is live again | **Stand inside the circle** (your body grounds the current) or hit her for 6 or more |
| **Ball lightning** | Overcharged on | 0.6 s: a ball forms in her hands and its lane lights on the floor | It rolls down the lane and bursts on whoever it touches (or at the end): 6 damage within 2.6 blocks | Leave the lane |
| **The storm** | Blackout | 1.5 s: a ring tightens on the floor under each of you (and two more at random), a glow gathering on the ceiling above each | Lightning from the ceiling: 8 damage within 1.9 blocks | Move off your ring |

Up close her **touch** jolts you (4 damage) and throws you off her, twice a second at most.

## How it ends

| Outcome | What happens |
| --- | --- |
| **You beat her** | "They're going to be so mad at me." **Elle's Capacitor** goes to whoever finished her (or her subject), and everyone is sent back to where they stood. The Company tries again in seven days. |
| **Everyone goes down** | She leaves. Same time next week, more or less: the Company tries again in two days. |
| **Ten minutes pass** | "That's plenty of data for one day." Everyone goes home; again in two days. |

## What she leaves: Elle's Capacitor

A cracked Primatech cell that still holds a charge. It comes with **three charges**.

- **Use** it to throw a bolt down your look (24 blocks): 8 damage, and what it hits is stunned (heavily slowed and
  weakened) for two seconds.
- **Sneak and use** it on any machine that stores Forge Energy to drink **10,000 FE** back into a charge.

## Commands and settings

```text
/heroesevolved elle omens <player>     the day of omens begins now; she arrives when it ends
/heroesevolved elle arrive <player>    the lights go out and she comes now
/heroesevolved elle status <player>    when the next visit is, and how earlier ones went
/heroesevolved elle dismiss            she leaves; everyone goes home
```

`config/heroesevolved-common.toml`, section `[elle]`:

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | The Company sends her at all. |
| `firstVisitDays` | `3` | Minecraft days with a power (after the relay rescue) before her first visit; the last is the day of omens. |
| `omenTicks` | `24000` | How long the omens run before she arrives (one Minecraft day). |
| `revisitDays` | `7` | Days after she is beaten before the next visit (two after a lost fight). |

## For developers

- Code: `story/elle/` (`ElleStoryService` schedule and endings, `CompanyOmens`, `PrimatechArena`, `PrimatechGeometry`,
  `ElleEntity`, `ElleMoves`, `ElleCapacitorItem`), art in `client/effects/setpiece/ElleArt`, renderer
  `entity/client/ElleRenderer`. Textures are drawn by `java tools/GenerateElleTextures.java`.
- The misfire uses `GridDisruption.jam` (a redstone-only field) and the machines use `GridDisruption.stall` with no
  source; see [machines](machines.md).
- Tests: `ElleGameTests` (11). Preview: `./gradlew runClient --init-script tools/elle-preview.init.gradle` writes
  shots to `build/elle-preview/screenshots/`.
