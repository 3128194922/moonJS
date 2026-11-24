package com.moonjs.server;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class MoonPhaseDefinition {
    private final String id;
    private final ResourceLocation texture;
    private final int vanillaPhase;

    public MoonPhaseDefinition(String id, ResourceLocation texture, int vanillaPhase) {
        this.id = MoonPhaseRegistry.validateId(id);
        this.texture = Objects.requireNonNull(texture, "texture");
        this.vanillaPhase = MoonPhaseRegistry.validateVanillaPhase(vanillaPhase);
    }

    public String id() { return id; }
    public ResourceLocation texture() { return texture; }
    public int vanillaPhase() { return vanillaPhase; }
}
