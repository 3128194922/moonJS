package com.moonjs.client;

import com.moonjs.server.MoonState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class MoonClientState {
    private static final MoonStateCache CACHE = new MoonStateCache();

    private MoonClientState() {}

    public static boolean apply(ResourceKey<Level> dimension, MoonState state) {
        return CACHE.apply(dimension, state);
    }

    public static MoonState get(ClientLevel level) {
        return level == null ? MoonState.empty() : CACHE.get(level.dimension());
    }

    public static void clear() {
        CACHE.clear();
    }
}

// Pure key/revision storage; loading this helper never resolves a Minecraft client class.
final class MoonStateCache {
    private final Map<ResourceKey<Level>, MoonState> states = new HashMap<>();

    boolean apply(ResourceKey<Level> dimension, MoonState state) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(state, "state");
        MoonState previous = states.get(dimension);
        if (previous != null && state.revision() <= previous.revision()) return false;
        states.put(dimension, state);
        return true;
    }

    MoonState get(ResourceKey<Level> dimension) {
        return states.getOrDefault(Objects.requireNonNull(dimension, "dimension"), MoonState.empty());
    }

    void clear() {
        states.clear();
    }
}
