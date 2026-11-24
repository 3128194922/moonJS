package com.moonjs.server;

import com.moonjs.network.MoonStatePacket;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class MoonStateContractTest {
    static {
        try {
            SharedConstants.tryDetectVersion();
            // ResourceKey initializes vanilla registries; avoid Forge's full network bootstrap in this JavaExec.
            Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrapped.setAccessible(true);
            bootstrapped.setBoolean(null, true);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static final ResourceKey<Registry<Level>> DIMENSION_REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("minecraft", "dimension"));
    private static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(DIMENSION_REGISTRY, ResourceLocation.fromNamespaceAndPath("moonjs", "test_dimension"));

    public static void main(String[] args) {
        shouldRoundTripEmptyStateThroughNbt();
        shouldRoundTripCustomStateThroughNbt();
        shouldRoundTripCustomStateThroughNetwork();
        shouldRejectInvalidVanillaPhase();
        shouldRejectInvalidIntensity();
        shouldKeepDimensionKeyInNetworkState();
        shouldRemoveNullableValuesFromReusedNbt();
        shouldRejectMalformedNetworkState();
        shouldRejectDuplicateAndInvalidDefinitions();
        shouldApplyEventTimeBoundaries();
        shouldValidateEventOptions();
        shouldStopAndPreserveExplicitPhase();
        shouldPersistPhaseProvenance();
        shouldStartAndReplaceEvent();
        shouldSwitchAndClearVanillaPhaseDuringEvent();
        shouldKeepServerPackageFreeOfClientImports();
        System.out.println("MoonJS state contract: PASS");
    }

    private static void shouldRoundTripEmptyStateThroughNbt() {
        MoonState state = MoonState.load(MoonState.empty().save(new CompoundTag()));
        require(state.phaseId() == null && state.eventId() == null && state.texture() == null, "empty nullable fields");
        require(state.vanillaPhase() == -1 && state.eventEndGameTime() == -1, "empty sentinels");
        require(state.colorRgb() == 0 && state.intensity() == 0.0f && state.revision() == 0, "empty defaults");
        require(!state.hasMoonOverride(), "empty override");
    }

    private static void shouldRoundTripCustomStateThroughNbt() {
        MoonState state = MoonState.load(custom().save(new CompoundTag()));
        assertCustom(state);
    }

    private static void shouldRoundTripCustomStateThroughNetwork() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MoonStatePacket.encode(new MoonStatePacket(DIMENSION, custom()), buffer);
            assertCustom(MoonStatePacket.decode(buffer).state());
        } finally {
            buffer.release();
        }
    }

    private static void shouldRejectInvalidVanillaPhase() {
        expectInvalid(() -> new MoonState(null, 8, null, null, -1, 0, 0, 0), "phase 8");
        expectInvalid(() -> new MoonState(null, -2, null, null, -1, 0, 0, 0), "phase -2");
    }

    private static void shouldRejectInvalidIntensity() {
        expectInvalid(() -> new MoonState(null, -1, null, null, -1, 0, Float.NaN, 0), "NaN intensity");
        expectInvalid(() -> new MoonState(null, -1, null, null, -1, 0, 1.01f, 0), "high intensity");
    }

    private static void shouldKeepDimensionKeyInNetworkState() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MoonStatePacket.encode(new MoonStatePacket(DIMENSION, custom()), buffer);
            require(DIMENSION.equals(MoonStatePacket.decode(buffer).dimension()), "dimension key round trip");
        } finally {
            buffer.release();
        }
    }

    private static void shouldRemoveNullableValuesFromReusedNbt() {
        CompoundTag tag = custom().save(new CompoundTag());
        MoonState.empty().save(tag);
        require(!tag.contains("phaseId") && !tag.contains("texture") && !tag.contains("eventId"),
                "nullable strings must be absent");
        require(MoonState.empty().equals(MoonState.load(tag)), "reused NBT must load empty state");
    }

    private static void shouldRejectMalformedNetworkState() {
        assertBadPacket(ResourceLocation.fromNamespaceAndPath("minecraft", "dimension"), 8, 0.0f);
        assertBadPacket(ResourceLocation.fromNamespaceAndPath("minecraft", "dimension"), -1, Float.POSITIVE_INFINITY);
        assertBadPacket(ResourceLocation.fromNamespaceAndPath("minecraft", "item"), -1, 0.0f);
    }

    private static void assertBadPacket(ResourceLocation registry, int phase, float intensity) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeResourceLocation(registry);
            buffer.writeResourceLocation(ResourceLocation.fromNamespaceAndPath("moonjs", "test_dimension"));
            buffer.writeBoolean(false);
            buffer.writeInt(phase);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeLong(-1);
            buffer.writeInt(0);
            buffer.writeFloat(intensity);
            buffer.writeLong(0);
            expectInvalid(() -> MoonStatePacket.decode(buffer), "malformed network state");
        } finally {
            buffer.release();
        }
    }

    private static void shouldRejectDuplicateAndInvalidDefinitions() {
        MoonPhaseRegistry registry = new MoonPhaseRegistry();
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("moonjs", "textures/moon/blood.png");
        registry.register("blood", texture, 0);
        expectInvalid(() -> registry.register("blood", texture, 0), "duplicate phase ID");
        expectInvalid(() -> registry.register("B", texture, 0), "invalid phase ID");
        expectInvalid(() -> registry.register("red", texture, 8), "invalid definition phase");
        require(registry.all().size() == 1 && registry.get("blood") != null, "registry contents");
        try {
            registry.all().clear();
            throw new AssertionError("registry exposed mutable lookup");
        } catch (UnsupportedOperationException expected) {
            // The snapshot must be immutable.
        }
    }

    private static void shouldApplyEventTimeBoundaries() {
        long end = MoonEventService.endGameTime(100L, 6000L);
        require(end == 6100L, "结束刻错误");
        require(!MoonEventService.isExpired(end, 6099L), "提前过期");
        require(MoonEventService.isExpired(end, 6100L), "达到结束刻后未过期");
        require(MoonEventService.endGameTime(100L, -1L) == -1L, "无限时长结束刻");
        require(!MoonEventService.isExpired(-1L, Long.MAX_VALUE), "无限事件过期");
        require(MoonEventService.endGameTime(100L, 0L) == 100L, "零时长结束刻");
        require(MoonEventService.isExpired(100L, 100L), "零时长未过期");
        expectInvalid(() -> MoonEventService.endGameTime(100L, -2L), "invalid duration");
        expectInvalid(() -> MoonEventService.endGameTime(Long.MAX_VALUE, 1L), "overflow duration");
    }

    private static void shouldValidateEventOptions() {
        MoonEventOptions options = new MoonEventOptions("blood_moon", -1L, 0xAAB51F2F, 0.45f);
        require(options.colorRgb() == 0xB51F2F, "RGB mask");
        expectInvalid(() -> new MoonEventOptions(null, 1L, 0, 0), "null event ID");
        expectInvalid(() -> new MoonEventOptions("  ", 1L, 0, 0), "blank event ID");
        expectInvalid(() -> new MoonEventOptions("blood", -2L, 0, 0), "negative duration");
        expectInvalid(() -> new MoonEventOptions("blood", 1L, 0, Float.NaN), "NaN option intensity");
        expectInvalid(() -> new MoonEventOptions("blood", 1L, 0, Float.POSITIVE_INFINITY), "infinite option intensity");
        expectInvalid(() -> new MoonEventOptions("blood", 1L, 0, -0.1f), "negative option intensity");
        expectInvalid(() -> new MoonEventOptions("blood", 1L, 0, 1.1f), "high option intensity");
    }

    private static void shouldStopAndPreserveExplicitPhase() {
        MoonState active = custom();
        MoonState stopped = MoonEventService.stoppedState(active, 6);
        require(stopped.phaseId() == null && stopped.texture() == null && stopped.eventId() == null,
                "stop nullable event fields");
        require(stopped.eventEndGameTime() == -1L && stopped.colorRgb() == 0 && stopped.intensity() == 0.0f,
                "stop time and tint");
        require(stopped.vanillaPhase() == 6 && stopped.revision() == 8L, "stop explicit phase and revision");
        MoonState defaultState = MoonEventService.stoppedState(active, -1);
        require(defaultState.vanillaPhase() == -1 && !defaultState.hasMoonOverride(), "stop default phase");
        require(MoonEventService.stoppedState(stopped, 6).equals(stopped), "stop idempotence");
    }

    private static void shouldPersistPhaseProvenance() {
        MoonStateSavedData data = MoonStateSavedData.create();
        data.setExplicitVanillaPhase(6);
        data.setEventBaseVanillaPhase(4);
        MoonStateSavedData loaded = MoonStateSavedData.load(data.save(new CompoundTag()));
        require(loaded.explicitVanillaPhase() == 6, "explicit phase persistence");
        require(loaded.eventBaseVanillaPhase() == 4, "event base persistence");
    }

    private static void shouldStartAndReplaceEvent() {
        MoonPhaseDefinition phase = new MoonPhaseDefinition("blood",
                ResourceLocation.fromNamespaceAndPath("moonjs", "textures/moon/blood.png"), 4);
        MoonEventOptions options = new MoonEventOptions("blood_moon", 6000L, 0xB51F2F, 0.45f);
        MoonState started = MoonEventService.startedState(MoonState.empty(), phase, options, 100L);
        require("blood".equals(started.phaseId()) && phase.texture().equals(started.texture()), "start phase");
        require(started.vanillaPhase() == 4 && "blood_moon".equals(started.eventId()), "start event");
        require(started.eventEndGameTime() == 6100L && started.colorRgb() == 0xB51F2F,
                "start duration and color");
        require(started.intensity() == 0.45f && started.revision() == 1L, "start intensity and revision");
        MoonState replaced = MoonEventService.startedState(started, phase,
                new MoonEventOptions("other", -1L, 0, 0), 200L);
        require("other".equals(replaced.eventId()) && replaced.eventEndGameTime() == -1L,
                "replace event");
        require(replaced.revision() == 2L, "replace revision");
    }

    private static void shouldSwitchAndClearVanillaPhaseDuringEvent() {
        MoonState active = custom();
        MoonState overridden = MoonEventService.withVanillaPhase(active, 6);
        require(overridden.vanillaPhase() == 6 && overridden.revision() == 8L,
                "explicit phase during event");
        MoonState cleared = MoonEventService.withVanillaPhase(overridden, 4);
        require(cleared.vanillaPhase() == 4 && cleared.revision() == 9L,
                "clear returns event base phase");
        require("blood_moon".equals(cleared.eventId()) && cleared.texture().equals(active.texture()),
                "phase change preserves event");
    }

    private static void shouldKeepServerPackageFreeOfClientImports() {
        Path root = Path.of("src/main/java/com/moonjs/server");
        try (Stream<Path> files = Files.list(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    require(!Files.readString(path).contains("import net.minecraft.client."),
                            "client import in " + path);
                } catch (IOException error) {
                    throw new AssertionError("cannot read " + path, error);
                }
            });
        } catch (IOException error) {
            throw new AssertionError("cannot inspect server package", error);
        }
    }

    private static MoonState custom() {
        return new MoonState("blood", 4, ResourceLocation.fromNamespaceAndPath("moonjs", "textures/moon/blood.png"),
                "blood_moon", 123456L, 0xB51F2F, 0.45f, 7L);
    }

    private static void assertCustom(MoonState state) {
        require("blood".equals(state.phaseId()), "phase ID");
        require(state.vanillaPhase() == 4 && state.hasMoonOverride(), "phase override");
        require(ResourceLocation.fromNamespaceAndPath("moonjs", "textures/moon/blood.png").equals(state.texture()), "texture");
        require("blood_moon".equals(state.eventId()) && state.eventEndGameTime() == 123456L, "event fields");
        require(state.colorRgb() == 0xB51F2F, "RGB");
        require(state.red() == 181 / 255.0f && state.green() == 31 / 255.0f && state.blue() == 47 / 255.0f, "color channels");
        require(state.intensity() == 0.45f && state.revision() == 7L, "intensity and revision");
    }

    private static void expectInvalid(Runnable operation, String name) {
        try {
            operation.run();
            throw new AssertionError("accepted " + name);
        } catch (IllegalArgumentException expected) {
            // Invalid state must fail at construction.
        }
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
