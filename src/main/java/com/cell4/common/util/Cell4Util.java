package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;

import java.util.*;

public class Cell4Util {

    // Keep backward compatibility alias
    public static final String BLACKLIST_KEY = NBTKeys.BLACKLIST;

    public static CompoundTag getCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : new CompoundTag();
    }

    public static void setCustomTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * BlacklistData holds three types of blacklist entries with tag caching (2.3).
     */
    public static class BlacklistData {
        private final Set<AEKey> itemKeys;
        private final List<String> tagNames;
        private final Set<String> modIds;
        // 2.3: Cache for tag blacklist matching results
        private Map<String, Boolean> tagMatchCache = new HashMap<>();

        public BlacklistData(Set<AEKey> itemKeys, List<String> tagNames, Set<String> modIds) {
            this.itemKeys = itemKeys;
            this.tagNames = tagNames;
            this.modIds = modIds;
        }

        public Set<AEKey> getItemKeys() { return itemKeys; }
        public List<String> getTagNames() { return tagNames; }
        public Set<String> getModIds() { return modIds; }

        public boolean isBlacklisted(AEKey key) {
            if (itemKeys.contains(key)) return true;

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

            if (!modIds.isEmpty()) {
                ResourceLocation rl = null;
                if (key instanceof AEItemKey itemKey) {
                    rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
                } else if (key instanceof AEFluidKey fluidKey) {
                    rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
                }
                if (rl != null && modIds.contains(rl.getNamespace())) return true;
            }

            return false;
        }

        public boolean isEmpty() {
            return itemKeys.isEmpty() && tagNames.isEmpty() && modIds.isEmpty();
        }
    }

    public static BlacklistData getBlacklistData(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(NBTKeys.BLACKLIST)) {
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
                String tagName = id.substring(1);
                if (!tagName.isEmpty()) tagNames.add(tagName);
            } else if (id.startsWith("@")) {
                String modId = id.substring(1);
                if (!modId.isEmpty()) modIds.add(modId);
            } else {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) {
                    var item = BuiltInRegistries.ITEM.getOptional(rl);
                    if (item.isPresent()) { itemKeys.add(AEItemKey.of(item.get())); continue; }
                    var fluid = BuiltInRegistries.FLUID.getOptional(rl);
                    if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                        itemKeys.add(AEFluidKey.of(fluid.get()));
                    }
                }
            }
        }

        return new BlacklistData(itemKeys, tagNames, modIds);
    }

    public static Set<AEKey> getBlacklistKeys(ItemStack stack) {
        return getBlacklistData(stack).getItemKeys();
    }

    public static List<String> getBlacklistIds(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(NBTKeys.BLACKLIST)) return Collections.emptyList();
        return parseStringList(tag, NBTKeys.BLACKLIST);
    }

    public static List<String> parseStringList(CompoundTag tag, String key) {
        if (!tag.contains(key)) return Collections.emptyList();
        if (tag.get(key) instanceof ListTag listTag) {
            List<String> result = new ArrayList<>(listTag.size());
            for (int i = 0; i < listTag.size(); i++) {
                String str = listTag.getString(i);
                if (!str.isEmpty()) result.add(str);
            }
            return result;
        }
        String single = tag.getString(key);
        if (!single.isEmpty()) return Collections.singletonList(single);
        return Collections.emptyList();
    }

    // === Shared NBT list get/set for cell data fields ===

    public static List<String> getStringList(ItemStack stack, String nbtKey) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(nbtKey)) return Collections.emptyList();
        return parseStringList(tag, nbtKey);
    }

    public static void setStringList(ItemStack stack, String nbtKey, List<String> values) {
        CompoundTag tag = getCustomTag(stack);
        ListTag listTag = new ListTag();
        for (String val : values) listTag.add(StringTag.valueOf(val));
        tag.put(nbtKey, listTag);
        setCustomTag(stack, tag);
    }

    public static void setStringValue(ItemStack stack, String nbtKey, String value) {
        CompoundTag tag = getCustomTag(stack);
        tag.putString(nbtKey, value);
        setCustomTag(stack, tag);
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

    private static boolean matchesTagBlacklist(AEKey key, String tagName) {
        ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
        if (tagRL == null) return false;
        if (key instanceof AEItemKey itemKey) {
            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
            return itemKey.getItem().builtInRegistryHolder().is(itemTag);
        } else if (key instanceof AEFluidKey fluidKey) {
            TagKey<net.minecraft.world.level.material.Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
            return fluidKey.getFluid().builtInRegistryHolder().is(fluidTag);
        }
        return false;
    }
}
