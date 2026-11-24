# MoonJS Moon Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Forge 1.20.1 `moonjs` addon that exposes persistent, per-dimension moon phases and moon events to KubeJS, synchronizes them to clients, renders single-image moon textures, and applies blood-moon world tinting.

**Architecture:** Keep the server authoritative. Each `ServerLevel` owns one `MoonStateSavedData`; `MoonEventService` validates and mutates it, then sends a versioned state packet to players in that dimension. KubeJS is optional and only discovers `MoonJSKubeJSPlugin` through `kubejs.plugins.txt`. The client caches state by dimension and uses Forge dimension-effects and viewport/GUI hooks for rendering.

**Tech Stack:** Minecraft 1.20.1, Forge 47.4.6, Java 17, official mappings, Forge `SimpleChannel`, `SavedData`, optional KubeJS 2001.6.5/Rhino compile-only APIs, no new runtime libraries.

**Spec:** `docs/superpowers/specs/2026-09-25-moonjs-design.md`

## Global Constraints

- Modify only `E:\Server_mod\moonJS`; never modify `E:\Server_mod\其他项目\KubeJS-2001`.
- Use Mod ID `moonjs`, display name `MoonJS`, and Java package `com.moonjs`.
- No Java package or resource path may contain `example`.
- KubeJS and Rhino are optional compile-only dependencies and must not be bundled into the moonJS JAR.
- Server state is per `ServerLevel` and all durations use server game time in ticks.
- Custom moon textures are single images addressed by `ResourceLocation`.
- Client-only classes must remain behind Forge client event subscribers; dedicated-server loading must not resolve `net.minecraft.client.*` classes.
- Preserve the requested default behavior: no moon override means vanilla rendering; missing KubeJS must not prevent the base mod from loading.
- Because the project has no Git repository, do not add commit steps; record verification results in the final report instead.

## File Map

Create or modify only these responsibility-focused files:

- Modify `build.gradle`, `gradle.properties`, and `src/main/resources/META-INF/mods.toml` for naming and optional dependencies.
- Delete the template `src/main/java/com/example/examplemod/ExampleMod.java` and `Config.java`.
- Create `src/main/java/com/moonjs/MoonJSMod.java` and `MoonJSConstants.java` for the Forge entry point and shared IDs.
- Create `src/main/java/com/moonjs/server/MoonPhaseDefinition.java`, `MoonPhaseRegistry.java`, `MoonState.java`, and `MoonStateSavedData.java` for definitions and persistence.
- Create `src/main/java/com/moonjs/server/MoonEventOptions.java`, `MoonEventService.java`, and `MoonServerEvents.java` for validated mutations and ticking.
- Create `src/main/java/com/moonjs/network/MoonNetwork.java` and `MoonStatePacket.java` for server-to-client synchronization.
- Create `src/main/java/com/moonjs/kubejs/MoonJSBindings.java`, `MoonJSKubeJSPlugin.java`, and `MoonKubeJSOptions.java` for the optional KubeJS surface.
- Create `src/main/java/com/moonjs/client/MoonClientState.java`, `MoonDimensionEffects.java`, `MoonSkyRenderer.java`, and `MoonClientEvents.java` for client caching and rendering.
- Create `src/test/java/com/moonjs/server/MoonStateContractTest.java`, `src/test/java/com/moonjs/kubejs/MoonKubeJSOptionsContractTest.java`, and `scripts/moonjs_contract_test.ps1` for offline contracts.
- Modify `README.txt` and create `src/main/resources/kubejs.plugins.txt`; do not add a sample binary texture that could hide missing-resource behavior.

### Task 1: Replace the MDK template and establish optional KubeJS loading

**Files:**
- Modify: `build.gradle`
- Modify: `gradle.properties`
- Modify: `src/main/resources/META-INF/mods.toml`
- Delete: `src/main/java/com/example/examplemod/ExampleMod.java`
- Delete: `src/main/java/com/example/examplemod/Config.java`
- Create: `src/main/java/com/moonjs/MoonJSConstants.java`
- Create: `src/main/java/com/moonjs/MoonJSMod.java`
- Create: `src/main/resources/kubejs.plugins.txt`

**Interfaces:**
- Produces `MoonJSConstants.MOD_ID = "moonjs"` and a Forge `@Mod("moonjs")` entry point.
- Produces a bare common Forge entry point; Task 2 adds the common setup hook after the network class exists, and Task 3 adds the server event registration after the server event class exists.
- Produces compile-only file dependencies from the supplied reference project's existing outputs: `../其他项目/KubeJS-2001/common/bin/main`, its Architectury 9.1.12 common/Forge jars, and its Rhino 2001.2.2-build.17 common/Forge jars. These paths are reference-project outputs, not new runtime libraries.

- [ ] **Step 1: Write the naming/resource contract before changing code.**

Add `scripts/moonjs_contract_test.ps1` with these checks:

```powershell
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaRoot = Join-Path $projectRoot 'src/main/java'
$forbidden = rg -n 'com\.example|examplemod|ExampleMod' $projectRoot -g '!build/**' -g '!run/**'
if ($LASTEXITCODE -eq 0) { throw "模板标识仍存在:`n$forbidden" }

$props = Get-Content -Raw (Join-Path $projectRoot 'gradle.properties')
if ($props -notmatch '(?m)^mod_id=moonjs$') { throw 'mod_id 未设置为 moonjs' }
if ($props -notmatch '(?m)^mod_group_id=com\.moonjs$') { throw 'mod_group_id 未设置为 com.moonjs' }

$pluginFile = Join-Path $projectRoot 'src/main/resources/kubejs.plugins.txt'
if (-not (Test-Path -LiteralPath $pluginFile)) { throw '缺少 kubejs.plugins.txt' }
if ((Get-Content -Raw $pluginFile) -notmatch 'com\.moonjs\.kubejs\.MoonJSKubeJSPlugin') { throw 'KubeJS 插件类未列出' }
```

- [ ] **Step 2: Replace the Gradle properties and metadata.**

Set the property values to:

```properties
mod_id=moonjs
mod_name=MoonJS
mod_group_id=com.moonjs
mod_authors=MoonJS Contributors
mod_description=KubeJS moon phases and moon events for Minecraft Forge 1.20.1.
```

Keep `minecraft_version=1.20.1`, `forge_version=47.4.6`, Java 17, and the existing ForgeGradle plugin.

- [ ] **Step 3: Add only the optional compile-time dependencies.**

Add the existing reference-project outputs to `dependencies` in `build.gradle`:

```groovy
compileOnly files('../其他项目/KubeJS-2001/common/bin/main')
compileOnly files('../其他项目/KubeJS-2001/.gradle/loom-cache/remapped_mods/loom_mappings_1_20_1_layered_hash_40359_v2/dev/architectury/architectury/9.1.12/architectury-9.1.12.jar')
compileOnly files('../其他项目/KubeJS-2001/.gradle/loom-cache/remapped_mods/loom_mappings_1_20_1_layered_hash_40359_v2_forge_1_20_1_47_1_81_forge/dev/architectury/architectury-forge/9.1.12/architectury-forge-9.1.12.jar')
compileOnly files('../其他项目/KubeJS-2001/.gradle/loom-cache/remapped_mods/loom_mappings_1_20_1_layered_hash_40359_v2/dev/latvian/mods/rhino/2001.2.2-build.17/rhino-2001.2.2-build.17.jar')
compileOnly files('../其他项目/KubeJS-2001/.gradle/loom-cache/remapped_mods/loom_mappings_1_20_1_layered_hash_40359_v2_forge_1_20_1_47_1_81_forge/dev/latvian/mods/rhino-forge/2001.2.2-build.17/rhino-forge-2001.2.2-build.17.jar')
```

Do not use `implementation`, `runtimeOnly`, shading, or a new repository for these dependencies.

- [ ] **Step 4: Create the smallest Forge entry point without future-task references.**

Use this common-side shape, leaving network registration to the later task:

```java
@Mod(MoonJSConstants.MOD_ID)
public final class MoonJSMod {
    public MoonJSMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
    }
}
```

Keep imports common-side only. Task 2 will add `modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(MoonNetwork::register));`, and Task 3 will add `MinecraftForge.EVENT_BUS.register(MoonServerEvents.class)`. The client event subscriber will be a separate class loaded by Forge on the client distribution.

- [ ] **Step 5: Run the contract and compile checks.**

Run from `E:\Server_mod\moonJS`:

```powershell
pwsh -NoProfile -File .\scripts\moonjs_contract_test.ps1
$env:GRADLE_USER_HOME='E:\Server_mod\.gradle'
.\gradlew.bat compileJava
```

Expected: the contract and the bare Forge entry point compile without any reference to later-task classes.

### Task 2: Implement definitions, per-dimension state, persistence, and packet encoding

**Files:**
- Create: `src/main/java/com/moonjs/server/MoonPhaseDefinition.java`
- Create: `src/main/java/com/moonjs/server/MoonPhaseRegistry.java`
- Create: `src/main/java/com/moonjs/server/MoonState.java`
- Create: `src/main/java/com/moonjs/server/MoonStateSavedData.java`
- Create: `src/main/java/com/moonjs/network/MoonStatePacket.java`
- Create: `src/main/java/com/moonjs/network/MoonNetwork.java`
- Create: `src/test/java/com/moonjs/server/MoonStateContractTest.java`

**Interfaces:**
- `MoonPhaseRegistry.register(String id, ResourceLocation texture, int vanillaPhase)` returns the stored `MoonPhaseDefinition` and rejects duplicate IDs.
- `MoonState.empty()` returns a state with no phase/event, `vanillaPhase=-1`, `eventEndGameTime=-1`, black color, intensity `0.0f`, and revision `0`.
- `MoonStateSavedData.get(ServerLevel level)` returns the data for that dimension.
- `MoonStatePacket.encode(MoonStatePacket, FriendlyByteBuf)` and `MoonStatePacket.decode(FriendlyByteBuf)` preserve the entire state and dimension key.
- `MoonNetwork.sendToDimension(ServerLevel, MoonState)` and `MoonNetwork.sendToPlayer(ServerPlayer, MoonState)` are the only server sync entry points.

- [ ] **Step 1: Write the failing pure state contract.**

Create a plain Java `main` test that asserts these exact invariants:

```java
public static void main(String[] args) {
    shouldRoundTripEmptyStateThroughNbt();
    shouldRoundTripCustomStateThroughNbt();
    shouldRoundTripCustomStateThroughNetwork();
    shouldRejectInvalidVanillaPhase();
    shouldRejectInvalidIntensity();
    shouldKeepDimensionKeyInNetworkState();
}
```

Use `CompoundTag`, `FriendlyByteBuf(Unpooled.buffer())`, and an explicit `require(boolean, String)` helper; do not add JUnit.

- [ ] **Step 2: Implement immutable phase definitions and the synchronized registry.**

Use a final class with this public shape:

```java
public final class MoonPhaseDefinition {
    public MoonPhaseDefinition(String id, ResourceLocation texture, int vanillaPhase) {}
    public String id() {}
    public ResourceLocation texture() {}
    public int vanillaPhase() {}
}
```

`MoonPhaseRegistry` owns a `LinkedHashMap<String, MoonPhaseDefinition>`, validates IDs against `[a-z][a-z0-9_]{1,63}`, validates `vanillaPhase` from 0 through 7, and returns an immutable lookup result.

- [ ] **Step 3: Implement state validation and NBT/network serialization.**

Use the following state fields and accessors:

```java
public final class MoonState {
    public static MoonState empty() {}
    public String phaseId() {}
    public int vanillaPhase() {}
    public ResourceLocation texture() {}
    public String eventId() {}
    public long eventEndGameTime() {}
    public int colorRgb() {}
    public float red() {}
    public float green() {}
    public float blue() {}
    public float intensity() {}
    public long revision() {}
    public boolean hasMoonOverride() {}
    public CompoundTag save(CompoundTag tag) {}
    public static MoonState load(CompoundTag tag) {}
    public void writeNetwork(FriendlyByteBuf buffer, ResourceKey<Level> dimension) {}
}
```

Use `vanillaPhase=-1` and absent NBT strings for nullable values. `MoonStatePacket.decode` must validate that the decoded phase is within 0–7, intensity is finite and within 0–1, and the dimension is a dimension registry key before creating a state.

- [ ] **Step 4: Implement per-dimension `SavedData`.**

Use the Forge 1.20.1 pattern:

```java
public static MoonStateSavedData get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(
            MoonStateSavedData::load,
            MoonStateSavedData::create,
            DATA_NAME
    );
}
```

Expose `state()`, `setState(MoonState)`, and `save(CompoundTag)`. `setState` must call `setDirty()` only when the state changes.

- [ ] **Step 5: Implement the network packet and channel.**

Register one packet ID only:

```java
CHANNEL.registerMessage(
        0,
        MoonStatePacket.class,
        MoonStatePacket::encode,
        MoonStatePacket::decode,
        MoonStatePacket::handle
);
```

The handler must enqueue on the client main thread, reject a packet whose revision is older than the cached revision, and never expose a client-to-server mutation packet.

After the packet class exists, modify `MoonJSMod` to add:

```java
modBus.addListener((FMLCommonSetupEvent event) ->
        event.enqueueWork(MoonNetwork::register));
```

- [ ] **Step 6: Run the state contract.**

Add a Gradle `JavaExec` task named `runMoonJSStateContract` using `sourceSets.test.runtimeClasspath`, then run:

```powershell
$env:GRADLE_USER_HOME='E:\Server_mod\.gradle'
.\gradlew.bat compileJava compileTestJava runMoonJSStateContract
```

Expected: all state assertions pass and the packet preserves the dimension, texture, color, intensity, and revision.

### Task 3: Implement server mutations, expiration, and player synchronization

**Files:**
- Create: `src/main/java/com/moonjs/server/MoonEventOptions.java`
- Create: `src/main/java/com/moonjs/server/MoonEventService.java`
- Create: `src/main/java/com/moonjs/server/MoonServerEvents.java`
- Modify: `src/main/java/com/moonjs/MoonJSMod.java`

**Interfaces:**
- `MoonEventService.start(ServerLevel, MoonPhaseDefinition, MoonEventOptions)` starts or replaces the dimension event.
- `MoonEventService.stop(ServerLevel)` clears event-owned fields and restores the normal moon strategy.
- `MoonEventService.setVanillaPhase(ServerLevel, int)` and `clearVanillaPhase(ServerLevel)` manage visual overrides.
- `MoonEventService.getState(ServerLevel)` returns the current immutable state.
- `MoonServerEvents.onLevelTick(TickEvent.LevelTickEvent)` expires events only on `END` phase and server side.

- [ ] **Step 1: Write the service contract first.**

Add tests for this sequence:

```java
long end = MoonEventService.endGameTime(100L, 6000L);
require(end == 6100L, "结束刻错误");
require(MoonEventService.isExpired(end, 6099L) == false, "提前过期");
require(MoonEventService.isExpired(end, 6100L), "达到结束刻后未过期");
```

Also assert that `durationTicks=-1` remains active, zero expires at the current game time, and `stop` removes the texture, event ID, and tint.

- [ ] **Step 2: Implement validated event options.**

Use this Java shape:

```java
public record MoonEventOptions(
        String eventId,
        long durationTicks,
        int colorRgb,
        float intensity
) {
    public MoonEventOptions {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId 不能为空");
        if (durationTicks < -1L) throw new IllegalArgumentException("durationTicks 必须为 -1 或非负数");
        if (!Float.isFinite(intensity) || intensity < 0.0F || intensity > 1.0F) {
            throw new IllegalArgumentException("intensity 必须在 0.0 到 1.0 之间");
        }
        colorRgb &= 0xFFFFFF;
    }
}
```

- [ ] **Step 3: Implement state transitions in one service.**

`start` must copy the registered phase ID, texture, base vanilla phase, event ID, color, and intensity into a new `MoonState`, calculate `currentGameTime + durationTicks` unless the duration is `-1`, increment the revision, mark `SavedData` dirty, and call `MoonNetwork.sendToDimension`.

`stop` must preserve an explicitly configured vanilla phase override if one exists, but clear `phaseId`, `texture`, `eventId`, `eventEndGameTime`, `colorRgb`, and `intensity`.

- [ ] **Step 4: Add server tick expiration and player lifecycle sync.**

Register handlers for:

```java
@SubscribeEvent public static void onLevelTick(TickEvent.LevelTickEvent event) {}
@SubscribeEvent public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {}
@SubscribeEvent public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {}
@SubscribeEvent public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {}
```

Only send `MoonStatePacket` to `ServerPlayer` instances. On expiration, call `stop` exactly once and broadcast the new revision.

- [ ] **Step 5: Run server-side contracts.**

Run:

```powershell
$env:GRADLE_USER_HOME='E:\Server_mod\.gradle'
.\gradlew.bat compileJava compileTestJava runMoonJSStateContract
```

Expected: expiry, replacement, stop, and player-sync code compile without any client imports in server packages.

### Task 4: Add the optional KubeJS binding surface

**Files:**
- Create: `src/main/java/com/moonjs/kubejs/MoonKubeJSOptions.java`
- Create: `src/main/java/com/moonjs/kubejs/MoonJSBindings.java`
- Create: `src/main/java/com/moonjs/kubejs/MoonJSKubeJSPlugin.java`
- Create: `src/test/java/com/moonjs/kubejs/MoonKubeJSOptionsContractTest.java`
- Modify: `src/main/resources/kubejs.plugins.txt`

**Interfaces:**
- `MoonJSKubeJSPlugin extends KubeJSPlugin` and overrides `registerBindings(BindingsEvent)`.
- `MoonJSBindings.registerPhase(String, Object)` is startup-only.
- `MoonJSBindings.start(Object, String, Object)` is server-only.
- `MoonJSBindings.stop(Object)`, `setVanillaPhase(Object, int)`, `clearVanillaPhase(Object)`, and `getState(Object)` are server-only.
- `MoonKubeJSOptions.parse(Map<?, ?>)` returns `MoonEventOptions`.

- [ ] **Step 1: Write option parser tests before the KubeJS wrapper.**

Test these maps:

```java
Map.of("eventId", "blood_moon", "durationTicks", 6000, "color", "#B51F2F", "intensity", 0.45)
Map.of("durationTicks", -1)
Map.of("intensity", 1.1)
Map.of("color", "not-a-color")
Map.of("durationTicks", -2)
```

Expected: the first two parse successfully; the last three throw `IllegalArgumentException` naming the invalid field.

- [ ] **Step 2: Implement KubeJS option conversion using the reference API.**

Convert the JavaScript object with `MapJS.of(options)`, require a map, accept numeric values through `Number`, accept `CharSequence` for strings, and reject unknown keys so script errors are not silently ignored. Parse both `#RRGGBB` and `#AARRGGBB`, discard alpha after validating it, and default `eventId="moon_event"`, `durationTicks=-1`, `color="#FFFFFF"`, and `intensity=0.0`.

- [ ] **Step 3: Implement side-checked bindings.**

Construct bindings with the current `ScriptType`:

```java
public MoonJSBindings(ScriptType scriptType) {
    this.scriptType = scriptType;
}
```

`registerPhase` must require `scriptType.isStartup()`. `start` and all state mutation/query methods must require `scriptType.isServer()`. Convert a level using:

```java
private static ServerLevel requireServerLevel(Object value) {
    if (value instanceof ServerLevel level) return level;
    if (value instanceof ServerLevelKJS level) return level.kjs$self();
    throw new IllegalArgumentException("level 必须是 ServerLevel");
}
```

Return `getState` as a fresh `Map<String, Object>` containing only serializable strings, numbers, and booleans; never return mutable `SavedData` or server objects to JavaScript.

- [ ] **Step 4: Register the plugin exactly as KubeJS 2001.6.5 expects.**

Use:

```java
public final class MoonJSKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("MoonJS", new MoonJSBindings(event.getType()));
    }
}
```

Put exactly this line in `src/main/resources/kubejs.plugins.txt`:

```text
com.moonjs.kubejs.MoonJSKubeJSPlugin
```

- [ ] **Step 5: Run the binding contracts and inspect optionality.**

Run:

```powershell
$env:GRADLE_USER_HOME='E:\Server_mod\.gradle'
.\gradlew.bat compileJava compileTestJava runMoonJSStateContract build
```

Inspect the JAR:

```powershell
jar tf .\build\libs\moonjs-*.jar | Select-String 'com/moonjs|kubejs.plugins.txt|kubejs-forge|rhino'
```

Expected: moonJS classes and `kubejs.plugins.txt` are present; no KubeJS or Rhino class files are present.

### Task 5: Implement client state caching, custom moon rendering, and blood-moon tinting

**Files:**
- Create: `src/main/java/com/moonjs/client/MoonClientState.java`
- Create: `src/main/java/com/moonjs/client/MoonDimensionEffects.java`
- Create: `src/main/java/com/moonjs/client/MoonSkyRenderer.java`
- Create: `src/main/java/com/moonjs/client/MoonClientEvents.java`
- Modify: `src/main/java/com/moonjs/network/MoonStatePacket.java`

**Interfaces:**
- `MoonClientState.apply(ResourceKey<Level>, MoonState)` accepts only newer revisions.
- `MoonClientState.get(ClientLevel)` returns the cached state or `MoonState.empty()`.
- `MoonDimensionEffects extends DimensionSpecialEffects.OverworldEffects` and overrides `renderSky` and `adjustLightmapColors`.
- `MoonSkyRenderer.render(ClientLevel, MoonState, PoseStack, Matrix4f, int, float, Camera, boolean, Runnable)` renders the standard sky and one single-image moon quad.
- `MoonClientEvents` handles dimension-effects registration, fog color, final world tint, and logout cleanup.

- [ ] **Step 1: Add client cache tests to the existing pure contract suite.**

Assert that revision 3 replaces revision 2, revision 2 cannot replace revision 3, different dimension keys do not share state, and clearing the cache removes all entries. Keep the test independent of `net.minecraft.client` by testing the cache’s key/version helper separately from the actual client event class.

- [ ] **Step 2: Implement the client packet handler.**

In `MoonStatePacket.handle`, enqueue:

```java
ctx.get().enqueueWork(() -> MoonClientState.apply(message.dimension(), message.state()));
ctx.get().setPacketHandled(true);
```

Do not access `ServerLevel`, `SavedData`, or KubeJS from this handler.

- [ ] **Step 3: Register the custom Overworld dimension effect.**

Subscribe on the mod event bus and register the effect for `minecraft:overworld`:

```java
@SubscribeEvent
public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
    event.register(new ResourceLocation("minecraft", "overworld"), new MoonDimensionEffects());
}
```

`renderSky` returns `false` when the state has no moon override, preserving vanilla rendering. When a custom texture or explicit vanilla phase is active, it calls `MoonSkyRenderer.render(level, state, poseStack, projectionMatrix, ticks, partialTick, camera, isFoggy, setupFog)` and returns `true`.

- [ ] **Step 4: Implement the 1.20.1 sky renderer from the mapped Forge sources.**

Keep the exact vanilla 1.20.1 operation order for sky disc, star field, sun, and matrix/depth cleanup. Replace only the moon portion:

```java
ResourceLocation texture = state.texture() != null
        ? state.texture()
        : new ResourceLocation("minecraft", "textures/environment/moon_phases.png");
```

For a custom single image, bind the texture and draw UV coordinates `(0, 0)` through `(1, 1)` on one moon quad. For an original atlas, select the 4×2 cell from `state.vanillaPhase()`. Restore the prior shader color, texture, blend mode, depth mask, and pose stack before returning.

- [ ] **Step 5: Apply lightmap and fog color changes.**

In `MoonDimensionEffects.adjustLightmapColors`, mix each lightmap vector toward the state RGB by `intensity`, clamp each component to `[0.0F, 1.0F]`, and leave the vector unchanged when intensity is zero.

In `MoonClientEvents`:

```java
@SubscribeEvent
public static void onFogColor(ViewportEvent.ComputeFogColor event) {
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null) return;
        MoonState state = MoonClientState.get(level);
        float strength = state.intensity();
        event.setRed(event.getRed() * (1.0F - strength) + state.red() * strength);
        event.setGreen(event.getGreen() * (1.0F - strength) + state.green() * strength);
        event.setBlue(event.getBlue() * (1.0F - strength) + state.blue() * strength);
}
```

Use the camera’s current client level only; do not retain a `ClientLevel` reference after logout.

- [ ] **Step 6: Draw the final world tint without covering HUD text.**

Listen to `RenderGuiOverlayEvent.Post`, check `event.getOverlay().id()` against `VanillaGuiOverlay.VIGNETTE.id()`, and call `GuiGraphics.fill` over the window bounds with an alpha derived from `intensity` and the state RGB. Do not cancel the vanilla overlay and do not draw on chat, title, hotbar, or text overlays.

- [ ] **Step 7: Clear client state on logout and compile on both distributions.**

Register `ClientPlayerNetworkEvent.LoggingOut` to clear `MoonClientState`. Run:

```powershell
$env:GRADLE_USER_HOME='E:\Server_mod\.gradle'
.\gradlew.bat compileJava build
```

Expected: the dedicated-server classpath compiles without loading client classes; the client classes compile against Forge 47.4.6 official mappings.

### Task 6: Documentation, static contracts, and final verification

**Files:**
- Modify: `README.txt`
- Modify: `scripts/moonjs_contract_test.ps1`
- Modify: `src/main/resources/META-INF/mods.toml` if expanded property checks require it

- [ ] **Step 1: Document the supported script and resource contract.**

Add the two examples from the spec, explain that `blood.png` must be supplied by a resource pack or another mod under the declared namespace, document server-game-time durations, and state that moon-phase changes are visual and do not modify `dayTime`.

- [ ] **Step 2: Add static resource and dependency checks.**

Extend the PowerShell contract to assert:

```powershell
$mods = Get-Content -Raw (Join-Path $projectRoot 'src/main/resources/META-INF/mods.toml')
if ($mods -notmatch 'modId="\$\{mod_id\}"') { throw 'mods.toml 未使用 mod_id 属性' }
if ($mods -notmatch 'modId="kubejs"[\s\S]*?mandatory=false') { throw 'KubeJS 未声明为可选依赖' }
if ($mods -match 'modId="examplemod"|com\.example') { throw 'Forge 元数据仍包含模板标识' }
```

- [ ] **Step 3: Run the complete offline verification set.**

Run:

```powershell
pwsh -NoProfile -File .\scripts\moonjs_contract_test.ps1
$env:GRADLE_USER_HOME='E:\Server_mod\.gradle'
.\gradlew.bat clean build runMoonJSStateContract
git diff --check
```

The last command is expected to report that the directory is not a Git repository; record it as unavailable rather than treating it as a code failure. Use the PowerShell contract and JAR inspection as the replacement checks.

- [ ] **Step 4: Attempt the runtime verification within the available environment.**

If the Forge client and KubeJS runtime are available, execute a startup script that registers `blood`, a server script that starts it for 6000 ticks, and inspect login, dimension change, moon texture, phase override, expiration, and tint behavior. If the game cannot be launched, report build/static evidence separately from unrun in-game validation.

- [ ] **Step 5: Review the final changed-file list.**

Confirm that only `E:\Server_mod\moonJS` changed, the reference KubeJS project is untouched, no template package remains, and no KubeJS/Rhino class files entered `build/libs/moonjs-*.jar`.
