# Recovered Region Map and Seed Map

This additive contribution targets W3eklyPlayTT/petey-addon-anubis at commit
`ed4c2d5e4a59250d82733ea4743e0e4fc732208d`. Every file already present at that
commit is preserved byte for byte. No upstream changes have been submitted.

## Contents and provenance

Recovered from the supplied `petey-decompiled.zip`, checked against the local
original `petey-addon-0.5.0.jar`, and converted to Minecraft 1.21.11 Yarn names
(`1.21.11+build.1`, matching the repository):

- `PeteysRegionMap`: current-region cells, player marker and heading; Nether coordinates scale by eight.
- `PeteysSeedMap`: asynchronous biome sampling around the player using the original fixed seed, `6608149111735331168`, at Y=63; Overworld only.
- `DonutRegions`: original region geometry, lookup, names, colors and bounds.
- `BiomeColors`: original 54 biome colors and unknown fallback.
- `DragBox`: draggable HUD placement while chat is open, using hidden position settings.

The reconstruction retains the recovered behavior, including seed-map worker,
cache and queue behavior. The hardcoded seed and region layout are recovered
data; they have not been verified against the current DonutSMP server.
No Anubis code, binary, Minecraft binary, compiled addon, or dependency JAR is
included in this contribution.

## Integration without changing existing files

`PeteyMapsAddon` delegates category registration, original module initialization
and configuration callbacks to the existing `PeteyAddon`, then registers both
map modules. `maps.init.gradle` replaces the original entrypoint **in generated
build output only** with this wrapper. It preserves other entrypoints and keeps
the original source resource unchanged. A second simultaneous entrypoint under
`com.petey.addon` would be rejected by Anubis' package-overlap check.

Use the additive integration explicitly from the project root:

```text
gradle -I maps.init.gradle clean build
```

A plain build does not activate the maps. The source ZIP and patch are reviewable
contributions, not an installable mod or a claim of a working complete build.
The Gradle init script has been reviewed but not executed in a complete Loom build.
Before installing any successfully built artifact, inspect its `fabric.mod.json`:
the `anubis` entrypoint must be `com.petey.addon.maps.PeteyMapsAddon`, with the
original entrypoint replaced rather than retained alongside it.

## Verification and limitations

- All six added Java classes passed Java 21 compilation with annotation processing
  disabled, against locally remapped Minecraft 1.21.11, Anubis Beta 0.9.9.1,
  original Petey addon classes, imgui-java 1.90.0 and local Minecraft libraries.
  Existing `PeteyAddon` symbols came from the original Petey JAR for this isolated
  check; this is not compilation of the repository's existing ESP module.
- Region lookup matched the original JAR in 23,104 comparisons covering grid
  centers, boundaries, just-inside/outside boundaries and out-of-map positions.
- All 54 biome color values and the unknown fallback matched the original JAR.
- All original repository files were SHA-256 checked for exact preservation.
- Full source compilation was attempted with local dependencies and failed in
  unchanged repository code. Examples: `BlockEntityRenderDispatcher` and
  `MobSpawnerBlock` are unavailable under those names in the 1.21.11 mappings;
  `BlockEntityRenderer<E>` needs another type parameter; `OutlineStyle.Registrar`
  has a generic method incompatible with the existing lambda; `getTopY()` and
  `getBlockEntityNbt(...)` use obsolete signatures; a Block is tested against
  `ChestBlockEntity`. That classpath also lacked Fabric lifecycle API and
  MixinExtras. These errors were not repaired because existing files must remain exact.
- No Gradle executable/cache was available; the repository supplies wrapper
  properties but no wrapper scripts or wrapper JAR. A complete Gradle/Loom build,
  mixin annotation processing, remapping and in-game loading were not verified.
- The existing build uses split main/client source sets but puts client code in
  main, and declares the Anubis dependency as an unremapped `compileOnly` file.
  Named development compilation needs compatible remapped Anubis classes and
  explicit ImGui bindings; nested runtime dependencies do not automatically prove
  the development classpath is complete. Resolve these existing build concerns
  before expecting the command above to produce a working artifact.
- HUD rendering, dragging, game lifecycle and actual biome generation were not
  exercised in Minecraft. Compilation and pure-data comparisons cannot establish
  runtime correctness or fix inherited concurrency behavior.

The patch contains additions only. Apply to the recorded base with
`git apply --check petey-maps-additions.patch` followed by
`git apply petey-maps-additions.patch`. The full source ZIP is also provided for
review and contains the untouched baseline plus these additions. See
`MAPS-PROVENANCE.json` for input hashes and the baseline file manifest.
