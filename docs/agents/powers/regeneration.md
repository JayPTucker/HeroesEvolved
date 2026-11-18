# Regeneration: notes for agents

Tap or hold, Play Dead, Blood Bond and the tree (`regeneration/`, `RegenArt`, `RegenSkills`). What the player sees: [regeneration.md](../../regeneration.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Regeneration rework

Regeneration (`regeneration/`, new art in `client/effects/setpiece/RegenArt`) was reworked in November 2026: follow-ups on
three keys, a held Blood Gift, Play Dead, an eighteen-node interaction tree (`regeneration/RegenSkills`) and 18 poses.
What the player sees is in [docs/regeneration.md](../../../docs/regeneration.md).

- **Tap or hold on a plain action.** Blood Gift became tap/hold the same way Canary Cry is: `activate` records the press,
  the server tick turns a press held `STREAM_HOLD` ticks into the held effect, and `release` either gives the tap's effect
  or ends the held one. Move the "is there a target" check into `canUse` (with its own message), so a press with no one
  in reach still costs nothing.
- **Reuse a body state the client already draws.** Play Dead lies the player down by setting the "downed" flag in
  `BodyStatePayload` (`BodyService.state`); `BodyVisuals` forces the sleeping pose on every client. A server-side
  `setForcedPose` alone doesn't reach the player's own client. Check what else the flag drives (here, only the give-up
  key, which the server ignores unless she is really down).
- **Branch skills can have a second press.** `SkillCatalog`'s generic builder takes a `followUp`; Regeneration's Play
  Dead uses it to get up for free, as Cryokinesis's skills do.
- **A follow-up that spends what the first part collected.** Blood Bond keeps a running `taken` total of the wounds
  Claire draws onto herself; Pay It Back turns it into healing for her friends and Regrowth for her. Any held or
  absorbing move can bank a number this way and cash it out on the second press.
- **Watch which damage type a new hit uses.** Freefall's landing first used `playerAttack`, so it also counted as one of
  Indestructible's Reckless punches (which cost her health and gave Regrowth). It now uses an explosion source. Check
  the power's own `LivingIncomingDamageEvent` hooks before picking a source.
- **"Hostile" is not "not a friend".** `ExpansionSkills.hostile` is true for every creature that isn't an ally, cows
  included. A heal that should reach animals and villagers needs a friend rule (`RegenerationService.bonded`), not
  `!hostile`.
- **Blood should be drawn as blood.** `RegenArt` draws blood in the soft (normal-blended) pass so it is red, not light, and
  keeps only a thin wet highlight in the glow pass. `Canvas.bolt` adds a bright core, so dark cracks use plain jagged
  lines (`RegenArt.gash`).
- **Photographs:** `tools/regen-kit-preview.init.gradle` (`uitest/RegenKitPreview`) shoots every action, follow-up, skill and
  style into `build/regen-kit-preview/screenshots/`. A leap needs its landing shots timed to the fall; a front camera with
  someone standing in front of the player only shows their back.
