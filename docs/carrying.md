# Carrying mobs and players

Sneak and right-click a nearby entity to pick it up. Sneak and right-click a clear block face to set
it down. A carried player can press Sneak to get free. Flight's Skyhook uses the same system for
grabs, gentle releases and throws.

## How things are held

What you carry is held in front of you, in your arms, and is drawn where your arms actually are.

| What | Hold | Looks like |
| --- | --- | --- |
| A person: a player, villager, illager, witch, zombie, skeleton or piglin | **In your arms** | A bridal carry. They lie back across both arms, head toward your left shoulder and knees over your right arm. A carried player's arm goes up around your neck. Your arms reach forward under them. |
| Anything else your size or smaller (up to 1 block wide and 2.1 tall): a pig, sheep, cow, cat, chicken | **Against your chest** | Held in both arms, facing your left. |
| Bigger, up to 2 blocks wide and 3 tall (an iron golem, a horse) | **Overhead** | **Only Flight's Skyhook** has the strength. It goes up over your head, arms raised. |

Your own hands can't pick up anything bigger than you, and you get a "Too big to carry" message
when you try. Bosses, Sylar included, can't be carried at all. Left-handed carriers are mirrored.

While you carry something your body turns with your view. The cargo model follows your position and
facing every frame, including during flight. You can look down to see cargo in your arms in first person.
You can still click the ground to set it down, mine and use items.

A carried player's camera is where their head is drawn, in front of your left shoulder.

## Rules

The carrier, an invisible anchor and the cargo form one vanilla passenger chain. Clients work out
the seat from their local copy of the carrier. The anchor syncs both endpoints and the hold so clients
can attach cargo as soon as both entities are in view. A carried player keeps their view direction and can't steer.

- **Pickup** checks reach, solid cover, size and ownership on the server. Ordinary pickup reaches 4
  blocks; Skyhook reaches 7.
- An occupied carrier or cargo can't be picked up again, and circular or stacked carry chains are
  rejected. A set-down attempt that fails leaves the carry as it was.
- Cargo in your arms stays within your own height, so it only comes loose if you crawl under
  something chest-high. An overhead lift needs the space above your head. Released cargo is put on
  the ground just in front of you, or at the nearest safe spot.
- **Cleanup:** death, disconnects and dimension changes clean up both sides of a player carry, and so
  does dismounting, the anchor disappearing, or something else changing the mount.
  - External teleports and new vehicles keep their position and control.
  - Gravity, collision, invulnerability and mob AI return to their previous values.
  - A carry saved mid-way records the original values, so a reloaded entity doesn't keep its
    temporary protection.
- Server shutdown releases every carry before saving. Forced releases give a short Slow Falling
  cushion; intentional throws keep their launch speed.

## Verification

```bash
./gradlew build
./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle
./gradlew runClient --init-script tools/carry-preview.init.gradle
```

Fourteen carry gametests use real server players. They cover:

- Ownership in both directions.
- Movement and turning without repeated player teleports, and view clearance.
- Placement, and external dismounts and mounts.
- Lifecycle cleanup, restoring the original state, and recovering saved state.
- The size limits (a pig in your arms, a golem only overhead with Skyhook).
- The bridal hold, with the carried player's head in front of your left shoulder.

The preview captures both viewpoints, plus third-person shots of the bridal and chest holds, under
`build/carry-preview/screenshots/`.
