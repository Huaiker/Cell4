package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import java.util.*;

/**
 * Shared NBT utility for Cell⁴.
 * Provides parsing of the blacklist NBT key and string list format.
 */
public class Cell4Util {

    public static final String BLACKLIST_KEY = "cell4blacklist";

    /**
     * Parse blacklist AEKeys from an ItemStack's NBT.
     * The blacklist uses the same format as cell4item (single string or string list).
     */
    public static Set<AEKey> getBlacklistKeys(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(BLACKLIST_KEY)) {
            return Collections.emptySet();
        }

        List<String> ids = parseStringList(tag, BLACKLIST_KEY);
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }

        Set<AEKey> keys = new HashSet<>();
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                var item = BuiltInRegistries.ITEM.getOptional(rl);
                if (item.isPresent()) {
                    keys.add(AEItemKey.of(item.get()));
                    continue;
                }
                var fluid = BuiltInRegistries.FLUID.getOptional(rl);
                if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                    keys.add(AEFluidKey.of(fluid.get()));
                }
            }
        }
        return keys;
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
