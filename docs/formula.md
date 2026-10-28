# The Formula

Story sources: [the Formula](https://heroes.fandom.com/wiki/Formula) and
[Claire Bennett](https://heroes.fandom.com/wiki/Claire_Bennett). In Heroes, Pinehearst built a
Formula that could give people abilities, and Claire's regenerating blood was the key ingredient.
Here, a player with no power can brew it and inject it to awaken one of the **unclaimed** powers.

The one-owner-per-power rule stays intact. The Formula only ever awakens a power nobody owns, and
it uses the same ownership, awakening intro and advancement as a natural awakening. Numbers below
are config defaults.

## The chain

```text
Empty Syringe ──(use on the Regeneration owner)──► Regenerative Blood Sample
                                                          │
                                     Centrifuge ─────────┘──► Isolated Catalyst
Water Bottle ─► Buffered Saline ─► Unstable Compound ──┐
  (+ Bone Meal)    (+ Helixite Shard)                  ├─► The Formula
                                    Isolated Catalyst ─┘     (Synthesis Station)
The Formula + an Empty Syringe in your inventory ──(use)──► injection
```

## Crafting

| Item | Recipe |
| --- | --- |
| **Empty Syringe** ×2 | Glass pane, glass bottle and iron nugget in a diagonal |
| **Centrifuge** | 2 **Helixite Shards** in the top corners, 3 iron ingots around a redstone comparator, on 3 smooth stone |
| **Synthesis Station** | 2 glass and 1 **Helixite Shard** on top, a brewing stand in the middle, surrounded by 5 copper ingots |

A full lab costs **3 Helixite Shards**, and every three Formulas cost one more (see below).
Helixite is about a quarter as common as diamonds; see [Pyrokinesis and Helixite](pyrokinesis.md#helixite).

## Getting blood

Only the **Regeneration** owner's blood works. This is the moral choice at the heart of the system.

**Donated (85% success):**

- The Regeneration owner **crouches** while you use an Empty Syringe on them.
- Or they use a syringe on themselves.
- Or they hold an empty syringe while casting **Blood Gift**, which bottles the blood instead of healing someone.

A donation costs the donor 4 hearts, which their regeneration grows back. It also earns the
**Save the Cheerleader** advancement.

**Forced (60% success):** a non-consenting owner can only be drained while helpless:

- below 30% health
- frozen solid by Cryokinesis
- held in Telekinesis Grip
- paused by Selective Stasis or Still World
- asleep

A forced draw deals 6 damage and leaves the owner **Drained** for 30 seconds: Weakness II, and their
passive regeneration stops. They're told who did it.

**Limits on forced draws:**

- Each Regeneration owner can be forced at most once every **10 minutes**. The timer survives logout and death.
- Forced draws follow the server's PvP setting and scoreboard teams.
- Server owners can turn them off entirely with `formula.allowForcedDraws = false`.

**Other sources:** saving the Hourglass Annex gives each rescuer one donated sample, the first time
only. That keeps a path open on servers where no one owns Regeneration.

A blood sample can also be injected directly (use it) for 6 health and 10 seconds of Regeneration II.
That spends it, though.

## The lab

**Centrifuge** (uses the furnace layout):

- Put the sample on top and **redstone dust** below.
- One redstone powers one 20-second spin. The rotor glows teal while spinning.
- Out comes an **Isolated Catalyst** that remembers whose blood it is and how it was taken.

**Synthesis Station** (uses the brewing stand layout):

- Three bottle slots, an ingredient slot and **glowstone dust** fuel. One glowstone powers 8 stages.
- Each stage takes 20 seconds and converts up to three bottles with one ingredient:
  1. Water Bottle + **Bone Meal** = Buffered Saline
  2. Buffered Saline + **Helixite Shard** = Unstable Compound
  3. Unstable Compound + **Isolated Catalyst** = **The Formula**

So one blood sample and one Helixite Shard can make up to three Formulas. Each vial's tooltip shows
its donor, how the blood was taken, and its chance of success.

**Automation:** both blocks work with hoppers and item pipes.

| Block | Top | Sides | Bottom (outputs) |
| --- | --- | --- | --- |
| Centrifuge | Sample | Redstone | Catalyst |
| Synthesis Station | Ingredient | Bottles and glowstone | Finished bottles only |

Every completed catalyst or Formula draws Sylar's attention to any powered player within 16 blocks.

## Injection

Hold **The Formula** with an **Empty Syringe** anywhere in your inventory:

- **Use** it to inject yourself.
- **Use it on a player who is crouching** to inject them. They have to consent.

| Situation | Result |
| --- | --- |
| No power, unclaimed powers remain, the roll succeeds | A random **unclaimed** power awakens immediately, with its full intro. The ownership is permanent, exactly like a natural awakening. Earns **The Formula** advancement. |
| You hold a **dormant** power (the Eclipse chose you, but it hasn't woken) | On success, the Formula wakes *that* power, not a new one. |
| The roll fails | Rejection: 30 seconds of Nausea and Weakness, plus Poison (never lethal). The vial and syringe are used up. |
| You already have an awakened power | Your body rejects it: up to 4 damage plus sickness, and the dose is spent. Powers don't stack. |
| Every power is already claimed | Nothing happens, and **you keep the vial**. |
| During an Eclipse | Nothing happens: *"The Formula is inert under the black sun."* |

**Success chance:** 85% for donated blood or 60% for forced, plus 2% for each level the donor had
above 1. For example, donated blood from a level 5 Regeneration owner has a 93% chance.

**Lore flag:** a player awakened by the Formula is marked as Formula-born, so later story content
can treat them differently.

## Config (`heroesevolved-common.toml`, section `formula`)

| Setting | Default |
| --- | --- |
| `allowForcedDraws` | `true` |
| `forcedDrawCooldownSeconds` | `600` |
| `donatedSuccessChance` | `0.85` |
| `forcedSuccessChance` | `0.60` |

## Not yet included

- No admin commands for the Formula. Use the creative tab, which has donated and forced variants of each sample, catalyst and vial.
- The two lab blocks reuse the vanilla furnace and brewing stand screen art. They don't have custom GUI artwork yet.

Regenerate the item, block and effect textures with `python3 tools/generate-formula-textures.py`.
