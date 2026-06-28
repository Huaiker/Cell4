package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.cell4.common.integration.MekanismIntegration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import java.util.*;

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

    // === Identifier prefix parsing ===
    // Supported formats:
    //   fluid:namespace:path   → AEFluidKey
    //   chemical:namespace:path → MekanismKey (if Mekanism + AppMek installed)
    //   namespace:path         → item first, then fluid (legacy behavior)

    /**
     * Parse a user-entered identifier string into an AEKey.
     * <p>
     * Recognized prefixes:
     * <ul>
     *   <li>{@code fluid:ns:path} — explicit fluid</li>
     *   <li>{@code chemical:ns:path} — Mekanism chemical (requires Mekanism + Applied Mekanistics)</li>
     *   <li>{@code ns:path} (no prefix) — try item first, then fluid (legacy)</li>
     * </ul>
     * Returns null if the identifier cannot be resolved.
     */
    public static AEKey parseIdentifier(String id) {
        if (id == null || id.isEmpty()) return null;

        // Explicit Mekanism fluid prefix
        if (id.startsWith("mekfluid:")) {
            String rest = id.substring("mekfluid:".length());
            ResourceLocation rl = ResourceLocation.tryParse(rest);
            if (rl == null) return null;
            var fluid = BuiltInRegistries.FLUID.getOptional(rl);
            if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                return AEFluidKey.of(fluid.get());
            }
            return null;
        }

        // Explicit Mekanism chemical prefix
        if (id.startsWith("mekchemical:")) {
            String rest = id.substring("mekchemical:".length());
            ResourceLocation rl = ResourceLocation.tryParse(rest);
            if (rl == null) return null;
            return MekanismIntegration.parseChemicalKey(rl);
        }

        // Energy identifiers: energy:ars_source energy:botania_mana energy:fe
        if (id.startsWith("energy:")) {
            String rest = id.substring("energy:".length());
            if (rest.equals("ars_source")) return com.cell4.common.integration.ThirdPartyIntegration.getSourceKey();
            if (rest.equals("botania_mana")) return com.cell4.common.integration.ThirdPartyIntegration.getManaKey();
            if (rest.equals("fe")) return com.cell4.common.integration.ThirdPartyIntegration.getFEKey();
            return null;
        }

        // Legacy: try item, then fluid
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        var item = BuiltInRegistries.ITEM.getOptional(rl);
        if (item.isPresent()) return AEItemKey.of(item.get());
        var fluid = BuiltInRegistries.FLUID.getOptional(rl);
        if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
            return AEFluidKey.of(fluid.get());
        }
        return null;
    }


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
                String ns = getNamespace(key);
                if (ns != null && modIds.contains(ns)) return true;
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
                // Use parseIdentifier so fluid:/chemical: prefixes are honored,
                // and legacy plain ids fall back to item→fluid.
                AEKey key = parseIdentifier(id);
                if (key != null) itemKeys.add(key);
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
        // Mekanism chemical tag matching (no-op if Mekanism not installed)
        return MekanismIntegration.matchesTag(key, tagName);
    }

    /**
     * Get the namespace (mod ID) of an AEKey. Supports items, fluids, and Mekanism chemicals.
     */
    public static String getNamespace(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
            return rl != null ? rl.getNamespace() : null;
        } else if (key instanceof AEFluidKey fluidKey) {
            ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
            return rl != null ? rl.getNamespace() : null;
        }
        return MekanismIntegration.getNamespace(key);
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
        // Fall back to Mekanism chemicals (returns false if Mekanism not installed)
        return MekanismIntegration.belongsToMod(key, modIdSet);
    }
}
