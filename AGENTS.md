# AGENTS.md

## Project Goal

This repo is being turned from the old `TurretAutoFill` mod into `Vini-Client`, a client-side Mindustry Java mod that collects multiple small player tools under one entry point.

Keep the repo organized around feature packages. New features should be placed under `src/vini/client/<feature>/` instead of mixing unrelated code into the root package.

## Current Layout

- `src/vini/client/ViniClient.java` is the Mindustry `Mod` entry point.
- `src/vini/client/ViniFeature.java` is the minimal lifecycle interface for feature modules.
- `src/vini/client/turretfill/` contains the existing Turret Fill feature.
- `src/vini/client/bridgeconveyor/` contains bridge and conveyor placement helpers.
- `mod.hjson` points to `vini.client.ViniClient` and carries the in-game metadata.

Suggested future package names:

- `src/vini/client/<feature>/` for any other standalone client feature.

## Build And Verification

Use the Gradle wrapper:

```powershell
.\gradlew jar
```

The project targets Mindustry `v157` and Java 8 bytecode through Jabel. Keep code compatible with Java 8 APIs.

`deploy` also builds an Android jar, but it requires a configured Android SDK.

## Coding Guidelines

- Keep the main class small. Register feature modules from `ViniClient` and put behavior in feature packages.
- Prefer one feature package per user-facing tool.
- Register UI, settings, keybinds, and update hooks inside each feature's `init()` method.
- Avoid routine console/log output for player-client features; prefer quiet gameplay behavior.
- Guard client logic with Mindustry state checks such as `Vars.state.isGame()`, `Vars.player != null`, and `!Vars.player.dead()`.
- Keep settings keys feature-scoped, and preserve legacy keys when renaming old features.
- Do not add server-side requirements for client features. Features should fail quietly when the needed game state is unavailable.
- Update `mod.hjson`, `README.md`, and this file when the project identity, feature layout, or build process changes.