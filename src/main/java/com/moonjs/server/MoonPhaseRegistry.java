package com.moonjs.server;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class MoonPhaseRegistry {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private final LinkedHashMap<String, MoonPhaseDefinition> phases = new LinkedHashMap<>();

    public synchronized MoonPhaseDefinition register(String id, ResourceLocation texture, int vanillaPhase) {
        MoonPhaseDefinition definition = new MoonPhaseDefinition(id, texture, vanillaPhase);
        if (phases.containsKey(id)) throw new IllegalArgumentException("Duplicate phase ID: " + id);
        phases.put(id, definition);
        return definition;
    }

    public synchronized MoonPhaseDefinition get(String id) {
        return phases.get(id);
    }

    public synchronized Map<String, MoonPhaseDefinition> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(phases));
    }

    static String validateId(String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid phase ID: " + id);
        }
        return id;
    }

    static int validateVanillaPhase(int phase) {
        if (phase < 0 || phase > 7) throw new IllegalArgumentException("Invalid vanillaPhase: " + phase);
        return phase;
    }
}
