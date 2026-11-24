package com.moonjs.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

public final class MoonState {
    private final String phaseId;
    private final int vanillaPhase;
    private final ResourceLocation texture;
    private final String eventId;
    private final long eventEndGameTime;
    private final int colorRgb;
    private final float intensity;
    private final long revision;

    public MoonState(String phaseId, int vanillaPhase, ResourceLocation texture, String eventId,
                     long eventEndGameTime, int colorRgb, float intensity, long revision) {
        if (vanillaPhase != -1) MoonPhaseRegistry.validateVanillaPhase(vanillaPhase);
        if (eventEndGameTime < -1) throw new IllegalArgumentException("Invalid eventEndGameTime: " + eventEndGameTime);
        if (colorRgb < 0 || colorRgb > 0xFFFFFF) throw new IllegalArgumentException("Invalid colorRgb: " + colorRgb);
        if (!Float.isFinite(intensity) || intensity < 0.0f || intensity > 1.0f) {
            throw new IllegalArgumentException("Invalid intensity: " + intensity);
        }
        if (revision < 0) throw new IllegalArgumentException("Invalid revision: " + revision);
        this.phaseId = phaseId;
        this.vanillaPhase = vanillaPhase;
        this.texture = texture;
        this.eventId = eventId;
        this.eventEndGameTime = eventEndGameTime;
        this.colorRgb = colorRgb;
        this.intensity = intensity;
        this.revision = revision;
    }

    public static MoonState empty() {
        return new MoonState(null, -1, null, null, -1, 0, 0.0f, 0);
    }

    public String phaseId() { return phaseId; }
    public int vanillaPhase() { return vanillaPhase; }
    public ResourceLocation texture() { return texture; }
    public String eventId() { return eventId; }
    public long eventEndGameTime() { return eventEndGameTime; }
    public int colorRgb() { return colorRgb; }
    public float red() { return ((colorRgb >> 16) & 0xFF) / 255.0f; }
    public float green() { return ((colorRgb >> 8) & 0xFF) / 255.0f; }
    public float blue() { return (colorRgb & 0xFF) / 255.0f; }
    public float intensity() { return intensity; }
    public long revision() { return revision; }
    public boolean hasMoonOverride() { return phaseId != null || vanillaPhase != -1 || texture != null; }

    public CompoundTag save(CompoundTag tag) {
        if (phaseId != null) tag.putString("phaseId", phaseId);
        else tag.remove("phaseId");
        tag.putInt("vanillaPhase", vanillaPhase);
        if (texture != null) tag.putString("texture", texture.toString());
        else tag.remove("texture");
        if (eventId != null) tag.putString("eventId", eventId);
        else tag.remove("eventId");
        tag.putLong("eventEndGameTime", eventEndGameTime);
        tag.putInt("colorRgb", colorRgb);
        tag.putFloat("intensity", intensity);
        tag.putLong("revision", revision);
        return tag;
    }

    public static MoonState load(CompoundTag tag) {
        return new MoonState(tag.contains("phaseId", 8) ? tag.getString("phaseId") : null,
                tag.contains("vanillaPhase", 3) ? tag.getInt("vanillaPhase") : -1,
                tag.contains("texture", 8) ? ResourceLocation.parse(tag.getString("texture")) : null,
                tag.contains("eventId", 8) ? tag.getString("eventId") : null,
                tag.contains("eventEndGameTime", 4) ? tag.getLong("eventEndGameTime") : -1,
                tag.getInt("colorRgb"), tag.getFloat("intensity"), tag.getLong("revision"));
    }

    public void writeNetwork(FriendlyByteBuf buffer, ResourceKey<Level> dimension) {
        Objects.requireNonNull(dimension, "dimension");
        buffer.writeResourceLocation(dimension.registry());
        buffer.writeResourceLocation(dimension.location());
        buffer.writeBoolean(phaseId != null);
        if (phaseId != null) buffer.writeUtf(phaseId);
        buffer.writeInt(vanillaPhase);
        buffer.writeBoolean(texture != null);
        if (texture != null) buffer.writeResourceLocation(texture);
        buffer.writeBoolean(eventId != null);
        if (eventId != null) buffer.writeUtf(eventId);
        buffer.writeLong(eventEndGameTime);
        buffer.writeInt(colorRgb);
        buffer.writeFloat(intensity);
        buffer.writeLong(revision);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MoonState state)) return false;
        return vanillaPhase == state.vanillaPhase && eventEndGameTime == state.eventEndGameTime
                && colorRgb == state.colorRgb && Float.compare(intensity, state.intensity) == 0
                && revision == state.revision && Objects.equals(phaseId, state.phaseId)
                && Objects.equals(texture, state.texture) && Objects.equals(eventId, state.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phaseId, vanillaPhase, texture, eventId, eventEndGameTime, colorRgb, intensity, revision);
    }
}
