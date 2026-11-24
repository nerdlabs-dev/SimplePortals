# SimplePortals (lean rewrite)

Region portals for Paper 26.x: walk into a box and get teleported, sent to another server behind
Velocity/BungeeCord, or have commands run. Same commands as XZot1K's original SimplePortals, rewritten
from scratch on the plain Paper API, with no NMS, no reflection and no Minecraft-version checks, so a new
Minecraft drop normally only needs the `paper-api` version in `pom.xml` bumped.

## Build

Requires JDK 25 and Maven.

```
mvn package
```

The jar is `target/SimplePortals-2.0.0.jar`.

## Quick start

1. `/sp sm`, then left-click one corner and right-click the opposite corner.
2. `/sp create hub`
3. Either `/sp sl hub` (teleport to where you stand) or `/sp ss hub survival` (send to the `survival` server).
4. Optional: `/sp fill hub nether_portal`, `/sp message hub &aOff to survival!:TITLE`, `/sp delay hub 2`.

`/sp` with no arguments lists every command.

## Cross-server landing spot (`/sp ssl`)

Without `/sp ssl`, players arrive at the destination server's normal spawn. To land them somewhere specific:

* Put SimplePortals on the destination server too.
* On every server, set `server-name` to that server's name in `velocity.toml`, and point `transfer-folder`
  at the same shared folder (the default works when all servers live side by side in one parent folder).
* `/sp ssl <portal> <world> <x> <y> <z> <yaw> <pitch>`

Velocity needs `bungee-plugin-message-channel = true` in `velocity.toml` (this is the default).

## What changed from the original

* Portal names are one word, without spaces.
* No MySQL; cross-server landing spots go through the shared folder.
* Existing `portals` data from the old plugin isn't imported, so portals need to be recreated.
* Command lines default to running as console; add `:PLAYER` to run as the player.

Based on [SimplePortals](https://github.com/XZot1K/SimplePortals) by XZot1K (MIT).
