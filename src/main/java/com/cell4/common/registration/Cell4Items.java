package com.cell4.common.registration;

import com.cell4.Cell4;
import com.cell4.common.item.CellConfigurator;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Cell4Items {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, Cell4.MODID);

    public static final DeferredHolder<Item, InfinityItemCell> INFINITY_ITEM_CELL =
            ITEMS.register("infinity_item_cell", () -> new InfinityItemCell(new Item.Properties()));

    public static final DeferredHolder<Item, InfinityTagCell> INFINITY_TAG_CELL =
            ITEMS.register("infinity_tag_cell", () -> new InfinityTagCell(new Item.Properties()));

    public static final DeferredHolder<Item, InfinityModIdCell> INFINITY_MODID_CELL =
            ITEMS.register("infinity_modid_cell", () -> new InfinityModIdCell(new Item.Properties()));

    public static final DeferredHolder<Item, CellConfigurator> CELL_CONFIGURATOR =
            ITEMS.register("cell_configurator", () -> new CellConfigurator(new Item.Properties()));
}
