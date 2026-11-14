package com.cell4;

import com.cell4.common.handler.InfinityCellHandler;
import com.cell4.common.registration.Cell4CreativeTab;
import com.cell4.common.registration.Cell4Items;
import com.cell4.common.registration.Cell4MenuTypes;
import com.cell4.common.util.Cell4Config;
import appeng.api.storage.StorageCells;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Mod(Cell4.MODID)
public class Cell4 {
    public static final String MODID = "cell4";
    public static final Logger LOGGER = LogManager.getLogger();

    public Cell4(IEventBus modEventBus) {
        Cell4Config.init();
        Cell4Items.ITEMS.register(modEventBus);
        Cell4CreativeTab.CREATIVE_TABS.register(modEventBus);
        Cell4MenuTypes.MENU_TYPES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addPackFinders);
        LOGGER.info("Cell\u2074 initialized");
    }

    /**
     * Registers the generated custom-texture pack (config/Cell4/pack) with
     * highest priority so it can override the mod's base item models.
     */
    private void addPackFinders(final AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        // 在资源包被打开前生成材质包，保证本次重载就能读到新材质（否则第一次 F3+T 会紫黑）。
        Cell4Config.refresh();
        Path packDir = Cell4Config.getPackDir();
        if (packDir == null || !Files.isDirectory(packDir)) return;
        try {
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo("cell4_custom_textures",
                            Component.literal("Cell4 Custom Textures"),
                            PackSource.BUILT_IN, Optional.empty()),
                    new PathPackResources.PathResourcesSupplier(packDir),
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) {
                event.addRepositorySource(source -> source.accept(pack));
            }
        } catch (Throwable t) {
            LOGGER.warn("Cell4: failed to register custom texture pack", t);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            StorageCells.addCellHandler(InfinityCellHandler.INSTANCE);
            LOGGER.info("Cell\u2074 cell handler registered with AE2");
        });
    }
}
