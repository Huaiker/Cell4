package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;

import java.util.*;

/**
 * Shared NBT utility for Cell⁴.
 * Provides parsing of the blacklist NBT key and string list format.
 * In 1.21.1, item NBT is stored via DataComponents.CUSTOM_DATA.
 */
public class Cell4Util {

    public static final String BLACKLIST_KEY = "cell4blacklist";

    /**
     * Get the custom data CompoundTag from an ItemStack (1.21.1 data component system).
     */
    public static CompoundTag getCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : new CompoundTag();
    }

    /**
     * Set the custom data CompoundTag on an ItemStack (1.21.1 data component system).
     */
    public static void setCustomTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * BlacklistData holds three types of blacklist entries:
     * - Plain identifier (e.g. "minecraft:diamond") → item/fluid blacklist
     * - "#" prefix (e.g. "#minecraft:logs") → tag blacklist
     * - "@" prefix (e.g. "@mekanism") → mod ID blacklist
     */
    public static class BlacklistData {
        private final Set<AEKey> itemKeys;
        private final List<String> tagNames;
        private final Set<String> modIds;

        public BlacklistData(Set<AEKey> itemKeys, List<String> tagNames, Set<String> modIds) {
            this.itemKeys = itemKeys;
            this.tagNames = tagNames;
            this.modIds = modIds;
        }

        public Set<AEKey> getItemKeys() {
            return itemKeys;
        }

        public List<String> getTagNames() {
            return tagNames;
        }

        public Set<String> getModIds() {
            return modIds;
        }

        /**
         * Check if a given AEKey is blacklisted by any of the three blacklist types.
         */
        public boolean isBlacklisted(AEKey key) {
            // Check plain item/fluid blacklist
            if (itemKeys.contains(key)) {
                return true;
            }

            // Check tag blacklist
            for (String tagName : tagNames) {
                if (matchesTagBlacklist(key, tagName)) {
                    return true;
                }
            }

            // Check mod ID blacklist
            if (!modIds.isEmpty()) {
                ResourceLocation rl = null;
                if (key instanceof AEItemKey itemKey) {
                    rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
                } else if (key instanceof AEFluidKey fluidKey) {
                    rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
                }
                if (rl != null && modIds.contains(rl.getNamespace())) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Check if all blacklist types are empty.
         */
        public boolean isEmpty() {
            return itemKeys.isEmpty() && tagNames.isEmpty() && modIds.isEmpty();
        }
    }

    /**
     * Parse blacklist data from an ItemStack's custom data.
     * Supports three types of entries:
     * - Plain identifier → item/fluid key
     * - "#" prefix → tag blacklist
     * - "@" prefix → mod ID blacklist
     */
    public static BlacklistData getBlacklistData(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(BLACKLIST_KEY)) {
            return new BlacklistData(Collections.emptySet(), Collections.emptyList(), Collections.emptySet());
        }

        List<String> ids = parseStringList(tag, BLACKLIST_KEY);
        if (ids.isEmpty()) {
            return new BlacklistData(Collections.emptySet(), Collections.emptyList(), Collections.emptySet());
        }

        Set<AEKey> itemKeys = new HashSet<>();
        List<String> tagNames = new ArrayList<>();
        Set<String> modIds = new HashSet<>();

        for (String id : ids) {
            if (id.startsWith("#")) {
                // Tag blacklist
                String tagName = id.substring(1);
                if (!tagName.isEmpty()) {
                    tagNames.add(tagName);
                }
            } else if (id.startsWith("@")) {
                // Mod ID blacklist
                String modId = id.substring(1);
                if (!modId.isEmpty()) {
                    modIds.add(modId);
                }
            } else {
                // Plain identifier → item/fluid key
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) {
                    var item = BuiltInRegistries.ITEM.getOptional(rl);
                    if (item.isPresent()) {
                        itemKeys.add(AEItemKey.of(item.get()));
                        continue;
                    }
                    var fluid = BuiltInRegistries.FLUID.getOptional(rl);
                    if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                        itemKeys.add(AEFluidKey.of(fluid.get()));
                    }
                }
            }
        }

        return new BlacklistData(itemKeys, tagNames, modIds);
    }

    /**
     * Legacy method: returns only the item/fluid keys from blacklist data.
     */
    public static Set<AEKey> getBlacklistKeys(ItemStack stack) {
        return getBlacklistData(stack).getItemKeys();
    }

    /**
     * Get blacklist identifier strings from an ItemStack's custom data.
     */
    public static List<String> getBlacklistIds(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(BLACKLIST_KEY)) {
            return Collections.emptyList();
        }
        return parseStringList(tag, BLACKLIST_KEY);
    }

    /**
     * Parse a string list from NBT, supporting both single string and list formats.
     */
    public static List<String> parseStringList(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return Collections.emptyList();
        }

        // List format: {key:["a","b","c"]}
        if (tag.get(key) instanceof ListTag listTag) {
            List<String> result = new ArrayList<>(listTag.size());
            for (int i = 0; i < listTag.size(); i++) {
                String str = listTag.getString(i);
                if (!str.isEmpty()) {
                    result.add(str);
                }
            }
            return result;
        }

        // Single string format: {key:"a"}
        String single = tag.getString(key);
        if (!single.isEmpty()) {
            return Collections.singletonList(single);
        }

        return Collections.emptyList();
    }

    /**
     * Check if a given AEKey matches a specific tag blacklist entry.
     * Supports both AEItemKey and AEFluidKey.
     */
    private static boolean matchesTagBlacklist(AEKey key, String tagName) {
        ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
        if (tagRL == null) return false;

        if (key instanceof AEItemKey itemKey) {
            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(
                    BuiltInRegistries.ITEM.key(), tagRL);
            return itemKey.getItem().builtInRegistryHolder().is(itemTag);
        } else if (key instanceof AEFluidKey fluidKey) {
            TagKey<net.minecraft.world.level.material.Fluid> fluidTag = TagKey.create(
                    BuiltInRegistries.FLUID.key(), tagRL);
            return fluidKey.getFluid().builtInRegistryHolder().is(fluidTag);
        }

        return false;
    }
}
