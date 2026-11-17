# Meltdowns, friction and the suits that keep them off

Someone melting down is now dangerous to stand near, and a speedster's top speeds are dangerous to run at. Two suits,
made at a new bench from a new metal, keep it off. The code is in `hazard/` (suits, the bench, burning bodies),
`radiation/MeltdownField` (what pours off a meltdown), `radiation/RadiationDeaths` (bodies burning away) and
`speed/SpeedFriction`.

## What a meltdown does to everything round it

While a radioactive player's Critical Mass charges (voluntary or not) and while a villager's meltdown counts down, the
heat builds with the countdown:

| | At the start | At the end |
| --- | --- | --- |
| **Shock waves** roll out from them, throwing people back, burning them and dosing them | every 4½ s, 6 blocks, weak | every ¾ s, 22 blocks, hard enough to kill |
| **The near-field**: anyone unprotected inside it burns, takes Radiation Sickness and catches fire | 3 blocks | 12 blocks, lethal in seconds |
| **The ground** decays: blocks crack and crumble away, grass scorches to dirt, plants and leaves burn, fires start, water boils off, snow and ice melt, sand fuses to glass | 2 blocks, slowly | 10 blocks, fast |

- The near-field's edge is marked on the ground: a turning dashed ring of heat and green over a faint orange glow.
  Anyone without protection who comes near is told how close is too close.
- Something holding the meltdown in (a speedster's containment run, a freeze, stopped time, a radioactive player drawing
  the heat) weakens the waves to about a third, slows them to half as often, and shrinks the near-field a little.
- A player's meltdown breaks only what they could break; a villager's follows `mobGriefing`, like a creeper's. The
  block under their feet always holds.
- The waves don't throw a speedster running the containment ring, a radioactive player crouched beside them drawing the
  heat, or whoever is carrying them.
- Server owners can turn all of this off with `hazards.meltdownHazards`.

**Who survives it.** A full suit of either kind keeps all of it off (and wears as it does). Radioactive players are
immune: it is their own power. **Regeneration** users are the only ones who can stand in it without a suit: the heat
burns them, their skin peels away and grows back, and it can never take them below half a heart. Everyone else needs
the suit: a speedster to run rings round it, a cryokinetic to get close enough to freeze it (Ice Blast reaches 12
blocks, and so does the near-field at the end), anyone to carry the villager away.

## Burning up

- **Players** burning up (in a meltdown's heat, sick with radiation, or from friction) peel: their own skin flakes away
  pixel by pixel, the edges glowing then charring, over a charred body with ember-lit cracks and a skull's glowing eyes.
  It heals back slowly once the burning stops, and quickly for a Regeneration user.
- **Creatures** badly burnt or badly dosed (sickness stage 3+) shed small glowing cubes that drift up and cool to ash.
- **Anyone who dies of radiation** burns away: the body breaks apart into glowing cubes from the head down, each drifting
  up on the heat and cooling to ash, and no corpse is left. Items still drop. It counts as radiation if a meltdown's heat
  or friction did it, if a radiation power (not a punch or an arrow) did it, if radiation hurt them in the last half
  second (the nuclear blast, the fallout, the sickness), or if they were sick with it when they died.

## Friction

A speedster running faster than about **37 blocks a second** (run settings 5 and 6, or a lower one with a lot of
Momentum, and through the sound barrier) heats up, faster the faster they go. **Lightspeed** adds a burst of heat at
once. The heat shows as smoke, then flames at the feet and the skin scorching away, and past a point it burns them
every half-second until they ease off. At ordinary speeds it cools in a second or two (faster in water or rain). Blitz
and other dashes add none.

Each piece of the **velocity suit** keeps a quarter of it off; the whole suit, all of it.

**The burn meter.** Under Momentum on a speedster's HUD: **Friction** fills from amber to orange to a flashing red
"Burning up! Ease off", with notches where the skin starts to scorch (50) and where friction starts to hurt (70). Under
it, **Skin** shows how much of your skin has burnt away (from friction, a meltdown or radiation) in ember red, turning
green ("Skin regrowing") as it grows back. Each line only appears while there's something to show.

**A speedster heals.** Their body knits back half a health point every 70 ticks at tier 1 down to every 30 at tier 5
(a seventh to a third of a heart a second, on top of ordinary regeneration), once they haven't been hurt for two
seconds, aren't burning from friction or holding a meltdown in, and aren't starving; it costs hunger like ordinary
healing. Their burnt skin grows back faster than anyone's but a Regeneration user's: about 6 seconds from fully burnt at
tier 1, 3 at tier 5 (12 for anyone else, under 2 for Regeneration), and you can watch the peeled pixels fill back in. Server owners can turn
friction off with `hazards.frictionBurns`.

**Holding in a blast needs the suit.** A speedster who runs rings round someone about to go off (a radioactive player
or a villager in meltdown) without the whole velocity suit burns up on the ring for three and a half seconds, harder and
harder, then burns away, and the blast goes on: the suit is what lets the containment run work. In the suit, the hold
lasts ten seconds and builds to a climax, the run strains them less (60% of the heat), and if their body still gives
out they run on, burning, until the hold is done, then burn away taking the blast with them. Cooling a radioactive
player who is merely hot needs no suit.

## The suits

| | Radiation suit | Velocity suit |
| --- | --- | --- |
| What it is | Lead-lined, sealed with rubber (slime), with a leaded-glass visor and a charcoal respirator: a hazmat suit | A speedster's suit: aramid weave (Kevlar) over fired ceramic plates, lead-lined, open at the mouth, pale lenses, white trim |
| Pieces | Radiation Hood, Coat, Trousers, Boots | Velocity Cowl, Suit, Leggings, Boots |
| Radiation kept off | a quarter a piece; four seal you in | a quarter a piece; four seal you in (pieces of both suits add up) |
| Friction kept off | none | a quarter a piece |
| Armor | about leather's | a little better than leather's |
| Other | Heavy: each piece slows you 3%, and steadies you against shock waves | Dyeable (red by default); the white trim stays white |
| Repair | Lead ingots, at an anvil | Aramid fiber, at an anvil |

Both are worn skin-tight: their own armor model (`client/SuitModels`) inflates each box only just past the player's
own outer skin layer (0.55 of a pixel on the head, 0.42 elsewhere, 0.38 for the legs), where vanilla armor stands a full
pixel off the body.

Both wear as they absorb radiation or friction. Mixing pieces works for radiation: a velocity cowl over a radiation coat
still counts toward the four. A full suit also makes its wearer immune to Radiation Sickness and the blast's fallout,
but not to the nuclear blast itself: no suit survives ground zero.

The velocity suit is red and white, and built in 3D: swept white fins stand off the cowl's temples, a raised white
plate on the chest has a bold V (for velocity) cut through it in the suit's own colour, and a raised white belt, white
forearm bands and white boots stand off the body. The cowl leaves the mouth and chin bare. The radiation suit has a
respirator canister standing out at the mouth, an air tank on the back and thick black soles. Both are made the way the
`minecraft-armor` project skill (`.claude/skills/minecraft-armor/SKILL.md`) describes.

## The materials

Real radiation suits are lead-lined with leaded-glass visors and charcoal respirators; real heat suits are aramid
(Kevlar, Nomex) over ceramic tiles like a spacecraft's heat shield. So:

| Material | How | Stands for |
| --- | --- | --- |
| **Lead Ore** / **Deepslate Lead Ore** | Galena veins in the overworld's stone, Y −32 to 64, about as common as iron | galena, lead's ore |
| **Raw Lead** → **Lead Ingot** | Mine the ore; smelt or blast it (or the ore itself). Tagged `c:ingots/lead`, so other mods' lead works too | lead |
| **Leaded Glass** | 4 glass round 1 lead ingot makes 4. A building block too | radiation-shielding glass |
| **Aramid Fiber** | 3 string, a slime ball and blaze powder make 4 | Kevlar: a polymer, spun and heat-cured |
| **Unfired Ceramic Plate** → **Ceramic Plate** | 2 clay balls and a quartz make 2; fire them in a furnace | silica heat-shield tiles |
| **Respirator Filter** | 2 paper, charcoal and string make 2 | an activated-carbon filter |

## The Suit Fabricator

A sewing-and-pressing bench: lead ingot, shears, lead ingot over wool, a crafting table and wool over three iron ingots.
Pick a piece from the eight patterns along the top (the radiation suit, then the velocity suit, head to foot), put its
materials in the six slots in any order, and take the piece. A pattern whose materials are all in shows a green corner;
the selected pattern's materials are shown under the slots, greyed until there are enough.

| Piece | Materials |
| --- | --- |
| Radiation Hood | leather cap, 2 lead, leaded glass, respirator filter, slime ball |
| Radiation Coat | leather tunic, 4 lead, 2 slime balls |
| Radiation Trousers | leather pants, 3 lead, slime ball |
| Radiation Boots | leather boots, 2 lead, slime ball |
| Velocity Cowl | 3 aramid fiber, leaded glass, lead |
| Velocity Suit | 6 aramid fiber, 2 ceramic plates, 2 lead |
| Velocity Leggings | 5 aramid fiber, 2 ceramic plates, lead |
| Velocity Boots | 2 aramid fiber, 2 ceramic plates, lead, slime ball |

## Tests and textures

`radiation/MeltdownFieldGameTests` (who the heat hurts and how much a suit keeps off, Regeneration surviving, the waves,
telling radiation deaths apart, the Fabricator), `speed/SpeedFrictionGameTests` and the new cases in
`village/VillagerMeltdownGameTests` (a speedster without a suit burns away and the blast goes on; late-comers count;
the unloaded aren't counted dead). Every texture here is drawn by `py -3 tools/generate-suit-textures.py`.
