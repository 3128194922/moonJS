package com.moonjs.kubejs;

import com.moonjs.server.MoonEventOptions;

import java.util.Map;

public final class MoonKubeJSOptionsContractTest {
    public static void main(String[] args) {
        MoonEventOptions full = MoonKubeJSOptions.parse(Map.of("eventId", "blood_moon",
                "durationTicks", 6000, "color", "#B51F2F", "intensity", 0.45));
        require(full.eventId().equals("blood_moon") && full.durationTicks() == 6000L
                && full.colorRgb() == 0xB51F2F && full.intensity() == 0.45f, "full options");

        MoonEventOptions defaults = MoonKubeJSOptions.parse(Map.of("durationTicks", -1));
        require(defaults.eventId().equals("moon_event") && defaults.durationTicks() == -1L
                && defaults.colorRgb() == 0xFFFFFF && defaults.intensity() == 0.0f, "defaults");
        require(MoonKubeJSOptions.parse(Map.of("color", "#80b51f2f")).colorRgb() == 0xB51F2F,
                "alpha must be validated then discarded");
        require(MoonKubeJSOptions.parse(Map.of("durationTicks", 6.0)).durationTicks() == 6L,
                "integral JS number");

        rejects("intensity", Map.of("intensity", 1.1));
        rejects("color", Map.of("color", "not-a-color"));
        rejects("color", Map.of("color", "#GG112233"));
        rejects("durationTicks", Map.of("durationTicks", -2));
        rejects("durationTicks", Map.of("durationTicks", 1.5));
        rejects("eventId", Map.of("eventId", ""));
        rejects("unknown", Map.of("unknown", true));
        System.out.println("MoonJS KubeJS options contract: PASS");
    }

    private static void rejects(String field, Map<?, ?> map) {
        try {
            MoonKubeJSOptions.parse(map);
            throw new AssertionError("accepted invalid " + field);
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains(field), "error must name " + field);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
