package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.*;

/**
 * Shared NBT utility for Cell⁴.
 * Provides parsing of the blacklist NBT key and string list format.
 * Blacklist entries support three types:
 * - Plain identifier (e.g. "minecraft:diamond") → item/fluid blacklist
 * - "#" prefix (e.g. "#minecraft:logs") → tag blacklist
 * - "@" prefix (e.g. "@mekanism") → mod ID blacklist
 */
public class Cell4Util {

    public static final String BLACKLIST_KEY = "cell4blacklist";

    /**
     * Parsed blacklist data containing item keys, tag names, and mod IDs.
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
         * Check if a given AEKey is blacklisted by any of the three criteria.
         */
        public boolean isBlacklisted(AEKey key) {
            // Check direct item/fluid key
            if (itemKeys.contains(key)) return true;
            // Check tag-based blacklist
            for (String tagName : tagNames) {
                if (matchesTagBlacklist(key, tagName)) return true;
            }
            // Check mod ID-based blacklist
            if (key instanceof AEItemKey itemKey) {
                ResourceLocation rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
                if (rl != null && modIds.contains(rl.getNamespace())) return true;
            } else if (key instanceof AEFluidKey fluidKey) {
                ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
                if (rl != null && modIds.contains(rl.getNamespace())) return true;
            }
            return false;
        }

        public boolean isEmpty() {
            return itemKeys.isEmpty() && tagNames.isEmpty() && modIds.isEmpty();
        }

        private static boolean matchesTagBlacklist(AEKey key, String tagName) {
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

    /**
     * Parse blacklist data from an ItemStack's NBT.
     * Supports three types of entries:
     * - Plain identifier → item/fluid
     * - "#" prefix → tag
     * - "@" prefix → mod ID
     */
    public static BlacklistData getBlacklistData(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(BLACKLIST_KEY)) {
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
                // Tag entry: "#minecraft:logs" → "minecraft:logs"
                String tagName = id.substring(1);
                if (!tagName.isEmpty()) {
                    tagNames.add(tagName);
                }
            } else if (id.startsWith("@")) {
                // Mod ID entry: "@mekanism" → "mekanism"
                String modId = id.substring(1);
                if (!modId.isEmpty()) {
                    modIds.add(modId);
                }
            } else {
                // Item/fluid entry: "minecraft:diamond"
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
     * Parse blacklist AEKeys from an ItemStack's NBT (legacy - items only).
     */
    public static Set<AEKey> getBlacklistKeys(ItemStack stack) {
        return getBlacklistData(stack).getItemKeys();
    }

    /**
     * Get blacklist identifier strings from an ItemStack's NBT.
     */
    public static List<String> getBlacklistIds(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(BLACKLIST_KEY)) {
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
}
