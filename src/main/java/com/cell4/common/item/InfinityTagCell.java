package com.cell4.common.item;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.AEBaseItem;
import com.cell4.common.util.Cell4Util;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Infinity Tag Cell - can infinitely extract any item/fluid matching specified tags.
 * <p>
 * Binding is done purely via NBT. Supports both single-value and list formats:
 * /give @p cell4:infinity_tag_cell{cell4tag:"minecraft:logs"}
 * /give @p cell4:infinity_tag_cell{cell4tag:["minecraft:logs","forge:ingots/iron"]}
 * </p>
 */
public class InfinityTagCell extends AEBaseItem implements ICellWorkbenchItem {

    private static final String NBT_KEY = "cell4tag";

    public InfinityTagCell() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * Get tag name strings from NBT.
     * Supports both single string (legacy) and list format.
     */
    @NotNull
    public static List<String> getTagNames(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY)) {
            return Collections.emptyList();
        }

        // List format: {cell4tag:["minecraft:logs","forge:ingots"]}
        if (tag.get(NBT_KEY) instanceof ListTag listTag) {
            List<String> result = new ArrayList<>(listTag.size());
            for (int i = 0; i < listTag.size(); i++) {
                String str = listTag.getString(i);
                if (!str.isEmpty()) {
                    result.add(str);
                }
            }
            return result;
        }

        // Legacy single string format: {cell4tag:"minecraft:logs"}
        String single = tag.getString(NBT_KEY);
        if (!single.isEmpty()) {
            return Collections.singletonList(single);
        }

        return Collections.emptyList();
    }

    public static void setTagNames(ItemStack stack, List<String> tagNames) {
        var tag = stack.getOrCreateTag();
        ListTag listTag = new ListTag();
        for (String name : tagNames) {
            listTag.add(StringTag.valueOf(name));
        }
        tag.put(NBT_KEY, listTag);
    }

    public static void setTagName(ItemStack stack, String tagName) {
        var tag = stack.getOrCreateTag();
        tag.putString(NBT_KEY, tagName);
    }

    public static boolean hasTagNames(ItemStack stack) {
        return !getTagNames(stack).isEmpty();
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack is) {
        List<String> tags = getTagNames(is);
        if (tags.isEmpty()) {
            return Component.translatable("item.cell4.infinity_tag_cell");
        }
        if (tags.size() == 1) {
            return Component.translatable("item.cell4.infinity_tag_cell_name", tags.get(0));
        }
        return Component.translatable("item.cell4.infinity_tag_cell_name_multi", tags.size());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack is, Level world, @NotNull List<Component> lines, @NotNull TooltipFlag adv) {
        lines.add(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
        List<String> tags = getTagNames(is);
        for (String tag : tags) {
            lines.add(Component.translatable("tooltip.cell4.tag_filter", tag).withStyle(ChatFormatting.AQUA));
        }
        for (AEKey key : Cell4Util.getBlacklistKeys(is)) {
            lines.add(Component.translatable("tooltip.cell4.blacklist_item", key.getDisplayName()).withStyle(ChatFormatting.RED));
        }
    }

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        return Optional.empty();
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack itemStack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) {
        // NO-OP
    }
}
