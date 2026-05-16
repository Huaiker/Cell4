package com.cell4.common.handler;

import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import com.cell4.common.item.IInfinityCell;
import com.cell4.common.storage.InfinityItemStorage;
import com.cell4.common.storage.InfinityModIdStorage;
import com.cell4.common.storage.InfinityTagStorage;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * AE2 Cell Handler that recognizes our Infinity Cell items and provides
 * the appropriate StorageCell implementations.
 * <p>
 * 2.4: Now caches Storage instances using WeakHashMap keyed by ItemStack identity,
 * avoiding repeated NBT parsing and registry traversal on every AE2 query.
 * The cache uses identity-based comparison so different ItemStack instances
 * with the same NBT won't collide, and entries are GC'd when the ItemStack is no longer referenced.
 * </p>
 */
public class InfinityCellHandler implements ICellHandler {

    public static final InfinityCellHandler INSTANCE = new InfinityCellHandler();

    // 2.4: Cache Storage instances to avoid recreating them on every query
    private final Map<ItemStack, StorageCell> storageCache = new WeakHashMap<>();

    @Override
    public boolean isCell(ItemStack is) {
        if (is == null || is.isEmpty()) return false;
        return is.getItem() instanceof IInfinityCell;
    }

    @Override
    public @Nullable StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        if (is == null || is.isEmpty()) return null;
        if (!(is.getItem() instanceof IInfinityCell)) return null;

        // 2.4: Return cached instance if available
        StorageCell cached = storageCache.get(is);
        if (cached != null) return cached;

        StorageCell storage;
        switch (((IInfinityCell) is.getItem()).getCellType()) {
            case ITEM -> storage = new InfinityItemStorage(is);
            case TAG -> storage = new InfinityTagStorage(is);
            case MODID -> storage = new InfinityModIdStorage(is);
            default -> { return null; }
        }

        storageCache.put(is, storage);
        return storage;
    }
}
