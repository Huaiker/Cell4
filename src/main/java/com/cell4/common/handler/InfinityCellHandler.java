package com.cell4.common.handler;

import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.storage.InfinityItemStorage;
import com.cell4.common.storage.InfinityModIdStorage;
import com.cell4.common.storage.InfinityTagStorage;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * AE2 Cell Handler that recognizes our Infinity Cell items and provides
 * the appropriate StorageCell implementations.
 * <p>
 * Registered with AE2 via StorageCells.addCellHandler() during
 * FMLCommonSetupEvent.
 * </p>
 * <p>
 * The overall cell handler pattern references ExtendAE's approach
 * for integrating custom cells with AE2's ME Drive and ME Chest systems.
 * </p>
 */
public class InfinityCellHandler implements ICellHandler {

    public static final InfinityCellHandler INSTANCE = new InfinityCellHandler();

    @Override
    public boolean isCell(ItemStack is) {
        if (is == null || is.isEmpty()) return false;
        return is.getItem() instanceof InfinityItemCell
                || is.getItem() instanceof InfinityTagCell
                || is.getItem() instanceof InfinityModIdCell;
    }

    @Override
    public @Nullable StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        if (is == null || is.isEmpty()) return null;

        if (is.getItem() instanceof InfinityItemCell) {
            return new InfinityItemStorage(is);
        } else if (is.getItem() instanceof InfinityTagCell) {
            return new InfinityTagStorage(is);
        } else if (is.getItem() instanceof InfinityModIdCell) {
            return new InfinityModIdStorage(is);
        }

        return null;
    }
}
