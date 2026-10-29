# Telekinesis

Telekinesis is a physical force kit built around acquiring, suspending, redirecting and
releasing objects. Damage from launched fragments happens when their traveling paths hit
something. Bodies thrown into walls or slammed onto ground produce a separate impact.

## Base controls

The default action slots retain their saved IDs and unlock levels.

| Default key | Action | Interaction |
| --- | --- | --- |
| G | Telekinetic Push | A directional pressure front shoves visible enemies and loose objects. An available orbit fragment fires along the crosshair. Shift while gripping slams the held enemy downward. |
| H | Grip | Seize a visible creature, holding it beside the forward view. Press again to throw toward the crosshair. Shift + H attempts a checked placement on an aimed upper surface, otherwise releases gently. |
| Z | Bullet Catch | Catch up to twelve approaching hostile projectiles within six blocks, arranged in two peripheral banks. Press again to converge them on the crosshair. Shift cancels safely. |
| X | Disarm | Tear a mob's weapon into the world and draw visible loose items toward you. Players retain their equipment. |
| V | Telekinetic Storm | Mark up to sixteen visible enemies within twelve blocks. Gather for twenty ticks, then lift and swirl for forty-five ticks before slamming. Press again after gathering to slam early; Shift cancels. |

Grip uses yaw-relative placement rather than the camera's vertical angle. Looking straight up
or down cannot swing the held creature through the player's eye line. Grip no longer deals
automatic choking damage; throws, slams and follow-up techniques supply the damage.

Bullet Catch and Storm begin their recovery when the active sequence ends. Ending a sequence
does not require a second stamina payment. Timed-out or interrupted projectile catches drop
their shots safely, restoring each projectile's original gravity setting.

## All eighteen purchased nodes

Existing node IDs, prerequisites, prices and equipped action IDs remain compatible with saved
builds. The old six blanket stamina/damage/cooldown nodes now unlock specific interactions.

| Branch | Node | What it changes |
| --- | --- | --- |
| Fine Manipulation | Soft Landing | Gently released friendly creatures gain five seconds of Slow Falling. |
| Fine Manipulation | Vector Lance | Launch a visible, fast stone lance for eight damage, up to forty blocks. |
| Fine Manipulation | Thread the Needle | The lance can pierce its first creature and strike a second. |
| Fine Manipulation | Remote Hand | Pull a visible loose item or operate an eligible switch, door, gate or bell within twenty-four blocks. |
| Fine Manipulation | Long Reach | Remote Hand reaches forty-eight blocks; Grip reaches eight blocks farther. |
| Fine Manipulation | Retrieve | Extract one actual stack from an unlocked, editable container. It travels back through the world and can be intercepted. |
| Kinetic Arsenal | Salvage Orbit | A successful Push or Disarm gathers one temporary fragment beside you. Push launches an available fragment. |
| Kinetic Arsenal | Gravity Well | Place a five-second pull at the crosshair. Recast to loft gathered enemies upward, or Shift to end safely. |
| Kinetic Arsenal | Crossfire | A thrown enemy's wall/ground impact spends an orbiting fragment on a different nearby enemy. |
| Kinetic Arsenal | Kinetic Volley | Gather three fragments, then launch them at the aimed point in sequence. Each physical hit deals four damage. |
| Kinetic Arsenal | Five Fragments | Increase orbit capacity and volley size to five. |
| Kinetic Arsenal | Iron Rain | Shift + Volley uses ballistic arcs and small landing pressure waves. Ceilings and walls can stop the barrage. |
| Bastion | Grounding | Vector Step stores one safe landing for five seconds. |
| Bastion | Vector Step | Vault forward/up from a visible force foothold; Shift reverses the vault. |
| Bastion | Counterstep | The first twelve ticks of Vector Step deflect approaching hostile projectiles. |
| Bastion | Force Bubble | A moving shell deflects approaching hostile projectiles and pushes enemies outside. Recast releases an outward wave; Shift cancels. |
| Bastion | Forward Bastion | Increase the shell radius from three to four blocks and unlock Shift-casting an anchored shell at the crosshair. |
| Bastion | Counterbattery | The shell lasts ten seconds and redirects intercepted hostile projectiles toward the current crosshair. |

Normal second presses can release Gravity Well and Force Bubble during their cast cooldown,
without paying again. Shift + an already active field always cancels it; creating an anchored
Forward Bastion requires no existing bubble.

## Combinations to try

- Push an enemy into cover to salvage a fragment, Grip another enemy, then throw it into a
  wall: Crossfire spends the fragment on a different target.
- Gather enemies with Gravity Well, release them upward, then aim a piercing Vector Lance
  through the airborne group.
- Place Forward Bastion at a passage while standing to one side; Counterbattery returns the
  intercepted shots down your own aim line.
- Use Vector Step to change angle, then lob Iron Rain over low cover. An overhead ceiling
  remains a physical obstruction.
- Catch a projectile formation, reposition, and release it deliberately. A timed-out catch
  will not fire automatically.

## Presentation and limits

Nine dedicated effect compositions distinguish gathering, orbit, launch, impact, grip, pressure
waves, barriers, wells and force footholds. Gathering, holding and releasing also have distinct
animated hand poses in first and third person. Thin pale refraction collars and moving tension
threads leave the actual target or fragment visible.

Fragments are small vanilla block displays driven by bounded server-side physics. They never
remove terrain, create collectible blocks, save into chunks or generate loot. Swept creature
and block collision prevent fast fragments tunneling through cover. Lance and volley paths
have finite ranges and lifetimes; Iron Rain follows a ballistic path rather than spawning
projectiles behind a ceiling.

Allied creatures, owned pets, protected players, carried bodies and temporal copies are
excluded from hostile interactions. Field effects respect solid cover. Death, logout,
dimension changes, power removal, loss of control, skill reset and server shutdown clean up
their transient state. A complete single skill branch still fits the level-five point budget.

Dedicated GameTests cover progression persistence, physical travel and interception, timing,
field recasts, targeting, cleanup and the base interaction controls. The standalone effect
geometry check includes eighty-four kinetic hand/skin/phase combinations. In-game multiplayer
playtesting remains the check for animation feel and balance.
