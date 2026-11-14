package com.cell4.compat;

import com.cell4.client.CellConfiguratorScreen;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** JEI 可选兼容：让 JEI 的物品面板避开配置界面的预览区域，并支持从 JEI 拖拽物品。 */
@JeiPlugin
public class Cell4JeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation("cell4", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(CellConfiguratorScreen.class,
                new IGuiContainerHandler<CellConfiguratorScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(CellConfiguratorScreen screen) {
                        Rect2i bounds = screen.getPreviewBounds();
                        return bounds == null ? List.of() : List.of(bounds);
                    }
                });

        // 允许从 JEI 左键拖动物品到配置器界面。
        registration.addGhostIngredientHandler(CellConfiguratorScreen.class,
                new IGhostIngredientHandler<CellConfiguratorScreen>() {
                    @Override
                    public <I> List<Target<I>> getTargetsTyped(CellConfiguratorScreen gui,
                                                               ITypedIngredient<I> ingredient,
                                                               boolean doStart) {
                        List<Target<I>> targets = new ArrayList<>();
                        if (!(ingredient.getIngredient() instanceof ItemStack stack)) return targets;
                        String id = CellConfiguratorScreen.itemEntryFromStack(stack);
                        if (id == null) return targets;
                        for (CellConfiguratorScreen.DropZone zone : gui.getDropZones()) {
                            targets.add(new Target<I>() {
                                @Override
                                public Rect2i getArea() {
                                    return zone.bounds();
                                }

                                @Override
                                public void accept(I ignored) {
                                    gui.acceptDrop(id, zone.bounds().getX() + 8, zone.bounds().getY() + 8);
                                }
                            });
                        }
                        return targets;
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }
}
