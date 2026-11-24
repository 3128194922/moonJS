package com.moonjs.server;

import com.moonjs.network.MoonNetwork;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public final class MoonEventService {
    private MoonEventService() {}

    public static void start(ServerLevel level, MoonPhaseDefinition phase, MoonEventOptions options) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(options, "options");
        MoonStateSavedData data = MoonStateSavedData.get(level);
        MoonState next = startedState(data.state(), phase, options, level.getGameTime());
        data.setEventBaseVanillaPhase(phase.vanillaPhase());
        publish(level, data, next);
    }

    static MoonState startedState(MoonState current, MoonPhaseDefinition phase,
                                  MoonEventOptions options, long gameTime) {
        return new MoonState(phase.id(), phase.vanillaPhase(), phase.texture(), options.eventId(),
                endGameTime(gameTime, options.durationTicks()), options.colorRgb(),
                options.intensity(), Math.incrementExact(current.revision()));
    }

    public static void stop(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        MoonStateSavedData data = MoonStateSavedData.get(level);
        MoonState next = stoppedState(data.state(), data.explicitVanillaPhase());
        if (next == data.state()) return;
        data.setEventBaseVanillaPhase(-1);
        publish(level, data, next);
    }

    static MoonState stoppedState(MoonState current, int explicitVanillaPhase) {
        Objects.requireNonNull(current, "current");
        if (explicitVanillaPhase != -1) MoonPhaseRegistry.validateVanillaPhase(explicitVanillaPhase);
        if (current.eventId() == null && current.phaseId() == null && current.texture() == null
                && current.eventEndGameTime() == -1 && current.colorRgb() == 0 && current.intensity() == 0.0f) {
            return current;
        }
        return new MoonState(null, explicitVanillaPhase, null, null, -1L, 0, 0.0f,
                Math.incrementExact(current.revision()));
    }

    public static void setVanillaPhase(ServerLevel level, int phase) {
        Objects.requireNonNull(level, "level");
        MoonPhaseRegistry.validateVanillaPhase(phase);
        MoonStateSavedData data = MoonStateSavedData.get(level);
        MoonState current = data.state();
        if (data.explicitVanillaPhase() == phase && current.vanillaPhase() == phase) return;
        MoonState next = withVanillaPhase(current, phase);
        data.setExplicitVanillaPhase(phase);
        publish(level, data, next);
    }

    public static void clearVanillaPhase(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        MoonStateSavedData data = MoonStateSavedData.get(level);
        MoonState current = data.state();
        int phase = current.eventId() == null ? -1 : data.eventBaseVanillaPhase();
        if (data.explicitVanillaPhase() == -1 && current.vanillaPhase() == phase) return;
        MoonState next = withVanillaPhase(current, phase);
        data.setExplicitVanillaPhase(-1);
        publish(level, data, next);
    }

    static MoonState withVanillaPhase(MoonState current, int phase) {
        return new MoonState(current.phaseId(), phase, current.texture(), current.eventId(),
                current.eventEndGameTime(), current.colorRgb(), current.intensity(),
                Math.incrementExact(current.revision()));
    }

    public static MoonState getState(ServerLevel level) {
        return MoonStateSavedData.get(Objects.requireNonNull(level, "level")).state();
    }

    public static long endGameTime(long currentGameTime, long durationTicks) {
        if (durationTicks < -1L) throw new IllegalArgumentException("durationTicks 必须为 -1 或非负数");
        if (durationTicks == -1L) return -1L;
        try {
            return Math.addExact(currentGameTime, durationTicks);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("结束刻超出 long 范围", error);
        }
    }

    public static boolean isExpired(long endGameTime, long currentGameTime) {
        return endGameTime != -1L && currentGameTime >= endGameTime;
    }

    private static void publish(ServerLevel level, MoonStateSavedData data, MoonState next) {
        data.setState(next);
        MoonNetwork.sendToDimension(level, next);
    }
}
