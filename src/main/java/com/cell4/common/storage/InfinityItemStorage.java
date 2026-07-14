package com.cell4.common.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.GenericStack;
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
        // Use set() instead of add() to prevent overflow.
        // AE2's KeyCounter.add() does raw long arithmetic: Long.MAX_VALUE + any positive
        // amount overflows to a negative long, causing AE2 to hide the stack from the terminal.
        // set() directly replaces the value, so it never accumulates beyond Long.MAX_VALUE.
        //
        // We always set to Long.MAX_VALUE (the cached value), regardless of what other cells
        // may have already added. This means:
        // - If our cell is called AFTER other cells: their add() results are overwritten — fine,
        //   infinite + finite = infinite.
        // - If our cell is called BEFORE other cells: we set MAX, then their add() may overflow.
        //   AE2 will hide the stack until the next cache refresh, when our cell runs again and
        //   detects the negative (overflowed) value, resetting it back to MAX.
        // - If the counter is already in a negative (broken) state, we reset it to MAX.
        for (var entry : cachedAvailableStacks) {
            AEKey key = entry.getKey();
            long existing = out.get(key);
            if (existing < 0) {
                // Counter overflowed from a previous add() — reset to MAX.
                out.set(key, Long.MAX_VALUE);
            } else if (existing < Long.MAX_VALUE) {
                // Overwrite whatever was there (0 or a finite amount from other cells) with MAX.
                out.set(key, Long.MAX_VALUE);
            }
            // else existing == Long.MAX_VALUE — already at max, no action needed.
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_item_cell");
    }
}
