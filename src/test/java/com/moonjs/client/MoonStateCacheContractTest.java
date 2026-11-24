package com.moonjs.client;

import com.moonjs.server.MoonState;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;

public final class MoonStateCacheContractTest {
    static {
        try {
            SharedConstants.tryDetectVersion();
            Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrapped.setAccessible(true);
            bootstrapped.setBoolean(null, true);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static final ResourceKey<Registry<Level>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath("minecraft", "dimension"));
    private static final ResourceKey<Level> OVERWORLD = dimension("overworld");
    private static final ResourceKey<Level> NETHER = dimension("the_nether");

    public static void main(String[] args) {
        MoonStateCache cache = new MoonStateCache();
        MoonState revision2 = state(2, 2);
        MoonState revision3 = state(3, 3);

        require(cache.get(OVERWORLD).equals(MoonState.empty()), "uncached dimension is empty");
        require(cache.apply(OVERWORLD, revision2), "revision 2 accepted");
        require(cache.apply(OVERWORLD, revision3), "revision 3 replaces 2");
        require(cache.get(OVERWORLD).equals(revision3), "newer state retained");
        require(!cache.apply(OVERWORLD, revision2), "older revision rejected");
        require(!cache.apply(OVERWORLD, state(3, 4)), "equal revision rejected");
        require(cache.get(OVERWORLD).equals(revision3), "stale packet did not replace state");

        require(cache.get(NETHER).equals(MoonState.empty()), "other dimension isolated");
        require(cache.apply(NETHER, revision2), "other dimension accepts own revision");
        require(cache.get(NETHER).equals(revision2), "other dimension stores own state");
        cache.clear();
        require(cache.get(OVERWORLD).equals(MoonState.empty()), "overworld cleared");
        require(cache.get(NETHER).equals(MoonState.empty()), "nether cleared");
        require(cache.apply(OVERWORLD, revision2), "new session accepts lower revision");
        System.out.println("MoonJS client cache contract: PASS");
    }

    private static ResourceKey<Level> dimension(String name) {
        return ResourceKey.create(REGISTRY, ResourceLocation.fromNamespaceAndPath("minecraft", name));
    }

    private static MoonState state(long revision, int phase) {
        return new MoonState(null, phase, null, null, -1, 0, 0, revision);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
