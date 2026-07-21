# JellyStorage — AGENTS.md

## Product
**果冻拼装乱斗** (Jelly Arena) — roguelike action RPG with jelly blob fighters, weapon synergies, ink-wash art style. Ad-supported free game.

## Stack
- Kotlin, Jetpack Compose + Canvas, minSdk 26, compileSdk 35, Java 17
- Compose BOM 2024.12.01, Material3
- AdMob (play-services-ads 23.6.0)
- No external game engine — all rendering is custom Canvas

## Entry chain
```
MainActivity → GameView → PlayScreen (main screen, ~3000 lines)
```
`GameView.kt` is a thin wrapper that delegates to `PlayScreen`. All game screens are managed inside `PlayScreen` via a `Screen` enum.

## Key packages (under app/src/main/java/com/jellystorage/)
- `play/` — **active main game** (PlayScreen, ProgressStore, ArenaSim, GearSets, LootSystem, etc.)
- `engine/` — battle simulation (BattleSimulator, SynergyModels, VerletJellyMesh physics)
- `softbody/` — soft-body rendering + legacy assemble mode
- `run/` — roguelike run progression (StageRunScreen, RunModels)

## Legacy vs active
The old `softbody/assemble/` system (AssembleGame, ArenaCombat, Modules, Stages) still exists but is **not wired into the main flow**. `PlayScreen` is the real entry. Don't treat `AssembleGame.kt` as the primary game.

## Build & test
```bash
./gradlew assembleDebug          # build debug APK
./gradlew testDebugUnitTest      # run unit tests
./gradlew lintDebug              # lint
```
Single test class: `./gradlew testDebugUnitTest --tests "com.jellystorage.engine.BattleSimulatorTest"`

## Progression & storage
- `ProgressStore` — SharedPreferences wrapper for character data, gold, stats, tutorial state
- SharedPreferences key: `jelly_arena_v2` (in AssembleGame, legacy)
- `RunMeta` — mid-run state (hp, mp, level, passives, equipment)
- `CrashReporter` — installed in `JellyApp.onCreate()`

## Conventions
- Landscape-only (SENSOR_LANDSCAPE set in MainActivity)
- Edge-to-edge with safeDrawingPadding
- Canvas-based rendering (no XML layouts)
- Chinese comments throughout (product is Chinese-market)
- Procedural soft-body mesh for fighters (VerletJellyMesh)
- No Compose Navigation — screen routing is manual via `Screen` enum state

## Gotchas
- `PlayScreen.kt` is ~3000 lines — expect large diffs, split by screen enum section
- AdMob init is wrapped in try/catch — failures are silent by design
- `softbody/assemble/` files are dead code for the main flow; editing them won't affect PlayScreen
- Tests are unit-only (JUnit4), no instrumentation tests
- No CI pipeline configured in-repo
