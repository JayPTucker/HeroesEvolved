# Pyrokinesis: notes for agents

The kit and its art (`pyro/`, `PyroArt`). Firebending, the melee style every physical power copies, is in [fighting-styles.md](../fighting-styles.md). What the player sees: [pyrokinesis.md](../../pyrokinesis.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Pyrokinesis rework

Pyrokinesis (`pyro/`, art in `client/effects/setpiece/PyroArt`) was reworked alongside Flight. What the player
sees is in [docs/pyrokinesis.md](../../../docs/pyrokinesis.md).

- **One geometry class for gameplay and drawing.** `pyro/PyroGeometry` holds the shared numbers (the breath
  cone's slope, warning lengths, the sun's flight time) and the body anchors: `castingHand(player, partial)`
  (shoulder 1.375 up, or 1.17 crouching, 0.3125 out from the body's yaw, then 0.64 along the look) and
  `breathOrigin(player, partial)`. The server aims with them and `PyroArt` draws from them, so fire leaves the
  palm the player sees.
- **A variant flag packed into the piece's scale.** Blue fire adds `PyroGeometry.BLUE_SCALE` (32) to the set
  piece's `scale`; `PyroArt.draw` checks `scale >= BLUE_SCALE` and subtracts it. That gives a second colour
  without a new piece or payload field. Use it for any two-colour variant whose real sizes stay well under 32.
- **One `Fire` palette record, two palettes.** `PyroArt` keeps `Fire(edge, body, gold)` records (`EMBER`
  red-orange-gold, `BLUE` indigo-blue-cyan) and passes one to every primitive (`billows`, `flame`, `orb`,
  `jet`, `curtain`, `marker`), so every move switches colour together.
- **Mark the ground before the hit.** Firestorm marks its footprint for 0.6 s (`STORM_WARNING`) before the
  wall rises, Collapse shrinks the wall over `COLLAPSE_WARNING`, Sunfall shows its impact ring while it
  charges, and Ember Mines give a public `MINE_WARNING`. A readable telegraph makes area damage fair.
- **Private warnings for hidden things.** `PyroGeometry.showPrivate(player, ...)` sends a set piece to the
  owner only: an armed Ember Mine is visible to whoever laid it and to no one else until it triggers.
- **Abilities that feed each other.** Dragon's Breath aimed at your own Firestorm *fans* it (wider and
  faster-burning); a fully charged Cinder Spear or a Blue Lance primes armed Ember Mines along its path;
  Fire Shepherd turns fires back into Heat. Cross-ability interactions like these are what make a kit fun
  to master. Design new kits with at least one.
- **Blue fire leaves soul fire.** Blue flames place `Blocks.SOUL_FIRE` where regular fire could stay: twice
  the contact damage and no spreading, so the hotter variant is stronger but safer for builds.
- **Live drawing for flight jets.** `PyroArt.live()` / `drawLive(c)` draw the flame streams on palms and
  boots for every pyro flyer each frame, following the bank, the same way `FlightArt` draws wind.
