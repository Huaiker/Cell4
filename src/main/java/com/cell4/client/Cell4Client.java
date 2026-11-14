package com.cell4.client;

import com.cell4.common.registration.Cell4MenuTypes;
import com.cell4.common.util.Cell4Config;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.cell4.Cell4;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mod.EventBusSubscriber(modid = Cell4.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class Cell4Client {
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(Cell4MenuTypes.CELL_CONFIGURATOR.get(), CellConfiguratorScreen::new);
            Cell4Textures.registerItemProperties();
        });
    }

    /**
     * F3+T 时先重新扫描 config/Cell4/texture/ 并重建运行时资源包，
     * 再让模型/贴图加载，这样新增或修改贴图无需重启游戏。
     */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier,
                                                  ResourceManager resourceManager,
                                                  ProfilerFiller preparationsProfiler,
                                                  ProfilerFiller reloadProfiler,
                                                  Executor backgroundExecutor,
                                                  Executor gameExecutor) {
                return CompletableFuture.runAsync(Cell4Config::refresh, backgroundExecutor)
                        .thenCompose(barrier::wait);
            }
        });
    }
}
