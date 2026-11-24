package com.moonjs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moonjs.server.MoonState;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class MoonDimensionEffects extends DimensionSpecialEffects.OverworldEffects {
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        MoonState state = MoonClientState.get(level);
        if (state.texture() == null && state.vanillaPhase() < 0) return false;
        MoonSkyRenderer.render(level, state, poseStack, projectionMatrix, ticks, partialTick,
                camera, isFoggy, setupFog);
        return true;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken,
                                     float blockLightRedFlicker, float skyLight, int pixelX, int pixelY,
                                     Vector3f colors) {
        MoonState state = MoonClientState.get(level);
        float strength = state.intensity();
        if (strength <= 0.0f) return;
        colors.set(mix(colors.x, state.red(), strength), mix(colors.y, state.green(), strength),
                mix(colors.z, state.blue(), strength));
    }

    private static float mix(float current, float target, float strength) {
        return Math.max(0.0f, Math.min(1.0f, current * (1.0f - strength) + target * strength));
    }
}
