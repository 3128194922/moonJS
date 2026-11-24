package com.moonjs.network;

import com.moonjs.MoonJSConstants;
import com.moonjs.server.MoonState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MoonNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MoonJSConstants.MOD_ID, "state"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private MoonNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, MoonStatePacket.class, MoonStatePacket::encode,
                MoonStatePacket::decode, MoonStatePacket::handle);
    }

    public static void sendToDimension(ServerLevel level, MoonState state) {
        MoonStatePacket packet = new MoonStatePacket(level.dimension(), state);
        for (ServerPlayer player : level.players()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void sendToPlayer(ServerPlayer player, MoonState state) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MoonStatePacket(player.serverLevel().dimension(), state));
    }
}
