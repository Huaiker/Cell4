package com.cell4.client;

import com.cell4.Cell4;
import com.cell4.common.registration.Cell4MenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Cell4.MODID, value = Dist.CLIENT)
public class Cell4Client {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Cell4MenuTypes.CELL_CONFIGURATOR.get(), CellConfiguratorScreen::new);
    }
}
