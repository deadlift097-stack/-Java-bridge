# ModHost Java Bridge (LeviLauncher Android)

Runs Java mods inside Minecraft Bedrock on Android through a native LeviLauncher mod.
This is **not** Fabric/NeoForge: mods target the small API in `java/api` (`ModHostMod`, `Events`, `ModLogger`).
Java Edition mods must be ported to it.

    C++ mod (template) -> JavaBridge -> DexClassLoader -> com.modhost.api.Bootstrap -> your ModHostMod

## How the build works
CI checks out the LeviLauncher template next to this repo, runs `scripts/overlay.py` (adds `src/bridge`,
hooks it into `MyMod.cpp` and `CMakeLists.txt`, ships `modhost-api.dex.jar` inside the `.levipack`),
then runs the template's `scripts/package.ps1 -Abi arm64-v8a`. Nothing to merge by hand.
Minecraft version filter is the template's default `1.26.*`.

## Writing a mod
Implement `ModHostMod`, build with javac + d8 (see `scripts/build-java.sh`), and put
`yourmod.jar` + `yourmod.json` (`{"id": "...", "main": "pkg.Class"}`) into `<dataDir>/mods`.
Jars must be dexed with d8; Android can't load plain class-file jars.

## Features
- Events: tick (20/s), app.resumed/app.paused, custom events, native `fire()` (docs/EVENTS.md)
- In-game "Mods" button: toggle Java mods on/off, change each mod's settings, read the log (all saved)
- Fabric-style jars (`ModInitializer`) load without rewriting; `convert-mods` workflow prepares them (docs/COMPAT.md)

## Status
- Done: JVM bridge, API, events, menu, Fabric-style loader, converter, CI.
- Not done: native game-event hooks (need Bedrock signatures per version), Lua loader.
- Untested on a device or inside the real template; expect small fixes.
