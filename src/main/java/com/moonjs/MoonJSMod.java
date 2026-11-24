package com.moonjs;

import com.moonjs.network.MoonNetwork;
import com.moonjs.server.MoonServerEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MoonJSConstants.MOD_ID)
public final class MoonJSMod {
    public MoonJSMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(MoonNetwork::register));
        MinecraftForge.EVENT_BUS.register(MoonServerEvents.class);
    }
}
