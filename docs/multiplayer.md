# Playing on a server, hosting friends, and Essential

Heroes Evolved works on dedicated servers, in worlds you host for friends (Open to LAN, or Essential's Host World), and
alongside the Essential mod.

## What everyone needs

| Mod | Server | Every player |
| --- | --- | --- |
| Heroes Evolved (the same version) | required | required |
| TaCZ (Timeless and Classics Zero) | required | required |
| Simple Voice Chat | required | required |
| Simple Radio, with Lexiconfig | required | required |
| JEI, Jade | optional | optional |
| Essential | not needed | optional |

A player whose Heroes Evolved version doesn't match the server's is turned away when they join, with a message about
mismatched mod versions. Update both to the same version.

## Settings

- **Gameplay settings belong to the server:** `config/heroesevolved-common.toml` in the server's folder (or the host's
  game folder for a hosted world) decides costs, durations, events and everything else that changes play. A player's
  own copy of that file has no effect on someone else's server.
- **Comfort settings belong to each player:** camera shake, flashes, cold fog, severed limbs, the fighting-style mouse
  and dodge inputs, and the finisher and Ultimate cameras (`config/heroesevolved-client.toml`, also in game under Mods →
  Heroes Evolved → Config, or the Powers screen).

## Flying, hovering and the server's flight setting

Servers normally kick anyone who floats in the air for a few seconds ("Flying is not enabled on this server"). Powers
that lift you (flight, hovers, launches, rides, being frozen mid-air in stopped time) are allowed by Heroes Evolved
itself, so you don't need to turn on `allow-flight` in `server.properties`. Floating with no power behind it is still
caught.

## Admins

The `/heroesevolved` commands and the admin menu need operator permission (level 2). Powers are one owner per world, on
a server as anywhere else: `/heroesevolved power remove <player>` frees a power for someone else.

## Hosting with Essential or Open to LAN

A hosted world is the host's own game acting as the server. Friends need the same mods as for a server (above); the
host's `heroesevolved-common.toml` is the server's. Nothing else changes: every power, event and story beat works for
guests the same as on a dedicated server.

## Essential

Essential's character preview and sidebar sit beside Heroes Evolved's title menu, and its pause menu works as usual.
Heroes Evolved changes nothing in Essential and needs nothing from it.
