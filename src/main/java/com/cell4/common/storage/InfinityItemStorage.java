package com.cell4.common.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.util.Cell4Patterns;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * StorageCell implementation for the Infinity Item Cell.
 * Supports exact identifiers and glob/regex patterns in cell4item.
 */
public class InfinityItemStorage extends AbstractInfinityStorage {

    private final List<String> patterns;
    /**
     * 精确条目 → key。map 的键是“完整条目字符串”，因此同 ID 不同 NBT 的变体
     * 各自保留一个独立 key，不会被 AEKey 的相等性合并（否则终端只显示一格）。
     */
    private final Map<String, AEKey> exactVariants;
    /** 快速过滤：变体 key 的集合。 */
    private final Set<AEKey> exactKeys;

    // 2.2: Lazy-load and cache available stacks
    private List<AEKey> cachedAvailableKeys;

    public InfinityItemStorage(ItemStack cellItem) {
        super(cellItem);
        this.exactKeys = new HashSet<>();
        this.exactVariants = new LinkedHashMap<>();
        this.patterns = new ArrayList<>();
        for (String entry : InfinityItemCell.getIdentifiers(cellItem)) {
            if (entry == null || entry.isEmpty()) continue;
            if (entry.indexOf('{') < 0 && Cell4Patterns.isPattern(entry)) {
                if (Cell4Patterns.isValidPattern(entry)) patterns.add(entry);
            } else {
                // parseExactEntry 失败时会输出带摘要/异常的 warn，绑定条目不再静默消失。
                AEKey key = Cell4Patterns.parseExactEntry(entry);
                if (key != null) {
                    exactVariants.putIfAbsent(entry, key);
                    exactKeys.add(key);
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
        if (cachedAvailableKeys == null) {
            Map<String, AEKey> keys = new LinkedHashMap<>(exactVariants);
            for (String pattern : patterns) {
                for (AEKey key : Cell4Patterns.expand(pattern)) {
                    if (key != null) keys.putIfAbsent("pattern|" + key, key);
                }
            }
            List<AEKey> available = new ArrayList<>(keys.size());
            for (AEKey key : keys.values()) {
                if (!blacklist.isBlacklisted(key)) available.add(key);
            }
            cachedAvailableKeys = available;
        }
        // Use set() instead of add() to prevent overflow: every NBT variant is
        // reported independently at Long.MAX_VALUE so the terminal shows one row each.
        for (AEKey key : cachedAvailableKeys) {
            out.set(key, Long.MAX_VALUE);
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_item_cell");
    }
}
