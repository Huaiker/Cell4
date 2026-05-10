package com.cell4.common.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.cells.CellState;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.util.Cell4Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * StorageCell implementation for the Infinity Item Cell.
 * Provides infinite extraction of specific AEKeys (items, fluids, etc.).
 * Insertion of matching keys is accepted but items are silently discarded (phantom storage).
 * Supports blacklist via cell4blacklist NBT key.
 */
public class InfinityItemStorage implements StorageCell {

    private final List<AEKey> recordKeys;
    private final Set<AEKey> recordKeySet;
    private final Cell4Util.BlacklistData blacklist;

    public InfinityItemStorage(ItemStack cellItem) {
        this.recordKeys = InfinityItemCell.getRecords(cellItem);
        this.recordKeySet = recordKeys.stream().collect(Collectors.toSet());
        this.blacklist = Cell4Util.getBlacklistData(cellItem);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // Accept matching keys but silently discard them (phantom storage sink)
        // Exclude blacklisted keys
        if (recordKeySet.contains(what) && !blacklist.isBlacklisted(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        // Tell AE2 to route matching items to this cell first
        // Exclude blacklisted keys
        return recordKeySet.contains(what) && !blacklist.isBlacklisted(what);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        // Allow infinite extraction of the recorded keys only
        // Exclude blacklisted keys
        if (recordKeySet.contains(what) && !blacklist.isBlacklisted(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        // Show all recorded keys with infinite amounts, excluding blacklisted
        for (AEKey key : recordKeys) {
            if (!blacklist.isBlacklisted(key)) {
                out.add(key, InfinityItemCell.getAsIntMax(key));
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_item_cell");
    }

    @Override
    public CellState getStatus() {
        return recordKeys.isEmpty() ? CellState.EMPTY : CellState.NOT_EMPTY;
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
