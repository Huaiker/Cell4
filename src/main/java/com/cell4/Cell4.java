package com.cell4;

import com.cell4.common.handler.InfinityCellHandler;
import com.cell4.common.registration.Cell4CreativeTab;
import com.cell4.common.registration.Cell4Items;
import com.cell4.common.registration.Cell4MenuTypes;
import com.cell4.common.util.Cell4Config;
import com.cell4.network.Cell4Network;
import appeng.api.storage.StorageCells;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod(Cell4.MODID)
public class Cell4 {
    public static final String MODID = "cell4";
    public static final Logger LOGGER = LogManager.getLogger();

    public Cell4() {
        Cell4Config.init();
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Cell4Items.ITEMS.register(bus);
        Cell4CreativeTab.CREATIVE_TABS.register(bus);
        Cell4MenuTypes.MENU_TYPES.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::addPackFinders);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Cell\u2074 initialized");
    }

    private void addPackFinders(final AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        // 在资源包被打开前生成材质包，保证本次重载就能读到新材质（否则第一次 F3+T 会紫黑）。
        Cell4Config.refresh();
        Path packDir = Cell4Config.getPackDir();
        if (packDir == null || !Files.isDirectory(packDir)) return;
        try {
            event.addRepositorySource(consumer -> {
                Pack pack = Pack.readMetaAndCreate(
                        "cell4_custom_textures",
                        Component.literal("Cell4 Custom Textures"),
                        true,
                        id -> new PathPackResources(id, packDir, true),
                        PackType.CLIENT_RESOURCES,
                        Pack.Position.TOP,
                        PackSource.BUILT_IN
                );
                if (pack != null) consumer.accept(pack);
            });
        } catch (Throwable t) {
            LOGGER.warn("Cell4: failed to register custom texture pack", t);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            StorageCells.addCellHandler(InfinityCellHandler.INSTANCE);
            Cell4Network.register();
            LOGGER.info("Cell\u2074 cell handler registered with AE2");
        });
    }
}
