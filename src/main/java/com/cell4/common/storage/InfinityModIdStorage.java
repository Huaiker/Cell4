package com.cell4.common.storage;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.item.IInfinityCell;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Set;

/**
 * StorageCell implementation for the Infinity ModID Cell.
 * Provides infinite extraction of any item/fluid from specified mods (namespaces).
 * Insertion of matching keys is accepted but items are silently discarded (phantom storage).
 * Supports blacklist via cell4blacklist NBT key.
 */
public class InfinityModIdStorage extends AbstractInfinityStorage {

    private final List<String> modIds;
    private final Set<String> modIdSet;

    // 2.2: Cached available stacks
    private KeyCounter cachedAvailableStacks;
    private boolean cacheValid = false;

    public InfinityModIdStorage(ItemStack cellItem) {
        super(cellItem);
        this.modIds = InfinityModIdCell.getModIds(cellItem);
        this.modIdSet = Set.copyOf(modIds);
    }

    @Override
    protected boolean matchesFilter(AEKey what) {
        return matchesAnyModId(what);
    }

    @Override
    protected boolean hasConfiguration() {
        return !modIds.isEmpty();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (modIds.isEmpty()) return;

        // 2.2: Lazy-load and cache available stacks
        if (!cacheValid) {
            cachedAvailableStacks = new KeyCounter();

            for (var item : BuiltInRegistries.ITEM) {
                Identifier rl = BuiltInRegistries.ITEM.getKey(item);
                if (rl != null && modIdSet.contains(rl.getNamespace())) {
                    var key = AEItemKey.of(item);
                    if (key != null && !blacklist.isBlacklisted(key)) {
                        cachedAvailableStacks.add(key, IInfinityCell.getAsIntMax(key));
                    }
                }
            }

            for (var fluid : BuiltInRegistries.FLUID) {
                if (fluid == Fluids.EMPTY) continue;
                Identifier rl = BuiltInRegistries.FLUID.getKey(fluid);
                if (rl != null && modIdSet.contains(rl.getNamespace())) {
                    var key = AEFluidKey.of(fluid);
                    if (key != null && !blacklist.isBlacklisted(key)) {
                        cachedAvailableStacks.add(key, IInfinityCell.getAsIntMax(key));
                    }
                }
            }
            cacheValid = true;
        }
        for (var entry : cachedAvailableStacks) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_modid_cell");
    }

    private boolean matchesAnyModId(AEKey key) {
        return Cell4Util.belongsToMod(key, modIdSet);
    }
}
