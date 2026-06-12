# Vini Client

Client-side Mindustry Java mod by Vini.

## Features

- Turret Fill: toggled with `F4`, fills nearby item turrets from carried ammo or from the closest core in transfer range.
- Turret ammo priorities: configurable in the Mindustry settings under `Vini Client`.

## Project Layout

- `src/vini/client/ViniClient.java`: main Mindustry mod entry point.
- `src/vini/client/ViniFeature.java`: small interface used by client features.
- `src/vini/client/turretfill/`: Turret Fill feature code.

Future features should live in their own package under `src/vini/client/`, for example `bridgeconveyor`.

## Build

```powershell
.\gradlew jar
```

The desktop jar is written to `build/libs/`.
