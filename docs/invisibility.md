# Invisibility — bend light, mislead the hunt

Invisibility combines **Exposure**, physical refraction movement, public optical doubles and
cooperative concealment. Its equipment, name tag and held items disappear with the body.
The original action IDs and eighteen skill IDs remain compatible with saved purchases and loadouts.

## Exposure and counterplay

Exposure measures how unstable the concealment is, from 0 to 100. Movement adds Exposure;
sprinting adds substantially more than careful walking. Lower-level users build it faster.
Standing still lets it settle; crouching while moving also steadies the refraction. At high
Exposure, a short visible flicker gives opponents an opening.

- An attack exposes its attacker briefly and adds Exposure. It does not reveal every companion
  inside the same veil.
- Taking damage flickers the victim visible without ending a sustained Fade.
- Entering water or rain creates a revealing splash; remaining wet makes concealment harder to
  maintain. Burning players remain visible.
- Echo Sense, Tremor Sense and other implemented reveal effects still pierce concealment.
- A distant mob loses its exact target lock when the player fades. It searches the last known
  position or investigates an optical clue. A mob within two blocks can still notice the player.
- The concealed player sees faint hand contours and an Exposure display. Other players see
  brief distortion when the concealment becomes unstable, rather than a permanent outline.

## Base actions

These are the default keys; learned active skills can replace them as usual.

| Key | Level | Action | Behavior | Base stamina / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 | **Fade** | Toggle full concealment. The silhouette separates into refracted layers before vanishing. Press again or Shift + key to reappear. | 6 / 0.5 s; then 2 stamina/s |
| H | 1 | **Refraction Step** | A six-tick physical dash along the view direction, leaving a short optical echo. Reach grows with level; swept collision and ordinary movement stop it at solid terrain. | 12 / 1.5 s |
| Z | 2 | **Mirage** | Send a public optical double down a false route for six seconds. It follows checked terrain, wears a refracted likeness of the caster and attracts nearby hostile mobs to investigate. | 16 / 6 s |
| X | 3 | **Shared Veil** | Carry a four-block-radius field for twelve seconds. Nearby consenting companions remain concealed while inside it; damage and attacks reveal each person independently. Shift + key ends the field. | 25 / 15 s; 1–4 stamina/s while maintained |
| V | 5 | **Ghost Protocol** | For eight seconds Exposure from movement builds at one fifth its normal rate. Up to three optical echoes peel away along diverging routes; new echoes replace old ones as the caster moves. Sensing powers still work. | 90 / 90 s |

Optical doubles are visible to everyone nearby. They cannot hurt anyone or provide an inventory.
Attacking or interacting with a reachable double shatters it; a projectile crossing it also breaks
it. The server validates the interaction, movement and lifetime. At most three doubles can belong
to one caster, and each checked route stops at walls or unsafe drops.

A useful sequence is **Fade → Mirage toward a doorway → Refraction Step around the other side →
Shared Veil to escort a companion**. Ghost Protocol creates a brief opportunity to escape or
change angle, with visible routes for opponents to investigate.

## Eighteen-node skill tree

The three branches grant distinct preparation, deception and rescue tools. Saved IDs, point
costs, prerequisites and the one-capstone limit are preserved; the old blanket cost, damage and
cooldown bonuses are replaced by the listed interactions.

| Branch | Opening → active → mastery | Technique → upgrade → capstone |
| --- | --- | --- |
| **Ghost** | **Soft Footfall:** careful walking builds half as much Exposure → **Vanishing Counter:** fade for three seconds and prepare one surprise melee hit for ten seconds → **Clean Break:** Exposure settles faster when nearby hunters have no line of sight | **False Footsteps:** place an audible optical clue at aimed ground so hostile mobs investigate it → **Lingering Mirage:** longer investigation and greater attraction range → **Assassin:** a fully concealed surprise hit deals triple damage; the opening is spent once and reveals its attacker |
| **Trickster** | **Double Take:** Mirage lasts one second longer → **Mirror Guard:** a double, six seconds of Fade and a brief Resistance window → **Perfect Imitation:** doubles copy the caster's attack gesture | **Sleight of Hand:** lift a mob's held item within four blocks; learned hands also operate doors or containers without the usual interaction Exposure → **Light Fingers:** item lifting reaches eight blocks → **Phantom:** take 30% less incoming damage while fully concealed; being hit still reveals you |
| **Infiltrator** | **Close Formation:** Shared Veil reaches one block farther → **Slipstream:** five seconds of Speed II and Fade to leave hunters searching the old trail → **Shared Escape:** Slipstream also conceals nearby consenting companions | **Hidden Passage:** a wall-clipped twelve-block optical corridor conceals consenting friends inside it for fifteen seconds → **Broad Passage:** corridor width grows from a two-block radius to three → **Claude's Lesson:** sustained Fade drains one stamina/s instead of two |

Hidden Passage uses the original `invisibility_cloak_field` ID; False Footsteps retains
`invisibility_smoke_and_mirrors`, and Sleight of Hand retains `invisibility_pickpocket`.
The corridor stays where it was cast, while the base Shared Veil travels with its owner.

## Effects and cleanup

The visual language is pale silver, cyan and violet refraction: layered skin silhouettes,
peeling glass-like ribbons, sparse movement distortion, optical threads and prism shards.
Mirages share a recognizable silhouette but show a refracted edge when tested. Veil boundaries
are readable without filling the area with constant particles.

Death, logout, dimension changes, power removal, suppression and immobilization end active
concealment, checked movement and optical constructions. Reveals preserve the underlying Fade
so a quiet player can settle back into concealment after the reveal window ends. Pre-existing
vanilla Invisibility potion duration is restored when the power finishes.

## Verification

The full game-test suite passes all **347 required tests**. The Invisibility regressions cover
existing aggro release, Exposure progression and settling, damage and sensing counters, shared
concealment with independent reveal, dash collision, authoritative double interactions, mob
investigation, suppression cleanup, potion restoration, borrowed Fade and saved skill identities.
The isolated live preview can capture the transition, movement, public doubles, shared veil and
Ghost Protocol effects from the actual client. Its sources are excluded from release builds.

The live client check passed Fade, Exposure movement, a real hunter searching the old location,
Mirage hit-to-shatter interaction, a physical dash stopped by a wall, the moving veil, Ghost
Protocol's three echoes and full cleanup. The veil's formation boundary is shown to its owner
and consenting companions, so it does not mark the hidden caster for opponents.
