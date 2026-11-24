package com.moonjs.kubejs;

import com.moonjs.server.MoonEventService;
import com.moonjs.server.MoonPhaseDefinition;
import com.moonjs.server.MoonPhaseRegistry;
import com.moonjs.server.MoonState;
import dev.latvian.mods.kubejs.core.ServerLevelKJS;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.MapJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class MoonJSBindings {
    private static final MoonPhaseRegistry PHASES = new MoonPhaseRegistry();
    private final ScriptType scriptType;

    public MoonJSBindings(ScriptType scriptType) {
        this.scriptType = Objects.requireNonNull(scriptType, "scriptType");
    }

    public void registerPhase(String id, Object options) {
        requireStartup();
        Map<?, ?> map = requireOptions(options);
        for (Object key : map.keySet()) {
            if (!"texture".equals(key) && !"vanillaPhase".equals(key)) {
                throw new IllegalArgumentException("未知 registerPhase 字段: " + key);
            }
        }
        Object value = map.get("texture");
        ResourceLocation texture;
        if (value instanceof ResourceLocation location) {
            texture = location;
        } else if (value instanceof CharSequence text) {
            try {
                texture = ResourceLocation.parse(text.toString());
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("texture 不是有效资源路径", error);
            }
        } else {
            throw new IllegalArgumentException("texture 必须是资源路径");
        }
        int vanillaPhase = map.containsKey("vanillaPhase") ? vanillaPhase(map.get("vanillaPhase")) : 0;
        PHASES.register(id, texture, vanillaPhase);
    }

    public void start(Object level, String phaseId, Object options) {
        requireServer();
        ServerLevel serverLevel = requireServerLevel(level);
        MoonPhaseDefinition phase = PHASES.get(phaseId);
        if (phase == null) throw new IllegalArgumentException("phaseId 未注册: " + phaseId);
        MoonEventService.start(serverLevel, phase, MoonKubeJSOptions.parse(requireOptions(options)));
    }

    public void stop(Object level) {
        requireServer();
        MoonEventService.stop(requireServerLevel(level));
    }

    public void setVanillaPhase(Object level, int phase) {
        requireServer();
        MoonEventService.setVanillaPhase(requireServerLevel(level), phase);
    }

    public void clearVanillaPhase(Object level) {
        requireServer();
        MoonEventService.clearVanillaPhase(requireServerLevel(level));
    }

    public Map<String, Object> getState(Object level) {
        requireServer();
        MoonState state = MoonEventService.getState(requireServerLevel(level));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phaseId", state.phaseId() == null ? "" : state.phaseId());
        result.put("vanillaPhase", state.vanillaPhase());
        result.put("texture", state.texture() == null ? "" : state.texture().toString());
        result.put("eventId", state.eventId() == null ? "" : state.eventId());
        result.put("eventEndGameTime", state.eventEndGameTime());
        result.put("colorRgb", state.colorRgb());
        result.put("intensity", state.intensity());
        result.put("revision", state.revision());
        result.put("hasMoonOverride", state.hasMoonOverride());
        return result;
    }

    private void requireStartup() {
        if (!scriptType.isStartup()) throw new IllegalStateException("registerPhase 只能在 startup_scripts 调用");
    }

    private void requireServer() {
        if (!scriptType.isServer()) throw new IllegalStateException("此方法只能在 server_scripts 调用");
    }

    private static Map<?, ?> requireOptions(Object value) {
        Map<?, ?> map = MapJS.of(value);
        if (map == null) throw new IllegalArgumentException("options 必须是对象");
        return map;
    }

    private static int vanillaPhase(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("vanillaPhase 必须是 0 到 7 的整数");
        try {
            int phase = new BigDecimal(number.toString()).intValueExact();
            if (phase < 0 || phase > 7) throw new IllegalArgumentException("vanillaPhase 必须在 0 到 7 之间");
            return phase;
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IllegalArgumentException("vanillaPhase 必须是 0 到 7 的整数", error);
        }
    }

    private static ServerLevel requireServerLevel(Object value) {
        if (value instanceof ServerLevel level) return level;
        if (value instanceof ServerLevelKJS level) return level.kjs$self();
        throw new IllegalArgumentException("level 必须是 ServerLevel");
    }
}
