package com.moonjs.server;

public record MoonEventOptions(String eventId, long durationTicks, int colorRgb, float intensity) {
    public MoonEventOptions {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId 不能为空");
        if (durationTicks < -1L) throw new IllegalArgumentException("durationTicks 必须为 -1 或非负数");
        if (!Float.isFinite(intensity) || intensity < 0.0F || intensity > 1.0F) {
            throw new IllegalArgumentException("intensity 必须在 0.0 到 1.0 之间");
        }
        colorRgb &= 0xFFFFFF;
    }
}
