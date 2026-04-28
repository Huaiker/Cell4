package com.cell4;

import com.cell4.common.handler.InfinityCellHandler;
import com.cell4.common.registration.Cell4CreativeTab;
import com.cell4.common.registration.Cell4Items;
import appeng.api.storage.StorageCells;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Cell4.MODID)
public class Cell4 {

    public static final String MODID = "cell4";
    public static final Logger LOGGER = LogManager.getLogger();

    public Cell4() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register deferred registers
        Cell4Items.ITEMS.register(bus);
        Cell4CreativeTab.CREATIVE_TABS.register(bus);

        // Register setup event
        bus.addListener(this::commonSetup);

        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Cell\u2074 initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Register our custom cell handler with AE2
            StorageCells.addCellHandler(InfinityCellHandler.INSTANCE);
            LOGGER.info("Cell\u2074 cell handler registered with AE2");
        });
    }
}
