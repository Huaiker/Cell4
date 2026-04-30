package com.cell4.common.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.cells.CellState;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.util.Cell4Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Set;

/**
 * StorageCell implementation for the Infinity ModID Cell.
 * Provides infinite extraction of any item/fluid from specified mods (namespaces).
 * Insertion of matching keys is accepted but items are silently discarded (phantom storage).
 * Supports blacklist via cell4blacklist NBT key.
 */
public class InfinityModIdStorage implements StorageCell {

    private final List<String> modIds;
    private final Set<String> modIdSet;
    private final Set<AEKey> blacklist;

    public InfinityModIdStorage(ItemStack cellItem) {
        this.modIds = InfinityModIdCell.getModIds(cellItem);
        this.modIdSet = Set.copyOf(modIds);
        this.blacklist = Cell4Util.getBlacklistKeys(cellItem);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (modIds.isEmpty()) {
            return 0;
        }
        if (matchesAnyModId(what) && !blacklist.contains(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (modIds.isEmpty()) return false;
        return matchesAnyModId(what) && !blacklist.contains(what);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (modIds.isEmpty()) {
            return 0;
        }
        if (matchesAnyModId(what) && !blacklist.contains(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (modIds.isEmpty()) {
            return;
        }

        // Add all items from the specified mods, excluding blacklisted
        for (var item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl != null && modIdSet.contains(rl.getNamespace())) {
                var key = AEItemKey.of(item);
                if (key != null && !blacklist.contains(key)) {
                    out.add(key, Integer.MAX_VALUE);
                }
            }
        }

        // Add all fluids from the specified mods, excluding blacklisted
        for (var fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY) continue;
            ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluid);
            if (rl != null && modIdSet.contains(rl.getNamespace())) {
                var key = AEFluidKey.of(fluid);
                if (key != null && !blacklist.contains(key)) {
                    out.add(key, (long) Integer.MAX_VALUE * AEFluidKey.AMOUNT_BUCKET);
                }
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_modid_cell");
    }

    @Override
    public CellState getStatus() {
        return modIds.isEmpty() ? CellState.EMPTY : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return 0.0;
    }

    @Override
    public void persist() {
        // No persistence needed
    }

    /**
     * Check if a given AEKey belongs to any of the configured mod IDs.
     * Supports both AEItemKey and AEFluidKey.
     */
    private boolean matchesAnyModId(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
            return rl != null && modIdSet.contains(rl.getNamespace());
        } else if (key instanceof AEFluidKey fluidKey) {
            ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
            return rl != null && modIdSet.contains(rl.getNamespace());
        }
        return false;
    }
}
