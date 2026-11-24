package com.moonjs.network;

import com.moonjs.server.MoonState;
import com.moonjs.client.MoonClientState;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.Objects;
import java.util.function.Supplier;

public final class MoonStatePacket {
    private static final ResourceLocation DIMENSION_REGISTRY = ResourceLocation.fromNamespaceAndPath("minecraft", "dimension");
    private static final ResourceKey<Registry<Level>> DIMENSION_KEY_TYPE = ResourceKey.createRegistryKey(DIMENSION_REGISTRY);
    private final ResourceKey<Level> dimension;
    private final MoonState state;

    public MoonStatePacket(ResourceKey<Level> dimension, MoonState state) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        if (!DIMENSION_REGISTRY.equals(dimension.registry())) {
            throw new IllegalArgumentException("Not a dimension key: " + dimension);
        }
        this.state = Objects.requireNonNull(state, "state");
    }

    public ResourceKey<Level> dimension() { return dimension; }
    public MoonState state() { return state; }

    public static void encode(MoonStatePacket packet, FriendlyByteBuf buffer) {
        packet.state.writeNetwork(buffer, packet.dimension);
    }

    public static MoonStatePacket decode(FriendlyByteBuf buffer) {
        ResourceLocation registry = buffer.readResourceLocation();
        if (!DIMENSION_REGISTRY.equals(registry)) throw new IllegalArgumentException("Not a dimension registry: " + registry);
        ResourceKey<Level> dimension = ResourceKey.create(DIMENSION_KEY_TYPE, buffer.readResourceLocation());
        String phaseId = buffer.readBoolean() ? buffer.readUtf() : null;
        int vanillaPhase = buffer.readInt();
        ResourceLocation texture = buffer.readBoolean() ? buffer.readResourceLocation() : null;
        String eventId = buffer.readBoolean() ? buffer.readUtf() : null;
        long eventEndGameTime = buffer.readLong();
        int colorRgb = buffer.readInt();
        float intensity = buffer.readFloat();
        long revision = buffer.readLong();
        if (vanillaPhase < -1 || vanillaPhase > 7) {
            throw new IllegalArgumentException("Invalid vanillaPhase in packet: " + vanillaPhase);
        }
        if (!Float.isFinite(intensity) || intensity < 0.0f || intensity > 1.0f) {
            throw new IllegalArgumentException("Invalid intensity in packet: " + intensity);
        }
        return new MoonStatePacket(dimension, new MoonState(phaseId, vanillaPhase, texture, eventId,
                eventEndGameTime, colorRgb, intensity, revision));
    }

    public static void handle(MoonStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MoonClientState.apply(packet.dimension, packet.state)));
        context.setPacketHandled(true);
    }
}
