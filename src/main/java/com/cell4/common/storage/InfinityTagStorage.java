package com.cell4.common.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import appeng.api.storage.cells.CellState;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.util.Cell4Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Set;

/**
 * StorageCell implementation for the Infinity Tag Cell.
 * Provides infinite extraction of any item/fluid matching specified tags.
 * Insertion of matching keys is accepted but items are silently discarded (phantom storage).
 * Supports blacklist via cell4blacklist NBT key.
 */
public class InfinityTagStorage implements StorageCell {

    private final List<String> tagNames;
    private final Set<AEKey> blacklist;

    public InfinityTagStorage(ItemStack cellItem) {
        this.tagNames = InfinityTagCell.getTagNames(cellItem);
        this.blacklist = Cell4Util.getBlacklistKeys(cellItem);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (tagNames.isEmpty()) {
            return 0;
        }
        if (matchesAnyTag(what) && !blacklist.contains(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (tagNames.isEmpty()) return false;
        return matchesAnyTag(what) && !blacklist.contains(what);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (tagNames.isEmpty()) {
            return 0;
        }
        if (matchesAnyTag(what) && !blacklist.contains(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (tagNames.isEmpty()) {
            return;
        }

        for (String tagName : tagNames) {
            ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
            if (tagRL == null) continue;

            // Add all items matching the tag, excluding blacklisted
            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
                var key = AEItemKey.of(holder.value());
                if (key != null && !blacklist.contains(key)) {
                    out.add(key, Integer.MAX_VALUE);
                }
            }

            // Add all fluids matching the tag, excluding blacklisted
            TagKey<Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
            for (var holder : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                Fluid fluid = holder.value();
                if (fluid == Fluids.EMPTY) continue;
                var key = AEFluidKey.of(fluid);
                if (key != null && !blacklist.contains(key)) {
                    out.add(key, (long) Integer.MAX_VALUE * AEFluidKey.AMOUNT_BUCKET);
                }
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_tag_cell");
    }

    @Override
    public CellState getStatus() {
        return tagNames.isEmpty() ? CellState.EMPTY : CellState.NOT_EMPTY;
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
     * Check if a given AEKey matches any of the configured tags.
     */
    private boolean matchesAnyTag(AEKey key) {
        for (String tagName : tagNames) {
            if (matchesTag(key, tagName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a given AEKey matches a specific tag.
     * Supports both AEItemKey and AEFluidKey.
     */
    private boolean matchesTag(AEKey key, String tagName) {
        ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
        if (tagRL == null) return false;

        if (key instanceof AEItemKey itemKey) {
            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(
                    BuiltInRegistries.ITEM.key(), tagRL);
            return itemKey.getItem().builtInRegistryHolder().is(itemTag);
        } else if (key instanceof AEFluidKey fluidKey) {
            TagKey<Fluid> fluidTag = TagKey.create(
                    BuiltInRegistries.FLUID.key(), tagRL);
            return fluidKey.getFluid().builtInRegistryHolder().is(fluidTag);
        }

        return false;
    }
}
