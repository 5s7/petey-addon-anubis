# petey-addon — buildable source (1.21.11)

Decompiled and remapped from `petey-addon-0_3_1.jar` (intermediary → yarn 1.21.11).

## Requirements

- **JDK 21+**
- **Gradle 8.11+** (or use the wrapper: `./gradlew`)
- The **Anubis+** client JAR (the client this addon targets)

## Setup

1. **Provide the Anubis JAR**

   Place `anubisplus-1.21.11-SNAPSHOT.jar` (or whatever version you have) into the `libs/` folder
   at the root of this project:

   ```
   petey-addon/
   ├── libs/
   │   └── anubisplus-1.21.11-SNAPSHOT.jar
   ├── src/
   ├── build.gradle
   └── ...
   ```

   If your Anubis JAR has a different name, update `anubis_version` in `gradle.properties`
   to match (without the `.jar` extension).

2. **Build**

   ```bash
   ./gradlew build
   ```

   The output JAR lands in `build/libs/petey-addon-0.3.1.jar`.

3. **Drop into Anubis addons folder** per the Anubis client's addon instructions.

## What was changed from the bytecode

| Area | Change |
|---|---|
| All `net.minecraft.class_XXXX` references | Remapped to proper yarn names (e.g. `class_310` → `MinecraftClient`) |
| All `method_XXXXX()` calls | Remapped to yarn names with full signatures documented in-source |
| `BlockEntityRenderDispatcherMixin` | Target string updated from intermediary method descriptor to named yarn descriptor |
| `PeteysStaffLive` — `ClickEvent` inner class | Updated from intermediary `class_2558$class_10608` to `ClickEvent.OpenUrl` (1.21.x API) |
| `PeteysStaffLive` — Toast call | Updated from obfuscated `method_1566().method_1999(toast)` to `getToastManager().add(toast)` |
| `PeteysEspBypass` — `needsModel` check | Fixed decompiler confusion: `class_4739` (ChestBlockEntity) used as block type; corrected to `ChestBlockEntity` / `ShulkerBoxBlock` |
| `WorldChunk.getBlockEntityNbt` call | Added `.toImmutable()` call that the decompiler collapsed, required by 1.21.x API |
| `worldRenderer.updateBlock` | Signature corrected to 5-arg variant matching 1.21.11 |

## Notes

- This is a **client-side** Fabric mod. It depends on Anubis+ and Fabric API.
- No obfuscation was present in the original JAR beyond standard Minecraft intermediary mapping.
- The `ChunkScanner`, `BlockLayer`, `EspStyle`, `ModuleChat`, `ChallengeToast`, and all
  `net.anubis.*` classes come from Anubis+ itself and are compile-only dependencies.
