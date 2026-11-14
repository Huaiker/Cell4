package com.cell4.compat;

import com.cell4.client.CellConfiguratorScreen;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * 可选 EMI 兼容：把元件配置器右侧的预览面板声明为屏幕边界，
 * EMI 会把物品面板移到预览之外，并支持从 EMI 拖拽物品到配置器。
 */
@EmiEntrypoint
public class Cell4EmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addScreenBoundsProvider(CellConfiguratorScreen.class, screen -> {
            Rect2i bounds = screen.getPreviewBounds();
            if (bounds == null) return null;
            return new Bounds(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        });

        registry.addDragDropHandler(CellConfiguratorScreen.class,
                new EmiDragDropHandler.BoundsBased<CellConfiguratorScreen>((screen, consumer) -> {
                    for (CellConfiguratorScreen.DropZone zone : screen.getDropZones()) {
                        Rect2i b = zone.bounds();
                        consumer.accept(new Bounds(b.getX(), b.getY(), b.getWidth(), b.getHeight()), ingredient -> {
                            String id = itemIdOf(ingredient);
                            if (id != null) screen.acceptDrop(id, b.getX() + 8, b.getY() + 8);
                        });
                    }
                }));
    }

    private static String itemIdOf(EmiIngredient ingredient) {
        for (EmiStack stack : ingredient.getEmiStacks()) {
            ItemStack itemStack = stack.getItemStack();
            if (itemStack.isEmpty()) continue;
            String entry = CellConfiguratorScreen.itemEntryFromStack(itemStack);
            if (!entry.isEmpty()) return entry;
        }
        return null;
    }
}
