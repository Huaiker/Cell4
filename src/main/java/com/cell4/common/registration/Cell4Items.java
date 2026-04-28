package com.cell4.common.registration;

import com.cell4.Cell4;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Cell4Items {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Cell4.MODID);

    public static final RegistryObject<InfinityItemCell> INFINITY_ITEM_CELL =
            ITEMS.register("infinity_item_cell", InfinityItemCell::new);

    public static final RegistryObject<InfinityTagCell> INFINITY_TAG_CELL =
            ITEMS.register("infinity_tag_cell", InfinityTagCell::new);

    public static final RegistryObject<InfinityModIdCell> INFINITY_MODID_CELL =
            ITEMS.register("infinity_modid_cell", InfinityModIdCell::new);
}
