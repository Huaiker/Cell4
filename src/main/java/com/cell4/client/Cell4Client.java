package com.cell4.client;

import com.cell4.common.registration.Cell4MenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.cell4.Cell4;

@Mod.EventBusSubscriber(modid = Cell4.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class Cell4Client {
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(Cell4MenuTypes.CELL_CONFIGURATOR.get(), CellConfiguratorScreen::new);
        });
    }
}
