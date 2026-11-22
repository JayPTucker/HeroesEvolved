# Mastery and Power Levels: notes for agents

What the player sees is in [docs/progression.md](../progression.md). This file is how it is built and what has gone
wrong.

## How it is built

- **One ledger, saved with the player.** `progression/PlayerProgressionData` holds Mastery and every anti-farm limit:
  active ticks, the practice allowance left this minute, whether an exploration reward is ready, the regions explored
  and the kills per encounter (a 256-block region and a mob type). It survives death, logout and restarts, so a farm
  can't reset a limit by relogging. Only timestamps and fractions (`MasteryService`'s maps) are per session.
- **Four sources, each bounded.** Active play: 12 a minute of observed active ticks, never wall-clock time. Practice:
  hits and stamina spent share 6 a minute, refilled only by an active minute. Exploration: 30 for a new 128-block
  region, at most one per active minute. Kills: 3 per 10 maximum health, full for the first six of a type in an
  encounter region, half for the next six, then nothing; bosses at least 120 and four times the toughness reward.
- **One question: does this creature pay?** `MasteryService.rewardable` decides for hits, kills and the gifted reward
  callback. Spawners, trial spawners, eggs, dispensers, commands, breeding, reinforcements and followers pay nothing;
  summoned mobs pay only if they are bosses. Add a branch there, never a second check in a caller.
- **Max stamina comes from levels.** `PlayerEnergyService.getMaximumEnergy` is `baseEnergy + (level - 1) *
  energyPerPowerLevel` (100 to 176). It is the only source today; an item or skill that raises it belongs in that one
  method.

## Reusable patterns

- **Moving is not playing.** A water loop, a minecart track or a piston pusher moves a body without anyone at the
  keyboard, and an autoclicker breaks cobblestone at one look forever. Movement and `MasteryService.activity`
  (building, interacting) count only when the view has also turned at least 2 degrees since the last counted activity
  (`ActiveTimeTracker.looked`). Game tests that walk a fake player with `setPos` must turn it too (`setYRot`), as
  `MasteryBalanceGameTests.activePlayAndNewRegionsBothRewardProgress` does.
- **A reward flag on the mob, not the player.** `KILL_REWARDED` in the mob's persistent data stops the death event and
  `GiftedMobs.reward` paying twice; `HIT_REWARDED` caps hit practice at the mob's maximum health, so healing a captive
  can't make fresh damage.
- **Tests:** `skills/MasteryBalanceGameTests` (ledger round trip, local kill limits across a relog, spawn provenance and
  boss rewards, air casting, water loops and autoclickers, active time and exploration).
