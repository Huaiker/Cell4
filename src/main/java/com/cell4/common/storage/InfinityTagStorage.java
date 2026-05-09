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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * StorageCell implementation for the Infinity Tag Cell.
 * Provides infinite extraction of any item/fluid matching specified tags
 * or from specified mods.
 * Insertion of matching keys is accepted but items are silently discarded (phantom storage).
 * Supports blacklist via cell4blacklist NBT key (items, tags, and mod IDs).
 */
public class InfinityTagStorage implements StorageCell {

    private final List<String> tagNames;
    private final List<String> modIds;
    private final Set<String> modIdSet;
    private final Cell4Util.BlacklistData blacklist;

    public InfinityTagStorage(ItemStack cellItem) {
        this.tagNames = InfinityTagCell.getTagNames(cellItem);
        this.modIds = InfinityTagCell.getModIds(cellItem);
        this.modIdSet = Set.copyOf(modIds);
        this.blacklist = Cell4Util.getBlacklistData(cellItem);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (tagNames.isEmpty() && modIds.isEmpty()) {
            return 0;
        }
        if (matchesAnyFilter(what) && !blacklist.isBlacklisted(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (tagNames.isEmpty() && modIds.isEmpty()) return false;
        return matchesAnyFilter(what) && !blacklist.isBlacklisted(what);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (tagNames.isEmpty() && modIds.isEmpty()) {
            return 0;
        }
        if (matchesAnyFilter(what) && !blacklist.isBlacklisted(what)) {
            return amount;
        }
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (tagNames.isEmpty() && modIds.isEmpty()) {
            return;
        }

        Set<AEKey> addedKeys = new HashSet<>();

        // Add all items matching the tags, excluding blacklisted
        for (String tagName : tagNames) {
            ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
            if (tagRL == null) continue;

            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
                var key = AEItemKey.of(holder.value());
                if (key != null && !blacklist.isBlacklisted(key) && addedKeys.add(key)) {
                    out.add(key, Integer.MAX_VALUE);
                }
            }

            TagKey<Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
            for (var holder : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                Fluid fluid = holder.value();
                if (fluid == Fluids.EMPTY) continue;
                var key = AEFluidKey.of(fluid);
                if (key != null && !blacklist.isBlacklisted(key) && addedKeys.add(key)) {
                    out.add(key, (long) Integer.MAX_VALUE * AEFluidKey.AMOUNT_BUCKET);
                }
            }
        }

        // Add all items/fluids from the specified mods, excluding blacklisted
        if (!modIds.isEmpty()) {
            for (var item : BuiltInRegistries.ITEM) {
                ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
                if (rl != null && modIdSet.contains(rl.getNamespace())) {
                    var key = AEItemKey.of(item);
                    if (key != null && !blacklist.isBlacklisted(key) && addedKeys.add(key)) {
                        out.add(key, Integer.MAX_VALUE);
                    }
                }
            }

            for (var fluid : BuiltInRegistries.FLUID) {
                if (fluid == Fluids.EMPTY) continue;
                ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluid);
                if (rl != null && modIdSet.contains(rl.getNamespace())) {
                    var key = AEFluidKey.of(fluid);
                    if (key != null && !blacklist.isBlacklisted(key) && addedKeys.add(key)) {
                        out.add(key, (long) Integer.MAX_VALUE * AEFluidKey.AMOUNT_BUCKET);
                    }
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
        return (tagNames.isEmpty() && modIds.isEmpty()) ? CellState.EMPTY : CellState.NOT_EMPTY;
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
     * Check if a given AEKey matches any of the configured filters (tags or mod IDs).
     */
    private boolean matchesAnyFilter(AEKey key) {
        // Check tag filters
        for (String tagName : tagNames) {
            if (matchesTag(key, tagName)) {
                return true;
            }
        }
        // Check mod ID filters
        if (matchesAnyModId(key)) {
            return true;
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
     * Check if a given AEKey belongs to any of the configured mod IDs.
     */
    private boolean matchesAnyModId(AEKey key) {
        if (modIdSet.isEmpty()) return false;

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
