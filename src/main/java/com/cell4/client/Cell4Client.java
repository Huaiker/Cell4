package com.cell4.client;

import com.cell4.Cell4;
import com.cell4.common.registration.Cell4MenuTypes;
import com.cell4.common.util.Cell4Config;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(modid = Cell4.MODID, value = Dist.CLIENT)
public class Cell4Client {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Cell4MenuTypes.CELL_CONFIGURATOR.get(), CellConfiguratorScreen::new);
    }

    @SubscribeEvent
    public static void registerItemModelProperties(RegisterSelectItemModelPropertyEvent event) {
        Cell4Textures.register(event);
    }

    /**
     * F3+T 时先重新扫描 config/Cell4/texture/ 并重建运行时资源包，
     * 再让模型/贴图加载，这样新增或修改贴图无需重启游戏。
     */
    @SubscribeEvent
    public static void registerReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Cell4.MODID, "custom_textures"),
                new PreparableReloadListener() {
                    @Override
                    public CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor,
                                                          PreparationBarrier barrier, Executor gameExecutor) {
                        return CompletableFuture.runAsync(Cell4Config::refresh, backgroundExecutor)
                                .thenCompose(barrier::wait);
                    }
                });
    }
}
