package com.cell4.common.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.cell4.common.item.IInfinityCell;
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
public class InfinityItemStorage extends AbstractInfinityStorage {

    private final List<AEKey> recordKeys;
    private final Set<AEKey> recordKeySet;

    // 2.2: Lazy-load and cache available stacks
    private KeyCounter cachedAvailableStacks;
    private boolean cacheValid = false;

    public InfinityItemStorage(ItemStack cellItem) {
        super(cellItem);
        this.recordKeys = InfinityItemCell.getRecords(cellItem);
        this.recordKeySet = recordKeys.stream().collect(Collectors.toSet());
    }

    @Override
    protected boolean matchesFilter(AEKey what) {
        return recordKeySet.contains(what);
    }

    @Override
    protected boolean hasConfiguration() {
        return !recordKeys.isEmpty();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        // 2.2: Lazy-load and cache available stacks
        if (!cacheValid) {
            cachedAvailableStacks = new KeyCounter();
            for (AEKey key : recordKeys) {
                if (!blacklist.isBlacklisted(key)) {
                    cachedAvailableStacks.add(key, IInfinityCell.getAsIntMax(key));
                }
            }
            cacheValid = true;
        }
        // Copy cached data to output
        for (var entry : cachedAvailableStacks) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_item_cell");
    }
}
