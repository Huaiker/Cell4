package com.cell4.common.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.cell4.common.item.IInfinityCell;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.util.Cell4Patterns;
import com.cell4.common.util.Cell4Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * StorageCell implementation for the Infinity Item Cell.
 * Provides infinite extraction of specific AEKeys (items, fluids, etc.).
 * Insertion of matching keys is accepted but items are silently discarded (phantom storage).
 * Supports blacklist via cell4blacklist NBT key and glob/regex patterns via cell4item.
 */
public class InfinityItemStorage extends AbstractInfinityStorage {

    private final List<String> patterns;
    private final Set<AEKey> exactKeys;
    /** 按配置顺序保存每个精确条目的 AEKey：同一 ID 的不同组件/NBT 变体互不覆盖。 */
    private final List<AEKey> exactKeyOrder;
    private final Set<String> exactEntrySeen;
    private KeyCounter cachedAvailableStacks;
    private boolean cacheValid = false;

    public InfinityItemStorage(ItemStack cellItem) {
        super(cellItem);
        List<String> entries = InfinityItemCell.getIdentifiers(cellItem);
        this.exactKeys = new LinkedHashSet<>();
        this.exactKeyOrder = new ArrayList<>();
        this.exactEntrySeen = new HashSet<>();
        this.patterns = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String value = entry.trim();
            if (value.isEmpty()) continue;
            if (value.indexOf('{') < 0 && Cell4Patterns.isPattern(value)) {
                if (Cell4Patterns.isValidPattern(value) && !patterns.contains(value)) patterns.add(value);
            } else if (exactEntrySeen.add(value)) {
                // 以完整条目字符串去重：id 相同但组件/NBT 不同的变体都会保留。
                AEKey key = Cell4Util.parseIdentifier(value);
                if (key != null) {
                    exactKeys.add(key);
                    exactKeyOrder.add(key);
                }
            }
        }
    }

    @Override
    protected boolean matchesFilter(AEKey what) {
        if (exactKeys.contains(what)) return true;
        for (String pattern : patterns) {
            if (Cell4Patterns.matches(pattern, what)) return true;
        }
        return false;
    }

    @Override
    protected boolean hasConfiguration() {
        return !exactKeys.isEmpty() || !patterns.isEmpty();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        // 2.2: Lazy-load and cache available stacks
        if (!cacheValid) {
            cachedAvailableStacks = new KeyCounter();
            Set<AEKey> added = new HashSet<>();

            // 精确条目按条目顺序逐个写入：同一 ID 的组件/NBT 变体各自独立。
            for (AEKey key : exactKeyOrder) {
                if (added.add(key) && !blacklist.isBlacklisted(key)) {
                    cachedAvailableStacks.set(key, IInfinityCell.getAsIntMax(key));
                }
            }
            for (String pattern : patterns) {
                for (AEKey key : Cell4Patterns.expand(pattern)) {
                    if (key != null && added.add(key) && !blacklist.isBlacklisted(key)) {
                        cachedAvailableStacks.set(key, IInfinityCell.getAsIntMax(key));
                    }
                }
            }
            cacheValid = true;
        }
        // Use set() instead of add() to prevent overflow.
        // AE2's KeyCounter.add() does raw long arithmetic: Long.MAX_VALUE + any positive
        // amount overflows to a negative long, causing AE2 to hide the stack from the terminal.
        // set() directly replaces the value, so it never accumulates beyond Long.MAX_VALUE.
        for (var entry : cachedAvailableStacks) {
            out.set(entry.getKey(), Long.MAX_VALUE);
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_item_cell");
    }
}
