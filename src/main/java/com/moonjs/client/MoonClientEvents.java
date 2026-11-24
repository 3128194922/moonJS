package com.moonjs.client;

import com.moonjs.MoonJSConstants;
import com.moonjs.server.MoonState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class MoonClientEvents {
    private MoonClientEvents() {}

    @Mod.EventBusSubscriber(modid = MoonJSConstants.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                    new MoonDimensionEffects());
        }
    }

    @Mod.EventBusSubscriber(modid = MoonJSConstants.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void onFogColor(ViewportEvent.ComputeFogColor event) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || event.getCamera().getEntity() == null
                    || event.getCamera().getEntity().level() != level) return;
            MoonState state = MoonClientState.get(level);
            float strength = state.intensity();
            if (strength <= 0.0f) return;
            event.setRed(mix(event.getRed(), state.red(), strength));
            event.setGreen(mix(event.getGreen(), state.green(), strength));
            event.setBlue(mix(event.getBlue(), state.blue(), strength));
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Post event) {
            if (!VanillaGuiOverlay.VIGNETTE.id().equals(event.getOverlay().id())) return;
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            MoonState state = MoonClientState.get(level);
            if (state.intensity() <= 0.0f) return;
            int alpha = Math.round(state.intensity() * 96.0f);
            int argb = (alpha << 24) | state.colorRgb();
            event.getGuiGraphics().fill(0, 0, event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight(), argb);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            MoonClientState.clear();
        }

        private static float mix(float current, float target, float strength) {
            return Math.max(0.0f, Math.min(1.0f, current * (1.0f - strength) + target * strength));
        }
    }
}
