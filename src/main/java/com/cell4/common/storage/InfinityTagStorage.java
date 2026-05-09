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
 * Provides infinite extraction of any item/fluid matching specified tags,
 * optionally filtered by specified mod IDs.
 * <p>
 * Matching logic:
 * - Only tags → match any item with any of the tags
 * - Only mod IDs → no effect (cell is empty)
 * - Both tags AND mod IDs → match items that belong to any of the mods AND match any of the tags
 * </p>
 * Insertion of matching keys is accepted but items are silently discarded (phantom storage).
 * Supports blacklist via cell4blacklist NBT key (items, tags, and mod IDs).
 */
public class InfinityTagStorage implements StorageCell {

    private final List<String> tagNames;
    private final List<String> modIds;
    private final Set<String> modIdSet;
    private final boolean hasTags;
    private final boolean hasModIds;
    private final Cell4Util.BlacklistData blacklist;

    public InfinityTagStorage(ItemStack cellItem) {
        this.tagNames = InfinityTagCell.getTagNames(cellItem);
        this.modIds = InfinityTagCell.getModIds(cellItem);
        this.modIdSet = Set.copyOf(modIds);
        this.hasTags = !tagNames.isEmpty();
        this.hasModIds = !modIds.isEmpty();
        this.blacklist = Cell4Util.getBlacklistData(cellItem);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!hasTags) {
            return 0;
        }
        if (matchesFilter(what) && !blacklist.isBlacklisted(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (!hasTags) return false;
        return matchesFilter(what) && !blacklist.isBlacklisted(what);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!hasTags) {
            return 0;
        }
        if (matchesFilter(what) && !blacklist.isBlacklisted(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!hasTags) {
            return;
        }

        for (String tagName : tagNames) {
            ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
            if (tagRL == null) continue;

            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
                var key = AEItemKey.of(holder.value());
                if (key == null || blacklist.isBlacklisted(key)) continue;
                // If mod IDs specified, only include items from those mods
                if (hasModIds && !belongsToMod(key, modIdSet)) continue;
                out.add(key, Integer.MAX_VALUE);
            }

            TagKey<Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
            for (var holder : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                Fluid fluid = holder.value();
                if (fluid == Fluids.EMPTY) continue;
                var key = AEFluidKey.of(fluid);
                if (key == null || blacklist.isBlacklisted(key)) continue;
                if (hasModIds && !belongsToMod(key, modIdSet)) continue;
                out.add(key, (long) Integer.MAX_VALUE * AEFluidKey.AMOUNT_BUCKET);
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_tag_cell");
    }

    @Override
    public CellState getStatus() {
        return hasTags ? CellState.NOT_EMPTY : CellState.EMPTY;
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
     * Check if a given AEKey matches the filter.
     * - Only tags → matches any tag
     * - Both tags and mod IDs → must match any tag AND belong to any mod
     * - Only mod IDs → no effect (never called, hasTags is checked first)
     */
    private boolean matchesFilter(AEKey key) {
        boolean tagMatch = matchesAnyTag(key);
        if (!hasModIds) {
            return tagMatch;
        }
        return tagMatch && belongsToMod(key, modIdSet);
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

    /**
     * Check if a given AEKey belongs to any of the specified mod IDs.
     */
    private static boolean belongsToMod(AEKey key, Set<String> modIdSet) {
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
