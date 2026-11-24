package com.moonjs.kubejs;

import com.moonjs.server.MoonEventOptions;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Pattern;

public final class MoonKubeJSOptions {
    private static final Pattern COLOR = Pattern.compile("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    private MoonKubeJSOptions() {}

    public static MoonEventOptions parse(Map<?, ?> options) {
        if (options == null) throw new IllegalArgumentException("options 必须是对象");
        for (Object key : options.keySet()) {
            if (!(key instanceof String name) || !(name.equals("eventId")
                    || name.equals("durationTicks") || name.equals("color") || name.equals("intensity"))) {
                throw new IllegalArgumentException("unknown option: " + key);
            }
        }
        String eventId = options.containsKey("eventId") ? string(options.get("eventId"), "eventId") : "moon_event";
        long duration = options.containsKey("durationTicks") ? integer(options.get("durationTicks"), "durationTicks") : -1L;
        int color = options.containsKey("color") ? color(options.get("color")) : 0xFFFFFF;
        float intensity = options.containsKey("intensity") ? intensity(options.get("intensity")) : 0.0f;
        return new MoonEventOptions(eventId, duration, color, intensity);
    }

    private static String string(Object value, String field) {
        if (!(value instanceof CharSequence text)) throw new IllegalArgumentException(field + " 必须是字符串");
        return text.toString();
    }

    private static long integer(Object value, String field) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(field + " 必须是整数");
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IllegalArgumentException(field + " 必须是 long 范围内的整数", error);
        }
    }

    private static int color(Object value) {
        String text = string(value, "color");
        if (!COLOR.matcher(text).matches()) {
            throw new IllegalArgumentException("color 必须为 #RRGGBB 或 #AARRGGBB");
        }
        return Integer.parseInt(text.substring(text.length() - 6), 16);
    }

    private static float intensity(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("intensity 必须是数字");
        double valueDouble = number.doubleValue();
        if (!Double.isFinite(valueDouble) || valueDouble < 0.0 || valueDouble > 1.0) {
            throw new IllegalArgumentException("intensity 必须在 0.0 到 1.0 之间");
        }
        return (float) valueDouble;
    }
}
