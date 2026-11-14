package com.cell4.client;

import com.cell4.common.registration.Cell4Items;
import com.cell4.common.util.Cell4Config;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Client-side property registration for custom cell textures (1.20.1).
 */
public final class Cell4Textures {

    private Cell4Textures() {}

    private static final ResourceLocation TEXTURE_INDEX =
            new ResourceLocation("cell4", "texture_index");

    public static void registerItemProperties() {
        ClampedItemPropertyFunction function = (stack, level, entity, seed) ->
                (float) Cell4Config.getTextureIndex(stack);
        for (Item item : new Item[]{
                Cell4Items.INFINITY_ITEM_CELL.get(),
                Cell4Items.INFINITY_TAG_CELL.get(),
                Cell4Items.INFINITY_MODID_CELL.get(),
                Cell4Items.PORTABLE_INFINITY_ITEM_CELL.get(),
                Cell4Items.PORTABLE_INFINITY_TAG_CELL.get(),
                Cell4Items.PORTABLE_INFINITY_MODID_CELL.get()
        }) {
            ItemProperties.register(item, TEXTURE_INDEX, function);
        }
    }
}
