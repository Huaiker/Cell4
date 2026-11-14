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
 * REI 会把物品面板移到预览之外，并支持从 REI 拖拽物品到配置器。
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
            public Stream<BoundsProvider> getDraggableAcceptingBounds(
                    DraggingContext<CellConfiguratorScreen> context, DraggableStack stack) {
                if (itemIdOf(stack) == null) return Stream.empty();
                return context.getScreen().getDropZones().stream().map(zone -> {
                    Rect2i b = zone.bounds();
                    return BoundsProvider.ofRectangle(
                            new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
                });
            }

            @Override
            public DraggedAcceptorResult acceptDraggedStack(
                    DraggingContext<CellConfiguratorScreen> context, DraggableStack stack) {
                String id = itemIdOf(stack);
                if (id == null) return DraggedAcceptorResult.PASS;
                Point pos = context.getCurrentPosition();
                if (pos == null) return DraggedAcceptorResult.PASS;
                for (CellConfiguratorScreen.DropZone zone : context.getScreen().getDropZones()) {
                    if (zone.bounds().contains(pos.getX(), pos.getY())) {
                        context.getScreen().acceptDrop(id, pos.getX(), pos.getY());
                        return DraggedAcceptorResult.ACCEPTED;
                    }
                }
                return DraggedAcceptorResult.PASS;
            }
        });
    }

    private static String itemIdOf(DraggableStack stack) {
        if (stack == null) return null;
        Object value = stack.getStack().getValue();
        if (!(value instanceof ItemStack itemStack) || itemStack.isEmpty()) return null;
        String entry = CellConfiguratorScreen.itemEntryFromStack(itemStack);
        return entry.isEmpty() ? null : entry;
    }
}
