package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import java.util.*;
import java.util.Set;

/**
 * Shared NBT utility for Cell⁴.
 * Provides parsing of the blacklist NBT key and string list format.
 * Supports three blacklist types:
 * <ul>
 *   <li>Plain identifier (e.g. "minecraft:diamond") → item/fluid blacklist</li>
 *   <li>"#" prefix (e.g. "#minecraft:logs") → tag blacklist</li>
 *   <li>"@" prefix (e.g. "@mekanism") → mod ID blacklist</li>
 * </ul>
 */
public class Cell4Util {

    // Keep backward compatibility alias
    public static final String BLACKLIST_KEY = NBTKeys.BLACKLIST;

    /**
     * Data class holding parsed blacklist information from a cell's NBT.
     * Contains three types of blacklists: item/fluid keys, tag names, and mod IDs.
     * 2.3: Now caches tag blacklist match results for performance.
     */
    public static class BlacklistData {
        private final Set<AEKey> itemKeys;
        private final List<String> tagNames;
        private final Set<String> modIds;
        // 2.3: Cache for tag blacklist matching results
        private final Map<String, Boolean> tagMatchCache = new HashMap<>();

        public BlacklistData(Set<AEKey> itemKeys, List<String> tagNames, Set<String> modIds) {
            this.itemKeys = itemKeys;
            this.tagNames = tagNames;
            this.modIds = modIds;
        }

        /**
         * Check if a given AEKey is blacklisted by any of the three blacklist types.
         * 2.3: Uses cached tag matching for repeated lookups.
         */
        public boolean isBlacklisted(AEKey key) {
            // Check item/fluid key blacklist
            if (itemKeys.contains(key)) {
                return true;
            }

            // 2.3: Use cached tag matching
            for (String tagName : tagNames) {
                String cacheKey = key.toString() + "|" + tagName;
                Boolean cached = tagMatchCache.get(cacheKey);
                if (cached != null) {
                    if (cached) return true;
                } else {
                    boolean matches = matchesTagBlacklist(key, tagName);
                    tagMatchCache.put(cacheKey, matches);
                    if (matches) return true;
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

        public Set<AEKey> getItemKeys() {
            return itemKeys;
        }

        public List<String> getTagNames() {
            return tagNames;
        }

        public Set<String> getModIds() {
            return modIds;
        }
    }

    /**
     * Parse blacklist data from an ItemStack's NBT.
     * Splits entries by prefix:
     * - No prefix → item/fluid key
     * - "#" prefix → tag name
     * - "@" prefix → mod ID
     */
    public static BlacklistData getBlacklistData(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(NBTKeys.BLACKLIST)) {
            return new BlacklistData(Collections.emptySet(), Collections.emptyList(), Collections.emptySet());
        }

        List<String> ids = parseStringList(tag, NBTKeys.BLACKLIST);
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
                // Plain item/fluid identifier
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
     * Parse blacklist AEKeys from an ItemStack's NBT (legacy method).
     * Returns only the item/fluid key portion of the blacklist.
     */
    public static Set<AEKey> getBlacklistKeys(ItemStack stack) {
        return getBlacklistData(stack).getItemKeys();
    }

    /**
     * Get blacklist identifier strings from an ItemStack's NBT.
     */
    public static List<String> getBlacklistIds(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(NBTKeys.BLACKLIST)) {
            return Collections.emptyList();
        }
        return parseStringList(tag, NBTKeys.BLACKLIST);
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
     * Check if an AEKey matches a tag blacklist entry.
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

    /**
     * Get the namespace (mod ID) of an AEKey.
     */
    public static String getNamespace(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
            return rl != null ? rl.getNamespace() : null;
        } else if (key instanceof AEFluidKey fluidKey) {
            ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
            return rl != null ? rl.getNamespace() : null;
        }
        return null;
    }

    // === Shared NBT list get/set for cell data fields ===

    public static List<String> getStringList(ItemStack stack, String nbtKey) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(nbtKey)) return Collections.emptyList();
        return parseStringList(tag, nbtKey);
    }

    public static void setStringList(ItemStack stack, String nbtKey, List<String> values) {
        var tag = stack.getOrCreateTag();
        ListTag listTag = new ListTag();
        for (String val : values) listTag.add(StringTag.valueOf(val));
        tag.put(nbtKey, listTag);
    }

    public static void setStringValue(ItemStack stack, String nbtKey, String value) {
        var tag = stack.getOrCreateTag();
        tag.putString(nbtKey, value);
    }

    public static boolean belongsToMod(AEKey key, Set<String> modIdSet) {
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
