package com.cell4.compat;

import com.cell4.client.CellConfiguratorScreen;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Stream;

/**
 * 可选 REI 兼容：把元件配置器右侧的预览面板声明为排除区域，
 * REI 会把物品面板移到预览之外；同时允许从 REI 拖动物品到条目/网格。
 */
@REIPluginClient
public class Cell4ReiPlugin implements REIClientPlugin {

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(CellConfiguratorScreen.class, screen -> {
            Rect2i bounds = screen.getPreviewBounds();
            if (bounds == null) return List.of();
            return List.of(new Rectangle(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
        });
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerDraggableStackVisitor(new DraggableStackVisitor<CellConfiguratorScreen>() {
            @Override
            public <R extends Screen> boolean isHandingScreen(R screen) {
                return screen instanceof CellConfiguratorScreen;
            }

            @Override
            public DraggedAcceptorResult acceptDraggedStack(DraggingContext<CellConfiguratorScreen> context,
                                                            DraggableStack stack) {
                if (!(stack.getStack().getValue() instanceof ItemStack itemStack)) {
                    return DraggedAcceptorResult.PASS;
                }
                String id = CellConfiguratorScreen.itemEntryFromStack(itemStack);
                if (id == null) return DraggedAcceptorResult.PASS;
                Point position = context.getCurrentPosition();
                if (position == null) return DraggedAcceptorResult.PASS;
                CellConfiguratorScreen gui = context.getScreen();
                for (CellConfiguratorScreen.DropZone zone : gui.getDropZones()) {
                    if (zone.bounds().contains((int) position.x, (int) position.y)) {
                        gui.acceptDrop(id, position.x, position.y);
                        return DraggedAcceptorResult.ACCEPTED;
                    }
                }
                return DraggedAcceptorResult.PASS;
            }

            @Override
            public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<CellConfiguratorScreen> context,
                                                                      DraggableStack stack) {
                return context.getScreen().getDropZones().stream()
                        .map(zone -> BoundsProvider.ofRectangle(new Rectangle(
                                zone.bounds().getX(), zone.bounds().getY(),
                                zone.bounds().getWidth(), zone.bounds().getHeight())));
            }
        });
    }
}
