package com.cell4.common.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.cells.CellState;
import com.cell4.common.item.IInfinityCell;
import com.cell4.common.util.Cell4Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;

/**
 * Abstract base class for all Infinity Storage implementations.
 * Extracts common logic: getIdleDrain(), persist(), getStatus(),
 * and the framework for insert/extract/isPreferredStorageFor.
 * Subclasses only need to implement matchesFilter() and getAvailableStacks().
 */
public abstract class AbstractInfinityStorage implements StorageCell {

    protected final Cell4Util.BlacklistData blacklist;
    protected final ItemStack cellItem;
    /** 构造时的元件自定义数据快照，用于判断缓存实例是否已过期。 */
    private final CustomData cellDataSnapshot;

    public AbstractInfinityStorage(ItemStack cellItem) {
        this.cellItem = cellItem;
        this.cellDataSnapshot = cellItem.get(DataComponents.CUSTOM_DATA);
        this.blacklist = Cell4Util.getBlacklistData(cellItem);
    }

    /**
     * 该存储实例是否仍与给定元件一致。
     * 配置器保存时会就地修改元件 NBT，AE2 的槽位又可能复用同一个 ItemStack 实例，
     * 因此返回缓存前必须校验快照，否则终端会一直显示旧的绑定配置。
     */
    public boolean matchesCell(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != cellItem.getItem()) {
            return false;
        }
        return Objects.equals(cellDataSnapshot, stack.get(DataComponents.CUSTOM_DATA));
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
