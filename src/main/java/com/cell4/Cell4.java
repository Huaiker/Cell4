package com.cell4;

import com.cell4.common.handler.InfinityCellHandler;
import com.cell4.common.registration.Cell4CreativeTab;
import com.cell4.common.registration.Cell4Items;
import com.cell4.common.registration.Cell4MenuTypes;
import appeng.api.storage.StorageCells;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Cell4.MODID)
public class Cell4 {

    public static final String MODID = "cell4";
    public static final Logger LOGGER = LogManager.getLogger();

    public Cell4(IEventBus modEventBus) {
        // Register deferred registers
        Cell4Items.ITEMS.register(modEventBus);
        Cell4CreativeTab.CREATIVE_TABS.register(modEventBus);
        Cell4MenuTypes.MENU_TYPES.register(modEventBus);

        // Register setup event
        modEventBus.addListener(this::commonSetup);

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
