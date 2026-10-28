# Plan: Pyrokinesis and the Formula

Design proposal, October 27, 2026. **Part 1 (Pyrokinesis) and the Helixite ore are implemented**; see
[Pyrokinesis and Helixite](pyrokinesis.md) for the shipped behavior, which supersedes the numbers below.
Part 2 (the Formula) is also implemented; see [The Formula](formula.md), which supersedes Part 2 below
(notably: Helixite is the Unstable Compound ingredient, one catalyst makes up to three vials, and the
Formula Vial is injected directly with a syringe in your inventory rather than crafted into a loaded syringe).

Character sources: [Meredith Gordon](https://heroes.fandom.com/wiki/Meredith_Gordon),
[Flint Gordon](https://heroes.fandom.com/wiki/Flint_Gordon),
[Pyrokinesis](https://heroes.fandom.com/wiki/Pyrokinesis),
[Claire Bennett](https://heroes.fandom.com/wiki/Claire_Bennett) and
[the Formula](https://heroes.fandom.com/wiki/Formula). Numbers below are starting
values for playtesting, and all of them go into `HeroesEvolvedConfig` and `power_balance.json`.

Decisions already made:

- The Formula only awakens **unclaimed** powers. The one-owner-per-power rule stays.
- The Formula is made with **new lab blocks**, working like brewing stands. The catalyst is
  **blood from the Regeneration owner**, either donated or taken by force.
- Pyrokinesis lights **real, spreading vanilla fire**. It respects protection, the
  `doFireTick` gamerule and a config toggle.

---

## Part 1: Pyrokinesis (`heroesevolved:pyrokinesis`)

Meredith's orange flame is the base. Flint's hotter **blue flame** is the power's high-end state.
It's the opposite of Cryokinesis, and it's the reason fire-and-ice reactions exist.

### Passive

- You are immune to fire and burning damage. From **level 3** you are also immune to lava contact.
- From level 2 your punches set targets on fire.
- Standing in fire or lava **restores stamina** slowly, the way lightning recharges Electrokinesis.
- Weaknesses: rain and water drain stamina and snuff out your Heat. Cold damage, including all
  Cryokinesis attacks, hurts you 50% more. This mirrors Cryo's existing weakness to fire.
- **Heat gauge (0–100):** using fire builds Heat, and it cools quickly when you stop. At 80 or more
  your flames turn **blue**: more damage and a stronger burn. Unlike Radiation stress, this isn't a
  loss-of-control meter. It's a reward for staying aggressive. Water knocks you out of it at once.

### Actions

| Key | Level | Action | Behavior | Start cost / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 | **Flare** | Throws a fire bolt up to 32 blocks. It sets the target on fire for 4 s and lights the block it hits. | 12 / 1.5 s |
| H | 1 | **Flame Palm** | A held flamethrower cone, 6 blocks long, up to 5 s. Uses the same hold-and-release system as the Canary Scream. It drains stamina every second and lights flammable blocks it sweeps. | 8 + 10/s |
| Z | 2 | **Ring of Fire** | A ring of real fire, radius 4, lasting 8 s. Arrows and other projectiles crossing it burn up, and enemies who walk through it catch fire. | 24 / 12 s |
| X | 3 | **Backdraft** | A fire-propelled leap along your gaze, leaving a burning trail. Cushions your landing. Uses the same collision checks as Quickstep. | 18 / 6 s |
| V | 5 | **Inferno** | Charges for 3 s, then a radius-10 fire nova with heavy damage and burning ground. Shift cancels while charging. At blue Heat it becomes a **Blue Inferno**: about 1.5x damage, plus a shockwave. | 40 / 45 s |

### Real fire

- Every fire block goes through `WorldMutationPolicy`: build permissions, spawn protection, claims
  and canceled place events. Vanilla `doFireTick` decides whether it spreads.
- New config `pyrokinesis.worldFire` (default **true**). When false, fire is temporary and never
  spreads, using the `TemporaryBlocks` approach Cryo already uses.
- No fire in the future city, Temporal Snapshot or Annex dimensions. Lighting is capped per action,
  and blocks are written to the existing rewind journal so **Rewrite the Moment can undo a fire**.
- The **Controlled Burn** node (below) lets careful players light fire that never spreads.

### Skill tree: 18 nodes in three named branches

Every node changes a rule. Each branch keeps a single plain stat node as its opener.

| Branch | Nodes (opener → techniques → upgrades → capstone) |
| --- | --- |
| **Kindler** (control) | *Steady Hands* (−10% stamina) → **Controlled Burn** (toggle: your fire never spreads or burns blocks) → **Ember Mine** (active: hide an ember that erupts when an enemy steps on it) → *Long Fuse* (two mines) → **Smoke Screen** (active: a smoke cloud that blinds mobs and breaks their targeting) → capstone **Fire Shepherd** (active: pull nearby fire blocks into one big projectile, putting them out. It doubles as **firefighting**) |
| **Blue Flame** (force) | *Stoke* (+Heat gain) → **Blue Lance** (active: a piercing blue jet that always counts as blue flame) → *Slag* (burning enemies take more armor-piercing damage) → *White Heat* (blue state starts at 60 Heat) → **Melt** (Flare melts ice, snow and Cryo constructs, and boils water) → capstone **Flint's Fury** (Inferno is always blue and gains +3 radius) |
| **Phoenix** (survival) | *Warm Blood* (−10% cooldowns) → **Heat Soak** (active: absorb fire within 6 blocks to heal and refill stamina) → **Thaw** (active: cure freezing and chill on yourself and allies, a direct counter to Cryo) → *Ember Heart* (once per 60 s, dropping below 3 hearts triggers a fire burst that knocks enemies back) → *Ash Walker* (no fall damage after Backdraft) → capstone **Pillar of Flame** (Backdraft can be held to hover on a fire jet for up to 4 s) |

Only one capstone at a time, with the same respecialization and training-point rules as the other
expanded trees.

### New shared reactions (extending `PowerReactions`)

| Reaction | Setup → trigger | Result |
| --- | --- | --- |
| **Thermal Shock** | Cryo freezes a target → any Pyro hit | Uses up the freeze. A steam explosion deals heavy damage and briefly blinds anything nearby. |
| **Steam Veil** | Fire hits water, or ice from Rime Loom / Ice Wall | A steam cloud that blocks mob sight lines for 6 s. A stealth or escape tool. |
| **Firestorm** | Flight's Cyclone or Superspeed's Whirlwind passes through a Ring of Fire | The vortex catches fire for its remaining time and burns everything it pulls in. |

These follow the existing rules: each one consumes its setup, can't trigger another reaction, has
target limits, and grants a two-point discovery reward the first time.

### Awakening and story hooks

- **Intro:** the first flame is uncontrolled. Both hands ignite over 2.5 s, then a ring of fire bursts
  out (radius 8, real fire, footing protected). The owner is protected, as in the other intros.
- **Sylar** can learn it: his copied variant throws fireballs.
- **Eclipse** suppresses it as normal. Unclaimed Pyrokinesis joins the Eclipse pool automatically.
- **Effects:** new effect kinds `FIRE_BOLT`, `FIRE_STREAM`, `FIRE_RING`, `FIRE_NOVA`, `BLUE_FLAME`,
  `STEAM` and `PYRO_AWAKEN_CHARGE/RELEASE`. They're added at the **end** of the `Kind` enum because the
  payload sends the enum's position, and the network protocol goes from 26 to 27. Sounds are
  vanilla (blaze shoot, fire charge, extinguish) or original generated audio. No ripped clips.

---

## Part 2: The Formula

In Heroes, Pinehearst built its Formula with Claire's blood as the catalyst. Here, a player with no
power brews a Formula and injects it to awaken one of the **unclaimed** powers. It's another way into
the roster besides waiting for an Eclipse or a near-death awakening.

### The chain at a glance

```text
Empty Syringe ──(use on Regeneration owner)──► Regenerative Blood Sample
                                                      │
                                          Centrifuge ─┘──► Isolated Catalyst
Water Bottle ─► Buffered Saline ─► Unstable Compound ─┐
                 (Synthesis Station)                   ├─► Formula Vial
                                     Isolated Catalyst ┘
Formula Vial + Empty Syringe ─(craft)─► Loaded Formula Syringe ─(use)─► injection
```

### New items

| Item | Made from | Notes |
| --- | --- | --- |
| **Empty Syringe** | Glass pane + iron nugget + Glass Bottle (crafting table, yields 2) | Draws blood, and is loaded with the Formula |
| **Regenerative Blood Sample** | Syringe used on the Regeneration owner | Remembers its **purity** (Donated / Forced) and the donor's power level |
| **Isolated Catalyst** | Centrifuge output | Keeps purity. Doesn't stack, so every vial needs its own catalyst |
| **Buffered Saline**, **Unstable Compound** | Synthesis Station stages | In-between bottles |
| **Formula Vial**, **Loaded Formula Syringe** | Final stages | The syringe is what you actually use |

### Getting blood: donated vs. forced

This is the moral choice at the heart of the system, the way Claire was treated in the show.

- **Donated:** the Regeneration owner **crouches** while another player uses a syringe on them, or
  crouches and uses one on themself. It costs 4 hearts, which regeneration heals as usual, plus a
  short donation cooldown. The sample is **Donated** (high purity). The donor gets a thank-you
  message and an advancement, "Save the Cheerleader."
- **Forced:** the owner isn't consenting, so the draw only works if they're **helpless**: below
  30% health, frozen solid, held in Telekinesis Grip or Selective Stasis, caught in Still World,
  controlled by Puppet, or sleeping. The draw does 6 damage, applies **Drained** (Weakness, and
  regeneration paused for 30 s) and tells the victim who did it. The sample is **Forced** (lower purity).
- **Anti-harassment:** each Regeneration owner can be forced at most once every 10 minutes
  (configurable, and it survives restarts). Forced draws respect PvP and scoreboard-team rules, and
  can be turned off entirely with `formula.allowForcedDraws`.
- **Blood Gift tie-in:** a Regeneration owner holding an empty syringe while using Blood Gift fills
  it as a Donated sample instead of healing a target.
- A blood sample can also be injected directly for a short burst of strong regeneration. That's
  canon (Claire's blood healed others) and makes donations worth something on their own.

### New blocks (two, both working like brewing stands)

| Block | Recipe | Slots | Fuel | Process |
| --- | --- | --- | --- | --- |
| **Centrifuge** | Iron ingots, a redstone comparator, smooth stone | 1 sample in, 1 catalyst out | Redstone dust (1 per spin) | 20 s with a spinning animation. Blood Sample → Isolated Catalyst |
| **Synthesis Station** | Brewing stand, copper ingots, glass, an amethyst shard | 3 bottle slots plus 1 ingredient slot, like a brewing stand | Glowstone dust | Each stage takes 20 s. Water + **bone meal** → Buffered Saline. Saline + **glowstone + amethyst shard** → Unstable Compound. Compound + **Isolated Catalyst** → Formula Vial |

Both get their own menu screen, block entity, hopper support, saved progress and a Helix-marked
texture generated by a script in `tools/`, matching the Sylar skin and smoke generators.

### Injection

You can inject yourself, or a **crouching** (consenting) player.

1. **Already powered:** the body rejects it. You get Nausea and Poison, take 4 damage and lose the
   vial. The Formula can't stack powers.
2. **No unclaimed powers left:** it refuses and **keeps the item**: *"There's nothing left in you to awaken."*
3. **Otherwise** it rolls for success:
   - Donated catalyst: **85%**. Forced catalyst: **60%**. Each donor level above 1 adds 2%.
   - **Success:** pick a random unclaimed power, assign it and awaken it through the normal path
     (`assignDormantAbility` then `unlockAbility` then `onAwaken`). The full first-acquisition
     intro plays and ownership is claimed **permanently**, as with any natural awakening.
     Formula-born owners carry a lore flag, so later story beats can mention Pinehearst.
   - **Rejection:** 30 s of Nausea, Weakness and Poison, never lethal (stops at 1 heart). The vial is used up.
4. Injecting during an **Eclipse** always fails: *"The formula is inert under the black sun."*

Because the Formula goes through the existing ownership service, Eclipse rolls, admin grants and
ownership persistence all stay correct without changes.

### Story and admin hooks

- **Sylar's attention:** each Centrifuge or Synthesis Station run raises his interest in nearby
  players, and he may stalk a Regeneration owner who has been drained repeatedly.
- **Hourglass Annex:** a successful rescue also rewards one Regenerative Blood Sample (Donated),
  so servers without an active Regeneration owner still have a way in.
- Commands: `/heroesevolved formula give <player> <item>`,
  `/heroesevolved formula drawcooldown <player> reset`.
- Config: success chances, the forced-draw toggle and cooldown, donation damage, process times.

---

## Build order

Each milestone builds and passes the existing gametest suite before the next one starts.

1. **Items and blocks foundation.** Add the first `ModItems`, `ModBlocks`, `ModBlockEntities`
   and `ModMenus` registries, plus a creative tab. The mod has no item or block registries yet.
2. **Pyrokinesis core.** `PyrokinesisAbility`, `PyroService`, the passive and Heat, five actions,
   real-fire policy, effects, config and balance entries, language entries and the awakening intro.
3. **Pyrokinesis tree and reactions.** 18 nodes in `skill_trees.json`, the mechanics in
   `ExpansionSkills`, and the three reactions. `verify-skill-content.py` updated.
4. **Formula: blood and syringes.** Donated and forced draws, purity data, the Drained effect and the
   saved forced-draw cooldown.
5. **Formula: lab blocks.** Centrifuge and Synthesis Station: block entities, menus, screens, fuel,
   hoppers, recipes, models and generated textures.
6. **Formula: injection and hooks.** Outcome rolls, the ownership path, Eclipse refusal, Annex
   reward, Sylar attention, commands and advancements.
7. **Docs and verification.** A player guide (`docs/pyrokinesis.md`, `docs/formula.md`), README
   updates and the stale-count fixes ("eleven"/"six powers"), then a full build.

### Tests to add (gametest)

- Pyro: fire and lava immunity by level, stamina from fire, water draining Heat, blue state,
  Flare lighting blocks, real fire refused in protected areas, the Controlled Burn no-spread toggle,
  Ring of Fire burning projectiles, Inferno cancel, Rewind restoring burned blocks, and all three reactions
  using up their setups without recursion.
- Formula: a donated draw needs crouching, a forced draw needs a helpless target, the forced cooldown
  survives a restart, the purity of both paths, the Centrifuge and Station finish and save their
  progress, a powered user is rejected, a full roster keeps the item, Eclipse refuses, and success
  claims ownership and plays the intro exactly once.

### Not covered by automated checks

Fire spreading feel, Heat pacing, lab block textures and animations, and multiplayer balance
between forced draws and Regeneration owners all need an in-game playtest.
