# Carrying mobs and players

Sneak and right-click a nearby entity to pick it up. Sneak and right-click a clear
block face to set it down. A carried player can press Sneak to get free. Flight's
Skyhook uses the same system for grabs, gentle releases and throws.

Cargo sits upright beside and slightly behind the carrier, rather than across the
crosshair. Placement follows yaw independently of looking up or down. It accounts
for the cargo's size, mirrors left-handed carriers, and tries the opposite side or
behind you when a wall blocks the preferred side. Cargo stays visible in third
person and to other players. Your own cargo and its name/shadow are hidden in
first person, including large mobs, so your view and held items remain usable.

The carrier, invisible seat and recipient now share one vanilla passenger chain.
Clients derive the seat from their local carrier position; the server no longer
moves an independent anchor behind them or sends repeated correction teleports.
A carried player retains their view direction and cannot steer their carrier.

Pickup validates reach, solid cover and ownership on the server. Ordinary pickup
has a four-block limit; Skyhook has seven. An occupied carrier or recipient cannot
be picked up again, and circular or stacked carry chains are rejected. Invalid
set-down attempts leave the existing carry intact. Oversized cargo is released at
a safe nearby position if it cannot fit through the next space.

Death, disconnects and dimension changes clean up both sides of a player carry.
Dismounting, an anchor disappearing or an externally changed mount also clear old
ownership. External teleports and new vehicles keep their position and control.
Gravity, collision, invulnerability and mob AI return to their previous values;
saved mid-carry state includes recovery metadata so a reloaded recipient does not
keep temporary protection. Server shutdown releases active carries before saving.
Forced releases grant a short Slow Falling cushion; intentional throws keep their
launch velocity.

## Verification

```bash
./gradlew build
./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle
./gradlew runClient --init-script tools/carry-preview.init.gradle
```

Twelve carry GameTests use actual server players, covering both ownership directions,
movement and turning without repeated player teleports, view clearance, placement,
external dismounts/mounts, lifecycle cleanup, original state restoration and saved
state recovery. The isolated preview captures both carrier and recipient viewpoints
under `build/carry-preview/screenshots/`.
