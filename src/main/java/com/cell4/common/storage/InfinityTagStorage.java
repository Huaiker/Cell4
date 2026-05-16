package com.cell4.common.storage;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.item.IInfinityCell;
import com.cell4.common.util.Cell4Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * Supports blacklist via cell4blacklist NBT key and mod ID filtering via cell4modid NBT key.
 * <p>
 * Critical logic (updated with 5.1):
 * - Only cell4tag → tag matching (original behavior)
 * - Only cell4modid → ModID-only matching (new: behaves like ModID cell for tag cell)
 * - Both cell4tag AND cell4modid → AND/intersection (items must match tag AND belong to specified mod)
 */
public class InfinityTagStorage extends AbstractInfinityStorage {

    private final List<String> tagNames;
    private final List<String> modIds;
    private final Set<String> modIdSet;
    private final boolean hasTags;
    private final boolean hasModIds;

    // 2.2: Cached available stacks
    private KeyCounter cachedAvailableStacks;
    private boolean cacheValid = false;

    // 2.3: Cached tag blacklist matching results
    private Set<AEKey> cachedBlacklistedByTag;
    private boolean tagBlacklistCacheValid = false;

    public InfinityTagStorage(ItemStack cellItem) {
        super(cellItem);
        this.tagNames = InfinityTagCell.getTagNames(cellItem);
        this.modIds = InfinityTagCell.getModIds(cellItem);
        this.modIdSet = Set.copyOf(modIds);
        this.hasTags = !tagNames.isEmpty();
        this.hasModIds = !modIds.isEmpty();
    }

    @Override
    protected boolean matchesFilter(AEKey what) {
        // 5.1: Allow pure ModID mode
        if (hasTags) {
            // Original behavior: must match tag, optionally filtered by modid
            if (!matchesAnyTag(what)) return false;
            if (hasModIds && !belongsToMod(what, modIdSet)) return false;
            return true;
        } else if (hasModIds) {
            // 5.1: Pure ModID mode - behave like ModID cell
            return belongsToMod(what, modIdSet);
        }
        return false;
    }

    @Override
    protected boolean hasConfiguration() {
        // 5.1: Now valid with either tags or modids
        return hasTags || hasModIds;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!hasConfiguration()) return;

        // 2.2: Lazy-load and cache available stacks
        if (!cacheValid) {
            cachedAvailableStacks = new KeyCounter();

            if (hasTags) {
                for (String tagName : tagNames) {
                    Identifier tagRL = Identifier.tryParse(tagName);
                    if (tagRL == null) continue;

                    // Add items matching the tag
                    TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
                    for (Holder<net.minecraft.world.item.Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
                        var key = AEItemKey.of(holder.value());
                        if (key == null) continue;
                        if (hasModIds && !belongsToMod(key, modIdSet)) continue;
                        if (!blacklist.isBlacklisted(key)) {
                            cachedAvailableStacks.add(key, IInfinityCell.getAsIntMax(key));
                        }
                    }

                    // Add fluids matching the tag (6.2: include fluid display)
                    TagKey<Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
                    for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                        Fluid fluid = holder.value();
                        if (fluid == Fluids.EMPTY) continue;
                        var key = AEFluidKey.of(fluid);
                        if (key == null) continue;
                        if (hasModIds && !belongsToMod(key, modIdSet)) continue;
                        if (!blacklist.isBlacklisted(key)) {
                            cachedAvailableStacks.add(key, IInfinityCell.getAsIntMax(key));
                        }
                    }
                }
            } else if (hasModIds) {
                // 5.1: Pure ModID mode - add items from specified mods
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
            }
            cacheValid = true;
        }
        for (var entry : cachedAvailableStacks) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.cell4.infinity_tag_cell");
    }

    private boolean matchesAnyTag(AEKey key) {
        for (String tagName : tagNames) {
            if (matchesTag(key, tagName)) return true;
        }
        return false;
    }

    private boolean matchesTag(AEKey key, String tagName) {
        Identifier tagRL = Identifier.tryParse(tagName);
        if (tagRL == null) return false;
        if (key instanceof AEItemKey itemKey) {
            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
            return itemKey.getItem().builtInRegistryHolder().is(itemTag);
        } else if (key instanceof AEFluidKey fluidKey) {
            TagKey<Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
            return fluidKey.getFluid().builtInRegistryHolder().is(fluidTag);
        }
        return false;
    }

    private static boolean belongsToMod(AEKey key, Set<String> modIdSet) {
        return Cell4Util.belongsToMod(key, modIdSet);
    }
}
