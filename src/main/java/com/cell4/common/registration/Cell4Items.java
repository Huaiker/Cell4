package com.cell4.common.registration;

import com.cell4.Cell4;
import com.cell4.common.item.CellConfigurator;
import com.cell4.common.item.IInfinityCell;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.item.PortableInfinityCell;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Cell4Items {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cell4.MODID);

    public static final DeferredItem<InfinityItemCell> INFINITY_ITEM_CELL =
        ITEMS.registerItem("infinity_item_cell", InfinityItemCell::new, props -> props.stacksTo(1));

    public static final DeferredItem<InfinityTagCell> INFINITY_TAG_CELL =
        ITEMS.registerItem("infinity_tag_cell", InfinityTagCell::new, props -> props.stacksTo(1));

    public static final DeferredItem<InfinityModIdCell> INFINITY_MODID_CELL =
        ITEMS.registerItem("infinity_modid_cell", InfinityModIdCell::new, props -> props.stacksTo(1));

    public static final DeferredItem<CellConfigurator> CELL_CONFIGURATOR =
        ITEMS.registerItem("cell_configurator", CellConfigurator::new, props -> props.stacksTo(1));

    public static final DeferredItem<PortableInfinityCell> PORTABLE_INFINITY_ITEM_CELL =
        ITEMS.registerItem("portable_infinity_item_cell",
            props -> new PortableInfinityCell(props, IInfinityCell.CellType.ITEM),
            props -> props.stacksTo(1));

    public static final DeferredItem<PortableInfinityCell> PORTABLE_INFINITY_TAG_CELL =
        ITEMS.registerItem("portable_infinity_tag_cell",
            props -> new PortableInfinityCell(props, IInfinityCell.CellType.TAG),
            props -> props.stacksTo(1));

    public static final DeferredItem<PortableInfinityCell> PORTABLE_INFINITY_MODID_CELL =
        ITEMS.registerItem("portable_infinity_modid_cell",
            props -> new PortableInfinityCell(props, IInfinityCell.CellType.MODID),
            props -> props.stacksTo(1));
}
