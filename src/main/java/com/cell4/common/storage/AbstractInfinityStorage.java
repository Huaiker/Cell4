package com.cell4.common.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.cells.CellState;
import com.cell4.common.util.Cell4Util;
import net.minecraft.world.item.ItemStack;

/**
 * Abstract base class for all Infinity Storage implementations.
 * Extracts common logic: getIdleDrain(), persist(), getStatus(),
 * and the framework for insert/extract/isPreferredStorageFor.
 * Subclasses only need to implement matchesFilter() and getAvailableStacks().
 */
public abstract class AbstractInfinityStorage implements StorageCell {

    protected final Cell4Util.BlacklistData blacklist;
    protected final ItemStack cellItem;

    public AbstractInfinityStorage(ItemStack cellItem) {
        this.cellItem = cellItem;
        this.blacklist = Cell4Util.getBlacklistData(cellItem);
    }

    /**
     * Check if the given key matches this storage's filter.
     */
    protected abstract boolean matchesFilter(AEKey what);

    /**
     * Check if this storage has any valid configuration (non-empty).
     */
    protected abstract boolean hasConfiguration();

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!hasConfiguration()) return 0;
        if (matchesFilter(what) && !blacklist.isBlacklisted(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (!hasConfiguration()) return false;
        return matchesFilter(what) && !blacklist.isBlacklisted(what);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!hasConfiguration()) return 0;
        if (matchesFilter(what) && !blacklist.isBlacklisted(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public CellState getStatus() {
        return hasConfiguration() ? CellState.NOT_EMPTY : CellState.EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return 0.0;
    }

    @Override
    public void persist() {
        // No persistence needed - the cell is always infinite
    }
}
