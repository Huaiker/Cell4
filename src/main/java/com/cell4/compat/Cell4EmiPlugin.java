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

/** EMI 可选兼容：让 EMI 的物品面板避开配置界面的预览区域，并支持从 EMI 拖拽物品。 */
@EmiEntrypoint
public class Cell4EmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addScreenBoundsProvider(CellConfiguratorScreen.class, screen -> {
            Rect2i bounds = screen.getPreviewBounds();
            if (bounds == null) return Bounds.EMPTY;
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
            if (entry != null) return entry;
        }
        return null;
    }
}
