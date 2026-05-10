package com.cell4.network;

import com.cell4.Cell4;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Cell4.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Cell4Network {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            CellConfigSavePacket.TYPE,
            CellConfigSavePacket.STREAM_CODEC,
            CellConfigSavePacket::handle
        );
    }
}
