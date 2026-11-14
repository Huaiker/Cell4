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

/** REI 可选兼容：让 REI 的物品面板避开配置界面的预览区域，并支持从 REI 拖拽物品。 */
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
        return CellConfiguratorScreen.itemEntryFromStack(itemStack);
    }
}
